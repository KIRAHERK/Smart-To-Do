package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.domain.ChatRole
import io.github.kiraherk.smarttodo.ui.components.BigMicButton
import io.github.kiraherk.smarttodo.ui.components.ConfirmDialog
import io.github.kiraherk.smarttodo.ui.components.EmptyState
import io.github.kiraherk.smarttodo.ui.theme.BigText
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantViewModel(private val c: AppContainer) : ViewModel() {
    val messages = c.db.chat().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun clear() = viewModelScope.launch { c.db.chat().deleteAll() }
    fun speak(text: String) = c.speaker.speakNow(text)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen() {
    val c = appContainer()
    val vm: AssistantViewModel = viewModel { AssistantViewModel(c) }
    val voice = LocalVoice.current
    val messages by vm.messages.collectAsStateWithLifecycle()
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

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistant_title)) },
                actions = {
                    if (messages.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.clear_chat)) }
                },
            )
        },
        floatingActionButton = { BigMicButton(onClick = { voice.start(VoiceViewModel.Request()) }, label = stringResource(R.string.ask)) },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) item { EmptyState(emptyText) }
                items(messages, key = { it.id }) { m ->
                    val mine = m.role == ChatRole.USER.name
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 340.dp),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Text(m.text, style = if (mine) MaterialTheme.typography.bodyLarge else BigText, modifier = Modifier.weight(1f, fill = false))
                                if (!mine) IconButton(onClick = { vm.speak(m.text) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { s -> AssistChip(onClick = { voice.ask(s) }, label = { Text(s, style = MaterialTheme.typography.bodyMedium) }) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 150.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.type_instead)) },
                    singleLine = true,
                )
                IconButton(onClick = {
                    if (typed.isNotBlank()) {
                        voice.processText(typed, VoiceViewModel.Request())
                        typed = ""
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send)) }
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
