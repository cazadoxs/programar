package app.facil.launcher.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.facil.launcher.core.Schedule
import app.facil.launcher.data.Store

/**
 * Una sola alarma: la del próximo recordatorio. Al sonar avisa de los que
 * tocan y programa el siguiente. Alarmas inexactas que funcionan en reposo
 * (no pedimos el permiso de alarmas exactas, restringido en Play).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = Store.get(ctx)
        when (intent.action) {
            ACTION_FIRE -> {
                val now = System.currentTimeMillis()
                val last = ctx.getSharedPreferences("facil_alarm", Context.MODE_PRIVATE)
                val lastFired = last.getLong("lastFired", now - 30 * 60_000L)
                store.config.reminders.forEach { r ->
                    val occ = Schedule.lastOccurrence(r, now) ?: return@forEach
                    if (occ > lastFired && now - occ < 3 * 3_600_000L) Notifs.reminder(ctx, r)
                }
                last.edit().putLong("lastFired", now).apply()
                schedule(ctx)
            }
            ACTION_DONE -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                if (store.config.reminders.none { it.id == id }) return
                store.markReminderDone(id, System.currentTimeMillis())
                Notifs.cancelReminder(ctx, id)
                SyncJob.runSoon(ctx)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                schedule(ctx)
                SyncJob.schedule(ctx)
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.facil.launcher.REMINDER_FIRE"
        const val ACTION_DONE = "app.facil.launcher.REMINDER_DONE"
        const val EXTRA_ID = "id"

        fun schedule(ctx: Context) {
            val am = ctx.svc<AlarmManager>()
            val pi = PendingIntent.getBroadcast(
                ctx, 1, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.cancel(pi)
            val store = Store.get(ctx)
            if (!store.isSenior) return
            val now = System.currentTimeMillis()
            val next = store.config.reminders.mapNotNull { Schedule.next(it, now) }.minOrNull() ?: return
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }
}
