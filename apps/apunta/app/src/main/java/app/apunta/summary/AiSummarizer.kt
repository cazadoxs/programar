package app.apunta.summary

import app.apunta.settings.AiProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional cloud summary using the user's OWN API key. Only transcript TEXT is
 * sent (never audio), only after the user accepted the consent dialog, straight
 * to the provider they chose; Apunta runs no server and sees nothing.
 */
object AiSummarizer {
    class AiException(val kind: Kind, message: String? = null) : IOException(message) {
        enum class Kind { AUTH, RATE, NETWORK, SERVER, EMPTY }
    }

    private const val MAX_CHARS = 120_000

    fun prompt(lang: String?): String =
        "Eres un asistente que resume transcripciones de audio (clases, reuniones, entrevistas). " +
            "Escribe en el idioma de la transcripción${if (lang != null) " ($lang)" else ""}. " +
            "Devuelve: 1) un resumen de 3-5 frases, 2) puntos clave en viñetas, 3) tareas o decisiones si las hay. " +
            "No inventes nada que no esté en el texto. La transcripción puede tener errores de reconocimiento."

    fun summarize(provider: AiProvider, model: String, apiKey: String, transcript: String, lang: String?): String {
        val text = transcript.take(MAX_CHARS)
        val sys = prompt(lang)
        val m = model.ifBlank { provider.defaultModel }
        return when (provider) {
            AiProvider.ANTHROPIC -> post(
                "https://api.anthropic.com/v1/messages",
                mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
                JSONObject().put("model", m).put("max_tokens", 1500).put("system", sys)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text))),
            ) { it.getJSONArray("content").getJSONObject(0).getString("text") }
            AiProvider.OPENAI -> post(
                "https://api.openai.com/v1/chat/completions",
                mapOf("Authorization" to "Bearer $apiKey"),
                JSONObject().put("model", m).put(
                    "messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", sys))
                        .put(JSONObject().put("role", "user").put("content", text))
                ),
            ) { it.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content") }
            AiProvider.GEMINI -> post(
                "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent",
                mapOf("x-goog-api-key" to apiKey),
                JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                    .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))))),
            ) { it.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text") }
            AiProvider.NONE -> throw AiException(AiException.Kind.EMPTY)
        }.trim().ifEmpty { throw AiException(AiException.Kind.EMPTY) }
    }

    /** Model ids end up in a URL path; allow only safe characters. */
    fun isSafeModelId(id: String) = Regex("^[A-Za-z0-9._-]{1,64}$").matches(id)

    private fun post(url: String, headers: Map<String, String>, body: JSONObject, parse: (JSONObject) -> String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                val txt = conn.inputStream.bufferedReader().use { it.readText() }
                return parse(JSONObject(txt))
            }
            when (code) {
                401, 403 -> throw AiException(AiException.Kind.AUTH)
                429 -> throw AiException(AiException.Kind.RATE)
                else -> throw AiException(AiException.Kind.SERVER, "HTTP $code")
            }
        } catch (e: AiException) {
            throw e
        } catch (e: org.json.JSONException) {
            throw AiException(AiException.Kind.SERVER, "bad response")
        } catch (e: IOException) {
            throw AiException(AiException.Kind.NETWORK)
        } finally {
            conn.disconnect()
        }
    }
}
