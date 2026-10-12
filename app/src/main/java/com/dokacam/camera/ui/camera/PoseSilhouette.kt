package com.dokacam.camera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dokacam.camera.data.model.POSE_BONES
import com.dokacam.camera.data.model.PoseIdx
import com.dokacam.camera.data.model.PosePoint
import kotlin.math.min
import kotlin.math.sqrt

/** 肢体段匹配着色阈值（躯干长归一化误差，与 PoseMatcher 引导阈值同量级） */
private const val SEG_GOOD_ERR = 0.15f
private const val SEG_BAD_ERR = 0.28f

private val SEG_GOOD = Color(0xFF3DDC84)
private val SEG_BAD = Color(0xFFFF5252)

// ------------------------------------------------------------------
// 艺术线条画（line art）：SVG path 数据（M/L/C/Q/Z 绝对坐标，0-100 视框）

/**
 * 极简 SVG path 解析 → Compose Path 列表（每条 M 起一条子路径）。
 * 仅支持绝对坐标 M/L/C/Q/Z，非法段跳过（断线比乱线好）。
 */
fun parseArtPaths(d: String): List<Path> {
    val tokens = Regex("([MLCQZ])|(-?\\d+\\.?\\d*)").findAll(d).map { it.value }.toList()
    val out = ArrayList<Path>()
    var cur: Path? = null
    var i = 0
    var cmd = ' '
    fun num(k: Int) = tokens.getOrNull(k)?.toFloatOrNull() ?: Float.NaN
    while (i < tokens.size) {
        val t = tokens[i]
        if (t.length == 1 && t[0] in "MLCQZ") { cmd = t[0]; i++; if (cmd == 'Z') { cur?.close(); cur = null }; continue }
        when (cmd) {
            'M' -> {
                val p = Path(); p.moveTo(num(i), num(i + 1)); out += p; cur = p; i += 2; cmd = 'L'
            }
            'L' -> { cur?.lineTo(num(i), num(i + 1)); i += 2 }
            'C' -> {
                cur?.cubicTo(num(i), num(i + 1), num(i + 2), num(i + 3), num(i + 4), num(i + 5)); i += 6
            }
            'Q' -> { cur?.quadraticTo(num(i), num(i + 1), num(i + 2), num(i + 3)); i += 4 }
            else -> i++
        }
    }
    return out
}

/**
 * 把 0-100 视框的艺术线条等比缩放画进 [dst]（像素矩形）。
 * 水平居中；[alignBottom] 时底边对齐（锚定坐姿/站姿「坐/站」在目标上），否则垂直居中。
 * [strokePx] 为最终像素线宽（不受缩放影响），圆头圆角。
 */
fun DrawScope.drawArtPaths(
    paths: List<Path>,
    dst: Rect,
    color: Color,
    strokePx: Float,
    alignBottom: Boolean = false,
) {
    if (paths.isEmpty() || dst.width <= 0f || dst.height <= 0f) return
    val s = min(dst.width, dst.height) / 100f
    val dx = dst.left + (dst.width - 100f * s) / 2f
    val dy = if (alignBottom) dst.top + dst.height - 100f * s
             else dst.top + (dst.height - 100f * s) / 2f
    translate(dx, dy) {
        scale(s, s, Offset.Zero) {
            val style = Stroke(width = strokePx / s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (p in paths) drawPath(p, color, style = style)
        }
    }
}

/** 模板卡片示例图：艺术线条等比居中，深色底由卡片提供 */
@Composable
fun PoseArtCanvas(
    artPath: String,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
) {
    val paths = remember(artPath) { parseArtPaths(artPath) }
    Canvas(modifier) {
        val pad = size.minDimension * 0.07f
        drawArtPaths(
            paths,
            Rect(pad, pad, size.width - pad, size.height - pad),
            color,
            strokeWidth.toPx(),
        )
    }
}

/** 模板卡片示例图：由 33 点骨架自动渲染的时装速写轮廓（无手绘 SVG 时兜底） */
@Composable
fun SkeletonArtCanvas(
    skeleton: List<PosePoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val pad = size.minDimension * 0.07f
        drawSkeletonFigure(
            skeleton,
            Rect(pad, pad, size.width - pad, size.height - pad),
            color,
        )
    }
}

// ------------------------------------------------------------------
// 实时检测姿态：圆头骨骼线（按肢体段着色：匹配好绿 / 偏差大红）

/**
 * 实时骨架描线：躯干四肢主干 + 头圆。
 * [pts] 为像素坐标（null = 不可见，跳过对应段）。
 */
fun DrawScope.drawPoseBones(
    pts: List<Offset?>,
    color: Color,
    jointErrors: Map<Int, Float>? = null,
    strokePx: Float = 2.5.dp.toPx(),
) {
    fun p(i: Int): Offset? = pts.getOrNull(i)
    fun segColor(a: Int, b: Int): Color {
        if (jointErrors == null) return color
        val ea = jointErrors[a]; val eb = jointErrors[b]
        if (ea == null && eb == null) return color
        val e = maxOf(ea ?: 0f, eb ?: 0f)
        return when {
            e <= SEG_GOOD_ERR -> SEG_GOOD
            e >= SEG_BAD_ERR -> SEG_BAD
            else -> color
        }
    }
    for ((a, b) in POSE_BONES) {
        val pa = p(a) ?: continue
        val pb = p(b) ?: continue
        drawLine(segColor(a, b), pa, pb, strokeWidth = strokePx, cap = StrokeCap.Round)
    }
    // 头：鼻/耳估算
    val earL = p(PoseIdx.EAR_L); val earR = p(PoseIdx.EAR_R); val nose = p(PoseIdx.NOSE)
    val headC = when {
        earL != null && earR != null -> Offset((earL.x + earR.x) / 2f, (earL.y + earR.y) / 2f)
        else -> nose
    }
    val headR = when {
        earL != null && earR != null -> dist(earL, earR) * 0.62f
        nose != null -> 3.dp.toPx() * 4
        else -> 0f
    }
    if (headC != null && headR > 2f) {
        drawCircle(segColor(PoseIdx.NOSE, PoseIdx.EAR_R), radius = headR, center = headC,
            style = Stroke(width = strokePx))
    }
}

// ------------------------------------------------------------------
// 骨架自动渲染「时装速写」轮廓：圆头 + 收腰躯干 + 圆头四肢（上粗下细）。
// 与手绘 SVG 线条画风格统一；无 artPath 的模板用它兜底出示例图，
// 也作为实时参考（guide overlay）的第二优先渲染源。按骨架包围盒等比放进 [dst]。

fun DrawScope.drawSkeletonFigure(
    skeleton: List<PosePoint>,
    dst: Rect,
    color: Color,
    alignBottom: Boolean = false,
) {
    if (dst.width <= 0f || dst.height <= 0f) return
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (p in skeleton) {
        if (p.weight <= 0f) continue
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    if (minX > maxX || minY > maxY) return
    val w0 = (maxX - minX).coerceAtLeast(0.01f)
    val h0 = (maxY - minY).coerceAtLeast(0.01f)
    // 留 8% 边距（头圆可能略超出包围盒顶部）
    val s = min(dst.width / w0, dst.height / h0) * 0.92f
    val ox = dst.left + (dst.width - w0 * s) / 2f - minX * s
    val oy = if (alignBottom) dst.bottom - h0 * s - minY * s
             else dst.top + (dst.height - h0 * s) / 2f - minY * s

    fun X(x: Float) = x * s + ox
    fun Y(y: Float) = y * s + oy
    fun pt(i: Int): Offset? {
        val p = skeleton.getOrNull(i) ?: return null
        return if (p.weight > 0f) Offset(X(p.x), Y(p.y)) else null
    }

    val lineStyle = { w: Float -> Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) }

    // 1) 收腰躯干：肩→腰(收窄)→髋 的闭合六边形，勾勒女性身段
    val sL = pt(PoseIdx.SHOULDER_L); val sR = pt(PoseIdx.SHOULDER_R)
    val hL = pt(PoseIdx.HIP_L); val hR = pt(PoseIdx.HIP_R)
    if (sL != null && sR != null && hL != null && hR != null) {
        val t = 0.42f
        val waistMidX = lerp((sL.x + sR.x) / 2f, (hL.x + hR.x) / 2f, t)
        val shoulderW = (sR.x - sL.x).coerceAtLeast(0.01f)
        val hipW = (hR.x - hL.x).coerceAtLeast(0.01f)
        val waistHalf = min(shoulderW, hipW) * 0.32f
        val waistY = lerp(sL.y, hL.y, t)
        val torso = Path().apply {
            moveTo(sL.x, sL.y)
            lineTo(waistMidX - waistHalf, waistY)
            lineTo(hL.x, hL.y)
            lineTo(hR.x, hR.y)
            lineTo(waistMidX + waistHalf, waistY)
            lineTo(sR.x, sR.y)
            close()
        }
        drawPath(torso, color, style = lineStyle(s * 0.055f))
    }

    // 2) 头：鼻/耳中点为心的圆
    val nose = pt(PoseIdx.NOSE)
    val earL = pt(PoseIdx.EAR_L); val earR = pt(PoseIdx.EAR_R)
    val headC = when {
        earL != null && earR != null -> Offset((earL.x + earR.x) / 2f, (earL.y + earR.y) / 2f)
        else -> nose
    }
    if (headC != null) {
        drawCircle(color, radius = s * 0.16f, center = headC, style = lineStyle(s * 0.045f))
    }

    // 3) 四肢：上臂/大腿略粗，前臂/小腿略细，圆头收口
    val armW = s * 0.052f
    val foreW = s * 0.036f
    val thighW = s * 0.056f
    val shinW = s * 0.038f
    val footW = s * 0.028f
    fun limb(a: Int, b: Int, w: Float) {
        val pa = pt(a) ?: return
        val pb = pt(b) ?: return
        drawLine(color, pa, pb, strokeWidth = w, cap = StrokeCap.Round)
    }
    limb(PoseIdx.SHOULDER_L, PoseIdx.ELBOW_L, armW)
    limb(PoseIdx.ELBOW_L, PoseIdx.WRIST_L, foreW)
    limb(PoseIdx.SHOULDER_R, PoseIdx.ELBOW_R, armW)
    limb(PoseIdx.ELBOW_R, PoseIdx.WRIST_R, foreW)
    limb(PoseIdx.HIP_L, PoseIdx.KNEE_L, thighW)
    limb(PoseIdx.KNEE_L, PoseIdx.ANKLE_L, shinW)
    limb(PoseIdx.HIP_R, PoseIdx.KNEE_R, thighW)
    limb(PoseIdx.KNEE_R, PoseIdx.ANKLE_R, shinW)
    limb(PoseIdx.ANKLE_L, PoseIdx.TOE_L, footW)
    limb(PoseIdx.ANKLE_R, PoseIdx.TOE_R, footW)
}

private fun dist(a: Offset, b: Offset): Float {
    val dx = a.x - b.x; val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
