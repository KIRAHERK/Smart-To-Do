package io.github.kiraherk.smarttodo.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.kiraherk.smarttodo.MainActivity
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.reminders.Notifications
import java.util.concurrent.TimeUnit

/** Once a day: is there a new version on GitHub? Also nudges him if recordings are waiting in the inbox. */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SmartTodoApp
        val c = app.container
        val ctx = c.localized()

        runCatching { c.updateChecker.check() }.getOrNull()?.let { info ->
            if (c.settings.current().lastNotifiedUpdate != info.version) {
                Notifications.post(
                    ctx, Notifications.ID_UPDATE,
                    Notifications.builder(ctx, Notifications.CH_UPDATES, ctx.getString(R.string.update_available_title, info.version), ctx.getString(R.string.update_available_text))
                        .setContentIntent(Notifications.openApp(ctx, Notifications.ID_UPDATE, screen = MainActivity.SCREEN_UPDATE)),
                )
                c.settings.setLastNotifiedUpdate(info.version)
            }
        }

        val waiting = c.db.inbox().count()
        if (waiting > 0 && c.secrets.hasApiKey()) {
            Notifications.post(
                ctx, Notifications.ID_INBOX,
                Notifications.builder(ctx, Notifications.CH_GENERAL, ctx.getString(R.string.inbox_title), ctx.resources.getQuantityString(R.plurals.inbox_waiting, waiting, waiting))
                    .setContentIntent(Notifications.openApp(ctx, Notifications.ID_INBOX, screen = MainActivity.SCREEN_INBOX)),
            )
        }
        c.backup.autoBackupIfDue()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
