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
