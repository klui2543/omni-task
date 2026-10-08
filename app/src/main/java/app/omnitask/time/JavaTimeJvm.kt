package app.omnitask.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/*
 * Where the shared dates meet Android, which speaks java.time: alarms and the calendar provider count in epoch
 * milliseconds of a zone, and dates are shown with java.time's formatter in the app's language.
 */

fun LocalDate.format(formatter: DateTimeFormatter): String = toJavaLocalDate().format(formatter)
fun LocalTime.format(formatter: DateTimeFormatter): String = toJavaLocalTime().format(formatter)
fun LocalDateTime.format(formatter: DateTimeFormatter): String = toJavaLocalDateTime().format(formatter)

fun LocalDateTime.atZone(zone: ZoneId): ZonedDateTime = toJavaLocalDateTime().atZone(zone)
fun LocalDate.atStartOfDay(zone: ZoneId): ZonedDateTime = toJavaLocalDate().atStartOfDay(zone)
fun LocalDateTime.Companion.ofInstant(instant: Instant, zone: ZoneId): LocalDateTime =
    java.time.LocalDateTime.ofInstant(instant, zone).toKotlinLocalDateTime()

/** The month a date falls in, for the month calendars. */
val LocalDate.yearMonth: YearMonth get() = YearMonth.from(toJavaLocalDate())
fun YearMonth.firstDay(): LocalDate = atDay(1).toKotlinLocalDate()
