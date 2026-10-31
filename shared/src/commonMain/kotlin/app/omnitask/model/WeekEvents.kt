package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The calendar events under each day of the Focus page's two weeks (owner's pick ก, 2026-10-31, mock week-events):
 * the same events and the same rows as today's timeline, so a day ahead reads like a day today.
 */
object WeekEvents {

    /**
     * The events on each day from [from] to [to], a day with none left out. An all-day event, and the later days of
     * one running over several days, come first; the timed ones follow by start time. A multi-day event is on each
     * day it covers, as on [DayPlan.build].
     */
    fun byDay(events: List<CalendarEvent>, from: LocalDate, to: LocalDate): Map<LocalDate, List<DayPlan.Item.EventItem>> {
        val out = LinkedHashMap<LocalDate, List<DayPlan.Item.EventItem>>()
        var day = from
        while (day <= to) {
            val rows = events.filter { DayPlan.overlaps(it, day) }.map { e ->
                val time = if (e.allDay) null else e.begin.toLocalTime().takeIf { e.begin.toLocalDate() == day }
                DayPlan.Item.EventItem(e, time)
            }
            if (rows.isNotEmpty()) out[day] = rows.sortedWith(compareBy<DayPlan.Item.EventItem> { it.time != null }.thenBy { it.time ?: LocalTime.MIDNIGHT }.thenBy { it.event.title })
            day = day.plusDays(1)
        }
        return out
    }
}
