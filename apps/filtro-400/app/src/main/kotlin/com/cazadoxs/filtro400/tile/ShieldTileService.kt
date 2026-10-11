package com.cazadoxs.filtro400.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cazadoxs.filtro400.graph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Interruptor en los ajustes rápidos: enciende o apaga el filtro sin abrir la app. */
class ShieldTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val enabled = applicationContext.graph.config?.enabled
            ?: runBlocking { applicationContext.graph.settings.config.first().enabled }
        render(enabled)
    }

    override fun onClick() {
        super.onClick()
        val graph = applicationContext.graph
        val newValue = !(graph.config?.enabled ?: true)
        render(newValue)
        graph.scope.launch { graph.settings.updateConfig { it.copy(enabled = newValue) } }
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (enabled) "Activo" else "Apagado"
        tile.updateTile()
    }
}
