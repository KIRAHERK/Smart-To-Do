package io.github.kiraherk.smarttodo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelRankingTest {
    private val ids = listOf(
        "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-3.5-flash-lite", "gemini-3.6-flash",
        "gemini-3.6-flash-preview-tts", "gemini-2.5-pro", "gemma-3-27b-it", "gemini-flash-latest",
        "gemini-3.8-flash-image", "gemini-3.7-flash-preview", "text-embedding-004",
    )

    @Test
    fun commandsPreferNewestStableLite() {
        val ranked = ModelRanking.rank(ids, preferLite = true)
        assertEquals("gemini-3.5-flash-lite", ranked.first())
        assertEquals("gemini-2.5-flash-lite", ranked[1])
    }

    @Test
    fun questionsPreferNewestStableFlash() {
        val ranked = ModelRanking.rank(ids, preferLite = false)
        assertEquals("gemini-3.6-flash", ranked.first())
        // Preview models come after stable ones; lite models are the last fallback.
        assertEquals(listOf("gemini-3.6-flash", "gemini-2.5-flash", "gemini-flash-latest", "gemini-3.7-flash-preview"), ranked.take(4))
    }

    @Test
    fun excludesNonTextAndNonFlashModels() {
        val ranked = ModelRanking.rank(ids + listOf("gemini-omni-1.2-flash", "gemini-omni-flash-preview"), preferLite = false)
        listOf(
            "gemini-3.6-flash-preview-tts", "gemini-2.5-pro", "gemma-3-27b-it", "gemini-3.8-flash-image", "text-embedding-004",
            "gemini-omni-1.2-flash", "gemini-omni-flash-preview",
        ).forEach { assertFalse(it, it in ranked) }
    }

    @Test
    fun liteIsTheDefaultForEverything() {
        assertEquals(ModelRanking.rank(ids, preferLite = true), ModelRanking.rank(ids))
    }

    @Test
    fun gemini3RunsAtItsDefaultTemperature() {
        assertEquals(null, ModelRanking.temperatureFor("gemini-3.5-flash-lite", 0.1))
        assertEquals(null, ModelRanking.temperatureFor("gemini-flash-lite-latest", 0.1))
        assertEquals(0.1, ModelRanking.temperatureFor("gemini-2.5-flash-lite", 0.1)!!, 0.0)
    }

    @Test
    fun speechModelsFastestFirst() {
        val all = listOf(
            "gemini-3.1-flash-tts-preview", "gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts", "gemini-2.5-flash-preview-tts",
            "gemini-3.8-flash", "gemini-2.5-flash-native-audio-latest", "gemini-3.1-flash-live-preview",
        )
        assertEquals(
            listOf("gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts", "gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts"),
            ModelRanking.rankTts(all),
        )
    }
}
