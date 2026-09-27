package com.mobilestudio.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mobilestudio.app.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "studio_settings")

/** All user-configurable settings (Section 14), persisted across restarts via DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val RES_WIDTH = intPreferencesKey("res_width")
        val RES_HEIGHT = intPreferencesKey("res_height")
        val FPS = intPreferencesKey("fps")
        val BITRATE = intPreferencesKey("bitrate")
        val CODEC = stringPreferencesKey("codec")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PERF_MODE = booleanPreferencesKey("perf_mode")
        val PREVIEW_LOW = booleanPreferencesKey("preview_low")
        val DEFAULT_CAMERA = stringPreferencesKey("default_camera")
    }

    val videoSettings: Flow<VideoOutputSettings> = context.dataStore.data.map { p ->
        VideoOutputSettings(
            preset = p[Keys.QUALITY_PRESET]?.let { runCatching { QualityPreset.valueOf(it) }.getOrNull() } ?: QualityPreset.MEDIUM,
            resolution = ResolutionPreset.P720,
            customWidth = p[Keys.RES_WIDTH] ?: 1280,
            customHeight = p[Keys.RES_HEIGHT] ?: 720,
            fps = p[Keys.FPS] ?: 30,
            bitrateKbps = p[Keys.BITRATE] ?: 4000,
            codec = p[Keys.CODEC] ?: "H.264 (AVC)",
            audioEnabled = p[Keys.AUDIO_ENABLED] ?: true
        )
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { p ->
        p[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: AppThemeMode.DARK
    }

    val performanceSettings: Flow<PerformanceSettings> = context.dataStore.data.map { p ->
        PerformanceSettings(
            previewQualityLow = p[Keys.PREVIEW_LOW] ?: false,
            performanceMode = p[Keys.PERF_MODE] ?: false
        )
    }

    val defaultCameraFacing: Flow<CameraFacing> = context.dataStore.data.map { p ->
        p[Keys.DEFAULT_CAMERA]?.let { runCatching { CameraFacing.valueOf(it) }.getOrNull() } ?: CameraFacing.BACK
    }

    suspend fun setResolution(width: Int, height: Int) {
        context.dataStore.edit { it[Keys.RES_WIDTH] = width; it[Keys.RES_HEIGHT] = height }
    }
    suspend fun setFps(fps: Int) { context.dataStore.edit { it[Keys.FPS] = fps } }
    suspend fun setBitrate(kbps: Int) { context.dataStore.edit { it[Keys.BITRATE] = kbps } }
    suspend fun setCodec(codec: String) { context.dataStore.edit { it[Keys.CODEC] = codec } }
    suspend fun setAudioEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.AUDIO_ENABLED] = enabled } }
    suspend fun setQualityPreset(preset: QualityPreset) { context.dataStore.edit { it[Keys.QUALITY_PRESET] = preset.name } }
    suspend fun setThemeMode(mode: AppThemeMode) { context.dataStore.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setPerformanceMode(enabled: Boolean) { context.dataStore.edit { it[Keys.PERF_MODE] = enabled } }
    suspend fun setPreviewLow(enabled: Boolean) { context.dataStore.edit { it[Keys.PREVIEW_LOW] = enabled } }
    suspend fun setDefaultCamera(facing: CameraFacing) { context.dataStore.edit { it[Keys.DEFAULT_CAMERA] = facing.name } }
}
