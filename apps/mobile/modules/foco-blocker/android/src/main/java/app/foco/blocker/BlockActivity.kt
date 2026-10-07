package app.foco.blocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen page shown on top of a blocked app. Built in code to avoid resources/themes. */
class BlockActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
    val density = resources.displayMetrics.density
    val pad = (32 * density).toInt()

    val title = TextView(this).apply {
      text = "Ahora no 🙂"
      textSize = 30f
      setTypeface(typeface, Typeface.BOLD)
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
    }
    val body = TextView(this).apply {
      text = if (reason.isNotBlank()) "Esta app está bloqueada por «$reason».\nVuelve a lo tuyo, lo estás haciendo bien." else "Esta app está bloqueada ahora mismo."
      textSize = 17f
      setTextColor(Color.parseColor("#D0D4FF"))
      gravity = Gravity.CENTER
      setPadding(0, pad / 2, 0, pad)
    }
    val home = Button(this).apply {
      text = "Volver al inicio"
      setOnClickListener { goHome() }
    }
    val openFoco = Button(this).apply {
      text = "Abrir Foco"
      setOnClickListener {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
      }
    }
    setContentView(LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundColor(Color.parseColor("#1B1F3B"))
      setPadding(pad, pad, pad, pad)
      addView(title)
      addView(body)
      addView(home)
      addView(openFoco)
    })
  }

  @Deprecated("Back must not return to the blocked app")
  override fun onBackPressed() = goHome()

  private fun goHome() {
    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    finish()
  }

  companion object {
    const val EXTRA_REASON = "reason"
    const val EXTRA_APP = "app"
  }
}
