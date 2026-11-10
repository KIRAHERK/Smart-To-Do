package io.github.kiraherk.smarttodo.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.MutableClock
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.ActivityType
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.inMemoryDb
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

/** Runs on Android 8.0 and 10 (the range of his Galaxy A9) and a current version. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 29, 35], application = Application::class)
class TaskRepositoryTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    // Tuesday 10 November 2026, 11:00 local time.
    private val clock = MutableClock(Instant.parse("2026-11-10T08:00:00Z"), zone)
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private var reminderSyncs = 0

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = TaskRepository(db, defaultReminderTime = { "09:00" }, clock = clock, onRemindersChanged = { reminderSyncs++ })
    }

    @After
    fun tearDown() = db.close()

    private fun millis(date: String, h: Int, m: Int = 0) = LocalDate.parse(date).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun oneOffTaskWithReminder() = runBlocking {
        val id = repo.createTask(
            TaskRepository.NewTask("اتصل بالبنك", category = CategoryKey.WORK, date = LocalDate.parse("2026-11-11"), time = LocalTime.of(10, 0), reminder = true)
        )
        val o = repo.currentOccurrence(id)!!
        assertEquals("2026-11-11", o.date)
        assertEquals("10:00", o.time)
        assertEquals(millis("2026-11-11", 10), o.remindAt)
        assertEquals(listOf(ActivityType.CREATED.name), db.activity().all().map { it.type })
        assertTrue(reminderSyncs > 0)
    }

    @Test
    fun reminderWithoutTimeUsesDefaultTime() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("دواء", date = LocalDate.parse("2026-11-12"), reminder = true))
        assertEquals(millis("2026-11-12", 9), repo.currentOccurrence(id)!!.remindAt)
    }

    @Test
    fun reminderInThePastDoesNotRingImmediately() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("اجتماع", date = LocalDate.parse("2026-11-10"), time = LocalTime.of(7, 0), reminder = true))
        assertNull(repo.currentOccurrence(id)!!.remindAt)
    }

    @Test
    fun everyTwoWeeksCreatesNextRoundWhenDone() = runBlocking {
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(1))
        val id = repo.createTask(TaskRepository.NewTask("الاطمئنان على أحمد", recurrence = rule, reminder = true))
        val first = repo.currentOccurrence(id)!!
        assertEquals("2026-11-16", first.date) // next Monday
        repo.setStatusForTask(id, TaskStatus.DONE, note = "كلمته وهو بخير")
        val rounds = db.occurrences().all().filter { it.taskId == id }.sortedBy { it.date }
        assertEquals(listOf("2026-11-16" to "DONE", "2026-11-30" to "NOT_STARTED"), rounds.map { it.date to it.status })
        assertEquals(millis("2026-11-30", 9), rounds[1].remindAt)
        assertEquals(1, db.notes().forTask(id).size)
    }

    @Test
    fun missedRoundsAreMarkedAndSkipped() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("رياضة", recurrence = RecurrenceRule(Frequency.DAILY)))
        clock.advance(Duration.ofDays(3))
        repo.rollForward()
        val rounds = db.occurrences().all().filter { it.taskId == id }.sortedBy { it.date }
        assertEquals(listOf("2026-11-10" to "MISSED", "2026-11-13" to "NOT_STARTED"), rounds.map { it.date to it.status })
        // Running it again changes nothing.
        repo.rollForward()
        assertEquals(2, db.occurrences().all().size)
    }

    @Test
    fun partlyDoneThenPostponed() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("تقرير المبيعات", date = LocalDate.parse("2026-11-10")))
        repo.setStatusForTask(id, TaskStatus.PARTIALLY_DONE, progress = 50, note = "بقي الجدول الأخير")
        assertEquals(50, repo.currentOccurrence(id)!!.progress)
        repo.reschedule(id, LocalDate.parse("2026-11-12"), null, postponed = true)
        val o = repo.currentOccurrence(id)!!
        assertEquals("2026-11-12", o.date)
        assertEquals(TaskStatus.PARTIALLY_DONE.name, o.status) // progress is kept
        assertTrue(db.activity().all().any { it.type == ActivityType.POSTPONED.name })
    }

    @Test
    fun changingRepeatMovesCurrentRound() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("زيارة المصنع", recurrence = RecurrenceRule(Frequency.WEEKLY, weekdays = listOf(1))))
        repo.changeRecurrence(id, RecurrenceRule(Frequency.MONTHLY, dayOfMonth = 1))
        assertEquals("2026-12-01", repo.currentOccurrence(id)!!.date)
        repo.changeRecurrence(id, null)
        assertNull(repo.task(id)!!.recurrenceJson)
    }

    @Test
    fun deleteKeepsHistoryAndHidesTask() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("شراء هدية", date = LocalDate.parse("2026-11-10")))
        repo.archive(id)
        assertTrue(db.occurrences().open(TaskStatus.OPEN).none { it.task.id == id })
        assertEquals(2, db.activity().all().size)
        repo.restore(id)
        assertTrue(db.occurrences().open(TaskStatus.OPEN).any { it.task.id == id })
    }

    @Test
    fun searchFindsNotesDespiteSpellingDifferences() = runBlocking {
        val id = repo.createTask(TaskRepository.NewTask("متابعة الشحنة", category = CategoryKey.WORK))
        repo.addNote(id, "اتصلت بأحمد بخصوص الدفع")
        repo.createTask(TaskRepository.NewTask("شراء خبز", category = CategoryKey.HOME))
        val hits = repo.search("متى كلمت أحمد؟")
        assertEquals(id, hits.first().task.id)
        assertTrue(hits.first().note!!.text.contains("بأحمد"))
    }
}
