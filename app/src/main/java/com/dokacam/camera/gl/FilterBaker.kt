package com.dokacam.camera.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log
import com.dokacam.camera.data.model.FilterParams

/**
 * 离线滤镜烘焙：把当前 FilterParams 用 [FilterShaders.fragmentFor2D] 全效档
 * （uQuality=1）渲染到成片 Bitmap。
 *
 * 线程模型：在调用线程上自建独立的 EGL 环境（pbuffer + ES3 上下文，
 * 不与预览共享上下文），用完即毁。可安全地在拍照后台线程调用。
 *
 * 坐标说明：GL 帧缓冲原点在左下，绘制时翻转 V 坐标，
 * 配合 [GlUtil.readFramebufferToBitmap] 的行翻转，输出与输入方向一致。
 */
object FilterBaker {

    private const val TAG = "FilterBaker"

    /** 烘焙失败时返回原图（调用方兜底，保证拍照链路不中断） */
    fun bake(src: Bitmap, params: FilterParams): Bitmap {
        if (params.isNeutral()) return src

        var display: EGLDisplay? = null
        var context: EGLContext? = null
        var surface: EGLSurface? = null
        var prog: FilterProgram? = null
        var srcTex = 0
        var fbo = 0
        var fboTex = 0

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
            val ver = IntArray(2)
            check(EGL14.eglInitialize(display, ver, 0, ver, 1)) { "eglInitialize failed" }

            val config = chooseConfig(display)
                ?: throw IllegalStateException("no ES3 EGL config")
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
            )
            check(context != null && context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            check(surface != null && surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }

            val w = src.width
            val h = src.height
            prog = FilterProgram(FilterShaders.VERTEX_SHADER, FilterShaders.fragmentFor2D())
            check(prog.isValid()) { "filter program build failed" }

            srcTex = GlUtil.uploadBitmap(src)
            val (fboId, texId, _) = GlUtil.createFbo(w, h)
            fbo = fboId; fboTex = texId

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glViewport(0, 0, w, h)
            prog.use()
            // V 翻转的四边形：抵消 readFramebufferToBitmap 的行翻转
            val vertexBuf = GlUtil.createFloatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            val texBuf = GlUtil.createFloatBuffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
            prog.loc("uTexture").takeIf { it >= 0 }?.let { GLES30.glUniform1i(it, 0) }
            prog.setMatrix("uTexMatrix", GlUtil.identityM())
            // 拍照全效档；固定时间种子让颗粒稳定；镜像/旋转已在 Bitmap 阶段完成
            prog.applyParams(params, w, h, timeSec = 0.35f, quality = 1f, mirror = 0f)

            // 顶点属性位置取 shader 声明（layout location），不硬编码 0/1
            val aPos = prog.aPosition
            val aTex = prog.aTexCoord
            GLES30.glEnableVertexAttribArray(aPos)
            GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 0, vertexBuf)
            GLES30.glEnableVertexAttribArray(aTex)
            GLES30.glVertexAttribPointer(aTex, 2, GLES30.GL_FLOAT, false, 0, texBuf)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisableVertexAttribArray(aPos)
            GLES30.glDisableVertexAttribArray(aTex)

            val out = GlUtil.readFramebufferToBitmap(w, h)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            return out
        } catch (t: Throwable) {
            Log.e(TAG, "bake failed, returning original", t)
            return src
        } finally {
            runCatching {
                if (srcTex != 0) GLES30.glDeleteTextures(1, intArrayOf(srcTex), 0)
                if (fboTex != 0) GLES30.glDeleteTextures(1, intArrayOf(fboTex), 0)
                if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
                prog?.release()
                if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                    )
                    if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(display, surface)
                    }
                    if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(display, context)
                    }
                    EGL14.eglTerminate(display)
                }
            }
        }
    }

    private fun chooseConfig(display: EGLDisplay): EGLConfig? {
        // 优先 ES3；个别老驱动只暴露 ES2 位时回退（ES3 上下文仍可能创建成功）
        val renderableAttempts = intArrayOf(EGL_OPENGL_ES3_BIT, EGL14.EGL_OPENGL_ES2_BIT)
        for (renderable in renderableAttempts) {
            val attrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, renderable,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
                return configs[0]
            }
        }
        return null
    }

    private const val EGL_OPENGL_ES3_BIT = 0x40 // EGL_OPENGL_ES3_BIT_KHR
}
