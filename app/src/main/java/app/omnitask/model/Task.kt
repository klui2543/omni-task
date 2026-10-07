package app.omnitask.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class Priority(val emoji: String?, val label: String) {
    HIGHEST("🔺", "สูงสุด"),
    HIGH("⏫", "สูง"),
    MEDIUM("🔼", "กลาง"),
    NONE(null, "ปกติ"),
    LOW("🔽", "ต่ำ"),
    LOWEST("⏬", "ต่ำสุด"),
}

enum class Status { TODO, IN_PROGRESS, DONE, CANCELLED }

/** Which date a TaskForge reminder time hangs on: `⏰ HH:mm` → due, `🎯 HH:mm` → scheduled. */
enum class ReminderOn { DUE, SCHEDULED }

/** One checkbox line in a vault note. [raw] is the line exactly as it stands in the file. */
data class Task(
    val raw: String,
    val title: String,
    val status: Status,
    val priority: Priority = Priority.NONE,
    val created: LocalDate? = null,
    val start: LocalDate? = null,
    val scheduled: LocalDate? = null,
    val due: LocalDate? = null,
    val done: LocalDate? = null,
    val recurrence: String? = null,
    val reminderTime: LocalTime? = null,
    val reminderOn: ReminderOn? = null,
    val tags: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val filePath: String = "",
    val fileUri: String = "",
    val lineIndex: Int = -1,
) {
    val isOpen get() = status == Status.TODO || status == Status.IN_PROGRESS
    val noteName get() = filePath.substringAfterLast('/').removeSuffix(".md")
    val key get() = "$fileUri#$lineIndex"

    /** When this task's reminder fires, or null when it has no time or no date to hang it on. */
    val reminderAt: LocalDateTime?
        get() {
            val time = reminderTime ?: return null
            val day = when (reminderOn) {
                ReminderOn.SCHEDULED -> scheduled ?: due
                else -> due ?: scheduled
            } ?: return null
            return day.atTime(time)
        }
}

enum class DateBucket(val label: String) {
    TODAY("วันนี้"),
    OVERDUE("เลยกำหนด"),
    THIS_WEEK("สัปดาห์นี้"),
    NEXT_WEEK("สัปดาห์หน้า"),
    FUTURE("อนาคต"),
    NO_DATE("ไม่มีวันที่"),
}

/** Weeks run Monday to Sunday. */
fun endOfWeek(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

fun Task.bucket(today: LocalDate): DateBucket {
    if (due == today || scheduled == today) return DateBucket.TODAY
    val d = due ?: scheduled ?: start ?: return DateBucket.NO_DATE
    val weekEnd = endOfWeek(today)
    return when {
        d < today -> DateBucket.OVERDUE
        d <= weekEnd -> DateBucket.THIS_WEEK
        d <= weekEnd.plusDays(7) -> DateBucket.NEXT_WEEK
        else -> DateBucket.FUTURE
    }
}

enum class UrgentRule(val label: String) {
    TWO_DAYS("ภายใน 2 วัน"),
    THREE_DAYS("ภายใน 3 วัน"),
    THIS_WEEK("ภายในสัปดาห์นี้"),
}

enum class Quadrant(val label: String, val urgent: Boolean, val important: Boolean) {
    DO("ด่วนและสำคัญ", urgent = true, important = true),
    PLAN("ไม่ด่วนแต่สำคัญ", urgent = false, important = true),
    QUICK("ด่วนแต่ไม่สำคัญ", urgent = true, important = false),
    LATER("ไม่ด่วนไม่สำคัญ", urgent = false, important = false),
}

fun Task.isUrgent(today: LocalDate, rule: UrgentRule): Boolean {
    val d = due ?: scheduled ?: return false
    val limit = when (rule) {
        UrgentRule.TWO_DAYS -> today.plusDays(2)
        UrgentRule.THREE_DAYS -> today.plusDays(3)
        UrgentRule.THIS_WEEK -> endOfWeek(today)
    }
    return d <= limit
}

val Task.isImportant get() = priority == Priority.HIGHEST || priority == Priority.HIGH

fun Task.quadrant(today: LocalDate, rule: UrgentRule): Quadrant {
    val urgent = isUrgent(today, rule)
    return when {
        urgent && isImportant -> Quadrant.DO
        isImportant -> Quadrant.PLAN
        urgent -> Quadrant.QUICK
        else -> Quadrant.LATER
    }
}
