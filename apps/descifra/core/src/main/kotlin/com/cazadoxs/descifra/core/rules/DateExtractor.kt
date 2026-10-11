package com.cazadoxs.descifra.core.rules

import com.cazadoxs.descifra.core.model.DeadlineKind
import java.time.DateTimeException
import java.time.LocalDate

enum class DateRole { DEADLINE, ISSUE, OTHER }

enum class PeriodUnit { BUSINESS_DAYS, CALENDAR_DAYS, MONTHS }

data class DateMention(
    val date: LocalDate,
    val role: DateRole,
    val kind: DeadlineKind,
    val quote: String,
    val start: Int,
)

data class PeriodMention(
    val amount: Int,
    val unit: PeriodUnit,
    val kind: DeadlineKind,
    val quote: String,
    val start: Int,
) {
    fun resolve(from: LocalDate): LocalDate = when (unit) {
        PeriodUnit.BUSINESS_DAYS -> BusinessDays.addBusinessDays(from, amount)
        PeriodUnit.CALENDAR_DAYS -> BusinessDays.addCalendarDays(from, amount)
        PeriodUnit.MONTHS -> BusinessDays.addMonths(from, amount)
    }

    val describe: String
        get() = when (unit) {
            PeriodUnit.BUSINESS_DAYS -> "$amount días hábiles"
            PeriodUnit.CALENDAR_DAYS -> "$amount días naturales"
            PeriodUnit.MONTHS -> if (amount == 1) "1 mes" else "$amount meses"
        }
}

object DateExtractor {

    private val monthAlt = SpanishText.months.keys.joinToString("|")

    private val numericDate = Regex("""\b(\d{1,2})\s?[/\-.]\s?(\d{1,2})\s?[/\-.]\s?(\d{4}|\d{2})\b""")
    private val spelledDate = Regex("""\b(\d{1,2})\s*(?:de\s+)?($monthAlt)(?:\s*(?:de|del)?\s*(\d{4}))?\b""")

    private val numberToken = """(\d{1,3}|${SpanishText.numberWordsPattern})"""
    private val period = Regex(
        """\b$numberToken(?:\s*\(\s*\d{1,3}\s*\))?\s+(dias?|mes(?:es)?)(?:\s+(habiles|naturales))?\b""",
    )
    private val periodContext = Regex(
        """(plazo|dispone|disponer|cuenta con|termino|dentro de|en los|en el|transcurrid|contar desde|a partir|siguientes)""",
    )

    private val deadlineCues = listOf(
        "antes del", "antes de el", "hasta el", "no mas tarde", "fecha limite", "vencimiento", "vence",
        "plazo", "a mas tardar", "fecha de cargo", "se cargara", "debera", "finaliza", "termina el",
        "fin del periodo", "fin de periodo", "ultimo dia", "fecha tope", "se le cita", "comparecer el",
    )
    private val issueCues = listOf(
        "fecha:", "fecha de emision", "fecha emision", "fecha de expedicion", "fecha de notificacion",
        "de fecha", "fecha del documento", "fecha de firma", "firmado", "fecha de envio",
    )

    fun dates(text: String, today: LocalDate = LocalDate.now()): List<DateMention> {
        val folded = SpanishText.foldSameLength(text)
        val found = mutableListOf<Triple<LocalDate, Int, Int>>()

        numericDate.findAll(folded).forEach { m ->
            val (d, mo, y) = m.destructured
            val year = normalizeYear(y.toInt())
            safeDate(year, mo.toInt(), d.toInt())?.let { found += Triple(it, m.range.first, m.range.last + 1) }
        }
        spelledDate.findAll(folded).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = SpanishText.months.getValue(m.groupValues[2])
            val year = m.groupValues[3].toIntOrNull() ?: inferYear(month, day, today)
            safeDate(year, month, day)?.let { found += Triple(it, m.range.first, m.range.last + 1) }
        }

        return found
            .distinctBy { it.second }
            .sortedBy { it.second }
            .map { (date, start, end) ->
                val before = folded.substring(maxOf(0, start - 70), start)
                val after = folded.substring(end, minOf(folded.length, end + 40))
                DateMention(
                    date = date,
                    role = role(before, folded.substring(maxOf(0, start - 25), start)),
                    kind = kindOf(before + " " + after),
                    quote = quote(text, start, end),
                    start = start,
                )
            }
    }

    fun periods(text: String): List<PeriodMention> {
        val folded = SpanishText.foldSameLength(text)
        return period.findAll(folded).mapNotNull { m ->
            val amount = SpanishText.parseNumber(m.groupValues[1]) ?: return@mapNotNull null
            if (amount <= 0 || amount > 365) return@mapNotNull null
            val start = m.range.first
            val before = folded.substring(maxOf(0, start - 90), start)
            if (!periodContext.containsMatchIn(before)) return@mapNotNull null
            // "hace 3 meses" or ages are not deadlines.
            if (before.trimEnd().endsWith("hace")) return@mapNotNull null
            val isMonth = m.groupValues[2].startsWith("mes")
            val unit = when {
                isMonth -> PeriodUnit.MONTHS
                m.groupValues[3] == "naturales" -> PeriodUnit.CALENDAR_DAYS
                // Ley 39/2015 art. 30.2: when not specified, days are business days.
                else -> PeriodUnit.BUSINESS_DAYS
            }
            val after = folded.substring(m.range.last + 1, minOf(folded.length, m.range.last + 120))
            PeriodMention(
                amount = amount,
                unit = unit,
                kind = kindOf(before.takeLast(60) + " " + after),
                quote = quote(text, maxOf(0, start - 40), minOf(text.length, m.range.last + 1 + 60)),
                start = start,
            )
        }.distinctBy { Triple(it.amount, it.unit, it.kind) }.toList()
    }

    private fun role(before: String, near: String): DateRole = when {
        issueCues.any { near.contains(it) } -> DateRole.ISSUE
        Regex(""",\s*a\s*$""").containsMatchIn(near) -> DateRole.ISSUE // "En Madrid, a 3 de octubre"
        deadlineCues.any { before.contains(it) } -> DateRole.DEADLINE
        else -> DateRole.OTHER
    }

    internal fun kindOf(context: String): DeadlineKind = when {
        Regex("""(recurso|reposicion|reclamacion economico|alegacion|alegar|alegaciones)""").containsMatchIn(context) -> DeadlineKind.APPEAL
        Regex("""(aport|documentacion|presentar|remitir|facilitar|justificante|subsanar)""").containsMatchIn(context) -> DeadlineKind.PROVIDE_DOCS
        Regex("""(compar(e|ez)c|personarse|acudir|cita |citado|citacion|vista)""").containsMatchIn(context) -> DeadlineKind.APPEAR
        Regex("""(pago|pagar|abon|ingres|importe|cargo|deuda|domicili|recibo|cuota|reduccion)""").containsMatchIn(context) -> DeadlineKind.PAY
        Regex("""(contestar|responder|respuesta|comunicar|manifestar)""").containsMatchIn(context) -> DeadlineKind.RESPOND
        else -> DeadlineKind.OTHER
    }

    private fun quote(text: String, start: Int, end: Int): String {
        var s = maxOf(0, start - 60)
        var e = minOf(text.length, end + 50)
        // Expand to word boundaries so quotes do not start mid-word.
        while (s > 0 && !text[s - 1].isWhitespace()) s--
        while (e < text.length && !text[e].isWhitespace()) e++
        return text.substring(s, e).replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeYear(y: Int): Int = if (y < 100) 2000 + y else y

    private fun inferYear(month: Int, day: Int, today: LocalDate): Int {
        // A date without a year in a letter almost always refers to the coming months.
        val candidate = safeDate(today.year, month, day) ?: return today.year
        return if (candidate.isBefore(today.minusMonths(2))) today.year + 1 else today.year
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
        if (y !in 1990..2100) null else LocalDate.of(y, m, d)
    } catch (_: DateTimeException) {
        null
    }
}
