package app.apunta.audio

/**
 * Long recordings are transcribed in chunks of a few minutes so memory stays flat
 * (a 2-hour class would otherwise need ~460 MB of float samples) and progress can
 * be saved and resumed. Cutting at a fixed sample would split words in half, so
 * the cut is moved to the quietest moment near the target.
 */
object ChunkPlanner {
    const val SAMPLE_RATE = 16_000
    const val CHUNK_SECONDS = 300            // 5 minutes
    const val SEARCH_WINDOW_SECONDS = 15     // look for silence in the last 15 s
    private const val FRAME = SAMPLE_RATE / 50  // 20 ms

    /**
     * Returns the sample index (relative to [samples]) where the chunk should end.
     * [samples] holds the audio from the chunk start; if it is shorter than a full
     * chunk the whole buffer is used.
     */
    fun findCut(samples: FloatArray, length: Int = samples.size, targetSamples: Int = CHUNK_SECONDS * SAMPLE_RATE): Int {
        if (length <= targetSamples) return length
        val windowStart = (targetSamples - SEARCH_WINDOW_SECONDS * SAMPLE_RATE).coerceAtLeast(0)
        var bestIndex = targetSamples
        var bestEnergy = Double.MAX_VALUE
        var i = windowStart
        while (i + FRAME <= targetSamples) {
            var e = 0.0
            for (k in i until i + FRAME) e += samples[k] * samples[k]
            // Prefer later cuts slightly when energies are equal (bigger chunks).
            if (e <= bestEnergy) {
                bestEnergy = e
                bestIndex = i + FRAME / 2
            }
            i += FRAME
        }
        return bestIndex
    }

    /** RMS of a block, 0..1, used for the live waveform. */
    fun rms(samples: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var acc = 0.0
        for (i in 0 until length) {
            val v = samples[i] / 32768.0
            acc += v * v
        }
        return kotlin.math.sqrt(acc / length).toFloat()
    }
}
