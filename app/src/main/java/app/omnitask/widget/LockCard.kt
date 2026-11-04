package app.omnitask.widget

import app.omnitask.model.DayFill
import app.omnitask.model.LockPlan
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

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

    /**
     * Today for the card from the today page's plan (owner's pick ก, 2026-11-01, mock lock-now-next): all-day blocks
     * first, then what is not over yet in order, the red line at [now] (or crossing the block on now), work in [done]
     * left out. Planned work takes [accent]; other blocks the slate bar.
     */
    fun fromPlan(copy: LockPlan.Copy, now: LocalDateTime, done: Set<String>, accent: Int, color: (Long) -> Int? = { null }): TodayWeek.Day {
        val min = now.hour * 60 + now.minute
        val clock = LocalTime(now.hour, now.minute)
        val out = ArrayList<TodayWeek.Row>()
        val live = copy.slots.filter { it.ident == null || it.ident !in done }
        fun tint(s: LockPlan.Slot) = if (s.card) accent else s.calendar?.let(color)
        live.filter { it.at == null }.forEach { out += TodayWeek.Row.Event(it.title, tr("ทั้งวัน", "All day"), tint(it), past = false) }
        var nowShown = false
        live.filter { it.at != null }.sortedBy { it.at }.forEach { s ->
            val at = s.at!!
            val end = s.end
            if (end != null && end <= min || end == null && at < min) return@forEach
            val running = end != null && at <= min
            if (!nowShown && !running && at > min) { out += TodayWeek.Row.Now(clock); nowShown = true }
            // As the mock: work shows when it starts, a block its hours, the one on now always its hours.
            val time = DayFill.hm(at) + (end?.takeIf { running || !s.card }?.let { tr(" ถึง ", " to ") + DayFill.hm(it) } ?: "")
            out += TodayWeek.Row.Event(s.title, time, tint(s), past = false, run = if (running) (min - at).toFloat() / (end!! - at) else null)
            if (running) nowShown = true
        }
        if (!nowShown) out += TodayWeek.Row.Now(clock)
        return TodayWeek.Day(copy.day, out)
    }

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

    /**
     * Where the one thing next sits on the card (owner's pick ก, 2026-11-03, mock six-pains-fix): its own row when today
     * already has it, so it is not shown twice; a row folded into "อีก N" comes up first with its time; with no row
     * today, a new first row. The last row then joins "อีก N", and the now line goes with it when it stood after the
     * cut, as in [card]. [titles] are the names the today plan gave the task (the copy can lag a renamed task).
     * Returns the card and the index of the next row in it.
     */
    fun placeNext(card: Card, day: List<TodayWeek.Row>, titles: Set<String>, key: String, max: Int = MAX): Pair<Card, Int> {
        fun its(r: TodayWeek.Row) = r is TodayWeek.Row.Todo && r.task.key == key || r is TodayWeek.Row.Event && !r.past && r.title in titles
        val at = card.rows.indexOfFirst(::its)
        if (at >= 0) return card to at
        val folded = day.firstOrNull { its(it) && !(it is TodayWeek.Row.Todo && !it.task.isOpen) }
        val rows = ArrayList<TodayWeek.Row>(card.rows)
        rows.add(0, folded ?: TodayWeek.Row.Event(titles.first(), "", null, past = false))
        var more = card.more - if (folded != null) 1 else 0
        if (rows.count { it !is TodayWeek.Row.Now } > max) {
            val cut = rows.indexOfLast { it !is TodayWeek.Row.Now }
            rows.removeAt(cut)
            more++
            val now = rows.indexOfFirst { it is TodayWeek.Row.Now }
            if (now >= cut) rows.removeAt(now)
        }
        return Card(rows, more.coerceAtLeast(0)) to 0
    }
}
