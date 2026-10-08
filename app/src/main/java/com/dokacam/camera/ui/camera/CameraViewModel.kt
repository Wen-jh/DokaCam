package com.dokacam.camera.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dokacam.camera.ai.CompositionAnalyzer
import com.dokacam.camera.ai.CompositionResult
import com.dokacam.camera.ai.FilterRecommender
import com.dokacam.camera.camera.CaptureProcessor
import com.dokacam.camera.camera.CameraController
import com.dokacam.camera.data.media.MediaRepository
import com.dokacam.camera.data.media.PhotoItem
import com.dokacam.camera.data.model.CameraSettings
import com.dokacam.camera.data.model.FilterParams
import com.dokacam.camera.data.model.FilterPreset
import com.dokacam.camera.data.settings.SettingsRepository
import com.dokacam.camera.filter.FilterPresets
import com.dokacam.camera.gl.RenderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 拍摄界面状态机。
 *
 * 单一数据流：SettingsRepository(Flow) → settings → 派生 RenderConfig → GL 预览
 *                                  ↘ AI 分析循环 → composition / recommendations
 */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val mediaRepo = MediaRepository(app)
    val cameraController = CameraController(app)
    private val captureProcessor = CaptureProcessor(app)
    private val analyzer = CompositionAnalyzer()

    // ---------------- 状态 ----------------

    private val _settings = MutableStateFlow(CameraSettings())
    val settings: StateFlow<CameraSettings> = _settings.asStateFlow()

    private val _composition = MutableStateFlow<CompositionResult?>(null)
    val composition: StateFlow<CompositionResult?> = _composition.asStateFlow()

    private val _recommendations = MutableStateFlow<List<FilterRecommender.Recommendation>>(emptyList())
    val recommendations: StateFlow<List<FilterRecommender.Recommendation>> = _recommendations.asStateFlow()

    private val _currentPreset = MutableStateFlow(FilterPreset.NONE)
    val currentPreset: StateFlow<FilterPreset> = _currentPreset.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _lastPhoto = MutableStateFlow<PhotoItem?>(null)
    val lastPhoto: StateFlow<PhotoItem?> = _lastPhoto.asStateFlow()

    private val _timerCountdown = MutableStateFlow(0)
    val timerCountdown: StateFlow<Int> = _timerCountdown.asStateFlow()

    sealed interface CaptureState {
        data object Idle : CaptureState
        data object Focusing : CaptureState
        data class Capturing(val progress: Float) : CaptureState
        data class Processing(val stage: String) : CaptureState
        data object Saved : CaptureState
        data class Failed(val reason: String) : CaptureState
    }

    private var analysisJob: Job? = null
    private val tmpShotFile: File
        get() = File(getApplication<Application>().cacheDir, "doka_shot_tmp.jpg")

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                _settings.value = s
                _currentPreset.value = FilterPresets.get(s.selectedPresetId)
            }
        }
        viewModelScope.launch {
            mediaRepo.queryMyPhotos().firstOrNull()?.let { _lastPhoto.value = it }
        }
    }

    // ---------------- 设置变更 ----------------

    fun selectPreset(id: String) = viewModelScope.launch {
        settingsRepo.setSelectedPreset(id)
    }

    fun setFilterIntensity(v: Float) = viewModelScope.launch {
        settingsRepo.setFilterIntensity(v)
    }

    fun updateSetting(transform: suspend (CameraSettings) -> Unit) = viewModelScope.launch {
        transform(_settings.value)
    }

    fun toggleFlash() = viewModelScope.launch {
        val next = when (_settings.value.flashMode) {
            com.dokacam.camera.data.model.FlashMode.OFF -> com.dokacam.camera.data.model.FlashMode.AUTO
            com.dokacam.camera.data.model.FlashMode.AUTO -> com.dokacam.camera.data.model.FlashMode.ON
            com.dokacam.camera.data.model.FlashMode.ON -> com.dokacam.camera.data.model.FlashMode.TORCH
            com.dokacam.camera.data.model.FlashMode.TORCH -> com.dokacam.camera.data.model.FlashMode.OFF
        }
        settingsRepo.setFlashMode(next)
    }

    fun cycleGrid() = viewModelScope.launch {
        val all = com.dokacam.camera.data.model.GridType.entries
        val idx = all.indexOf(_settings.value.gridType)
        settingsRepo.setGridType(all[(idx + 1) % all.size])
    }

    fun cycleTimer() = viewModelScope.launch {
        val next = when (_settings.value.timerDelay.seconds) {
            0 -> com.dokacam.camera.data.model.TimerDelay.S_3
            3 -> com.dokacam.camera.data.model.TimerDelay.S_10
            else -> com.dokacam.camera.data.model.TimerDelay.OFF
        }
        settingsRepo.setTimerDelay(next)
    }

    fun toggleAi() = viewModelScope.launch {
        settingsRepo.setAiComposition(!_settings.value.aiCompositionEnabled)
    }

    fun switchCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner) = viewModelScope.launch {
        cameraController.switchCamera()
        cameraController.rebindAfterSwitch(lifecycleOwner) { bmp ->
            feedAnalysisFrame(bmp)
        }
    }

    // ---------------- 派生渲染配置 ----------------

    /** 组合「滤镜 + 美颜」为最终渲染参数（预览与拍照共用） */
    fun renderParams(settings: CameraSettings, preset: FilterPreset): FilterParams {
        if (!settings.beautyEnabled) return preset.params
        val b = settings.beautyIntensity
        return preset.params.copy(
            skinSoften = maxOf(preset.params.skinSoften, 0.35f * b),
            skinBrighten = maxOf(preset.params.skinBrighten, 0.20f * b),
        )
    }

    fun buildRenderConfig(): RenderConfig = RenderConfig(
        params = renderParams(_settings.value, _currentPreset.value),
        intensity = _settings.value.filterIntensity,
        mirror = cameraController.facing == CameraSelector.LENS_FACING_FRONT &&
            _settings.value.mirrorFrontCamera,
    )

    // ---------------- AI 分析循环 ----------------

    /** 由 CameraScreen 定时喂帧（不需要每帧，5 帧/秒足够） */
    fun feedAnalysisFrame(bitmap: Bitmap) {
        if (!_settings.value.aiCompositionEnabled &&
            !_settings.value.aiFilterRecommendEnabled
        ) return
        if (analysisJob?.isActive == true) return

        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val result = analyzer.analyze(bitmap)
                _composition.value = result
                if (_settings.value.aiFilterRecommendEnabled) {
                    val stats = FilterRecommender.analyzeFrame(bitmap)
                    _recommendations.value =
                        FilterRecommender.recommend(result.sceneKey, stats)
                }
            }
        }
    }

    // ---------------- 拍照 ----------------

    fun capture(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (_captureState.value is CaptureState.Capturing) return

        viewModelScope.launch {
            val s = _settings.value

            // ---- 定时倒计时 ----
            if (s.timerDelay.seconds > 0) {
                for (t in s.timerDelay.seconds downTo 1) {
                    _timerCountdown.value = t
                    delay(1000)
                }
                _timerCountdown.value = 0
            }

            _captureState.value = CaptureState.Capturing(0.2f)

            try {
                cameraController.setFlashMode(
                    when (s.flashMode) {
                        com.dokacam.camera.data.model.FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                        com.dokacam.camera.data.model.FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    },
                )
                cameraController.setTorch(s.flashMode == com.dokacam.camera.data.model.FlashMode.TORCH)

                // 1. 高分辨率原始帧 → 临时文件
                val rawFile = tmpShotFile
                _captureState.value = CaptureState.Processing("拍摄")

                val shot = withContext(Dispatchers.IO) {
                    awaitShot { onDone ->
                        cameraController.takeRawShot(
                            executor = Dispatchers.IO.asExecutor(),
                            outputFile = rawFile,
                            onSaved = { onDone(true) },
                            onError = { onDone(false) },
                        )
                    }
                }
                if (!shot) {
                    _captureState.value = CaptureState.Failed("拍照失败")
                    return@launch
                }

                // 2. 离屏滤镜 + 水印
                _captureState.value = CaptureState.Processing("滤镜处理")
                val preset = _currentPreset.value
                val processed = withContext(Dispatchers.Default) {
                    captureProcessor.process(
                        rawFile,
                        CaptureProcessor.Options(
                            params = renderParams(s, preset),
                            intensity = s.filterIntensity,
                            mirror = cameraController.facing == CameraSelector.LENS_FACING_FRONT &&
                                s.mirrorFrontCamera,
                            ccdMode = s.ccdModeEnabled,
                            dateStamp = s.dateStampEnabled,
                            dateStampFormat = s.dateStampFormat,
                            modelStamp = s.modelStampEnabled,
                            jpegQuality = s.jpegQuality,
                        ),
                    )
                }
                if (processed == null) {
                    _captureState.value = CaptureState.Failed("图像处理失败")
                    return@launch
                }

                // 3. 保存相册
                _captureState.value = CaptureState.Processing("保存")
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                val name = "DOKA_${stamp}.jpg"
                val uri = mediaRepo.saveBitmap(processed, name, quality = s.jpegQuality)
                processed.recycle()

                // 4. 可选：同时保存原图
                if (s.saveOriginal) {
                    val bytes = withContext(Dispatchers.IO) { rawFile.readBytes() }
                    mediaRepo.saveOriginalBytes(bytes, "DOKA_${stamp}_orig.jpg")
                }
                rawFile.delete()

                if (uri != null) {
                    _lastPhoto.value = PhotoItem(
                        id = 0, uri = uri, displayName = name,
                        dateTaken = System.currentTimeMillis(), width = 0, height = 0, size = 0,
                    )
                    _captureState.value = CaptureState.Saved
                    delay(1200)
                    _captureState.value = CaptureState.Idle
                } else {
                    _captureState.value = CaptureState.Failed("保存失败")
                }
            } catch (t: Throwable) {
                _captureState.value = CaptureState.Failed(t.message ?: "未知错误")
            }
        }
    }

    fun refreshLastPhoto() = viewModelScope.launch {
        _lastPhoto.value = mediaRepo.queryMyPhotos().firstOrNull()
    }

    override fun onCleared() {
        captureProcessor.release()
        cameraController.unbind()
        super.onCleared()
    }
}

/** 把回调式拍照 API 转成挂起 */
private suspend fun awaitShot(
    block: (onDone: (Boolean) -> Unit) -> Unit,
): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    block { ok ->
        if (cont.isActive) cont.resumeWith(Result.success(ok))
    }
}
