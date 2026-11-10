package io.github.kiraherk.smarttodo.ai

import io.github.kiraherk.smarttodo.data.settings.SettingsRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

enum class AiPurpose { COMMAND, ASSISTANT }

/**
 * Picks Gemini models from the live model list instead of hard-coding names, because Google
 * renames free models often. Commands prefer the newest "flash-lite" (largest free daily quota),
 * questions prefer the newest "flash". On quota/availability errors it falls back to the next model.
 */
object ModelRanking {
    private val excluded = listOf("image", "tts", "audio", "live", "embedding", "vision", "computer", "robotics", "thinking", "learnlm", "8b")

    /** Used only when the model list can't be fetched. */
    val FALLBACK = listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.5-flash")

    fun isUsable(id: String): Boolean {
        val s = id.lowercase()
        return s.startsWith("gemini") && "flash" in s && excluded.none { it in s }
    }

    fun version(id: String): Double =
        Regex("gemini-(\\d+(?:\\.\\d+)?)").find(id.lowercase())?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    fun isLite(id: String) = "lite" in id.lowercase()
    fun isPreview(id: String) = listOf("preview", "exp").any { it in id.lowercase() }

    fun rank(ids: List<String>, preferLite: Boolean): List<String> = ids.filter(::isUsable).distinct().sortedWith(
        compareBy<String> { if (isLite(it) == preferLite) 0 else 1 }
            .thenBy { if (isPreview(it)) 1 else 0 }
            .thenByDescending { version(it) }
            .thenBy { it.length }
    )
}

class AiService(
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private val listJson = Json { ignoreUnknownKeys = true }

    /** Fetches the live model list (needs a valid key) and caches it. */
    suspend fun refreshModels(): List<String> {
        val ids = gemini.listModels()
        settings.setCachedModels(listJson.encodeToString(ListSerializer(String.serializer()), ids))
        return ids
    }

    suspend fun cachedModels(): List<String> = settings.current().cachedModels.takeIf { it.isNotBlank() }
        ?.let { runCatching { listJson.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }
        .orEmpty()

    private suspend fun knownModels(): List<String> {
        cachedModels().takeIf { list -> list.any(ModelRanking::isUsable) }?.let { return it }
        return try {
            refreshModels().takeIf { list -> list.any(ModelRanking::isUsable) } ?: ModelRanking.FALLBACK
        } catch (e: AiException.InvalidKey) {
            throw e
        } catch (e: AiException.NoKey) {
            throw e
        } catch (e: AiException) {
            ModelRanking.FALLBACK
        }
    }

    suspend fun chain(purpose: AiPurpose): List<String> {
        val s = settings.current()
        val chosen = if (purpose == AiPurpose.COMMAND) s.commandModel else s.assistantModel
        val ranked = ModelRanking.rank(knownModels(), preferLite = purpose == AiPurpose.COMMAND)
        return (listOfNotNull(chosen.takeIf { it.isNotBlank() }) + ranked).distinct().take(5)
    }

    suspend fun generate(
        purpose: AiPurpose,
        system: String,
        turns: List<GeminiClient.Turn>,
        schema: JsonObject? = null,
        temperature: Double = 0.2,
    ): String {
        val chain = chain(purpose)
        val t = now()
        val usable = chain.filter { (cooldownUntil[it] ?: 0L) < t }.ifEmpty { chain }
        var last: AiException? = null
        for (model in usable) {
            try {
                return gemini.generate(model, system, turns, schema, temperature)
            } catch (e: AiException.Quota) {
                cooldownUntil[model] = now() + 15 * 60_000L
                last = e
            } catch (e: AiException.ModelUnavailable) {
                cooldownUntil[model] = now() + 24 * 3_600_000L
                last = e
            } catch (e: AiException.BadRequest) {
                last = e
            } catch (e: AiException.Server) {
                last = e
            } catch (e: AiException.Blocked) {
                last = e
            } catch (e: AiException.BadResponse) {
                last = e
            }
        }
        throw last ?: AiException.Quota(null, "no model available")
    }
}
