package com.dokacam.camera.ai

import android.graphics.Bitmap
import android.graphics.RectF
import com.dokacam.camera.data.model.GuideDirection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 构图分析结果（归一化坐标 0..1）
 */
data class CompositionResult(
    val score: Int,                    // 0..100
    val subjects: List<RectF>,         // 检测到的主体包围盒
    val faces: List<RectF>,            // 人脸（用于优先引导）
    val directions: List<GuideDirection>,
    val advice: String,                // 一句话建议
    val sceneKey: String,              // 场景标签（供滤镜推荐）
    val isPortrait: Boolean,
)

/**
 * AI 构图引导 —— Doka 的核心卖点。
 *
 * 算法（全部端侧、离线）：
 *  1. ML Kit 检测主体（物体 + 人脸）
 *  2. 取最大主体计算构图评分：
 *     - 三分法：主体中心离最近三分交点的距离
 *     - 视线空间：人脸朝向侧预留空间
 *     - 大小适中：主体占比 25%~60% 最佳
 *  3. 生成方向性引导（左移/右移/靠近/后退/端平）
 *  4. 简单场景分类（人像/食物/风景/夜景…）给滤镜推荐用
 */
class CompositionAnalyzer {

    private val objectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build(),
        )
    }

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.12f)
                .build(),
        )
    }

    /**
     * 分析一帧。bitmap 会被缩放到长边 640（AI 不需要全分辨率）。
     */
    suspend fun analyze(frame: Bitmap): CompositionResult {
        val small = downscale(frame, 640)

        val objects = detectObjects(small)
        val faces = detectFaces(small)

        // ML Kit 返回的坐标基于 small，归一化
        val objBoxes = objects.map { normalize(it, small.width, small.height) }
        val faceBoxes = faces.map { normalize(it, small.width, small.height) }

        if (small !== frame) small.recycle()

        val subjects = (faceBoxes + objBoxes)
        val primary = subjects.maxByOrNull { it.width() * it.height() }

        return if (primary == null) {
            CompositionResult(
                score = 60, subjects = emptyList(), faces = faceBoxes,
                directions = emptyList(),
                advice = "未检测到明显主体，试试靠近一点",
                sceneKey = classifyScene(null, objBoxes, faceBoxes),
                isPortrait = faceBoxes.isNotEmpty(),
            )
        } else {
            val (score, dirs) = evaluateComposition(primary, faceBoxes)
            CompositionResult(
                score = score,
                subjects = subjects,
                faces = faceBoxes,
                directions = dirs,
                advice = adviceFor(dirs, score),
                sceneKey = classifyScene(primary, objBoxes, faceBoxes),
                isPortrait = faceBoxes.isNotEmpty(),
            )
        }
    }

    // ------------------------------------------------------------------

    private suspend fun detectObjects(bmp: Bitmap): List<RectF> =
        suspendCancellableCoroutine { cont ->
            objectDetector.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { list -> cont.resume(list.map { android.graphics.RectF(it.boundingBox) }) }
                .addOnFailureListener { cont.resume(emptyList()) } // 失败降级，不阻塞取景
        }

    private suspend fun detectFaces(bmp: Bitmap): List<RectF> =
        suspendCancellableCoroutine { cont ->
            faceDetector.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { list -> cont.resume(list.map { android.graphics.RectF(it.boundingBox) }) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

    /**
     * 构图评分。
     * 返回 (分数, 引导方向列表)
     */
    private fun evaluateComposition(
        subject: RectF,
        faces: List<RectF>,
    ): Pair<Int, List<GuideDirection>> {
        val cx = subject.centerX()
        val cy = subject.centerY()
        val dirs = mutableListOf<GuideDirection>()

        // ---- 三分法 ----
        val thirdsX = floatArrayOf(1f / 3f, 2f / 3f)
        val thirdsY = floatArrayOf(1f / 3f, 2f / 3f)
        val nearestX = thirdsX.minBy { abs(it - cx) }
        val nearestY = thirdsY.minBy { abs(it - cy) }
        val dx = cx - nearestX
        val dy = cy - nearestY
        val dist = sqrt(dx * dx + dy * dy)

        val thirdsScore = when {
            dist < 0.06f -> 100f
            dist < 0.12f -> 85f
            dist < 0.20f -> 65f
            dist < 0.30f -> 45f
            else -> 30f
        }

        // 中心构图对单体/对称场景也 OK，给部分分
        val centerDist = sqrt(
            (cx - 0.5f) * (cx - 0.5f) + (cy - 0.5f) * (cy - 0.5f),
        )
        val centerBonus = when {
            centerDist < 0.05f -> 25f
            centerDist < 0.10f -> 12f
            else -> 0f
        }

        // ---- 主体大小 ----
        val area = subject.width() * subject.height()
        val sizeScore = when {
            area < 0.04f -> 35f
            area < 0.10f -> 55f
            area <= 0.55f -> 95f
            area > 0.85f -> 50f
            else -> 80f
        }

        // ---- 视线空间（人脸优先） ----
        var gazeScore = 70f
        if (faces.isNotEmpty()) {
            val f = faces.first()
            val fcx = f.centerX()
            gazeScore = when {
                fcx > 0.30f && fcx < 0.70f -> 80f
                fcx <= 0.30f -> 65f
                else -> 65f
            }
        }

        val score = (
            thirdsScore * 0.45f +
                maxOf(centerBonus, 0f) * 0.10f +
                sizeScore * 0.30f +
                gazeScore * 0.15f
            ).toInt().coerceIn(0, 100)

        // ---- 引导方向 ----
        if (abs(dx) > 0.10f) dirs +=
            if (dx > 0) GuideDirection.MOVE_LEFT else GuideDirection.MOVE_RIGHT
        if (abs(dy) > 0.12f) dirs +=
            if (dy > 0) GuideDirection.MOVE_UP else GuideDirection.MOVE_DOWN
        if (area < 0.08f) dirs += GuideDirection.ZOOM_IN
        if (area > 0.80f) dirs += GuideDirection.ZOOM_OUT
        if (dirs.isEmpty() && score >= 80) dirs += GuideDirection.HOLD_STEADY

        return score to dirs
    }

    /** 场景分类（简单启发式，够用且完全离线） */
    private fun classifyScene(
        primary: RectF?,
        objects: List<RectF>,
        faces: List<RectF>,
    ): String = when {
        faces.isNotEmpty() && (primary == null || faces.any { it == primary }) -> "portrait"
        primary == null -> "general"
        else -> {
            val area = primary.width() * primary.height()
            when {
                area > 0.55f -> "landscape"
                area in 0.18f..0.50f && primary.centerY() > 0.55f -> "food"
                else -> "general"
            }
        }
    }

    private fun adviceFor(dirs: List<GuideDirection>, score: Int): String = when {
        dirs.isEmpty() -> "构图不错，保持"
        dirs.contains(GuideDirection.ZOOM_IN) -> "主体太小，靠近或放大"
        dirs.contains(GuideDirection.ZOOM_OUT) -> "主体太满，后退一点"
        dirs.contains(GuideDirection.MOVE_LEFT) -> "相机向左移"
        dirs.contains(GuideDirection.MOVE_RIGHT) -> "相机向右移"
        dirs.contains(GuideDirection.MOVE_UP) -> "镜头稍微抬高"
        dirs.contains(GuideDirection.MOVE_DOWN) -> "镜头稍微压低"
        else -> "微调取景即可"
    }

    private fun normalize(r: RectF, w: Int, h: Int) = RectF(
        r.left / w, r.top / h, r.right / w, r.bottom / h,
    )

    private fun downscale(src: Bitmap, maxDim: Int): Bitmap {
        val m = max(src.width, src.height)
        if (m <= maxDim) return src
        val s = maxDim.toFloat() / m
        return Bitmap.createScaledBitmap(
            src, (src.width * s).toInt(), (src.height * s).toInt(), true,
        )
    }
}
