package app.apunta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.apunta.billing.BillingManager
import app.apunta.billing.Quota
import app.apunta.data.AppDatabase
import app.apunta.models.ModelManager
import app.apunta.settings.SecretStore
import app.apunta.settings.Settings
import app.apunta.transcribe.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled service locator: the app is small enough that a DI framework costs more than it saves. */
class ApuntaApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { AppDatabase.build(this) }
    val settings by lazy { Settings(this) }
    val secrets by lazy { SecretStore(this) }
    val quota by lazy { Quota(this) }
    val billing by lazy { BillingManager(this) }
    val models by lazy { ModelManager(this, appScope) }
    val transcriber by lazy { Transcriber(this, db, models, settings, quota) { billing.isPro.value } }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RECORD, getString(R.string.ch_record), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WORK, getString(R.string.ch_work), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CH_RECORD = "record"
        const val CH_WORK = "work"
        const val CH_DONE = "done"
    }
}

val android.content.Context.app: ApuntaApp get() = applicationContext as ApuntaApp
