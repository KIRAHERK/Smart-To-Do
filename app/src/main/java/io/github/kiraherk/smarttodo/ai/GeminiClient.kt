package io.github.kiraherk.smarttodo.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoKey : AiException("No Gemini API key")
    class InvalidKey(message: String) : AiException(message)
    /**
     * The free limit was hit. [retryAfterMs] is Google's own "try again in" hint when it gives one;
     * [daily] = the limit for the whole day is used up (otherwise it is a per-minute limit).
     */
    class Quota(val model: String?, message: String, val retryAfterMs: Long? = null, val daily: Boolean = false) : AiException(message) {
        /** How long to leave this model alone. */
        val restMs: Long get() = retryAfterMs?.coerceIn(5_000L, 30 * 60_000L) ?: if (daily) 6 * 3_600_000L else 60_000L
    }
    class ModelUnavailable(val model: String?, message: String) : AiException(message)
    class BadRequest(val model: String?, message: String) : AiException(message)
    class Server(val code: Int, message: String) : AiException(message)
    class Network(cause: Throwable) : AiException(cause.message ?: "network error", cause)
    /** The model took too long (Google's servers are often slow when they are busy). */
    class Timeout(val model: String?) : AiException("timed out")
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

    /**
     * One generateContent request. It can be cancelled (the HTTP call is aborted too) and gives up
     * after [timeoutMs] with [AiException.Timeout].
     *
     * [temperature] null = the model's own default. Gemini 3 models are meant to run at their
     * default; low values can make them loop or ramble.
     */
    suspend fun generate(
        model: String,
        system: String,
        turns: List<Turn>,
        responseSchema: JsonObject? = null,
        temperature: Double? = null,
        maxOutputTokens: Int? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String {
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
                if (temperature != null) put("temperature", temperature)
                if (maxOutputTokens != null) put("maxOutputTokens", maxOutputTokens)
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
        return extractText(await(request, model, timeoutMs))
    }

    /** Audio from a speech model: the bytes and their MIME type (a WAV file or bare 16-bit samples). */
    class Audio(val bytes: ByteArray, val mimeType: String?)

    /**
     * Reads [text] aloud with one of Gemini's speech models ("…-tts") and a prebuilt voice such
     * as "Kore". [style] is a short spoken-style direction placed before the text.
     */
    suspend fun speech(model: String, text: String, voice: String, timeoutMs: Long): Audio {
        val key = requireKey()
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    // Only the words to say: any instruction placed here gets read out loud too.
                    putJsonArray("parts") { addJsonObject { put("text", text) } }
                }
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) } }
                }
            }
        }
        val request = Request.Builder()
            .url("${baseUrl()}/models/$model:generateContent")
            .header(KEY_HEADER, key)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val response = await(request, model, timeoutMs)
        val obj = runCatching { json.parseToJsonElement(response).jsonObject }.getOrElse { throw AiException.BadResponse("not json") }
        val inline = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.firstNotNullOfOrNull { it.jsonObject["inlineData"]?.jsonObject ?: it.jsonObject["inline_data"]?.jsonObject }
            ?: throw AiException.Blocked("no audio")
        val data = inline["data"]?.jsonPrimitive?.contentOrNull ?: throw AiException.Blocked("no audio")
        val mime = (inline["mimeType"] ?: inline["mime_type"])?.jsonPrimitive?.contentOrNull
        val bytes = runCatching { java.util.Base64.getDecoder().decode(data) }.getOrElse { throw AiException.BadResponse("bad audio") }
        return Audio(bytes, mime)
    }

    /** Runs the call on OkHttp's own threads so that cancelling the coroutine aborts the request. */
    private suspend fun await(request: Request, model: String?, timeoutMs: Long): String =
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(request)
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(mapFailure(e, model))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { handle(response, model) }
                    if (cont.isActive) cont.resumeWith(result.recoverCatching { e ->
                        throw if (e is IOException) mapFailure(e, model) else e
                    })
                }
            })
        }

    private fun mapFailure(e: IOException, model: String?): AiException =
        if (e is InterruptedIOException) AiException.Timeout(model) else AiException.Network(e)

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
        val call = http.newCall(request)
        call.timeout().timeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw mapFailure(e, model)
        }
        return handle(response, model)
    }

    private fun handle(response: Response, model: String?): String {
        response.use { r ->
            val body = r.body.string()
            if (r.isSuccessful) return body
            val message = runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "HTTP ${r.code}"
            throw when {
                r.code == 429 -> AiException.Quota(model, message, retryDelayMs(body), daily = body.contains("PerDay", ignoreCase = true))
                r.code == 404 -> AiException.ModelUnavailable(model, message)
                r.code == 401 || r.code == 403 -> AiException.InvalidKey(message)
                r.code == 400 && (body.contains("API_KEY_INVALID") || message.contains("API key", ignoreCase = true)) -> AiException.InvalidKey(message)
                r.code == 400 -> AiException.BadRequest(model, message)
                else -> AiException.Server(r.code, message)
            }
        }
    }

    companion object {
        /** Reads Google's "retryDelay": "34s" hint from an error body. */
        fun retryDelayMs(body: String): Long? =
            Regex("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"").find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 1000).toLong() }

        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val KEY_HEADER = "x-goog-api-key"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
