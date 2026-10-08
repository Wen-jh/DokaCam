package com.dokacam.camera.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dokacam.camera.data.model.CameraSettings
import com.dokacam.camera.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val settings: StateFlow<CameraSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraSettings())

    fun setSaveOriginal(v: Boolean) = launch { repo.setSaveOriginal(v) }
    fun setShutterSound(v: Boolean) = launch { repo.setShutterSound(v) }
    fun setTapToCapture(v: Boolean) = launch { repo.setTapToCapture(v) }
    fun setLevelEnabled(v: Boolean) = launch { repo.setLevelEnabled(v) }
    fun setMirrorFront(v: Boolean) = launch { repo.setMirrorFront(v) }
    fun setAspectRatio(v: com.dokacam.camera.data.model.AspectRatio) = launch { repo.setAspectRatio(v) }
    fun setPhotoFormat(v: com.dokacam.camera.data.model.PhotoFormat) = launch { repo.setPhotoFormat(v) }
    fun setJpegQuality(v: Int) = launch { repo.setJpegQuality(v) }
    fun setCcdMode(v: Boolean) = launch { repo.setCcdMode(v) }
    fun setDateStamp(v: Boolean) = launch { repo.setDateStampEnabled(v) }
    fun setDateStampFormat(v: String) = launch { repo.setDateStampFormat(v) }
    fun setModelStamp(v: Boolean) = launch { repo.setModelStamp(v) }
    fun setAiComposition(v: Boolean) = launch { repo.setAiComposition(v) }
    fun setAiRecommend(v: Boolean) = launch { repo.setAiRecommend(v) }
    fun setBeauty(v: Boolean) = launch { repo.setBeautyEnabled(v) }
    fun setBeautyIntensity(v: Float) = launch { repo.setBeautyIntensity(v) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
