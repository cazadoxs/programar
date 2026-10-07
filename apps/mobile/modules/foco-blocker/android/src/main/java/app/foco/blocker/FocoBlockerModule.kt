package app.foco.blocker

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.Calendar

class FocoBlockerModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("React context not available")

  override fun definition() = ModuleDefinition {
    Name("FocoBlocker")

    Function("getStatus") {
      mapOf(
        "platform" to "android",
        "accessibilityEnabled" to isAccessibilityEnabled(),
        "serviceRunning" to (FocoAccessibilityService.instance != null),
        "overlayPermission" to Settings.canDrawOverlays(context),
        "usageAccess" to hasUsageAccess(),
      )
    }

    /** JSON produced by buildNativeState() in JS. */
    Function("setState") { json: String ->
      BlockState.save(context, json)
      FocoAccessibilityService.instance?.applyPackageFilter()
    }

    Function("getBlockStats") { BlockStats.all(context) }

    /** Minutes in the foreground today per package. Needs usage access. */
    Function("getUsageToday") {
      if (!hasUsageAccess()) return@Function emptyMap<String, Double>()
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
      val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
      }.timeInMillis
      usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        .mapValues { it.value.totalTimeInForeground / 60000.0 }
        .filterValues { it > 0 }
    }

    Function("openAccessibilitySettings") { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    Function("openOverlaySettings") {
      open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
    }
    Function("openUsageAccessSettings") { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
  }

  private fun open(intent: Intent) {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  private fun isAccessibilityEnabled(): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(context, FocoAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
  }

  private fun hasUsageAccess(): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
      @Suppress("DEPRECATION")
      ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
  }
}
