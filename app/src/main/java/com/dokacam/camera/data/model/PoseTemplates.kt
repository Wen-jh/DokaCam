package com.dokacam.camera.data.model

import android.graphics.RectF

/**
 * 内置自拍姿势模板库 —— 15 个不挑景的自拍姿势（大头/半身/全身，通用摄影知识整理，原创内容）。
 *
 * 骨架（skeleton）为 33 点列表，与 BlazePose 索引对齐；
 * 坐标以髋部中心为原点、躯干长为 1（y 向下，x 为画面右向，屏幕空间），
 * weight=0 的点不参与匹配与绘制。只有全身（FULL）类模板提供骨架，
 * 大头/半身走人脸框轻量引导（见 ai/SelfieGuide）。
 * 每个模板都必须有手绘线条画（PoseArt），示例图不允许几何兜底。
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

    /** S 站姿：重心右腿顶胯（contrapposto），左腿前伸，一手叉腰 */
    private val SK_S_LINE = skeleton(
        PoseIdx.NOSE to pp(-0.02f, -1.42f, 0.5f),
        PoseIdx.EAR_L to pp(-0.17f, -1.40f, 0.3f),
        PoseIdx.EAR_R to pp(0.13f, -1.40f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.36f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.28f, -1.0f),
        PoseIdx.ELBOW_L to pp(-0.40f, -0.48f),
        PoseIdx.WRIST_L to pp(-0.38f, 0.04f),
        PoseIdx.ELBOW_R to pp(0.36f, -0.52f),
        PoseIdx.WRIST_R to pp(0.20f, -0.78f),
        PoseIdx.HIP_L to pp(-0.10f, 0f),
        PoseIdx.HIP_R to pp(0.30f, 0f),
        PoseIdx.KNEE_L to pp(-0.16f, 1.0f),
        PoseIdx.KNEE_R to pp(0.30f, 0.96f),
        PoseIdx.ANKLE_L to pp(-0.24f, 1.92f),
        PoseIdx.ANKLE_R to pp(0.30f, 1.86f),
        PoseIdx.TOE_L to pp(-0.28f, 2.0f, 0.5f),
        PoseIdx.TOE_R to pp(0.38f, 1.94f, 0.5f),
    )

    /** 交叉腿：双膝靠拢，右腿跨前脚尖点地，脚跟微抬 */
    private val SK_CROSSED = skeleton(
        PoseIdx.NOSE to pp(0f, -1.42f, 0.5f),
        PoseIdx.EAR_L to pp(-0.15f, -1.40f, 0.3f),
        PoseIdx.EAR_R to pp(0.15f, -1.40f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.30f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.30f, -1.0f),
        PoseIdx.ELBOW_L to pp(-0.36f, -0.48f),
        PoseIdx.WRIST_L to pp(-0.34f, 0.04f),
        PoseIdx.ELBOW_R to pp(0.36f, -0.50f),
        PoseIdx.WRIST_R to pp(0.34f, -0.02f),
        PoseIdx.HIP_L to pp(-0.20f, 0f),
        PoseIdx.HIP_R to pp(0.20f, 0f),
        PoseIdx.KNEE_L to pp(-0.10f, 1.0f),
        PoseIdx.KNEE_R to pp(0.10f, 0.98f),
        PoseIdx.ANKLE_L to pp(0.08f, 1.90f),
        PoseIdx.ANKLE_R to pp(-0.12f, 1.82f),
        PoseIdx.TOE_L to pp(0.14f, 1.98f, 0.5f),
        PoseIdx.TOE_R to pp(-0.06f, 1.90f, 0.5f),
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

    /** 倚墙：斜倚抱胸 + 一腿屈膝踩墙 */
    private val SK_WALL_LEAN = skeleton(
        PoseIdx.NOSE to pp(0.12f, -1.38f, 0.5f),
        PoseIdx.EAR_L to pp(0.02f, -1.38f, 0.3f),
        PoseIdx.EAR_R to pp(0.18f, -1.36f, 0.3f),
        PoseIdx.SHOULDER_L to pp(-0.22f, -1.0f),
        PoseIdx.SHOULDER_R to pp(0.14f, -0.96f),
        PoseIdx.ELBOW_L to pp(-0.06f, -0.64f),
        PoseIdx.WRIST_L to pp(0.10f, -0.56f),
        PoseIdx.ELBOW_R to pp(0.16f, -0.60f),
        PoseIdx.WRIST_R to pp(-0.02f, -0.54f),
        PoseIdx.HIP_L to pp(-0.16f, 0f),
        PoseIdx.HIP_R to pp(0.14f, 0f),
        PoseIdx.KNEE_L to pp(0.30f, 0.06f),
        PoseIdx.KNEE_R to pp(0.24f, 0.92f),
        PoseIdx.ANKLE_L to pp(0.34f, 0.76f),
        PoseIdx.ANKLE_R to pp(0.20f, 1.86f),
        PoseIdx.TOE_L to pp(0.38f, 0.84f, 0.5f),
        PoseIdx.TOE_R to pp(0.24f, 1.94f, 0.5f),
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

    // ---------------- 模板库 ----------------

    val ALL: List<PoseTemplate> = listOf(
        // ===== 大头照：白墙就能拍，脸+手出片 =====
        PoseTemplate(
            id = "head_chin", name = "托脸", category = PoseCategory.HEAD,
            tips = listOf(
                "手背轻轻托下巴，脸往一侧微倾 15°",
                "手肘找个支撑（桌面/膝盖），画面才不抖",
                "用 2x 拍，脸不变形，皮肤质感也更好",
                "眼神看镜头，笑不笑都好看",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.28f, 0.12f, 0.72f, 0.52f),
            filterId = "ccd_casio", exposureEv = 0.5f,
            artPath = PoseArt.HEAD_CHIN,
            group = "单人·特写", moodTip = "想着开心的事，眼神变软",
        ),
        PoseTemplate(
            id = "head_side", name = "侧脸杀", category = PoseCategory.HEAD,
            tips = listOf(
                "整个头转向一侧，眼睛看斜前方",
                "把头发别到耳后，露出下颌线",
                "侧脸朝向光源，轮廓更有立体感",
                "用 2x 拍，背景虚化更干净",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.46f, 0.12f, 0.78f, 0.52f),
            filterId = "pro400h", exposureEv = 0.5f,
            artPath = PoseArt.HEAD_SIDE,
            group = "单人·特写", moodTip = "眼神看远处，像在等风来",
        ),
        PoseTemplate(
            id = "head_peek", name = "半遮脸", category = PoseCategory.HEAD,
            tips = listOf(
                "手指轻轻搭在脸颊，挡住下半张脸",
                "指缝间露出一只眼睛，眼神要有戏",
                "手腕放松，别用整只手掌按在脸上",
                "脸微侧一点，遮得更自然",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.26f, 0.14f, 0.58f, 0.52f),
            filterId = "ccd_ixus", exposureEv = 0.5f,
            artPath = PoseArt.HEAD_PEEK,
            group = "单人·特写", moodTip = "想象看到好笑的事，眼睛笑弯",
        ),
        PoseTemplate(
            id = "head_hair", name = "撩发", category = PoseCategory.HEAD,
            tips = listOf(
                "手指插进发根，慢慢往后拨",
                "拨发的瞬间按快门，最有氛围感",
                "头微微后仰，下巴线条更好看",
                "头发扬起来时抓拍，别摆太久",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.44f, 0.12f, 0.76f, 0.54f),
            filterId = "gold200", exposureEv = 0.5f,
            artPath = PoseArt.HEAD_HAIR,
            group = "单人·特写", moodTip = "想象微风刚好吹过发梢",
        ),
        PoseTemplate(
            id = "head_top", name = "高颅顶", category = PoseCategory.HEAD,
            tips = listOf(
                "手掌轻轻搭在头顶，肘部自然打开",
                "手机放低一点，从下往上拍",
                "这样能拉高颅顶，脸也显小",
                "肩膀放松，别耸肩",
            ),
            zoom = 2f, pitch = CameraPitch.UP,
            subjectArea = RectF(0.30f, 0.08f, 0.70f, 0.48f),
            filterId = "portra400", exposureEv = 0.5f,
            artPath = PoseArt.HEAD_TOP,
            group = "单人·特写", moodTip = "下巴微收，眼神笃定一点",
        ),
        PoseTemplate(
            id = "head_pout", name = "松弛感", category = PoseCategory.HEAD,
            tips = listOf(
                "不用刻意笑，嘴唇放松微微分开",
                "眼神放空看向镜头，像刚发现有人拍你",
                "头微侧，下颌线收一点",
                "越不在乎越出片，别绷着",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.30f, 0.14f, 0.70f, 0.54f),
            filterId = "ccd_casio", exposureEv = 0.5f,
            artPath = PoseArt.HEAD_POUT,
            group = "单人·特写", moodTip = "什么都不想，彻底放空",
        ),

        // ===== 半身照：纯背景 + 上半身手势 =====
        PoseTemplate(
            id = "half_lookback", name = "回眸", category = PoseCategory.HALF,
            tips = listOf(
                "先背对镜头，回头看向镜头方向",
                "只转头和肩，腰不要跟着扭",
                "人放在画面一侧，视线方向留白",
                "用 2x 拍半身，背景虚化更有氛围",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.10f, 0.10f, 0.58f, 0.68f),
            filterId = "superia400", exposureEv = 0.33f,
            artPath = PoseArt.HALF_LOOKBACK,
            group = "单人·街拍", moodTip = "像听到有人喊你名字",
        ),
        PoseTemplate(
            id = "half_collarbone", name = "锁骨手", category = PoseCategory.HALF,
            tips = listOf(
                "手指轻轻搭在锁骨上，别用力压",
                "肩膀自然下沉，锁骨线条才明显",
                "头微侧看向镜头，气质感就来了",
                "穿露肩或 V 领，效果更明显",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.40f, 0.10f, 0.80f, 0.66f),
            filterId = "gold200", exposureEv = 0.33f,
            artPath = PoseArt.HALF_COLLAR,
            group = "单人·街拍", moodTip = "想象刚听到一句夸奖",
        ),
        PoseTemplate(
            id = "half_crossarm", name = "抱臂", category = PoseCategory.HALF,
            tips = listOf(
                "双臂在胸前自然交叠，手轻轻搭住",
                "肩往后打开，背挺直",
                "头微侧，表情松弛一点",
                "手肘别夹太紧，线条才舒展",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.28f, 0.10f, 0.72f, 0.66f),
            filterId = "superia400", exposureEv = 0.33f,
            artPath = PoseArt.HALF_CROSSARM,
            group = "单人·街拍", moodTip = "心里默念：我最酷",
        ),
        PoseTemplate(
            id = "half_prop", name = "拿道具", category = PoseCategory.HALF,
            tips = listOf(
                "举起杯子或手机，杯沿齐下巴",
                "手腕放松，手指自然扣住",
                "道具挡住下巴下方，显脸小",
                "眼睛看道具或镜头，都很有故事感",
            ),
            zoom = 2f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.24f, 0.10f, 0.64f, 0.66f),
            filterId = "jp_clean", exposureEv = 0.33f,
            artPath = PoseArt.HALF_PROP,
            group = "单人·街拍", moodTip = "想象在喝第一口冰美式",
        ),

        // ===== 全身照：对镜/靠墙/空地即可，保留骨架匹配 =====
        PoseTemplate(
            id = "full_sline", name = "S 站姿", category = PoseCategory.FULL,
            tips = listOf(
                "重心放在一条腿上，另一条腿往前伸",
                "胯部自然顶出，身体形成 S 线",
                "让双脚贴近画面底部，头顶留白",
                "一手叉腰一手垂，肩膀打开",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.22f, 0.04f, 0.78f, 0.96f),
            filterId = "portra400", skeleton = SK_S_LINE,
            artPath = PoseArt.STAND,
            group = "单人·街拍", moodTip = "肩膀打开，气场拿出来",
        ),
        PoseTemplate(
            id = "full_crossleg", name = "交叉腿", category = PoseCategory.FULL,
            tips = listOf(
                "一条腿跨到另一条前面，脚尖点地",
                "重心放在后面的腿上，胯往一侧顶",
                "手叉腰或插兜，身体线条更明显",
                "脚贴画面底边，头顶留白显高",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.22f, 0.04f, 0.78f, 0.96f),
            filterId = "agfavista", skeleton = SK_CROSSED,
            artPath = PoseArt.FULL_CROSSLEG,
            group = "单人·街拍", moodTip = "想象刚被街拍师拦下",
        ),
        PoseTemplate(
            id = "full_walking", name = "走路抓拍", category = PoseCategory.FULL,
            tips = listOf(
                "自然往前走，别刻意摆动作",
                "前腿迈出、手臂摆开的瞬间最舒展",
                "连拍几张，挑最有动感的一张",
                "眼睛看前方或镜头，都好看",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.25f, 0.08f, 0.75f, 0.96f),
            filterId = "kodachrome", skeleton = SK_WALKING,
            artPath = PoseArt.WALKING,
            group = "单人·街拍", moodTip = "哼着歌往前走，别管镜头",
        ),
        PoseTemplate(
            id = "full_wall", name = "靠墙插兜", category = PoseCategory.FULL,
            tips = listOf(
                "背或肩轻靠墙，手插口袋",
                "一只脚屈膝踩墙，另一条腿伸直",
                "表情放松，看向镜头外的方向",
                "墙面干净时用 1x，杂的话切 2x",
            ),
            zoom = 1f, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.24f, 0.08f, 0.76f, 0.94f),
            filterId = "trix400", skeleton = SK_WALL_LEAN,
            artPath = PoseArt.WALL,
            group = "单人·街拍", moodTip = "想象在等一个老朋友",
        ),
        PoseTemplate(
            id = "full_mirror", name = "对镜自拍", category = PoseCategory.FULL,
            tips = listOf(
                "手机挡脸或露出半张脸",
                "人站镜子斜侧 45°，不要正对镜子",
                "另一只手叉腰或插兜，身体微侧",
                "擦干净镜子，注意背景别太乱",
            ),
            zoom = null, pitch = CameraPitch.LEVEL,
            subjectArea = RectF(0.20f, 0.05f, 0.80f, 0.95f),
            filterId = "ccd_casio", skeleton = SK_MIRROR_SELFIE,
            frontCamera = true,
            artPath = PoseArt.SELFIE,
            group = "单人·对镜", moodTip = "对镜子里的自己眨个眼",
        ),
    )

    val byId: Map<String, PoseTemplate> = ALL.associateBy { it.id }
}
