package com.dokacam.camera.gl

import android.opengl.GLES30
import android.opengl.Matrix
import com.dokacam.camera.data.model.FilterParams
import com.dokacam.camera.data.model.Vec3

/**
 * 滤镜着色器程序基类：编译、链接、uniform 定位与参数下发。
 * 2D / OES 两个变体共用同一份 uniform 表。
 */
abstract class FilterProgram protected constructor(private val fragmentSource: String) {

    protected var programHandle = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTexMatrixLoc = 0

    // uniform 缓存：避免每帧 glGetUniformLocation（驱动调用很贵）
    private val uniformLocs = HashMap<String, Int>()

    // 全屏 quad：位置与 UV 分开两个 buffer（TRIANGLE_STRIP 4 顶点）
    private val posBuf = GlUtil.createFloatBuffer(
        floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
    )
    private val uvBuf = GlUtil.createFloatBuffer(
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f),
    )

    init {
        build()
    }

    private fun build() {
        // trimIndent 去掉 raw string 的前导空行，保证 #version 是首行（GLSL 规范）
        val vs = compile(GLES30.GL_VERTEX_SHADER, FilterShaders.VERTEX_SHADER.trimIndent())
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource.trimIndent())
        programHandle = GLES30.glCreateProgram()
        GLES30.glAttachShader(programHandle, vs)
        GLES30.glAttachShader(programHandle, fs)
        GLES30.glLinkProgram(programHandle)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(programHandle, GLES30.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            throw RuntimeException(
                "program link failed: ${GLES30.glGetProgramInfoLog(programHandle)}",
            )
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        aPositionLoc = GLES30.glGetAttribLocation(programHandle, "aPosition")
        aTexCoordLoc = GLES30.glGetAttribLocation(programHandle, "aTexCoord")
        uTexMatrixLoc = GLES30.glGetUniformLocation(programHandle, "uTexMatrix")
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("shader compile failed: $log")
        }
        return shader
    }

    fun use() = GLES30.glUseProgram(programHandle)

    protected fun loc(name: String): Int =
        uniformLocs.getOrPut(name) { GLES30.glGetUniformLocation(programHandle, name) }

    // ---------------- 参数下发 ----------------

    fun setTexMatrix(matrix: FloatArray) =
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, matrix, 0)

    fun setUniform1(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)
    fun setUniform2(name: String, v0: Float, v1: Float) = GLES30.glUniform2f(loc(name), v0, v1)
    fun setUniform3(name: String, v: Vec3) = GLES30.glUniform3f(loc(name), v.r, v.g, v.b)

    fun setTexelSize(w: Int, h: Int) =
        setUniform2("uTexelSize", 1f / w, 1f / h)

    /**
     * 把一份滤镜参数下发到着色器。
     * @param intensity 0..1 强度（已在上层完成插值则传 1）
     */
    fun applyParams(params: FilterParams, intensity: Float) {
        val p = if (intensity >= 0.999f) params
        else FilterParams.lerpParams(FilterParams.NEUTRAL, params, intensity)

        setUniform1("uExposure", p.exposure)
        setUniform1("uContrast", p.contrast)
        setUniform1("uSaturation", p.saturation)
        setUniform1("uVibrance", p.vibrance)
        setUniform1("uTemperature", p.temperature)
        setUniform1("uTint", p.tint)
        setUniform1("uHighlights", p.highlights)
        setUniform1("uShadows", p.shadows)
        setUniform1("uWhites", p.whites)
        setUniform1("uBlacks", p.blacks)
        setUniform1("uFade", p.fade)
        setUniform1("uGamma", p.gamma)
        setUniform3("uLift", p.lift)
        setUniform3("uGain", p.gain)
        setUniform3("uShadowTint", p.shadowTint)
        setUniform3("uHighlightTint", p.highlightTint)
        setUniform1("uSplitBalance", p.splitBalance)

        val m = p.colorMatrix
        if (m != null && m.size == 9) {
            setUniform1("uUseMatrix", 1f)
            // GLSL mat3 列主序
            GLES30.glUniformMatrix3fv(
                loc("uColorMatrix"), 1, false,
                floatArrayOf(
                    m[0], m[3], m[6],
                    m[1], m[4], m[7],
                    m[2], m[5], m[8],
                ), 0,
            )
        } else {
            setUniform1("uUseMatrix", 0f)
        }

        setUniform1("uGrain", p.grain)
        setUniform1("uGrainSize", p.grainSize)
        setUniform1("uVignette", p.vignette)
        setUniform1("uChroma", p.chromaAberration)
        setUniform1("uHalation", p.halation)
        setUniform1("uBloom", p.bloom)
        setUniform1("uSoften", p.soften)
        setUniform1("uSharpen", p.sharpen)
        setUniform1("uPosterize", p.posterize)
        setUniform1("uScanline", p.scanline)
        setUniform1("uSkinSoften", p.skinSoften)
        setUniform1("uSkinBrighten", p.skinBrighten)
    }

    fun setTime(t: Float) = setUniform1("uTime", t)
    fun setMirror(mirror: Boolean) = setUniform1("uMirror", if (mirror) 1f else 0f)
    fun setQuality(full: Boolean) = setUniform1("uQuality", if (full) 1f else 0f)

    // ---------------- 绘制 ----------------

    fun drawFullscreen(texMatrix: FloatArray) {
        GLES30.glEnableVertexAttribArray(aPositionLoc)
        GLES30.glVertexAttribPointer(aPositionLoc, 2, GLES30.GL_FLOAT, false, 8, posBuf)
        GLES30.glEnableVertexAttribArray(aTexCoordLoc)
        GLES30.glVertexAttribPointer(aTexCoordLoc, 2, GLES30.GL_FLOAT, false, 8, uvBuf)

        setTexMatrix(texMatrix)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(aPositionLoc)
        GLES30.glDisableVertexAttribArray(aTexCoordLoc)
    }

    fun release() {
        if (programHandle != 0) {
            GLES30.glDeleteProgram(programHandle)
            programHandle = 0
        }
    }
}

/** 离线（Bitmap）处理变体 */
class FilterProgram2D : FilterProgram(FilterShaders.fragmentFor2D()) {
    fun bindTexture(texId: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        setUniform1("uTexture", 0f)
    }
}

/** 相机预览（OES）变体 */
class FilterProgramOes : FilterProgram(FilterShaders.fragmentForOes()) {
    fun bindTexture(texId: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        setUniform1("uTexture", 0f)
    }
}

private object GLES11Ext {
    const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
}
