package com.cazadoxs.descifra.core.ai

import com.cazadoxs.descifra.core.model.Analysis
import com.cazadoxs.descifra.core.model.AnalysisSource
import com.cazadoxs.descifra.core.privacy.Redactor
import com.cazadoxs.descifra.core.rules.LocalAnalyzer
import java.time.LocalDate

/** Blocking calls: run them off the main thread. */
object CloudAnalysis {

    private fun prepare(letter: String, redact: Boolean): Redactor.Result =
        if (redact) Redactor().redact(letter) else Redactor.Result(letter, emptyMap())

    fun analyze(
        letter: String,
        received: LocalDate,
        client: LlmClient,
        redact: Boolean,
        today: LocalDate = LocalDate.now(),
    ): Analysis {
        val local = LocalAnalyzer.analyze(letter, received, today)
        val prepared = prepare(letter, redact)
        val raw = client.complete(Prompts.system(today, received), Prompts.user(prepared.text), Prompts.analysisSchema)
        return AiMerge.merge(raw, letter, local, AnalysisSource.CLOUD_AI, client.providerLabel, today, prepared::restore)
    }

    fun ask(letter: String, question: String, client: LlmClient, redact: Boolean): String {
        val prepared = prepare(letter, redact)
        return prepared.restore(client.complete(Prompts.questionSystem(), Prompts.question(prepared.text, question)))
    }

    fun draft(letter: String, request: String, client: LlmClient, redact: Boolean): String {
        val prepared = prepare(letter, redact)
        return prepared.restore(client.complete(Prompts.draftSystem(), Prompts.draft(prepared.text, request)))
    }
}
