package com.mobilestudio.app.recording

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mobilestudio.app.MainActivity
import com.mobilestudio.app.R
import com.mobilestudio.app.capture.ScreenCaptureManager
import com.mobilestudio.app.rendering.CompositorEngine
import com.mobilestudio.app.virtualcamera.VirtualCameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Foreground service (Section 16) that keeps the compositor, active recording, screen capture
 * and/or virtual-camera stream alive when the app is backgrounded, with the persistent
 * notification Android requires for these operation types. All resources (camera, microphone,
 * MediaProjection) are released in onDestroy — never leaked.
 */
class StudioForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): StudioForegroundService = this@StudioForegroundService
    }

    private val binder = LocalBinder()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val compositor = CompositorEngine()
    val recorder = RecorderManager()
    val virtualCameraManager = VirtualCameraManager()
    var screenCaptureManager: ScreenCaptureManager? = null
        private set

    private var recordingStartNs = 0L
    var isRecording = false
        private set
    var isStreaming = false
        private set
    var isCapturingScreen = false
        private set

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createChannel()
        compositor.addFrameListener { frame -> onComposedFrame(frame) }
        compositor.start()
    }

    private fun onComposedFrame(frame: Bitmap) {
        if (isRecording) recorder.submitFrame(frame)
        if (isStreaming) virtualCameraManager.submitFrame(frame)
    }

    fun startForegroundNotification(text: String) {
        val notification = buildNotification(text)
        val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, type)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel_recording), NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun beginScreenCapture(resultCode: Int, data: Intent, width: Int, height: Int, density: Int, sourceId: String) {
        val mgr = ScreenCaptureManager(this)
        mgr.begin(resultCode, data, width, height, density)
        screenCaptureManager = mgr
        compositor.registerFrameSource(sourceId, mgr)
        isCapturingScreen = true
        startForegroundNotification(getString(R.string.notif_capture_title))
    }

    fun stopScreenCapture(sourceId: String) {
        compositor.unregisterFrameSource(sourceId)
        screenCaptureManager?.stop()
        screenCaptureManager = null
        isCapturingScreen = false
        refreshNotificationOrStop()
    }

    fun startRecording(outputDir: java.io.File, width: Int, height: Int, fps: Int, bitrateKbps: Int, withAudio: Boolean): Boolean {
        compositor.setOutputSize(width, height)
        compositor.setTargetFps(fps)
        val ok = recorder.start(outputDir, width, height, fps, bitrateKbps, withAudio)
        if (ok) {
            isRecording = true
            recordingStartNs = System.nanoTime()
            startForegroundNotification(getString(R.string.notif_recording_title))
        }
        return ok
    }

    fun pauseRecording() = recorder.pause()
    fun resumeRecording() = recorder.resume()

    fun stopRecording(): java.io.File? {
        val file = recorder.stop()
        isRecording = false
        refreshNotificationOrStop()
        return file
    }

    fun startVirtualCamera(port: Int = 8080) {
        virtualCameraManager.start(scope, port)
        isStreaming = true
        startForegroundNotification(getString(R.string.notif_vcam_title))
    }

    fun stopVirtualCamera() {
        virtualCameraManager.stop()
        isStreaming = false
        refreshNotificationOrStop()
    }

    private fun refreshNotificationOrStop() {
        when {
            isRecording -> updateNotification(getString(R.string.notif_recording_title))
            isCapturingScreen -> updateNotification(getString(R.string.notif_capture_title))
            isStreaming -> updateNotification(getString(R.string.notif_vcam_title))
            else -> stopForegroundCompat()
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    override fun onDestroy() {
        recorder.stop()
        virtualCameraManager.stop()
        screenCaptureManager?.release()
        compositor.releaseAll()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "studio_recording_channel"
        const val NOTIF_ID = 9001
    }
}
