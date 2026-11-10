package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.SmartTodoApp
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.ui.categoryColor
import io.github.kiraherk.smarttodo.ui.categoryIcon
import io.github.kiraherk.smarttodo.ui.categoryName
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as SmartTodoApp).container

/** What any screen can ask of the voice system. Provided by AppRoot (which handles the mic permission). */
class VoiceActions(
    val start: (VoiceViewModel.Request) -> Unit,
    val processText: (String, VoiceViewModel.Request) -> Unit,
    val ask: (String) -> Unit,
    val searchText: SharedFlow<String>,
)

val LocalVoice = staticCompositionLocalOf<VoiceActions> { error("VoiceActions not provided") }

object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
    const val TASK = "task/{id}"
    const val REVIEW = "review/{weekly}"
    const val INBOX = "inbox"
    const val SETUP = "setup"

    fun task(id: Long) = "task/$id"
    fun review(weekly: Boolean) = "review/$weekly"
}

/** Row of big category filters: All · Work · Home · Personal · Travel. */
@Composable
fun CategoryFilter(selected: CategoryKey?, onSelect: (CategoryKey?) -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.all)) })
        CategoryKey.entries.forEach { key ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(if (selected == key) null else key) },
                label = { Text(categoryName(ctx, key)) },
                leadingIcon = { Icon(categoryIcon(key), contentDescription = null, tint = categoryColor(key), modifier = Modifier.size(20.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = categoryColor(key).copy(alpha = 0.18f)),
            )
        }
    }
}
