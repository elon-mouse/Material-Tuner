package com.emouse.materialtuner

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TunerEngine {

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state

    private var job: Job? = null
    private var recorder: AudioRecord? = null

    fun setMicPermission(granted: Boolean) {
        _state.value = _state.value.copy(hasMicPermission = granted)
    }

    fun start() {
        if (_state.value.isListening) return
        if (!_state.value.hasMicPermission) return

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBuffer * 2).coerceAtLeast(sampleRate / 2)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        ).also { it.startRecording() }

        _state.value = _state.value.copy(isListening = true)

        job = CoroutineScope(Dispatchers.Default).launch {
            val shortBuf = ShortArray(bufferSize / 2)
            val floatBuf = FloatArray(shortBuf.size)

            // Publish at a steady rate so UI looks smooth
            val publishEveryMs = 33L // ~30 FPS
            var lastPublish = 0L

            // Low-pass smoothing (EMA) for output
            var hzSmooth = 0.0
            var centsSmooth = 0f
            val alphaHz = 0.15      // lower = smoother, higher = faster
            val alphaCents = 0.18f

            while (isActive) {
                val read = recorder?.read(shortBuf, 0, shortBuf.size) ?: 0
                if (read <= 0) continue

                // Convert to float + compute RMS (noise gate)
                var sumSq = 0.0
                for (i in 0 until read) {
                    val v = shortBuf[i] / 32768f
                    floatBuf[i] = v
                    sumSq += (v * v).toDouble()
                }
                val rms = kotlin.math.sqrt(sumSq / read.toDouble()).toFloat()

                // 🔒 Gate: ignore near-silence (tune this number)
                if (rms < 0.012f) {
                    // Optional: if you want the UI to "idle" in silence:
                    // _state.value = _state.value.copy(note="--", hz=0.0, cents=0f)
                    continue
                }

                val frame = floatBuf.copyOf(read)

                val hzRaw = PitchDetector.detectPitch(
                    input = frame,
                    sampleRate = sampleRate,
                    windowType = PitchDetector.WindowType.HANNING
                )

                // 🔒 Sanity clamp: ignore insane values
                if (hzRaw !in 50.0..2000.0) continue

                val (note, _, centsRaw) = PitchDetector.hzToNoteAndCents(hzRaw)

                // Smooth outputs (your existing EMA)
                hzSmooth = if (hzSmooth == 0.0) hzRaw else (hzSmooth + alphaHz * (hzRaw - hzSmooth))
                centsSmooth =
                    if (lastPublish == 0L) centsRaw else (centsSmooth + alphaCents * (centsRaw - centsSmooth))

                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastPublish >= publishEveryMs) {
                    lastPublish = now
                    _state.value = _state.value.copy(
                        note = note,
                        hz = hzSmooth,
                        cents = centsSmooth
                    )
                }
            }
        }
    }

            fun stop() {
        _state.value = _state.value.copy(isListening = false)

        job?.cancel()
        job = null

        recorder?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        recorder = null
    }
}
