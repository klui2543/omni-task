package app.omnitask.widget

import app.omnitask.model.Task
import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * What the lock-screen card shows of today: the Today widget's rows without what is already over (events that ended,
 * finished work), cut to [MAX] rows with "อีก N" for the rest (owner, 2026-10-31: the widget's look, today only,
 * at most 4 rows; mock lock-v4, way ก). The red now line stays while nothing before it was cut.
 */
internal object LockCard {
    const val MAX = 4

    data class Card(val rows: List<TodayWeek.Row>, val more: Int)

    /**
     * Today for the card with only the owner's own: events of other people's calendars ([others] by calendar id) are
     * left out, folded and open (owner, 2026-10-31, way ค). The app's today page and the widget still show them.
     */
    fun today(
        date: LocalDate, now: LocalDateTime, tasks: List<Task>, events: List<CalendarEvent>, others: (Long) -> Boolean,
        color: (CalendarEvent) -> Int?,
    ): TodayWeek.Day = TodayWeek.days(date, now, tasks, events.filterNot { others(it.calendarId) }, 1, color).first()

    fun card(day: TodayWeek.Day, max: Int = MAX): Card {
        val out = ArrayList<TodayWeek.Row>()
        var shown = 0
        var more = 0
        day.rows.forEach { r ->
            when {
                r is TodayWeek.Row.Event && r.past -> Unit
                r is TodayWeek.Row.Todo && !r.task.isOpen -> Unit
                r is TodayWeek.Row.Now -> if (more == 0) out += r
                shown < max -> { out += r; shown++ }
                else -> more++
            }
        }
        return Card(out, more)
    }
}
