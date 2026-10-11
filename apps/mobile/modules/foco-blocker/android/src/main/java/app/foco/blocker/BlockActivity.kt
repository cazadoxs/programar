package app.foco.blocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen page shown on top of a blocked app, built in code to avoid
 * resources/themes. Two modes:
 *  - block: hard block, only "Volver al inicio" / "Abrir Foco";
 *  - friction: a 10 s pause, then "Abrir igualmente" lets the app through for 5 min.
 *
 * Not exported, so only Foco itself can start it. Even so, the extras are not
 * trusted: "Abrir igualmente" re-checks with BlockEngine that the app really
 * is under a friction block before granting the pass.
 */
class BlockActivity : Activity() {

  private val handler = Handler(Looper.getMainLooper())
  private var countdown: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    render(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    render(intent)
  }

  override fun onDestroy() {
    stopCountdown()
    super.onDestroy()
  }

  private fun render(intent: Intent?) {
    stopCountdown()
    val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_BLOCK
    val reason = intent?.getStringExtra(EXTRA_REASON).orEmpty().take(200)
    val appId = intent?.getStringExtra(EXTRA_APP).orEmpty()
    val pkg = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
    val density = resources.displayMetrics.density
    val pad = (32 * density).toInt()

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundColor(Color.parseColor("#1B1F3B"))
      setPadding(pad, pad, pad, pad)
    }
    val title = TextView(this).apply {
      textSize = 30f
      setTypeface(typeface, Typeface.BOLD)
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
    }
    val body = TextView(this).apply {
      textSize = 17f
      setTextColor(Color.parseColor("#D0D4FF"))
      gravity = Gravity.CENTER
      setPadding(0, pad / 2, 0, pad)
    }
    val home = Button(this).apply {
      text = "Volver"
      setOnClickListener { goHome() }
    }
    root.addView(title)
    root.addView(body)
    root.addView(home)

    if (mode == MODE_FRICTION && appId.isNotEmpty() && pkg.isNotEmpty()) {
      title.text = "Respira."
      body.text = "¿De verdad quieres abrir ${appName(appId, pkg)}?" + if (reason.isNotBlank()) "\n\n($reason)" else ""
      val anyway = Button(this).apply {
        isEnabled = false
        setOnClickListener { openAnyway(appId, pkg) }
      }
      root.addView(anyway)
      var left = FRICTION_SECONDS
      val tick = object : Runnable {
        override fun run() {
          if (left > 0) {
            anyway.text = "Abrir igualmente ($left)"
            left--
            handler.postDelayed(this, 1_000L)
          } else {
            anyway.text = "Abrir igualmente"
            anyway.isEnabled = true
            countdown = null
          }
        }
      }
      countdown = tick
      tick.run()
    } else {
      title.text = "Ahora no 🙂"
      body.text = if (reason.isNotBlank()) "Esta app está bloqueada por «$reason».\nVuelve a lo tuyo, lo estás haciendo bien." else "Esta app está bloqueada ahora mismo."
      home.text = "Volver al inicio"
      root.addView(Button(this).apply {
        text = "Abrir Foco"
        setOnClickListener {
          packageManager.getLaunchIntentForPackage(packageName)?.let {
            runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
          }
          finish()
        }
      })
    }
    setContentView(root)
  }

  private fun openAnyway(appId: String, pkg: String) {
    val decision = BlockEngine.decide(this, pkg, Engine.USAGE)
    // Only a friction block for that same app can be skipped (never a strict/hard block).
    if (decision is Decision.Friction && decision.block.appId == appId) {
      Allowances.grant(this, appId)
      finish()
    } else if (decision == null) {
      finish()
    } else {
      goHome()
    }
  }

  private fun stopCountdown() {
    countdown?.let(handler::removeCallbacks)
    countdown = null
  }

  @Deprecated("Back must not return to the blocked app")
  override fun onBackPressed() = goHome()

  private fun goHome() {
    BlockEngine.goHome(this)
    finish()
  }

  private fun appName(appId: String, pkg: String): String {
    KNOWN_NAMES[appId]?.let { return it }
    val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    return label?.takeIf { it.isNotBlank() } ?: appId.replaceFirstChar { it.uppercase() }
  }

  companion object {
    const val EXTRA_MODE = "mode"
    const val EXTRA_REASON = "reason"
    const val EXTRA_APP = "app"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_ENGINE = "engine"
    const val MODE_BLOCK = "block"
    const val MODE_FRICTION = "friction"
    private const val FRICTION_SECONDS = 10

    private val KNOWN_NAMES = mapOf(
      "instagram" to "Instagram", "tiktok" to "TikTok", "youtube" to "YouTube", "snapchat" to "Snapchat",
      "facebook" to "Facebook", "x" to "X", "reddit" to "Reddit", "whatsapp" to "WhatsApp",
    )
  }
}
