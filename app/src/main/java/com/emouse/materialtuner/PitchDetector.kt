package com.emouse.materialtuner

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

object PitchDetector {

    fun detectPitch(samples: FloatArray, sampleRate: Int): Double {
        // Remove DC offset
        var mean = 0f
        for (s in samples) mean += s
        mean /= samples.size
        for (i in samples.indices) samples[i] -= mean

        // Autocorrelation
        val minFreq = 65.0   // C2-ish
        val maxFreq = 1000.0 // high notes
        val maxLag = (sampleRate / minFreq).toInt()
        val minLag = (sampleRate / maxFreq).toInt().coerceAtLeast(2)

        var bestLag = -1
        var bestCorr = 0.0

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
            val denom = kotlin.math.sqrt(norm1 * norm2)
            if (denom > 0) corr /= denom

            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        // Reject low confidence
        if (bestLag <= 0 || bestCorr < 0.6) return 0.0

        return sampleRate.toDouble() / bestLag.toDouble()
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
