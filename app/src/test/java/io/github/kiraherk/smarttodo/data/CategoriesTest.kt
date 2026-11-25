package io.github.kiraherk.smarttodo.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.MutableClock
import io.github.kiraherk.smarttodo.ai.ActionExecutor
import io.github.kiraherk.smarttodo.ai.AiAction
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.repo.CategoryRepository
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.ActivityType
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.inMemoryDb
import io.github.kiraherk.smarttodo.testContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 29, 35], application = Application::class)
class CategoriesTest {
    private val clock = MutableClock(Instant.parse("2026-11-10T08:00:00Z"), ZoneId.of("Asia/Riyadh"))

    /** An update from 0.2.x (database version 1) keeps his tasks and gives him the four categories. */
    @Test
    fun updatingFromVersion1KeepsTasksAndAddsTheCategories() = runBlocking {
        val name = "migrate-test.db"
        val file = testContext().getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val schema = Json.parseToJsonElement(File("schemas/io.github.kiraherk.smarttodo.data.db.AppDatabase/1.json").readText())
            .jsonObject["database"]!!.jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            schema["entities"]!!.jsonArray.forEach { e ->
                val table = e.jsonObject["tableName"]!!.jsonPrimitive.content
                old.execSQL(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e.jsonObject["indices"]?.jsonArray?.forEach { i ->
                    old.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["views"]?.jsonArray?.forEach { v -> old.execSQL(v.jsonObject["createSql"]!!.jsonPrimitive.content) }
            schema["setupQueries"]!!.jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
            // A task saved by version 1 (every required column filled in).
            val tasks = schema["entities"]!!.jsonArray.first { it.jsonObject["tableName"]!!.jsonPrimitive.content == "tasks" }.jsonObject
            val values = android.content.ContentValues()
            tasks["fields"]!!.jsonArray.map { it.jsonObject }.filter { it["notNull"]?.jsonPrimitive?.content == "true" && it["columnName"]!!.jsonPrimitive.content != "id" }.forEach { f ->
                val col = f["columnName"]!!.jsonPrimitive.content
                if (f["affinity"]!!.jsonPrimitive.content == "TEXT") values.put(col, "") else values.put(col, 0)
            }
            values.put("title", "تصليح المكيف")
            values.put("category", "HOME")
            old.insertOrThrow("tasks", null, values)
            old.version = 1
        }
        val db1 = Room.databaseBuilder(testContext(), AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val cats = db1.categories().all()
            assertEquals(listOf("WORK", "HOME", "PERSONAL", "TRAVEL"), cats.map { it.key })
            val task = db1.tasks().all().single()
            assertEquals("تصليح المكيف", task.title)
            assertEquals("HOME", task.category)
        } finally {
            db1.close()
            file.delete()
        }
    }

    @Test
    fun addRenameAndDeleteMovesTasksSafely() = runBlocking {
        val db = inMemoryDb()
        try {
            val cats = CategoryRepository(db)
            val repo = TaskRepository(db, defaultReminderTime = { "09:00" }, clock = clock, onRemindersChanged = {})
            val farm = cats.add("المزرعة", 4, "home")
            val other = cats.add("الصحة", 5, "person")
            assertTrue(farm != other)
            assertEquals(6, cats.all().size)

            val task = repo.createTask(TaskRepository.NewTask("سقاية الأشجار", category = farm))
            cats.update(cats.all().first { it.key == farm }.copy(name = "  المزرعة الكبيرة "))
            assertEquals("المزرعة الكبيرة", cats.all().first { it.key == farm }.name)

            assertTrue(cats.delete(farm, moveTo = CategoryKey.HOME.name))
            assertEquals(CategoryKey.HOME.name, db.tasks().get(task)!!.category)
            assertFalse(cats.all().any { it.key == farm })

            // The last category can never be removed.
            cats.all().dropLast(1).forEach { c -> cats.delete(c.key, cats.all().last().key) }
            assertEquals(1, cats.all().size)
            assertFalse(cats.delete(cats.all().single().key, "WORK"))
        } finally {
            db.close()
        }
    }

    @Test
    fun theAiCanFileTasksUnderHisOwnCategory() = runBlocking {
        val db = inMemoryDb()
        try {
            val cats = CategoryRepository(db)
            val repo = TaskRepository(db, defaultReminderTime = { "09:00" }, clock = clock, onRemindersChanged = {})
            val health = cats.add("الصحة", 5, "person")
            val exec = ActionExecutor(repo, cats)
            exec.apply(listOf(AiAction(type = "create_task", title = "قياس الضغط", category = health, spokenSummary = "سأضيف مهمة: قياس الضغط")), null)
            exec.apply(listOf(AiAction(type = "create_task", title = "شيء ما", category = "NO_SUCH_KEY", spokenSummary = "x")), null)
            val tasks = db.tasks().all().associateBy { it.title }
            assertEquals(health, tasks.getValue("قياس الضغط").category)
            assertEquals(CategoryKey.PERSONAL.name, tasks.getValue("شيء ما").category)
        } finally {
            db.close()
        }
    }

    @Test
    fun oneChangeIsOneHistoryLine() = runBlocking {
        val db = inMemoryDb()
        try {
            val repo = TaskRepository(db, defaultReminderTime = { "09:00" }, clock = clock, onRemindersChanged = {})
            val exec = ActionExecutor(repo, CategoryRepository(db))
            exec.apply(listOf(AiAction(type = "create_task", title = "شرب كاسة ماء", spokenSummary = "سأضيف مهمة جديدة: شرب كاسة ماء")), null)
            val id = db.tasks().all().single().id
            // New name and new date from one sentence: both steps write the same line.
            exec.apply(listOf(AiAction(type = "update_task", taskId = id, title = "شرب كاستين ماء", date = "2026-11-12", spokenSummary = "سأغيّر المهمة")), null)
            val lines = db.activity().observeForTask(id).first()
            assertEquals(1, lines.count { it.type == ActivityType.CREATED.name })
            assertFalse("the read-back promise isn't stored as history", lines.any { it.text?.startsWith("سأضيف") == true })
            assertEquals(1, lines.count { it.text == "سأغيّر المهمة" })

            // The same change again later is a new line.
            clock.advance(Duration.ofMinutes(5))
            repo.updateTask(id, title = "شرب كاسة ماء", logText = "سأغيّر المهمة")
            assertEquals(2, db.activity().observeForTask(id).first().count { it.text == "سأغيّر المهمة" })
        } finally {
            db.close()
        }
    }
}
