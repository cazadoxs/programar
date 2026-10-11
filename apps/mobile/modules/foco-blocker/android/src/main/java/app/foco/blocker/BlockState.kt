package app.foco.blocker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One app blocked during a time segment. surfaces contains "app" for the whole app. */
data class AppBlock(
  val appId: String,
  val packages: Set<String>,
  val surfaces: Set<String>,
  val reason: String,
  val strict: Boolean,
) {
  val wholeApp: Boolean get() = SURFACE_APP in surfaces

  companion object {
    const val SURFACE_APP = "app"
  }
}

data class Segment(val start: Long, val end: Long, val blocks: List<AppBlock>)

/** "X minutes a day" for an app, watched natively between two setState calls. */
data class DailyLimit(val appId: String, val packages: Set<String>, val minutesPerDay: Int, val reason: String)

/**
 * What to block and when, precomputed by the JS side (packages/core) for the
 * next days, so the engines work with the app closed and never have to
 * evaluate rules themselves.
 */
data class BlockState(
  val timeline: List<Segment>,
  val captureGesture: String,
  val watchedPackages: Set<String>,
  val patterns: JSONObject?,
  /** Show the 10 s pause (with "Abrir igualmente") instead of a hard block for non-strict whole-app blocks. */
  val wholeAppFriction: Boolean = false,
  val dailyLimits: List<DailyLimit> = emptyList(),
) {
  fun segmentAt(now: Long): Segment? = timeline.firstOrNull { now >= it.start && now < it.end }

  fun blocksAt(now: Long): List<AppBlock> = segmentAt(now)?.blocks ?: emptyList()

  /** Every block for [pkg] right now, merged into one (several rules can target the same app). */
  fun blockFor(pkg: String, now: Long): AppBlock? {
    val matches = blocksAt(now).filter { pkg in it.packages }
    if (matches.size <= 1) return matches.firstOrNull()
    val first = matches.first()
    return first.copy(
      packages = matches.flatMap { it.packages }.toSet(),
      surfaces = matches.flatMap { it.surfaces }.toSet(),
      reason = matches.map { it.reason }.filter { it.isNotBlank() }.distinct().joinToString(", "),
      strict = matches.any { it.strict },
    )
  }

  fun limitFor(pkg: String): DailyLimit? = dailyLimits.firstOrNull { pkg in it.packages }

  /** True while something can be blocked right now (a segment with blocks, or daily limits to watch). */
  fun needsWatching(now: Long): Boolean = blocksAt(now).isNotEmpty() || dailyLimits.isNotEmpty()

  /** True if anything can be blocked from [now] on, i.e. a background engine is worth running at all. */
  fun hasWorkFrom(now: Long): Boolean = dailyLimits.isNotEmpty() || timeline.any { it.end > now && it.blocks.isNotEmpty() }

  /** Next instant at which what is blocked may change (segment boundary), or null if none is known. */
  fun nextBoundaryAfter(now: Long): Long? =
    timeline.asSequence().flatMap { sequenceOf(it.start, it.end) }.filter { it > now }.minOrNull()

  /** End of the precomputed timeline (JS must refresh before it), or null if empty. */
  val timelineEnd: Long? get() = timeline.maxOfOrNull { it.end }

  /** Packages any engine needs to hear about. */
  fun allPackages(): Set<String> =
    watchedPackages + timeline.flatMap { s -> s.blocks.flatMap { it.packages } } + dailyLimits.flatMap { it.packages }

  companion object {
    private const val TAG = "FocoBlocker"
    private const val PREFS = "foco_blocker"
    private const val KEY = "state"

    @Volatile private var cached: BlockState? = null

    val EMPTY = BlockState(emptyList(), "volume_down_double", emptySet(), null)

    fun load(context: Context): BlockState {
      cached?.let { return it }
      synchronized(this) {
        cached?.let { return it }
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val state = raw?.let { s ->
          runCatching { parse(JSONObject(s)) }
            .onFailure { Log.w(TAG, "Stored state is unreadable, ignoring it", it) }
            .getOrNull()
        } ?: EMPTY
        cached = state
        return state
      }
    }

    /** Validates and stores the JSON sent by JS. Throws IllegalArgumentException if it is not a JSON object. */
    fun save(context: Context, json: String): BlockState {
      val state = try {
        parse(JSONObject(json))
      } catch (e: JSONException) {
        throw IllegalArgumentException("FocoBlocker.setState: invalid JSON (${e.message})", e)
      }
      synchronized(this) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
        cached = state
      }
      return state
    }

    /**
     * Lenient parser: a malformed segment or block is skipped instead of
     * dropping the whole state, so one bad entry never disables blocking.
     */
    internal fun parse(o: JSONObject): BlockState {
      val timeline = mutableListOf<Segment>()
      val segs = o.optJSONArray("timeline") ?: JSONArray()
      for (i in 0 until segs.length()) {
        val s = segs.optJSONObject(i) ?: continue
        val start = s.optLong("start", Long.MIN_VALUE)
        val end = s.optLong("end", Long.MIN_VALUE)
        if (start == Long.MIN_VALUE || end == Long.MIN_VALUE || end <= start) continue
        val blocks = mutableListOf<AppBlock>()
        val arr = s.optJSONArray("blocks") ?: JSONArray()
        for (j in 0 until arr.length()) {
          val b = arr.optJSONObject(j) ?: continue
          val appId = b.optString("appId", "").takeIf { it.isNotBlank() } ?: continue
          val packages = b.optJSONArray("packages").toStringSet()
          val surfaces = b.optJSONArray("surfaces").toStringSet()
          if (packages.isEmpty() || surfaces.isEmpty()) continue
          blocks += AppBlock(
            appId = appId,
            packages = packages,
            surfaces = surfaces,
            reason = b.optString("reason", ""),
            strict = b.optBoolean("strict", false),
          )
        }
        timeline += Segment(start, end, blocks)
      }
      timeline.sortBy { it.start }

      val limits = mutableListOf<DailyLimit>()
      // "limits" (current name) or "dailyLimits" (early drafts).
      val limitArr = o.optJSONArray("limits") ?: o.optJSONArray("dailyLimits") ?: JSONArray()
      for (i in 0 until limitArr.length()) {
        val l = limitArr.optJSONObject(i) ?: continue
        val appId = l.optString("appId", "").takeIf { it.isNotBlank() } ?: continue
        val packages = l.optJSONArray("packages").toStringSet()
        val minutes = l.optInt("minutesPerDay", -1)
        if (packages.isEmpty() || minutes < 0) continue
        limits += DailyLimit(appId, packages, minutes, l.optString("reason", "Límite diario ($minutes min)"))
      }

      return BlockState(
        timeline = timeline,
        captureGesture = o.optString("captureGesture", "volume_down_double"),
        watchedPackages = o.optJSONArray("watchedPackages").toStringSet(),
        patterns = o.optJSONObject("patterns"),
        wholeAppFriction = o.optBoolean("wholeAppFriction", false),
        dailyLimits = limits,
      )
    }

    private fun JSONArray?.toStringSet(): Set<String> {
      if (this == null) return emptySet()
      val out = LinkedHashSet<String>()
      for (i in 0 until length()) {
        val v = opt(i) as? String ?: continue
        if (v.isNotBlank()) out += v
      }
      return out
    }
  }
}

/** Per-day counters of blocked attempts, read by the stats screen. */
object BlockStats {
  private const val PREFS = "foco_blocker_stats"

  @Synchronized
  fun record(context: Context, appId: String, day: String) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val key = "$day|$appId"
    prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
  }

  /** {"2026-10-07": {"instagram": 3}} */
  fun all(context: Context): Map<String, Map<String, Int>> {
    val out = mutableMapOf<String, MutableMap<String, Int>>()
    for ((key, value) in context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) {
      val parts = key.split("|", limit = 2)
      if (parts.size != 2) continue
      val (day, app) = parts
      out.getOrPut(day) { mutableMapOf() }[app] = (value as? Int) ?: 0
    }
    return out
  }
}
