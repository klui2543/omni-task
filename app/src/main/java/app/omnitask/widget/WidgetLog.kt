package app.omnitask.widget

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the month widget did and when, for finding out why a tap was slow on the owner's phone: the lines are shared
 * from the frames and emoji page. Kept in memory and in a small file in the app's own storage.
 */
object WidgetLog {
    private const val KEEP = 600
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var file: File? = null

    fun attach(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, "widget-log.txt")
    }

    fun add(what: String) {
        val line = "${clock.format(Date())} ${SystemClock.elapsedRealtime() % 100_000} $what"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > KEEP) lines.removeFirst()
        }
        file?.let { f ->
            runCatching {
                if (f.length() > 200_000) f.writeText(text())
                else f.appendText(line + "\n")
            }
        }
    }

    /** The lines kept, oldest first. */
    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    /** The file's lines, which outlive the process, then what is in memory. */
    fun all(context: Context): String {
        attach(context)
        val saved = runCatching { file?.readText() }.getOrNull().orEmpty()
        return (if (saved.isEmpty()) "" else saved.trimEnd() + "\n") + text()
    }

    /** Milliseconds since [since] (an elapsedRealtime), for lines that say how long a step took. */
    fun ms(since: Long): Long = SystemClock.elapsedRealtime() - since
}
