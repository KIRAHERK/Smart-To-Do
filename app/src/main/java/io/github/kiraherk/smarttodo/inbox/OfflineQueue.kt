package io.github.kiraherk.smarttodo.inbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.ai.ActionType
import io.github.kiraherk.smarttodo.ai.AiException
import io.github.kiraherk.smarttodo.data.db.InboxEntity
import io.github.kiraherk.smarttodo.reminders.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/**
 * What he said while there was no internet (or the AI was resting) waits here and is added
 * by itself as soon as the connection is back: no button to press.
 */
class OfflineQueue(
    private val inbox: io.github.kiraherk.smarttodo.data.db.InboxDao,
    private val hasKey: () -> Boolean,
    private val interpret: suspend (String) -> List<io.github.kiraherk.smarttodo.ai.AiAction>,
    /** Saves the changes; returns how many could be applied. */
    private val apply: suspend (List<io.github.kiraherk.smarttodo.ai.AiAction>, String) -> Int,
    /** Answers a question he asked offline (the answer goes to the assistant chat). */
    private val answer: suspend (String) -> Unit,
    private val onAdded: (List<String>) -> Unit,
) {
    constructor(c: AppContainer) : this(
        inbox = c.db.inbox(),
        hasKey = { c.secrets.hasApiKey() },
        interpret = { c.interpreter.interpret(it) },
        apply = { actions, text -> c.executor.apply(actions, text).applied },
        answer = { c.assistant.answer(it) },
        onAdded = { notifyAdded(c, it) },
    )

    private val mutex = Mutex()

    /** What happened in one round. [retryInMs] = try again after that long (AI resting); null = nothing to wait for. */
    data class Outcome(val added: List<String>, val needsHim: Int, val retryInMs: Long?)

    /** Goes through everything waiting, oldest first. Returns null if a round is already running or there is nothing to do. */
    suspend fun processAll(): Outcome? {
        if (!hasKey()) return null
        if (!mutex.tryLock()) return null
        try {
            val waiting = inbox.all().filter { it.state != NEEDS_HIM }.sortedBy { it.createdAt }
            if (waiting.isEmpty()) return null
            val added = mutableListOf<String>()
            var needsHim = 0
            var retryInMs: Long? = null
            for (item in waiting) {
                when (val r = handle(item)) {
                    Handled.Done -> added += item.text
                    Handled.NeedsHim -> needsHim++
                    is Handled.Later -> {
                        retryInMs = r.afterMs
                        break
                    }
                }
            }
            if (added.isNotEmpty()) onAdded(added)
            return Outcome(added, needsHim, retryInMs)
        } finally {
            mutex.unlock()
        }
    }

    private sealed interface Handled {
        data object Done : Handled
        data object NeedsHim : Handled
        data class Later(val afterMs: Long) : Handled
    }

    private suspend fun handle(item: InboxEntity): Handled {
        try {
            val actions = interpret(item.text)
            val changes = actions.filter { it.type in ActionType.CHANGES }
            val question = actions.firstOrNull { it.type == ActionType.ASK }
            return when {
                changes.isNotEmpty() -> {
                    if (apply(changes, item.text) > 0) {
                        inbox.delete(item.id)
                        Handled.Done
                    } else {
                        needsHim(item, "could not apply")
                    }
                }
                question != null -> {
                    // The answer goes to the assistant chat, where he can read it.
                    answer(question.question?.takeIf { it.isNotBlank() } ?: item.text)
                    inbox.delete(item.id)
                    Handled.Done
                }
                else -> needsHim(item, "unclear")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException.Quota) {
            return Handled.Later(e.restMs)
        } catch (e: AiException.Network) {
            return Handled.Later(NETWORK_RETRY_MS)
        } catch (e: AiException.Timeout) {
            return Handled.Later(NETWORK_RETRY_MS)
        } catch (e: AiException.Server) {
            return Handled.Later(NETWORK_RETRY_MS)
        } catch (e: AiException.NoKey) {
            return Handled.Later(NETWORK_RETRY_MS)
        } catch (e: Exception) {
            val attempts = item.attempts + 1
            if (attempts >= MAX_ATTEMPTS) return needsHim(item, e.message)
            inbox.update(item.copy(attempts = attempts, error = e.message))
            return Handled.Later(NETWORK_RETRY_MS)
        }
    }

    /** The AI couldn't make sense of it: it stays in the list for him to look at. */
    private suspend fun needsHim(item: InboxEntity, why: String?): Handled {
        inbox.update(item.copy(state = NEEDS_HIM, error = why, attempts = item.attempts + 1))
        return Handled.NeedsHim
    }

    companion object {
    private fun notifyAdded(c: AppContainer, added: List<String>) {
        val ctx = c.localized()
        val title = ctx.resources.getQuantityString(R.plurals.offline_added, added.size, added.size)
        val text = added.joinToString("\n") { "✓ $it" }
        Notifications.post(
            ctx, Notifications.ID_INBOX,
            Notifications.builder(ctx, Notifications.CH_GENERAL, title, text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(Notifications.openApp(ctx, Notifications.ID_INBOX)),
        )
    }

        const val NEEDS_HIM = "NEEDS_HIM"
        const val MAX_ATTEMPTS = 3
        const val NETWORK_RETRY_MS = 15 * 60_000L
        private const val WORK_ON_CONNECT = "offline-queue"
        private const val WORK_LATER = "offline-queue-later"

        /** Runs the queue as soon as there is internet, even if the app is closed by then. */
        fun scheduleOnConnection(context: Context) = runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_ON_CONNECT, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OfflineQueueWorker>().setConstraints(connected()).build(),
            )
        }

        /** Tries again after [delayMs] (the AI asked for a rest, or the connection was too weak). */
        fun scheduleLater(context: Context, delayMs: Long) = runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_LATER, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<OfflineQueueWorker>()
                    .setConstraints(connected())
                    .setInitialDelay(delayMs.coerceAtLeast(5_000L), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }

        private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}

class OfflineQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SmartTodoApp).container
        val outcome = c.offlineQueue.processAll()
        outcome?.retryInMs?.let { OfflineQueue.scheduleLater(applicationContext, it) }
        return Result.success()
    }
}

/** Knows whether the phone is online, and runs the queue the moment Wi-Fi or mobile data comes back. */
class NetworkWatcher(context: Context, private val onBackOnline: () -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(isOnlineNow())
    val online: StateFlow<Boolean> = _online

    fun start() {
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasOffline = !_online.value
                    _online.value = true
                    if (wasOffline) onBackOnline()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    _online.value = isOnlineNow()
                }
            })
        }
    }

    private fun isOnlineNow(): Boolean = runCatching {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}
