package app.facil.launcher.data

import android.content.Context
import android.content.SharedPreferences
import app.facil.launcher.core.LauncherConfig
import org.json.JSONArray
import org.json.JSONObject

/** Un móvil de persona mayor que este familiar gestiona. */
data class ManagedDevice(val id: String, val name: String)

/**
 * Almacenamiento local. Todo vive en SharedPreferences privadas de la app
 * (excluidas de la copia en la nube, ver backup_rules.xml); el token de
 * sesión de Firebase va además cifrado con el Keystore ([Secure]).
 */
class Store private constructor(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("facil", Context.MODE_PRIVATE)
    private val listeners = mutableSetOf<() -> Unit>()

    var role: String
        get() = p.getString("role", "") ?: ""
        set(v) = p.edit().putString("role", v).apply()

    val isSenior get() = role == ROLE_SENIOR
    val isFamily get() = role == ROLE_FAMILY

    var config: LauncherConfig
        get() = LauncherConfig.fromJsonString(p.getString("config", null)) ?: LauncherConfig.defaults()
        set(v) {
            p.edit().putString("config", v.toJson().toString()).apply()
            listeners.toList().forEach { it() }
        }

    /** Guarda un cambio hecho en este móvil (sube la revisión). */
    fun saveLocal(c: LauncherConfig, by: String = "este móvil") {
        config = c.copy(rev = maxOf(c.rev, config.rev)).bumped(by)
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /**
     * Compartir batería y actividad con la familia. Es decisión de la persona
     * mayor: vive solo en su móvil y la familia no puede cambiarlo a distancia.
     */
    var shareStatus: Boolean
        get() = p.getBoolean("share", true)
        set(v) = p.edit().putBoolean("share", v).apply()

    var askedCallPermission: Boolean
        get() = p.getBoolean("askedCall", false)
        set(v) = p.edit().putBoolean("askedCall", v).apply()

    /** Última vez que se pulsó el botón de ayuda (se comparte con la familia). */
    var lastSos: Long
        get() = p.getLong("lastSos", 0)
        set(v) = p.edit().putLong("lastSos", v).apply()

    // --- PIN: intentos fallidos ---
    var pinFailures: Int
        get() = p.getInt("pinFailures", 0)
        set(v) = p.edit().putInt("pinFailures", v).apply()
    var pinLastFailure: Long
        get() = p.getLong("pinLastFailure", 0)
        set(v) = p.edit().putLong("pinLastFailure", v).apply()

    // --- Actividad y recordatorios del móvil mayor ---
    var lastActive: Long
        get() = p.getLong("lastActive", 0)
        set(v) = p.edit().putLong("lastActive", v).apply()

    fun remindersDone(): MutableMap<String, Long> {
        val o = try { JSONObject(p.getString("remDone", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        return o.keys().asSequence().associateWith { o.optLong(it) }.toMutableMap()
    }
    fun markReminderDone(id: String, at: Long) {
        val m = remindersDone(); m[id] = at
        p.edit().putString("remDone", JSONObject(m as Map<*, *>).toString()).apply()
        listeners.toList().forEach { it() }
    }

    // --- Firebase (sesión anónima) ---
    var uid: String
        get() = p.getString("uid", "") ?: ""
        set(v) = p.edit().putString("uid", v).apply()
    var refreshTokenEnc: String
        get() = p.getString("rt", "") ?: ""
        set(v) = p.edit().putString("rt", v).apply()

    /** Móvil mayor: nombre visible para la familia. */
    var deviceName: String
        get() = p.getString("deviceName", "") ?: ""
        set(v) = p.edit().putString("deviceName", v.take(40)).apply()
    /** Móvil mayor: se ha emparejado alguna vez (hay que sincronizar). */
    var cloudEnabled: Boolean
        get() = p.getBoolean("cloud", false)
        set(v) = p.edit().putBoolean("cloud", v).apply()
    /** Móvil mayor: familiares activos (caché para mostrar el aviso de privacidad). */
    var memberNames: List<String>
        get() = (p.getString("members", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = p.edit().putString("members", v.joinToString("\n")).apply()

    // --- Familiar ---
    var myName: String
        get() = p.getString("myName", "") ?: ""
        set(v) = p.edit().putString("myName", v.take(40)).apply()

    var devices: List<ManagedDevice>
        get() {
            val a = try { JSONArray(p.getString("devices", "[]")) } catch (e: Exception) { JSONArray() }
            return (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { ManagedDevice(it.optString("id"), it.optString("name")) }
            }.filter { it.id.isNotEmpty() }
        }
        set(v) = p.edit().putString("devices", JSONArray().apply {
            v.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        }.toString()).apply()

    /** Familiar: claves de alertas ya notificadas (para no repetir). */
    var notifiedAlerts: Set<String>
        get() = p.getStringSet("alerts", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("alerts", v).apply()

    /** Familiar: la suscripción está activa (caché; se revalida con Play al abrir). */
    var premium: Boolean
        get() = p.getBoolean("premium", false)
        set(v) = p.edit().putBoolean("premium", v).apply()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    fun clearCloud() {
        p.edit().remove("uid").remove("rt").remove("cloud").remove("members").remove("devices").remove("alerts").apply()
    }

    companion object {
        const val ROLE_SENIOR = "senior"
        const val ROLE_FAMILY = "family"

        @Volatile private var instance: Store? = null
        fun get(ctx: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(ctx.applicationContext).also { instance = it }
        }
    }
}
