package io.github.kiraherk.smarttodo.ui.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ui.theme.BigText

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
        onDismissRequest = { if (state !is VoiceViewModel.UiState.Thinking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
        ) {
            Column(
                Modifier.padding(20.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state) {
                    is VoiceViewModel.UiState.Listening -> Listening(state, onFinish, onDismiss)
                    is VoiceViewModel.UiState.Thinking -> Thinking(state)
                    is VoiceViewModel.UiState.Confirm -> Confirm(state, onConfirm, onReject, onSpeakAgain)
                    is VoiceViewModel.UiState.Answer -> Answer(state, onRetry, onSpeakAgain, onDismiss)
                    is VoiceViewModel.UiState.Message -> Message(state, onRetry, onSpeakAgain, onDismiss)
                    VoiceViewModel.UiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun Listening(state: VoiceViewModel.UiState.Listening, onFinish: () -> Unit, onCancel: () -> Unit) {
    val scale by animateFloatAsState(1f + state.level * 0.35f, label = "mic")
    Text(stringResource(R.string.listening), style = MaterialTheme.typography.titleLarge)
    Box(
        Modifier.size(110.dp).scale(scale).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
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
private fun Thinking(state: VoiceViewModel.UiState.Thinking) {
    CircularProgressIndicator(Modifier.size(64.dp), strokeWidth = 6.dp)
    Text(stringResource(R.string.thinking), style = MaterialTheme.typography.titleLarge)
    Text("«${state.transcript}»", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Confirm(state: VoiceViewModel.UiState.Confirm, onConfirm: () -> Unit, onReject: () -> Unit, onSpeakAgain: () -> Unit) {
    Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.titleLarge)
    state.previews.forEach { p ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(p.summary, style = BigText, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (p.detail.isNotBlank()) {
                    Text(p.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    Text("«${state.transcript}»", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    BigButton(stringResource(R.string.yes_save), Icons.Filled.Check, onConfirm)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onReject, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Text(" " + stringResource(R.string.no_cancel), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onSpeakAgain, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.read_aloud))
        }
    }
}

@Composable
private fun Answer(state: VoiceViewModel.UiState.Answer, onAskAgain: () -> Unit, onSpeakAgain: () -> Unit, onClose: () -> Unit) {
    Text("«${state.question}»", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(state.answer, style = BigText)
    BigButton(stringResource(R.string.ok), Icons.Filled.Check, onClose)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onSpeakAgain, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            Text(" " + stringResource(R.string.read_aloud))
        }
        OutlinedButton(onClick = onAskAgain, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Text(" " + stringResource(R.string.speak_again))
        }
    }
}

@Composable
private fun Message(state: VoiceViewModel.UiState.Message, onRetry: () -> Unit, onSpeakAgain: () -> Unit, onClose: () -> Unit) {
    Text(
        state.text,
        style = BigText,
        textAlign = TextAlign.Center,
        color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    BigButton(stringResource(R.string.ok), Icons.Filled.Check, onClose)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.canRetry) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(" " + stringResource(R.string.speak_again))
            }
        }
        OutlinedButton(onClick = onSpeakAgain, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.read_aloud))
        }
    }
}

@Composable
private fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Text("  $text", style = MaterialTheme.typography.titleLarge)
    }
}
