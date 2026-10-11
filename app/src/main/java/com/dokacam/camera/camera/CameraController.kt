package com.dokacam.camera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.SensorManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dokacam.camera.data.media.MediaRepository
import com.dokacam.camera.data.model.AspectRatio
import com.dokacam.camera.data.model.FilterParams
import com.dokacam.camera.gl.CameraGlView
import com.dokacam.camera.gl.FilterBaker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    // 拍照管线：ImageProxy 解码/转正/裁剪/压缩走后台线程，结果回主线程。
    // shutdown() 会停掉二者（页面退出/权限态切换都会触发），start() 发现已停则重建，
    // 否则「授权弹窗 → DisposableEffect 先 dispose 后 restart」的流程会让拍照永久抛 RejectedExecutionException。
    @Volatile private var captureExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var saveScope = newSaveScope()
    private val mediaRepo = MediaRepository(context)
    private var shutterSound: MediaActionSound? = null

    /** 物理握持朝向：竖屏锁定 App 里 display rotation 恒为 0，
     *  拍照转正必须跟传感器监听到的真实朝向走，否则横握拍出的照片全侧立 */
    private var orientationListener: OrientationEventListener? = null
    @Volatile private var captureRotation = Surface.ROTATION_0

    fun start(owner: LifecycleOwner) {
        lifecycleOwner = owner
        started = true
        // shutdown 后再 start（权限弹窗流程/页面重进）：重建已停的后台执行器与保存作用域
        if (captureExecutor.isShutdown) captureExecutor = Executors.newSingleThreadExecutor()
        if (!saveScope.isActive) saveScope = newSaveScope()
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
                        // 预览同步跟手（竖屏锁定：display rotation 恒 0，只能靠物理方向补偿）
                        glView?.orientationCompCw = when (rot) {
                            Surface.ROTATION_90 -> 90
                            Surface.ROTATION_180 -> 180
                            Surface.ROTATION_270 -> 270
                            else -> 0
                        }
                    }
                }
            }
        }
        // 每次 start 都重新 enable：shutdown 只 disable 不置空，不恢复会导致物理方向补偿静默失效
        orientationListener?.let { if (it.canDetectOrientation()) it.enable() }
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
        val fallback = if (frontFacing) CameraSelector.DEFAULT_BACK_CAMERA
                       else CameraSelector.DEFAULT_FRONT_CAMERA

        // 成片用全传感器画幅（自然 4:3），不做 ViewPort 屏幕比例裁剪——
        // 否则照片被裁成取景框的细长条（如 1888x4096），观感像被放大/广角畸变。
        // 预览的满屏裁剪由 GL 层 FILL_CENTER 负责（仅显示层，不影响成片）。
        camera = try {
            provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
        } catch (e: Exception) {
            Log.e(TAG, "bindToLifecycle failed, trying other lens", e)
            // 绑定失败时回退另一面，避免 unbindAll 后永久黑屏；并回滚朝向状态
            try {
                provider.bindToLifecycle(owner, fallback, *useCases.toTypedArray()).also {
                    frontFacing = !frontFacing
                }
            } catch (e2: Exception) {
                Log.e(TAG, "fallback bind failed", e2)
                null
            }
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

    private fun newSaveScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 对焦测光。入参为预览缓冲归一化坐标（0..1，缓冲原始方向，y 向下）——
     * ViewPort 移除后视图只显示缓冲 FILL_CENTER 裁剪的一部分，视图像素不能直接当
     * surface 坐标；视图→缓冲的逆映射由 [CameraGlView.mapViewPointToBuffer] 完成。
     */
    fun focusAt(bufferX: Float, bufferY: Float): Boolean {
        val cam = camera ?: return false
        val gl = glView ?: return false
        val bw = gl.bufferWidth
        val bh = gl.bufferHeight
        if (bw <= 0 || bh <= 0) return false
        val factory = SurfaceOrientedMeteringPointFactory(bw.toFloat(), bh.toFloat())
        val point = factory.createPoint(
            (bufferX * bw).coerceIn(0f, bw.toFloat()),
            (bufferY * bh).coerceIn(0f, bh.toFloat()),
        )
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).disableAutoCancel().build()
        cam.cameraControl.startFocusAndMetering(action)
        return true
    }

    /**
     * 拍照：OnImageCapturedCallback 拿 JPEG ImageProxy → 转正 →（前摄按需镜像）→
     * 按画幅裁剪 →（[filterParams] 非中性时 GL 烘焙滤镜，与预览同一 Uber-Shader）→
     * 按 jpegQuality 压缩 → 写系统相册 Pictures/AICam。
     * saveOriginal 时额外保存一张「转正但未镜像/未裁剪/未烘焙」的原图（quality 95）。
     * 成功回调给出 MediaStore Uri。
     *
     * 转正走像素级旋转（proxy.imageInfo.rotationDegrees，由 imageCapture.targetRotation
     * 驱动，targetRotation 又跟物理握持方向），成片 EXIF 恒为正向 —— 与预览方向链路同源。
     */
    fun takePhoto(
        jpegQuality: Int = 95,
        saveOriginal: Boolean = false,
        mirrorFront: Boolean = true,
        aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
        shutterSoundOn: Boolean = true,
        filterParams: FilterParams? = null,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit,
    ) {
        val capture = imageCapture ?: run { onError("相机未就绪"); return }
        if (shutterSoundOn) playShutterSound()
        val isFront = frontFacing
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(proxy: ImageProxy) {
                try {
                    val rotation = proxy.imageInfo.rotationDegrees
                    val buf = proxy.planes[0].buffer
                    val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    proxy.close()

                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw IllegalStateException("JPEG 解码失败")

                    // 按 rotationDegrees 转正（OnImageCapturedCallback 不写 EXIF，像素直接转）
                    val rotated = if (rotation != 0) {
                        val m = Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                            .also { if (it != decoded) decoded.recycle() }
                    } else decoded

                    // 前摄镜像：让成片与取景画面一致（所见即所得）
                    val oriented = if (isFront && mirrorFront) {
                        val m = Matrix().apply { postScale(-1f, 1f) }
                        Bitmap.createBitmap(rotated, 0, 0, rotated.width, rotated.height, m, true)
                    } else rotated

                    val cropped = cropToAspect(oriented, aspectRatio)

                    // 滤镜烘焙：解码/转正/裁剪之后、压缩之前，全效档与预览同一 shader
                    val finalBmp = if (filterParams != null && !filterParams.isNeutral()) {
                        val baked = FilterBaker.bake(cropped, filterParams)
                        if (baked !== cropped && cropped !== oriented && cropped !== rotated) {
                            cropped.recycle()
                        }
                        baked
                    } else cropped

                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                    saveScope.launch {
                        val uri = mediaRepo.saveBitmap(
                            finalBmp, "AICam_$stamp.jpg", quality = jpegQuality
                        )
                        // 原图：转正但不做镜像/裁剪，供后期再加工
                        if (saveOriginal) {
                            mediaRepo.saveBitmap(rotated, "AICam_${stamp}_orig.jpg", quality = 95)
                        }
                        mainHandler.post {
                            if (uri != null) onSaved(uri) else onError("写入相册失败")
                        }
                    }
                } catch (e: Exception) {
                    runCatching { proxy.close() }
                    Log.e(TAG, "capture process failed", e)
                    mainHandler.post { onError(e.message ?: "拍摄失败") }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                mainHandler.post { onError(exception.message ?: "拍摄失败") }
            }
        })
    }

    /** 系统快门音（MediaActionSound 必须在主线程 load/play） */
    private fun playShutterSound() {
        runCatching {
            val s = shutterSound ?: MediaActionSound().also {
                it.load(MediaActionSound.SHUTTER_CLICK)
                shutterSound = it
            }
            s.play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    /**
     * 居中裁剪到目标画幅。枚举比例按横屏语义（4:3、16:9 宽>高），
     * 竖构图位图自动取倒数；与当前比例几乎一致（默认 4:3 直出）时不裁。
     */
    private fun cropToAspect(src: Bitmap, aspect: AspectRatio): Bitmap {
        val w = src.width
        val h = src.height
        val portrait = h > w
        val r = aspect.ratio
        val target = if (portrait) (if (r >= 1f) 1f / r else r) else (if (r >= 1f) r else 1f / r)
        val current = w.toFloat() / h
        if (abs(current - target) < 0.01f) return src
        return if (current > target) {
            val nw = (h * target).toInt().coerceIn(1, w)
            Bitmap.createBitmap(src, (w - nw) / 2, 0, nw, h)
        } else {
            val nh = (w / target).toInt().coerceIn(1, h)
            Bitmap.createBitmap(src, 0, (h - nh) / 2, w, nh)
        }
    }

    fun shutdown() {
        started = false
        orientationListener?.disable()
        try { cameraProvider?.unbindAll() } catch (e: Exception) { Log.w(TAG, "unbind", e) }
        runCatching { shutterSound?.release() }
        shutterSound = null
        captureExecutor.shutdown()
        saveScope.cancel()
    }
}
