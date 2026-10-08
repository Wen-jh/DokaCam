package com.dokacam.camera.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘矢量图标 —— 不依赖 material-icons-extended（已废弃且体积大）。
 * 相机 App 的图标语言应当统一：细线、几何、克制。
 */
object DokaIcons {

    /** 闪光灯（闪电） */
    val Flash: ImageVector by lazy {
        build("flash") {
            path(fill = SolidColor(Color.White)) {
                moveTo(13f, 2f)
                lineTo(4.5f, 13.5f)
                horizontalLineTo(11f)
                lineTo(10f, 22f)
                lineTo(19.5f, 10f)
                horizontalLineTo(13.5f)
                close()
            }
        }
    }

    /** 前后摄切换（相机 + 环形箭头） */
    val FlipCamera: ImageVector by lazy {
        build("flip_camera") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 12f)
                arcTo(8f, 8f, 0f, false, true, 20f, 12f)
                arcTo(8f, 8f, 0f, false, false, 4f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2.5f, 12f)
                lineTo(5.5f, 9f)
                lineTo(5.5f, 15f)
                close()
                moveTo(21.5f, 12f)
                lineTo(18.5f, 15f)
                lineTo(18.5f, 9f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 8.2f)
                lineTo(14.8f, 10f)
                lineTo(14.8f, 14f)
                lineTo(12f, 15.8f)
                lineTo(9.2f, 14f)
                lineTo(9.2f, 10f)
                close()
            }
        }
    }

    /** 网格（九宫格） */
    val Grid: ImageVector by lazy {
        build("grid") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(3f, 9f); horizontalLineTo(21f)
                moveTo(3f, 15f); horizontalLineTo(21f)
                moveTo(9f, 3f); verticalLineTo(21f)
                moveTo(15f, 3f); verticalLineTo(21f)
                moveTo(4f, 4f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(4f); close()
            }
        }
    }

    /** 定时器（秒表） */
    val Timer: ImageVector by lazy {
        build("timer") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 13f); lineTo(15.5f, 9.5f)
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9f, 3f); horizontalLineTo(15f)
                moveTo(12f, 3f); verticalLineTo(5.5f)
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(19f, 13f)
                arcTo(7f, 7f, 0f, false, false, 5f, 13f)
                arcTo(7f, 7f, 0f, false, false, 19f, 13f)
                close()
            }
        }
    }

    /** AI（四角星） */
    val Sparkle: ImageVector by lazy {
        build("sparkle") {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(12.9f, 6.2f, 15.8f, 9.1f, 20f, 10f)
                curveTo(15.8f, 10.9f, 12.9f, 13.8f, 12f, 18f)
                curveTo(11.1f, 13.8f, 8.2f, 10.9f, 4f, 10f)
                curveTo(8.2f, 9.1f, 11.1f, 6.2f, 12f, 2f)
                close()
                moveTo(19f, 15f)
                curveTo(19.4f, 17f, 20.9f, 18.6f, 23f, 19f)
                curveTo(20.9f, 19.4f, 19.4f, 21f, 19f, 23f)
                curveTo(18.6f, 21f, 17.1f, 19.4f, 15f, 19f)
                curveTo(17.1f, 18.6f, 18.6f, 17f, 19f, 15f)
                close()
            }
        }
    }

    private fun build(
        name: String,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit,
    ): ImageVector = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()
}
