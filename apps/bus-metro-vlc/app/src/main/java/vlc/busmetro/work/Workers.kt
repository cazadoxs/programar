package vlc.busmetro.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import vlc.busmetro.App
import vlc.busmetro.R
import vlc.busmetro.app
import vlc.busmetro.ui.MainActivity
import vlc.busmetro.widget.WidgetUpdater
import java.util.Locale
import java.util.concurrent.TimeUnit

object Scheduler {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork("alerts", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertsWorker>(30, TimeUnit.MINUTES).setConstraints(net).build())
        // Timetables change every few weeks; refresh weekly on unmetered networks while charging.
        wm.enqueueUniquePeriodicWork("feeds", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FeedsWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build()).build())
        wm.enqueueUniquePeriodicWork("widgets", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetsWorker>(15, TimeUnit.MINUTES).setConstraints(net).build())
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("feeds-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FeedsWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}

class FeedsWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val results = app.feeds.syncAll()
        if (results.any { it.ok }) app.prefs.setLastSync(System.currentTimeMillis())
        return if (results.all { it.ok }) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}

class WidgetsWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAllNow(applicationContext)
        return Result.success()
    }
}

class AlertsWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val lang = Locale.getDefault().language.let { if (it == "ca" || it == "en") it else "es" }
        val fresh = runCatching { app.repo.refreshAlerts(lang) }.getOrElse { return Result.retry() }
        val settings = app.prefs.current()
        if (!settings.notifyAlerts || fresh.isEmpty()) return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()

        // Only notify about alerts touching the person's favourites (or their lines).
        val favs = app.repo.favoriteList()
        val favKeys = favs.map { it.key }.toSet()
        val favLines = favs.flatMap { f ->
            val s = app.repo.stop(f.key) ?: return@flatMap emptyList()
            val net = s.network
            s.lines.split(',').filter { it.isNotEmpty() }.map { "$net:line:$it" }
        }.toSet()
        val relevant = fresh.filter { a ->
            val lines = a.routeKeys.split(',').filter { it.isNotEmpty() }
            val stops = a.stopKeys.split(',').filter { it.isNotEmpty() }
            lines.any { it in favLines } || stops.any { it in favKeys }
        }
        val nm = NotificationManagerCompat.from(applicationContext)
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java).setAction(MainActivity.ACTION_ALERTS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        relevant.take(5).forEach { a ->
            val n = NotificationCompat.Builder(applicationContext, App.CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(a.title)
                .setContentText(a.body.take(120))
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.body.take(600)))
                .setContentIntent(open).setAutoCancel(true).build()
            runCatching { nm.notify(a.id.hashCode(), n) }
        }
        app.repo.markNotified(fresh.map { it.id })
        return Result.success()
    }
}
