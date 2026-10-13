package app.omnitask.web

import app.omnitask.model.Focus
import app.omnitask.model.Status
import app.omnitask.model.StuckLog
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.time.ChronoUnit
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The stuck log for the web: why a long-open task is still open, as Android's StuckUi shows it ([StuckLog]). The
 * page gets each task's path (made, every note, every finished subtask), the bars a week, the tasks to ask about and
 * the log of every task with notes; writing a note is the edit op `stuck` ([WebCore.editTask]).
 */
object WebStuck {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** One point on a task's path: its day, [step] (START, NOTE or DONE) and the words Android shows beside the dot. */
    @Serializable
    data class EventOut(val day: String, val step: String, val text: String)

    /** One task as the edit panel, a Focus row and the dialogs show it. */
    @Serializable
    data class TaskOut(
        val key: String,
        val title: String,
        /** Late, open 21 days or more, or already explained: the path goes up front. */
        val matters: Boolean,
        /** How many notes, and the day of the latest. */
        val notes: Int,
        val last: String?,
        /** The whole path, oldest first, with when the task was made. */
        val events: List<EventOut>,
        /** "ขยับล่าสุด 12 วันก่อน", or that nothing is noted yet. */
        val status: String,
        /** A bar a week, oldest first: NONE, NOTE or DONE. */
        val weeks: List<String>,
        /** Subtasks done and in all. */
        val done: Int,
        val total: Int,
        /** Days since ➕, or null. */
        val age: Long?,
    )

    /** A task the weekly review asks about: open a long while with nothing written. */
    @Serializable
    data class AskOut(val key: String, val title: String, val age: Long?)

    /** One task in the log, its path without the start (the most recently explained first). */
    @Serializable
    data class LogOut(val key: String, val title: String, val open: Boolean, val age: Long?, val events: List<EventOut>)

    @Serializable
    data class Out(val toAsk: List<AskOut>, val log: List<LogOut>)

    /** The quick picks for the reason, in Android's order, as a JSON list. */
    fun reasons(): String = json.encodeToString(StuckLog.Reason.entries.map { it.label })

    private fun event(e: StuckLog.Event) = EventOut(
        e.day.toString(), e.step.name,
        when (e.step) {
            StuckLog.Step.START -> tr("เริ่มงาน", "Started")
            StuckLog.Step.DONE -> tr("งานย่อยเสร็จ: ", "Subtask done: ") + e.text
            StuckLog.Step.NOTE -> e.text.ifEmpty { tr("(ไม่ได้เขียนเหตุผล)", "(no reason written)") }
        },
    )

    /** "ขยับล่าสุด 12 วันก่อน", or that nothing is noted yet: what Focus says under a stuck task (Android's stuckStatus). */
    fun status(task: Task, subtasks: List<Task>, today: LocalDate): String {
        val last = StuckLog.lastMove(task, subtasks) ?: return tr("ยังไม่ได้จด", "nothing noted")
        val days = ChronoUnit.DAYS.between(last, today)
        return if (days <= 0L) tr("ขยับล่าสุดวันนี้", "moved today") else tr("ขยับล่าสุด $days วันก่อน", "moved $days days ago")
    }

    /** The tasks right under [task], in note order, as Android's subtasksOf. */
    internal fun subtasksOf(task: Task, tasks: List<Task>) = tasks.filter { it.parent == task.key }.sortedBy { it.lineIndex }

    private fun taskOut(t: Task, tasks: List<Task>, today: LocalDate): TaskOut {
        val subs = subtasksOf(t, tasks)
        val entries = StuckLog.entries(t)
        return TaskOut(
            t.key, t.title, StuckLog.matters(t, today), entries.size, entries.lastOrNull()?.day?.toString(),
            StuckLog.events(t, subs).map { event(it) }, status(t, subs, today),
            StuckLog.weeks(t, subs, today).map { it.name },
            subs.count { it.status == Status.DONE }, subs.size, Focus.ageDays(t, today),
        )
    }

    private fun read(fileKey: String, path: String, text: String, today: LocalDate, branchesJson: String): List<Task> =
        WebCore.withoutParked(WebNotes.tasks(fileKey, path, text, today), json.decodeFromString<List<String>>(branchesJson))

    /** The tasks the weekly review asks about (the one waiting longest first) and the log, as JSON. */
    fun view(fileKey: String, path: String, text: String, today: String, branchesJson: String): String {
        val day = LocalDate.parse(today)
        val tasks = read(fileKey, path, text, day, branchesJson)
        return json.encodeToString(
            Out(
                toAsk = StuckLog.toAsk(tasks, day).map { AskOut(it.key, it.title, Focus.ageDays(it, day)) },
                log = StuckLog.byTask(tasks).map { (t, events) ->
                    LogOut(t.key, t.title, t.isOpen, Focus.ageDays(t, day), events.filter { it.step != StuckLog.Step.START }.map { event(it) })
                },
            ),
        )
    }

    /** One task's path and bars by its key, as JSON, or `null` when it is not among the tasks. */
    fun task(fileKey: String, path: String, text: String, key: String, today: String, branchesJson: String): String {
        val day = LocalDate.parse(today)
        val tasks = read(fileKey, path, text, day, branchesJson)
        val t = tasks.firstOrNull { it.key == key } ?: return "null"
        return json.encodeToString(taskOut(t, tasks, day))
    }
}
