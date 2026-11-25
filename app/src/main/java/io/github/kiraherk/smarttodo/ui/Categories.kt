package io.github.kiraherk.smarttodo.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.kiraherk.smarttodo.data.db.CategoryEntity
import io.github.kiraherk.smarttodo.domain.CategoryKey

/** A category as the screens show it. */
data class CategoryUi(val key: String, val name: String, val color: Color, val icon: ImageVector)

/** Colours he can give a category. */
val CATEGORY_COLORS = listOf(
    Color(0xFF2F5DA8), Color(0xFF2E8B57), Color(0xFF8A4FA3), Color(0xFFD27A1F), Color(0xFFBF3A2B),
    Color(0xFF0E7C86), Color(0xFFB98A2E), Color(0xFF6D4C41), Color(0xFFC2185B), Color(0xFF546E7A),
)

/** Icons he can give a category (stored by name). */
val CATEGORY_ICONS: Map<String, ImageVector> = linkedMapOf(
    "work" to Icons.Filled.BusinessCenter,
    "home" to Icons.Filled.Home,
    "person" to Icons.Filled.Person,
    "flight" to Icons.Filled.Flight,
    "family" to Icons.Filled.FamilyRestroom,
    "health" to Icons.Filled.LocalHospital,
    "heart" to Icons.Filled.Favorite,
    "money" to Icons.Filled.Payments,
    "shopping" to Icons.Filled.ShoppingCart,
    "car" to Icons.Filled.DirectionsCar,
    "people" to Icons.Filled.Groups,
    "phone" to Icons.Filled.Call,
    "school" to Icons.Filled.School,
    "build" to Icons.Filled.Build,
    "event" to Icons.Filled.Event,
    "star" to Icons.Filled.Star,
)

private val OTHER = CategoryUi("", "—", Color(0xFF7A8580), Icons.Filled.Person)

/** The categories, provided once by AppRoot so every screen shows his current ones. */
val LocalCategories = staticCompositionLocalOf<List<CategoryUi>> { emptyList() }

/** Built-in name in the app language, for a built-in key. */
fun builtInCategoryName(ctx: Context, key: String): String? = CategoryKey.parse(key)?.let { categoryName(ctx, it) }

fun CategoryEntity.displayName(ctx: Context): String =
    name.ifBlank { builtInCategoryName(ctx, key) ?: key }

fun CategoryEntity.toUi(ctx: Context) = CategoryUi(
    key = key,
    name = displayName(ctx),
    color = CATEGORY_COLORS[colorIndex.mod(CATEGORY_COLORS.size)],
    icon = CATEGORY_ICONS[icon] ?: Icons.Filled.Person,
)

fun List<CategoryUi>.find(key: String?): CategoryUi =
    firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OTHER.copy(name = key ?: "—")

/** His categories; the four built-in ones until the list is provided (previews, tests). */
@Composable
fun currentCategories(): List<CategoryUi> {
    val provided = LocalCategories.current
    if (provided.isNotEmpty()) return provided
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return io.github.kiraherk.smarttodo.data.repo.CategoryRepository.DEFAULTS.map { it.toUi(ctx) }
}

/** The category with this key, as currently defined (falls back to a plain grey one). */
@Composable
fun categoryOf(key: String?): CategoryUi = currentCategories().find(key)

/** Name of a category key outside a screen (notifications, messages). */
fun categoryLabel(ctx: Context, all: List<CategoryEntity>, key: String?): String =
    all.firstOrNull { it.key.equals(key, ignoreCase = true) }?.displayName(ctx) ?: builtInCategoryName(ctx, key.orEmpty()) ?: (key ?: "")

