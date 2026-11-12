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
 * (owner, 2026-11-13). Off until Termux is installed and the owner has allowed Omni to run commands in it; until
 * then the app opens Obsidian as before.
 */
object TermuxSync {
    private const val PACKAGE = "com.termux"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val HOME = "/data/data/com.termux/files/home"
    private const val KEY_LAST_START = "termux.lastStart"

    private val _results = MutableSharedFlow<HeadlessSync.Outcome>(extraBufferCapacity = 4)

    /** Every run's outcome, and a run that could not be started; the app reloads on Ok and says why otherwise. */
    val results: SharedFlow<HeadlessSync.Outcome> = _results

    /** True from a run's start until its answer comes back (or the wait gives up). */
    @Volatile var pending: Boolean = false

    private fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    private fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The one switch for the new way: Termux is on the phone and Omni may run commands in it. */
    fun available(context: Context): Boolean = installed(context) && granted(context)

    /** Termux is on the phone but Omni has not been allowed to run commands in it yet: ask for the permission. */
    fun installedWithoutPermission(context: Context): Boolean = installed(context) && !granted(context)

    fun lastStart(context: Context): Long = prefs(context).getLong(KEY_LAST_START, 0L)

    /**
     * Starts a sync when the new way is on and the last run was long enough ago ([HeadlessSync.due]). True when one
     * started; a start that throws is sent to [results] as a failure, so it reaches the owner.
     */
    fun request(context: Context, reason: String): Boolean {
        if (!available(context)) return false
        val now = System.currentTimeMillis()
        if (!HeadlessSync.due(lastStart(context), now)) return false
        return try {
            val code = (now and 0x7fffffff).toInt()
            val answer = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, TermuxSyncReceiver::class.java),
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
            context.startForegroundService(intent)
            // Saved only once the run has started, so a start that failed is tried again on the next resume.
            prefs(context).edit().putLong(KEY_LAST_START, now).apply()
            pending = true
            true
        } catch (e: Exception) {
            // SecurityException (permission taken back) or IllegalStateException (start not allowed now).
            publish(HeadlessSync.Outcome.Failed(e.message ?: e.javaClass.simpleName))
            false
        }
    }

    internal fun publish(outcome: HeadlessSync.Outcome) {
        pending = false
        _results.tryEmit(outcome)
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
        TermuxSync.publish(outcome)
    }
}
