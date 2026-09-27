package com.mobilestudio.app.data

import android.content.Context
import com.mobilestudio.app.data.db.RecordingEntity
import com.mobilestudio.app.data.db.SceneEntity
import com.mobilestudio.app.data.db.SourceEntity
import com.mobilestudio.app.data.db.StudioDatabase
import com.mobilestudio.app.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for scenes/sources/recordings, backed by Room so the whole
 * project (scenes, sources, positions, settings) survives an app restart automatically.
 */
class StudioRepository(context: Context) {

    private val db = StudioDatabase.get(context)

    fun observeScenesWithSources(): Flow<List<SceneModel>> =
        db.sceneDao().observeScenes().map { sceneEntities ->
            sceneEntities.map { se ->
                SceneModel(id = se.id, name = se.name, order = se.orderIndex)
            }
        }

    suspend fun loadFullProject(): List<SceneModel> {
        val scenes = db.sceneDao().getScenesOnce()
        val allSources = db.sourceDao().getAllOnce()
        return scenes.map { se ->
            SceneModel(
                id = se.id,
                name = se.name,
                order = se.orderIndex,
                sources = allSources.filter { it.sceneId == se.id }
                    .sortedBy { it.zOrder }
                    .map { it.toModel() }
                    .toMutableList()
            )
        }.sortedBy { it.order }
    }

    suspend fun saveScene(scene: SceneModel) {
        db.sceneDao().upsert(SceneEntity(scene.id, scene.name, scene.order))
    }

    suspend fun saveScenes(scenes: List<SceneModel>) {
        db.sceneDao().upsertAll(scenes.map { SceneEntity(it.id, it.name, it.order) })
    }

    suspend fun deleteScene(sceneId: String) {
        db.sourceDao().deleteForScene(sceneId)
        db.sceneDao().delete(sceneId)
    }

    suspend fun saveSource(source: SourceModel) {
        db.sourceDao().upsert(source.toEntity())
    }

    suspend fun saveSources(sources: List<SourceModel>) {
        db.sourceDao().upsertAll(sources.map { it.toEntity() })
    }

    suspend fun deleteSource(sourceId: String) {
        db.sourceDao().delete(sourceId)
    }

    fun observeRecordings(): Flow<List<RecordingEntity>> = db.recordingDao().observeRecordings()

    suspend fun saveRecording(recording: RecordingEntity) {
        db.recordingDao().upsert(recording)
    }

    suspend fun deleteRecording(id: String) {
        db.recordingDao().delete(id)
    }
}

private fun SourceEntity.toModel(): SourceModel = SourceModel(
    id = id,
    sceneId = sceneId,
    type = SourceType.valueOf(type),
    name = name,
    visible = visible,
    locked = locked,
    zOrder = zOrder,
    transform = Transform(xNorm, yNorm, widthNorm, heightNorm, rotationDeg, opacity, cropLeft, cropTop, cropRight, cropBottom),
    cameraFacing = CameraFacing.valueOf(cameraFacing),
    imageUri = imageUri,
    videoUri = videoUri,
    videoLoop = videoLoop,
    videoMuted = videoMuted,
    webUrl = webUrl,
    textContent = textContent,
    textSizeSp = textSizeSp,
    textBold = textBold,
    textColorArgb = textColorArgb,
    textBackgroundArgb = textBackgroundArgb,
    textAlign = textAlign,
    colorArgb = colorArgb
)

private fun SourceModel.toEntity(): SourceEntity = SourceEntity(
    id = id,
    sceneId = sceneId,
    type = type.name,
    name = name,
    visible = visible,
    locked = locked,
    zOrder = zOrder,
    xNorm = transform.xNorm,
    yNorm = transform.yNorm,
    widthNorm = transform.widthNorm,
    heightNorm = transform.heightNorm,
    rotationDeg = transform.rotationDeg,
    opacity = transform.opacity,
    cropLeft = transform.cropLeft,
    cropTop = transform.cropTop,
    cropRight = transform.cropRight,
    cropBottom = transform.cropBottom,
    cameraFacing = cameraFacing.name,
    imageUri = imageUri,
    videoUri = videoUri,
    videoLoop = videoLoop,
    videoMuted = videoMuted,
    webUrl = webUrl,
    textContent = textContent,
    textSizeSp = textSizeSp,
    textBold = textBold,
    textColorArgb = textColorArgb,
    textBackgroundArgb = textBackgroundArgb,
    textAlign = textAlign,
    colorArgb = colorArgb
)
