package io.github.kiraherk.smarttodo.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.BuildConfig
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ai.AiException
import io.github.kiraherk.smarttodo.ai.ModelRanking
import io.github.kiraherk.smarttodo.data.settings.AppSettings
import io.github.kiraherk.smarttodo.reminders.Notifications
import io.github.kiraherk.smarttodo.ui.voiceLabel
import io.github.kiraherk.smarttodo.ai.AiService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import io.github.kiraherk.smarttodo.ui.components.AppCard
import io.github.kiraherk.smarttodo.ui.components.ScreenHeader
import io.github.kiraherk.smarttodo.ui.components.SettingRow
import io.github.kiraherk.smarttodo.ui.components.SwitchRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.Color
import io.github.kiraherk.smarttodo.ui.components.ChoiceDialog
import io.github.kiraherk.smarttodo.ui.components.ConfirmDialog
import io.github.kiraherk.smarttodo.ui.components.SectionHeader
import io.github.kiraherk.smarttodo.ui.components.pickTime
import io.github.kiraherk.smarttodo.update.UpdateInfo
import io.github.kiraherk.smarttodo.update.UpdateInstaller
import io.github.kiraherk.smarttodo.util.Dates
import io.github.kiraherk.smarttodo.util.LocaleHelper
import io.github.kiraherk.smarttodo.voice.Speaker
import io.github.kiraherk.smarttodo.voice.SpeechCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val hasKey = MutableStateFlow(c.secrets.hasApiKey())
    val models = MutableStateFlow<List<String>>(emptyList())
    val keyStatus = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val toast = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { models.value = c.ai.cachedModels().filter(ModelRanking::isUsable) }
    }

    private fun str(id: Int, vararg args: Any) = c.localized().getString(id, *args)

    fun saveKey(key: String) = viewModelScope.launch {
        c.secrets.setApiKey(key)
        hasKey.value = c.secrets.hasApiKey()
        if (hasKey.value) testKey()
    }

    fun removeKey() {
        c.secrets.setApiKey(null)
        hasKey.value = false
        keyStatus.value = null
    }

    fun testKey() = viewModelScope.launch {
        busy.value = true
        keyStatus.value = try {
            val usable = c.ai.refreshModels().filter(ModelRanking::isUsable)
            models.value = usable
            val cmd = c.ai.chain(io.github.kiraherk.smarttodo.ai.AiPurpose.COMMAND).firstOrNull() ?: "—"
            val ask = c.ai.chain(io.github.kiraherk.smarttodo.ai.AiPurpose.ASSISTANT).firstOrNull() ?: "—"
            str(R.string.key_ok, usable.size, cmd, ask)
        } catch (e: AiException.InvalidKey) {
            str(R.string.err_invalid_key)
        } catch (e: AiException.Network) {
            str(R.string.err_network)
        } catch (e: Exception) {
            str(R.string.err_ai_generic) + " (" + e.message + ")"
        }
        busy.value = false
    }

    fun setCommandModel(id: String) = viewModelScope.launch { c.settings.setCommandModel(id) }
    fun setAssistantModel(id: String) = viewModelScope.launch { c.settings.setAssistantModel(id) }
    fun setDialect(v: String) = viewModelScope.launch { c.settings.setDialect(v) }
    fun setReadAloud(v: Boolean) = viewModelScope.launch { c.settings.setReadAloud(v); c.speaker.enabled = v }
    fun setAutoConfirm(v: Boolean) = viewModelScope.launch { c.settings.setAutoConfirm(v) }
    fun setVoicePopup(v: Boolean) = viewModelScope.launch { c.settings.setVoicePopup(v) }
    fun setTextScale(v: Float) = viewModelScope.launch { c.settings.setTextScale(v) }
    fun testVoice() = c.speaker.speakNow(str(R.string.voice_test_sentence))
    /** Plays the sample with a voice, without saving it. */
    fun previewVoice(v: String) = c.speaker.preview(str(R.string.voice_test_sentence), v)
    fun saveVoice(v: String) = viewModelScope.launch { c.settings.setVoiceName(v) }
    fun stopVoice() = c.speaker.stop()

    /** A short explanation when the phone voice had to read instead of the natural one. */
    fun voiceStatus(): String? {
        val issue = c.ai.speechIssue ?: return null
        val age = System.currentTimeMillis() - c.ai.speechIssueAt
        val fresh = if (issue == AiService.SpeechIssue.DAILY_LIMIT) age < 12 * 3_600_000L else age < 10 * 60_000L
        if (!fresh) return null
        return str(
            when (issue) {
                AiService.SpeechIssue.DAILY_LIMIT -> R.string.voice_status_daily
                AiService.SpeechIssue.MINUTE_LIMIT -> R.string.voice_status_minute
                AiService.SpeechIssue.SLOW -> R.string.voice_status_slow
                AiService.SpeechIssue.NO_INTERNET -> R.string.voice_status_offline
                AiService.SpeechIssue.OTHER -> R.string.voice_status_other
            }
        )
    }

    fun setMorning(enabled: Boolean, time: String) = viewModelScope.launch { c.settings.setMorning(enabled, time); c.checkInScheduler.scheduleAll() }
    fun setEvening(enabled: Boolean, time: String) = viewModelScope.launch { c.settings.setEvening(enabled, time); c.checkInScheduler.scheduleAll() }
    fun setWeekly(enabled: Boolean, day: Int, time: String) = viewModelScope.launch { c.settings.setWeekly(enabled, day, time); c.checkInScheduler.scheduleAll() }
    fun setDefaultReminderTime(v: String) = viewModelScope.launch { c.settings.setDefaultReminderTime(v) }

    fun setBackupFolder(uri: Uri) = viewModelScope.launch {
        runCatching {
            c.app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        c.settings.setBackupTreeUri(uri.toString())
        backupNow()
    }

    fun backupNow() = viewModelScope.launch {
        toast.value = runCatching { c.backup.backupNow("manual") }.fold({ str(R.string.backup_done) }, { str(R.string.backup_failed) })
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        toast.value = runCatching { c.backup.restore(uri) }.fold({ str(R.string.restore_done) }, { str(R.string.restore_failed) + " " + it.message })
    }

    fun shareIntent(): Intent? {
        val file = c.backup.latestFile() ?: return null
        val uri = FileProvider.getUriForFile(c.app, c.app.packageName + ".files", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            str(R.string.share_backup),
        )
    }

    fun checkUpdate() = viewModelScope.launch {
        update.value = UpdateState.Checking
        update.value = try {
            c.updateChecker.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Error(str(R.string.update_check_failed) + " " + (e.message ?: ""))
        }
    }

    fun unknownSourcesIntent() = c.updateInstaller.unknownSourcesSettings()

    fun installUpdate(info: UpdateInfo) = viewModelScope.launch {
        if (!c.updateInstaller.canInstall()) {
            update.value = UpdateState.NeedsPermission(info)
            return@launch
        }
        update.value = UpdateState.Downloading(0f)
        update.value = try {
            val file = c.updateInstaller.download(info) { p -> update.value = UpdateState.Downloading(p) }
            c.updateInstaller.verify(file)
            c.updateInstaller.install(file)
            UpdateState.Installing
        } catch (e: UpdateInstaller.Problem.DebugBuild) {
            UpdateState.Error(str(R.string.update_debug_build))
        } catch (e: UpdateInstaller.Problem.WrongSignature) {
            UpdateState.Error(str(R.string.update_bad_signature))
        } catch (e: UpdateInstaller.Problem.NotNewer) {
            UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Error(str(R.string.update_failed) + " " + (e.message ?: ""))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(openUpdate: Boolean, onUpdateOpened: () -> Unit, onOpenSetup: () -> Unit, onOpenInbox: () -> Unit) {
    val c = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(c) }
    val ctx = LocalContext.current
    val activity = LocalActivity.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.setBackupFolder(it) } }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    LaunchedEffect(toast) { toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.toast.value = null } }
    LaunchedEffect(openUpdate) { if (openUpdate) { vm.checkUpdate(); onUpdateOpened() } }

    val auto = stringResource(R.string.automatic)
    val locale = LocaleHelper.locale(ctx)

    val blue = Color(0xFF2F5DA8)
    val green = Color(0xFF2E8B57)
    val plum = Color(0xFF8A4FA3)
    val gold = MaterialTheme.colorScheme.secondary
    val warm = MaterialTheme.colorScheme.tertiary
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenHeader(stringResource(R.string.settings_title)) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {

            SectionHeader(stringResource(R.string.section_ai), MaterialTheme.colorScheme.primary)
            Group {
                SettingRow(Icons.Filled.Key, stringResource(R.string.gemini_key), stringResource(if (hasKey) R.string.key_saved else R.string.key_missing)) { dialog = "key" }
                if (hasKey) {
                    SettingRow(Icons.Filled.Memory, stringResource(R.string.test_key), keyStatus ?: stringResource(R.string.test_key_hint)) { vm.testKey() }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    SettingRow(Icons.Filled.Memory, stringResource(R.string.model_commands), s.commandModel.ifBlank { auto }) { dialog = "cmdModel" }
                    SettingRow(Icons.Filled.Memory, stringResource(R.string.model_questions), s.assistantModel.ifBlank { auto }) { dialog = "askModel" }
                }
                Text(stringResource(R.string.ai_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }

            SectionHeader(stringResource(R.string.section_voice), gold)
            Group {
                SettingRow(Icons.Filled.RecordVoiceOver, stringResource(R.string.voice_choice), voiceLabel(ctx, s.voiceName), color = gold) { dialog = "voice" }
                SettingRow(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.test_voice), stringResource(R.string.test_voice_hint), color = gold) { vm.testVoice() }
                SwitchRow(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.read_aloud_setting), s.readAloud, color = gold) { vm.setReadAloud(it) }
                SettingRow(Icons.Filled.Language, stringResource(R.string.dialect), Locale.forLanguageTag(s.dialect).getDisplayName(locale), color = gold) { dialog = "dialect" }
                SwitchRow(Icons.Filled.Checklist, stringResource(R.string.auto_confirm), s.autoConfirm, color = gold) { vm.setAutoConfirm(it) }
                SwitchRow(Icons.Filled.Mic, stringResource(R.string.voice_popup_setting), s.voicePopup, color = gold) { vm.setVoicePopup(it) }
                Text(stringResource(R.string.voice_natural_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                vm.voiceStatus()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }

            SectionHeader(stringResource(R.string.section_checkins), warm)
            Group {
                SwitchRow(Icons.Filled.WbSunny, stringResource(R.string.morning_summary) + " · " + friendly(ctx, s.morningTime), s.morningEnabled, color = warm) { vm.setMorning(it, s.morningTime) }
                SettingRow(Icons.Filled.Schedule, stringResource(R.string.change_time), friendly(ctx, s.morningTime), color = warm) {
                    pickTime(ctx, Dates.parseTime(s.morningTime) ?: LocalTime.of(8, 0)) { vm.setMorning(s.morningEnabled, Dates.formatTime(it)) }
                }
                SwitchRow(Icons.Filled.NightsStay, stringResource(R.string.evening_review) + " · " + friendly(ctx, s.eveningTime), s.eveningEnabled, color = blue) { vm.setEvening(it, s.eveningTime) }
                SettingRow(Icons.Filled.Schedule, stringResource(R.string.change_time), friendly(ctx, s.eveningTime), color = blue) {
                    pickTime(ctx, Dates.parseTime(s.eveningTime) ?: LocalTime.of(20, 0)) { vm.setEvening(s.eveningEnabled, Dates.formatTime(it)) }
                }
                SwitchRow(Icons.Filled.DateRange, stringResource(R.string.weekly_review) + " · " + Dates.weekdayName(s.weeklyDay, locale) + " " + friendly(ctx, s.weeklyTime), s.weeklyEnabled, color = plum) {
                    vm.setWeekly(it, s.weeklyDay, s.weeklyTime)
                }
                SettingRow(Icons.Filled.DateRange, stringResource(R.string.change_day), Dates.weekdayName(s.weeklyDay, locale), color = plum) { dialog = "weekday" }
                SettingRow(Icons.Filled.Schedule, stringResource(R.string.change_time), friendly(ctx, s.weeklyTime), color = plum) {
                    pickTime(ctx, Dates.parseTime(s.weeklyTime) ?: LocalTime.of(18, 0)) { vm.setWeekly(s.weeklyEnabled, s.weeklyDay, Dates.formatTime(it)) }
                }
                SettingRow(Icons.Filled.NotificationsActive, stringResource(R.string.default_reminder_time), friendly(ctx, s.defaultReminderTime)) {
                    pickTime(ctx, Dates.parseTime(s.defaultReminderTime) ?: LocalTime.of(9, 0)) { vm.setDefaultReminderTime(Dates.formatTime(it)) }
                }
            }

            SectionHeader(stringResource(R.string.section_display), blue)
            Group {
                SettingRow(Icons.Filled.Language, stringResource(R.string.app_language), if (LocaleHelper.isArabic(ctx)) "العربية" else "English", color = blue) { dialog = "language" }
                SettingRow(Icons.Filled.TextFields, stringResource(R.string.text_size), "${(s.textScale * 100).toInt()}%", color = blue) { dialog = "textSize" }
            }

            SectionHeader(stringResource(R.string.section_backup), green)
            Group {
                Text(
                    if (s.lastBackupAt > 0) stringResource(R.string.last_backup, Dates.dateTimeOf(ctx, s.lastBackupAt)) else stringResource(R.string.no_backup_yet),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                SettingRow(Icons.Filled.Backup, stringResource(R.string.backup_now), stringResource(R.string.backup_now_hint), color = green) { vm.backupNow() }
                SettingRow(Icons.Filled.Folder, stringResource(R.string.backup_folder), if (s.backupTreeUri.isBlank()) stringResource(R.string.backup_folder_none) else stringResource(R.string.backup_folder_set), color = green) {
                    folderPicker.launch(null)
                }
                SettingRow(Icons.Filled.Share, stringResource(R.string.share_backup), stringResource(R.string.share_backup_hint), color = green) {
                    vm.shareIntent()?.let { ctx.startActivity(it) } ?: vm.backupNow()
                }
                SettingRow(Icons.Filled.Restore, stringResource(R.string.restore_backup), stringResource(R.string.restore_backup_hint), color = green) {
                    restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                }
                SettingRow(Icons.Filled.Inbox, stringResource(R.string.inbox_title), stringResource(R.string.inbox_settings_hint), color = green) { onOpenInbox() }
            }

            SectionHeader(stringResource(R.string.section_updates), MaterialTheme.colorScheme.primary)
            Group {
                SettingRow(Icons.Filled.SystemUpdate, stringResource(R.string.check_updates), stringResource(R.string.current_version, BuildConfig.VERSION_NAME)) { vm.checkUpdate() }
                UpdateCard(update, onInstall = { vm.installUpdate(it) }, onPermission = { ctx.startActivity(vm.unknownSourcesIntent()) })
            }

            SectionHeader(stringResource(R.string.section_phone), grey)
            Group {
                SettingRow(Icons.Filled.Checklist, stringResource(R.string.setup_title), stringResource(R.string.setup_settings_hint), color = grey) { onOpenSetup() }
                SettingRow(Icons.Filled.Code, stringResource(R.string.open_source), "github.com/" + BuildConfig.GITHUB_REPO, color = grey) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + BuildConfig.GITHUB_REPO)))
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    when (dialog) {
        "key" -> KeyDialog(hasKey, onSave = { vm.saveKey(it) }, onRemove = { vm.removeKey() }, onDismiss = { dialog = null })
        "cmdModel", "askModel" -> {
            val isCmd = dialog == "cmdModel"
            val ranked = ModelRanking.rank(models)
            ChoiceDialog(
                title = stringResource(if (isCmd) R.string.model_commands else R.string.model_questions),
                options = listOf("" to auto) + ranked.map { it to it },
                selected = if (isCmd) s.commandModel else s.assistantModel,
                onSelect = { if (isCmd) vm.setCommandModel(it) else vm.setAssistantModel(it) },
                onDismiss = { dialog = null },
            )
        }
        "voice" -> VoicePickerDialog(
            current = s.voiceName,
            onPreview = { vm.previewVoice(it) },
            onSave = { vm.saveVoice(it) },
            onDismiss = { vm.stopVoice(); dialog = null },
        )
        "dialect" -> ChoiceDialog(
            title = stringResource(R.string.dialect),
            options = SpeechCapture.DIALECTS.map { it to Locale.forLanguageTag(it).getDisplayName(locale) },
            selected = s.dialect,
            onSelect = { vm.setDialect(it) },
            onDismiss = { dialog = null },
        )
        "weekday" -> ChoiceDialog(
            title = stringResource(R.string.change_day),
            options = listOf(6, 7, 1, 2, 3, 4, 5).map { it to Dates.weekdayName(it, locale) },
            selected = s.weeklyDay,
            onSelect = { vm.setWeekly(s.weeklyEnabled, it, s.weeklyTime) },
            onDismiss = { dialog = null },
        )
        "textSize" -> ChoiceDialog(
            title = stringResource(R.string.text_size),
            options = listOf(1.0f, 1.15f, 1.3f, 1.5f).map { it to "${(it * 100).toInt()}%" },
            selected = s.textScale,
            onSelect = { vm.setTextScale(it) },
            onDismiss = { dialog = null },
        )
        "language" -> ChoiceDialog(
            title = stringResource(R.string.app_language),
            options = listOf("ar" to "العربية", "en" to "English"),
            selected = LocaleHelper.language(ctx),
            onSelect = {
                LocaleHelper.setLanguage(ctx, it)
                Notifications.createChannels(ctx.applicationContext)
                activity?.recreate()
            },
            onDismiss = { dialog = null },
        )
    }
    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_backup),
            text = stringResource(R.string.restore_confirm),
            confirm = stringResource(R.string.restore_backup),
            onConfirm = { vm.restore(uri) },
            onDismiss = { confirmRestore = null },
        )
    }
}

private fun friendly(ctx: android.content.Context, time: String): String =
    Dates.parseTime(time)?.let { Dates.friendlyTime(ctx, it) } ?: time

/**
 * Choosing the reading voice: tapping a voice plays a sample and marks it, nothing is saved until
 * he presses Save (so he can listen to several first).
 */
@Composable
private fun VoicePickerDialog(current: String, onPreview: (String) -> Unit, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.voice_choice), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(stringResource(R.string.voice_pick_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((Speaker.NATURAL_VOICES + "").size) { i ->
                        val name = (Speaker.NATURAL_VOICES + "")[i]
                        val isSelected = name == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { selected = name; onPreview(name) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = isSelected, onClick = { selected = name; onPreview(name) })
                            Text(voiceLabel(ctx, name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = { onSave(selected); onDismiss() }, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.titleSmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A white rounded group of settings rows. */
@Composable
private fun Group(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    AppCard(padding = PaddingValues(6.dp), content = content)
}

@Composable
private fun UpdateCard(state: UpdateState, onInstall: (UpdateInfo) -> Unit, onPermission: () -> Unit) {
    val text: String = when (state) {
        UpdateState.Idle -> return
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Available -> stringResource(R.string.update_available_title, state.info.version) + "\n" + state.info.notes.take(600)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, (state.progress * 100).toInt())
        UpdateState.Installing -> stringResource(R.string.update_installing)
        is UpdateState.NeedsPermission -> stringResource(R.string.update_needs_permission)
        is UpdateState.Error -> state.message
    }
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (state is UpdateState.Downloading) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { onInstall(state.info) }) { Text(stringResource(R.string.update_now), style = MaterialTheme.typography.titleMedium) }
                is UpdateState.NeedsPermission -> Row {
                    TextButton(onClick = onPermission) { Text(stringResource(R.string.allow_install)) }
                    TextButton(onClick = { onInstall(state.info) }) { Text(stringResource(R.string.update_now)) }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun KeyDialog(hasKey: Boolean, onSave: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gemini_key)) },
        text = {
            Column {
                Text(stringResource(R.string.key_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("AIza…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = key.length > 10, onClick = { onSave(key); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (hasKey) TextButton(onClick = { onRemove(); onDismiss() }) { Text(stringResource(R.string.remove_key)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
