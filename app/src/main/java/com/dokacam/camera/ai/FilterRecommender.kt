package com.dokacam.camera.ai

import android.graphics.Bitmap
import com.dokacam.camera.data.model.FilterPreset
import com.dokacam.camera.filter.FilterPresets
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * AI 滤镜推荐 —— Doka 的「告别滤镜选择困难症」。
 *
 * 信号融合（全部端侧）：
 *  1. [场景] CompositionAnalyzer 给出的 sceneKey（人像/食物/风景…）
 *  2. [光线] 帧平均亮度 + 对比（夜景优先霓虹/胶片夜拍）
 *  3. [色彩] 帧平均色温（暖场景避开冷调滤镜，避免撞色）
 *
 * 输出 Top-3，UI 以「⭐ 推荐角标」呈现，用户一键切换。
 */
object FilterRecommender {

    data class Recommendation(
        val preset: FilterPreset,
        val reason: String,
        val score: Float,
    )

    /** 帧色彩统计（缩到 32x32 后计算，成本忽略不计） */
    data class FrameStats(
        val avgLuma: Float,
        val contrast: Float,
        val warmth: Float,   // >0 偏暖
        val saturation: Float,
    )

    fun analyzeFrame(frame: Bitmap): FrameStats {
        val tiny = Bitmap.createScaledBitmap(frame, 32, 32, true)
        var sumL = 0.0; var sumL2 = 0.0; var sumR = 0.0; var sumB = 0.0; var sumSat = 0.0
        val n = tiny.width * tiny.height
        val px = IntArray(n)
        tiny.getPixels(px, 0, tiny.width, 0, 0, tiny.width, tiny.height)
        for (c in px) {
            val r = (c shr 16 and 0xFF) / 255f
            val g = (c shr 8 and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            sumL += l; sumL2 += l * l.toDouble()
            sumR += r; sumB += b
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
            sumSat += if (mx > 0) (mx - mn) / mx else 0f
        }
        tiny.recycle()
        val avgL = (sumL / n).toFloat()
        val variance = (sumL2 / n - (sumL / n) * (sumL / n)).toFloat().coerceAtLeast(0f)
        return FrameStats(
            avgLuma = avgL,
            contrast = sqrt(variance),
            warmth = ((sumR - sumB) / n).toFloat(),
            saturation = (sumSat / n).toFloat(),
        )
    }

    fun recommend(sceneKey: String, stats: FrameStats): List<Recommendation> {
        val candidates = FilterPresets.presetsForScene(sceneKey).toMutableList()
        val scored = candidates.map { preset ->
            var score = 60f
            val sb = StringBuilder()

            // ---- 光线匹配 ----
            val isNight = stats.avgLuma < 0.28f
            when {
                isNight && preset.id in setOf("cinestill800t", "night_neon", "vhs") -> {
                    score += 25; sb.append("夜景适配 ")
                }
                !isNight && preset.id in setOf("cinestill800t", "night_neon") -> score -= 20
                isNight && preset.id in setOf("velvia50", "ektar100") -> score -= 15
                stats.avgLuma > 0.72f && preset.params.highlights < -0.08f -> {
                    score += 12; sb.append("高光保护 ")
                }
                stats.avgLuma < 0.4f && preset.params.shadows > 0.08f -> {
                    score += 10; sb.append("暗部提亮 ")
                }
            }

            // ---- 色温撞色规避 ----
            if (stats.warmth > 0.08f && preset.params.temperature < -0.1f) score -= 18
            if (stats.warmth < -0.08f && preset.params.temperature > 0.15f) score -= 10
            if (abs(stats.warmth) < 0.05f && abs(preset.params.temperature) < 0.1f) {
                score += 8; sb.append("色温和谐 ")
            }

            // ---- 低饱和场景适合浓艳滤镜，反之亦然 ----
            if (stats.saturation < 0.18f && preset.params.saturation > 1.1f) {
                score += 10; sb.append("增强色彩 ")
            }
            if (stats.saturation > 0.45f && preset.params.saturation < 0.92f) {
                score += 8; sb.append("降低溢出 ")
            }

            // ---- 人像场景检查肤色保护 ----
            if (sceneKey == "portrait" && preset.params.skinSoften > 0f) {
                score += 15; sb.append("肤色优化 ")
            }

            if (sb.isEmpty()) sb.append("经典适配")

            Recommendation(preset, sb.toString().trim(), score)
        }
        return scored.sortedByDescending { it.score }.take(3)
    }
}
