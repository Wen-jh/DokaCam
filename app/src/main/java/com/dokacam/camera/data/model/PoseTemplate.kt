package com.dokacam.camera.data.model

import android.graphics.RectF

/** 拍摄场景分类（网红模板分组展示用） */
enum class PoseCategory(val label: String) {
    STREET("街拍"),
    SHOP("探店"),
    OUTDOOR("户外"),
    HOME("居家"),
    NIGHT("夜景"),
    SELFIE("自拍"),
}

/** 机位俯仰要求 */
enum class CameraPitch(val label: String) {
    DOWN("俯拍"),
    LEVEL("平视"),
    UP("仰拍"),
    ANY("不限"),
}

/** 轮廓与场景锚定目标的对齐方式 */
enum class AnchorAlign {
    /** 坐姿：髋线（canonical y=0）贴检测框顶部约 1/5 处，轮廓宽=框宽×1.2 */
    SIT_ON,
    /** 站姿：脚底（canonical 最大 y）贴检测框底部，人比框高 */
    STAND_BEHIND,
    /** 倚靠：髋部贴检测框顶部中线，轮廓宽≈框宽 */
    LEAN_ON,
}

/**
 * 模板骨架点。归一化坐标，y 向下；以髋部中心为原点、
 * 躯干长（肩中点→髋中点）为 1 的缩放无关坐标系。
 * [weight] 0 = 该点不参与匹配/绘制；x 正方向为画面右侧（屏幕空间）。
 */
data class PosePoint(val x: Float, val y: Float, val weight: Float = 1f)

/**
 * 网红人像拍摄模板：拍摄技巧 + 相机参数 + 姿势骨架。
 *
 * @param subjectArea 人物应占据的画面区域（视图归一化坐标，无锚定目标时轮廓映射到这里）
 * @param zoom        推荐变焦倍率（null = 不动当前变焦）
 * @param filterId    推荐滤镜（[com.dokacam.camera.filter.FilterPresets] 的 id，null = 不动）
 * @param skeleton    33 点模板骨架（BlazePose 索引对齐，weight=0 的点忽略）；null = 无骨架参考
 * @param anchorLabel 场景锚定目标的 COCO 类名（"chair"/"couch"/"bench" 等，null=不锚定）；
 *                    检测到该类目标时轮廓改贴检测框，检测不到时淡显在 subjectArea 并提示对准
 */
data class PoseTemplate(
    val id: String,
    val name: String,
    val category: PoseCategory,
    val tips: List<String>,
    val zoom: Float?,
    val pitch: CameraPitch,
    val subjectArea: RectF,
    val filterId: String?,
    val skeleton: List<PosePoint>? = null,
    val anchorLabel: String? = null,
    val anchorAlign: AnchorAlign = AnchorAlign.SIT_ON,
    /** 艺术线条画（SVG path，M/L/C/Q/Z 绝对坐标，0-100 视框）；空串=无示例图 */
    val artPath: String = "",
)

/** BlazePose 33 点索引常量（奇数=左，偶数=右，以被摄者自身为准） */
object PoseIdx {
    const val NOSE = 0
    const val EAR_L = 7
    const val EAR_R = 8
    const val SHOULDER_L = 11
    const val SHOULDER_R = 12
    const val ELBOW_L = 13
    const val ELBOW_R = 14
    const val WRIST_L = 15
    const val WRIST_R = 16
    const val HIP_L = 23
    const val HIP_R = 24
    const val KNEE_L = 25
    const val KNEE_R = 26
    const val ANKLE_L = 27
    const val ANKLE_R = 28
    const val HEEL_L = 29
    const val HEEL_R = 30
    const val TOE_L = 31
    const val TOE_R = 32
}

/** 骨架连线（绘制模板/实时骨架共用，只画躯干与四肢主干） */
val POSE_BONES: List<Pair<Int, Int>> = listOf(
    PoseIdx.SHOULDER_L to PoseIdx.SHOULDER_R,
    PoseIdx.SHOULDER_L to PoseIdx.ELBOW_L,
    PoseIdx.ELBOW_L to PoseIdx.WRIST_L,
    PoseIdx.SHOULDER_R to PoseIdx.ELBOW_R,
    PoseIdx.ELBOW_R to PoseIdx.WRIST_R,
    PoseIdx.SHOULDER_L to PoseIdx.HIP_L,
    PoseIdx.SHOULDER_R to PoseIdx.HIP_R,
    PoseIdx.HIP_L to PoseIdx.HIP_R,
    PoseIdx.HIP_L to PoseIdx.KNEE_L,
    PoseIdx.KNEE_L to PoseIdx.ANKLE_L,
    PoseIdx.HIP_R to PoseIdx.KNEE_R,
    PoseIdx.KNEE_R to PoseIdx.ANKLE_R,
    PoseIdx.ANKLE_L to PoseIdx.TOE_L,
    PoseIdx.ANKLE_R to PoseIdx.TOE_R,
)
