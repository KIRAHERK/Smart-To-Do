package io.github.kiraherk.smarttodo.ui.screens

import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.DeleteSweep

import androidx.compose.material.icons.filled.RadioButtonUnchecked

import androidx.compose.material.icons.filled.CheckCircle

import androidx.compose.foundation.background

import androidx.compose.foundation.combinedClickable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.db.ActivityWithTask
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.domain.ActivityType
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.activityName
import io.github.kiraherk.smarttodo.ui.components.AppCard
import io.github.kiraherk.smarttodo.ui.components.IconTile
import io.github.kiraherk.smarttodo.ui.components.Pill
import io.github.kiraherk.smarttodo.ui.components.ScreenHeader
import io.github.kiraherk.smarttodo.ui.theme.LocalBrand
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import io.github.kiraherk.smarttodo.ui.components.EmptyState
import io.github.kiraherk.smarttodo.ui.components.SectionHeader
import io.github.kiraherk.smarttodo.ui.statusColor
import io.github.kiraherk.smarttodo.ui.statusName
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import io.github.kiraherk.smarttodo.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val days = MutableStateFlow(30)
    val filter = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    val today: LocalDate = c.repo.today()

    /** A status name, or [DELETED]; null = all. */
    val statusFilter = MutableStateFlow<String?>(null)

    val history = combine(days.flatMapLatest { d -> c.repo.observeHistory(System.currentTimeMillis() - d * 86_400_000L) }, filter, statusFilter) { list, f, s ->
        Triple(list, f, s)
    }.mapLatest { (list, f, s) ->
        HistoryGrouping.group(list) { taskId, occurrenceId ->
            (occurrenceId?.let { c.db.occurrences().get(it) } ?: c.repo.currentOccurrence(taskId))
        }.filter { (f == null || it.task.category == f) && (s == null || it.statusKey == s) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Takes the chosen lines out of the History (after he confirmed). */
    fun remove(items: List<HistoryItem>) = viewModelScope.launch { c.repo.removeFromHistory(items.map { it.target }) }

    /** Empties the whole History (after he confirmed). */
    fun clearAll() = viewModelScope.launch { c.repo.clearHistory() }

    val results = query.debounce(250).mapLatest { q -> if (q.isBlank()) emptyList() else c.repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<TaskRepository.SearchHit>())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenTask: (Long) -> Unit) {
    val c = appContainer()
    val vm: HistoryViewModel = viewModel { HistoryViewModel(c) }
    val voice = LocalVoice.current
    val ctx = LocalContext.current
    val history by vm.history.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { voice.searchText.collect { vm.query.value = it } }

    val zone = ZoneId.systemDefault()
    val statusFilter by vm.statusFilter.collectAsStateWithLifecycle()
    // Press and hold a line to start choosing; then tap more lines. Deleting always asks first.
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var askDelete by remember { mutableStateOf(false) }
    var askClearAll by remember { mutableStateOf(false) }
    LaunchedEffect(history) { selected.retainAll(history.map { it.key }.toSet()) }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }
    val byDay = history.groupBy { Instant.ofEpochMilli(it.latest.at).atZone(zone).toLocalDate() }
    val noResults = stringResource(R.string.no_results)
    val emptyHistory = stringResource(R.string.empty_history)
    val showMore = stringResource(R.string.show_more)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionBar(
                    count = selected.size,
                    allSelected = selected.size == history.size,
                    onClose = { selected.clear() },
                    onSelectAll = { selected.clear(); selected.addAll(history.map { it.key }) },
                    onDelete = { askDelete = true },
                )
            } else {
                ScreenHeader(stringResource(R.string.history_title)) {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { askClearAll = true }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear_all), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    trailingIcon = {
                        Row {
                            if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear)) }
                            IconButton(onClick = { voice.start(VoiceViewModel.Request(VoiceViewModel.Mode.SEARCH)) }) {
                                Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.search_by_voice), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                )
            }
            item { CategoryFilter(filter) { vm.filter.value = it } }
            item { StatusFilter(statusFilter) { vm.statusFilter.value = it } }

            if (query.isNotBlank()) {
                if (results.isEmpty()) item { EmptyState(noResults, Icons.Filled.Search) }
                items(results, key = { "r-${it.task.id}" }) { hit ->
                    val cat = io.github.kiraherk.smarttodo.ui.categoryOf(hit.task.category)
                    AppCard(modifier = Modifier.padding(vertical = 5.dp), onClick = { onOpenTask(hit.task.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(cat.icon, cat.color)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(hit.task.title, style = MaterialTheme.typography.titleMedium)
                                hit.note?.let { Text("📝 " + it.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3) }
                            }
                        }
                    }
                }
            } else {
                if (history.isEmpty()) item { EmptyState(emptyHistory, Icons.Filled.History) }
                byDay.forEach { (day, entries) ->
                    item(key = "d-$day") {
                        val done = entries.count { it.status == TaskStatus.DONE }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader(Dates.friendlyDate(ctx, day, vm.today), MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                if (done > 0) {
                                    Pill(pluralStringResource(R.plurals.done_count, done, done), LocalBrand.current.success, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                            AppCard(padding = PaddingValues(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 2.dp)) {
                                entries.forEachIndexed { i, entry ->
                                    val isSelected = entry.key in selected
                                    fun toggle() { if (isSelected) selected.remove(entry.key) else selected.add(entry.key) }
                                    HistoryRow(
                                        entry,
                                        last = i == entries.lastIndex,
                                        selecting = selected.isNotEmpty(),
                                        selected = isSelected,
                                        onLongClick = { toggle() },
                                    ) { if (selected.isNotEmpty()) toggle() else onOpenTask(entry.task.id) }
                                }
                            }
                        }
                    }
                }
                if (history.isNotEmpty()) {
                    item {
                        OutlinedButton(onClick = { vm.days.value = days + 30 }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 14.dp)) {
                            Text(showMore, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    if (askDelete) {
        val chosen = history.filter { it.key in selected }
        io.github.kiraherk.smarttodo.ui.components.ConfirmDialog(
            title = pluralStringResource(R.plurals.history_delete_title, chosen.size, chosen.size),
            text = stringResource(R.string.history_delete_explain),
            confirm = stringResource(R.string.delete),
            onConfirm = { vm.remove(chosen); selected.clear() },
            onDismiss = { askDelete = false },
        )
    }
    if (askClearAll) {
        io.github.kiraherk.smarttodo.ui.components.ConfirmDialog(
            title = stringResource(R.string.history_clear_all),
            text = stringResource(R.string.history_clear_all_explain),
            confirm = stringResource(R.string.history_clear_all),
            onConfirm = { vm.clearAll() },
            onDismiss = { askClearAll = false },
        )
    }
}

/** The extra line under a history entry, or null when it would only repeat the task's name. */
fun historyText(a: io.github.kiraherk.smarttodo.data.db.ActivityEntity, title: String): String? {
    val text = a.text?.trim().orEmpty()
    if (text.isEmpty() || text == title.trim()) return null
    // Added / deleted / restored already say what happened; older versions stored the read-back sentence there.
    if (a.type in setOf(ActivityType.CREATED.name, ActivityType.ARCHIVED.name, ActivityType.RESTORED.name)) return null
    return text
}

@Composable
private fun HistoryRow(
    entry: HistoryItem,
    last: Boolean,
    selecting: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val a = entry.latest
    val type = ActivityType.entries.firstOrNull { it.name == a.type }
    val cat = io.github.kiraherk.smarttodo.ui.categoryOf(entry.task.category)
    val time = Instant.ofEpochMilli(a.at).atZone(ZoneId.systemDefault()).toLocalTime()
    val deletedColor = MaterialTheme.colorScheme.error
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() },
                onLongClickLabel = stringResource(R.string.history_select),
            ),
    ) {
        TimelineRow(if (entry.deleted) deletedColor else entry.status?.let { statusColor(it) } ?: cat.color, last) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = stringResource(if (selected) R.string.selected else R.string.not_selected),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(entry.task.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(Dates.friendlyTime(ctx, time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(cat.icon, contentDescription = null, tint = cat.color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                // Where the task stands now; one row per task, however often it changed.
                when {
                    entry.deleted -> Pill(stringResource(R.string.status_deleted), deletedColor, icon = Icons.Filled.Delete)
                    entry.status != null -> Pill(
                        statusName(ctx, entry.status) + (entry.progress.takeIf { it in 1..99 && entry.status.isOpen }?.let { " $it%" } ?: ""),
                        statusColor(entry.status),
                        icon = io.github.kiraherk.smarttodo.ui.statusIcon(entry.status),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    activityName(ctx, type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            historyText(a, entry.task.title)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
            if (entry.earlier > 0) {
                Text(
                    pluralStringResource(R.plurals.earlier_changes, entry.earlier, entry.earlier),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** One line in the history list: a task (or one round of a repeating task) and where it stands now. */
data class HistoryItem(
    val task: io.github.kiraherk.smarttodo.data.db.TaskEntity,
    val status: TaskStatus?,
    val progress: Int,
    val deleted: Boolean,
    /** The newest change, shown in the row. */
    val latest: io.github.kiraherk.smarttodo.data.db.ActivityEntity,
    /** How many older changes are folded into it (the task page shows them all). */
    val earlier: Int,
    /** For a repeating task: the round (day) this line is about. */
    val roundId: Long? = null,
) {
    val statusKey: String? get() = if (deleted) HistoryGrouping.DELETED else status?.name
    val key: String get() = "${task.id}-${roundId ?: 0}"
    val target get() = TaskRepository.HistoryTarget(task.id, roundId)
}

object HistoryGrouping {
    const val DELETED = "DELETED"

    /**
     * Folds the change log into one item per task, newest first; a repeating task gets one item per
     * round (day), so each day keeps its own result. [round] finds the round's current state.
     */
    suspend fun group(
        log: List<ActivityWithTask>,
        round: suspend (taskId: Long, occurrenceId: Long?) -> io.github.kiraherk.smarttodo.data.db.OccurrenceEntity?,
    ): List<HistoryItem> {
        val newestFirst = log.sortedWith(compareByDescending<ActivityWithTask> { it.activity.at }.thenByDescending { it.activity.id })
        // Changes to a repeating task that aren't tied to a round (renamed, deleted...) join its newest round.
        val newestRound = HashMap<Long, Long>()
        newestFirst.forEach { e -> e.activity.occurrenceId?.let { newestRound.putIfAbsent(e.task.id, it) } }
        val groups = LinkedHashMap<String, MutableList<ActivityWithTask>>()
        newestFirst.forEach { e ->
            val key = if (e.task.recurrenceJson == null) {
                "t${e.task.id}"
            } else {
                "o${e.activity.occurrenceId ?: newestRound[e.task.id] ?: "t${e.task.id}"}"
            }
            groups.getOrPut(key) { mutableListOf() }.add(e)
        }
        return groups.values.map { entries ->
            val first = entries.first()
            val occurrenceId = if (first.task.recurrenceJson == null) null else entries.firstNotNullOfOrNull { it.activity.occurrenceId }
            val o = round(first.task.id, occurrenceId)
            HistoryItem(
                task = first.task,
                status = TaskStatus.parse(o?.status),
                progress = o?.progress ?: 0,
                deleted = first.task.archived,
                latest = first.activity,
                earlier = entries.size - 1,
                roundId = occurrenceId,
            )
        }
    }
}

/** Shown instead of the title while he is choosing lines to delete. */
@Composable
private fun SelectionBar(count: Int, allSelected: Boolean, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(28.dp))
        }
        Text(
            pluralStringResource(R.plurals.history_selected, count, count),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        if (!allSelected) {
            androidx.compose.material3.TextButton(onClick = onSelectAll, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.select_all), style = MaterialTheme.typography.titleSmall)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
        }
    }
}
