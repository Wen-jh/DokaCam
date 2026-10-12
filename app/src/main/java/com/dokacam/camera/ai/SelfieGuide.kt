package com.dokacam.camera.ai

import android.graphics.RectF
import com.dokacam.camera.data.model.PoseCategory

/**
 * 大头/半身自拍的「轻量引导」：不做 33 点骨架匹配，
 * 只用主脸检测框的两个信息给出最高价值的提示——
 * 脸的大小 → 距离（拿远消畸变 / 靠近更聚焦），
 * 脸的位置 → 居中（避开镜头边缘畸变）。
 *
 * 所有坐标/尺寸均为「视图归一化」（0..1，经 glView 映射后），
 * 与 subjectArea（视图坐标）同空间，可直接比较。
 */

/** 视图空间下的主脸信息 */
data class SelfieFaceInfo(
    val rectView: RectF,
    /** 脸宽 / 视图宽（距离的代理：越大越近） */
    val sizeFrac: Float,
    val centerX: Float,
    val centerY: Float,
)

enum class SelfieHintKind { TOO_CLOSE, TOO_FAR, SHIFT_X, SHIFT_Y, NO_FACE }

data class SelfieHint(val kind: SelfieHintKind, val text: String)

object SelfieGuide {

    /** 各构图下脸宽的舒适区间（视图宽占比）：(下限=太远, 上限=太近) */
    fun sizeBand(category: PoseCategory): Pair<Float, Float> = when (category) {
        PoseCategory.HEAD -> 0.13f to 0.34f
        PoseCategory.HALF -> 0.07f to 0.22f
        PoseCategory.FULL -> 0.025f to 0.11f
    }

    fun sizeHint(sizeFrac: Float, category: PoseCategory): SelfieHint? {
        val (far, near) = sizeBand(category)
        return when {
            sizeFrac > near -> SelfieHint(SelfieHintKind.TOO_CLOSE, "拿远一点，脸会小一圈")
            sizeFrac < far -> SelfieHint(SelfieHintKind.TOO_FAR, "靠近一点，脸再大一点")
            else -> null
        }
    }

    /** 脸中心 vs 目标区域中心；竖向优先（机位问题比左右更影响观感） */
    fun centerHint(
        faceCx: Float,
        faceCy: Float,
        area: RectF,
        frontFacing: Boolean,
    ): SelfieHint? {
        val dx = faceCx - area.centerX()
        val dy = faceCy - area.centerY()
        if (kotlin.math.abs(dy) > 0.12f) {
            return if (dy > 0) {
                SelfieHint(
                    SelfieHintKind.SHIFT_Y,
                    if (frontFacing) "手机抬高一点" else "镜头抬高一点",
                )
            } else {
                SelfieHint(
                    SelfieHintKind.SHIFT_Y,
                    if (frontFacing) "手机放低一点" else "镜头放低一点",
                )
            }
        }
        if (kotlin.math.abs(dx) > 0.09f) {
            return if (dx > 0) {
                SelfieHint(
                    SelfieHintKind.SHIFT_X,
                    if (frontFacing) "脸往左一点" else "镜头往左移一点",
                )
            } else {
                SelfieHint(
                    SelfieHintKind.SHIFT_X,
                    if (frontFacing) "脸往右一点" else "镜头往右移一点",
                )
            }
        }
        return null
    }

    /** 长时间未检测到脸时的提示（由 UI 计时触发） */
    fun noFaceHint(category: PoseCategory): SelfieHint = when (category) {
        PoseCategory.HEAD, PoseCategory.HALF ->
            SelfieHint(SelfieHintKind.NO_FACE, "把脸露出来，看镜头这边")
        PoseCategory.FULL ->
            SelfieHint(SelfieHintKind.NO_FACE, "站远一点，让全身入镜")
    }
}

/**
 * 提示稳定器：同一种提示持续 [holdMs] 才输出（避免逐帧抖动刷屏）；
 * 提示种类变化立即清零，新提示重新计时。
 */
class HintStabilizer(private val holdMs: Long = 600L) {

    private var current: SelfieHint? = null
    private var heldSinceMs: Long = -1L

    fun push(hint: SelfieHint?, nowMs: Long): SelfieHint? {
        if (hint?.kind != current?.kind) {
            current = hint
            heldSinceMs = if (hint == null) -1L else nowMs
            return null
        }
        if (hint == null) return null
        return if (nowMs - heldSinceMs >= holdMs) hint else null
    }

    fun reset() {
        current = null
        heldSinceMs = -1L
    }
}
