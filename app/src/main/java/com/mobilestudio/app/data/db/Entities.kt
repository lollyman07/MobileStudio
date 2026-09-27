package com.mobilestudio.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val sceneId: String,
    val type: String,
    val name: String,
    val visible: Boolean,
    val locked: Boolean,
    val zOrder: Int,
    val xNorm: Float,
    val yNorm: Float,
    val widthNorm: Float,
    val heightNorm: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val cameraFacing: String,
    val imageUri: String?,
    val videoUri: String?,
    val videoLoop: Boolean,
    val videoMuted: Boolean,
    val webUrl: String?,
    val textContent: String,
    val textSizeSp: Float,
    val textBold: Boolean,
    val textColorArgb: Long,
    val textBackgroundArgb: Long,
    val textAlign: String,
    val colorArgb: Long
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val fileSizeBytes: Long,
    val createdAtMs: Long
)
