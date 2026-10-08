package app.omnitask.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** The language choice as Compose state, so switching redraws every screen. */
private object ComposeLang : LangHolder {
    override var english by mutableStateOf(false)
}

private const val KEY = "lang.english"

private fun useCompose() {
    if (Lang.holder !== ComposeLang) {
        ComposeLang.english = Lang.english
        Lang.holder = ComposeLang
    }
}

val Lang.locale: Locale get() = if (english) Locale.ENGLISH else Locale("th")

/** Reads the saved choice; the app, the alarm receiver and the boot receiver all call this first. */
fun Lang.load(context: Context) {
    useCompose()
    english = context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).getBoolean(KEY, false)
}

fun Lang.set(context: Context, value: Boolean) {
    useCompose()
    english = value
    context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
}
