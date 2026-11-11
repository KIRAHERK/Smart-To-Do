package io.github.kiraherk.smarttodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kiraherk.smarttodo.R

/*
 * Look and feel: calm and premium rather than "default app". Deep emerald with a touch of gold on
 * warm ivory, soft rounded cards, and IBM Plex Sans Arabic, a clear business typeface. Everything
 * stays big and high-contrast so it is easy to read.
 */

val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

private val Emerald = Color(0xFF0E5E4F)
private val EmeraldDeep = Color(0xFF0A4A3F)
private val EmeraldBright = Color(0xFF18876F)
private val Gold = Color(0xFFB98A2E)

private val Light = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5ECE4),
    onPrimaryContainer = Color(0xFF032C24),
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E9CF),
    onSecondaryContainer = Color(0xFF3D2C07),
    tertiary = Color(0xFFC0582F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE4D9),
    onTertiaryContainer = Color(0xFF4A1A07),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF1D1B17),
    surface = Color.White,
    onSurface = Color(0xFF1D1B17),
    surfaceVariant = Color(0xFFEFEBE2),
    onSurfaceVariant = Color(0xFF615B50),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF9F5),
    surfaceContainer = Color(0xFFF3EFE7),
    surfaceContainerHigh = Color(0xFFEDE8DE),
    surfaceContainerHighest = Color(0xFFE6E0D4),
    outline = Color(0xFFCFC7B7),
    outlineVariant = Color(0xFFE7E1D5),
    error = Color(0xFFBF3A2B),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3DE),
    onErrorContainer = Color(0xFF4D0F07),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD4BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0E4A3F),
    onPrimaryContainer = Color(0xFFC9EFE3),
    secondary = Color(0xFFE5C27A),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF4A3A16),
    onSecondaryContainer = Color(0xFFF7E3BA),
    tertiary = Color(0xFFF2A281),
    onTertiary = Color(0xFF55200A),
    tertiaryContainer = Color(0xFF6E3018),
    onTertiaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF12110F),
    onBackground = Color(0xFFEDE8DF),
    surface = Color(0xFF1C1B18),
    onSurface = Color(0xFFEDE8DF),
    surfaceVariant = Color(0xFF2A2824),
    onSurfaceVariant = Color(0xFFC8C1B4),
    surfaceContainerLowest = Color(0xFF151412),
    surfaceContainerLow = Color(0xFF1C1B18),
    surfaceContainer = Color(0xFF22211D),
    surfaceContainerHigh = Color(0xFF2B2925),
    surfaceContainerHighest = Color(0xFF35332E),
    outline = Color(0xFF6E685D),
    outlineVariant = Color(0xFF3A3732),
    error = Color(0xFFF2A79B),
    onError = Color(0xFF5C1209),
    errorContainer = Color(0xFF7A2418),
    onErrorContainer = Color(0xFFFFDAD3),
)

/** Colours outside the Material scheme: the header gradient and the "done" green. */
@Immutable
data class BrandColors(
    val heroStart: Color,
    val heroEnd: Color,
    val heroGlow: Color,
    val gold: Color,
    val success: Color,
    val dark: Boolean,
) {
    val heroBrush: Brush get() = Brush.linearGradient(listOf(heroStart, heroEnd))
}

private val LightBrand = BrandColors(EmeraldDeep, EmeraldBright, Color(0xFF4FD1AE), Gold, Color(0xFF2E8B57), dark = false)
private val DarkBrand = BrandColors(Color(0xFF0B3D34), Color(0xFF136B59), Color(0xFF4FD1AE), Color(0xFFE5C27A), Color(0xFF6FD39A), dark = true)

val LocalBrand = staticCompositionLocalOf { LightBrand }

/** Bigger than Material defaults: he should never have to squint. */
private val AppTypography = Typography().let { t ->
    fun TextStyle.plex(size: Int, line: Int, weight: FontWeight) =
        copy(fontFamily = PlexArabic, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
    t.copy(
        displaySmall = t.displaySmall.plex(34, 44, FontWeight.Bold),
        headlineLarge = t.headlineLarge.plex(31, 42, FontWeight.Bold),
        headlineMedium = t.headlineMedium.plex(28, 38, FontWeight.Bold),
        headlineSmall = t.headlineSmall.plex(26, 36, FontWeight.Bold),
        titleLarge = t.titleLarge.plex(23, 32, FontWeight.Bold),
        titleMedium = t.titleMedium.plex(20, 29, FontWeight.SemiBold),
        titleSmall = t.titleSmall.plex(18, 26, FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.plex(19, 29, FontWeight.Normal),
        bodyMedium = t.bodyMedium.plex(17, 25, FontWeight.Normal),
        bodySmall = t.bodySmall.plex(15, 22, FontWeight.Normal),
        labelLarge = t.labelLarge.plex(17, 24, FontWeight.SemiBold),
        labelMedium = t.labelMedium.plex(15, 21, FontWeight.Medium),
        labelSmall = t.labelSmall.plex(13, 18, FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val BigText = TextStyle(fontFamily = PlexArabic, fontSize = 22.sp, lineHeight = 33.sp)

@Composable
fun SmartTodoTheme(textScale: Float = 1f, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val density = LocalDensity.current
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes) {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * textScale),
            LocalBrand provides if (dark) DarkBrand else LightBrand,
        ) {
            content()
        }
    }
}
