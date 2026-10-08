package com.dokacam.camera.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dokacam.camera.data.model.AspectRatio
import com.dokacam.camera.data.model.CameraSettings
import com.dokacam.camera.data.model.FlashMode
import com.dokacam.camera.data.model.GridType
import com.dokacam.camera.data.model.PhotoFormat
import com.dokacam.camera.data.model.TimerDelay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doka_settings")

/**
 * 设置仓库 —— DataStore 持久化，Flow 驱动 UI。
 * 所有键集中声明，避免魔法字符串散落各处。
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val aspect = stringPreferencesKey("aspect_ratio")
        val grid = stringPreferencesKey("grid_type")
        val flash = stringPreferencesKey("flash_mode")
        val timer = stringPreferencesKey("timer_delay")
        val level = booleanPreferencesKey("level_enabled")
        val tapCapture = booleanPreferencesKey("tap_to_capture")
        val saveOriginal = booleanPreferencesKey("save_original")
        val mirrorFront = booleanPreferencesKey("mirror_front")
        val shutterSound = booleanPreferencesKey("shutter_sound")
        val photoFormat = stringPreferencesKey("photo_format")
        val jpegQuality = intPreferencesKey("jpeg_quality")
        val aiComposition = booleanPreferencesKey("ai_composition")
        val aiRecommend = booleanPreferencesKey("ai_recommend")
        val beauty = booleanPreferencesKey("beauty_enabled")
        val beautyIntensity = floatPreferencesKey("beauty_intensity")
        val dateStamp = booleanPreferencesKey("date_stamp")
        val dateStampFormat = stringPreferencesKey("date_stamp_format")
        val locationStamp = booleanPreferencesKey("location_stamp")
        val modelStamp = booleanPreferencesKey("model_stamp")
        val ccdMode = booleanPreferencesKey("ccd_mode")
        val presetId = stringPreferencesKey("selected_preset")
        val filterIntensity = floatPreferencesKey("filter_intensity")
    }

    val settings: Flow<CameraSettings> = context.dataStore.data.map { p ->
        CameraSettings(
            aspectRatio = p[Keys.aspect]?.let { AspectRatio.fromLabel(it) } ?: AspectRatio.RATIO_4_3,
            gridType = GridType.fromName(p[Keys.grid] ?: "THIRDS"),
            flashMode = runCatching { FlashMode.valueOf(p[Keys.flash] ?: "OFF") }.getOrDefault(FlashMode.OFF),
            timerDelay = TimerDelay.fromName(p[Keys.timer] ?: "OFF"),
            levelEnabled = p[Keys.level] ?: true,
            tapToCapture = p[Keys.tapCapture] ?: false,
            saveOriginal = p[Keys.saveOriginal] ?: false,
            mirrorFrontCamera = p[Keys.mirrorFront] ?: true,
            shutterSound = p[Keys.shutterSound] ?: true,
            photoFormat = runCatching { PhotoFormat.valueOf(p[Keys.photoFormat] ?: "JPEG") }.getOrDefault(PhotoFormat.JPEG),
            jpegQuality = p[Keys.jpegQuality] ?: 95,
            aiCompositionEnabled = p[Keys.aiComposition] ?: true,
            aiFilterRecommendEnabled = p[Keys.aiRecommend] ?: true,
            beautyEnabled = p[Keys.beauty] ?: false,
            beautyIntensity = p[Keys.beautyIntensity] ?: 0.4f,
            dateStampEnabled = p[Keys.dateStamp] ?: false,
            dateStampFormat = p[Keys.dateStampFormat] ?: "yy M d",
            locationStampEnabled = p[Keys.locationStamp] ?: false,
            modelStampEnabled = p[Keys.modelStamp] ?: false,
            ccdModeEnabled = p[Keys.ccdMode] ?: false,
            selectedPresetId = p[Keys.presetId] ?: "none",
            filterIntensity = p[Keys.filterIntensity] ?: 1f,
        )
    }

    // ---------------- 逐项更新 ----------------

    suspend fun setAspectRatio(v: AspectRatio) = context.dataStore.edit { it[Keys.aspect] = v.label }
    suspend fun setGridType(v: GridType) = context.dataStore.edit { it[Keys.grid] = v.name }
    suspend fun setFlashMode(v: FlashMode) = context.dataStore.edit { it[Keys.flash] = v.name }
    suspend fun setTimerDelay(v: TimerDelay) = context.dataStore.edit { it[Keys.timer] = v.name }
    suspend fun setLevelEnabled(v: Boolean) = context.dataStore.edit { it[Keys.level] = v }
    suspend fun setTapToCapture(v: Boolean) = context.dataStore.edit { it[Keys.tapCapture] = v }
    suspend fun setSaveOriginal(v: Boolean) = context.dataStore.edit { it[Keys.saveOriginal] = v }
    suspend fun setMirrorFront(v: Boolean) = context.dataStore.edit { it[Keys.mirrorFront] = v }
    suspend fun setShutterSound(v: Boolean) = context.dataStore.edit { it[Keys.shutterSound] = v }
    suspend fun setPhotoFormat(v: PhotoFormat) = context.dataStore.edit { it[Keys.photoFormat] = v.name }
    suspend fun setJpegQuality(v: Int) = context.dataStore.edit { it[Keys.jpegQuality] = v }
    suspend fun setAiComposition(v: Boolean) = context.dataStore.edit { it[Keys.aiComposition] = v }
    suspend fun setAiRecommend(v: Boolean) = context.dataStore.edit { it[Keys.aiRecommend] = v }
    suspend fun setBeautyEnabled(v: Boolean) = context.dataStore.edit { it[Keys.beauty] = v }
    suspend fun setBeautyIntensity(v: Float) = context.dataStore.edit { it[Keys.beautyIntensity] = v }
    suspend fun setDateStampEnabled(v: Boolean) = context.dataStore.edit { it[Keys.dateStamp] = v }
    suspend fun setDateStampFormat(v: String) = context.dataStore.edit { it[Keys.dateStampFormat] = v }
    suspend fun setLocationStamp(v: Boolean) = context.dataStore.edit { it[Keys.locationStamp] = v }
    suspend fun setModelStamp(v: Boolean) = context.dataStore.edit { it[Keys.modelStamp] = v }
    suspend fun setCcdMode(v: Boolean) = context.dataStore.edit { it[Keys.ccdMode] = v }
    suspend fun setSelectedPreset(v: String) = context.dataStore.edit { it[Keys.presetId] = v }
    suspend fun setFilterIntensity(v: Float) = context.dataStore.edit { it[Keys.filterIntensity] = v }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
