package com.cazadoxs.descifra.billing

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.cazadoxs.descifra.BuildConfig
import com.cazadoxs.descifra.data.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class ProState(
    val isPro: Boolean = false,
    val price: String? = null,
    val pending: Boolean = false,
    val message: String? = null,
)

/**
 * One-time purchase "Descifra Pro". The core (scan, read, explain, deadlines, calendar) is free
 * forever. Pro adds the extras that cost real effort: your own cloud AI key, questions about the
 * letter, reply drafts and PDF export.
 *
 * Entitlement is cached encrypted so it works offline, and is re-checked against Google Play at
 * every launch. Purchase signatures are verified with the Play licence key when one is configured.
 */
class Billing(context: Context, private val vault: Vault) {
    private val app = context.applicationContext
    private val _state = MutableStateFlow(ProState(isPro = cachedPro()))
    val state: StateFlow<ProState> = _state.asStateFlow()
    private var details: ProductDetails? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handle(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _state.value = _state.value.copy(message = "La compra no se ha completado. No se ha cobrado nada.")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private fun cachedPro(): Boolean = vault.getString("billing", "pro") == "1"

    private fun setPro(value: Boolean) {
        vault.putString("billing", "pro", if (value) "1" else null)
        _state.value = _state.value.copy(isPro = value, pending = false, message = null)
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    /** Call on launch and on resume. Never removes Pro just because Play is unreachable. */
    suspend fun refresh() {
        if (!connect()) return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val res = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build())
        details = res.productDetailsList?.firstOrNull()
        _state.value = _state.value.copy(price = details?.oneTimePurchaseOfferDetails?.formattedPrice)

        val purchases = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (purchases.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val owned = purchases.purchasesList.firstOrNull { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED && verify(it) }
        if (owned != null) {
            acknowledgeIfNeeded(owned)
            setPro(true)
        } else if (purchases.purchasesList.none { it.products.contains(PRODUCT_ID) }) {
            // Refunded or never bought: Play answered successfully and there is no purchase.
            setPro(false)
        }
    }

    suspend fun buy(activity: Activity) {
        if (!connect()) {
            _state.value = _state.value.copy(message = "No se pudo conectar con Google Play.")
            return
        }
        if (details == null) refresh()
        val d = details ?: run {
            _state.value = _state.value.copy(message = "Descifra Pro todavía no está disponible en Google Play.")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun handle(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID)) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!verify(purchase)) {
                    _state.value = _state.value.copy(message = "No se pudo verificar la compra.")
                    return
                }
                setPro(true)
                ackAsync(purchase)
            }
            Purchase.PurchaseState.PENDING -> _state.value = _state.value.copy(pending = true, message = "Pago pendiente. Se activará cuando se confirme.")
            else -> Unit
        }
    }

    private fun ackAsync(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
    }

    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
        }
    }

    /** Checks Google's RSA signature when a licence key is configured at build time. */
    private fun verify(purchase: Purchase): Boolean {
        val key = BuildConfig.PLAY_LICENSE_KEY
        if (key.isBlank()) return true
        return try {
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT)))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(publicKey)
                update(purchase.originalJson.toByteArray())
                verify(Base64.decode(purchase.signature, Base64.DEFAULT))
            }
        } catch (e: Exception) {
            false
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    companion object {
        const val PRODUCT_ID = "descifra_pro"
    }
}
