package com.cazadoxs.descifra.core.ai

import com.cazadoxs.descifra.core.model.Amount
import com.cazadoxs.descifra.core.model.Analysis
import com.cazadoxs.descifra.core.model.AnalysisSource
import com.cazadoxs.descifra.core.model.Deadline
import com.cazadoxs.descifra.core.model.DeadlineKind
import com.cazadoxs.descifra.core.rules.LocalAnalyzer
import com.cazadoxs.descifra.core.rules.SpanishText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.roundToLong

@Serializable
internal data class AiDeadline(
    val label: String = "",
    val date: String = "",
    val kind: String = "OTHER",
    val estimated: Boolean = false,
    val quote: String = "",
)

@Serializable
internal data class AiAmount(val label: String = "", val euros: Double = 0.0, val quote: String = "")

@Serializable
internal data class AiAnalysis(
    val title: String = "",
    val summary: String = "",
    val docType: String = "",
    val whatTheyAsk: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val deadlines: List<AiDeadline> = emptyList(),
    val amounts: List<AiAmount> = emptyList(),
    val warnings: List<String> = emptyList(),
)

class AiParseException(message: String) : Exception(message)

/**
 * Turns a model answer into an [Analysis] and cross-checks it against the deterministic one.
 * Anything the model claims about dates and amounts must be traceable to the letter.
 */
object AiMerge {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    internal fun parse(raw: String): AiAnalysis {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiParseException("La respuesta de la IA no contiene JSON")
        return try {
            json.decodeFromString(AiAnalysis.serializer(), raw.substring(start, end + 1))
        } catch (e: Exception) {
            throw AiParseException("No se pudo leer la respuesta de la IA: ${e.message?.take(120)}")
        }
    }

    fun merge(
        raw: String,
        letter: String,
        local: Analysis,
        source: AnalysisSource,
        provider: String?,
        today: LocalDate = LocalDate.now(),
        restore: (String) -> String = { it },
    ): Analysis {
        val ai = parse(restore(raw))
        val foldedLetter = normalizeForMatch(letter)

        val aiDeadlines = ai.deadlines.mapNotNull { d ->
            val date = runCatching { LocalDate.parse(d.date.trim()) }.getOrNull() ?: return@mapNotNull null
            if (date.year !in 2000..2100) return@mapNotNull null
            val kind = runCatching { DeadlineKind.valueOf(d.kind.trim().uppercase()) }.getOrDefault(DeadlineKind.OTHER)
            val verified = d.quote.isNotBlank() && quoteFound(d.quote, foldedLetter)
            Deadline(
                label = d.label.ifBlank { LocalAnalyzer.labelFor(kind) },
                date = date.toString(),
                kind = kind,
                estimated = d.estimated,
                quote = d.quote.ifBlank { null },
                verified = verified,
            )
        }

        // Rule-based deadlines win on the same date; AI adds what rules missed.
        val deadlines = (local.deadlines + aiDeadlines.filter { a -> local.deadlines.none { it.date == a.date } })
            .distinctBy { it.date to it.kind }
            .sortedBy { it.date }

        val aiAmounts = ai.amounts.filter { it.euros > 0 }.map { Amount(it.label.ifBlank { "Importe" }, (it.euros * 100).roundToLong(), it.quote.ifBlank { null }) }
        // Only keep AI amounts whose number literally appears in the letter, or that rules also saw.
        val amounts = (local.amounts + aiAmounts.filter { a ->
            local.amounts.none { it.cents == a.cents } && amountInLetter(a.cents, letter)
        }).distinctBy { it.cents }

        val unverified = deadlines.count { !it.verified }
        val warnings = buildList {
            addAll(ai.warnings.filter { it.isNotBlank() })
            addAll(local.warnings)
            if (unverified > 0) add("La IA propone $unverified fecha(s) que no hemos encontrado literalmente en la carta. Compruébalas en el original.")
        }.distinct()

        return local.copy(
            title = ai.title.ifBlank { local.title }.take(80),
            summary = ai.summary.ifBlank { local.summary },
            docType = local.docType ?: ai.docType.ifBlank { null },
            whatTheyAsk = ai.whatTheyAsk.filter { it.isNotBlank() }.ifEmpty { local.whatTheyAsk },
            steps = ai.steps.filter { it.isNotBlank() }.ifEmpty { local.steps } +
                local.steps.filter { it.startsWith("Trámites en línea") },
            deadlines = deadlines,
            amounts = amounts,
            warnings = warnings,
            urgency = LocalAnalyzer.urgencyOf(deadlines, null, today).let { if (it < local.urgency) local.urgency else it },
            source = source,
            provider = provider,
        )
    }

    internal fun normalizeForMatch(s: String): String =
        SpanishText.fold(s).replace(Regex("[^a-z0-9]+"), " ").trim()

    /** True when most of the quote's words appear in order-insensitive windows of the letter. */
    internal fun quoteFound(quote: String, normalizedLetter: String): Boolean {
        val q = normalizeForMatch(quote)
        if (q.isEmpty()) return false
        if (normalizedLetter.contains(q)) return true
        // OCR noise tolerance: accept if 80 % of the quote's 3-word shingles are present.
        val words = q.split(' ')
        if (words.size < 3) return false
        val shingles = words.windowed(3).map { it.joinToString(" ") }
        val hits = shingles.count { normalizedLetter.contains(it) }
        return hits >= (shingles.size * 0.8)
    }

    private fun amountInLetter(cents: Long, letter: String): Boolean {
        val euros = cents / 100
        val rest = cents % 100
        val plain = euros.toString()
        val grouped = plain.reversed().chunked(3).joinToString(".").reversed()
        val decimals = rest.toString().padStart(2, '0')
        val compact = letter.replace(" ", "")
        return listOf("$grouped,$decimals", "$plain,$decimals", "$plain.$decimals", grouped, plain)
            .any { compact.contains(it) }
    }
}
