package io.github.kiraherk.smarttodo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(stringResource(R.string.all), Icons.Filled.Apps, MaterialTheme.colorScheme.primary, selected == null) { onSelect(null) }
        CategoryKey.entries.forEach { key ->
            FilterPill(categoryName(ctx, key), categoryIcon(key), categoryColor(key), selected == key) { onSelect(if (selected == key) null else key) }
        }
    }
}

@Composable
private fun FilterPill(label: String, icon: ImageVector, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) color else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}
