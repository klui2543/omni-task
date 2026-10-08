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
    val buckets: Set<DateBucket> = emptySet(),
    /** Task types (ปกติ, มีคนรอ, ลงทุนอนาคต, พักไว้ก่อน); empty means every type except parked work. */
    val kinds: Set<TaskKind> = emptySet(),
    /** List notes to show (Bucket list, Watch list...); their items stay out of the views otherwise. */
    val lists: Set<String> = emptySet(),
    /** The views (Kanban, Matrix, Gantt, calendar) leave done and cancelled work out. */
    val hideDone: Boolean = true,
    /** Subtasks normally live inside their parent; the calendar asks for them so dated ones still show. */
    val withSubtasks: Boolean = false,
    val text: String = "",
    val groupBy: GroupBy = GroupBy.DATE,
    val sortBy: SortBy = SortBy.DUE,
    val ascending: Boolean = true,
    /** Further sort levels after [sortBy], e.g. priority then due date. */
    val thenBy: List<Pair<SortBy, Boolean>> = emptyList(),
) {
    /** How many filters are narrowing the list, for the badge on the filter button. */
    val activeFilters: Int
        get() = (if (statuses != DEFAULT.statuses) 1 else 0) + priorities.size + tags.size + notes.size + buckets.size + kinds.size + lists.size

    fun matches(t: Task, today: LocalDate): Boolean =
        (statuses.isEmpty() || t.status in statuses) &&
            (priorities.isEmpty() || t.priority in priorities) &&
            // A filter tag also matches its nested tags: #รอ covers #รอ/พี่เอ.
            (tags.isEmpty() || t.tags.any { tag -> tags.any { tag == it || tag.startsWith("$it/") } }) &&
            (notes.isEmpty() || t.noteName in notes) &&
            (buckets.isEmpty() || t.bucket(today) in buckets) &&
            (withSubtasks || t.parent == null) &&
            (text.isBlank() || t.title.contains(text.trim(), ignoreCase = true) || t.description.contains(text.trim(), ignoreCase = true)) &&
            typeMatches(t)

    /**
     * Types and lists together: with neither chosen, every ordinary task shows (parked work and list items
     * stay out of the way); otherwise a task shows when its type or its list is chosen.
     */
    private fun typeMatches(t: Task): Boolean {
        if (t.list != null) return t.list in lists
        val kind = TaskKind.of(t)
        if (kinds.isEmpty() && lists.isEmpty()) return kind != TaskKind.SOMEDAY || tags.any { it == Focus.SOMEDAY_TAG }
        return kind in kinds
    }

    /** Each active filter as a label and the query without it, for the removable chips under the header. */
    fun activeChips(): List<Pair<String, TaskQuery>> = buildList {
        if (statuses != DEFAULT.statuses) add(statuses.joinToString(", ") { it.label } to copy(statuses = DEFAULT.statuses))
        kinds.forEach { add(it.label to copy(kinds = kinds - it)) }
        lists.forEach { add(it to copy(lists = lists - it)) }
        buckets.forEach { add(it.label to copy(buckets = buckets - it)) }
        priorities.forEach { add(it.label to copy(priorities = priorities - it)) }
        tags.forEach { add("#$it" to copy(tags = tags - it)) }
        notes.forEach { add(it to copy(notes = notes - it)) }
    }

    /** The same filters with nothing chosen, keeping grouping, sorting and the done switch. */
    fun cleared() = copy(statuses = DEFAULT.statuses, priorities = emptySet(), tags = emptySet(), notes = emptySet(), buckets = emptySet(), kinds = emptySet(), lists = emptySet(), text = "")

    /** Only the filter part, as one line for saving a named filter. */
    fun encode(): String = listOf(
        statuses.joinToString(US) { it.name }, priorities.joinToString(US) { it.name }, tags.joinToString(US), notes.joinToString(US),
        buckets.joinToString(US) { it.name }, kinds.joinToString(US) { it.name }, lists.joinToString(US),
    ).joinToString(RS)

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
        private const val RS = "\u001E"
        private const val US = "\u001F"

        /** Lays a saved filter line over [base], keeping its grouping and sorting. */
        fun decode(line: String, base: TaskQuery): TaskQuery {
            val f = line.split(RS) + List(7) { "" }
            fun parts(i: Int) = f[i].split(US).filter { it.isNotEmpty() }
            return base.copy(
                statuses = parts(0).mapNotNull { n -> Status.entries.firstOrNull { it.name == n } }.toSet(),
                priorities = parts(1).mapNotNull { n -> Priority.entries.firstOrNull { it.name == n } }.toSet(),
                tags = parts(2).toSet(),
                notes = parts(3).toSet(),
                buckets = parts(4).mapNotNull { n -> DateBucket.entries.firstOrNull { it.name == n } }.toSet(),
                kinds = parts(5).mapNotNull { n -> TaskKind.entries.firstOrNull { it.name == n } }.toSet(),
                lists = parts(6).toSet(),
            )
        }
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
    }
}
