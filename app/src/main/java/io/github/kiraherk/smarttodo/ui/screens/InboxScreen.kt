package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ui.components.ConfirmDialog
import io.github.kiraherk.smarttodo.ui.components.EmptyState
import io.github.kiraherk.smarttodo.ui.theme.BigText
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import io.github.kiraherk.smarttodo.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(private val c: AppContainer) : ViewModel() {
    val items = c.db.inbox().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { c.db.inbox().delete(id) }
}

/** Things he said that couldn't be understood at the time (no internet, no key, daily limit). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(onBack: () -> Unit) {
    val c = appContainer()
    val vm: InboxViewModel = viewModel { InboxViewModel(c) }
    val voice = LocalVoice.current
    val ctx = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Long?>(null) }
    val explain = stringResource(R.string.inbox_explain)
    val empty = stringResource(R.string.inbox_empty)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { Text(explain, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp)) }
            if (items.isEmpty()) item { EmptyState(empty) }
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Dates.dateTimeOf(ctx, item.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.text, style = BigText)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { voice.processText(item.text, VoiceViewModel.Request(inboxId = item.id)) },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                Text(" " + stringResource(R.string.understand_now))
                            }
                            OutlinedButton(onClick = { toDelete = item.id }, modifier = Modifier.heightIn(min = 56.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.inbox_delete_explain),
            confirm = stringResource(R.string.delete),
            onConfirm = { vm.delete(id) },
            onDismiss = { toDelete = null },
        )
    }
}
