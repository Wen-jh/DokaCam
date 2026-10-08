package com.dokacam.camera.data.model

/**
 * 滤镜分组。Doka 的设计哲学是「克制」——只提供经过筛选的经典色调，
 * 因此分组本身就是一种策展：胶片 / CCD / 黑白 / 创意。
 */
enum class FilterGroup(val label: String) {
    ORIGINAL("原片"),
    FILM("胶片"),
    CCD("CCD"),
    MONO("黑白"),
    CREATIVE("创意")
}

/** 三维向量，用于 lift / gain / 分离色调 */
data class Vec3(val r: Float, val g: Float, val b: Float) {
    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
    }
}

/**
 * 滤镜参数 —— 全部映射到片元着色器的 uniform。
 *
 * 设计说明：与其为每个滤镜写一个独立着色器（编译慢、维护地狱），
 * 不如定义一套**通用色彩管线**，每个滤镜只是一组参数。
 * 这既让新增滤镜变成「填一张表」，也让强度滑杆天然可用（参数插值）。
 */
data class FilterParams(
    // ---- 基础曝光 ----
    val exposure: Float = 0f,          // EV, -2..2
    val contrast: Float = 1f,          // 1 = 中性
    val saturation: Float = 1f,        // 1 = 中性
    val vibrance: Float = 0f,          // 保护肤色与高饱和区
    val temperature: Float = 0f,       // -1 冷 .. +1 暖
    val tint: Float = 0f,              // -1 绿 .. +1 品红

    // ---- 影调 ----
    val highlights: Float = 0f,        // -1 压低高光
    val shadows: Float = 0f,           // +1 提亮阴影
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val fade: Float = 0f,              // 0..1 提黑（胶片褪色感）
    val gamma: Float = 1f,

    // ---- 三级调色（ASC CDL）----
    val lift: Vec3 = Vec3.ZERO,
    val gain: Vec3 = Vec3.ONE,
    val shadowTint: Vec3 = Vec3.ZERO,      // 阴影分离色调
    val highlightTint: Vec3 = Vec3.ZERO,   // 高光分离色调
    val splitBalance: Float = 0.5f,

    // ---- 通道混合矩阵（9 个数，行主序）----
    val colorMatrix: List<Float>? = null,

    // ---- 质感 ----
    val grain: Float = 0f,             // 0..1 颗粒强度
    val grainSize: Float = 1.5f,       // 颗粒粗细
    val vignette: Float = 0f,          // 正=压暗 负=提亮
    val chromaAberration: Float = 0f,  // 色散（镜头轴向色差）
    val halation: Float = 0f,          // 高光红晕（胶片卤素光晕）
    val bloom: Float = 0f,             // 高光柔化
    val soften: Float = 0f,            // 整体柔焦
    val sharpen: Float = 0f,
    val posterize: Float = 0f,         // 0 = 关；否则色阶数（CCD 低比特感）
    val scanline: Float = 0f,          // VHS 扫描线
    val resolutionScale: Float = 1f,   // CCD 低像素模拟（1 = 原始）

    // ---- 人像（可与任意滤镜叠加）----
    val skinSoften: Float = 0f,
    val skinBrighten: Float = 0f,
) {
    companion object {
        /** 中性参数：所有滤镜强度的起点 */
        val NEUTRAL = FilterParams()

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun lerpV3(a: Vec3, b: Vec3, t: Float) =
            Vec3(lerp(a.r, b.r, t), lerp(a.g, b.g, t), lerp(a.b, b.b, t))

        /**
         * 参数插值 —— 滤镜强度滑杆的实现基础。
         * t=0 得到中性画面，t=1 得到完整滤镜效果。
         */
        fun lerpParams(from: FilterParams, to: FilterParams, t: Float): FilterParams =
            FilterParams(
                exposure = lerp(from.exposure, to.exposure, t),
                contrast = lerp(from.contrast, to.contrast, t),
                saturation = lerp(from.saturation, to.saturation, t),
                vibrance = lerp(from.vibrance, to.vibrance, t),
                temperature = lerp(from.temperature, to.temperature, t),
                tint = lerp(from.tint, to.tint, t),
                highlights = lerp(from.highlights, to.highlights, t),
                shadows = lerp(from.shadows, to.shadows, t),
                whites = lerp(from.whites, to.whites, t),
                blacks = lerp(from.blacks, to.blacks, t),
                fade = lerp(from.fade, to.fade, t),
                gamma = lerp(from.gamma, to.gamma, t),
                lift = lerpV3(from.lift, to.lift, t),
                gain = lerpV3(from.gain, to.gain, t),
                shadowTint = lerpV3(from.shadowTint, to.shadowTint, t),
                highlightTint = lerpV3(from.highlightTint, to.highlightTint, t),
                splitBalance = lerp(from.splitBalance, to.splitBalance, t),
                colorMatrix = if (t >= 1f) to.colorMatrix else from.colorMatrix,
                grain = lerp(from.grain, to.grain, t),
                grainSize = lerp(from.grainSize, to.grainSize, t),
                vignette = lerp(from.vignette, to.vignette, t),
                chromaAberration = lerp(from.chromaAberration, to.chromaAberration, t),
                halation = lerp(from.halation, to.halation, t),
                bloom = lerp(from.bloom, to.bloom, t),
                soften = lerp(from.soften, to.soften, t),
                sharpen = lerp(from.sharpen, to.sharpen, t),
                posterize = lerp(from.posterize, to.posterize, t),
                scanline = lerp(from.scanline, to.scanline, t),
                resolutionScale = lerp(from.resolutionScale, to.resolutionScale, t),
                skinSoften = lerp(from.skinSoften, to.skinSoften, t),
                skinBrighten = lerp(from.skinBrighten, to.skinBrighten, t),
            )
    }
}

/**
 * 一个完整的滤镜预设。
 *
 * @param lutAsset  可选：外部 .cube LUT 文件路径（assets 下），预设色彩查找表
 * @param cameraName 对应的「虚拟相机」名，复古相机 App 的核心情感设计
 */
data class FilterPreset(
    val id: String,
    val name: String,
    val group: FilterGroup,
    val description: String,
    val params: FilterParams,
    val lutAsset: String? = null,
    val cameraName: String? = null,
    val isPremium: Boolean = false,
) {
    companion object {
        val NONE = FilterPreset(
            id = "none",
            name = "原片",
            group = FilterGroup.ORIGINAL,
            description = "不做任何处理，保留传感器原始色彩",
            params = FilterParams.NEUTRAL,
        )
    }
}
