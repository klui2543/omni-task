package app.omnitask.model

/** Where an app keeps the language choice. */
interface LangHolder {
    var english: Boolean
}

/**
 * The app's language, Thai or English. Text is written in place as `tr("ไทย", "English")`, so both
 * languages sit side by side in the code. Each app keeps the choice its own way: Android puts it in Compose
 * state ([holder]), so switching redraws every screen.
 */
object Lang {
    var holder: LangHolder = object : LangHolder {
        override var english = false
    }

    var english: Boolean
        get() = holder.english
        set(value) {
            holder.english = value
        }
}

fun tr(th: String, en: String): String = if (Lang.english) en else th
