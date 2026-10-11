package app.apunta

import app.apunta.audio.ChunkPlanner
import app.apunta.audio.Resampler
import app.apunta.billing.QuotaRules
import app.apunta.summary.LocalSummarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioTest {
    private fun sine(freq: Double, rate: Int, seconds: Double) =
        FloatArray((rate * seconds).toInt()) { sin(2 * PI * freq * it / rate).toFloat() * 0.5f }

    private fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Double {
        var s = 0.0; for (i in from until to) s += a[i] * a[i]; return sqrt(s / (to - from))
    }

    @Test fun resamplesLengthAndKeepsInBandTone() {
        val input = sine(440.0, 48_000, 2.0)
        val r = Resampler(48_000, 16_000)
        val out = r.process(input) + r.flush()
        assertTrue("length ~ 32000, was ${out.size}", abs(out.size - 32_000) <= 4)
        assertEquals(0.5 / sqrt(2.0), rms(out, 1000, 31_000), 0.02)
    }

    @Test fun removesOutOfBandTone() {
        // 10 kHz is above the 8 kHz Nyquist of the output and must not alias into it.
        val out = Resampler(48_000, 16_000).process(sine(10_000.0, 48_000, 1.0))
        assertTrue("alias energy ${rms(out, 500, out.size - 500)}", rms(out, 500, out.size - 500) < 0.05)
    }

    @Test fun chunkedProcessingMatchesWholeProcessing() {
        val input = sine(300.0, 44_100, 3.0)
        val whole = Resampler(44_100, 16_000).let { it.process(input) + it.flush() }
        val r = Resampler(44_100, 16_000)
        val parts = ArrayList<Float>()
        var i = 0
        val sizes = intArrayOf(1000, 777, 4096, 513)
        var k = 0
        while (i < input.size) {
            val n = minOf(sizes[k++ % sizes.size], input.size - i)
            r.process(input.copyOfRange(i, i + n)).forEach { parts.add(it) }
            i += n
        }
        r.flush().forEach { parts.add(it) }
        assertTrue("lengths ${whole.size} vs ${parts.size}", abs(whole.size - parts.size) <= 2)
        for (j in 200 until minOf(whole.size, parts.size) - 200 step 97) assertEquals(whole[j].toDouble(), parts[j].toDouble(), 0.02)
    }

    @Test fun upsamplingWorks() {
        val out = Resampler(8_000, 16_000).let { it.process(sine(200.0, 8_000, 1.0)) + it.flush() }
        assertTrue(abs(out.size - 16_000) <= 3)
    }

    @Test fun chunkCutLandsOnQuietMoment() {
        val rate = ChunkPlanner.SAMPLE_RATE
        val target = 60 * rate
        val audio = FloatArray(target + 20 * rate) { 0.3f * sin(it * 0.05f) }
        // a silent gap 6 s before the target
        for (i in (target - 6 * rate) until (target - 6 * rate + 8000)) audio[i] = 0f
        val cut = ChunkPlanner.findCut(audio, audio.size, target)
        assertTrue("cut $cut", cut in (target - 6 * rate) until (target - 6 * rate + 8000))
    }

    @Test fun shortAudioIsNotCut() {
        assertEquals(1000, ChunkPlanner.findCut(FloatArray(1000), 1000, 5000))
    }

    @Test fun quotaRules() {
        assertEquals(300 * 60_000L, QuotaRules.remainingMs(0))
        assertEquals(0L, QuotaRules.remainingMs(999_999_999))
        assertTrue(QuotaRules.canTranscribe(true, Long.MAX_VALUE))
        assertTrue(!QuotaRules.canTranscribe(false, 300 * 60_000L))
        assertEquals("2026-01", QuotaRules.monthKey(1_767_225_600_000L + 86_400_000L * 10, java.time.ZoneOffset.UTC))
    }

    @Test fun localSummaryKeepsOrderAndSize() {
        val text = "La reunión trató sobre el presupuesto del proyecto nuevo. " +
            "El presupuesto del proyecto sube un diez por ciento este año. " +
            "Nadie comió pizza ayer por la noche en casa. " +
            "Hay que aprobar el presupuesto antes de que acabe el mes. " +
            "El equipo del proyecto revisará las cifras del presupuesto el lunes. " +
            "Hace buen tiempo hoy en la ciudad de Valencia. " +
            "Ana presentará el proyecto al comité de dirección mañana."
        val s = LocalSummarizer.summarize(LocalSummarizer.splitSentences(text), 3)
        assertEquals(3, s.size)
        assertTrue(s.all { it.contains("presupuesto") || it.contains("proyecto") })
        val order = s.map { text.indexOf(it) }
        assertEquals(order.sorted(), order)
    }
}
