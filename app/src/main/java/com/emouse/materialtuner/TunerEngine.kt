package com.emouse.materialtuner

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

            while (isActive) {
                val read = recorder?.read(shortBuf, 0, shortBuf.size) ?: 0
                if (read <= 0) continue

                for (i in 0 until read) {
                    floatBuf[i] = shortBuf[i] / 32768f
                }

                val frame = floatBuf.copyOf(read)
                val hz = PitchDetector.detectPitch(frame, sampleRate)

                if (hz > 0.0) {
                    val (note, _, cents) = PitchDetector.hzToNoteAndCents(hz)
                    _state.value = _state.value.copy(
                        note = note,
                        hz = hz,
                        cents = cents
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
