package com.cazadoxs.descifra.core.rules

import java.text.Normalizer

internal object SpanishText {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Lowercase, strip accents (keeps ñ as n) and collapse whitespace. Length can change. */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace(Regex("\\s+"), " ")

    /**
     * Same as [fold] but keeps a 1:1 character mapping with the input, so indexes found in
     * the folded string point at the same place in the original. Used to quote the original.
     */
    fun foldSameLength(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).replace(diacritics, "")
            val ch = if (base.length == 1) base[0] else c
            sb.append(if (ch.isWhitespace()) ' ' else ch.lowercaseChar())
        }
        return sb.toString()
    }

    val months = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5, "junio" to 6,
        "julio" to 7, "agosto" to 8, "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12,
    )

    private val units = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11,
        "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16,
        "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20,
        "veintiun" to 21, "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
        "veintiocho" to 28, "veintinueve" to 29, "treinta" to 30, "cuarenta" to 40,
        "cuarenta y cinco" to 45, "sesenta" to 60, "noventa" to 90,
    )

    /** Regex alternation for spelled-out numbers, longest first so "veintiuno" beats "veinti". */
    val numberWordsPattern: String = units.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    /** Parses "10", "diez", "diez (10)" style numbers already folded. */
    fun parseNumber(token: String): Int? {
        val t = token.trim()
        t.toIntOrNull()?.let { return it }
        return units[t]
    }
}
