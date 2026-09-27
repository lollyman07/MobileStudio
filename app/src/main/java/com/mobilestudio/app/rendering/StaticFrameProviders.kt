package com.mobilestudio.app.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** Decodes a still image once (PNG/JPG/WebP — all natively supported by BitmapFactory). */
class ImageFrameProvider(
    private val context: Context,
    private val uri: Uri
) : FrameSource {
    private var bitmap: Bitmap? = null

    override fun start() {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                bitmap = BitmapFactory.decodeStream(stream)
            }
        }
    }

    override fun latestFrame(): Bitmap? = bitmap
    override fun stop() {}
    override fun release() { bitmap = null }
}
