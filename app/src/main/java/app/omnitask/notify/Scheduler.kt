package app.omnitask.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import app.omnitask.data.CalendarReader
import app.omnitask.data.VaultRepository
import app.omnitask.model.Task
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.time.ZoneId
import app.omnitask.time.*

/** Sets and cancels the alarms [AlarmPlan] asks for, and stores the notification settings. */
object Scheduler {

    private const val PREFS = "omnitask"
    private const val KEY_VAULT = "vault"
    private const val KEY_ALARM_IDS = "alarmIds"
    private const val KEY_SETTINGS = "notify."

    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_RAW = "raw"
    const val EXTRA_FILE = "file"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadSettings(context: Context): NotifySettings {
        val p = prefs(context)
        val d = NotifySettings()
        return NotifySettings(
            taskReminders = p.getBoolean(KEY_SETTINGS + "task", d.taskReminders),
            digestTimes = p.getString(KEY_SETTINGS + "digestTimes", null)
                ?.split(',')?.filter { it.isNotBlank() }?.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: d.digestTimes,
            digestDueToday = p.getBoolean(KEY_SETTINGS + "dueToday", d.digestDueToday),
            digestOverdue = p.getBoolean(KEY_SETTINGS + "overdue", d.digestOverdue),
            digestWaiting = p.getBoolean(KEY_SETTINGS + "waiting", d.digestWaiting),
            weeklyReview = p.getBoolean(KEY_SETTINGS + "weekly", d.weeklyReview),
            calendarEvents = p.getBoolean(KEY_SETTINGS + "calendar", d.calendarEvents),
            calendarLeads = loadLeads(p, d.calendarLeads),
        )
    }

    /** The leads picked, as "30,1440"; before several could be picked there was one number under "lead", kept as it was. */
    private fun loadLeads(p: SharedPreferences, default: List<Int>): List<Int> {
        p.getString(KEY_SETTINGS + "leads", null)?.let { s -> return s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().sorted() }
        if (p.contains(KEY_SETTINGS + "lead")) return listOf(p.getInt(KEY_SETTINGS + "lead", default.firstOrNull() ?: 10))
        return default
    }

    fun saveSettings(context: Context, s: NotifySettings) {
        prefs(context).edit()
            .putBoolean(KEY_SETTINGS + "task", s.taskReminders)
            .putString(KEY_SETTINGS + "digestTimes", s.digestTimes.sorted().joinToString(","))
            .putBoolean(KEY_SETTINGS + "dueToday", s.digestDueToday)
            .putBoolean(KEY_SETTINGS + "overdue", s.digestOverdue)
            .putBoolean(KEY_SETTINGS + "waiting", s.digestWaiting)
            .putBoolean(KEY_SETTINGS + "weekly", s.weeklyReview)
            .putBoolean(KEY_SETTINGS + "calendar", s.calendarEvents)
            .putString(KEY_SETTINGS + "leads", s.calendarLeads.distinct().sorted().joinToString(","))
            .remove(KEY_SETTINGS + "lead")
            .apply()
    }

    /**
     * How old a read of Drive may be for widgets and reminders, which load several times in a row (a tick from the
     * widget loads, writes, loads again and redraws). Edits always read Drive fresh; opening the app always asks.
     */
    const val BACKGROUND_FRESH_MS = 2 * 60_000L

    /** Reads the vault the app was pointed at; empty when no vault is set or it cannot be read. */
    fun loadVaultTasks(context: Context): List<Task> {
        val vault = prefs(context).getString(KEY_VAULT, null)?.let(Uri::parse) ?: return emptyList()
        // Tasks in parked project branches stay quiet: no alarms, not in the widgets.
        val parked = app.omnitask.model.Branches.parse(prefs(context).getStringSet("branchStates", emptySet()).orEmpty())
        return runCatching { VaultRepository(context).loadTasks(vault, BACKGROUND_FRESH_MS) }.getOrDefault(emptyList())
            .filterNot { app.omnitask.model.Branches.isParked(it, parked) }
    }

    /** Replaces every planned alarm with a fresh plan. Safe to call often. */
    fun reschedule(context: Context, tasks: List<Task>) {
        val now = LocalDateTime.now()
        val settings = loadSettings(context)
        val events = if (settings.calendarEvents) readCalendar(context, now, settings) else emptyList()
        val plan = AlarmPlan.plan(tasks, events, settings, now)

        val am = context.getSystemService(AlarmManager::class.java)
        val previous = prefs(context).getStringSet(KEY_ALARM_IDS, emptySet()).orEmpty()
        val next = plan.map { it.id }.toSet()
        (previous - next).forEach { id -> am.cancel(pendingIntent(context, PlannedAlarm(AlarmKind.RESCAN, now, id))) }
        plan.forEach { set(context, am, it) }
        prefs(context).edit().putStringSet(KEY_ALARM_IDS, next).apply()
        runCatching { scheduleWidgetTick(context, am, now) }
    }

    /**
     * The Today widget draws the now line through the running event and greys it once it ends, so while an event runs it
     * is redrawn every 10 minutes and again when the event ends; with none running, when the next one starts. Every
     * alarm ends in a reschedule and a widget refresh, so each tick sets up the next. Kept out of the plan, which a
     * rescan replaces, and not tied to the notification settings.
     */
    private fun scheduleWidgetTick(context: Context, am: AlarmManager, now: LocalDateTime) {
        val events = CalendarReader.events(context, now.date.atStartOfDay(), now.date.plusDays(1).atStartOfDay()).filter { !it.allDay }
        val running = events.filter { it.begin <= now && it.end > now }
        val at = if (running.isNotEmpty()) minOf(now.plusMinutes(10), running.minOf { it.end }) else events.filter { it.begin > now }.minOfOrNull { it.begin }
        val tick = PlannedAlarm(AlarmKind.RESCAN, at ?: now, "widget-tick")
        if (at == null) am.cancel(pendingIntent(context, tick)) else set(context, am, tick)
    }

    /** One-off alarms (snooze, test) live outside the plan so a rescan never cancels them. */
    fun scheduleOneOff(context: Context, alarm: PlannedAlarm) {
        set(context, context.getSystemService(AlarmManager::class.java), alarm)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun set(context: Context, am: AlarmManager, alarm: PlannedAlarm) {
        val millis = alarm.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(context, alarm)
        if (alarm.kind == AlarmKind.RESCAN) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun pendingIntent(context: Context, alarm: PlannedAlarm): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            // The data URI makes each alarm's intent distinct, so they never overwrite one another.
            data = Uri.fromParts("omnitask", alarm.id, null)
            putExtra(EXTRA_KIND, alarm.kind.name)
            putExtra(EXTRA_ID, alarm.id)
            putExtra(EXTRA_TITLE, alarm.title)
            putExtra(EXTRA_TEXT, alarm.text)
            putExtra(EXTRA_RAW, alarm.taskRaw)
            putExtra(EXTRA_FILE, alarm.fileUri)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun hasCalendarPermission(context: Context) = CalendarReader.hasPermission(context)

    private fun readCalendar(context: Context, now: LocalDateTime, settings: NotifySettings): List<CalendarEvent> =
        CalendarReader.events(context, now, now.plusHours(AlarmPlan.eventReadHours(settings)))
}
