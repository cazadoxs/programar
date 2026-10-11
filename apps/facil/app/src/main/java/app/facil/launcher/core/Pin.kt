package app.facil.launcher.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN de ajustes. Nunca se guarda en claro: PBKDF2-SHA256 con sal aleatoria.
 * La comparación es en tiempo constante y los intentos fallidos bloquean
 * la entrada durante un tiempo creciente ([Throttle]).
 */
object Pin {
    const val ITERATIONS = 60_000
    private const val KEY_BITS = 256

    fun isValidFormat(pin: String) = pin.length in 4..8 && pin.all { it in '0'..'9' }

    fun newSalt(): String {
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(b)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(key)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: String, expected: String): Boolean {
        if (!isValidFormat(pin) || salt.isEmpty() || expected.isEmpty()) return false
        return try {
            MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}

/** Bloqueo progresivo tras fallos: 5 intentos libres, luego 30 s, 60 s, 120 s… hasta 15 min. */
object Throttle {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 15 * 60_000L

    fun lockMillis(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val exp = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_MS shl exp).coerceAtMost(MAX_MS)
    }

    /** Milisegundos que quedan de bloqueo, o 0 si se puede intentar. */
    fun remaining(failures: Int, lastFailureAt: Long, now: Long): Long =
        (lastFailureAt + lockMillis(failures) - now).coerceAtLeast(0)
}
