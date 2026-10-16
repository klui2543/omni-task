package app.omnitask.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import android.os.SystemClock
import app.omnitask.data.CalendarReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * Redraws the widgets when the phone's calendar changes. Without it a widget only caught a new Google event at its
 * next scheduled update, half an hour or more later. A content-trigger job fires even while the app is closed, but
 * only once, so it is set again every time it ends and whenever the widgets are redrawn.
 */
object CalendarWatch {
    private const val JOB_ID = 4201

    /** How many times the watch has woken since this process started; the emulator test waits on it. */
    val wakes = java.util.concurrent.atomic.AtomicInteger()

    fun isArmed(context: Context) = context.getSystemService(JobScheduler::class.java)?.getPendingJob(JOB_ID) != null

    fun armIfNeeded(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) == null) arm(context)
    }

    fun arm(context: Context) {
        if (!CalendarReader.hasPermission(context)) return
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarWatchService::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            // A sync writes many rows: wait for it to settle, but never longer than 20 seconds.
            .setTriggerContentUpdateDelay(5_000)
            .setTriggerContentMaxDelay(20_000)
            .build()
        runCatching { scheduler.schedule(job) }
    }
}

class CalendarWatchService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        CalendarWatch.wakes.incrementAndGet()
        scope.launch {
            runCatching { CalendarPrint.refreshIfChanged(applicationContext, "calendar watch") }
            jobFinished(params, false)
            CalendarWatch.arm(applicationContext)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}

/**
 * What the phone's calendar holds around now, as one number. Google's sync touches the calendar every few minutes,
 * mostly without changing an event, and each touch used to reload the vault and the calendar for every widget (up to
 * three seconds each on the owner's phone). Reading the events alone is quick, so a touch now redraws the widgets
 * only when the events or the calendars actually changed.
 */
object CalendarPrint {
    private const val PREFS = "calendar-print"
    private const val KEY = "print"

    /** The events from about three months back to six ahead, and the calendars with whether each is shown. */
    fun take(context: Context): Long {
        val today = LocalDate.now()
        val events = CalendarReader.events(context, today.minusDays(100).atStartOfDay(), today.plusDays(200).atStartOfDay())
        val calendars = CalendarReader.calendars(context)
        return (events.hashCode().toLong() shl 32) xor (calendars.hashCode().toLong() * 31 + CalendarReader.hiddenIds(context).hashCode() + today.hashCode())
    }

    /** Whether the calendar changed since the last call; the first call answers yes. */
    fun changed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = take(context)
        if (prefs.contains(KEY) && prefs.getLong(KEY, 0) == now) return false
        prefs.edit().putLong(KEY, now).apply()
        return true
    }

    /** Redraws the widgets if the calendar changed; [why] goes in the widget log. */
    suspend fun refreshIfChanged(context: Context, why: String) {
        WidgetLog.attach(context)
        val began = SystemClock.elapsedRealtime()
        val changed = runCatching { changed(context) }.getOrDefault(true)
        WidgetLog.add("$why: calendar ${if (changed) "changed, redrawing" else "the same, skipped"} (checked in ${WidgetLog.ms(began)} ms)")
        if (changed) OmniWidgets.refresh(context)
    }
}
