package com.dokacam.camera.data.model

import android.graphics.RectF

/** 自拍取景分类（不依赖场景：大头/半身/全身） */
enum class PoseCategory(val label: String) {
    HEAD("大头照"),
    HALF("半身照"),
    FULL("全身照"),
}

/** 机位俯仰要求 */
enum class CameraPitch(val label: String) {
    DOWN("俯拍"),
    LEVEL("平视"),
    UP("仰拍"),
    ANY("不限"),
}

/**
 * 模板骨架点。归一化坐标，y 向下；以髋部中心为原点、
 * 躯干长（肩中点→髋中点）为 1 的缩放无关坐标系。
 * [weight] 0 = 该点不参与匹配/绘制；x 正方向为画面右侧（屏幕空间）。
 */
data class PosePoint(val x: Float, val y: Float, val weight: Float = 1f)

/**
 * 自拍姿势模板：拍摄技巧 + 相机参数 + 姿势骨架。
 *
 * @param subjectArea 人物应占据的画面区域（视图归一化坐标，参考轮廓映射到这里）
 * @param zoom        推荐变焦倍率（null = 不动当前变焦）
 * @param pitch       机位俯仰要求
 * @param filterId    推荐滤镜（[com.dokacam.camera.filter.FilterPresets] 的 id，null = 不动）
 * @param skeleton    33 点模板骨架（BlazePose 索引对齐，weight=0 的点忽略）；
 *                    仅全身（FULL）类模板提供——大头/半身用脸部引导，无骨架匹配
 * @param frontCamera 模板要求前置镜头（对镜自拍等）
 * @param exposureEv  推荐 EV 补偿（0 = 不动；大头/半身小正补偿提亮肤色）
 * @param artPath     艺术线条画（SVG path，M/L/C/Q/Z 绝对坐标，0-100 视框）；每个模板必填
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
    val frontCamera: Boolean = false,
    val exposureEv: Float = 0f,
    val artPath: String,
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
