package io.github.cazadoxs.toma

import android.app.Application
import android.content.Context
import io.github.cazadoxs.toma.alarm.AlarmScheduler
import io.github.cazadoxs.toma.alarm.Notifications
import io.github.cazadoxs.toma.billing.BillingManager
import io.github.cazadoxs.toma.data.Repository
import io.github.cazadoxs.toma.data.TomaDatabase
import io.github.cazadoxs.toma.settings.SecretStore
import io.github.cazadoxs.toma.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TomaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.scope.launch { container.scheduler.rescheduleAll() }
    }
}

/** Hand-wired dependencies: small app, no DI framework needed. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = TomaDatabase.create(app)
    val repository = Repository(database)
    val settings = Settings(app)
    val secrets = SecretStore(app)
    val scheduler = AlarmScheduler(app, repository, settings)
    val billing = BillingManager(app, settings)
}

val Context.container: AppContainer get() = (applicationContext as TomaApp).container
