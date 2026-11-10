package io.github.kiraherk.smarttodo.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.domain.TaskStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps one exact alarm per pending reminder. Simply re-syncs everything after each change:
 * there are only ever a few dozen, and it makes the logic impossible to get out of step.
 */
class ReminderScheduler(private val context: Context, private val db: AppDatabase) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun rescheduleAll() = mutex.withLock {
        val now = System.currentTimeMillis()
        val scheduled = mutableSetOf<String>()
        for (o in db.occurrences().pendingReminders(TaskStatus.OPEN)) {
            val at = o.remindAt ?: continue
            if (at < now - STALE_AFTER_MS) {
                // The phone was off for more than a day: don't ring for something that old.
                db.occurrences().update(o.copy(reminderFiredAt = now))
                continue
            }
            setExact(maxOf(at, now + 2_000), reminderIntent(o.id))
            scheduled += o.id.toString()
        }
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        (previous - scheduled).forEach { id -> id.toLongOrNull()?.let { alarmManager.cancel(reminderIntent(it)) } }
        prefs.edit().putStringSet(KEY_IDS, scheduled).apply()
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    @SuppressLint("ScheduleExactAlarm")
    fun setExact(at: Long, pendingIntent: PendingIntent) {
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        }
    }

    fun cancel(pendingIntent: PendingIntent) = alarmManager.cancel(pendingIntent)

    private fun reminderIntent(occurrenceId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        occurrenceId.toInt(),
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_REMINDER)
            .putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val KEY_IDS = "scheduled_ids"
        private const val STALE_AFTER_MS = 24 * 3_600_000L
    }
}
