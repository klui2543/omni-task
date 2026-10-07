package app.omnitask.model

import java.time.LocalDate

enum class GroupBy(val label: String) {
    DATE("วันที่"), NOTE("โน้ต"), PRIORITY("ความสำคัญ"), TAG("Tag"), STATUS("สถานะ"), NONE("ไม่จัดกลุ่ม"),
}

enum class SortBy(val label: String) {
    DUE("ครบกำหนด"), SCHEDULED("วันนัดทำ"), PRIORITY("ความสำคัญ"), CREATED("วันที่สร้าง"), TITLE("ชื่องาน"),
}

/** How a group header is coloured: overdue groups warn, today stands out, the rest stay quiet. */
enum class Tone { ALERT, ACCENT, PLAIN, MUTED }

data class TaskGroup(val label: String, val tone: Tone, val tasks: List<Task>)

val Status.label
    get() = when (this) {
        Status.TODO -> "ยังไม่เริ่ม"
        Status.IN_PROGRESS -> "กำลังทำ"
        Status.DONE -> "เสร็จ"
        Status.CANCELLED -> "ยกเลิก"
    }

/** Every filter, grouping and sort the task list supports, in the spirit of TaskForge's query options. */
data class TaskQuery(
    val statuses: Set<Status> = setOf(Status.TODO, Status.IN_PROGRESS),
    val priorities: Set<Priority> = emptySet(),
    val tags: Set<String> = emptySet(),
    val notes: Set<String> = emptySet(),
    val bucket: DateBucket? = null,
    val text: String = "",
    val groupBy: GroupBy = GroupBy.DATE,
    val sortBy: SortBy = SortBy.DUE,
    val ascending: Boolean = true,
) {
    /** How many filters are narrowing the list, for the badge on the filter button. */
    val activeFilters: Int
        get() = listOf(
            statuses != DEFAULT.statuses, priorities.isNotEmpty(), tags.isNotEmpty(), notes.isNotEmpty(), bucket != null,
        ).count { it }

    fun matches(t: Task, today: LocalDate): Boolean =
        (statuses.isEmpty() || t.status in statuses) &&
            (priorities.isEmpty() || t.priority in priorities) &&
            // A filter tag also matches its nested tags: #รอ covers #รอ/พี่เอ.
            (tags.isEmpty() || t.tags.any { tag -> tags.any { tag == it || tag.startsWith("$it/") } }) &&
            (notes.isEmpty() || t.noteName in notes) &&
            (bucket == null || t.bucket(today) == bucket) &&
            (text.isBlank() || t.title.contains(text.trim(), ignoreCase = true)) &&
            // Parked work stays out of the way unless asked for by its tag.
            (!Focus.isSomeday(t) || tags.any { it == Focus.SOMEDAY_TAG })

    fun run(tasks: List<Task>, today: LocalDate): List<TaskGroup> {
        val sorted = tasks.filter { matches(it, today) }.sortedWith(comparator())
        return when (groupBy) {
            GroupBy.NONE -> listOf(TaskGroup("ทั้งหมด", Tone.PLAIN, sorted))
            GroupBy.DATE -> DATE_ORDER.map { b ->
                TaskGroup(b.label, toneOf(b), sorted.filter { it.bucket(today) == b })
            }
            GroupBy.PRIORITY -> Priority.entries.map { p -> TaskGroup(p.label, Tone.PLAIN, sorted.filter { it.priority == p }) }
            GroupBy.STATUS -> Status.entries.map { s ->
                TaskGroup(s.label, if (s == Status.IN_PROGRESS) Tone.ACCENT else Tone.PLAIN, sorted.filter { it.status == s })
            }
            GroupBy.NOTE -> sorted.groupBy { it.noteName }.map { (k, v) -> TaskGroup(k, Tone.PLAIN, v) }
            GroupBy.TAG -> sorted.groupBy { it.tags.firstOrNull { t -> !t.startsWith("remind-at-") }?.let { "#$it" } ?: "ไม่มี Tag" }
                .map { (k, v) -> TaskGroup(k, if (k == "ไม่มี Tag") Tone.MUTED else Tone.PLAIN, v) }
        }.filter { it.tasks.isNotEmpty() }
    }

    private fun comparator(): Comparator<Task> {
        val primary: Comparator<Task> = when (sortBy) {
            SortBy.DUE -> compareBy(nullsLast()) { it.due ?: it.scheduled }
            SortBy.SCHEDULED -> compareBy(nullsLast()) { it.scheduled ?: it.due }
            SortBy.PRIORITY -> compareBy { it.priority.ordinal }
            SortBy.CREATED -> compareBy(nullsLast()) { it.created }
            SortBy.TITLE -> compareBy { it.title.lowercase() }
        }
        val ordered = if (ascending) primary else primary.reversed()
        return ordered.thenBy { it.priority.ordinal }.thenBy { it.title }
    }

    companion object {
        val DEFAULT = TaskQuery()
        private val DATE_ORDER = listOf(
            DateBucket.OVERDUE, DateBucket.TODAY, DateBucket.THIS_WEEK, DateBucket.NEXT_WEEK, DateBucket.FUTURE, DateBucket.NO_DATE,
        )
        private fun toneOf(b: DateBucket) = when (b) {
            DateBucket.OVERDUE -> Tone.ALERT
            DateBucket.TODAY -> Tone.ACCENT
            DateBucket.NO_DATE -> Tone.MUTED
            else -> Tone.PLAIN
        }

        /** The quick views above the list. Each one replaces the filters but keeps grouping and sort. */
        val SAVED: List<Pair<String, (TaskQuery) -> TaskQuery>> = listOf(
            "ทั้งหมด" to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = emptySet(), notes = emptySet(), bucket = null) },
            "วันนี้" to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = emptySet(), notes = emptySet(), bucket = DateBucket.TODAY) },
            "มีคนรอ" to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = setOf(Focus.WAITING_TAG), notes = emptySet(), bucket = null) },
            "สำคัญ" to { q -> q.copy(statuses = DEFAULT.statuses, priorities = setOf(Priority.HIGHEST, Priority.HIGH), tags = emptySet(), notes = emptySet(), bucket = null) },
            "ลงทุนอนาคต" to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = setOf(Focus.FUTURE_TAG), notes = emptySet(), bucket = null) },
            "พักไว้" to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = setOf(Focus.SOMEDAY_TAG), notes = emptySet(), bucket = null) },
        )
    }
}
