package io.github.cazadoxs.toma.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.core.DoseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.work(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        work {
            val c = context.container
            val now = Instant.now()
            when (action) {
                ACTION_DUE, ACTION_RERING -> {
                    val due = c.repository.dueDoses(now)
                    if (due.isNotEmpty()) {
                        Notifications.ring(context, due, c.repository.profiles())
                        c.scheduler.scheduleRering(now.plusSeconds(c.settings.current.repeatMinutes * 60L))
                    }
                    c.scheduler.rescheduleAll(now)
                }
                ACTION_CHECK -> c.scheduler.rescheduleAll(now)
            }
        }
    }

    companion object {
        const val ACTION_DUE = "io.github.cazadoxs.toma.DUE"
        const val ACTION_RERING = "io.github.cazadoxs.toma.RERING"
        const val ACTION_CHECK = "io.github.cazadoxs.toma.CHECK"
    }
}

/** Buttons on the alarm notification. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        work {
            val c = context.container
            when (action) {
                ACTION_TAKE_ALL -> {
                    Notifications.dismissAlarm(context)
                    DoseActions.record(context, c.repository.dueDoses(Instant.now()), DoseStatus.TAKEN)
                }
                ACTION_SNOOZE -> snooze(context)
            }
        }
    }

    companion object {
        const val ACTION_TAKE_ALL = "io.github.cazadoxs.toma.TAKE_ALL"
        const val ACTION_SNOOZE = "io.github.cazadoxs.toma.SNOOZE"

        fun pending(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, action.hashCode(),
                Intent(context, DoseActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun snooze(context: Context) {
            val c = context.container
            Notifications.dismissAlarm(context)
            c.scheduler.scheduleRering(Instant.now().plusSeconds(c.settings.current.snoozeMinutes * 60L))
        }
    }
}

/** Alarms don't survive a reboot, a clock change or an update: put them back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> work { context.container.scheduler.rescheduleAll() }
        }
    }
}
