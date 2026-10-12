package com.cazadoxs.descifra.core.rules

import com.cazadoxs.descifra.core.model.Analysis
import com.cazadoxs.descifra.core.model.AnalysisSource
import com.cazadoxs.descifra.core.model.Deadline
import com.cazadoxs.descifra.core.model.DeadlineKind
import com.cazadoxs.descifra.core.model.Urgency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Deterministic, offline analysis of a Spanish letter. Always runs, on every phone, and is the
 * safety net the AI result is checked against.
 */
object LocalAnalyzer {

    private val es = Locale.forLanguageTag("es-ES")
    private val longDate = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", es)
    private val shortDate = DateTimeFormatter.ofPattern("d/M/yyyy", es)

    fun formatLong(date: LocalDate): String = date.format(longDate)

    fun analyze(text: String, received: LocalDate, today: LocalDate = LocalDate.now()): Analysis {
        val folded = SpanishText.fold(text)
        val issuer = Issuers.detect(folded)
        val docType = DocTypes.detect(folded)

        val dates = DateExtractor.dates(text, today)
        val issueDate = dates.firstOrNull { it.role == DateRole.ISSUE }?.date
        val deadlines = mutableListOf<Deadline>()

        dates.filter { it.role == DateRole.DEADLINE && (issueDate == null || !it.date.isBefore(issueDate)) }
            .forEach { m ->
                val kind = if (m.kind == DeadlineKind.OTHER) docType?.defaultKind ?: DeadlineKind.OTHER else m.kind
                deadlines += Deadline(labelFor(kind), m.date.toString(), kind, estimated = false, quote = m.quote)
            }

        DateExtractor.periods(text).forEach { p ->
            val kind = if (p.kind == DeadlineKind.OTHER) docType?.defaultKind ?: DeadlineKind.OTHER else p.kind
            deadlines += Deadline(
                label = labelFor(kind),
                date = p.resolve(received).toString(),
                kind = kind,
                estimated = true,
                basis = "${p.describe} contados desde que la recibiste (${received.format(shortDate)})",
                quote = p.quote,
            )
        }

        if (deadlines.none { it.kind != DeadlineKind.APPEAL } && docType?.defaultDeadline != null &&
            deadlines.none { it.kind == docType.defaultKind }
        ) {
            deadlines += Deadline(
                label = labelFor(docType.defaultKind),
                date = docType.defaultDeadline.invoke(received).toString(),
                kind = docType.defaultKind,
                estimated = true,
                basis = docType.defaultDeadlineBasis,
                quote = null,
            )
        }

        val unique = deadlines
            .distinctBy { it.date to it.kind }
            .sortedBy { it.date }

        val amounts = AmountExtractor.extract(text)
        val references = ReferenceExtractor.extract(text)
        val urgency = urgencyOf(unique, docType?.baseUrgency, today)

        val warnings = buildList {
            if (unique.any { it.estimated }) add("Los plazos estimados no tienen en cuenta festivos autonómicos ni locales. Confírmalos en la carta.")
            if (unique.any { LocalDate.parse(it.date).isBefore(today) }) add("Alguna fecha de la carta ya ha pasado. Revisa si aún puedes actuar.")
            if (Regex("embarg").containsMatchIn(folded) && docType?.id != "embargo") add("La carta menciona un posible embargo.")
            if (Regex("recargo").containsMatchIn(folded)) add("Hay recargos: pagar tarde cuesta más.")
            if (text.length < 120) add("Se ha leído muy poco texto. Repite el escaneo con más luz si falta información.")
        }

        val nextDeadline = unique.firstOrNull { !LocalDate.parse(it.date).isBefore(today) && it.kind != DeadlineKind.APPEAL }
            ?: unique.firstOrNull { !LocalDate.parse(it.date).isBefore(today) }
        val mainAmount = amounts.firstOrNull { it.label.startsWith("Total") || it.label == "A pagar" } ?: amounts.firstOrNull()

        val summary = buildString {
            val who = issuer?.name ?: "Quien firma la carta"
            if (docType != null) {
                append("$who te envía ${article(docType.name)} ${docType.name.lowercase(es)}. ")
                append(docType.explanation)
            } else {
                append("$who te envía una carta. No hemos reconocido el tipo exacto de documento, así que revisa el texto original.")
            }
            if (mainAmount != null) append(" Importe destacado: ${mainAmount.formatted} (${mainAmount.label.lowercase(es)}).")
            if (nextDeadline != null) {
                val date = LocalDate.parse(nextDeadline.date)
                val days = ChronoUnit.DAYS.between(today, date)
                append(" Fecha clave: ${formatLong(date)}")
                append(if (days == 0L) " (es hoy)." else " (quedan $days días).")
            }
        }

        val title = when {
            docType != null && issuer != null -> "${docType.name} · ${issuer.shortName}"
            docType != null -> docType.name
            issuer != null -> "Carta de ${issuer.shortName}"
            else -> "Documento sin identificar"
        }

        val steps = buildList {
            addAll(docType?.steps ?: listOf("Lee la carta con calma y localiza qué te piden y la fecha límite.", "Si tienes dudas, llama o pide cita a quien la envía."))
            issuer?.officialSite?.let { add("Trámites en línea: $it") }
        }

        return Analysis(
            title = title,
            summary = summary,
            issuer = issuer,
            docType = docType?.name,
            docTypeExplanation = docType?.explanation,
            whatTheyAsk = listOfNotNull(docType?.whatTheyAsk),
            steps = steps,
            deadlines = unique,
            amounts = amounts,
            references = references,
            warnings = warnings,
            urgency = urgency,
            source = AnalysisSource.RULES,
            issueDate = issueDate?.toString(),
        )
    }

    fun labelFor(kind: DeadlineKind): String = when (kind) {
        DeadlineKind.PAY -> "Pagar"
        DeadlineKind.RESPOND -> "Responder"
        DeadlineKind.APPEAR -> "Acudir"
        DeadlineKind.PROVIDE_DOCS -> "Entregar documentos"
        DeadlineKind.APPEAL -> "Alegar o recurrir (si no estás de acuerdo)"
        DeadlineKind.INFO -> "Fecha indicada"
        DeadlineKind.OTHER -> "Fecha límite"
    }

    fun urgencyOf(deadlines: List<Deadline>, base: Urgency?, today: LocalDate): Urgency {
        val actionable = deadlines.filter { it.kind != DeadlineKind.INFO }
        val upcoming = actionable.map { ChronoUnit.DAYS.between(today, LocalDate.parse(it.date)) }.filter { it >= 0 }
        if (upcoming.isEmpty()) {
            return when {
                actionable.isNotEmpty() -> Urgency.HIGH // everything has passed: act now
                else -> base ?: Urgency.NONE
            }
        }
        val days = upcoming.min()
        val fromDays = when {
            days <= 3 -> Urgency.CRITICAL
            days <= 7 -> Urgency.HIGH
            days <= 20 -> Urgency.MEDIUM
            else -> Urgency.LOW
        }
        return if (base == Urgency.CRITICAL && fromDays < Urgency.HIGH) Urgency.HIGH else fromDays
    }

    private fun article(name: String): String {
        val first = name.lowercase(es).substringBefore(' ')
        val feminine = first.endsWith("a") || first.endsWith("ion") || first.endsWith("ión") || first == "multa"
        return if (feminine) "una" else "un"
    }
}
