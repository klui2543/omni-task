package app.omnitask.web

import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.model.DateBucket
import app.omnitask.model.GroupBy
import app.omnitask.model.Priority
import app.omnitask.model.Projects
import app.omnitask.model.Quadrant
import app.omnitask.model.QuickAdd
import app.omnitask.model.SortBy
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.TaskKind
import app.omnitask.model.TaskQuery
import app.omnitask.model.UrgentRule
import app.omnitask.model.quadrant
import app.omnitask.model.tr
import app.omnitask.time.minusDays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The Views page for the web: Kanban, Matrix, Gantt and the calendars, over the same filters as the task list.
 * It answers with task keys only (the page already has the tasks); which task sits in which column, quadrant or
 * bar comes from the same rules the Android Views screen uses ([TaskQuery], [Quadrant], [UrgentRule]).
 */
object WebViews {

    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    @Serializable
    data class In(
        /** Today as `yyyy-MM-dd` on the device. */
        val today: String,
        /** The list's filters; grouping and sorting count too (Kanban cards follow the sort). */
        val query: WebCore.QueryDto = WebCore.QueryDto(),
        /** "Hide done": finished and cancelled work stays out of Matrix, Gantt and the calendars. */
        val hideDone: Boolean = true,
        /** A [UrgentRule] name. */
        val urgent: String = "THIS_WEEK",
        /** First day of the Gantt range; defaults to yesterday. */
        val ganttFirst: String? = null,
    )

    @Serializable
    data class Column(val status: String, val keys: List<String>)

    @Serializable
    data class Quad(val id: String, val label: String, val urgent: Boolean, val important: Boolean, val keys: List<String>)

    /** A task on the calendar; [at] is when its reminder fires (`yyyy-MM-ddTHH:mm`), when it has a time. */
    @Serializable
    data class CalTask(val key: String, val at: String? = null)

    @Serializable
    data class Span(val key: String, val start: String, val end: String)

    @Serializable
    data class GanttGroup(val project: String, val none: Boolean, val spans: List<Span>)

    @Serializable
    data class Out(
        /** Tasks the Matrix, Gantt and calendars draw from, for "show n tasks" on the filter panel. */
        val shown: Int,
        val kanban: List<Column>,
        val matrix: List<Quad>,
        /** What to say when a task is dragged across the urgent line. */
        val sideways: String,
        val calendar: List<CalTask>,
        val gantt: List<GanttGroup>,
        val progress: Map<String, WebCore.ProgressDto>,
    )

    /** What Android says when a task is dropped sideways in the Matrix. */
    val SIDEWAYS get() = tr(
        "ด่วน/ไม่ด่วนมาจากวันครบกำหนด ลากได้แค่ขึ้นลง (สำคัญ ↔ ไม่สำคัญ)",
        "Urgency comes from the due date, so drag only up or down (important ↔ not important)",
    )

    internal fun queryOf(q: WebCore.QueryDto, hideDone: Boolean): TaskQuery {
        fun <E : Enum<E>> pick(names: List<String>, all: Array<E>) = names.mapNotNull { n -> all.firstOrNull { it.name == n } }.toSet()
        val sorts = q.sorts.mapNotNull { s -> SortBy.entries.firstOrNull { it.name == s.by }?.let { it to s.ascending } }
        val main = sorts.firstOrNull() ?: (SortBy.DUE to true)
        return TaskQuery(
            statuses = pick(q.statuses, Status.entries.toTypedArray()),
            priorities = pick(q.priorities, Priority.entries.toTypedArray()),
            tags = q.tags.toSet(),
            buckets = pick(q.buckets, DateBucket.entries.toTypedArray()),
            kinds = pick(q.kinds, TaskKind.entries.toTypedArray()),
            hideDone = hideDone,
            text = q.text,
            groupBy = GroupBy.entries.firstOrNull { it.name == q.groupBy } ?: GroupBy.DATE,
            sortBy = main.first,
            ascending = main.second,
            thenBy = sorts.drop(1),
        )
    }

    fun build(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = json.decodeFromString<In>(stateJson)
        val today = LocalDate.parse(s.today)
        val rule = UrgentRule.entries.firstOrNull { it.name == s.urgent } ?: UrgentRule.THIS_WEEK
        val all = WebCore.withoutParked(VaultText.parseFile(fileKey, path, text), s.query.branches)
        val q = queryOf(s.query, s.hideDone)

        // Every view uses the list's filters without the status filter; Kanban shows all statuses as columns,
        // so the status filter only narrows the other views, and only when it differs from the default.
        val pool = all.filter { q.copy(statuses = emptySet()).matches(it, today) }
        val narrowed = (if (q.statuses.isEmpty() || q.statuses == TaskQuery.DEFAULT.statuses) pool else pool.filter { it.status in q.statuses })
            .filter { !q.hideDone || it.isOpen }

        // Kanban: cards follow the sort; Done shows the last week's finished work only; cancelled work has no column.
        val ordered = q.copy(statuses = emptySet(), groupBy = GroupBy.NONE).run(pool, today).flatMap { it.tasks }
        val weekAgo = today.minusDays(7)
        val kanban = listOf(Status.TODO, Status.IN_PROGRESS, Status.DONE).map { status ->
            Column(status.name, ordered.filter { t -> t.status == status && (status != Status.DONE || t.done?.let { it > weekAgo } == true) }.map { it.key })
        }

        // Matrix: open tasks by quadrant, soonest first, those without a date last.
        val byQuadrant = narrowed.filter { it.isOpen }.groupBy { it.quadrant(today, rule) }
        val matrix = Quadrant.entries.map { quad ->
            val tasks = byQuadrant[quad].orEmpty().sortedWith(compareBy<Task, LocalDate?>(nullsLast()) { it.due ?: it.scheduled })
            Quad(quad.name, quad.label, quad.urgent, quad.important, tasks.map { it.key })
        }

        // Calendar: a dated subtask belongs on the calendar although subtasks live inside their parent elsewhere.
        val narrowedKeys = narrowed.map { it.key }.toSet()
        val calendar = narrowed + all.filter { t ->
            t.parent != null && t.key !in narrowedKeys && (t.due != null || t.scheduled != null) && (!q.hideDone || t.isOpen)
        }

        // Gantt: dated tasks that are open, or were done within the range; a bar runs from start (or scheduled) to due.
        val first = s.ganttFirst?.let { LocalDate.parse(it) } ?: today.minusDays(1)
        val noProject = tr("ไม่มีโปรเจกต์", "No project")
        val groups = narrowed
            .filter { t -> t.status != Status.CANCELLED && (t.isOpen || t.done?.let { it >= first } == true) }
            .mapNotNull { t ->
                val a = t.start ?: t.scheduled ?: t.due ?: return@mapNotNull null
                val b = t.due ?: t.scheduled ?: a
                (Projects.projectOf(t) ?: noProject) to Span(t.key, a.toString(), (if (b < a) a else b).toString())
            }
            .groupBy({ it.first }, { it.second })
            .toList()
            .sortedWith(compareBy<Pair<String, List<Span>>> { it.first == noProject }.thenBy { it.first })
            .map { (name, spans) -> GanttGroup(name, name == noProject, spans.sortedBy { it.start }) }

        val progress = all.filter { it.parent != null && it.status != Status.CANCELLED }.groupBy { it.parent!! }
            .mapValues { (_, kids) -> WebCore.ProgressDto(kids.count { it.status == Status.DONE }, kids.size) }

        return json.encodeToString(
            Out(
                shown = narrowed.size,
                kanban = kanban,
                matrix = matrix,
                sideways = SIDEWAYS,
                calendar = calendar.map { CalTask(it.key, it.reminderAt?.toString()) },
                gantt = groups,
                progress = progress,
            ),
        )
    }

    /** Quick add from a folded Kanban column: the sentence is read as usual, and the new task starts in [status]. */
    fun addTask(text: String, sentence: String, today: String, status: String): String {
        val day = LocalDate.parse(today)
        val draft = QuickAdd.parse(sentence, day)
        if (draft.title.isBlank()) return json.encodeToString(WebCore.EditResult(false, error = "empty"))
        val line = TaskLine.setStatus(draft.line(day), Status.valueOf(status), day)
        return json.encodeToString(WebCore.EditResult(true, VaultText.appendLine(text, line)))
    }

    /** Dragging between quadrants changes importance only: priority High going in, Medium going out. */
    fun priorityFor(quadrant: Quadrant): Priority = if (quadrant.important) Priority.HIGH else Priority.MEDIUM
}
