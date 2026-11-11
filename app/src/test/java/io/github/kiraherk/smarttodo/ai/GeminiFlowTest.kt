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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

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
        // The settings store outlives a single test: start every test with no cached model list.
        runBlocking { settings.setCachedModels(""); settings.setCommandModel(""); settings.setAssistantModel("") }
        val base = server.url("/v1beta").toString().trimEnd('/')
        gemini = GeminiClient(OkHttpClient(), { "test-key" }, { base })
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
        assertEquals("hello", out.text)
        assertEquals("gemini-2.5-flash-lite", out.model)

        val list = server.takeRequest()
        assertTrue(list.path!!.startsWith("/v1beta/models"))
        assertEquals("test-key", list.getHeader("x-goog-api-key"))
        assertTrue(server.takeRequest().path!!.contains("/models/gemini-3.5-flash-lite:generateContent"))
        assertTrue(server.takeRequest().path!!.contains("/models/gemini-2.5-flash-lite:generateContent"))
    }

    @Test
    fun reasoningInTheTitleIsCaughtAndAskedAgainOnAnotherModel() = runBlocking {
        val leaked = """{"actions":[{"type":"create_task","title":"...نضعها WORK. Wait, let's look at the title: الاتصال باحمد شماط. Category: WORK. Date: 2026-11-12.","category":"WORK","date":"2026-11-12","time":"12:00","reminder":true,"spokenSummary":"سأضيف مهمة"}]}"""
        val clean = """{"actions":[{"type":"create_task","title":"الاتصال باحمد شماط","category":"WORK","date":"2026-11-12","time":"12:00","reminder":true,"spokenSummary":"سأضيف مهمة: الاتصال باحمد شماط غداً الساعة 12 ظهراً."}]}"""
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setBody(reply(leaked)))
        server.enqueue(MockResponse().setBody(reply(clean)))

        val repo = TaskRepository(db, { "09:00" }, clock)
        val parsed = CommandInterpreter(AiService(gemini, settings), ContextBuilder(db, repo), clock) { "ar" }
            .interpret("ذكرني بكرة الساعة 12 الظهر اتصل باحمد شماط")
        assertEquals("الاتصال باحمد شماط", parsed.single().title)

        server.takeRequest() // model list
        assertTrue(server.takeRequest().path!!.contains("gemini-3.5-flash-lite"))
        val second = server.takeRequest()
        assertTrue("the retry goes to another model", second.path!!.contains("gemini-2.5-flash-lite"))
        assertTrue(second.body.readUtf8().contains("rejected"))
    }

    @Test
    fun aDeletedTaskCanBeBroughtBackByVoice() = runBlocking {
        val repo = TaskRepository(db, { "09:00" }, clock)
        val id = repo.createTask(TaskRepository.NewTask("الاتصال باحمد شماط"))
        repo.archive(id)
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setBody(reply("""{"actions":[{"type":"restore_task","taskId":$id,"spokenSummary":"سأسترجع مهمة الاتصال باحمد شماط."}]}""")))

        val parsed = CommandInterpreter(AiService(gemini, settings), ContextBuilder(db, repo), clock) { "ar" }.interpret("رجّعها")
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue("deleted tasks must be listed for the AI", body.contains("RECENTLY DELETED TASKS") && body.contains("#$id | الاتصال باحمد شماط"))
        assertTrue(body.contains("restore_task"))

        assertEquals(1, ActionExecutor(repo).apply(parsed, "رجّعها").applied)
        assertEquals(false, db.tasks().get(id)!!.archived)
        assertEquals(listOf("CREATED", "ARCHIVED", "RESTORED"), db.activity().all().sortedBy { it.id }.map { it.type })
    }

    @Test
    fun ifTheRetryIsAlsoBrokenTheNameIsCleanedUp() = runBlocking {
        val leaked = """{"actions":[{"type":"create_task","title":"Wait, let's look at the title: الاتصال باحمد شماط. Category: WORK.","spokenSummary":"سأضيف مهمة"}]}"""
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setBody(reply(leaked)))
        server.enqueue(MockResponse().setBody(reply(leaked)))

        val repo = TaskRepository(db, { "09:00" }, clock)
        val parsed = CommandInterpreter(AiService(gemini, settings), ContextBuilder(db, repo), clock) { "ar" }
            .interpret("ذكرني اتصل باحمد شماط")
        assertEquals("الاتصال باحمد شماط", parsed.single().title)
    }

    @Test
    fun aSlowModelIsAbandonedAndTheNextOneAnswers() = runBlocking {
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setBody(reply("late")).setHeadersDelay(5, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody(reply("fast")))

        val started = System.nanoTime()
        val out = AiService(gemini, settings, perTryMs = 800).generate(AiPurpose.ASSISTANT, "system", listOf(GeminiClient.Turn.user("hi")))
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals("fast", out.text)
        assertTrue("took $tookMs ms", tookMs < 4_000)
    }

    @Test
    fun theWholeRequestHasATimeBudget() = runBlocking {
        server.enqueue(MockResponse().setBody(models))
        repeat(5) { server.enqueue(MockResponse().setBody(reply("late")).setHeadersDelay(5, TimeUnit.SECONDS)) }
        val started = System.nanoTime()
        val error = runCatching {
            AiService(gemini, settings, perTryMs = 1_000).generate(AiPurpose.COMMAND, "s", listOf(GeminiClient.Turn.user("hi")), budgetMs = 6_000)
        }.exceptionOrNull()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("$error", error is AiException.Timeout)
        assertTrue("took $tookMs ms", tookMs < 6_500)
    }

    @Test
    fun cancellingStopsTheRequestAtOnce() = runBlocking {
        server.enqueue(MockResponse().setBody(reply("late")).setHeadersDelay(10, TimeUnit.SECONDS))
        val started = System.nanoTime()
        val result = withTimeoutOrNull(500) { gemini.generate("gemini-3.5-flash-lite", "s", listOf(GeminiClient.Turn.user("hi"))) }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(null, result)
        assertTrue("took $tookMs ms", tookMs < 2_000)
    }

    @Test
    fun naturalVoiceAsksTheFastSpeechModelWithTheChosenVoice() = runBlocking {
        val withTts = models.substringBeforeLast("]}") +
            """,{"name":"models/gemini-3.8-flash-lite-tts","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-3.1-flash-tts-preview","supportedGenerationMethods":["generateContent"]}]}"""
        val audio = java.util.Base64.getEncoder().encodeToString(ByteArray(4800) { 1 })
        server.enqueue(MockResponse().setBody(withTts))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":429,"message":"Quota exceeded"}}"""))
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;codec=pcm;rate=24000","data":"$audio"}}]}}]}"""))

        val result = AiService(gemini, settings).speech("سأضيف مهمة", "Kore")
        val pcm = io.github.kiraherk.smarttodo.voice.Pcm.decode(result.bytes, result.mimeType)
        assertEquals(24_000, pcm.sampleRate)
        assertEquals(100, pcm.durationMs)

        server.takeRequest()
        assertTrue(server.takeRequest().path!!.contains("gemini-3.8-flash-lite-tts:generateContent"))
        val second = server.takeRequest()
        assertTrue(second.path!!.contains("gemini-3.1-flash-tts-preview:generateContent"))
        val body = second.body.readUtf8()
        assertTrue(body, body.contains("\"responseModalities\":[\"AUDIO\"]") && body.contains("\"voiceName\":\"Kore\"") && body.contains("سأضيف مهمة"))
        // Only the Arabic words: any instruction sent with them is read out loud too.
        assertTrue(body, body.contains("\"parts\":[{\"text\":\"سأضيف مهمة\"}]"))
    }

    @Test
    fun aPerMinuteLimitRestsTheVoiceBrieflyADailyLimitForHours() {
        val minute = """{"error":{"code":429,"details":[{"violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]},{"retryDelay":"34s"}]}}"""
        val daily = """{"error":{"code":429,"details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        assertEquals(34_000L, GeminiClient.retryDelayMs(minute))
        assertEquals(34_000L, AiException.Quota(null, "x", GeminiClient.retryDelayMs(minute), daily = false).restMs)
        assertEquals(null, GeminiClient.retryDelayMs(daily))
        assertEquals(6 * 3_600_000L, AiException.Quota(null, "x", null, daily = true).restMs)
        assertEquals(60_000L, AiException.Quota(null, "x").restMs)
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
        assertTrue("fields must come in a fixed order, title early", body.contains("\"propertyOrdering\":[\"type\",\"taskId\",\"title\""))
        assertTrue("the transcript must be sent", body.contains("ذكرني كل أسبوعين"))
        assertFalse("Gemini 3 runs at its own default temperature", body.contains("\"temperature\""))
        assertTrue("output is capped", body.contains("\"maxOutputTokens\":4096"))

        val result = ActionExecutor(repo).apply(parsed, "ذكرني كل أسبوعين أطمن على أحمد")
        assertEquals(1, result.applied)
        val task = db.tasks().all().single()
        assertEquals("الاطمئنان على أحمد", task.title)
        assertEquals(true, task.reminderEnabled)
        assertEquals(2, RecurrenceRule.fromJson(task.recurrenceJson)!!.interval)
        assertEquals("سأضيف مهمة: الاطمئنان على أحمد كل أسبوعين مع تذكير.", db.activity().all().single().text)
    }
}
