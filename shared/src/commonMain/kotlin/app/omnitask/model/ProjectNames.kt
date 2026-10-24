package app.omnitask.model

import kotlinx.datetime.LocalDate

/**
 * The project names the owner chose, kept in the vault as `Claude/ชื่อโปรเจกต์.md`: a table of each project's name
 * and the other names it was written with, and the moves the owner made in the app (`## ประวัติการย้าย`). The owner
 * groups projects and branches freely; the app writes every move here, so the secretary writes the new names, and a
 * task still tagged with an old name (`#omni`, `#rxshift`) is read as the name it has now.
 */
object ProjectNames {
    const val PATH = "📁 Folder/หลังบ้าน/Claude/ชื่อโปรเจกต์.md"
    private const val H_MOVES = "ประวัติการย้าย"

    /** One move: everything tagged [from] (and under it) is now under [to]. */
    data class Move(val from: String, val to: String)

    /** How tags are read: the other names of each project, then the owner's moves in the order they were made. */
    data class Names(val aliases: Map<String, String> = emptyMap(), val moves: List<Move> = emptyList()) {
        /**
         * The name [tag] has now: an other name becomes the project's name (case does not matter, the rest of the
         * branch stays), then each move is applied in turn, so moving back and forth ends where the owner left it.
         */
        fun canonical(tag: String): String {
            if (tag.isEmpty() || (aliases.isEmpty() && moves.isEmpty())) return tag
            var t = tag
            val parts = t.split('/')
            for (k in parts.size downTo 1) {
                val hit = aliases[parts.take(k).joinToString("/").lowercase()] ?: continue
                t = (listOf(hit) + parts.drop(k)).joinToString("/")
                break
            }
            moves.forEach { m ->
                when {
                    t.equals(m.from, ignoreCase = true) -> t = m.to
                    t.startsWith(m.from + "/", ignoreCase = true) -> t = m.to + t.substring(m.from.length)
                }
            }
            return t
        }
    }

    /** What task lines are read with; set on each load (empty: tags as written). */
    @kotlin.concurrent.Volatile var current: Names = Names()

    fun canonical(tag: String): String = current.canonical(tag)

    /** The names in the note: each row's name and its other names that can be a tag (no spaces), and the moves. */
    fun parse(text: String?): Names {
        if (text.isNullOrBlank()) return Names()
        val aliases = mutableMapOf<String, String>()
        rows(text.lines()).forEach { (name, others) ->
            aliases.getOrPut(name.lowercase()) { name }
            others.filter { tagLike(it) }.forEach { aliases.getOrPut(it.lowercase()) { name } }
        }
        val moves = section(text.lines()).mapNotNull { MOVE.find(it) }.map { Move(it.groupValues[1], it.groupValues[2]) }
            .reversed() // written newest first, applied oldest first
        return Names(aliases, moves)
    }

    /**
     * The note after the owner moved [old] to [new] in the app: a row named [old] (or under it) takes the new name and
     * keeps [old] as an other name, an other name that is now part of the new place is dropped (it names that place
     * itself), a project not in the table gets a row, and the move is written on top of the history.
     */
    fun record(text: String?, old: String, new: String, today: LocalDate): String {
        val lines = (text ?: DEFAULT).replace("\r\n", "\n").lines().toMutableList()
        val prefixes = new.split('/').let { p -> (1..p.size).map { p.take(it).joinToString("/").lowercase() } }.toSet()
        var touched = false
        val start = lines.indexOfFirst { isRow(it) }
        if (start >= 0) {
            var i = start + 2 // the header, then the line of dashes
            while (i < lines.size && isRow(lines[i])) {
                val cells = cells(lines[i])
                if (cells.size >= 2) {
                    var name = cells[0]
                    val others = cells[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    var changed = false
                    if (name.equals(old, ignoreCase = true)) { name = new; changed = true }
                    else if (name.startsWith("$old/", ignoreCase = true)) { name = new + name.substring(old.length); changed = true }
                    if (changed) {
                        touched = true
                        if (others.none { it.equals(old, ignoreCase = true) }) others.add(0, old)
                    }
                    val kept = others.filterNot { (it.lowercase() in prefixes && !it.equals(old, ignoreCase = true)) || it.equals(name, ignoreCase = true) }
                    if (changed || kept.size != others.size) lines[i] = "| $name | ${kept.joinToString(", ")} |"
                }
                i++
            }
            if (!touched && '/' !in old) lines.add(i, "| $new | $old |")
        }
        val line = "- $today #$old → #$new (ย้ายในแอป)"
        val head = lines.indexOfFirst { it.trim() == "## $H_MOVES" }
        if (head >= 0) lines.add(head + 1, line)
        else {
            while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
            lines += listOf("", "## $H_MOVES", "Klui ย้ายหรือเปลี่ยนชื่อโปรเจกต์และกิ่งในแอป บรรทัดใหม่สุดอยู่บน แอปและเลขาใช้ชื่อตามบรรทัดเหล่านี้", line)
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun isRow(l: String) = l.trim().startsWith("|") && l.trim().endsWith("|")

    private fun cells(l: String) = l.trim().trim('|').split('|').map { it.trim() }

    /** The table's rows (name, other names), without its header and dashes. */
    private fun rows(lines: List<String>): List<Pair<String, List<String>>> {
        val start = lines.indexOfFirst { isRow(it) }
        if (start < 0) return emptyList()
        return lines.drop(start + 2).takeWhile { isRow(it) }.map { cells(it) }.filter { it.size >= 2 && it[0].isNotEmpty() }
            .map { c -> c[0].removePrefix("#") to c[1].split(',').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() } }
    }

    private fun section(lines: List<String>): List<String> {
        val head = lines.indexOfFirst { it.trim() == "## $H_MOVES" }
        if (head < 0) return emptyList()
        return lines.drop(head + 1).takeWhile { !it.startsWith("## ") }
    }

    private fun tagLike(s: String) = s.isNotEmpty() && s.none { it.isWhitespace() || it == '#' }

    private val MOVE = Regex("""#(\S+)\s*(?:→|->)\s*#(\S+)""")

    private val DEFAULT = """
        # ชื่อโปรเจกต์

        | ชื่อใน Omni | ชื่อเรียกอื่นที่ Klui หรือ Claude อาจใช้ |
        |---|---|
    """.trimIndent()
}
