package com.mobilestudio.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Live microphone level meter (Section 9) — reads real PCM samples via AudioRecord and reports
 * RMS level in dBFS, independent from whatever AudioRecord instance the recorder itself may be
 * using (so the meter keeps working whether or not a recording is in progress).
 */
class AudioLevelMonitor {

    private val _levelDb = MutableStateFlow(-60f) // -60dB floor == silence
    val levelDb: StateFlow<Float> = _levelDb

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted

    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    var lastError: String? = null
        private set

    fun setMuted(muted: Boolean) { _muted.value = muted }

    fun start(scope: CoroutineScope) {
        val minBuf = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { lastError = "Unsupported audio configuration on this device"; return }
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
        } catch (e: SecurityException) {
            lastError = "Microphone permission denied"
            return
        }
        val rec = audioRecord ?: return
        rec.startRecording()
        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(minBuf / 2)
            while (isActive) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read > 0) {
                    if (_muted.value) {
                        _levelDb.value = -60f
                    } else {
                        var sum = 0.0
                        for (i in 0 until read) sum += buffer[i] * buffer[i].toDouble()
                        val rms = sqrt(sum / read)
                        val db = if (rms > 1) 20 * log10(rms / 32767.0) else -60.0
                        _levelDb.value = db.toFloat().coerceIn(-60f, 0f)
                    }
                }
                delay(60)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release(); audioRecord = null
    }
}
