package com.emouse.materialtuner

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PitchDetector {

    enum class WindowType { HANNING, HAMMING }

    private fun applyWindow(samples: FloatArray, type: WindowType) {
        val n = samples.size
        if (n <= 1) return
        val denom = (n - 1).toDouble()

        for (i in 0 until n) {
            val x = i / denom
            val w = when (type) {
                WindowType.HANNING -> (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * x))
                WindowType.HAMMING -> (0.54 - 0.46 * kotlin.math.cos(2.0 * Math.PI * x))
            }
            samples[i] = (samples[i] * w).toFloat()
        }
    }

    private fun removeDc(samples: FloatArray) {
        var mean = 0f
        for (s in samples) mean += s
        mean /= samples.size
        for (i in samples.indices) samples[i] -= mean
    }

    /**
     * Autocorrelation pitch detection (monophonic-friendly).
     * Returns Hz or 0.0 if not confident.
     */
    fun detectPitch(
        input: FloatArray,
        sampleRate: Int,
        windowType: WindowType = WindowType.HANNING
    ): Double {
        if (input.size < 64) return 0.0

        // Work on a copy so caller can reuse its buffer
        val samples = input.copyOf()

        // 1) Windowing to reduce leakage (helps stability)
        applyWindow(samples, windowType)

        // 2) Remove DC
        removeDc(samples)

        // Autocorrelation lag bounds
        val minFreq = 65.0     // ~C2
        val maxFreq = 1000.0   // high notes
        val maxLag = (sampleRate / minFreq).toInt()
        val minLag = (sampleRate / maxFreq).toInt().coerceAtLeast(2)

        var bestLag = -1
        var bestCorr = 0.0

        // Normalized autocorrelation
        for (lag in minLag..maxLag) {
            var corr = 0.0
            var norm1 = 0.0
            var norm2 = 0.0
            val limit = samples.size - lag
            for (i in 0 until limit) {
                val a = samples[i].toDouble()
                val b = samples[i + lag].toDouble()
                corr += a * b
                norm1 += a * a
                norm2 += b * b
            }
            val denom = sqrt(norm1 * norm2)
            if (denom > 0.0) corr /= denom

            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        // Confidence gate (tune if needed)
        if (bestLag <= 0 || bestCorr < 0.75) return 0.0

        // Optional: small parabolic refinement around bestLag
        // (improves accuracy a bit without FFT)
        val refinedLag = refineLag(samples, bestLag)

        return sampleRate.toDouble() / refinedLag
    }

    private fun refineLag(samples: FloatArray, lag: Int): Double {
        // Parabolic interpolation using correlation-like values at lag-1, lag, lag+1
        // If out of bounds, just return lag.
        if (lag <= 1 || lag >= samples.size - 2) return lag.toDouble()

        fun corrAt(l: Int): Double {
            var c = 0.0
            val limit = samples.size - l
            for (i in 0 until limit) c += (samples[i] * samples[i + l]).toDouble()
            return c
        }

        val c1 = corrAt(lag - 1)
        val c2 = corrAt(lag)
        val c3 = corrAt(lag + 1)

        val denom = (c1 - 2 * c2 + c3)
        if (denom == 0.0) return lag.toDouble()

        val delta = 0.5 * (c1 - c3) / denom
        return (lag.toDouble() + delta).coerceAtLeast(1.0)
    }

    fun hzToNoteAndCents(hz: Double, a4: Double = 440.0): Triple<String, Int, Float> {
        if (hz <= 0.0) return Triple("--", 0, 0f)

        val noteNames = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val midi = (69 + 12 * (ln(hz / a4) / ln(2.0))).roundToInt()
        val noteIndex = ((midi % 12) + 12) % 12
        val octave = (midi / 12) - 1
        val note = "${noteNames[noteIndex]}$octave"

        val targetHz = a4 * Math.pow(2.0, (midi - 69) / 12.0)
        val cents = (1200.0 * (ln(hz / targetHz) / ln(2.0))).toFloat()

        return Triple(note, midi, cents.coerceIn(-50f, 50f))
    }
}
