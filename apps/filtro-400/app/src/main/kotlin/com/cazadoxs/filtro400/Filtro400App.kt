package com.cazadoxs.filtro400

import android.app.Application
import com.cazadoxs.filtro400.billing.BillingManager
import com.cazadoxs.filtro400.core.FilterConfig
import com.cazadoxs.filtro400.data.CommunityRepository
import com.cazadoxs.filtro400.data.HistoryDb
import com.cazadoxs.filtro400.data.SettingsStore
import com.cazadoxs.filtro400.notify.Notifications
import com.cazadoxs.filtro400.work.MaintenanceWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Filtro400App : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.start()
        Notifications.createChannels(this)
        MaintenanceWorker.schedule(this)
    }
}

/** Dependencias de la app. Sin frameworks de inyección: son cuatro piezas. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val history = HistoryDb(app)
    val community = CommunityRepository(app, settings)
    val billing by lazy { BillingManager(app, settings, scope) }

    /**
     * Copia en memoria de la configuración para que el servicio de filtrado
     * responda en milisegundos (Android solo le da 5 segundos).
     */
    @Volatile var config: FilterConfig? = null
        private set
    @Volatile var premium: Boolean = false
        private set

    fun start() {
        scope.launch { settings.config.collect { config = it } }
        scope.launch { settings.flags.collect { premium = it.premium } }
        scope.launch { community.loadCached() }
    }
}

val android.content.Context.graph: AppGraph
    get() = (applicationContext as Filtro400App).graph
