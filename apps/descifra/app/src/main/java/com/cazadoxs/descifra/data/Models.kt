package com.cazadoxs.descifra.data

import com.cazadoxs.descifra.core.ai.CloudProvider
import com.cazadoxs.descifra.core.model.Analysis
import kotlinx.serialization.Serializable

@Serializable
data class Letter(
    val id: String,
    val createdAt: Long,
    /** ISO date the user received the letter; deadlines in "business days" count from here. */
    val receivedOn: String,
    /** Exact OCR / pasted text. Always shown next to the summary. */
    val text: String,
    val analysis: Analysis,
    val pages: Int = 1,
    val note: String = "",
    val done: Boolean = false,
    val pinned: Boolean = false,
    /** Question/answer history with the assistant, if any. */
    val chat: List<ChatTurn> = emptyList(),
    val draft: String = "",
    val remindersOn: Boolean = true,
)

@Serializable
data class ChatTurn(val question: String, val answer: String)

enum class AnalysisMode(val label: String, val help: String) {
    AUTO("Automático", "En el móvil si se puede; si no, con tu clave. Y siempre las reglas locales."),
    ON_DEVICE("Solo en el móvil", "Nada sale del teléfono. Necesita un móvil compatible con Gemini Nano."),
    CLOUD("Con mi clave", "Envía el texto al proveedor que elijas. Los datos personales se ocultan antes."),
    RULES("Solo reglas", "Sin IA. Lee fechas, importes y referencias, y explica los tipos de carta más comunes."),
}

data class Settings(
    val mode: AnalysisMode = AnalysisMode.AUTO,
    val provider: CloudProvider = CloudProvider.ANTHROPIC,
    val model: String = "",
    val redact: Boolean = true,
    val lockWithBiometrics: Boolean = false,
    val reminderDaysBefore: Set<Int> = setOf(7, 1),
    val reminderHour: Int = 9,
    val onboarded: Boolean = false,
    val cloudConsent: Boolean = false,
    val hasKey: Boolean = false,
    val allowScreenshots: Boolean = false,
)
