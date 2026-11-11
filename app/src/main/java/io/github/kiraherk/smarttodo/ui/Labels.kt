package io.github.kiraherk.smarttodo.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ai.ActionType
import io.github.kiraherk.smarttodo.domain.ActivityType
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.Frequency
import io.github.kiraherk.smarttodo.domain.RecurrenceRule
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.util.Dates
import io.github.kiraherk.smarttodo.util.LocaleHelper

fun categoryName(ctx: Context, key: CategoryKey): String = ctx.getString(
    when (key) {
        CategoryKey.WORK -> R.string.cat_work
        CategoryKey.HOME -> R.string.cat_home
        CategoryKey.PERSONAL -> R.string.cat_personal
        CategoryKey.TRAVEL -> R.string.cat_travel
    }
)

fun categoryColor(key: CategoryKey?): Color = when (key) {
    CategoryKey.WORK -> Color(0xFF1565C0)
    CategoryKey.HOME -> Color(0xFF2E7D32)
    CategoryKey.PERSONAL -> Color(0xFF7B1FA2)
    CategoryKey.TRAVEL -> Color(0xFFE65100)
    null -> Color(0xFF607D8B)
}

fun categoryIcon(key: CategoryKey?): ImageVector = when (key) {
    CategoryKey.WORK -> Icons.Filled.Work
    CategoryKey.HOME -> Icons.Filled.Home
    CategoryKey.TRAVEL -> Icons.Filled.Flight
    else -> Icons.Filled.Person
}

fun statusName(ctx: Context, status: TaskStatus): String = ctx.getString(
    when (status) {
        TaskStatus.NOT_STARTED -> R.string.status_not_started
        TaskStatus.IN_PROGRESS -> R.string.status_in_progress
        TaskStatus.PARTIALLY_DONE -> R.string.status_partly
        TaskStatus.POSTPONED -> R.string.status_postponed
        TaskStatus.DONE -> R.string.status_done
        TaskStatus.NOT_DONE -> R.string.status_not_done
        TaskStatus.CANCELLED -> R.string.status_cancelled
        TaskStatus.MISSED -> R.string.status_missed
    }
)

fun statusColor(status: TaskStatus?): Color = when (status) {
    TaskStatus.DONE -> Color(0xFF2E7D32)
    TaskStatus.PARTIALLY_DONE -> Color(0xFFF9A825)
    TaskStatus.IN_PROGRESS -> Color(0xFF1565C0)
    TaskStatus.POSTPONED -> Color(0xFFEF6C00)
    TaskStatus.NOT_DONE -> Color(0xFFC62828)
    TaskStatus.MISSED -> Color(0xFF8D6E63)
    TaskStatus.CANCELLED -> Color(0xFF616161)
    TaskStatus.NOT_STARTED, null -> Color(0xFF78909C)
}

fun activityName(ctx: Context, type: ActivityType?): String = ctx.getString(
    when (type) {
        ActivityType.CREATED -> R.string.activity_created
        ActivityType.STATUS -> R.string.activity_status
        ActivityType.NOTE -> R.string.activity_note
        ActivityType.EDIT -> R.string.activity_edit
        ActivityType.POSTPONED -> R.string.activity_postponed
        ActivityType.ARCHIVED -> R.string.activity_archived
        ActivityType.RESTORED -> R.string.activity_restored
        null -> R.string.activity_edit
    }
)

fun actionName(ctx: Context, type: String): String = ctx.getString(
    when (type) {
        ActionType.CREATE -> R.string.act_create
        ActionType.UPDATE -> R.string.act_update
        ActionType.SET_STATUS -> R.string.act_status
        ActionType.ADD_NOTE -> R.string.act_note
        ActionType.SET_REMINDER -> R.string.act_reminder
        ActionType.CHANGE_RECURRENCE -> R.string.act_recurrence
        ActionType.POSTPONE -> R.string.act_postpone
        ActionType.DELETE -> R.string.act_delete
        ActionType.RESTORE -> R.string.act_restore
        else -> R.string.act_other
    }
)

/** "كل أسبوعين يوم الاثنين" / "Every 2 weeks on Monday". */
fun recurrenceText(ctx: Context, rule: RecurrenceRule): String {
    val ar = LocaleHelper.isArabic(ctx)
    val locale = LocaleHelper.locale(ctx)
    val n = rule.interval
    val base = when (rule.frequency) {
        Frequency.DAILY -> if (ar) arCount(n, "كل يوم", "كل يومين", "أيام", "يوماً") else enCount(n, "day")
        Frequency.WEEKLY -> if (ar) arCount(n, "كل أسبوع", "كل أسبوعين", "أسابيع", "أسبوعاً") else enCount(n, "week")
        Frequency.MONTHLY -> if (ar) arCount(n, "كل شهر", "كل شهرين", "أشهر", "شهراً") else enCount(n, "month")
        Frequency.YEARLY -> if (ar) arCount(n, "كل سنة", "كل سنتين", "سنوات", "سنة") else enCount(n, "year")
    }
    val extra = when {
        rule.frequency == Frequency.WEEKLY && rule.weekdays.isNotEmpty() ->
            (if (ar) " يوم " else " on ") + rule.weekdays.joinToString(if (ar) " و" else ", ") { Dates.weekdayName(it, locale) }
        rule.frequency == Frequency.MONTHLY && rule.dayOfMonth != null ->
            if (ar) " يوم ${rule.dayOfMonth}" else " on day ${rule.dayOfMonth}"
        else -> ""
    }
    val end = rule.endDate?.let { if (ar) " حتى $it" else " until $it" }.orEmpty()
    return base + extra + end
}

private fun arCount(n: Int, one: String, two: String, few: String, many: String) = when {
    n <= 1 -> one
    n == 2 -> two
    n in 3..10 -> "كل $n $few"
    else -> "كل $n $many"
}

private fun enCount(n: Int, unit: String) = if (n <= 1) "Every $unit" else "Every $n ${unit}s"

/** How a reading voice is described to him ("" = the phone's own voice). */
fun voiceLabel(ctx: Context, name: String): String = ctx.getString(
    when (name) {
        "Kore" -> R.string.voice_kore
        "Aoede" -> R.string.voice_aoede
        "Leda" -> R.string.voice_leda
        "Charon" -> R.string.voice_charon
        "Orus" -> R.string.voice_orus
        "Puck" -> R.string.voice_puck
        else -> R.string.voice_phone
    }
)
