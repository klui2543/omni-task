package app.omnitask.model

/**
 * Branches inside a project: nested tags like `#peddose/แอป/มือถือ`. Each branch can be worked on, tried
 * against its siblings, chosen, or parked; parked branches drop out of Focus, the calendar and the lists
 * but stay in the file. Statuses live in the synced settings as "project\tpath\tSTATUS".
 */
object Branches {

    enum class State(private val th: String, private val en: String) {
        ACTIVE("กำลังทำ", "Active"), TRYING("กำลังลอง", "Trying"), CHOSEN("เลือกแล้ว", "Chosen"), PARKED("พับเก็บ", "Parked");

        val label get() = tr(th, en)
    }

    /** One branch; the root is the project itself with an empty [path]. */
    data class Node(
        val project: String,
        val path: String,
        val state: State,
        /** Tasks tagged exactly with this branch. */
        val own: List<Task>,
        /** Tasks in this branch and every branch under it. */
        val all: List<Task>,
        val children: List<Node>,
    ) {
        val name get() = if (path.isEmpty()) project else path.substringAfterLast('/')
        val tag get() = if (path.isEmpty()) project else "$project/$path"
        val depth get() = if (path.isEmpty()) 0 else path.count { it == '/' } + 1
        val parentPath get() = if ('/' in path) path.substringBeforeLast('/') else ""
        val done get() = all.count { it.status == Status.DONE }
        val open get() = all.filter { it.isOpen }

        fun flatten(): List<Node> = listOf(this) + children.flatMap { it.flatten() }
    }

    /** The branch path of a task within its project ("แอป/มือถือ"), or "" when it sits on the project itself. */
    fun pathOf(t: Task): String = Projects.tagOf(t)?.substringAfter('/', "") ?: ""

    fun parse(saved: Set<String>): Map<Pair<String, String>, State> = saved.mapNotNull { e ->
        val parts = e.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val state = State.entries.firstOrNull { it.name == parts[2] } ?: return@mapNotNull null
        (parts[0] to parts[1]) to state
    }.toMap()

    /** Every saved state is kept, Active too, so a branch made in the mind map stays before it has tasks. */
    fun encode(states: Map<Pair<String, String>, State>): Set<String> =
        states.map { (k, v) -> "${k.first}\t${k.second}\t${v.name}" }.toSet()

    /**
     * The tree of one project's tasks; every prefix of a used path is a branch, even without tasks of its own,
     * and so is every branch with a saved state (made in the mind map, no tasks yet).
     */
    fun tree(project: String, tasks: List<Task>, states: Map<Pair<String, String>, State>): Node {
        val mine = tasks.filter { Projects.projectOf(it) == project && it.status != Status.CANCELLED }
        val paths = (mine.map { pathOf(it) } + states.keys.filter { it.first == project }.map { it.second }).filter { it.isNotEmpty() }
            .flatMap { p -> p.split('/').indices.map { i -> p.split('/').take(i + 1).joinToString("/") } }
            .toSet().sorted()
        fun build(path: String): Node {
            val kids = paths.filter { (if (path.isEmpty()) '/' !in it else it.startsWith("$path/") && it.count { c -> c == '/' } == path.count { c -> c == '/' } + 1) }
                .map { build(it) }
            val own = mine.filter { pathOf(it) == path }
            val all = mine.filter { val p = pathOf(it); path.isEmpty() || p == path || p.startsWith("$path/") }
            return Node(project, path, states[project to path] ?: State.ACTIVE, own, all, kids)
        }
        return build("")
    }

    /** Whether a task sits in a parked branch (or under one). */
    fun isParked(t: Task, states: Map<Pair<String, String>, State>): Boolean {
        val project = Projects.projectOf(t) ?: return false
        val path = pathOf(t)
        if (path.isEmpty()) return states[project to ""] == State.PARKED
        val parts = path.split('/')
        return (parts.indices).any { i -> states[project to parts.take(i + 1).joinToString("/")] == State.PARKED }
    }

    /** Choosing one branch parks the siblings still being tried. */
    fun choose(node: Node, siblings: List<Node>, states: Map<Pair<String, String>, State>): Map<Pair<String, String>, State> {
        val next = states.toMutableMap()
        next[node.project to node.path] = State.CHOSEN
        siblings.filter { it.path != node.path && it.state == State.TRYING }.forEach { next[it.project to it.path] = State.PARKED }
        return next
    }

    /** States after a branch is renamed: its own and every one under it move to the new path. */
    fun movePaths(states: Map<Pair<String, String>, State>, project: String, old: String, new: String): Map<Pair<String, String>, State> =
        states.mapKeys { (k, _) ->
            when {
                k.first != project -> k
                k.second == old -> project to new
                k.second.startsWith("$old/") -> project to new + k.second.removePrefix(old)
                else -> k
            }
        }

    /** States without a branch and everything under it. */
    fun dropPaths(states: Map<Pair<String, String>, State>, project: String, path: String): Map<Pair<String, String>, State> =
        states.filterKeys { k -> !(k.first == project && (k.second == path || k.second.startsWith("$path/"))) }

    /** A branch name as it goes into a tag: no spaces, # or slashes. */
    fun clean(name: String): String = name.trim().replace(Regex("""[\s#/]+"""), "-").trim('-')

    private val TAG = Regex("""(?<!\S)#([^\s#]+)""")

    /**
     * Renames a project inside one task line: `#old` and `#old/branch` become `#new` and `#new/branch`.
     * Lines that are not tasks, and other tags, are left alone.
     */
    fun renameInLine(line: String, old: String, new: String): String = TAG.replace(line) { m ->
        val tag = m.groupValues[1]
        when {
            tag == old -> "#$new"
            tag.startsWith("$old/") -> "#$new/" + tag.removePrefix("$old/")
            else -> m.value
        }
    }
}
