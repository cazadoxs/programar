package com.cazadoxs.escanerlimpio.classify

import java.text.Normalizer

object TextTools {
    private val diacritics = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    /** Lowercase, accent-free, single-spaced text for keyword matching. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase()
            .replace('º', 'o')
            .replace('ª', 'a')
            .replace(spaces, " ")
            .trim()

    private val illegalFileChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** A safe file name (no path separators or control characters), without extension. */
    fun safeFileName(title: String, fallback: String = "Documento"): String {
        val cleaned = title.replace(illegalFileChars, " ")
            .replace(Regex("\\.{2,}"), " ")
            .replace(spaces, " ")
            .trim()
            .trimStart('.')
            .take(80)
            .trim()
        return cleaned.ifEmpty { fallback }
    }

    /** Words of the query, normalized, for "all words must appear" search. */
    fun queryTerms(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotBlank() }
}
