package io.github.kiraherk.smarttodo.backup

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.MutableClock
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.data.settings.SettingsRepository
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.inMemoryDb
import io.github.kiraherk.smarttodo.testContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class BackupTest {
    @Test
    fun exportAndRestoreKeepsEverything() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-11-10T08:00:00Z"), ZoneId.of("Asia/Riyadh"))
        val source = inMemoryDb()
        val repo = TaskRepository(source, { "09:00" }, clock)
        val id = repo.createTask(TaskRepository.NewTask("الاطمئنان على أحمد", recurrence = RecurrenceRule(Frequency.WEEKLY, interval = 2), reminder = true))
        repo.addNote(id, "ملاحظة مهمة")
        repo.setStatusForTask(id, TaskStatus.DONE)

        val settings = SettingsRepository(testContext())
        val text = BackupManager(testContext(), source, settings) {}.let { it.encode(it.export()) }

        val target = inMemoryDb()
        var restoredHook = false
        val manager = BackupManager(testContext(), target, settings) { restoredHook = true }
        manager.restore(text)

        assertEquals(source.tasks().all(), target.tasks().all())
        assertEquals(source.occurrences().all(), target.occurrences().all())
        assertEquals(source.notes().all(), target.notes().all())
        assertEquals(source.activity().all(), target.activity().all())
        assertTrue(restoredHook)
        // A safety copy of the (empty) target was written before restoring.
        assertTrue(manager.latestFile() != null)
        source.close()
        target.close()
    }
}
