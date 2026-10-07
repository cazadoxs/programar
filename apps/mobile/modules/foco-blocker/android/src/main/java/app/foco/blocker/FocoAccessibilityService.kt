package app.foco.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Event-driven blocker (no polling, no wake locks), evolved from FocusGuard's
 * BlockerAccessibilityService:
 *  - whole-app blocks show BlockActivity on top of the blocked app;
 *  - surface blocks (reels, stories…) back the user out of that screen;
 *  - a double press of the chosen volume key opens Foco's quick capture.
 */
class FocoAccessibilityService : AccessibilityService() {

  private val handler = Handler(Looper.getMainLooper())
  private var pendingCheck: Runnable? = null
  private var detector = SurfaceDetector(null)
  private var detectorPatterns: Any? = null
  private val lastBlockAt = mutableMapOf<String, Long>()
  private val attemptStreak = mutableMapOf<String, Int>()
  private var lastVolumePress = 0L

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    applyPackageFilter()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    instance = null
    pendingCheck?.let(handler::removeCallbacks)
    return super.onUnbind(intent)
  }

  override fun onInterrupt() {}

  /** Only receive events from apps that can be blocked (the list comes from the JS catalog). */
  fun applyPackageFilter() {
    val state = BlockState.load(this)
    val info = serviceInfo ?: return
    info.packageNames = state.watchedPackages.takeIf { it.isNotEmpty() }?.toTypedArray()
    serviceInfo = info
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString() ?: return
    if (pkg == packageName) return
    val state = BlockState.load(this)
    val block = state.blockFor(pkg, System.currentTimeMillis()) ?: return

    if ("app" in block.surfaces) {
      // Whole app blocked: react immediately on window changes.
      if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) showBlockScreen(block)
      return
    }
    // Coalesce bursts of content-changed events while scrolling.
    pendingCheck?.let(handler::removeCallbacks)
    pendingCheck = Runnable { checkSurfaces(block, state) }.also { handler.postDelayed(it, DEBOUNCE_MS) }
  }

  private fun checkSurfaces(block: AppBlock, state: BlockState) {
    val root = rootInActiveWindow ?: return
    if (state.patterns !== detectorPatterns) {
      detector = SurfaceDetector(state.patterns)
      detectorPatterns = state.patterns
    }
    val found = detector.detect(block.appId, root, block.surfaces)
    if (found.isEmpty()) {
      attemptStreak[block.appId] = 0
      return
    }
    val now = System.currentTimeMillis()
    val last = lastBlockAt[block.appId] ?: 0L
    if (now - last > COOLDOWN_MS) {
      lastBlockAt[block.appId] = now
      attemptStreak[block.appId] = 0
      BlockStats.record(this, block.appId, today())
    }
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

  private fun showBlockScreen(block: AppBlock) {
    val now = System.currentTimeMillis()
    if (now - (lastBlockAt[block.appId] ?: 0L) > COOLDOWN_MS) BlockStats.record(this, block.appId, today())
    lastBlockAt[block.appId] = now
    val intent = Intent(this, BlockActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      .putExtra(BlockActivity.EXTRA_REASON, block.reason)
      .putExtra(BlockActivity.EXTRA_APP, block.appId)
    startActivity(intent)
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    val gesture = BlockState.load(this).captureGesture
    val wanted = when (gesture) {
      "volume_down_double" -> KeyEvent.KEYCODE_VOLUME_DOWN
      "volume_up_double" -> KeyEvent.KEYCODE_VOLUME_UP
      else -> return false
    }
    if (event.keyCode != wanted || event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
    val now = event.eventTime
    if (now - lastVolumePress < DOUBLE_PRESS_MS) {
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

  private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

  companion object {
    private const val DEBOUNCE_MS = 220L
    private const val COOLDOWN_MS = 3500L
    private const val DOUBLE_PRESS_MS = 450L

    @Volatile var instance: FocoAccessibilityService? = null
      private set
  }
}
