package app.foco.blocker

import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.Locale

/**
 * Heuristic detector for the part of an app currently on screen (reels,
 * stories, explore, messages, feed), ported and extended from FocusGuard's
 * ShortsDetector. Android offers no API for this, so it reads view-id names
 * and selected tabs from the accessibility tree. The markers change when the
 * apps update; `overrides` (downloaded by the app) can replace them without
 * shipping a new version.
 *
 * The walk is bounded (MAX_NODES, MAX_CHILDREN) so a check costs a few
 * hundred field reads at most.
 */
class SurfaceDetector(overrides: JSONObject?) {

  /** appId → surface → markers. "ids" match view-id names, "tabs" match a selected tab's text/description. */
  private val markers: Map<String, Map<String, Markers>> = DEFAULTS.mapValues { (appId, surfaces) ->
    surfaces.mapValues { (surface, m) -> overrides?.optJSONObject(appId)?.optJSONObject(surface)?.let(Markers::fromJson) ?: m }
  }

  data class Markers(val ids: List<String> = emptyList(), val tabs: List<String> = emptyList()) {
    companion object {
      fun fromJson(o: JSONObject) = Markers(
        ids = o.optJSONArray("ids")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        tabs = o.optJSONArray("tabs")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
      )
    }
  }

  fun detect(appId: String, root: AccessibilityNodeInfo, wanted: Set<String>): Set<String> {
    val appMarkers = markers[appId] ?: return emptySet()
    val relevant = appMarkers.filterKeys { it in wanted }
    if (relevant.isEmpty()) return emptySet()

    val found = mutableSetOf<String>()
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    var visited = 0
    while (queue.isNotEmpty() && visited < MAX_NODES && found.size < relevant.size) {
      val node = queue.poll() ?: continue
      visited++
      val id = node.viewIdResourceName?.lowercase(Locale.US)
      val selected = node.isSelected || node.isChecked
      val label = if (selected) "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase(Locale.US) else null
      for ((surface, m) in relevant) {
        if (surface in found) continue
        if (id != null && m.ids.any { id.contains(it) }) found += surface
        else if (label != null && m.tabs.any { label.contains(it) }) found += surface
      }
      for (i in 0 until minOf(node.childCount, MAX_CHILDREN)) node.getChild(i)?.let(queue::add)
    }
    // TikTok: a selected inbox/profile tab means the user is not in the feed.
    if (appId == "tiktok" && "feed" in found) {
      val safe = Markers(tabs = listOf("inbox", "bandeja", "profile", "perfil", "friends", "amigos"))
      if (hasSelectedTab(root, safe)) found -= "feed"
    }
    return found
  }

  private fun hasSelectedTab(root: AccessibilityNodeInfo, m: Markers): Boolean {
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    var visited = 0
    while (queue.isNotEmpty() && visited < MAX_NODES) {
      val node = queue.poll() ?: continue
      visited++
      if (node.isSelected || node.isChecked) {
        val label = "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase(Locale.US)
        if (m.tabs.any { label.contains(it) }) return true
      }
      for (i in 0 until minOf(node.childCount, MAX_CHILDREN)) node.getChild(i)?.let(queue::add)
    }
    return false
  }

  companion object {
    private const val MAX_NODES = 500
    private const val MAX_CHILDREN = 12

    // Instagram calls Reels "clips" internally and Stories "reel".
    val DEFAULTS: Map<String, Map<String, Markers>> = mapOf(
      "instagram" to mapOf(
        "reels" to Markers(ids = listOf("clips_viewer", "clips_tab", "clips_swipe_refresh", "clips_grid_item", "clips_video"), tabs = listOf("reels")),
        "stories" to Markers(ids = listOf("reel_viewer", "reel_viewer_media", "story_viewer", "reels_tray_container")),
        "explore" to Markers(ids = listOf("explore_", "search_tab"), tabs = listOf("search and explore", "buscar y explorar", "explore", "explorar")),
        "messages" to Markers(ids = listOf("direct_inbox", "direct_thread", "row_thread_composer"), tabs = listOf("direct", "messenger", "mensajes", "messages")),
        "feed" to Markers(ids = listOf("feed_tab", "main_feed"), tabs = listOf("home", "inicio")),
      ),
      "youtube" to mapOf(
        "reels" to Markers(ids = listOf("reel_player", "reel_watch", "reel_recycler", "shorts_container", "shorts_player", "reels_player_navigation", "shorts_shelf"), tabs = listOf("shorts")),
        "feed" to Markers(tabs = listOf("home", "inicio")),
        "explore" to Markers(ids = listOf("search_edit_text"), tabs = listOf("explore", "explorar")),
      ),
      "tiktok" to mapOf(
        "feed" to Markers(ids = listOf("vv_video_container", "feed_recycler_view", "tux_tab_feed", "video_feed_container", "feed_view_pager", "video_view_wrapper"), tabs = listOf("home", "inicio", "for you", "para ti")),
        "messages" to Markers(tabs = listOf("inbox", "bandeja de entrada", "mensajes")),
        "explore" to Markers(tabs = listOf("discover", "descubrir", "search", "buscar")),
      ),
      "snapchat" to mapOf(
        "stories" to Markers(ids = listOf("story_viewer", "stories_page", "discover_feed"), tabs = listOf("stories", "historias")),
        "reels" to Markers(ids = listOf("spotlight"), tabs = listOf("spotlight")),
        "messages" to Markers(ids = listOf("chat_"), tabs = listOf("chat")),
      ),
      "facebook" to mapOf(
        "reels" to Markers(ids = listOf("reels_viewer", "video_home", "fb_shorts"), tabs = listOf("reels", "video", "watch")),
        "stories" to Markers(ids = listOf("stories_viewer", "story_viewer")),
        "feed" to Markers(tabs = listOf("home", "inicio", "news feed")),
        "messages" to Markers(tabs = listOf("messenger", "chats")),
      ),
      "x" to mapOf(
        "feed" to Markers(ids = listOf("timeline"), tabs = listOf("home", "inicio", "for you", "para ti")),
        "explore" to Markers(tabs = listOf("search and explore", "buscar", "explore")),
        "messages" to Markers(ids = listOf("dm_"), tabs = listOf("messages", "mensajes")),
      ),
      "reddit" to mapOf("feed" to Markers(tabs = listOf("home", "inicio", "popular"))),
      "whatsapp" to mapOf("stories" to Markers(ids = listOf("status_playback", "updates_"), tabs = listOf("updates", "novedades"))),
    )
  }
}
