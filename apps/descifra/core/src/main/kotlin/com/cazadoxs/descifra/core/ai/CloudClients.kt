package com.cazadoxs.descifra.core.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration

enum class CloudProvider(val label: String, val defaultModel: String, val models: List<String>, val keyHint: String, val consoleUrl: String) {
    ANTHROPIC(
        "Claude (Anthropic)",
        "claude-opus-5-5",
        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        "sk-ant-…",
        "https://console.anthropic.com/settings/keys",
    ),
    OPENAI("OpenAI", "gpt-5-mini", listOf("gpt-5-mini", "gpt-5"), "sk-…", "https://platform.openai.com/api-keys"),
    GOOGLE("Gemini (Google)", "gemini-2.5-flash", listOf("gemini-2.5-flash", "gemini-2.5-pro"), "AIza…", "https://aistudio.google.com/apikey"),
}

/** A user-facing failure; [message] is already in Spanish and safe to show. */
class CloudException(message: String, val retryable: Boolean = false) : Exception(message)

interface LlmClient {
    val providerLabel: String

    /** Returns the raw text answer. When [jsonSchema] is set the answer must be a JSON object. */
    fun complete(system: String, user: String, jsonSchema: Map<String, Any>? = null): String
}

object CloudClients {
    fun create(provider: CloudProvider, apiKey: String, model: String): LlmClient {
        require(apiKey.isNotBlank()) { "Falta la clave" }
        val m = model.ifBlank { provider.defaultModel }
        return when (provider) {
            CloudProvider.ANTHROPIC -> ClaudeClient(apiKey.trim(), m)
            CloudProvider.OPENAI -> OpenAiClient(apiKey.trim(), m)
            CloudProvider.GOOGLE -> GeminiClient(apiKey.trim(), m)
        }
    }
}

/** Claude through the official Anthropic Java SDK. */
class ClaudeClient(apiKey: String, private val model: String) : LlmClient {
    override val providerLabel = "Claude · $model"

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofSeconds(120))
        .maxRetries(2)
        .build()

    override fun complete(system: String, user: String, jsonSchema: Map<String, Any>?): String {
        val output = OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM)
        if (jsonSchema != null) {
            val schema = JsonOutputFormat.Schema.builder()
            jsonSchema.forEach { (k, v) -> schema.putAdditionalProperty(k, JsonValue.from(v)) }
            output.format(JsonOutputFormat.builder().schema(schema.build()).build())
        }
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            .addUserMessage(user)
            .outputConfig(output.build())
            .apply {
                // Server-side fallback when a safety classifier declines (Opus/Sonnet/Fable only).
                if (!model.contains("haiku")) {
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()

        val message = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw CloudException("La clave de Anthropic no es válida. Revísala en Ajustes.")
        } catch (e: PermissionDeniedException) {
            throw CloudException("Tu clave de Anthropic no tiene permiso para usar $model.")
        } catch (e: RateLimitException) {
            throw CloudException("Has superado el límite de uso de tu cuenta de Anthropic. Prueba en un rato.", retryable = true)
        } catch (e: AnthropicServiceException) {
            throw CloudException("Anthropic respondió con un error (${e.statusCode()}).", retryable = e.statusCode() >= 500)
        } catch (e: RuntimeException) {
            if (e.cause is IOException || e is java.io.UncheckedIOException) {
                throw CloudException("No hay conexión con Anthropic. Revisa tu internet.", retryable = true)
            }
            throw e
        }

        val stop = message.stopReason().map { it.toString() }.orElse("")
        if (stop.equals("refusal", ignoreCase = true)) {
            throw CloudException("Claude no ha podido analizar este texto. Prueba con el análisis en el móvil.")
        }
        val text = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
        if (text.isBlank()) throw CloudException("Claude devolvió una respuesta vacía.")
        return text
    }
}

/** Minimal JSON-over-HTTPS helper for the providers without an Anthropic SDK. */
internal object Http {
    val json = Json { ignoreUnknownKeys = true }

    fun postJson(url: String, headers: Map<String, String>, body: JsonObject, provider: String): JsonObject {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach(conn::setRequestProperty)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            when (code) {
                in 200..299 -> return json.parseToJsonElement(text).jsonObject
                400 -> throw CloudException("$provider rechazó la petición. Comprueba el nombre del modelo en Ajustes.")
                401, 403 -> throw CloudException("La clave de $provider no es válida o no tiene permiso.")
                404 -> throw CloudException("$provider no reconoce ese modelo. Cámbialo en Ajustes.")
                429 -> throw CloudException("Has superado el límite de uso de tu cuenta de $provider.", retryable = true)
                else -> throw CloudException("$provider respondió con un error ($code).", retryable = code >= 500)
            }
        } catch (e: IOException) {
            throw CloudException("No hay conexión con $provider. Revisa tu internet.", retryable = true)
        } finally {
            conn.disconnect()
        }
    }

    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is List<*> -> JsonArray(value.map(::toJson))
        else -> JsonPrimitive(value.toString())
    }
}

class OpenAiClient(private val apiKey: String, private val model: String) : LlmClient {
    override val providerLabel = "OpenAI · $model"

    override fun complete(system: String, user: String, jsonSchema: Map<String, Any>?): String {
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
            if (jsonSchema != null) {
                put("response_format", buildJsonObject {
                    put("type", "json_schema")
                    put("json_schema", buildJsonObject {
                        put("name", "analisis_carta")
                        put("strict", true)
                        put("schema", Http.toJson(jsonSchema))
                    })
                })
            }
        }
        val res = Http.postJson(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $apiKey"),
            body,
            "OpenAI",
        )
        return res["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?: throw CloudException("OpenAI devolvió una respuesta vacía.")
    }
}

class GeminiClient(private val apiKey: String, private val model: String) : LlmClient {
    override val providerLabel = "Gemini · $model"

    override fun complete(system: String, user: String, jsonSchema: Map<String, Any>?): String {
        require(Regex("[a-z0-9.\\-]+").matches(model)) { "Nombre de modelo no válido" }
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", user) }) })
                })
            })
            if (jsonSchema != null) {
                put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
            }
        }
        // The key goes in a header, never in the URL, so it cannot leak into logs or proxies.
        val res = Http.postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey),
            body,
            "Gemini",
        )
        val parts = res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
        val text = parts?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
        return text?.takeIf { it.isNotBlank() } ?: throw CloudException("Gemini devolvió una respuesta vacía o bloqueada.")
    }
}
