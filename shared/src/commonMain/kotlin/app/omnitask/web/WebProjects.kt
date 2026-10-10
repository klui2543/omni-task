package app.omnitask.web

import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.model.Branches
import app.omnitask.model.OmniList
import app.omnitask.model.Projects
import app.omnitask.model.QuickAdd
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.time.LATEST
import app.omnitask.ui.ListEmoji
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The Projects and Lists page for the web: the same project cards, overview, branch tree and list screens as the
 * Android tabs, built by the same code ([Projects], [Branches], [OmniList]), and every text edit those screens
 * make (rename a project or branch, add to a list, pull tasks into a list, chain tasks in order). What the owner
 * chose on this device (project order, stars, task order, branch states) comes in as text, and the page comes out
 * as JSON; edits return the whole new note text, like [WebCore].
 */
object WebProjects {

    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    /** A list note read from the vault: the key its tasks go by (the file's id on the web), its path and its text. */
    @Serializable
    data class NoteIn(val key: String, val path: String, val text: String)

    @Serializable
    data class StateIn(
        /** The owner's own project order; starred projects come first. */
        val order: List<String> = emptyList(),
        val starred: List<String> = emptyList(),
        /** Branch states as Android saves them: "project\tpath\tSTATE". */
        val branches: List<String> = emptyList(),
        /** Each project's task order, by title. */
        val taskOrder: Map<String, List<String>> = emptyMap(),
        /** Projects whose order is enforced with 🆔 and ⛔. */
        val strict: List<String> = emptyList(),
    )

    @Serializable
    data class NextOut(val key: String, val title: String)

    @Serializable
    data class BranchOut(
        val path: String,
        val name: String,
        val tag: String,
        val depth: Int,
        val parentPath: String,
        val state: String,
        val done: Int,
        val count: Int,
        /** The branch's own tasks (tagged exactly with it), by key. */
        val own: List<String>,
        /** Keys of the sub-branches' paths, in order. */
        val children: List<String>,
    )

    /**
     * One row of the ordered list. [meta] says which line goes under the title: `strict` (waits for task [pos]),
     * `waiting` (waits for [waitingOn]) or `plain` (next, date, subtask progress).
     */
    @Serializable
    data class OrderOut(
        val key: String,
        val pos: Int,
        val next: Boolean,
        val locked: Boolean,
        val meta: String,
        val waitingOn: String? = null,
        val date: String? = null,
        val subDone: Int? = null,
        val subTotal: Int? = null,
    )

    @Serializable
    data class ProjectOut(
        val name: String,
        val total: Int,
        val done: Int,
        val pct: Int,
        val overdue: Int,
        val blocked: List<String>,
        val next: NextOut?,
        val starred: Boolean,
        val strict: Boolean,
        /** What renaming would change, for the preview: lines and files. */
        val renameLines: Int,
        val renameFiles: Int,
        val branches: List<BranchOut>,
        val order: List<OrderOut>,
        val finished: List<String>,
    )

    @Serializable
    data class ItemOut(val key: String, val done: Boolean, val tags: List<String>, val sub: String)

    @Serializable
    data class ListOut(
        val key: String,
        val name: String,
        val path: String,
        val icon: String,
        val emoji: String,
        val categories: List<String>,
        val tag: String,
        val done: Int,
        val total: Int,
        /** Open first, then done, each by title. */
        val items: List<ItemOut>,
    )

    @Serializable
    data class Out(
        val projects: List<ProjectOut>,
        val lists: List<ListOut>,
        /** The tasks of the list notes, which the TaskForge note does not hold. */
        val noteTasks: List<WebCore.TaskDto>,
        /** Keys of tasks sitting in a parked branch. */
        val parked: List<String>,
        /** Tags that are lists or their categories, which never name a project. */
        val ignored: List<String>,
    )

    /**
     * The page for the TaskForge note at [text] and the list notes in [notesJson], given what this device chose
     * ([stateJson]). Tasks in a parked branch still belong to their project but are listed in `parked`.
     */
    fun build(fileKey: String, path: String, text: String, notesJson: String, stateJson: String, today: String): String {
        val day = LocalDate.parse(today)
        val st = json.decodeFromString<StateIn>(stateJson)
        val notes = json.decodeFromString<List<NoteIn>>(notesJson)

        val lists = ArrayList<OmniList>()
        val noteTasks = ArrayList<Task>()
        val keys = HashMap<String, String>()
        notes.forEach { n ->
            val list = OmniList.parse(n.path, n.text) ?: return@forEach
            lists += list
            keys[list.path] = n.key
            noteTasks += VaultText.parseFile(n.key, n.path, n.text).map { it.copy(list = list.name) }
        }
        lists.sortBy { it.name.lowercase() }
        // Lists, their categories: tags that are not projects. Set before any project is read.
        val ignored = lists.flatMap { listOf(it.tag) + it.categories }.toSet()
        Projects.ignoredTags = ignored

        val states = Branches.parse(st.branches.toSet())
        val everything = VaultText.parseFile(fileKey, path, text)
        val parked = everything.filter { Branches.isParked(it, states) }
        val tasks = everything.filter { !Branches.isParked(it, states) }
        val projectTasks = tasks + parked
        val allTasks = tasks + noteTasks

        val rank = st.order.withIndex().associate { it.value to it.index }
        val projects = Projects.build(projectTasks, day)
            .sortedWith(compareBy({ it.name !in st.starred }, { rank[it.name] ?: Int.MAX_VALUE }))
        val progress = allTasks.filter { it.parent != null && it.status != Status.CANCELLED }.groupBy { it.parent!! }
            .mapValues { (_, kids) -> kids.count { it.status == Status.DONE } to kids.size }
        val parkedSet = parked.toSet()

        val projectOut = projects.map { p ->
            val tree = Branches.tree(p.name, projectTasks, states)
            val rankOf = st.taskOrder[p.name].orEmpty().withIndex().associate { it.value to it.index }
            val open = p.tasks.filter { it.isOpen && it.parent == null && it !in parkedSet }
                .sortedWith(compareBy<Task>({ rankOf[it.title] ?: Int.MAX_VALUE }, { it.due ?: it.scheduled ?: LocalDate.LATEST }))
            val strict = p.name in st.strict
            val named = (projectTasks + noteTasks).filter { Projects.projectOf(it) == p.name }
            ProjectOut(
                name = p.name, total = p.tasks.size, done = p.done, pct = (p.ratio * 100).toInt(), overdue = p.overdue,
                blocked = p.blocked.map { it.key }, next = p.next?.let { NextOut(it.key, it.title) },
                starred = p.name in st.starred, strict = strict,
                renameLines = named.size, renameFiles = named.map { it.fileUri }.distinct().size,
                branches = tree.flatten().map { n ->
                    BranchOut(
                        n.path, n.name, n.tag, n.depth, n.parentPath, n.state.name, n.done, n.all.size,
                        n.own.map { it.key }, n.children.map { it.path },
                    )
                },
                order = open.mapIndexed { pos, t ->
                    val waiting = t in p.blocked
                    val meta = when {
                        waiting && strict && pos > 0 -> "strict"
                        waiting -> "waiting"
                        else -> "plain"
                    }
                    val sub = progress[t.key]
                    OrderOut(
                        t.key, pos, pos == 0, waiting, meta,
                        waitingOn = if (meta == "waiting") Projects.waitingOn(t, tasks) ?: "" else null,
                        date = (t.due ?: t.scheduled)?.toString(),
                        subDone = sub?.first, subTotal = sub?.second,
                    )
                },
                finished = p.tasks.filter { !it.isOpen && it.parent == null }.sortedByDescending { it.done }.map { it.key },
            )
        }

        val listOut = lists.map { l ->
            val own = noteTasks.filter { it.list == l.name }
            val tagged = projectTasks.filter { t -> t.tags.any { it.equals(l.tag, ignoreCase = true) } }
            val items = own + tagged
            val sorted = items.sortedWith(compareBy({ it.status == Status.DONE || it.status == Status.CANCELLED }, { it.title.lowercase() }))
            ListOut(
                key = keys.getValue(l.path), name = l.name, path = l.path, icon = l.icon, emoji = ListEmoji.of(l.icon),
                categories = l.categories, tag = l.tag,
                done = items.count { it.status == Status.DONE }, total = items.size,
                items = sorted.map { t ->
                    // Tasks pulled in from elsewhere say where they live.
                    val sub = t.tags.filter { it in l.categories } +
                        listOfNotNull(t.filePath.takeIf { t.list == null }?.substringAfterLast('/')?.removeSuffix(".md"))
                    ItemOut(t.key, t.status == Status.DONE, t.tags, sub.joinToString(", "))
                },
            )
        }

        return json.encodeToString(
            Out(
                projects = projectOut,
                lists = listOut,
                noteTasks = noteTasks.map { WebCore.dto(it, day) },
                parked = parked.map { it.key },
                ignored = ignored.toList(),
            ),
        )
    }

    // ---- Edits: each takes a note's text and answers with the whole new text, or why nothing changed ----

    @Serializable
    data class EditOut(
        val ok: Boolean,
        val text: String? = null,
        val error: String? = null,
        /** Lines changed (rename) or tasks changed (pull into a list). */
        val changed: Int = 0,
        val path: String? = null,
    )

    private fun ok(text: String, changed: Int = 0, path: String? = null) = json.encodeToString(EditOut(true, text, changed = changed, path = path))
    private fun fail(error: String) = json.encodeToString(EditOut(false, error = error))

    @Serializable
    data class TaskRef(val raw: String, val lineIndex: Int)

    /** The line's index in [lines] where the task was left, or anywhere it now is; -1 when someone changed it. */
    private fun indexOf(lines: List<String>, ref: TaskRef): Int =
        if (lines.getOrNull(ref.lineIndex) == ref.raw) ref.lineIndex else lines.indexOf(ref.raw)

    /** Tags that name a list or one of its categories, which never name a project (kept from the last time the lists were read). */
    fun ignoreTags(tagsJson: String) {
        Projects.ignoredTags = json.decodeFromString<List<String>>(tagsJson).toSet()
    }

    /** A project (or a branch, by its full tag) renamed in every task line: `#old` and `#old/x` become `#new...`. */
    fun renameTag(text: String, old: String, new: String): String {
        val (out, n) = VaultText.renameProject(text, old, new)
        return ok(out, n)
    }

    /** Whether [name] can name a project, and its clean form: no spaces, no leading #, no slash. */
    fun cleanProjectName(name: String): String = name.trim().removePrefix("#").replace(Regex("""\s+"""), "-")

    /** A task in a branch (or a project): the title is read like quick add and gets the branch's tag. */
    fun addTagged(text: String, tag: String, title: String, today: String): String {
        if (title.isBlank()) return fail("empty")
        val day = LocalDate.parse(today)
        val line = TaskLine.addTag(QuickAdd.parse(title, day).line(day), tag)
        return ok(VaultText.appendLine(text, line))
    }

    /**
     * Turns "do in order" on or off for [ordered] (the project's open tasks in their order): each task waits for
     * the one before it by `⛔` and gets a `🆔` when on, and the waits inside the project are dropped when off;
     * waits on tasks outside the project are kept.
     */
    fun chain(text: String, orderedJson: String, on: Boolean): String {
        val ordered = json.decodeFromString<List<TaskRef>>(orderedJson)
        val separator = VaultText.separatorOf(text)
        val lines = text.split(separator).toMutableList()
        val parsed = VaultText.parseFile("", "", text).associateBy { it.lineIndex }
        val at = ordered.map { indexOf(lines, it) }
        if (at.any { it < 0 }) return fail("conflict")
        val tasks = at.map { parsed[it] ?: return fail("conflict") }
        val taken = parsed.values.mapNotNull { it.id }.toMutableSet()
        val ids = tasks.map { t ->
            t.id ?: run {
                var id: String
                do { id = "o" + (1..5).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("") } while (id in taken)
                taken += id
                id
            }
        }
        val own = ids.toSet()
        tasks.forEachIndexed { i, t ->
            val outside = t.dependsOn.filter { it !in own }
            val deps = if (on && i > 0) outside + ids[i - 1] else outside
            lines[at[i]] = TaskLine.setDependsOn(if (on) TaskLine.setId(lines[at[i]], ids[i]) else lines[at[i]], deps)
        }
        return ok(lines.joinToString(separator), tasks.size)
    }

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    // ---- Lists ----

    /** The two lists every owner starts with, as notes to write. */
    fun starters(): String = json.encodeToString(OmniList.starters().map { NoteIn("", it.path, it.render()) })

    /** The emoji to choose from, by theme: label (Thai) and emoji. */
    @Serializable
    data class IconTheme(val label: String, val emoji: List<String>)

    fun iconThemes(): String = json.encodeToString(ListEmoji.groups.map { (label, emoji) -> IconTheme(label.first, emoji) })

    /** A new list note: its name made safe for a file name, and the note's path and text. */
    fun createList(name: String, icon: String, categoriesJson: String): String {
        val clean = name.trim().replace(Regex("""[\\/:*?"<>|#^\[\]]"""), " ").trim()
        if (clean.isEmpty()) return fail("empty")
        val categories = json.decodeFromString<List<String>>(categoriesJson).map { it.trim() }.filter { it.isNotEmpty() }
        val list = OmniList(clean, "${OmniList.FOLDER}/$clean.md", icon, categories)
        return ok(list.render(), path = list.path)
    }

    /** A list's icon or categories changed by rewriting its header; the items stay as they are. */
    fun updateList(text: String, path: String, icon: String, categoriesJson: String): String {
        val list = OmniList.parse(path, text) ?: return fail("conflict")
        val categories = json.decodeFromString<List<String>>(categoriesJson)
        val end = text.indexOf("\n---", 3)
        val body = text.substring(end + 4).trimStart('\r', '\n')
        val head = list.copy(icon = icon, categories = categories).render().substringBefore("# ")
        return ok(head + body)
    }

    /** A new item at the end of a list note, tagged with the list's tag (and category) and today's date. */
    fun addListItem(text: String, path: String, title: String, category: String?, today: String): String {
        val list = OmniList.parse(path, text) ?: return fail("conflict")
        if (title.isBlank()) return fail("empty")
        var line = TaskLine.addTag("- [ ] ${title.trim()}", list.tag)
        if (!category.isNullOrEmpty()) line = TaskLine.addTag(line, category)
        line = TaskLine.setDate(line, TaskLine.DateField.CREATED, LocalDate.parse(today))
        return ok(VaultText.appendLine(text, line))
    }

    /** Puts tasks in a list by tagging them where they are. [changed] says how many were still where they were. */
    fun includeInList(text: String, tasksJson: String, tag: String, category: String?): String {
        val refs = json.decodeFromString<List<TaskRef>>(tasksJson)
        val separator = VaultText.separatorOf(text)
        val lines = text.split(separator).toMutableList()
        var changed = 0
        refs.forEach { ref ->
            val i = indexOf(lines, ref)
            if (i < 0) return@forEach
            var line = TaskLine.addTag(lines[i], tag)
            if (!category.isNullOrEmpty()) line = TaskLine.addTag(line, category)
            lines[i] = line
            changed++
        }
        return ok(lines.joinToString(separator), changed)
    }

    // ---- Branches: states are kept on the device, so these answer with the states to keep ----

    @Serializable
    data class BranchOp(
        /** `set`, `choose`, `add`, `rename`, `delete` or `moveProject`. */
        val op: String,
        val project: String,
        val path: String = "",
        /** The state for `set`; the new name for `add`, `rename` and `moveProject`. */
        val value: String = "",
    )

    @Serializable
    data class BranchResult(
        val ok: Boolean,
        val states: List<String> = emptyList(),
        val error: String? = null,
        /** For `rename`: the full tags to rewrite in the notes before the states move. */
        val oldTag: String? = null,
        val newTag: String? = null,
        val path: String? = null,
    )

    /**
     * One change to the branch states of a project: set a branch's state, choose one among its siblings (the others
     * being tried are parked), add an empty branch, rename a branch, delete an empty one, or move a renamed
     * project's states to its new name. The tasks decide the tree, so the TaskForge note comes in too.
     */
    fun branchChange(fileKey: String, path: String, text: String, statesJson: String, opJson: String): String {
        val op = json.decodeFromString<BranchOp>(opJson)
        val saved = json.decodeFromString<List<String>>(statesJson).toSet()
        val states = Branches.parse(saved)
        fun done(next: Map<Pair<String, String>, Branches.State>, extra: BranchResult = BranchResult(true)) =
            json.encodeToString(extra.copy(ok = true, states = Branches.encode(next).sorted()))
        if (op.op == "moveProject") {
            val name = op.value
            return done(states.mapKeys { (k, _) -> if (k.first == op.project) name to k.second else k })
        }
        val tasks = VaultText.parseFile(fileKey, path, text)
        val tree = Branches.tree(op.project, tasks, states)
        val nodes = tree.flatten()
        val node = nodes.firstOrNull { it.path == op.path } ?: return json.encodeToString(BranchResult(false, error = "missing"))
        return when (op.op) {
            "set" -> done(states + ((op.project to op.path) to Branches.State.valueOf(op.value)))
            "choose" -> {
                val siblings = nodes.firstOrNull { it.path == node.parentPath && node.path.isNotEmpty() }?.children ?: emptyList()
                done(Branches.choose(node, siblings, states))
            }
            "add" -> {
                val clean = Branches.clean(op.value)
                if (clean.isEmpty()) return json.encodeToString(BranchResult(false, error = "empty"))
                val child = if (node.path.isEmpty()) clean else node.path + "/" + clean
                if (node.children.any { it.path == child }) return done(states, BranchResult(true, path = child))
                done(states + ((op.project to child) to if (node.path.isEmpty()) Branches.State.ACTIVE else Branches.State.TRYING), BranchResult(true, path = child))
            }
            "rename" -> {
                val clean = Branches.clean(op.value)
                if (clean.isEmpty() || node.path.isEmpty() || clean == node.name) return json.encodeToString(BranchResult(false, error = "empty"))
                val newPath = if ('/' in node.path) node.parentPath + "/" + clean else clean
                done(
                    Branches.movePaths(states, op.project, node.path, newPath),
                    BranchResult(true, oldTag = node.tag, newTag = op.project + "/" + newPath, path = newPath),
                )
            }
            "delete" -> when {
                node.path.isEmpty() -> json.encodeToString(BranchResult(false, error = "empty"))
                node.all.isNotEmpty() -> json.encodeToString(BranchResult(false, error = "hasTasks"))
                else -> done(Branches.dropPaths(states, op.project, node.path))
            }
            else -> json.encodeToString(BranchResult(false, error = "unknown"))
        }
    }
}
