package com.cazadoxs.escanerlimpio

import android.app.Application
import com.cazadoxs.escanerlimpio.billing.BillingManager
import com.cazadoxs.escanerlimpio.data.DocumentService
import com.cazadoxs.escanerlimpio.data.DocumentStore
import com.cazadoxs.escanerlimpio.data.Settings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class EscanerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var store: DocumentStore
        private set
    lateinit var settings: Settings
        private set
    lateinit var service: DocumentService
        private set
    lateinit var billing: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        store = DocumentStore(this)
        settings = Settings(this)
        service = DocumentService(this, store, settings, appScope)
        billing = BillingManager(this)
        appScope.launch { store.load() }
        billing.start()
        // Shared copies are only needed while the share sheet is open.
        appScope.launch(Dispatchers.IO) { File(cacheDir, "compartir").deleteRecursively() }
    }
}
