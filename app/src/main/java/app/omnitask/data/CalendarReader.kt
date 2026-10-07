package app.omnitask.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import app.omnitask.notify.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Reads events from every calendar on the phone; Google Calendar syncs into these. */
object CalendarReader {

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events overlapping [from, to). All-day events come back starting at midnight with [CalendarEvent.allDay] set. */
    fun events(context: Context, from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.atZone(zone).toInstant().toEpochMilli())
            ContentUris.appendId(it, to.atZone(zone).toInstant().toEpochMilli())
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val out = ArrayList<CalendarEvent>()
        runCatching {
            context.contentResolver.query(uri, cols, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    // All-day instances are stored in UTC midnights; read them as local dates.
                    fun at(ms: Long) = if (allDay) {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                    } else {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
                    }
                    out += CalendarEvent(c.getLong(0), c.getString(1) ?: "(ไม่มีชื่อ)", at(c.getLong(2)), at(c.getLong(3)), allDay)
                }
            }
        }
        return out
    }

    fun month(context: Context, anyDay: LocalDate): List<CalendarEvent> {
        val first = anyDay.withDayOfMonth(1)
        return events(context, first.minusDays(7).atStartOfDay(), first.plusMonths(1).plusDays(7).atStartOfDay())
    }
}
