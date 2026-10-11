package com.cazadoxs.descifra.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cazadoxs.descifra.DescifraApp
import com.cazadoxs.descifra.MainActivity
import com.cazadoxs.descifra.core.model.Deadline
import com.cazadoxs.descifra.core.model.DeadlineKind
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "plazos"
    private const val WORK = "descifra_daily_reminders"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Plazos de tus cartas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisos antes de que venza un plazo"
                    // The text of a notification can show on the lock screen: keep it generic there.
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    /** Daily check at the user's chosen hour. Survives reboots and Doze (WorkManager). */
    fun schedule(context: Context, hour: Int) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(hour, 0)
        if (!first.isAfter(now)) first = first.plusDays(1)
        val delay = Duration.between(now, first)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun canNotify(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Opens the user's calendar app with the event pre-filled; no calendar permission needed. */
    fun addToCalendarIntent(title: String, deadline: Deadline): Intent {
        val date = LocalDate.parse(deadline.date)
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "${deadline.label}: $title")
            .putExtra(CalendarContract.Events.DESCRIPTION, "Plazo detectado por Descifra. Compruébalo en la carta original." +
                (if (deadline.estimated) "\nFecha estimada: ${deadline.basis.orEmpty()}" else ""))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 3_600_000L)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.Events.HAS_ALARM, true)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DescifraApp ?: return Result.success()
        if (!Reminders.canNotify(app)) return Result.success()
        val repo = app.repository
        val settings = repo.settings.value
        val today = LocalDate.now()
        Reminders.ensureChannel(app)
        val nm = app.getSystemService(NotificationManager::class.java)

        repo.letters.value.filter { it.remindersOn && !it.done }.forEach { letter ->
            letter.analysis.deadlines
                .filter { it.kind != DeadlineKind.INFO }
                .forEach { d ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(d.date)).toInt()
                    if (days in settings.reminderDaysBefore || days == 0) {
                        // Generic text: never put the sender, amount or personal data in a notification.
                        val text = when (days) {
                            0 -> "Hoy vence un plazo de una de tus cartas."
                            1 -> "Mañana vence un plazo de una de tus cartas."
                            else -> "Dentro de $days días vence un plazo de una de tus cartas."
                        }
                        val open = PendingIntent.getActivity(
                            app,
                            letter.id.hashCode(),
                            Intent(app, MainActivity::class.java).putExtra("letter", letter.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                        nm.notify(
                            (letter.id + d.date).hashCode(),
                            NotificationCompat.Builder(app, Reminders.CHANNEL)
                                .setSmallIcon(com.cazadoxs.descifra.R.drawable.ic_notification)
                                .setContentTitle("Descifra · plazo")
                                .setContentText(text)
                                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                                .setContentIntent(open)
                                .setAutoCancel(true)
                                .build(),
                        )
                    }
                }
        }
        return Result.success()
    }
}
