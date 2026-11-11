package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import io.github.kiraherk.smarttodo.domain.Priority
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.activityName
import io.github.kiraherk.smarttodo.ui.categoryColor
import io.github.kiraherk.smarttodo.ui.categoryIcon
import io.github.kiraherk.smarttodo.ui.categoryName
import io.github.kiraherk.smarttodo.ui.components.AppCard
import io.github.kiraherk.smarttodo.ui.components.ChipFlow
import io.github.kiraherk.smarttodo.ui.components.ChoiceDialog
import io.github.kiraherk.smarttodo.ui.components.ConfirmDialog
import io.github.kiraherk.smarttodo.ui.components.IconTile
import io.github.kiraherk.smarttodo.ui.components.Pill
import io.github.kiraherk.smarttodo.ui.components.ScreenHeader
import io.github.kiraherk.smarttodo.ui.components.SectionHeader
import io.github.kiraherk.smarttodo.ui.components.SettingRow
import io.github.kiraherk.smarttodo.ui.components.StatusPill
import io.github.kiraherk.smarttodo.ui.components.SwitchRow
import io.github.kiraherk.smarttodo.ui.components.TextInputDialog
import io.github.kiraherk.smarttodo.ui.components.pickDate
import io.github.kiraherk.smarttodo.ui.components.pickTime
import io.github.kiraherk.smarttodo.ui.components.tint
import io.github.kiraherk.smarttodo.ui.recurrenceText
import io.github.kiraherk.smarttodo.ui.statusColor
import io.github.kiraherk.smarttodo.ui.statusIcon
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
    val catColor = categoryColor(category)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenHeader(stringResource(R.string.task_details), onBack = onBack) {
                IconButton(onClick = { vm.readAloud() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.read_aloud), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
    ) { padding ->
        if (t == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: category colour wash, big icon, name and status.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(tint(catColor, 0.12f, 0.24f)).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    IconTile(categoryIcon(category), catColor, size = 58.dp, iconSize = 32.dp, filled = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category?.let { categoryName(ctx, it) } ?: "", style = MaterialTheme.typography.labelLarge, color = catColor)
                        Text(t.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { editTitle = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_title), tint = catColor)
                    }
                }
                if (t.details.isNotBlank()) {
                    Text(t.details, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    current?.let { o -> StatusPill(TaskStatus.parse(o.status) ?: TaskStatus.NOT_STARTED, o.progress) }
                    if (t.priority == Priority.HIGH.name) Pill(stringResource(R.string.important), MaterialTheme.colorScheme.tertiary)
                    if (t.archived) Pill(stringResource(R.string.archived_note), MaterialTheme.colorScheme.error)
                }
            }

            // Voice buttons: full width, one above the other, so the labels stay on one line.
            Button(
                onClick = { voice.start(VoiceViewModel.Request(VoiceViewModel.Mode.NOTE, taskId = taskId)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.add_voice_note), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = { voice.start(VoiceViewModel.Request(VoiceViewModel.Mode.COMMAND, taskId = taskId)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.change_by_voice), style = MaterialTheme.typography.titleMedium)
            }

            // Status of the current round
            if (current != null) {
                SectionHeader(stringResource(R.string.status), MaterialTheme.colorScheme.primary)
                val currentStatus = TaskStatus.parse(current.status) ?: TaskStatus.NOT_STARTED
                current.statusNote?.takeIf { it.isNotBlank() }?.let {
                    Text("📝 $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp))
                }
                ChipFlow {
                    TaskStatus.entries.filter { it != TaskStatus.MISSED }.forEach { s ->
                        val selected = s == currentStatus
                        val color = statusColor(s)
                        OutlinedButton(
                            onClick = { if (s == TaskStatus.PARTIALLY_DONE) showPartly = current.id else vm.setStatus(current.id, s) },
                            modifier = Modifier.heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) tint(color, 0.16f, 0.30f) else MaterialTheme.colorScheme.surface,
                                contentColor = color,
                            ),
                        ) {
                            Icon(statusIcon(s), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(statusName(ctx, s), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            // When, repeat, reminder, category
            SectionHeader(stringResource(R.string.when_section), MaterialTheme.colorScheme.primary)
            AppCard(padding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                val date = Dates.parseDate(current?.date)
                val time = Dates.parseTime(current?.time)
                SettingRow(Icons.Filled.Event, stringResource(R.string.date), date?.let { Dates.friendlyDate(ctx, it, vm.today) } ?: stringResource(R.string.no_date)) {
                    pickDate(ctx, date ?: vm.today) { vm.setDate(it) }
                }
                SettingRow(Icons.Filled.Schedule, stringResource(R.string.time), time?.let { Dates.friendlyTime(ctx, it) } ?: stringResource(R.string.no_time)) {
                    pickTime(ctx, time ?: LocalTime.of(9, 0)) { vm.setTime(it) }
                }
                SettingRow(Icons.Filled.Repeat, stringResource(R.string.repeat), rule?.let { recurrenceText(ctx, it) } ?: stringResource(R.string.no_repeat)) { showRepeat = true }
                SwitchRow(Icons.Filled.NotificationsActive, stringResource(R.string.reminder), t.reminderEnabled) { vm.setReminder(it) }
                SettingRow(categoryIcon(category), stringResource(R.string.category), category?.let { categoryName(ctx, it) } ?: "—", color = catColor) { showCategory = true }
            }

            // Notes
            SectionHeader(stringResource(R.string.notes), MaterialTheme.colorScheme.secondary, count = notes.size)
            if (notes.isEmpty()) Text(stringResource(R.string.no_notes), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            notes.forEach { n ->
                AppCard {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.secondary))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(Dates.dateTimeOf(ctx, n.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(n.text, style = BigText)
                        }
                    }
                }
            }

            // Past rounds of a repeating task
            if (occurrences.size > 1) {
                SectionHeader(stringResource(R.string.past_rounds), MaterialTheme.colorScheme.primary)
                AppCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    occurrences.forEach { o ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Dates.parseDate(o.date)?.let { Dates.friendlyDate(ctx, it, vm.today) } ?: "—", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            StatusPill(TaskStatus.parse(o.status) ?: TaskStatus.NOT_STARTED, o.progress)
                        }
                    }
                }
            }

            // Full history as a timeline
            SectionHeader(stringResource(R.string.history), MaterialTheme.colorScheme.primary)
            AppCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                activity.forEachIndexed { i, a ->
                    val type = ActivityType.entries.firstOrNull { it.name == a.type }
                    val to = TaskStatus.parse(a.toStatus)
                    val dot = to?.let { statusColor(it) } ?: MaterialTheme.colorScheme.primary
                    TimelineRow(dot, last = i == activity.lastIndex) {
                        Text(Dates.dateTimeOf(ctx, a.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            activityName(ctx, type) + (to?.let { " → " + statusName(ctx, it) } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                            color = to?.let { statusColor(it) } ?: MaterialTheme.colorScheme.onSurface,
                        )
                        a.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            // Delete / restore
            if (t.archived) {
                TextButton(onClick = { vm.restore() }, modifier = Modifier.heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.Unarchive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.restore_task), style = MaterialTheme.typography.titleSmall)
                }
            } else {
                TextButton(onClick = { confirmArchive = true }, modifier = Modifier.heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_task), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(32.dp))
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

/** One step in a vertical timeline: a coloured dot with a line down to the next step. */
@Composable
fun TimelineRow(dot: Color, last: Boolean, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 14.dp), content = content)
    }
}
