package app.foco.blocker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

/** Usage-access helpers shared by the module (stats) and the fallback engine (foreground app). */
object UsageAccess {
  // Raw values so they work from minSdk 24 (ACTIVITY_* names are API 29; the values are the same).
  internal const val ACTIVITY_RESUMED = 1 // == MOVE_TO_FOREGROUND
  internal const val ACTIVITY_PAUSED = 2 // == MOVE_TO_BACKGROUND
  internal const val SCREEN_NON_INTERACTIVE = 16
  internal const val KEYGUARD_SHOWN = 17
  internal const val ACTIVITY_STOPPED = 23
  internal const val DEVICE_SHUTDOWN = 26

  fun hasAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
      @Suppress("DEPRECATION")
      ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
  }

  fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
  }.timeInMillis

  /**
   * Foreground minutes since midnight per package, computed from activity
   * resume/pause events. More exact than queryAndAggregateUsageStats, whose
   * daily buckets do not start at local midnight and miss the running session.
   * [only] restricts the result (and the work) to some packages.
   */
  fun minutesToday(context: Context, only: Set<String>? = null, now: Long = System.currentTimeMillis()): Map<String, Double> {
    if (!hasAccess(context)) return emptyMap()
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
    val dayStart = startOfToday()
    val events = runCatching { usm.queryEvents(dayStart, now) }.getOrNull() ?: return emptyMap()
    val totals = HashMap<String, Long>()
    val openSince = HashMap<String, Long>()
    val seen = HashSet<String>()
    val e = UsageEvents.Event()
    while (events.hasNextEvent()) {
      if (!events.getNextEvent(e)) break
      val type = e.eventType
      if (type == SCREEN_NON_INTERACTIVE || type == DEVICE_SHUTDOWN) {
        for ((p, since) in openSince) totals[p] = (totals[p] ?: 0L) + (e.timeStamp - since)
        openSince.clear()
        continue
      }
      val pkg = e.packageName ?: continue
      if (only != null && pkg !in only) continue
      when (type) {
        ACTIVITY_RESUMED -> {
          seen += pkg
          if (pkg !in openSince) openSince[pkg] = e.timeStamp
        }
        ACTIVITY_PAUSED, ACTIVITY_STOPPED -> {
          val since = openSince.remove(pkg) ?: if (pkg !in seen) dayStart else null // was open at midnight
          seen += pkg
          if (since != null) totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - since)
        }
      }
    }
    for ((p, since) in openSince) totals[p] = (totals[p] ?: 0L) + (now - since)
    return totals.filterValues { it > 0 }.mapValues { it.value / 60_000.0 }
  }
}

/**
 * Tracks the app in the foreground by reading UsageStatsManager events
 * incrementally (only the events since the previous poll). Not thread-safe:
 * owned by the fallback service's worker thread.
 */
class ForegroundAppDetector(context: Context) {
  private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
  private var queriedUntil = 0L
  private var lastEventAt = 0L

  /** Last package seen resumed, or null when unknown / the screen is locked. */
  var current: String? = null
    private set

  fun reset() {
    queriedUntil = 0L
    lastEventAt = 0L
    current = null
  }

  fun poll(now: Long = System.currentTimeMillis()): String? {
    val usm = usm ?: return null
    // Small overlap: events can be committed slightly after their timestamp.
    val begin = if (queriedUntil == 0L) now - INITIAL_LOOKBACK_MS else queriedUntil - OVERLAP_MS
    val events = runCatching { usm.queryEvents(begin, now) }.getOrNull() ?: return current
    queriedUntil = now
    val e = UsageEvents.Event()
    while (events.hasNextEvent()) {
      if (!events.getNextEvent(e)) break
      if (e.timeStamp < lastEventAt) continue
      when (e.eventType) {
        UsageAccess.ACTIVITY_RESUMED -> {
          current = e.packageName
          lastEventAt = e.timeStamp
        }
        UsageAccess.ACTIVITY_PAUSED, UsageAccess.ACTIVITY_STOPPED -> if (e.packageName == current) {
          current = null
          lastEventAt = e.timeStamp
        }
        UsageAccess.KEYGUARD_SHOWN, UsageAccess.SCREEN_NON_INTERACTIVE -> {
          current = null
          lastEventAt = e.timeStamp
        }
      }
    }
    return current
  }

  private companion object {
    const val INITIAL_LOOKBACK_MS = 60 * 60_000L
    const val OVERLAP_MS = 2_000L
  }
}
