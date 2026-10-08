package app.omnitask.model

/** How Obsidian names notes in `[[links]]`, and how to find a linked note again. */
object NoteLinks {

    private fun base(path: String) = path.substringAfterLast('/').removeSuffix(".md")

    /** The shortest link text Obsidian would write: the note name, or its path when the name is not unique. */
    fun linkText(path: String, all: List<String>): String {
        val name = base(path)
        return if (all.count { base(it) == name } > 1) path.removeSuffix(".md") else name
    }

    /** The note a link points to, or null when it is not in the vault (yet). */
    fun resolve(link: String, all: List<String>): String? {
        val target = link.removeSuffix(".md")
        return all.firstOrNull { it.removeSuffix(".md") == target }
            ?: all.firstOrNull { base(it) == target.substringAfterLast('/') && it.removeSuffix(".md").endsWith(target) }
    }

    /** Notes matching the search text, names first, then paths. */
    fun search(text: String, all: List<String>, limit: Int = 60): List<String> {
        val q = text.trim()
        if (q.isEmpty()) return all.sortedBy { base(it).lowercase() }.take(limit)
        val byName = all.filter { base(it).contains(q, ignoreCase = true) }.sortedBy { base(it).length }
        val byPath = all.filter { it !in byName && it.contains(q, ignoreCase = true) }
        return (byName + byPath).take(limit)
    }

    fun displayName(path: String) = base(path)
}
