package com.cazadoxs.filtro400.billing

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
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.cazadoxs.filtro400.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Un plan de la suscripción "Comunidad" tal y como lo devuelve Google Play. */
data class Plan(
    val basePlanId: String,
    val price: String,
    /** Periodo ISO-8601: P1M, P1Y... */
    val period: String,
    val offerToken: String,
    val hasFreeTrial: Boolean,
    internal val details: ProductDetails,
)

/**
 * Suscripción Premium con Google Play Billing.
 *
 * El filtrado local es gratis y sin límites. La suscripción solo da acceso a la
 * lista de la comunidad, que cuesta dinero mantener actualizada. El servidor
 * vuelve a comprobar el token de compra con Google antes de entregar la lista,
 * así que modificar la app no sirve para saltarse el pago.
 */
class BillingManager(
    context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                scope.launch { handlePurchases(purchases) }
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                _available.value = result.responseCode == BillingClient.BillingResponseCode.OK
                if (_available.value) scope.launch { refresh() }
            }

            override fun onBillingServiceDisconnected() {
                _available.value = false
            }
        })
    }

    /** Vuelve a leer planes y compras activas (también sirve de "Restaurar compra"). */
    suspend fun refresh() {
        if (!client.isReady) return
        _plans.value = queryPlans()
        // null = Google Play no respondió: no tocamos el estado Premium guardado.
        val purchases = queryActivePurchases() ?: return
        handlePurchases(purchases)
        if (purchases.none { it.isOurs() && it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            settings.setPremium(false, null)
        }
    }

    fun launch(activity: Activity, plan: Plan): Boolean {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.details)
                        .setOfferToken(plan.offerToken)
                        .build(),
                ),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val active = purchases.firstOrNull { it.isOurs() && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            ?: return
        if (!active.isAcknowledged) acknowledge(active.purchaseToken)
        settings.setPremium(true, active.purchaseToken)
    }

    private fun Purchase.isOurs() = products.contains(PRODUCT_ID)

    private suspend fun queryPlans(): List<Plan> = suspendCancellableCoroutine { cont ->
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            val list = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details.productDetailsList.flatMap { pd ->
                    pd.subscriptionOfferDetails.orEmpty()
                        // Una entrada por plan base: preferimos la oferta con prueba gratis si la hay.
                        .groupBy { it.basePlanId }
                        .mapNotNull { (basePlan, offers) ->
                            val offer = offers.firstOrNull { o ->
                                o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
                            } ?: offers.firstOrNull { it.offerId == null } ?: offers.first()
                            val phases = offer.pricingPhases.pricingPhaseList
                            val recurring = phases.lastOrNull() ?: return@mapNotNull null
                            Plan(
                                basePlanId = basePlan,
                                price = recurring.formattedPrice,
                                period = recurring.billingPeriod,
                                offerToken = offer.offerToken,
                                hasFreeTrial = phases.any { it.priceAmountMicros == 0L },
                                details = pd,
                            )
                        }
                }.sortedBy { if (it.period == "P1Y") 0 else 1 }
            } else emptyList()
            if (cont.isActive) cont.resume(list)
        }
    }

    private suspend fun queryActivePurchases(): List<Purchase>? = suspendCancellableCoroutine { cont ->
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (cont.isActive) {
                cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases else null)
            }
        }
    }

    private suspend fun acknowledge(token: String) = suspendCancellableCoroutine { cont ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) {
            if (cont.isActive) cont.resume(Unit)
        }
    }

    companion object {
        /** Debe coincidir con el ID de la suscripción creada en Play Console. */
        const val PRODUCT_ID = "comunidad"
    }
}
