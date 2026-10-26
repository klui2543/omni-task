package app.omnitask.model

import app.omnitask.data.TaskLine

/**
 * Tags seen as folders, the way files sit in folders on a computer (owner's pick, 2026-10-26, mock way 1). Nothing
 * new is written: a folder is the project tag a task already carries (`#Siriraj/ตารางเวรเภสัช/wording`), and a task
 * sits in one folder. Tags that say who acts or what kind of task it is (`#kluido`, `#รอ`, `#สักวัน`...) are labels:
 * a task can carry several, and they never count as a folder.
 */
object Folders {

    /** Tags that mark who acts or what kind of task it is, unless the owner made one of them a project. */
    val LABELS = setOf(SecretaryBook.OWNER_TAG, "claudedo", Focus.WAITING_TAG, Focus.SOMEDAY_TAG, Focus.FUTURE_TAG)

    fun isLabel(tag: String): Boolean {
        val root = tag.substringBefore('/')
        return !Projects.isPlain(tag) || (root in LABELS && Projects.chosen?.contains(root) != true)
    }

    /** The folder a task sits in: its project tag (branch and all), else its first tag that is not a label. */
    fun of(t: Task): String? = Projects.tagOf(t)?.takeUnless(::isLabel) ?: t.tags.firstOrNull { !isLabel(it) }

    /**
     * A new task's tags with [folder] as its one folder, in front where the project tag goes; the folder it had
     * leaves, and its labels stay. A null [folder] leaves only the labels.
     */
    fun withFolder(tags: List<String>, folder: String?): List<String> {
        val name = folder?.let(TaskLine::normalizeTag)?.takeIf { it.isNotEmpty() }
        val old = tags.firstOrNull { !isLabel(it) }
        return listOfNotNull(name) + tags.filter { it != old && !it.equals(name, ignoreCase = true) }
    }

    /** Every folder, with every folder on the way to it (`a`, `a/b`, `a/b/c`), from the tasks and the [branches] made by hand. */
    fun paths(tasks: List<Task>, branches: Set<String>): List<String> =
        (tasks.filter { it.status != Status.CANCELLED }.mapNotNull(::of) + branches)
            .flatMap { p -> p.split('/').indices.map { i -> p.split('/').take(i + 1).joinToString("/") } }
            .filter { it.isNotEmpty() }.toSet().sortedBy { it.lowercase() }

    /** The folders of the newest tasks, newest first: where the owner is putting things these days. */
    fun recent(tasks: List<Task>, n: Int): List<String> =
        tasks.filter { it.created != null }.sortedByDescending { it.created }.mapNotNull(::of).distinct().take(n)

    /** Folders whose path holds [query]: those whose own name holds it first, then those matching higher up. */
    fun search(paths: List<String>, query: String): List<String> {
        val q = query.trim().removePrefix("#")
        if (q.isEmpty()) return paths
        val byName = paths.filter { it.substringAfterLast('/').contains(q, ignoreCase = true) }
        return byName + paths.filter { it !in byName && it.contains(q, ignoreCase = true) }
    }
}
