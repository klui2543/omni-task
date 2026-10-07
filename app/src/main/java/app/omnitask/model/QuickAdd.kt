package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

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

    private val TAG = Regex("""(?<!\S)#[^\s#]+""")
    private val TIME = Regex("""(?<![\d:])([01]?\d|2[0-3])[:.]([0-5]\d)(?![\d:])(?:\s*น\.)?""")
    private val BANG = Regex("""(?<!\S)(!{1,3})(?!\S)""")
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
    private val EN_WEEKDAY = Regex("""(?i)(?<!\S)(next\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?!\S)""")

    private val WORDS: List<Pair<Regex, (LocalDate) -> LocalDate>> = listOf(
        Regex("""มะรืนนี้|มะรืน""") to { d -> d.plusDays(2) },
        Regex("""พรุ่งนี้""") to { d -> d.plusDays(1) },
        Regex("""วันนี้|คืนนี้""") to { d -> d },
        Regex("""สัปดาห์หน้า|(?<!วัน)อาทิตย์หน้า""") to { d -> d.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) },
        Regex("""สิ้นเดือน""") to { d -> d.with(TemporalAdjusters.lastDayOfMonth()) },
        Regex("""(?i)(?<!\S)(?:the\s+)?day after tomorrow(?!\S)""") to { d -> d.plusDays(2) },
        Regex("""(?i)(?<!\S)tomorrow(?!\S)""") to { d -> d.plusDays(1) },
        Regex("""(?i)(?<!\S)(?:today|tonight)(?!\S)""") to { d -> d },
        Regex("""(?i)(?<!\S)next week(?!\S)""") to { d -> d.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) },
    )

    fun parse(text: String, today: LocalDate): Draft {
        var rest = " ${text.trim()} "
        fun cut(range: IntRange) { rest = rest.substring(0, range.first) + " " + rest.substring(range.last + 1) }

        val tags = TAG.findAll(rest).map { it.value.removePrefix("#") }.toList()
        rest = TAG.replace(rest, " ")

        var priority = Priority.NONE
        BANG.find(rest)?.let { m ->
            priority = when (m.groupValues[1].length) { 3 -> Priority.HIGHEST; 2 -> Priority.HIGH; else -> Priority.MEDIUM }
            cut(m.range)
        }

        var time: LocalTime? = null
        TIME.find(rest)?.let { m ->
            time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            cut(m.range)
        }

        var due: LocalDate? = null
        SLASH_DATE.find(rest)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = m.groupValues[3].toIntOrNull()?.let { y ->
                when {
                    y > 2400 -> y - 543
                    y < 100 -> if (y > 60) 2500 + y - 543 else 2000 + y
                    else -> y
                }
            }
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
        // A time on its own means today.
        if (due == null && time != null) due = today

        val title = rest.replace(Regex("""\s+"""), " ").trim()
        return Draft(title, due, time, tags, priority)
    }
}
