package app.omnitask.data

import app.omnitask.model.Priority
import app.omnitask.model.Status
import app.omnitask.model.Task
import java.time.LocalDate

/**
 * Reads and edits one checkbox line in the Tasks-emoji format that TaskForge and Task Genius write:
 * `- [ ] title #tag ⏫ 🔁 every day ➕ 2026-09-29 ⏳ 2026-09-30 📅 2026-10-01 ✅ 2026-10-01`
 *
 * Edits touch only the token that changes; everything else on the line is kept as it stands.
 */
object TaskLine {

    enum class DateField(val emoji: String, internal val rank: Int) {
        CREATED("➕", 3),
        START("🛫", 4),
        SCHEDULED("⏳", 5),
        DUE("📅", 6),
        DONE("✅", 7),
    }

    private const val VS = "️?"
    private const val DATE = """\d{4}-\d{2}-\d{2}"""
    private const val PRIORITY_RANK = 1
    private const val RECURRENCE_RANK = 2

    private val LINE = Regex("""^(\s*(?:[-*+]|\d+[.)])\s+\[)(.)(\]\s+)(.*)$""")
    private val PRIORITY = Regex("""(🔺|⏫|🔼|🔽|⏬)$VS""")
    private val RECURRENCE = Regex("""🔁$VS\s*([^➕🛫⏳📅✅❌🔺⏫🔼🔽⏬⏰🎯#]*)""")
    private val TIME = Regex("""(?:⏰|🎯)$VS\s*\d{1,2}:\d{2}""")
    private val TAG = Regex("""(?<!\S)#[^\s#]+""")
    private val ANY_DATE = Regex("""(?:➕|🛫|⏳|📅|✅|❌)$VS\s*$DATE""")

    private fun dateRegex(field: DateField) = Regex("""${field.emoji}$VS\s*($DATE)""")

    fun isTask(raw: String) = LINE.matches(raw)

    fun parse(raw: String): Task? {
        val m = LINE.matchEntire(raw) ?: return null
        val body = m.groupValues[4]
        val status = when (m.groupValues[2]) {
            " " -> Status.TODO
            "x", "X" -> Status.DONE
            "/", ">" -> Status.IN_PROGRESS
            "-" -> Status.CANCELLED
            else -> Status.TODO
        }
        fun date(field: DateField) =
            dateRegex(field).find(body)?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

        val priority = PRIORITY.find(body)?.let { p -> Priority.entries.first { it.emoji == p.groupValues[1] } }
            ?: Priority.NONE
        val title = listOf(ANY_DATE, RECURRENCE, PRIORITY, TIME, TAG)
            .fold(body) { acc, re -> acc.replace(re, " ") }
            .replace(Regex("""\s+"""), " ")
            .trim()

        return Task(
            raw = raw,
            title = title,
            status = status,
            priority = priority,
            created = date(DateField.CREATED),
            start = date(DateField.START),
            scheduled = date(DateField.SCHEDULED),
            due = date(DateField.DUE),
            done = date(DateField.DONE),
            recurrence = RECURRENCE.find(body)?.groupValues?.get(1)?.trim()?.ifEmpty { null },
            tags = TAG.findAll(body).map { it.value.removePrefix("#") }.toList(),
        )
    }

    fun setDate(raw: String, field: DateField, value: LocalDate?): String = editBody(raw) { body ->
        val existing = dateRegex(field).find(body)
        when {
            existing != null && value != null ->
                body.replaceRange(existing.groups[1]!!.range, value.toString())
            existing != null -> remove(body, existing.range)
            value != null -> insertByRank(body, "${field.emoji} $value", field.rank)
            else -> body
        }
    }

    fun setPriority(raw: String, priority: Priority): String = editBody(raw) { body ->
        val cleared = PRIORITY.find(body)?.let { remove(body, it.range) } ?: body
        priority.emoji?.let { insertByRank(cleared, it, PRIORITY_RANK) } ?: cleared
    }

    /** Spaces become dashes and a leading # is dropped, so `ร้าน ยา` is written as `#ร้าน-ยา`. */
    fun normalizeTag(input: String): String =
        input.trim().removePrefix("#").trim().replace(Regex("""[\s#]+"""), "-")

    /**
     * Adds `#tag` among the line's tags, keeping TaskForge's order: plain tags, then any
     * `#remind-at-… ⏰ HH:mm` pair, then priority and the rest.
     */
    fun addTag(raw: String, tag: String): String = editBody(raw) { body ->
        val name = normalizeTag(tag)
        if (name.isEmpty() || TAG.findAll(body).any { it.value.equals("#$name", ignoreCase = true) }) return@editBody body
        val token = "#$name"
        val tags = TAG.findAll(body).toList()
        val reminder = tags.firstOrNull { it.value.startsWith("#remind-at-") }
        val pos = reminder?.range?.first
            ?: tags.lastOrNull()?.let { it.range.last + 1 }
            ?: listOf(TIME, PRIORITY, RECURRENCE, ANY_DATE).mapNotNull { it.find(body)?.range?.first }.minOrNull()
            ?: return@editBody if (body.isBlank()) token else "${body.trimEnd()} $token"
        val before = body.substring(0, pos).trimEnd()
        val after = body.substring(pos).trimStart()
        listOf(before, token, after).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun removeTag(raw: String, tag: String): String = editBody(raw) { body ->
        TAG.findAll(body).firstOrNull { it.value == "#$tag" }?.let { remove(body, it.range) } ?: body
    }

    fun setDone(raw: String, done: Boolean, today: LocalDate): String {
        val m = LINE.matchEntire(raw) ?: return raw
        val marked = m.groupValues[1] + (if (done) "x" else " ") + m.groupValues[3] + m.groupValues[4]
        return setDate(marked, DateField.DONE, if (done) today else null)
    }

    private fun editBody(raw: String, edit: (String) -> String): String {
        val m = LINE.matchEntire(raw) ?: return raw
        return m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + edit(m.groupValues[4])
    }

    private fun remove(body: String, range: IntRange): String {
        val before = body.substring(0, range.first).trimEnd()
        val after = body.substring(range.last + 1).trimStart()
        return if (before.isEmpty() || after.isEmpty()) before + after else "$before $after"
    }

    /** Keeps the token order TaskForge writes: priority, 🔁, ➕, 🛫, ⏳, 📅, ✅. */
    private fun insertByRank(body: String, token: String, rank: Int): String {
        val markers = buildList {
            add(PRIORITY_RANK to PRIORITY)
            add(RECURRENCE_RANK to RECURRENCE)
            DateField.entries.forEach { add(it.rank to dateRegex(it)) }
        }
        val pos = markers
            .filter { it.first > rank }
            .mapNotNull { it.second.find(body)?.range?.first }
            .minOrNull()
            ?: return if (body.isBlank()) token else "${body.trimEnd()} $token"
        val before = body.substring(0, pos).trimEnd()
        val after = body.substring(pos)
        return if (before.isEmpty()) "$token $after" else "$before $token $after"
    }
}
