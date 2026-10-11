package app.omnitask.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.omnitask.R
import app.omnitask.model.Appearance
import app.omnitask.model.FontChoice
import app.omnitask.model.PaletteChoice
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
import app.omnitask.model.Tone

/**
 * Every colour the app draws with. Text colours keep contrast on the cards: [text] and [text2] for
 * reading, [muted] for secondary lines, [faint] as the floor for anything that must still be read.
 */
class Palette(
    val dark: Boolean,
    val bg: Color, val card: Color, val cardBorder: Color, val raised: Color, val sunken: Color, val divider: Color, val control: Color,
    val text: Color, val text2: Color, val muted: Color, val faint: Color,
    val accent: Color, val accentSoft: Color, val accentDeep: Color, val accentText: Color, val accentLine: Color, val onAccent: Color,
    val lime: Color, val limeSoft: Color, val red: Color, val redSoft: Color, val amber: Color, val amberSoft: Color, val blue: Color,
    val teal: Color, val tealText: Color, val tealSoft: Color, val tealChip: Color, val eventText: Color,
    /** The floating nav bar, a little see-through over the content. */
    val navBg: Color,
    /** Card corners: soft in the first look, tighter in the graphite one. */
    val radius: Dp,
    /**
     * The quiet look: one grey ring for every open task with the priority as a small dot after the title, events as
     * a line at the side without a fill, and grey buttons. Colour is kept for what is late or urgent.
     */
    val calm: Boolean = false,
)

object Palettes {
    /** The first look: dark only, violet and lime. */
    val midnight = Palette(
        dark = true,
        bg = Color(0xFF0D0F14), card = Color(0xFF151821), cardBorder = Color(0xFF222736), raised = Color(0xFF1F2330),
        sunken = Color(0xFF12141B), divider = Color(0xFF1E222D), control = Color(0xFF2C3140),
        text = Color(0xFFF3F4F8), text2 = Color(0xFFCDD1DB), muted = Color(0xFFAEB3C2), faint = Color(0xFF858B9D),
        accent = Color(0xFFA99CFF), accentSoft = Color(0xFF2A2550), accentDeep = Color(0xFF1A1730), accentText = Color(0xFFC7BEFF),
        accentLine = Color(0xFF3A3466), onAccent = Color(0xFF0D0F14),
        lime = Color(0xFFB9F26B), limeSoft = Color(0xFF2B3A1A), red = Color(0xFFFF8A80), redSoft = Color(0xFF3A1F22),
        amber = Color(0xFFFFC266), amberSoft = Color(0xFF33281A), blue = Color(0xFF7FB2FF),
        teal = Color(0xFF4FD1C5), tealText = Color(0xFF8CCBC4), tealSoft = Color(0xFF16302D), tealChip = Color(0xFF7EDCD1), eventText = Color(0xFFCFF4F0),
        navBg = Color(0xF0171A23), radius = 18.dp,
    )

    /** Graphite, dark: a flat grey page, one muted blue-grey accent, colour only for what is late or urgent. */
    val graphiteDark = Palette(
        dark = true,
        bg = Color(0xFF0E0E10), card = Color(0xFF161618), cardBorder = Color(0xFF26262A), raised = Color(0xFF1E1E22),
        sunken = Color(0xFF121214), divider = Color(0xFF212125), control = Color(0xFF34343A),
        text = Color(0xFFECECEE), text2 = Color(0xFFC4C4C9), muted = Color(0xFF8E8E96), faint = Color(0xFF7A7A83),
        accent = Color(0xFF9DB4D3), accentSoft = Color(0xFF1D2330), accentDeep = Color(0xFF171B24), accentText = Color(0xFFB4C6DF),
        accentLine = Color(0xFF2F3A4E), onAccent = Color(0xFF0E1118),
        lime = Color(0xFF7FAE93), limeSoft = Color(0xFF1A2620), red = Color(0xFFD9726C), redSoft = Color(0xFF2D1B1A),
        amber = Color(0xFFC4A56A), amberSoft = Color(0xFF1A1A1D), blue = Color(0xFF8FA6C4),
        teal = Color(0xFF7D8794), tealText = Color(0xFF8E8E96), tealSoft = Color.Transparent, tealChip = Color(0xFF86A6A0), eventText = Color(0xFFECECEE),
        navBg = Color(0xF0161618), radius = 12.dp, calm = true,
    )

    /** Graphite, light: white cards on a soft grey page, an ink blue-grey accent. */
    val graphiteLight = Palette(
        dark = false,
        bg = Color(0xFFF2F3F5), card = Color(0xFFFFFFFF), cardBorder = Color(0xFFE1E3E8), raised = Color(0xFFECEEF1),
        sunken = Color(0xFFEBEDF0), divider = Color(0xFFE8EAEE), control = Color(0xFFCDD2DA),
        text = Color(0xFF1B1E23), text2 = Color(0xFF3C424C), muted = Color(0xFF667080), faint = Color(0xFF6B7482),
        accent = Color(0xFF3D4F6E), accentSoft = Color(0xFFE6EAF1), accentDeep = Color(0xFFEFF2F7), accentText = Color(0xFF33445F),
        accentLine = Color(0xFFC5CCD9), onAccent = Color(0xFFFFFFFF),
        lime = Color(0xFF4C8566), limeSoft = Color(0xFFE3F0E9), red = Color(0xFFBF4B47), redSoft = Color(0xFFFBE9E8),
        amber = Color(0xFF9A6A12), amberSoft = Color(0xFFF5F1E6), blue = Color(0xFF5A6F94),
        teal = Color(0xFF8893A3), tealText = Color(0xFF667080), tealSoft = Color.Transparent, tealChip = Color(0xFF5E8C86), eventText = Color(0xFF1B1E23),
        navBg = Color(0xF2FFFFFF), radius = 12.dp, calm = true,
    )

    /** The palette for the owner's choice and, in "follow the system" mode, the phone's dark setting. */
    val current: Palette
        get() = when {
            Appearance.palette == PaletteChoice.MIDNIGHT -> midnight
            Appearance.dark -> graphiteDark
            else -> graphiteLight
        }
}

/**
 * The colours to draw with right now. Each one reads [Appearance], which is Compose state, so every
 * screen redraws when the theme or the phone's dark mode changes.
 */
object C {
    private val p get() = Palettes.current
    val bg get() = p.bg
    val card get() = p.card
    val cardBorder get() = p.cardBorder
    val raised get() = p.raised
    val sunken get() = p.sunken
    val divider get() = p.divider
    val control get() = p.control

    val text get() = p.text
    val text2 get() = p.text2
    val muted get() = p.muted
    val faint get() = p.faint

    val accent get() = p.accent
    val accentSoft get() = p.accentSoft
    val accentDeep get() = p.accentDeep
    val accentText get() = p.accentText
    val accentLine get() = p.accentLine
    val onAccent get() = p.onAccent

    val lime get() = p.lime
    val limeSoft get() = p.limeSoft
    val red get() = p.red
    val redSoft get() = p.redSoft
    val amber get() = p.amber
    val amberSoft get() = p.amberSoft
    val blue get() = p.blue
    val teal get() = p.teal
    val tealText get() = p.tealText
    val tealSoft get() = p.tealSoft
    val tealChip get() = p.tealChip
    val eventText get() = p.eventText
    val navBg get() = p.navBg
    val radius get() = p.radius
    val calm get() = p.calm
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

private fun scheme(p: Palette) = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accentText,
    secondary = p.lime,
    onSecondary = p.onAccent,
    tertiary = p.teal,
    background = p.bg,
    onBackground = p.text,
    surface = p.card,
    onSurface = p.text,
    surfaceVariant = p.raised,
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.sunken,
    surfaceContainerLow = p.card,
    surfaceContainer = p.card,
    surfaceContainerHigh = p.raised,
    surfaceContainerHighest = p.control,
    inverseSurface = p.text,
    inverseOnSurface = p.bg,
    outline = p.faint,
    outlineVariant = p.divider,
    error = p.red,
    onError = p.onAccent,
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
    val palette = Palettes.current
    val colors = remember(palette) { scheme(palette) }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

val Priority.tint: Color
    get() = when (this) {
        Priority.HIGHEST -> C.red
        Priority.HIGH -> C.amber
        Priority.MEDIUM -> C.blue
        Priority.NONE, Priority.LOW, Priority.LOWEST -> C.faint
    }

/** The small dot after the title in the quiet look: only for work that matters more than usual, else none. */
val Priority.dot: Color?
    get() = when (this) {
        Priority.HIGHEST -> C.red
        Priority.HIGH -> C.amber
        else -> null
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
