package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import app.omnitask.time.*

/** How far away a task's deadline is, for the countdown the owner pins on the Focus screen. */
object Countdown {

    /** "อีก 12 วัน", "อีก 3 ชม. 20 นาที", "วันนี้" or "เลยมา 2 วัน"; null when the task has no date. */
    fun text(task: Task, now: LocalDateTime): String? {
        val due = task.due ?: task.scheduled ?: return null
        val today = now.toLocalDate()
        val at = task.reminderAt?.takeIf { it.toLocalDate() == due }
        if (due == today && at != null) {
            val mins = ChronoUnit.MINUTES.between(now, at)
            return when {
                mins < 0 -> tr("เลยเวลามา ${hm(-mins)}", "${hm(-mins, false)} ago")
                else -> tr("อีก ${hm(mins)}", "in ${hm(mins, false)}")
            }
        }
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days == 0L -> tr("วันนี้", "today")
            days == 1L -> tr("พรุ่งนี้", "tomorrow")
            days > 0 -> tr("อีก $days วัน", "in $days days")
            else -> tr("เลยมา ${-days} วัน", "${-days} days ago")
        }
    }

    /** The date the countdown runs to, for sorting the picker. */
    fun target(task: Task): LocalDate? = task.due ?: task.scheduled

    private fun hm(minutes: Long, thai: Boolean = true): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (thai) {
            if (h > 0) "$h ชม. $m นาที" else "$m นาที"
        } else {
            if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }
}
