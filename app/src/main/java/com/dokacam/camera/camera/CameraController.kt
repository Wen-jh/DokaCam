package com.dokacam.camera.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.dokacam.camera.gl.CameraGlView
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.roundToInt

/**
 * 可用镜头描述（0.5x / 1x / 2x / 3x…）
 */
data class LensOption(
    val zoomRatio: Float,          // 逻辑焦段
    val physicalId: Int,           // CameraX lensFacing 已隐含，这里存 ordinal
    val label: String,
)

/**
 * CameraX 统一控制器。
 *
 * 职责：
 *  - 绑定 Preview（→ GL 自定义管线）+ ImageCapture
 *  - 枚举物理镜头并给出「焦段条」数据
 *  - 变焦 / 点按对焦 / 曝光补偿 / 闪光灯 / 前后切换
 */
class CameraController(private val context: Context) {

    companion object { private const val TAG = "CameraController" }

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    private var cameraControl: CameraControl? = null

    var facing: Int = CameraSelector.LENS_FACING_BACK
        private set

    /** 设备支持的镜头焦段（升序） */
    var lensOptions: List<LensOption> = listOf(LensOption(1f, 0, "1x"))
        private set

    /** UI 当前选中的焦段 */
    var activeZoom: Float = 1f
        private set

    val zoomRange: ClosedFloatingPointRange<Float> get() {
        val c = camera?.cameraInfo?.zoomState?.value
        return if (c != null) c.minZoomRatio..c.maxZoomRatio else 1f..1f
    }

    // ------------------------------------------------------------------

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        glView: CameraGlView,
        executor: Executor,
        initialFacing: Int = CameraSelector.LENS_FACING_BACK,
        onAnalysisFrame: ((android.graphics.Bitmap) -> Unit)? = null,
    ) {
        val p = ProcessCameraProvider.awaitInstance(context)
        provider = p

        facing = initialFacing
        refreshLensOptions()

        preview = Preview.Builder()
            .setResolutionSelector(
                androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY,
                    )
                    .build(),
            )
            .build()
            .also { pr ->
                pr.setSurfaceProvider(executor) { request: SurfaceRequest ->
                    glView.handleSurfaceRequest(request, executor)
                }
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(97)
            .build()

        val analysis = onAnalysisFrame?.let { cb ->
            AnalysisUtils.buildAnalyzer(facing) { bmp ->
                cb(bmp)
            }
        }

        rebind(lifecycleOwner, analysis)
    }

    private fun rebind(
        lifecycleOwner: LifecycleOwner,
        analysis: ImageAnalysis? = null,
    ) {
        val p = provider ?: return
        p.unbindAll()

        val selector = CameraSelector.Builder()
            .requireLensFacing(facing)
            .build()

        try {
            val useCases = mutableListOf<androidx.camera.core.UseCase>(preview!!, imageCapture!!)
            analysis?.let { useCases += it }
            camera = p.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
            cameraControl = camera?.cameraControl
            applyZoom(activeZoom)
            Log.i(TAG, "bound: facing=$facing lenses=${lensOptions.map { it.zoomRatio }}")
        } catch (e: Exception) {
            Log.e(TAG, "bind failed", e)
        }
    }

    // ------------------------------------------------------------------
    // 镜头 / 变焦

    private fun refreshLensOptions() {
        val p = provider ?: return
        val info = try {
            p.getCameraInfo(
                CameraSelector.Builder().requireLensFacing(facing).build(),
            )
        } catch (e: Exception) {
            null
        }

        // 通过 zoomState 判断镜头能力：
        //  minZoomRatio < 1  → 有超广角（0.5x）
        //  maxZoomRatio ≥ 2  → 有长焦或高倍数码变焦
        val zoom = info?.zoomState?.value
        val minRatio = zoom?.minZoomRatio ?: 1f
        val maxRatio = zoom?.maxZoomRatio ?: 1f

        val opts = mutableListOf(LensOption(1f, 0, "1x"))
        if (minRatio < 0.95f) opts.add(0, LensOption(0.5f, 0, "0.5x"))
        if (maxRatio >= 1.9f) opts += LensOption(2f, 2, "2x")
        if (maxRatio >= 2.9f) opts += LensOption(3f, 3, "3x")
        if (maxRatio >= 4.9f) opts += LensOption(5f, 5, "5x")
        lensOptions = opts
    }

    fun applyZoom(ratio: Float) {
        activeZoom = ratio
        cameraControl?.setZoomRatio(ratio)
    }

    /** 捏合变焦（连续值） */
    fun applyPinchZoom(scaleFromOne: Float) {
        val range = zoomRange
        applyZoom((1f * scaleFromOne).coerceIn(range.start, range.endInclusive))
    }

    fun switchCamera() {
        facing = if (facing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        activeZoom = 1f
    }

    fun rebindAfterSwitch(lifecycleOwner: LifecycleOwner, onAnalysisFrame: ((android.graphics.Bitmap) -> Unit)? = null) {
        refreshLensOptions()
        rebind(lifecycleOwner, onAnalysisFrame?.let { AnalysisUtils.buildAnalyzer(facing, it) })
    }

    // ------------------------------------------------------------------
    // 对焦 / 曝光

    /** 点按对焦：把屏幕归一化坐标转成 MeteringPoint */
    fun focusAt(
        glView: CameraGlView,
        normX: Float,   // 0..1
        normY: Float,
    ) {
        val factory = androidx.camera.view.SurfaceOrientedMeteringPointFactory(
            glView.width.toFloat(), glView.height.toFloat(),
        )
        val point = factory.createPoint(normX * glView.width, normY * glView.height)
        cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(point).build(),
        )
    }

    fun setExposureCompensation(value: Int) {
        val info = camera?.cameraInfo ?: return
        val range = info.exposureState.exposureCompensationRange
        val v = value.coerceIn(range.lower, range.upper)
        cameraControl?.setExposureCompensationIndex(v)
    }

    // ------------------------------------------------------------------
    // 闪光灯

    fun setFlashMode(mode: Int) {
        imageCapture?.flashMode = mode
    }

    fun setTorch(on: Boolean) {
        cameraControl?.enableTorch(on)
    }

    // ------------------------------------------------------------------
    // 拍照

    /**
     * 抓取原始高分辨率帧（输出到临时文件），随后由 CaptureProcessor 应用滤镜。
     */
    fun takeRawShot(
        executor: Executor,
        outputFile: File,
        onSaved: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val ic = imageCapture ?: run { onError("camera not bound"); return }
        val opts = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        ic.takePicture(
            opts, executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onSaved(outputFile)
                }

                override fun onError(ex: ImageCaptureException) {
                    Log.e(TAG, "takePicture failed", ex)
                    onError(ex.message ?: "unknown")
                }
            },
        )
    }

    fun unbind() {
        provider?.unbindAll()
    }
}
