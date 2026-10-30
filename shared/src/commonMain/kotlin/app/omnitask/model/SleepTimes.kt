package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import app.omnitask.time.*

/** One sleep the watch recorded (Samsung Health, read on the phone through Health Connect), on the phone's clock. */
data class SleepSession(val start: LocalDateTime, val end: LocalDateTime) {
    val minutes: Int get() = DayFill.minutesOf(end, start.date) - DayFill.minutesOf(start, start.date)
}

/**
 * When the owner really wakes and goes to bed, from the watch (owner's pick, 2026-10-30: through Health Connect). Today's
 * wake is this morning's real one; tonight's bed is the usual time of the last [DAYS] days of the same kind (before a
 * work day or before a day off), since the watch only knows a bedtime after it. Nights around a night shift are left
 * out: their times are set by the shift, not by the owner.
 */
object SleepTimes {
    const val DAYS = 14
    /** Fewer nights than this and the usual time is not told: the profile's time stands. */
    const val MIN_NIGHTS = 3
    /** Shorter sleeps are naps, not the night. */
    private const val MIN_NIGHT = 3 * 60

    /** The main sleep of the night that starts on the evening of [eve]: the longest that begins from 18:00 to 04:00. */
    fun night(sessions: List<SleepSession>, eve: LocalDate): SleepSession? {
        val from = eve.atTime(18, 0)
        val to = eve.plusDays(1).atTime(4, 0)
        return sessions.filter { it.start >= from && it.start < to && it.minutes >= MIN_NIGHT }.maxByOrNull { it.minutes }
    }

    /** This morning's wake on [day] (minutes from its start), when the watch has the night before it. */
    fun wokeOn(sessions: List<SleepSession>, day: LocalDate): Int? =
        night(sessions, day.minusDays(1))?.takeIf { it.end.date == day }?.let { DayFill.minutesOf(it.end, day) }

    /** The bedtime of the evening of [eve], in minutes from its start (past midnight runs over 1440). */
    fun bedOn(sessions: List<SleepSession>, eve: LocalDate): Int? = night(sessions, eve)?.let { DayFill.minutesOf(it.start, eve) }

    /**
     * The usual time over the [DAYS] days before [day] that [keep] lets in, read by [read] (null: no night that day).
     * The middle value, so one late night does not move it; rounded to 5 minutes. Null under [MIN_NIGHTS] nights.
     */
    fun usual(day: LocalDate, keep: (LocalDate) -> Boolean, read: (LocalDate) -> Int?): Int? {
        val seen = (1..DAYS).map { day.minusDays(it.toLong()) }.filter(keep).mapNotNull(read).sorted()
        if (seen.size < MIN_NIGHTS) return null
        val mid = if (seen.size % 2 == 1) seen[seen.size / 2] else (seen[seen.size / 2 - 1] + seen[seen.size / 2]) / 2
        return (mid + 2) / 5 * 5
    }
}
