package io.github.kiraherk.smarttodo

import android.app.Application
import io.github.kiraherk.smarttodo.reminders.Notifications
import io.github.kiraherk.smarttodo.update.DailyCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartTodoApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch { container.maintenance.run() }
        runCatching { DailyCheckWorker.schedule(this) }
    }
}
