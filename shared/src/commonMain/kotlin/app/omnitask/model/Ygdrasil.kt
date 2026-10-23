package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*
import kotlinx.datetime.isoDayNumber

/**
 * Project Ygdrasil: every project in one tree. The trunk holds each project as a world, each world its branches
 * (nested tags, as in [Branches]), each branch its tasks; tasks that wait for one another (`🆔` and `⛔`) are tied
 * together across the worlds. [grow] picks what shows through a [Filter]; [layout] places it once for both apps.
 */
object Ygdrasil {

    /**
     * What the tree shows: the task list's own filter ([query], the same chips and saved filters as the Tasks tab),
     * plus what only the tree has. Someday work (#สักวัน) shows by default, greyed, unless [someday] is off or the
     * query picks task types of its own.
     */
    data class Filter(
        val query: TaskQuery = TaskQuery.DEFAULT,
        /** Only tasks waiting for another task that is still open. */
        val blockedOnly: Boolean = false,
        val starredOnly: Boolean = false,
        /** Parked branches stay out unless this is on. */
        val parked: Boolean = false,
        /** Someday work, shown greyed with its label. */
        val someday: Boolean = true,
        /** The lines between tasks that wait for one another. */
        val links: Boolean = true,
    ) {
        /** A narrowing filter drops the branches and worlds left with nothing to show; the plain one keeps the whole shape. */
        val narrows get() = query.activeFilters > 0 || query.text.isNotBlank() || blockedOnly

        /** How many choices differ from the plain filter, for the filter button. */
        val changed get() = query.activeFilters + (if (query.text.isNotBlank()) 1 else 0) +
            listOf(blockedOnly, starredOnly, parked, !someday, !links).count { it }

        /** Only the filter part of the query, with the tree's own choices: one line for keeping it between visits. */
        fun encode(): String = listOf(
            "v=2", "q=" + query.encode().replace('\n', ' '), "text=" + query.text.replace('\n', ' '),
            "blocked=$blockedOnly", "starred=$starredOnly", "parked=$parked", "someday=$someday", "links=$links",
        ).joinToString("\n")

        companion object {
            fun decode(s: String?): Filter {
                if (s.isNullOrBlank()) return Filter()
                val m = s.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
                val flags = Filter(
                    blockedOnly = m["blocked"] == "true", starredOnly = m["starred"] == "true", parked = m["parked"] == "true",
                    someday = m["someday"] != "false", links = m["links"] != "false",
                )
                if (m["v"] == "2") {
                    return flags.copy(query = TaskQuery.decode(m["q"].orEmpty(), TaskQuery.DEFAULT).copy(text = m["text"].orEmpty()))
                }
                // The first filter (show, dates, priorities) becomes the nearest task-list filter.
                val statuses = when (m["show"]) {
                    "ALL" -> setOf(Status.TODO, Status.IN_PROGRESS, Status.DONE)
                    "DONE" -> setOf(Status.DONE)
                    else -> TaskQuery.DEFAULT.statuses
                }
                val buckets = when (m["dates"]) {
                    "OVERDUE" -> setOf(DateBucket.OVERDUE)
                    "TODAY" -> setOf(DateBucket.OVERDUE, DateBucket.TODAY)
                    "WEEK" -> setOf(DateBucket.OVERDUE, DateBucket.TODAY, DateBucket.THIS_WEEK)
                    "UNDATED" -> setOf(DateBucket.NO_DATE)
                    else -> emptySet()
                }
                val priorities = m["priorities"].orEmpty().split(',').mapNotNull { n -> Priority.entries.firstOrNull { it.name == n } }.toSet()
                return flags.copy(query = TaskQuery.DEFAULT.copy(statuses = statuses, buckets = buckets, priorities = priorities, text = m["text"].orEmpty()))
            }
        }
    }

    /** A branch as the tree shows it: its tasks that pass the filter, and the sub-branches still holding any. */
    data class Limb(val node: Branches.Node, val tasks: List<Task>, val children: List<Limb>) {
        /** Tasks shown here and under it. */
        val matched: Int get() = tasks.size + children.sumOf { it.matched }

        fun flatten(): List<Limb> = listOf(this) + children.flatMap { it.flatten() }
    }

    /** [waiting] waits for [blocker]; [across] when they sit in different projects. */
    data class Link(val blocker: Task, val waiting: Task, val across: Boolean)

    data class Tree(val worlds: List<Limb>, val links: List<Link>) {
        val matched get() = worlds.sumOf { it.matched }
    }

    /** Someday work, which the tree shows greyed. */
    fun isSomeday(t: Task) = TaskKind.of(t) == TaskKind.SOMEDAY

    fun passes(t: Task, f: Filter, today: LocalDate, blocked: Set<Task>): Boolean {
        val q = f.query
        // The task list leaves someday work out unless its type is picked; the tree keeps it, greyed, by default.
        val ok = if (f.someday && q.kinds.isEmpty() && q.lists.isEmpty() && t.list == null && isSomeday(t)) {
            q.copy(kinds = setOf(TaskKind.SOMEDAY)).matches(t, today)
        } else {
            q.matches(t, today)
        }
        if (!ok) return false
        if (f.blockedOnly && t !in blocked) return false
        return true
    }

    /**
     * The whole tree from every task ([tasks], parked ones included), the saved branch [states] and what the owner
     * chose: worlds come starred first, then in the owner's [order], then by most open work. Subtasks stay with
     * their parent, and the stand-in rounds of repeating tasks are left out (their own line is there already).
     */
    fun grow(
        tasks: List<Task>,
        states: Map<Pair<String, String>, Branches.State>,
        today: LocalDate,
        filter: Filter,
        starred: Set<String> = emptySet(),
        order: List<String> = emptyList(),
    ): Tree {
        val live = tasks.filter { !it.todayCopy && it.status != Status.CANCELLED }
        val blocked = Projects.blocked(live)
        val rank = order.withIndex().associate { it.value to it.index }
        val projects = Projects.build(live, today)
            .sortedWith(compareBy({ it.name !in starred }, { rank[it.name] ?: Int.MAX_VALUE }))
            .filter { !filter.starredOnly || it.name in starred }

        fun limb(n: Branches.Node): Limb? {
            if (!filter.parked && n.state == Branches.State.PARKED) return null
            val own = n.own.filter { it.parent == null && passes(it, filter, today, blocked) }
                .sortedWith(compareBy<Task>({ !it.isOpen }, { it.due ?: it.scheduled ?: LocalDate.LATEST }, { it.priority.ordinal }, { it.lineIndex }))
            val kids = n.children.mapNotNull { limb(it) }
            val l = Limb(n, own, kids)
            return if (filter.narrows && l.matched == 0) null else l
        }

        val worlds = projects.mapNotNull { p -> limb(Branches.tree(p.name, live, states)) }
        val shown = worlds.flatMap { w -> w.flatten().flatMap { it.tasks } }.toSet()
        val inWorld = worlds.flatMap { it.node.all }.filter { filter.parked || !Branches.isParked(it, states) }.toSet()
        val byId = live.filter { it.id != null }.associateBy { it.id }
        // Only waits still in force, and only those touching a task that shows; the other end may sit folded away.
        val links = if (!filter.links) emptyList() else live.filter { it.isOpen && it.dependsOn.isNotEmpty() }.flatMap { t ->
            t.dependsOn.mapNotNull { byId[it] }.filter { b ->
                b.isOpen && b !== t && (t in shown || b in shown) && t in inWorld && b in inWorld
            }.map { b -> Link(b, t, Projects.projectOf(b) != Projects.projectOf(t)) }
        }
        return Tree(worlds, links)
    }

    // ---- Worlds that open on their own ----

    /**
     * How far ahead a world's work opens it when the tree is shown: work past its 📅 date or its own ⏳ target always does; [TODAY],
     * [WEEK] (to Sunday) and [MONTH] (to the month's end) add work whose 📅 or ⏳ date falls in that reach.
     */
    enum class Reach(private val th: String, private val en: String) {
        LATE("เฉพาะที่เลยกำหนด", "Past due only"),
        TODAY("วันนี้", "Today"),
        WEEK("สัปดาห์นี้", "This week"),
        MONTH("เดือนนี้", "This month");

        val label get() = tr(th, en)

        /** The last day this reach takes in, or null for past-due work only. */
        fun end(today: LocalDate): LocalDate? = when (this) {
            LATE -> null
            TODAY -> today
            WEEK -> today.plusDays((7 - today.dayOfWeek.isoDayNumber).toLong())
            MONTH -> LocalDate(today.year, today.month, 1).plusMonths(1).minusDays(1)
        }

        companion object {
            fun of(name: String?): Reach = entries.firstOrNull { it.name == name } ?: WEEK
        }
    }

    /**
     * What a world tells while the tree is shown: its open work past the 📅 date ([late]), past the owner's own ⏳
     * target only ([targetLate], a world that opens too, with its own tag), dated within the reach ([within]), and the task to do
     * [next].
     */
    data class Urgency(val late: Int, val targetLate: Int, val within: Int, val next: Task?) {
        val opens get() = late > 0 || targetLate > 0 || within > 0
    }

    fun urgency(world: Limb, reach: Reach, today: LocalDate): Urgency {
        val open = world.flatten().flatMap { it.tasks }.filter { it.isOpen }
        val end = reach.end(today)
        val late = open.count { t -> t.due?.let { it < today } == true }
        val targetLate = open.count { t -> t.due?.let { it < today } != true && t.scheduled?.let { it < today } == true }
        val within = if (end == null) 0 else open.count { t ->
            t.due?.let { it < today } != true && listOfNotNull(t.due, t.scheduled).any { it in today..end }
        }
        val next = open.minWithOrNull(compareBy<Task>({ it.due ?: it.scheduled ?: LocalDate.LATEST }, { it.priority.ordinal }, { it.lineIndex }))
        return Urgency(late, targetLate, within, next)
    }

    /** Every world and what it tells, by its tag. */
    fun urgencies(tree: Tree, reach: Reach, today: LocalDate): Map<String, Urgency> =
        tree.worlds.associate { it.node.tag to urgency(it, reach, today) }

    /**
     * The folds when the tree is shown: every world folded but those with work in [reach]; the branches folded
     * inside a world ([kept], tags with a slash) stay as they were.
     */
    fun autoFolded(tree: Tree, reach: Reach, today: LocalDate, kept: Set<String> = emptySet()): Set<String> =
        kept.filter { '/' in it }.toSet() + tree.worlds.filter { !urgency(it, reach, today).opens }.map { it.node.tag }

    // ---- Tasks with no project yet ----

    /** Tasks with no project, gathered by the first tag they have ([tag] null: no tag at all). */
    data class Strays(val tag: String?, val tasks: List<Task>)

    /** How the tray orders a group: nearest date first, oldest added first, or by name. */
    enum class StraySort { DUE, OLD, AZ }

    fun straySorted(tasks: List<Task>, sort: StraySort): List<Task> = tasks.sortedWith(
        when (sort) {
            StraySort.DUE -> compareBy<Task>({ !it.isOpen }, { it.due ?: it.scheduled ?: LocalDate.LATEST }, { it.priority.ordinal }, { it.lineIndex })
            StraySort.OLD -> compareBy<Task>({ it.created ?: LocalDate.LATEST }, { it.lineIndex })
            StraySort.AZ -> compareBy<Task>({ it.title.lowercase() }, { it.lineIndex })
        },
    )

    /**
     * The tray under the tree: the tasks that passed [filter] but have no project, so the tree cannot show them.
     * Tasks with no tag come first, then each tag that is not a project, most tasks first. Subtasks stay with their
     * parent, list items and the stand-in rounds of repeating tasks are left out.
     */
    fun strays(tasks: List<Task>, today: LocalDate, filter: Filter, sort: StraySort = StraySort.DUE): List<Strays> {
        val live = tasks.filter { !it.todayCopy && it.status != Status.CANCELLED && it.parent == null && it.list == null }
        val blocked = Projects.blocked(live)
        return live.filter { Projects.projectOf(it) == null && passes(it, filter, today, blocked) }
            .groupBy { Projects.looseTag(it) }
            .map { (tag, list) -> Strays(tag, straySorted(list, sort)) }
            .sortedWith(compareBy<Strays>({ it.tag != null }, { -it.tasks.size }, { it.tag?.lowercase() }))
    }

    // ---- Layout ----

    /** Sizes on the tree, in dp (Android) or pixels (web), the same as the project mind map. */
    const val COL = 200f
    const val NODE_W = 156f
    const val NODE_H = 40f
    const val TRUNK_H = 52f
    const val TASK_W = 190f
    const val TASK_H = 32f
    const val TASKS_SHOWN = 6
    /** A folded world that tells its next task. */
    const val WIDE_W = NODE_W + COL
    const val WIDE_H = 58f

    enum class Kind { TRUNK, LIMB, TASK, MORE }

    /** Something placed on the tree; [y] is its centre line, [x] its left edge. */
    data class Spot(
        val kind: Kind,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val limb: Limb? = null,
        val task: Task? = null,
        /** The "and N more" count, or the tasks folded away under a folded limb. */
        val count: Int = 0,
        val folded: Boolean = false,
    )

    enum class EdgeKind { BRANCH, TASK, LINK, LINK_ACROSS }

    /** A curve from (x1, y1) to (x2, y2) with its two control points; [state] colours a branch. */
    data class Edge(
        val kind: EdgeKind,
        val x1: Float, val y1: Float,
        val cx1: Float, val cy1: Float,
        val cx2: Float, val cy2: Float,
        val x2: Float, val y2: Float,
        val state: Branches.State? = null,
        /** For a wait: the task waited for and the one waiting. */
        val blocker: Task? = null,
        val waiting: Task? = null,
    )

    data class Layout(val spots: List<Spot>, val edges: List<Edge>, val width: Float, val height: Float)

    /**
     * A tidy tree read left to right, as the mind map: the trunk, then each world in the next column, each limb in the
     * middle of the rows its tasks and sub-limbs take. Limbs whose tag is in [folded] keep their tasks and sub-limbs in.
     * A link whose task is folded away ends at the nearest limb that shows.
     */
    fun layout(tree: Tree, folded: Set<String>, tasksShown: Int = TASKS_SHOWN, notes: Map<String, Urgency> = emptyMap()): Layout {
        val spots = ArrayList<Spot>()
        val edges = ArrayList<Edge>()
        val limbAt = HashMap<String, Spot>()
        val taskAt = HashMap<Task, Spot>()
        var deepest = 0

        fun place(l: Limb, depth: Int, top: Float): Float {
            deepest = maxOf(deepest, depth)
            val closed = l.node.tag in folded
            var y = top
            if (!closed) {
                val shown = l.tasks.take(tasksShown)
                val x = (depth + 1) * COL
                shown.forEach { t -> spots += Spot(Kind.TASK, x, y + TASK_H / 2, TASK_W, TASK_H, limb = l, task = t); y += TASK_H }
                if (l.tasks.size > shown.size) { spots += Spot(Kind.MORE, x, y + TASK_H / 2, TASK_W, TASK_H, limb = l, count = l.tasks.size - shown.size); y += TASK_H }
                if (shown.isNotEmpty() && l.children.isNotEmpty()) y += 6f
                l.children.forEachIndexed { i, k -> if (i > 0) y += 12f; y += place(k, depth + 1, y) }
            }
            // A folded world with a note is one wide row: its name, then the task to do next.
            val wide = closed && depth == 1 && l.node.tag in notes
            val h = maxOf(if (wide) WIDE_H else NODE_H, y - top)
            val s = Spot(
                Kind.LIMB, depth * COL, top + h / 2, if (wide) WIDE_W else NODE_W, if (wide) WIDE_H else NODE_H,
                limb = l, count = if (closed) l.matched else 0, folded = closed,
            )
            spots += s
            limbAt[l.node.tag] = s
            return h
        }

        var y = 0f
        tree.worlds.forEachIndexed { i, w -> if (i > 0) y += 24f; y += place(w, 1, y) }
        val height = maxOf(TRUNK_H, y)
        val trunk = Spot(Kind.TRUNK, 0f, height / 2, NODE_W, TRUNK_H, count = tree.matched)
        spots += trunk
        spots.filter { it.kind == Kind.TASK }.forEach { taskAt[it.task!!] = it }

        fun curve(kind: EdgeKind, x1: Float, y1: Float, x2: Float, y2: Float, state: Branches.State? = null): Edge {
            val mx = (x1 + x2) / 2
            return Edge(kind, x1, y1, mx, y1, mx, y2, x2, y2, state)
        }
        spots.forEach { s ->
            when (s.kind) {
                Kind.LIMB -> {
                    val n = s.limb!!.node
                    val from = if (n.path.isEmpty()) trunk else limbAt[if ('/' in n.tag) n.tag.substringBeforeLast('/') else ""]
                    if (from != null) edges += curve(EdgeKind.BRANCH, from.x + from.w, from.y, s.x, s.y, n.state)
                }
                Kind.TASK -> limbAt[s.limb!!.node.tag]?.let { p -> edges += curve(EdgeKind.TASK, p.x + p.w, p.y, s.x, s.y) }
                else -> Unit
            }
        }

        // A task's end of a link: the task itself, or the deepest limb that shows and holds it.
        fun endOf(t: Task): Spot? = taskAt[t] ?: Projects.tagOf(t)?.let { tag ->
            val parts = tag.split('/')
            (parts.size downTo 1).firstNotNullOfOrNull { n -> limbAt[parts.take(n).joinToString("/")] }
        }
        val seen = HashSet<Pair<Spot, Spot>>()
        tree.links.forEach { link ->
            val a = endOf(link.blocker) ?: return@forEach
            val b = endOf(link.waiting) ?: return@forEach
            if (a === b || !seen.add(a to b)) return@forEach
            // Both ends hook on the left edge and the curve bows out to the left, clear of the tree's own lines.
            val bow = 40f + minOf(160f, kotlin.math.abs(b.y - a.y) * 0.15f)
            edges += Edge(
                if (link.across) EdgeKind.LINK_ACROSS else EdgeKind.LINK,
                a.x, a.y, a.x - bow, a.y, b.x - bow, b.y, b.x, b.y,
                blocker = link.blocker, waiting = link.waiting,
            )
        }
        return Layout(spots, edges, (deepest + 1) * COL + TASK_W, height)
    }
}
