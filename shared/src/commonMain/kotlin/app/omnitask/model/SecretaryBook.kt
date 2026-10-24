package app.omnitask.model

import app.omnitask.data.VaultText
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The secretary's book: `Claude log.md`, kept by the secretary (a Claude session on the cloud, see secretary.md).
 *
 * - The owner's own next step on a project is a `#kluido` task there, at most one per project, with `ที่:` (where to
 *   do it) and `เสร็จเมื่อ:` (when it counts as done) on lines under it.
 * - Claude's own work is plain bullets under `# งานของ Claude`, never checkboxes, so it never shows as the owner's task.
 * - What the secretary closed itself carries a `ปิดโดยเลขา:` line with the reason, so the owner sees why and can open it
 *   again by unticking it.
 *
 * [brief] turns it into the morning brief of the secretary chat: what waits on the owner today (three at most), what
 * the secretary closed since yesterday, and what Claude is ready to start.
 */
object SecretaryBook {
    const val LOG_PATH = "📁 Folder/หลังบ้าน/Claude/Claude log.md"
    const val WHERE = "ที่:"
    const val DONE_WHEN = "เสร็จเมื่อ:"
    const val CLOSED_BY = "ปิดโดยเลขา:"
    const val OWNER_TAG = "kluido"
    const val CLAUDE_HEAD = "งานของ Claude"
    const val READY = "พร้อมเริ่ม"

    /** The folder where the app keeps the files it hands to Claude ("บันทึกไว้ในคลัง"): copies, never a source of tasks. */
    const val SENT_DIR = "${VaultText.OMNI_DIR}/ส่งเลขา"
    const val SENT_KEEP_DAYS = 7
    const val MAX_WAITING = 3

    /** One thing waiting on the owner: its project tag in full, and where to do it and when it counts as done when the book says. */
    data class Item(val task: Task, val project: String?, val where: String?, val doneWhen: String?)

    /** A task the secretary closed, with its reason. */
    data class Closed(val task: Task, val reason: String)

    data class Brief(val waiting: List<Item>, val total: Int, val closed: List<Closed>, val claudeReady: List<String>) {
        val isEmpty get() = waiting.isEmpty() && closed.isEmpty() && claudeReady.isEmpty()
    }

    /** Whether [path] is the book itself. */
    fun isBook(path: String) = path == LOG_PATH || path.substringAfterLast('/') == "Claude log.md"

    /** Whether [path] is a file kept in [SENT_DIR]: it repeats the book's tasks, so it is never read for tasks. */
    fun isSentCopy(path: String) = path.substringBeforeLast('/', "").substringAfterLast('/') == SENT_DIR.substringAfterLast('/')

    private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")

    /** The kept files among [names] whose date (`เลขา 2026-10-24 0823.md`) is more than [SENT_KEEP_DAYS] days before [today]. */
    fun staleSent(names: List<String>, today: LocalDate): List<String> = names.filter { n ->
        val day = DAY.find(n)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        n.endsWith(".md") && day != null && day.daysUntil(today) > SENT_KEEP_DAYS
    }

    /** [text] with its checkboxes turned into plain bullets, so a copy of the book is not read as tasks again. */
    fun plain(text: String): String = text.lines().joinToString("\n") { l ->
        CHECKBOX.find(l)?.let { m ->
            val done = m.groupValues[2].lowercase() == "x"
            m.groupValues[1] + "- " + (if (done) "เสร็จแล้ว: " else "") + l.substring(m.range.last + 1)
        } ?: l
    }

    private val CHECKBOX = Regex("""^(\s*)[-*] \[([ xX/-])\] """)

    private fun line(t: Task, prefix: String) = t.notes.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()?.ifEmpty { null }

    /** The secretary's reason for closing [t], when it was the secretary that closed it. */
    fun closedReason(t: Task) = line(t, CLOSED_BY)

    private val FAR = LocalDate(9999, 12, 31)

    /**
     * The morning brief from [tasks] (every task the app read, the book's included) and the book's [log] text.
     * Waiting: the book's open `#kluido` tasks whose ⏳ or 🛫 day has come, and any other open task with a real 📅
     * within two days; real deadlines first, then the oldest. Closed: the book's tasks closed by the secretary today
     * or yesterday. Claude: the bullets under `# งานของ Claude` marked ready to start.
     */
    fun brief(tasks: List<Task>, log: String?, today: LocalDate): Brief {
        val book = tasks.filter { isBook(it.filePath) && it.parent == null && !it.todayCopy }
        val mine = book.filter { t ->
            t.isOpen && t.tags.any { it.equals(OWNER_TAG, ignoreCase = true) } && (t.scheduled ?: t.start).let { it == null || it <= today }
        }
        val deadlines = tasks.filter { t ->
            !isBook(t.filePath) && t.isOpen && t.parent == null && t.list == null && t.due.let { it != null && it <= today.plusDays(2) }
        }
        val all = (mine + deadlines).distinctBy { it.key }.sortedWith(compareBy({ it.due ?: FAR }, { it.created ?: FAR }))
        val waiting = all.take(MAX_WAITING).map { Item(it, Projects.tagOf(it)?.takeUnless { p -> p.equals(OWNER_TAG, ignoreCase = true) }, line(it, WHERE), line(it, DONE_WHEN)) }
        val closed = book.filter { t -> !t.isOpen && t.done.let { it != null && it.daysUntil(today) <= 1 } }
            .mapNotNull { t -> closedReason(t)?.let { Closed(t, it) } }
        return Brief(waiting, all.size, closed, claudeWork(log).filter { READY in it }.map(::readable))
    }

    /** The bullets under `# งานของ Claude` in [log], as written. */
    fun claudeWork(log: String?): List<String> {
        if (log == null) return emptyList()
        var inside = false
        return log.lines().mapNotNull { l ->
            if (l.startsWith("# ")) inside = l.removePrefix("# ").trim() == CLAUDE_HEAD
            if (inside && l.startsWith("- ")) l.removePrefix("- ").trim() else null
        }
    }

    /** One bullet of Claude's work as the brief shows it: without its markers and date. */
    fun readable(bullet: String): String = bullet
        .replace("($READY)", "")
        .replace(Regex("""\s*เพิ่ม \d{4}-\d{2}-\d{2}"""), "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}
