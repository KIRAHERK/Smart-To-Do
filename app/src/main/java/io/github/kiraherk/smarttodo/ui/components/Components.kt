package io.github.kiraherk.smarttodo.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import io.github.kiraherk.smarttodo.ui.theme.LocalBrand
import io.github.kiraherk.smarttodo.util.Dates
import java.time.LocalDate
import java.time.LocalTime

/** A soft-tinted version of [color] for backgrounds (works in light and dark mode). */
@Composable
fun tint(color: Color, light: Float = 0.12f, dark: Float = 0.22f): Color = color.copy(alpha = if (LocalBrand.current.dark) dark else light)

/**
 * The main button of the app: a wide emerald pill with the microphone, gently glowing so it is
 * always easy to find. Floats above the content at the bottom of the screen.
 */
@Composable
fun BigMicButton(onClick: () -> Unit, label: String = stringResource(R.string.talk)) {
    val brand = LocalBrand.current
    val pulse = rememberInfiniteTransition(label = "talk")
    val glow by pulse.animateFloat(0.25f, 0.55f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
    val grow by pulse.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "grow")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 4.dp)) {
        // Soft halo the size of the button, breathing slowly.
        Box(
            Modifier
                .matchParentSize()
                .scale(grow)
                .background(brand.heroGlow.copy(alpha = glow * 0.5f), RoundedCornerShape(50))
        )
        Row(
            Modifier
                .padding(8.dp)
                .shadow(12.dp, RoundedCornerShape(50), ambientColor = brand.heroStart, spotColor = brand.heroStart)
                .clip(RoundedCornerShape(50))
                .background(brand.heroBrush)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .heightIn(min = 70.dp)
                .padding(start = 10.dp, end = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = brand.heroStart, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** A rounded square with a tinted background and an icon: used for categories, settings, actions. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 46.dp, iconSize: Dp = 26.dp, filled: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(if (filled) color else tint(color)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else color, modifier = Modifier.size(iconSize))
    }
}

/** White rounded card with a hairline border and a very soft shadow. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(tint(color, 0.14f, 0.26f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Small grey chip with an icon, for "when" information on a card. */
@Composable
fun MetaChip(text: String?, icon: ImageVector, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(16.dp))
        if (text != null) {
            Spacer(Modifier.width(4.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
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
    Pill(key?.let { categoryName(ctx, it) } ?: "—", categoryColor(key), icon = categoryIcon(key))
}

/** Section title with an optional count bubble. */
@Composable
fun SectionHeader(text: String, color: Color = MaterialTheme.colorScheme.onBackground, count: Int? = null) {
    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.85f)))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(tint(color, 0.14f, 0.28f)).padding(horizontal = 10.dp, vertical = 1.dp)) {
                Text("$count", style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}

/** One task in a list: category tile, big title, when, and a round "done" button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(item: OccurrenceWithTask, today: LocalDate, onClick: () -> Unit, onDone: (() -> Unit)?) {
    val ctx = LocalContext.current
    val brand = LocalBrand.current
    val category = CategoryKey.parse(item.task.category)
    val status = TaskStatus.parse(item.occurrence.status) ?: TaskStatus.NOT_STARTED
    val date = Dates.parseDate(item.occurrence.date)
    val time = Dates.parseTime(item.occurrence.time)
    val rule = RecurrenceRule.fromJson(item.task.recurrenceJson)
    val overdue = date != null && date < today
    val important = item.task.priority == Priority.HIGH.name
    AppCard(
        modifier = Modifier.padding(vertical = 5.dp),
        onClick = onClick,
        padding = PaddingValues(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(categoryIcon(category), categoryColor(category), size = 50.dp, iconSize = 28.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    val whenText = listOfNotNull(date?.let { Dates.friendlyDate(ctx, it, today) }, time?.let { Dates.friendlyTime(ctx, it) }).joinToString(" · ")
                    if (whenText.isNotEmpty()) MetaChip(whenText, Icons.Filled.Schedule, if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (important) Pill(stringResource(R.string.important), MaterialTheme.colorScheme.tertiary, icon = Icons.Filled.Flag)
                    if (rule != null) MetaChip(recurrenceText(ctx, rule), Icons.Filled.Repeat)
                    if (item.task.reminderEnabled) MetaChip(null, Icons.Filled.NotificationsActive)
                    if (status != TaskStatus.NOT_STARTED) StatusPill(status, item.occurrence.progress)
                }
            }
            if (onDone != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, brand.success.copy(alpha = 0.55f), CircleShape)
                        .background(tint(brand.success, 0.06f, 0.12f))
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.mark_done), onClick = onDone),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.mark_done), tint = brand.success, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

/** A friendly empty screen: soft circle with an icon, a title and a hint. */
@Composable
fun EmptyState(text: String, icon: ImageVector? = null, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            val primary = MaterialTheme.colorScheme.primary
            Box(
                Modifier.size(112.dp).drawBehind {
                    drawCircle(primary.copy(alpha = 0.07f), radius = size.minDimension / 2)
                    drawCircle(primary.copy(alpha = 0.10f), radius = size.minDimension / 2.9f)
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Highlighted message at the top of a screen (missing key, inbox waiting...). */
@Composable
fun Banner(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    AppCard(modifier = Modifier.padding(vertical = 5.dp), onClick = onClick, color = tint(color, 0.10f, 0.20f).compositeOver(MaterialTheme.colorScheme.surface)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, color, size = 44.dp, iconSize = 24.dp, filled = true)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = color)
        }
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

/**
 * Top of an inner screen: large title on the warm background (no grey app bar), with a back
 * button and optional actions.
 */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.size(28.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** A row in a settings-style list: tinted icon tile, title, value, and a chevron. */
@Composable
fun SettingRow(icon: ImageVector, title: String, value: String, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color, size = 42.dp, iconSize = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, color: Color = MaterialTheme.colorScheme.primary, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onChange(!checked) }.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color, size = 42.dp, iconSize = 22.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** A list of choices (no typing). */
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(options) { (value, label) ->
                    val isSelected = value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onSelect(value); onDismiss() }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelect(value); onDismiss() }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A dialog with one text box, for the rare times he (or someone helping him) wants to type. */
@Composable
fun TextInputDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()); onDismiss() }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Wraps choices in a flowing row (statuses, quick picks). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Decorative soft circles for the gradient headers. */
fun Modifier.heroCircles(color: Color = Color.White): Modifier = drawBehind {
    drawCircle(color.copy(alpha = 0.07f), radius = size.width * 0.42f, center = Offset(size.width * 0.05f, size.height * 0.05f))
    drawCircle(color.copy(alpha = 0.06f), radius = size.width * 0.28f, center = Offset(size.width * 0.98f, size.height * 0.92f))
    drawCircle(color.copy(alpha = 0.05f), radius = size.width * 0.12f, center = Offset(size.width * 0.70f, size.height * 0.18f))
}

/** Vertical gradient from the brand colours, for headers. */
@Composable
fun heroBrush(): Brush = LocalBrand.current.heroBrush

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, false).show()
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
