package app.apunta.data

import java.text.Normalizer

object SearchQuery {
    /**
     * Turns free text typed by the user into a safe FTS4 MATCH expression:
     * every word becomes a quoted prefix term, so "reunión pres" finds
     * "reunión del presupuesto". Quotes and FTS operators typed by the user are
     * stripped, so a stray `"` or `NEAR(` can never make the query throw.
     * Returns null when nothing searchable is left.
     */
    fun toFtsMatch(input: String): String? {
        val terms = input
            .split(Regex("[\\s\\p{Punct}¿¡«»“”‘’]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.uppercase() in setOf("AND", "OR", "NOT", "NEAR") }
            .take(12)
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "\"$it*\"" }
    }

    /** Escapes LIKE wildcards for a `LIKE ... ESCAPE '\'` query. */
    fun escapeLike(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Lower-case, accent-free form used to highlight matches in the UI. */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()

    /**
     * Character ranges in [text] that match any word of [query], ignoring case and
     * accents. Folding can change string length (e.g. ligatures), so matching is
     * done per character to keep indexes aligned with the original text.
     */
    fun highlightRanges(text: String, query: String): List<IntRange> {
        val words = query.split(Regex("[\\s\\p{Punct}]+")).map { fold(it) }.filter { it.isNotEmpty() }
        if (words.isEmpty() || text.isEmpty()) return emptyList()
        val folded = buildString(text.length) { text.forEach { c -> append(fold(c.toString()).firstOrNull() ?: c) } }
        val ranges = mutableListOf<IntRange>()
        for (w in words) {
            var from = 0
            while (true) {
                val idx = folded.indexOf(w, from)
                if (idx < 0) break
                ranges += idx until idx + w.length
                from = idx + w.length
            }
        }
        return merge(ranges)
    }

    private fun merge(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return ranges
        val sorted = ranges.sortedBy { it.first }
        val out = mutableListOf(sorted.first())
        for (r in sorted.drop(1)) {
            val last = out.last()
            if (r.first <= last.last + 1) out[out.size - 1] = last.first..maxOf(last.last, r.last)
            else out += r
        }
        return out
    }
}
