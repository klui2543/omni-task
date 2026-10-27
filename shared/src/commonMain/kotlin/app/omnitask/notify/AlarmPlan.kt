package app.omnitask.notify

import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/** What the user turned on in the notification settings. Everything is on by default. */
data class NotifySettings(
    val taskReminders: Boolean = true,
    val digestTimes: List<LocalTime> = listOf(LocalTime.of(6, 30)),
    val digestDueToday: Boolean = true,
    val digestOverdue: Boolean = true,
    val digestWaiting: Boolean = true,
    val weeklyReview: Boolean = true,
    val calendarEvents: Boolean = true,
    /**
     * How long before an event to remind, in minutes: one alarm for each (owner, 2026-10-26: "เลือกหลายเวลาได้ และ
     * ล่วงหน้าหลักวันได้"). Empty: no event reminders.
     */
    val calendarLeads: List<Int> = listOf(10),
) {
    companion object {
        val WEEKLY_DAY: DayOfWeek = DayOfWeek.SUNDAY
        val WEEKLY_TIME: LocalTime = LocalTime.of(20, 0)
        val DIGEST_PRESETS: List<LocalTime> = listOf(6 to 0, 6 to 30, 7 to 0, 12 to 0, 17 to 0, 21 to 0).map { (h, m) -> LocalTime.of(h, m) }
        /** Leads under a day, in minutes. */
        val LEAD_PRESETS = listOf(5, 10, 15, 30, 60, 120)
        /** Leads of whole days, in minutes: 1 day, 2 days, 1 week. */
        val DAY_LEAD_PRESETS = listOf(1440, 2880, 10080)

        /** "30 นาที", "2 ชั่วโมง", "1 วัน", "1 สัปดาห์". */
        fun leadLabel(minutes: Int): String = when {
            minutes >= 10080 && minutes % 10080 == 0 -> (minutes / 10080).let { tr("$it สัปดาห์", if (it == 1) "1 week" else "$it weeks") }
            minutes >= 1440 && minutes % 1440 == 0 -> (minutes / 1440).let { tr("$it วัน", if (it == 1) "1 day" else "$it days") }
            minutes >= 60 && minutes % 60 == 0 -> (minutes / 60).let { tr("$it ชั่วโมง", if (it == 1) "1 hour" else "$it hours") }
            else -> tr("$minutes นาที", "$minutes min")
        }
    }
}

/** One event from the phone's calendars (Google Calendar syncs into these). */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    /** The phone calendar it is in, so Focus can say which one; 0 where that is not known (the web). */
    val calendarId: Long = 0,
) {
    companion object {
        /**
         * One of each event. The same event can sit in two calendars on the phone (an account synced twice, or another
         * app's copy of a Google calendar) and comes back once from each; Google Calendar shows it once, so Omni does too.
         * Copies match on title and time; the one with the lowest id is kept, so every read keeps the same one.
         */
        fun distinct(events: List<CalendarEvent>): List<CalendarEvent> =
            events.groupBy { listOf(it.title.trim(), it.begin, it.end, it.allDay) }.values.map { copies -> copies.minBy { it.id } }
    }
}

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
    /** Event alarms are set for the next this many hours; a rescan every few hours sets the later ones. */
    const val EVENT_HORIZON_HOURS = 48L

    /**
     * How far ahead the phone must read the calendars: an event this far away can still have an alarm within
     * [EVENT_HORIZON_HOURS] when its lead is long (a week before).
     */
    fun eventReadHours(settings: NotifySettings): Long = EVENT_HORIZON_HOURS + (settings.calendarLeads.maxOrNull() ?: 0) / 60L + 1

    fun plan(tasks: List<Task>, events: List<CalendarEvent>, settings: NotifySettings, now: LocalDateTime): List<PlannedAlarm> = buildList {
        if (settings.taskReminders) addAll(taskAlarms(tasks, now, now.plusDays(TASK_HORIZON_DAYS)))
        if (settings.calendarEvents) {
            events.filterNot { it.allDay }.forEach { e ->
                settings.calendarLeads.distinct().sorted().forEach { lead ->
                    val at = e.begin.minusMinutes(lead.toLong())
                    if (at.isAfter(now) && !at.isAfter(now.plusHours(EVENT_HORIZON_HOURS))) {
                        add(PlannedAlarm(AlarmKind.EVENT, at, "event:${e.id}:${e.begin}:$lead", e.title, eventText(e, lead)))
                    }
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

    /**
     * The reminders of open tasks (⏰ or 🎯 with a time) that ring after [from] and no later than [to]. The phone sets
     * the ones still to come; the web, which can only remind while it is open, also looks back for those it missed.
     */
    fun taskAlarms(tasks: List<Task>, from: LocalDateTime, to: LocalDateTime): List<PlannedAlarm> = tasks.filter { it.isOpen }.mapNotNull { t ->
        val at = t.reminderAt ?: return@mapNotNull null
        if (!at.isAfter(from) || at.isAfter(to)) return@mapNotNull null
        PlannedAlarm(AlarmKind.TASK, at, "task:${t.fileUri}:${t.raw}", t.title, reminderText(t), taskRaw = t.raw, fileUri = t.fileUri)
    }

    private fun nextAt(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun hhmm(t: LocalDateTime) = app.omnitask.time.hhmm(t.hour, t.minute)

    private fun reminderText(t: Task): String = buildList {
        t.due?.let { add(tr("ครบ $it", "Due $it")) }
        if (t.due == null) t.scheduled?.let { add(tr("นัด $it", "Scheduled $it")) }
        t.tags.filterNot { it.startsWith("remind-at-") }.take(2).forEach { add("#$it") }
    }.joinToString(", ")

    /** "อีก 30 นาที (13:00 ถึง 14:00)"; a lead of a day or more names the day too, "อีก 1 วัน (ศ. 30 ต.ค. 09:00 ถึง 10:00)". */
    private fun eventText(e: CalendarEvent, lead: Int): String {
        val day = if (lead >= 1440) app.omnitask.model.Countdown.day(e.begin.date) + " " else ""
        val label = NotifySettings.leadLabel(lead)
        return tr("อีก $label ($day${hhmm(e.begin)} ถึง ${hhmm(e.end)})", "In $label ($day${hhmm(e.begin)} to ${hhmm(e.end)})")
    }
}
