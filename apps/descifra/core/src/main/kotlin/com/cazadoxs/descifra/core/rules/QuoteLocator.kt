package com.cazadoxs.descifra.core.rules

/** Finds where a quoted fragment sits in the original text, ignoring case, accents and spacing. */
object QuoteLocator {
    fun find(text: String, quote: String?): IntRange? {
        if (quote.isNullOrBlank()) return null
        val words = SpanishText.foldSameLength(quote).trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val folded = SpanishText.foldSameLength(text)
        val pattern = words.joinToString("\\s+") { Regex.escape(it) }
        val full = Regex(pattern).find(folded)
        if (full != null) return full.range
        // OCR noise: fall back to the longest run of the first words that does match.
        for (n in (words.size - 1) downTo minOf(3, words.size)) {
            val partial = Regex(words.take(n).joinToString("\\s+") { Regex.escape(it) }).find(folded)
            if (partial != null) return partial.range
        }
        return null
    }

    fun findAll(text: String, quotes: List<String?>): List<IntRange> =
        quotes.mapNotNull { find(text, it) }.sortedBy { it.first }.fold(mutableListOf()) { acc, r ->
            if (acc.isNotEmpty() && r.first <= acc.last().last) acc[acc.lastIndex] = acc.last().first..maxOf(acc.last().last, r.last) else acc += r
            acc
        }
}
