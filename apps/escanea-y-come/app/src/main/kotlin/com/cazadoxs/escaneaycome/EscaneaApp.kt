package com.cazadoxs.escaneaycome

import android.app.Application
import com.cazadoxs.escaneaycome.billing.BillingManager
import com.cazadoxs.escaneaycome.data.AppDatabase
import com.cazadoxs.escaneaycome.data.OffClient
import com.cazadoxs.escaneaycome.data.Repository
import com.cazadoxs.escaneaycome.data.SettingsStore

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val db = AppDatabase.build(app)
    val repo = Repository(db, OffClient())
    val billing = BillingManager(app, settings)
}

class EscaneaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.billing.start()
    }
}
