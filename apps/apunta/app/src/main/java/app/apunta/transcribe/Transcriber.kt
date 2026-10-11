package app.apunta.transcribe

import android.content.Context
import app.apunta.audio.AudioDecoder
import app.apunta.audio.ChunkPlanner
import app.apunta.billing.Quota
import app.apunta.billing.QuotaRules
import app.apunta.data.AppDatabase
import app.apunta.data.Recording
import app.apunta.data.Segment
import app.apunta.data.Status
import app.apunta.models.ModelCatalog
import app.apunta.models.ModelManager
import app.apunta.settings.Performance
import app.apunta.settings.Settings
import app.apunta.whisper.WhisperListener
import app.apunta.whisper.WhisperNative
import java.io.File

/**
 * Runs one recording through whisper, chunk by chunk, saving progress after every
 * chunk so a crash, a low-battery kill or a cancelled job resumes where it stopped.
 * Blocking: call from a background thread only.
 */
class Transcriber(
    private val context: Context,
    private val db: AppDatabase,
    private val models: ModelManager,
    private val settings: Settings,
    private val quota: Quota,
    private val isPro: () -> Boolean,
) {
    sealed interface Outcome {
        data object Done : Outcome
        data object Cancelled : Outcome
        data object NeedsPro : Outcome
        data class Failed(val reason: String) : Outcome
    }

    fun recordingsDir() = File(context.filesDir, "recordings").apply { mkdirs() }

    fun run(id: Long, isCancelled: () -> Boolean, onProgress: (Float) -> Unit): Outcome {
        val dao = db.recordings()
        val segDao = db.segments()
        val rec = kotlinx.coroutines.runBlocking { dao.get(id) } ?: return Outcome.Failed("missing")
        val s = settings.current
        val modelId = rec.modelId?.takeIf { models.fileFor(it) != null } ?: s.modelId
        val model = ModelCatalog.byId(modelId)
        if (model?.pro == true && !isPro()) return Outcome.NeedsPro
        val modelFile = models.fileFor(modelId) ?: return Outcome.Failed("model")
        val audio = File(recordingsDir(), rec.fileName)
        if (!audio.exists()) return Outcome.Failed("audio")

        val pcm = File(context.cacheDir, "pcm_$id.raw")
        var ctx = 0L
        try {
            kotlinx.coroutines.runBlocking { dao.setStatus(id, Status.RUNNING, rec.progress) }
            WhisperNative.load(context)

            // Reuse a decode from an interrupted run if it is complete enough.
            val total: Long = if (pcm.exists() && pcm.length() > 0) AudioDecoder.sampleCount(pcm) else {
                AudioDecoder.decodeToPcm16k(audio, pcm, { onProgress(it * 0.05f) }, isCancelled).samples
            }
            val durationMs = total * 1000 / ChunkPlanner.SAMPLE_RATE
            kotlinx.coroutines.runBlocking { dao.update((dao.get(id) ?: rec).copy(durationMs = durationMs)) }

            ctx = WhisperNative.initContext(modelFile.absolutePath)
            if (ctx == 0L) return Outcome.Failed("init")

            var cursor = (rec.processedMs * ChunkPlanner.SAMPLE_RATE / 1000).coerceAtMost(total)
            val threads = threadsFor(s.performance)
            var lang = rec.language
            var prompt: String? = promptFor(lang)

            while (cursor < total) {
                if (isCancelled()) return Outcome.Cancelled
                if (!isPro() && !QuotaRules.canTranscribe(false, quota.refresh().usedMs)) return Outcome.NeedsPro

                val want = minOf(
                    (ChunkPlanner.CHUNK_SECONDS + ChunkPlanner.SEARCH_WINDOW_SECONDS).toLong() * ChunkPlanner.SAMPLE_RATE,
                    total - cursor,
                ).toInt()
                val samples = AudioDecoder.readFloats(pcm, cursor, want)
                val isLast = cursor + want >= total
                val cut = if (isLast) samples.size else ChunkPlanner.findCut(samples)
                if (cut <= 0) break
                val offsetMs = cursor * 1000 / ChunkPlanner.SAMPLE_RATE

                val batch = ArrayList<Segment>()
                val listener = object : WhisperListener {
                    override fun onSegment(startMs: Long, endMs: Long, utf8: ByteArray, noSpeechProb: Float) {
                        val text = String(utf8, Charsets.UTF_8).trim()
                        if (text.isEmpty() || isHallucination(text, noSpeechProb)) return
                        // Samples past `cut` belong to the next chunk; drop them here.
                        if (startMs - offsetMs >= cut * 1000L / ChunkPlanner.SAMPLE_RATE) return
                        batch += Segment(recordingId = id, startMs = startMs, endMs = endMs, text = text)
                    }
                    override fun onProgress(percent: Int) {
                        val chunkFraction = percent / 100f
                        val done = cursor + (cut * chunkFraction).toLong()
                        onProgress(0.05f + 0.95f * (done.toFloat() / total))
                    }
                    override fun shouldAbort() = isCancelled()
                    override fun onLanguage(code: String) {
                        if (lang == "auto") {
                            lang = code // lock the detected language for the remaining chunks
                            kotlinx.coroutines.runBlocking { dao.setDetectedLanguage(id, code) }
                        }
                    }
                }
                val rc = WhisperNative.transcribe(
                    ctx, samples, cut, lang, rec.translate, threads, prompt, offsetMs, listener,
                )
                if (rc == 1) return Outcome.Cancelled
                if (rc != 0) return Outcome.Failed("whisper:$rc")

                val chunkMs = cut * 1000L / ChunkPlanner.SAMPLE_RATE
                kotlinx.coroutines.runBlocking {
                    db.segments().insertAll(batch)
                    cursor += cut
                    val processed = cursor * 1000 / ChunkPlanner.SAMPLE_RATE
                    dao.setProcessed(id, processed, 0.05f + 0.95f * cursor.toFloat() / total)
                }
                if (!isPro()) quota.addUsage(chunkMs)
                prompt = batch.lastOrNull()?.text?.takeLast(200) ?: prompt
            }
            kotlinx.coroutines.runBlocking {
                val fresh = dao.get(id) ?: rec
                dao.update(fresh.copy(status = Status.DONE, progress = 1f, processedMs = durationMs, error = null))
            }
            return Outcome.Done
        } catch (e: InterruptedException) {
            return Outcome.Cancelled
        } catch (e: AudioDecoder.DecodeException) {
            return Outcome.Failed("decode")
        } catch (e: OutOfMemoryError) {
            return Outcome.Failed("memory")
        } catch (e: Exception) {
            return Outcome.Failed(e.javaClass.simpleName)
        } finally {
            if (ctx != 0L) WhisperNative.freeContext(ctx)
            // Keep the decoded PCM only while the job can still be resumed.
            val st = kotlinx.coroutines.runBlocking { dao.get(id)?.status }
            if (st == Status.DONE || st == Status.FAILED) pcm.delete()
        }
    }

    private fun threadsFor(p: Performance): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when (p) {
            Performance.SAVER -> (cores / 4).coerceIn(1, 2)
            Performance.BALANCED -> (cores / 2).coerceIn(2, 4)
            Performance.FAST -> (cores - 1).coerceIn(2, 8)
        }
    }

    private fun promptFor(lang: String): String? = when (lang) {
        "es" -> "Hola, buenos días. Esto es una transcripción con puntuación correcta."
        "ca" -> "Hola, bon dia. Això és una transcripció amb puntuació correcta."
        else -> null
    }

    companion object {
        /** Whisper invents text over silence; these are the well-known offenders. */
        private val JUNK = listOf(
            "subtítulos realizados por", "subtitulos realizados por", "amara.org", "gracias por ver",
            "thanks for watching", "subtítols fets per", "[música]", "[music]", "(música)",
        )

        fun isHallucination(text: String, noSpeechProb: Float): Boolean {
            val t = text.lowercase()
            if (noSpeechProb > 0.85f) return true
            if (noSpeechProb > 0.5f && JUNK.any { t.contains(it) }) return true
            return false
        }
    }
}
