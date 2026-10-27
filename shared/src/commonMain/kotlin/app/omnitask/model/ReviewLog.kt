package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlinx.datetime.LocalDate

/**
 * When the owner last went over a task in the review mode: one line `- ทบทวน 2026-10-27` under the task, rewritten
 * each time, so there is only ever one. Only the word and a date make the line; "ทบทวน บทที่ 3" stays description.
 */
object ReviewLog {

    const val MARK = "ทบทวน"

    private val LINE = Regex("""^$MARK\s+(\d{4}-\d{2}-\d{2})$""")

    fun line(day: LocalDate): String = "$MARK $day"

    fun parse(note: String): LocalDate? =
        LINE.matchEntire(note.trim())?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

    fun isNote(note: String) = parse(note) != null

    /** The day the task was last reviewed, or null when it never was. */
    fun last(task: Task): LocalDate? = task.notes.mapNotNull(::parse).maxOrNull()

    /** Sets the review line under the task at [index] to [day]: the old review line is replaced, or one is added last. */
    fun write(lines: MutableList<String>, index: Int, day: LocalDate) {
        val indent = lines[index].takeWhile { it.isWhitespace() } + "    "
        var i = index + 1
        var found = -1
        while (i < lines.size && lines[i].isNotBlank() && lines[i].first().isWhitespace() && !TaskLine.isTask(lines[i])) {
            if (found < 0 && isNote(lines[i].trim().removePrefix("- "))) found = i
            i++
        }
        if (found >= 0) lines[found] = "$indent- ${line(day)}" else lines.add(i, "$indent- ${line(day)}")
    }
}
