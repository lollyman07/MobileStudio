package com.mobilestudio.app.model

enum class ResolutionPreset(val label: String, val width: Int, val height: Int) {
    P480("480p", 854, 480),
    P720("720p", 1280, 720),
    P1080("1080p", 1920, 1080),
    CUSTOM("Custom", 1280, 720)
}

enum class QualityPreset { LOW, MEDIUM, HIGH, CUSTOM }

data class VideoOutputSettings(
    val preset: QualityPreset = QualityPreset.MEDIUM,
    val resolution: ResolutionPreset = ResolutionPreset.P720,
    val customWidth: Int = 1280,
    val customHeight: Int = 720,
    val fps: Int = 30,
    val bitrateKbps: Int = 4000,
    val codec: String = "H.264 (AVC)",
    val audioEnabled: Boolean = true
)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

data class PerformanceSettings(
    val previewQualityLow: Boolean = false,
    val performanceMode: Boolean = false
)
