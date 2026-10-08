package app.omnitask.data

import android.content.Context
import java.time.LocalDate

/**
 * When each repeating task was completed. A repeating line is moved on in place rather than copied,
 * so its history lives here (in the app's preferences, which are mirrored to the vault settings file).
 */
object RecurHistory {

    private const val KEY = "recurHistory"

    fun add(context: Context, title: String, day: LocalDate) {
        val prefs = context.getSharedPreferences("omnitask", Context.MODE_PRIVATE)
        // Oldest entries fall off so the set stays small.
        val kept = prefs.getStringSet(KEY, emptySet()).orEmpty().sortedBy { it.substringAfterLast('|') }.takeLast(499)
        prefs.edit().putStringSet(KEY, (kept + "$title|$day").toSet()).apply()
    }

    /** Completion days per task title. */
    fun all(context: Context): Map<String, List<LocalDate>> =
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { e -> runCatching { e.substringBeforeLast('|') to LocalDate.parse(e.substringAfterLast('|')) }.getOrNull() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, days) -> days.sorted() }
}
