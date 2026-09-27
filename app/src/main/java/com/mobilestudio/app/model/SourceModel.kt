package com.mobilestudio.app.model

import java.util.UUID

data class SourceModel(
    val id: String = UUID.randomUUID().toString(),
    val sceneId: String,
    val type: SourceType,
    var name: String,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var zOrder: Int = 0,
    var transform: Transform = Transform(),

    var cameraFacing: CameraFacing = CameraFacing.BACK,
    var imageUri: String? = null,
    var videoUri: String? = null,
    var videoLoop: Boolean = true,
    var videoMuted: Boolean = false,
    var webUrl: String? = null,
    var textContent: String = "",
    var textSizeSp: Float = 32f,
    var textBold: Boolean = false,
    var textColorArgb: Long = 0xFFFFFFFF,
    var textBackgroundArgb: Long = 0x00000000,
    var textAlign: String = "left",
    var colorArgb: Long = 0xFF7C5CFF
)
