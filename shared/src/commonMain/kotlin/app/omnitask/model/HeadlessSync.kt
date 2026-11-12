package app.omnitask.model

import app.omnitask.drive.sameName

/**
 * Obsidian's headless sync client (`ob`) running in Termux on the phone, started by Omni in the background instead of
 * opening the Obsidian app over Omni (owner, 2026-11-13). This file reads what Termux sends back and says when another
 * run may start; the Android side (TermuxSync) sends the command.
 */
object HeadlessSync {
    /** The script the setup in docs/termux-sync-setup.sh writes in Termux's home folder. */
    const val SCRIPT = "/data/data/com.termux/files/home/omni-sync.sh"

    /** Two runs at least this far apart, so leaving and coming back quickly does not start a sync each time. */
    const val MIN_GAP_MS = 20_000L

    /**
     * How long Omni waits for a resume run's answer before it says Termux did not answer, and how old a run still
     * marked as running may get before another one may start.
     */
    const val TIMEOUT_MS = 180_000L

    /**
     * How long Omni waits for a leave run's answer: the script may first wait up to 150 seconds for a run that holds
     * its lock, then sync for up to 170 seconds, plus a margin.
     */
    const val LEAVE_TIMEOUT_MS = 360_000L

    /** Two leave runs at least this far apart, so a burst of pauses (folding the phone) starts one sync, not several. */
    const val LEAVE_GAP_MS = 3_000L

    /** The argument a leave run passes to the script, so it waits for a run that holds the lock instead of giving up. */
    const val LEAVE_ARG = "leave"

    /** The folder the setup script syncs (/storage/emulated/0/OmniVault). The new way is on only while Omni reads it. */
    const val FOLDER = "OmniVault"

    /** The script's exit when another run holds its lock. */
    const val EXIT_BUSY = 75

    /** The reason given when Termux's RUN_COMMAND service could not be found (Termux from Google Play has none). */
    const val NO_SERVICE = "RUN_COMMAND service not found"

    sealed class Outcome {
        /** The sync finished: read the vault again. */
        object Ok : Outcome()

        /** Termux is there but the script is not: the setup has not been run. */
        object NotSetUp : Outcome()

        /** Termux refuses commands from other apps until allow-external-apps is set in termux.properties. */
        object NotAllowed : Outcome()

        /** Another run holds the script's lock and is still syncing: nothing to say, nothing to do. */
        object Busy : Outcome()

        /** Anything else that went wrong, with the line that says why. */
        data class Failed(val reason: String) : Outcome()
    }

    /**
     * What a run's result says. [err] is Termux's own error code (-1 when there was none), [errmsg] its text,
     * [exitCode] and [stderr] the script's. No result at all is never success.
     */
    fun outcome(err: Int?, errmsg: String?, exitCode: Int?, stderr: String?): Outcome {
        val msg = errmsg?.trim().orEmpty()
        if (msg.contains("allow-external-apps")) return Outcome.NotAllowed
        if (exitCode == 127 || "No such file" in msg || scriptMissing(msg) || scriptMissing(stderr.orEmpty())) return Outcome.NotSetUp
        if (err != null && err != -1) return Outcome.Failed(msg.ifEmpty { "error $err" })
        if (exitCode == null) return Outcome.Failed(msg.ifEmpty { lastLine(stderr) ?: "no result" })
        if (exitCode == 0) return Outcome.Ok
        if (exitCode == EXIT_BUSY) return Outcome.Busy
        return Outcome.Failed(lastLine(stderr) ?: msg.ifEmpty { "exit $exitCode" })
    }

    /** Whether a run may start now: the first one, one after the clock went back, or one [MIN_GAP_MS] after the last. */
    fun due(lastStartMs: Long, nowMs: Long): Boolean =
        lastStartMs <= 0 || nowMs < lastStartMs || nowMs - lastStartMs >= MIN_GAP_MS

    /**
     * Whether a leave run may start now: the first one, one after the clock went back, or one [LEAVE_GAP_MS] after the
     * last leave start. Leave runs skip [due] and [free], so what Omni wrote is sent up even right after a resume run.
     */
    fun leaveDue(lastLeaveStartMs: Long, nowMs: Long): Boolean =
        lastLeaveStartMs <= 0 || nowMs < lastLeaveStartMs || nowMs - lastLeaveStartMs >= LEAVE_GAP_MS

    /** How long Omni waits for a run's answer: a leave run may wait for the lock first, so it gets longer. */
    fun timeoutMs(leave: Boolean): Long = if (leave) LEAVE_TIMEOUT_MS else TIMEOUT_MS

    /** Why the synced folder is not syncing: Termux is not on the phone, or Omni may not run commands in it. */
    enum class NotReady { NO_TERMUX, NO_PERMISSION }

    /**
     * Why the folder Omni reads, named [folderName], is not kept up to date, or null when there is nothing to say: the
     * folder is not [FOLDER] (the way it always was), or Termux is there and Omni may run commands in it.
     */
    fun notReady(folderName: String?, termuxInstalled: Boolean, permissionGranted: Boolean): NotReady? = when {
        !isSyncedFolder(folderName) -> null
        !termuxInstalled -> NotReady.NO_TERMUX
        !permissionGranted -> NotReady.NO_PERMISSION
        else -> null
    }

    /**
     * Whether a new run may start while [pendingSinceMs] marks one as running (0 when none): none is running, the
     * running one is older than [TIMEOUT_MS] (its answer is lost, for example the process died), or the clock went back.
     */
    fun free(pendingSinceMs: Long, nowMs: Long): Boolean =
        pendingSinceMs <= 0 || nowMs < pendingSinceMs || nowMs - pendingSinceMs >= TIMEOUT_MS

    /** Whether the vault folder Omni reads, named [folderName], is the one the headless sync keeps up to date. */
    fun isSyncedFolder(folderName: String?): Boolean = folderName != null && sameName(folderName, FOLDER)

    /**
     * The vault to name in an `obsidian://open` link for the folder [folderName]. None for [FOLDER]: the Obsidian app
     * has no vault of that name (it keeps its own folder), so the link must open in the app's own vault.
     */
    fun obsidianVault(folderName: String?): String? = folderName?.takeIf { !isSyncedFolder(it) }

    /**
     * A line that says the script itself is missing. A "No such file" from inside a run that did start (ob missing a
     * vault file) is a failure, not a missing setup, so the script's name must be on it.
     */
    private fun scriptMissing(text: String): Boolean {
        val t = text.lowercase()
        return SCRIPT_NAME in t && ("no such file" in t || "not found" in t || "does not exist" in t)
    }

    private const val SCRIPT_NAME = "omni-sync.sh"

    private fun lastLine(text: String?): String? = text?.lines()?.lastOrNull { it.isNotBlank() }?.trim()
}
