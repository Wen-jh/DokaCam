package com.dokacam.camera.gl

import android.opengl.GLES30
import android.util.Log
import com.dokacam.camera.data.model.FilterParams

/**
 * Uber-Shader 渲染程序：编译 [FilterShaders] 的片元变体并缓存全部 uniform 位置，
 * 供预览（OES 变体）与离线烘焙（2D 变体）共用一套参数下发逻辑。
 *
 * 必须在持有 EGL 上下文的线程上构造与调用。
 */
class FilterProgram(vertexSrc: String, fragmentSrc: String) {

    companion object { private const val TAG = "FilterProgram" }

    val program: Int

    // 顶点属性与几何矩阵（由调用方按自身管线设置）
    val aPosition: Int
    val aTexCoord: Int

    private val loc = HashMap<String, Int>()

    init {
        program = build(vertexSrc, fragmentSrc)
        aPosition = GLES30.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES30.glGetAttribLocation(program, "aTexCoord")
    }

    fun isValid() = program != 0

    fun use() = GLES30.glUseProgram(program)

    fun loc(name: String): Int = loc.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    /** 由调用方设置的几何 uniform（预览管线需要；离线烘焙不用） */
    fun setMatrix(name: String, m: FloatArray) {
        val l = loc(name)
        if (l >= 0) GLES30.glUniformMatrix4fv(l, 1, false, m, 0)
    }

    /**
     * 把 [FilterParams] 逐项映射到 shader uniform。
     * @param quality 0 = 预览精简档（关色散/光晕/锐化），1 = 拍照全效档
     * @param mirror  shader 内部水平镜像；预览/烘焙的几何层已处理镜像，一般传 0
     */
    fun applyParams(
        p: FilterParams,
        texWidth: Int,
        texHeight: Int,
        timeSec: Float,
        quality: Float,
        mirror: Float = 0f,
    ) {
        fun f(name: String, v: Float) { val l = loc(name); if (l >= 0) GLES30.glUniform1f(l, v) }
        fun v3(name: String, v: com.dokacam.camera.data.model.Vec3) {
            val l = loc(name); if (l >= 0) GLES30.glUniform3f(l, v.r, v.g, v.b)
        }

        loc("uTexelSize").takeIf { it >= 0 }?.let {
            GLES30.glUniform2f(it, 1f / texWidth.coerceAtLeast(1), 1f / texHeight.coerceAtLeast(1))
        }
        f("uTime", timeSec)
        f("uMirror", mirror)
        f("uAspect", texWidth.toFloat() / texHeight.coerceAtLeast(1))
        f("uQuality", quality)

        val mat = p.colorMatrix
        f("uUseMatrix", if (mat != null && mat.size == 9) 1f else 0f)
        if (mat != null && mat.size == 9) {
            loc("uColorMatrix").takeIf { it >= 0 }?.let {
                GLES30.glUniformMatrix3fv(it, 1, false, mat.toFloatArray(), 0)
            }
        }

        f("uExposure", p.exposure)
        f("uContrast", p.contrast)
        f("uSaturation", p.saturation)
        f("uVibrance", p.vibrance)
        f("uTemperature", p.temperature)
        f("uTint", p.tint)

        f("uHighlights", p.highlights)
        f("uShadows", p.shadows)
        f("uWhites", p.whites)
        f("uBlacks", p.blacks)
        f("uFade", p.fade)
        f("uGamma", p.gamma)

        v3("uLift", p.lift)
        v3("uGain", p.gain)
        v3("uShadowTint", p.shadowTint)
        v3("uHighlightTint", p.highlightTint)
        f("uSplitBalance", p.splitBalance)

        f("uGrain", p.grain)
        f("uGrainSize", p.grainSize)
        f("uVignette", p.vignette)
        f("uChroma", p.chromaAberration)
        f("uHalation", p.halation)
        f("uBloom", p.bloom)
        f("uSoften", p.soften)
        f("uSharpen", p.sharpen)
        f("uPosterize", p.posterize)
        f("uScanline", p.scanline)

        f("uSkinSoften", p.skinSoften)
        f("uSkinBrighten", p.skinBrighten)
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
    }

    private fun build(vsSrc: String, fsSrc: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            // 三引号模板首行是换行，#version 必须在源码第一行，先剥掉前导空白
            GLES30.glShaderSource(s, src.trimStart())
            GLES30.glCompileShader(s)
            val status = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e(TAG, "shader compile failed: ${GLES30.glGetShaderInfoLog(s)}")
                GLES30.glDeleteShader(s)
                return 0
            }
            return s
        }
        val vs = shader(GLES30.GL_VERTEX_SHADER, vsSrc)
        val fs = shader(GLES30.GL_FRAGMENT_SHADER, fsSrc)
        if (vs == 0 || fs == 0) return 0
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "program link failed: ${GLES30.glGetProgramInfoLog(p)}")
            GLES30.glDeleteProgram(p)
            return 0
        }
        return p
    }
}
