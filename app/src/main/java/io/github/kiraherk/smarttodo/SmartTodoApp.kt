package io.github.kiraherk.smarttodo

import android.app.Application
import io.github.kiraherk.smarttodo.reminders.Notifications
import io.github.kiraherk.smarttodo.inbox.NetworkWatcher
import io.github.kiraherk.smarttodo.inbox.OfflineQueue
import io.github.kiraherk.smarttodo.update.DailyCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartTodoApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy { AppContainer(this) }

    /** Online or not (for the Today screen), and the trigger that empties the offline queue. */
    val network: NetworkWatcher by lazy { NetworkWatcher(this) { processOfflineQueue() } }

    /** Adds what he said offline, now if possible, otherwise as soon as there is internet. */
    fun processOfflineQueue() {
        appScope.launch {
            if (container.db.inbox().count() == 0) return@launch
            val outcome = container.offlineQueue.processAll()
            when {
                outcome?.retryInMs != null -> OfflineQueue.scheduleLater(this@SmartTodoApp, outcome.retryInMs)
                container.db.inbox().all().any { it.state != OfflineQueue.NEEDS_HIM } -> OfflineQueue.scheduleOnConnection(this@SmartTodoApp)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch { container.maintenance.run() }
        runCatching { DailyCheckWorker.schedule(this) }
        network.start()
        processOfflineQueue()
    }
}
