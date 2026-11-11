package io.github.kiraherk.smarttodo.update

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.reminders.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The update must not get stuck when Play Protect's scan screen is in front during the install. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class InstallFlowTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")

    private fun result(session: Int, status: Int) = Intent(app, InstallResultReceiver::class.java)
        .putExtra(PackageInstaller.EXTRA_SESSION_ID, session)
        .putExtra(PackageInstaller.EXTRA_STATUS, status)
        .putExtra(Intent.EXTRA_INTENT, confirm)

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Before
    fun setUp() {
        Notifications.createChannels(app)
        PendingInstall.clear(app)
        PendingInstall.sessionId = 5
        PendingInstall.appInFront = false
        while (shadowOf(app).nextStartedActivity != null) Unit
    }

    @Test
    fun confirmationWaitsWhileAnotherScreenIsInFrontThenOpensWhenTheAppIsBack() {
        InstallResultReceiver().onReceive(app, result(5, PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertNull("must not try to open it from the background", shadowOf(app).nextStartedActivity)
        assertEquals(1, notifications().size)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        PendingInstall.showIfWaiting(activity)
        assertEquals(confirm.action, shadowOf(activity).nextStartedActivity.action)
        assertEquals(0, notifications().size)

        // Only once.
        PendingInstall.showIfWaiting(activity)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun opensRightAwayWhenTheAppIsInFront() {
        PendingInstall.appInFront = true
        InstallResultReceiver().onReceive(app, result(5, PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(0, notifications().size)
    }

    @Test
    fun resultsOfAnOldAbandonedAttemptAreIgnored() {
        InstallResultReceiver().onReceive(app, result(4, PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(0, notifications().size)
    }

    @Test
    fun aStoppedUpdateExplainsHowToTryAgain() {
        InstallResultReceiver().onReceive(app, result(5, PackageInstaller.STATUS_FAILURE_ABORTED))
        val n = notifications().single()
        val text = n.extras.getCharSequence("android.text").toString()
        assertEquals(io.github.kiraherk.smarttodo.util.LocaleHelper.wrap(app).getString(io.github.kiraherk.smarttodo.R.string.update_stopped), text)
    }
}
