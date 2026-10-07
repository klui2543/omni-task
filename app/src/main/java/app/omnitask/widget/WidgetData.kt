package app.omnitask.widget

import android.content.Context
import app.omnitask.data.CalendarReader
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Lang
import app.omnitask.model.Quadrant
import app.omnitask.model.Task
import app.omnitask.model.UrgentRule
import app.omnitask.model.quadrant
import app.omnitask.notify.CalendarEvent
import app.omnitask.notify.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Everything the home-screen widgets show, read straight from the vault and the calendar. */
class WidgetData(
    val today: LocalDate,
    val tasks: List<Task>,
    val events: List<CalendarEvent>,
    val urgentRule: UrgentRule,
) {
    val brief by lazy { Focus.build(tasks, today, futureCount = 1) }

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
        suspend fun load(context: Context): WidgetData = withContext(Dispatchers.IO) {
            Lang.load(context)
            val today = LocalDate.now()
            val rule = context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).getString("urgentRule", null)
                ?.let { name -> UrgentRule.entries.firstOrNull { it.name == name } } ?: UrgentRule.THIS_WEEK
            val events = runCatching { CalendarReader.events(context, today.atStartOfDay(), today.plusDays(1).atStartOfDay()) }.getOrDefault(emptyList())
            WidgetData(today, Scheduler.loadVaultTasks(context), events, rule)
        }
    }
}
