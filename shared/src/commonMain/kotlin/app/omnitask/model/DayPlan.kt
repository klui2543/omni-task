package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/** Today's tasks and calendar events, laid out by part of the day for the Focus timeline. */
object DayPlan {

    enum class Part(private val th: String, private val en: String) {
        LATE("ค้างอยู่", "Overdue"), MORNING("เช้า", "Morning"), AFTERNOON("บ่าย", "Afternoon"), EVENING("เย็น", "Evening"),
        ANYTIME("ไม่ระบุเวลา", "Any time"),
        ;

        val label get() = tr(th, en)
    }

    sealed interface Item {
        val time: LocalTime?
        data class TaskItem(val task: Task, override val time: LocalTime?) : Item
        data class EventItem(val event: CalendarEvent, override val time: LocalTime?) : Item
    }

    data class Section(val part: Part, val items: List<Item>)

    /** The hours the owner is normally awake, used to estimate free time. */
    private val DAY_START = LocalTime.of(7, 0)
    private val DAY_END = LocalTime.of(21, 0)

    fun build(tasks: List<Task>, events: List<CalendarEvent>, today: LocalDate): List<Section> {
        val items = ArrayList<Pair<Part, Item>>()
        tasks.filter { it.isOpen }.forEach { t ->
            val due = t.due
            when {
                due != null && due < today -> items += Part.LATE to Item.TaskItem(t, null)
                due == today || t.scheduled == today -> {
                    val time = t.reminderAt?.takeIf { it.toLocalDate() == today }?.toLocalTime()
                    items += partOf(time) to Item.TaskItem(t, time)
                }
            }
        }
        events.filter { overlaps(it, today) }.forEach { e ->
            val time = if (e.allDay) null else e.begin.toLocalTime().takeIf { e.begin.toLocalDate() == today }
            items += partOf(time) to Item.EventItem(e, time)
        }
        return Part.entries.map { part ->
            Section(part, items.filter { it.first == part }.map { it.second }.sortedBy { it.time ?: LocalTime.LAST })
        }.filter { it.items.isNotEmpty() }
    }

    /** Minutes between 07:00 and 21:00 today not taken by timed events. */
    fun freeMinutes(events: List<CalendarEvent>, today: LocalDate): Long {
        val dayStart = today.atTime(DAY_START)
        val dayEnd = today.atTime(DAY_END)
        val busy = events.filter { !it.allDay && it.end > dayStart && it.begin < dayEnd }
            .map { maxOf(it.begin, dayStart) to minOf(it.end, dayEnd) }
            .sortedBy { it.first }
        var taken = 0L
        var cursor: LocalDateTime = dayStart
        busy.forEach { (b, e) ->
            val start = maxOf(b, cursor)
            if (e > start) {
                taken += ChronoUnit.MINUTES.between(start, e)
                cursor = e
            }
        }
        return ChronoUnit.MINUTES.between(dayStart, dayEnd) - taken
    }

    /** Free minutes left today: from now (or waking) until bedtime, minus timed events. */
    fun freeMinutesLeft(events: List<CalendarEvent>, now: LocalDateTime, wake: LocalTime, sleep: LocalTime): Long {
        val day = now.toLocalDate()
        val start = maxOf(now, day.atTime(wake))
        val end = day.atTime(sleep)
        if (start >= end) return 0
        val busy = events.filter { !it.allDay && it.end > start && it.begin < end }
            .map { maxOf(it.begin, start) to minOf(it.end, end) }
            .sortedBy { it.first }
        var taken = 0L
        var cursor = start
        busy.forEach { (b, e) ->
            val from = maxOf(b, cursor)
            if (e > from) {
                taken += ChronoUnit.MINUTES.between(from, e)
                cursor = e
            }
        }
        return ChronoUnit.MINUTES.between(start, end) - taken
    }

    /** How long sleep can last if it starts now, and what ends it. */
    data class SleepLeft(val minutes: Long, val wakeAt: LocalDateTime, val because: CalendarEvent?)

    /**
     * From 18:00 until the morning: sleep starts now (or when the current event, like a shift, ends)
     * and must end at the profile's wake time, or an hour before the first event of the morning if that is earlier.
     * Null during the day.
     */
    fun sleepLeft(events: List<CalendarEvent>, profile: Profile, now: LocalDateTime): SleepLeft? {
        val t = now.toLocalTime()
        val evening = t >= LocalTime.of(18, 0)
        if (!evening && t >= profile.wake) return null
        val timed = events.filter { !it.allDay }
        val start = timed.filter { it.begin <= now && it.end > now }.maxOfOrNull { it.end } ?: now
        val morning = if (evening) now.toLocalDate().plusDays(1) else now.toLocalDate()
        val first = timed.filter { it.begin > start && it.begin <= morning.atTime(12, 0) }.minByOrNull { it.begin }
        val byEvent = first?.begin?.minusMinutes(60)
        val byProfile = morning.atTime(profile.wake)
        val wakeAt = if (byEvent != null && byEvent < byProfile) byEvent else byProfile
        val minutes = ChronoUnit.MINUTES.between(start, wakeAt).coerceAtLeast(0)
        return SleepLeft(minutes, wakeAt, first.takeIf { byEvent != null && byEvent < byProfile })
    }

    /** Tonight with a planned bedtime: how long until bed, and how long sleep can last after it. */
    data class Night(val toBed: Long, val sleep: Long, val bedAt: LocalDateTime, val wakeAt: LocalDateTime, val because: CalendarEvent?)

    /**
     * From 18:00 until the morning wake time: bed is at [bedtime] (tonight's, after midnight when it is before
     * noon, or now if it has passed), and sleep ends as in [sleepLeft]. Null during the day.
     */
    fun night(events: List<CalendarEvent>, profile: Profile, now: LocalDateTime, bedtime: LocalTime): Night? {
        val t = now.toLocalTime()
        val evening = t >= LocalTime.of(18, 0)
        if (!evening && t >= profile.wake) return null
        // The evening this night belongs to.
        val eve = if (evening) now.toLocalDate() else now.toLocalDate().minusDays(1)
        val planned = if (bedtime >= LocalTime.NOON) eve.atTime(bedtime) else eve.plusDays(1).atTime(bedtime)
        val bedAt = if (planned < now) now else planned
        val left = sleepLeft(events, profile, bedAt.coerceAtMostMorning(eve, profile)) ?: return null
        return Night(ChronoUnit.MINUTES.between(now, bedAt).coerceAtLeast(0), left.minutes, bedAt, left.wakeAt, left.because)
    }

    // A bedtime later than the wake time would read as daytime; keep it inside the night.
    private fun LocalDateTime.coerceAtMostMorning(eve: LocalDate, profile: Profile): LocalDateTime {
        val wake = eve.plusDays(1).atTime(profile.wake).minusMinutes(1)
        return if (this > wake) wake else this
    }

    private fun overlaps(e: CalendarEvent, day: LocalDate): Boolean =
        e.begin < day.plusDays(1).atStartOfDay() && e.end > day.atStartOfDay()

    private fun partOf(time: LocalTime?): Part = when {
        time == null -> Part.ANYTIME
        time < LocalTime.NOON -> Part.MORNING
        time < LocalTime.of(17, 0) -> Part.AFTERNOON
        else -> Part.EVENING
    }
}
