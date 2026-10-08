package app.omnitask.model

/**
 * What kind of task this is, picked from a field in the app. It is stored as a tag at the end of the
 * line's tags so Obsidian and TaskForge still see it, but the owner never has to type it.
 */
enum class TaskKind(private val th: String, private val en: String, val tag: String?) {
    NORMAL("ปกติ", "Normal", null),
    WAITING("มีคนรอ", "Waiting", Focus.WAITING_TAG),
    FUTURE("ลงทุนอนาคต", "Future", Focus.FUTURE_TAG),
    SOMEDAY("พักไว้ก่อน", "Someday", Focus.SOMEDAY_TAG);

    val label get() = tr(th, en)

    companion object {
        fun of(task: Task): TaskKind = when {
            Focus.isSomeday(task) -> SOMEDAY
            Focus.isWaiting(task) -> WAITING
            Focus.isFutureWork(task) -> FUTURE
            else -> NORMAL
        }

        /** Every tag that marks a kind, including `#รอ/name`. */
        fun kindTags(task: Task): List<String> = task.tags.filter { tag ->
            entries.mapNotNull { it.tag }.any { tag == it || tag.startsWith("$it/") }
        }
    }
}

/**
 * A kind the owner made, like "อ่าน" or "งานบ้าน": a name, an emoji and the tag that marks it. Kept in the
 * synced settings as "name\temoji\ttag".
 */
data class CustomKind(val name: String, val emoji: String, val tag: String) {
    val label get() = if (emoji.isBlank()) name else "$emoji $name"

    companion object {
        fun parse(saved: Set<String>): List<CustomKind> = saved.mapNotNull { e ->
            val p = e.split('\t')
            if (p.size != 3 || p[0].isBlank() || p[2].isBlank()) null else CustomKind(p[0], p[1], p[2])
        }.sortedBy { it.name.lowercase() }

        fun encode(kinds: List<CustomKind>): Set<String> = kinds.map { "${it.name}\t${it.emoji}\t${it.tag}" }.toSet()

        /** The tag for a new kind: its name without spaces. */
        fun tagFor(name: String): String = name.trim().filter { !it.isWhitespace() && it !in "#,.;:!?()[]{}\"'" }

        /** The owner's kind a task carries, if any. */
        fun of(task: Task, kinds: List<CustomKind>): CustomKind? =
            kinds.firstOrNull { k -> task.tags.any { it.equals(k.tag, ignoreCase = true) } }
    }
}
