package com.cazadoxs.escaneaycome.billing

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
import com.cazadoxs.escaneaycome.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PlusState {
    data object Loading : PlusState
    data class Available(val price: String) : PlusState
    data object Pending : PlusState
    data object Owned : PlusState
    data class Unavailable(val reason: String) : PlusState
}

/**
 * One-time "Plus" unlock through Google Play Billing. The entitlement is cached in
 * settings so the app works offline, and it is re-checked against Play on every start
 * (a refund removes it).
 */
class BillingManager(context: Context, private val settings: SettingsStore) : PurchasesUpdatedListener {

    private val _state = MutableStateFlow<PlusState>(if (settings.current.plus) PlusState.Owned else PlusState.Loading)
    val state: StateFlow<PlusState> = _state.asStateFlow()

    private var details: ProductDetails? = null

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
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else if (!settings.current.plus) {
                    _state.value = PlusState.Unavailable("Google Play no está disponible ahora mismo.")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun refresh() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handlePurchases(purchases, fromQuery = true)
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ),
        ).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val d = queryResult.productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync
            details = d
            if (_state.value !is PlusState.Owned && _state.value !is PlusState.Pending) {
                val price = d.oneTimePurchaseOfferDetails?.formattedPrice
                _state.value = if (price != null) PlusState.Available(price) else PlusState.Unavailable("Producto no disponible.")
            }
        }
    }

    fun buy(activity: Activity) {
        val d = details
        if (d == null) {
            start(); return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases, fromQuery = false)
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refresh()
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, fromQuery: Boolean) {
        val plus = purchases.filter { PRODUCT_ID in it.products }
        val purchased = plus.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        when {
            purchased != null -> {
                grant(true)
                if (!purchased.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchased.purchaseToken).build()
                    client.acknowledgePurchase(ack) { }
                }
            }
            plus.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> _state.value = PlusState.Pending
            // Only an authoritative query can revoke (refunds, family-library changes).
            fromQuery -> grant(false)
        }
    }

    private fun grant(owned: Boolean) {
        if (settings.current.plus != owned) settings.update { it.copy(plus = owned) }
        if (owned) {
            _state.value = PlusState.Owned
        } else if (_state.value is PlusState.Owned) {
            _state.value = PlusState.Loading
        }
    }

    companion object {
        /** Create this one-time product in Play Console > Monetize > In-app products. */
        const val PRODUCT_ID = "plus_para_siempre"
    }
}
