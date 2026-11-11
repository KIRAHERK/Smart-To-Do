package io.github.kiraherk.smarttodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    /** BCP-47 tag for speech recognition, e.g. "ar-SA", "ar-EG", "ar-LB". */
    val dialect: String = "ar-SA",
    /** Gemini model chosen by hand, or "" for automatic (best free model). */
    val commandModel: String = "",
    val assistantModel: String = "",
    /** JSON list of model ids last fetched from the Gemini API. */
    val cachedModels: String = "",
    val morningEnabled: Boolean = true,
    val morningTime: String = "08:00",
    val eveningEnabled: Boolean = true,
    val eveningTime: String = "20:00",
    val weeklyEnabled: Boolean = true,
    /** ISO day 1 = Monday ... 7 = Sunday. Default Friday. */
    val weeklyDay: Int = 5,
    val weeklyTime: String = "18:00",
    /** Used when he asks for a reminder without saying a time. */
    val defaultReminderTime: String = "09:00",
    val readAloud: Boolean = true,
    val autoConfirm: Boolean = false,
    /** Use Google's listening popup instead of listening inside the app (set automatically if needed). */
    val voicePopup: Boolean = false,
    val textScale: Float = 1.15f,
    val backupTreeUri: String = "",
    val lastBackupAt: Long = 0,
    val lastNotifiedUpdate: String = "",
    val setupDone: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val dialect = stringPreferencesKey("dialect")
        val commandModel = stringPreferencesKey("command_model")
        val assistantModel = stringPreferencesKey("assistant_model")
        val cachedModels = stringPreferencesKey("cached_models")
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val morningTime = stringPreferencesKey("morning_time")
        val eveningEnabled = booleanPreferencesKey("evening_enabled")
        val eveningTime = stringPreferencesKey("evening_time")
        val weeklyEnabled = booleanPreferencesKey("weekly_enabled")
        val weeklyDay = intPreferencesKey("weekly_day")
        val weeklyTime = stringPreferencesKey("weekly_time")
        val defaultReminderTime = stringPreferencesKey("default_reminder_time")
        val readAloud = booleanPreferencesKey("read_aloud")
        val autoConfirm = booleanPreferencesKey("auto_confirm")
        val voicePopup = booleanPreferencesKey("voice_popup")
        val textScale = floatPreferencesKey("text_scale")
        val backupTreeUri = stringPreferencesKey("backup_tree_uri")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val lastNotifiedUpdate = stringPreferencesKey("last_notified_update")
        val setupDone = booleanPreferencesKey("setup_done")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            dialect = p[Keys.dialect] ?: d.dialect,
            commandModel = p[Keys.commandModel] ?: d.commandModel,
            assistantModel = p[Keys.assistantModel] ?: d.assistantModel,
            cachedModels = p[Keys.cachedModels] ?: d.cachedModels,
            morningEnabled = p[Keys.morningEnabled] ?: d.morningEnabled,
            morningTime = p[Keys.morningTime] ?: d.morningTime,
            eveningEnabled = p[Keys.eveningEnabled] ?: d.eveningEnabled,
            eveningTime = p[Keys.eveningTime] ?: d.eveningTime,
            weeklyEnabled = p[Keys.weeklyEnabled] ?: d.weeklyEnabled,
            weeklyDay = p[Keys.weeklyDay] ?: d.weeklyDay,
            weeklyTime = p[Keys.weeklyTime] ?: d.weeklyTime,
            defaultReminderTime = p[Keys.defaultReminderTime] ?: d.defaultReminderTime,
            readAloud = p[Keys.readAloud] ?: d.readAloud,
            autoConfirm = p[Keys.autoConfirm] ?: d.autoConfirm,
            voicePopup = p[Keys.voicePopup] ?: d.voicePopup,
            textScale = p[Keys.textScale] ?: d.textScale,
            backupTreeUri = p[Keys.backupTreeUri] ?: d.backupTreeUri,
            lastBackupAt = p[Keys.lastBackupAt] ?: d.lastBackupAt,
            lastNotifiedUpdate = p[Keys.lastNotifiedUpdate] ?: d.lastNotifiedUpdate,
            setupDone = p[Keys.setupDone] ?: d.setupDone,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setDialect(v: String) = put(Keys.dialect, v)
    suspend fun setCommandModel(v: String) = put(Keys.commandModel, v)
    suspend fun setAssistantModel(v: String) = put(Keys.assistantModel, v)
    suspend fun setCachedModels(v: String) = put(Keys.cachedModels, v)
    suspend fun setMorning(enabled: Boolean, time: String) { put(Keys.morningEnabled, enabled); put(Keys.morningTime, time) }
    suspend fun setEvening(enabled: Boolean, time: String) { put(Keys.eveningEnabled, enabled); put(Keys.eveningTime, time) }
    suspend fun setWeekly(enabled: Boolean, day: Int, time: String) { put(Keys.weeklyEnabled, enabled); put(Keys.weeklyDay, day); put(Keys.weeklyTime, time) }
    suspend fun setDefaultReminderTime(v: String) = put(Keys.defaultReminderTime, v)
    suspend fun setReadAloud(v: Boolean) = put(Keys.readAloud, v)
    suspend fun setAutoConfirm(v: Boolean) = put(Keys.autoConfirm, v)
    suspend fun setVoicePopup(v: Boolean) = put(Keys.voicePopup, v)
    suspend fun setTextScale(v: Float) = put(Keys.textScale, v)
    suspend fun setBackupTreeUri(v: String) = put(Keys.backupTreeUri, v)
    suspend fun setLastBackupAt(v: Long) = put(Keys.lastBackupAt, v)
    suspend fun setLastNotifiedUpdate(v: String) = put(Keys.lastNotifiedUpdate, v)
    suspend fun setSetupDone(v: Boolean) = put(Keys.setupDone, v)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
