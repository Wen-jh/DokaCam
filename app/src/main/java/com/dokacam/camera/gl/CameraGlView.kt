package com.dokacam.camera.gl

import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import com.dokacam.camera.camera.CameraController
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

class CameraGlView(context: Context) : GLSurfaceView(context) {

    companion object { private const val TAG = "CameraGlView" }

    var controller: CameraController? = null

    @Volatile private var surfaceTexture: SurfaceTexture? = null
    @Volatile private var pendingRequest: SurfaceRequest? = null

    @Volatile var bufferWidth = 0
        private set
    @Volatile var bufferHeight = 0
        private set
    @Volatile var frontFacing = false

    // 传感器→显示 的方向信息（来自 SurfaceRequest.TransformationInfo）
    @Volatile private var rotationDegrees = 0
    @Volatile private var cropRect: Rect? = null
    @Volatile private var hasCameraTransform = true

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        renderMode = RENDERMODE_WHEN_DIRTY
        setRenderer(PreviewRenderer())
    }

    fun supplySurfaceRequest(request: SurfaceRequest) {
        val st = surfaceTexture
        if (st != null) attachSurface(st, request) else pendingRequest = request
    }

    private fun attachSurface(st: SurfaceTexture, request: SurfaceRequest) {
        val resolution = request.resolution
        bufferWidth = resolution.width
        bufferHeight = resolution.height
        st.setDefaultBufferSize(resolution.width, resolution.height)
        val surface = Surface(st)
        request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { surface.release() }
        // 旋转/裁剪信息随绑定推进，驱动重绘
        request.setTransformationInfoListener(ContextCompat.getMainExecutor(context)) { info ->
            rotationDegrees = info.rotationDegrees
            cropRect = info.cropRect
            hasCameraTransform = info.hasCameraTransform()
            requestRender()
        }
    }

    private fun onTextureReady(st: SurfaceTexture) {
        surfaceTexture = st
        val req = pendingRequest
        pendingRequest = null
        if (req != null) attachSurface(st, req)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pendingRequest = null
        val st = surfaceTexture
        surfaceTexture = null
        st?.release()
    }

    private inner class PreviewRenderer : GLSurfaceView.Renderer {

        private var program = 0
        private var aPosition = 0
        private var aTexCoord = 0
        private var uMvp = 0
        private var uStMatrix = 0
        private var uTexture = 0
        private var textureId = 0

        private val stMatrix = FloatArray(16)
        private val mvpMatrix = FloatArray(16)
        private val cropMatrix = FloatArray(16)
        private val tmpMatrix = FloatArray(16)
        private var surfaceWidth = 1
        private var surfaceHeight = 1

        private lateinit var vertexBuffer: FloatBuffer
        private lateinit var texBuffer: FloatBuffer

        private val vertexCode = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMvp;
            uniform mat4 uStMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 0.0, 1.0);
                vTexCoord = (uStMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        private val fragmentCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
            // EGL 上下文（重）建：换新 SurfaceTexture，并让相机重新协商一路新 SurfaceRequest
            surfaceTexture?.release()
            surfaceTexture = null

            program = buildProgram()
            if (program == 0) {
                Log.e(TAG, "shader program failed; preview will stay black")
                return
            }
            aPosition = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
            uMvp = GLES20.glGetUniformLocation(program, "uMvp")
            uStMatrix = GLES20.glGetUniformLocation(program, "uStMatrix")
            uTexture = GLES20.glGetUniformLocation(program, "uTexture")

            vertexBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            texBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // 帧驱动回路（主线程 Looper，避免 GL 线程无 Looper 崩溃）
            val st = SurfaceTexture(textureId)
            st.setOnFrameAvailableListener({ requestRender() }, Handler(Looper.getMainLooper()))
            post { onTextureReady(st) }
            // 旧 Surface 已随旧 ST 作废：让 CameraX 重新发起 SurfaceRequest 绑到新 ST
            post { controller?.rebindForNewSurface() }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (program == 0) return
            val st = surfaceTexture ?: return

            // 消费帧 + 缓冲裁剪/翻转
            try {
                st.updateTexImage()
                st.getTransformMatrix(stMatrix)
            } catch (e: Exception) {
                return // ST 与渲染线程竞态被释放：跳过本帧
            }
            if (!hasCameraTransform) Matrix.setIdentityM(stMatrix, 0)

            val bw = if (bufferWidth > 0) bufferWidth else 640
            val bh = if (bufferHeight > 0) bufferHeight else 480
            val rot = rotationDegrees
            val crop = cropRect
            val cw = if (crop != null && crop.width() > 0) crop.width() else bw
            val ch = if (crop != null && crop.height() > 0) crop.height() else bh

            // C：cropRect（缓冲像素坐标，y 向下）映射到满四边形
            Matrix.setIdentityM(cropMatrix, 0)
            if (crop != null && (cw != bw || ch != bh)) {
                val nx = (crop.left + crop.right).toFloat() / bw - 1f
                val ny = 1f - (crop.top + crop.bottom).toFloat() / bh
                Matrix.scaleM(cropMatrix, 0, bw.toFloat() / cw, bh.toFloat() / ch, 1f)
                Matrix.translateM(cropMatrix, 0, -nx, -ny, 0f)
            }

            // FILL_CENTER：按旋转后的宽高比现算（90/270 交换宽高）
            val contentAspect = if (rot == 90 || rot == 270) ch.toFloat() / cw
                                else cw.toFloat() / ch
            val viewAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
            val ratio = if (viewAspect > 0f) contentAspect / viewAspect else 1f
            var sx = if (ratio >= 1f) ratio else 1f
            val sy = if (ratio >= 1f) 1f else 1f / ratio
            if (frontFacing) sx = -sx

            // uMvp = S(铺满/镜像) * R(转正；Android 约定顺时针为正，NDC 取负角) * C(裁剪)
            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.scaleM(mvpMatrix, 0, sx, sy, 1f)
            if (rot != 0) Matrix.rotateM(mvpMatrix, 0, -rot.toFloat(), 0f, 0f, 1f)
            Matrix.multiplyMM(tmpMatrix, 0, mvpMatrix, 0, cropMatrix, 0)

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(uTexture, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, tmpMatrix, 0)
            GLES20.glUniformMatrix4fv(uStMatrix, 1, false, stMatrix, 0)

            GLES20.glEnableVertexAttribArray(aPosition)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aTexCoord)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPosition)
            GLES20.glDisableVertexAttribArray(aTexCoord)
        }

        private fun buildProgram(): Int {
            fun shader(type: Int, src: String): Int {
                val s = GLES20.glCreateShader(type)
                GLES20.glShaderSource(s, src)
                GLES20.glCompileShader(s)
                val status = IntArray(1)
                GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
                if (status[0] == 0) {
                    Log.e(TAG, "shader compile failed: ${GLES20.glGetShaderInfoLog(s)}")
                    GLES20.glDeleteShader(s)
                    return 0
                }
                return s
            }
            val vs = shader(GLES20.GL_VERTEX_SHADER, vertexCode)
            val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
            if (vs == 0 || fs == 0) return 0
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vs)
            GLES20.glAttachShader(p, fs)
            GLES20.glLinkProgram(p)
            val status = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e(TAG, "program link failed: ${GLES20.glGetProgramInfoLog(p)}")
                GLES20.glDeleteProgram(p)
                return 0
            }
            return p
        }

        private fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(values); position(0) }
    }
}
