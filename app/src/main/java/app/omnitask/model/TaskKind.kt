package app.omnitask.model

/**
 * What kind of task this is, picked from a field in the app. It is stored as a tag at the end of the
 * line's tags so Obsidian and TaskForge still see it, but the owner never has to type it.
 */
enum class TaskKind(val label: String, val tag: String?) {
    NORMAL("ปกติ", null),
    WAITING("มีคนรอ", Focus.WAITING_TAG),
    FUTURE("ลงทุนอนาคต", Focus.FUTURE_TAG),
    SOMEDAY("พักไว้ก่อน", Focus.SOMEDAY_TAG);

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
