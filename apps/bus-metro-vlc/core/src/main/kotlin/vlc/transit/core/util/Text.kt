package vlc.transit.core.util

import java.text.Normalizer

private val MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex("\\s+")
private val PUNCT = Regex("[^a-z0-9 ]")

/** Lowercase, accent-free, punctuation-free form used for search (so "plaça" finds "Plaça"). */
fun normalizeForSearch(s: String): String {
    val noMarks = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
    return SPACES.replace(PUNCT.replace(noMarks.replace('·', ' ').replace('-', ' '), " "), " ").trim()
}

/** Collapses the double spaces and padding some feeds use in names. */
fun tidyName(s: String): String = SPACES.replace(s, " ").trim()

/** True when every query word is a prefix of some word of [haystackNorm]. */
fun matchesQuery(haystackNorm: String, queryNorm: String): Boolean {
    if (queryNorm.isEmpty()) return true
    val words = haystackNorm.split(' ')
    return queryNorm.split(' ').all { q -> words.any { it.startsWith(q) } }
}
