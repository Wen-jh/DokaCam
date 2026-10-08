package com.dokacam.camera.filter

import com.dokacam.camera.data.model.FilterGroup
import com.dokacam.camera.data.model.FilterParams
import com.dokacam.camera.data.model.FilterPreset
import com.dokacam.camera.data.model.Vec3

/**
 * 滤镜库 —— 全部为声明式参数，无任何像素级硬编码。
 *
 * 策展原则（与 Doka 一致）：
 *  1. 克制 —— 不做夸张风格化，每种色调都应有真实胶片/相机原型
 *  2. 分组即语义 —— 胶片 / CCD / 黑白 / 创意
 *  3. 参数可插值 —— 强度滑杆对所有滤镜生效
 */
object FilterPresets {

    /** 通道混合矩阵（行主序） */
    private fun mat(
        rr: Float, rg: Float, rb: Float,
        gr: Float, gg: Float, gb: Float,
        br: Float, bg: Float, bb: Float,
    ) = listOf(rr, rg, rb, gr, gg, gb, br, bg, bb)

    private fun p(
        id: String, name: String, group: FilterGroup, desc: String,
        camera: String? = null,
        block: FilterParamsBuilder.() -> Unit,
    ): FilterPreset = FilterPresetBuilderHost.build(id, name, group, desc, camera, block)

    // 为避免 data class 全参构造的冗长，用一个小 builder
    private object FilterPresetBuilderHost {
        fun build(
            id: String, name: String, group: FilterGroup, desc: String,
            camera: String?, block: FilterParamsBuilder.() -> Unit,
        ): FilterPreset {
            val b = FilterParamsBuilder().apply(block)
            return FilterPreset(
                id = id, name = name, group = group, description = desc,
                params = b.toParams(), cameraName = camera,
            )
        }
    }

    val ALL: List<FilterPreset> by lazy {
        listOf(
            // ================= 原片 =================
            FilterPreset.NONE,

            // ================= 胶片 =================
            p(
                "portra400", "柯达 Portra", FilterGroup.FILM,
                "人像卷之王，暖调低饱和，肤色温润", "Kodak Portra 400",
            ) {
                exposure = 0.12f; contrast = 0.94f; saturation = 0.92f
                temperature = 0.16f; tint = 0.03f
                highlights = -0.10f; shadows = 0.14f; fade = 0.08f
                lift = Vec3(0.012f, 0.008f, 0.004f)
                gain = Vec3(1.02f, 1.0f, 0.965f)
                shadowTint = Vec3(-0.012f, 0.0f, 0.02f)
                highlightTint = Vec3(0.03f, 0.012f, -0.01f)
                grain = 0.20f; grainSize = 1.6f; vignette = 0.10f
                skinSoften = 0.10f; skinBrighten = 0.06f
            },
            p(
                "gold200", "柯达 Gold", FilterGroup.FILM,
                "金黄暖调的日常卷，怀旧生活感", "Kodak Gold 200",
            ) {
                exposure = 0.06f; contrast = 1.04f; saturation = 1.10f
                temperature = 0.26f; tint = 0.04f
                highlights = -0.06f; shadows = 0.06f
                gain = Vec3(1.05f, 1.0f, 0.93f)
                highlightTint = Vec3(0.05f, 0.02f, -0.03f)
                shadowTint = Vec3(0.01f, 0.005f, -0.015f)
                grain = 0.22f; grainSize = 1.8f; vignette = 0.14f
            },
            p(
                "ektar100", "柯达 Ektar", FilterGroup.FILM,
                "世界上色彩最鲜艳的负片，风景利器", "Kodak Ektar 100",
            ) {
                contrast = 1.16f; saturation = 1.28f; vibrance = 0.12f
                temperature = -0.05f
                highlights = -0.12f; shadows = -0.06f; blacks = -0.04f
                gain = Vec3(1.04f, 1.0f, 1.03f)
                grain = 0.12f; sharpen = 0.15f; vignette = 0.10f
            },
            p(
                "superia400", "富士 Superia", FilterGroup.FILM,
                "富士经典街拍卷，青绿第四层", "Fuji Superia 400",
            ) {
                exposure = 0.05f; contrast = 1.06f; saturation = 1.08f
                temperature = -0.08f; tint = -0.05f
                shadows = 0.08f
                lift = Vec3(0.0f, 0.006f, 0.012f)
                gain = Vec3(0.985f, 1.005f, 1.03f)
                shadowTint = Vec3(-0.01f, 0.005f, 0.025f)
                grain = 0.24f; grainSize = 1.7f; vignette = 0.12f
            },
            p(
                "pro400h", "富士 Pro 400H", FilterGroup.FILM,
                "日系淡雅，青调高光，婚礼常客", "Fuji Pro 400H",
            ) {
                exposure = 0.15f; contrast = 0.90f; saturation = 0.86f
                temperature = -0.06f; tint = 0.04f
                highlights = -0.08f; shadows = 0.16f; fade = 0.14f
                lift = Vec3(0.008f, 0.012f, 0.014f)
                highlightTint = Vec3(-0.015f, 0.01f, 0.025f)
                shadowTint = Vec3(0.012f, 0.006f, -0.008f)
                grain = 0.16f; soften = 0.08f
            },
            p(
                "velvia50", "富士 Velvia", FilterGroup.FILM,
                "反转片风光标准，浓烈到极致", "Fuji Velvia 50",
            ) {
                contrast = 1.22f; saturation = 1.35f; vibrance = 0.15f
                temperature = -0.04f; tint = 0.03f
                highlights = -0.14f; shadows = -0.12f; blacks = -0.06f
                gain = Vec3(1.06f, 1.0f, 1.05f)
                grain = 0.08f; sharpen = 0.20f; vignette = 0.16f
            },
            p(
                "agfavista", "Agfa Vista", FilterGroup.FILM,
                "德国老卷，独特的品红暖调", "Agfa Vista 200",
            ) {
                contrast = 1.02f; saturation = 1.06f
                temperature = 0.12f; tint = 0.10f
                shadows = 0.05f
                gain = Vec3(1.03f, 0.99f, 1.01f)
                shadowTint = Vec3(0.015f, 0.0f, 0.02f)
                highlightTint = Vec3(0.03f, 0.005f, 0.02f)
                grain = 0.22f; grainSize = 2.0f; vignette = 0.14f
            },
            p(
                "kodachrome", "柯达克罗姆", FilterGroup.FILM,
                "国家地理传奇胶卷，红黄复古", "Kodachrome 64",
            ) {
                contrast = 1.12f; saturation = 1.14f
                temperature = 0.14f; tint = 0.06f
                highlights = -0.10f; shadows = -0.08f; blacks = -0.05f
                gain = Vec3(1.06f, 1.0f, 0.94f)
                shadowTint = Vec3(0.02f, 0.01f, -0.01f)
                highlightTint = Vec3(0.04f, 0.02f, -0.02f)
                grain = 0.26f; grainSize = 1.9f; vignette = 0.20f
            },
            p(
                "cinestill800t", "CineStill", FilterGroup.FILM,
                "电影卷去掉防光晕层，霓虹红晕", "CineStill 800T",
            ) {
                exposure = 0.08f; contrast = 1.06f; saturation = 1.05f
                temperature = -0.30f; tint = 0.06f
                highlights = -0.05f; shadows = 0.10f
                lift = Vec3(0.0f, 0.004f, 0.014f)
                gain = Vec3(0.97f, 0.99f, 1.06f)
                shadowTint = Vec3(-0.01f, 0.0f, 0.03f)
                highlightTint = Vec3(0.05f, 0.0f, -0.02f)
                halation = 0.55f; bloom = 0.25f
                grain = 0.30f; grainSize = 2.2f; vignette = 0.18f
            },
            p(
                "polaroid600", "拍立得", FilterGroup.FILM,
                "即时成像的褪色感与柔光", "Polaroid 600",
            ) {
                exposure = 0.18f; contrast = 0.82f; saturation = 0.80f
                temperature = 0.10f; tint = 0.05f
                highlights = -0.05f; shadows = 0.22f; fade = 0.26f
                lift = Vec3(0.03f, 0.024f, 0.018f)
                gain = Vec3(0.98f, 0.98f, 0.94f)
                shadowTint = Vec3(0.02f, 0.01f, 0.01f)
                highlightTint = Vec3(0.01f, 0.012f, -0.005f)
                grain = 0.14f; soften = 0.18f; vignette = 0.08f
            },

            // ================= CCD =================
            p(
                "ccd_classic", "经典 CCD", FilterGroup.CCD,
                "千禧年前后的高饱和硬质感", "Sony CCD 2000",
            ) {
                contrast = 1.14f; saturation = 1.22f; vibrance = 0.08f
                temperature = 0.05f; tint = 0.03f
                highlights = -0.08f; shadows = -0.10f; blacks = -0.06f
                gain = Vec3(1.04f, 1.0f, 1.02f)
                grain = 0.30f; grainSize = 2.4f; vignette = 0.24f
                posterize = 42f; sharpen = 0.12f
            },
            p(
                "ccd_ixus", "千禧卡片机", FilterGroup.CCD,
                "佳能 IXUS 时代的轻品红柔感", "Canon IXUS",
            ) {
                exposure = 0.08f; contrast = 1.02f; saturation = 1.08f
                temperature = 0.08f; tint = 0.08f
                highlights = -0.04f; shadows = 0.08f
                gain = Vec3(1.02f, 0.995f, 1.0f)
                highlightTint = Vec3(0.03f, 0.008f, 0.015f)
                grain = 0.26f; grainSize = 2.6f; vignette = 0.20f
                posterize = 48f
            },
            p(
                "ccd_cybershot", "索尼 CCD", FilterGroup.CCD,
                "Cyber-shot 的浓郁红润", "Sony Cyber-shot",
            ) {
                contrast = 1.10f; saturation = 1.18f
                temperature = 0.14f; tint = 0.04f
                highlights = -0.10f; shadows = -0.04f
                gain = Vec3(1.06f, 0.99f, 0.98f)
                grain = 0.28f; grainSize = 2.3f; vignette = 0.22f
                posterize = 40f; sharpen = 0.16f
            },
            p(
                "ccd_casio", "卡西欧", FilterGroup.CCD,
                "冷白锐利的自拍神器风", "Casio Exilim",
            ) {
                exposure = 0.16f; contrast = 1.04f; saturation = 1.02f
                temperature = -0.12f; tint = 0.03f
                highlights = -0.02f; shadows = 0.12f; whites = 0.06f
                lift = Vec3(0.0f, 0.004f, 0.010f)
                grain = 0.20f; grainSize = 2.0f; vignette = 0.14f
                skinSoften = 0.18f; skinBrighten = 0.12f
                posterize = 52f; sharpen = 0.10f
            },
            p(
                "digicam_flash", "闪光夜拍", FilterGroup.CCD,
                "硬闪光灯 + 浓重暗角，千禧夜拍", "Flash Digicam",
            ) {
                contrast = 1.24f; saturation = 1.12f
                temperature = 0.06f
                highlights = -0.16f; shadows = -0.20f; blacks = -0.10f
                grain = 0.34f; grainSize = 2.8f; vignette = 0.42f
                posterize = 36f; chromaAberration = 0.15f
            },
            p(
                "vhs", "VHS 录像带", FilterGroup.CCD,
                "家用摄像机的扫描线与拖影色偏", "VHS Camcorder",
            ) {
                exposure = 0.05f; contrast = 0.96f; saturation = 1.14f
                temperature = -0.06f; tint = -0.08f
                shadows = 0.10f; fade = 0.10f
                lift = Vec3(0.015f, 0.0f, 0.02f)
                grain = 0.38f; grainSize = 3.2f; vignette = 0.30f
                chromaAberration = 0.35f; scanline = 0.45f
                posterize = 32f; soften = 0.10f
            },

            // ================= 黑白 =================
            p(
                "hp5", "Ilford HP5", FilterGroup.MONO,
                "英国经典新闻黑白卷，颗粒细腻", "Ilford HP5 Plus",
            ) {
                contrast = 1.10f; saturation = 0f
                highlights = -0.08f; shadows = 0.10f; blacks = -0.03f
                grain = 0.30f; grainSize = 1.9f; vignette = 0.16f
            },
            p(
                "trix400", "柯达 Tri-X", FilterGroup.MONO,
                "街头摄影图腾，高反差粗颗粒", "Kodak Tri-X 400",
            ) {
                contrast = 1.26f; saturation = 0f
                highlights = -0.12f; shadows = -0.14f; blacks = -0.08f
                grain = 0.42f; grainSize = 2.4f; vignette = 0.24f; sharpen = 0.12f
            },
            p(
                "mono_soft", "柔调黑白", FilterGroup.MONO,
                "低反差灰阶，安静克制", null,
            ) {
                contrast = 0.86f; saturation = 0f
                highlights = -0.04f; shadows = 0.18f; fade = 0.12f
                grain = 0.14f; soften = 0.08f
            },
            p(
                "mono_noir", "硬朗黑白", FilterGroup.MONO,
                "近乎木刻的极致反差", null,
            ) {
                contrast = 1.45f; saturation = 0f
                highlights = -0.16f; shadows = -0.20f; blacks = -0.12f
                grain = 0.34f; vignette = 0.28f; sharpen = 0.18f
            },
            p(
                "sepia", "棕褐怀旧", FilterGroup.MONO,
                "老照片的暖棕氧化色", null,
            ) {
                contrast = 1.02f; saturation = 0f
                shadows = 0.10f; fade = 0.10f
                shadowTint = Vec3(0.10f, 0.055f, 0.01f)
                highlightTint = Vec3(0.09f, 0.06f, 0.02f)
                grain = 0.22f; vignette = 0.26f
            },

            // ================= 创意 =================
            p(
                "doka_portrait", "Doka 人像", FilterGroup.CREATIVE,
                "对标 Doka 人像模式：肤色透亮，质感保留", "Doka Portrait",
            ) {
                exposure = 0.10f; contrast = 1.02f; saturation = 1.02f; vibrance = 0.06f
                temperature = 0.08f
                highlights = -0.08f; shadows = 0.12f
                gain = Vec3(1.02f, 1.0f, 0.985f)
                skinSoften = 0.30f; skinBrighten = 0.18f
                soften = 0.06f; grain = 0.10f
            },
            p(
                "doka_food", "Doka 食物", FilterGroup.CREATIVE,
                "提升食物质感与暖光氛围", "Doka Food",
            ) {
                exposure = 0.08f; contrast = 1.10f; saturation = 1.16f; vibrance = 0.10f
                temperature = 0.18f
                highlights = -0.10f; shadows = 0.04f
                gain = Vec3(1.04f, 1.005f, 0.95f)
                highlightTint = Vec3(0.04f, 0.015f, -0.015f)
                sharpen = 0.18f; vignette = 0.12f
            },
            p(
                "teal_orange", "青橙电影", FilterGroup.CREATIVE,
                "好莱坞调色：青色阴影 + 暖橙肤色", null,
            ) {
                contrast = 1.12f; saturation = 1.10f
                highlights = -0.10f; shadows = -0.04f
                shadowTint = Vec3(-0.03f, 0.005f, 0.03f)
                highlightTint = Vec3(0.04f, 0.015f, -0.025f)
                gain = Vec3(1.03f, 0.995f, 1.01f)
                vignette = 0.16f; grain = 0.08f
            },
            p(
                "jp_clean", "日系清透", FilterGroup.CREATIVE,
                "低饱和高明度，空气感", null,
            ) {
                exposure = 0.20f; contrast = 0.92f; saturation = 0.88f
                temperature = 0.04f; tint = 0.03f
                highlights = -0.06f; shadows = 0.20f; whites = 0.05f; fade = 0.10f
                lift = Vec3(0.012f, 0.014f, 0.012f)
                highlightTint = Vec3(-0.008f, 0.006f, 0.012f)
                soften = 0.10f; grain = 0.08f
            },
            p(
                "night_neon", "赛博霓虹", FilterGroup.CREATIVE,
                "夜景霓虹的品青对撞", null,
            ) {
                contrast = 1.14f; saturation = 1.24f
                temperature = -0.16f; tint = 0.12f
                highlights = -0.06f; shadows = -0.08f; blacks = -0.08f
                shadowTint = Vec3(-0.02f, -0.005f, 0.04f)
                highlightTint = Vec3(0.05f, -0.01f, 0.02f)
                bloom = 0.35f; halation = 0.30f
                vignette = 0.22f; grain = 0.12f
            },
        )
    }

    val byId: Map<String, FilterPreset> by lazy { ALL.associateBy { it.id } }

    fun get(id: String): FilterPreset = byId[id] ?: FilterPreset.NONE

    /** AI 推荐结果与场景的映射入口 */
    fun presetsForScene(sceneKey: String): List<FilterPreset> = when (sceneKey) {
        "portrait", "selfie" -> listOf(get("doka_portrait"), get("portra400"), get("ccd_casio"))
        "food" -> listOf(get("doka_food"), get("gold200"), get("jp_clean"))
        "landscape", "sky", "nature" -> listOf(get("ektar100"), get("velvia50"), get("teal_orange"))
        "night", "city", "building" -> listOf(get("cinestill800t"), get("night_neon"), get("vhs"))
        "street", "document" -> listOf(get("superia400"), get("trix400"), get("kodachrome"))
        "pet", "animal" -> listOf(get("ccd_classic"), get("ektar100"), get("hp5"))
        "flower", "plant" -> listOf(get("velvia50"), get("jp_clean"), get("pro400h"))
        "sea", "beach" -> listOf(get("superia400"), get("teal_orange"), get("jp_clean"))
        else -> listOf(get("portra400"), get("ccd_classic"), get("jp_clean"))
    }
}

/** FilterParams 的可读构造器 */
class FilterParamsBuilder {
    var exposure = 0f
    var contrast = 1f
    var saturation = 1f
    var vibrance = 0f
    var temperature = 0f
    var tint = 0f
    var highlights = 0f
    var shadows = 0f
    var whites = 0f
    var blacks = 0f
    var fade = 0f
    var gamma = 1f
    var lift: Vec3 = Vec3.ZERO
    var gain: Vec3 = Vec3.ONE
    var shadowTint: Vec3 = Vec3.ZERO
    var highlightTint: Vec3 = Vec3.ZERO
    var splitBalance = 0.5f
    var colorMatrix: List<Float>? = null
    var grain = 0f
    var grainSize = 1.5f
    var vignette = 0f
    var chromaAberration = 0f
    var halation = 0f
    var bloom = 0f
    var soften = 0f
    var sharpen = 0f
    var posterize = 0f
    var scanline = 0f
    var resolutionScale = 1f
    var skinSoften = 0f
    var skinBrighten = 0f

    fun toParams() = FilterParams(
        exposure, contrast, saturation, vibrance, temperature, tint,
        highlights, shadows, whites, blacks, fade, gamma,
        lift, gain, shadowTint, highlightTint, splitBalance, colorMatrix,
        grain, grainSize, vignette, chromaAberration, halation, bloom,
        soften, sharpen, posterize, scanline, resolutionScale,
        skinSoften, skinBrighten,
    )
}
