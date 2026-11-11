package io.github.kiraherk.smarttodo.ui.voice

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * When the phone's speech services can't be used from inside the app, the app opens Google's
 * listening popup instead; what comes back must be handled exactly like in-app speech.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = SmartTodoApp::class)
class VoicePopupTest {
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("condition not met in time", condition())
    }

    @Test
    fun popupTextGoesThroughTheSameFlow() {
        val app = ApplicationProvider.getApplicationContext<SmartTodoApp>()
        val c = app.container
        runBlocking { c.secrets.setApiKey(null) }
        val vm = VoiceViewModel(c)
        val opened = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { vm.popupRequests.collect { opened += it } }

        // No usable in-app speech service on this (test) phone: the popup is requested.
        vm.start(VoiceViewModel.Request())
        waitFor { opened.isNotEmpty() }
        assertEquals("ar-SA", opened.single())

        // What he said in the popup is handled like any command (no key yet, so it goes to the Inbox).
        vm.onPopupResult("بكرة أروح للبنك")
        waitFor { runBlocking { c.db.inbox().all() }.isNotEmpty() }
        assertEquals("بكرة أروح للبنك", runBlocking { c.db.inbox().all() }.single().text)
        val expected = LocaleHelper.wrap(app).getString(R.string.saved_inbox_no_key)
        waitFor { (vm.state.value as? VoiceViewModel.UiState.Message)?.text == expected }

        // Closing the popup without speaking says so, and offers to try again.
        vm.start(VoiceViewModel.Request())
        waitFor { opened.size == 2 }
        vm.onPopupResult(null)
        waitFor { (vm.state.value as? VoiceViewModel.UiState.Message)?.canRetry == true }
        scope.cancel()
    }
}
