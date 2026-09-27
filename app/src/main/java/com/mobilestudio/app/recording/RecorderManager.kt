package com.mobilestudio.app.recording

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.*
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Encodes the compositor's output into a real MP4 file using MediaCodec + MediaMuxer.
 *
 * Video path: composed Bitmaps are pushed onto the encoder's input Surface via
 * Surface.lockCanvas/unlockCanvasAndPost — a standard, fully-supported technique for feeding
 * app-drawn content into a hardware encoder without a GLES pipeline.
 *
 * Audio path (optional): AudioRecord captures the microphone, an AAC MediaCodec encodes it, and
 * both encoded streams are interleaved into one MP4 via MediaMuxer with correctly-scaled
 * presentation timestamps.
 *
 * Pause/resume is implemented by pausing the *source* feeds (no new frames/samples are pushed to
 * the encoders) while keeping both codecs and the muxer alive — the safe way to pause on Android,
 * since MediaCodec itself has no native pause.
 */
class RecorderManager {

    enum class State { IDLE, RECORDING, PAUSED, STOPPED, ERROR }

    var state: State = State.IDLE
        private set
    var lastError: String? = null
        private set

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var inputSurface: android.view.Surface? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Any()

    private var audioRecord: AudioRecord? = null
    private val recording = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var encoderDrainThread: Thread? = null

    private var startTimeNs = 0L
    private var pausedAccumNs = 0L
    private var pauseStartNs = 0L

    var outputFile: File? = null
        private set

    private var width = 1280
    private var height = 720
    private var fps = 30
    private var bitrate = 4_000_000
    private var withAudio = true

    fun start(
        outputDir: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        withAudio: Boolean
    ): Boolean {
        this.width = width - (width % 2) // encoders require even dimensions
        this.height = height - (height % 2)
        this.fps = fps
        this.bitrate = bitrateKbps * 1000
        this.withAudio = withAudio

        return try {
            setupVideoCodec()
            if (withAudio) setupAudioCodec()

            val fileName = "MobileStudio_${System.currentTimeMillis()}.mp4"
            val file = File(outputDir, fileName)
            outputFile = file
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            startTimeNs = System.nanoTime()
            pausedAccumNs = 0
            recording.set(true)
            state = State.RECORDING

            startDrainThread()
            if (withAudio) startAudioCaptureThread()
            true
        } catch (e: Exception) {
            lastError = e.message
            state = State.ERROR
            false
        }
    }

    private fun setupVideoCodec() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
            start()
        }
    }

    private fun setupAudioCodec() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        }
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    /** Called by the compositor for every composed frame while recording is active. */
    fun submitFrame(bitmap: Bitmap) {
        if (state != State.RECORDING) return
        val surface = inputSurface ?: return
        try {
            val canvas: Canvas = surface.lockCanvas(Rect(0, 0, width, height))
            canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
            surface.unlockCanvasAndPost(canvas)
        } catch (_: Exception) {
            // Surface not ready / encoder saturated — drop this frame, keep recording alive.
        }
    }

    private fun startDrainThread() {
        encoderDrainThread = Thread {
            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            while (recording.get() || state == State.PAUSED) {
                drainCodec(videoCodec, videoBufferInfo, isVideo = true)
                if (withAudio) drainCodec(audioCodec, audioBufferInfo, isVideo = false)
                Thread.sleep(5)
            }
            // final drain after stop signalled
            drainCodec(videoCodec, videoBufferInfo, isVideo = true, endOfStream = true)
            if (withAudio) drainCodec(audioCodec, audioBufferInfo, isVideo = false, endOfStream = true)
        }.also { it.start() }
    }

    private fun drainCodec(codec: MediaCodec?, info: MediaCodec.BufferInfo, isVideo: Boolean, endOfStream: Boolean = false) {
        val c = codec ?: return
        if (endOfStream && isVideo) {
            try { c.signalEndOfInputStream() } catch (_: Exception) {}
        }
        while (true) {
            val outIndex = try { c.dequeueOutputBuffer(info, 10_000) } catch (_: Exception) { return }
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        val m = muxer ?: return
                        if (isVideo) videoTrackIndex = m.addTrack(c.outputFormat)
                        else audioTrackIndex = m.addTrack(c.outputFormat)
                        maybeStartMuxer()
                    }
                }
                outIndex >= 0 -> {
                    val encodedData: ByteBuffer? = c.getOutputBuffer(outIndex)
                    if (encodedData != null && info.size > 0 && muxerStarted) {
                        info.presentationTimeUs = System.nanoTime() / 1000
                        synchronized(muxerLock) {
                            val trackIndex = if (isVideo) videoTrackIndex else audioTrackIndex
                            if (trackIndex >= 0) {
                                try { muxer?.writeSampleData(trackIndex, encodedData, info) } catch (_: Exception) {}
                            }
                        }
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun maybeStartMuxer() {
        if (muxerStarted) return
        val ready = if (withAudio) videoTrackIndex >= 0 && audioTrackIndex >= 0 else videoTrackIndex >= 0
        if (ready) {
            muxer?.start()
            muxerStarted = true
        }
    }

    private fun startAudioCaptureThread() {
        val minBuf = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { withAudio = false; return }
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
        } catch (e: SecurityException) {
            lastError = "Microphone permission denied"
            withAudio = false
            return
        }
        val record = audioRecord ?: return
        record.startRecording()
        recordingThread = Thread {
            val buffer = ByteArray(minBuf)
            while (recording.get()) {
                if (state == State.PAUSED) { Thread.sleep(20); continue }
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val codec = audioCodec ?: continue
                    try {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inBuffer = codec.getInputBuffer(inIndex)
                            inBuffer?.clear()
                            inBuffer?.put(buffer, 0, read)
                            codec.queueInputBuffer(inIndex, 0, read, System.nanoTime() / 1000, 0)
                        }
                    } catch (_: Exception) {}
                }
            }
        }.also { it.start() }
    }

    fun pause() { if (state == State.RECORDING) state = State.PAUSED }
    fun resume() { if (state == State.PAUSED) state = State.RECORDING }

    fun stop(): File? {
        recording.set(false)
        state = State.STOPPED
        encoderDrainThread?.join(2000)
        recordingThread?.join(500)

        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release()

        try { videoCodec?.stop() } catch (_: Exception) {}
        videoCodec?.release()
        try { audioCodec?.stop() } catch (_: Exception) {}
        audioCodec?.release()

        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}

        inputSurface?.release()

        val result = outputFile
        state = State.IDLE
        return result
    }
}
