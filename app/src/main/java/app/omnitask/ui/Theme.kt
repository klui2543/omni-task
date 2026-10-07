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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
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

/** IBM Plex Sans Thai Looped: Thai with loops (ตัวมีหัว), which the owner finds easier to read, and matching Latin. */
val AppFont = FontFamily(
    Font(R.font.plex_thai_looped_regular, FontWeight.Normal),
    Font(R.font.plex_thai_looped_medium, FontWeight.Medium),
    Font(R.font.plex_thai_looped_semibold, FontWeight.SemiBold),
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

/** The only text sizes in the app, so every screen reads the same. */
object TS {
    /** Dense grids only: the nav bar, Gantt and month cells. */
    val micro = 12.sp
    /** Dates, counts, hints and every secondary line. */
    val caption = 12.5.sp
    /** All primary text: task titles, buttons, fields. */
    val body = 14.sp
    val title = 16.sp
    /** Big numbers in stat tiles. */
    val stat = 20.sp
}

// Line height is relative to the size, so a Text that only changes fontSize keeps room for Thai marks above and below.
private fun style(size: Number, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = AppFont, fontSize = size.toFloat().sp, lineHeight = 1.45.em, fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val AppTypography = Typography(
    displaySmall = style(32, FontWeight.Medium),
    headlineMedium = style(26, FontWeight.Medium),
    headlineSmall = style(23, FontWeight.Medium),
    titleLarge = style(20, FontWeight.Medium),
    titleMedium = style(16, FontWeight.Medium),
    titleSmall = style(14, FontWeight.Medium),
    bodyLarge = style(14),
    bodyMedium = style(14),
    bodySmall = style(12.5f),
    labelLarge = style(14, FontWeight.Medium),
    labelMedium = style(12.5f),
    labelSmall = style(12.5f),
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
