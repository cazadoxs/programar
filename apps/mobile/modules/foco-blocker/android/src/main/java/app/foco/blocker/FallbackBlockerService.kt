package app.foco.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Fallback engine for when the accessibility service is not available
 * (refused by the user, or blocked by Android 17 Advanced Protection Mode).
 *
 * Reads the foreground app from usage-access events. To save battery it only
 * polls (every POLL_MS) while the screen is on AND the current timeline
 * segment has blocks or daily limits must be watched; otherwise it sleeps on a
 * Handler until the next segment boundary (or until the screen turns on).
 * Handler delays use uptime, which stops in deep sleep, but the screen-on
 * receiver re-evaluates immediately, so a late wake-up is never observable.
 *
 * If FocoAccessibilityService is running it is the primary engine: this
 * service stops itself and never fires.
 */
class FallbackBlockerService : Service() {

  private lateinit var thread: HandlerThread
  private lateinit var worker: Handler
  private lateinit var detector: ForegroundAppDetector
  private var screenReceiverRegistered = false

  // Worker-thread state.
  private var lastForeground: String? = null
  private var lastActionPkg: String? = null
  private var lastActionAt = 0L

  private val tick = Runnable { runTick() }

  private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> reschedule(0L)
        Intent.ACTION_SCREEN_OFF -> worker.post {
          worker.removeCallbacks(tick)
          detector.reset()
          lastForeground = null
        }
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    thread = HandlerThread("FocoFallbackBlocker").also { it.start() }
    worker = Handler(thread.looper)
    detector = ForegroundAppDetector(this)
    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_SCREEN_ON)
      addAction(Intent.ACTION_SCREEN_OFF)
      addAction(Intent.ACTION_USER_PRESENT)
    }
    // System broadcasts still reach a NOT_EXPORTED receiver; other apps cannot poke it.
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    else registerReceiver(screenReceiver, filter)
    screenReceiverRegistered = true
    instance = this
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // startForeground must be called promptly after startForegroundService, even if we stop right away.
    if (!goForeground()) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (!shouldRun(this)) {
      stopSelf()
      return START_NOT_STICKY
    }
    reschedule(0L)
    return START_STICKY
  }

  override fun onDestroy() {
    instance = null
    if (screenReceiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
    screenReceiverRegistered = false
    worker.removeCallbacksAndMessages(null)
    thread.quitSafely()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  /** New state from JS, or accessibility appeared/disappeared. */
  fun refresh() = reschedule(0L)

  private fun reschedule(delayMs: Long) {
    worker.removeCallbacks(tick)
    worker.postDelayed(tick, delayMs)
  }

  private fun runTick() {
    if (!shouldRun(this)) {
      stopSelf()
      return
    }
    val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (power != null && !power.isInteractive) return // wait for ACTION_SCREEN_ON

    val now = System.currentTimeMillis()
    val state = BlockState.load(this)
    if (!state.needsWatching(now)) {
      lastForeground = null
      detector.reset()
      val next = state.nextBoundaryAfter(now)
      if (next != null) worker.postDelayed(tick, (next - now).coerceIn(1_000L, MAX_SLEEP_MS))
      else worker.postDelayed(tick, MAX_SLEEP_MS)
      return
    }

    try {
      check(now)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Fallback check failed", e)
    }
    worker.postDelayed(tick, POLL_MS)
  }

  private fun check(now: Long) {
    val fg = detector.poll(now)
    val changed = fg != lastForeground
    lastForeground = fg
    if (fg == null || fg == packageName) return
    val decision = BlockEngine.decide(this, fg, Engine.USAGE, now) ?: return
    // Act when the user enters the app; if they are still in it later (segment
    // started while inside, pass expired, launch refused) act again, but not every tick.
    val uptime = SystemClock.uptimeMillis()
    if (!changed && fg == lastActionPkg && uptime - lastActionAt < REPEAT_MS) return
    lastActionPkg = fg
    lastActionAt = uptime
    if (!BlockEngine.showScreen(this, decision, fg, Engine.USAGE) && decision is Decision.Block) {
      BlockEngine.goHome(this)
    }
  }

  private fun goForeground(): Boolean {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
      val channel = NotificationChannel(CHANNEL_ID, "Protección de concentración", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Aviso fijo mientras Foco vigila las apps que has decidido bloquear."
        setShowBadge(false)
      }
      manager.createNotificationChannel(channel)
    }
    val launch = packageManager.getLaunchIntentForPackage(packageName)
    val content = launch?.let {
      PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
    else @Suppress("DEPRECATION") Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
    val notification = builder
      .setSmallIcon(R.drawable.foco_ic_notification)
      .setContentTitle("Foco está protegiendo tu concentración")
      .setContentText("Vigila las apps que has elegido bloquear.")
      .setOngoing(true)
      .setShowWhen(false)
      .setCategory(Notification.CATEGORY_SERVICE)
      .apply { if (content != null) setContentIntent(content) }
      .build()
    return try {
      if (Build.VERSION.SDK_INT >= 34) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      true
    } catch (e: RuntimeException) {
      // e.g. ForegroundServiceStartNotAllowedException when started from the background.
      Log.w(TAG, "Could not start the fallback blocker in the foreground", e)
      false
    }
  }

  companion object {
    private const val TAG = "FocoBlocker"
    private const val CHANNEL_ID = "foco_protection"
    private const val NOTIFICATION_ID = 4107
    private const val POLL_MS = 800L
    private const val REPEAT_MS = 3_000L
    private const val MAX_SLEEP_MS = 6 * 3600_000L

    @Volatile var instance: FallbackBlockerService? = null
      private set

    val isRunning: Boolean get() = instance != null

    /** The fallback engine is wanted: enabled, usage access granted, accessibility not running, something to block. */
    fun shouldRun(context: Context): Boolean =
      FocoAccessibilityService.instance == null &&
        BlockerSettings.fallbackEnabled(context) &&
        UsageAccess.hasAccess(context) &&
        BlockState.load(context).hasWorkFrom(System.currentTimeMillis())

    /** Starts, refreshes or stops the service to match [shouldRun]. Safe to call from any thread. */
    fun sync(context: Context) {
      val app = context.applicationContext
      val running = instance
      if (!shouldRun(app)) {
        if (running != null) app.stopService(Intent(app, FallbackBlockerService::class.java))
        return
      }
      if (running != null) {
        running.refresh()
        return
      }
      val intent = Intent(app, FallbackBlockerService::class.java)
      try {
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
      } catch (e: RuntimeException) {
        // Background start not allowed right now (IllegalStateException / ForegroundServiceStartNotAllowedException).
        // It is retried on the next setState from the app, and at boot.
        Log.w(TAG, "Could not start the fallback blocker", e)
      }
    }
  }
}
