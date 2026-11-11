package io.github.kiraherk.smarttodo

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.kiraherk.smarttodo.TestEnv.c
import io.github.kiraherk.smarttodo.TestEnv.reset
import io.github.kiraherk.smarttodo.TestEnv.shot
import io.github.kiraherk.smarttodo.TestEnv.str
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Runs the real app on an Android emulator (Android 8.0 and 10 in CI, the range of his Galaxy A9)
 * and uses it the way he would: tapping buttons and reading the screen.
 */
@RunWith(AndroidJUnit4::class)
class AppOnDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
    }

    private fun today() = LocalDate.now()

    private fun create(spec: TaskRepository.NewTask): Long = runBlocking { c.repo.createTask(spec) }

    private fun statusOf(taskId: Long): TaskStatus? = runBlocking { TaskStatus.parse(c.repo.currentOccurrence(taskId)?.status) }

    @Test
    fun firstLaunchShowsSetupThenTodayScreen() {
        reset(setupDone = false)
        launch()
        compose.waitForText(str(R.string.setup_title))
        shot("01-setup")
        // "Gemini key" opens Settings on top; back returns to the checklist.
        compose.clickable(str(R.string.open)).performScrollTo().performClick()
        compose.waitForText(str(R.string.gemini_key))
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForText(str(R.string.setup_title))
        compose.clickable(str(R.string.setup_finish)).performScrollTo().performClick()
        compose.waitForText(str(R.string.today_title))
        compose.waitForText(str(R.string.empty_today))
        shot("02-today-empty")
        assertTrue(runBlocking { c.settings.current().setupDone })
    }

    @Test
    fun todayListAndDoneButtonUpdateHistory() {
        reset()
        val late = create(TaskRepository.NewTask("تصليح المكيف", category = CategoryKey.HOME, date = today().minusDays(1)))
        create(TaskRepository.NewTask("اتصل بالبنك", category = CategoryKey.WORK, date = today(), time = LocalTime.of(10, 0)))
        create(TaskRepository.NewTask("حجز تذاكر دبي", category = CategoryKey.TRAVEL, date = today().plusDays(4)))
        launch()
        compose.waitForText("اتصل بالبنك")
        shot("03-today-tasks")

        // The first ✓ belongs to the late task (late tasks are listed first).
        compose.clickableDesc(str(R.string.mark_done)).performClick()
        compose.waitUntilTrue { statusOf(late) == TaskStatus.DONE }

        // The trip is further down the list: scroll to it like he would.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("حجز تذاكر دبي"))
        compose.waitForText("حجز تذاكر دبي")

        // Open a task, then the Today tab must show the list again (not the task).
        compose.clickable("حجز تذاكر دبي").performClick()
        compose.waitForText(str(R.string.add_voice_note))
        compose.clickableDesc(str(R.string.back)).performClick()
        compose.waitForText(str(R.string.today_title))

        compose.clickable(str(R.string.nav_history)).performClick()
        compose.waitForText(str(R.string.search_hint))
        compose.waitForText("تصليح المكيف")
        compose.waitForText(str(R.string.status_done))
        shot("04-history")
    }

    @Test
    fun taskDetailPartlyDoneWithPercentage() {
        reset()
        val id = create(TaskRepository.NewTask("تقرير المبيعات", category = CategoryKey.WORK, date = today()))
        launch()
        compose.waitForText("تقرير المبيعات")
        compose.clickable("تقرير المبيعات").performClick()
        compose.waitForText(str(R.string.add_voice_note))
        compose.clickable(str(R.string.status_partly)).performClick()
        compose.waitForText(str(R.string.how_much_done))
        compose.clickable("50%").performClick()
        compose.waitUntilTrue { runBlocking { c.repo.currentOccurrence(id)?.progress } == 50 }
        assertEquals(TaskStatus.PARTIALLY_DONE, statusOf(id))
        shot("05-task-detail")
    }

    @Test
    fun typedCommandIsUnderstoodConfirmedAndSaved() {
        reset()
        FakeGemini().use { gemini ->
            gemini.commandReply = """{"actions":[{"type":"create_task","title":"الاطمئنان على أحمد","category":"PERSONAL",
                "reminder":true,"recurrence":{"frequency":"WEEKLY","interval":2,"weekdays":[]},
                "spokenSummary":"سأضيف مهمة: الاطمئنان على أحمد كل أسبوعين مع تذكير."}]}"""
            c.geminiBaseUrl = gemini.baseUrl
            c.secrets.setApiKey("test-key")
            launch()
            compose.waitForText(str(R.string.today_title))
            compose.clickable(str(R.string.nav_assistant)).performClick()
            compose.onNode(hasSetTextAction()).performTextInput("ذكرني كل أسبوعين أطمن على أحمد")
            compose.clickableDesc(str(R.string.send)).performClick()

            compose.waitForText(str(R.string.confirm_title), timeoutMs = 30_000)
            compose.waitForText("سأضيف مهمة: الاطمئنان على أحمد", substring = true)
            shot("06-confirm")
            compose.clickable(str(R.string.yes_save)).performClick()
            compose.waitForText(str(R.string.saved_ok))
            compose.clickable(str(R.string.ok)).performClick()

            val task = runBlocking { c.db.tasks().all() }.single()
            assertEquals("الاطمئنان على أحمد", task.title)
            assertTrue(task.reminderEnabled)
            assertEquals(2, RecurrenceRule.fromJson(task.recurrenceJson)!!.interval)

            compose.clickable(str(R.string.nav_today)).performClick()
            compose.waitForText("الاطمئنان على أحمد")
            shot("07-today-after-command")
        }
    }

    @Test
    fun assistantAnswersAQuestion() {
        reset()
        FakeGemini().use { gemini ->
            gemini.assistantReply = "آخر مرة اتصلت بأحمد كانت يوم الثلاثاء، وتمت المهمة."
            c.geminiBaseUrl = gemini.baseUrl
            c.secrets.setApiKey("test-key")
            launch()
            compose.waitForText(str(R.string.today_title))
            compose.clickable(str(R.string.nav_assistant)).performClick()
            compose.clickable(str(R.string.suggest_today)).performClick()
            compose.waitForText("آخر مرة اتصلت بأحمد", substring = true, timeoutMs = 30_000)
            shot("08-assistant-answer")
            compose.clickable(str(R.string.ok)).performClick()
            assertEquals(2, runBlocking { c.db.chat().all() }.size)
        }
    }

    @Test
    fun withoutKeyWhatHeSaidGoesToTheInbox() {
        reset()
        launch()
        compose.waitForText(str(R.string.today_title))
        compose.clickable(str(R.string.nav_assistant)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("بكرة أروح للبنك")
        compose.clickableDesc(str(R.string.send)).performClick()
        compose.waitForText(str(R.string.saved_inbox_no_key))
        compose.clickable(str(R.string.ok)).performClick()
        assertEquals("بكرة أروح للبنك", runBlocking { c.db.inbox().all() }.single().text)
    }

    @Test
    fun reminderRingsAndDoneButtonWorks() {
        reset()
        val at = LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
        val id = create(
            TaskRepository.NewTask("موعد طبيب الأسنان", category = CategoryKey.PERSONAL, date = at.toLocalDate(), time = at.toLocalTime(), reminder = true)
        )
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.openNotification()
        assertTrue("reminder notification did not appear", device.wait(Until.hasObject(By.textContains("موعد طبيب الأسنان")), 200_000))
        shot("09-reminder")

        val doneLabel = str(R.string.action_done)
        var done = device.wait(Until.findObject(By.text(doneLabel)), 5_000)
        if (done == null) {
            // Collapsed notification: expand it to show the buttons.
            device.findObject(By.textContains("موعد طبيب الأسنان"))?.swipe(androidx.test.uiautomator.Direction.DOWN, 1f)
            done = device.wait(Until.findObject(By.text(doneLabel)), 5_000)
        }
        assertTrue("Done button not found on the reminder", done != null)
        done!!.click()
        val deadline = System.currentTimeMillis() + 15_000
        while (statusOf(id) != TaskStatus.DONE && System.currentTimeMillis() < deadline) Thread.sleep(300)
        assertEquals(TaskStatus.DONE, statusOf(id))
        device.pressBack()
    }

    @Test
    fun everyScreenOpensInArabicAndEnglish() {
        reset()
        create(TaskRepository.NewTask("زيارة المصنع", category = CategoryKey.WORK, date = today()))
        launch()
        compose.waitForText("زيارة المصنع")

        compose.clickableDesc(str(R.string.review_today)).performClick()
        compose.waitForText(str(R.string.evening_hint))
        shot("10-evening-review")
        compose.clickableDesc(str(R.string.back)).performClick()

        compose.clickableDesc(str(R.string.review_week)).performClick()
        compose.waitForText(str(R.string.weekly_hint))
        shot("11-weekly-review")
        compose.clickableDesc(str(R.string.back)).performClick()

        compose.clickable(str(R.string.nav_history)).performClick()
        compose.waitForText(str(R.string.search_hint))
        compose.clickable(str(R.string.nav_assistant)).performClick()
        compose.waitForText(str(R.string.assistant_empty))
        shot("12-assistant")

        compose.clickable(str(R.string.nav_settings)).performClick()
        compose.waitForText(str(R.string.gemini_key))
        shot("13-settings")
        compose.clickable(str(R.string.inbox_title)).performScrollTo().performClick()
        compose.waitForText(str(R.string.inbox_explain))
        compose.clickableDesc(str(R.string.back)).performClick()

        // Switch to English and back: the app must restart its screen without crashing.
        compose.clickable(str(R.string.app_language)).performScrollTo().performClick()
        compose.clickable("English").performClick()
        compose.waitForText("App language")
        shot("14-settings-english")
        compose.clickable("App language").performScrollTo().performClick()
        compose.clickable("العربية").performClick()
        compose.waitForText(str(R.string.settings_title))
    }
}
