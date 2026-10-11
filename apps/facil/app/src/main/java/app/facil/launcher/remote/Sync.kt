package app.facil.launcher.remote

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.facil.launcher.core.Codes
import app.facil.launcher.core.DeviceStatus
import app.facil.launcher.core.LauncherConfig
import app.facil.launcher.core.Validate
import app.facil.launcher.data.ManagedDevice
import app.facil.launcher.data.Store
import org.json.JSONObject

/**
 * Esquema en Realtime Database (ver firebase/database.rules.json):
 *
 *   devices/{uidMayor}/owner           = uidMayor
 *   devices/{uidMayor}/name            = "Móvil de Carmen"
 *   devices/{uidMayor}/config          = { json, rev, by }
 *   devices/{uidMayor}/status          = { battery, charging, lastActive, lastSeen, done }
 *   devices/{uidMayor}/pairing         = { code, expiresAt }      (solo el dueño)
 *   devices/{uidMayor}/members/{uidF}  = { name, status: pending|active, code, createdAt }
 *   codes/{CODIGO}                     = { dev, expiresAt }
 *   users/{uidF}/devices/{uidMayor}    = true
 */
data class Member(val uid: String, val name: String, val active: Boolean)

/** Lado de la persona mayor. */
class SeniorSync(private val ctx: Context) {
    private val fb = Firebase.get(ctx)
    private val store = Store.get(ctx)

    private val me get() = fb.uid

    fun enableCloud() {
        fb.ensureSignedIn()
        fb.put("devices/$me/owner", me)
        fb.put("devices/$me/name", store.deviceName.ifBlank { "Móvil" })
        store.cloudEnabled = true
        pushConfig(store.config)
        pushStatus()
    }

    fun startPairing(): String {
        if (!store.cloudEnabled) enableCloud()
        fb.ensureSignedIn()
        val code = Codes.newCode()
        val expires = System.currentTimeMillis() + Codes.TTL_MS
        fb.put("codes/$code", JSONObject().put("dev", me).put("expiresAt", expires))
        fb.put("devices/$me/pairing", JSONObject().put("code", code).put("expiresAt", expires))
        return code
    }

    fun endPairing(code: String?) {
        try {
            if (code != null) fb.delete("codes/$code")
            fb.delete("devices/$me/pairing")
        } catch (e: Exception) { /* caduca solo */ }
    }

    fun members(): List<Member> {
        val o = fb.getObject("devices/$me/members") ?: JSONObject()
        val list = o.keys().asSequence().mapNotNull { k ->
            val m = o.optJSONObject(k) ?: return@mapNotNull null
            Member(k, Validate.text(m.optString("name"), 40).ifBlank { "Familiar" }, m.optString("status") == "active")
        }.toList()
        store.memberNames = list.filter { it.active }.map { it.name }
        return list
    }

    fun approve(uid: String) { fb.put("devices/$me/members/$uid/status", "active") }

    fun remove(uid: String) { fb.delete("devices/$me/members/$uid") }

    fun renameDevice(name: String) {
        store.deviceName = name
        if (store.cloudEnabled) fb.put("devices/$me/name", name.take(40))
    }

    fun pushConfig(c: LauncherConfig) {
        fb.put("devices/$me/config", JSONObject()
            .put("json", c.toJson().toString()).put("rev", c.rev).put("by", c.updatedBy))
    }

    /** Trae la configuración si la familia la ha cambiado, o sube la local si es más nueva. */
    fun syncConfig(): Boolean {
        if (!store.cloudEnabled) return false
        val remote = fb.getObject("devices/$me/config")
        val local = store.config
        val remoteRev = remote?.optLong("rev", -1) ?: -1
        return when {
            remote == null || remoteRev < local.rev -> { pushConfig(local); false }
            remoteRev > local.rev -> {
                val c = LauncherConfig.fromJsonString(remote.optString("json")) ?: return false
                store.config = c.copy(rev = remoteRev)
                true
            }
            else -> false
        }
    }

    fun pushStatus() {
        if (!store.cloudEnabled) return
        if (!store.shareStatus) {
            fb.delete("devices/$me/status")
            return
        }
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val done = JSONObject()
        val since = System.currentTimeMillis() - 2 * 86_400_000L
        store.remindersDone().filter { it.value > since }.forEach { (k, v) -> if (Validate.id(k) == k) done.put(k, v) }
        fb.put("devices/$me/status", JSONObject()
            .put("battery", pct).put("charging", plugged)
            .put("lastActive", store.lastActive)
            .put("sos", store.lastSos)
            .put("lastSeen", Firebase.SERVER_TIME)
            .put("done", done))
    }

    /** Borra todo lo que hay en la nube de este móvil y la cuenta anónima. */
    fun deleteEverything() {
        if (fb.configured && store.uid.isNotEmpty()) {
            try {
                fb.getObject("devices/$me/pairing")?.optString("code")?.let { c ->
                    if (Codes.normalize(c) == c) fb.delete("codes/$c")
                }
                fb.delete("devices/$me")
                fb.deleteAccount()
            } catch (e: FirebaseException) {
                if (e.code != 401 && e.code != 403) throw e
            }
        }
        store.clearCloud()
    }
}

data class RemoteDevice(
    val id: String,
    val name: String,
    val config: LauncherConfig?,
    val configRev: Long,
    val status: DeviceStatus,
)

/** Lado del familiar. */
class FamilySync(ctx: Context) {
    private val fb = Firebase.get(ctx)
    private val store = Store.get(ctx)
    private val me get() = fb.uid

    enum class JoinResult { OK, BAD_CODE, EXPIRED }

    /** Pide unirse; el móvil mayor tiene que aprobarlo. Devuelve el id del dispositivo. */
    fun join(code: String, myName: String): Pair<JoinResult, String?> {
        fb.ensureSignedIn()
        val c = fb.getObject("codes/$code") ?: return JoinResult.BAD_CODE to null
        val dev = c.optString("dev")
        if (!ID_RE.matches(dev)) return JoinResult.BAD_CODE to null
        if (c.optLong("expiresAt") < System.currentTimeMillis()) return JoinResult.EXPIRED to null
        try {
            fb.put("devices/$dev/members/$me", JSONObject()
                .put("name", myName.take(40)).put("status", "pending")
                .put("code", code).put("createdAt", Firebase.SERVER_TIME))
        } catch (e: FirebaseException) {
            if (e.code == 401 || e.code == 403) return JoinResult.EXPIRED to null
            throw e
        }
        fb.put("users/$me/devices/$dev", true)
        return JoinResult.OK to dev
    }

    /** "active", "pending" o null si nos han quitado. */
    fun memberStatus(dev: String): String? = fb.get("devices/$dev/members/$me/status") as? String

    fun fetch(dev: String): RemoteDevice? {
        val o = try { fb.getObject("devices/$dev") } catch (e: FirebaseException) {
            if (e.code == 401 || e.code == 403) return null else throw e
        } ?: return null
        val cfg = o.optJSONObject("config")
        val st = o.optJSONObject("status")
        val done = st?.optJSONObject("done")
        return RemoteDevice(
            id = dev,
            name = Validate.text(o.optString("name"), 40).ifBlank { "Móvil" },
            config = LauncherConfig.fromJsonString(cfg?.optString("json")),
            configRev = cfg?.optLong("rev", 0) ?: 0,
            status = DeviceStatus(
                battery = st?.optInt("battery", -1) ?: -1,
                charging = st?.optBoolean("charging") ?: false,
                lastActive = st?.optLong("lastActive") ?: 0,
                lastSeen = st?.optLong("lastSeen") ?: 0,
                lastSos = st?.optLong("sos") ?: 0,
                remindersDone = done?.keys()?.asSequence()?.associateWith { done.optLong(it) } ?: emptyMap(),
            ),
        )
    }

    fun pushConfig(dev: String, c: LauncherConfig): LauncherConfig {
        val remoteRev = (fb.get("devices/$dev/config/rev") as? Number)?.toLong() ?: 0
        val next = c.copy(rev = maxOf(remoteRev, c.rev) + 1, updatedAt = System.currentTimeMillis(),
            updatedBy = store.myName.ifBlank { "Familia" })
        fb.put("devices/$dev/config", JSONObject()
            .put("json", next.toJson().toString()).put("rev", next.rev).put("by", next.updatedBy))
        return next
    }

    fun leave(dev: String) {
        try { fb.delete("devices/$dev/members/$me") } catch (e: FirebaseException) { /* ya no éramos miembros */ }
        fb.delete("users/$me/devices/$dev")
        store.devices = store.devices.filter { it.id != dev }
    }

    fun addLocal(dev: String, name: String) {
        store.devices = store.devices.filter { it.id != dev } + ManagedDevice(dev, name)
    }

    fun deleteEverything() {
        if (fb.configured && store.uid.isNotEmpty()) {
            store.devices.forEach { try { leave(it.id) } catch (e: Exception) { } }
            try {
                fb.delete("users/$me")
                fb.deleteAccount()
            } catch (e: FirebaseException) {
                if (e.code != 401 && e.code != 403) throw e
            }
        }
        store.clearCloud()
    }

    companion object {
        val ID_RE = Regex("^[A-Za-z0-9]{10,64}$")
    }
}
