package app.foco.blocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One app blocked during a time segment. surfaces contains "app" for the whole app. */
data class AppBlock(
  val appId: String,
  val packages: Set<String>,
  val surfaces: Set<String>,
  val reason: String,
  val strict: Boolean,
)

data class Segment(val start: Long, val end: Long, val blocks: List<AppBlock>)

/**
 * What to block and when, precomputed by the JS side (packages/core) for the
 * next hours, so the service works with the app closed and never has to
 * evaluate rules itself.
 */
data class BlockState(
  val timeline: List<Segment>,
  val captureGesture: String,
  val watchedPackages: Set<String>,
  val patterns: JSONObject?,
) {
  fun blocksAt(now: Long): List<AppBlock> = timeline.firstOrNull { now >= it.start && now < it.end }?.blocks ?: emptyList()

  fun blockFor(pkg: String, now: Long): AppBlock? = blocksAt(now).firstOrNull { pkg in it.packages }

  companion object {
    private const val PREFS = "foco_blocker"
    private const val KEY = "state"

    @Volatile private var cached: BlockState? = null

    val EMPTY = BlockState(emptyList(), "volume_down_double", emptySet(), null)

    fun load(context: Context): BlockState {
      cached?.let { return it }
      val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
      val state = raw?.let { runCatching { parse(JSONObject(it)) }.getOrNull() } ?: EMPTY
      cached = state
      return state
    }

    fun save(context: Context, json: String) {
      val state = parse(JSONObject(json))
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
      cached = state
    }

    private fun parse(o: JSONObject): BlockState {
      val timeline = mutableListOf<Segment>()
      val segs = o.optJSONArray("timeline") ?: JSONArray()
      for (i in 0 until segs.length()) {
        val s = segs.getJSONObject(i)
        val blocks = mutableListOf<AppBlock>()
        val arr = s.optJSONArray("blocks") ?: JSONArray()
        for (j in 0 until arr.length()) {
          val b = arr.getJSONObject(j)
          blocks += AppBlock(
            appId = b.getString("appId"),
            packages = b.getJSONArray("packages").toStringSet(),
            surfaces = b.getJSONArray("surfaces").toStringSet(),
            reason = b.optString("reason", ""),
            strict = b.optBoolean("strict", false),
          )
        }
        timeline += Segment(s.getLong("start"), s.getLong("end"), blocks)
      }
      return BlockState(
        timeline = timeline,
        captureGesture = o.optString("captureGesture", "volume_down_double"),
        watchedPackages = (o.optJSONArray("watchedPackages") ?: JSONArray()).toStringSet(),
        patterns = o.optJSONObject("patterns"),
      )
    }

    private fun JSONArray.toStringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()
  }
}

/** Per-day counters of blocked attempts, read by the stats screen. */
object BlockStats {
  private const val PREFS = "foco_blocker_stats"

  fun record(context: Context, appId: String, day: String) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val key = "$day|$appId"
    prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
  }

  /** {"2026-10-07": {"instagram": 3}} */
  fun all(context: Context): Map<String, Map<String, Int>> {
    val out = mutableMapOf<String, MutableMap<String, Int>>()
    for ((key, value) in context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) {
      val parts = key.split("|", limit = 2)
      if (parts.size != 2) continue
      val (day, app) = parts
      out.getOrPut(day) { mutableMapOf() }[app] = (value as? Int) ?: 0
    }
    return out
  }
}
