package app.omnitask.model

import kotlinx.datetime.LocalDate

/**
 * A project's roadmap on the Timeline (owner's pick ก, 2026-11-03, mock rx-roadmap-timeline v1: "timeline mode is born
 * for that purpose"). The secretary copies each milestone's start-by and deadline from the project's plan file into
 * `ไมล์สโตน.md`, one plain line per milestone under the project's `## ` heading:
 *
 *     - ช่วงแผน: #Siriraj/ตารางเวรเภสัช | เปิดโหมดเงาปฏิทิน | 2026-11-03 | 2026-11-30 | เดา | ขั้นถัดไป: Klui กดรันปุ่มเปิดโหมดเงา [ที่มา: แผน-RxShift.md]
 *
 * The Timeline shows them as bars above that project's tasks, only while the filter names the project's tag, and a
 * guessed date draws dashed. Nothing is written back; a line it cannot read is left out.
 */
object PlanSpans {
    const val KEY = "ช่วงแผน:"
    const val GUESS = "เดา"
    private const val NEXT = "ขั้นถัดไป:"

    data class Span(val project: String?, val tag: String, val title: String, val start: LocalDate, val end: LocalDate, val guess: Boolean, val next: String?)

    private val SOURCE = Regex("""\s*\[ที่มา:[^\]]*\]\s*$""")

    /** Every readable `ช่วงแผน:` line of [milestones] (the text of `ไมล์สโตน.md`), in the file's order. */
    fun parse(milestones: String?): List<Span> {
        if (milestones == null) return emptyList()
        var project: String? = null
        val out = ArrayList<Span>()
        for (raw in milestones.lines()) {
            val l = raw.trim()
            if (l.startsWith("## ")) { project = l.removePrefix("## ").trim(); continue }
            if (!l.startsWith("- $KEY") || "💬" in l) continue
            val parts = l.removePrefix("- $KEY").replace(SOURCE, "").split("|").map { it.trim() }
            if (parts.size < 4) continue
            val tag = parts[0].removePrefix("#").trim()
            val title = parts[1]
            val start = runCatching { LocalDate.parse(parts[2]) }.getOrNull() ?: continue
            val end = runCatching { LocalDate.parse(parts[3]) }.getOrNull() ?: continue
            if (tag.isEmpty() || title.isEmpty()) continue
            val rest = parts.drop(4)
            val guess = rest.any { it == GUESS }
            val next = rest.firstOrNull { it.startsWith(NEXT) }?.removePrefix(NEXT)?.trim()?.ifEmpty { null }
            out += Span(project, tag, title, minOf(start, end), maxOf(start, end), guess, next)
        }
        return out
    }

    /** The spans the Timeline shows for [filterTags]: those whose tag is a filter tag, inside one, or holds one. */
    fun shown(spans: List<Span>, filterTags: Set<String>): List<Span> = spans.filter { s ->
        filterTags.any { q ->
            s.tag.equals(q, ignoreCase = true) || s.tag.startsWith("$q/", ignoreCase = true) || q.startsWith("${s.tag}/", ignoreCase = true)
        }
    }
}
