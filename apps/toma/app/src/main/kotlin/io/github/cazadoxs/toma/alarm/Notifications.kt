package io.github.cazadoxs.toma.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.data.DueDose
import io.github.cazadoxs.toma.data.MedicationEntity
import io.github.cazadoxs.toma.data.ProfileEntity
import io.github.cazadoxs.toma.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Notifications {
    const val CHANNEL_ALARM = "dose_alarm"
    const val CHANNEL_REFILL = "refill"
    const val CHANNEL_INFO = "info"
    const val ID_ALARM = 1001
    private const val ID_REFILL_BASE = 20_000
    const val ID_INFO = 1003

    private val vibration = longArrayOf(0, 800, 400, 800, 400, 800, 1200)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = vibration
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        val refill = NotificationChannel(
            CHANNEL_REFILL,
            context.getString(R.string.channel_refill),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_refill_desc) }
        val info = NotificationChannel(
            CHANNEL_INFO,
            context.getString(R.string.channel_info),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannels(listOf(alarm, refill, info))
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun timeText(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at.atZone(zone))

    /**
     * Shows (or re-shows) the ringing alarm for the doses that are due. The sound repeats until
     * the user confirms, snoozes or opens it: [NotificationCompat.FLAG_INSISTENT].
     */
    @SuppressLint("MissingPermission") // canPost() checks it
    fun ring(context: Context, due: List<DueDose>, profiles: List<ProfileEntity>) {
        if (due.isEmpty() || !canPost(context)) return
        val names = profiles.associate { it.id to it.name }
        val multiProfile = due.map { it.med.profileId }.distinct().size > 1 || profiles.size > 1
        val lines = due.map { d -> lineFor(context, d.med, names[d.med.profileId].takeIf { multiProfile }) }
        val title = if (!multiProfile) {
            context.getString(R.string.alarm_title_self)
        } else {
            val who = due.map { names[it.med.profileId] ?: "" }.distinct().joinToString(", ")
            context.getString(R.string.alarm_title_for, who)
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val take = DoseActionReceiver.pending(context, DoseActionReceiver.ACTION_TAKE_ALL)
        val snooze = DoseActionReceiver.pending(context, DoseActionReceiver.ACTION_SNOOZE)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_toma)
            .setContentTitle(context.getString(R.string.alarm_title_private))
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_toma)
            .setColor(ContextCompat.getColor(context, R.color.toma_coral))
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(7).forEach(s::addLine) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setDeleteIntent(snooze)
            .addAction(0, context.getString(R.string.action_taken_all), take)
            .addAction(0, context.getString(R.string.action_snooze), snooze)
        if (canUseFullScreen(context)) builder.setFullScreenIntent(open, true)
        val notification = builder.build().apply { flags = flags or android.app.Notification.FLAG_INSISTENT }
        NotificationManagerCompat.from(context).notify(ID_ALARM, notification)
    }

    fun dismissAlarm(context: Context) = NotificationManagerCompat.from(context).cancel(ID_ALARM)

    fun lineFor(context: Context, med: MedicationEntity, profileName: String?): String {
        val base = if (med.doseLabel.isBlank()) med.name else context.getString(R.string.med_with_dose, med.name, med.doseLabel)
        return if (profileName != null) "$profileName: $base" else base
    }

    @SuppressLint("MissingPermission")
    fun refill(context: Context, med: MedicationEntity, daysLeft: Int?) {
        if (!canPost(context)) return
        val text = when {
            med.stock == null -> return
            med.stock <= 0.0 -> context.getString(R.string.refill_empty)
            daysLeft != null -> context.resources.getQuantityString(R.plurals.refill_days_left, daysLeft, daysLeft)
            else -> context.getString(R.string.refill_low)
        }
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REFILL)
            .setSmallIcon(R.drawable.ic_stat_toma)
            .setContentTitle(context.getString(R.string.refill_title, med.name))
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_REFILL_BASE + (med.id % 10_000).toInt(), n)
    }

    @SuppressLint("MissingPermission")
    fun info(context: Context, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_toma)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_INFO, n)
    }
}
