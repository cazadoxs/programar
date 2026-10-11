package com.cazadoxs.descifra.core.model

import kotlinx.serialization.Serializable

/** Who sent the letter. The [id] is stable and used for icons and colours in the app. */
@Serializable
data class Issuer(
    val id: String,
    val name: String,
    val shortName: String,
    val officialSite: String? = null,
    val phone: String? = null,
)

@Serializable
enum class DeadlineKind { PAY, RESPOND, APPEAR, PROVIDE_DOCS, APPEAL, INFO, OTHER }

@Serializable
enum class Urgency { NONE, LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
enum class AnalysisSource { RULES, ON_DEVICE_AI, CLOUD_AI }

/**
 * A date the user has to act by.
 *
 * @property date ISO-8601 date (yyyy-MM-dd).
 * @property estimated true when the date was computed from a period ("10 días hábiles")
 *   rather than read literally from the letter.
 * @property quote the exact fragment of the original text that supports this deadline.
 * @property verified true when [quote] was found in the original text. AI deadlines whose quote
 *   cannot be found are kept but flagged, so the user never trusts an invented date blindly.
 */
@Serializable
data class Deadline(
    val label: String,
    val date: String,
    val kind: DeadlineKind = DeadlineKind.OTHER,
    val estimated: Boolean = false,
    val basis: String? = null,
    val quote: String? = null,
    val verified: Boolean = true,
)

@Serializable
data class Amount(
    val label: String,
    val cents: Long,
    val quote: String? = null,
) {
    val formatted: String get() = formatEuros(cents)
}

@Serializable
enum class ReferenceKind { NIF, NIE, CIF, IBAN, EXPEDIENTE, CSV, JUSTIFICANTE, MATRICULA, REFERENCIA }

@Serializable
data class Reference(val kind: ReferenceKind, val value: String)

@Serializable
data class Analysis(
    val title: String,
    val summary: String,
    val issuer: Issuer? = null,
    val docType: String? = null,
    val docTypeExplanation: String? = null,
    val whatTheyAsk: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val deadlines: List<Deadline> = emptyList(),
    val amounts: List<Amount> = emptyList(),
    val references: List<Reference> = emptyList(),
    val warnings: List<String> = emptyList(),
    val urgency: Urgency = Urgency.NONE,
    val source: AnalysisSource = AnalysisSource.RULES,
    val provider: String? = null,
    val issueDate: String? = null,
)

fun formatEuros(cents: Long): String {
    val negative = cents < 0
    val abs = kotlin.math.abs(cents)
    val euros = abs / 100
    val rest = abs % 100
    val grouped = euros.toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (negative) "-" else "") + grouped + "," + rest.toString().padStart(2, '0') + " €"
}
