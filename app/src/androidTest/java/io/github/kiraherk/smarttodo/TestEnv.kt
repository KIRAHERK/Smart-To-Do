package io.github.kiraherk.smarttodo

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kiraherk.smarttodo.ai.GeminiClient
import io.github.kiraherk.smarttodo.util.LocaleHelper
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.Closeable
import java.io.File

/** Shared helpers for the emulator tests. */
object TestEnv {
    val app: SmartTodoApp get() = ApplicationProvider.getApplicationContext()
    val c: AppContainer get() = app.container

    fun str(@StringRes id: Int, vararg args: Any): String = LocaleHelper.wrap(app).getString(id, *args)

    /** Fresh state before each test: empty database, Arabic, no key, quiet. */
    fun reset(setupDone: Boolean = true) = runBlocking {
        LocaleHelper.setLanguage(app, "ar")
        c.db.clearAllTables()
        c.settings.setSetupDone(setupDone)
        c.settings.setCachedModels("")
        c.settings.setCommandModel("")
        c.settings.setAssistantModel("")
        c.settings.setReadAloud(false)
        c.secrets.setApiKey(null)
        c.geminiBaseUrl = GeminiClient.DEFAULT_BASE_URL
    }

    /** Saves a screenshot of the whole screen; the workflow pulls these and uploads them. */
    fun shot(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(app.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "api${Build.VERSION.SDK_INT}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 15_000) {
    waitUntil(timeoutMs) { onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.waitUntilTrue(timeoutMs: Long = 15_000, condition: () -> Boolean) = waitUntil(timeoutMs, condition)

/** A clickable element (button, tab, row) showing this text. */
fun ComposeTestRule.clickable(text: String): SemanticsNodeInteraction =
    onAllNodes(hasText(text) and hasClickAction()).onFirst()

fun ComposeTestRule.clickableDesc(description: String): SemanticsNodeInteraction =
    onAllNodes(hasContentDescription(description) and hasClickAction()).onFirst()

/** A stand-in for the Gemini API running on the device, so the AI flow can be tested without a key. */
class FakeGemini : Closeable {
    private val server = MockWebServer()
    @Volatile var commandReply: String = """{"actions":[]}"""
    @Volatile var assistantReply: String = "…"

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                return when {
                    path.contains(":generateContent") ->
                        MockResponse().setBody(reply(if (body.contains("responseSchema")) commandReply else assistantReply))
                    path.contains("/models") -> MockResponse().setBody(MODELS)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    val baseUrl: String = server.url("/v1beta").toString().trimEnd('/')

    override fun close() = server.shutdown()

    private fun reply(text: String): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"candidates":[{"content":{"role":"model","parts":[{"text":"$escaped"}]},"finishReason":"STOP"}]}"""
    }

    companion object {
        private const val MODELS = """{"models":[
            {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.6-flash","supportedGenerationMethods":["generateContent"]}]}"""
    }
}
