package com.cazadoxs.descifra.core.rules

import com.cazadoxs.descifra.core.model.Amount
import com.cazadoxs.descifra.core.model.Reference
import com.cazadoxs.descifra.core.model.ReferenceKind
import java.math.BigInteger

object AmountExtractor {
    // 1.234,56 € | 1234,56 euros | € 1.234,56 | EUR 300 | 300 €
    private val number = """(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:,\d{1,2})?)"""
    private val after = Regex("""$number\s*(?:€|eur(?:os?)?\b)""")
    private val before = Regex("""(?:€|eur\b)\s*$number""")

    private val labels = listOf(
        "total a ingresar" to "Total a pagar",
        "importe total" to "Importe total",
        "total a pagar" to "Total a pagar",
        "a ingresar" to "A pagar",
        "a devolver" to "A devolver",
        "deuda pendiente" to "Deuda pendiente",
        "principal" to "Principal",
        "recargo" to "Recargo",
        "intereses de demora" to "Intereses de demora",
        "intereses" to "Intereses",
        "sancion" to "Sanción",
        "multa" to "Multa",
        "reduccion" to "Con reducción",
        "nueva renta" to "Nueva renta",
        "renta mensual" to "Renta mensual",
        "fianza" to "Fianza",
        "cuota" to "Cuota",
        "comision" to "Comisión",
        "saldo" to "Saldo",
        "importe" to "Importe",
        "total" to "Total",
    )

    fun extract(text: String): List<Amount> {
        val folded = SpanishText.foldSameLength(text)
        val hits = mutableListOf<Pair<IntRange, String>>()
        after.findAll(folded).forEach { hits += it.range to it.groupValues[1] }
        before.findAll(folded).forEach { m -> if (hits.none { it.first.overlaps(m.range) }) hits += m.range to m.groupValues[1] }
        return hits.sortedBy { it.first.first }.mapNotNull { (range, raw) ->
            val cents = parseCents(raw) ?: return@mapNotNull null
            if (cents <= 0) return@mapNotNull null
            val context = folded.substring(maxOf(0, range.first - 50), range.first)
            // The cue closest to the number wins; on ties the more specific (earlier in the list) one.
            val label = labels.filter { context.contains(it.first) }
                .maxByOrNull { context.lastIndexOf(it.first) + it.first.length }?.second ?: "Importe"
            val qs = maxOf(0, range.first - 40)
            Amount(label, cents, text.substring(qs, minOf(text.length, range.last + 1)).replace(Regex("\\s+"), " ").trim())
        }.distinctBy { it.cents to it.label }
    }

    fun parseCents(raw: String): Long? {
        val clean = raw.replace(".", "")
        val parts = clean.split(",")
        val euros = parts[0].toLongOrNull() ?: return null
        val cents = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return euros * 100 + cents
    }

    private fun IntRange.overlaps(o: IntRange) = first <= o.last && o.first <= last
}

object ReferenceExtractor {
    private const val NIF_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"

    private val nif = Regex("""\b(\d{8})[\s-]?([A-Z])\b""")
    private val nie = Regex("""\b([XYZ])[\s-]?(\d{7})[\s-]?([A-Z])\b""")
    private val cif = Regex("""\b([ABCDEFGHJNPQRSUVW])[\s-]?(\d{7})[\s-]?([0-9A-J])\b""")
    private val iban = Regex("""\b(ES\d{2}(?:[\s-]?\d{4}){5})\b""")
    private val csv = Regex("""(?i)(?:c[oó]digo seguro de verificaci[oó]n|\bCSV)\s*[:.]?\s*([A-Z0-9]{8,24})""")
    private val expediente = Regex("""(?i)\b(?:n[ºo°.]*\s*(?:de\s+)?)?expediente\s*(?:n[ºo°.]*)?\s*[:.]?\s*([A-Z0-9][A-Z0-9/.\-]{4,30})""")
    private val justificante = Regex("""(?i)\b(?:n[ºo°.]*\s*(?:de\s+)?)?justificante\s*(?:n[ºo°.]*)?\s*[:.]?\s*(\d{10,16})""")
    private val matricula = Regex("""\b(\d{4}[\s-]?[BCDFGHJKLMNPRSTVWXYZ]{3})\b""")
    private val referencia = Regex("""(?i)\breferencia\s*(?:n[ºo°.]*)?\s*[:.]?\s*([A-Z0-9][A-Z0-9/.\-]{5,30})""")

    fun extract(text: String): List<Reference> {
        val out = linkedSetOf<Reference>()
        nie.findAll(text).forEach { m ->
            val (p, digits, letter) = m.destructured
            val n = ("XYZ".indexOf(p).toString() + digits).toInt()
            if (NIF_LETTERS[n % 23].toString() == letter) out += Reference(ReferenceKind.NIE, "$p$digits$letter")
        }
        nif.findAll(text).forEach { m ->
            val (digits, letter) = m.destructured
            if (validNif(digits, letter[0])) out += Reference(ReferenceKind.NIF, "$digits$letter")
        }
        cif.findAll(text).forEach { m ->
            val value = m.groupValues.drop(1).joinToString("")
            if (validCif(value)) out += Reference(ReferenceKind.CIF, value)
        }
        iban.findAll(text).forEach { m ->
            val value = m.groupValues[1].replace(Regex("[\\s-]"), "")
            if (validIban(value)) out += Reference(ReferenceKind.IBAN, value.chunked(4).joinToString(" "))
        }
        csv.findAll(text).forEach { out += Reference(ReferenceKind.CSV, it.groupValues[1]) }
        expediente.findAll(text).forEach { m ->
            val v = m.groupValues[1].trimEnd('.', '-')
            if (v.any(Char::isDigit)) out += Reference(ReferenceKind.EXPEDIENTE, v)
        }
        justificante.findAll(text).forEach { out += Reference(ReferenceKind.JUSTIFICANTE, it.groupValues[1]) }
        referencia.findAll(text).forEach { m ->
            val v = m.groupValues[1].trimEnd('.', '-')
            if (v.any(Char::isDigit) && out.none { it.value == v }) out += Reference(ReferenceKind.REFERENCIA, v)
        }
        if (Regex("(?i)(matr[ií]cula|veh[ií]culo|tr[aá]fico)").containsMatchIn(text)) {
            matricula.findAll(text).forEach { out += Reference(ReferenceKind.MATRICULA, it.groupValues[1].replace(Regex("[\\s-]"), "")) }
        }
        return out.toList()
    }

    fun validNif(digits: String, letter: Char): Boolean =
        digits.length == 8 && digits.all(Char::isDigit) && NIF_LETTERS[digits.toInt() % 23] == letter

    fun validIban(iban: String): Boolean {
        val compact = iban.replace(" ", "").uppercase()
        if (compact.length < 15 || compact.length > 34) return false
        val rearranged = compact.drop(4) + compact.take(4)
        val numeric = rearranged.map { c -> if (c.isLetter()) (c - 'A' + 10).toString() else c.toString() }.joinToString("")
        return BigInteger(numeric).mod(BigInteger.valueOf(97)) == BigInteger.ONE
    }

    fun validCif(cif: String): Boolean {
        if (cif.length != 9) return false
        val digits = cif.substring(1, 8)
        if (!digits.all(Char::isDigit)) return false
        var sum = 0
        digits.forEachIndexed { i, c ->
            val n = c - '0'
            sum += if (i % 2 == 0) (n * 2).let { it / 10 + it % 10 } else n
        }
        val control = (10 - sum % 10) % 10
        val last = cif[8]
        return last == ('0' + control) || last == "JABCDEFGHI"[control]
    }
}
