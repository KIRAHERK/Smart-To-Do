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
 * renames free models often. The newest "flash-lite" comes first: it has the largest free daily
 * quota and answers in a second or two, while the newest big "flash" models are often overloaded
 * and can take half a minute or fail. On errors it falls back to the next model.
 */
object ModelRanking {
    private val excluded = listOf(
        "image", "tts", "audio", "live", "embedding", "vision", "computer", "robotics", "thinking", "learnlm", "8b", "omni",
    )

    /** Used only when the model list can't be fetched. */
    val FALLBACK = listOf("gemini-flash-lite-latest", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-flash-latest")

    fun isUsable(id: String): Boolean {
        val s = id.lowercase()
        return s.startsWith("gemini") && "flash" in s && excluded.none { it in s }
    }

    fun version(id: String): Double =
        Regex("gemini-(\\d+(?:\\.\\d+)?)").find(id.lowercase())?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    fun isLite(id: String) = "lite" in id.lowercase()
    fun isPreview(id: String) = listOf("preview", "exp").any { it in id.lowercase() }

    fun rank(ids: List<String>, preferLite: Boolean = true): List<String> = ids.filter(::isUsable).distinct().sortedWith(
        compareBy<String> { if (isLite(it) == preferLite) 0 else 1 }
            .thenBy { if (isPreview(it)) 1 else 0 }
            .thenByDescending { version(it) }
            .thenBy { it.length }
    )

    /**
     * Gemini 2.x models follow a low temperature well; Gemini 3 and later (and the "-latest"
     * aliases, which point to them) should run at their default, so null is returned for them.
     */
    fun temperatureFor(model: String, wanted: Double): Double? = version(model).takeIf { it > 0 && it < 3 }?.let { wanted }
}

/** What came back and which model wrote it. */
data class Generated(val text: String, val model: String)

class AiService(
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val perTryMs: Long = PER_TRY_MS,
) {
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private val lastGood = ConcurrentHashMap<AiPurpose, String>()
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

    /** His own choice first, then the model that worked last time, then the ranked list. */
    suspend fun chain(purpose: AiPurpose): List<String> {
        val s = settings.current()
        val chosen = if (purpose == AiPurpose.COMMAND) s.commandModel else s.assistantModel
        val ranked = ModelRanking.rank(knownModels())
        return (listOfNotNull(chosen.takeIf { it.isNotBlank() }, lastGood[purpose]) + ranked).distinct().take(MAX_MODELS)
    }

    /**
     * Asks the models in [chain] order until one answers. Each try is limited in time and the
     * whole request has a total budget, so he never waits more than about a minute.
     * Models that fail are rested for a while so the next request starts with one that works.
     */
    suspend fun generate(
        purpose: AiPurpose,
        system: String,
        turns: List<GeminiClient.Turn>,
        schema: JsonObject? = null,
        temperature: Double = 0.2,
        avoid: Set<String> = emptySet(),
        budgetMs: Long = if (purpose == AiPurpose.COMMAND) COMMAND_BUDGET_MS else ASSISTANT_BUDGET_MS,
    ): Generated {
        val chain = chain(purpose)
        val start = now()
        val usable = chain.filter { (cooldownUntil[it] ?: 0L) < start && it !in avoid }
            .ifEmpty { chain.filter { it !in avoid } }
            .ifEmpty { chain }
        var last: AiException? = null
        for (model in usable) {
            val left = budgetMs - (now() - start)
            if (left < MIN_TRY_MS) break
            try {
                val text = gemini.generate(
                    model, system, turns, schema,
                    temperature = ModelRanking.temperatureFor(model, temperature),
                    maxOutputTokens = MAX_OUTPUT_TOKENS,
                    timeoutMs = minOf(perTryMs, left),
                )
                lastGood[purpose] = model
                cooldownUntil.remove(model)
                return Generated(text, model)
            } catch (e: AiException.Quota) {
                rest(model, 15 * MINUTE, e).also { last = it }
            } catch (e: AiException.ModelUnavailable) {
                rest(model, 24 * 60 * MINUTE, e).also { last = it }
            } catch (e: AiException.Timeout) {
                rest(model, 10 * MINUTE, e).also { last = it }
            } catch (e: AiException.Server) {
                rest(model, 5 * MINUTE, e).also { last = it }
            } catch (e: AiException.BadRequest) {
                last = e
            } catch (e: AiException.Blocked) {
                last = e
            } catch (e: AiException.BadResponse) {
                last = e
            }
        }
        throw last ?: AiException.Timeout(null)
    }

    private fun rest(model: String, ms: Long, e: AiException): AiException {
        cooldownUntil[model] = now() + ms
        if (lastGood.values.contains(model)) lastGood.entries.removeIf { it.value == model }
        return e
    }

    companion object {
        private const val MINUTE = 60_000L
        const val MAX_MODELS = 5
        const val PER_TRY_MS = 25_000L
        const val MIN_TRY_MS = 4_000L
        const val COMMAND_BUDGET_MS = 45_000L
        const val ASSISTANT_BUDGET_MS = 55_000L
        /** Far more than any real answer needs; stops a model that rambles from running for minutes. */
        const val MAX_OUTPUT_TOKENS = 4096
    }
}
