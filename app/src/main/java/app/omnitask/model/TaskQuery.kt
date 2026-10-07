package app.omnitask.model

import java.time.LocalDate

enum class GroupBy(private val th: String, private val en: String) {
    DATE("วันที่", "Date"), NOTE("โน้ต", "Note"), PRIORITY("ความสำคัญ", "Priority"), TAG("Tag", "Tag"), STATUS("สถานะ", "Status"),
    NONE("ไม่จัดกลุ่ม", "None"),
    ;

    val label get() = tr(th, en)
}

enum class SortBy(private val th: String, private val en: String) {
    DUE("ครบกำหนด", "Due"), SCHEDULED("วันนัดทำ", "Scheduled"), START("วันเริ่ม", "Start"), PRIORITY("ความสำคัญ", "Priority"),
    CREATED("วันที่สร้าง", "Created"), STATUS("สถานะ", "Status"), PROJECT("โปรเจกต์", "Project"), NOTE("โน้ต", "Note"),
    TITLE("ชื่องาน", "Title"),
    ;

    val label get() = tr(th, en)
}

/** How a group header is coloured: overdue groups warn, today stands out, the rest stay quiet. */
enum class Tone { ALERT, ACCENT, PLAIN, MUTED }

data class TaskGroup(val label: String, val tone: Tone, val tasks: List<Task>)

val Status.label
    get() = when (this) {
        Status.TODO -> tr("ยังไม่เริ่ม", "To do")
        Status.IN_PROGRESS -> tr("กำลังทำ", "In progress")
        Status.DONE -> tr("เสร็จ", "Done")
        Status.CANCELLED -> tr("ยกเลิก", "Cancelled")
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
    /** Further sort levels after [sortBy], e.g. priority then due date. */
    val thenBy: List<Pair<SortBy, Boolean>> = emptyList(),
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
            GroupBy.NONE -> listOf(TaskGroup(tr("ทั้งหมด", "All"), Tone.PLAIN, sorted))
            GroupBy.DATE -> DATE_ORDER.map { b ->
                TaskGroup(b.label, toneOf(b), sorted.filter { it.bucket(today) == b })
            }
            GroupBy.PRIORITY -> Priority.entries.map { p -> TaskGroup(p.label, Tone.PLAIN, sorted.filter { it.priority == p }) }
            GroupBy.STATUS -> Status.entries.map { s ->
                TaskGroup(s.label, if (s == Status.IN_PROGRESS) Tone.ACCENT else Tone.PLAIN, sorted.filter { it.status == s })
            }
            GroupBy.NOTE -> sorted.groupBy { it.noteName }.map { (k, v) -> TaskGroup(k, Tone.PLAIN, v) }
            GroupBy.TAG -> {
                val none = tr("ไม่มี Tag", "No tag")
                sorted.groupBy { it.tags.firstOrNull { t -> !t.startsWith("remind-at-") }?.let { "#$it" } ?: none }
                    .map { (k, v) -> TaskGroup(k, if (k == none) Tone.MUTED else Tone.PLAIN, v) }
            }
        }.filter { it.tasks.isNotEmpty() }
    }

    /** Every sort level in order: the main one, then each "then by". */
    val sorts: List<Pair<SortBy, Boolean>> get() = listOf(sortBy to ascending) + thenBy.filter { it.first != sortBy }

    private fun comparator(): Comparator<Task> {
        var c: Comparator<Task> = Comparator { _, _ -> 0 }
        sorts.forEach { (by, asc) -> c = c.then(level(by, asc)) }
        return c.thenBy { it.priority.ordinal }.thenBy { it.title }
    }

    /** One sort level. Tasks missing the value (no date, no project) always go last, whichever the direction. */
    private fun level(by: SortBy, asc: Boolean): Comparator<Task> {
        fun key(get: (Task) -> Comparable<*>?) = Comparator<Task> { a, b ->
            val x = get(a)
            val y = get(b)
            when {
                x == null && y == null -> 0
                x == null -> 1
                y == null -> -1
                asc -> compareValues(x, y)
                else -> compareValues(y, x)
            }
        }
        return when (by) {
            SortBy.DUE -> key { it.due ?: it.scheduled }
            SortBy.SCHEDULED -> key { it.scheduled ?: it.due }
            SortBy.START -> key { it.start ?: it.scheduled }
            SortBy.PRIORITY -> key { it.priority.ordinal }
            SortBy.CREATED -> key { it.created }
            SortBy.STATUS -> key { STATUS_ORDER.indexOf(it.status) }
            SortBy.PROJECT -> key { Projects.projectOf(it)?.lowercase() }
            SortBy.NOTE -> key { it.noteName.lowercase() }
            SortBy.TITLE -> key { it.title.lowercase() }
        }
    }

    companion object {
        val DEFAULT = TaskQuery()
        private val STATUS_ORDER = listOf(Status.IN_PROGRESS, Status.TODO, Status.DONE, Status.CANCELLED)
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
        val SAVED: List<Pair<String, (TaskQuery) -> TaskQuery>> get() = listOf(
            tr("ทั้งหมด", "All") to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = emptySet(), notes = emptySet(), bucket = null) },
            tr("วันนี้", "Today") to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = emptySet(), notes = emptySet(), bucket = DateBucket.TODAY) },
            tr("มีคนรอ", "Waiting") to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = setOf(Focus.WAITING_TAG), notes = emptySet(), bucket = null) },
            tr("สำคัญ", "Important") to { q -> q.copy(statuses = DEFAULT.statuses, priorities = setOf(Priority.HIGHEST, Priority.HIGH), tags = emptySet(), notes = emptySet(), bucket = null) },
            tr("ลงทุนอนาคต", "Future") to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = setOf(Focus.FUTURE_TAG), notes = emptySet(), bucket = null) },
            tr("พักไว้", "Someday") to { q -> q.copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = setOf(Focus.SOMEDAY_TAG), notes = emptySet(), bucket = null) },
        )
    }
}
