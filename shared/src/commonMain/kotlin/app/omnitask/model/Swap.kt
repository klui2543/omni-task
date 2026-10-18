package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.data.VaultText
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Turning a task into a calendar event and back, without losing what the other side cannot hold. A task that becomes
 * an event leaves its whole block (priority, tags, subtasks, stuck notes, created day) in `omni-swap.json`, under the
 * event's id; an event that becomes a task leaves its end, place, alerts and calendar there, under the task's `🆔`.
 * Going back picks them up again, so nothing has to be typed twice however often it goes back and forth.
 */
object Swap {

    const val PATH = "${VaultText.OMNI_DIR}/omni-swap.json"

    /** A task's lines while it is an event: [file] is where it came from, [title] and [start] find it on another phone. */
    @Serializable
    data class KeptTask(val lines: List<String>, val file: String = "", val title: String = "", val start: String = "")

    /** An event's own details while it is a task. [time] is its start when timed; [days] is how many days an all-day one spans after the first. */
    @Serializable
    data class KeptEvent(
        val time: String? = null,
        val minutes: Long = 60,
        val allDay: Boolean = false,
        val days: Long = 0,
        val place: String = "",
        val reminders: List<Int> = emptyList(),
        val calendarId: Long = 0,
    )

    @Serializable
    data class Store(val events: Map<String, KeptTask> = emptyMap(), val tasks: Map<String, KeptEvent> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }

    fun read(text: String?): Store = text?.let { runCatching { json.decodeFromString<Store>(it) }.getOrNull() } ?: Store()

    fun write(store: Store): String = json.encodeToString(store)

    /** The task kept for an event: by the event's id, else (the id differs on another phone) by its title and start. */
    fun keptTask(store: Store, eventId: Long, title: String, start: LocalDateTime): Pair<String, KeptTask>? =
        store.events[eventId.toString()]?.let { eventId.toString() to it }
            ?: store.events.entries.firstOrNull { it.value.title == title && it.value.start == start.toString() }?.toPair()

    /** A short new `🆔`, the kind the Tasks plugin writes. */
    fun newId(random: Random = Random): String = (1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)] }.joinToString("")

    /** The day a task's event goes on: the day its reminder is for, else its due day, else its scheduled day. */
    fun eventDay(task: Task, today: LocalDate): LocalDate =
        (if (task.reminderOn == ReminderOn.SCHEDULED) task.scheduled ?: task.due else task.due ?: task.scheduled) ?: today

    /** The task's own description, for the event's: its notes without the stuck log, which stays with the task. */
    fun eventNotes(task: Task): String = task.notes.filterNot { StuckLog.isNote(it) }.joinToString("\n")

    /** What a task keeps while it is an event, in words for the sheet: priority, tags, subtasks, repeat, stuck notes, created day. */
    fun keptOfTask(task: Task, subtasks: Int, shortDate: (LocalDate) -> String): List<String> = buildList {
        if (task.priority != Priority.NONE) add(tr("ความสำคัญ ", "Priority ") + task.priority.label)
        task.tags.filterNot { it.startsWith("remind-at-") }.forEach { add("#$it") }
        if (subtasks > 0) add(tr("งานย่อย $subtasks", "$subtasks subtasks"))
        task.recurrence?.let { add(Recurrence.describe(it)) }
        StuckLog.entries(task).size.takeIf { it > 0 }?.let { add(tr("บันทึกค้าง $it", "$it stuck notes")) }
        task.created?.let { add(tr("วันสร้าง ", "Created ") + shortDate(it)) }
    }

    /**
     * The lines a task comes back as from its event: its kept block, with the event's day on the date it had (its due
     * day, or its scheduled day when it had only that) and the event's start as its reminder. An all-day event leaves
     * the old reminder as it was. The task keeps (or gets) a `🆔`, returned too, so the event's details can wait under it.
     */
    fun restoredLines(kept: KeptTask, day: LocalDate, time: LocalTime?, random: Random = Random): Pair<List<String>, String> {
        val lines = kept.lines.toMutableList()
        val task = TaskLine.parse(lines[0])
        val scheduled = task != null && task.due == null && task.scheduled != null
        var first = TaskLine.setDate(lines[0], if (scheduled) DateField.SCHEDULED else DateField.DUE, day)
        if (time != null) first = TaskLine.setReminder(first, time, task?.reminderOn ?: if (scheduled) ReminderOn.SCHEDULED else ReminderOn.DUE)
        val id = task?.id ?: newId(random)
        if (task?.id == null) first = TaskLine.setId(first, id)
        lines[0] = first
        return lines to id
    }

    /** A new task made from an event, in TaskForge token order with a `🆔` at the end; the event's description becomes its notes. */
    fun newTaskLines(title: String, day: LocalDate, time: LocalTime?, scheduled: Boolean, notes: String, today: LocalDate, id: String): List<String> {
        val field = if (scheduled) DateField.SCHEDULED else DateField.DUE
        var line = "- [ ] ${title.trim()}"
        if (time != null) line = TaskLine.setReminder(line, time, if (scheduled) ReminderOn.SCHEDULED else ReminderOn.DUE)
        line = TaskLine.setDate(line, DateField.CREATED, today)
        line = TaskLine.setDate(line, field, day)
        line = TaskLine.setId(line, id)
        return listOf(line) + notes.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { "    - $it" }
    }

    /** What an event keeps while it is a task, in words for the sheet. */
    fun keptOfEvent(kept: KeptEvent, calendarName: String?): List<String> = buildList {
        if (kept.allDay) {
            if (kept.days > 0) add(tr("ทั้งวัน ${kept.days + 1} วัน", "All day, ${kept.days + 1} days"))
        } else {
            val start = kept.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val end = start?.plusMinutes(kept.minutes)
            add((end?.let { tr("จบ ", "Ends ") + hhmm(it.hour, it.minute) + " " } ?: "") + "(" + length(kept.minutes) + ")")
        }
        if (kept.place.isNotBlank()) add(kept.place)
        kept.reminders.forEach { add(alert(it)) }
        calendarName?.let { add(it) }
    }

    fun length(m: Long): String = when {
        m < 60 -> tr("$m นาที", "$m min")
        m % 60 == 0L -> tr("${m / 60} ชม.", "${m / 60} h")
        else -> tr("${m / 60} ชม. ${m % 60} นาที", "${m / 60} h ${m % 60} min")
    }

    fun alert(m: Int): String = when {
        m == 0 -> tr("เตือนตอนเริ่ม", "Alert at start")
        m % 1440 == 0 -> tr("เตือนก่อน ${m / 1440} วัน", "${m / 1440} days before")
        m % 60 == 0 -> tr("เตือนก่อน ${m / 60} ชม.", "${m / 60} h before")
        else -> tr("เตือนก่อน $m นาที", "$m min before")
    }
}
