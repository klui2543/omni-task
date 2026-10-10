package app.omnitask.model

/**
 * A list note in the vault, like Bucket list or Watch list: plain checkbox lines under a small header
 * the app reads and writes. Categories are tags on the lines, e.g. `- [ ] Shogun #ซีรีส์`.
 *
 * ```
 * ---
 * omni-list: true
 * icon: 🏔️
 * categories: เที่ยว, ประสบการณ์, เรียนรู้
 * tag: bucketlist
 * ---
 * ```
 *
 * Every item carries the list's own [tag], and any task elsewhere in the vault with that tag shows in the
 * list too, so a task can be added to a list without moving it out of TaskForge.
 */
data class OmniList(val name: String, val path: String, val icon: String, val categories: List<String>, val tag: String = tagFor(name)) {

    fun render(): String = buildString {
        appendLine("---")
        appendLine("omni-list: true")
        appendLine("icon: $icon")
        appendLine("categories: ${categories.joinToString(", ")}")
        appendLine("tag: $tag")
        appendLine("---")
        appendLine("# $name")
        appendLine()
    }

    companion object {
        const val FOLDER = "📁 Folder/หลังบ้าน/Omni"

        /** The header of a list note, or null when the note is not a list. */
        fun parse(path: String, text: String): OmniList? {
            if (!text.startsWith("---")) return null
            val end = text.indexOf("\n---", 3).takeIf { it > 0 } ?: return null
            val head = text.substring(3, end).lines().map { it.trim() }
            fun value(key: String) = head.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()
            if (value("omni-list") != "true") return null
            return OmniList(
                name = path.substringAfterLast('/').removeSuffix(".md"),
                path = path,
                icon = value("icon") ?: "📋",
                categories = value("categories")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                tag = value("tag")?.removePrefix("#")?.takeIf { it.isNotBlank() } ?: tagFor(path.substringAfterLast('/').removeSuffix(".md")),
            )
        }

        /** The tag a list goes by when its note does not name one: its name in lower case without spaces ("Watch list" is #watchlist). */
        fun tagFor(name: String): String = name.lowercase().filter { !it.isWhitespace() && it !in "#,.;:!?()[]{}\"'" }

        /** The two lists every owner starts with. */
        fun starters(): List<OmniList> = listOf(
            OmniList("Bucket list", "$FOLDER/Bucket list.md", "🏔️", listOf("เที่ยว", "ประสบการณ์", "เรียนรู้")),
            OmniList("Watch list", "$FOLDER/Watch list.md", "🎬", listOf("หนัง", "ซีรีส์", "หนังสือ")),
        )
    }
}
