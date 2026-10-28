package app.omnitask.model

import app.omnitask.data.TaskLine

/**
 * A whole group carried on Project Ygdrasil (owner, 2026-10-28: "ต้องทำไปหมดทุกคุณสมบัติ", a list's folders do all
 * that a project's branches do): a list, a folder in a list, a project or a branch, let go on a list or one of its
 * folders, on the List card (a new list), or on a project, a branch or the trunk. Everything inside goes along and
 * keeps its place under it.
 */
object GroupMove {

    sealed interface From {
        /** A folder of [list] ("เที่ยว/ญี่ปุ่น"), or the whole list when [path] is "". */
        data class Folder(val list: OmniList, val path: String) : From

        /** A project or a branch, by its tag (`omni/แอป`). */
        data class Branch(val tag: String) : From
    }

    sealed interface To {
        /** Under the folder [path] of [list] ("" for the list itself); [made]: the list is new and is the group itself. */
        data class Folder(val list: OmniList, val path: String, val made: Boolean = false) : To

        /** Under a project or a branch, by its tag, or on the trunk (""): a branch, or a project of its own. */
        data class Branch(val tag: String) : To
    }

    /**
     * One task to change. [line] is its new text; [rest] changes the lines of its block under it (a subtask with a tag
     * of the group goes along with it); [note] is the note the block moves to, null to change it where it is.
     */
    class Step(val task: Task, val line: (String) -> String, val rest: (String) -> String, val note: String?)

    class Plan(
        val steps: List<Step>,
        /** Where the group ends: the folder path in the list, or the branch tag. */
        val at: String,
        /** The saved empty folders or branches of the group at their new place: folder paths, or branch tags. */
        val empty: List<String>,
    )

    /** The group's own name, written as a tag level (`Bucket-list` for the list Bucket list). */
    fun nameOf(from: From): String = ListFolders.clean(
        when (from) {
            is From.Folder -> if (from.path.isEmpty()) from.list.name else ListFolders.nameOf(from.path)
            is From.Branch -> from.tag.substringAfterLast('/')
        },
    )

    /** Where the group ends, or null when letting it go there changes nothing or would put it inside itself. */
    fun placeOf(from: From, to: To): String? {
        val name = nameOf(from)
        if (name.isEmpty()) return null
        return when (to) {
            is To.Folder -> when {
                to.made -> ""
                from is From.Folder && from.list.path == to.list.path ->
                    if (from.path.isEmpty()) null else ListFolders.movedTo(from.path, to.path, sameList = true)
                else -> join(to.path, name)
            }
            is To.Branch -> when {
                from is From.Branch -> null
                else -> join(to.tag, name)
            }
        }
    }

    /**
     * What carrying [from] to [to] does to each task in [pool] (every task the app has read, list items too);
     * [branches] are the saved branch tags (`omni/แอป`) and [taskFile] the note a list item goes to when it becomes
     * a project's task. Null when nothing would change.
     */
    fun plan(from: From, to: To, pool: List<Task>, branches: Collection<String>, taskFile: String): Plan? {
        val at = placeOf(from, to) ?: return null
        val moving = members(from, pool)
        val keys = moving.map { it.key }.toSet()
        val steps = moving.mapNotNull { t ->
            val note = noteOf(t, from, to, taskFile)
            // A subtask goes along inside its parent's block when the parent moves to another note.
            val parentMoves = t.parent != null && t.parent in keys && moving.first { it.key == t.parent }.let { noteOf(it, from, to, taskFile) != null }
            if (parentMoves) return@mapNotNull null
            // Only a line already in the list's note loses the list's old category tags; one moved in keeps a tag like #เที่ยว.
            val inNote = to is To.Folder && t.list == to.list.name
            Step(
                t,
                line = { raw -> move(raw, from, to, at, relOf(from, t, main = true).orEmpty(), t.list, inNote) },
                rest = { raw ->
                    val sub = TaskLine.parse(raw)
                    val rel = sub?.let { relOf(from, it, main = false) }
                    if (rel == null) raw else move(raw, from, to, at, rel, t.list, inNote)
                },
                note = note,
            )
        }
        val saved = when (from) {
            is From.Folder -> ListFolders.savedInside(from.list, from.path).map { if (from.path.isEmpty()) it else it.substring(from.path.length).removePrefix("/") }
            is From.Branch -> branches.filter { it == from.tag || it.startsWith(from.tag + "/") }.map { it.substring(from.tag.length).removePrefix("/") }
        }
        // An empty group is kept as an empty folder or branch where it lands.
        val rels = (saved.filter { it.isNotEmpty() } + listOfNotNull("".takeIf { moving.isEmpty() })).distinct()
        val empty = rels.map { join(at, it) }.filter { it.isNotEmpty() }
        if (steps.isEmpty() && empty.isEmpty() && !(to is To.Folder && to.made)) return null
        return Plan(steps, at, empty)
    }

    /** The tasks of [pool] in the group [from], each once. */
    fun members(from: From, pool: List<Task>): List<Task> =
        pool.filter { !it.todayCopy }.distinctBy { it.key }.filter { relOf(from, it, main = true) != null }

    /** Whether [t] is in the group, and where under it ("" for the group itself, "ญี่ปุ่น/โตเกียว" deeper). */
    private fun relOf(from: From, t: Task, main: Boolean): String? = when (from) {
        is From.Folder -> {
            // A line under a moving task counts only when it carries the list's tag itself.
            if (!main && t.tags.none { ListFolders.isListTag(from.list, it) }) null
            else if (main && !ListFolders.belongs(from.list, t)) null
            else ListFolders.pathOf(from.list, t).takeIf { ListFolders.inside(from.path, it) }
                ?.let { p -> if (from.path.isEmpty()) p else p.substring(minOf(from.path.length, p.length)).removePrefix("/") }
        }
        is From.Branch -> t.tags.firstOrNull { it == from.tag || it.startsWith(from.tag + "/") }?.substring(from.tag.length)?.removePrefix("/")
    }

    /**
     * The note a task's block moves to: an item written in the moving list's own note leaves it for the new list's
     * note, or for the task note. A line anywhere else (another list's note too) is changed where it is.
     */
    private fun noteOf(t: Task, from: From, to: To, taskFile: String): String? {
        if (from !is From.Folder || t.list != from.list.name) return null
        return when (to) {
            is To.Folder -> if (t.list != to.list.name) to.list.path else null
            is To.Branch -> taskFile
        }
    }

    private fun move(raw: String, from: From, to: To, at: String, rel: String, list: String?, inNote: Boolean): String {
        val off = when (from) {
            is From.Folder -> ListFolders.unplace(raw, from.list, inNote = list == from.list.name)
            is From.Branch -> TaskLine.parse(raw)?.tags?.firstOrNull { it == from.tag || it.startsWith(from.tag + "/") }
                ?.let { TaskLine.removeTag(raw, it) } ?: raw
        }
        val place = join(at, rel)
        return when (to) {
            is To.Folder -> ListFolders.place(off, to.list, place, inNote)
            // Its project, if it had one besides the list, gives way: a task shows in one place only (owner, 2026-10-27).
            is To.Branch -> TaskLine.parse(off)?.let { Projects.tagOf(it) }?.let { TaskLine.retag(off, it, place) } ?: TaskLine.homeTag(off, place)
        }
    }

    private fun join(vararg parts: String) = parts.filter { it.isNotEmpty() }.joinToString("/")
}
