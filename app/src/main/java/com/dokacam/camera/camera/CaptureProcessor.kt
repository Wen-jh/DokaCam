package com.dokacam.camera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.EGLContext
import android.util.Log
import com.dokacam.camera.data.model.FilterParams
import com.dokacam.camera.gl.FilterProgram2D
import com.dokacam.camera.gl.OffscreenFilterSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 拍照后处理：
 *  1. 解码 CameraX 输出的原始 JPEG
 *  2. （可选）CCD 低清降采样
 *  3. 离屏 GL 跑与预览同一套滤镜（quality=full）
 *  4. （可选）绘制复古日期水印
 *  5. 回写 JPEG
 */
class CaptureProcessor(private val context: Context) {

    companion object { private const val TAG = "CaptureProcessor" }

    data class Options(
        val params: FilterParams,
        val intensity: Float,
        val mirror: Boolean,
        val ccdMode: Boolean,
        val dateStamp: Boolean,
        val dateStampFormat: String,
        val modelStamp: Boolean,
        val jpegQuality: Int,
    )

    private var session: OffscreenFilterSession? = null

    fun process(inputFile: File, options: Options): Bitmap? = try {
        // 1. 解码（最大 50MP 防御）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(inputFile.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth * bounds.outHeight / (sample * sample) > 50_000_000) sample *= 2
        val raw = BitmapFactory.decodeFile(
            inputFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        var bmp = raw

        // 2. CCD 低清：降采样再升回，形成「糊出来的低像素」
        if (options.ccdMode) {
            val scale = 0.35f
            val small = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(320),
                (bmp.height * scale).toInt().coerceAtLeast(240),
                true,
            )
            bmp = Bitmap.createScaledBitmap(small, bmp.width, bmp.height, false)
            small.recycle()
        }

        // 3. 滤镜（全质量）
        if (options.intensity > 0.01f) {
            val out = render(bmp, options)
            if (out != null) {
                bmp.recycle()
                bmp = out
            }
        }

        // 4. 水印
        if (options.dateStamp || options.modelStamp) {
            val stamped = drawStamps(bmp, options)
            if (stamped != bmp) {
                bmp.recycle()
                bmp = stamped
            }
        }

        bmp
    } catch (t: Throwable) {
        Log.e(TAG, "process failed", t)
        null
    }

    private fun render(input: Bitmap, options: Options): Bitmap? = try {
        val s = session ?: OffscreenFilterSession().also { session = it }
        s.process(input) { prog: FilterProgram2D ->
            prog.setMirror(options.mirror)
            prog.setQuality(full = true)
            prog.setTime(0f)
            prog.applyParams(options.params, options.intensity)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "GL render unavailable, fallback to original", t)
        null
    }

    /** 复古橙色日期戳 —— CCD 时代的经典视觉符号 */
    private fun drawStamps(src: Bitmap, options: Options): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val h = src.height
        val textSize = h * 0.035f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 180, 80)
            textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(textSize * 0.06f, 0f, 0f, Color.argb(90, 255, 140, 40))
        }

        if (options.dateStamp) {
            val text = SimpleDateFormat(options.dateStampFormat, Locale.CHINA)
                .format(Date())
                .replace("'", "’")
            val x = src.width * 0.06f
            val y = src.height * 0.88f
            canvas.drawText(text, x, y, paint)
        }
        if (options.modelStamp) {
            val model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".uppercase()
            val p2 = Paint(paint).apply {
                textSize = h * 0.022f
                color = Color.argb(200, 255, 210, 140)
            }
            canvas.drawText(model, src.width * 0.06f, src.height * 0.94f, p2)
        }
        return out
    }

    fun release() {
        session?.release()
        session = null
    }
}
