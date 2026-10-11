package com.dokacam.camera.data.model

import android.graphics.RectF

/**
 * 内置网红人像拍摄模板库 —— 15 个经典人像构图（通用摄影知识整理，原创内容）。
 *
 * 骨架（skeleton）为 33 点列表，与 BlazePose 索引对齐；
 * 坐标以髋部中心为原点、躯干长为 1（y 向下，x 为画面右向，屏幕空间），
 * weight=0 的点不参与匹配与绘制。8 个模板带参数化骨架，其余纯技巧引导。
 */
object PoseTemplates {

    private fun pp(x: Float, y: Float, w: Float = 1f) = PosePoint(x, y, w)

    /** 由稀疏关键点构造 33 点骨架，未给定的点 weight=0 */
    private fun skeleton(vararg pts: Pair<Int, PosePoint>): List<PosePoint> {
        val arr = Array(33) { PosePoint(0f, 0f, 0f) }
        for ((i, p) in pts) arr[i] = p
        return arr.toList()
    }

    // ---------------- 骨架原型（髋中心原点，躯干长=1，y 向下） ----------------

    /** 正面站姿：一脚稍前跨，手臂自然下垂 */
    private val SK_STAND_TALL = skeleton(
        PoseIdx.NOSE to pp(0f, -1.42f, 0.5f),
        PoseIdx.EAR_L to pp(-0.15f, -1.40f, 0.3f),
        PoseIdx.EAR_R to pp(0.15f, -1.40f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.32f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.32f, -1.0f),
        PoseIdx.ELBOW_L to pp(-0.36f, -0.48f),
        PoseIdx.ELBOW_R to pp(0.36f, -0.48f),
        PoseIdx.WRIST_L to pp(-0.34f, 0.04f),
        PoseIdx.WRIST_R to pp(0.34f, 0.04f),
        PoseIdx.HIP_L to pp(-0.20f, 0f),
        PoseIdx.HIP_R to pp(0.20f, 0f),
        PoseIdx.KNEE_L to pp(-0.20f, 1.0f),
        PoseIdx.KNEE_R to pp(0.24f, 0.96f),
        PoseIdx.ANKLE_L to pp(-0.20f, 1.92f),
        PoseIdx.ANKLE_R to pp(0.30f, 1.84f),
        PoseIdx.TOE_L to pp(-0.20f, 2.0f, 0.5f),
        PoseIdx.TOE_R to pp(0.38f, 1.92f, 0.5f),
    )

    /** 侧坐：身体朝画面左侧，一臂前伸（搭桌面） */
    private val SK_CAFE_SIT = skeleton(
        PoseIdx.NOSE to pp(-0.15f, -1.38f, 0.5f),
        PoseIdx.EAR_L to pp(-0.02f, -1.38f, 0.3f),
        PoseIdx.EAR_R to pp(0.06f, -1.36f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.12f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.02f, -0.98f),
        PoseIdx.ELBOW_L to pp(-0.18f, -0.52f),
        PoseIdx.WRIST_L to pp(-0.06f, -0.06f),
        PoseIdx.ELBOW_R to pp(0.06f, -0.50f),
        PoseIdx.WRIST_R to pp(0.40f, -0.46f),
        PoseIdx.HIP_L to pp(-0.05f, 0f),
        PoseIdx.HIP_R to pp(0.05f, 0f),
        PoseIdx.KNEE_L to pp(0.55f, 0.02f),
        PoseIdx.KNEE_R to pp(0.64f, 0.10f),
        PoseIdx.ANKLE_L to pp(0.48f, 0.92f),
        PoseIdx.ANKLE_R to pp(0.60f, 0.98f),
        PoseIdx.TOE_L to pp(0.56f, 0.98f, 0.5f),
        PoseIdx.TOE_R to pp(0.68f, 1.04f, 0.5f),
    )

    /** 蹲姿街拍：一膝竖起，手搭膝头 */
    private val SK_CROUCH = skeleton(
        PoseIdx.NOSE to pp(0f, -1.40f, 0.5f),
        PoseIdx.EAR_L to pp(-0.15f, -1.38f, 0.3f),
        PoseIdx.EAR_R to pp(0.15f, -1.38f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.30f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.30f, -1.0f),
        PoseIdx.ELBOW_L to pp(-0.34f, -0.48f),
        PoseIdx.WRIST_L to pp(-0.26f, -0.04f),
        PoseIdx.ELBOW_R to pp(0.24f, -0.42f),
        PoseIdx.WRIST_R to pp(0.46f, -0.18f),
        PoseIdx.HIP_L to pp(-0.20f, 0f),
        PoseIdx.HIP_R to pp(0.20f, 0f),
        PoseIdx.KNEE_L to pp(-0.34f, 0.30f),
        PoseIdx.KNEE_R to pp(0.42f, -0.28f),
        PoseIdx.ANKLE_L to pp(-0.10f, 0.72f),
        PoseIdx.ANKLE_R to pp(0.24f, 0.72f),
        PoseIdx.TOE_L to pp(-0.04f, 0.80f, 0.5f),
        PoseIdx.TOE_R to pp(0.30f, 0.80f, 0.5f),
    )

    /** 回眸：身体背向微侧，头转向镜头 */
    private val SK_LOOK_BACK = skeleton(
        PoseIdx.NOSE to pp(0.10f, -1.40f, 1.2f),
        PoseIdx.EAR_L to pp(0.20f, -1.38f, 0.3f),
        PoseIdx.EAR_R to pp(0.30f, -1.36f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.26f, -1.02f),
        PoseIdx.SHOULDER_R to pp(0.24f, -0.92f),
        PoseIdx.ELBOW_L to pp(-0.32f, -0.50f),
        PoseIdx.WRIST_L to pp(-0.28f, 0.02f),
        PoseIdx.ELBOW_R to pp(0.30f, -0.46f),
        PoseIdx.WRIST_R to pp(0.24f, 0.06f),
        PoseIdx.HIP_L to pp(-0.18f, 0f),
        PoseIdx.HIP_R to pp(0.18f, 0f),
        PoseIdx.KNEE_L to pp(-0.20f, 0.98f),
        PoseIdx.KNEE_R to pp(0.22f, 0.96f),
        PoseIdx.ANKLE_L to pp(-0.22f, 1.90f),
        PoseIdx.ANKLE_R to pp(0.24f, 1.88f),
        PoseIdx.TOE_L to pp(-0.24f, 1.98f, 0.5f),
        PoseIdx.TOE_R to pp(0.26f, 1.96f, 0.5f),
    )

    /** 行走抓拍：前后迈步，双臂自然摆动 */
    private val SK_WALKING = skeleton(
        PoseIdx.NOSE to pp(0f, -1.42f, 0.5f),
        PoseIdx.EAR_L to pp(-0.15f, -1.40f, 0.3f),
        PoseIdx.EAR_R to pp(0.15f, -1.40f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.30f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.30f, -1.0f),
        PoseIdx.ELBOW_L to pp(-0.40f, -0.52f),
        PoseIdx.WRIST_L to pp(-0.44f, -0.06f),
        PoseIdx.ELBOW_R to pp(0.34f, -0.56f),
        PoseIdx.WRIST_R to pp(0.20f, -0.96f),
        PoseIdx.HIP_L to pp(-0.20f, 0f),
        PoseIdx.HIP_R to pp(0.20f, 0f),
        PoseIdx.KNEE_L to pp(-0.28f, 0.94f),
        PoseIdx.KNEE_R to pp(0.38f, 0.72f),
        PoseIdx.ANKLE_L to pp(-0.40f, 1.86f),
        PoseIdx.ANKLE_R to pp(0.52f, 1.66f),
        PoseIdx.TOE_L to pp(-0.48f, 1.92f, 0.5f),
        PoseIdx.TOE_R to pp(0.60f, 1.74f, 0.5f),
    )

    /** 背影：正面朝远方站直，双臂微微张开 */
    private val SK_BACK_VIEW = skeleton(
        PoseIdx.NOSE to pp(0f, -1.42f, 0.4f),
        PoseIdx.EAR_L to pp(-0.14f, -1.40f, 0.3f),
        PoseIdx.EAR_R to pp(0.14f, -1.40f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.32f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.32f, -1.0f),
        PoseIdx.ELBOW_L to pp(-0.40f, -0.48f),
        PoseIdx.ELBOW_R to pp(0.40f, -0.48f),
        PoseIdx.WRIST_L to pp(-0.38f, 0.06f),
        PoseIdx.WRIST_R to pp(0.38f, 0.06f),
        PoseIdx.HIP_L to pp(-0.20f, 0f),
        PoseIdx.HIP_R to pp(0.20f, 0f),
        PoseIdx.KNEE_L to pp(-0.20f, 1.0f),
        PoseIdx.KNEE_R to pp(0.20f, 1.0f),
        PoseIdx.ANKLE_L to pp(-0.22f, 1.92f),
        PoseIdx.ANKLE_R to pp(0.22f, 1.92f),
        PoseIdx.TOE_L to pp(-0.24f, 2.0f, 0.5f),
        PoseIdx.TOE_R to pp(0.24f, 2.0f, 0.5f),
    )

    /** 对镜自拍（前置）：一手举机在面前，一手叉腰 */
    private val SK_MIRROR_SELFIE = skeleton(
        PoseIdx.NOSE to pp(0f, -1.42f, 0.5f),
        PoseIdx.EAR_L to pp(-0.15f, -1.40f, 0.3f),
        PoseIdx.EAR_R to pp(0.15f, -1.40f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.32f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.32f, -1.0f),
        PoseIdx.ELBOW_L to pp(-0.40f, -0.50f),
        PoseIdx.WRIST_L to pp(-0.26f, -0.10f),
        PoseIdx.ELBOW_R to pp(0.46f, -0.62f),
        PoseIdx.WRIST_R to pp(0.26f, -1.18f),
        PoseIdx.HIP_L to pp(-0.20f, 0f),
        PoseIdx.HIP_R to pp(0.20f, 0f),
        PoseIdx.KNEE_L to pp(-0.20f, 1.0f),
        PoseIdx.KNEE_R to pp(0.20f, 1.0f),
        PoseIdx.ANKLE_L to pp(-0.22f, 1.92f),
        PoseIdx.ANKLE_R to pp(0.22f, 1.92f),
        PoseIdx.TOE_L to pp(-0.24f, 2.0f, 0.5f),
        PoseIdx.TOE_R to pp(0.24f, 2.0f, 0.5f),
    )

    /** 楼梯坐姿：一腿屈起踏高阶，一腿伸直下伸，身体略后仰 */
    private val SK_STAIRS_SIT = skeleton(
        PoseIdx.NOSE to pp(-0.05f, -1.44f, 0.5f),
        PoseIdx.EAR_L to pp(-0.18f, -1.42f, 0.3f),
        PoseIdx.EAR_R to pp(0.08f, -1.40f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.30f, -1.04f),
        PoseIdx.SHOULDER_R to pp(0.14f, -0.98f),
        PoseIdx.ELBOW_L to pp(-0.44f, -0.58f),
        PoseIdx.WRIST_L to pp(-0.52f, -0.10f),
        PoseIdx.ELBOW_R to pp(0.20f, -0.52f),
        PoseIdx.WRIST_R to pp(0.40f, -0.30f),
        PoseIdx.HIP_L to pp(-0.16f, 0f),
        PoseIdx.HIP_R to pp(0.14f, 0f),
        PoseIdx.KNEE_L to pp(0.10f, -0.42f),
        PoseIdx.KNEE_R to pp(0.48f, 0.28f),
        PoseIdx.ANKLE_L to pp(0.06f, 0.50f),
        PoseIdx.ANKLE_R to pp(0.72f, 0.92f),
        PoseIdx.TOE_L to pp(0.12f, 0.56f, 0.5f),
        PoseIdx.TOE_R to pp(0.80f, 0.98f, 0.5f),
    )

    // ---------------- 艺术线条画（0-100 视框 SVG path，手工设计） ----------------

    /** 站姿显腿长：丸子头 + 收腰 + 一手叉腰 + 长腿 */
    private val ART_STAND = "M 54 5.6 C 56.2 3.4 59.4 3.8 59.9 6.6 C 60.4 9.2 58 10.8 55.8 10 M 45.2 12.8 C 44.8 9.4 47.4 6.8 50.6 6.8 C 53.8 6.8 56.2 9.4 56 12.8 C 55.8 16.2 53.4 18.8 50.4 18.8 C 48.8 18.8 47.4 18.2 46.4 17 M 45.8 8.6 C 43.4 12 42.8 18 44 24.5 M 48.4 18.8 C 48.6 21.2 48 22.8 46.2 24.4 C 44.4 26 43.4 27.4 43 30 C 42.7 33 43.6 37 44.6 40 C 45.3 42.2 45.3 43.4 44.8 45.4 C 44 48.4 42.9 51.2 42.9 54.2 M 52.8 18.8 C 52.6 21.2 53.2 22.8 55 24.4 C 56.8 26 57.8 27.4 58.2 30 C 58.5 33 57.6 37 56.6 40 C 55.9 42.2 55.9 43.4 56.4 45.4 C 57.2 48.4 58.3 51.2 58.3 54.2 M 58.2 30 C 61.6 33.4 63.8 37.6 62.2 40.8 C 61 43 58.8 44.4 56.6 45.2 M 43 30 C 40.6 33.6 39.6 38.6 40.4 43.6 C 41 47.4 41.8 50.8 43 53 C 43.7 54.3 44.8 55 46 55.2 M 42.9 54.2 C 42.2 64 42.4 75 43 87 C 43.2 90.4 42.2 93 40 94 C 38.8 94.6 37.4 94.2 37 93.2 M 47.5 59 C 46 67 44.9 77 44.4 87 C 44.2 90.2 44.7 92.6 46 94 M 53.8 59 C 55.3 66.5 56.6 74 57.8 81.5 C 58.4 85.5 59.4 88 61.2 89.4 M 58.3 54.2 C 59.4 63 59.4 70.5 58.2 78.5 C 57.4 83 56.6 86 57 88.6 C 57.8 91.2 59.6 92.4 61.8 92.2"

    /** 咖啡馆侧坐：侧脸 + 低发髻 + 长背弧 + 举杯 */
    private val ART_CAFE = "M 45.8 12.4 C 45.6 9.2 48.2 6.8 51.4 6.8 C 54.2 6.8 56.2 8.8 56.4 11.4 C 56.6 12.8 56.4 13.8 55.8 14.6 C 56.4 15 56.4 15.7 55.8 16 C 56.2 16.9 55.8 17.8 54.9 18.2 C 54.1 19 53.2 19.4 52.2 19.5 M 46.4 9.4 C 43.6 9.8 42.6 12.4 43.8 14.8 M 48.6 19.4 C 47 22.6 45.6 26.6 45 30.8 C 44.4 35 45 38.8 46.4 41.8 M 54.2 19.6 C 55.4 22.2 56.3 25.4 56.5 28.6 C 56.7 31.6 56.1 34.6 54.9 37 M 46.4 41.8 C 45 44.8 44.6 47 45.4 49 C 50.2 47.9 56 47.9 61 49.2 M 61 49.2 C 62.6 50.4 63.1 52 62.8 53.8 C 62.5 60.8 61.5 68.2 60 74.8 C 59.6 77.4 60.6 79.4 62.6 80.4 M 45.8 51.2 C 50.4 52.4 54.6 53 58 54.2 C 58.6 58.4 58 63.4 57 68.6 C 56.4 71.8 55.6 74.6 54.8 77 C 55.4 79 57 80 59 79.8 M 55.7 25.4 C 57.4 28.4 59 31 61 33.2 C 63 31.8 64.6 30 65.6 27.8 M 65.6 27.8 C 66.5 26.4 67.6 26 68.7 26.5"

    /** 街边回眸：左向侧脸 + 丸子头 + 发束落胸前 + 裙摆 */
    private val ART_LOOKBACK = "M 57 4 C 59 2.2 61.8 2.8 62.2 5.4 C 62.6 7.8 60.4 9.2 58.4 8.4 M 45.8 10.2 C 45.5 7 47.9 4.6 51 4.6 C 53.9 4.6 56 6.6 56.2 9.6 C 56.4 12.2 55.6 14.4 54.2 15.8 C 53 17.2 51.4 17.8 49.9 17.5 M 45.8 10.2 C 44.9 11 44.3 12.1 44.3 13.2 C 43.6 13.8 43.5 14.6 44.1 15.1 C 43.8 16 44.1 16.9 44.9 17.3 C 45.6 18.2 46.7 18.7 47.9 18.8 M 49.5 18.2 C 48 22 47.2 27 47.6 32 C 47.9 35.5 48.8 38.5 50.2 40.8 M 50.6 17.8 C 53.2 20 56.2 21.8 58.2 23.8 C 60.4 26 61 28 60.6 30.6 C 60.2 34 58.8 37.8 57.6 40.8 C 56.9 42.6 56.9 43.8 57.4 45.6 C 58.3 48.6 59.6 50.8 59.4 53.8 M 47.6 18.6 C 45.6 20.6 44 22.8 43.2 25.4 C 41.8 29.8 41.2 35.5 42 41.5 C 42.5 45.6 43.6 49.2 44.8 52 M 42.6 52.2 C 41.2 58 40.4 64.5 40.8 70.5 C 41.1 74.4 42.4 77 44.4 78 M 59.4 53.8 C 60.8 59 61.4 65 60.8 70.5 C 60.4 74.4 59.2 77 57.2 78 M 44.4 78 C 48 80.4 53.6 80.6 57.2 78 M 47 78.8 C 46.6 83.5 46.4 88 46.8 92.2 C 46.6 93.8 45.6 94.8 44.4 95 M 54.4 78.8 C 54.8 83.5 55 87.5 54.6 91.8 C 54.8 93.4 55.8 94.4 57 94.6"

    /** 夕阳背影：双臂高举 V 形 + 收腰 + A 字长裙 */
    private val ART_SUNSET = "M 46.6 9.5 C 44 12.5 43.4 17.5 44.4 22.5 M 55.4 9.5 C 58 12.5 58.6 17.5 57.6 22.5 M 46.2 12.5 C 46.2 9.2 48.6 6.8 51.5 6.8 C 54.4 6.8 56.8 9.2 56.8 12.5 C 56.8 15.8 54.4 18.2 51.5 18.2 C 48.6 18.2 46.2 15.8 46.2 12.5 M 44.5 27.5 C 41 23.5 38 18.5 36.4 13 C 35.8 10.8 34.8 9.2 33.4 8.2 C 32.6 7.4 31.6 7.2 30.8 7.6 M 57.5 27.5 C 61 23.5 64 18.5 65.6 13 C 66.2 10.8 67.2 9.2 68.6 8.2 C 69.4 7.4 70.4 7.2 71.2 7.6 M 44.5 27.5 C 45 32 45.8 37 46.5 41.5 C 46 44.5 45.2 47.5 44.7 50.5 M 57.5 27.5 C 57 32 56.2 37 55.5 41.5 C 56 44.5 56.8 47.5 57.3 50.5 M 44.7 50.5 C 43 58 41.8 66 42.2 74.5 C 42.5 79 43.8 81.6 46 82.6 M 57.3 50.5 C 59 58 60.2 66 59.8 74.5 C 59.5 79 58.2 81.6 56 82.6 M 46 82.6 C 49 84.6 53 84.6 56 82.6 M 47.8 83.2 C 47.5 87 47.5 90.4 47.9 93.4 C 47.7 95 46.7 96 45.5 96.2 M 53.9 83.2 C 54.2 87 54.2 90.4 53.8 93.4 C 54 95 55 96 56.2 96.2"

    /** 蹲姿街拍：正面劈叉蹲（双膝外张），肘压膝内侧手垂裆前 */
    private val ART_CROUCH = "M 55 7 C 57.2 5 60.4 5.6 60.8 8.2 C 61.2 10.8 58.8 12.2 56.8 11.4 M 45.4 14.2 C 45 10.8 47.6 8.2 50.8 8.2 C 54 8.2 56.4 10.8 56.2 14.2 C 56 17.6 53.4 20.2 50.4 20.2 C 48.8 20.2 47.4 19.6 46.4 18.4 M 48.6 20.2 C 48.8 22.6 48.2 24.2 46.2 25.8 C 44.2 27.6 43.2 29 42.8 31.4 C 42.5 34.4 43.2 38 44.2 41.4 C 44.9 43.4 44.9 44.6 44.4 46.6 M 53 20.2 C 52.8 22.6 53.4 24.2 55.4 25.8 C 57.4 27.6 58.4 29 58.8 31.4 C 59.1 34.4 58.4 38 57.4 41.4 C 56.7 43.4 56.7 44.6 57.2 46.6 M 44.4 46.6 C 41 50.5 37.8 55 36 60 C 35 63 34.6 66 35 68.5 M 35 68.5 C 34.4 74 34.6 80 35.6 85.5 C 36 88.5 35.2 91 33.4 92 C 32.2 92.6 30.8 92.2 30.4 91.2 M 57.2 46.6 C 60.6 50.5 63.8 55 65.6 60 C 66.6 63 67 66 66.6 68.5 M 66.6 68.5 C 67.2 74 67 80 66 85.5 C 65.6 88.5 66.4 91 68.2 92 C 69.4 92.6 70.8 92.2 71.2 91.2 M 42.8 31.4 C 40.8 37 40 43 40.6 49 C 41 53 42 56.5 43.8 58.8 C 45 62 46.4 65 47.6 67.4 C 48.4 69.8 48.4 72.2 47.6 74.4 M 58.8 31.4 C 60.8 37 61.6 43 61 49 C 60.6 53 59.6 56.5 57.8 58.8 C 56.6 62 55.2 65 54 67.4 C 53.2 69.8 53.2 72.2 54 74.4"

    /** 倚墙：斜倚抱胸 + 一腿屈膝踩墙 */
    private val ART_WALL = "M 47 10 C 44 13 43.4 18.5 44.6 24 M 46.3 14 C 46 10.6 48.6 8 51.8 8 C 55 8 57.4 10.6 57.2 14 C 57 17.4 54.4 20 51.2 20 C 49.6 20 48.2 19.4 47.2 18.2 M 56.5 10.5 C 58.8 13.5 59.4 18 58.6 22.5 M 49 20.2 C 49.2 22.6 48.6 24.2 46.8 25.8 C 44.8 27.6 43.6 29 43.2 31.4 C 42.8 34.4 43.6 38.4 44.6 41.4 C 45.3 43.4 45.4 44.6 45 46.6 C 44.3 49.6 43.4 52.4 43.6 55.4 M 53.4 20.2 C 53.2 22.6 53.8 24.2 55.6 25.8 C 57.6 27.6 58.8 29 59.2 31.4 C 59.6 34.4 58.8 38.4 57.8 41.4 C 57.1 43.4 57 44.6 57.5 46.6 C 58.2 49.6 59.2 52 59.6 54.4 M 44 32 C 48 34.5 53 35.5 57.6 34.8 M 58.4 31.4 C 54.5 34 49.5 35.2 45 34.6 M 43.6 55.4 C 42.8 64.5 43 75.5 43.8 87.5 C 44 91 43 93.6 40.8 94.6 C 39.6 95.2 38.2 94.8 37.8 93.8 M 48.6 60 C 47.2 68 46.2 77.5 45.6 87.5 C 45.4 90.6 45.9 93 47.2 94.4 M 59.6 54.4 C 62 58 64 62 64.8 66.5 M 64.8 66.5 C 65.8 62 66.6 57.5 66.8 53 C 67.6 52.2 68.6 51.8 69.6 52.2 L 71.6 52.4 M 55 58 C 57.5 61.5 59.9 64.8 63.2 68"

    /** 走路抓拍：低马尾后扬 + 前后摆臂 + 大步幅 */
    private val ART_WALKING = "M 49.5 6 C 45.4 5.4 41.6 7.8 40.4 11.8 C 39.4 15.2 40.4 19 42.4 21.4 M 40.6 11.5 C 36.8 12.5 34 15.5 33.4 19.5 M 46.3 11.8 C 46 8.6 48.5 6.2 51.6 6.2 C 54.5 6.2 56.6 8.4 56.8 11.4 C 57 13.4 56.4 15.2 55.1 16.4 C 53.9 17.8 52.2 18.4 50.7 18.2 M 56.8 11.4 C 57.5 12.2 57.6 13.2 57 14 M 48.5 18.2 C 48.7 20.6 48.2 22.2 46.4 23.8 C 44.5 25.5 43.4 27 43 29.5 C 42.6 32.5 43.4 36.5 44.5 39.5 C 45.2 41.5 45.2 42.8 44.6 44.8 C 43.8 47.8 42.8 50.6 43 53.8 M 53.3 18.2 C 53.1 20.6 53.6 22.2 55.4 23.8 C 57.3 25.5 58.4 27 58.8 29.5 C 59.2 32.5 58.4 36.5 57.3 39.5 C 56.6 41.5 56.6 42.8 57.2 44.8 C 58 47.8 59 50.6 58.8 53.8 M 43 29.5 C 40.4 33.6 39.4 38.6 40.4 43.4 C 41.4 47 43.4 49.6 46 50.8 M 58.8 29.5 C 61.6 33.2 62.9 38.2 62.2 43 C 61.8 46.4 60.6 49.2 59.6 51.6 M 58.8 53.8 C 61.8 58.5 64.4 64 66 70 C 66.7 74 67 77.6 66.6 80.6 C 66.9 84 67.6 87 68.8 89.6 C 69.8 91.6 71.4 92.6 73.4 92.4 M 54 57 C 57 61.5 59.8 66.8 61.6 72.5 C 62.4 76.4 62.7 80.2 62.3 83.6 M 43 53.8 C 41.2 60.4 39.8 67.4 39.2 74.5 C 38.8 79.5 37.9 83.5 36 87 C 34.9 89.2 33.4 90.6 31.7 91.4 C 30.2 92.2 29.4 93.6 29.8 95 M 48 57 C 46 63.5 44.6 70.4 43.8 77.5 C 43.3 82.2 42.2 86 40.4 89"

    /** 对镜自拍：长发 + 手机贴脸侧 + 一手叉腰 */
    private val ART_SELFIE = "M 46 8.5 C 43.4 11.5 42.8 16.5 43.8 22 M 55.5 8.5 C 57.4 11 58 15 57.6 19 M 45.6 13 C 45.2 9.4 47.8 6.6 51 6.6 C 54.2 6.6 56.6 9.4 56.4 13 C 56.2 16.6 53.6 19.4 50.6 19.4 C 48.8 19.4 47.4 18.6 46.4 17.4 M 48.6 19 C 48.8 21.4 48.2 23 46.2 24.6 C 44.2 26.4 43.2 27.8 42.8 30.4 C 42.5 33.4 43.4 37.4 44.4 40.4 C 45.1 42.4 45.1 43.6 44.6 45.6 C 43.8 48.6 42.7 51.4 42.9 54.4 M 53 19 C 52.8 21.4 53.4 23 55.4 24.6 C 57.4 26.4 58.4 27.8 58.8 30.4 C 59.1 33.4 58.2 37.4 57.2 40.4 C 56.5 42.4 56.5 43.6 57 45.6 C 57.8 48.6 58.9 51.4 58.7 54.4 M 58.8 30.4 C 62.4 32.2 64.8 34.4 65.4 37 C 64.6 33.8 63.6 29.6 63 25.6 C 62.6 24.6 62.2 24 61.6 23.7 M 60.2 16.5 L 63.4 16 L 64 23.4 L 60.8 23.9 Z M 42.8 30.4 C 39.4 33.8 37.8 37.8 39.2 40.8 C 40.4 42.8 42.6 44.2 45.4 45.2 M 42.9 54.4 C 42.2 63.8 42.4 74.8 43 86.8 C 43.2 90.2 42.2 92.8 40 93.8 C 38.8 94.4 37.4 94 37 93 M 47.6 59 C 46 67 44.9 77 44.4 87 C 44.2 90.2 44.7 92.6 46 94 M 53.8 59 C 55.3 66.5 56.6 74 57.8 81.5 C 58.4 85.5 59.4 88 61.2 89.4 M 58.7 54.4 C 59.6 63 59.4 70.5 58.2 78.5 C 57.4 83 56.6 86 57 88.6 C 57.8 91.2 59.6 92.4 61.8 92.2"

    // ---------------- 模板库 ----------------

    val ALL: List<PoseTemplate> = listOf(
        PoseTemplate(
            id = "stand_tall", name = "站姿显腿长", category = PoseCategory.STREET,
            tips = listOf(
                "用 0.6x 广角，镜头放低向上仰拍",
                "让双脚贴近画面底部边缘，头顶留 1/3 空间",
                "一只脚稍向前伸，脚尖朝镜头方向",
                "身体挺直、肩膀打开，不要含胸",
            ),
            zoom = 0.6f, pitch = CameraPitch.UP,
            subjectArea = RectF(0.20f, 0.02f, 0.80f, 0.98f),
            filterId = "portra400", skeleton = SK_STAND_TALL,
            artPath = ART_STAND,
        ),
        PoseTemplate(
            id = "cafe_sit", name = "咖啡馆侧坐", category = PoseCategory.SHOP,
            tips = listOf(
                "用 2x 中长焦压缩空间，背景更干净",
                "人放在画面右三分线，面朝的一侧留白",
                "侧坐朝向窗户光源，脸转向光",
                "一手搭桌面或举杯，动作自然不僵硬",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.35f, 0.15f, 0.95f, 0.92f),
            filterId = "gold200", skeleton = SK_CAFE_SIT,
            anchorLabel = "chair", anchorAlign = AnchorAlign.SIT_ON,
            artPath = ART_CAFE,
        ),
        PoseTemplate(
            id = "look_back", name = "街边回眸", category = PoseCategory.STREET,
            tips = listOf(
                "人先往前走，再回头让拍摄者抓拍",
                "人放在画面左三分线，行进方向多留白",
                "用 2x 焦段退远一点拍，避免大脸畸变",
                "回头时肩膀不要跟着转，只转头更自然",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.10f, 0.08f, 0.66f, 0.96f),
            filterId = "superia400", skeleton = SK_LOOK_BACK,
            artPath = ART_LOOKBACK,
        ),
        PoseTemplate(
            id = "sunset_silhouette", name = "夕阳剪影", category = PoseCategory.OUTDOOR,
            tips = listOf(
                "让人背对太阳站，镜头正对逆光",
                "点按屏幕上的亮部天空测光，人自然变黑",
                "肢体动作做大：伸臂、撩发，轮廓才好看",
                "太阳放在人身侧，不要正挡在头后",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.30f, 0.20f, 0.70f, 0.96f),
            filterId = "teal_orange",
            artPath = ART_SUNSET,
        ),
        PoseTemplate(
            id = "grass_topdown", name = "草地俯拍", category = PoseCategory.OUTDOOR,
            tips = listOf(
                "人躺草地，拍摄者站正上方垂直向下俯拍",
                "头放在画面中心或三分点，四肢自然摊开",
                "头发散开、帽子书本放手边当道具",
                "避开正午顶光，下午斜光更柔和",
            ),
            zoom = 1f, pitch = CameraPitch.DOWN,
            subjectArea = RectF(0.10f, 0.10f, 0.90f, 0.90f),
            filterId = "jp_clean",
        ),
        PoseTemplate(
            id = "wall_half", name = "倚墙半身", category = PoseCategory.STREET,
            tips = listOf(
                "用 2x 焦段拍半身，从腰部以上取景",
                "背或肩轻靠墙，一只脚屈膝踩墙",
                "人放三分线上，墙面延伸方向留白",
                "眼睛看画面外，比直盯镜头更有故事感",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.25f, 0.10f, 0.75f, 0.75f),
            filterId = "trix400",
            artPath = ART_WALL,
        ),
        PoseTemplate(
            id = "window_profile", name = "窗边侧脸", category = PoseCategory.HOME,
            tips = listOf(
                "人侧对窗户站，让侧光照亮半边脸",
                "用 2x 焦段拍侧脸特写，只取头肩",
                "脸微微转向窗户方向，眼神看向窗外",
                "拉上纱帘柔化光线，避免脸上硬阴影",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.30f, 0.10f, 0.90f, 0.70f),
            filterId = "pro400h",
        ),
        PoseTemplate(
            id = "street_crouch", name = "蹲姿街拍", category = PoseCategory.STREET,
            tips = listOf(
                "蹲在路边，一膝竖起、手搭膝头",
                "镜头与人眼同高或略低，不要站着俯拍",
                "背靠干净的墙或路沿，人物居中偏三分线",
                "表情放松看镜头或看远处，酷一点",
            ),
            zoom = 1f, pitch = CameraPitch.ANY,
            subjectArea = RectF(0.20f, 0.15f, 0.80f, 0.95f),
            filterId = "ccd_classic", skeleton = SK_CROUCH,
            artPath = ART_CROUCH,
        ),
        PoseTemplate(
            id = "sky_low", name = "天空仰拍", category = PoseCategory.OUTDOOR,
            tips = listOf(
                "用 0.6x 广角，镜头贴近地面向上仰拍",
                "天空占画面 2/3 以上，人只占下方一角",
                "让人弯腰或探身看镜头，避免只拍到下巴",
                "选蓝天或有云的傍晚，画面更干净",
            ),
            zoom = 0.6f, pitch = CameraPitch.UP,
            subjectArea = RectF(0.25f, 0.35f, 0.75f, 0.98f),
            filterId = "ektar100",
        ),
        PoseTemplate(
            id = "walking", name = "走路抓拍", category = PoseCategory.STREET,
            tips = listOf(
                "人沿街道自然走，连拍抓迈步瞬间",
                "前腿迈出、手臂摆动时姿态最舒展",
                "人放三分线上，行进方向留足空间",
                "快门按在脚即将落地的一刻",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.25f, 0.08f, 0.75f, 0.96f),
            filterId = "kodachrome", skeleton = SK_WALKING,
            artPath = ART_WALKING,
        ),
        PoseTemplate(
            id = "night_portrait", name = "夜景人像", category = PoseCategory.NIGHT,
            tips = listOf(
                "找霓虹灯/橱窗做背景，人脸朝向光源",
                "点顶栏手电或让同伴用手机补面光",
                "点按人脸对焦测光，防止背景灯过曝抢镜",
                "人站灯牌侧前方，利用光斑做虚化背景",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.20f, 0.10f, 0.80f, 0.90f),
            filterId = "cinestill800t",
        ),
        PoseTemplate(
            id = "seaside_back", name = "海边背影", category = PoseCategory.OUTDOOR,
            tips = listOf(
                "人背对镜头面向大海，站在海天线上",
                "海平线放在上三分线，不要切头",
                "双臂微微张开或扶帽檐，避免贴着身体",
                "日出日落时分拍，顺光背影最通透",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.30f, 0.15f, 0.70f, 0.92f),
            filterId = "polaroid600", skeleton = SK_BACK_VIEW,
        ),
        PoseTemplate(
            id = "stairs_sit", name = "楼梯坐姿", category = PoseCategory.SHOP,
            tips = listOf(
                "侧坐在台阶上，一腿屈起、一腿向下伸直",
                "伸出的腿朝向画面下角，显腿长",
                "拍摄者站侧前方平视拍，不要正对面",
                "手撑身后台阶或搭膝盖，身体略后仰",
            ),
            zoom = 1f, pitch = CameraPitch.ANY,
            subjectArea = RectF(0.18f, 0.18f, 0.88f, 0.95f),
            filterId = "agfavista", skeleton = SK_STAIRS_SIT,
            anchorLabel = "bench", anchorAlign = AnchorAlign.SIT_ON,
        ),
        PoseTemplate(
            id = "mirror_selfie", name = "对镜自拍", category = PoseCategory.SELFIE,
            tips = listOf(
                "切到前置镜头，手机挡脸或露半脸",
                "人站镜子斜侧 45°，不要正对镜子",
                "另一只手叉腰或插兜，身体微侧显线条",
                "擦干净镜子，注意背景别太乱",
            ),
            zoom = null, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.20f, 0.05f, 0.80f, 0.95f),
            filterId = "ccd_casio", skeleton = SK_MIRROR_SELFIE,
            artPath = ART_SELFIE,
        ),
        PoseTemplate(
            id = "home_cozy", name = "居家氛围感", category = PoseCategory.HOME,
            tips = listOf(
                "窝在沙发或地毯上，抱抱枕或杯子",
                "关掉顶灯，用台灯/落地灯的暖光",
                "用 2x 焦段从稍远处拍，画面更松弛",
                "不看镜头，低头笑或看向旁边更自然",
            ),
            zoom = 2f, pitch = CameraPitch.ANY,
            subjectArea = RectF(0.20f, 0.15f, 0.90f, 0.92f),
            filterId = "ccd_ixus",
            anchorLabel = "couch", anchorAlign = AnchorAlign.LEAN_ON,
        ),
    )

    val byId: Map<String, PoseTemplate> = ALL.associateBy { it.id }
}
