package app.omnitask.notify

import app.omnitask.model.Task
import app.omnitask.model.tr
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** What the user turned on in the notification settings. Everything is on by default. */
data class NotifySettings(
    val taskReminders: Boolean = true,
    val digestTimes: List<LocalTime> = listOf(LocalTime.of(6, 30)),
    val digestDueToday: Boolean = true,
    val digestOverdue: Boolean = true,
    val digestWaiting: Boolean = true,
    val weeklyReview: Boolean = true,
    val calendarEvents: Boolean = true,
    val calendarLeadMinutes: Int = 10,
) {
    companion object {
        val WEEKLY_DAY: DayOfWeek = DayOfWeek.SUNDAY
        val WEEKLY_TIME: LocalTime = LocalTime.of(20, 0)
        val DIGEST_PRESETS: List<LocalTime> = listOf(6 to 0, 6 to 30, 7 to 0, 12 to 0, 17 to 0, 21 to 0).map { (h, m) -> LocalTime.of(h, m) }
        val LEAD_PRESETS = listOf(5, 10, 15, 30)
    }
}

/** One event from the phone's calendars (Google Calendar syncs into these). */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
)

enum class AlarmKind { TASK, EVENT, DIGEST, WEEKLY, RESCAN, TEST }

/**
 * One alarm to set. [id] is stable for the same thing across rescans, so re-planning replaces an alarm
 * instead of duplicating it. Task alarms carry the line itself so the receiver can find and edit it.
 */
data class PlannedAlarm(
    val kind: AlarmKind,
    val at: LocalDateTime,
    val id: String,
    val title: String = "",
    val text: String = "",
    val taskRaw: String? = null,
    val fileUri: String? = null,
)

/** Pure planning: which alarms should exist right now. The Android side only sets what this returns. */
object AlarmPlan {

    /** Vault files can change through sync while the app is closed, so the plan is rebuilt this often. */
    const val RESCAN_HOURS = 3L
    private const val TASK_HORIZON_DAYS = 7L
    private const val EVENT_HORIZON_HOURS = 48L

    fun plan(tasks: List<Task>, events: List<CalendarEvent>, settings: NotifySettings, now: LocalDateTime): List<PlannedAlarm> = buildList {
        if (settings.taskReminders) {
            tasks.filter { it.isOpen }.forEach { t ->
                val at = t.reminderAt ?: return@forEach
                if (at.isAfter(now) && !at.isAfter(now.plusDays(TASK_HORIZON_DAYS))) {
                    add(
                        PlannedAlarm(
                            AlarmKind.TASK, at, "task:${t.fileUri}:${t.raw}", t.title,
                            reminderText(t), taskRaw = t.raw, fileUri = t.fileUri,
                        ),
                    )
                }
            }
        }
        if (settings.calendarEvents) {
            events.filterNot { it.allDay }.forEach { e ->
                val at = e.begin.minusMinutes(settings.calendarLeadMinutes.toLong())
                if (at.isAfter(now) && !e.begin.isAfter(now.plusHours(EVENT_HORIZON_HOURS))) {
                    add(PlannedAlarm(AlarmKind.EVENT, at, "event:${e.id}:${e.begin}", e.title, eventText(e, settings.calendarLeadMinutes)))
                }
            }
        }
        settings.digestTimes.distinct().forEach { time ->
            add(PlannedAlarm(AlarmKind.DIGEST, nextAt(now, time), "digest:$time"))
        }
        if (settings.weeklyReview) {
            val day = now.toLocalDate().with(TemporalAdjusters.nextOrSame(NotifySettings.WEEKLY_DAY))
            var at = day.atTime(NotifySettings.WEEKLY_TIME)
            if (!at.isAfter(now)) at = at.plusWeeks(1)
            add(PlannedAlarm(AlarmKind.WEEKLY, at, "weekly"))
        }
        add(PlannedAlarm(AlarmKind.RESCAN, now.plusHours(RESCAN_HOURS), "rescan"))
    }

    private fun nextAt(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun hhmm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

    private fun reminderText(t: Task): String = buildList {
        t.due?.let { add(tr("ครบ $it", "Due $it")) }
        if (t.due == null) t.scheduled?.let { add(tr("นัด $it", "Scheduled $it")) }
        t.tags.filterNot { it.startsWith("remind-at-") }.take(2).forEach { add("#$it") }
    }.joinToString(", ")

    private fun eventText(e: CalendarEvent, lead: Int) =
        tr("อีก $lead นาที (${hhmm(e.begin)} ถึง ${hhmm(e.end)})", "In $lead min (${hhmm(e.begin)} to ${hhmm(e.end)})")
}
