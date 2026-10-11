package com.cazadoxs.escanerlimpio.billing

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Checks that a purchase was signed by Google Play with this app's licence key
 * (Play Console → Monetise → Monetisation setup). Rejects anything forged by
 * billing emulators. Fails closed when the data or signature is malformed.
 */
object PurchaseVerifier {
    fun verify(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (base64PublicKey.isBlank() || signedData.isEmpty() || signature.isEmpty()) return false
        return runCatching {
            val keyBytes = Base64.decode(base64PublicKey, Base64.DEFAULT)
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(key)
            sig.update(signedData.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(signature, Base64.DEFAULT))
        }.getOrDefault(false)
    }
}
