package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.db.ChatMessageEntity
import io.github.kiraherk.smarttodo.domain.ChatRole
import io.github.kiraherk.smarttodo.ui.components.ConfirmDialog
import io.github.kiraherk.smarttodo.ui.components.EmptyState
import io.github.kiraherk.smarttodo.ui.components.ScreenHeader
import io.github.kiraherk.smarttodo.ui.theme.BigText
import io.github.kiraherk.smarttodo.ui.theme.LocalBrand
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import io.github.kiraherk.smarttodo.voice.Speaker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantViewModel(private val c: AppContainer) : ViewModel() {
    val messages = c.db.chat().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val speaking = c.speaker.now
    fun clear() = viewModelScope.launch { c.speaker.stop(); c.db.chat().deleteAll() }

    /** Play / pause for one reply. */
    fun toggle(m: ChatMessageEntity) = c.speaker.toggle(m.text, key = chatKey(m.id))
    fun stopVoice() = c.speaker.stop()

    companion object {
        fun chatKey(id: Long) = "chat-$id"
    }
}

/** The assistant as a chat: what he sends appears straight away, the assistant "types", then answers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen() {
    val c = appContainer()
    val vm: AssistantViewModel = viewModel { AssistantViewModel(c) }
    val voice = LocalVoice.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val typing by voice.chatTyping.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var typed by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val suggestions = listOf(
        stringResource(R.string.suggest_today),
        stringResource(R.string.suggest_week),
        stringResource(R.string.suggest_pending),
        stringResource(R.string.suggest_travel),
    )
    val emptyText = stringResource(R.string.assistant_empty)

    // What he said into the microphone goes into the box, so he can fix a misheard word before sending.
    LaunchedEffect(Unit) { voice.dictation.collect { said -> typed = if (typed.isBlank()) said else typed.trimEnd() + " " + said } }
    // Leaving the chat stops the voice, so it never keeps talking on another screen.
    DisposableEffect(Unit) { onDispose { vm.stopVoice() } }

    val rows = messages.size + (if (typing) 1 else 0)
    // Opening the chat lands on the newest message at once; only messages that arrive later glide in.
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(rows) {
        if (rows == 0) return@LaunchedEffect
        if (landed) listState.animateScrollToItem(rows - 1) else listState.scrollToItem(rows - 1)
        landed = true
    }

    fun send(text: String) {
        if (text.isBlank()) return
        voice.chat(text)
        typed = ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenHeader(stringResource(R.string.assistant_title)) {
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.clear_chat), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty() && !typing) item { EmptyState(emptyText, Icons.Filled.AutoAwesome) }
                items(messages, key = { it.id }) { m ->
                    val now = speaking?.takeIf { it.key == AssistantViewModel.chatKey(m.id) }
                    Bubble(m, now) { vm.toggle(m) }
                }
                if (typing) item(key = "typing") { TypingBubble() }
            }
            // Ready-made questions, one tap each.
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { s ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { voice.chatAsk(s) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.type_instead)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                // Speak instead of typing: the words appear in the box first.
                IconButton(
                    onClick = { vm.stopVoice(); voice.start(VoiceViewModel.Request(VoiceViewModel.Mode.DICTATE)) },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.ask), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                FilledIconButton(
                    onClick = { send(typed) },
                    enabled = typed.isNotBlank(),
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send)) }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_chat),
            text = stringResource(R.string.clear_chat_explain),
            confirm = stringResource(R.string.clear_chat),
            onConfirm = { vm.clear() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun AssistantAvatar() {
    val brand = LocalBrand.current
    Box(Modifier.size(38.dp).clip(CircleShape).background(brand.heroBrush), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Bubble(m: ChatMessageEntity, speaking: Speaker.Now?, onPlay: () -> Unit) {
    val mine = m.role == ChatRole.USER.name
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!mine) {
            AssistantAvatar()
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape = if (mine) RoundedCornerShape(22.dp, 6.dp, 22.dp, 22.dp) else RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (mine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = if (mine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    m.text,
                    style = if (mine) MaterialTheme.typography.bodyLarge else BigText,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
                if (!mine) PlayButton(speaking, onPlay)
            }
        }
    }
}

/** ▶ Listen / ⏸ Stop / loading, so he always knows whether the voice is coming. */
@Composable
private fun PlayButton(speaking: Speaker.Now?, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val label = stringResource(
        when {
            speaking == null -> R.string.listen
            speaking.loading -> R.string.voice_loading
            else -> R.string.pause_voice
        }
    )
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            speaking == null -> Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
            speaking.loading -> CircularProgressIndicator(color = primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else -> Icon(Icons.Filled.Pause, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = primary)
    }
}

/** Three dots that bounce while the assistant is working, like a person typing in WhatsApp. */
@Composable
private fun TypingBubble() {
    val typingLabel = stringResource(R.string.assistant_typing)
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = typingLabel }, verticalAlignment = Alignment.Top) {
        AssistantAvatar()
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val a by transition.animateFloat(
                        initialValue = 0.25f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse),
                        label = "dot$i",
                    )
                    Box(Modifier.size(9.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
    }
}
