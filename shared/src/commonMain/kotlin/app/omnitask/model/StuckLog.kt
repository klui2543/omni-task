package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * Why a task has stayed open so long, in the owner's words, dated: `- ค้าง 2026-10-13 รอคนอื่น: รอรูปจากพี่เอ` on a
 * line under the task. Kept in the vault next to the task (it moves to the archive with it), so it reads in
 * Obsidian and anyone reading the vault (Claude included) sees the history; the apps gather the lines into a log by date.
 */
object StuckLog {

    /** The word that starts the line, a date right after it, so a description line that merely starts with it is left alone. */
    const val MARK = "ค้าง"

    data class Entry(val day: LocalDate, val reason: String)

    /** Quick picks for the reason; the owner can add their own words after one, or write without any. */
    enum class Reason(private val th: String, private val en: String) {
        WAITING("รอคนอื่น", "Waiting on someone"),
        UNSURE("ไม่รู้จะเริ่มยังไง", "Not sure how to start"),
        TOO_BIG("งานใหญ่เกิน", "Too big"),
        NO_TIME("ไม่มีเวลา", "No time"),
        NO_ENERGY("ไม่มีแรง", "No energy"),
        LONG_PROCESS("เป็นงานยาวอยู่แล้ว", "A long process by nature"),
        ;

        val label get() = tr(th, en)

        /** Whether a note's words start with this reason, in either language. */
        fun opens(text: String) = text.startsWith(th) || text.startsWith(en)
    }

    /** Open this long (from ➕, or from the last note) with nothing written: the weekly notification asks why. */
    const val ASK_AFTER_DAYS = 21L

    private val LINE = Regex("""^$MARK\s+(\d{4}-\d{2}-\d{2})\s*:?\s*(.*)$""")

    /** The note line for [reason] written on [day], on one line so it stays a single note under the task. */
    fun line(day: LocalDate, reason: String): String = "$MARK $day ${reason.trim().replace(Regex("""\s*\n\s*"""), " ")}"

    fun parse(note: String): Entry? {
        val m = LINE.matchEntire(note.trim()) ?: return null
        val day = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull() ?: return null
        return Entry(day, m.groupValues[2].trim())
    }

    fun isNote(note: String) = parse(note) != null

    /** The task's notes, oldest first. */
    fun entries(task: Task): List<Entry> = task.notes.mapNotNull { parse(it) }.sortedBy { it.day }

    fun last(task: Task): LocalDate? = entries(task).lastOrNull()?.day

    /** Every note of every task, newest day first, a day's notes in task order. */
    fun timeline(tasks: List<Task>): List<Pair<LocalDate, List<Pair<Task, Entry>>>> =
        tasks.filter { it.list == null }
            .flatMap { t -> entries(t).map { t to it } }
            .groupBy { it.second.day }
            .toList()
            .sortedByDescending { it.first }

    /**
     * Open top-level tasks that have sat [ASK_AFTER_DAYS] or longer since they were made or last explained, oldest
     * first. Repeating tasks keep their first ➕ as they move on, and parked ones (#สักวัน) wait on purpose, so neither is asked.
     */
    fun toAsk(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { t -> t.isOpen && t.parent == null && t.list == null && t.recurrence == null && !Focus.isSomeday(t) }
            .mapNotNull { t ->
                val base = listOfNotNull(t.created, last(t)).maxOrNull() ?: return@mapNotNull null
                if (ChronoUnit.DAYS.between(base, today) >= ASK_AFTER_DAYS) t to base else null
            }
            .sortedBy { it.second }
            .map { it.first }

    /**
     * Whether the task's notes (or the question) belong up front where it is shown: it is open and late, has sat
     * [ASK_AFTER_DAYS] or longer, or already has notes.
     */
    fun matters(task: Task, today: LocalDate): Boolean =
        task.isOpen && task.list == null && (
            entries(task).isNotEmpty() ||
                task.due?.let { it < today } == true ||
                (task.recurrence == null && (Focus.ageDays(task, today) ?: 0) >= ASK_AFTER_DAYS)
            )

    /**
     * For the picture handed to Claude (in Thai, as the rest of it): each open task's latest notes, the most
     * recently explained first, so Claude sees what holds the work up and whether it is a process or a struggle.
     */
    fun summary(tasks: List<Task>, perTask: Int = 3, maxTasks: Int = 20): List<String> =
        tasks.filter { it.isOpen && it.list == null && entries(it).isNotEmpty() }
            .sortedByDescending { last(it) }
            .take(maxTasks)
            .map { t -> t.title + ": " + entries(t).takeLast(perTask).joinToString("; ") { "${it.day} ${it.reason}".trim() } }

    // ---- The path a task has taken ----

    enum class Step { START, NOTE, DONE }

    /** One point on a task's path: when it was made, each note, each subtask finished. */
    data class Event(val day: LocalDate, val step: Step, val text: String)

    /**
     * The task's path, oldest first: made (➕, or 🛫 when there is no ➕), every note, and every subtask ticked off
     * ([subtasks] are the tasks right under it). Side by side they show a long process still moving or a task that is stuck.
     */
    fun events(task: Task, subtasks: List<Task>): List<Event> = buildList {
        (task.created ?: task.start)?.let { add(Event(it, Step.START, "")) }
        entries(task).forEach { add(Event(it.day, Step.NOTE, it.reason)) }
        subtasks.forEach { s -> s.done?.takeIf { s.status == Status.DONE }?.let { add(Event(it, Step.DONE, s.title)) } }
    }.sortedWith(compareBy<Event>({ it.day }, { it.step.ordinal }))

    /** The last day something happened to the task (a note or a finished subtask), or null when nothing has. */
    /** Days an open task has sat since it was made or last moved (a note, a subtask ticked), when that is [ASK_AFTER_DAYS] or more. */
    fun staleDays(task: Task, subtasks: List<Task>, today: LocalDate): Int? {
        if (!task.isOpen || task.recurrence != null) return null
        val base = listOfNotNull(task.created, lastMove(task, subtasks)).maxOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(base, today)
        return if (days >= ASK_AFTER_DAYS) days.toInt() else null
    }

    fun lastMove(task: Task, subtasks: List<Task>): LocalDate? = events(task, subtasks).lastOrNull { it.step != Step.START }?.day

    /** What each of a task's weeks held, for the small bars: nothing, a note, or a finished subtask (which wins). */
    enum class Week { NONE, NOTE, DONE }

    /**
     * The task's last [max] weeks, oldest first and the current week last, from the week it was made: a task made
     * this week has one bar. Weeks run back from [today] in sevens.
     */
    fun weeks(task: Task, subtasks: List<Task>, today: LocalDate, max: Int = 8): List<Week> {
        val begin = task.created ?: task.start ?: today
        val count = (ChronoUnit.DAYS.between(begin, today) / 7 + 1).toInt().coerceIn(1, max)
        val events = events(task, subtasks)
        return (count - 1 downTo 0).map { back ->
            val end = today.minusDays(7L * back)
            val start = end.minusDays(6)
            val inWeek = events.filter { it.day in start..end }
            when {
                inWeek.any { it.step == Step.DONE } -> Week.DONE
                inWeek.any { it.step == Step.NOTE } -> Week.NOTE
                else -> Week.NONE
            }
        }
    }

    /** Every task with notes, each with its path; the most recently explained first. */
    fun byTask(tasks: List<Task>): List<Pair<Task, List<Event>>> =
        tasks.filter { it.list == null && entries(it).isNotEmpty() }
            .sortedByDescending { last(it) }
            .map { t -> t to events(t, tasks.filter { it.parent == t.key }) }
}
