package com.cazadoxs.descifra.core.privacy

/**
 * Replaces personal identifiers with placeholders before text leaves the phone, and restores them
 * in the answer. The AI only needs to know *that* there is a NIF or an IBAN, not which one.
 */
class Redactor {

    data class Result(val text: String, val mapping: Map<String, String>) {
        fun restore(answer: String): String =
            mapping.entries.fold(answer) { acc, (placeholder, original) -> acc.replace(placeholder, original) }
    }

    private data class Rule(val tag: String, val regex: Regex)

    private val rules = listOf(
        Rule("IBAN", Regex("""\b[A-Z]{2}\d{2}(?:[\s-]?[A-Z0-9]{4}){3,7}(?:[\s-]?[A-Z0-9]{1,4})?\b""")),
        Rule("TARJETA", Regex("""\b(?:\d{4}[\s-]?){3}\d{4}\b""")),
        Rule("NIE", Regex("""\b[XYZ][\s-]?\d{7}[\s-]?[A-Z]\b""")),
        Rule("NIF", Regex("""\b\d{8}[\s-]?[A-Z]\b""")),
        Rule("EMAIL", Regex("""\b[\w.+-]+@[\w-]+\.[\w.-]+\b""")),
        Rule("TELEFONO", Regex("""(?<![\d,.])(?:\+34[\s-]?)?[6789]\d{2}[\s-]?\d{3}[\s-]?\d{3}(?!\d|,\d)""")),
        Rule("SS", Regex("""(?i)(?<=afiliaci[oó]n|NAF|NUSS)\s*[:.]?\s*\d{2}[\s/]?\d{8,10}""")),
        // Names after courtesy titles: "D. Juan Pérez García", "Dña. Ana López".
        Rule("NOMBRE", Regex("""(?<=\b(?:D\.|Dª|Dña\.|Don|Doña|Sr\.|Sra\.)\s)(?:[A-ZÁÉÍÓÚÑ][\p{L}'-]+\s?){1,4}""")),
        // Street addresses: "C/ Mayor 12, 3º B", "Calle de la Paz, 4".
        Rule("DIRECCION", Regex("""(?i)\b(?:c/|calle|avda\.?|avenida|plaza|pza\.?|paseo|camino|carrer|rúa)\s[^\n,]{2,40},?\s*(?:n[ºo°]\s*)?\d{1,4}[^\n]{0,20}""")),
    )

    fun redact(text: String): Result {
        val mapping = linkedMapOf<String, String>()
        val counters = mutableMapOf<String, Int>()
        val reverse = mutableMapOf<String, String>()
        var out = text
        for (rule in rules) {
            out = rule.regex.replace(out) { m ->
                val original = m.value.trim()
                if (original.isEmpty() || original.startsWith("[")) return@replace m.value
                val placeholder = reverse.getOrPut(original) {
                    val n = (counters[rule.tag] ?: 0) + 1
                    counters[rule.tag] = n
                    "[${rule.tag}_$n]".also { mapping[it] = original }
                }
                // Keep surrounding whitespace that the match may have consumed.
                val lead = m.value.takeWhile(Char::isWhitespace)
                val trail = m.value.takeLastWhile(Char::isWhitespace)
                lead + placeholder + trail
            }
        }
        return Result(out, mapping)
    }
}
