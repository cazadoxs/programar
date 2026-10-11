package app.facil.launcher.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.facil.launcher.R
import app.facil.launcher.core.LauncherConfig
import app.facil.launcher.core.Schedule
import app.facil.launcher.core.Tile
import app.facil.launcher.remote.Firebase
import app.facil.launcher.remote.SeniorSync
import app.facil.launcher.system.Actions
import app.facil.launcher.system.Notifs
import app.facil.launcher.system.ReminderReceiver
import app.facil.launcher.system.SyncJob
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de inicio: reloj grande, recordatorios del momento, los botones
 * configurados y el botón de ayuda. Ni el botón "atrás" ni pulsaciones
 * largas cambian nada: los ajustes solo se abren manteniendo pulsado y con PIN.
 */
class HomeActivity : BaseActivity() {
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var greeting: TextView
    private lateinit var battery: TextView
    private lateinit var remindersBox: LinearLayout
    private val tileViews = mutableListOf<TileView>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var renderedRev = -1L
    private var lastSync = 0L
    private var pendingCall: String? = null
    private val es = Locale("es", "ES")

    private val configListener: () -> Unit = { main.post { if (store.config.rev != renderedRev) rebuild() } }
    private val torchListener: () -> Unit = { tileViews.forEach { it.active = Actions.torchOn } }

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            if (System.currentTimeMillis() - lastSync > 60_000) syncNow()
            main.postDelayed(this, 15_000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            battery.text = (if (plugged) "⚡ " else "") + "$pct %"
            val low = pct <= 15 && !plugged
            battery.setTextColor(if (low) Color.WHITE else pal.text)
            battery.background = rounded(if (low) pal.sos else pal.surface, dpf(20), pal.line, dp(1))
            battery.contentDescription = "Batería al $pct por ciento" + if (plugged) ", cargando" else if (low) ". Hay que cargar el móvil" else ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            store.isFamily -> { startActivity(Intent(this, FamilyActivity::class.java)); finish(); return }
            !store.onboarded -> startActivity(Intent(this, WelcomeActivity::class.java))
        }
        rebuild()
        Actions.listenTorch(this)
    }

    private fun rebuild() {
        loadTheme()
        val c = store.config
        renderedRev = c.rev
        tileViews.clear()

        val root = vbox()
        // Cabecera: saludo + batería
        root.addView(hbox {
            greeting = text("", 20f, pal.sub, MEDIUM)
            addView(greeting, lp(0, WRAP, 1f))
            battery = text("", 20f, pal.text, BOLD, Gravity.CENTER).apply {
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = rounded(pal.surface, dpf(20), pal.line, dp(1))
            }
            addView(battery)
        })
        // Reloj (al tocarlo dice la hora)
        val clockBox = vbox {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, dp(8))
            clock = text("", 84f, pal.text, BLACK, Gravity.CENTER).apply { includeFontPadding = false; letterSpacing = -0.02f }
            date = text("", 23f, pal.text, MEDIUM, Gravity.CENTER)
            addView(clock); addView(date)
            isClickable = true
            setOnClickListener { speakTime() }
        }
        root.addView(clockBox)

        remindersBox = vbox()
        root.addView(remindersBox)

        // Rejilla de botones
        val grid = vbox()
        val tiles = c.tiles.filter { Actions.isAvailable(this, it) }
        val rows = tiles.chunked(2)
        rows.forEachIndexed { ri, pair ->
            val row = hbox {}
            pair.forEachIndexed { ci, t ->
                val idx = c.tiles.indexOf(t)
                val tv = TileView(this, t, idx).apply { setOnClickListener { onTile(t) } }
                if (t.type == Tile.FLASHLIGHT) tv.active = Actions.torchOn
                tileViews += tv
                row.addView(tv, lp(0, MATCH, 1f).apply {
                    if (ci == 0 && pair.size == 2) marginEnd = dp(7) else if (ci == 1) marginStart = dp(7)
                })
            }
            grid.addView(row, lp(MATCH, 0, 1f).apply { if (ri > 0) topMargin = dp(14) })
        }
        if (tiles.isEmpty()) grid.addView(text("Mantén pulsado «Ajustes» para añadir botones", 22f, pal.sub, gravity = Gravity.CENTER), lp(MATCH, MATCH))
        root.addView(grid, lp(MATCH, 0, 1f).apply { topMargin = dp(6) })

        if (c.showAppsButton) {
            root.addView(ghostButton("Todas las aplicaciones") { startActivity(Intent(this, AppsActivity::class.java)) },
                lp().apply { topMargin = dp(12) })
        }

        // Pie: ajustes (mantener) + ayuda (mantener)
        root.addView(hbox {
            val settings = HoldButton(this@HomeActivity, "Ajustes", pal.surface, pal.sub, 2000) { openSettings() }.apply {
                hint = { toast("Mantén pulsado para abrir los ajustes") }
                background = rounded(Color.TRANSPARENT, dpf(38), pal.line, dp(2))
            }
            addView(settings, lp(dp(130), dp(76)).apply { marginEnd = dp(12) })
            val sos = HoldButton(this@HomeActivity, "AYUDA", pal.sos, pal.onSos, 1500) {
                startActivity(Intent(this@HomeActivity, EmergencyActivity::class.java))
            }.apply { hint = { toast("Mantén pulsado el botón rojo para pedir ayuda") } }
            addView(sos, lp(0, dp(76), 1f))
        }, lp().apply { topMargin = dp(14) })

        val members = store.memberNames
        if (members.isNotEmpty() && store.shareStatus && store.cloudEnabled) {
            root.addView(hbox {
                gravity = Gravity.CENTER
                addView(ImageView(this@HomeActivity).apply {
                    setImageResource(R.drawable.ic_family); imageTintList = ColorStateList.valueOf(pal.sub)
                }, lp(dp(20), dp(20)).apply { marginEnd = dp(6) })
                addView(text("${members.joinToString(", ")} te ayuda con este móvil", 15f, pal.sub))
            }, lp().apply { topMargin = dp(8) })
        }

        setRoot(root)
        updateClock()
        refreshReminders()
    }

    override fun onResume() {
        super.onResume()
        if (!::clock.isInitialized) return
        store.lastActive = System.currentTimeMillis()
        store.addListener(configListener)
        Actions.torchListeners += torchListener
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (store.config.rev != renderedRev) rebuild()
        main.post(tick)
        refreshReminders()
    }

    override fun onPause() {
        super.onPause()
        store.removeListener(configListener)
        Actions.torchListeners -= torchListener
        try { unregisterReceiver(batteryReceiver) } catch (e: IllegalArgumentException) { }
        main.removeCallbacks(tick)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* En el inicio "atrás" no hace nada. */ }

    private fun syncNow() {
        lastSync = System.currentTimeMillis()
        if (!store.cloudEnabled || !Firebase.get(this).configured) return
        bg({
            val s = SeniorSync(this)
            val changed = s.syncConfig()
            s.pushStatus()
            s.members()
            changed
        }) { r ->
            if (r.getOrNull() == true) ReminderReceiver.schedule(this)
            Notifs.updateSharing(this)
        }
    }

    private fun updateClock() {
        val now = Date()
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        clock.text = SimpleDateFormat(if (is24) "H:mm" else "h:mm", es).format(now)
        date.text = SimpleDateFormat("EEEE, d 'de' MMMM", es).format(now).replaceFirstChar { it.titlecase(es) }
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = when (h) { in 6..12 -> "Buenos días"; in 13..20 -> "Buenas tardes"; else -> "Buenas noches" }
        val name = store.config.ownerName
        greeting.text = if (name.isNotBlank()) "$hello, $name" else hello
        refreshReminders()
    }

    private fun refreshReminders() {
        if (!::remindersBox.isInitialized) return
        remindersBox.removeAllViews()
        val now = System.currentTimeMillis()
        val done = store.remindersDone()
        for (r in store.config.reminders) {
            val occ = Schedule.lastOccurrence(r, now) ?: continue
            if (now - occ > 3 * 3_600_000L) continue
            if ((done[r.id] ?: 0) >= occ - 30 * 60_000L) continue
            remindersBox.addView(hbox {
                background = rounded(pal.warn, dpf(22), pal.line, dp(if (pal === Palette.CONTRAST) 2 else 0))
                setPadding(dp(16), dp(12), dp(12), dp(12))
                addView(ImageView(this@HomeActivity).apply {
                    setImageResource(R.drawable.ic_pill); imageTintList = ColorStateList.valueOf(pal.onWarn)
                }, lp(dp(34), dp(34)).apply { marginEnd = dp(12) })
                addView(vbox {
                    addView(text(r.label, 22f, pal.onWarn, BOLD))
                    addView(text("A las ${r.timeText()}", 17f, pal.onWarn))
                }, lp(0, WRAP, 1f))
                addView(button("Hecho", pal.onWarn, pal.warn, 20f) {
                    store.markReminderDone(r.id, System.currentTimeMillis())
                    Notifs.cancelReminder(this@HomeActivity, r.id)
                    SyncJob.runSoon(this@HomeActivity)
                    say("Muy bien")
                    refreshReminders()
                })
            }, lp().apply { bottomMargin = dp(10) })
        }
    }

    private fun onTile(t: Tile) {
        val c = store.config
        if (c.speak && t.type != Tile.TIME) say(t.label.ifBlank { TileView.defaultLabel(t.type) })
        when (t.type) {
            Tile.CALL -> withPhone(t) { phone ->
                if (c.confirmCalls) confirm("¿Llamar a ${t.label}?", "Sí, llamar", "No") { placeCall(phone) }
                else placeCall(phone)
            }
            Tile.WHATSAPP -> Actions.whatsapp(this, t.phone)
            Tile.SMS_TO -> withPhone(t) { Actions.smsTo(this, it) }
            Tile.CAMERA -> Actions.camera(this)
            Tile.GALLERY -> Actions.gallery(this)
            Tile.DIALER -> Actions.dialer(this)
            Tile.MESSAGES -> Actions.messages(this)
            Tile.APP -> Actions.app(this, t.pkg)
            Tile.FLASHLIGHT -> Actions.toggleTorch(this)
            Tile.ALL_APPS -> startActivity(Intent(this, AppsActivity::class.java))
            Tile.TIME -> speakTime()
        }
    }

    private fun withPhone(t: Tile, f: (String) -> Unit) {
        if (t.phone.isEmpty()) toast("Este botón todavía no tiene número. Pide a tu familia que lo configure.")
        else f(t.phone)
    }

    private fun placeCall(phone: String) {
        if (!Actions.canCallDirectly(this) && !store.askedCallPermission) {
            store.askedCallPermission = true
            pendingCall = phone
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
            return
        }
        Actions.call(this, phone)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_CALL) pendingCall?.let { Actions.call(this, it) }
        pendingCall = null
    }

    private fun openSettings() {
        val c = store.config
        if (c.hasPin) startActivity(Intent(this, PinActivity::class.java).putExtra(PinActivity.EXTRA_NEXT, PinActivity.NEXT_SETTINGS))
        else startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun speakTime() {
        val now = Date()
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val m = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
        val day = SimpleDateFormat("EEEE d 'de' MMMM", es).format(now)
        say(Speech.time(h, m) + ". Hoy es " + day + ".")
    }

    private fun say(s: String) {
        val t = tts
        if (t != null && ttsReady) { t.speak(s, TextToSpeech.QUEUE_FLUSH, null, "facil"); return }
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = es
                tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "facil")
            }
        }
    }

    companion object {
        private const val REQ_CALL = 7
    }
}

/** Frases para leer en voz alta. */
object Speech {
    fun time(h: Int, m: Int): String {
        val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
        val hourWord = if (h12 == 1) "la una" else "las $h12"
        val verb = if (h12 == 1) "Es" else "Son"
        val part = when (h) { in 6..11 -> "de la mañana"; 12, 13 -> "del mediodía"; in 14..20 -> "de la tarde"; else -> "de la noche" }
        val mins = when {
            m == 0 -> "en punto"
            m == 15 -> "y cuarto"
            m == 30 -> "y media"
            else -> "y $m"
        }
        return "$verb $hourWord $mins $part"
    }
}
