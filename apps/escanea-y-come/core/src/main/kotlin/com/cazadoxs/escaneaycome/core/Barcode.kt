package com.cazadoxs.escaneaycome.core

/** Retail barcodes (EAN-8, UPC-A, EAN-13, GTIN-14) as digit strings. */
object Barcode {
    private val DIGITS = Regex("^[0-9]{8,14}$")

    /**
     * Returns the normalized code, or null when it is not a plausible product barcode.
     * Only digits ever reach a URL, so this is also the input guard for the network layer.
     */
    fun normalize(raw: String): String? {
        val code = raw.trim().filter { !it.isWhitespace() }
        if (!DIGITS.matches(code)) return null
        val normalized = when (code.length) {
            8 -> return if (hasValidCheckDigit(code)) code else upcEToEan13(code)
            13, 14 -> code
            12 -> "0$code" // UPC-A is EAN-13 with a leading zero
            else -> return null
        }
        return if (hasValidCheckDigit(normalized)) normalized else null
    }

    /** Expands a zero-suppressed UPC-E code to its EAN-13 form, or null if it is not one. */
    fun upcEToEan13(code: String): String? {
        if (code.length != 8 || code[0] !in "01" || !code.all { it in '0'..'9' }) return null
        val d = code.substring(1, 7)
        val body = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        val ean = "0" + code[0] + body + code[7]
        return if (hasValidCheckDigit(ean)) ean else null
    }

    fun hasValidCheckDigit(code: String): Boolean {
        if (code.isEmpty() || !code.all { it in '0'..'9' }) return false
        val digits = code.map { it - '0' }
        val body = digits.dropLast(1).reversed()
        val sum = body.withIndex().sumOf { (i, d) -> if (i % 2 == 0) d * 3 else d }
        val check = (10 - sum % 10) % 10
        return check == digits.last()
    }

    /** Products packed in store (weighed fruit, deli) use 02/2x prefixes and vary per item. */
    fun isInStoreCode(code: String): Boolean = code.length == 13 && code[0] == '2'
}
