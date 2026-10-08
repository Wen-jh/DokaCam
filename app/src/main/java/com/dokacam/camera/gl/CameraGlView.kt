package com.dokacam.camera.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.Executor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 相机预览视图：GLSurfaceView + 实时滤镜渲染。
 *
 * 数据流：CameraX Preview → SurfaceRequest → SurfaceTexture(OES) → 滤镜着色器 → 屏幕。
 * 「所见即所得」的关键 —— 预览跑的滤镜管线与拍照保存的完全同一套代码。
 *
 * @param frameListener 每帧回调（GL 线程），用于 AI 构图分析等
 */
class CameraGlView(
    context: Context,
    private val frameListener: (() -> Unit)? = null,
) : GLSurfaceView(context) {

    companion object { private const val TAG = "CameraGlView" }

    private var renderer: Renderer? = null

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        renderer = Renderer().also { setRenderer(it) }
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** 滤镜配置热切换（内部已做线程切换，任意线程可调） */
    fun setRenderConfig(config: RenderConfig) {
        queueEvent { renderer?.config = config }
    }

    /** 当前 EGL context（须在 GL 线程拿，拍照离屏渲染用来共享 program） */
    fun getEglContext(onReady: (android.opengl.EGLContext?) -> Unit) {
        queueEvent { onReady(renderer?.eglContext) }
    }

    /**
     * 直接作为 CameraX Preview 的 SurfaceProvider。
     * CameraX 调用 setSurfaceProvider { request -> ... } 时回调到本方法。
     */
    fun handleSurfaceRequest(request: SurfaceRequest, executor: Executor) {
        queueEvent {
            val r = renderer
            val surface = r?.surface
            if (r == null || surface == null) {
                Log.w(TAG, "renderer/surface not ready, rejecting")
                request.willNotProvideSurface()
                return@queueEvent
            }
            request.provideSurface(surface, executor) { }
        }
    }

    fun release() {
        queueEvent { renderer?.releaseGl() }
    }

    inner class Renderer : GLSurfaceView.Renderer {

        @Volatile var config: RenderConfig = RenderConfig()

        var eglContext: android.opengl.EGLContext? = null
            private set

        var surface: Surface? = null
            private set

        private var program: FilterProgramOes? = null
        private var oesTexId = 0
        private var surfaceTexture: SurfaceTexture? = null
        private var texMatrix = GlUtil.identityM()
        private var previewWidth = 1920
        private var previewHeight = 1080
        private var startMillis = 0L

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            eglContext = EGL14.eglGetCurrentContext()
            program = FilterProgramOes()
            oesTexId = GlUtil.createTextureOes()

            surfaceTexture = SurfaceTexture(oesTexId).apply {
                setDefaultBufferSize(previewWidth, previewHeight)
                setOnFrameAvailableListener {
                    try {
                        updateTexImage()
                        it.getTransformMatrix(texMatrix)
                    } catch (e: Exception) {
                        Log.w(TAG, "updateTexImage: ${e.message}")
                    }
                }
            }
            surface = Surface(surfaceTexture)
            startMillis = System.currentTimeMillis()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            val st = surfaceTexture ?: return
            val prog = program ?: return

            frameListener?.invoke()

            val cfg = this.config
            prog.use()
            prog.bindTexture(oesTexId)
            prog.setUniform2("uTexelSize", 1f / previewWidth, 1f / previewHeight)
            prog.setTime((System.currentTimeMillis() - startMillis) / 1000f)
            prog.setMirror(cfg.mirror)
            prog.setQuality(full = false) // 预览降级保帧率
            prog.applyParams(cfg.params, cfg.intensity)
            prog.drawFullscreen(texMatrix)
        }

        fun releaseGl() {
            program?.release()
            program = null
            if (oesTexId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(oesTexId), 0)
                oesTexId = 0
            }
            surface?.release()
            surface = null
            surfaceTexture?.release()
            surfaceTexture = null
        }
    }
}

/** 预览渲染配置（滤镜参数 + 强度 + 镜像） */
data class RenderConfig(
    val params: com.dokacam.camera.data.model.FilterParams =
        com.dokacam.camera.data.model.FilterParams.NEUTRAL,
    val intensity: Float = 1f,
    val mirror: Boolean = false,
)
