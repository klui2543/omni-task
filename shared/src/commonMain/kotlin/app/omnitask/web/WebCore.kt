package app.omnitask.web

import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.model.QuickAdd
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.bucket
import app.omnitask.model.Projects
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
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
    )

    @Serializable
    data class EditResult(val ok: Boolean, val text: String? = null, val error: String? = null, val message: String? = null)

    fun dto(t: Task, today: LocalDate) = TaskDto(
        key = t.key, raw = t.raw, title = t.title, status = t.status.name, open = t.isOpen,
        priority = t.priority.name, created = t.created?.toString(), scheduled = t.scheduled?.toString(),
        due = t.due?.toString(), done = t.done?.toString(), recurrence = t.recurrence,
        reminder = t.reminderTime?.let { app.omnitask.time.hhmm(it.hour, it.minute) },
        tags = t.tags, project = Projects.projectOf(t), description = t.description,
        bucket = t.bucket(today).name, lineIndex = t.lineIndex, parent = t.parent,
    )

    /** The tasks of one note as JSON. [fileKey] identifies the note in each task's key. */
    fun loadTasks(fileKey: String, path: String, text: String, today: String): String {
        val day = LocalDate.parse(today)
        return json.encodeToString(VaultText.parseFile(fileKey, path, text).map { dto(it, day) })
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

    /** Ticks or unticks a task; a repeating task moves on to its next date instead of being ticked. */
    fun toggle(text: String, raw: String, lineIndex: Int, today: String): String {
        val day = LocalDate.parse(today)
        val task = find(text, raw, lineIndex) ?: return fail("conflict")
        val edited = VaultText.edit(text, task) { lines, i ->
            if (task.recurrence != null && task.isOpen) {
                lines[i] = TaskLine.advanceRecurring(task.raw, day) ?: throw RuleUnreadable()
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

    private class RuleUnreadable : Exception()

    /** Runs [block] and turns the unreadable-repeat case into an error result. */
    fun guarded(block: () -> String): String = try {
        block()
    } catch (e: RuleUnreadable) {
        fail("rule")
    }
}
