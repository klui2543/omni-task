package app.omnitask.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's language, Thai or English. Text is written in place as `tr("ไทย", "English")`, so both
 * languages sit side by side in the code. The choice is Compose state: switching redraws every screen.
 */
object Lang {
    var english by mutableStateOf(false)

    val locale: Locale get() = if (english) Locale.ENGLISH else Locale("th")

    private const val KEY = "lang.english"

    /** Reads the saved choice; the app, the alarm receiver and the boot receiver all call this first. */
    fun load(context: Context) {
        english = context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, value: Boolean) {
        english = value
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}

fun tr(th: String, en: String): String = if (Lang.english) en else th
