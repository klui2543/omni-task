package app.omnitask.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.omnitask.model.HeadlessSync
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Starts Obsidian's headless sync (`ob`, set up in Termux by docs/termux-sync-setup.sh) in the background through
 * Termux's RUN_COMMAND service, so the vault on the phone catches up without opening the Obsidian app over Omni
 * (owner, 2026-11-13). Off ([on]) until Termux is installed, the owner has allowed Omni to run commands in it, and Omni
 * reads the folder the setup syncs ([HeadlessSync.FOLDER]); until then everything works as before.
 */
object TermuxSync {
    private const val PACKAGE = "com.termux"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val HOME = "/data/data/com.termux/files/home"
    private const val KEY_LAST_START = "termux.lastStart"

    /** Omni's own extras on the answer intent; Termux only adds its `result` bundle, so these come back as sent. */
    internal const val EXTRA_RUN = "app.omnitask.TERMUX_RUN"
    internal const val EXTRA_REASON = "app.omnitask.TERMUX_REASON"

    /** Why a run was started: the app came back to the screen, or it left (each has its own timeout, see [HeadlessSync.timeoutMs]). */
    const val RESUME = "resume"
    const val LEAVE = "leave"

    /** One run's answer: which run, why it was started, and what came of it. */
    data class Answer(val runId: Long, val reason: String, val outcome: HeadlessSync.Outcome)

    private val _results = MutableSharedFlow<Answer>(extraBufferCapacity = 8)

    /** Every run's answer, and a run that could not be started; the app reloads on Ok and says why otherwise. */
    val results: SharedFlow<Answer> = _results

    /** The run that is syncing now (0 when none) and when it started; kept until its own answer or its timeout. */
    private var pendingRun = 0L
    private var pendingSince = 0L

    /** When the last leave run started in this process (0 when none), for [HeadlessSync.leaveDue]. */
    private var lastLeaveStart = 0L
    private val lock = Any()

    private fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    private fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Termux is on the phone and Omni may run commands in it (the folder is not checked: see [on]). */
    fun ready(context: Context): Boolean = installed(context) && granted(context)

    /**
     * The one switch for the new way: Termux is on the phone, Omni may run commands in it, and the vault folder Omni
     * reads, named [folderName], is the one the headless sync keeps up to date.
     */
    fun on(context: Context, folderName: String?): Boolean =
        HeadlessSync.isSyncedFolder(folderName) && ready(context)

    /** Omni reads the synced folder and Termux is on the phone, but Omni may not run commands in it yet: ask. */
    fun shouldAsk(context: Context, folderName: String?): Boolean =
        HeadlessSync.notReady(folderName, installed(context), granted(context)) == HeadlessSync.NotReady.NO_PERMISSION

    /** Why Omni reads the synced folder but cannot keep it up to date, or null (another folder, or all is ready). */
    fun notReady(context: Context, folderName: String?): HeadlessSync.NotReady? =
        HeadlessSync.notReady(folderName, installed(context), granted(context))

    fun lastStart(context: Context): Long = prefs(context).getLong(KEY_LAST_START, 0L)

    /**
     * Starts a sync when the new way is [on]. A resume run starts only when no other resume run of Omni's is still
     * syncing (one older than [HeadlessSync.TIMEOUT_MS] no longer counts) and the last resume start was long enough ago
     * ([HeadlessSync.due]). A leave run skips both, so what Omni wrote is always sent up: only a burst of pauses within
     * [HeadlessSync.LEAVE_GAP_MS] is one run, and the script waits for a run that holds its lock ([HeadlessSync.LEAVE_ARG]).
     * Gives the run's id when one started; a start that fails is sent to [results] as a failure, so it reaches the owner.
     */
    fun request(context: Context, folderName: String?, reason: String): Long? {
        if (!on(context, folderName)) return null
        val leave = reason == LEAVE
        val now = System.currentTimeMillis()
        return synchronized(lock) {
            if (leave) {
                if (!HeadlessSync.leaveDue(lastLeaveStart, now)) return null
            } else {
                if (!HeadlessSync.free(pendingSince, now)) return null
                if (!HeadlessSync.due(lastStart(context), now)) return null
            }
            val runId = now
            try {
                val answer = PendingIntent.getBroadcast(
                    context,
                    (runId and 0x7fffffff).toInt(),
                    Intent(context, TermuxSyncReceiver::class.java)
                        .putExtra(EXTRA_RUN, runId)
                        .putExtra(EXTRA_REASON, reason),
                    // Termux fills the result into this intent, so it has to stay mutable on Android 12 and later.
                    PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
                )
                val intent = Intent(ACTION)
                    .setClassName(PACKAGE, SERVICE)
                    .putExtra(EXTRA_PATH, HeadlessSync.SCRIPT)
                    .putExtra(EXTRA_WORKDIR, HOME)
                    .putExtra(EXTRA_BACKGROUND, true)
                    .putExtra(EXTRA_LABEL, "Omni sync ($reason)")
                    .putExtra(EXTRA_PENDING_INTENT, answer)
                if (leave) intent.putExtra(EXTRA_ARGUMENTS, arrayOf(HeadlessSync.LEAVE_ARG))
                if (context.startForegroundService(intent) == null) {
                    // No such service: a Termux without RUN_COMMAND (the old one from Google Play). Nothing started.
                    answer.cancel()
                    _results.tryEmit(Answer(runId, reason, HeadlessSync.Outcome.Failed(HeadlessSync.NO_SERVICE)))
                    return null
                }
                // Saved only once the run has started, so a start that failed is tried again next time.
                if (leave) {
                    // Not the pending run: a resume run's gate stays its own, and this run's answer ends nothing.
                    lastLeaveStart = now
                } else {
                    prefs(context).edit().putLong(KEY_LAST_START, now).apply()
                    pendingRun = runId
                    pendingSince = now
                }
                return runId
            } catch (e: Exception) {
                // SecurityException (permission taken back) or IllegalStateException (start not allowed now).
                _results.tryEmit(Answer(runId, reason, HeadlessSync.Outcome.Failed(e.message ?: e.javaClass.simpleName)))
                return null
            }
        }
    }

    /** An answer came back. It ends the run it names, unless the lock was busy (another run is still syncing). */
    internal fun publish(result: Answer) {
        if (result.outcome != HeadlessSync.Outcome.Busy) end(result.runId)
        _results.tryEmit(result)
    }

    /** The run [runId] is over (answered, or its wait gave up): another may start. Another run's mark is kept. */
    fun end(runId: Long) {
        synchronized(lock) {
            if (pendingRun == runId) {
                pendingRun = 0L
                pendingSince = 0L
            }
        }
    }

    /**
     * This phone's own record, kept apart from the "omnitask" settings: those are copied to the vault on every change
     * (SettingsSync), and a start time written there would rewrite the settings note at every sync.
     */
    fun prefs(context: Context) = context.getSharedPreferences("termux", Context.MODE_PRIVATE)
}

/** Termux's answer to a run, sent back through the PendingIntent [TermuxSync.request] gave it. */
class TermuxSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra("result")
        val outcome = HeadlessSync.outcome(
            err = result?.takeIf { it.containsKey("err") }?.getInt("err", -1),
            errmsg = result?.getString("errmsg"),
            exitCode = result?.takeIf { it.containsKey("exitCode") }?.getInt("exitCode", -1),
            stderr = result?.getString("stderr"),
        )
        TermuxSync.publish(
            TermuxSync.Answer(
                runId = intent.getLongExtra(TermuxSync.EXTRA_RUN, 0L),
                reason = intent.getStringExtra(TermuxSync.EXTRA_REASON) ?: "unknown",
                outcome = outcome,
            ),
        )
    }
}
