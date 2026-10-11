package io.github.cazadoxs.toma.billing

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Checks that a purchase was signed by Google Play with this app's licence key, so a fake
 * purchase injected on a tampered device isn't accepted. With no key configured (debug builds)
 * the check is skipped.
 */
object PurchaseVerifier {
    fun verify(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (base64PublicKey.isBlank()) return true
        if (signedData.isEmpty() || signature.isEmpty()) return false
        return runCatching {
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Base64.decode(base64PublicKey, Base64.DEFAULT)))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(signedData.toByteArray())
                verify(Base64.decode(signature, Base64.DEFAULT))
            }
        }.getOrDefault(false)
    }
}
