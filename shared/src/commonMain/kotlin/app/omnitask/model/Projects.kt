package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * Projects until the dotpm format is settled: the owner makes a tag a project on the Projects page ([chosen]), and a
 * task's first tag of one names its project; other tags stay plain tags. Dependencies come from the Tasks plugin's
 * `🆔` and `⛔` fields.
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

    /** The lists' own tags: a folder under one (`bucketlist/เที่ยว`, owner's pick 2026-10-28) never names a project either. Set on each load. */
    @kotlin.concurrent.Volatile var listRoots: Set<String> = emptySet()

    /**
     * The projects the owner made on the Projects page; any other tag is a plain tag. Null until the owner's settings
     * hold a choice, when every task's first tag still names a project (see [seed]). Set on each load.
     */
    @kotlin.concurrent.Volatile var chosen: Set<String>? = null

    /** A tag that can name a project at all: not a reminder, a wait or a list, kind or category. */
    private fun plain(tag: String) =
        !tag.startsWith("remind-at-") && tag != Focus.WAITING_TAG && !tag.startsWith("${Focus.WAITING_TAG}/") && tag !in ignoredTags &&
            listRoots.none { tag.startsWith("$it/", ignoreCase = true) }

    /**
     * [tag] with its first part in the spelling of the chosen project it names in another case (`siriraj/flow` is
     * `Siriraj/flow` when Siriraj is a project), so one project never splits by case. Other tags stay as written.
     */
    fun spell(tag: String): String {
        val made = chosen ?: return tag
        val root = tag.substringBefore('/')
        if (root.isEmpty() || root in made) return tag
        val hit = made.firstOrNull { it.equals(root, ignoreCase = true) } ?: return tag
        return hit + tag.substring(root.length)
    }

    /**
     * The chosen projects with one spelling each: names that differ only by case are one project, kept in the
     * names table's spelling ([ProjectNames]) when it has one, else the one with capitals.
     */
    fun oneSpelling(tags: Set<String>): Set<String> =
        tags.groupBy { it.lowercase() }.values.map { same ->
            same.firstOrNull { ProjectNames.current.canonical(it) == it && it != it.lowercase() }
                ?: same.firstOrNull { it != it.lowercase() } ?: same.first()
        }.toSet()

    /** Whether [tag] could name a project at all (see [plain]); the folder rules build on it. */
    fun isPlain(tag: String) = plain(tag)

    /** The task's project tag in full, branch included ("peddose/แอป/มือถือ"): its first tag of a chosen project. */
    fun tagOf(t: Task): String? {
        val made = chosen
        return t.tags.firstOrNull { plain(it) && (made == null || it.substringBefore('/') in made) }
    }

    /** The project a task belongs to: its project tag up to the first slash, so branches stay in their project. */
    fun projectOf(t: Task): String? = tagOf(t)?.substringBefore('/')

    /** For a task with no project: its first tag that could name one, up to the first slash; null when it has none. */
    fun looseTag(t: Task): String? = t.tags.firstOrNull(::plain)?.substringBefore('/')?.takeIf { it.isNotEmpty() }

    /** The projects as every first tag named them before the owner chose: what [chosen] starts from. */
    fun seed(tasks: List<Task>): Set<String> =
        tasks.filter { it.status != Status.CANCELLED }.mapNotNull { t -> t.tags.firstOrNull(::plain)?.substringBefore('/') }.toSet()

    /**
     * Project names a task added in the last [days] days brings that are not projects yet (and were not taken off
     * by the owner, [declined]): the secretary writes a task with a new project's tag, and proposes it; it becomes a
     * project only when the owner takes it (owner, 2026-10-28; before, it was made one at once).
     */
    fun newOnes(tasks: List<Task>, made: Set<String>, declined: Set<String>, today: LocalDate, days: Int = 3): Set<String> =
        tasks.filter { it.isOpen && it.list == null && it.created?.let { c -> c >= today.plusDays(-days.toLong()) } == true }
            .mapNotNull { t -> t.tags.firstOrNull(::plain)?.substringBefore('/')?.takeIf { it.isNotEmpty() } }
            .filter { n -> made.none { it.equals(n, ignoreCase = true) } && declined.none { it.equals(n, ignoreCase = true) } }.toSet()

    /** Tags on [tasks] that are not projects and could be made one, with how many tasks carry each; most used first. */
    fun others(tasks: List<Task>): List<Pair<String, Int>> {
        val made = chosen ?: return emptyList()
        return tasks.filter { it.status != Status.CANCELLED }
            .flatMap { t -> t.tags.filter(::plain).map { it.substringBefore('/') }.filter { it.isNotEmpty() && it !in made }.distinct() }
            .groupingBy { it }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
    }

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
