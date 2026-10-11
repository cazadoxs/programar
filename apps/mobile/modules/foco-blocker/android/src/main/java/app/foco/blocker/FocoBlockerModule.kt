package app.foco.blocker

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class FocoBlockerModule : Module() {
  private val mainHandler = Handler(Looper.getMainLooper())

  private val context: Context
    get() = appContext.reactContext?.applicationContext ?: throw IllegalStateException("React context not available")

  override fun definition() = ModuleDefinition {
    Name("FocoBlocker")

    Function("getStatus") {
      val ctx = context
      val accessibilityRunning = FocoAccessibilityService.instance != null
      val usageAccess = UsageAccess.hasAccess(ctx)
      val fallbackEnabled = BlockerSettings.fallbackEnabled(ctx)
      val engine = when {
        accessibilityRunning -> Engine.ACCESSIBILITY.id
        fallbackEnabled && usageAccess -> Engine.USAGE.id
        else -> "none"
      }
      mapOf(
        "platform" to "android",
        "accessibilityEnabled" to isAccessibilityEnabled(ctx),
        "serviceRunning" to accessibilityRunning,
        "overlayPermission" to Settings.canDrawOverlays(ctx),
        "usageAccess" to usageAccess,
        "engine" to engine,
        "notificationsPermission" to notificationsAllowed(ctx),
        "fallbackEnabled" to fallbackEnabled,
        "fallbackRunning" to FallbackBlockerService.isRunning,
      )
    }

    /** JSON produced by buildNativeState() in JS. */
    Function("setState") { json: String ->
      val ctx = context
      BlockState.save(ctx, json)
      // Accessibility callbacks run on the main thread; update its package filter there too.
      FocoAccessibilityService.instance?.let { service -> mainHandler.post { service.onStateChanged() } }
      FallbackBlockerService.sync(ctx)
      RenewalReminder.schedule(ctx)
    }

    Function("setFallbackEnabled") { enabled: Boolean ->
      val ctx = context
      BlockerSettings.setFallbackEnabled(ctx, enabled)
      FallbackBlockerService.sync(ctx)
    }

    Function("getBlockStats") { BlockStats.all(context) }

    /** Minutes in the foreground today per package. Needs usage access. */
    Function("getUsageToday") { UsageAccess.minutesToday(context) }

    Function("openAccessibilitySettings") { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    Function("openOverlaySettings") {
      open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
    }
    Function("openUsageAccessSettings") { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

    Function("openNotificationSettings") { openNotificationSettings() }

    /**
     * Shows the Android 13+ notification prompt (needed for the fallback's
     * ongoing notification). If Android will not show it any more (denied
     * twice), opens the app's notification settings instead. Returns whether
     * notifications are allowed right now; call getStatus() again after the
     * user answers.
     */
    Function("requestNotificationPermission") {
      val ctx = context
      if (notificationsAllowed(ctx)) return@Function true
      val activity = appContext.currentActivity
      if (Build.VERSION.SDK_INT >= 33 && activity != null) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val canPrompt = !BlockerSettings.askedNotifications(ctx) || activity.shouldShowRequestPermissionRationale(permission)
        if (canPrompt) {
          BlockerSettings.setAskedNotifications(ctx)
          activity.runOnUiThread { activity.requestPermissions(arrayOf(permission), NOTIFICATION_REQUEST_CODE) }
          return@Function false
        }
      }
      openNotificationSettings()
      false
    }
  }

  private fun open(intent: Intent) {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  private fun openNotificationSettings() {
    val ctx = context
    val intent = if (Build.VERSION.SDK_INT >= 26) {
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    }
    open(intent)
  }

  private fun notificationsAllowed(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
      ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      return false
    }
    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
    return manager.areNotificationsEnabled()
  }

  private fun isAccessibilityEnabled(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(ctx, FocoAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
  }

  private companion object {
    const val NOTIFICATION_REQUEST_CODE = 41071
  }
}

