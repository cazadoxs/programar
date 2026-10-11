package vlc.busmetro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import vlc.busmetro.billing.Billing
import vlc.busmetro.data.*
import vlc.busmetro.widget.WidgetUpdater
import vlc.busmetro.work.Scheduler

/** Hand-wired singletons: small enough that a DI framework would only add weight. */
class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http by lazy { Http() }
    val transitDb by lazy { TransitDb.create(this) }
    val userDb by lazy { UserDb.create(this) }
    val prefs by lazy { Prefs(this) }
    val repo by lazy { TransitRepository(http, transitDb, userDb) }
    val feeds by lazy { FeedRepository(this, http, transitDb) { repo.invalidate(); WidgetUpdater.requestAll(this) } }
    val billing by lazy { Billing(this, prefs, scope) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
        Scheduler.schedule(this)
        billing.start()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = getString(R.string.channel_alerts_desc) })
    }

    companion object {
        const val CH_ALERTS = "alerts"
        lateinit var instance: App
            private set
    }
}

val Context.app: App get() = applicationContext as App
