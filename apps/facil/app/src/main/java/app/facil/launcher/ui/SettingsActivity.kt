package app.facil.launcher.ui

import app.facil.launcher.system.svc
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.facil.launcher.R
import app.facil.launcher.core.Ids
import app.facil.launcher.core.LauncherConfig
import app.facil.launcher.core.Phone
import app.facil.launcher.core.Reminder
import app.facil.launcher.core.Schedule
import app.facil.launcher.core.Tile
import app.facil.launcher.remote.FamilySync
import app.facil.launcher.remote.Firebase
import app.facil.launcher.remote.SeniorSync
import app.facil.launcher.system.Notifs
import app.facil.launcher.system.ReminderReceiver
import org.json.JSONObject

/**
 * Ajustes. La misma pantalla sirve en el móvil de la persona mayor (guarda
 * en local y sube a la nube) y en el del familiar (con [EXTRA_DEVICE]: edita
 * a distancia la configuración de ese móvil).
 */
class SettingsActivity : BaseActivity() {
    private val remoteId: String? get() = intent.getStringExtra(EXTRA_DEVICE)
    private val remote get() = remoteId != null
    private var cfg: LauncherConfig? = null
    private lateinit var content: LinearLayout
    private var personName: EditText? = null
    private var personPhone: EditText? = null
    private var saving = false

    override fun themeConfig(): LauncherConfig =
        if (remote) LauncherConfig.defaults().copy(theme = store.config.theme) else store.config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = vbox()
        val root = vbox {
            addView(hbox {
                addView(title(if (remote) "Ajustes de ${intent.getStringExtra(EXTRA_NAME) ?: "su móvil"}" else "Ajustes"), lp(0, WRAP, 1f))
            })
            addView(scroll(content), lp(MATCH, 0, 1f))
            addView(button(if (remote) "Listo" else "Volver al inicio") { finish() }, lp().apply { topMargin = dp(12) })
        }
        setRoot(root)
        if (remote) {
            content.addView(text("Cargando…", 20f, pal.sub))
            bg({ FamilySync(this).fetch(remoteId!!) }) { r ->
                val d = r.getOrNull()
                if (d?.config == null) {
                    content.removeAllViews()
                    content.addView(text(if (r.isFailure) "Sin conexión. Inténtalo de nuevo." else "No tienes acceso a este móvil.", 20f, pal.text))
                } else { cfg = d.config!!.copy(rev = d.configRev); render() }
            }
        } else {
            cfg = store.config
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!remote && cfg != null) { cfg = store.config; render() }
    }

    /** Guarda el cambio (en local o enviándolo al móvil de la persona mayor) y repinta. */
    private fun commit(c: LauncherConfig) {
        cfg = c
        render()
        if (remote) {
            saving = true
            bg({ FamilySync(this).pushConfig(remoteId!!, c) }) { r ->
                saving = false
                r.onSuccess { cfg = it }.onFailure { toast("No se ha podido enviar. Revisa la conexión.") }
            }
        } else {
            store.saveLocal(c)
            cfg = store.config
            ReminderReceiver.schedule(this)
            if (store.cloudEnabled && Firebase.get(this).configured) {
                val latest = store.config
                bg({ SeniorSync(this).pushConfig(latest) }) { }
            }
        }
    }

    private fun section(name: String) {
        content.addView(text(name.uppercase(), 15f, pal.accent, BOLD).apply { letterSpacing = 0.08f; setPadding(dp(4), dp(22), 0, dp(6)) })
    }

    private fun render() {
        val c = cfg ?: return
        content.removeAllViews()

        // ---- Botones ----
        section("Botones de la pantalla de inicio")
        content.addView(card {
            c.tiles.forEachIndexed { i, t ->
                if (i > 0) addView(divider())
                addView(hbox {
                    addView(row(t.label.ifBlank { TileView.defaultLabel(t.type) }, describeTile(t), TileView.iconFor(t.type)) { editTile(t) }, lp(0, WRAP, 1f))
                    addView(smallButton("▲", i > 0) { commit(c.copy(tiles = c.tiles.toMutableList().apply { add(i - 1, removeAt(i)) })) })
                    addView(smallButton("▼", i < c.tiles.size - 1) { commit(c.copy(tiles = c.tiles.toMutableList().apply { add(i + 1, removeAt(i)) })) })
                })
            }
            if (c.tiles.size < LauncherConfig.MAX_TILES) {
                addView(space(8))
                addView(button("Añadir botón") { editTile(null) })
            }
        })

        // ---- Ayuda ----
        section("Botón rojo de ayuda")
        content.addView(card {
            addView(row("Primera persona a la que llamar",
                if (c.sosPhone.isEmpty()) "Sin configurar" else "${c.sosName} · ${Phone.pretty(c.sosPhone)}", R.drawable.ic_sos) {
                editPerson("Contacto de emergencia", c.sosName, c.sosPhone) { n, p -> commit(c.copy(sosName = n, sosPhone = p)) }
            })
            addView(divider())
            addView(row("Segunda persona (opcional)",
                if (c.sos2Phone.isEmpty()) "Sin configurar" else "${c.sos2Name} · ${Phone.pretty(c.sos2Phone)}", R.drawable.ic_person) {
                editPerson("Segundo contacto", c.sos2Name, c.sos2Phone) { n, p -> commit(c.copy(sos2Name = n, sos2Phone = p)) }
            })
        })

        // ---- Persona y apariencia ----
        section("Apariencia")
        content.addView(card {
            addView(row("Nombre para el saludo", c.ownerName.ifBlank { "Sin nombre" }, R.drawable.ic_person) {
                editText("¿Cómo se llama?", c.ownerName, 30) { commit(c.copy(ownerName = it)) }
            })
            addView(divider())
            addView(row("Colores", themeName(c.theme), R.drawable.ic_palette) {
                choose("Colores", LauncherConfig.THEMES.map(::themeName), LauncherConfig.THEMES.indexOf(c.theme)) {
                    commit(c.copy(theme = LauncherConfig.THEMES[it]))
                    if (!remote) recreate()
                }
            })
            addView(divider())
            val sizes = listOf("Grande", "Muy grande", "Enorme")
            addView(row("Tamaño de letra", sizes[LauncherConfig.TEXT_SCALES.indexOf(c.textScale).coerceAtLeast(0)], R.drawable.ic_text) {
                choose("Tamaño de letra", sizes, LauncherConfig.TEXT_SCALES.indexOf(c.textScale)) {
                    commit(c.copy(textScale = LauncherConfig.TEXT_SCALES[it]))
                    if (!remote) recreate()
                }
            })
            addView(divider())
            addView(toggle("Leer en voz alta", "Dice el nombre de cada botón al pulsarlo", c.speak) { commit(c.copy(speak = it)) })
            addView(divider())
            addView(toggle("Preguntar antes de llamar", "Evita llamadas sin querer", c.confirmCalls) { commit(c.copy(confirmCalls = it)) })
            addView(divider())
            addView(toggle("Botón «Todas las aplicaciones»", "Muestra la lista de apps en el inicio", c.showAppsButton) { commit(c.copy(showAppsButton = it)) })
            if (!remote && c.showAppsButton) {
                addView(divider())
                addView(row("Qué aplicaciones se ven", if (c.allowedApps.isEmpty()) "Todas" else "${c.allowedApps.size} elegidas", R.drawable.ic_apps) { chooseApps(c) })
            }
        })

        // ---- Recordatorios ----
        section("Recordatorios")
        content.addView(card {
            if (c.reminders.isEmpty()) addView(text("Pastillas, citas, regar las plantas… Avisa con sonido y queda en la pantalla hasta que se marca como hecho.", 17f, pal.sub))
            c.reminders.sortedBy { it.hour * 60 + it.minute }.forEachIndexed { i, r ->
                if (i > 0) addView(divider())
                addView(row("${r.timeText()} · ${r.label}", Schedule.daysText(r.days), R.drawable.ic_pill) { editReminder(r) })
            }
            if (c.reminders.size < LauncherConfig.MAX_REMINDERS) {
                addView(space(8))
                addView(button("Añadir recordatorio") { editReminder(null) })
            }
        })

        // ---- Familia ----
        section("Familia")
        content.addView(card {
            if (!remote) {
                addView(row("Gestión desde el móvil de un familiar",
                    if (store.memberNames.isEmpty()) "Que alguien de confianza configure este móvil desde el suyo" else store.memberNames.joinToString(", "),
                    R.drawable.ic_family) { startActivity(Intent(this@SettingsActivity, PairActivity::class.java)) })
                addView(divider())
            }
            val hours = listOf(4, 8, 12, 24, 48)
            addView(row("Avisar si no se usa el móvil en", "${c.inactivityHours} horas", R.drawable.ic_clock) {
                choose("Avisar a la familia tras…", hours.map { "$it horas" }, hours.indexOf(c.inactivityHours)) {
                    commit(c.copy(inactivityHours = hours[it]))
                }
            })
        })

        // ---- Seguridad ----
        section("Seguridad")
        content.addView(card {
            addView(row(if (c.hasPin) "Cambiar PIN" else "Poner un PIN",
                if (c.hasPin) "Los ajustes están protegidos" else "Recomendado: así nadie los cambia sin querer", R.drawable.ic_lock) {
                startActivityForResult(Intent(this@SettingsActivity, PinActivity::class.java).putExtra(PinActivity.EXTRA_CREATE, true), REQ_PIN)
            })
            if (c.hasPin) {
                addView(divider())
                addView(row("Quitar el PIN", null, R.drawable.ic_trash) {
                    confirm("¿Quitar el PIN?", "Sí, quitar", "No", "Cualquiera podrá entrar en los ajustes") { commit(c.copy(pinSalt = "", pinHash = "")) }
                })
            }
        })

        if (!remote) {
            section("Este móvil")
            content.addView(card {
                addView(row(if (isDefaultHome()) "Fácil es la pantalla de inicio" else "Usar Fácil como pantalla de inicio",
                    if (isDefaultHome()) "Al pulsar el botón de inicio aparece Fácil" else "Pulsa para elegirlo", R.drawable.ic_home) { requestHome() })
                addView(divider())
                addView(row("Privacidad y datos", "Qué se comparte y cómo borrarlo", R.drawable.ic_lock) {
                    startActivity(Intent(this@SettingsActivity, PrivacyActivity::class.java))
                })
                addView(divider())
                addView(row("Ajustes del sistema Android", "Wifi, sonido, brillo…", R.drawable.ic_settings) {
                    try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) { }
                })
                addView(divider())
                addView(row("Valorar Fácil", "Nos ayuda muchísimo", R.drawable.ic_star) { openStore() })
            })
            content.addView(text("Fácil ${versionName()}", 14f, pal.sub, gravity = Gravity.CENTER).apply { setPadding(0, dp(18), 0, 0) })
        }
    }

    private fun smallButton(s: String, enabled: Boolean, onClick: () -> Unit) = text(s, 20f, if (enabled) pal.text else pal.line, BOLD, Gravity.CENTER).apply {
        layoutParams = lp(dp(48), dp(56))
        if (enabled) {
            background = ripple(rounded(android.graphics.Color.TRANSPARENT, dpf(14)), pal.dark, dpf(14))
            isClickable = true
            setOnClickListener { onClick() }
            contentDescription = if (s == "▲") "Subir" else "Bajar"
        }
    }

    private fun toggle(t: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit): LinearLayout = hbox {
        setPadding(dp(4), dp(12), dp(4), dp(12))
        addView(vbox {
            addView(text(t, 20f, pal.text, MEDIUM))
            addView(text(sub, 16f, pal.sub))
        }, lp(0, WRAP, 1f))
        val pill = text(if (value) "SÍ" else "NO", 18f, if (value) pal.onAccent else pal.text, BOLD, Gravity.CENTER).apply {
            background = rounded(if (value) pal.accent else pal.surface, dpf(18), pal.line, dp(2))
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        addView(pill, lp(dp(76), WRAP).apply { marginStart = dp(10) })
        isClickable = true
        background = ripple(rounded(android.graphics.Color.TRANSPARENT, dpf(12)), pal.dark, dpf(12))
        setOnClickListener { onChange(!value) }
    }

    private fun describeTile(t: Tile) = when {
        t.needsPhone && t.phone.isNotEmpty() -> TileView.typeName(t.type) + " · " + Phone.pretty(t.phone)
        t.needsPhone && t.type != Tile.WHATSAPP -> "Falta el número"
        else -> TileView.typeName(t.type)
    }

    private fun themeName(t: String) = when (t) {
        LauncherConfig.THEME_NIGHT -> "Noche (fondo oscuro)"
        LauncherConfig.THEME_CONTRAST -> "Alto contraste (negro y amarillo)"
        else -> "Crema (claro y cálido)"
    }

    private fun editTile(t: Tile?) {
        val i = Intent(this, TileEditorActivity::class.java).putExtra(TileEditorActivity.EXTRA_REMOTE, remote)
        if (t != null) i.putExtra(TileEditorActivity.EXTRA_TILE, t.toJson().toString())
        startActivityForResult(i, REQ_TILE)
    }

    private fun editText(titleText: String, value: String, max: Int, onSave: (String) -> Unit) {
        val inp = input(titleText, value, max = max)
        val box = vbox { setPadding(dp(20), dp(8), dp(20), 0); addView(inp) }
        dialog().setTitle(titleText).setView(box)
            .setPositiveButton("Guardar") { _, _ -> onSave(inp.text.toString().trim()) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun editPerson(titleText: String, name: String, phone: String, onSave: (String, String) -> Unit) {
        val n = input("Nombre", name, max = 30)
        val p = input("Teléfono", phone, InputType.TYPE_CLASS_PHONE, 20)
        personName = n; personPhone = p
        val box = vbox {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(n); addView(space(10)); addView(p); addView(space(10))
            addView(ghostButton("Elegir de la agenda") { pickContact(REQ_CONTACT_PERSON) })
        }
        dialog().setTitle(titleText).setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val ph = Phone.clean(p.text.toString())
                if (ph.isNotEmpty() && !Phone.isValid(ph)) toast("El número no parece correcto")
                else onSave(n.text.toString().trim().take(30), ph)
            }
            .setNeutralButton("Borrar") { _, _ -> onSave("", "") }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun pickContact(req: Int) {
        try {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), req)
        } catch (e: Exception) { toast("No hay agenda de contactos") }
    }

    private fun choose(titleText: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        dialog().setTitle(titleText)
            .setSingleChoiceItems(options.toTypedArray(), selected) { d, which -> d.dismiss(); onPick(which) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun chooseApps(c: LauncherConfig) {
        bg({
            val pm = packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .sortedBy { it.second.lowercase() }
        }) { r ->
            val apps = r.getOrDefault(emptyList())
            val sel = BooleanArray(apps.size) { apps[it].first in c.allowedApps }
            dialog().setTitle("Aplicaciones visibles")
                .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), sel) { _, w, v -> sel[w] = v }
                .setPositiveButton("Guardar") { _, _ ->
                    commit(c.copy(allowedApps = apps.filterIndexed { i, _ -> sel[i] }.map { it.first }))
                }
                .setNeutralButton("Mostrar todas") { _, _ -> commit(c.copy(allowedApps = emptyList())) }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    private fun editReminder(r: Reminder?) {
        val c = cfg ?: return
        val label = input("Qué hay que hacer (p. ej. Pastilla de la tensión)", r?.label ?: "", max = 40)
        var hour = r?.hour ?: 9
        var minute = r?.minute ?: 0
        var days = r?.days ?: 127
        lateinit var timeBtn: TextView
        lateinit var daysBtn: TextView
        val box = vbox {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(label); addView(space(10))
            timeBtn = ghostButton("Hora: %02d:%02d".format(hour, minute)) {
                TimePickerDialog(this@SettingsActivity, { _, h, m ->
                    hour = h; minute = m; timeBtn.text = "Hora: %02d:%02d".format(h, m)
                }, hour, minute, true).show()
            }
            addView(timeBtn); addView(space(10))
            daysBtn = ghostButton(Schedule.daysText(days)) {
                val names = arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
                val sel = BooleanArray(7) { (days shr it) and 1 == 1 }
                dialog().setTitle("Qué días").setMultiChoiceItems(names, sel) { _, w, v -> sel[w] = v }
                    .setPositiveButton("Listo") { _, _ ->
                        val d = sel.foldIndexed(0) { i, acc, v -> if (v) acc or (1 shl i) else acc }
                        days = if (d == 0) 127 else d
                        daysBtn.text = Schedule.daysText(days)
                    }.show()
            }
            addView(daysBtn)
        }
        val b = dialog().setTitle(if (r == null) "Nuevo recordatorio" else "Recordatorio").setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val text = label.text.toString().trim()
                if (text.isEmpty()) { toast("Escribe qué hay que recordar"); return@setPositiveButton }
                val nr = Reminder(r?.id ?: Ids.random(8), text.take(40), hour, minute, days)
                commit(c.copy(reminders = c.reminders.filter { it.id != nr.id } + nr))
            }
            .setNegativeButton("Cancelar", null)
        if (r != null) b.setNeutralButton("Borrar") { _, _ -> commit(c.copy(reminders = c.reminders.filter { it.id != r.id })) }
        b.show()
    }

    private fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == packageName
    }

    private fun requestHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = svc<RoleManager>()
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME); return
            }
        }
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openStore() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
        catch (e: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
    }

    private fun versionName(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val c = cfg ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_TILE -> {
                val deleteId = data?.getStringExtra(TileEditorActivity.RES_DELETE)
                if (deleteId != null) { commit(c.copy(tiles = c.tiles.filter { it.id != deleteId })); return }
                val t = data?.getStringExtra(TileEditorActivity.RES_TILE)?.let { runCatching { Tile.fromJson(JSONObject(it)) }.getOrNull() } ?: return
                val idx = c.tiles.indexOfFirst { it.id == t.id }
                val tiles = c.tiles.toMutableList().apply { if (idx >= 0) set(idx, t) else if (size < LauncherConfig.MAX_TILES) add(t) }
                commit(c.copy(tiles = tiles))
            }
            REQ_PIN -> {
                val salt = data?.getStringExtra(PinActivity.RES_SALT) ?: return
                val hash = data.getStringExtra(PinActivity.RES_HASH) ?: return
                commit(c.copy(pinSalt = salt, pinHash = hash))
                toast("PIN guardado")
            }
            REQ_CONTACT_PERSON -> {
                val (name, phone) = Contacts.read(this, data?.data) ?: return
                personName?.setText(name.take(30)); personPhone?.setText(Phone.clean(phone))
            }
            REQ_HOME -> render()
        }
    }

    companion object {
        const val EXTRA_DEVICE = "device"
        const val EXTRA_NAME = "name"
        private const val REQ_TILE = 1
        private const val REQ_PIN = 2
        private const val REQ_CONTACT_PERSON = 3
        private const val REQ_HOME = 4
    }
}

object Contacts {
    data class Picked(val name: String, val phone: String, val photoUri: String?)

    fun readFull(act: android.app.Activity, uri: Uri?): Picked? {
        if (uri == null) return null
        return try {
            act.contentResolver.query(uri, arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ), null, null, null)?.use { c ->
                if (!c.moveToFirst()) null else Picked(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2))
            }
        } catch (e: Exception) { null }
    }

    fun read(act: android.app.Activity, uri: Uri?): Pair<String, String>? = readFull(act, uri)?.let { it.name to it.phone }
}
