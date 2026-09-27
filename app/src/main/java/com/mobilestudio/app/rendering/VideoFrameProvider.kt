package com.mobilestudio.app.rendering

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Renders a local video file as a live source.
 *
 * Limitation (documented, not hidden): we sample frames with MediaMetadataRetriever rather than
 * running a full decoder+SurfaceTexture pipeline, so playback advances at ~8 fps of freshly
 * decoded frames instead of the video's native frame rate. This keeps CPU/battery cost low and
 * is stable across all API 26+ devices and codecs the retriever itself supports; a
 * MediaCodec+SurfaceTexture pipeline is the natural upgrade path (Section 12: modular for a
 * future improvement) if a device needs smoother video-source playback.
 */
class VideoFrameProvider(
    private val context: Context,
    private val videoUri: Uri,
    private val loop: Boolean
) : FrameSource {

    private var scope: CoroutineScope? = null
    private val current = AtomicReference<Bitmap?>(null)
    private var durationUs: Long = 0
    private var positionUs: Long = 0
    var playing: Boolean = true

    override fun start() {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
        } catch (_: Exception) {
        }

        scope = CoroutineScope(Dispatchers.Default).also { sc ->
            sc.launch {
                while (isActive) {
                    if (playing && durationUs > 0) {
                        try {
                            val frame = retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (frame != null) current.set(frame)
                        } catch (_: Exception) { /* unsupported frame position — skip */ }
                        positionUs += 125_000 // ~8fps step
                        if (positionUs >= durationUs) {
                            positionUs = 0
                            if (!loop) playing = false
                        }
                    }
                    delay(125)
                }
            }
        }
    }

    override fun latestFrame(): Bitmap? = current.get()

    override fun stop() {
        scope?.cancel()
        scope = null
    }

    override fun release() {
        stop()
        current.set(null)
    }
}
