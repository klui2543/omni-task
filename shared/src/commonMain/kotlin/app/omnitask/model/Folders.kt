package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlinx.datetime.daysUntil

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
        val label = LABELS.any { it.equals(root, ignoreCase = true) }
        return !Projects.isPlain(tag) || (label && Projects.chosen?.any { it.equals(root, ignoreCase = true) } != true)
    }

    /** The folder a task sits in: its project tag (branch and all), else its first tag that is not a label. */
    fun of(t: Task): String? = ofTags(t.tags)

    /** The same for tags not yet on a line (a new task): a tag of a chosen project first, else the first non-label. */
    fun ofTags(tags: List<String>): String? {
        val made = Projects.chosen
        return tags.firstOrNull { !isLabel(it) && (made == null || it.substringBefore('/') in made) }
            ?: tags.firstOrNull { !isLabel(it) }
    }

    /** A folder as typed, tidied the way a line writes tags: no empty levels, no spaces around a slash. */
    fun clean(input: String): String =
        input.trim().removePrefix("#").split('/').map { TaskLine.normalizeTag(it.trim()) }.filter { it.isNotEmpty() }.joinToString("/")

    /**
     * A new task's tags with [folder] as its one folder, in front where the project tag goes; the folder it had
     * leaves, and its labels stay. A null [folder] leaves only the labels.
     */
    fun withFolder(tags: List<String>, folder: String?): List<String> {
        val name = folder?.let(::clean)?.takeIf { it.isNotEmpty() }
        // A label typed where a folder goes joins the labels; the folder stays.
        if (name != null && isLabel(name)) return if (tags.any { it.equals(name, ignoreCase = true) }) tags else tags + name
        val old = ofTags(tags)
        return listOfNotNull(name) + tags.filter { it != old && !it.equals(name, ignoreCase = true) }
    }

    /** Every folder, with every folder on the way to it (`a`, `a/b`, `a/b/c`), from the tasks and the [branches] made by hand. */
    fun paths(tasks: List<Task>, branches: Set<String>): List<String> =
        (tasks.filter { it.status != Status.CANCELLED }.mapNotNull(::of) + branches.map(ProjectNames::canonical))
            .flatMap { p -> p.split('/').indices.map { i -> p.split('/').take(i + 1).joinToString("/") } }
            .filter { it.isNotEmpty() }.toSet().sortedBy { it.lowercase() }

    /**
     * The folder picker's "ใช้บ่อย" row (owner's pick, 2026-10-30, mock way ค: used often, by itself, no pins): the
     * folders of [all] that got the most tasks made in the last [days] days, with that count, most first. Folders
     * picked lately ([picked], newest first) follow, so a new folder shows before it has tasks; a tie keeps that order.
     */
    fun frequent(tasks: List<Task>, today: kotlinx.datetime.LocalDate, picked: List<String>, all: List<String>, days: Int = 30, n: Int = 6): List<Pair<String, Int>> {
        fun known(p: String) = all.firstOrNull { it.equals(ProjectNames.canonical(p), ignoreCase = true) }
        val counts = tasks.asSequence()
            .filter { t -> t.created?.let { c -> c <= today && c.daysUntil(today) < days } == true }
            .mapNotNull { t -> of(t)?.let(::known) }
            .groupingBy { it }.eachCount()
        val lately = picked.mapNotNull(::known).distinct()
        return (counts.keys + lately).distinct()
            .sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { lately.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it.lowercase() })
            .take(n).map { it to (counts[it] ?: 0) }
    }

    /** The folders the owner picked lately, newest first, after picking [path]: what "used lately" offers. */
    fun picked(list: List<String>, path: String, n: Int = 3): List<String> =
        (listOf(path) + list.filterNot { it.equals(path, ignoreCase = true) }).take(n)

    /** Folders whose path holds [query]: those whose own name holds it first, then those matching higher up. */
    fun search(paths: List<String>, query: String): List<String> {
        val q = query.trim().removePrefix("#")
        if (q.isEmpty()) return paths
        val byName = paths.filter { it.substringAfterLast('/').contains(q, ignoreCase = true) }
        return byName + paths.filter { it !in byName && it.contains(q, ignoreCase = true) }
    }
}
