package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlinx.datetime.LocalDate

/**
 * What the secretary asks the owner (owner, 2026-10-26): `📁 Folder/หลังบ้าน/Claude/เลขาถาม.md`, written whole by the
 * secretary at every round. Each question is a plain bullet (never a checkbox, so the file is not read for tasks) that
 * repeats an open task's line without its checkbox, with the file it lives in and why the secretary asks:
 *
 * ```
 * - ปรับระบบใหม่เป็นระบบ "ป้าย" #Siriraj/ตารางเวรเภสัช ⏫ ➕ 2026-10-04 📅 2026-10-14
 *     - ไฟล์: 📁 Folder/หลังบ้าน/Omni/Omni note.md
 *     - เหตุ: เลยกำหนดมา 12 วัน และเวอร์ชัน 5.39.5 ขึ้นระบบแล้ว
 * ```
 *
 * The secretary may add a due date it proposes, with the reason in `เหตุ:` (step 2, owner's pick of 2026-10-29):
 *
 * ```
 *     - วันส่ง: 2026-11-04
 * ```
 *
 * The today page shows the open ones in one line, and the owner answers each: done ticks the task, not done writes
 * why on a [StuckLog] line under it. A task answered today is not asked again until the next day; nothing is hushed or
 * put off beyond that (owner, 2026-10-26: "ไม่ควรให้เงียบหรือเลื่อน").
 */
object SecretaryAsk {
    const val PATH = "📁 Folder/หลังบ้าน/Claude/เลขาถาม.md"
    const val FILE = "ไฟล์:"
    const val WHY = "เหตุ:"
    const val DUE = "วันส่ง:"

    /**
     * One question: the task's title as its line reads, the file it lives in (when given), why the secretary asks, and
     * the due date it proposes (a `วันส่ง:` line that holds no date is left out).
     */
    data class Ask(val title: String, val file: String?, val why: String?, val due: LocalDate? = null)

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** A question with the open task it asks about. */
    data class Open(val ask: Ask, val task: Task)

    private val TOP = Regex("""^[-*]\s+(.*)$""")
    private val SUB = Regex("""^\s+[-*]\s+(.*)$""")
    private val CHECKBOX = Regex("""^\[.\]\s""")

    private fun norm(s: String) = s.trim().replace(Regex("""\s+"""), " ")

    /** The questions in the file's text, in its order; lines it cannot read are skipped. */
    fun parse(text: String?): List<Ask> {
        if (text.isNullOrBlank()) return emptyList()
        val asks = ArrayList<Ask>()
        var title: String? = null
        var file: String? = null
        var why: String? = null
        var due: LocalDate? = null
        fun flush() {
            title?.let { asks += Ask(it, file, why, due) }
            title = null; file = null; why = null; due = null
        }
        for (line in text.lines()) {
            val sub = SUB.matchEntire(line)
            if (sub != null) {
                if (title == null) continue
                val v = sub.groupValues[1].trim()
                when {
                    v.startsWith(FILE) -> file = v.removePrefix(FILE).trim().ifEmpty { null }
                    v.startsWith(WHY) -> why = v.removePrefix(WHY).trim().ifEmpty { null }
                    v.startsWith(DUE) -> due = DATE.find(v)?.let { runCatching { LocalDate.parse(it.value) }.getOrNull() }
                }
                continue
            }
            val top = TOP.matchEntire(line)
            if (top == null) { flush(); continue }
            flush()
            val body = top.groupValues[1]
            // A checkbox would be a task in its own right: the secretary never writes one here, so it is not a question.
            if (CHECKBOX.containsMatchIn(body)) continue
            // Read the bullet as the task line it repeats, so its title comes out the way the task's own does.
            val t = TaskLine.parse("- [ ] $body")?.title?.takeIf { it.isNotBlank() } ?: body
            title = norm(t)
        }
        flush()
        return asks
    }

    /**
     * The questions whose task is still open and was not answered today (a [StuckLog] line or a [ReviewLog] line dated
     * [today]), each with
     * that task, in the file's order. A question whose task cannot be found is left out: there is nothing to tick or
     * note on.
     */
    fun open(asks: List<Ask>, tasks: List<Task>, today: LocalDate): List<Open> {
        val seen = HashSet<String>()
        return asks.mapNotNull { a ->
            val task = tasks.firstOrNull { t ->
                t.isOpen && !t.todayCopy && norm(t.title) == a.title &&
                    (a.file == null || t.filePath.substringAfterLast('/') == a.file.substringAfterLast('/'))
            } ?: return@mapNotNull null
            if (StuckLog.entries(task).any { it.day == today }) return@mapNotNull null
            // Answered on the review card (Klui's pick ข, 2026-10-30): a card saved today writes `ทบทวน <today>`.
            if (ReviewLog.last(task) == today) return@mapNotNull null
            if (!seen.add(task.key)) return@mapNotNull null
            Open(a, task)
        }
    }
}
