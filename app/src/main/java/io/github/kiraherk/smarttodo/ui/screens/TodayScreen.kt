package io.github.kiraherk.smarttodo.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.components.BigMicButton
import io.github.kiraherk.smarttodo.ui.components.EmptyState
import io.github.kiraherk.smarttodo.ui.components.SectionHeader
import io.github.kiraherk.smarttodo.ui.components.TaskCard
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import io.github.kiraherk.smarttodo.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(private val c: AppContainer) : ViewModel() {
    val today = c.repo.today()
    val filter = MutableStateFlow<CategoryKey?>(null)
    val open = combine(c.repo.observeOpen(), filter) { list, f -> if (f == null) list else list.filter { it.task.category == f.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val doneToday = c.repo.observeBetween(today, today).map { list -> list.count { it.occurrence.status == TaskStatus.DONE.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val inboxCount = c.db.inbox().observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun hasKey() = c.secrets.hasApiKey()
    fun markDone(occurrenceId: Long) = viewModelScope.launch { c.repo.setStatus(occurrenceId, TaskStatus.DONE) }
    fun speakSummary() = viewModelScope.launch { c.speaker.speakNow(c.checkIns.morningText(spoken = true)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenTask: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    speakSummaryNow: Boolean,
    onSummarySpoken: () -> Unit,
) {
    val c = appContainer()
    val vm: TodayViewModel = viewModel { TodayViewModel(c) }
    val voice = LocalVoice.current
    val ctx = LocalContext.current
    val items by vm.open.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val doneToday by vm.doneToday.collectAsStateWithLifecycle()
    val inboxCount by vm.inboxCount.collectAsStateWithLifecycle()
    val today = vm.today
    val todayIso = today.toString()

    LaunchedEffect(speakSummaryNow) {
        if (speakSummaryNow) {
            vm.speakSummary()
            onSummarySpoken()
        }
    }

    val overdue = items.filter { it.occurrence.date != null && it.occurrence.date < todayIso }
    val todays = items.filter { it.occurrence.date == todayIso }
    val upcoming = items.filter { it.occurrence.date != null && it.occurrence.date > todayIso }
    val noDate = items.filter { it.occurrence.date == null }
    val colors = MaterialTheme.colorScheme
    val titles = listOf(
        stringResource(R.string.section_overdue),
        stringResource(R.string.section_today),
        stringResource(R.string.section_upcoming),
        stringResource(R.string.section_no_date),
    )
    val noKeyText = stringResource(R.string.banner_no_key)
    val emptyText = stringResource(R.string.empty_today)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleLarge)
                        Text(Dates.fullDate(ctx, today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.speakSummary() }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.read_my_day)) }
                    IconButton(onClick = { onNavigate(Routes.review(false)) }) { Icon(Icons.Filled.TaskAlt, contentDescription = stringResource(R.string.review_today)) }
                    IconButton(onClick = { onNavigate(Routes.review(true)) }) { Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.review_week)) }
                },
            )
        },
        floatingActionButton = { BigMicButton(onClick = { voice.start(VoiceViewModel.Request()) }) },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 160.dp),
        ) {
            if (!vm.hasKey()) {
                item { Banner(Icons.Filled.Key, noKeyText, colors.errorContainer) { onNavigate(Routes.SETTINGS) } }
            }
            if (inboxCount > 0) {
                item { Banner(Icons.Filled.Inbox, pluralStringResource(R.plurals.inbox_waiting, inboxCount, inboxCount), colors.tertiaryContainer) { onNavigate(Routes.INBOX) } }
            }
            if (doneToday > 0) {
                item {
                    Text(
                        pluralStringResource(R.plurals.done_today, doneToday, doneToday),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }
            item { CategoryFilter(filter) { vm.filter.value = it } }

            if (items.isEmpty()) {
                item { EmptyState(emptyText) }
            }
            section(titles[0], overdue, colors.error, today, onOpenTask) { vm.markDone(it) }
            section(titles[1], todays, colors.primary, today, onOpenTask) { vm.markDone(it) }
            section(titles[2], upcoming, colors.secondary, today, onOpenTask) { vm.markDone(it) }
            section(titles[3], noDate, colors.secondary, today, onOpenTask) { vm.markDone(it) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<io.github.kiraherk.smarttodo.data.db.OccurrenceWithTask>,
    color: Color,
    today: java.time.LocalDate,
    onOpenTask: (Long) -> Unit,
    onDone: (Long) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionHeader("$title (${list.size})", color) }
    items(list, key = { it.occurrence.id }) { item ->
        TaskCard(item, today, onClick = { onOpenTask(item.task.id) }, onDone = { onDone(item.occurrence.id) })
    }
}

@Composable
fun Banner(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text("  $text", style = MaterialTheme.typography.titleMedium)
        }
    }
}
