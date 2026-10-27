package app.omnitask.data

import android.content.Context
import android.net.Uri
import app.omnitask.model.Behavior
import app.omnitask.model.BehaviorEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.io.File
import app.omnitask.time.*

/**
 * The phone's side of the behavior log ([Behavior]). Events wait in a file of the app's own, so the notification
 * buttons can add to it with the app closed, until [sync] writes them into the vault's `omni-behavior.json`, the
 * one file the phone, the watch and the web all learn from.
 */
object BehaviorLog {

    const val SRC = "android"

    private const val WAITING = "behavior-waiting.jsonl"

    /** The vault's file as last synced, for the summaries the alarms post with the app closed. */
    private const val CACHE = "behavior-cache.jsonl"

    /** The keys of the log this phone kept in its preferences before the file, brought in by [sync]. */
    private const val PREFS = "omnitask"
    private const val KEY_OLD_LOG = "doneLog"
    private const val KEY_DECLINED = "declinedInsights"

    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, WAITING)

    /** Keeps [event] until the next [sync]; nothing when null. */
    fun record(context: Context, event: BehaviorEvent?) {
        event ?: return
        synchronized(lock) { runCatching { file(context).appendText(Behavior.line(event) + "\n") } }
    }

    /** Every event as of the last [sync], with those still waiting; read without the network. */
    fun cached(context: Context): List<BehaviorEvent> {
        val kept = Behavior.parse(runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText() }.getOrNull())
        return (kept + waiting(context)).distinctBy { it.id }
    }

    /** The events not yet in the vault. */
    fun waiting(context: Context): List<BehaviorEvent> =
        synchronized(lock) { Behavior.parse(runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()) }

    /**
     * Takes the events [ids] back, wherever they are now (still waiting, or already in the vault's file): a tick undone
     * was never done. Throws when the vault cannot be written; the events are then already gone from the phone.
     */
    fun forget(context: Context, repo: VaultRepository, vault: Uri, ids: Set<String>) = synchronized(this) {
        if (ids.isEmpty()) return@synchronized
        synchronized(lock) {
            val all = waiting(context)
            val left = all.filter { it.id !in ids }
            if (left.size != all.size) file(context).writeText(left.joinToString("") { Behavior.line(it) + "\n" })
        }
        val text = repo.readPath(vault, Behavior.PATH) ?: return@synchronized
        if (Behavior.parse(text).none { it.id in ids }) return@synchronized
        repo.editPath(vault, Behavior.PATH) { old -> Behavior.drop(old, ids) }
        repo.readPath(vault, Behavior.PATH)?.let { runCatching { File(context.filesDir, CACHE).writeText(it) } }
    }

    /** The old preference log and the questions answered "no", as events; the same ids on every phone. */
    private fun fromPrefs(context: Context): List<BehaviorEvent> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = LocalDateTime.now()
        return prefs.getStringSet(KEY_OLD_LOG, emptySet()).orEmpty().mapNotNull { Behavior.legacyDone(it, SRC) } +
            prefs.getStringSet(KEY_DECLINED, emptySet()).orEmpty().map { Behavior.declined(it, now, SRC) }
    }

    /**
     * Writes the waiting events (and, the first time, the old preference log) into the vault, then returns every
     * event there is: the file's and any still waiting. Throws when the vault cannot be read; the events then keep
     * waiting. Offline on Drive, the write is kept with the other offline edits and sent with them.
     */
    fun sync(context: Context, repo: VaultRepository, vault: Uri): List<BehaviorEvent> = synchronized(this) {
        val text = repo.readPath(vault, Behavior.PATH)
        val known = Behavior.parse(text).mapTo(HashSet()) { it.id }
        val sending = (waiting(context) + fromPrefs(context)).filter { it.id !in known }.distinctBy { it.id }
        var now = text
        if (sending.isNotEmpty()) {
            val today = LocalDate.now()
            repo.editPath(vault, Behavior.PATH) { old -> Behavior.merge(old, sending, today) }
            now = repo.readPath(vault, Behavior.PATH)
            val sent = sending.mapTo(HashSet()) { it.id }
            synchronized(lock) {
                val left = waiting(context).filter { it.id !in sent }
                runCatching { file(context).writeText(left.joinToString("") { Behavior.line(it) + "\n" }) }
            }
        }
        // The old log now lives in the file; it no longer grows here, nor travels in the settings file.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_OLD_LOG).apply()
        now?.let { runCatching { File(context.filesDir, CACHE).writeText(it) } }
        (Behavior.parse(now) + waiting(context)).distinctBy { it.id }
    }
}
