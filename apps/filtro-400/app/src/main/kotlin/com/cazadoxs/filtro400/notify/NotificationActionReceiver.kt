package com.cazadoxs.filtro400.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cazadoxs.filtro400.core.NumberEntry
import com.cazadoxs.filtro400.core.PhoneNumbers
import com.cazadoxs.filtro400.graph
import kotlinx.coroutines.launch

/** Acción "Permitir este número" de la notificación. No exportado: solo la propia app puede lanzarlo. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALLOW) return
        val number = PhoneNumbers.normalize(intent.getStringExtra(EXTRA_NUMBER)) ?: return
        if (!number.startsWith("+")) return
        val pending = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.settings.updateConfig { c ->
                    if (c.allowed.any { it.number == number }) c
                    else c.copy(
                        allowed = c.allowed + NumberEntry(number, "Desde notificación", System.currentTimeMillis()),
                        blocked = c.blocked.filterNot { it.number == number },
                    )
                }
                Notifications.cancelFiltered(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_ALLOW = "com.cazadoxs.filtro400.ALLOW_NUMBER"
        private const val EXTRA_NUMBER = "number"

        fun allowIntent(context: Context, number: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            number.hashCode(),
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_ALLOW).putExtra(EXTRA_NUMBER, number),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
