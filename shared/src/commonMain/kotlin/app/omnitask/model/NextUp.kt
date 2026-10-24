package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The one task Focus offers to do next, so the day starts from a single thing instead of a list to read.
 *
 * Order: a real deadline (📅) already past or today, then work already started, then what was planned for today
 * (⏳ today), then an own target (⏳) that slipped. Within one reason, higher priority first, then the earlier date.
 * Tasks waiting on another open task, list items and tasks skipped for today are left out.
 */
object NextUp {
    enum class Why { DUE_LATE, DUE_TODAY, DOING, PLANNED_TODAY, TARGET_LATE }

    data class Pick(val task: Task, val why: Why, val days: Int = 0) {
        /** Why this one, in a few words. */
        val reason: String get() = when (why) {
            Why.DUE_LATE -> tr("เลยกำหนดจริงมา $days วัน", "Deadline passed $days days ago")
            Why.DUE_TODAY -> tr("กำหนดจริงวันนี้", "Deadline today")
            Why.DOING -> tr("ทำค้างไว้", "Already started")
            Why.PLANNED_TODAY -> tr("ตั้งใจทำวันนี้", "Planned for today")
            Why.TARGET_LATE -> tr("เป้าที่ตั้งเองเลยมา $days วัน", "Own target slipped $days days")
        }
    }

    fun of(t: Task, today: LocalDate): Pick? {
        if (!t.isOpen || t.list != null) return null
        val due = t.due
        val planned = t.scheduled
        return when {
            due != null && due < today -> Pick(t, Why.DUE_LATE, due.daysUntil(today))
            due == today -> Pick(t, Why.DUE_TODAY)
            t.status == Status.IN_PROGRESS -> Pick(t, Why.DOING)
            planned == today -> Pick(t, Why.PLANNED_TODAY)
            planned != null && planned < today -> Pick(t, Why.TARGET_LATE, planned.daysUntil(today))
            else -> null
        }
    }

    fun ranked(tasks: List<Task>, today: LocalDate, skipped: Set<String> = emptySet()): List<Pick> {
        val blocked = Projects.blocked(tasks)
        return tasks.asSequence()
            .filter { it.key !in skipped && it !in blocked }
            .mapNotNull { of(it, today) }
            .sortedWith(
                compareBy<Pick>({ it.why.ordinal }, { it.task.priority.ordinal }, { it.task.due ?: it.task.scheduled ?: LocalDate(9999, 12, 31) }),
            )
            .toList()
    }

    /** A slipped own target (⏳ past, no 📅 past) is not urgent: the new look shows it quietly instead of in red. */
    fun onlyTargetLate(t: Task, today: LocalDate): Boolean =
        t.isOpen && t.due.let { it == null || it >= today } && t.scheduled.let { it != null && it < today }
}
