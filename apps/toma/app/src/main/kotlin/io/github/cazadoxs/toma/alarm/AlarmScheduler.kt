package io.github.cazadoxs.toma.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.cazadoxs.toma.caregiver.CaregiverAlerts
import io.github.cazadoxs.toma.data.Repository
import io.github.cazadoxs.toma.settings.Settings
import io.github.cazadoxs.toma.ui.MainActivity
import io.github.cazadoxs.toma.widget.NextDoseWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Keeps at most three alarms registered, all derived from the database so there is no state
 * to drift: the next dose, a re-ring while doses are unconfirmed, and the check that turns
 * unconfirmed doses into missed ones (and tells the caregiver).
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: Repository,
    private val settings: Settings,
) {
    private val mutex = Mutex()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleAll(now: Instant = Instant.now()) = mutex.withLock {
        val prefs = settings.current
        val graceMillis = prefs.graceMinutes * 60_000L
        val missed = repository.markMissed(now, graceMillis)
        if (missed.isNotEmpty()) CaregiverAlerts.onMissed(context, missed, repository, prefs)
        settings.update { it.copy(lastSweepAt = now.toEpochMilli()) }

        val next = repository.nextDose(now)
        if (next != null) setAlarmClock(REQ_DUE, AlarmReceiver.ACTION_DUE, next.at) else cancel(REQ_DUE, AlarmReceiver.ACTION_DUE)

        val due = repository.dueDoses(now)
        if (due.isEmpty()) {
            Notifications.dismissAlarm(context)
            cancel(REQ_RERING, AlarmReceiver.ACTION_RERING)
            cancel(REQ_CHECK, AlarmReceiver.ACTION_CHECK)
        } else {
            val checkAt = due.first().at.plusMillis(graceMillis)
            setExact(REQ_CHECK, AlarmReceiver.ACTION_CHECK, maxOf(checkAt, now.plusSeconds(5)))
        }
        NextDoseWidget.updateAll(context)
    }

    fun scheduleRering(at: Instant) = setAlarmClock(REQ_RERING, AlarmReceiver.ACTION_RERING, at)

    fun cancelRering() = cancel(REQ_RERING, AlarmReceiver.ACTION_RERING)

    private fun pendingIntent(request: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, request,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Alarm-clock alarms are exempt from Doze and shown in the status bar: right for a dose. */
    private fun setAlarmClock(request: Int, action: String, at: Instant) {
        val operation = pendingIntent(request, action)
        try {
            if (canScheduleExact()) {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), operation)
                return
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call: fall through to inexact.
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
    }

    private fun setExact(request: Int, action: String, at: Instant) {
        val operation = pendingIntent(request, action)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
                return
            }
        } catch (_: SecurityException) {
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
    }

    private fun cancel(request: Int, action: String) = alarmManager.cancel(pendingIntent(request, action))

    private companion object {
        const val REQ_DUE = 10
        const val REQ_RERING = 11
        const val REQ_CHECK = 12
    }
}
