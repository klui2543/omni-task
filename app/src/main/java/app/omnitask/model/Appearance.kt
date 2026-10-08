package app.omnitask.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The fonts the owner can pick. [sizeFactor] evens out how big a font reads at the same point size.
 * Measured on the files in res/font, the Thai letter ก stands 0.586 em in Sarabun, 0.558 in Plex,
 * 0.566 in Noto and 0.550 in Prompt, so none needs a correction yet; the hook stays for fonts that do.
 */
enum class FontChoice(private val th: String, private val en: String, val sizeFactor: Float) {
    SARABUN("Sarabun (คล้าย TH Sarabun)", "Sarabun (like TH Sarabun)", 1f),
    PLEX_LOOPED("IBM Plex Sans Thai Looped", "IBM Plex Sans Thai Looped", 1f),
    NOTO_LOOPED("Noto Sans Thai Looped", "Noto Sans Thai Looped", 1f),
    PROMPT("Prompt (ไม่มีหัว)", "Prompt (loopless)", 1f),
    SYSTEM("ฟอนต์ของระบบ", "System font", 1f),
    ;

    val label get() = tr(th, en)
}

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode(private val th: String, private val en: String) {
    SYSTEM("ตามระบบ", "System"), LIGHT("สว่าง", "Light"), DARK("มืด", "Dark");

    val label get() = tr(th, en)
}

/** The colour set: the Linear look (light and dark), or the first violet one (dark only). */
enum class PaletteChoice(private val th: String, private val en: String) {
    LINEAR("Linear", "Linear"), MIDNIGHT("สีเดิม (มืดเท่านั้น)", "Original (dark only)");

    val label get() = tr(th, en)
}

/**
 * The app's font, text size and theme. Both are Compose state, so changing them redraws every screen; the
 * values live in the "omnitask" preferences, which the view model also mirrors to the vault.
 */
object Appearance {
    var font by mutableStateOf(FontChoice.SARABUN)
        private set

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var palette by mutableStateOf(PaletteChoice.LINEAR)
        private set

    /** Whether the phone is in dark mode; the activity keeps it current. */
    var systemDark by mutableStateOf(true)

    /** Whether the app draws dark now. */
    val dark: Boolean
        get() = palette == PaletteChoice.MIDNIGHT || when (themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    /** The owner's text size, 1 = the design size. */
    var scale by mutableFloatStateOf(1f)
        private set

    /** What every text size is multiplied by: the chosen size times the font's own correction. */
    val factor: Float get() = scale * font.sizeFactor

    /** The sizes offered in settings. */
    val SCALES = listOf(0.9f, 1f, 1.15f, 1.3f)

    private const val PREFS = "omnitask"
    private const val KEY_FONT = "appearance.font"
    private const val KEY_SCALE = "appearance.scale"
    private const val KEY_THEME = "appearance.theme"
    private const val KEY_PALETTE = "appearance.palette"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the saved choice; call before the first frame so the app never flashes the default font. */
    fun load(context: Context) {
        val all = prefs(context).all
        font = (all[KEY_FONT] as? String)
            ?.let { name -> FontChoice.entries.firstOrNull { it.name == name } }
            ?: FontChoice.SARABUN
        // Read loosely: a value restored from the vault file must never crash the start of the app.
        scale = when (val v = all[KEY_SCALE]) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull()
            else -> null
        }?.takeIf { it in 0.5f..2f } ?: 1f
        themeMode = (all[KEY_THEME] as? String)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
        palette = (all[KEY_PALETTE] as? String)?.let { name -> PaletteChoice.entries.firstOrNull { it.name == name } } ?: PaletteChoice.LINEAR
    }

    fun setThemeMode(context: Context, value: ThemeMode) {
        themeMode = value
        prefs(context).edit().putString(KEY_THEME, value.name).apply()
    }

    fun setPalette(context: Context, value: PaletteChoice) {
        palette = value
        prefs(context).edit().putString(KEY_PALETTE, value.name).apply()
    }

    fun setFont(context: Context, value: FontChoice) {
        font = value
        prefs(context).edit().putString(KEY_FONT, value.name).apply()
    }

    fun setScale(context: Context, value: Float) {
        scale = value
        prefs(context).edit().putFloat(KEY_SCALE, value).apply()
    }
}
