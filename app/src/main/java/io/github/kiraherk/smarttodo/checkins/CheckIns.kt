package io.github.kiraherk.smarttodo.checkins

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.kiraherk.smarttodo.MainActivity
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.db.OccurrenceWithTask
import io.github.kiraherk.smarttodo.data.repo.TaskRepository
import io.github.kiraherk.smarttodo.data.settings.SettingsRepository
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.reminders.AlarmReceiver
import io.github.kiraherk.smarttodo.reminders.Notifications
import io.github.kiraherk.smarttodo.reminders.ReminderScheduler
import io.github.kiraherk.smarttodo.util.Dates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Sets the alarms for the daily/weekly check-ins and the nightly upkeep. */
class CheckInScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
) {
    suspend fun scheduleAll() {
        val s = settings.current()
        set(AlarmReceiver.ACTION_MORNING, REQ_MORNING, if (s.morningEnabled) nextDaily(s.morningTime) else null)
        set(AlarmReceiver.ACTION_EVENING, REQ_EVENING, if (s.eveningEnabled) nextDaily(s.eveningTime) else null)
        set(AlarmReceiver.ACTION_WEEKLY, REQ_WEEKLY, if (s.weeklyEnabled) nextWeekly(s.weeklyDay, s.weeklyTime) else null)
        set(AlarmReceiver.ACTION_MAINTENANCE, REQ_MAINTENANCE, nextDaily("00:05"))
    }

    private fun set(action: String, requestCode: Int, at: Long?) {
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) reminders.cancel(pi) else reminders.setExact(at, pi)
    }

    private fun nextDaily(time: String): Long {
        val t = Dates.parseTime(time) ?: LocalTime.of(8, 0)
        val now = LocalDateTime.now()
        var at = LocalDate.now().atTime(t)
        if (!at.isAfter(now.plusSeconds(30))) at = at.plusDays(1)
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun nextWeekly(day: Int, time: String): Long {
        val t = Dates.parseTime(time) ?: LocalTime.of(18, 0)
        val now = LocalDateTime.now()
        var at = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day.coerceIn(1, 7)))).atTime(t)
        if (!at.isAfter(now.plusSeconds(30))) at = at.plusWeeks(1)
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    companion object {
        private const val REQ_MORNING = -1
        private const val REQ_EVENING = -2
        private const val REQ_WEEKLY = -3
        private const val REQ_MAINTENANCE = -4
    }
}

/** Builds the check-in messages locally (no AI request needed, so they are free and instant). */
class CheckIns(
    private val db: AppDatabase,
    private val repo: TaskRepository,
    private val localized: () -> Context,
) {
    data class Day(val today: List<OccurrenceWithTask>, val overdue: List<OccurrenceWithTask>)

    suspend fun day(): Day {
        val today = repo.today().toString()
        val open = db.occurrences().open(TaskStatus.OPEN)
        return Day(
            today = open.filter { it.occurrence.date == today },
            overdue = open.filter { it.occurrence.date != null && it.occurrence.date < today },
        )
    }

    /** "Good morning! You have 3 things today: ..." Also used when he taps the notification. */
    suspend fun morningText(spoken: Boolean): String {
        val ctx = localized()
        val d = day()
        val res = ctx.resources
        return buildString {
            append(ctx.getString(R.string.morning_greeting))
            append(' ')
            if (d.today.isEmpty() && d.overdue.isEmpty()) {
                append(ctx.getString(R.string.morning_nothing))
                return@buildString
            }
            if (d.today.isEmpty()) {
                append(ctx.getString(R.string.morning_nothing_today))
            } else {
                append(res.getQuantityString(R.plurals.morning_today_count, d.today.size, d.today.size))
                d.today.take(10).forEach { item ->
                    append(if (spoken) "، " else "\n• ")
                    append(item.task.title)
                    Dates.parseTime(item.occurrence.time)?.let { append(" (" + Dates.friendlyTime(ctx, it) + ")") }
                }
                append(if (spoken) "." else "")
            }
            if (d.overdue.isNotEmpty()) {
                append(if (spoken) " " else "\n")
                append(res.getQuantityString(R.plurals.morning_overdue_count, d.overdue.size, d.overdue.size))
            }
        }
    }

    suspend fun morning() {
        repo.rollForward()
        val ctx = localized()
        post(ctx, Notifications.ID_MORNING, ctx.getString(R.string.morning_title), morningText(spoken = false), MainActivity.SCREEN_TODAY_SPEAK)
    }

    suspend fun evening() {
        val ctx = localized()
        val today = repo.today().toString()
        val todays = db.occurrences().between(today, today)
        val open = todays.count { it.occurrence.isOpen }
        val text = if (todays.isEmpty()) ctx.getString(R.string.evening_text_empty)
        else ctx.resources.getQuantityString(R.plurals.evening_text, open, open, todays.size)
        post(ctx, Notifications.ID_EVENING, ctx.getString(R.string.evening_title), text, MainActivity.SCREEN_REVIEW)
    }

    data class Week(val done: Int, val partly: Int, val notDone: Int, val missed: Int, val open: Int, val nextWeek: Int)

    suspend fun week(): Week {
        val today = repo.today()
        val last = db.occurrences().between(today.minusDays(6).toString(), today.toString())
        val next = db.occurrences().between(today.plusDays(1).toString(), today.plusDays(7).toString())
        fun count(s: TaskStatus) = last.count { it.occurrence.status == s.name }
        return Week(
            done = count(TaskStatus.DONE),
            partly = count(TaskStatus.PARTIALLY_DONE) + count(TaskStatus.IN_PROGRESS),
            notDone = count(TaskStatus.NOT_DONE) + count(TaskStatus.CANCELLED),
            missed = count(TaskStatus.MISSED),
            open = db.occurrences().open(TaskStatus.OPEN).count { it.occurrence.date != null && it.occurrence.date <= today.toString() },
            nextWeek = next.size,
        )
    }

    suspend fun weekText(): String {
        val ctx = localized()
        val w = week()
        return ctx.getString(R.string.weekly_text, w.done, w.partly, w.notDone + w.missed, w.open, w.nextWeek)
    }

    suspend fun weekly() {
        val ctx = localized()
        post(ctx, Notifications.ID_WEEKLY, ctx.getString(R.string.weekly_title), weekText(), MainActivity.SCREEN_WEEKLY)
    }

    private fun post(ctx: Context, id: Int, title: String, text: String, screen: String) {
        Notifications.post(
            ctx, id,
            Notifications.builder(ctx, Notifications.CH_CHECKINS, title, text)
                .setContentIntent(Notifications.openApp(ctx, id, screen = screen)),
        )
    }
}
