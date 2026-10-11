package vlc.busmetro.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vlc.busmetro.data.Prefs

/**
 * One-time purchase "Pro": removes the banner and unlocks the advanced
 * widgets and extra favourites. Entitlement is re-read from Play on every
 * start, so it follows the Google account across devices; the local flag is
 * only a cache for offline starts.
 */
class Billing(context: Context, private val prefs: Prefs, private val scope: CoroutineScope) : PurchasesUpdatedListener {

    data class Offer(val price: String)

    private val _offer = MutableStateFlow<Offer?>(null)
    val offer: StateFlow<Offer?> = _offer
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private var details: ProductDetails? = null
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        scope.launch {
            var attempt = 0
            while (!client.isReady && attempt < 3) {
                connect()
                if (!client.isReady) delay(2000L shl attempt++)
            }
            if (client.isReady) { loadProduct(); restore() }
        }
    }

    private suspend fun connect() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRO_ID).setProductType(BillingClient.ProductType.INAPP).build())).build()
        val res = client.queryProductDetails(params)
        details = res.productDetailsList?.firstOrNull()
        _offer.value = details?.oneTimePurchaseOfferDetails?.let { Offer(it.formattedPrice) }
    }

    /** Re-checks ownership (also picks up purchases made on another device or restored after reinstall). */
    suspend fun restore() {
        val res = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (res.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val owned = res.purchasesList.filter { it.products.contains(PRO_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        owned.forEach { acknowledge(it) }
        prefs.setPro(owned.isNotEmpty())
    }

    fun launch(activity: Activity) {
        val d = details ?: return
        _busy.value = true
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())).build()
        val r = client.launchBillingFlow(activity, flow)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) _busy.value = false
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        _busy.value = false
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        scope.launch {
            purchases.orEmpty().filter { it.products.contains(PRO_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach {
                acknowledge(it); prefs.setPro(true)
            }
        }
    }

    private suspend fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
    }

    companion object { const val PRO_ID = "pro_unlock" }
}
