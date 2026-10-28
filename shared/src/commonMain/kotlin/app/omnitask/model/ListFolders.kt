package app.omnitask.model

import app.omnitask.data.TaskLine

/**
 * Folders inside a list, nesting without limit (owner's pick, 2026-10-28, way ก): a folder is a nested tag under the
 * list's own tag, `#bucketlist/เที่ยว/ญี่ปุ่น`, on a line in the list note or anywhere else. The list notes stay where
 * they are. An old category tag (`#เที่ยว` on a Bucket list item) still counts as the folder of that name, so nothing
 * has to be moved; the line takes the nested tag the next time it is put in a folder. Empty folders made in the app
 * are kept in the note: one level in `categories:` (as before), deeper ones in `folders:` (`เที่ยว/ญี่ปุ่น`).
 */
object ListFolders {

    /** Whether [tag] is [list]'s own tag or a folder under it, in any case. */
    fun isListTag(list: OmniList, tag: String): Boolean =
        tag.equals(list.tag, ignoreCase = true) || tag.startsWith(list.tag + "/", ignoreCase = true)

    /** Whether [t] is in [list]: written in its note, or tagged with its tag or one of its folders. */
    fun belongs(list: OmniList, t: Task): Boolean = t.list == list.name || t.tags.any { isListTag(list, it) }

    /** The one-level categories of [list] as a line writes them, which items may carry as plain tags (`#เที่ยว`). */
    private fun flat(list: OmniList) = list.categories.filter { '/' !in it }.map(::clean).filter { it.isNotEmpty() }

    /** The old category tag that puts [t] in a folder, when no nested tag does. */
    private fun categoryTagOf(list: OmniList, t: Task): String? {
        if (t.tags.any { it.length > list.tag.length + 1 && it.startsWith(list.tag + "/", ignoreCase = true) }) return null
        val cats = flat(list)
        return t.tags.firstOrNull { tag -> cats.any { it.equals(tag, ignoreCase = true) } }
    }

    /** The folder [t] sits in inside [list] ("เที่ยว/ญี่ปุ่น"), "" for the list itself. A nested tag wins over an old category tag. */
    fun pathOf(list: OmniList, t: Task): String {
        t.tags.firstOrNull { it.length > list.tag.length + 1 && it.startsWith(list.tag + "/", ignoreCase = true) }
            ?.let { return it.substring(list.tag.length + 1) }
        val tag = categoryTagOf(list, t) ?: return ""
        return flat(list).first { it.equals(tag, ignoreCase = true) }
    }

    /** Every folder in [list], each level of a path counted: the saved ones and the ones its [items] sit in, sorted. */
    fun all(list: OmniList, items: List<Task>): List<String> =
        (list.categories + list.folders + items.map { pathOf(list, it) }).map(::clean).filter { it.isNotEmpty() }
            .flatMap { p -> p.split('/').indices.map { i -> p.split('/').take(i + 1).joinToString("/") } }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    /** The folders right under [path] ("" is the list itself), from [folders] as [all] gives them. */
    fun children(folders: List<String>, path: String): List<String> = folders.filter { parentOf(it).equals(path, ignoreCase = true) }

    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    fun nameOf(path: String): String = path.substringAfterLast('/')

    /** Whether an item at [itemPath] is in the folder [path] or deeper. */
    fun inside(path: String, itemPath: String): Boolean =
        path.isEmpty() || itemPath.equals(path, ignoreCase = true) || itemPath.startsWith("$path/", ignoreCase = true)

    /** A folder path as typed, tidied the way a line writes tags (no spaces, no empty levels). */
    fun clean(input: String): String =
        input.trim().removePrefix("#").split('/').map { TaskLine.normalizeTag(it.trim()) }.filter { it.isNotEmpty() }.joinToString("/")

    /** The tag that puts a line in [path] of [list]. */
    fun tagOf(list: OmniList, path: String): String = clean(path).let { if (it.isEmpty()) list.tag else "${list.tag}/$it" }

    /**
     * The line [raw] put in folder [path] of [list]: the list's tag and its folder tags come off, and the one tag of
     * the new place goes on. An old category tag comes off only when it is what put the line in its folder, on a line
     * already in the list ([inNote] for a line written in the list's note): a task pulled in keeps a tag like
     * `#หนังสือ` that only shares a category's name. Other tags stay.
     */
    fun place(raw: String, list: OmniList, path: String, inNote: Boolean = false): String {
        val task = TaskLine.parse(raw) ?: return raw
        val inList = inNote || task.tags.any { isListTag(list, it) }
        val off = (task.tags.filter { isListTag(list, it) } + listOfNotNull(categoryTagOf(list, task)?.takeIf { inList }))
            .fold(raw) { acc, tag -> TaskLine.removeTag(acc, tag) }
        return TaskLine.addTag(off, tagOf(list, path))
    }

    /** [list] with the empty folder [path] saved: one level in `categories:`, deeper in `folders:`. */
    fun withFolder(list: OmniList, path: String): OmniList {
        val p = clean(path)
        if (p.isEmpty() || (list.categories + list.folders).any { clean(it).equals(p, ignoreCase = true) }) return list
        return if ('/' in p) list.copy(folders = list.folders + p) else list.copy(categories = list.categories + p)
    }

    /** [list] with the saved folder [path], and every folder under it, gone from both lines. */
    fun withoutFolder(list: OmniList, path: String): OmniList {
        fun gone(c: String) = clean(c).let { it.equals(path, ignoreCase = true) || it.startsWith("$path/", ignoreCase = true) }
        return list.copy(categories = list.categories.filterNot(::gone), folders = list.folders.filterNot(::gone))
    }
}
