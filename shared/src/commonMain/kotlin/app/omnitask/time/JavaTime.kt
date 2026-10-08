package app.omnitask.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil

/*
 * The app was written against java.time, which only exists on the JVM. The shared code runs on the JVM and in
 * the browser, so it uses kotlinx-datetime; these extensions give its types the java.time names the code
 * already calls (plusDays, withDayOfMonth, ChronoUnit.DAYS.between...), with java.time's meaning.
 * Arithmetic on dates and times is local, with no time zone, exactly as java.time's local types do it.
 */

private const val NANOS_PER_DAY = 86_400_000_000_000L
private const val SECONDS_PER_DAY = 86_400

fun LocalDate.Companion.of(year: Int, month: Int, dayOfMonth: Int) = LocalDate(year, month, dayOfMonth)
fun LocalDate.Companion.now(): LocalDate = LocalDateTime.now().date

/**
 * The earliest and latest dates, for sorting dateless items first or last. Far enough out for any vault and valid on
 * every platform (java.time's MIN and MAX are not). Named apart from kotlinx-datetime's own internal MIN and MAX.
 */
val LocalDate.Companion.EARLIEST: LocalDate get() = LocalDate(1, 1, 1)
val LocalDate.Companion.LATEST: LocalDate get() = LocalDate(9999, 12, 31)

fun LocalDate.plusDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Long): LocalDate = plus(-days, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(weeks: Long): LocalDate = plus(weeks, DateTimeUnit.WEEK)
fun LocalDate.minusWeeks(weeks: Long): LocalDate = plus(-weeks, DateTimeUnit.WEEK)
fun LocalDate.plusMonths(months: Long): LocalDate = plus(months, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(months: Long): LocalDate = plus(-months, DateTimeUnit.MONTH)
fun LocalDate.plusYears(years: Long): LocalDate = plus(years, DateTimeUnit.YEAR)
fun LocalDate.minusYears(years: Long): LocalDate = plus(-years, DateTimeUnit.YEAR)

fun LocalDate.isBefore(other: LocalDate) = this < other
fun LocalDate.isAfter(other: LocalDate) = this > other

val LocalDate.monthValue: Int get() = monthNumber

fun LocalDate.withDayOfMonth(day: Int) = LocalDate(year, monthNumber, day)

fun LocalDate.lengthOfMonth(): Int = LocalDate(year, monthNumber, 1).plusMonths(1).minusDays(1).dayOfMonth

fun LocalDate.lengthOfYear(): Int = if (LocalDate(year, 12, 31).dayOfYear == 366) 366 else 365

fun LocalDate.atStartOfDay() = LocalDateTime(this, LocalTime(0, 0))
fun LocalDate.atTime(time: LocalTime) = LocalDateTime(this, time)
fun LocalDate.atTime(hour: Int, minute: Int) = LocalDateTime(this, LocalTime(hour, minute))

fun LocalDate.with(adjuster: TemporalAdjuster): LocalDate = adjuster.adjustInto(this)

/** How java.time moves a date to a weekday or to the edge of its month. */
fun interface TemporalAdjuster {
    fun adjustInto(date: LocalDate): LocalDate
}

object TemporalAdjusters {
    fun next(day: DayOfWeek) = TemporalAdjuster { it.plusDays(forward(it.dayOfWeek, day, sameCounts = false)) }
    fun nextOrSame(day: DayOfWeek) = TemporalAdjuster { it.plusDays(forward(it.dayOfWeek, day, sameCounts = true)) }
    fun previous(day: DayOfWeek) = TemporalAdjuster { it.minusDays(forward(day, it.dayOfWeek, sameCounts = false)) }
    fun previousOrSame(day: DayOfWeek) = TemporalAdjuster { it.minusDays(forward(day, it.dayOfWeek, sameCounts = true)) }
    fun firstDayOfMonth() = TemporalAdjuster { it.withDayOfMonth(1) }
    fun lastDayOfMonth() = TemporalAdjuster { it.withDayOfMonth(it.lengthOfMonth()) }
    fun firstDayOfNextMonth() = TemporalAdjuster { it.withDayOfMonth(1).plusMonths(1) }

    /** Days from [from] forward to [to]: 0 to 6, or 1 to 7 when the same day does not count. */
    private fun forward(from: DayOfWeek, to: DayOfWeek, sameCounts: Boolean): Long {
        val d = ((to.isoDayNumber - from.isoDayNumber) + 7) % 7
        return if (d == 0 && !sameCounts) 7 else d.toLong()
    }
}

fun LocalTime.Companion.of(hour: Int, minute: Int, second: Int = 0) = LocalTime(hour, minute, second)
fun LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time
val LocalTime.Companion.MIDNIGHT: LocalTime get() = LocalTime(0, 0)
val LocalTime.Companion.NOON: LocalTime get() = LocalTime(12, 0)
/** The last moment of a day, java.time's LocalTime.MAX. */
val LocalTime.Companion.LAST: LocalTime get() = LocalTime(23, 59, 59, 999_999_999)

/** Clock arithmetic on a time of day wraps around midnight, as in java.time. */
fun LocalTime.plusSeconds(seconds: Long): LocalTime {
    if (seconds == 0L) return this
    val s = ((toSecondOfDay() + seconds % SECONDS_PER_DAY) % SECONDS_PER_DAY + SECONDS_PER_DAY) % SECONDS_PER_DAY
    return LocalTime(s.toInt() / 3600, s.toInt() / 60 % 60, s.toInt() % 60, nanosecond)
}
fun LocalTime.plusMinutes(minutes: Long): LocalTime = plusSeconds(minutes % (24 * 60) * 60)
fun LocalTime.minusMinutes(minutes: Long): LocalTime = plusMinutes(-(minutes % (24 * 60)))
fun LocalTime.plusHours(hours: Long): LocalTime = plusSeconds(hours % 24 * 3600)
fun LocalTime.minusHours(hours: Long): LocalTime = plusHours(-(hours % 24))
fun LocalTime.withHour(hour: Int) = LocalTime(hour, minute, second, nanosecond)
fun LocalTime.withMinute(minute: Int) = LocalTime(hour, minute, second, nanosecond)
fun LocalTime.withSecond(second: Int) = LocalTime(hour, minute, second, nanosecond)
fun LocalTime.withNano(nano: Int) = LocalTime(hour, minute, second, nano)
fun LocalTime.isBefore(other: LocalTime) = this < other
fun LocalTime.isAfter(other: LocalTime) = this > other
fun LocalTime.atDate(date: LocalDate) = LocalDateTime(date, this)

fun LocalDateTime.Companion.of(date: LocalDate, time: LocalTime) = LocalDateTime(date, time)
fun LocalDateTime.Companion.now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.toLocalTime(): LocalTime = time
fun LocalDateTime.isBefore(other: LocalDateTime) = this < other
fun LocalDateTime.isAfter(other: LocalDateTime) = this > other

/** Moves by a span of nanoseconds, carrying into the date. */
fun LocalDateTime.plusNanos(nanos: Long): LocalDateTime {
    val total = time.toNanosecondOfDay() + nanos
    val days = total.floorDiv(NANOS_PER_DAY)
    return LocalDateTime(date.plusDays(days), LocalTime.fromNanosecondOfDay(total.mod(NANOS_PER_DAY)))
}
fun LocalDateTime.minusNanos(nanos: Long) = plusNanos(-nanos)
fun LocalDateTime.plusSeconds(seconds: Long) = plusNanos(seconds * 1_000_000_000L)
fun LocalDateTime.plusMinutes(minutes: Long) = plusSeconds(minutes * 60)
fun LocalDateTime.minusMinutes(minutes: Long) = plusMinutes(-minutes)
fun LocalDateTime.plusHours(hours: Long) = plusSeconds(hours * 3600)
fun LocalDateTime.minusHours(hours: Long) = plusHours(-hours)
fun LocalDateTime.plusDays(days: Long) = LocalDateTime(date.plusDays(days), time)
fun LocalDateTime.minusDays(days: Long) = LocalDateTime(date.minusDays(days), time)
fun LocalDateTime.plusWeeks(weeks: Long) = LocalDateTime(date.plusWeeks(weeks), time)
fun LocalDateTime.withHour(hour: Int) = LocalDateTime(date, time.withHour(hour))
fun LocalDateTime.withMinute(minute: Int) = LocalDateTime(date, time.withMinute(minute))
fun LocalDateTime.withSecond(second: Int) = LocalDateTime(date, time.withSecond(second))
fun LocalDateTime.withNano(nano: Int) = LocalDateTime(date, time.withNano(nano))
fun LocalDateTime.with(time: LocalTime) = LocalDateTime(date, time)
fun LocalDateTime.with(adjuster: TemporalAdjuster) = LocalDateTime(adjuster.adjustInto(date), time)

/** Whole units between two local values, cut toward zero as java.time's ChronoUnit.between does. */
enum class ChronoUnit {
    SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS;

    fun between(from: LocalDate, to: LocalDate): Long = when (this) {
        DAYS -> from.daysUntil(to).toLong()
        WEEKS -> (from.daysUntil(to) / 7).toLong()
        MONTHS -> from.monthsUntil(to).toLong()
        YEARS -> from.yearsUntil(to).toLong()
        else -> between(from.atStartOfDay(), to.atStartOfDay())
    }

    fun between(from: LocalDateTime, to: LocalDateTime): Long {
        val nanos = from.date.daysUntil(to.date) * NANOS_PER_DAY + (to.time.toNanosecondOfDay() - from.time.toNanosecondOfDay())
        return when (this) {
            SECONDS -> nanos / 1_000_000_000L
            MINUTES -> nanos / 60_000_000_000L
            HOURS -> nanos / 3_600_000_000_000L
            DAYS -> nanos / NANOS_PER_DAY
            WEEKS -> nanos / (7 * NANOS_PER_DAY)
            // A month or year counts only once the time of day is reached too.
            MONTHS, YEARS -> {
                var months = from.date.monthsUntil(to.date).toLong()
                val landed = LocalDateTime(from.date.plusMonths(months), from.time)
                if (months > 0 && landed > to) months-- else if (months < 0 && landed < to) months++
                if (this == YEARS) months / 12 else months
            }
        }
    }

    fun between(from: LocalTime, to: LocalTime): Long {
        val nanos = to.toNanosecondOfDay() - from.toNanosecondOfDay()
        return when (this) {
            SECONDS -> nanos / 1_000_000_000L
            MINUTES -> nanos / 60_000_000_000L
            HOURS -> nanos / 3_600_000_000_000L
            else -> 0
        }
    }
}

/** The day's English name, "Monday", as java.time's TextStyle.FULL in English gives it. */
val DayOfWeek.englishName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

/** Two digits, as `%02d` writes them; String.format is JVM only. */
fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** "09:05". */
fun hhmm(hour: Int, minute: Int) = "${pad2(hour)}:${pad2(minute)}"
