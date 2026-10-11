package app.apunta.summary

import java.text.Normalizer

/**
 * Offline extractive summary: picks the sentences that carry the most frequent
 * content words, in original order. Works in any language with no model and no
 * network, so every user gets a summary even without an AI key.
 */
object LocalSummarizer {
    private val STOP = setOf(
        // es
        "de","la","que","el","en","y","a","los","del","se","las","por","un","para","con","no","una","su","al","lo","como","más","pero","sus","le","ya","o","este","sí","porque","esta","entre","cuando","muy","sin","sobre","también","me","hasta","hay","donde","quien","desde","todo","nos","durante","todos","uno","les","ni","contra","otros","ese","eso","ante","ellos","e","esto","mí","antes","algunos","qué","unos","yo","otro","otras","otra","él","tanto","esa","estos","mucho","quienes","nada","muchos","cual","poco","ella","estar","estas","algunas","algo","nosotros","es","son","fue","ha","he","pues","vale","bueno","entonces",
        // ca
        "els","amb","per","una","dels","les","és","són","també","però","més","com","que","aquest","aquesta","això","molt","ja","doncs",
        // en
        "the","of","and","to","in","is","that","it","for","on","with","as","was","at","be","this","are","or","an","by","from","but","not","have","had","they","you","we","so","then","just","like",
    )

    fun summarize(sentencesIn: List<String>, maxSentences: Int = 5): List<String> {
        val sentences = sentencesIn.map { it.trim() }.filter { it.split(' ').size >= 5 }
        if (sentences.size <= maxSentences) return sentences
        val freq = HashMap<String, Int>()
        val tokens = sentences.map { s -> words(s).also { ws -> ws.forEach { freq[it] = (freq[it] ?: 0) + 1 } } }
        val scored = sentences.indices.map { i ->
            val ws = tokens[i]
            val score = if (ws.isEmpty()) 0.0 else ws.sumOf { (freq[it] ?: 0).toDouble() } / Math.sqrt(ws.size.toDouble())
            i to score
        }
        return scored.sortedByDescending { it.second }.take(maxSentences).map { it.first }.sorted().map { sentences[it] }
    }

    fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?…])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun words(s: String): List<String> =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 3 && it !in STOP }
}
