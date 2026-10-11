package com.cazadoxs.descifra.data

import com.cazadoxs.descifra.core.ai.AiMerge
import com.cazadoxs.descifra.core.ai.CloudAnalysis
import com.cazadoxs.descifra.core.ai.CloudClients
import com.cazadoxs.descifra.core.ai.CloudException
import com.cazadoxs.descifra.core.ai.Prompts
import com.cazadoxs.descifra.core.model.Analysis
import com.cazadoxs.descifra.core.model.AnalysisSource
import com.cazadoxs.descifra.core.rules.LocalAnalyzer
import com.cazadoxs.descifra.ml.NanoState
import com.cazadoxs.descifra.ml.OnDeviceAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class EngineResult(val analysis: Analysis, val notes: List<String> = emptyList())

/**
 * Decides where a letter is analysed. The rules-based result is always computed first on the
 * phone and is the fallback for every failure, so scanning a letter never ends in an error.
 */
class Engine(private val repo: Repository, private val nano: OnDeviceAi) {

    suspend fun analyze(text: String, received: LocalDate, isPro: Boolean): EngineResult = withContext(Dispatchers.Default) {
        val settings = repo.settings.value
        val local = LocalAnalyzer.analyze(text, received)
        val notes = mutableListOf<String>()

        suspend fun onDevice(): Analysis? {
            if (nano.state() != NanoState.AVAILABLE) return null
            return try {
                val raw = nano.generate(Prompts.system(LocalDate.now(), received), Prompts.user(OnDeviceAi.trim(text)))
                AiMerge.merge(raw, text, local, AnalysisSource.ON_DEVICE_AI, "Gemini Nano (en tu móvil)")
            } catch (e: Exception) {
                notes += "La IA del móvil no pudo con esta carta; mostramos el análisis por reglas."
                null
            }
        }

        suspend fun cloud(): Analysis? {
            if (!isPro) return null
            val key = repo.apiKey(settings.provider) ?: return null
            if (!settings.cloudConsent) return null
            return try {
                val client = CloudClients.create(settings.provider, key, settings.model)
                withContext(Dispatchers.IO) { CloudAnalysis.analyze(text, received, client, settings.redact) }
            } catch (e: CloudException) {
                notes += e.message.orEmpty()
                null
            } catch (e: Exception) {
                notes += "No se pudo usar tu proveedor de IA; mostramos el análisis local."
                null
            }
        }

        val result = when (settings.mode) {
            AnalysisMode.RULES -> null
            AnalysisMode.ON_DEVICE -> onDevice().also { if (it == null && notes.isEmpty()) notes += "Este móvil no es compatible con la IA integrada. Usamos el análisis por reglas." }
            AnalysisMode.CLOUD -> cloud().also { if (it == null && notes.isEmpty()) notes += "Falta la clave o el permiso para enviar texto. Usamos el análisis por reglas." }
            AnalysisMode.AUTO -> onDevice() ?: cloud()
        }
        EngineResult(result ?: local, notes)
    }

    suspend fun ask(letter: Letter, question: String): String = withContext(Dispatchers.IO) {
        val settings = repo.settings.value
        val key = repo.apiKey(settings.provider)
        if (key != null && settings.cloudConsent && settings.mode != AnalysisMode.ON_DEVICE && settings.mode != AnalysisMode.RULES) {
            val client = CloudClients.create(settings.provider, key, settings.model)
            return@withContext CloudAnalysis.ask(letter.text, question, client, settings.redact)
        }
        if (nano.state() == NanoState.AVAILABLE && settings.mode != AnalysisMode.CLOUD) {
            return@withContext nano.generate(Prompts.questionSystem(), Prompts.question(OnDeviceAi.trim(letter.text), question))
        }
        throw CloudException("Para preguntar sobre la carta necesitas un móvil con IA integrada o tu clave de IA en Ajustes.")
    }

    suspend fun draft(letter: Letter, request: String): String = withContext(Dispatchers.IO) {
        val settings = repo.settings.value
        val key = repo.apiKey(settings.provider)
        if (key != null && settings.cloudConsent && settings.mode != AnalysisMode.ON_DEVICE && settings.mode != AnalysisMode.RULES) {
            val client = CloudClients.create(settings.provider, key, settings.model)
            return@withContext CloudAnalysis.draft(letter.text, request, client, settings.redact)
        }
        if (nano.state() == NanoState.AVAILABLE && settings.mode != AnalysisMode.CLOUD) {
            return@withContext nano.generate(Prompts.draftSystem(), Prompts.draft(OnDeviceAi.trim(letter.text), request))
        }
        throw CloudException("Para redactar un borrador necesitas un móvil con IA integrada o tu clave de IA en Ajustes.")
    }
}
