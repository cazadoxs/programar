package com.cazadoxs.descifra

import android.app.Application
import com.cazadoxs.descifra.billing.Billing
import com.cazadoxs.descifra.data.Engine
import com.cazadoxs.descifra.data.Repository
import com.cazadoxs.descifra.data.Vault
import com.cazadoxs.descifra.ml.OnDeviceAi
import com.cazadoxs.descifra.work.Reminders

class DescifraApp : Application() {
    lateinit var vault: Vault
        private set
    lateinit var repository: Repository
        private set
    lateinit var billing: Billing
        private set
    lateinit var nano: OnDeviceAi
        private set
    lateinit var engine: Engine
        private set

    override fun onCreate() {
        super.onCreate()
        vault = Vault(this)
        repository = Repository(this, vault)
        billing = Billing(this, vault)
        nano = OnDeviceAi()
        engine = Engine(repository, nano)
        // PDF exports are temporary: remove leftovers from earlier sessions.
        runCatching { java.io.File(cacheDir, "exports").deleteRecursively() }
        Reminders.ensureChannel(this)
        Reminders.schedule(this, repository.settings.value.reminderHour)
    }
}
