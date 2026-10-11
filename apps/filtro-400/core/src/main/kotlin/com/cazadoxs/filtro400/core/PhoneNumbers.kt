package com.cazadoxs.filtro400.core

/**
 * Tipo de número según el Plan Nacional de Numeración español.
 *
 * Solo se clasifica a partir del propio número: nunca hace falta consultar
 * contactos, registro de llamadas ni ningún servidor.
 */
enum class NumberKind {
    /** Rango 400, reservado desde 2026 a llamadas comerciales (Ley 10/2025). */
    COMMERCIAL_400,
    /** 803, 806, 807 y 905: tarificación adicional. */
    PREMIUM_RATE,
    /** 901 y 902: coste compartido. */
    SHARED_COST,
    /** 800 y 900: gratuitos (atención al cliente). */
    FREEPHONE,
    /** 70x: numeración personal. */
    PERSONAL,
    /** 51x: redes corporativas. */
    CORPORATE,
    MOBILE,
    LANDLINE,
    /** Otro número español de 9 cifras que no encaja en los rangos anteriores. */
    OTHER_SPANISH,
    INTERNATIONAL,
    /** Números cortos (1004, 016, 112...). */
    SHORT,
}

object PhoneNumbers {

    private const val SPAIN = "+34"

    /**
     * Normaliza un número tal y como lo entrega el sistema (tel:+34 400 12 34 56,
     * 0034400123456, 400123456...) a formato E.164 ("+34400123456").
     *
     * Los números cortos (3 a 6 cifras) se devuelven solo con sus cifras, sin "+".
     * Devuelve null si no parece un número de teléfono.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim().removePrefix("tel:")
        if (trimmed.isEmpty()) return null
        val plus = trimmed.startsWith("+")
        val digits = StringBuilder()
        for (c in trimmed) {
            when {
                c.isAsciiDigit() -> digits.append(c)
                c == '+' || c == ' ' || c == '-' || c == '.' || c == '(' || c == ')' || c == '/' -> Unit
                // %2B es un "+" codificado en URIs tel:
                else -> return if (trimmed.startsWith("%2B")) normalize("+" + trimmed.substring(3)) else null
            }
        }
        val d = digits.toString()
        if (d.isEmpty() || d.length > 15) return null

        return when {
            plus -> if (d.length >= 7) "+$d" else null
            d.startsWith("00") && d.length >= 9 -> "+" + d.substring(2)
            d.length == 9 && d[0] in '4'..'9' -> SPAIN + d
            d.length == 11 && d.startsWith("34") && d[2] in '4'..'9' -> "+$d"
            d.length in 3..6 -> d
            else -> null
        }
    }

    /** Parte nacional (9 cifras) de un número español, o null si no es español. */
    fun nationalPart(e164: String): String? =
        if (e164.startsWith(SPAIN) && e164.length == 12) e164.substring(3) else null

    fun classify(number: String): NumberKind {
        if (!number.startsWith("+")) return NumberKind.SHORT
        if (!number.startsWith(SPAIN)) return NumberKind.INTERNATIONAL
        val n = nationalPart(number) ?: return NumberKind.OTHER_SPANISH
        return when {
            n.startsWith("400") -> NumberKind.COMMERCIAL_400
            n.startsWith("800") || n.startsWith("900") -> NumberKind.FREEPHONE
            n.startsWith("901") || n.startsWith("902") -> NumberKind.SHARED_COST
            n.startsWith("803") || n.startsWith("806") || n.startsWith("807") || n.startsWith("905") ->
                NumberKind.PREMIUM_RATE
            n.startsWith("70") -> NumberKind.PERSONAL
            n.startsWith("51") -> NumberKind.CORPORATE
            n[0] == '6' || (n[0] == '7' && n[1] in '1'..'4') -> NumberKind.MOBILE
            n[0] == '8' || n[0] == '9' -> NumberKind.LANDLINE
            else -> NumberKind.OTHER_SPANISH
        }
    }

    /** "+34400123456" -> "400 123 456"; los internacionales se muestran tal cual. */
    fun format(number: String): String {
        val n = nationalPart(number) ?: return number
        return "${n.substring(0, 3)} ${n.substring(3, 6)} ${n.substring(6)}"
    }

    /**
     * Normaliza un prefijo escrito por el usuario para las reglas.
     * "400", "+34 400", "0044" -> "+34400", "+34400", "+44".
     */
    fun normalizePrefix(raw: String): String? {
        val t = raw.trim()
        val d = t.filter { it.isAsciiDigit() }
        if (d.isEmpty() || d.length > 12) return null
        return when {
            t.startsWith("+") -> "+$d"
            d.startsWith("00") && d.length > 2 -> "+" + d.substring(2)
            d[0] in '4'..'9' -> SPAIN + d
            else -> null
        }
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
