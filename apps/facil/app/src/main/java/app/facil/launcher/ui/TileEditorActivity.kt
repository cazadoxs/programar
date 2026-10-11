package app.facil.launcher.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import app.facil.launcher.core.Ids
import app.facil.launcher.core.Phone
import app.facil.launcher.core.Tile
import org.json.JSONObject

/** Crear o editar un botón de la pantalla de inicio. */
class TileEditorActivity : BaseActivity() {
    private var tile: Tile = Tile(Ids.random(8), Tile.CALL, "")
    private var isNew = true
    private lateinit var content: LinearLayout
    private var labelInput: EditText? = null
    private var phoneInput: EditText? = null
    private val remote get() = intent.getBooleanExtra(EXTRA_REMOTE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_TILE)?.let { s ->
            runCatching { Tile.fromJson(JSONObject(s)) }.getOrNull()?.let { tile = it; isNew = false }
        }
        savedInstanceState?.getString("tile")?.let { s -> runCatching { Tile.fromJson(JSONObject(s)) }.getOrNull()?.let { tile = it } }
        content = vbox()
        setRoot(vbox {
            addView(title(if (isNew) "Nuevo botón" else "Editar botón"))
            addView(scroll(content), lp(MATCH, 0, 1f))
        })
        if (isNew && savedInstanceState == null) chooseType() else render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        readInputs()
        outState.putString("tile", tile.toJson().toString())
    }

    private fun chooseType() {
        content.removeAllViews()
        content.addView(text("¿Qué hará este botón?", 22f, pal.text, MEDIUM).apply { setPadding(0, 0, 0, dp(10)) })
        content.addView(card {
            Tile.TYPES.forEachIndexed { i, type ->
                if (i > 0) addView(divider())
                addView(row(TileView.typeName(type), null, TileView.iconFor(type)) {
                    tile = tile.copy(type = type, label = if (type == Tile.CALL || type == Tile.SMS_TO) "" else TileView.defaultLabel(type),
                        phone = "", pkg = "", photo = "")
                    if (type == Tile.APP) pickApp() else render()
                })
            }
        })
        content.addView(space(12))
        content.addView(ghostButton("Cancelar") { finish() })
    }

    private fun readInputs() {
        labelInput?.let { tile = tile.copy(label = it.text.toString().trim().take(30)) }
        phoneInput?.let { tile = tile.copy(phone = Phone.clean(it.text.toString())) }
    }

    private fun render() {
        content.removeAllViews()
        labelInput = null; phoneInput = null

        // Vista previa del botón tal y como se verá
        content.addView(TileView(this, tile, if (tile.color >= 0) tile.color else 0).apply { isClickable = false },
            lp(MATCH, dp(190)).apply { bottomMargin = dp(16) })

        content.addView(text("Tipo: " + TileView.typeName(tile.type), 18f, pal.sub))
        if (isNew) content.addView(text("Cambiar tipo", 18f, pal.accent, BOLD).apply {
            setPadding(0, dp(4), 0, dp(10)); isClickable = true; setOnClickListener { readInputs(); chooseType() }
        })

        if (tile.needsPhone) {
            content.addView(button("Elegir de la agenda") {
                readInputs()
                try { startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT) }
                catch (e: Exception) { toast("No hay agenda de contactos") }
            }, lp().apply { topMargin = dp(8); bottomMargin = dp(12) })
        }

        content.addView(text("Nombre que se verá", 18f, pal.sub))
        labelInput = input(if (tile.needsPhone) "Por ejemplo: María (hija)" else "Nombre del botón", tile.label, max = 30)
        content.addView(labelInput, lp().apply { bottomMargin = dp(12) })

        if (tile.needsPhone) {
            content.addView(text(if (tile.type == Tile.WHATSAPP) "Teléfono (vacío = abrir WhatsApp)" else "Teléfono", 18f, pal.sub))
            phoneInput = input("612 345 678", tile.phone, InputType.TYPE_CLASS_PHONE, 20)
            content.addView(phoneInput, lp().apply { bottomMargin = dp(12) })
            content.addView(hbox {
                addView(ghostButton(if (tile.photo.isEmpty()) "Poner foto" else "Cambiar foto") { readInputs(); pickPhoto() }, lp(0, WRAP, 1f))
                if (tile.photo.isNotEmpty()) addView(ghostButton("Quitar foto") { readInputs(); tile = tile.copy(photo = ""); render() },
                    lp(0, WRAP, 1f).apply { marginStart = dp(10) })
            }, lp().apply { bottomMargin = dp(12) })
        }

        if (tile.type == Tile.APP) {
            content.addView(ghostButton("Elegir otra aplicación") { readInputs(); pickApp() }, lp().apply { bottomMargin = dp(8) })
            if (remote) content.addView(text("Si la aplicación no está instalada en su móvil, el botón no aparecerá.", 16f, pal.sub))
        }

        // Color
        content.addView(text("Color", 18f, pal.sub).apply { setPadding(0, dp(10), 0, dp(6)) })
        content.addView(hbox {
            for (i in 0 until 8) {
                val sw = View(this@TileEditorActivity).apply {
                    val selected = tile.color == i
                    background = rounded(pal.tile(i), dpf(24), if (selected) pal.text else pal.line, dp(if (selected) 4 else 1))
                    contentDescription = "Color ${i + 1}" + if (selected) ", elegido" else ""
                    setOnClickListener { readInputs(); tile = tile.copy(color = i); render() }
                }
                addView(sw, lp(0, dp(48), 1f).apply { if (i > 0) marginStart = dp(6) })
            }
        }, lp().apply { bottomMargin = dp(20) })

        content.addView(button("Guardar") { save() }, lp().apply { bottomMargin = dp(10) })
        if (!isNew) content.addView(button("Eliminar este botón", pal.sos, pal.onSos) {
            confirm("¿Eliminar este botón?", "Sí, eliminar", "No") {
                setResult(RESULT_OK, Intent().putExtra(RES_DELETE, tile.id)); finish()
            }
        }, lp().apply { bottomMargin = dp(10) })
        content.addView(ghostButton("Cancelar") { finish() })
    }

    private fun save() {
        readInputs()
        if (tile.needsPhone && tile.type != Tile.WHATSAPP && !Phone.isValid(tile.phone)) { toast("Falta un número de teléfono válido"); return }
        if (tile.type == Tile.APP && tile.pkg.isEmpty()) { toast("Elige una aplicación"); return }
        if (tile.label.isBlank()) tile = tile.copy(label = TileView.defaultLabel(tile.type))
        setResult(RESULT_OK, Intent().putExtra(RES_TILE, tile.toJson().toString()))
        finish()
    }

    private fun pickPhoto() {
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
        else Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try { startActivityForResult(i, REQ_PHOTO) } catch (e: Exception) { toast("No hay galería de fotos") }
    }

    private fun pickApp() {
        startActivityForResult(Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK, true), REQ_APP)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQ_APP && tile.pkg.isEmpty()) chooseType()
            return
        }
        when (requestCode) {
            REQ_CONTACT -> {
                val p = Contacts.readFull(this, data.data) ?: return
                tile = tile.copy(label = p.name.take(30), phone = Phone.clean(p.phone))
                render()
                // La foto del contacto, si la hay, en segundo plano.
                p.photoUri?.let { u ->
                    bg({ Images.encodePicked(this, Uri.parse(u)) }) { r -> r.getOrNull()?.let { tile = tile.copy(photo = it); readInputs(); render() } }
                }
            }
            REQ_PHOTO -> {
                val uri = data.data ?: return
                bg({ Images.encodePicked(this, uri) }) { r ->
                    val b64 = r.getOrNull()
                    if (b64 == null) toast("No se ha podido usar esa foto") else { tile = tile.copy(photo = b64); render() }
                }
            }
            REQ_APP -> {
                val pkg = data.getStringExtra(AppsActivity.RES_PKG) ?: return
                tile = tile.copy(type = Tile.APP, pkg = pkg, label = (data.getStringExtra(AppsActivity.RES_LABEL) ?: "").take(30))
                render()
            }
        }
    }

    companion object {
        const val EXTRA_TILE = "tile"
        const val EXTRA_REMOTE = "remote"
        const val RES_TILE = "tile"
        const val RES_DELETE = "delete"
        private const val REQ_CONTACT = 1
        private const val REQ_PHOTO = 2
        private const val REQ_APP = 3
    }
}
