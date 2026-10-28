package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The chips under the new-task box (owner's pick, 2026-10-28, mock way ก): a day, a time or a folder picked on a
 * chip is written into the sentence as if typed, so the sentence stays the one place the task is read from.
 */
object QuickAddChips {

    /** The sentence without the day typed in it. */
    fun withoutDue(text: String, today: LocalDate): String = drop(text, QuickAdd.spans(text, today).due)

    /** The sentence without the time typed in it. */
    fun withoutTime(text: String, today: LocalDate): String = drop(text, QuickAdd.spans(text, today).time)

    /** The sentence with [day] in place of the day typed before. */
    fun withDue(text: String, day: LocalDate, today: LocalDate, english: Boolean = false): String =
        add(withoutDue(text, today), dayWords(day, today, english))

    /** The sentence with [time] in place of the time typed before. */
    fun withTime(text: String, time: LocalTime, today: LocalDate): String {
        val rest = withoutTime(text, today)
        return add(rest, "${time.hour}:" + time.minute.toString().padStart(2, '0'))
    }

    /** The sentence with [folder] as its folder tag in place of the one before; null takes the folder out. */
    fun withFolder(text: String, folder: String?, today: LocalDate): String {
        val old = Folders.ofTags(QuickAdd.parse(text, today).tags)
        var rest = text
        if (old != null) rest = Regex("""(?<!\S)#${Regex.escape(old)}(?!\S)""").replace(rest, " ")
        val name = folder?.let(Folders::clean)?.takeIf { it.isNotEmpty() }
        return add(rest, name?.let { "#$it" })
    }

    /** A day the parser reads back as [day]: วันนี้, พรุ่งนี้, else the date, with the year only when it is needed. */
    fun dayWords(day: LocalDate, today: LocalDate, english: Boolean = false): String = when (day) {
        today -> if (english) "today" else "วันนี้"
        today.plusDays(1) -> if (english) "tomorrow" else "พรุ่งนี้"
        else -> {
            val short = "${day.dayOfMonth}/${day.monthNumber}"
            // "d/m" alone is this year's, or next year's once the day has gone by.
            val readBack = runCatching { LocalDate.of(today.year, day.monthNumber, day.dayOfMonth) }.getOrNull()
                ?.let { if (it < today) it.plusYears(1) else it }
            if (readBack == day) short else "$short/${day.year}"
        }
    }

    // Results end in a space, so the owner can type on after a chip.
    private fun drop(text: String, span: IntRange?): String {
        val t = text.trim()
        return open(if (span == null) t else t.substring(0, span.first) + " " + t.substring(span.last + 1))
    }

    private fun add(text: String, word: String?): String = open(listOfNotNull(text, word).joinToString(" "))

    private fun open(text: String) = text.replace(Regex("""\s+"""), " ").trim().let { if (it.isEmpty()) it else "$it " }
}
