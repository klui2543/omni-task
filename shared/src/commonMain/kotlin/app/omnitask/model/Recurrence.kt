package app.omnitask.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The 🔁 rules of the Tasks plugin, as TaskForge writes them: "every day", "every 2 weeks",
 * "every weekday", "every week on Monday, Thursday", "every month on the 15th",
 * "every month on the last", "every year", each optionally followed by "when done".
 */
object Recurrence {

    data class Rule(
        val unit: ChronoUnit,
        val interval: Int = 1,
        val weekdays: Set<DayOfWeek> = emptySet(),
        /** Day of month; -1 means the last day. */
        val monthDay: Int? = null,
        val whenDone: Boolean = false,
    )

    private val DAY_NAMES = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
    )
    private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val HEAD = Regex("""^every\s+(?:(\d+)\s+)?(day|week|month|year)s?\b(.*)$""")
    private val MONTH_DAY = Regex("""on the (\d{1,2})(?:st|nd|rd|th)?\b""")

    fun parse(text: String?): Rule? {
        if (text == null) return null
        var t = text.trim().lowercase().replace(Regex("""\s+"""), " ")
        val whenDone = t.endsWith(" when done")
        t = t.removeSuffix(" when done").trim()

        if (t == "every weekday") return Rule(ChronoUnit.WEEKS, weekdays = WORKDAYS, whenDone = whenDone)
        if (t == "every weekend") return Rule(ChronoUnit.WEEKS, weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), whenDone = whenDone)
        // "every Monday" or "every Monday, Friday" is shorthand for a weekly rule.
        t.removePrefix("every ").let { rest -> days(rest) }?.let { return Rule(ChronoUnit.WEEKS, weekdays = it, whenDone = whenDone) }

        val m = HEAD.matchEntire(t) ?: return null
        val interval = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val tail = m.groupValues[3].trim()
        return when (m.groupValues[2]) {
            "day" -> Rule(ChronoUnit.DAYS, interval, whenDone = whenDone).takeIf { tail.isEmpty() }
            "week" -> when {
                tail.isEmpty() -> Rule(ChronoUnit.WEEKS, interval, whenDone = whenDone)
                tail.startsWith("on ") -> days(tail.removePrefix("on "))?.let { Rule(ChronoUnit.WEEKS, interval, it, whenDone = whenDone) }
                else -> null
            }
            "month" -> when {
                tail.isEmpty() -> Rule(ChronoUnit.MONTHS, interval, whenDone = whenDone)
                tail == "on the last" || tail == "on the last day" -> Rule(ChronoUnit.MONTHS, interval, monthDay = -1, whenDone = whenDone)
                else -> MONTH_DAY.matchEntire(tail)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }
                    ?.let { Rule(ChronoUnit.MONTHS, interval, monthDay = it, whenDone = whenDone) }
            }
            "year" -> Rule(ChronoUnit.YEARS, interval, whenDone = whenDone).takeIf { tail.isEmpty() }
            else -> null
        }
    }

    /** "monday, thursday" or "monday and thursday" into weekdays; null if any word is not a day. */
    private fun days(text: String): Set<DayOfWeek>? {
        val words = text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() && it != "and" }
        if (words.isEmpty()) return null
        val days = words.map { DAY_NAMES[it] ?: return null }
        return days.toSet()
    }

    /** The first occurrence after [from]. */
    fun next(rule: Rule, from: LocalDate): LocalDate = when (rule.unit) {
        ChronoUnit.DAYS -> from.plusDays(rule.interval.toLong())
        ChronoUnit.WEEKS -> if (rule.weekdays.isEmpty()) {
            from.plusWeeks(rule.interval.toLong())
        } else {
            // Later this week if one of the days is still to come, else the first day of the next active week.
            val laterThisWeek = rule.weekdays.filter { it > from.dayOfWeek }.minOrNull()
            if (laterThisWeek != null) {
                from.with(TemporalAdjusters.next(laterThisWeek))
            } else {
                val monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(rule.interval.toLong())
                monday.with(TemporalAdjusters.nextOrSame(rule.weekdays.min()))
            }
        }
        ChronoUnit.MONTHS -> {
            fun on(month: LocalDate): LocalDate {
                val day = rule.monthDay ?: return month
                val last = month.lengthOfMonth()
                return month.withDayOfMonth(if (day == -1) last else minOf(day, last))
            }
            val sameMonth = if (rule.monthDay != null) on(from) else null
            if (sameMonth != null && sameMonth > from && rule.interval == 1) sameMonth else on(from.plusMonths(rule.interval.toLong()))
        }
        ChronoUnit.YEARS -> from.plusYears(rule.interval.toLong())
        else -> from.plusDays(1)
    }

    /** How many rounds [todayCopy] walks before it gives up on a task left open for years. */
    private const val MAX_ROUNDS = 5000

    /**
     * The round of an open repeating task that falls on [today] while the open line is still on an earlier one
     * (yesterday's was never ticked, so the line never moved on). The line keeps the round that was missed, and
     * this copy stands for today's, with every date moved by the same amount. Null when no round lands on today,
     * and for "when done" rules, which have no rounds apart from the completion itself.
     */
    fun todayCopy(task: Task, today: LocalDate): Task? {
        if (!task.isOpen || task.parent != null || task.list != null || task.todayCopy) return null
        val rule = parse(task.recurrence) ?: return null
        if (rule.whenDone) return null
        val from = task.due ?: task.scheduled ?: task.start ?: return null
        if (from >= today) return null
        var round = from
        var walked = 0
        while (round < today && walked++ < MAX_ROUNDS) round = next(rule, round)
        if (round != today) return null
        val shift = ChronoUnit.DAYS.between(from, today)
        return task.copy(
            start = task.start?.plusDays(shift),
            scheduled = task.scheduled?.plusDays(shift),
            due = task.due?.plusDays(shift),
            todayCopy = true,
        )
    }

    /** [tasks] with each repeating task's [todayCopy] added right after it. */
    fun withTodayCopies(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.flatMap { t -> listOfNotNull(t, todayCopy(t, today)) }

    /** Ready-made rules for the edit sheet, in the words the Tasks plugin reads. */
    val PRESETS = listOf(
        "every day", "every weekday", "every week", "every 2 weeks", "every month", "every year",
    )

    fun describe(text: String): String {
        val r = parse(text) ?: return text
        val base = when (r.unit) {
            ChronoUnit.DAYS -> if (r.interval == 1) tr("ทุกวัน", "Every day") else tr("ทุก ${r.interval} วัน", "Every ${r.interval} days")
            ChronoUnit.WEEKS -> when {
                r.weekdays == WORKDAYS -> tr("ทุกวันทำงาน", "Every weekday")
                r.weekdays.isNotEmpty() -> tr("ทุก", "Every ") + r.weekdays.sorted().joinToString(", ") { Profile.dayName(it) } +
                    if (r.interval > 1) tr(" ทุก ${r.interval} สัปดาห์", " every ${r.interval} weeks") else ""
                r.interval == 1 -> tr("ทุกสัปดาห์", "Every week")
                else -> tr("ทุก ${r.interval} สัปดาห์", "Every ${r.interval} weeks")
            }
            ChronoUnit.MONTHS -> (if (r.interval == 1) tr("ทุกเดือน", "Every month") else tr("ทุก ${r.interval} เดือน", "Every ${r.interval} months")) +
                when (r.monthDay) {
                    null -> ""
                    -1 -> tr(" วันสุดท้าย", ", last day")
                    else -> tr(" วันที่ ${r.monthDay}", " on day ${r.monthDay}")
                }
            ChronoUnit.YEARS -> if (r.interval == 1) tr("ทุกปี", "Every year") else tr("ทุก ${r.interval} ปี", "Every ${r.interval} years")
            else -> text
        }
        return if (r.whenDone) base + tr(" นับจากวันที่ทำเสร็จ", ", counted from completion") else base
    }
}
