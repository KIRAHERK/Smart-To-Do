package io.github.kiraherk.smarttodo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.CategoryKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = SmartTodoApp::class)
class MaintenanceTest {
    @Test
    fun aNameSavedWithTheAiThinkingInItIsRepairedAfterTheUpdate() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<SmartTodoApp>().container
        val broken = c.repo.createTask(
            TaskRepository.NewTask(
                "...نضعها WORK. Wait, let's look at the title: الاتصال باحمد شماط. Category: WORK. Date: 2026-11-12. Time: 12:00. Reminder: true. Priority: NORMAL",
                category = CategoryKey.WORK,
            ),
        )
        val fine = c.repo.createTask(TaskRepository.NewTask("Wait for the shipment", category = CategoryKey.WORK))

        c.maintenance.run()

        assertEquals("الاتصال باحمد شماط", c.db.tasks().get(broken)!!.title)
        assertEquals("Wait for the shipment", c.db.tasks().get(fine)!!.title)
    }
}
