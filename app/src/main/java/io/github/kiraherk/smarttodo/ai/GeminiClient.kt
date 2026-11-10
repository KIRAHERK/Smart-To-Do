package io.github.kiraherk.smarttodo.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoKey : AiException("No Gemini API key")
    class InvalidKey(message: String) : AiException(message)
    class Quota(val model: String?, message: String) : AiException(message)
    class ModelUnavailable(val model: String?, message: String) : AiException(message)
    class BadRequest(val model: String?, message: String) : AiException(message)
    class Server(val code: Int, message: String) : AiException(message)
    class Network(cause: Throwable) : AiException(cause.message ?: "network error", cause)
    class Blocked(reason: String) : AiException(reason)
    class BadResponse(message: String) : AiException(message)
}

/**
 * Minimal client for the Gemini Developer API (REST, v1beta) with an API key from Google AI Studio.
 * Docs: https://ai.google.dev/api
 */
class GeminiClient(
    private val http: OkHttpClient,
    private val apiKey: () -> String?,
    private val baseUrl: () -> String = { DEFAULT_BASE_URL },
) {
    data class Turn(val role: String, val text: String) {
        companion object {
            fun user(text: String) = Turn("user", text)
            fun model(text: String) = Turn("model", text)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Ids (without the "models/" prefix) of every model that supports generateContent for this key. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val key = requireKey()
        val ids = mutableListOf<String>()
        var pageToken: String? = null
        var pages = 0
        do {
            val url = "${baseUrl()}/models?pageSize=200" + (pageToken?.let { "&pageToken=$it" } ?: "")
            val body = execute(Request.Builder().url(url).header(KEY_HEADER, key).get().build(), model = null)
            val obj = json.parseToJsonElement(body).jsonObject
            obj["models"]?.jsonArray?.forEach { element ->
                val m = element.jsonObject
                val name = m["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") ?: return@forEach
                val methods = m["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                if ("generateContent" in methods) ids += name
            }
            pageToken = obj["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (!pageToken.isNullOrBlank() && ++pages < 10)
        ids
    }

    suspend fun generate(
        model: String,
        system: String,
        turns: List<Turn>,
        responseSchema: JsonObject? = null,
        temperature: Double = 0.2,
    ): String = withContext(Dispatchers.IO) {
        val key = requireKey()
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
            putJsonArray("contents") {
                turns.forEach { turn ->
                    addJsonObject {
                        put("role", turn.role)
                        putJsonArray("parts") { addJsonObject { put("text", turn.text) } }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                if (responseSchema != null) {
                    put("responseMimeType", "application/json")
                    put("responseSchema", responseSchema)
                }
            }
        }
        val request = Request.Builder()
            .url("${baseUrl()}/models/$model:generateContent")
            .header(KEY_HEADER, key)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        extractText(execute(request, model))
    }

    private fun requireKey(): String = apiKey()?.takeIf { it.isNotBlank() } ?: throw AiException.NoKey()

    private fun extractText(body: String): String {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw AiException.BadResponse("not json") }
        val candidates = obj["candidates"]?.jsonArray
        if (candidates.isNullOrEmpty()) {
            val reason = obj["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            throw AiException.Blocked(reason ?: "no candidates")
        }
        val first = candidates[0].jsonObject
        val parts = first["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val text = parts
            .filter { it.jsonObject["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        if (text.isBlank()) throw AiException.Blocked(first["finishReason"]?.jsonPrimitive?.contentOrNull ?: "empty")
        return text
    }

    private fun execute(request: Request, model: String?): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw AiException.Network(e)
        }
        response.use { r ->
            val body = r.body.string()
            if (r.isSuccessful) return body
            val message = runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "HTTP ${r.code}"
            throw when {
                r.code == 429 -> AiException.Quota(model, message)
                r.code == 404 -> AiException.ModelUnavailable(model, message)
                r.code == 401 || r.code == 403 -> AiException.InvalidKey(message)
                r.code == 400 && (body.contains("API_KEY_INVALID") || message.contains("API key", ignoreCase = true)) -> AiException.InvalidKey(message)
                r.code == 400 -> AiException.BadRequest(model, message)
                else -> AiException.Server(r.code, message)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val KEY_HEADER = "x-goog-api-key"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
