package app.omnitask.data

import android.content.Context
import android.net.Uri
import app.omnitask.model.Task
import app.omnitask.quest.QuestAction
import app.omnitask.quest.QuestLog
import java.io.File

/**
 * The phone's side of Omni Quest's log ([QuestLog]), kept the same way as [BehaviorLog]: actions wait in a file of
 * the app's own, so the game works offline, until [sync] adds them to the vault's `omni-quest.json`.
 * Also reads the archive notes, which the task views leave out but the character grew from.
 */
object QuestStore {

    const val SRC = "android"

    private const val WAITING = "quest-waiting.jsonl"
    private const val CACHE = "quest-cache.jsonl"

    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, WAITING)

    fun record(context: Context, action: QuestAction) {
        synchronized(lock) { runCatching { file(context).appendText(QuestLog.line(action) + "\n") } }
    }

    fun waiting(context: Context): List<QuestAction> =
        synchronized(lock) { QuestLog.parse(runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()) }

    /** Every action as of the last [sync], with those still waiting; read without the network. */
    fun cached(context: Context): List<QuestAction> {
        val kept = QuestLog.parse(runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText() }.getOrNull())
        return (kept + waiting(context)).distinctBy { it.id }
    }

    /** Sends the waiting actions to the vault and returns every action there is. Throws when the vault cannot be read. */
    fun sync(context: Context, repo: VaultRepository, vault: Uri): List<QuestAction> = synchronized(this) {
        val text = repo.readPath(vault, QuestLog.PATH)
        val known = QuestLog.parse(text).mapTo(HashSet()) { it.id }
        val sending = waiting(context).filter { it.id !in known }
        var now = text
        if (sending.isNotEmpty()) {
            repo.editPath(vault, QuestLog.PATH) { old -> QuestLog.merge(old, sending) }
            now = repo.readPath(vault, QuestLog.PATH)
            val sent = sending.mapTo(HashSet()) { it.id }
            synchronized(lock) {
                val left = waiting(context).filter { it.id !in sent }
                runCatching { file(context).writeText(left.joinToString("") { QuestLog.line(it) + "\n" }) }
            }
        }
        now?.let { runCatching { File(context.filesDir, CACHE).writeText(it) } }
        (QuestLog.parse(now) + waiting(context)).distinctBy { it.id }
    }

    /** The finished tasks in the archive notes (the current one and the TaskForge one). */
    fun archived(repo: VaultRepository, vault: Uri): List<Task> =
        listOf(Archive.FILE, Archive.LEGACY_FILE).flatMap { path ->
            val text = runCatching { repo.readPath(vault, path) }.getOrNull() ?: return@flatMap emptyList()
            VaultText.parseFile("archive:$path", path, text)
        }
}
