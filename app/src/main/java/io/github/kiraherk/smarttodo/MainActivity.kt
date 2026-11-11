package io.github.kiraherk.smarttodo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.kiraherk.smarttodo.ui.AppRoot
import io.github.kiraherk.smarttodo.update.PendingInstall
import io.github.kiraherk.smarttodo.util.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow

/** Where a notification or shortcut wants the app to go. */
data class NavRequest(val taskId: Long? = null, val screen: String? = null, val id: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private val navRequests = MutableStateFlow<NavRequest?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppRoot(navRequests = navRequests, onNavHandled = { navRequests.value = null })
        }
    }

    override fun onResume() {
        super.onResume()
        PendingInstall.appInFront = true
        // An update that was waiting for his "Install" (e.g. after Play Protect's scan) continues now.
        PendingInstall.showIfWaiting(this)
    }

    override fun onPause() {
        PendingInstall.appInFront = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val taskId = intent.getLongExtra(EXTRA_OPEN_TASK, -1L).takeIf { it > 0 }
        val screen = intent.getStringExtra(EXTRA_OPEN_SCREEN)
            ?: if (intent.action == ACTION_TALK) SCREEN_VOICE else null
        if (taskId != null || screen != null) navRequests.value = NavRequest(taskId, screen)
    }

    companion object {
        const val EXTRA_OPEN_TASK = "open_task"
        const val EXTRA_OPEN_SCREEN = "open_screen"
        const val ACTION_TALK = "io.github.kiraherk.smarttodo.TALK"

        const val SCREEN_TODAY_SPEAK = "today_speak"
        const val SCREEN_REVIEW = "review"
        const val SCREEN_WEEKLY = "weekly"
        const val SCREEN_INBOX = "inbox"
        const val SCREEN_UPDATE = "update"
        const val SCREEN_VOICE = "voice"
    }
}
