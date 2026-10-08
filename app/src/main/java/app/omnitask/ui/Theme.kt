package app.omnitask.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.omnitask.R
import app.omnitask.model.Appearance
import app.omnitask.model.FontChoice
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

/** Sarabun: the open redesign of TH Sarabun New, the face the owner reads every day. */
private val Sarabun = FontFamily(
    Font(R.font.sarabun_regular, FontWeight.Normal),
    Font(R.font.sarabun_medium, FontWeight.Medium),
    Font(R.font.sarabun_semibold, FontWeight.SemiBold),
)

/** IBM Plex Sans Thai Looped: Thai with loops (ตัวมีหัว) and matching Latin. */
private val PlexLooped = FontFamily(
    Font(R.font.plex_thai_looped_regular, FontWeight.Normal),
    Font(R.font.plex_thai_looped_medium, FontWeight.Medium),
    Font(R.font.plex_thai_looped_semibold, FontWeight.SemiBold),
)

/** Google ships Noto Sans Thai Looped only as a variable font, so one file serves every weight through its wght axis. */
@OptIn(ExperimentalTextApi::class)
private fun notoLooped(weight: FontWeight): Font = Font(
    R.font.noto_sans_thai_looped,
    weight,
    FontStyle.Normal,
    FontLoadingStrategy.Blocking,
    FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val NotoLooped = FontFamily(
    notoLooped(FontWeight.Normal),
    notoLooped(FontWeight.Medium),
    notoLooped(FontWeight.SemiBold),
)

/** Prompt: loopless Thai (ไม่มีหัว), the app's first font. */
private val Prompt = FontFamily(
    Font(R.font.prompt_regular, FontWeight.Normal),
    Font(R.font.prompt_medium, FontWeight.Medium),
    Font(R.font.prompt_semibold, FontWeight.SemiBold),
)

val FontChoice.family: FontFamily
    get() = when (this) {
        FontChoice.SARABUN -> Sarabun
        FontChoice.PLEX_LOOPED -> PlexLooped
        FontChoice.NOTO_LOOPED -> NotoLooped
        FontChoice.PROMPT -> Prompt
        FontChoice.SYSTEM -> FontFamily.Default
    }

/** The font picked in settings. Reading it is a state read, so text that uses it follows a change at once. */
val AppFont: FontFamily get() = Appearance.font.family

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

/**
 * The only text sizes in the app, so every screen reads the same. Each is the design size times the
 * text size chosen in settings; reading one is a state read, so screens redraw when it changes.
 */
object TS {
    /** Dense grids only: the nav bar, Gantt and month cells. */
    val micro: TextUnit get() = scaled(12f)
    /** Dates, counts, hints and every secondary line. */
    val caption: TextUnit get() = scaled(12.5f)
    /** All primary text: task titles, buttons, fields. */
    val body: TextUnit get() = scaled(14f)
    val title: TextUnit get() = scaled(16f)
    /** Big numbers in stat tiles. */
    val stat: TextUnit get() = scaled(20f)
}

private fun scaled(size: Float): TextUnit = (size * Appearance.factor).sp

// Line height is relative to the size, so a Text that only changes fontSize keeps room for Thai marks above and below.
private fun style(font: FontFamily, size: Float, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = font, fontSize = size.sp, lineHeight = 1.45.em, fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Material's text styles in the chosen font, every size multiplied by [factor]. */
private fun appTypography(font: FontFamily, factor: Float) = Typography(
    displaySmall = style(font, 32f * factor, FontWeight.Medium),
    headlineMedium = style(font, 26f * factor, FontWeight.Medium),
    headlineSmall = style(font, 23f * factor, FontWeight.Medium),
    titleLarge = style(font, 20f * factor, FontWeight.Medium),
    titleMedium = style(font, 16f * factor, FontWeight.Medium),
    titleSmall = style(font, 14f * factor, FontWeight.Medium),
    bodyLarge = style(font, 14f * factor),
    bodyMedium = style(font, 14f * factor),
    bodySmall = style(font, 12.5f * factor),
    labelLarge = style(font, 14f * factor, FontWeight.Medium),
    labelMedium = style(font, 12.5f * factor),
    labelSmall = style(font, 12.5f * factor),
)

@Composable
fun OmniTheme(content: @Composable () -> Unit) {
    val font = Appearance.font
    val factor = Appearance.factor
    val typography = remember(font, factor) { appTypography(font.family, factor) }
    MaterialTheme(colorScheme = Scheme, typography = typography, content = content)
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
