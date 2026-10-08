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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
            calendarLeadMinutes = p.getInt(KEY_SETTINGS + "lead", d.calendarLeadMinutes),
        )
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
            .putInt(KEY_SETTINGS + "lead", s.calendarLeadMinutes)
            .apply()
    }

    /** Reads the vault the app was pointed at; empty when no vault is set or it cannot be read. */
    fun loadVaultTasks(context: Context): List<Task> {
        val vault = prefs(context).getString(KEY_VAULT, null)?.let(Uri::parse) ?: return emptyList()
        // Tasks in parked project branches stay quiet: no alarms, not in the widgets.
        val parked = app.omnitask.model.Branches.parse(prefs(context).getStringSet("branchStates", emptySet()).orEmpty())
        return runCatching { VaultRepository(context).loadTasks(vault) }.getOrDefault(emptyList())
            .filterNot { app.omnitask.model.Branches.isParked(it, parked) }
    }

    /** Replaces every planned alarm with a fresh plan. Safe to call often. */
    fun reschedule(context: Context, tasks: List<Task>) {
        val now = LocalDateTime.now()
        val settings = loadSettings(context)
        val events = if (settings.calendarEvents) readCalendar(context, now) else emptyList()
        val plan = AlarmPlan.plan(tasks, events, settings, now)

        val am = context.getSystemService(AlarmManager::class.java)
        val previous = prefs(context).getStringSet(KEY_ALARM_IDS, emptySet()).orEmpty()
        val next = plan.map { it.id }.toSet()
        (previous - next).forEach { id -> am.cancel(pendingIntent(context, PlannedAlarm(AlarmKind.RESCAN, now, id))) }
        plan.forEach { set(context, am, it) }
        prefs(context).edit().putStringSet(KEY_ALARM_IDS, next).apply()
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

    private fun readCalendar(context: Context, now: LocalDateTime): List<CalendarEvent> =
        CalendarReader.events(context, now, now.plusHours(48))
}
