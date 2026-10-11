package com.cazadoxs.filtro400.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cazadoxs.filtro400.billing.Plan
import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.core.CallFilter
import com.cazadoxs.filtro400.core.Decision
import com.cazadoxs.filtro400.core.ExpectedCall
import com.cazadoxs.filtro400.core.FilterConfig
import com.cazadoxs.filtro400.core.NumberEntry
import com.cazadoxs.filtro400.core.PhoneNumbers
import com.cazadoxs.filtro400.core.PrefixRule
import com.cazadoxs.filtro400.core.QuietHours
import com.cazadoxs.filtro400.core.SpamCategory
import com.cazadoxs.filtro400.data.AppFlags
import com.cazadoxs.filtro400.data.CallRecord
import com.cazadoxs.filtro400.data.NetResult
import com.cazadoxs.filtro400.data.Stats
import com.cazadoxs.filtro400.graph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val g = app.graph
    private val zone get() = ZoneId.systemDefault()

    val config: StateFlow<FilterConfig?> = g.settings.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val flags: StateFlow<AppFlags?> = g.settings.flags
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val history: StateFlow<List<CallRecord>> = g.history.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun startOfToday() = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

    val today: StateFlow<Stats> = g.history.stats(startOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Stats(0, 0, 0))

    val week: StateFlow<Stats> = g.history.stats(startOfToday() - 6 * DAY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Stats(0, 0, 0))

    /** Filtradas por día, de hace 6 días (índice 0) a hoy (índice 6). */
    val daily: StateFlow<List<Int>> = g.history.dailyFiltered(7, startOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(7) { 0 })

    val communitySize: StateFlow<Int> = g.community.index.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val communityConfigured: Boolean get() = g.community.isConfigured

    val plans: StateFlow<List<Plan>> get() = g.billing.plans
    val billingAvailable: StateFlow<Boolean> get() = g.billing.available

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val ready: StateFlow<Boolean> = combine(config, flags) { c, f -> c != null && f != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun say(msg: String) { _messages.tryEmit(msg) }

    private fun update(t: (FilterConfig) -> FilterConfig) = viewModelScope.launch { g.settings.updateConfig(t) }

    // ---- Filtro -------------------------------------------------------------

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun set400(a: CallAction) = update { it.copy(commercial400 = a) }
    fun setPremiumRate(a: CallAction) = update { it.copy(premiumRate = a) }
    fun setSharedCost(a: CallAction) = update { it.copy(sharedCost = a) }
    fun setInternational(a: CallAction) = update { it.copy(international = a) }
    fun setCommunity(a: CallAction) = update { it.copy(community = a) }
    fun setQuietHours(q: QuietHours) = update { it.copy(quietHours = q) }
    fun setNotify(on: Boolean) = update { it.copy(notifyOnBlock = on) }
    fun setSkipCallLog(on: Boolean) = update { it.copy(skipCallLog = on) }
    fun setHistoryDays(days: Int) = update { it.copy(historyDays = days) }

    fun addPrefixRule(raw: String, action: CallAction, label: String): Boolean {
        val prefix = PhoneNumbers.normalizePrefix(raw) ?: return false.also { say("Prefijo no válido") }
        update { c ->
            c.copy(prefixRules = c.prefixRules.filterNot { it.prefix == prefix } +
                PrefixRule(UUID.randomUUID().toString(), prefix, action, label.trim().take(40)))
        }
        return true
    }

    fun removePrefixRule(id: String) = update { c -> c.copy(prefixRules = c.prefixRules.filterNot { it.id == id }) }

    fun block(raw: String, label: String = ""): Boolean {
        val n = fullNumber(raw) ?: return false
        update { c ->
            c.copy(
                blocked = c.blocked.filterNot { it.number == n } + NumberEntry(n, label.trim().take(40), System.currentTimeMillis()),
                allowed = c.allowed.filterNot { it.number == n },
            )
        }
        say("${PhoneNumbers.format(n)} bloqueado siempre")
        return true
    }

    fun allow(raw: String, label: String = ""): Boolean {
        val n = fullNumber(raw) ?: return false
        update { c ->
            c.copy(
                allowed = c.allowed.filterNot { it.number == n } + NumberEntry(n, label.trim().take(40), System.currentTimeMillis()),
                blocked = c.blocked.filterNot { it.number == n },
            )
        }
        say("${PhoneNumbers.format(n)} siempre sonará")
        return true
    }

    fun unblock(n: String) = update { c -> c.copy(blocked = c.blocked.filterNot { it.number == n }) }
    fun unallow(n: String) = update { c -> c.copy(allowed = c.allowed.filterNot { it.number == n }) }

    fun addExpected(label: String, rawNumber: String?, durationMs: Long): Boolean {
        val number = rawNumber?.takeIf { it.isNotBlank() }?.let { fullNumber(it) ?: return false }
        val until = System.currentTimeMillis() + durationMs
        update { c ->
            c.withoutExpired(System.currentTimeMillis()).let {
                it.copy(expected = it.expected + ExpectedCall(UUID.randomUUID().toString(), label.trim().ifBlank { "Llamada esperada" }.take(40), number, until))
            }
        }
        return true
    }

    fun removeExpected(id: String) = update { c -> c.copy(expected = c.expected.filterNot { it.id == id }) }

    /** "Probar un número": qué haría el filtro ahora mismo con este número. */
    fun test(raw: String): Decision? {
        val c = config.value ?: return null
        if (PhoneNumbers.normalize(raw) == null) return null
        val lookup = if (flags.value?.premium == true) g.community.index.value::lookup else null
        return CallFilter.decide(raw, c, System.currentTimeMillis(), zone, lookup)
    }

    private fun fullNumber(raw: String): String? {
        val n = PhoneNumbers.normalize(raw)
        if (n == null || !n.startsWith("+")) {
            say("Escribe el número completo, por ejemplo 612 345 678")
            return null
        }
        return n
    }

    // ---- Historial ----------------------------------------------------------

    fun report(record: CallRecord, category: SpamCategory) = viewModelScope.launch {
        when (val r = g.community.report(record.number, category)) {
            NetResult.Ok -> {
                g.history.markReported(record.id)
                say("Gracias. Tu denuncia es anónima y ayuda a toda la comunidad.")
            }
            NetResult.NotConfigured -> say("La lista de la comunidad aún no está disponible")
            NetResult.NotPremium -> say("No se pudo enviar")
            is NetResult.Failed -> say(r.message)
        }
    }

    fun deleteRecord(id: Long) = viewModelScope.launch { g.history.delete(id) }
    fun clearHistory() = viewModelScope.launch { g.history.clear(); say("Historial borrado") }

    // ---- App ----------------------------------------------------------------

    fun finishOnboarding() = viewModelScope.launch { g.settings.setOnboarded() }
    fun setRobinsonDone(done: Boolean) = viewModelScope.launch { g.settings.setRobinsonDone(done) }

    fun connectBilling() = g.billing.connect()

    fun buy(activity: Activity, plan: Plan) {
        if (!g.billing.launch(activity, plan)) say("Google Play no está disponible ahora mismo")
    }

    fun restore() = viewModelScope.launch {
        g.billing.refresh()
        say(if (flags.value?.premium == true) "Premium activo" else "No hay ninguna suscripción activa en esta cuenta")
    }

    fun syncCommunity() = viewModelScope.launch {
        when (val r = g.community.sync()) {
            NetResult.Ok -> say("Lista actualizada: ${communitySize.value} números")
            NetResult.NotConfigured -> say("La lista de la comunidad aún no está disponible")
            NetResult.NotPremium -> say("Hace falta Premium para descargar la lista")
            is NetResult.Failed -> say(r.message)
        }
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
