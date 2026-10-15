package app.omnitask.data

import android.content.Context
import android.net.Uri
import app.omnitask.model.MonthMarks
import app.omnitask.notify.Scheduler
import java.io.File

/**
 * The month widget's frames and emoji. The phone keeps a copy in its own files, so the widget draws without waiting
 * for Drive; every change also goes to the vault ([MonthMarks.PATH]), and a load takes the vault's copy when it was
 * saved later (from another phone, or before the app was cleared).
 */
object MonthMarksStore {

    private fun cache(context: Context) = File(context.filesDir, "omni-calendar.json")

    private fun vault(context: Context): Uri? = Scheduler.prefs(context).getString("vault", null)?.let(Uri::parse)

    @Synchronized
    fun local(context: Context): MonthMarks =
        MonthMarks.decode(runCatching { cache(context).readText() }.getOrNull()) ?: MonthMarks()

    /** Saves [marks] on the phone and in the vault, stamped now; returns what was saved. Call off the main thread. */
    fun save(context: Context, marks: MonthMarks): MonthMarks {
        val stamped = marks.copy(savedAt = System.currentTimeMillis())
        writeLocal(context, stamped)
        vault(context)?.let { v -> runCatching { VaultRepository(context).writePath(v, MonthMarks.PATH, stamped.encode()) } }
        return stamped
    }

    /**
     * Brings the phone and the vault level: the later copy wins on both sides. Returns true when the phone's copy
     * changed, so the widget needs redrawing. Call off the main thread.
     */
    fun sync(context: Context, repo: VaultRepository, vault: Uri): Boolean {
        val local = local(context)
        val text = repo.readPath(vault, MonthMarks.PATH)
        val remote = MonthMarks.decode(text)
        val winner = MonthMarks.newer(local, remote)
        if (winner === local) {
            // Nothing in the vault yet, or a save that did not reach it (offline): send the phone's copy up.
            if (local.savedAt > 0 && (remote == null || remote.savedAt < local.savedAt)) repo.writePath(vault, MonthMarks.PATH, local.encode())
            return false
        }
        if (winner == local) return false
        writeLocal(context, winner)
        return true
    }

    @Synchronized
    private fun writeLocal(context: Context, marks: MonthMarks) {
        val file = cache(context)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(marks.encode())
        if (!tmp.renameTo(file)) { file.writeText(marks.encode()); tmp.delete() }
    }
}
