package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The owner's real day, read from the calendars with nothing to fill in (owner, 2026-10-29: the schedule changes
 * every day and forms are too much): the regular job on weekdays, which is in no calendar; which events are shifts
 * (every event of a shift calendar such as "SiRxShift", whose titles read "O11 รอบ 3" with no shift word); which
 * shifts are night ones, by their hours; and the days the job is off (public holidays, a day off the owner wrote in,
 * leave or a trip).
 */
object RealDay {

    /** The regular job: [days] from [from] to [to]. */
    data class Job(val days: Set<DayOfWeek>, val from: LocalTime, val to: LocalTime) {
        companion object {
            /** The owner's day job, Monday to Friday 08:30 to 16:30 (owner, 2026-10-29). */
            val WEEKDAYS = Job(
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 30), LocalTime.of(16, 30),
            )
        }
    }

    /**
     * What the planner knows on top of the events. The default knows nothing and changes nothing, so a caller that
     * passes none (the web) plans as before.
     */
    data class Facts(
        val job: Job? = null,
        /** Days the job is off from the phone's holiday calendars, with the holiday's name. */
        val daysOff: Map<LocalDate, String> = emptyMap(),
        /** Phone calendars whose every event is a shift. */
        val shiftCalendars: Set<Long> = emptySet(),
    )

    /** Night hours: a shift running through any of them is a night shift. */
    val NIGHT_END: LocalTime = LocalTime.of(6, 0)

    /** Bedtime the evening before a shift that starts after midnight (interview, 2026-10-22: asleep 21:00 to 22:00, up about 01:45). */
    val BED_BEFORE_NIGHT: LocalTime = LocalTime.of(21, 0)

    val jobTitle: String get() = tr("งานประจำ", "Day job")

    /** A calendar whose every event is a shift: its name has a shift word ("เวรรพ. Rx") or says shift ("SiRxShift"). */
    fun isShiftCalendar(name: String, profile: Profile): Boolean =
        profile.shiftWords.any { name.contains(it, ignoreCase = true) } || name.contains("shift", ignoreCase = true)

    fun isShift(e: CalendarEvent, profile: Profile, facts: Facts): Boolean =
        (e.calendarId != 0L && e.calendarId in facts.shiftCalendars) || profile.shiftWords.any { e.title.contains(it, ignoreCase = true) }

    /** A night shift: named so, or a timed shift that runs through any part of midnight to 06:00 ("O11 รอบ 3" is 02:30 to 08:30). */
    fun isNight(e: CalendarEvent, profile: Profile, facts: Facts): Boolean =
        isShift(e, profile, facts) && (profile.nightWords.any { e.title.contains(it, ignoreCase = true) } || (!e.allDay && throughNight(e)))

    private fun throughNight(e: CalendarEvent): Boolean {
        var d = e.begin.date
        while (d <= e.end.date) {
            if (maxOf(e.begin, d.atStartOfDay()) < minOf(e.end, d.atTime(NIGHT_END))) return true
            d = d.plusDays(1)
        }
        return false
    }

    /** A timed shift that starts on [day] after midnight and before 06:00: the evening before it is short. */
    fun nightStart(day: LocalDate, events: List<CalendarEvent>, profile: Profile, facts: Facts): CalendarEvent? =
        events.filter { !it.allDay && it.begin.date == day && it.begin.time < NIGHT_END && isShift(it, profile, facts) }.minByOrNull { it.begin }

    private val AWAY_WORDS = listOf(
        "ลางาน", "ลาพัก", "ลาป่วย", "ลากิจ", "วันลา", "วันหยุด", "หยุดงาน", "เที่ยว",
        "leave", "vacation", "day off", "holiday", "trip",
    )

    /**
     * An all-day event that takes the whole day away from the job: a day off the owner wrote in (the cabinet's special
     * holidays are missing from Google's holiday calendar, so the owner adds them, such as "วันหยุดประชุม World bank"),
     * leave, or a trip ("ไปนครปฐม", owner, 2026-10-29). Reminders such as "ล้าง CPAP" are not.
     */
    fun isDayAway(e: CalendarEvent, facts: Facts): Boolean =
        e.allDay && e.calendarId !in facts.shiftCalendars &&
            (e.title.trim().startsWith("ไป") || AWAY_WORDS.any { e.title.contains(it, ignoreCase = true) })

    private fun covers(e: CalendarEvent, day: LocalDate): Boolean {
        val last = if (e.allDay) maxOf(e.begin.date, e.end.minusNanos(1).date) else e.end.date
        return e.begin.date <= day && day <= last
    }

    /** Whether the job runs on [day]: a job day, not a holiday, and no day away. */
    fun jobOn(day: LocalDate, events: List<CalendarEvent>, facts: Facts): Boolean {
        val job = facts.job ?: return false
        if (day.dayOfWeek !in job.days || day in facts.daysOff) return false
        return events.none { isDayAway(it, facts) && covers(it, day) }
    }

    /** Ids for the job's blocks, far below the planner's own placed tasks (-1, -2, ...). */
    private const val JOB_ID = -1_000_000_000L

    /** The job as one busy block on each of [days] it runs, so the planner keeps it clear like any event. */
    fun jobBlocks(days: List<LocalDate>, events: List<CalendarEvent>, facts: Facts): List<CalendarEvent> {
        val job = facts.job ?: return emptyList()
        return days.distinct().filter { jobOn(it, events, facts) }.map { d ->
            CalendarEvent(JOB_ID - d.toEpochDays(), jobTitle, d.atTime(job.from), d.atTime(job.to))
        }
    }

    /** True when [e] is one of [jobBlocks]. */
    fun isJob(e: CalendarEvent): Boolean = e.id <= JOB_ID && e.id > JOB_ID - 10_000_000L

    /**
     * Observances in a holiday calendar (Christmas Eve, Valentine's Day) are not days off. Google marks them in the
     * description: "วันสำคัญ" or "Observance"; a public holiday says "วันหยุดนักขัตฤกษ์" or "Public holiday".
     * A holiday with no description counts as a day off.
     */
    fun isDayOff(description: String?): Boolean {
        val d = description?.trim().orEmpty()
        return !(d.startsWith("วันสำคัญ") || d.startsWith("Observance", ignoreCase = true))
    }

    /** Only [LocalDateTime] helpers this file needs. */
    private fun maxOf(a: LocalDateTime, b: LocalDateTime) = if (a >= b) a else b
    private fun minOf(a: LocalDateTime, b: LocalDateTime) = if (a <= b) a else b
}
