package com.dokacam.camera.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log

/**
 * 离屏 EGL 上下文 —— 拍照后对全分辨率图应用滤镜用。
 *
 * 与预览共享一个 share context，可复用已编译的 shader/纹理。
 * 用完必须 [release]，否则显存泄漏。
 */
class EglCore(private val shareContext: EGLContext? = null) {

    companion object {
        private const val TAG = "EglCore"
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    init {
        start()
    }

    private fun start() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay !== EGL14.EGL_NO_DISPLAY) { "unable to get EGL14 display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) &&
                numConfigs[0] > 0
        ) { "unable to find RGBA8888 / ES3 config" }
        eglConfig = configs[0]

        val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig,
            shareContext ?: EGL14.EGL_NO_CONTEXT, ctxAttrib, 0,
        )
        checkEglError("eglCreateContext")
        check(eglContext !== EGL14.EGL_NO_CONTEXT) { "null context" }
    }

    /** 创建离屏 pbuffer surface */
    fun createPbufferSurface(width: Int, height: Int): EGLSurface {
        val attrib = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE,
        )
        val surf = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, attrib, 0)
        checkEglError("eglCreatePbufferSurface")
        check(surf != null && surf !== EGL14.EGL_NO_SURFACE) { "pbuffer surface creation failed" }
        return surf
    }

    fun makeCurrent(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            "eglMakeCurrent failed"
        }
    }

    fun releaseSurface(surface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, surface)
    }

    fun release() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }

    private fun checkEglError(msg: String) {
        val err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS) {
            Log.e(TAG, "$msg: EGL error 0x${Integer.toHexString(err)}")
        }
    }
}

/**
 * 一次性离屏渲染会话：上传 Bitmap → 跑滤镜 → 读回 Bitmap。
 * 全流程在调用者线程同步执行，适合拍照后的成片处理。
 */
class OffscreenFilterSession(shareContext: EGLContext? = null) {

    private val egl = EglCore(shareContext)
    private var program: FilterProgram2D? = null

    /** @param block 返回处理后的纹理 id，默认直接绘制全屏 quad */
    fun process(
        input: Bitmap,
        configure: (FilterProgram2D) -> Unit,
    ): Bitmap {
        val w = input.width
        val h = input.height
        val surface = egl.createPbufferSurface(w, h)
        try {
            egl.makeCurrent(surface)
            GLES30SetViewport(w, h)

            val prog = program ?: FilterProgram2D().also { program = it }
            prog.use()

            val texId = GlUtil.uploadBitmap(input, maxDim = 0)
            prog.bindTexture(texId)

            configure(prog)
            prog.setTexelSize(w, h)

            prog.drawFullscreen(GlUtil.identityM())
            GlUtil.checkGlError("draw")

            val out = GlUtil.readFramebufferToBitmap(w, h)

            GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
            return out
        } finally {
            egl.releaseSurface(surface)
        }
    }

    fun release() {
        program?.release()
        program = null
        egl.release()
    }

    private fun GLES30SetViewport(w: Int, h: Int) {
        android.opengl.GLES30.glViewport(0, 0, w, h)
        android.opengl.GLES30.glClearColor(0f, 0f, 0f, 1f)
        android.opengl.GLES30.glClear(android.opengl.GLES30.GL_COLOR_BUFFER_BIT)
    }
}
