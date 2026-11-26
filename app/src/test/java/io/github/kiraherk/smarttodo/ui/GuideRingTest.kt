package io.github.kiraherk.smarttodo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.ui.theme.SmartTodoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The guide's circle sits over the tab it explains: in Arabic the first tab is the rightmost one. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = SmartTodoApp::class, qualifiers = "ar-w400dp-h860dp-xxhdpi")
class GuideRingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun ringCenterX(tab: Int): Float {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SmartTodoTheme(1f) {
                GuideOverlay(steps = listOf(Guide.steps.first { it.route == Guide.TABS[tab] }, Guide.steps.last()), whatsNew = false, onGoTo = {}, onDone = {})
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val b = compose.onNodeWithTag(RING_TAG, useUnmergedTree = true).getBoundsInRoot()
        return ((b.left + b.right) / 2).value
    }

    @Test
    fun firstTabIsCircledOnTheRightInArabic() {
        val x = ringCenterX(0)
        val width = compose.onRoot().getBoundsInRoot().right.value
        val slot = width / Guide.TABS.size
        assertTrue("ring at $x of $width", x > width - slot)
    }

    @Test
    fun secondTabIsCircledSecondFromTheRight() {
        val x = ringCenterX(1)
        val width = compose.onRoot().getBoundsInRoot().right.value
        val slot = width / Guide.TABS.size
        assertTrue("ring at $x of $width", x < width - slot && x > width - 2 * slot)
    }
}
