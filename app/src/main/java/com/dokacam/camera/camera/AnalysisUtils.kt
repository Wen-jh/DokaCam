package com.dokacam.camera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * YUV → Bitmap 转换 + AI 分析用例配置。
 *
 * CameraX 的 ImageAnalysis 输出 YUV_420_888，
 * ML Kit 的 InputImage.fromMediaImage 可直接吃，
 * 但我们还需要低分辨率 Bitmap 做色彩统计 → 统一在这里转。
 */
object AnalysisUtils {

    private const val TAG = "AnalysisUtils"

    /** ImageProxy → Bitmap（自动旋转 + 缩放到 maxDim） */
    fun imageProxyToBitmap(proxy: ImageProxy, maxDim: Int = 480): Bitmap? = try {
        val rot = proxy.imageInfo.rotationDegrees

        // 先走 ML Kit 兼容路径：NV21 → YuvImage → JPEG → Bitmap
        val nv21 = yuv420ToNv21(proxy)
        val yuv = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 80, out)
        val bytes = out.toByteArray()

        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        if (rot != 0) {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }

        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide > maxDim) {
            val s = maxDim.toFloat() / longSide
            val small = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * s).toInt().coerceAtLeast(1),
                (bmp.height * s).toInt().coerceAtLeast(1),
                true,
            )
            if (small !== bmp) bmp.recycle()
            bmp = small
        }
        bmp
    } catch (t: Throwable) {
        Log.w(TAG, "proxy→bitmap: ${t.message}")
        null
    }

    /** YUV_420_888（可能带 rowStride/pixelStride 偏移）→ NV21 */
    private fun yuv420ToNv21(proxy: ImageProxy): ByteArray {
        val width = proxy.width
        val height = proxy.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)

        // Y
        val yPlane = proxy.planes[0]
        val yBuf = yPlane.buffer.duplicate()
        var pos = 0
        if (yPlane.rowStride == width && yPlane.pixelStride == 1) {
            yBuf.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                yBuf.position(row * yPlane.rowStride)
                for (col in 0 until width step yPlane.pixelStride) {
                    nv21[pos++] = yBuf.get(row * yPlane.rowStride + col)
                }
            }
        }

        // VU 交错（NV21 = Y + V + U）
        val uPlane = proxy.planes[1]
        val vPlane = proxy.planes[2]
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                nv21[pos++] = vBuf.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                nv21[pos++] = uBuf.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
        }
        return nv21
    }

    /** 构建低分辨率分析用例（STRATEGY_KEEP_ONLY_LATEST，天然节流） */
    fun buildAnalyzer(
        facing: Int,
        onFrame: (Bitmap) -> Unit,
    ): ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setResolutionStrategy(
                    androidx.camera.core.resolutionselector.ResolutionStrategy(
                        android.util.Size(480, 640),
                        androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()
        .also { analyzer ->
            analyzer.setAnalyzer(analysisExecutor) { proxy ->
                try {
                    val bmp = imageProxyToBitmap(proxy, 480)
                    if (bmp != null) onFrame(bmp)
                } finally {
                    proxy.close()
                }
            }
        }

    /** 单线程低优先级：AI 分析绝不抢 UI/预览的资源 */
    private val analysisExecutor: java.util.concurrent.Executor by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "doka-ai").apply { priority = Thread.NORM_PRIORITY - 1 }
        }
    }
}
