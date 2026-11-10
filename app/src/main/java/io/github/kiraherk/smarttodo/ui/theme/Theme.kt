package io.github.kiraherk.smarttodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF37474F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Color(0xFF102027),
    tertiary = Color(0xFFE65100),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE7ECEA),
    onSurfaceVariant = Color(0xFF37403E),
    error = Color(0xFFC62828),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1C313A),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF101413),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF1A1F1E),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF2A3230),
    onSurfaceVariant = Color(0xFFC9D1CF),
    error = Color(0xFFEF9A9A),
)

/** Bigger than Material defaults: he should never have to squint. */
private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontSize = 27.sp, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 19.sp, lineHeight = 28.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodySmall = t.bodySmall.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = t.labelLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontSize = 15.sp),
    )
}

val BigText = TextStyle(fontSize = 22.sp, lineHeight = 32.sp)

@Composable
fun SmartTodoTheme(textScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AppTypography) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
            content()
        }
    }
}
