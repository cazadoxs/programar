package app.facil.launcher.core

import java.security.SecureRandom

/**
 * Códigos de emparejamiento. 10 caracteres de un alfabeto sin letras
 * confusas (32^10 ≈ 10^15 combinaciones), caducan a los 15 minutos y
 * además cada familiar tiene que ser aprobado en el móvil de la persona
 * mayor, así que adivinar un código no da acceso a nada.
 */
object Codes {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 10
    const val TTL_MS = 15 * 60_000L
    const val QR_PREFIX = "FACIL:"

    private val rnd = SecureRandom()

    fun newCode(): String = buildString { repeat(LENGTH) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }

    /** "K7P 4XM 9QD A" o "facil:k7p4xm9qda" -> "K7P4XM9QDA"; null si no es válido. */
    fun normalize(input: String): String? {
        var s = input.trim().uppercase()
        if (s.startsWith(QR_PREFIX)) s = s.removePrefix(QR_PREFIX)
        s = s.filter { it.isLetterOrDigit() }
        if (s.length != LENGTH || s.any { it !in ALPHABET }) return null
        return s
    }

    fun pretty(code: String) = code.chunked(4).joinToString(" ")

    fun qrPayload(code: String) = QR_PREFIX + code
}
