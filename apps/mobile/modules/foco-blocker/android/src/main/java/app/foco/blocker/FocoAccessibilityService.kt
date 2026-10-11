package app.foco.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * Event-driven blocker (no polling, no wake locks), evolved from FocusGuard's
 * BlockerAccessibilityService. It is the primary engine: while it runs, the
 * usage-access fallback (FallbackBlockerService) stays stopped.
 *  - whole-app blocks show BlockActivity on top of the blocked app
 *    (or the friction pause, if the user chose it for non-strict blocks);
 *  - surface blocks (reels, stories…) back the user out of that screen;
 *  - a double press of the chosen volume key opens Foco's quick capture.
 *
 * All callbacks run on the main thread, so the maps below need no locking.
 */
class FocoAccessibilityService : AccessibilityService() {

  private val handler = Handler(Looper.getMainLooper())
  private var pendingCheck: Runnable? = null
  private var detector = SurfaceDetector(null)
  private var detectorPatterns: JSONObject? = null
  private val lastBackAt = mutableMapOf<String, Long>()
  private val lastShownAt = mutableMapOf<String, Long>()
  private val attemptStreak = mutableMapOf<String, Int>()
  private var lastVolumePress = 0L

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    onStateChanged()
    // Primary engine is up: the fallback must not double-fire.
    FallbackBlockerService.sync(this)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    detach()
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    detach()
    super.onDestroy()
  }

  private fun detach() {
    if (instance !== this) return
    instance = null
    pendingCheck?.let(handler::removeCallbacks)
    pendingCheck = null
    handler.removeCallbacks(boundaryCheck)
    // Accessibility turned off (or killed by Advanced Protection): hand over to the fallback.
    FallbackBlockerService.sync(this)
  }

  override fun onInterrupt() {}

  /** New state from JS (main thread). */
  fun onStateChanged() {
    applyPackageFilter()
    scheduleBoundaryCheck()
  }

  /**
   * Only receive events from apps that can be blocked (the list comes from the
   * JS catalog). Never widened to "every app": with no list, the catalogue
   * default from foco_accessibility_config.xml stays in place.
   */
  private fun applyPackageFilter() {
    val packages = BlockState.load(this).allPackages()
    if (packages.isEmpty()) return
    val info = serviceInfo ?: return
    info.packageNames = packages.toTypedArray()
    runCatching { serviceInfo = info }
  }

  /**
   * When a segment starts while the user is already inside a blocked app no
   * event may arrive (e.g. a paused video), so re-check the foreground window
   * at every segment boundary. A Handler is enough: the screen is off when the
   * device sleeps, and turning it on produces window events anyway.
   */
  private fun scheduleBoundaryCheck() {
    handler.removeCallbacks(boundaryCheck)
    val now = System.currentTimeMillis()
    val next = BlockState.load(this).nextBoundaryAfter(now) ?: return
    handler.postDelayed(boundaryCheck, (next - now + 500L).coerceAtMost(MAX_BOUNDARY_WAIT_MS))
  }

  private val boundaryCheck = Runnable {
    val root = rootInActiveWindow
    val pkg = try { root?.packageName?.toString() } finally { recycle(root) }
    if (pkg != null && pkg != packageName) {
      when (val decision = BlockEngine.decide(this, pkg, Engine.ACCESSIBILITY)) {
        is Decision.Block, is Decision.Friction -> {
          lastShownAt[pkg] = System.currentTimeMillis()
          BlockEngine.showScreen(this, decision, pkg, Engine.ACCESSIBILITY)
        }
        is Decision.Surfaces -> checkSurfaces(pkg)
        null -> {}
      }
    }
    scheduleBoundaryCheck()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // The system recycles the event after this returns: read what we need now, keep no reference.
    event ?: return
    val pkg = event.packageName?.toString() ?: return
    val type = event.eventType
    if (pkg == packageName) return
    val decision = BlockEngine.decide(this, pkg, Engine.ACCESSIBILITY) ?: return

    when (decision) {
      is Decision.Block, is Decision.Friction -> {
        // Whole app: react on window changes. Content changes only matter when the block began while
        // the user was already inside the app, so they are rate-limited to avoid a relaunch loop.
        val now = System.currentTimeMillis()
        val last = lastShownAt[pkg] ?: 0L
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || now - last > CONTENT_RESHOW_MS) {
          lastShownAt[pkg] = now
          BlockEngine.showScreen(this, decision, pkg, Engine.ACCESSIBILITY)
        }
      }
      is Decision.Surfaces -> {
        // Coalesce bursts of content-changed events while scrolling.
        pendingCheck?.let(handler::removeCallbacks)
        pendingCheck = Runnable {
          pendingCheck = null
          checkSurfaces(pkg)
        }.also { handler.postDelayed(it, DEBOUNCE_MS) }
      }
    }
  }

  private fun checkSurfaces(pkg: String) {
    // Re-evaluate: the segment may have ended or the user may have left during the debounce.
    val block = (BlockEngine.decide(this, pkg, Engine.ACCESSIBILITY) as? Decision.Surfaces)?.block ?: return
    val state = BlockState.load(this)
    val root = rootInActiveWindow ?: return
    val found = try {
      if (root.packageName?.toString() != pkg) return
      if (state.patterns !== detectorPatterns) {
        detector = SurfaceDetector(state.patterns)
        detectorPatterns = state.patterns
      }
      detector.detect(block.appId, root, block.surfaces)
    } finally {
      recycle(root)
    }
    if (found.isEmpty()) {
      attemptStreak[block.appId] = 0
      return
    }
    val now = System.currentTimeMillis()
    if (now - (lastBackAt[block.appId] ?: 0L) > STREAK_WINDOW_MS) attemptStreak[block.appId] = 0
    lastBackAt[block.appId] = now
    BlockEngine.recordAttempt(this, block.appId, now)
    val attempts = (attemptStreak[block.appId] ?: 0) + 1
    attemptStreak[block.appId] = attempts
    // Same safety valve as FocusGuard: if backing out keeps failing, go home.
    if (attempts >= 3) {
      performGlobalAction(GLOBAL_ACTION_HOME)
      attemptStreak[block.appId] = 0
    } else {
      performGlobalAction(GLOBAL_ACTION_BACK)
    }
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    val wanted = when (BlockState.load(this).captureGesture) {
      "volume_down_double" -> KeyEvent.KEYCODE_VOLUME_DOWN
      "volume_up_double" -> KeyEvent.KEYCODE_VOLUME_UP
      else -> return false
    }
    if (event.action != KeyEvent.ACTION_DOWN) return false
    if (event.keyCode != wanted) {
      // Any other key in between breaks the double press.
      lastVolumePress = 0L
      return false
    }
    if (event.repeatCount > 0) {
      // Held down (long press to change volume): not a gesture.
      lastVolumePress = 0L
      return false
    }
    val power = getSystemService(POWER_SERVICE) as? PowerManager
    if (power != null && !power.isInteractive) return false // screen off: leave volume alone (music)
    val now = event.eventTime
    if (lastVolumePress != 0L && now - lastVolumePress in 0..DOUBLE_PRESS_MS) {
      lastVolumePress = 0L
      openCapture()
    } else {
      lastVolumePress = now
    }
    // Never swallow the key: volume keeps working as usual.
    return false
  }

  private fun openCapture() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("foco://capture?source=gesture"))
      .setPackage(packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    runCatching { startActivity(intent) }
  }

  companion object {
    private const val DEBOUNCE_MS = 220L
    private const val STREAK_WINDOW_MS = 3_500L
    private const val DOUBLE_PRESS_MS = 450L
    private const val CONTENT_RESHOW_MS = 5_000L
    private const val MAX_BOUNDARY_WAIT_MS = 6 * 3600_000L

    @Volatile var instance: FocoAccessibilityService? = null
      private set

    /** Nodes are pooled before Android 13; returning them avoids exhausting the pool. No-op afterwards. */
    internal fun recycle(node: AccessibilityNodeInfo?) {
      if (node != null && Build.VERSION.SDK_INT < 33) {
        @Suppress("DEPRECATION")
        runCatching { node.recycle() }
      }
    }
  }
}
