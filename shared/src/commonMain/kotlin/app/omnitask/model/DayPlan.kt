package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/** Today's tasks and calendar events, laid out by part of the day for the Focus timeline. */
object DayPlan {

    enum class Part(private val th: String, private val en: String) {
        /** All-day events, and events running over several days, on top of the day and apart from the tasks. */
        ALL_DAY("วันนี้", "Today"),
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

    /**
     * With [includeDone] the tasks already ticked off today stay in the plan (the Focus timeline greys them
     * instead of dropping them): those dated today, and those from earlier days ticked today. Overdue work
     * (a due date or a scheduled date already past) goes in [Part.LATE].
     */
    fun build(tasks: List<Task>, events: List<CalendarEvent>, today: LocalDate, includeDone: Boolean = false): List<Section> {
        val items = ArrayList<Pair<Part, Item>>()
        tasks.forEach { t ->
            val due = t.due
            val dated = due == today || t.scheduled == today
            val doneToday = includeDone && t.status == Status.DONE
            when {
                !t.isOpen && !doneToday -> Unit
                t.isOpen && due != null && due < today -> items += Part.LATE to Item.TaskItem(t, null)
                dated -> {
                    val time = t.reminderAt?.takeIf { it.toLocalDate() == today }?.toLocalTime()
                    items += partOf(time) to Item.TaskItem(t, time)
                }
                // Scheduled for a day already gone and not due yet: still to do, so it waits with the overdue work.
                t.isOpen && t.scheduled.let { it != null && it < today } -> items += Part.LATE to Item.TaskItem(t, null)
                doneToday && t.done == today && ((due != null && due < today) || t.scheduled.let { it != null && it < today }) ->
                    items += Part.LATE to Item.TaskItem(t, null)
            }
        }
        events.filter { overlaps(it, today) }.forEach { e ->
            val time = if (e.allDay) null else e.begin.toLocalTime().takeIf { e.begin.toLocalDate() == today }
            items += (if (time == null) Part.ALL_DAY else partOf(time)) to Item.EventItem(e, time)
        }
        return Part.entries.map { part ->
            Section(part, items.filter { it.first == part }.map { it.second }.sortedBy { it.time ?: LocalTime.LAST })
        }.filter { it.items.isNotEmpty() }
    }

    /** Open tasks in the overdue card of [plan]: the Focus card's overdue number, so the two always agree. */
    fun lateCount(plan: List<Section>): Int =
        plan.filter { it.part == Part.LATE }.sumOf { s -> s.items.count { it is Item.TaskItem && it.task.isOpen } }

    /** Events on [plan]'s day, all-day ones included: the Focus card's events number, as many as the timeline shows. */
    fun eventCount(plan: List<Section>): Int = plan.sumOf { s -> s.items.count { it is Item.EventItem } }

    /** The time line of an event on [today]'s page: its hours, or All day (with which day of a long one). */
    fun range(e: CalendarEvent, timed: Boolean, today: LocalDate, hm: (LocalTime) -> String): String {
        if (timed) return tr("${hm(e.begin.toLocalTime())} ถึง ${hm(e.end.toLocalTime())}", "${hm(e.begin.toLocalTime())} to ${hm(e.end.toLocalTime())}")
        val day = dayOf(e, today)?.let { (n, of) -> tr(" (วันที่ $n จาก $of)", " (day $n of $of)") } ?: ""
        return tr("ทั้งวัน", "All day") + day
    }

    /** For an event over several days: which day [today] is of how many, like 2 of 3. Null for a one-day event. */
    fun dayOf(e: CalendarEvent, today: LocalDate): Pair<Int, Int>? {
        val first = e.begin.toLocalDate()
        // An event ending at midnight does not take the day it ends on.
        val last = e.end.toLocalDate().let { if (e.end.toLocalTime() == LocalTime.MIDNIGHT && it > first) it.minusDays(1) else it }
        val total = ChronoUnit.DAYS.between(first, last).toInt() + 1
        if (total < 2) return null
        return (ChronoUnit.DAYS.between(first, today).toInt() + 1).coerceIn(1, total) to total
    }

    /** Where a timed calendar event stands against the clock. All-day events never pass or run. */
    enum class Phase { UPCOMING, ACTIVE, PAST }

    fun phase(e: CalendarEvent, now: LocalDateTime): Phase = when {
        e.allDay -> Phase.UPCOMING
        e.end <= now -> Phase.PAST
        e.begin <= now -> Phase.ACTIVE
        else -> Phase.UPCOMING
    }

    /** How far into a timed event the clock is, 0 to 1; the Focus card and the widget draw the now line there. */
    fun progress(e: CalendarEvent, now: LocalDateTime): Float {
        val total = ChronoUnit.SECONDS.between(e.begin, e.end)
        if (total <= 0) return 0f
        return (ChronoUnit.SECONDS.between(e.begin, now).toFloat() / total).coerceIn(0f, 1f)
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
     * noon, or now if it has passed), and sleep ends as in [sleepLeft]. A [wake] picked for tonight replaces the
     * profile's and is kept as it is, so a morning event no longer pulls it earlier. Null during the day, unless the
     * [evening] the night starts on is given: then a daytime call plans the night ahead.
     */
    fun night(events: List<CalendarEvent>, profile: Profile, now: LocalDateTime, bedtime: LocalTime, wake: LocalTime? = null, evening: LocalDate? = null): Night? {
        val p = if (wake != null) profile.copy(wake = wake) else profile
        val t = now.toLocalTime()
        val late = t >= LocalTime.of(18, 0)
        // Given the evening, the night is planned all day long (the Focus card); without, there is none in the daytime.
        if (evening == null && !late && t >= p.wake) return null
        // The evening this night belongs to.
        val eve = evening ?: if (late) now.toLocalDate() else now.toLocalDate().minusDays(1)
        val planned = if (bedtime >= LocalTime.NOON) eve.atTime(bedtime) else eve.plusDays(1).atTime(bedtime)
        val bedAt = if (planned < now) now else planned
        // With a picked wake time only what is still on at bedtime counts (a shift running late), not the morning's events.
        val counted = if (wake != null) events.filter { it.begin <= bedAt } else events
        val left = sleepLeft(counted, p, bedAt.coerceAtMostMorning(eve, p)) ?: return null
        return Night(ChronoUnit.MINUTES.between(now, bedAt).coerceAtLeast(0), left.minutes, bedAt, left.wakeAt, left.because)
    }

    // A bedtime later than the wake time would read as daytime; keep it inside the night.
    private fun LocalDateTime.coerceAtMostMorning(eve: LocalDate, profile: Profile): LocalDateTime {
        val wake = eve.plusDays(1).atTime(profile.wake).minusMinutes(1)
        return if (this > wake) wake else this
    }

    /** Whether [e] is on [day] at all: an event ending at midnight does not take the day it ends on. */
    fun overlaps(e: CalendarEvent, day: LocalDate): Boolean =
        e.begin < day.plusDays(1).atStartOfDay() && e.end > day.atStartOfDay()

    private fun partOf(time: LocalTime?): Part = when {
        time == null -> Part.ANYTIME
        time < LocalTime.NOON -> Part.MORNING
        time < LocalTime.of(17, 0) -> Part.AFTERNOON
        else -> Part.EVENING
    }
}
