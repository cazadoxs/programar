package com.cazadoxs.descifra.ml

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class NanoState { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

/**
 * Gemini Nano through ML Kit GenAI Prompt API (AICore). Runs fully on the phone; AICore has no
 * direct internet access and keeps no record of the prompt. Alpha/beta API: every call is wrapped
 * so a failure falls back to the rules-based analysis instead of crashing.
 */
class OnDeviceAi {
    private var model: GenerativeModel? = null

    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }

    suspend fun state(): NanoState = try {
        when (client().checkStatus()) {
            FeatureStatus.AVAILABLE -> NanoState.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> NanoState.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> NanoState.DOWNLOADING
            else -> NanoState.UNAVAILABLE
        }
    } catch (e: Exception) {
        NanoState.UNAVAILABLE
    }

    /** Emits download progress in 0..1 (or null when unknown) until done. */
    fun download(): Flow<Float?> = flow {
        client().download().collect { s ->
            when (s) {
                is DownloadStatus.DownloadProgress -> emit(null)
                is DownloadStatus.DownloadFailed -> throw IllegalStateException("Descarga fallida")
                else -> Unit
            }
        }
    }

    suspend fun generate(system: String, user: String): String {
        val prompt = "$system\n\n$user"
        val response = client().generateContent(prompt)
        val text = response.candidates.firstOrNull()?.text.orEmpty()
        if (text.isBlank()) throw IllegalStateException("Gemini Nano devolvió una respuesta vacía")
        return text
    }

    fun close() {
        runCatching { model?.close() }
        model = null
    }

    companion object {
        /** Nano accepts about 4000 tokens in total; leave room for instructions and the answer. */
        const val MAX_LETTER_CHARS = 7500

        fun trim(text: String): String =
            if (text.length <= MAX_LETTER_CHARS) text
            else text.take(MAX_LETTER_CHARS * 2 / 3) + "\n[…]\n" + text.takeLast(MAX_LETTER_CHARS / 3)
    }
}
