package com.dokacam.camera.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.dokacam.camera.data.model.GuideDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * 一键出片的「拍摄教练」状态。
 *
 * score/advice/directions —— 构图引导（三分法、主体大小）
 * suggestedZoom           —— 焦距建议（主体太小 → 拉近，太满 → 放宽）
 * exposureTip             —— 曝光/用光建议（偏暗开闪光、过强点按测光）
 * sceneKey / sceneLabel   —— 场景识别（人像/美食/风景）
 * ready                   —— 构图到位，可以按快门
 */
data class ShotAdvice(
    val score: Int = 0,
    val advice: String = "",
    val directions: List<GuideDirection> = emptyList(),
    val subjects: List<RectF> = emptyList(),
    val faces: List<RectF> = emptyList(),
    val sceneKey: String = "general",
    val sceneLabel: String? = null,
    val isPortrait: Boolean = false,
    val suggestedZoom: Float? = null,
    val exposureTip: String? = null,
    val ready: Boolean = false,
)

/**
 * AI 拍摄教练 —— 让小白也能拍出神级照片。
 *
 * 全端侧、离线。信号融合：
 *  1. MediaPipe 主体/人脸检测 → 构图评分 + 方向引导（[CompositionAnalyzer]）
 *  2. 帧亮度/对比/色温统计 → 曝光用光建议（[FilterRecommender.analyzeFrame]）
 *  3. 主体占幅 → 焦距（变焦）建议，换算 35mm 等效焦距展示
 *
 * 节流策略：最新帧优先（conflated channel），分析中来的新帧直接替换，
 * 绝不排队堆积，也绝不抢预览帧率。
 */
class ShotCoach(context: Context, private val scope: CoroutineScope) {

    private val analyzer = CompositionAnalyzer(context)
    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val _advice = MutableStateFlow(ShotAdvice())
    val advice: StateFlow<ShotAdvice> = _advice.asStateFlow()

    private val analysisJob: Job
    @Volatile private var closed = false

    init {
        analysisJob = scope.launch(Dispatchers.Default) {
            for (ignored in signal) {
                val frame = latestFrame.getAndSet(null) ?: continue
                try {
                    _advice.value = analyze(frame)
                } catch (t: Throwable) {
                    // 分析失败静默降级，不打断取景
                } finally {
                    // getAndSet(null) 后本帧归分析协程独占，submit 不会再碰它
                    frame.recycle()
                }
            }
        }
    }

    /** 提交一帧（由 ImageAnalysis 回调）。旧帧直接丢弃回收。 */
    fun submit(frame: Bitmap) {
        if (closed) { frame.recycle(); return } // 关闭竞态窗口里送达的帧直接回收
        latestFrame.getAndSet(frame)?.recycle()
        signal.trySend(Unit)
    }

    fun clear() {
        latestFrame.getAndSet(null)?.recycle()
        _advice.value = ShotAdvice()
    }

    /** 释放 MediaPipe 引擎。等在途分析收尾后再关，避免 use-after-close。 */
    fun close() {
        closed = true
        clear()
        analysisJob.invokeOnCompletion { analyzer.close() }
        analysisJob.cancel()
    }

    // ------------------------------------------------------------------

    private suspend fun analyze(frame: Bitmap): ShotAdvice {
        val comp = analyzer.analyze(frame)
        val stats = FilterRecommender.analyzeFrame(frame)

        val area = comp.subjects.maxOfOrNull { it.width() * it.height() } ?: 0f
        val zoom = zoomFor(area)
        val exposure = exposureFor(stats.avgLuma, stats.contrast)
        // 无引导方向或仅「端稳」即视为构图到位
        val ready = comp.score >= 80 && comp.directions.all { it == GuideDirection.HOLD_STEADY }

        return ShotAdvice(
            score = comp.score,
            advice = comp.advice,
            directions = comp.directions,
            subjects = comp.subjects,
            faces = comp.faces,
            sceneKey = comp.sceneKey,
            sceneLabel = sceneLabel(comp.sceneKey),
            isPortrait = comp.isPortrait,
            suggestedZoom = zoom,
            exposureTip = exposure,
            ready = ready,
        )
    }

    /** 主体占幅 → 建议变焦倍率。null = 保持当前。 */
    private fun zoomFor(area: Float): Float? = when {
        area == 0f -> null
        area < 0.03f -> 3f
        area < 0.07f -> 2f
        area < 0.12f -> 1.5f
        area > 0.85f -> 0.6f
        else -> null
    }

    /** 帧亮度 → 用光建议。null = 光线合适。 */
    private fun exposureFor(luma: Float, contrast: Float): String? = when {
        luma < 0.20f -> "光线偏暗，建议开闪光或手电"
        luma < 0.32f -> "光线略暗，靠近光源或开手电"
        luma > 0.82f -> "光线过强，点按亮部测光压高光"
        contrast < 0.06f -> "画面偏灰，换个角度避开雾光"
        else -> null
    }

    private fun sceneLabel(key: String): String? = when (key) {
        "portrait" -> "人像"
        "food" -> "美食"
        "landscape" -> "风景"
        "pet" -> "萌宠"
        else -> null
    }

    companion object {
        /** 1x ≈ 26mm 等效焦距（手机主摄典型值），用于「焦距」展示 */
        fun equivMm(zoom: Float): Int = (26f * zoom).toInt()
    }
}
