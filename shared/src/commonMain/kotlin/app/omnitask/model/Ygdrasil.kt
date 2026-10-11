package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * Project Ygdrasil: every project in one tree. The trunk holds each project as a world, each world its branches
 * (nested tags, as in [Branches]), each branch its tasks; tasks that wait for one another (`🆔` and `⛔`) are tied
 * together across the worlds. [grow] picks what shows through a [Filter]; [layout] places it once for both apps.
 */
object Ygdrasil {

    enum class Show(private val th: String, private val en: String) {
        OPEN("ยังไม่เสร็จ", "Open"), ALL("ทั้งหมด", "All"), DONE("เสร็จแล้ว", "Done");

        val label get() = tr(th, en)
    }

    /** Which dates pass, by the task's due date (or its scheduled date when it has none). */
    enum class Dates(private val th: String, private val en: String) {
        ANY("ทุกวัน", "Any date"), OVERDUE("เลยกำหนด", "Overdue"), TODAY("ถึงวันนี้", "Up to today"),
        WEEK("ภายใน 7 วัน", "Within 7 days"), UNDATED("ไม่มีวัน", "No date");

        val label get() = tr(th, en)
    }

    data class Filter(
        val show: Show = Show.OPEN,
        val dates: Dates = Dates.ANY,
        /** Empty means every priority. */
        val priorities: Set<Priority> = emptySet(),
        /** Only tasks waiting for another task that is still open. */
        val blockedOnly: Boolean = false,
        val starredOnly: Boolean = false,
        /** Parked branches stay out unless this is on. */
        val parked: Boolean = false,
        /** The lines between tasks that wait for one another. */
        val links: Boolean = true,
        val text: String = "",
    ) {
        /** A narrowing filter drops the branches and worlds left with nothing to show; the plain one keeps the whole shape. */
        val narrows get() = show == Show.DONE || dates != Dates.ANY || priorities.isNotEmpty() || blockedOnly || text.isNotBlank()

        /** How many choices differ from the plain filter, for the filter button. */
        val changed get() = listOf(show != Show.OPEN, dates != Dates.ANY, priorities.isNotEmpty(), blockedOnly, starredOnly, parked, !links, text.isNotBlank()).count { it }

        /** One line per choice, for keeping the filter between visits. */
        fun encode(): String = listOf(
            "show=${show.name}", "dates=${dates.name}", "priorities=${priorities.joinToString(",") { it.name }}",
            "blocked=$blockedOnly", "starred=$starredOnly", "parked=$parked", "links=$links", "text=${text.replace('\n', ' ')}",
        ).joinToString("\n")

        companion object {
            fun decode(s: String?): Filter {
                if (s.isNullOrBlank()) return Filter()
                val m = s.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
                return Filter(
                    show = Show.entries.firstOrNull { it.name == m["show"] } ?: Show.OPEN,
                    dates = Dates.entries.firstOrNull { it.name == m["dates"] } ?: Dates.ANY,
                    priorities = m["priorities"].orEmpty().split(',').mapNotNull { n -> Priority.entries.firstOrNull { it.name == n } }.toSet(),
                    blockedOnly = m["blocked"] == "true",
                    starredOnly = m["starred"] == "true",
                    parked = m["parked"] == "true",
                    links = m["links"] != "false",
                    text = m["text"].orEmpty(),
                )
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

    fun passes(t: Task, f: Filter, today: LocalDate, blocked: Set<Task>): Boolean {
        val statusOk = when (f.show) {
            Show.OPEN -> t.isOpen
            Show.DONE -> t.status == Status.DONE
            Show.ALL -> t.status != Status.CANCELLED
        }
        if (!statusOk) return false
        val day = t.due ?: t.scheduled
        val dateOk = when (f.dates) {
            Dates.ANY -> true
            Dates.OVERDUE -> t.isOpen && day != null && day < today
            Dates.TODAY -> day != null && day <= today
            Dates.WEEK -> day != null && day <= today.plusDays(7)
            Dates.UNDATED -> day == null
        }
        if (!dateOk) return false
        if (f.priorities.isNotEmpty() && t.priority !in f.priorities) return false
        if (f.blockedOnly && t !in blocked) return false
        val q = f.text.trim()
        if (q.isNotEmpty() && !t.title.contains(q, ignoreCase = true) && t.tags.none { it.contains(q, ignoreCase = true) }) return false
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

    // ---- Layout ----

    /** Sizes on the tree, in dp (Android) or pixels (web), the same as the project mind map. */
    const val COL = 200f
    const val NODE_W = 156f
    const val NODE_H = 40f
    const val TRUNK_H = 52f
    const val TASK_W = 190f
    const val TASK_H = 32f
    const val TASKS_SHOWN = 6

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
    )

    data class Layout(val spots: List<Spot>, val edges: List<Edge>, val width: Float, val height: Float)

    /**
     * A tidy tree read left to right, as the mind map: the trunk, then each world in the next column, each limb in the
     * middle of the rows its tasks and sub-limbs take. Limbs whose tag is in [folded] keep their tasks and sub-limbs in.
     * A link whose task is folded away ends at the nearest limb that shows.
     */
    fun layout(tree: Tree, folded: Set<String>, tasksShown: Int = TASKS_SHOWN): Layout {
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
            val h = maxOf(NODE_H, y - top)
            val s = Spot(Kind.LIMB, depth * COL, top + h / 2, NODE_W, NODE_H, limb = l, count = if (closed) l.matched else 0, folded = closed)
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
            )
        }
        return Layout(spots, edges, (deepest + 1) * COL + TASK_W, height)
    }
}
