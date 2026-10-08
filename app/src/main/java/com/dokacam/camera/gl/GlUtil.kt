package com.dokacam.camera.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL 工具集：纹理创建、缓冲构造、错误检查、Bitmap 读写。
 * 统一用 GLES30（设备最低要求 API 24，ES 3.0 覆盖率 ~99%）。
 */
object GlUtil {

    private const val TAG = "GlUtil"

    fun checkGlError(op: String) {
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "$op failed: glError 0x${Integer.toHexString(err)}")
            throw RuntimeException("$op failed: glError 0x${Integer.toHexString(err)}")
        }
    }

    fun createFloatBuffer(array: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(array.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(array); position(0) }

    // ---------------- 纹理 ----------------

    /** 普通 RGBA 纹理 */
    fun createTexture2D(): Int {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        checkGlError("glGenTextures")
        bindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        setDefaultTexParams(GLES30.GL_TEXTURE_2D)
        return tex[0]
    }

    /** 相机外部纹理（OES） */
    fun createTextureOes(): Int {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        checkGlError("glGenTextures OES")
        bindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        setDefaultTexParams(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        return tex[0]
    }

    private fun bindTexture(target: Int, id: Int) {
        GLES30.glBindTexture(target, id)
    }

    private fun setDefaultTexParams(target: Int) {
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    /** Bitmap 上传为 GL 纹理（自动缩放到 maxDim 以内，防爆内存） */
    fun uploadBitmap(bitmap: Bitmap, maxDim: Int = 0): Int {
        val bmp = if (maxDim > 0 && (bitmap.width > maxDim || bitmap.height > maxDim)) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else bitmap

        val id = createTexture2D()
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        checkGlError("texImage2D")
        if (bmp !== bitmap) bmp.recycle()
        return id
    }

    /** 当前帧缓冲读回 Bitmap */
    fun readFramebufferToBitmap(width: Int, height: Int): Bitmap {
        val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        checkGlError("glReadPixels")
        val pixels = IntArray(width * height)
        buf.asIntBuffer().get(pixels)
        // GL 是 RGBA 字节序，Bitmap 需要 ARGB int 序，且 Y 轴翻转
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val srcRow = height - 1 - y
            for (x in 0 until width) {
                val p = pixels[srcRow * width + x]
                val r = (p shr 0) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = (p shr 16) and 0xFF
                val a = (p shr 24) and 0xFF
                out[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    // ---------------- 帧缓冲 ----------------

    fun createFbo(width: Int, height: Int): Triple<Int, Int, Int> {
        val fbo = IntArray(1)
        val tex = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )
        setDefaultTexParams(GLES30.GL_TEXTURE_2D)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, tex[0], 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "FBO incomplete: $status" }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return Triple(fbo[0], tex[0], 0)
    }

    // ---------------- 变换矩阵 ----------------

    fun identityM(): FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    /**
     * CameraX ImageProxy 的 YUV 无法直接进 GL，统一走 Bitmap 通道，
     * 预览则通过 SurfaceTexture.getTransformMatrix 获取正确方向。
     */
    fun surfaceTextureMatrix(st: SurfaceTexture): FloatArray {
        val m = FloatArray(16)
        st.getTransformMatrix(m)
        return m
    }
}
