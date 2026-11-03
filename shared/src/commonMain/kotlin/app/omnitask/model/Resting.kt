package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import app.omnitask.time.*

/**
 * Work that is under way or paused on purpose, so it is not counted or shown as overdue (owner's pick ก for every screen
 * of the mock six-pains, 2026-11-03, pain 3 of the 1 Nov interview: "paused or in-progress work shows red"). Read from
 * what the owner already writes, nothing new to fill in: a task marked in progress (`[/]`), a `ค้าง` line written in the
 * last [DAYS] days, or the #สักวัน tag.
 */
object Resting {
    const val DAYS = 14L

    fun of(t: Task, today: LocalDate): Boolean =
        t.isOpen && (t.status == Status.IN_PROGRESS || Focus.isSomeday(t) || StuckLog.last(t).let { it != null && it >= today.minusDays(DAYS) })

    private fun day(d: LocalDate) = d.dayOfMonth.toString() + " " + Countdown.monthShort(d)

    /** "เดินอยู่: <the last ค้าง reason> (29 ต.ค.)", "พัก: รอคนอื่น (14 ต.ค.)" or "พัก: สักวัน". */
    fun line(t: Task): String {
        val head = if (t.status == Status.IN_PROGRESS) tr("เดินอยู่", "Under way") else tr("พัก", "Paused")
        val last = StuckLog.entries(t).lastOrNull()
        val why = when {
            last != null -> last.reason.ifEmpty { tr("ค้าง", "stuck") } + " (" + day(last.day) + ")"
            Focus.isSomeday(t) -> tr("สักวัน", "someday")
            else -> null
        }
        return if (why == null) head else "$head: $why"
    }

    /**
     * Why an open task waits with the overdue work, never in red: "เลยมา 3 วัน" for a real deadline (📅) gone by, and
     * for a planned day (⏳) gone by, "ตั้งไว้ 27 ต.ค. ส่งภายใน 7 พ.ย." (or without the deadline when it has none).
     */
    fun lateLine(t: Task, today: LocalDate): String {
        val due = t.due
        if (due != null && due < today) return tr("เลยมา ${due.daysUntil(today)} วัน", "${due.daysUntil(today)} days late")
        val planned = t.scheduled ?: return ""
        val set = tr("ตั้งไว้ ", "Planned ") + day(planned)
        return if (due != null) set + tr(" ส่งภายใน ", ", due ") + day(due) else set
    }
}
