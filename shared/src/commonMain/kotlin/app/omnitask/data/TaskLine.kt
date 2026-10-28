package app.omnitask.data

import app.omnitask.model.Priority
import app.omnitask.model.Recurrence
import app.omnitask.model.ReminderOn
import app.omnitask.model.Status
import app.omnitask.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

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
    private val TIME = Regex("""(⏰|🎯)$VS\s*(\d{1,2}):(\d{2})""")
    /**
     * What a tag may hold, the way Obsidian reads one: it stops at a space, another #, a bracket or a punctuation
     * mark, so "(PR #79)" in a sentence gives no tag "79)".
     */
    const val TAG_CHAR = """[^\s#()\[\]{}<>,;:!?"'`]"""

    /** A tag (`#Siriraj/flow`) after a space or at the start; one of digits only (`#79`) is text, as in Obsidian. */
    const val TAG_PATTERN = """(?<!\S)#(?!\d+(?!$TAG_CHAR))($TAG_CHAR+)"""

    private val TAG = Regex(TAG_PATTERN)
    private val ANY_DATE = Regex("""(?:➕|🛫|⏳|📅|✅|❌)$VS\s*$DATE""")
    private val ID = Regex("""🆔$VS\s*([A-Za-z0-9_-]+)""")
    private val LINK_ALIAS = Regex("""(?<!!)\[\[([^\]|]+)\|([^\]]+)\]\]""")
    private val LINK = Regex("""(?<!!)\[\[([^\]|]+)\]\]""")
    private val DEPENDS = Regex("""⛔$VS\s*([A-Za-z0-9_,-]+)""")

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
        val title = listOf(ANY_DATE, RECURRENCE, PRIORITY, TIME, ID, DEPENDS, TAG)
            .fold(body) { acc, re -> acc.replace(re, " ") }
            // Obsidian shows [[note|alias]] as "alias" and [[folder/note]] as "note"; so does the title.
            .replace(LINK_ALIAS) { it.groupValues[2] }
            .replace(LINK) { it.groupValues[1].substringAfterLast('/').substringBefore('#') }
            .replace(Regex("""\s+"""), " ")
            .trim()

        val reminder = TIME.find(body)
        val reminderTime = reminder?.let {
            runCatching { LocalTime.of(it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
        }

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
            reminderTime = reminderTime,
            id = ID.find(body)?.groupValues?.get(1),
            dependsOn = DEPENDS.find(body)?.groupValues?.get(1)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            reminderOn = reminder?.let { if (it.groupValues[1] == "🎯") ReminderOn.SCHEDULED else ReminderOn.DUE },
            // Read with the names the owner uses now ([ProjectNames]): an old project name counts as the new one.
            tags = TAG.findAll(body).map { app.omnitask.model.ProjectNames.canonical(it.value.removePrefix("#")) }.toList(),
        )
    }

    fun setDate(raw: String, field: DateField, value: LocalDate?): String = editBody(raw) { body ->
        val existing = dateRegex(field).find(body)
        when {
            // The date is the end of the match (MatchGroup.range is JVM only).
            existing != null && value != null ->
                body.replaceRange(existing.range.last + 1 - existing.groupValues[1].length, existing.range.last + 1, value.toString())
            existing != null -> remove(body, existing.range)
            value != null -> insertByRank(body, "${field.emoji} $value", field.rank)
            else -> body
        }
    }

    /** Gives the task a new [title], keeping its tags, dates and the rest of the line. Unchanged when the old title is not found as written. */
    fun setTitle(raw: String, title: String): String {
        val old = parse(raw)?.title?.takeIf { it.isNotEmpty() } ?: return raw
        val clean = title.trim().replace(Regex("""\s+"""), " ")
        if (clean.isEmpty()) return raw
        return editBody(raw) { body ->
            val i = body.indexOf(old)
            if (i < 0) body else body.substring(0, i) + clean + body.substring(i + old.length)
        }
    }

    fun setPriority(raw: String, priority: Priority): String = editBody(raw) { body ->
        val cleared = PRIORITY.find(body)?.let { remove(body, it.range) } ?: body
        priority.emoji?.let { insertByRank(cleared, it, PRIORITY_RANK) } ?: cleared
    }

    /** Spaces (and marks a tag cannot hold, see [TAG_CHAR]) become dashes and a leading # is dropped, so `ร้าน ยา` is written as `#ร้าน-ยา`. */
    fun normalizeTag(input: String): String =
        input.trim().removePrefix("#").trim().replace(Regex("""(?:\s|[#()\[\]{}<>,;:!?"'`])+"""), "-")

    /**
     * Adds `#tag` among the line's tags, keeping TaskForge's order: plain tags, then any
     * `#remind-at-… ⏰ HH:mm` pair, then priority and the rest.
     */
    fun addTag(raw: String, tag: String): String = editBody(raw) { addTagToBody(it, normalizeTag(tag)) }

    private fun addTagToBody(body: String, name: String): String {
        if (name.isEmpty() || TAG.findAll(body).any { it.value.equals("#$name", ignoreCase = true) }) return body
        val token = "#$name"
        val tags = TAG.findAll(body).toList()
        val reminder = tags.firstOrNull { it.value.startsWith("#remind-at-") }
        val pos = reminder?.range?.first
            ?: tags.lastOrNull()?.let { it.range.last + 1 }
            ?: listOf(TIME, PRIORITY, RECURRENCE, ANY_DATE).mapNotNull { it.find(body)?.range?.first }.minOrNull()
            ?: return if (body.isBlank()) token else "${body.trimEnd()} $token"
        val before = body.substring(0, pos).trimEnd()
        val after = body.substring(pos).trimStart()
        return listOf(before, token, after).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Moves a task to another project or branch: its project tag [old] becomes [new] where it stands, so it stays the
     * first tag the line has; with no [old] tag on the line, [new] is added as [addTag] does.
     */
    fun retag(raw: String, old: String?, new: String): String {
        val name = normalizeTag(new)
        if (name.isEmpty() || old == name) return raw
        return editBody(raw) { body0 ->
            // The new tag may already sit further along the line; it is kept once, where the old one stood.
            val body = TAG.findAll(body0).firstOrNull { it.value.equals("#$name", ignoreCase = true) }?.let { remove(body0, it.range) } ?: body0
            val at = old?.let { o -> TAG.findAll(body).firstOrNull { it.value == "#$o" || app.omnitask.model.ProjectNames.canonical(it.value.removePrefix("#")) == o } }
            if (at == null) addTagToBody(body, name) else body.replaceRange(at.range, "#$name")
        }
    }

    /**
     * Gives a task with no project its project or branch: [tag] goes in front of the line's tags, so it is the first
     * one read, and the tags the task had stay after it (`#อ่าน` on `#omni` becomes `#omni #อ่าน`). A line with no
     * tags gets it where [addTag] would put it.
     */
    fun homeTag(raw: String, tag: String): String {
        val name = normalizeTag(tag)
        if (name.isEmpty()) return raw
        return editBody(raw) { body0 ->
            val body = TAG.findAll(body0).firstOrNull { it.value.equals("#$name", ignoreCase = true) }?.let { remove(body0, it.range) } ?: body0
            val first = TAG.findAll(body).firstOrNull { !it.value.startsWith("#remind-at-") }
            if (first == null) addTagToBody(body, name) else body.substring(0, first.range.first) + "#$name " + body.substring(first.range.first)
        }
    }

    fun removeTag(raw: String, tag: String): String = editBody(raw) { body ->
        TAG.findAll(body).firstOrNull { it.value == "#$tag" || app.omnitask.model.ProjectNames.canonical(it.value.removePrefix("#")) == tag }?.let { remove(body, it.range) } ?: body
    }

    /** Sets the checkbox character, and the ✅ date when the task becomes or stops being done. */
    fun setStatus(raw: String, status: Status, today: LocalDate): String {
        val m = LINE.matchEntire(raw) ?: return raw
        val mark = when (status) {
            Status.TODO -> " "
            Status.IN_PROGRESS -> "/"
            Status.DONE -> "x"
            Status.CANCELLED -> "-"
        }
        val marked = m.groupValues[1] + mark + m.groupValues[3] + m.groupValues[4]
        return setDate(marked, DateField.DONE, if (status == Status.DONE) today else null)
    }

    fun setDone(raw: String, done: Boolean, today: LocalDate): String {
        val m = LINE.matchEntire(raw) ?: return raw
        val marked = m.groupValues[1] + (if (done) "x" else " ") + m.groupValues[3] + m.groupValues[4]
        return setDate(marked, DateField.DONE, if (done) today else null)
    }

    /** Sets or clears the task's 🆔, which other tasks name in ⛔ to wait for it. */
    fun setId(raw: String, id: String?): String = editBody(raw) { body ->
        val cleared = ID.find(body)?.let { remove(body, it.range) } ?: body
        id?.let { "${cleared.trimEnd()} 🆔 $it" } ?: cleared
    }

    /** Sets the ids this task waits for (⛔ a,b), or clears them when the list is empty. */
    fun setDependsOn(raw: String, ids: List<String>): String = editBody(raw) { body ->
        val cleared = DEPENDS.find(body)?.let { remove(body, it.range) } ?: body
        if (ids.isEmpty()) cleared else "${cleared.trimEnd()} ⛔ ${ids.joinToString(",")}"
    }

    /** Sets or clears the 🔁 rule, keeping TaskForge's token order. */
    fun setRecurrence(raw: String, rule: String?): String = editBody(raw) { body ->
        val cleared = RECURRENCE.find(body)?.let { remove(body, it.range) } ?: body
        rule?.trim()?.ifEmpty { null }?.let { insertByRank(cleared, "🔁 $it", RECURRENCE_RANK) } ?: cleared
    }

    private val REMIND_TAG = Regex("""(?<!\S)#remind-at-(?:due|scheduled)(?!\S)""")

    /**
     * Sets or clears the reminder, written the TaskForge way right after the plain tags:
     * `#remind-at-due ⏰ 09:00` (on the due date) or `#remind-at-scheduled 🎯 09:00` (on the scheduled date).
     */
    fun setReminder(raw: String, time: LocalTime?, on: ReminderOn): String = editBody(raw) { body ->
        var b = body
        TIME.find(b)?.let { b = remove(b, it.range) }
        REMIND_TAG.find(b)?.let { b = remove(b, it.range) }
        if (time == null) return@editBody b
        val token = if (on == ReminderOn.SCHEDULED) "#remind-at-scheduled 🎯 " else "#remind-at-due ⏰ "
        val full = token + hhmm(time.hour, time.minute)
        val pos = TAG.findAll(b).lastOrNull()?.let { it.range.last + 1 }
            ?: listOf(PRIORITY, RECURRENCE, ANY_DATE).mapNotNull { it.find(b)?.range?.first }.minOrNull()
            ?: return@editBody if (b.isBlank()) full else "${b.trimEnd()} $full"
        val before = b.substring(0, pos).trimEnd()
        val after = b.substring(pos).trimStart()
        listOf(before, full, after).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * The next copy of a repeating task, as the Tasks plugin makes it: unticked, its dates moved by
     * the rule (from the old due, scheduled or start date, or from today for "when done"), and a fresh ➕.
     * Null when the rule cannot be read or the task has no date to repeat from.
     */
    fun nextOccurrence(raw: String, today: LocalDate): String? = advance(raw, today, freshCreated = true, pastToday = false)

    /**
     * The same line moved on to its next occurrence: still unticked, every date shifted by the rule, ➕ kept.
     * This is how the app completes a repeating task, so no copy of the line piles up in the file.
     * With [pastToday] (ticking [app.omnitask.model.Task.todayCopy]) it moves to the first round after today instead,
     * so the rounds missed on the way are skipped.
     */
    fun advanceRecurring(raw: String, today: LocalDate, pastToday: Boolean = false): String? =
        advance(raw, today, freshCreated = false, pastToday = pastToday)

    private fun advance(raw: String, today: LocalDate, freshCreated: Boolean, pastToday: Boolean): String? {
        val t = parse(raw) ?: return null
        val rule = Recurrence.parse(t.recurrence) ?: return null
        val ref = t.due ?: t.scheduled ?: t.start ?: return null
        var target = Recurrence.next(rule, if (rule.whenDone) today else ref)
        if (pastToday && !rule.whenDone) {
            var walked = 0
            while (target <= today && walked++ < 5000) target = Recurrence.next(rule, target)
        }
        val shift = ChronoUnit.DAYS.between(ref, target)
        var line = setDone(raw, false, today)
        listOf(DateField.START to t.start, DateField.SCHEDULED to t.scheduled, DateField.DUE to t.due).forEach { (field, date) ->
            if (date != null) line = setDate(line, field, date.plusDays(shift))
        }
        if (freshCreated && t.created != null) line = setDate(line, DateField.CREATED, today)
        return line
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
