package com.dokacam.camera.ai

import com.dokacam.camera.data.model.PoseIdx
import com.dokacam.camera.data.model.PosePoint
import com.dokacam.camera.data.model.PoseTemplate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 姿势匹配器 —— 把实时姿态与模板骨架对齐打分。
 *
 * 归一化：髋中心为原点、躯干长（肩中点→髋中点）为 1，x 乘帧宽高比换算到
 * 各向同性像素空间，与模板骨架同坐标系比较。
 *
 * 打分 = 关键点位移分（55%）+ 关键关节角度分（45%），时间维度 5 帧滑动平均。
 * visibility < 0.5 的点降权跳过；连续 ≥85 分约 1s 置 ready。
 *
 * 前置镜像：分析帧未镜像，而模板骨架是屏幕空间——前摄时把检测点 x 翻转
 * 再比对，引导文案的左/右按解剖学索引命名（用户看到的镜像里左右同名）。
 */
class PoseMatcher(private val template: PoseTemplate) {

    data class MatchResult(
        val score: Int,            // 平滑后的 0..100
        val rawScore: Int,         // 当帧原始分
        val detected: Boolean,     // 本帧是否检出人物
        val guidance: String?,     // 偏差最大关节的分段引导文案
        val ready: Boolean,        // 高分稳定，可以按快门
        /** 各关节归一化误差（躯干长为单位），供实时剪影按肢体段着色 */
        val jointErrors: Map<Int, Float> = emptyMap(),
    )

    private data class Vec(val x: Float, val y: Float) {
        operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
        fun len() = sqrt(x * x + y * y)
    }

    private val scoreWindow = ArrayDeque<Int>()
    private var readySinceMs = 0L

    /** 未检出人物时的反馈（清空平滑窗口，避免恢复后分数虚高） */
    fun noDetection(): MatchResult {
        scoreWindow.clear()
        readySinceMs = 0L
        return MatchResult(0, 0, detected = false, guidance = "站进画面里试试～", ready = false)
    }

    /** 喂入一帧姿态（仅模板模式由 ShotCoach 产出），返回平滑后的匹配结果 */
    fun update(pose: PoseFrame, mirrored: Boolean, nowMs: Long): MatchResult {
        val skeleton = template.skeleton
            ?: return MatchResult(0, 0, detected = true, guidance = null, ready = false)
        val pts = normalize(pose, mirrored)
            ?: return MatchResult(0, 0, detected = true, guidance = "再退后一点试试", ready = false)

        // ---- 关键点位移分 ----
        var errSum = 0f
        var wSum = 0f
        var used = 0
        var worstIdx = -1
        var worstErr = 0f
        var worstDx = 0f
        var worstDy = 0f
        val jointErrors = HashMap<Int, Float>()
        for (i in 0 until 33) {
            val tp = skeleton[i]
            if (tp.weight <= 0f) continue
            val det = pts[i] ?: continue
            used++
            val d = tp
            val dx = det.x - d.x
            val dy = det.y - d.y
            val err = sqrt(dx * dx + dy * dy)
            errSum += err * tp.weight
            wSum += tp.weight
            jointErrors[i] = err
            // 引导文案只针对可指导的身体关节（脚趾/耳朵等跳过）
            if (i in GUIDABLE_JOINTS && err > worstErr) {
                worstErr = err; worstIdx = i; worstDx = dx; worstDy = dy
            }
        }
        if (used < 6) {
            return MatchResult(0, 0, detected = true, guidance = "再退后一点试试", ready = false)
        }
        val meanErr = errSum / wSum
        var posScore = (100f * (1f - meanErr / 0.7f)).coerceIn(0f, 100f)

        // ---- 关节角度分 ----
        var angDiffSum = 0f
        var angCount = 0
        for (triple in ANGLE_JOINTS) {
            val aT = skeleton[triple[0]]; val bT = skeleton[triple[1]]; val cT = skeleton[triple[2]]
            if (aT.weight <= 0f || bT.weight <= 0f || cT.weight <= 0f) continue
            val aD = pts[triple[0]] ?: continue
            val bD = pts[triple[1]] ?: continue
            val cD = pts[triple[2]] ?: continue
            val tAng = angle(Vec(aT.x, aT.y), Vec(bT.x, bT.y), Vec(cT.x, cT.y))
            val dAng = angle(aD, bD, cD)
            var diff = abs(tAng - dAng)
            if (diff > Math.PI.toFloat()) diff = 2f * Math.PI.toFloat() - diff
            angDiffSum += diff
            angCount++
        }
        val angScore = if (angCount > 0)
            (100f * (1f - angDiffSum / angCount / 0.9f)).coerceIn(0f, 100f)
        else posScore

        var raw = (posScore * 0.55f + angScore * 0.45f).toInt().coerceIn(0, 100)
        // 可见关键点不足时压低上限，避免半身入镜拿高分
        if (used < 12) raw = raw.coerceAtMost(45)

        // ---- 时间平滑（滑动平均 5 帧） ----
        scoreWindow.addLast(raw)
        while (scoreWindow.size > 5) scoreWindow.removeFirst()
        val score = scoreWindow.sum() / scoreWindow.size

        // ---- 高分稳定 1s → ready ----
        if (score >= 85) {
            if (readySinceMs == 0L) readySinceMs = nowMs
        } else {
            readySinceMs = 0L
        }
        val ready = readySinceMs > 0L && nowMs - readySinceMs >= 1000L

        val guidance = when {
            ready -> "✓ 完美！保持住～"
            worstIdx >= 0 && worstErr > 0.18f -> guidanceFor(worstIdx, worstDx, worstDy)
            score >= 70 -> "差一点点～"
            else -> "跟着剪影摆一摆"
        }
        return MatchResult(score, raw, detected = true, guidance = guidance, ready = ready, jointErrors = jointErrors)
    }

    /** 髋中心原点 + 躯干长缩放归一化；前摄镜像时 x 翻转到屏幕空间 */
    private fun normalize(pose: PoseFrame, mirrored: Boolean): Array<Vec?>? {
        val lm = pose.landmarks
        if (lm.size < 33) return null
        fun vis(i: Int) = lm[i].visibility >= 0.5f
        if (!vis(PoseIdx.HIP_L) || !vis(PoseIdx.HIP_R)) return null
        fun px(i: Int) = Vec(lm[i].x * pose.aspect, lm[i].y)
        val hipC = (px(PoseIdx.HIP_L) + px(PoseIdx.HIP_R)) * 0.5f
        val torso = if (vis(PoseIdx.SHOULDER_L) && vis(PoseIdx.SHOULDER_R)) {
            val shC = (px(PoseIdx.SHOULDER_L) + px(PoseIdx.SHOULDER_R)) * 0.5f
            (shC - hipC).len()
        } else 0f
        if (torso < 0.02f) return null
        val out = arrayOfNulls<Vec>(33)
        for (i in 0 until 33) {
            if (!vis(i)) continue
            var n = (px(i) - hipC) * (1f / torso)
            if (mirrored) n = Vec(-n.x, n.y)
            out[i] = n
        }
        return out
    }

    private operator fun Vec.plus(o: Vec) = Vec(x + o.x, y + o.y)
    private operator fun Vec.times(k: Float) = Vec(x * k, y * k)

    /** b 点处 a-b 与 c-b 的夹角（弧度） */
    private fun angle(a: Vec, b: Vec, c: Vec): Float {
        val v1 = a - b
        val v2 = c - b
        val l1 = v1.len()
        val l2 = v2.len()
        if (l1 < 1e-4f || l2 < 1e-4f) return 0f
        return abs(atan2(v1.x * v2.y - v1.y * v2.x, v1.x * v2.x + v1.y * v2.y))
    }

    /** 偏差最大关节 → 可执行引导文案（y 向下：dy>0 = 手比模板低 → 抬高）。正向短句：关节名 + ≤4 字动作 */
    private fun guidanceFor(idx: Int, dx: Float, dy: Float): String {
        val name = JOINT_NAMES[idx] ?: "身体"
        val dir = if (abs(dy) >= abs(dx)) {
            if (dy > 0) "再抬点" else "再低点"
        } else {
            if (dx > 0) "往左收点" else "往右移点"
        }
        return name + dir
    }

    companion object {
        /** 可生成引导文案的关节（避开脚趾/脚跟等不可控点） */
        private val GUIDABLE_JOINTS = setOf(
            PoseIdx.NOSE,
            PoseIdx.SHOULDER_L, PoseIdx.SHOULDER_R,
            PoseIdx.ELBOW_L, PoseIdx.ELBOW_R,
            PoseIdx.WRIST_L, PoseIdx.WRIST_R,
            PoseIdx.KNEE_L, PoseIdx.KNEE_R,
            PoseIdx.ANKLE_L, PoseIdx.ANKLE_R,
        )

        private val JOINT_NAMES = mapOf(
            PoseIdx.NOSE to "头部",
            PoseIdx.SHOULDER_L to "左肩", PoseIdx.SHOULDER_R to "右肩",
            PoseIdx.ELBOW_L to "左手肘", PoseIdx.ELBOW_R to "右手肘",
            PoseIdx.WRIST_L to "左手", PoseIdx.WRIST_R to "右手",
            PoseIdx.KNEE_L to "左膝", PoseIdx.KNEE_R to "右膝",
            PoseIdx.ANKLE_L to "左脚", PoseIdx.ANKLE_R to "右脚",
        )

        /** 关键关节角度（顶点在中间）：肘/肩/膝/髋 左右对称四组 */
        private val ANGLE_JOINTS = listOf(
            intArrayOf(PoseIdx.SHOULDER_L, PoseIdx.ELBOW_L, PoseIdx.WRIST_L),
            intArrayOf(PoseIdx.SHOULDER_R, PoseIdx.ELBOW_R, PoseIdx.WRIST_R),
            intArrayOf(PoseIdx.ELBOW_L, PoseIdx.SHOULDER_L, PoseIdx.HIP_L),
            intArrayOf(PoseIdx.ELBOW_R, PoseIdx.SHOULDER_R, PoseIdx.HIP_R),
            intArrayOf(PoseIdx.HIP_L, PoseIdx.KNEE_L, PoseIdx.ANKLE_L),
            intArrayOf(PoseIdx.HIP_R, PoseIdx.KNEE_R, PoseIdx.ANKLE_R),
            intArrayOf(PoseIdx.SHOULDER_L, PoseIdx.HIP_L, PoseIdx.KNEE_L),
            intArrayOf(PoseIdx.SHOULDER_R, PoseIdx.HIP_R, PoseIdx.KNEE_R),
        )
    }
}
