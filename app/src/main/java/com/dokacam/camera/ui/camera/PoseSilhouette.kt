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
import com.dokacam.camera.data.model.POSE_BONES
import com.dokacam.camera.data.model.PoseIdx
import kotlin.math.max
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
 * [fitContent] 时按线条实际包围盒（而非整个 0-100 视框）缩放适配，手绘内容更饱满；
 * [strokePx] 为最终像素线宽（不受缩放影响），圆头圆角。
 */
fun DrawScope.drawArtPaths(
    paths: List<Path>,
    dst: Rect,
    color: Color,
    strokePx: Float,
    alignBottom: Boolean = false,
    fitContent: Boolean = false,
) {
    if (paths.isEmpty() || dst.width <= 0f || dst.height <= 0f) return
    val pad = if (fitContent) min(dst.width, dst.height) * 0.05f else 0f
    val (s, dx, dy) = if (fitContent) {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in paths) {
            val bd = p.getBounds()
            if (!bd.left.isFinite() || bd.right <= bd.left || bd.bottom <= bd.top) continue
            l = min(l, bd.left); t = min(t, bd.top)
            r = max(r, bd.right); b = max(b, bd.bottom)
        }
        // 无有效包围盒时退回整个视框
        if (!l.isFinite() || r - l <= 0f || b - t <= 0f) { l = 0f; t = 0f; r = 100f; b = 100f }
        val cw = r - l; val ch = b - t
        val sc = min((dst.width - 2 * pad) / cw, (dst.height - 2 * pad) / ch)
        val dx0 = dst.left + pad + (dst.width - 2 * pad - cw * sc) / 2f - l * sc
        val dy0 = if (alignBottom) dst.bottom - pad - b * sc
                  else dst.top + pad + (dst.height - 2 * pad - ch * sc) / 2f - t * sc
        Triple(sc, dx0, dy0)
    } else {
        val s0 = min(dst.width, dst.height) / 100f
        val dx0 = dst.left + (dst.width - 100f * s0) / 2f
        val dy0 = if (alignBottom) dst.top + dst.height - 100f * s0
                  else dst.top + (dst.height - 100f * s0) / 2f
        Triple(s0, dx0, dy0)
    }
    translate(dx, dy) {
        scale(s, s, Offset.Zero) {
            val style = Stroke(width = strokePx / s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (p in paths) drawPath(p, color, style = style)
        }
    }
}

/** 模板卡片示例图：艺术线条画（按内容包围盒适配，深色底由卡片提供） */
@Composable
fun PoseArtCanvas(
    artPath: String,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
    alignBottom: Boolean = false,
) {
    val paths = remember(artPath) { parseArtPaths(artPath) }
    Canvas(modifier) {
        val pad = size.minDimension * 0.07f
        drawArtPaths(
            paths,
            Rect(pad, pad, size.width - pad, size.height - pad),
            color,
            strokeWidth.toPx(),
            alignBottom = alignBottom,
            fitContent = true,
        )
    }
}

/** 取景参考框：四角分离的细括弧（比整圈方框更轻盈），[gapPx] 为角部留白 */
fun DrawScope.drawCornerBrackets(
    rect: Rect,
    color: Color,
    lenPx: Float,
    strokePx: Float,
    gapPx: Float = 6f,
) {
    val l = rect.left; val t = rect.top; val r = rect.right; val b = rect.bottom
    // 每个角两条短臂：竖臂 + 横臂，角部断开留白
    val arms = listOf(
        Offset(l, t + gapPx) to Offset(l, t + lenPx),
        Offset(l + gapPx, t) to Offset(l + lenPx, t),
        Offset(r, t + gapPx) to Offset(r, t + lenPx),
        Offset(r - gapPx, t) to Offset(r - lenPx, t),
        Offset(r, b - gapPx) to Offset(r, b - lenPx),
        Offset(r - gapPx, b) to Offset(r - lenPx, b),
        Offset(l, b - gapPx) to Offset(l, b - lenPx),
        Offset(l + gapPx, b) to Offset(l + lenPx, b),
    )
    for ((a, z) in arms) drawLine(color, a, z, strokeWidth = strokePx, cap = StrokeCap.Round)
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

private fun dist(a: Offset, b: Offset): Float {
    val dx = a.x - b.x; val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
