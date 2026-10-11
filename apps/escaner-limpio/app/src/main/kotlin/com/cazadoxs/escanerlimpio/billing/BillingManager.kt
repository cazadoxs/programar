package com.cazadoxs.escanerlimpio.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.cazadoxs.escanerlimpio.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time "Pro" purchase through Google Play Billing. No server and no
 * account: ownership comes from Play and is cached for offline use, and every
 * purchase signature is verified on the device.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {
    private val prefs = context.getSharedPreferences("compras", Context.MODE_PRIVATE)
    private val _isPro = MutableStateFlow(prefs.getBoolean(K_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var product: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else Log.w(TAG, "Billing setup: ${result.debugMessage}")
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun refresh() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = details.productDetailsList.firstOrNull()
                _price.value = product?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // Purchases missing here were refunded or revoked.
                setPro(purchases.any { owns(it) })
                purchases.forEach(::acknowledgeIfNeeded)
            }
        }
    }

    fun buy(activity: Activity) {
        val details = product
        if (details == null) {
            _message.value = "La tienda no está disponible ahora mismo. Comprueba que tienes Google Play y vuelve a intentarlo."
            start()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            ).build()
        client.launchBillingFlow(activity, params)
    }

    fun restore() {
        _message.value = "Comprobando tus compras…"
        start()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { p ->
                when {
                    owns(p) -> {
                        setPro(true)
                        acknowledgeIfNeeded(p)
                        _message.value = "¡Gracias! Pro está activado."
                    }
                    p.purchaseState == Purchase.PurchaseState.PENDING ->
                        _message.value = "Pago pendiente. Pro se activará en cuanto se confirme."
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = "No se pudo completar la compra (${result.responseCode})."
        }
    }

    private fun owns(p: Purchase): Boolean =
        p.purchaseState == Purchase.PurchaseState.PURCHASED &&
            PRODUCT_ID in p.products &&
            (BuildConfig.DEBUG && BuildConfig.PLAY_LICENSE_KEY.isBlank() ||
                PurchaseVerifier.verify(BuildConfig.PLAY_LICENSE_KEY, p.originalJson, p.signature))

    private fun acknowledgeIfNeeded(p: Purchase) {
        if (!owns(p) || p.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
        ) { r -> if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Ack failed: ${r.debugMessage}") }
    }

    private fun setPro(value: Boolean) {
        _isPro.value = value
        prefs.edit().putBoolean(K_PRO, value).apply()
    }

    companion object {
        private const val TAG = "Billing"
        const val PRODUCT_ID = "escaner_limpio_pro"
        private const val K_PRO = "pro"
    }
}
