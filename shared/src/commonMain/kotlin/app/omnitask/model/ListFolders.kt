package app.omnitask.model

import app.omnitask.data.TaskLine

/**
 * Folders inside a list, nesting without limit (owner's pick, 2026-10-28, way ก): a folder is a nested tag under the
 * list's own tag, `#bucketlist/เที่ยว/ญี่ปุ่น`, on a line in the list note or anywhere else. The list notes stay where
 * they are. An old category tag (`#เที่ยว` on a Bucket list item) still counts as the folder of that name, so nothing
 * has to be moved; the line takes the nested tag the next time it is put in a folder. Empty folders made in the app
 * are kept in the note's `categories:` line as paths (`เที่ยว/ญี่ปุ่น`).
 */
object ListFolders {

    /** Whether [tag] is [list]'s own tag or a folder under it, in any case. */
    fun isListTag(list: OmniList, tag: String): Boolean =
        tag.equals(list.tag, ignoreCase = true) || tag.startsWith(list.tag + "/", ignoreCase = true)

    /** Whether [t] is in [list]: written in its note, or tagged with its tag or one of its folders. */
    fun belongs(list: OmniList, t: Task): Boolean = t.list == list.name || t.tags.any { isListTag(list, it) }

    /** The old one-level categories of [list] (no slash), which items may still carry as plain tags. */
    private fun flat(list: OmniList) = list.categories.filter { '/' !in it }

    /** The folder [t] sits in inside [list] ("เที่ยว/ญี่ปุ่น"), "" for the list itself. A nested tag wins over an old category tag. */
    fun pathOf(list: OmniList, t: Task): String {
        t.tags.firstOrNull { it.length > list.tag.length + 1 && it.startsWith(list.tag + "/", ignoreCase = true) }
            ?.let { return it.substring(list.tag.length + 1) }
        val cats = flat(list)
        return t.tags.firstNotNullOfOrNull { tag -> cats.firstOrNull { it.equals(tag, ignoreCase = true) } } ?: ""
    }

    /** Every folder in [list], each level of a path counted: the saved ones and the ones its [items] sit in, sorted. */
    fun all(list: OmniList, items: List<Task>): List<String> =
        (list.categories + items.map { pathOf(list, it) }).map(::clean).filter { it.isNotEmpty() }
            .flatMap { p -> p.split('/').indices.map { i -> p.split('/').take(i + 1).joinToString("/") } }
            .distinct().sortedBy { it.lowercase() }

    /** The folders right under [path] ("" is the list itself), from [folders] as [all] gives them. */
    fun children(folders: List<String>, path: String): List<String> = folders.filter { parentOf(it) == path }

    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    fun nameOf(path: String): String = path.substringAfterLast('/')

    /** Whether an item at [itemPath] is in the folder [path] or deeper. */
    fun inside(path: String, itemPath: String): Boolean = path.isEmpty() || itemPath == path || itemPath.startsWith("$path/")

    /** A folder path as typed, tidied the way a line writes tags (no spaces, no empty levels). */
    fun clean(input: String): String =
        input.trim().removePrefix("#").split('/').map { TaskLine.normalizeTag(it.trim()) }.filter { it.isNotEmpty() }.joinToString("/")

    /** The tag that puts a line in [path] of [list]. */
    fun tagOf(list: OmniList, path: String): String = clean(path).let { if (it.isEmpty()) list.tag else "${list.tag}/$it" }

    /**
     * The line [raw] put in folder [path] of [list]: the list's tag, its folder tags and its old category tags come
     * off, and the one tag of the new place goes on. Other tags stay.
     */
    fun place(raw: String, list: OmniList, path: String): String {
        val cats = flat(list)
        val tags = TaskLine.parse(raw)?.tags.orEmpty()
        val off = tags.filter { tag -> isListTag(list, tag) || cats.any { it.equals(tag, ignoreCase = true) } }
            .fold(raw) { acc, tag -> TaskLine.removeTag(acc, tag) }
        return TaskLine.addTag(off, tagOf(list, path))
    }

    /** [list]'s saved folders with [path] added (and the levels above it), for the note's `categories:` line. */
    fun withFolder(list: OmniList, path: String): List<String> {
        val p = clean(path)
        if (p.isEmpty() || list.categories.any { it.equals(p, ignoreCase = true) }) return list.categories
        return list.categories + p
    }

    /** [list]'s saved folders without [path] and everything under it. */
    fun withoutFolder(list: OmniList, path: String): List<String> =
        list.categories.filterNot { c -> clean(c).let { it.equals(path, ignoreCase = true) || it.startsWith("$path/", ignoreCase = true) } }
}
