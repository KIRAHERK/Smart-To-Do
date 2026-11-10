package io.github.kiraherk.smarttodo.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.settings.AppSettings
import io.github.kiraherk.smarttodo.ui.components.ChoiceDialog
import io.github.kiraherk.smarttodo.util.LocaleHelper
import io.github.kiraherk.smarttodo.voice.SpeechCapture
import kotlinx.coroutines.launch
import java.util.Locale

/** First-run checklist so reminders really ring on his Samsung and the voice works. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onDone: () -> Unit, onOpenSettings: () -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var refresh by remember { mutableIntStateOf(0) }
    var showDialect by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            scope.launch { c.settings.setBackupTreeUri(it.toString()); c.backup.backupNow("manual") }
        }
    }

    @Suppress("UNUSED_EXPRESSION") refresh
    val micOk = granted(ctx, Manifest.permission.RECORD_AUDIO)
    val notifOk = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
    val alarmsOk = c.reminders.canScheduleExact()
    val batteryOk = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    val speechOk = c.speech.isAvailable()
    val voiceOk = c.speaker.voiceAvailable != false
    val keyOk = c.secrets.hasApiKey()
    val locale = LocaleHelper.locale(ctx)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)

            Step(micOk, stringResource(R.string.setup_mic), stringResource(R.string.setup_mic_hint), stringResource(R.string.allow)) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                Step(notifOk, stringResource(R.string.setup_notifications), stringResource(R.string.setup_notifications_hint), stringResource(R.string.allow)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                Step(alarmsOk, stringResource(R.string.setup_alarms), stringResource(R.string.setup_alarms_hint), stringResource(R.string.open)) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
                }
            }
            Step(batteryOk, stringResource(R.string.setup_battery), stringResource(R.string.setup_battery_hint), stringResource(R.string.allow)) {
                requestBatteryExemption(ctx)
            }
            Step(speechOk, stringResource(R.string.setup_speech), stringResource(R.string.setup_speech_hint), null) {}
            Step(true, stringResource(R.string.dialect), Locale.forLanguageTag(settings.dialect).getDisplayName(locale), stringResource(R.string.change)) { showDialect = true }
            Step(voiceOk, stringResource(R.string.setup_voice), stringResource(R.string.setup_voice_hint), stringResource(R.string.install)) {
                runCatching { ctx.startActivity(c.speaker.installVoiceIntent()) }
            }
            Step(keyOk, stringResource(R.string.gemini_key), stringResource(R.string.setup_key_hint), stringResource(R.string.open)) { onOpenSettings() }
            Step(settings.backupTreeUri.isNotBlank(), stringResource(R.string.backup_folder), stringResource(R.string.setup_backup_hint), stringResource(R.string.choose)) {
                folderPicker.launch(null)
            }

            Button(
                onClick = { scope.launch { c.settings.setSetupDone(true); c.checkInScheduler.scheduleAll(); onDone() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 10.dp),
            ) { Text(stringResource(R.string.setup_finish), style = MaterialTheme.typography.titleLarge) }
            Text("", Modifier.padding(bottom = 30.dp))
        }
    }

    if (showDialect) {
        ChoiceDialog(
            title = stringResource(R.string.dialect),
            options = SpeechCapture.DIALECTS.map { it to Locale.forLanguageTag(it).getDisplayName(locale) },
            selected = settings.dialect,
            onSelect = { scope.launch { c.settings.setDialect(it) } },
            onDismiss = { showDialect = false },
        )
    }
}

@Composable
private fun Step(ok: Boolean, title: String, hint: String, button: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(34.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (button != null && !ok) OutlinedButton(onClick = onClick) { Text(button) }
            if (button != null && ok && title == stringResource(R.string.dialect)) OutlinedButton(onClick = onClick) { Text(button) }
        }
    }
}

private fun granted(ctx: Context, permission: String) =
    ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(ctx: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    runCatching { ctx.startActivity(direct) }.onFailure {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}
