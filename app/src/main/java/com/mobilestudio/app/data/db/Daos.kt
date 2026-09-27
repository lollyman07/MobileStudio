package com.mobilestudio.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneDao {
    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC")
    fun observeScenes(): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC")
    suspend fun getScenesOnce(): List<SceneEntity>

    @Upsert
    suspend fun upsert(scene: SceneEntity)

    @Upsert
    suspend fun upsertAll(scenes: List<SceneEntity>)

    @Query("DELETE FROM scenes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE sceneId = :sceneId ORDER BY zOrder ASC")
    fun observeSourcesForScene(sceneId: String): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY zOrder ASC")
    suspend fun getAllOnce(): List<SourceEntity>

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Upsert
    suspend fun upsertAll(sources: List<SourceEntity>)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sources WHERE sceneId = :sceneId")
    suspend fun deleteForScene(sceneId: String)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAtMs DESC")
    fun observeRecordings(): Flow<List<RecordingEntity>>

    @Upsert
    suspend fun upsert(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)
}
