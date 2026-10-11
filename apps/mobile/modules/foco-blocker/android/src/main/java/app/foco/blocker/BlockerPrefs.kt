package app.foco.blocker

import android.content.Context

/** Native-only switches set from JS (they survive reboots, unlike the in-memory caches). */
object BlockerSettings {
  private const val PREFS = "foco_blocker_settings"
  private const val KEY_FALLBACK = "fallbackEnabled"
  private const val KEY_ASKED_NOTIFICATIONS = "askedNotifications"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** Usage-access engine allowed to run when accessibility is not available. On by default. */
  fun fallbackEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_FALLBACK, true)

  fun setFallbackEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_FALLBACK, enabled).apply()
  }

  fun askedNotifications(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED_NOTIFICATIONS, false)

  fun setAskedNotifications(context: Context) {
    prefs(context).edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
  }
}

/**
 * "Abrir igualmente" passes from the friction screen: appId → allowed until
 * (wall-clock ms). Persisted so a process restart does not re-prompt.
 */
object Allowances {
  const val DURATION_MS = 5 * 60_000L
  private const val PREFS = "foco_blocker_allowances"

  @Synchronized
  fun grant(context: Context, appId: String, now: Long = System.currentTimeMillis()) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val edit = prefs.edit()
    // Drop expired entries so the file never grows.
    for ((key, value) in prefs.all) if ((value as? Long ?: 0L) <= now) edit.remove(key)
    edit.putLong(appId, now + DURATION_MS).apply()
  }

  @Synchronized
  fun isAllowed(context: Context, appId: String, now: Long = System.currentTimeMillis()): Boolean =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(appId, 0L) > now
}
