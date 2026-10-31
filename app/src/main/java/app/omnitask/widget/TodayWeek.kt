package app.omnitask.widget

import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Projects
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The rows of the Today widget, day by day from today, and how they split into pages. The widget sits in a launcher
 * stack where a swipe changes the widget, so it never scrolls: each page holds as many whole days as fit its height,
 * and the arrows move to the next page (owner, 2026-10-30, mock round 4).
 */
internal object TodayWeek {

    sealed interface Row {
        /** The red line at the current time, on today only, when no event is happening now. */
        data class Now(val time: LocalTime) : Row
        /**
         * [run] is how far into the event the clock is (0 to 1) while it is happening: the red now line then crosses
         * this row at that point instead of standing on its own (owner, 2026-10-31, mock widget-now, way ข).
         */
        data class Event(val title: String, val time: String, val color: Int?, val past: Boolean, val run: Float? = null) : Row
        data class Todo(val task: Task, val time: String, val late: Boolean) : Row
    }

    data class Day(val date: LocalDate, val rows: List<Row>) {
        val count get() = rows.count { it !is Row.Now }
    }

    /** A page: some whole days, or one day cut to [cap] rows when it alone is taller than the widget. */
    data class Page(val days: List<Day>, val cap: Int? = null)

    // Heights in dp, as drawn by the widget.
    const val ROW = 28
    /** A row the now line crosses is taller, so the line and the time gone by show clearly. */
    const val RUN = 44
    const val ROW_GAP = 4
    const val NOW = 11
    const val EMPTY = 26
    const val MORE = 16
    const val DAY_GAP = 9
    /** The date column (weekday over the number; today's circle) is taller than one row. */
    const val DATE_MIN = 36
    /** On the first of a month the column also names the month. */
    const val DATE_MIN_MONTH = 46

    /** How tall [day] draws with at most [cap] rows. */
    fun height(day: Day, cap: Int? = null): Int {
        val least = if (day.date.dayOfMonth == 1) DATE_MIN_MONTH else DATE_MIN
        if (day.count == 0) return maxOf(EMPTY, least)
        var shown = 0
        var h = 0
        var first = true
        day.rows.forEach { r ->
            val rh = when (r) {
                is Row.Now -> NOW
                else -> if (cap != null && shown >= cap) return@forEach else { shown++; if (r is Row.Event && r.run != null) RUN else ROW }
            }
            h += rh + if (first) 0 else ROW_GAP
            first = false
        }
        if (cap != null && day.count > cap) h += ROW_GAP + MORE
        return maxOf(h, least)
    }

    /** Splits [days] into pages of whole days that fit in [space] dp. */
    fun pages(days: List<Day>, space: Int): List<Page> {
        val out = ArrayList<Page>()
        var i = 0
        while (i < days.size) {
            var used = height(days[i])
            if (used > space) {
                // One day taller than the widget: as many rows as fit, then "+ N more".
                var cap = days[i].count
                while (cap > 1 && height(days[i], cap) > space) cap--
                out += Page(listOf(days[i]), cap)
                i++
                continue
            }
            var n = 1
            while (i + n < days.size) {
                val more = used + DAY_GAP + height(days[i + n])
                if (more > space) break
                used = more
                n++
            }
            out += Page(days.subList(i, i + n))
            i += n
        }
        return out
    }

    /**
     * [count] days from [today]: today as the Focus timeline has it (events, timed work with the now line, then work
     * without a time; finished work stays, greyed; overdue work is left out, the header counts it), later days with
     * their events and the open work dated that day.
     */
    fun days(
        today: LocalDate, now: LocalDateTime, tasks: List<Task>, events: List<CalendarEvent>, count: Int,
        color: (CalendarEvent) -> Int?,
    ): List<Day> = (0 until count).map { i ->
        val d = today.plusDays(i.toLong())
        if (i == 0) Day(d, todayRows(today, now, tasks, events, color)) else Day(d, laterRows(d, tasks, events, color))
    }

    private fun todayRows(today: LocalDate, now: LocalDateTime, tasks: List<Task>, events: List<CalendarEvent>, color: (CalendarEvent) -> Int?): List<Row> {
        val plan = DayPlan.build(tasks, events.filter { onDay(it, today) }, today, includeDone = true)
        val out = ArrayList<Row>()
        plan.filter { it.part == DayPlan.Part.ALL_DAY }.flatMap { it.items }.forEach { out += row(it, today, now, color) }
        val timed = plan.filter { it.part != DayPlan.Part.LATE && it.part != DayPlan.Part.ANYTIME && it.part != DayPlan.Part.ALL_DAY }.flatMap { it.items }
        val clock = now.toLocalTime()
        // An event happening now carries the line itself (it crosses that row), so there is no line of its own.
        var nowShown = plan.flatMap { it.items }.any { it is DayPlan.Item.EventItem && running(it.event, now) }
        timed.forEach { item ->
            val t = item.time
            if (!nowShown && t != null && t > clock) { out += Row.Now(clock); nowShown = true }
            out += row(item, today, now, color)
        }
        if (!nowShown) out += Row.Now(clock)
        plan.filter { it.part == DayPlan.Part.ANYTIME }.flatMap { it.items }.forEach { out += row(it, today, now, color) }
        return out
    }

    private fun row(item: DayPlan.Item, today: LocalDate, now: LocalDateTime, color: (CalendarEvent) -> Int?): Row = when (item) {
        is DayPlan.Item.EventItem -> event(item.event, today, color, DayPlan.phase(item.event, now) == DayPlan.Phase.PAST)
            .let { if (running(item.event, now)) it.copy(run = DayPlan.progress(item.event, now)) else it }
        is DayPlan.Item.TaskItem -> Row.Todo(item.task, item.time?.let(::hm) ?: aside(item.task), late = false)
    }

    private fun laterRows(d: LocalDate, tasks: List<Task>, events: List<CalendarEvent>, color: (CalendarEvent) -> Int?): List<Row> {
        val evs = events.filter { onDay(it, d) }.sortedWith(compareBy<CalendarEvent>({ timedOn(it, d) }, { it.begin }))
            .map { event(it, d, color, past = false) }
        val todos = tasks.filter { it.isOpen && (it.due ?: it.scheduled) == d }
            .map { t -> t to t.reminderAt?.takeIf { it.toLocalDate() == d }?.toLocalTime() }
            .sortedBy { it.second ?: LocalTime.LAST }
            .map { (t, time) -> Row.Todo(t, time?.let(::hm) ?: aside(t), late = false) }
        return evs + todos
    }

    private fun event(e: CalendarEvent, d: LocalDate, color: (CalendarEvent) -> Int?, past: Boolean): Row.Event {
        val time = when {
            timedOn(e, d) -> {
                val end = e.end.takeIf { it.toLocalDate() == d && it > e.begin }?.toLocalTime()
                hm(e.begin.toLocalTime()) + (end?.let { tr(" ถึง ${hm(it)}", " to ${hm(it)}") } ?: "")
            }
            else -> DayPlan.dayOf(e, d)?.let { (n, of) -> tr("วันที่ $n/$of", "day $n/$of") } ?: ""
        }
        return Row.Event(e.title, time, color(e), past)
    }

    /** A timed event the clock is inside now (one that began on an earlier day too, such as the end of a night shift). */
    private fun running(e: CalendarEvent, now: LocalDateTime) = DayPlan.phase(e, now) == DayPlan.Phase.ACTIVE

    /** What a task without a time shows on the right: its project, or who it waits on. */
    private fun aside(t: Task) = Projects.projectOf(t)?.let { "#$it" } ?: Focus.waitingFor(t)?.let { tr("รอ $it", "waiting on $it") } ?: ""

    private fun timedOn(e: CalendarEvent, d: LocalDate) = !e.allDay && e.begin.toLocalDate() == d

    private fun onDay(e: CalendarEvent, d: LocalDate) = MonthData.onDay(e, d)
}
