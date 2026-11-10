package io.github.kiraherk.smarttodo.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.db.OccurrenceWithTask
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.Priority
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.categoryColor
import io.github.kiraherk.smarttodo.ui.categoryIcon
import io.github.kiraherk.smarttodo.ui.categoryName
import io.github.kiraherk.smarttodo.ui.recurrenceText
import io.github.kiraherk.smarttodo.ui.statusColor
import io.github.kiraherk.smarttodo.ui.statusName
import io.github.kiraherk.smarttodo.util.Dates
import java.time.LocalDate
import java.time.LocalTime

/** The main button of the app: big, round, labelled. */
@Composable
fun BigMicButton(onClick: () -> Unit, label: String = stringResource(R.string.talk)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LargeFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(96.dp),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = label, modifier = Modifier.size(52.dp))
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), maxLines = 1)
    }
}

@Composable
fun StatusPill(status: TaskStatus, progress: Int = 0) {
    val ctx = LocalContext.current
    val extra = if (progress in 1..99 && (status == TaskStatus.PARTIALLY_DONE || status == TaskStatus.IN_PROGRESS)) " $progress%" else ""
    Pill(statusName(ctx, status) + extra, statusColor(status))
}

@Composable
fun CategoryPill(key: CategoryKey?) {
    val ctx = LocalContext.current
    Pill(key?.let { categoryName(ctx, it) } ?: "—", categoryColor(key))
}

@Composable
fun SectionHeader(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp))
}

/** One task in a list: colour bar for the category, big title, when, and a big "done" button. */
@Composable
fun TaskCard(item: OccurrenceWithTask, today: LocalDate, onClick: () -> Unit, onDone: (() -> Unit)?) {
    val ctx = LocalContext.current
    val category = CategoryKey.parse(item.task.category)
    val status = TaskStatus.parse(item.occurrence.status) ?: TaskStatus.NOT_STARTED
    val date = Dates.parseDate(item.occurrence.date)
    val time = Dates.parseTime(item.occurrence.time)
    val rule = RecurrenceRule.fromJson(item.task.recurrenceJson)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(categoryColor(category)))
            Column(Modifier.weight(1f).heightIn(min = 76.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(category), contentDescription = null, tint = categoryColor(category), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (item.task.priority == Priority.HIGH.name) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                val whenText = listOfNotNull(
                    date?.let { Dates.friendlyDate(ctx, it, today) },
                    time?.let { Dates.friendlyTime(ctx, it) },
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (whenText.isNotEmpty()) {
                        Text(whenText, style = MaterialTheme.typography.bodyMedium, color = if (date != null && date < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (rule != null) Icon(Icons.Filled.Repeat, contentDescription = recurrenceText(ctx, rule), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.task.reminderEnabled) Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (status != TaskStatus.NOT_STARTED) StatusPill(status, item.occurrence.progress)
                }
            }
            if (onDone != null) {
                FilledIconButton(
                    onClick = onDone,
                    modifier = Modifier.padding(end = 10.dp).size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = statusColor(TaskStatus.DONE).copy(alpha = 0.15f), contentColor = statusColor(TaskStatus.DONE)),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.mark_done), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A simple list of choices with radio buttons (no typing). */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelect(value); onDismiss() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value); onDismiss() })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, false).show()
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
