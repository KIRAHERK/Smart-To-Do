package io.github.kiraherk.smarttodo.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ai.ActionType
import io.github.kiraherk.smarttodo.ui.actionIcon
import io.github.kiraherk.smarttodo.ui.components.ChipFlow
import io.github.kiraherk.smarttodo.ui.components.IconTile
import io.github.kiraherk.smarttodo.ui.components.tint
import io.github.kiraherk.smarttodo.ui.theme.BigText
import io.github.kiraherk.smarttodo.ui.theme.LocalBrand

/** Full-width dialog that shows what is happening with his voice command, in big clear text. */
@Composable
fun VoiceOverlay(
    state: VoiceViewModel.UiState,
    onFinish: () -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onRetry: () -> Unit,
    onSpeakAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is VoiceViewModel.UiState.Idle) return
    Dialog(
        // Back also works while it is thinking: it stops the request.
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        VoiceOverlayContent(state, onFinish, onConfirm, onReject, onRetry, onSpeakAgain, onDismiss)
    }
}

/** The card shown inside the voice dialog (separate so it can be previewed and tested). */
@Composable
fun VoiceOverlayContent(
    state: VoiceViewModel.UiState,
    onFinish: () -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onRetry: () -> Unit,
    onSpeakAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 22.dp).heightIn(max = 660.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state) {
                is VoiceViewModel.UiState.Listening -> Listening(state, onFinish, onDismiss)
                is VoiceViewModel.UiState.Thinking -> Thinking(state, onDismiss)
                is VoiceViewModel.UiState.Confirm -> Confirm(state, onConfirm, onReject, onSpeakAgain)
                is VoiceViewModel.UiState.Answer -> Answer(state, onRetry, onSpeakAgain, onDismiss)
                is VoiceViewModel.UiState.Message -> Message(state, onRetry, onSpeakAgain, onDismiss)
                VoiceViewModel.UiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun Listening(state: VoiceViewModel.UiState.Listening, onFinish: () -> Unit, onCancel: () -> Unit) {
    val brand = LocalBrand.current
    val level by animateFloatAsState(state.level, label = "level")
    val waves = rememberInfiniteTransition(label = "waves")
    val t by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "t")
    Text(stringResource(R.string.listening), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Three rings drifting outwards, a little bigger when he speaks louder.
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                val r = size.minDimension / 2 * (0.52f + 0.48f * p) * (1f + level * 0.08f)
                drawCircle(brand.heroGlow.copy(alpha = (1f - p) * 0.35f), radius = r)
            }
        }
        Box(
            Modifier.size(108.dp).scale(1f + level * 0.12f).clip(CircleShape).background(brand.heroBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
        }
    }
    Text(
        state.text.ifBlank { stringResource(R.string.listening_hint) },
        style = BigText,
        textAlign = TextAlign.Center,
        color = if (state.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
    BigButton(stringResource(R.string.done_speaking), Icons.Filled.Check, onFinish)
    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun Thinking(state: VoiceViewModel.UiState.Thinking, onCancel: () -> Unit) {
    val brand = LocalBrand.current
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            drawCircle(primary.copy(alpha = 0.10f), style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(brand.heroGlow.copy(alpha = 0f), brand.heroEnd, brand.heroStart)),
                startAngle = angle, sweepAngle = 260f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(40.dp))
    }
    Text(stringResource(R.string.thinking), style = MaterialTheme.typography.titleLarge)
    Quote(state.transcript)
    if (state.slow) {
        Text(stringResource(R.string.thinking_slow), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.tertiary)
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
        Icon(Icons.Filled.Close, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Confirm(state: VoiceViewModel.UiState.Confirm, onConfirm: () -> Unit, onReject: () -> Unit, onSpeakAgain: () -> Unit) {
    Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    state.previews.forEach { p ->
        val color = if (p.type == ActionType.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(tint(color, 0.07f, 0.16f)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(actionIcon(p.type), color, size = 46.dp, iconSize = 26.dp, filled = true)
                Spacer(Modifier.width(12.dp))
                Text(p.summary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
            val parts = p.detail.split(" · ").filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                Spacer(Modifier.size(10.dp))
                ChipFlow {
                    parts.forEach { part ->
                        Text(
                            part,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
    Quote(state.transcript, small = true)
    BigButton(stringResource(R.string.yes_save), Icons.Filled.Check, onConfirm)
    ButtonPair(
        Icons.Filled.Close, stringResource(R.string.no_cancel), onReject,
        Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.read_aloud), onSpeakAgain,
    )
}

@Composable
private fun Answer(state: VoiceViewModel.UiState.Answer, onAskAgain: () -> Unit, onSpeakAgain: () -> Unit, onClose: () -> Unit) {
    val brand = LocalBrand.current
    Box(Modifier.size(64.dp).clip(CircleShape).background(brand.heroBrush), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
    Quote(state.question, small = true)
    Text(state.answer, style = BigText, color = MaterialTheme.colorScheme.onSurface)
    BigButton(stringResource(R.string.ok), Icons.Filled.Check, onClose)
    ButtonPair(
        Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.read_aloud), onSpeakAgain,
        Icons.Filled.Mic, stringResource(R.string.speak_again), onAskAgain,
    )
}

@Composable
private fun Message(state: VoiceViewModel.UiState.Message, onRetry: () -> Unit, onSpeakAgain: () -> Unit, onClose: () -> Unit) {
    val brand = LocalBrand.current
    val (icon: ImageVector, color: Color) = when {
        state.error -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        state.canRetry -> Icons.Filled.Info to MaterialTheme.colorScheme.secondary
        else -> Icons.Filled.CheckCircle to brand.success
    }
    Box(Modifier.size(76.dp).clip(CircleShape).background(tint(color, 0.14f, 0.26f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
    }
    Text(state.text, style = BigText, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    BigButton(stringResource(R.string.ok), Icons.Filled.Check, onClose)
    if (state.canRetry) {
        ButtonPair(
            Icons.Filled.Refresh, stringResource(R.string.speak_again), onRetry,
            Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.read_aloud), onSpeakAgain,
        )
    } else {
        SideButton(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.read_aloud), onSpeakAgain, Modifier.fillMaxWidth())
    }
}

/** Two secondary buttons side by side, always the same width and the same height. */
@Composable
private fun ButtonPair(
    firstIcon: ImageVector, firstLabel: String, onFirst: () -> Unit,
    secondIcon: ImageVector, secondLabel: String, onSecond: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton(firstIcon, firstLabel, onFirst, Modifier.weight(1f).fillMaxHeight())
        SideButton(secondIcon, secondLabel, onSecond, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SideButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}

/** What he said, shown as a soft quote. */
@Composable
private fun Quote(text: String, small: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BigButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(20.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
