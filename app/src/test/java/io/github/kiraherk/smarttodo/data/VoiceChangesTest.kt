package io.github.kiraherk.smarttodo.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.MutableClock
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ai.ActionExecutor
import io.github.kiraherk.smarttodo.ai.AiAction
import io.github.kiraherk.smarttodo.ai.ContextBuilder
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.repo.CategoryRepository
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.inMemoryDb
import io.github.kiraherk.smarttodo.ui.screens.HistoryGrouping
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Changing anything by voice, and the history showing each task once with where it stands now. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class VoiceChangesTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    // Wednesday 25 November 2026, 11:00.
    private val clock = MutableClock(Instant.parse("2026-11-25T08:00:00Z"), zone)
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var exec: ActionExecutor

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = TaskRepository(db, defaultReminderTime = { "09:00" }, clock = clock, onRemindersChanged = {})
        exec = ActionExecutor(repo, CategoryRepository(db))
    }

    @After fun tearDown() = db.close()

    @Test
    fun aFinishedTaskWithoutADateIsStillKnownToTheAiAndCanBeReopened() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("شرب كاسة ماء"))
        repo.setStatusForTask(id, TaskStatus.DONE)
        val list = ContextBuilder(db, repo, { zone }).taskList()
        assertTrue(list, list.contains("#$id | شرب كاسة ماء") && list.contains("DONE"))

        exec.apply(listOf(AiAction(type = "set_status", taskId = id, status = "NOT_STARTED", spokenSummary = "x")), "رجع شرب الماء مش منجزة")
        assertEquals(TaskStatus.NOT_STARTED.name, repo.currentOccurrence(id)!!.status)
    }

    @Test
    fun yesterdayGoesToYesterdaysRoundOfARepeatingTask() = runBlocking {
        val id = repo.createTask(
            TaskRepository.NewTask("المشي نص ساعة", date = LocalDate.parse("2026-11-24"), recurrence = RecurrenceRule(Frequency.DAILY))
        )
        // Yesterday's round (never marked) and today's new one.
        repo.task(id)!!.let { repo.ensureCurrent(it) }
        val yesterday = db.occurrences().forTaskOn(id, "2026-11-24")!!
        val today = db.occurrences().forTaskOn(id, "2026-11-25")!!

        exec.apply(listOf(AiAction(type = "set_status", taskId = id, status = "DONE", spokenSummary = "x")), "المشي امبارح سويته")
        assertEquals(TaskStatus.DONE.name, db.occurrences().get(yesterday.id)!!.status)
        assertEquals(TaskStatus.NOT_STARTED.name, db.occurrences().get(today.id)!!.status)
    }

    @Test
    fun theTimeOrDayCanBeTakenOffByVoice() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("تصليح المكيف", date = LocalDate.parse("2026-11-27"), time = LocalTime.of(16, 0)))
        exec.apply(listOf(AiAction(type = "update_task", taskId = id, removeTime = true, spokenSummary = "x")), "شيل الوقت")
        repo.currentOccurrence(id)!!.let { assertEquals("2026-11-27", it.date); assertNull(it.time) }
        exec.apply(listOf(AiAction(type = "update_task", taskId = id, removeDate = true, spokenSummary = "y")), "بدون موعد")
        assertNull(repo.currentOccurrence(id)!!.date)
    }

    @Test
    fun historyShowsEachTaskOnceWithItsStatusNow() = runBlocking {
        val water = repo.createTask(TaskRepository.NewTask("شرب كاسة ماء"))
        clock.advance(Duration.ofMinutes(1)); repo.setStatusForTask(water, TaskStatus.DONE)
        clock.advance(Duration.ofMinutes(1)); repo.setStatusForTask(water, TaskStatus.NOT_DONE)
        clock.advance(Duration.ofMinutes(1)); repo.archive(water)
        clock.advance(Duration.ofMinutes(1)); repo.restore(water)
        val call = repo.createTask(TaskRepository.NewTask("الاتصال بأحمد"))
        clock.advance(Duration.ofMinutes(1)); repo.setStatusForTask(call, TaskStatus.IN_PROGRESS)

        val items = HistoryGrouping.group(repo.observeHistory(0).first()) { taskId, occ ->
            occ?.let { db.occurrences().get(it) } ?: repo.currentOccurrence(taskId)
        }
        assertEquals(listOf("الاتصال بأحمد", "شرب كاسة ماء"), items.map { it.task.title })
        assertEquals(TaskStatus.IN_PROGRESS, items[0].status)
        assertEquals(TaskStatus.NOT_DONE, items[1].status)
        assertEquals(4, items[1].earlier)
    }
}

/** Deleting from the History (press and hold, "clear all", or by voice) and the guide's "what's new". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class HistoryDeleteTest {
    private val clock = MutableClock(Instant.parse("2026-11-25T08:00:00Z"), ZoneId.of("Asia/Riyadh"))
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = TaskRepository(db, defaultReminderTime = { "09:00" }, clock = clock, onRemindersChanged = {})
    }

    @After fun tearDown() = db.close()

    @Test
    fun deletingLinesRemovesFinishedTasksButKeepsOpenOnes() = runBlocking {
        val done = repo.createTask(TaskRepository.NewTask("شرب كاسة ماء")).also { repo.setStatusForTask(it, TaskStatus.DONE) }
        val open = repo.createTask(TaskRepository.NewTask("الاتصال بأحمد"))
        val deleted = repo.createTask(TaskRepository.NewTask("تصليح المكيف")).also { repo.archive(it) }

        repo.removeFromHistory(listOf(done, open, deleted).map { TaskRepository.HistoryTarget(it) })

        assertNull(repo.task(done))
        assertNull(repo.task(deleted))
        // Still to do: it stays on Today, only its past entries are gone.
        assertEquals("الاتصال بأحمد", repo.task(open)!!.title)
        assertTrue(repo.observeHistory(0).first().isEmpty())
    }

    @Test
    fun deletingOneDayOfARepeatingTaskKeepsTheTask() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("المشي", date = LocalDate.parse("2026-11-24"), recurrence = RecurrenceRule(Frequency.DAILY)))
        repo.task(id)!!.let { repo.ensureCurrent(it) }
        val yesterday = db.occurrences().forTaskOn(id, "2026-11-24")!!
        repo.removeFromHistory(listOf(TaskRepository.HistoryTarget(id, yesterday.id)))
        assertNull(db.occurrences().get(yesterday.id))
        assertEquals("2026-11-25", repo.currentOccurrence(id)!!.date)
    }

    @Test
    fun clearingTheWholeHistoryByVoice() = runBlocking {
        repo.createTask(TaskRepository.NewTask("شرب كاسة ماء")).also { repo.setStatusForTask(it, TaskStatus.DONE) }
        val open = repo.createTask(TaskRepository.NewTask("الاتصال بأحمد"))
        val exec = ActionExecutor(repo, CategoryRepository(db))
        val result = exec.apply(listOf(AiAction(type = "clear_history", spokenSummary = "سأمسح السجل كله")), "امسح السجل كله")
        assertEquals(1, result.applied)
        assertEquals(listOf(open), db.tasks().all().map { it.id })
        assertTrue(repo.observeHistory(0).first().isEmpty())
    }

    @Test
    fun theGuideShowsEverythingOnceAndOnlyWhatIsNewAfterAnUpdate() {
        val all = io.github.kiraherk.smarttodo.ui.Guide.steps
        assertEquals(all, io.github.kiraherk.smarttodo.ui.Guide.stepsFor(null, "0.4.0"))
        val news = io.github.kiraherk.smarttodo.ui.Guide.stepsFor("0.3.1", "0.4.0")
        assertEquals(listOf(R.string.guide_chat_title, R.string.guide_history_title, R.string.guide_done_title), news.map { it.title })
        assertTrue(io.github.kiraherk.smarttodo.ui.Guide.stepsFor("0.4.0", "0.4.0").isEmpty())
        assertTrue(io.github.kiraherk.smarttodo.ui.Guide.stepsFor("0.4.0", "0.4.1").isEmpty())
    }
}
