package app.omnitask.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Rule-based daily focus, no AI. Three sections that never compete with each other:
 * - must: due today or overdue (or scheduled today)
 * - waiting: someone is waiting (`#รอ` or `#รอ/name`), longest-waiting first
 * - future: important, no due date, nobody waiting — the work that never shouts; always gets a slot
 */
object Focus {

    const val WAITING_TAG = "รอ"

    data class Brief(
        val must: List<Task>,
        val waiting: List<Task>,
        val future: List<Task>,
        val warnings: List<String>,
        val suggestions: List<Suggestion>,
    )

    data class Suggestion(val task: Task, val kind: Kind, val text: String) {
        /** Stable across line moves, so a dismissal sticks even if the file is reordered. */
        val id get() = "${kind.name}:${task.title}"
    }

    enum class Kind { RAISE_PRIORITY, SOFT_DATE }

    fun isWaiting(task: Task) = task.tags.any { it == WAITING_TAG || it.startsWith("$WAITING_TAG/") }

    fun waitingFor(task: Task): String? =
        task.tags.firstOrNull { it.startsWith("$WAITING_TAG/") }?.substringAfter('/')?.ifEmpty { null }

    /** Days since the task was created (➕); null when the line has no created date. */
    fun ageDays(task: Task, today: LocalDate): Long? = task.created?.let { ChronoUnit.DAYS.between(it, today) }

    fun isFutureWork(task: Task) = task.isImportant && task.due == null && !isWaiting(task)

    fun build(
        tasks: List<Task>,
        today: LocalDate,
        futureCount: Int = 1,
        skippedToday: Set<String> = emptySet(),
        dismissed: Set<String> = emptySet(),
    ): Brief {
        val open = tasks.filter { it.isOpen }

        val must = open
            .filter { t -> t.due?.let { it <= today } == true || t.scheduled == today }
            .sortedWith(compareBy<Task>({ it.due ?: it.scheduled }, { it.priority.ordinal }))

        val waiting = open
            .filter { isWaiting(it) && it !in must }
            .sortedWith(compareByDescending<Task> { ageDays(it, today) ?: 0 }.thenBy { it.priority.ordinal })
            .take(2)

        // Most-neglected first: in-progress work is already moving, so it goes last; then oldest first.
        val future = open
            .filter { isFutureWork(it) && it !in must && it.title !in skippedToday }
            .sortedWith(
                compareBy<Task>({ it.status == Status.IN_PROGRESS }, { it.created ?: LocalDate.MIN }, { it.priority.ordinal }),
            )
            .take(futureCount.coerceAtLeast(0))

        return Brief(must, waiting, future, warnings(tasks, today), suggestions(open, today).filter { it.id !in dismissed })
    }

    private fun warnings(tasks: List<Task>, today: LocalDate): List<String> = buildList {
        val open = tasks.filter { it.isOpen }
        val weekAgo = today.minusDays(7)
        val recentDone = tasks.filter { t -> t.done?.let { it > today.minusDays(3) } == true }

        // Pushing showcase work while people wait: done things lately, none of them for anyone waiting.
        val staleWaiting = open.filter { isWaiting(it) && (ageDays(it, today) ?: 0) >= 3 }
        if (staleWaiting.isNotEmpty() && recentDone.isNotEmpty() && recentDone.none { isWaiting(it) }) {
            val who = staleWaiting.mapNotNull { waitingFor(it) }.distinct()
            val names = if (who.isEmpty()) "" else " (" + who.joinToString(", ") + ")"
            add("3 วันที่ผ่านมาทำงานไป ${recentDone.size} งาน แต่ยังไม่แตะงานที่มีคนรอเลย$names")
        }

        val futureWork = tasks.filter { isFutureWork(it) }
        val touched = futureWork.any { t -> t.status == Status.IN_PROGRESS || t.done?.let { it > weekAgo } == true }
        if (futureWork.any { it.isOpen } && !touched) {
            add("7 วันแล้วยังไม่ได้ลงทุนกับงานเพื่ออนาคตเลย")
        }
    }

    private fun suggestions(open: List<Task>, today: LocalDate): List<Suggestion> = buildList {
        open.filter { isWaiting(it) && !it.isImportant && (ageDays(it, today) ?: 0) >= 7 }.forEach {
            add(Suggestion(it, Kind.RAISE_PRIORITY, "มีคนรอมา ${ageDays(it, today)} วันแล้ว ยกเป็นสำคัญ (⏫)?"))
        }
        open.filter { isFutureWork(it) && it.scheduled == null && it.status != Status.IN_PROGRESS && (ageDays(it, today) ?: 0) >= 30 }
            .forEach {
                add(Suggestion(it, Kind.SOFT_DATE, "ค้างมา ${ageDays(it, today)} วัน นัดทำ (⏳) เสาร์นี้?"))
            }
    }

    /** The coming Saturday (today if today is Saturday): the default soft date for neglected future work. */
    fun softDate(today: LocalDate): LocalDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
}
