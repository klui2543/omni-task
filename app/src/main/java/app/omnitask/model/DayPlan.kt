package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Today's tasks and calendar events, laid out by part of the day for the Focus timeline. */
object DayPlan {

    enum class Part(val label: String) {
        LATE("ค้างอยู่"), MORNING("เช้า"), AFTERNOON("บ่าย"), EVENING("เย็น"), ANYTIME("ไม่ระบุเวลา"),
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
            Section(part, items.filter { it.first == part }.map { it.second }.sortedBy { it.time ?: LocalTime.MAX })
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

    private fun overlaps(e: CalendarEvent, day: LocalDate): Boolean =
        e.begin < day.plusDays(1).atStartOfDay() && e.end > day.atStartOfDay()

    private fun partOf(time: LocalTime?): Part = when {
        time == null -> Part.ANYTIME
        time < LocalTime.NOON -> Part.MORNING
        time < LocalTime.of(17, 0) -> Part.AFTERNOON
        else -> Part.EVENING
    }
}
