package app.omnitask.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * Rule-based daily focus, no AI. Three sections that never compete with each other:
 * - must: due today or overdue (or scheduled today)
 * - waiting: someone is waiting (`#รอ` or `#รอ/name`), longest-waiting first
 * - future: work the owner picked as an investment (#อนาคต); it never shouts, so it always gets a slot
 */
object Focus {

    const val WAITING_TAG = "รอ"
    const val FUTURE_TAG = "อนาคต"
    const val SOMEDAY_TAG = "สักวัน"

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

    enum class Kind { RAISE_PRIORITY, SOFT_DATE, MARK_FUTURE }

    fun isWaiting(task: Task) = task.tags.any { it == WAITING_TAG || it.startsWith("$WAITING_TAG/") }

    fun waitingFor(task: Task): String? =
        task.tags.firstOrNull { it.startsWith("$WAITING_TAG/") }?.substringAfter('/')?.ifEmpty { null }

    /** Days since the task was created (➕); null when the line has no created date. */
    fun ageDays(task: Task, today: LocalDate): Long? = task.created?.let { ChronoUnit.DAYS.between(it, today) }

    /** Picked by the owner as work for the future (#อนาคต), not guessed from priority. */
    fun isFutureWork(task: Task) = task.tags.any { it == FUTURE_TAG || it.startsWith("$FUTURE_TAG/") } && !isSomeday(task)

    /** Parked on purpose (#สักวัน): out of the daily lists, but the review brings it back now and then. */
    fun isSomeday(task: Task) = task.tags.any { it == SOMEDAY_TAG || it.startsWith("$SOMEDAY_TAG/") }

    /** How often a task without a deadline comes up for review, in days. */
    fun reviewEvery(task: Task) = when {
        isFutureWork(task) -> 14L
        else -> 30L
    }

    /**
     * Open tasks with no deadline whose review is due: they were made (or last reviewed) longer ago
     * than [reviewEvery]. Oldest first, so the longest-ignored work gets looked at first.
     */
    fun toReview(tasks: List<Task>, today: LocalDate, reviewed: Map<String, LocalDate>): List<Task> =
        tasks.filter { t -> t.isOpen && t.parent == null && t.due == null && !isWaiting(t) && (t.scheduled == null || t.scheduled < today) }
            .mapNotNull { t ->
                val base = listOfNotNull(t.created, reviewed[t.title]).maxOrNull()
                if (base == null || ChronoUnit.DAYS.between(base, today) >= reviewEvery(t)) t to (base ?: LocalDate.EARLIEST) else null
            }
            .sortedBy { it.second }
            .map { it.first }

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

        // Everyone waiting, due today or not: the card shows them all, longest wait first.
        val waiting = open
            .filter { isWaiting(it) && !isSomeday(it) }
            .sortedWith(compareByDescending<Task> { ageDays(it, today) ?: 0 }.thenBy { it.priority.ordinal })

        // Most-neglected first: in-progress work is already moving, so it goes last; then oldest first.
        val future = open
            .filter { isFutureWork(it) && it !in must && it.title !in skippedToday }
            .sortedWith(
                compareBy<Task>({ it.status == Status.IN_PROGRESS }, { it.created ?: LocalDate.EARLIEST }, { it.priority.ordinal }),
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
            add(
                tr(
                    "3 วันที่ผ่านมาทำงานไป ${recentDone.size} งาน แต่ยังไม่แตะงานที่มีคนรอเลย$names",
                    "You finished ${recentDone.size} tasks in the last 3 days but none that people are waiting on$names",
                ),
            )
        }

        val futureWork = tasks.filter { isFutureWork(it) }
        val touched = futureWork.any { t -> t.status == Status.IN_PROGRESS || t.done?.let { it > weekAgo } == true }
        if (futureWork.any { it.isOpen } && !touched) {
            add(tr("7 วันแล้วยังไม่ได้ลงทุนกับงานเพื่ออนาคตเลย", "No time spent on future work in 7 days"))
        }
    }

    private fun suggestions(open: List<Task>, today: LocalDate): List<Suggestion> = buildList {
        open.filter { isWaiting(it) && !it.isImportant && (ageDays(it, today) ?: 0) >= 7 }.forEach {
            add(Suggestion(it, Kind.RAISE_PRIORITY, tr("มีคนรอมา ${ageDays(it, today)} วันแล้ว ยกเป็นสำคัญ?", "Waiting for ${ageDays(it, today)} days. Mark as important?")))
        }
        open.filter { isFutureWork(it) && it.scheduled == null && it.status != Status.IN_PROGRESS && (ageDays(it, today) ?: 0) >= 30 }
            .forEach {
                add(Suggestion(it, Kind.SOFT_DATE, tr("ค้างมา ${ageDays(it, today)} วัน นัดทำเสาร์นี้?", "Open for ${ageDays(it, today)} days. Schedule it this Saturday?")))
            }
        // Important work with no deadline that nobody waits on is what "ลงทุนอนาคต" is for; offer it, never assume it.
        open.filter { it.isImportant && it.due == null && !isWaiting(it) && !isFutureWork(it) && !isSomeday(it) && (ageDays(it, today) ?: 0) >= 14 }
            .take(2)
            .forEach { add(Suggestion(it, Kind.MARK_FUTURE, tr("สำคัญแต่ไม่มีเดดไลน์ ตั้งเป็นงานลงทุนอนาคตไหม?", "Important but no deadline. Make it future work?"))) }
    }

    /**
     * Every open task in the order it should be done, with the reason it sits there: late work, then what is
     * due soon, people waiting (longest first), today's plan, importance, and work for the future.
     */
    fun rank(tasks: List<Task>, today: LocalDate): List<Pair<Task, String>> =
        tasks.filter { it.isOpen && !isSomeday(it) }.map { t ->
            val due = t.due
            val late = due?.let { ChronoUnit.DAYS.between(it, today) }?.takeIf { it > 0 }
            val left = due?.let { ChronoUnit.DAYS.between(today, it) }?.takeIf { it >= 0 }
            val age = ageDays(t, today) ?: 0
            var score = when (t.priority) {
                Priority.HIGHEST -> 30L
                Priority.HIGH -> 20L
                Priority.MEDIUM -> 10L
                Priority.LOW -> -5L
                Priority.LOWEST -> -10L
                else -> 0L
            }
            val reason = when {
                late != null -> { score += 100 + late * 2; tr("เลยกำหนดมา $late วัน", "$late days overdue") }
                left == 0L -> { score += 80; tr("ครบวันนี้", "Due today") }
                left != null && left <= 3 -> { score += 60 - left * 5; tr("ครบในอีก $left วัน", "Due in $left days") }
                isWaiting(t) -> { score += 40 + age; (waitingFor(t) ?: tr("มีคน", "Someone")) + tr(" รอมา $age วัน", " waiting $age days") }
                t.scheduled == today -> { score += 50; tr("นัดทำวันนี้", "Scheduled today") }
                left != null -> { score += 30 - minOf(left, 25); tr("ครบในอีก $left วัน", "Due in $left days") }
                isFutureWork(t) -> { score += 15 + minOf(age, 30) / 3; tr("ลงทุนอนาคต", "Future work") }
                else -> tr("ไม่มีเดดไลน์", "No deadline")
            }
            if (t.status == Status.IN_PROGRESS) score += 10
            Triple(t, reason, score)
        }.sortedByDescending { it.third }.map { it.first to it.second }

    /** The coming Saturday (today if today is Saturday): the default soft date for neglected future work. */
    fun softDate(today: LocalDate): LocalDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
}
