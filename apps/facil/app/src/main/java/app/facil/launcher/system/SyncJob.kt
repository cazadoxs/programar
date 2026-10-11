package app.facil.launcher.system

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import app.facil.launcher.core.Alerts
import app.facil.launcher.data.Store
import app.facil.launcher.remote.FamilySync
import app.facil.launcher.remote.Firebase
import app.facil.launcher.remote.SeniorSync
import java.util.concurrent.Executors

/**
 * Trabajo periódico (cada ~15 min, con red):
 *  - Móvil mayor: trae la configuración que haya cambiado la familia y sube
 *    batería/actividad si está permitido.
 *  - Móvil familiar (con suscripción): comprueba el estado y avisa.
 */
class SyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        exec.execute {
            try { run(applicationContext) } catch (e: Exception) { /* se reintenta en el próximo ciclo */ }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val ID_PERIODIC = 42
        private const val ID_SOON = 43
        private val exec = Executors.newSingleThreadExecutor()

        fun schedule(ctx: Context) {
            val js = ctx.svc<JobScheduler>()
            val store = Store.get(ctx)
            if (!Firebase.get(ctx).configured || (store.isSenior && !store.cloudEnabled) || (store.isFamily && store.devices.isEmpty()) || store.role.isEmpty()) {
                js.cancel(ID_PERIODIC); return
            }
            if (js.getPendingJob(ID_PERIODIC) != null) return
            js.schedule(JobInfo.Builder(ID_PERIODIC, ComponentName(ctx, SyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build())
        }

        /** Sincroniza en cuanto haya red (p. ej. tras marcar un recordatorio). */
        fun runSoon(ctx: Context) {
            if (!Firebase.get(ctx).configured || !Store.get(ctx).cloudEnabled) return
            ctx.svc<JobScheduler>().schedule(
                JobInfo.Builder(ID_SOON, ComponentName(ctx, SyncJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(60_000L)
                    .build())
        }

        fun run(ctx: Context) {
            val store = Store.get(ctx)
            if (!Firebase.get(ctx).configured) return
            when {
                store.isSenior && store.cloudEnabled -> {
                    val s = SeniorSync(ctx)
                    if (s.syncConfig()) ReminderReceiver.schedule(ctx)
                    s.pushStatus()
                    s.members()
                    Notifs.updateSharing(ctx)
                }
                store.isFamily && store.premium -> checkAlerts(ctx)
            }
        }

        fun checkAlerts(ctx: Context) {
            val store = Store.get(ctx)
            val fam = FamilySync(ctx)
            val now = System.currentTimeMillis()
            val current = mutableSetOf<String>()
            val already = store.notifiedAlerts
            for (d in store.devices) {
                val r = fam.fetch(d.id) ?: continue
                val cfg = r.config ?: continue
                for (a in Alerts.evaluate(d.name.ifBlank { r.name }, r.status, cfg, now)) {
                    val key = d.id + ":" + a.key
                    current += key
                    if (key !in already) Notifs.alert(ctx, key, d.name.ifBlank { r.name }, a.text)
                }
            }
            // Guardamos solo las vigentes: si una alerta se resuelve y vuelve, se avisa de nuevo.
            store.notifiedAlerts = current
        }
    }
}
