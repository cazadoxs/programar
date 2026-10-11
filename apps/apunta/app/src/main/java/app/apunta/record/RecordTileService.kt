package app.apunta.record

import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.apunta.MainActivity

/** Quick-settings tile. Starting the mic needs a visible activity, so the tile opens the app in record mode. */
class RecordTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (RecorderService.state.value.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (RecorderService.state.value.active) {
            RecorderService.send(this, RecorderService.ACTION_STOP)
            return
        }
        val i = Intent(this, MainActivity::class.java).setAction("app.apunta.action.RECORD")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}
