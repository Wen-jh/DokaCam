package com.dokacam.camera.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.dokacam.camera.data.model.GuideDirection
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.abs
import kotlin.math.sqrt

/** 单个姿态关键点（归一化 0..1，分析帧坐标系，y 向下） */
data class PoseLandmark(val x: Float, val y: Float, val visibility: Float)

/** 一帧的姿态检测结果：33 关键点 + 帧宽高比（像素空间距离计算用） */
data class PoseFrame(val landmarks: List<PoseLandmark>, val aspect: Float)

/**
 * 构图分析结果（归一化坐标 0..1）
 */
data class CompositionResult(
    val score: Int,                    // 0..100
    val subjects: List<RectF>,         // 检测到的主体包围盒
    val faces: List<RectF>,            // 人脸（用于优先引导）
    val directions: List<GuideDirection>,
    val advice: String,                // 一句话建议
    val sceneKey: String,              // 场景标签（供教练卡展示）
    val isPortrait: Boolean,
    /** 带 COCO 标签的完整物体检测（供姿势模板场景锚定按类名取框） */
    val objects: List<ObjectHit> = emptyList(),
)

/** 物体检测结果：归一化框 + COCO 类别名 */
data class ObjectHit(val box: RectF, val label: String, val score: Float)

/**
 * AI 构图分析 —— MediaPipe 端侧引擎（全离线、TFLite）。
 *
 * 检测：
 *  1. Face Landmarker（blazeface + 478 关键点）—— 由关键点反推人脸框
 *  2. Object Detector（EfficientDet-Lite0，COCO 80 类）—— 人物/物体主体框
 *
 * 构图评分：
 *  - 三分法：主体中心离最近三分交点的距离
 *  - 视线空间：人脸位置留白
 *  - 大小适中：主体占比 25%~60% 最佳
 *
 * 输出方向性引导（左移/右移/靠近/后退）供 UI 叠加提示。
 * 引擎初始化失败时静默降级为「无主体」，绝不阻塞取景。
 */
class CompositionAnalyzer(context: Context) {

    private val appContext = context.applicationContext
    private val engineLock = Any()

    /** 模型位于 assets/；初始化失败返回 null 并永久降级，不逐帧重试 */
    @Volatile private var faceEngine: FaceLandmarker? = null
    @Volatile private var objectEngine: ObjectDetector? = null
    @Volatile private var poseEngine: PoseLandmarker? = null
    @Volatile private var faceEngineFailed = false
    @Volatile private var objectEngineFailed = false
    @Volatile private var poseEngineFailed = false
    /** GPU 推理运行期失败时置 true，下次起回退 CPU */
    @Volatile private var poseGpuFailed = false
    /** 姿态推理运行期连续失败计数：限频告警；CPU 持续失败则永久降级 */
    @Volatile private var poseFailStreak = 0

    private fun faceEngineOrNull(): FaceLandmarker? {
        if (faceEngineFailed) return null
        faceEngine?.let { return it }
        synchronized(engineLock) {
            if (faceEngineFailed) return null
            faceEngine?.let { return it }
            return try {
                FaceLandmarker.createFromOptions(
                    appContext,
                    FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
                        )
                        .setNumFaces(3)
                        .setMinFaceDetectionConfidence(0.3f)
                        .setMinFacePresenceConfidence(0.3f)
                        .build(),
                )
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "FaceLandmarker init failed: ${t.message}")
                faceEngineFailed = true
                null
            }.also { faceEngine = it }
        }
    }

    private fun objectEngineOrNull(): ObjectDetector? {
        if (objectEngineFailed) return null
        objectEngine?.let { return it }
        synchronized(engineLock) {
            if (objectEngineFailed) return null
            objectEngine?.let { return it }
            return try {
                ObjectDetector.createFromOptions(
                    appContext,
                    ObjectDetector.ObjectDetectorOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build()
                        )
                        .setScoreThreshold(0.35f)
                        .setMaxResults(4)
                        .build(),
                )
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "ObjectDetector init failed: ${t.message}")
                objectEngineFailed = true
                null
            }.also { objectEngine = it }
        }
    }

    /**
     * 姿态引擎懒加载（与 faceEngineOrNull 同模式：失败永久降级，不逐帧重试）。
     * 优先 GPU delegate，初始化失败即回退 CPU。
     */
    private fun poseEngineOrNull(): PoseLandmarker? {
        if (poseEngineFailed) return null
        poseEngine?.let { return it }
        synchronized(engineLock) {
            if (poseEngineFailed) return null
            poseEngine?.let { return it }
            return try {
                createPoseEngine(if (poseGpuFailed) Delegate.CPU else Delegate.GPU)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "PoseLandmarker init failed (gpu=$poseGpuFailed): ${t.message}")
                if (!poseGpuFailed) {
                    poseGpuFailed = true
                    try {
                        createPoseEngine(Delegate.CPU)
                    } catch (t2: Throwable) {
                        android.util.Log.w(TAG, "PoseLandmarker CPU init failed: ${t2.message}")
                        poseEngineFailed = true
                        null
                    }
                } else {
                    poseEngineFailed = true
                    null
                }
            }.also { poseEngine = it }
        }
    }

    private fun createPoseEngine(delegate: Delegate): PoseLandmarker =
        PoseLandmarker.createFromOptions(
            appContext,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("pose_landmarker_lite.task")
                        .setDelegate(delegate)
                        .build()
                )
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.3f)
                .setMinPosePresenceConfidence(0.3f)
                .setMinTrackingConfidence(0.3f)
                .build(),
        )

    fun close() {
        runCatching { faceEngine?.close() }
        runCatching { objectEngine?.close() }
        runCatching { poseEngine?.close() }
    }

    /**
     * 检测一帧中的人物姿态（仅模板模式调用，不在常规构图分析里跑）。
     * 返回主人物的 33 归一化关键点；未检出或引擎不可用时返回 null。
     * GPU 推理运行期抛错 → 关闭引擎并标记回退，下次重建 CPU 实例；
     * CPU 连续失败 100 帧 → 永久降级（同初始化失败），不再逐帧重试。
     */
    fun detectPose(frame: Bitmap): PoseFrame? {
        val engine = poseEngineOrNull() ?: return null
        return try {
            val image: MPImage = BitmapImageBuilder(frame).build()
            val pose = engine.detect(image).landmarks().firstOrNull() ?: return null
            if (pose.size < 33) return null
            poseFailStreak = 0
            PoseFrame(
                landmarks = pose.map {
                    PoseLandmark(it.x(), it.y(), it.visibility().orElse(0f))
                },
                aspect = frame.width.toFloat() / frame.height.toFloat(),
            )
        } catch (t: Throwable) {
            poseFailStreak++
            // 限频告警：首次 + 每 30 次一条，避免逐帧刷日志
            if (poseFailStreak == 1 || poseFailStreak % 30 == 0) {
                android.util.Log.w(TAG, "pose detect failed x$poseFailStreak: ${t.message}")
            }
            if (!poseGpuFailed) {
                // 可能是 GPU delegate 运行期故障：降级 CPU 重建
                poseGpuFailed = true
                synchronized(engineLock) {
                    runCatching { poseEngine?.close() }
                    poseEngine = null
                }
            } else if (poseFailStreak >= 100) {
                // CPU 持续失败：与初始化失败同样处理，永久降级不再逐帧重试
                poseEngineFailed = true
                synchronized(engineLock) {
                    runCatching { poseEngine?.close() }
                    poseEngine = null
                }
            }
            null
        }
    }

    /** 分析一帧（输入已由 AnalysisUtils 旋转到显示方向、缩到 ≤480px）。 */
    fun analyze(frame: Bitmap): CompositionResult {
        val faces = detectFaces(frame)
        val objects = detectObjects(frame)
        val objectBoxes = objects.map { it.box }
        val labels = objects.map { it.label }

        val subjects = (faces + objectBoxes)
        val primary = subjects.maxByOrNull { it.width() * it.height() }

        return if (primary == null) {
            CompositionResult(
                score = 60, subjects = emptyList(), faces = faces,
                directions = emptyList(),
                advice = "未检测到明显主体，试试靠近一点",
                sceneKey = classifyScene(null, labels, faces),
                isPortrait = faces.isNotEmpty(),
                objects = objects,
            )
        } else {
            val (score, dirs) = evaluateComposition(primary, faces)
            CompositionResult(
                score = score,
                subjects = subjects,
                faces = faces,
                directions = dirs,
                advice = adviceFor(dirs, score),
                sceneKey = classifyScene(primary, labels, faces),
                isPortrait = faces.isNotEmpty(),
                objects = objects,
            )
        }
    }

    // ------------------------------------------------------------------

    /** 人脸框：由 478 关键点 min/max 外扩得到（归一化坐标） */
    private fun detectFaces(frame: Bitmap): List<RectF> {
        val engine = faceEngineOrNull() ?: return emptyList()
        return try {
            val image: MPImage = BitmapImageBuilder(frame).build()
            engine.detect(image).faceLandmarks().mapNotNull { pts ->
                if (pts.isEmpty()) return@mapNotNull null
                var l = 1f; var t = 1f; var r = 0f; var b = 0f
                for (p in pts) {
                    if (p.x() < l) l = p.x()
                    if (p.x() > r) r = p.x()
                    if (p.y() < t) t = p.y()
                    if (p.y() > b) b = p.y()
                }
                // 关键点只覆盖五官，外扩成完整头框
                val padX = (r - l) * 0.18f
                val padY = (b - t) * 0.25f
                RectF(
                    (l - padX).coerceAtLeast(0f), (t - padY).coerceAtLeast(0f),
                    (r + padX).coerceAtMost(1f), (b + padY).coerceAtMost(1f),
                )
            }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "face detect: ${t.message}")
            emptyList()
        }
    }

    /** 物体框：EfficientDet 像素坐标 → 归一化 */
    private fun detectObjects(frame: Bitmap): List<ObjectHit> {
        val engine = objectEngineOrNull() ?: return emptyList()
        return try {
            val image: MPImage = BitmapImageBuilder(frame).build()
            val w = frame.width.toFloat()
            val h = frame.height.toFloat()
            engine.detect(image).detections().mapNotNull { d ->
                val box = d.boundingBox() ?: return@mapNotNull null
                val cat = d.categories().firstOrNull() ?: return@mapNotNull null
                if (box.width() <= 0f || box.height() <= 0f) return@mapNotNull null
                ObjectHit(
                    box = RectF(box.left / w, box.top / h, box.right / w, box.bottom / h),
                    label = cat.categoryName()?.lowercase().orEmpty(),
                    score = cat.score(),
                )
            }.filter { it.box.width() * it.box.height() < 0.98f } // 满框误检丢弃
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "object detect: ${t.message}")
            emptyList()
        }
    }

    /**
     * 构图评分。返回 (分数, 引导方向列表)
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
                else -> 65f
            }
        }

        val score = (
            thirdsScore * 0.45f +
                maxOf(centerBonus, 0f) * 0.10f +
                sizeScore * 0.30f +
                gazeScore * 0.15f
            ).toInt().coerceIn(0, 100)

        // ---- 引导方向（相机运动语义：dx>0=主体在线右 → 右摇把主体拉回线左）----
        if (abs(dx) > 0.10f) dirs +=
            if (dx > 0) GuideDirection.MOVE_RIGHT else GuideDirection.MOVE_LEFT
        if (abs(dy) > 0.12f) dirs +=
            if (dy > 0) GuideDirection.MOVE_DOWN else GuideDirection.MOVE_UP
        if (area < 0.08f) dirs += GuideDirection.ZOOM_IN
        if (area > 0.80f) dirs += GuideDirection.ZOOM_OUT
        if (dirs.isEmpty() && score >= 80) dirs += GuideDirection.HOLD_STEADY

        return score to dirs
    }

    /** 场景分类：COCO 标签优先，几何启发式兜底 */
    private fun classifyScene(primary: RectF?, labels: List<String>, faces: List<RectF>): String = when {
        faces.isNotEmpty() -> "portrait"
        primary == null -> "general"
        labels.any { it in ANIMALS } -> "pet"
        labels.any { it in FOOD } -> "food"
        primary.width() * primary.height() > 0.55f -> "landscape"
        else -> "general"
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

    companion object {
        private const val TAG = "CompositionAnalyzer"

        /** COCO 80 类中的食物/动物子集，用于场景识别 */
        private val FOOD = setOf(
            "banana", "apple", "sandwich", "orange", "broccoli", "carrot",
            "hot dog", "pizza", "donut", "cake", "bowl",
        )
        private val ANIMALS = setOf(
            "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe",
        )
    }
}
