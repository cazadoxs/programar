package app.apunta.billing

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import app.apunta.BuildConfig
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * One-time "Apunta Pro" purchase through Google Play Billing 8.
 *
 * Play is the source of truth: purchases are re-queried on every start, and the
 * cached flag only bridges the moment before Play answers (or when offline).
 * Purchase signatures are checked against the app's Play licence key.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    sealed interface PurchaseEvent {
        data object Success : PurchaseEvent
        data object Pending : PurchaseEvent
        data object Cancelled : PurchaseEvent
        data class Error(val code: Int) : PurchaseEvent
    }

    private val prefs = context.getSharedPreferences("billing", Context.MODE_PRIVATE)
    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _events = MutableStateFlow<PurchaseEvent?>(null)
    val events: StateFlow<PurchaseEvent?> = _events.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) {
            refresh(); return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call.
            }
        })
    }

    fun refresh() {
        queryProduct()
        queryPurchases()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_PRO)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = details.productDetailsList.firstOrNull()
                _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    private fun queryPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any { it.isValidPro() }
                purchases.filter { it.isValidPro() }.forEach { acknowledge(it) }
                setPro(owned)
            }
        }
    }

    /** Returns false when the store is not reachable yet (no product details). */
    fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails ?: run { start(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            ).build()
        val result = client.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                val pro = list.firstOrNull { it.isValidPro() }
                when {
                    pro != null -> {
                        acknowledge(pro); setPro(true); _events.value = PurchaseEvent.Success
                    }
                    list.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                        _events.value = PurchaseEvent.Pending
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryPurchases(); _events.value = PurchaseEvent.Success
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.value = PurchaseEvent.Cancelled
            else -> _events.value = PurchaseEvent.Error(result.responseCode)
        }
    }

    fun consumeEvent() { _events.value = null }

    private fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        ) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "acknowledge failed: ${r.responseCode}")
        }
    }

    private fun setPro(value: Boolean) {
        _isPro.value = value
        prefs.edit().putBoolean(KEY_PRO, value).apply()
    }

    private fun Purchase.isValidPro(): Boolean =
        purchaseState == Purchase.PurchaseState.PURCHASED &&
            products.contains(PRODUCT_PRO) &&
            verifySignature(originalJson, signature)

    companion object {
        const val PRODUCT_PRO = "apunta_pro"
        private const val KEY_PRO = "pro"
        private const val TAG = "Billing"

        /**
         * Verifies Play's RSA signature over the purchase JSON. Without a key
         * configured (local debug builds) verification is skipped only in debug;
         * a release build without the key never grants Pro from a purchase.
         */
        fun verifySignature(json: String, signature: String): Boolean {
            val key = BuildConfig.PLAY_LICENSE_KEY
            if (key.isBlank()) return BuildConfig.DEBUG
            return try {
                val publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT)))
                Signature.getInstance("SHA1withRSA").run {
                    initVerify(publicKey)
                    update(json.toByteArray(Charsets.UTF_8))
                    verify(Base64.decode(signature, Base64.DEFAULT))
                }
            } catch (e: Exception) {
                Log.w(TAG, "signature check failed", e)
                false
            }
        }
    }
}
