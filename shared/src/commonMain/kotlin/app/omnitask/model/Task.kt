package app.omnitask.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

enum class Priority(val emoji: String?, private val th: String, private val en: String) {
    HIGHEST("🔺", "สูงสุด", "Highest"),
    HIGH("⏫", "สูง", "High"),
    MEDIUM("🔼", "กลาง", "Medium"),
    NONE(null, "ปกติ", "Normal"),
    LOW("🔽", "ต่ำ", "Low"),
    LOWEST("⏬", "ต่ำสุด", "Lowest"),
    ;

    val label get() = tr(th, en)
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
    /** Tasks-plugin dependency fields: this task's `🆔 id` and the ids in `⛔ a,b` it waits for. */
    val id: String? = null,
    val dependsOn: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val filePath: String = "",
    val fileUri: String = "",
    val lineIndex: Int = -1,
    /** Set when the line lives in a list note (Bucket list, Watch list...), which keeps it out of the task views. */
    val list: String? = null,
    /** The key of the task this one sits under (an indented checkbox line), or null for a top-level task. */
    val parent: String? = null,
) {
    val isOpen get() = status == Status.TODO || status == Status.IN_PROGRESS
    val noteName get() = filePath.substringAfterLast('/').removeSuffix(".md")
    val key get() = "$fileUri#$lineIndex"

    /** Leading spaces of the line (a tab counts as four), which decide which task a subtask belongs to. */
    val indent: Int get() = raw.takeWhile { it == ' ' || it == '\t' }.fold(0) { n, c -> n + (if (c == '\t') 4 else 1) }

    val isSubtask get() = parent != null

    /** Images embedded in the lines under the task, e.g. `- ![[Omni-2026-10-07-1430.webp]]`. */
    val attachments: List<String>
        get() = notes.mapNotNull { EMBED.find(it)?.groupValues?.get(1) }

    /** Notes linked on their own line under the task, e.g. `- [[ประชุมทีม]]`; these can be removed in the app. */
    val linkLines: List<String>
        get() = notes.mapNotNull { LINK_LINE.find(it)?.groupValues?.get(1)?.trim() }.distinct()

    /** Every linked note: the lines under the task plus `[[links]]` written in the task text itself. */
    val links: List<String>
        get() = (linkLines + LINK_INLINE.findAll(raw).map { it.groupValues[1].trim() }).distinct()

    /** The smallest next action, kept on a line under the task: `- ก้าวแรก: เปิดไฟล์แล้วเขียน 3 บรรทัด`. */
    val firstStep: String?
        get() = notes.firstOrNull { it.startsWith(FIRST_STEP) }?.removePrefix(FIRST_STEP)?.trim()?.ifEmpty { null }

    /** Notes the user wrote (the description), without the image embeds, note links and first step. */
    val textNotes: List<String>
        get() = notes.filter { isPlainNote(it) }

    /** The description as one text, a line per note. */
    val description: String get() = textNotes.joinToString("\n")

    /** The description's first line, for a quiet hint under the title on cards. */
    val descriptionPreview: String? get() = textNotes.firstOrNull { it.isNotBlank() }?.trim()

    companion object {
        const val FIRST_STEP = "ก้าวแรก:"

        /** A note line that is part of the description, not one of the app's own lines. */
        fun isPlainNote(note: String) = EMBED.find(note) == null && LINK_LINE.find(note) == null && !note.startsWith(FIRST_STEP)
    }

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

private val LINK_LINE = Regex("""^\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|[^\]]*)?\]\]$""")
private val LINK_INLINE = Regex("""(?<!!)\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|[^\]]*)?\]\]""")
private val EMBED = Regex("""^!\[\[([^\]|]+\.(?:webp|png|jpe?g|gif))(?:\|[^\]]*)?\]\]$""", RegexOption.IGNORE_CASE)

enum class DateBucket(private val th: String, private val en: String) {
    TODAY("วันนี้", "Today"),
    OVERDUE("เลยกำหนด", "Overdue"),
    THIS_WEEK("สัปดาห์นี้", "This week"),
    NEXT_WEEK("สัปดาห์หน้า", "Next week"),
    FUTURE("อนาคต", "Later"),
    NO_DATE("ไม่มีวันที่", "No date"),
    ;

    val label get() = tr(th, en)
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

enum class UrgentRule(private val th: String, private val en: String) {
    TWO_DAYS("ภายใน 2 วัน", "Within 2 days"),
    THREE_DAYS("ภายใน 3 วัน", "Within 3 days"),
    THIS_WEEK("ภายในสัปดาห์นี้", "Within this week"),
    ;

    val label get() = tr(th, en)
}

enum class Quadrant(private val th: String, private val en: String, val urgent: Boolean, val important: Boolean) {
    DO("ด่วนและสำคัญ", "Urgent and important", urgent = true, important = true),
    PLAN("ไม่ด่วนแต่สำคัญ", "Important, not urgent", urgent = false, important = true),
    QUICK("ด่วนแต่ไม่สำคัญ", "Urgent, not important", urgent = true, important = false),
    LATER("ไม่ด่วนไม่สำคัญ", "Neither", urgent = false, important = false),
    ;

    val label get() = tr(th, en)
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
