package io.github.kiraherk.smarttodo.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.Priority
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.screens.LocalVoice
import io.github.kiraherk.smarttodo.ui.screens.TaskDetailScreen
import io.github.kiraherk.smarttodo.ui.screens.TodayScreen
import io.github.kiraherk.smarttodo.ui.screens.VoiceActions
import io.github.kiraherk.smarttodo.ui.theme.SmartTodoTheme
import io.github.kiraherk.smarttodo.ui.voice.VoiceOverlayContent
import androidx.compose.ui.test.onAllNodesWithText
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalTime

/**
 * Renders real screens in Arabic (right-to-left) to PNG files in app/build/screenshots, so the
 * layout can be checked without a phone. Not an assertion test: it fails only if a screen crashes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = SmartTodoApp::class, qualifiers = "ar-w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val voice = VoiceActions({}, { _, _ -> }, {}, MutableSharedFlow())

    private fun app() = compose.activity.application as SmartTodoApp

    private fun seed(): Long = runBlocking {
        val repo = app().container.repo
        val today = repo.today()
        val a = repo.createTask(TaskRepository.NewTask("الاتصال بأحمد بخصوص الشحنة", category = CategoryKey.WORK, date = today, time = LocalTime.of(10, 0), reminder = true, priority = Priority.HIGH))
        repo.createTask(TaskRepository.NewTask("شراء دواء الضغط", category = CategoryKey.PERSONAL, date = today, time = LocalTime.of(17, 30)))
        val c = repo.createTask(TaskRepository.NewTask("الاطمئنان على أبو خالد", category = CategoryKey.PERSONAL, recurrence = RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(today.plusDays(2).dayOfWeek.value)), reminder = true))
        repo.createTask(TaskRepository.NewTask("حجز تذاكر دبي", category = CategoryKey.TRAVEL, date = today.plusDays(5)))
        repo.createTask(TaskRepository.NewTask("تصليح المكيف", category = CategoryKey.HOME, date = today.minusDays(1)))
        repo.setStatusForTask(a, TaskStatus.PARTIALLY_DONE, progress = 50, note = "كلمته، بقي تأكيد موعد الوصول")
        repo.addNote(c, "قال إنه مسافر نهاية الشهر")
        a
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun todayScreen() {
        seed()
        compose.setContent {
            SmartTodoTheme(1.15f) {
                CompositionLocalProvider(LocalVoice provides voice) {
                    TodayScreen(onOpenTask = {}, onNavigate = {}, speakSummaryNow = false, onSummarySpoken = {})
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("حجز تذاكر دبي").fetchSemanticsNodes().isNotEmpty() }
        save("today")
    }

    @Test
    fun taskDetailScreen() {
        val id = seed()
        compose.setContent {
            SmartTodoTheme(1.15f) {
                CompositionLocalProvider(LocalVoice provides voice) { TaskDetailScreen(taskId = id, onBack = {}) }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("قال إنه مسافر", substring = true).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("كلمته، بقي تأكيد موعد الوصول", substring = true).fetchSemanticsNodes().isNotEmpty() }
        save("task_detail")
    }

    @Test
    fun confirmOverlay() {
        compose.setContent {
            SmartTodoTheme(1.15f) {
                VoiceOverlayContent(
                    state = VoiceViewModel.UiState.Confirm(
                        "ذكرني كل أسبوعين يوم الاثنين الساعة عشرة أتصل بأحمد",
                        listOf(VoiceViewModel.Preview("create_task", "سأضيف مهمة: الاتصال بأحمد في العمل، كل أسبوعين يوم الاثنين الساعة العاشرة صباحاً، مع تذكير.", "الاتصال بأحمد · العمل · كل أسبوعين يوم الاثنين · 🔔")),
                    ),
                    onFinish = {}, onConfirm = {}, onReject = {}, onRetry = {}, onSpeakAgain = {}, onDismiss = {},
                )
            }
        }
        save("confirm")
    }

    @Test
    fun listeningOverlay() {
        compose.setContent {
            SmartTodoTheme(1.15f) {
                VoiceOverlayContent(
                    state = VoiceViewModel.UiState.Listening("بكرة الساعة خمسة المغرب ذكرني أروح للدكتور", 0.4f),
                    onFinish = {}, onConfirm = {}, onReject = {}, onRetry = {}, onSpeakAgain = {}, onDismiss = {},
                )
            }
        }
        save("listening")
    }

    @Test
    fun slowThinkingOverlayHasCancel() {
        var cancelled = false
        compose.setContent {
            SmartTodoTheme(1.15f) {
                VoiceOverlayContent(
                    state = VoiceViewModel.UiState.Thinking("شو عندي اليوم", slow = true),
                    onFinish = {}, onConfirm = {}, onReject = {}, onRetry = {}, onSpeakAgain = {}, onDismiss = { cancelled = true },
                )
            }
        }
        save("thinking-slow")
        compose.onNodeWithText("إلغاء").performClick()
        assertTrue(cancelled)
    }
}
