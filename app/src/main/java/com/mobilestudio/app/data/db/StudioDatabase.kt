package com.mobilestudio.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SceneEntity::class, SourceEntity::class, RecordingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun sceneDao(): SceneDao
    abstract fun sourceDao(): SourceDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile private var INSTANCE: StudioDatabase? = null

        fun get(context: Context): StudioDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "mobilestudio.db"
                ).build().also { INSTANCE = it }
            }
    }
}
