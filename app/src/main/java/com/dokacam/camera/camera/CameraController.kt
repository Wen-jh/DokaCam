package com.dokacam.camera.camera

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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

    @Volatile var frontFacing = false
        private set
    @Volatile private var flashMode = ImageCapture.FLASH_MODE_OFF
    @Volatile private var torchOn = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: androidx.camera.core.Camera? = null
    private var lifecycleOwner: LifecycleOwner? = null

    fun start(owner: LifecycleOwner) {
        lifecycleOwner = owner
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
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

    private fun bind() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        provider.unbindAll()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
            .apply { flashMode = this@CameraController.flashMode }

        // 黑屏修复4：预览经 SurfaceRequest 直供 GL 纹理
        val preview = Preview.Builder().build().also { p ->
            p.setSurfaceProvider { request -> glView?.supplySurfaceRequest(request) }
        }

        val selector = if (frontFacing) CameraSelector.DEFAULT_FRONT_CAMERA
                       else CameraSelector.DEFAULT_BACK_CAMERA

        camera = try {
            provider.bindToLifecycle(owner, selector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "bindToLifecycle failed", e)
            null
        }
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
            point, FocusMeteringAction.FLAG_AF, FocusMeteringAction.FLAG_AE
        ).disableAutoCancel().build()
        cam.cameraControl.startFocusAndMetering(action)
        return true
    }

    fun takePhoto(onSaved: (String) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture ?: run { onError("相机未就绪"); return }
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "AICam"
        ).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "AICam_$stamp.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    onSaved(file.absolutePath)
                }
                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "拍摄失败")
                }
            })
    }

    fun shutdown() {
        try { cameraProvider?.unbindAll() } catch (e: Exception) { Log.w(TAG, "unbind", e) }
    }
}
