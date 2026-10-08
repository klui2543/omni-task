package app.omnitask.data

import app.omnitask.model.Task
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * Finished tasks leave the live TaskForge note for one archive note beside it, each with its whole block
 * (description, links, subtasks), under a heading for the month it was moved. The app does not read the
 * archive, so the Done filter only holds recent work; Obsidian still finds everything there.
 */
object Archive {

    const val FILE = "📁 Folder/หลังบ้าน/TaskForge/TaskForge Archive.md"

    private val CANCELLED_ON = Regex("""❌️?\s*(\d{4}-\d{2}-\d{2})""")

    /** The day a finished task was closed: its ✅ date, or the ❌ date of a cancelled one. */
    fun closedOn(t: Task): LocalDate? =
        t.done ?: CANCELLED_ON.find(t.raw)?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

    /** A block's lines moved to the left margin, so a task archived from under a heading reads the same. */
    private fun dedent(block: List<String>): List<String> {
        val indent = block.firstOrNull()?.takeWhile { it == ' ' || it == '\t' }.orEmpty()
        return block.map { it.removePrefix(indent) }
    }

    /** Adds blocks at the end of the archive text, under this month's heading (added when the last one differs). */
    fun append(archive: String?, blocks: List<List<String>>, today: LocalDate): String {
        val existing = archive?.takeIf { it.isNotBlank() }
        val separator = existing?.let { VaultText.separatorOf(it) } ?: "\n"
        val lines = existing?.trimEnd('\r', '\n')?.split(separator)?.toMutableList() ?: mutableListOf("# TaskForge Archive")
        val heading = "## ${today.toString().take(7)}"
        if (lines.lastOrNull { it.startsWith("## ") } != heading) {
            lines += ""
            lines += heading
            lines += ""
        }
        blocks.forEach { lines += dedent(it) }
        return lines.joinToString(separator) + separator
    }

    /** Takes a block added by [append] back out of the archive text (the last copy of it); null when it is gone. */
    fun remove(archive: String, block: List<String>): String? {
        val separator = VaultText.separatorOf(archive)
        val lines = archive.split(separator).toMutableList()
        val wanted = dedent(block)
        val at = (lines.size - wanted.size downTo 0).firstOrNull { i -> lines.subList(i, i + wanted.size) == wanted } ?: return null
        lines.subList(at, at + wanted.size).clear()
        return lines.joinToString(separator)
    }

    /** Whether the task at [index] and every checkbox under it are done or cancelled. */
    fun blockFinished(lines: List<String>, index: Int): Boolean =
        (index until VaultText.blockEnd(lines, index)).all { i -> TaskLine.parse(lines[i])?.isOpen != true }

    /** The live note after a sweep, the blocks taken out (in file order), and their titles. */
    class Sweep(val text: String, val blocks: List<List<String>>, val titles: List<String>)

    /**
     * Takes out of the live note every finished top-level task closed on or before [cutoff], unless [keep]
     * says it stays (project work stays done in place). A done copy of a repeating task whose open line is
     * still in the note goes whatever its age or project, since the open line carries the task on; when that
     * open line has no details of its own, the newest copy's details move onto it first.
     * Null when nothing is to move.
     */
    fun sweep(text: String, cutoff: LocalDate, keep: (Task) -> Boolean): Sweep? {
        val separator = VaultText.separatorOf(text)
        val lines = text.split(separator)
        val tops = VaultText.parseFile("", "", text).filter { it.parent == null }
        val openRepeats = tops.filter { it.isOpen && it.recurrence != null }
        fun twinOf(t: Task) = if (t.recurrence == null) null else openRepeats.firstOrNull { it.title == t.title && it.recurrence == t.recurrence }

        val chosen = tops.filter { t ->
            !t.isOpen && blockFinished(lines, t.lineIndex) &&
                (twinOf(t) != null || (closedOn(t)?.let { it <= cutoff } == true && !keep(t)))
        }
        if (chosen.isEmpty()) return null

        // Details the open repeating line is missing, taken from its newest finished copy.
        val carry = HashMap<Int, List<String>>()
        chosen.groupBy { twinOf(it) }.forEach { (twin, copies) ->
            if (twin == null || twin.notes.isNotEmpty()) return@forEach
            val newest = copies.filter { it.notes.isNotEmpty() }.maxByOrNull { closedOn(it) ?: LocalDate.EARLIEST } ?: return@forEach
            val from = newest.raw.takeWhile { it == ' ' || it == '\t' }
            val to = twin.raw.takeWhile { it == ' ' || it == '\t' }
            carry[twin.lineIndex] = lines.subList(newest.lineIndex + 1, newest.lineIndex + 1 + newest.notes.size).map { to + it.removePrefix(from) }
        }

        val taken = BooleanArray(lines.size)
        val blocks = chosen.map { t ->
            val end = VaultText.blockEnd(lines, t.lineIndex)
            for (i in t.lineIndex until end) taken[i] = true
            lines.subList(t.lineIndex, end).toList()
        }
        val out = ArrayList<String>(lines.size)
        lines.forEachIndexed { i, line ->
            if (!taken[i]) {
                out += line
                carry[i]?.let { out += it }
            }
        }
        return Sweep(out.joinToString(separator), blocks, chosen.map { it.title })
    }
}
