package app.omnitask.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import app.omnitask.time.*

/**
 * The one thing next across every project (owner's pick ก, 2026-11-03, mock six-pains screen 3, pains 1, 5 and 6 of the
 * 1 Nov interview): Klui's own next step that the secretary keeps in Claude log (an open `#kluido` task), the one to
 * start soonest first. It shows on the lock-screen card and as the first line of the today page; "ไม่ใช่ตอนนี้" shows
 * the one after it, and nothing turns red when it waits.
 *
 * The secretary writes a `ต้องเริ่ม:` line under a `#kluido` task when a project's plan file gives a start-by value
 * (as the plan writes it, `เดา` kept), and may add `ใช้เวลา:`. On Mondays the card also shows what to prepare in the
 * next four weeks: the `ต้องเริ่ม:` lines of `ไมล์สโตน.md`, which the secretary keeps to the next 28 days.
 */
object NextThing {
    const val START_BY = "ต้องเริ่ม:"
    const val TAKES = "ใช้เวลา:"
    const val MILESTONES_PATH = "📁 Folder/หลังบ้าน/Claude/ไมล์สโตน.md"
    const val AHEAD_DAYS = 28

    data class Pick(val task: Task, val meta: String?)

    /** One thing to prepare: the milestone with its project, and its start-by as the secretary wrote it. */
    data class Ahead(val title: String, val by: String)

    private fun note(t: Task, prefix: String) = t.notes.firstOrNull { it.trim().startsWith(prefix) }?.trim()?.removePrefix(prefix)?.trim()?.ifEmpty { null }

    private val FAR = LocalDate(9999, 12, 31)

    /** The open `#kluido` tasks of the book, not put off today, soonest start-by first, then in the book's order. */
    fun ranked(tasks: List<Task>, today: LocalDate, skipped: Set<String> = emptySet()): List<Pick> =
        tasks.asSequence()
            .filter { t ->
                SecretaryBook.isBook(t.filePath) && t.parent == null && !t.todayCopy && t.isOpen && t.key !in skipped &&
                    t.tags.any { it.equals(SecretaryBook.OWNER_TAG, ignoreCase = true) } && !Resting.of(t.copy(status = Status.TODO), today)
            }
            .sortedWith(compareBy<Task>({ startBy(it, today) ?: FAR }, { it.lineIndex }))
            .map { t -> Pick(t, meta(t)) }
            .toList()

    /** "ต้องเริ่มภายใน 5 พ.ย. (เดา) ใช้เวลา 1 นาที", from the secretary's lines; the task's own date when it has none. */
    fun meta(t: Task): String? {
        val by = note(t, START_BY)?.let { tr("ต้องเริ่มภายใน ", "Start by ") + it.removePrefix("ภายใน").trim() }
            ?: (t.scheduled ?: t.start)?.let { tr("ต้องเริ่มภายใน ", "Start by ") + it.dayOfMonth + " " + Countdown.monthShort(it) }
        val takes = note(t, TAKES)?.let { tr("ใช้เวลา ", "takes ") + it }
        return listOfNotNull(by, takes).joinToString(" ").ifEmpty { null }
    }

    /** The day [t] must start by: its `ต้องเริ่ม:` line read as a day, else its ⏳ or 🛫, else its 📅. */
    fun startBy(t: Task, today: LocalDate): LocalDate? =
        note(t, START_BY)?.let { day(it, today) } ?: t.scheduled ?: t.start ?: t.due

    private val ISO = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TH_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
    private val THAI = Regex("""(\d{1,2})\s*(${TH_MONTHS.joinToString("|") { Regex.escape(it) }})""")

    /**
     * The last day named in [text] ("5 พ.ย. (เดา)", "14 ถึง 19 พ.ย.", "2026-11-05"): a range counts by its last day, a
     * day without a year is the nearest such day to [today], past or future. Null when it names no day.
     */
    fun day(text: String, today: LocalDate): LocalDate? {
        ISO.findAll(text).lastOrNull()?.let { m -> return runCatching { LocalDate.parse(m.value) }.getOrNull() }
        val m = THAI.findAll(text).lastOrNull() ?: return null
        val d = m.groupValues[1].toInt()
        val month = TH_MONTHS.indexOf(m.groupValues[2]) + 1
        return listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { y -> runCatching { LocalDate(y, month, d) }.getOrNull() }
            .minByOrNull { kotlin.math.abs(it.daysUntil(today)) }
    }

    /** Whether the Monday look ahead shows today. */
    fun monday(today: LocalDate) = today.dayOfWeek == DayOfWeek.MONDAY

    private val AHEAD_LINE = Regex("""^-\s*${Regex.escape(START_BY)}\s*(.+?)\s+ภายใน\s+(.+?)(?:\s*\[ที่มา:.*\])?\s*$""")

    /**
     * What to prepare in the next [AHEAD_DAYS] days, from [milestones] (the text of `ไมล์สโตน.md`): its `ต้องเริ่ม:`
     * lines, each with the project of its `## ` heading, soonest first (words with no day last, in the file's order).
     */
    fun ahead(milestones: String?, today: LocalDate): List<Ahead> {
        if (milestones == null) return emptyList()
        var project: String? = null
        val found = ArrayList<Triple<Ahead, LocalDate?, Int>>()
        milestones.lines().forEachIndexed { i, raw ->
            val l = raw.trim()
            if (l.startsWith("## ")) project = l.removePrefix("## ").trim()
            val m = AHEAD_LINE.matchEntire(l) ?: return@forEachIndexed
            if ("💬" in l) return@forEachIndexed
            val by = m.groupValues[2].trim()
            val d = day(by, today)
            if (d != null && d > today.plusDays(AHEAD_DAYS.toLong())) return@forEachIndexed
            val title = m.groupValues[1].trim() + (project?.let { " ($it)" } ?: "")
            found += Triple(Ahead(title, by), d, i)
        }
        return found.sortedWith(compareBy<Triple<Ahead, LocalDate?, Int>>({ it.second ?: FAR }, { it.third })).map { it.first }
    }
}
