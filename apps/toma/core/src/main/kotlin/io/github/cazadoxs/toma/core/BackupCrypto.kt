package io.github.cazadoxs.toma.core

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for backup files: PBKDF2-HMAC-SHA256 derives an AES-256 key and
 * AES-GCM encrypts and authenticates the content, so a wrong password or a tampered file is
 * rejected instead of producing garbage.
 *
 * File layout: "TOMA" | version (1 byte) | iterations (int) | salt (16) | iv (12) | ciphertext+tag.
 * The header is bound to the ciphertext as associated data.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'O'.code.toByte(), 'M'.code.toByte(), 'A'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES
    const val DEFAULT_ITERATIONS = 310_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    const val MIN_PASSWORD_LENGTH = 8
    /** Refuse absurdly large inputs before allocating anything for them. */
    const val MAX_FILE_BYTES = 64 * 1024 * 1024

    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "password too short" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /** @throws BackupException when the file is not a Toma backup, the password is wrong or it was modified. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size > MAX_FILE_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE)
        if (file.size < HEADER_BYTES + TAG_BITS / 8) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        val buffer = ByteBuffer.wrap(file)
        val magic = ByteArray(4).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (buffer.get() != VERSION) throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION)
        val iterations = buffer.getInt()
        // A crafted header must not be able to make us spin for minutes.
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(file, 0, HEADER_BYTES)
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED)
        }
    }

    fun looksLikeBackup(file: ByteArray): Boolean =
        file.size >= 4 && file.copyOfRange(0, 4).contentEquals(MAGIC)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_BACKUP, UNSUPPORTED_VERSION, WRONG_PASSWORD_OR_DAMAGED, TOO_LARGE, INVALID_CONTENT }
}
