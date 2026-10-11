package app.foco.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the fallback engine after a reboot or an app update (the
 * accessibility service is rebound by the system on its own).
 *
 * Declared with exported="false": both actions are protected broadcasts sent
 * by the system, which can always deliver to non-exported receivers, so no
 * other app can reach this receiver at all.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    when (intent?.action) {
      Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
        FallbackBlockerService.sync(context)
        RenewalReminder.schedule(context) // alarms do not survive a reboot
      }
    }
  }
}
