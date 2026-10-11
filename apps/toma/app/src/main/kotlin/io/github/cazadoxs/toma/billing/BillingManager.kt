package io.github.cazadoxs.toma.billing

import android.app.Activity
import android.content.Context
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
import io.github.cazadoxs.toma.BuildConfig
import io.github.cazadoxs.toma.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Toma Plus is a single one-time purchase. Everything about taking medication stays free; Plus
 * only unlocks comfort extras (themes, widget, automatic backups).
 */
class BillingManager(
    context: Context,
    private val settings: Settings,
) : PurchasesUpdatedListener {

    enum class Status { IDLE, CONNECTING, READY, UNAVAILABLE, PENDING, ERROR }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        if (_status.value == Status.CONNECTING) return
        _status.value = Status.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _status.value = Status.READY
                    refresh()
                } else {
                    _status.value = Status.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = Status.IDLE
            }
        })
    }

    private fun refresh() {
        val productParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_PLUS)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ),
        ).build()
        client.queryProductDetailsAsync(productParams) { result, productResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = productResult.productDetailsList.firstOrNull { it.productId == PRODUCT_PLUS }
                _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            // Only trust a successful answer: an offline phone must not lose Plus.
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any(::isValidPlusPurchase)
                purchases.filter(::isValidPlusPurchase).forEach(::acknowledge)
                settings.update { it.copy(plusOwned = owned) }
                if (purchases.any { PRODUCT_PLUS in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }) {
                    _status.value = Status.PENDING
                }
            }
        }
    }

    /** @return false when the store isn't ready (no Play Store, offline, product not set up). */
    fun buy(activity: Activity): Boolean {
        val product = details ?: run {
            connect()
            return false
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
        ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { purchase ->
                when {
                    isValidPlusPurchase(purchase) -> {
                        settings.update { it.copy(plusOwned = true) }
                        acknowledge(purchase)
                        _status.value = Status.READY
                    }
                    purchase.purchaseState == Purchase.PurchaseState.PENDING -> _status.value = Status.PENDING
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _status.value = Status.ERROR
        }
    }

    private fun isValidPlusPurchase(purchase: Purchase): Boolean =
        PRODUCT_PLUS in purchase.products &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            PurchaseVerifier.verify(BuildConfig.PLAY_LICENSE_KEY, purchase.originalJson, purchase.signature)

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { }
    }

    companion object {
        const val PRODUCT_PLUS = "toma_plus"
    }
}
