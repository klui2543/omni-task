package app.omnitask.model

import app.omnitask.data.TaskLine

/**
 * A task carried to another place on Project Ygdrasil (owner's pick, 2026-10-27, way ก): it moves for real and shows
 * in one place only. On a project or branch it leaves its list; on a list it leaves its project; on "no project yet"
 * it leaves both. The tags it has that are neither stay.
 */
object TreeMove {

    sealed interface To {
        /** A project or branch, by its tag (`omni/แอป`). */
        data class Branch(val tag: String) : To

        /** A list, by the list's tag (`bucketlist`). */
        data class InList(val tag: String) : To

        /** "No project yet"; [group] is the tag of the group it was let go on, null for the whole or the no-tag group. */
        data class NoHome(val group: String?) : To
    }

    /**
     * The task line [raw] as it reads after the move. [project] is its project tag now (`Projects.tagOf`), null when it
     * has none; [lists] are the list tags it carries (any case).
     */
    fun line(raw: String, project: String?, lists: Collection<String>, to: To): String {
        fun dropLists(r: String, keep: String? = null) =
            lists.filterNot { keep != null && it.equals(keep, ignoreCase = true) }.fold(r) { acc, l -> removeAnyCase(acc, l) }
        return when (to) {
            // From a project its list tags stay (it showed in the project only); from a list it leaves the list.
            is To.Branch -> if (project != null) TaskLine.retag(raw, project, to.tag) else TaskLine.homeTag(dropLists(raw), to.tag)
            is To.InList -> {
                val off = dropLists(project?.let { TaskLine.removeTag(raw, it) } ?: raw, keep = to.tag)
                if (lists.any { it.equals(to.tag, ignoreCase = true) }) off else TaskLine.addTag(off, to.tag)
            }
            is To.NoHome -> {
                val off = dropLists(project?.let { TaskLine.removeTag(raw, it) } ?: raw)
                // Already in that group (`#อ่าน/หนังสือ` on อ่าน): the tag is not written twice.
                val inGroup = TaskLine.parse(off)?.let { Projects.looseTag(it) }
                to.group?.takeIf { it != inGroup }?.let { TaskLine.homeTag(off, it) } ?: off
            }
        }
    }

    /** The list tags on [t], from the [lists] the vault has; compared in any case, as the list pages do. */
    fun listTagsOn(t: Task, lists: Collection<String>): List<String> =
        t.tags.filter { tag -> lists.any { it.equals(tag, ignoreCase = true) } }

    private fun removeAnyCase(raw: String, tag: String): String {
        val exact = Regex(TaskLine.TAG_PATTERN).findAll(raw).firstOrNull { it.groupValues[1].equals(tag, ignoreCase = true) } ?: return raw
        return TaskLine.removeTag(raw, exact.groupValues[1])
    }
}
