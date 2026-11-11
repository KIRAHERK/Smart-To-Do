package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.db.OccurrenceEntity
import io.github.kiraherk.smarttodo.domain.ActivityType
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.NoteSource
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.activityName
import io.github.kiraherk.smarttodo.ui.categoryColor
import io.github.kiraherk.smarttodo.ui.categoryIcon
import io.github.kiraherk.smarttodo.ui.categoryName
import io.github.kiraherk.smarttodo.ui.components.ChoiceDialog
import io.github.kiraherk.smarttodo.ui.components.ConfirmDialog
import io.github.kiraherk.smarttodo.ui.components.SectionHeader
import io.github.kiraherk.smarttodo.ui.components.StatusPill
import io.github.kiraherk.smarttodo.ui.components.TextInputDialog
import io.github.kiraherk.smarttodo.ui.components.pickDate
import io.github.kiraherk.smarttodo.ui.components.pickTime
import io.github.kiraherk.smarttodo.ui.recurrenceText
import io.github.kiraherk.smarttodo.ui.statusColor
import io.github.kiraherk.smarttodo.ui.statusName
import io.github.kiraherk.smarttodo.ui.theme.BigText
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import io.github.kiraherk.smarttodo.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class TaskDetailViewModel(private val c: AppContainer, val taskId: Long) : ViewModel() {
    val task = c.repo.observeTask(taskId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val occurrences = c.repo.observeOccurrences(taskId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes = c.repo.observeNotes(taskId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activity = c.repo.observeActivity(taskId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val today: LocalDate = c.repo.today()

    fun setStatus(occurrenceId: Long, status: TaskStatus, progress: Int? = null) =
        viewModelScope.launch { c.repo.setStatus(occurrenceId, status, progress) }

    fun setCategory(key: CategoryKey) = viewModelScope.launch {
        c.repo.updateTask(taskId, category = key, logText = categoryName(c.localized(), key))
    }

    fun rename(title: String) = viewModelScope.launch {
        if (title.isNotBlank() && title != task.value?.title) c.repo.updateTask(taskId, title = title, logText = title)
    }

    fun setDate(date: LocalDate) = viewModelScope.launch { c.repo.reschedule(taskId, date, null) }
    fun setTime(time: LocalTime) = viewModelScope.launch { c.repo.reschedule(taskId, null, time) }

    fun setRecurrence(rule: RecurrenceRule?) = viewModelScope.launch {
        val ctx = c.localized()
        c.repo.changeRecurrence(taskId, rule, rule?.let { recurrenceText(ctx, it) } ?: ctx.getString(R.string.no_repeat))
    }

    fun setReminder(on: Boolean) = viewModelScope.launch { c.repo.setReminder(taskId, on) }
    fun archive() = viewModelScope.launch { c.repo.archive(taskId) }
    fun restore() = viewModelScope.launch { c.repo.restore(taskId) }
    fun addTypedNote(text: String) = viewModelScope.launch { c.repo.addNote(taskId, text, NoteSource.TEXT) }

    fun readAloud() {
        val t = task.value ?: return
        val ctx = c.localized()
        val current = currentOccurrence(occurrences.value)
        val parts = buildList {
            add(t.title)
            current?.let { o -> TaskStatus.parse(o.status)?.let { add(statusName(ctx, it)) } }
            Dates.parseDate(current?.date)?.let { add(Dates.friendlyDate(ctx, it, today)) }
            Dates.parseTime(current?.time)?.let { add(Dates.friendlyTime(ctx, it)) }
            RecurrenceRule.fromJson(t.recurrenceJson)?.let { add(recurrenceText(ctx, it)) }
            notes.value.take(3).forEach { add(it.text) }
        }
        c.speaker.speakNow(parts.joinToString(". "))
    }
}

/** The round that matters now: the earliest open one, otherwise the most recent. */
fun currentOccurrence(list: List<OccurrenceEntity>): OccurrenceEntity? =
    list.filter { it.isOpen }.minByOrNull { it.date ?: "9999" } ?: list.firstOrNull()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(taskId: Long, onBack: () -> Unit) {
    val c = appContainer()
    val vm: TaskDetailViewModel = viewModel(key = "task-$taskId") { TaskDetailViewModel(c, taskId) }
    val voice = LocalVoice.current
    val ctx = LocalContext.current
    val task by vm.task.collectAsStateWithLifecycle()
    val occurrences by vm.occurrences.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()

    var showCategory by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }
    var showPartly by remember { mutableStateOf<Long?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(false) }

    val t = task
    val current = currentOccurrence(occurrences)
    val category = CategoryKey.parse(t?.category)
    val rule = RecurrenceRule.fromJson(t?.recurrenceJson)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_details)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { vm.readAloud() }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.read_aloud)) }
                },
            )
        },
    ) { padding ->
        if (t == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Title + category
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(categoryIcon(category), contentDescription = null, tint = categoryColor(category), modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(t.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { editTitle = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_title), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (t.details.isNotBlank()) Text(t.details, style = MaterialTheme.typography.bodyLarge)
            if (t.archived) Text(stringResource(R.string.archived_note), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)

            // Voice buttons: full width, one above the other, so the labels stay on one line.
            Button(
                onClick = { voice.start(VoiceViewModel.Request(VoiceViewModel.Mode.NOTE, taskId = taskId)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            ) {
                Icon(Icons.Filled.EditNote, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_voice_note), style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { voice.start(VoiceViewModel.Request(VoiceViewModel.Mode.COMMAND, taskId = taskId)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.change_by_voice), style = MaterialTheme.typography.titleMedium)
            }

            // Status of the current round
            if (current != null) {
                SectionHeader(stringResource(R.string.status))
                val currentStatus = TaskStatus.parse(current.status) ?: TaskStatus.NOT_STARTED
                StatusPill(currentStatus, current.progress)
                current.statusNote?.takeIf { it.isNotBlank() }?.let { Text("📝 $it", style = MaterialTheme.typography.bodyLarge) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskStatus.entries.filter { it != TaskStatus.MISSED }.forEach { s ->
                        val selected = s == currentStatus
                        OutlinedButton(
                            onClick = { if (s == TaskStatus.PARTIALLY_DONE) showPartly = current.id else vm.setStatus(current.id, s) },
                            modifier = Modifier.heightIn(min = 52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) statusColor(s).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                contentColor = statusColor(s),
                            ),
                        ) { Text(statusName(ctx, s), style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }

            // When, repeat, reminder
            SectionHeader(stringResource(R.string.when_section))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    val date = Dates.parseDate(current?.date)
                    val time = Dates.parseTime(current?.time)
                    SettingRow(Icons.Filled.Event, stringResource(R.string.date), date?.let { Dates.friendlyDate(ctx, it, vm.today) } ?: stringResource(R.string.no_date)) {
                        pickDate(ctx, date ?: vm.today) { vm.setDate(it) }
                    }
                    SettingRow(Icons.Filled.Schedule, stringResource(R.string.time), time?.let { Dates.friendlyTime(ctx, it) } ?: stringResource(R.string.no_time)) {
                        pickTime(ctx, time ?: LocalTime.of(9, 0)) { vm.setTime(it) }
                    }
                    SettingRow(Icons.Filled.Repeat, stringResource(R.string.repeat), rule?.let { recurrenceText(ctx, it) } ?: stringResource(R.string.no_repeat)) { showRepeat = true }
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.reminder), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = t.reminderEnabled, onCheckedChange = { vm.setReminder(it) })
                    }
                    SettingRow(categoryIcon(category), stringResource(R.string.category), category?.let { categoryName(ctx, it) } ?: "—") { showCategory = true }
                }
            }

            // Notes
            SectionHeader(stringResource(R.string.notes) + " (${notes.size})")
            if (notes.isEmpty()) Text(stringResource(R.string.no_notes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            notes.forEach { n ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(Dates.dateTimeOf(ctx, n.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(n.text, style = BigText)
                    }
                }
            }

            // Past rounds of a repeating task
            if (occurrences.size > 1) {
                SectionHeader(stringResource(R.string.past_rounds))
                occurrences.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.parseDate(o.date)?.let { Dates.friendlyDate(ctx, it, vm.today) } ?: "—", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        StatusPill(TaskStatus.parse(o.status) ?: TaskStatus.NOT_STARTED, o.progress)
                    }
                }
            }

            // Full history
            SectionHeader(stringResource(R.string.history))
            activity.forEach { a ->
                val type = ActivityType.entries.firstOrNull { it.name == a.type }
                val to = TaskStatus.parse(a.toStatus)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(Dates.dateTimeOf(ctx, a.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        activityName(ctx, type) + (to?.let { " → " + statusName(ctx, it) } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = to?.let { statusColor(it) } ?: MaterialTheme.colorScheme.onSurface,
                    )
                    a.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                HorizontalDivider()
            }

            // Archive / restore
            if (t.archived) {
                AssistChip(onClick = { vm.restore() }, label = { Text(stringResource(R.string.restore_task)) }, leadingIcon = { Icon(Icons.Filled.Unarchive, null) })
            } else {
                AssistChip(onClick = { confirmArchive = true }, label = { Text(stringResource(R.string.delete_task)) }, leadingIcon = { Icon(Icons.Filled.Archive, null) })
            }
            Text("", Modifier.padding(bottom = 40.dp))
        }
    }

    if (showCategory) {
        ChoiceDialog(
            title = stringResource(R.string.category),
            options = CategoryKey.entries.map { it to categoryName(ctx, it) },
            selected = category,
            onSelect = { vm.setCategory(it) },
            onDismiss = { showCategory = false },
        )
    }
    if (showRepeat) {
        val anchor = Dates.parseDate(current?.date) ?: vm.today
        val options: List<Pair<RecurrenceRule?, String>> = listOf(
            null to stringResource(R.string.no_repeat),
            RecurrenceRule(Frequency.DAILY) to recurrenceText(ctx, RecurrenceRule(Frequency.DAILY)),
            RecurrenceRule(Frequency.WEEKLY, weekdays = listOf(anchor.dayOfWeek.value)).let { it to recurrenceText(ctx, it) },
            RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(anchor.dayOfWeek.value)).let { it to recurrenceText(ctx, it) },
            RecurrenceRule(Frequency.MONTHLY, dayOfMonth = anchor.dayOfMonth).let { it to recurrenceText(ctx, it) },
            RecurrenceRule(Frequency.YEARLY).let { it to recurrenceText(ctx, it) },
        )
        ChoiceDialog(
            title = stringResource(R.string.repeat),
            options = options,
            selected = rule,
            onSelect = { vm.setRecurrence(it) },
            onDismiss = { showRepeat = false },
        )
    }
    showPartly?.let { occurrenceId ->
        ChoiceDialog(
            title = stringResource(R.string.how_much_done),
            options = listOf(25, 50, 75).map { it to "$it%" },
            selected = null,
            onSelect = { vm.setStatus(occurrenceId, TaskStatus.PARTIALLY_DONE, it) },
            onDismiss = { showPartly = null },
        )
    }
    if (editTitle && t != null) {
        TextInputDialog(
            title = stringResource(R.string.edit_title),
            initial = t.title,
            onSave = { vm.rename(it) },
            onDismiss = { editTitle = false },
        )
    }
    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.delete_task),
            text = stringResource(R.string.delete_task_explain),
            confirm = stringResource(R.string.delete_task),
            onConfirm = { vm.archive(); onBack() },
            onDismiss = { confirmArchive = false },
        )
    }
}

@Composable
fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
