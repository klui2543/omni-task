package app.omnitask.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.omnitask.R
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
import app.omnitask.model.Tone

/**
 * The C+ "Midnight" palette. Text colours are chosen for contrast on the dark cards:
 * [text] 16:1, [text2] 11:1, [muted] 8:1, [faint] 5:1 (the floor for anything that must be read).
 */
object C {
    val bg = Color(0xFF0D0F14)
    val card = Color(0xFF151821)
    val cardBorder = Color(0xFF222736)
    val raised = Color(0xFF1F2330)
    val sunken = Color(0xFF12141B)
    val divider = Color(0xFF1E222D)
    val control = Color(0xFF2C3140)

    val text = Color(0xFFF3F4F8)
    val text2 = Color(0xFFCDD1DB)
    val muted = Color(0xFFAEB3C2)
    val faint = Color(0xFF858B9D)

    val accent = Color(0xFFA99CFF)
    val accentSoft = Color(0xFF2A2550)
    val accentDeep = Color(0xFF1A1730)
    val accentText = Color(0xFFC7BEFF)
    val onAccent = Color(0xFF0D0F14)

    val lime = Color(0xFFB9F26B)
    val red = Color(0xFFFF8A80)
    val redSoft = Color(0xFF3A1F22)
    val amber = Color(0xFFFFC266)
    val amberSoft = Color(0xFF33281A)
    val blue = Color(0xFF7FB2FF)
    val teal = Color(0xFF4FD1C5)
    val tealText = Color(0xFF8CCBC4)
    val tealSoft = Color(0xFF16302D)
    val tealChip = Color(0xFF7EDCD1)
}

val Prompt = FontFamily(
    Font(R.font.prompt_regular, FontWeight.Normal),
    Font(R.font.prompt_medium, FontWeight.Medium),
    Font(R.font.prompt_semibold, FontWeight.SemiBold),
)

private val Scheme = darkColorScheme(
    primary = C.accent,
    onPrimary = C.onAccent,
    primaryContainer = C.accentSoft,
    onPrimaryContainer = C.accentText,
    secondary = C.lime,
    onSecondary = C.onAccent,
    tertiary = C.teal,
    background = C.bg,
    onBackground = C.text,
    surface = C.card,
    onSurface = C.text,
    surfaceVariant = C.raised,
    onSurfaceVariant = C.muted,
    surfaceContainerLowest = C.sunken,
    surfaceContainerLow = C.card,
    surfaceContainer = C.card,
    surfaceContainerHigh = C.raised,
    surfaceContainerHighest = C.control,
    inverseSurface = C.text,
    inverseOnSurface = C.bg,
    outline = C.faint,
    outlineVariant = C.divider,
    error = C.red,
    onError = C.onAccent,
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Prompt, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private val AppTypography = Typography(
    displaySmall = style(32, 40, FontWeight.Medium),
    headlineMedium = style(26, 32, FontWeight.Medium),
    headlineSmall = style(23, 30, FontWeight.Medium),
    titleLarge = style(20, 26, FontWeight.Medium),
    titleMedium = style(16, 22, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(15, 21),
    bodyMedium = style(14, 20),
    bodySmall = style(13, 18),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(13, 18),
    labelSmall = style(12, 16),
)

@Composable
fun OmniTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}

val Priority.tint: Color
    get() = when (this) {
        Priority.HIGHEST -> C.red
        Priority.HIGH -> C.amber
        Priority.MEDIUM -> C.blue
        Priority.NONE, Priority.LOW, Priority.LOWEST -> C.faint
    }

val Quadrant.accent: Color
    get() = when (this) {
        Quadrant.DO -> C.red
        Quadrant.PLAN -> C.amber
        Quadrant.QUICK -> C.blue
        Quadrant.LATER -> C.tealChip
    }

val Tone.color: Color
    get() = when (this) {
        Tone.ALERT -> C.red
        Tone.ACCENT -> C.accentText
        Tone.PLAIN -> C.text
        Tone.MUTED -> C.muted
    }
