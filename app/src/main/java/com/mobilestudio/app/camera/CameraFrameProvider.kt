package com.mobilestudio.app.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.mobilestudio.app.model.CameraFacing
import com.mobilestudio.app.rendering.FrameSource
import com.mobilestudio.app.rendering.YuvToRgbConverter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Real CameraX-backed source: opens the requested lens, streams frames via ImageAnalysis,
 * converts each to a Bitmap the compositor can draw. Supports front/back switching, and
 * degrades gracefully (keeps last good frame, reports `available=false`) if the camera is
 * taken by another app or the device lacks the requested lens.
 */
class CameraFrameProvider(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private var facing: CameraFacing,
    private val targetWidth: Int = 1280,
    private val targetHeight: Int = 720
) : FrameSource {

    private val latest = AtomicReference<Bitmap?>(null)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    @Volatile var available: Boolean = false
        private set
    @Volatile var lastError: String? = null
        private set

    override fun start() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindUseCases(provider)
            } catch (e: Exception) {
                lastError = e.message
                available = false
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(provider: ProcessCameraProvider) {
        provider.unbindAll()
        val selector = if (facing == CameraFacing.FRONT)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(targetWidth, targetHeight))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        YuvToRgbConverter.toBitmap(imageProxy)?.let { bmp -> latest.set(bmp) }
                    } catch (_: Exception) {
                        // drop malformed frame, keep last good one
                    } finally {
                        imageProxy.close()
                    }
                }
            }

        try {
            camera = provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            available = true
            lastError = null
        } catch (e: Exception) {
            available = false
            lastError = e.message ?: "Camera unavailable (in use by another app or unsupported)"
        }
    }

    fun switchFacing(newFacing: CameraFacing) {
        facing = newFacing
        cameraProvider?.let { bindUseCases(it) }
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun hasFlash(): Boolean = camera?.cameraInfo?.hasFlashUnit() ?: false

    override fun latestFrame(): Bitmap? = latest.get()

    override fun stop() {
        cameraProvider?.unbindAll()
        available = false
    }

    override fun release() {
        stop()
        latest.set(null)
        analysisExecutor.shutdown()
    }
}
