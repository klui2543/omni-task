package app.omnitask.web

import app.omnitask.data.Archive
import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.model.DateBucket
import app.omnitask.model.GroupBy
import app.omnitask.model.Priority
import app.omnitask.model.QuickAdd
import app.omnitask.model.SortBy
import app.omnitask.model.TaskKind
import app.omnitask.model.TaskQuery
import app.omnitask.model.Recurrence
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.bucket
import app.omnitask.model.Projects
import app.omnitask.model.quadrant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.model.ReminderOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What the web app asks of the shared logic. Everything crosses as text (note text in, JSON or note text out),
 * so the browser needs no knowledge of Kotlin types. Edits return the whole new note text, or why nothing changed.
 */
object WebCore {

    private val json = Json { encodeDefaults = true }

    @Serializable
    data class TaskDto(
        val key: String,
        val raw: String,
        val title: String,
        val status: String,
        val open: Boolean,
        val priority: String,
        val created: String?,
        val scheduled: String?,
        val due: String?,
        val done: String?,
        val recurrence: String?,
        val reminder: String?,
        val tags: List<String>,
        val project: String?,
        val description: String,
        val bucket: String,
        val lineIndex: Int,
        val parent: String?,
        /** The description's first line, shown quietly under the title. */
        val preview: String? = null,
        /** The repeat rule in words, as on Android's task rows. */
        val repeatText: String? = null,
        val attachments: Int = 0,
        val links: Int = 0,
        val start: String? = null,
        /** Which date the reminder hangs on: DUE or SCHEDULED. */
        val reminderOn: String? = null,
        /** Linked note names, for the edit panel. */
        val linkNames: List<String> = emptyList(),
        val attachmentNames: List<String> = emptyList(),
        /** The vault path of the note the task is in. */
        val note: String? = null,
    )

    @Serializable
    data class EditResult(
        val ok: Boolean,
        val text: String? = null,
        val error: String? = null,
        val message: String? = null,
        /** For a delete or archive: where the block was and its lines, so it can be put back. */
        val cutIndex: Int? = null,
        val cutLines: List<String>? = null,
    )

    fun dto(t: Task, today: LocalDate) = TaskDto(
        key = t.key, raw = t.raw, title = t.title, status = t.status.name, open = t.isOpen,
        priority = t.priority.name, created = t.created?.toString(), scheduled = t.scheduled?.toString(),
        due = t.due?.toString(), done = t.done?.toString(), recurrence = t.recurrence,
        reminder = t.reminderTime?.let { app.omnitask.time.hhmm(it.hour, it.minute) },
        tags = t.tags, project = Projects.projectOf(t), description = t.description,
        bucket = t.bucket(today).name, lineIndex = t.lineIndex, parent = t.parent,
        preview = t.descriptionPreview, repeatText = t.recurrence?.let { Recurrence.describe(it) },
        attachments = t.attachments.size, links = t.links.size,
        start = t.start?.toString(), reminderOn = t.reminderOn?.name,
        linkNames = t.links, attachmentNames = t.attachments,
        note = t.filePath.ifEmpty { null },
    )

    /** The tasks of one note as JSON. [fileKey] identifies the note in each task's key. */
    fun loadTasks(fileKey: String, path: String, text: String, today: String): String {
        val day = LocalDate.parse(today)
        return json.encodeToString(WebNotes.tasks(fileKey, path, text).map { dto(it, day) })
    }

    /** The tasks without those in a parked branch, which every page but Projects leaves out (as on Android). */
    internal fun withoutParked(tasks: List<Task>, branches: List<String>): List<Task> {
        if (branches.isEmpty()) return tasks
        val states = app.omnitask.model.Branches.parse(branches.toSet())
        return tasks.filter { !app.omnitask.model.Branches.isParked(it, states) }
    }

    /** The task list's filters, grouping and sorting, named as in [TaskQuery]; empty sets mean no filter. */
    @Serializable
    data class QueryDto(
        val statuses: List<String> = listOf("TODO", "IN_PROGRESS"),
        val priorities: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val buckets: List<String> = emptyList(),
        val kinds: List<String> = emptyList(),
        val text: String = "",
        val groupBy: String = "DATE",
        /** Sort levels in order, each a [SortBy] name and whether it runs ascending. */
        val sorts: List<SortDto> = listOf(SortDto("DUE", true)),
        /** Branch states as Android saves them ("project\tpath\tSTATE"); tasks in a parked branch are left out. */
        val branches: List<String> = emptyList(),
    )

    @Serializable
    data class SortDto(val by: String, val ascending: Boolean)

    /** One group of the list: its heading, how the heading is coloured, and its tasks by key, in order. */
    @Serializable
    data class GroupDto(val label: String, val tone: String, val keys: List<String>)

    /** Done and total subtasks of a parent, for the "2/4" on its row. */
    @Serializable
    data class ProgressDto(val done: Int, val total: Int)

    @Serializable
    data class ListDto(val groups: List<GroupDto>, val progress: Map<String, ProgressDto>)

    /** The task list as Android shows it: [TaskQuery] run over the note, plus each parent's subtask progress. */
    fun list(fileKey: String, path: String, text: String, today: String, query: String): String {
        val day = LocalDate.parse(today)
        val q = json.decodeFromString<QueryDto>(query)
        val tasks = withoutParked(WebNotes.tasks(fileKey, path, text), q.branches)
        fun <E : Enum<E>> pick(names: List<String>, all: Array<E>) = names.mapNotNull { n -> all.firstOrNull { it.name == n } }.toSet()
        val sorts = q.sorts.mapNotNull { s -> SortBy.entries.firstOrNull { it.name == s.by }?.let { it to s.ascending } }
        val main = sorts.firstOrNull() ?: (SortBy.DUE to true)
        val query = TaskQuery(
            statuses = pick(q.statuses, Status.entries.toTypedArray()),
            priorities = pick(q.priorities, Priority.entries.toTypedArray()),
            tags = q.tags.toSet(),
            buckets = pick(q.buckets, DateBucket.entries.toTypedArray()),
            kinds = pick(q.kinds, TaskKind.entries.toTypedArray()),
            text = q.text,
            groupBy = GroupBy.entries.firstOrNull { it.name == q.groupBy } ?: GroupBy.DATE,
            sortBy = main.first,
            ascending = main.second,
            thenBy = sorts.drop(1),
        )
        val groups = query.run(tasks, day).map { g -> GroupDto(g.label, g.tone.name, g.tasks.map { it.key }) }
        val progress = tasks.filter { it.parent != null && it.status != Status.CANCELLED }.groupBy { it.parent!! }
            .mapValues { (_, kids) -> ProgressDto(kids.count { it.status == Status.DONE }, kids.size) }
        return json.encodeToString(ListDto(groups, progress))
    }

    private fun ok(text: String) = json.encodeToString(EditResult(true, text))
    private fun fail(error: String, message: String? = null) = json.encodeToString(EditResult(false, error = error, message = message))

    /** The task as it stands in [text] by its line, or null when someone else changed or moved it. */
    private fun find(text: String, raw: String, lineIndex: Int): Task? {
        val lines = text.split(VaultText.separatorOf(text))
        val index = if (lines.getOrNull(lineIndex) == raw) lineIndex else lines.indexOf(raw)
        if (index < 0) return null
        return VaultText.parseFile("", "", text).firstOrNull { it.lineIndex == index }
    }

    /**
     * Ticks or unticks a task; a repeating task moves on to its next date instead of being ticked. With
     * [withSubtasks], the task's open direct subtasks are ticked in the same write, as the Android app offers.
     */
    fun toggle(text: String, raw: String, lineIndex: Int, today: String, withSubtasks: Boolean = false): String {
        val day = LocalDate.parse(today)
        val task = find(text, raw, lineIndex) ?: return fail("conflict")
        val repeats = task.recurrence != null && task.isOpen
        // A repeating task moves on with its checklist opened again, so its subtasks are never ticked here.
        val children = if (withSubtasks && task.isOpen && !repeats) {
            VaultText.parseFile("", "", text).filter { it.parent == task.key && it.isOpen }
        } else {
            emptyList()
        }
        val edited = VaultText.edit(text, task) { lines, i ->
            // Ticking changes no line count, so the subtasks are still where they were parsed.
            children.forEach { lines[it.lineIndex] = TaskLine.setDone(it.raw, true, day) }
            if (repeats) {
                if (!VaultText.advanceRecurring(lines, i, day)) throw RuleUnreadable()
            } else {
                lines[i] = TaskLine.setDone(task.raw, task.isOpen, day)
            }
        }
        return ok(edited ?: return fail("conflict"))
    }

    fun setStatus(text: String, raw: String, lineIndex: Int, status: String, today: String): String {
        val task = find(text, raw, lineIndex) ?: return fail("conflict")
        val next = Status.valueOf(status)
        val edited = VaultText.edit(text, task) { lines, i -> lines[i] = TaskLine.setStatus(task.raw, next, LocalDate.parse(today)) }
        return ok(edited ?: return fail("conflict"))
    }

    /** Adds a task from a quick-add sentence to the end of the note. */
    fun addTask(text: String, sentence: String, today: String): String {
        val day = LocalDate.parse(today)
        val draft = QuickAdd.parse(sentence, day)
        if (draft.title.isBlank()) return fail("empty")
        return ok(VaultText.appendLine(text, draft.line(day)))
    }

    /** One change from the edit panel; [op] names it and the other fields carry its value. */
    @Serializable
    data class EditOp(
        val op: String,
        val field: String? = null,
        val value: String? = null,
        val on: String? = null,
        /** For a kind change: who is waiting (the "kind" op) and the tags of the owner's own kinds, which are swapped out too. */
        val who: String? = null,
        val custom: List<String> = emptyList(),
    )

    /**
     * Applies one edit-panel change to the task, as the Android edit sheet does: dates, priority, repeat,
     * reminder, tags, status, description and a new subtask (read like quick add).
     */
    fun editTask(text: String, raw: String, lineIndex: Int, today: String, opJson: String): String {
        val day = LocalDate.parse(today)
        val op = json.decodeFromString<EditOp>(opJson)
        val task = find(text, raw, lineIndex) ?: return fail("conflict")
        val value = op.value?.trim()?.ifEmpty { null }
        fun line(change: (String) -> String) = VaultText.edit(text, task) { lines, i -> lines[i] = change(lines[i]) }
        val edited = when (op.op) {
            "date" -> {
                val field = TaskLine.DateField.entries.first { it.name == op.field }
                line { TaskLine.setDate(it, field, value?.let { LocalDate.parse(it) }) }
            }
            "priority" -> line { TaskLine.setPriority(it, Priority.valueOf(op.value!!)) }
            "recurrence" -> {
                if (value != null && Recurrence.parse(value) == null) return fail("rule")
                line { TaskLine.setRecurrence(it, value) }
            }
            "reminder" -> {
                val on = if (op.on == "SCHEDULED") ReminderOn.SCHEDULED else ReminderOn.DUE
                line { TaskLine.setReminder(it, value?.let { LocalTime.parse(it) }, on) }
            }
            "addTag" -> {
                val tag = value ?: return fail("empty")
                line { TaskLine.addTag(it, tag) }
            }
            "removeTag" -> line { TaskLine.removeTag(it, op.value!!) }
            "status" -> line { TaskLine.setStatus(it, Status.valueOf(op.value!!), day) }
            // The kind tag (#รอ, #อนาคต, #สักวัน) is swapped for the new one, as Android's kind picker does.
            "kind" -> {
                val kind = TaskKind.valueOf(op.value!!)
                line { raw -> WebKinds.withKind(task, raw, kind, op.who, op.custom) }
            }
            // One of the owner's own kinds (see WebKinds): its tag replaces any other kind's.
            "customKind" -> {
                val tag = value ?: return fail("empty")
                line { raw -> WebKinds.withCustomKind(task, raw, tag, op.custom) }
            }
            "describe" -> VaultText.edit(text, task) { lines, i -> VaultText.describe(lines, i, op.value.orEmpty()) }
            "subtask" -> {
                val draft = QuickAdd.parse(value ?: return fail("empty"), day)
                if (draft.title.isBlank()) return fail("empty")
                VaultText.edit(text, task) { lines, i -> VaultText.insertSubtask(lines, i, draft.line(day)) }
            }
            // Moved between Matrix quadrants (see WebViews): importance changes, urgency comes from the dates.
            "quadrant" -> {
                val to = app.omnitask.model.Quadrant.valueOf(op.value!!)
                val rule = app.omnitask.model.UrgentRule.entries.firstOrNull { it.name == op.field } ?: app.omnitask.model.UrgentRule.THIS_WEEK
                val from = task.quadrant(day, rule)
                if (from == to) return ok(text)
                if (from.urgent != to.urgent) return fail("sideways", WebViews.SIDEWAYS)
                line { TaskLine.setPriority(it, WebViews.priorityFor(to)) }
            }
            else -> return fail("unknown")
        }
        return ok(edited ?: return fail("conflict"))
    }

    /** Takes the task out with its whole block (description, links, subtasks); the result says what was cut. */
    fun cut(text: String, raw: String, lineIndex: Int): String {
        val task = find(text, raw, lineIndex) ?: return fail("conflict")
        var index = -1
        var removed: List<String> = emptyList()
        val edited = VaultText.edit(text, task) { lines, i -> index = i; removed = VaultText.cutBlock(lines, i) } ?: return fail("conflict")
        return json.encodeToString(EditResult(true, edited, cutIndex = index, cutLines = removed))
    }

    /** Puts a block taken out by [cut] back where it was. */
    fun restore(text: String, index: Int, linesJson: String): String =
        ok(VaultText.restore(text, index, json.decodeFromString<List<String>>(linesJson)))

    /** The archive note with the block added under this month's heading; [archive] is empty when the note is new. */
    fun archiveAppend(archive: String, linesJson: String, today: String): String =
        Archive.append(archive.ifEmpty { null }, listOf(json.decodeFromString<List<String>>(linesJson)), LocalDate.parse(today))

    /** The archive note with an archived block taken back out; null when it is no longer there. */
    fun archiveRemove(archive: String, linesJson: String): String? =
        Archive.remove(archive, json.decodeFromString<List<String>>(linesJson))

    /** A repeat rule in words, or null when the Tasks plugin would not read it. */
    fun describeRule(rule: String): String? = Recurrence.parse(rule)?.let { Recurrence.describe(rule) }

    private class RuleUnreadable : Exception()

    /** Runs [block] and turns the unreadable-repeat case into an error result. */
    fun guarded(block: () -> String): String = try {
        block()
    } catch (e: RuleUnreadable) {
        fail("rule")
    }
}
