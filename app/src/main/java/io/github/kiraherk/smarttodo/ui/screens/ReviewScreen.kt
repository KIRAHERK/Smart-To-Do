package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.checkins.CheckIns
import io.github.kiraherk.smarttodo.data.db.OccurrenceWithTask
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.components.BigMicButton
import io.github.kiraherk.smarttodo.ui.components.EmptyState
import io.github.kiraherk.smarttodo.ui.components.SectionHeader
import io.github.kiraherk.smarttodo.ui.components.StatusPill
import io.github.kiraherk.smarttodo.ui.components.TaskCard
import io.github.kiraherk.smarttodo.ui.statusColor
import io.github.kiraherk.smarttodo.ui.statusName
import io.github.kiraherk.smarttodo.ui.theme.BigText
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    val today = c.repo.today()
    val week = MutableStateFlow<CheckIns.Week?>(null)
    val todayItems = c.repo.observeBetween(today, today)
        .onEach { refreshWeek() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val overdue = c.repo.observeOpen()
        .map { list -> list.filter { it.occurrence.date != null && it.occurrence.date < today.toString() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val nextWeek = c.repo.observeBetween(today.plusDays(1), today.plusDays(7))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun refreshWeek() = viewModelScope.launch { week.value = c.checkIns.week() }
    fun setStatus(occurrenceId: Long, status: TaskStatus) = viewModelScope.launch { c.repo.setStatus(occurrenceId, status) }
    fun speakWeek() = viewModelScope.launch { c.speaker.speakNow(c.checkIns.weekText()) }
}

/** Evening review (weekly = false) or weekly review (weekly = true). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(weekly: Boolean, onBack: () -> Unit, onOpenTask: (Long) -> Unit) {
    val c = appContainer()
    val vm: ReviewViewModel = viewModel { ReviewViewModel(c) }
    val voice = LocalVoice.current
    val todayItems by vm.todayItems.collectAsStateWithLifecycle()
    val overdue by vm.overdue.collectAsStateWithLifecycle()
    val nextWeek by vm.nextWeek.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()

    val titles = listOf(
        stringResource(R.string.section_today),
        stringResource(R.string.section_overdue),
        stringResource(R.string.next_7_days),
    )
    val hint = stringResource(if (weekly) R.string.weekly_hint else R.string.evening_hint)
    val nothing = stringResource(R.string.nothing_today)
    val weekSummary = week?.let { stringResource(R.string.week_summary, it.done, it.partly, it.notDone, it.missed, it.open) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (weekly) R.string.review_week else R.string.review_today)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = { if (weekly) IconButton(onClick = { vm.speakWeek() }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.read_aloud)) } },
            )
        },
        floatingActionButton = {
            BigMicButton(
                onClick = { voice.start(VoiceViewModel.Request(if (weekly) VoiceViewModel.Mode.COMMAND else VoiceViewModel.Mode.REVIEW)) },
                label = stringResource(if (weekly) R.string.plan_by_voice else R.string.tell_me),
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 160.dp),
        ) {
            item { Text(hint, style = BigText, modifier = Modifier.padding(vertical = 10.dp)) }
            if (weekly && weekSummary != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Text(weekSummary, style = BigText, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            if (!weekly) {
                item { SectionHeader(titles[0]) }
                if (todayItems.isEmpty()) item { EmptyState(nothing) }
                items(todayItems, key = { "t-${it.occurrence.id}" }) { ReviewRow(it, onOpenTask, vm::setStatus) }
            }
            if (overdue.isNotEmpty()) {
                item { SectionHeader(titles[1]) }
                items(overdue, key = { "o-${it.occurrence.id}" }) { ReviewRow(it, onOpenTask, vm::setStatus) }
            }
            if (weekly && nextWeek.isNotEmpty()) {
                item { SectionHeader(titles[2]) }
                items(nextWeek, key = { "n-${it.occurrence.id}" }) { TaskCard(it, vm.today, onClick = { onOpenTask(it.task.id) }, onDone = null) }
            }
        }
    }
}

/** A task with three big buttons: Done / Partly / Not done. */
@Composable
private fun ReviewRow(item: OccurrenceWithTask, onOpenTask: (Long) -> Unit, setStatus: (Long, TaskStatus) -> Unit) {
    val ctx = LocalContext.current
    val status = TaskStatus.parse(item.occurrence.status) ?: TaskStatus.NOT_STARTED
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenTask(item.task.id) }) {
                Text(item.task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill(status, item.occurrence.progress)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskStatus.DONE, TaskStatus.PARTIALLY_DONE, TaskStatus.NOT_DONE).forEach { s ->
                    OutlinedButton(
                        onClick = { setStatus(item.occurrence.id, s) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        contentPadding = PaddingValues(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (s == status) statusColor(s).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                            contentColor = statusColor(s),
                        ),
                    ) { Text(statusName(ctx, s), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                }
            }
        }
    }
}
