package io.github.kiraherk.smarttodo.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.categoryName
import io.github.kiraherk.smarttodo.util.Dates
import kotlinx.coroutines.launch

/** Rings reminders and the morning / evening / weekly check-ins, and runs the nightly upkeep. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SmartTodoApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val c = app.container
                when (intent.action) {
                    ACTION_REMINDER -> showReminder(app, intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1))
                    ACTION_MORNING -> { c.checkIns.morning(); c.checkInScheduler.scheduleAll() }
                    ACTION_EVENING -> { c.checkIns.evening(); c.checkInScheduler.scheduleAll() }
                    ACTION_WEEKLY -> { c.checkIns.weekly(); c.checkInScheduler.scheduleAll() }
                    ACTION_MAINTENANCE -> c.maintenance.run()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun showReminder(app: SmartTodoApp, occurrenceId: Long) {
        val c = app.container
        val item = c.db.occurrences().getWithTask(occurrenceId) ?: return
        if (!item.occurrence.isOpen || item.task.archived) return
        c.repo.markReminderFired(occurrenceId)
        val ctx = c.localized()
        val parts = buildList {
            Dates.parseTime(item.occurrence.time)?.let { add(Dates.friendlyTime(ctx, it)) }
            CategoryKey.parse(item.task.category)?.let { add(categoryName(ctx, it)) }
            if (item.task.details.isNotBlank()) add(item.task.details)
        }
        val id = occurrenceId.toInt()
        val builder = Notifications.builder(ctx, Notifications.CH_REMINDERS, "🔔 " + item.task.title, parts.joinToString(" · ").ifBlank { ctx.getString(R.string.reminder_default_text) })
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(Notifications.openApp(ctx, id, taskId = item.task.id))
            .addAction(0, ctx.getString(R.string.action_done), actionIntent(ctx, NotificationActionReceiver.ACTION_DONE, occurrenceId))
            .addAction(0, ctx.getString(R.string.action_partly), actionIntent(ctx, NotificationActionReceiver.ACTION_PARTLY, occurrenceId))
            .addAction(0, ctx.getString(R.string.action_snooze_hour), actionIntent(ctx, NotificationActionReceiver.ACTION_SNOOZE, occurrenceId))
        Notifications.post(ctx, id, builder)
    }

    private fun actionIntent(context: Context, action: String, occurrenceId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        (occurrenceId * 10 + action.hashCode() % 10).toInt(),
        Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_OCCURRENCE_ID, occurrenceId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REMINDER = "io.github.kiraherk.smarttodo.REMINDER"
        const val ACTION_MORNING = "io.github.kiraherk.smarttodo.MORNING"
        const val ACTION_EVENING = "io.github.kiraherk.smarttodo.EVENING"
        const val ACTION_WEEKLY = "io.github.kiraherk.smarttodo.WEEKLY"
        const val ACTION_MAINTENANCE = "io.github.kiraherk.smarttodo.MAINTENANCE"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
    }
}

/** Buttons on a reminder: Done / Partly done / Snooze one hour. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SmartTodoApp
        val occurrenceId = intent.getLongExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, -1)
        if (occurrenceId < 0) return
        Notifications.cancel(context, occurrenceId.toInt())
        val pending = goAsync()
        app.appScope.launch {
            try {
                val repo = app.container.repo
                when (intent.action) {
                    ACTION_DONE -> repo.setStatus(occurrenceId, TaskStatus.DONE)
                    ACTION_PARTLY -> repo.setStatus(occurrenceId, TaskStatus.PARTIALLY_DONE)
                    ACTION_SNOOZE -> repo.snooze(occurrenceId, 60)
                    ACTION_SNOOZE_TOMORROW -> repo.snoozeToTomorrow(occurrenceId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "io.github.kiraherk.smarttodo.ACTION_DONE"
        const val ACTION_PARTLY = "io.github.kiraherk.smarttodo.ACTION_PARTLY"
        const val ACTION_SNOOZE = "io.github.kiraherk.smarttodo.ACTION_SNOOZE"
        const val ACTION_SNOOZE_TOMORROW = "io.github.kiraherk.smarttodo.ACTION_SNOOZE_TOMORROW"
    }
}

/** After a restart, an app update or a clock/time-zone change, alarms must be set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val app = context.applicationContext as SmartTodoApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.maintenance.run()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
