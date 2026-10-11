package app.facil.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.facil.launcher.data.Store
import app.facil.launcher.system.Notifs
import app.facil.launcher.system.ReminderReceiver
import app.facil.launcher.system.SyncJob

class FacilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifs.createChannels(this)
        ReminderReceiver.schedule(this)
        SyncJob.schedule(this)
        // Al desbloquear el móvil anotamos "última actividad". Solo la hora,
        // nada de qué se hace. Como somos el lanzador, el proceso suele estar vivo.
        val onUnlock = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                Store.get(c).lastActive = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(onUnlock, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(onUnlock, filter)
    }
}
