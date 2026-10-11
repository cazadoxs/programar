package com.cazadoxs.filtro400.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cazadoxs.filtro400.data.HistoryDb
import com.cazadoxs.filtro400.graph
import com.cazadoxs.filtro400.notify.Notifications
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Tarea diaria: borra historial antiguo, quita llamadas esperadas caducadas,
 * actualiza la lista de la comunidad (si eres Premium) y, una sola vez a los
 * pocos días de instalar, recuerda la Lista Robinson.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = applicationContext.graph
        val now = System.currentTimeMillis()
        val config = graph.settings.currentConfig()

        graph.history.prune(now - config.historyDays.coerceIn(1, 365) * HistoryDb.DAY)
        if (config.expected.any { it.untilEpochMs <= now }) {
            graph.settings.updateConfig { it.withoutExpired(now) }
        }

        val flags = graph.settings.flags.first()
        if (flags.premium && now - flags.lastCommunitySync > 20 * 60 * 60 * 1000L) {
            graph.community.sync()
        }

        val prefs = applicationContext.getSharedPreferences("maintenance", Context.MODE_PRIVATE)
        val firstRun = prefs.getLong("first_run", 0L).takeIf { it > 0 } ?: now.also {
            prefs.edit().putLong("first_run", it).apply()
        }
        if (!flags.robinsonDone && !prefs.getBoolean("robinson_reminded", false) && now - firstRun > 3 * HistoryDb.DAY) {
            Notifications.showRobinsonReminder(applicationContext)
            prefs.edit().putBoolean("robinson_reminded", true).apply()
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
