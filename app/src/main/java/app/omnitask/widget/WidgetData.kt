package app.omnitask.widget

import android.content.Context
import app.omnitask.data.CalendarReader
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Lang
import app.omnitask.model.load
import app.omnitask.model.Quadrant
import app.omnitask.model.Task
import app.omnitask.model.UrgentRule
import app.omnitask.model.quadrant
import app.omnitask.notify.CalendarEvent
import app.omnitask.notify.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/** Everything the home-screen widgets show, read straight from the vault and the calendar. */
class WidgetData(
    val today: LocalDate,
    val tasks: List<Task>,
    val events: List<CalendarEvent>,
    val urgentRule: UrgentRule,
    /** Events from today for [DAYS_AHEAD] days, for the Today widget's pages. */
    val eventsAhead: List<CalendarEvent> = events,
) {
    /** The Today widget's days, today first. */
    internal fun days(now: LocalDateTime): List<TodayWeek.Day> =
        TodayWeek.days(today, now, tasks, eventsAhead, DAYS_AHEAD) { calendarArgb(it.calendarId) }

    val brief by lazy { Focus.build(tasks, today, futureCount = 1) }

    /** Today's plan as on the Focus screen: late work, then the parts of the day, then work without a time; finished work stays, for the widget to grey. */
    val plan by lazy { DayPlan.build(tasks, events, today, includeDone = true) }

    /** Today's open work in the same order as the Focus timeline. */
    val todayTasks: List<Task> by lazy {
        DayPlan.build(tasks, events, today).flatMap { it.items }.filterIsInstance<DayPlan.Item.TaskItem>().map { it.task }
    }

    val doneToday get() = tasks.count { t -> t.done == today && (t.due ?: t.scheduled)?.let { it <= today } == true }

    fun nextEvent(now: LocalDateTime): CalendarEvent? = events.filter { !it.allDay && it.begin > now }.minByOrNull { it.begin }

    /** The next stretch of at least 30 free minutes before 21:00, or null when the day is full. */
    fun nextGap(now: LocalDateTime): Pair<LocalTime, LocalTime>? {
        val end = today.atTime(21, 0)
        var cursor = now.withSecond(0).withNano(0).let { it.plusMinutes(((15 - it.minute % 15) % 15).toLong()) }
        events.filter { !it.allDay && it.end > cursor && it.begin < end }.sortedBy { it.begin }.forEach { e ->
            if (ChronoUnit.MINUTES.between(cursor, e.begin) >= 30) return cursor.toLocalTime() to e.begin.toLocalTime()
            if (e.end > cursor) cursor = e.end
        }
        return if (ChronoUnit.MINUTES.between(cursor, end) >= 30) cursor.toLocalTime() to end.toLocalTime() else null
    }

    fun quadrantCounts(): Map<Quadrant, Int> =
        tasks.filter { it.isOpen && !Focus.isSomeday(it) }.groupingBy { it.quadrant(today, urgentRule) }.eachCount()

    companion object {
        const val DAYS_AHEAD = 21

        suspend fun load(context: Context): WidgetData = withContext(Dispatchers.IO) {
            Lang.load(context)
            app.omnitask.model.Appearance.load(context)
            val today = LocalDate.now()
            val rule = context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).getString("urgentRule", null)
                ?.let { name -> UrgentRule.entries.firstOrNull { it.name == name } } ?: UrgentRule.THIS_WEEK
            // Three weeks of events: the Today widget pages through the days ahead; the other widgets keep to today.
            val ahead = runCatching { CalendarReader.events(context, today.atStartOfDay(), today.plusDays(DAYS_AHEAD.toLong()).atStartOfDay()) }.getOrDefault(emptyList())
            val events = ahead.filter { it.begin < today.plusDays(1).atStartOfDay() && it.end > today.atStartOfDay() || it.begin.toLocalDate() == today }
            WidgetData(today, Scheduler.loadVaultTasks(context), events, rule, ahead)
        }
    }
}
