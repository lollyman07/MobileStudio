package com.mobilestudio.app.util

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Publishes a finished recording into the shared Movies collection via MediaStore (Section 7/18)
 * — the modern, scoped-storage-compliant way to make a file show up in the user's Gallery/Files
 * app without any broad storage permission.
 */
object MediaStoreHelper {

    fun publishRecording(context: Context, file: File): android.net.Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MobileStudio")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(file).use { input -> input.copyTo(out) }
        }
        return uri
    }

    fun appOutputDir(context: Context): File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
}
