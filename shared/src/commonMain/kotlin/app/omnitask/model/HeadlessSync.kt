package app.omnitask.model

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

    /** How long Omni waits for a run's answer before it falls back to opening Obsidian. */
    const val TIMEOUT_MS = 180_000L

    sealed class Outcome {
        /** The sync finished: read the vault again. */
        object Ok : Outcome()

        /** Termux is there but the script is not: the setup has not been run. */
        object NotSetUp : Outcome()

        /** Termux refuses commands from other apps until allow-external-apps is set in termux.properties. */
        object NotAllowed : Outcome()

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
        return Outcome.Failed(lastLine(stderr) ?: msg.ifEmpty { "exit $exitCode" })
    }

    /** Whether a run may start now: the first one, one after the clock went back, or one [MIN_GAP_MS] after the last. */
    fun due(lastStartMs: Long, nowMs: Long): Boolean =
        lastStartMs <= 0 || nowMs < lastStartMs || nowMs - lastStartMs >= MIN_GAP_MS

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
