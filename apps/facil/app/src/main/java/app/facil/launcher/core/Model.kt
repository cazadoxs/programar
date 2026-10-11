package app.facil.launcher.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuración completa del lanzador. Es lo único que se sincroniza con el
 * móvil del familiar: se guarda como un JSON en local y en la nube, con un
 * número de revisión creciente para saber cuál es la más reciente.
 *
 * Todo lo que llega de fuera (nube, otro móvil) pasa por [LauncherConfig.fromJson],
 * que recorta longitudes y descarta valores raros: un JSON malicioso o
 * corrupto no puede romper la app ni colar intents arbitrarios.
 */
data class Tile(
    val id: String,
    val type: String,
    val label: String,
    val phone: String = "",
    val pkg: String = "",
    /** Foto en JPEG pequeña codificada en base64 (sin prefijo data:). */
    val photo: String = "",
    /** Índice de color de la paleta; -1 = automático. */
    val color: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type).put("label", label)
        .put("phone", phone).put("pkg", pkg).put("photo", photo).put("color", color)

    val needsPhone get() = type == CALL || type == WHATSAPP || type == SMS_TO

    companion object {
        const val CALL = "call"
        const val WHATSAPP = "whatsapp"
        const val SMS_TO = "sms_to"
        const val CAMERA = "camera"
        const val GALLERY = "gallery"
        const val DIALER = "dialer"
        const val MESSAGES = "messages"
        const val APP = "app"
        const val FLASHLIGHT = "flashlight"
        const val ALL_APPS = "apps"
        const val TIME = "time"

        val TYPES = listOf(CALL, WHATSAPP, SMS_TO, CAMERA, GALLERY, DIALER, MESSAGES, APP, FLASHLIGHT, ALL_APPS, TIME)

        const val MAX_PHOTO_CHARS = 60_000

        fun fromJson(o: JSONObject): Tile? {
            val type = o.optString("type")
            if (type !in TYPES) return null
            val pkg = o.optString("pkg").take(200)
            if (type == APP && !Validate.isPackageName(pkg)) return null
            val photo = o.optString("photo")
            return Tile(
                id = Validate.id(o.optString("id")),
                type = type,
                label = Validate.text(o.optString("label"), 30),
                phone = Phone.clean(o.optString("phone")),
                pkg = if (type == APP) pkg else "",
                photo = if (photo.length <= MAX_PHOTO_CHARS && Validate.isBase64(photo)) photo else "",
                color = o.optInt("color", -1).coerceIn(-1, 7),
            )
        }
    }
}

data class Reminder(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = lunes … bit 6 = domingo. 127 = todos los días. */
    val days: Int = 127,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("hour", hour).put("minute", minute).put("days", days)

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    companion object {
        fun fromJson(o: JSONObject): Reminder? {
            val label = Validate.text(o.optString("label"), 40)
            if (label.isBlank()) return null
            val days = o.optInt("days", 127) and 127
            return Reminder(
                id = Validate.id(o.optString("id")),
                label = label,
                hour = o.optInt("hour", 9).coerceIn(0, 23),
                minute = o.optInt("minute", 0).coerceIn(0, 59),
                days = if (days == 0) 127 else days,
            )
        }
    }
}

data class LauncherConfig(
    val tiles: List<Tile>,
    /** Nombre de la persona mayor, para el saludo ("Buenos días, Carmen"). */
    val ownerName: String = "",
    val sosName: String = "",
    val sosPhone: String = "",
    val sos2Name: String = "",
    val sos2Phone: String = "",
    val theme: String = THEME_CREAM,
    val textScale: Float = 1f,
    val confirmCalls: Boolean = true,
    val speak: Boolean = false,
    val showAppsButton: Boolean = false,
    /** Apps visibles en "Todas las apps". Vacío = todas. */
    val allowedApps: List<String> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val pinSalt: String = "",
    val pinHash: String = "",
    val inactivityHours: Int = 12,
    val rev: Long = 0,
    val updatedAt: Long = 0,
    val updatedBy: String = "",
) {
    val hasPin get() = pinHash.isNotEmpty() && pinSalt.isNotEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("tiles", JSONArray().apply { tiles.forEach { put(it.toJson()) } })
        .put("ownerName", ownerName)
        .put("sosName", sosName).put("sosPhone", sosPhone)
        .put("sos2Name", sos2Name).put("sos2Phone", sos2Phone)
        .put("theme", theme).put("textScale", textScale.toDouble())
        .put("confirmCalls", confirmCalls).put("speak", speak)
        .put("showAppsButton", showAppsButton)
        .put("allowedApps", JSONArray(allowedApps))
        .put("reminders", JSONArray().apply { reminders.forEach { put(it.toJson()) } })
        .put("pinSalt", pinSalt).put("pinHash", pinHash)
        .put("inactivityHours", inactivityHours)
        .put("rev", rev).put("updatedAt", updatedAt).put("updatedBy", updatedBy)

    fun bumped(by: String, now: Long = System.currentTimeMillis()) =
        copy(rev = rev + 1, updatedAt = now, updatedBy = by.take(40))

    companion object {
        const val THEME_CREAM = "cream"
        const val THEME_NIGHT = "night"
        const val THEME_CONTRAST = "contrast"
        val THEMES = listOf(THEME_CREAM, THEME_NIGHT, THEME_CONTRAST)
        val TEXT_SCALES = listOf(1f, 1.2f, 1.4f)
        const val MAX_TILES = 8
        const val MAX_REMINDERS = 12
        const val MAX_JSON_CHARS = 400_000

        fun defaults(): LauncherConfig = LauncherConfig(
            tiles = listOf(
                Tile("t1", Tile.CALL, "Llamar", color = 0),
                Tile("t2", Tile.WHATSAPP, "WhatsApp", color = 1),
                Tile("t3", Tile.CAMERA, "Cámara", color = 2),
                Tile("t4", Tile.GALLERY, "Fotos", color = 3),
                Tile("t5", Tile.DIALER, "Teléfono", color = 4),
                Tile("t6", Tile.FLASHLIGHT, "Linterna", color = 5),
            ),
        )

        fun fromJsonString(s: String?): LauncherConfig? {
            if (s.isNullOrEmpty() || s.length > MAX_JSON_CHARS) return null
            return try { fromJson(JSONObject(s)) } catch (e: Exception) { null }
        }

        fun fromJson(o: JSONObject): LauncherConfig {
            val tiles = mutableListOf<Tile>()
            o.optJSONArray("tiles")?.let { a ->
                for (i in 0 until minOf(a.length(), MAX_TILES)) a.optJSONObject(i)?.let(Tile::fromJson)?.let(tiles::add)
            }
            val reminders = mutableListOf<Reminder>()
            o.optJSONArray("reminders")?.let { a ->
                for (i in 0 until minOf(a.length(), MAX_REMINDERS)) a.optJSONObject(i)?.let(Reminder::fromJson)?.let(reminders::add)
            }
            val allowed = mutableListOf<String>()
            o.optJSONArray("allowedApps")?.let { a ->
                for (i in 0 until minOf(a.length(), 300)) a.optString(i).takeIf(Validate::isPackageName)?.let(allowed::add)
            }
            val theme = o.optString("theme").takeIf { it in THEMES } ?: THEME_CREAM
            val scale = o.optDouble("textScale", 1.0).toFloat()
            val salt = o.optString("pinSalt")
            val hash = o.optString("pinHash")
            val pinOk = salt.length in 16..64 && hash.length in 32..128 && Validate.isBase64(salt) && Validate.isBase64(hash)
            return LauncherConfig(
                tiles = tiles.distinctBy { it.id },
                ownerName = Validate.text(o.optString("ownerName"), 30),
                sosName = Validate.text(o.optString("sosName"), 30),
                sosPhone = Phone.clean(o.optString("sosPhone")),
                sos2Name = Validate.text(o.optString("sos2Name"), 30),
                sos2Phone = Phone.clean(o.optString("sos2Phone")),
                theme = theme,
                textScale = TEXT_SCALES.minByOrNull { kotlin.math.abs(it - scale) } ?: 1f,
                confirmCalls = o.optBoolean("confirmCalls", true),
                speak = o.optBoolean("speak", false),
                showAppsButton = o.optBoolean("showAppsButton", false),
                allowedApps = allowed.distinct(),
                reminders = reminders.distinctBy { it.id },
                pinSalt = if (pinOk) salt else "",
                pinHash = if (pinOk) hash else "",
                inactivityHours = o.optInt("inactivityHours", 12).coerceIn(2, 72),
                rev = o.optLong("rev", 0).coerceAtLeast(0),
                updatedAt = o.optLong("updatedAt", 0),
                updatedBy = Validate.text(o.optString("updatedBy"), 40),
            )
        }
    }
}

object Validate {
    private val pkgRe = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val idRe = Regex("^[A-Za-z0-9_-]{1,40}$")
    private val b64Re = Regex("^[A-Za-z0-9+/=\\r\\n]*$")

    fun isPackageName(s: String) = s.length in 3..200 && pkgRe.matches(s)
    fun isBase64(s: String) = b64Re.matches(s)
    fun id(s: String): String = if (idRe.matches(s)) s else Ids.random(10)

    /** Quita caracteres de control y recorta. */
    fun text(s: String, max: Int): String =
        s.filter { !it.isISOControl() || it == ' ' }.trim().take(max)
}

object Ids {
    private val rnd = java.security.SecureRandom()
    private const val ALPHA = "abcdefghijkmnopqrstuvwxyz23456789"
    fun random(n: Int): String = buildString { repeat(n) { append(ALPHA[rnd.nextInt(ALPHA.length)]) } }
}
