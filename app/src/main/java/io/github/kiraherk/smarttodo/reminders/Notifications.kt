package io.github.kiraherk.smarttodo.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.kiraherk.smarttodo.MainActivity
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.util.LocaleHelper

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_CHECKINS = "checkins"
    const val CH_UPDATES = "updates"
    const val CH_GENERAL = "general"

    const val ID_MORNING = 1_000_001
    const val ID_EVENING = 1_000_002
    const val ID_WEEKLY = 1_000_003
    const val ID_UPDATE = 1_000_004
    const val ID_INBOX = 1_000_005
    const val ID_INSTALL = 1_000_006

    fun createChannels(context: Context) {
        val c = LocaleHelper.wrap(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, c.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                },
                NotificationChannel(CH_CHECKINS, c.getString(R.string.channel_checkins), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_UPDATES, c.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_GENERAL, c.getString(R.string.channel_general), NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun builder(context: Context, channel: String, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setColor(0xFF00695C.toInt())

    /** Opens the app on a task or screen. */
    fun openApp(context: Context, requestCode: Int, taskId: Long? = null, screen: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            taskId?.let { putExtra(MainActivity.EXTRA_OPEN_TASK, it) }
            screen?.let { putExtra(MainActivity.EXTRA_OPEN_SCREEN, it) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
