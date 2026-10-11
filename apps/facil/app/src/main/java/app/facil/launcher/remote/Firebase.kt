package app.facil.launcher.remote

import android.content.Context
import app.facil.launcher.R
import app.facil.launcher.data.Secure
import app.facil.launcher.data.Store
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FirebaseException(val code: Int, msg: String) : IOException(msg)

/**
 * Cliente mínimo de Firebase por REST: Auth anónima + Realtime Database.
 * Sin SDK de Google para que la app pese poco y se pueda auditar entera.
 *
 * - Solo HTTPS (el manifiesto prohíbe tráfico en claro).
 * - El id token vive en memoria; el refresh token se guarda cifrado.
 * - La seguridad real está en las reglas de la base de datos
 *   (firebase/database.rules.json): cada usuario solo lee/escribe lo suyo.
 *
 * Todas las llamadas son bloqueantes: llamarlas siempre desde un hilo de fondo.
 */
class Firebase private constructor(private val ctx: Context) {
    private val apiKey = ctx.getString(R.string.firebase_api_key).trim()
    private val dbUrl = ctx.getString(R.string.firebase_db_url).trim().trimEnd('/')
    private val store = Store.get(ctx)

    @Volatile private var idToken: String = ""
    @Volatile private var idTokenExpiry: Long = 0

    val configured: Boolean
        get() = apiKey.isNotEmpty() && dbUrl.startsWith("https://") &&
            (dbUrl.endsWith(".firebaseio.com") || dbUrl.endsWith(".firebasedatabase.app"))

    val uid: String get() = store.uid

    @Synchronized
    fun ensureSignedIn(): String {
        if (!configured) throw FirebaseException(0, "Firebase no está configurado")
        val now = System.currentTimeMillis()
        if (idToken.isNotEmpty() && now < idTokenExpiry - 60_000) return idToken
        val refresh = Secure.decrypt(store.refreshTokenEnc)
        if (refresh.isNotEmpty()) {
            try {
                val r = post(
                    "https://securetoken.googleapis.com/v1/token?key=${enc(apiKey)}",
                    "grant_type=refresh_token&refresh_token=${enc(refresh)}",
                    "application/x-www-form-urlencoded",
                )
                setSession(r.getString("user_id"), r.getString("id_token"), r.getString("refresh_token"), r.optString("expires_in", "3600"))
                return idToken
            } catch (e: FirebaseException) {
                // Token revocado o cuenta borrada: si el uid cambia, perdemos el
                // acceso a lo anterior, así que solo creamos una cuenta nueva
                // cuando no había nada que perder (400 = token inválido).
                if (e.code != 400) throw e
            }
        }
        val r = post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${enc(apiKey)}",
            JSONObject().put("returnSecureToken", true).toString(),
            "application/json",
        )
        setSession(r.getString("localId"), r.getString("idToken"), r.getString("refreshToken"), r.optString("expiresIn", "3600"))
        return idToken
    }

    private fun setSession(uid: String, token: String, refresh: String, expiresIn: String) {
        store.uid = uid
        store.refreshTokenEnc = Secure.encrypt(refresh)
        idToken = token
        idTokenExpiry = System.currentTimeMillis() + (expiresIn.toLongOrNull() ?: 3600) * 1000
    }

    /** Borra la cuenta anónima en Firebase Auth (derecho de supresión). */
    fun deleteAccount() {
        if (!configured || store.uid.isEmpty()) return
        val token = ensureSignedIn()
        post(
            "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${enc(apiKey)}",
            JSONObject().put("idToken", token).toString(),
            "application/json",
        )
        idToken = ""
        idTokenExpiry = 0
    }

    // --- Realtime Database ---

    fun get(path: String): Any? {
        val body = request("GET", path, null)
        return if (body.isBlank() || body == "null") null else org.json.JSONTokener(body).nextValue()
    }

    fun getObject(path: String): JSONObject? = get(path) as? JSONObject

    fun put(path: String, value: Any) { request("PUT", path, JSONObject.wrap(value)?.toString() ?: "null") }
    fun patch(path: String, value: JSONObject) { request("PATCH", path, value.toString()) }
    fun delete(path: String) { request("DELETE", path, null) }

    private fun request(method: String, path: String, body: String?): String {
        require(PATH_RE.matches(path)) { "ruta no válida" }
        val token = ensureSignedIn()
        val url = "$dbUrl/$path.json?auth=${enc(token)}"
        return http(method, url, body, "application/json")
    }

    private fun post(url: String, body: String, type: String): JSONObject = JSONObject(http("POST", url, body, type))

    private fun http(method: String, url: String, body: String?, type: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.useCaches = false
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "$type; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { s -> s.readBytesLimited(MAX_RESPONSE).toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) throw FirebaseException(code, "HTTP $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("respuesta demasiado grande")
        }
        return out.toByteArray()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val MAX_RESPONSE = 2_000_000
        /** Solo segmentos con caracteres seguros: evita inyectar rutas o parámetros. */
        private val PATH_RE = Regex("^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*$")

        val SERVER_TIME: JSONObject get() = JSONObject().put(".sv", "timestamp")

        @Volatile private var instance: Firebase? = null
        fun get(ctx: Context): Firebase = instance ?: synchronized(this) {
            instance ?: Firebase(ctx.applicationContext).also { instance = it }
        }
    }
}
