package com.cazadoxs.descifra.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives in the Android Keystore (hardware-backed where available) and
 * never leaves it. Everything the user scans and the API key are stored only through this class.
 */
class Vault(private val context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray, aad: ByteArray = EMPTY): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, aad: ByteArray = EMPTY): ByteArray {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }

    /** Atomic write so a crash mid-save cannot corrupt the user's letters. */
    fun writeFile(name: String, plain: ByteArray) {
        val target = File(context.filesDir, name)
        val tmp = File(context.filesDir, "$name.tmp")
        tmp.writeBytes(encrypt(plain, name.toByteArray()))
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "No se pudo guardar" }
        }
    }

    fun readFile(name: String): ByteArray? {
        val f = File(context.filesDir, name)
        if (!f.exists()) return null
        return try {
            decrypt(f.readBytes(), name.toByteArray())
        } catch (e: Exception) {
            null // Key lost (e.g. new phone) or file damaged: start empty rather than crash.
        }
    }

    fun deleteFile(name: String) {
        File(context.filesDir, name).delete()
    }

    fun putString(prefs: String, name: String, value: String?) {
        val sp = context.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        if (value == null) sp.edit().remove(name).apply()
        else sp.edit().putString(name, Base64.encodeToString(encrypt(value.toByteArray(), name.toByteArray()), Base64.NO_WRAP)).apply()
    }

    fun getString(prefs: String, name: String): String? {
        val raw = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(name, null) ?: return null
        return try {
            String(decrypt(Base64.decode(raw, Base64.NO_WRAP), name.toByteArray()))
        } catch (e: Exception) {
            null
        }
    }

    fun wipeKey() {
        keyStore.deleteEntry(ALIAS)
    }

    private companion object {
        const val ALIAS = "descifra_vault_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
        val EMPTY = ByteArray(0)
    }
}
