package io.github.kiraherk.smarttodo.update

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** When Android stops the first install by itself, the app tries the same file once more, and only once. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class UpdateRetryTest {
    private val ctx: Context get() = testContext()
    private lateinit var apk: File

    @Before
    fun setUp() {
        ctx.getSharedPreferences("update_install", Context.MODE_PRIVATE).edit().clear().commit()
        apk = File(ctx.cacheDir, "SmartToDo-9.9.9.apk").apply { writeText("apk") }
    }

    @Test
    fun theFirstStoppedInstallIsRetriedOnce() {
        PendingInstall.remember(ctx, apk, again = false)
        assertEquals(apk.absolutePath, PendingInstall.fileToRetry(ctx)?.absolutePath)
        // The retry itself stopping too: no endless loop, he sees the message instead.
        assertNull(PendingInstall.fileToRetry(ctx))
    }

    @Test
    fun theAutomaticRetryIsNotRetriedAgain() {
        PendingInstall.remember(ctx, apk, again = true)
        assertNull(PendingInstall.fileToRetry(ctx))
    }

    @Test
    fun anOldAttemptOrAMissingFileIsNotRetried() {
        PendingInstall.remember(ctx, apk, again = false)
        ctx.getSharedPreferences("update_install", Context.MODE_PRIVATE).edit().putLong("at", System.currentTimeMillis() - 60 * 60_000L).commit()
        assertNull(PendingInstall.fileToRetry(ctx))

        PendingInstall.remember(ctx, apk, again = false)
        apk.delete()
        assertNull(PendingInstall.fileToRetry(ctx))
    }
}
