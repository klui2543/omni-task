package app.omnitask.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the app's process out of the cached state while the month widget is being tapped.
 *
 * The owner's phone held a tap's broadcast back for about five seconds now and then, and then delivered the taps of
 * those seconds all at once: the app had gone back to the cached state between taps, and Android holds broadcasts to
 * a cached app. A running job keeps the process out of that state, so this one runs from a tap until [IDLE_MS] after
 * the last one. It is asked to run at once (expedited), does no work, and holds no wake lock of its own beyond the
 * one a running job carries.
 */
object WidgetWarm {
    private const val JOB_ID = 4202
    private const val IDLE_MS = 60_000L

    @Volatile
    var running = false
        private set

    /** Starts the job if it is not running already; harmless to call on every tap. */
    fun keep(context: Context) {
        if (running) return
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, WidgetWarmService::class.java))
            .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(true) else setOverrideDeadline(0) }
            .build()
        val result = runCatching { scheduler.schedule(job) }
        WidgetLog.add("warm job scheduled: ${result.getOrNull() ?: result.exceptionOrNull()}")
    }

    /** Runs the job until the widget has been left alone for [IDLE_MS]. */
    internal suspend fun hold() {
        running = true
        try {
            while (SystemClock.elapsedRealtime() - MonthLive.lastTapAt < IDLE_MS) delay(1_000)
        } finally {
            running = false
        }
    }
}

class WidgetWarmService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        WidgetLog.add("warm job started, expedited=${Build.VERSION.SDK_INT >= 31 && params.isExpeditedJob} ${AppRestriction.describe(this)}")
        scope.launch {
            WidgetWarm.hold()
            WidgetLog.add("warm job done")
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        WidgetLog.add("warm job stopped by the system, reason=${if (Build.VERSION.SDK_INT >= 31) params.stopReason else -1} ${AppRestriction.describe(this)}")
        // Asked for again by the next tap, since the job is not kept when the system stops it.
        return false
    }
}
