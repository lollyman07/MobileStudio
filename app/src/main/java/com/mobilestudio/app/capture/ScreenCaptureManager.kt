package com.mobilestudio.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import com.mobilestudio.app.rendering.FrameSource
import java.util.concurrent.atomic.AtomicReference

/**
 * Captures the device screen using Android's public MediaProjection API and exposes each frame
 * as a Bitmap so it can be composited exactly like any other source (Section 3).
 *
 * Android requires the user to explicitly grant this via a system permission dialog per capture
 * session (SYSTEM_ALERT-free, no root) — MediaProjectionManager.createScreenCaptureIntent().
 * Since Android 14 a foreground service of type `mediaProjection` must be running while capture
 * is active; see StudioForegroundService.
 */
class ScreenCaptureManager(private val context: Context) : FrameSource {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val latest = AtomicReference<Bitmap?>(null)
    private var handlerThread: HandlerThread? = null
    private var width = 720
    private var height = 1280
    private var density = DisplayMetrics.DENSITY_DEFAULT

    fun requestIntent(): Intent {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.createScreenCaptureIntent()
    }

    /** Call after the user grants permission via the system dialog (Activity.RESULT_OK). */
    fun begin(resultCode: Int, data: Intent, outputWidth: Int, outputHeight: Int, screenDensity: Int) {
        width = outputWidth
        height = outputHeight
        density = screenDensity
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mgr.getMediaProjection(resultCode, data)
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, null)

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val rowStride = plane.rowStride
                    val pixelStride = plane.pixelStride
                    val rowPadding = rowStride - pixelStride * width
                    val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(plane.buffer)
                    latest.set(if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height))
                } catch (_: Exception) {
                    // dropped frame
                } finally {
                    image.close()
                }
            }, handler)
        }

        virtualDisplay = projection.createVirtualDisplay(
            "MobileStudioScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )
    }

    override fun start() { /* no-op — capture begins via begin() once permission is granted */ }
    override fun latestFrame(): Bitmap? = latest.get()

    override fun stop() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        mediaProjection?.stop(); mediaProjection = null
        handlerThread?.quitSafely(); handlerThread = null
    }

    override fun release() {
        stop()
        latest.set(null)
    }

    val isActive: Boolean get() = mediaProjection != null

    companion object {
        fun isPermissionResult(requestCode: Int, expectedCode: Int) = requestCode == expectedCode
        const val REQUEST_CODE = 4201
    }
}
