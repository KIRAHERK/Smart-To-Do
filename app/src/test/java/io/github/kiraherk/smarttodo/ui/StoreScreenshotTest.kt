package io.github.kiraherk.smarttodo.ui

import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.ai.AiAction
import io.github.kiraherk.smarttodo.ai.AiRecurrence
import io.github.kiraherk.smarttodo.data.db.ChatMessageEntity
import io.github.kiraherk.smarttodo.data.repo.TaskRepository.NewTask
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.Priority
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.screens.AssistantScreen
import io.github.kiraherk.smarttodo.ui.screens.HistoryScreen
import io.github.kiraherk.smarttodo.ui.screens.LocalVoice
import io.github.kiraherk.smarttodo.ui.screens.TodayScreen
import io.github.kiraherk.smarttodo.ui.screens.VoiceActions
import io.github.kiraherk.smarttodo.ui.theme.SmartTodoTheme
import io.github.kiraherk.smarttodo.ui.voice.VoiceOverlayContent
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalTime

/**
 * The real screens with a realistic day of tasks, for the download page and README
 * (app/build/store-screenshots). Like ScreenshotTest, it fails only if a screen crashes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = SmartTodoApp::class, qualifiers = "ar-w400dp-h860dp-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val voice = VoiceActions({}, { _, _ -> }, MutableSharedFlow())
    private fun app() = compose.activity.application as SmartTodoApp

    /** A phone that is set up and online, so no setup or offline notice covers the screen. */
    private fun ready() {
        if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.addProvider(FakeAndroidKeyStore())
        app().container.secrets.setApiKey("demo-key-for-screenshots")
        check(app().container.secrets.hasApiKey())
        val cm = app().getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).networkCallbacks.forEach { cb -> cm.activeNetwork?.let { cb.onAvailable(it) } }
    }

    private fun day() = runBlocking {
        val repo = app().container.repo
        val today = repo.today()
        val ahmed = repo.createTask(NewTask("الاتصال بأحمد بخصوص الشحنة", category = CategoryKey.WORK.name, date = today, time = LocalTime.of(10, 0), reminder = true, priority = Priority.HIGH))
        val report = repo.createTask(NewTask("تقرير المبيعات الشهري", category = CategoryKey.WORK.name, date = today, time = LocalTime.of(12, 0)))
        val lease = repo.createTask(NewTask("مراجعة عقد الإيجار مع المحامي", category = CategoryKey.WORK.name, date = today, time = LocalTime.of(15, 0), reminder = true))
        repo.createTask(NewTask("شراء دواء الضغط", category = CategoryKey.PERSONAL.name, date = today, time = LocalTime.of(17, 30), reminder = true))
        repo.createTask(NewTask("زيارة الوالدة", category = CategoryKey.HOME.name, time = LocalTime.of(19, 0), recurrence = RecurrenceRule(Frequency.WEEKLY, weekdays = listOf(today.dayOfWeek.value))))
        repo.createTask(NewTask("تصليح المكيف", category = CategoryKey.HOME.name, date = today.minusDays(1)))
        val khaled = repo.createTask(NewTask("الاطمئنان على أبو خالد", category = CategoryKey.PERSONAL.name, recurrence = RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(today.plusDays(2).dayOfWeek.value)), reminder = true))
        repo.createTask(NewTask("حجز تذاكر دبي", category = CategoryKey.TRAVEL.name, date = today.plusDays(4)))
        repo.setStatusForTask(ahmed, TaskStatus.DONE, note = "قال إن الشحنة توصل يوم الخميس")
        repo.setStatusForTask(report, TaskStatus.PARTIALLY_DONE, progress = 50, note = "باقي الجدول الأخير")
        repo.setStatusForTask(lease, TaskStatus.IN_PROGRESS)
        repo.addNote(khaled, "قال إنه مسافر نهاية الشهر")
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/store-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun today() {
        ready(); day()
        compose.setContent {
            SmartTodoTheme(1.15f) {
                CompositionLocalProvider(LocalVoice provides voice) {
                    TodayScreen(onOpenTask = {}, onNavigate = {}, speakSummaryNow = false, onSummarySpoken = {})
                }
            }
        }
        waitFor("تصليح المكيف")
        shot("today")
    }

    /** The voice card as the phone shows it: over the Today screen, dimmed. */
    private fun overDimmedToday(card: @androidx.compose.runtime.Composable () -> Unit) {
        ready(); day()
        compose.setContent {
            SmartTodoTheme(1.15f) {
                CompositionLocalProvider(LocalVoice provides voice) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        TodayScreen(onOpenTask = {}, onNavigate = {}, speakSummaryNow = false, onSummarySpoken = {})
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)))
                        card()
                    }
                }
            }
        }
        waitFor("تصليح المكيف")
    }

    @Test
    fun listening() {
        overDimmedToday {
            run {
                VoiceOverlayContent(
                    state = VoiceViewModel.UiState.Listening("بكرة الساعة خمسة ذكرني أروح للدكتور", 0.5f),
                    onFinish = {}, onConfirm = {}, onReject = {}, onRetry = {}, onSpeakAgain = {}, onDismiss = {},
                )
            }
        }
        shot("listening")
    }

    @Test
    fun confirm() {
        overDimmedToday {
            run {
                VoiceOverlayContent(
                    state = VoiceViewModel.UiState.Confirm(
                        "ذكرني كل أسبوعين يوم الاثنين الساعة عشرة أتصل بأحمد",
                        listOf(VoiceViewModel.Preview("create_task", "سأضيف مهمة: الاتصال بأحمد في العمل، كل أسبوعين يوم الاثنين الساعة العاشرة صباحاً، مع تذكير.", "")),
                        listOf(AiAction(type = "create_task", title = "الاتصال بأحمد", category = "WORK", time = "10:00", reminder = true, recurrence = AiRecurrence("WEEKLY", 2, listOf(1)))),
                    ),
                    onFinish = {}, onConfirm = {}, onReject = {}, onRetry = {}, onSpeakAgain = {}, onDismiss = {},
                )
            }
        }
        shot("confirm")
    }

    @Test
    fun history() {
        ready(); day()
        compose.setContent {
            SmartTodoTheme(1.15f) {
                CompositionLocalProvider(LocalVoice provides voice) { HistoryScreen(onOpenTask = {}) }
            }
        }
        waitFor("تقرير المبيعات الشهري")
        shot("history")
    }

    @Test
    fun assistant() {
        ready()
        runBlocking {
            val chat = app().container.db.chat()
            listOf(
                "USER" to "ايش عندي اليوم؟",
                "ASSISTANT" to "عندك اليوم 5 مهام. خلصت الاتصال بأحمد، وتقرير المبيعات نصه جاهز. باقي مراجعة عقد الإيجار الساعة 3، ودواء الضغط الساعة 5:30، وزيارة الوالدة الساعة 7 مساءً.",
                "USER" to "متى آخر مرة كلمت أحمد؟",
                "ASSISTANT" to "اليوم الساعة 10 صباحاً بخصوص الشحنة، وقال إنها توصل يوم الخميس.",
            ).forEachIndexed { i, (role, text) -> chat.insert(ChatMessageEntity(role = role, text = text, at = i + 1L)) }
        }
        compose.setContent {
            SmartTodoTheme(1.15f) {
                CompositionLocalProvider(LocalVoice provides voice) { AssistantScreen() }
            }
        }
        waitFor("توصل يوم الخميس")
        shot("assistant")
    }
}

/** Robolectric has no Android Keystore; this in-memory stand-in lets SecretStore hold a key in tests. */
class FakeAndroidKeyStore : java.security.Provider("AndroidKeyStore", 1.0, "test only") {
    init {
        put("KeyStore.AndroidKeyStore", Store::class.java.name)
        put("KeyGenerator.AES", Gen::class.java.name)
    }

    companion object { val keys = mutableMapOf<String, javax.crypto.SecretKey>() }

    class Store : java.security.KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?) = keys[alias]
        override fun engineGetEntry(alias: String, p: java.security.KeyStore.ProtectionParameter?) =
            keys[alias]?.let { java.security.KeyStore.SecretKeyEntry(it) }
        override fun engineGetCertificateChain(alias: String): Array<java.security.cert.Certificate>? = null
        override fun engineGetCertificate(alias: String): java.security.cert.Certificate? = null
        override fun engineGetCreationDate(alias: String): java.util.Date? = null
        override fun engineSetKeyEntry(alias: String, key: java.security.Key, password: CharArray?, chain: Array<java.security.cert.Certificate>?) = Unit
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<java.security.cert.Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String, cert: java.security.cert.Certificate) = Unit
        override fun engineDeleteEntry(alias: String) { keys.remove(alias) }
        override fun engineAliases(): java.util.Enumeration<String> = java.util.Collections.enumeration(keys.keys)
        override fun engineContainsAlias(alias: String) = alias in keys
        override fun engineSize() = keys.size
        override fun engineIsKeyEntry(alias: String) = alias in keys
        override fun engineIsCertificateEntry(alias: String) = false
        override fun engineGetCertificateAlias(cert: java.security.cert.Certificate): String? = null
        override fun engineStore(stream: java.io.OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: java.io.InputStream?, password: CharArray?) = Unit
    }

    class Gen : javax.crypto.KeyGeneratorSpi() {
        private var alias = "key"
        override fun engineInit(random: java.security.SecureRandom?) = Unit
        override fun engineInit(params: java.security.spec.AlgorithmParameterSpec?, random: java.security.SecureRandom?) {
            (params as? android.security.keystore.KeyGenParameterSpec)?.let { alias = it.keystoreAlias }
        }
        override fun engineInit(keysize: Int, random: java.security.SecureRandom?) = Unit
        override fun engineGenerateKey(): javax.crypto.SecretKey =
            javax.crypto.spec.SecretKeySpec(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, "AES").also { keys[alias] = it }
    }
}
