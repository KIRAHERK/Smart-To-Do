package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.db.OccurrenceWithTask
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.components.Banner
import io.github.kiraherk.smarttodo.ui.components.BigMicButton
import io.github.kiraherk.smarttodo.ui.components.EmptyState
import io.github.kiraherk.smarttodo.ui.components.SectionHeader
import io.github.kiraherk.smarttodo.ui.components.TaskCard
import io.github.kiraherk.smarttodo.ui.components.heroCircles
import io.github.kiraherk.smarttodo.ui.theme.LocalBrand
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import io.github.kiraherk.smarttodo.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

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
    val emptyTitle = stringResource(R.string.empty_today_title)
    val emptyHint = stringResource(R.string.empty_today_hint)
    val now = LocalTime.now()
    val next = todays.filter { it.occurrence.time != null && it.occurrence.time >= "%02d:%02d".format(now.hour, now.minute) }
        .minByOrNull { it.occurrence.time!! } ?: todays.firstOrNull()

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = { BigMicButton(onClick = { voice.start(VoiceViewModel.Request()) }) },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 130.dp),
        ) {
            item(key = "hero") {
                HeroCard(
                    today = today,
                    done = doneToday,
                    total = doneToday + todays.size,
                    next = next,
                    onRead = { vm.speakSummary() },
                    onReviewDay = { onNavigate(Routes.review(false)) },
                    onReviewWeek = { onNavigate(Routes.review(true)) },
                )
            }
            if (!vm.hasKey()) {
                item { Banner(Icons.Filled.Key, noKeyText, colors.error) { onNavigate(Routes.SETTINGS) } }
            }
            if (inboxCount > 0) {
                item { Banner(Icons.Filled.Inbox, pluralStringResource(R.plurals.inbox_waiting, inboxCount, inboxCount), colors.secondary) { onNavigate(Routes.INBOX) } }
            }
            item(key = "filter") { CategoryFilter(filter) { vm.filter.value = it } }

            if (items.isEmpty()) {
                item { EmptyState(emptyTitle, Icons.Filled.WbSunny, emptyHint) }
            }
            section(titles[0], overdue, colors.error, today, onOpenTask) { vm.markDone(it) }
            section(titles[1], todays, colors.primary, today, onOpenTask) { vm.markDone(it) }
            section(titles[2], upcoming, colors.secondary, today, onOpenTask) { vm.markDone(it) }
            section(titles[3], noDate, colors.outline, today, onOpenTask) { vm.markDone(it) }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<OccurrenceWithTask>,
    color: Color,
    today: LocalDate,
    onOpenTask: (Long) -> Unit,
    onDone: (Long) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionHeader(title, color, count = list.size) }
    items(list, key = { it.occurrence.id }) { item ->
        TaskCard(item, today, onClick = { onOpenTask(item.task.id) }, onDone = { onDone(item.occurrence.id) })
    }
}

/** The green card at the top: greeting, both calendars, today's progress, what's next, quick actions. */
@Composable
private fun HeroCard(
    today: LocalDate,
    done: Int,
    total: Int,
    next: OccurrenceWithTask?,
    onRead: () -> Unit,
    onReviewDay: () -> Unit,
    onReviewWeek: () -> Unit,
) {
    val ctx = LocalContext.current
    val brand = LocalBrand.current
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(brand.heroBrush)
            .heroCircles(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_title), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(Dates.fullDate(ctx, today), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                }
                Spacer(Modifier.width(12.dp))
                ProgressRing(done, total)
            }
            if (next != null) {
                Spacer(Modifier.size(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.next_up) + ":", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(6.dp))
                    Text(next.task.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Dates.parseTime(next.occurrence.time)?.let {
                        Text(Dates.friendlyTime(ctx, it), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.read_my_day_short), stringResource(R.string.read_my_day), onRead)
                QuickAction(Icons.Filled.TaskAlt, stringResource(R.string.review_today_short), stringResource(R.string.review_today), onReviewDay)
                QuickAction(Icons.Filled.DateRange, stringResource(R.string.review_week_short), stringResource(R.string.review_week), onReviewWeek)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickAction(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Circle that fills up as today's tasks get done. */
@Composable
private fun ProgressRing(done: Int, total: Int) {
    val brand = LocalBrand.current
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val gold = Color(0xFFF3D99B)
    Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(stroke))
            if (fraction > 0f) drawArc(if (brand.dark) brand.gold else gold, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (total > 0 && done >= total) {
            Icon(Icons.Filled.Done, contentDescription = stringResource(R.string.all_clear), tint = Color.White, modifier = Modifier.size(40.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$done/$total", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(stringResource(R.string.done_of), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
