package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * Reads a quick-add sentence the way people type it, e.g. "ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ !!":
 * a day word or date, a time, tags and `!` marks are pulled out, and the rest is the title.
 */
object QuickAdd {

    data class Draft(
        val title: String,
        val due: LocalDate? = null,
        val time: LocalTime? = null,
        val tags: List<String> = emptyList(),
        val priority: Priority = Priority.NONE,
        /** The end of a typed time range ("9:00-11:00"); only an event uses it. */
        val endTime: LocalTime? = null,
        /** The last day of a typed date range ("20-22/10"); only an event uses it, a task keeps [due]. */
        val endDue: LocalDate? = null,
    ) {
        /** The TaskForge line, in TaskForge token order. */
        fun line(today: LocalDate): String {
            var line = "- [ ] $title"
            tags.forEach { line = TaskLine.addTag(line, it) }
            if (time != null) line = TaskLine.setReminder(line, time, ReminderOn.DUE)
            if (priority != Priority.NONE) line = TaskLine.setPriority(line, priority)
            line = TaskLine.setDate(line, DateField.CREATED, today)
            if (due != null) line = TaskLine.setDate(line, DateField.DUE, due)
            return line
        }
    }

    /** A task written from the detailed add page: the line in TaskForge token order, then its notes and subtasks. */
    data class Detail(
        val title: String,
        /** True when [date] is the ⏳ scheduled day (reminder on that day), false for the 📅 due day. */
        val scheduled: Boolean = false,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val start: LocalDate? = null,
        val rule: String? = null,
        val tags: List<String> = emptyList(),
        val priority: Priority = Priority.NONE,
        val notes: List<String> = emptyList(),
        val subtasks: List<String> = emptyList(),
    ) {
        fun line(today: LocalDate): String {
            var line = "- [ ] $title"
            tags.forEach { line = TaskLine.addTag(line, it) }
            if (time != null) line = TaskLine.setReminder(line, time, if (scheduled) ReminderOn.SCHEDULED else ReminderOn.DUE)
            if (priority != Priority.NONE) line = TaskLine.setPriority(line, priority)
            if (rule != null) line = TaskLine.setRecurrence(line, rule)
            line = TaskLine.setDate(line, DateField.CREATED, today)
            if (start != null) line = TaskLine.setDate(line, DateField.START, start)
            if (date != null) line = TaskLine.setDate(line, if (scheduled) DateField.SCHEDULED else DateField.DUE, date)
            return line
        }

        /** The task line with its description lines and subtasks under it, as written to the note. */
        fun block(today: LocalDate): String = buildList {
            add(line(today))
            notes.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add("    - $it") }
            subtasks.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add("    - [ ] $it") }
        }.joinToString("\n")
    }

    private val TAG = Regex(app.omnitask.data.TaskLine.TAG_PATTERN)
    // "9:00-11:00", "9:00 ถึง 11:00", "9:00 to 11:00".
    private val TIME_RANGE = Regex("""(?<![\d:])([01]?\d|2[0-3])[.:]([0-5]\d)\s*(?:-|–|ถึง|to)\s*([01]?\d|2[0-3])[.:]([0-5]\d)(?![\d:])(?:\s*น\.)?""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""(?<![\d:])([01]?\d|2[0-3])[.:]([0-5]\d)(?![\d:])(?:\s*น\.)?""")
    private val BANG = Regex("""(?<!\S)(!{1,3})(?!\S)""")
    // "20-22/10" (one month), and "28/12-3/1" or "20/10 ถึง 22/10" (each day with its month).
    private val DAY_RANGE = Regex("""(?<![\d/])(\d{1,2})\s*(?:-|–|ถึง|to)\s*(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?![\d/])""", RegexOption.IGNORE_CASE)
    private val DATE_RANGE = Regex("""(?<![\d/])(\d{1,2})/(\d{1,2})\s*(?:-|–|ถึง|to)\s*(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?![\d/])""", RegexOption.IGNORE_CASE)
    private val SLASH_DATE = Regex("""(?<![\d/])(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?![\d/])""")

    private val THAI_DAYS = listOf("จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "พฤหัส", "ศุกร์", "เสาร์", "อาทิตย์")
    private fun thaiDay(word: String) = when (word) {
        "จันทร์" -> DayOfWeek.MONDAY
        "อังคาร" -> DayOfWeek.TUESDAY
        "พุธ" -> DayOfWeek.WEDNESDAY
        "พฤหัสบดี", "พฤหัส" -> DayOfWeek.THURSDAY
        "ศุกร์" -> DayOfWeek.FRIDAY
        "เสาร์" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    // "วันจันทร์", "วันจันทร์หน้า", or a bare "จันทร์" standing on its own.
    private val THAI_WEEKDAY = Regex("""(?:วัน(${THAI_DAYS.joinToString("|")})|(?<!\S)(${THAI_DAYS.joinToString("|")}))(นี้|หน้า)?(?!\S)""")
    private val EN_WEEKDAY = Regex("""(?<!\S)(next\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?!\S)""", RegexOption.IGNORE_CASE)

    private val WORDS: List<Pair<Regex, (LocalDate) -> LocalDate>> = listOf(
        Regex("""มะรืนนี้|มะรืน""") to { d -> d.plusDays(2) },
        Regex("""พรุ่งนี้""") to { d -> d.plusDays(1) },
        Regex("""วันนี้|คืนนี้""") to { d -> d },
        Regex("""สัปดาห์หน้า|(?<!วัน)อาทิตย์หน้า""") to { d -> d.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) },
        Regex("""สิ้นเดือน""") to { d -> d.with(TemporalAdjusters.lastDayOfMonth()) },
        Regex("""(?<!\S)(?:the\s+)?day after tomorrow(?!\S)""", RegexOption.IGNORE_CASE) to { d -> d.plusDays(2) },
        Regex("""(?<!\S)tomorrow(?!\S)""", RegexOption.IGNORE_CASE) to { d -> d.plusDays(1) },
        Regex("""(?<!\S)(?:today|tonight)(?!\S)""", RegexOption.IGNORE_CASE) to { d -> d },
        Regex("""(?<!\S)next week(?!\S)""", RegexOption.IGNORE_CASE) to { d -> d.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) },
    )

    /** What "#" can turn into while typing: a list note (or one of its categories), or a tag already in use. */
    sealed interface HashPick {
        data class ToList(val list: OmniList, val category: String? = null) : HashPick
        data class ToTag(val tag: String) : HashPick
    }

    /** The "#word" being typed at the end of [text], without the #, or null when the last word is not a tag. */
    fun hashToken(text: String): String? = text.substringAfterLast(' ').takeIf { it.startsWith("#") }?.removePrefix("#")

    /**
     * Choices for the typed [token]: lists whose name (or a category) contains it first, then up to six tags.
     * A bare "#" offers every list.
     */
    fun hashPicks(token: String, lists: List<OmniList>, tags: List<String>): List<HashPick> {
        val t = token.trim().lowercase()
        val out = ArrayList<HashPick>()
        lists.forEach { l ->
            val named = l.name.lowercase().contains(t)
            if (named) out += HashPick.ToList(l)
            l.categories.filter { named && t.isNotEmpty() || (t.isNotEmpty() && it.lowercase().contains(t)) }.forEach { out += HashPick.ToList(l, it) }
        }
        val listNames = lists.map { it.name.lowercase() }.toSet()
        tags.distinct().filter { it.lowercase().contains(t) && it.lowercase() !in listNames }
            .sortedBy { !it.lowercase().startsWith(t) }.take(6).forEach { out += HashPick.ToTag(it) }
        return out
    }

    fun parse(text: String, today: LocalDate): Draft = read(text, today).first

    /**
     * Where the day and the time were read in the trimmed [text] (null when none was typed), so the chips under
     * the box can take a typed day or time out, or put another in its place.
     */
    data class Spans(val due: IntRange?, val time: IntRange?)

    fun spans(text: String, today: LocalDate): Spans = read(text, today).second

    private fun read(text: String, today: LocalDate): Pair<Draft, Spans> {
        var rest = " ${text.trim()} "
        // Cut words are blanked with as many spaces, so every place in [rest] is still its place in the text.
        var cutRange: IntRange? = null
        fun cut(range: IntRange) {
            rest = rest.substring(0, range.first) + " ".repeat(range.last - range.first + 1) + rest.substring(range.last + 1)
            cutRange = range
        }
        fun place(r: IntRange?) = r?.let { (it.first - 1)..(it.last - 1) }

        val tags = TAG.findAll(rest).map { it.value.removePrefix("#") }.toList()
        rest = TAG.replace(rest) { " ".repeat(it.value.length) }

        var priority = Priority.NONE
        BANG.find(rest)?.let { m ->
            priority = when (m.groupValues[1].length) { 3 -> Priority.HIGHEST; 2 -> Priority.HIGH; else -> Priority.MEDIUM }
            cut(m.range)
        }

        var time: LocalTime? = null
        var endTime: LocalTime? = null
        TIME_RANGE.find(rest)?.let { m ->
            val g = m.groupValues
            time = LocalTime.of(g[1].toInt(), g[2].toInt())
            endTime = LocalTime.of(g[3].toInt(), g[4].toInt())
            cut(m.range)
        }
        if (time == null) TIME.find(rest)?.let { m ->
            time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            cut(m.range)
        }

        val timeSpan = if (time != null) place(cutRange) else null
        cutRange = null

        var due: LocalDate? = null
        var endDue: LocalDate? = null
        // A range's days: a year typed at the end is for both; without one, a range already over is next year's.
        fun range(fromDay: Int, fromMonth: Int, toDay: Int, toMonth: Int, typedYear: String, m: MatchResult) {
            val year = year(typedYear)
            val from = runCatching { LocalDate.of(year ?: today.year, fromMonth, fromDay) }.getOrNull() ?: return
            var to = runCatching { LocalDate.of(from.year, toMonth, toDay) }.getOrNull() ?: return
            // "28/12-3/1": the end is in the next year.
            if (to < from) to = to.plusYears(1)
            val shift = year == null && to < today
            due = if (shift) from.plusYears(1) else from
            endDue = if (shift) to.plusYears(1) else to
            cut(m.range)
        }
        DATE_RANGE.find(rest)?.let { m ->
            val g = m.groupValues
            range(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5], m)
        }
        if (due == null) DAY_RANGE.find(rest)?.let { m ->
            val g = m.groupValues
            range(g[1].toInt(), g[3].toInt(), g[2].toInt(), g[3].toInt(), g[4], m)
        }
        if (due == null) SLASH_DATE.find(rest)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = year(m.groupValues[3])
            runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull()?.let { d ->
                due = if (year == null && d < today) d.plusYears(1) else d
                cut(m.range)
            }
        }
        if (due == null) {
            THAI_WEEKDAY.find(rest)?.let { m ->
                val dow = thaiDay(m.groupValues[1].ifEmpty { m.groupValues[2] })
                val base = today.with(TemporalAdjusters.next(dow))
                due = if (m.groupValues[3] == "หน้า") base.plusWeeks(1) else base
                cut(m.range)
            }
        }
        if (due == null) {
            EN_WEEKDAY.find(rest)?.let { m ->
                val dow = DayOfWeek.valueOf(m.groupValues[2].uppercase())
                val base = today.with(TemporalAdjusters.next(dow))
                due = if (m.groupValues[1].isNotBlank()) base.plusWeeks(1) else base
                cut(m.range)
            }
        }
        if (due == null) {
            for ((re, at) in WORDS) {
                val m = re.find(rest) ?: continue
                due = at(today)
                cut(m.range)
                break
            }
        }
        val dueSpan = if (due != null) place(cutRange) else null
        // A time on its own means today.
        if (due == null && time != null) due = today

        val title = rest.replace(Regex("""\s+"""), " ").trim()
        // A range on one day is no range.
        if (endDue == due) endDue = null
        return Draft(title, due, time, tags, priority, endTime, endDue) to Spans(dueSpan, timeSpan)
    }

    /** A typed year: Buddhist years (2569, or 69) and short years (26) as the Western year. */
    private fun year(typed: String): Int? = typed.toIntOrNull()?.let { y ->
        when {
            y > 2400 -> y - 543
            y < 100 -> if (y > 60) 2500 + y - 543 else 2000 + y
            else -> y
        }
    }
}
