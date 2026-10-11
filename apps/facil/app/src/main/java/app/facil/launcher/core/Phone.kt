package app.facil.launcher.core

object Phone {
    /** Deja solo dígitos y un "+" inicial. Máximo 20 caracteres. */
    fun clean(raw: String): String {
        val t = raw.trim()
        val digits = t.filter { it.isDigit() }.take(18)
        if (digits.isEmpty()) return ""
        return if (t.startsWith("+") || t.startsWith("00")) "+" + digits.removePrefix(if (t.startsWith("00")) "00" else "") else digits
    }

    fun isValid(p: String) = p.count { it.isDigit() } in 3..18

    /**
     * Número para wa.me: solo dígitos con prefijo de país. Si el número no lo
     * lleva, se usa el prefijo por defecto (34 = España).
     */
    fun forWhatsApp(p: String, defaultCountry: String = "34"): String {
        val c = clean(p)
        if (c.startsWith("+")) return c.drop(1)
        return if (c.length <= 9) defaultCountry + c else c
    }

    /** "612345678" -> "612 345 678" para leerlo mejor en pantalla. */
    fun pretty(p: String): String {
        val c = clean(p)
        val plus = c.startsWith("+")
        val d = c.removePrefix("+")
        if (d.length != 9 && !(plus && d.length == 11)) return c
        val local = d.takeLast(9)
        val prefix = if (plus) "+" + d.dropLast(9) + " " else ""
        return prefix + local.chunked(3).joinToString(" ")
    }
}
