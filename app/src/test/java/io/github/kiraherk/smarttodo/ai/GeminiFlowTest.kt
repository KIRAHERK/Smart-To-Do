package io.github.kiraherk.smarttodo.ai

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.MutableClock
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.data.settings.SettingsRepository
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.inMemoryDb
import io.github.kiraherk.smarttodo.testContext
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** The AI path end to end against a fake Gemini server: model choice, fallback, parsing, applying. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class GeminiFlowTest {
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var gemini: GeminiClient
    private val clock = MutableClock(Instant.parse("2026-11-10T08:00:00Z"), ZoneId.of("Asia/Riyadh"))

    private val models = """
        {"models":[
          {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent","countTokens"]},
          {"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.6-flash","supportedGenerationMethods":["generateContent"]},
          {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
        ]}
    """.trimIndent()

    private fun reply(text: String): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"candidates":[{"content":{"role":"model","parts":[{"text":"hmm","thought":true},{"text":"$escaped"}]},"finishReason":"STOP"}]}"""
    }

    @Before
    fun setUp() {
        server.start()
        db = inMemoryDb()
        settings = SettingsRepository(testContext())
        gemini = GeminiClient(OkHttpClient(), { "test-key" }, server.url("/v1beta").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun fallsBackToNextModelWhenFreeQuotaIsUsedUp() = runBlocking {
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""))
        server.enqueue(MockResponse().setBody(reply("hello")))

        val out = AiService(gemini, settings).generate(AiPurpose.COMMAND, "system", listOf(GeminiClient.Turn.user("hi")))
        assertEquals("hello", out)

        val list = server.takeRequest()
        assertTrue(list.path!!.startsWith("/v1beta/models"))
        assertEquals("test-key", list.getHeader("x-goog-api-key"))
        assertTrue(server.takeRequest().path!!.contains("/models/gemini-3.5-flash-lite:generateContent"))
        assertTrue(server.takeRequest().path!!.contains("/models/gemini-2.5-flash-lite:generateContent"))
    }

    @Test(expected = AiException.InvalidKey::class)
    fun invalidKeyIsReported(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        gemini.listModels()
        Unit
    }

    @Test
    fun spokenCommandBecomesARepeatingTaskWithReminder() = runBlocking {
        val actions = """{"actions":[{"type":"create_task","title":"الاطمئنان على أحمد","category":"PERSONAL",
            "reminder":true,"recurrence":{"frequency":"WEEKLY","interval":2,"weekdays":[]},
            "spokenSummary":"سأضيف مهمة: الاطمئنان على أحمد كل أسبوعين مع تذكير."}]}"""
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setBody(reply(actions)))

        val repo = TaskRepository(db, { "09:00" }, clock)
        val interpreter = CommandInterpreter(AiService(gemini, settings), ContextBuilder(db, repo), clock) { "ar" }
        val parsed = interpreter.interpret("ذكرني كل أسبوعين أطمن على أحمد")
        assertEquals(1, parsed.size)

        val request = server.takeRequest().let { server.takeRequest() }
        val body = request.body.readUtf8()
        assertTrue("schema must be sent", body.contains("\"responseSchema\""))
        assertTrue("the transcript must be sent", body.contains("ذكرني كل أسبوعين"))

        val result = ActionExecutor(repo).apply(parsed, "ذكرني كل أسبوعين أطمن على أحمد")
        assertEquals(1, result.applied)
        val task = db.tasks().all().single()
        assertEquals("الاطمئنان على أحمد", task.title)
        assertEquals(true, task.reminderEnabled)
        assertEquals(2, RecurrenceRule.fromJson(task.recurrenceJson)!!.interval)
        assertEquals("سأضيف مهمة: الاطمئنان على أحمد كل أسبوعين مع تذكير.", db.activity().all().single().text)
    }
}
