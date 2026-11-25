package io.github.kiraherk.smarttodo.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ui.screens.Routes
import io.github.kiraherk.smarttodo.update.Versions

/**
 * One stop of the tour: the tab it opens, what is there, and the one thing people tend to miss.
 * [since] = the version that brought it, so an update shows only what is new.
 */
data class GuideStep(
    val route: String?,
    @StringRes val title: Int,
    val points: List<Int>,
    @StringRes val tip: Int,
    val since: String,
)

object Guide {
    /** The bottom tabs, in their order (the ring is drawn around the right one). */
    val TABS = listOf(Routes.TODAY, Routes.ASSISTANT, Routes.HISTORY, Routes.SETTINGS)

    val steps = listOf(
        GuideStep(Routes.TODAY, R.string.guide_today_title, listOf(R.string.guide_today_1, R.string.guide_today_2, R.string.guide_today_3), R.string.guide_today_tip, "0.1.0"),
        GuideStep(Routes.ASSISTANT, R.string.guide_chat_title, listOf(R.string.guide_chat_1, R.string.guide_chat_2, R.string.guide_chat_3), R.string.guide_chat_tip, "0.4.0"),
        GuideStep(Routes.HISTORY, R.string.guide_history_title, listOf(R.string.guide_history_1, R.string.guide_history_2, R.string.guide_history_3), R.string.guide_history_tip, "0.4.0"),
        GuideStep(Routes.SETTINGS, R.string.guide_settings_title, listOf(R.string.guide_settings_1, R.string.guide_settings_2, R.string.guide_settings_3), R.string.guide_settings_tip, "0.1.0"),
        GuideStep(null, R.string.guide_done_title, listOf(R.string.guide_done_1, R.string.guide_done_2), R.string.guide_done_tip, "0.1.0"),
    )

    /** Before the guide existed (an update from 0.3.x or older). */
    const val BEFORE_GUIDE = "0.3.1"

    /**
     * What to show: everything for a full tour; after an update only the stops added since the
     * version he last saw, plus the closing card; nothing if there is nothing new.
     */
    fun stepsFor(seenVersion: String?, current: String): List<GuideStep> {
        if (seenVersion == null) return steps
        if (!Versions.isNewer(current, seenVersion)) return emptyList()
        val new = steps.filter { it.route != null && Versions.isNewer(it.since, seenVersion) }
        return if (new.isEmpty()) emptyList() else new + steps.last()
    }
}

/**
 * A walk through the tabs (like Forge's): it opens each tab for real, dims it, rings the tab in
 * the bottom bar and explains it in a card. [whatsNew] = shown after an update.
 */
@Composable
fun GuideOverlay(steps: List<GuideStep>, whatsNew: Boolean, onGoTo: (String) -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val cur = steps[step.coerceIn(0, steps.lastIndex)]
    val last = step >= steps.lastIndex
    LaunchedEffect(step) { cur.route?.let(onGoTo) }
    BackHandler { if (step > 0) step-- else onDone() }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pulse by rememberInfiniteTransition(label = "guide").animateFloat(
        1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val accent = MaterialTheme.colorScheme.primary
    val barHeight = 80.dp

    BoxWithConstraints(
        Modifier.fillMaxSize()
            // Taps on the screen underneath are swallowed during the tour.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val tabIndex = cur.route?.let { Guide.TABS.indexOf(it) }?.takeIf { it >= 0 && !last }
        Box(
            Modifier.fillMaxSize()
                .padding(bottom = if (tabIndex != null) navBottom + barHeight else 0.dp)
                .background(Color.Black.copy(alpha = 0.55f)),
        )
        if (tabIndex != null) {
            val slot = maxWidth / Guide.TABS.size
            val ring = 60.dp
            // In Arabic the first tab is on the right.
            val center = if (rtl) maxWidth - slot * tabIndex - slot / 2 else slot * tabIndex + slot / 2
            Box(
                Modifier
                    .offset(x = center - ring / 2, y = maxHeight - navBottom - barHeight / 2 - ring / 2)
                    .size(ring)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 1.6f - pulse }
                    .border(3.dp, accent, CircleShape),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp)
                .padding(start = 14.dp, end = 14.dp, bottom = navBottom + if (tabIndex != null) barHeight + 12.dp else 28.dp),
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(Modifier.padding(20.dp)) {
                    // On a small screen with big text the words scroll; Back / Next always stay visible.
                    AnimatedContent(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        targetState = step,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260))) togetherWith fadeOut(tween(140))
                        },
                        label = "guideStep",
                    ) { i ->
                        val g = steps[i.coerceIn(0, steps.lastIndex)]
                        Column {
                            val eyebrow = when {
                                whatsNew && g.route != null -> stringResource(R.string.guide_whats_new)
                                g.route == null -> stringResource(R.string.guide_title)
                                else -> stringResource(R.string.guide_step_of, i + 1, steps.size - 1)
                            }
                            Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = accent)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(g.title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(10.dp))
                            g.points.forEach { p ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Box(Modifier.padding(top = 10.dp).size(6.dp).clip(CircleShape).background(accent))
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(p), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.10f)).padding(12.dp),
                            ) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                val tip = if (g.tip == R.string.guide_done_tip) {
                                    stringResource(g.tip, io.github.kiraherk.smarttodo.Credits.AUTHOR)
                                } else {
                                    stringResource(g.tip)
                                }
                                Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (step > 0) {
                            TextButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.guide_back), style = MaterialTheme.typography.titleSmall)
                            }
                        } else {
                            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.guide_skip), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Dots(step, steps.size, Modifier.weight(1f))
                        Button(
                            onClick = { if (last) onDone() else step++ },
                            modifier = Modifier.heightIn(min = 52.dp).width(130.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(stringResource(if (last) R.string.guide_start else R.string.guide_next), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dots(current: Int, count: Int, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            Box(
                Modifier.height(6.dp).width(if (i == current) 18.dp else 6.dp).clip(CircleShape)
                    .background(if (i == current) accent else off),
            )
        }
    }
}
