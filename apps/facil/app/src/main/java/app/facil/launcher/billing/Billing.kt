package app.facil.launcher.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.facil.launcher.data.Store
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

/** Un plan de la suscripción tal como lo devuelve Google Play. */
data class Plan(val basePlanId: String, val price: String, val period: String, val offerToken: String, val hasTrial: Boolean)

/**
 * Suscripción "Fácil Familia" (producto `facil_familia` en Play Console, con
 * los planes base `anual` y `mensual`). Desbloquea en el móvil del familiar
 * la edición remota y las alertas. El lanzador en sí es gratis y sin anuncios.
 *
 * La comprobación es en el dispositivo contra Google Play. Para un control
 * a prueba de manipulación habría que validar el token en un servidor
 * (ver docs/SEGURIDAD.md); el riesgo es bajo porque solo afecta a funciones
 * de pago del propio usuario, no a datos de terceros.
 */
class Billing private constructor(ctx: Context) : PurchasesUpdatedListener {
    private val app = ctx.applicationContext
    private val store = Store.get(app)
    private val main = Handler(Looper.getMainLooper())
    private var details: ProductDetails? = null
    private val listeners = mutableSetOf<() -> Unit>()

    var lastError: String = ""
        private set

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    val plans: List<Plan>
        get() = details?.subscriptionOfferDetails.orEmpty().map { o ->
            val phases = o.pricingPhases.pricingPhaseList
            val paid = phases.lastOrNull()
            Plan(
                basePlanId = o.basePlanId,
                price = paid?.formattedPrice ?: "",
                period = paid?.billingPeriod ?: "",
                offerToken = o.offerToken,
                hasTrial = phases.any { it.priceAmountMicros == 0L },
            )
        }.sortedWith(compareBy({ it.basePlanId != "anual" }, { !it.hasTrial }))
            .distinctBy { it.basePlanId }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyChanged() = main.post { listeners.toList().forEach { it() } }

    fun connect() {
        if (client.isReady) { refresh(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else { lastError = "Google Play no está disponible"; notifyChanged() }
            }
            override fun onBillingServiceDisconnected() { /* se reintenta en el próximo connect() */ }
        })
    }

    private fun refresh() {
        val q = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(q) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) details = list.firstOrNull()
            notifyChanged()
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { r, purchases ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, fullList = true)
        }
    }

    fun buy(activity: Activity, plan: Plan): Boolean {
        val d = details ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(d).setOfferToken(plan.offerToken).build()
        )).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), fullList = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> { lastError = "No se pudo completar la compra"; notifyChanged() }
        }
    }

    private fun handle(purchases: List<Purchase>, fullList: Boolean) {
        val active = purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        active.filter { !it.isAcknowledged }.forEach { p ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            ) { /* si falla, Play lo reintenta y lo devolverá en la próxima consulta */ }
        }
        // Con la lista completa sabemos si ya no hay suscripción (caducada/cancelada).
        if (active.isNotEmpty()) store.premium = true
        else if (fullList) store.premium = false
        notifyChanged()
    }

    companion object {
        const val PRODUCT_ID = "facil_familia"

        @Volatile private var instance: Billing? = null
        fun get(ctx: Context): Billing = instance ?: synchronized(this) {
            instance ?: Billing(ctx).also { instance = it }
        }

        /** "P1Y" -> "al año", "P1M" -> "al mes". */
        fun periodText(iso: String): String = when (iso) {
            "P1Y" -> "al año"
            "P6M" -> "cada 6 meses"
            "P3M" -> "cada 3 meses"
            "P1M" -> "al mes"
            "P1W" -> "a la semana"
            else -> ""
        }
    }
}
