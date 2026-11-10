package io.github.kiraherk.smarttodo

import android.app.Application
import android.content.Context
import io.github.kiraherk.smarttodo.ai.ActionExecutor
import io.github.kiraherk.smarttodo.ai.AiService
import io.github.kiraherk.smarttodo.ai.AssistantService
import io.github.kiraherk.smarttodo.ai.CommandInterpreter
import io.github.kiraherk.smarttodo.ai.ContextBuilder
import io.github.kiraherk.smarttodo.ai.GeminiClient
import io.github.kiraherk.smarttodo.backup.BackupManager
import io.github.kiraherk.smarttodo.checkins.CheckInScheduler
import io.github.kiraherk.smarttodo.checkins.CheckIns
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.data.settings.SecretStore
import io.github.kiraherk.smarttodo.data.settings.SettingsRepository
import io.github.kiraherk.smarttodo.reminders.ReminderScheduler
import io.github.kiraherk.smarttodo.update.UpdateChecker
import io.github.kiraherk.smarttodo.update.UpdateInstaller
import io.github.kiraherk.smarttodo.util.LocaleHelper
import io.github.kiraherk.smarttodo.voice.Speaker
import io.github.kiraherk.smarttodo.voice.SpeechCapture
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit

/** Creates and holds the app's single instances (simple manual dependency injection). */
class AppContainer(val app: Application) {
    private val clock: Clock = Clock.systemDefaultZone()

    val db: AppDatabase = AppDatabase.build(app)
    val settings = SettingsRepository(app)
    val secrets = SecretStore(app)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Where Gemini requests go. Only the emulator tests change it, to point at a fake server. */
    @Volatile
    var geminiBaseUrl: String = GeminiClient.DEFAULT_BASE_URL

    val gemini = GeminiClient(http, apiKey = { secrets.getApiKey() }, baseUrl = { geminiBaseUrl })
    val ai = AiService(gemini, settings)

    val reminders = ReminderScheduler(app, db)
    val checkInScheduler = CheckInScheduler(app, settings, reminders)

    val repo = TaskRepository(
        db = db,
        defaultReminderTime = { settings.current().defaultReminderTime },
        clock = clock,
        onRemindersChanged = { reminders.rescheduleAll() },
    )

    val contextBuilder = ContextBuilder(db, repo)
    val interpreter = CommandInterpreter(ai, contextBuilder, clock) { language() }
    val executor = ActionExecutor(repo)
    val assistant = AssistantService(ai, contextBuilder, db, clock) { language() }
    val checkIns = CheckIns(db, repo) { localized() }

    val backup: BackupManager = BackupManager(app, db, settings) { maintenance.run() }
    val updateChecker = UpdateChecker(http)
    val updateInstaller = UpdateInstaller(app, http, backup)

    /** Created on first use, on the main thread. */
    val speech: SpeechCapture by lazy { SpeechCapture(app) }
    val speaker: Speaker by lazy { Speaker(app) { language() } }

    val maintenance: Maintenance = Maintenance()

    fun language(): String = LocaleHelper.language(app)

    /** Context with the app language, for any text shown outside an Activity. */
    fun localized(): Context = LocaleHelper.wrap(app)

    /** Daily upkeep, also run at app start, after boot and after restore. Safe to run any time. */
    inner class Maintenance {
        private val mutex = Mutex()

        suspend fun run() {
            mutex.withLock {
                runCatching { repo.rollForward() }
                runCatching { reminders.rescheduleAll() }
                runCatching { checkInScheduler.scheduleAll() }
                runCatching { backup.autoBackupIfDue() }
            }
        }
    }
}
