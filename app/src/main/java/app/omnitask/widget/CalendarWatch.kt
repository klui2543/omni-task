package app.omnitask.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import app.omnitask.data.CalendarReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when the phone's calendar changes. Without it a widget only caught a new Google event at its
 * next scheduled update, half an hour or more later. A content-trigger job fires even while the app is closed, but
 * only once, so it is set again every time it ends and whenever the widgets are redrawn.
 */
object CalendarWatch {
    private const val JOB_ID = 4201

    fun armIfNeeded(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) == null) arm(context)
    }

    fun arm(context: Context) {
        if (!CalendarReader.hasPermission(context)) return
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarWatchService::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            // A sync writes many rows: wait for it to settle, but never longer than 10 seconds.
            .setTriggerContentUpdateDelay(2_000)
            .setTriggerContentMaxDelay(10_000)
            .build()
        runCatching { scheduler.schedule(job) }
    }
}

class CalendarWatchService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            runCatching { OmniWidgets.refresh(applicationContext) }
            jobFinished(params, false)
            CalendarWatch.arm(applicationContext)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
