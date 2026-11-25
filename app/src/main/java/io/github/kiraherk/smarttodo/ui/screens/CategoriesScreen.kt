package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.data.db.CategoryEntity
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.ui.CATEGORY_COLORS
import io.github.kiraherk.smarttodo.ui.CATEGORY_ICONS
import io.github.kiraherk.smarttodo.ui.components.AppCard
import io.github.kiraherk.smarttodo.ui.components.ChipFlow
import io.github.kiraherk.smarttodo.ui.components.ConfirmDialog
import io.github.kiraherk.smarttodo.ui.components.IconTile
import io.github.kiraherk.smarttodo.ui.components.ScreenHeader
import io.github.kiraherk.smarttodo.ui.displayName
import io.github.kiraherk.smarttodo.ui.toUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val categories = c.categories.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun add(name: String, color: Int, icon: String) = viewModelScope.launch { c.categories.add(name, color, icon) }
    fun save(category: CategoryEntity) = viewModelScope.launch { c.categories.update(category) }
    fun delete(key: String, moveTo: String) = viewModelScope.launch { c.categories.delete(key, moveTo) }
}

/** His categories: rename, recolour, add or remove. The AI files tasks under these. */
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val c = appContainer()
    val vm: CategoriesViewModel = viewModel { CategoriesViewModel(c) }
    val ctx = LocalContext.current
    val list by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenHeader(stringResource(R.string.categories_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.categories_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            items(list, key = { it.key }) { cat ->
                val ui = cat.toUi(ctx)
                AppCard(padding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(ui.icon, ui.color)
                        Spacer(Modifier.width(12.dp))
                        Text(ui.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = cat }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.category_edit), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (list.size > 1) {
                            IconButton(onClick = { deleting = cat }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.category_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(top = 6.dp), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.category_add), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (adding) {
        CategoryDialog(
            title = stringResource(R.string.category_add),
            initial = CategoryEntity(key = "", name = "", colorIndex = list.size % CATEGORY_COLORS.size, icon = "star"),
            onSave = { vm.add(it.name, it.colorIndex, it.icon) },
            onDismiss = { adding = false },
        )
    }
    editing?.let { cat ->
        CategoryDialog(
            title = stringResource(R.string.category_edit),
            initial = cat.copy(name = cat.displayName(ctx)),
            onSave = { edited ->
                // A built-in category keeps following the app language unless he really renamed it.
                val name = if (edited.name == cat.displayName(ctx) && cat.name.isBlank()) "" else edited.name
                vm.save(edited.copy(name = name))
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { cat ->
        val target = list.firstOrNull { it.key == CategoryKey.PERSONAL.name && it.key != cat.key } ?: list.first { it.key != cat.key }
        ConfirmDialog(
            title = stringResource(R.string.category_delete) + ": " + cat.displayName(ctx),
            text = stringResource(R.string.category_delete_explain, target.displayName(ctx)),
            confirm = stringResource(R.string.category_delete),
            onConfirm = { vm.delete(cat.key, target.key) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryDialog(title: String, initial: CategoryEntity, onSave: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.colorIndex) }
    var icon by remember { mutableStateOf(initial.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.category_color), style = MaterialTheme.typography.titleSmall)
                ChipFlow {
                    CATEGORY_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .border(3.dp, if (i == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { if (i == color) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White) }
                    }
                }
                Text(stringResource(R.string.category_icon), style = MaterialTheme.typography.titleSmall)
                ChipFlow {
                    CATEGORY_ICONS.forEach { (key, vector) ->
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable { icon = key }) {
                            IconTile(vector, CATEGORY_COLORS[color], size = 44.dp, iconSize = 24.dp, filled = key == icon)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(initial.copy(name = name.trim(), colorIndex = color, icon = icon)); onDismiss() }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
