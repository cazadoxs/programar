package com.cazadoxs.filtro400.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.core.CallFilter
import com.cazadoxs.filtro400.core.Decision
import com.cazadoxs.filtro400.core.FilterConfig
import com.cazadoxs.filtro400.core.PhoneNumbers
import com.cazadoxs.filtro400.core.Reason
import com.cazadoxs.filtro400.graph
import com.cazadoxs.filtro400.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/**
 * Servicio que Android llama antes de que suene una llamada de un número que NO
 * está en tus contactos (los contactos nunca se le pasan, porque Filtro 400 no
 * pide permiso de contactos). Tampoco llegan los números ocultos: Android no se
 * los entrega a ninguna app de filtrado.
 *
 * Hay que responder en menos de 5 segundos o Android deja sonar la llamada. Por
 * eso la decisión se toma con datos ya en memoria, con un tope de 3,5 s, y ante
 * cualquier error la llamada SUENA: nunca se pierde una llamada por un fallo.
 */
class FiltroScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val graph = applicationContext.graph
        val raw = details.handle?.schemeSpecificPart
        val now = System.currentTimeMillis()

        scope.launch {
            var config = FilterConfig()
            val decision = withTimeoutOrNull(DECISION_BUDGET_MS) {
                runCatching {
                    config = graph.config ?: withContext(Dispatchers.IO) { graph.settings.currentConfig() }
                    val lookup = if (graph.premium) {
                        withContext(Dispatchers.IO) { graph.community.loadCached() }
                        graph.community.index.value::lookup
                    } else null
                    CallFilter.decide(raw, config, now, ZoneId.systemDefault(), lookup)
                }.getOrNull()
            } ?: Decision(CallAction.ALLOW, Reason.DEFAULT)

            respondToCall(details, buildResponse(decision.action, config))

            // Lo que no afecta a la llamada se hace después de responder.
            val number = PhoneNumbers.normalize(raw) ?: raw.orEmpty()
            graph.scope.launch(Dispatchers.IO) {
                graph.history.insert(number, decision.action, decision.reason, decision.detail, now)
                if (decision.action != CallAction.ALLOW && config.notifyOnBlock) {
                    Notifications.showFiltered(applicationContext, number, decision)
                }
            }
        }
    }

    private fun buildResponse(action: CallAction, config: FilterConfig): CallResponse {
        val b = CallResponse.Builder()
        when (action) {
            CallAction.BLOCK -> b.setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(config.skipCallLog)
            CallAction.SILENCE -> b.setSilenceCall(true)
            CallAction.ALLOW -> Unit
        }
        return b.build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val DECISION_BUDGET_MS = 3_500L
    }
}
