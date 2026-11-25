package io.github.kiraherk.smarttodo.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ai.AiAction
import io.github.kiraherk.smarttodo.ai.AiRecurrence
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.ui.currentCategories
import io.github.kiraherk.smarttodo.ui.categoryOf
import io.github.kiraherk.smarttodo.ui.components.ChipFlow
import io.github.kiraherk.smarttodo.ui.components.ChoiceDialog
import io.github.kiraherk.smarttodo.ui.components.TextInputDialog
import io.github.kiraherk.smarttodo.ui.components.pickDate
import io.github.kiraherk.smarttodo.ui.components.pickTime
import io.github.kiraherk.smarttodo.ui.recurrenceText
import io.github.kiraherk.smarttodo.util.Dates
import java.time.LocalDate
import java.time.LocalTime

/**
 * On the confirmation card of a new task: every detail the AI understood can be changed with a tap
 * before saving (name, category, day, time, repeat, reminder).
 */
@Composable
fun NewTaskEditor(action: AiAction, onChange: (AiAction) -> Unit) {
    val ctx = LocalContext.current
    val categories = currentCategories()
    var dialog by remember { mutableStateOf<String?>(null) }
    val date = Dates.parseDate(action.date)
    val time = Dates.parseTime(action.time)
    val rule = action.recurrence?.toRule()
    val cat = categoryOf(action.category)
    val primary = MaterialTheme.colorScheme.primary

    ChipFlow {
        EditChip(Icons.Filled.Edit, action.title.orEmpty().ifBlank { "…" }, primary) { dialog = "title" }
        EditChip(cat.icon, cat.name, cat.color) { dialog = "category" }
        EditChip(Icons.Filled.Event, date?.let { Dates.friendlyDate(ctx, it) } ?: stringResource(R.string.no_date), primary,
            onClear = if (date != null) ({ onChange(action.copy(date = null)) }) else null) {
            pickDate(ctx, date ?: LocalDate.now()) { onChange(action.copy(date = it.toString())) }
        }
        EditChip(Icons.Filled.Schedule, time?.let { Dates.friendlyTime(ctx, it) } ?: stringResource(R.string.no_time), primary,
            onClear = if (time != null) ({ onChange(action.copy(time = null)) }) else null) {
            pickTime(ctx, time ?: LocalTime.of(9, 0)) { onChange(action.copy(time = Dates.formatTime(it))) }
        }
        EditChip(Icons.Filled.Repeat, rule?.let { recurrenceText(ctx, it) } ?: stringResource(R.string.no_repeat), primary) { dialog = "repeat" }
        val on = action.reminder == true
        EditChip(if (on) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
            stringResource(if (on) R.string.reminder_on else R.string.reminder_off), if (on) primary else MaterialTheme.colorScheme.outline) {
            onChange(action.copy(reminder = !on))
        }
    }

    when (dialog) {
        "title" -> TextInputDialog(
            title = stringResource(R.string.edit_title),
            initial = action.title.orEmpty(),
            onSave = { onChange(action.copy(title = it)) },
            onDismiss = { dialog = null },
        )
        "category" -> ChoiceDialog(
            title = stringResource(R.string.category),
            options = categories.map { it.key to it.name },
            selected = action.category,
            onSelect = { onChange(action.copy(category = it)) },
            onDismiss = { dialog = null },
        )
        "repeat" -> {
            val day = (date ?: LocalDate.now())
            val options: List<Pair<RecurrenceRule?, String>> = listOf(
                null to stringResource(R.string.no_repeat),
                RecurrenceRule(Frequency.DAILY).let { it to recurrenceText(ctx, it) },
                RecurrenceRule(Frequency.WEEKLY, weekdays = listOf(day.dayOfWeek.value)).let { it to recurrenceText(ctx, it) },
                RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = listOf(day.dayOfWeek.value)).let { it to recurrenceText(ctx, it) },
                RecurrenceRule(Frequency.MONTHLY, dayOfMonth = day.dayOfMonth).let { it to recurrenceText(ctx, it) },
                RecurrenceRule(Frequency.YEARLY).let { it to recurrenceText(ctx, it) },
            )
            ChoiceDialog(
                title = stringResource(R.string.repeat),
                options = options,
                selected = rule,
                onSelect = { r ->
                    onChange(action.copy(recurrence = r?.let { AiRecurrence(it.frequency.name, it.interval, it.weekdays, it.dayOfMonth, it.endDate) }))
                },
                onDismiss = { dialog = null },
            )
        }
    }
}

@Composable
private fun EditChip(icon: ImageVector, text: String, color: Color, onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)
            .heightIn(min = 44.dp).padding(start = 10.dp, end = if (onClear != null) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        if (onClear != null) {
            Icon(
                Icons.Filled.Close, contentDescription = stringResource(R.string.clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(30.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClear).padding(6.dp),
            )
        }
    }
}
