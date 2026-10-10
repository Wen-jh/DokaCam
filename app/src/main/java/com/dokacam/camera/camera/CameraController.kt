package com.dokacam.camera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dokacam.camera.gl.CameraGlView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraController(private val context: Context) {

    companion object { private const val TAG = "CameraController" }

    var glView: CameraGlView? = null

    /** AI 分析帧回调（已旋转到显示方向、缩到 480px）。null = 不绑定 ImageAnalysis。
     *  开关 AI 直接在回调里丢帧即可，无需重绑相机。 */
    @Volatile var onAnalysisFrame: ((Bitmap) -> Unit)? = null

    @Volatile var frontFacing = false
        private set
    @Volatile private var flashMode = ImageCapture.FLASH_MODE_OFF
    @Volatile private var torchOn = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: androidx.camera.core.Camera? = null
    private var lifecycleOwner: LifecycleOwner? = null
    @Volatile private var started = false

    /** 物理握持朝向：竖屏锁定 App 里 display rotation 恒为 0，
     *  拍照 EXIF 必须跟传感器监听到的真实朝向走，否则横握拍出的照片全侧立 */
    private var orientationListener: OrientationEventListener? = null
    @Volatile private var captureRotation = Surface.ROTATION_0

    fun start(owner: LifecycleOwner) {
        lifecycleOwner = owner
        started = true
        if (orientationListener == null) {
            orientationListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_UI) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    val rot = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                    if (rot != captureRotation) {
                        captureRotation = rot
                        imageCapture?.targetRotation = rot // 绑定前后设置均合法
                    }
                }
            }.also { if (it.canDetectOrientation()) it.enable() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!started) return@addListener
            try {
                cameraProvider = future.get()
                bind()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera() {
        frontFacing = !frontFacing
        bind()
    }

    /** EGL/ST 重建后由 GL 层回调：重新发起 SurfaceRequest 绑到新纹理 */
    fun rebindForNewSurface() {
        if (started) bind()
    }

    private fun bind() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        provider.unbindAll()

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
            .apply {
                flashMode = this@CameraController.flashMode
                targetRotation = captureRotation
            }
        imageCapture = capture

        // 黑屏修复4：预览经 SurfaceRequest 直供 GL 纹理
        val preview = Preview.Builder().build().also { p ->
            p.setSurfaceProvider { request ->
                val gl = glView
                if (gl != null) gl.supplySurfaceRequest(request)
                else request.willNotProvideSurface() // 无人接盘时显式拒绝，避免悬挂
            }
        }

        val selector = if (frontFacing) CameraSelector.DEFAULT_FRONT_CAMERA
                       else CameraSelector.DEFAULT_BACK_CAMERA

        // AI 分析用例：低分辨率 + KEEP_ONLY_LATEST，绝不拖累预览
        val analysis = if (onAnalysisFrame != null) {
            AnalysisUtils.buildAnalyzer(if (frontFacing) CameraSelector.LENS_FACING_FRONT
                                        else CameraSelector.LENS_FACING_BACK) { bmp ->
                onAnalysisFrame?.invoke(bmp) ?: bmp.recycle()
            }
        } else null

        val useCases = listOfNotNull(preview, imageCapture, analysis)

        // WYSIWYG：三个用例共享 ViewPort（取景框宽高比），成片 FOV 与预览一致，
        // 不再"拍到取景框外的东西"。cropRect 经 TransformationInfo 下发，GL 与 AI 映射均已支持。
        val gl = glView
        val vw = gl?.width?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val vh = gl?.height?.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .apply { analysis?.let { addUseCase(it) } }
            .setViewPort(
                ViewPort.Builder(Rational(vw, vh), Surface.ROTATION_0)
                    .setScaleType(ViewPort.FILL_CENTER)
                    .build(),
            )
            .build()

        fun bindTo(selector: CameraSelector, withViewport: Boolean): androidx.camera.core.Camera? = try {
            if (withViewport) provider.bindToLifecycle(owner, selector, group)
            else provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
        } catch (e: Exception) {
            Log.w(TAG, "bind($selector, viewport=$withViewport) failed: ${e.message}")
            null
        }

        val fallback = if (frontFacing) CameraSelector.DEFAULT_BACK_CAMERA
                       else CameraSelector.DEFAULT_FRONT_CAMERA
        camera = bindTo(selector, true)
            ?: bindTo(fallback, true)?.also { frontFacing = !frontFacing }
            ?: bindTo(fallback, false)?.also { frontFacing = !frontFacing } // 个别机型 ViewPort 异常时的保底
        if (camera == null) Log.e(TAG, "all bind attempts failed")
        glView?.frontFacing = frontFacing
        camera?.cameraControl?.enableTorch(torchOn)
    }

    fun setFlashMode(mode: Int) {
        flashMode = mode
        imageCapture?.flashMode = mode
    }

    fun setTorch(on: Boolean) {
        torchOn = on
        camera?.cameraControl?.enableTorch(on)
    }

    fun setZoomRatio(ratio: Float): Boolean {
        val control = camera?.cameraControl ?: return false
        control.setZoomRatio(ratio)
        return true
    }

    fun getZoomRatio(): Float =
        camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun getZoomRange(): Pair<Float, Float>? {
        val zs = camera?.cameraInfo?.zoomState?.value ?: return null
        return Pair(zs.minZoomRatio, zs.maxZoomRatio)
    }

    fun focusAt(x: Float, y: Float, viewWidth: Float, viewHeight: Float): Boolean {
        val cam = camera ?: return false
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight)
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).disableAutoCancel().build()
        cam.cameraControl.startFocusAndMetering(action)
        return true
    }

    fun takePhoto(onSaved: (Uri) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture ?: run { onError("相机未就绪"); return }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val name = "AICam_$stamp.jpg"
        val options = if (Build.VERSION.SDK_INT >= 29) {
            // 直接入库 MediaStore（owner=本包，Pictures/AICam）：
            // 应用内相册查得到、EXIF 方向原样保留、外部分享可见 —— 与系统相机一致
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AICam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ).build()
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                "AICam",
            ).apply { mkdirs() }
            ImageCapture.OutputFileOptions.Builder(File(dir, name)).build()
        }
        capture.takePicture(options, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    val uri = results.savedUri ?: run { onError("保存失败"); return }
                    if (Build.VERSION.SDK_INT >= 29) {
                        // CameraX 正常会自行清 IS_PENDING；这里兜底再清一次（幂等）
                        runCatching {
                            context.contentResolver.update(
                                uri,
                                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                                null, null,
                            )
                        }
                    }
                    onSaved(uri)
                }
                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "拍摄失败")
                }
            })
    }

    fun shutdown() {
        started = false
        orientationListener?.disable()
        try { cameraProvider?.unbindAll() } catch (e: Exception) { Log.w(TAG, "unbind", e) }
    }
}
