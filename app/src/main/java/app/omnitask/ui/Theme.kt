package app.omnitask.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant

/**
 * A fixed, quiet palette in the spirit of Obsidian task plugins: neutral greys, one muted violet accent,
 * colour used only where it carries meaning. Wallpaper-based dynamic colour is deliberately not used.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF5B54C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E7FA),
    onPrimaryContainer = Color(0xFF2A2566),
    secondary = Color(0xFF5F6170),
    secondaryContainer = Color(0xFFEDEDF2),
    onSecondaryContainer = Color(0xFF2B2C33),
    background = Color(0xFFFAFAFB),
    surface = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF2F2F5),
    surfaceContainerLow = Color(0xFFF5F5F7),
    surfaceContainerHigh = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFFEDEDF0),
    onSurface = Color(0xFF1E1F24),
    onSurfaceVariant = Color(0xFF6B6D78),
    outline = Color(0xFFA3A5B0),
    outlineVariant = Color(0xFFE2E2E8),
    error = Color(0xFFC0453E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9D97F2),
    onPrimary = Color(0xFF1E1A4D),
    primaryContainer = Color(0xFF2E2B52),
    onPrimaryContainer = Color(0xFFE2DFFF),
    secondary = Color(0xFFB4B6C2),
    secondaryContainer = Color(0xFF2A2B31),
    onSecondaryContainer = Color(0xFFDCDDE4),
    background = Color(0xFF17181C),
    surface = Color(0xFF17181C),
    surfaceContainer = Color(0xFF1F2025),
    surfaceContainerLow = Color(0xFF1B1C20),
    surfaceContainerHigh = Color(0xFF25262C),
    surfaceVariant = Color(0xFF25262C),
    onSurface = Color(0xFFE4E4E9),
    onSurfaceVariant = Color(0xFF9A9CA8),
    outline = Color(0xFF5C5E69),
    outlineVariant = Color(0xFF2C2D34),
    error = Color(0xFFE5776F),
)

private val base = Typography()
private val AppTypography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)

@Composable
fun OmniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}

/** Muted signal colours: readable in both themes, never louder than the text they sit beside. */
val Priority.tint: Color?
    get() = when (this) {
        Priority.HIGHEST -> Color(0xFFC0574F)
        Priority.HIGH -> Color(0xFFC08A3E)
        Priority.MEDIUM -> Color(0xFF5B83B5)
        Priority.LOW, Priority.LOWEST -> Color(0xFF8A8D99)
        Priority.NONE -> null
    }

val Quadrant.accent: Color
    get() = when (this) {
        Quadrant.DO -> Color(0xFFC0574F)
        Quadrant.PLAN -> Color(0xFFC08A3E)
        Quadrant.QUICK -> Color(0xFF5B83B5)
        Quadrant.LATER -> Color(0xFF6C9479)
    }
