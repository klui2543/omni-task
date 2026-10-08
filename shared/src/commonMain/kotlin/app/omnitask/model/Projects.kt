package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * Projects until the dotpm format is settled: each task's first tag (other than reminder and #รอ tags)
 * names its project. Dependencies come from the Tasks plugin's `🆔` and `⛔` fields.
 */
object Projects {

    data class Project(
        val name: String,
        val tasks: List<Task>,
        val done: Int,
        val overdue: Int,
        val blocked: Set<Task>,
        val next: Task?,
    ) {
        val ratio get() = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    }

    /** Tags that never name a project: lists, their categories, and the owner's own kinds. Set on each load. */
    @kotlin.concurrent.Volatile var ignoredTags: Set<String> = emptySet()

    /** The task's project tag in full, branch included ("peddose/แอป/มือถือ"). */
    fun tagOf(t: Task): String? =
        t.tags.firstOrNull { !it.startsWith("remind-at-") && it != Focus.WAITING_TAG && !it.startsWith("${Focus.WAITING_TAG}/") && it !in ignoredTags }

    /** The project a task belongs to: its first tag up to the first slash, so branches stay in their project. */
    fun projectOf(t: Task): String? = tagOf(t)?.substringBefore('/')

    /** A task is blocked while any task it waits for (by id) is still open. */
    fun blocked(tasks: List<Task>): Set<Task> {
        val openIds = tasks.filter { it.isOpen && it.id != null }.mapNotNull { it.id }.toSet()
        return tasks.filter { it.isOpen && it.dependsOn.any { id -> id in openIds } }.toSet()
    }

    /** Title of a task this one waits for, for the lock label. */
    fun waitingOn(t: Task, tasks: List<Task>): String? {
        val byId = tasks.filter { it.id != null }.associateBy { it.id }
        return t.dependsOn.mapNotNull { byId[it] }.firstOrNull { it.isOpen }?.title
    }

    fun build(tasks: List<Task>, today: LocalDate): List<Project> {
        val blockedAll = blocked(tasks)
        return tasks.filter { projectOf(it) != null && it.status != Status.CANCELLED }
            .groupBy { projectOf(it)!! }
            .map { (name, list) ->
                Project(
                    name = name,
                    tasks = list,
                    done = list.count { it.status == Status.DONE },
                    overdue = list.count { t -> t.isOpen && t.due?.let { it < today } == true },
                    blocked = list.filter { it in blockedAll }.toSet(),
                    next = list.filter { it.isOpen && it !in blockedAll }.minWithOrNull(
                        compareBy<Task, LocalDate?>(nullsLast()) { it.due ?: it.scheduled }.thenBy { it.priority.ordinal },
                    ),
                )
            }
            // Live projects first: most open work, then by name.
            .sortedWith(compareByDescending<Project> { it.tasks.size - it.done }.thenBy { it.name })
    }
}
