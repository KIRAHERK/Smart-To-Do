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
import io.github.kiraherk.smarttodo.ui.components.AppCard
import io.github.kiraherk.smarttodo.ui.components.ScreenHeader
import io.github.kiraherk.smarttodo.ui.theme.LocalBrand
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    val brand = LocalBrand.current
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
        floatingActionButton = { BigMicButton(onClick = { voice.start(VoiceViewModel.Request()) }, label = stringResource(R.string.ask)) },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) {
                    item { EmptyState(emptyText, Icons.Filled.AutoAwesome) }
                    items(suggestions) { s ->
                        AppCard(onClick = { voice.ask(s) }, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(s, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { m ->
                    val mine = m.role == ChatRole.USER.name
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (!mine) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(brand.heroBrush), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
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
                                if (!mine) {
                                    Row(
                                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.speak(m.text) }.padding(vertical = 4.dp, horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.read_aloud), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (messages.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { s ->
                        Text(
                            s,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { voice.ask(s) }.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 116.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.type_instead)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (typed.isNotBlank()) {
                            voice.processText(typed, VoiceViewModel.Request())
                            typed = ""
                        }
                    },
                    modifier = Modifier.size(56.dp),
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
