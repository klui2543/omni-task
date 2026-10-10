package app.omnitask.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import java.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import app.omnitask.time.*

/** Reads events from the calendars on the phone that are shown there and not switched off in Omni; Google Calendar syncs into these. */
object CalendarReader {

    private const val PREFS = "omnitask"

    /**
     * Ids of the calendars the owner switched off in Omni. Hidden, not shown, so a calendar added later appears by default.
     * Ids differ per phone, so this key stays out of the synced settings file (see SettingsSync).
     */
    const val KEY_HIDDEN = "calendars.hidden"

    /** Whether [id] is switched off in [hidden]. */
    fun isHidden(id: Long, hidden: Set<Long>) = id in hidden

    fun hiddenIds(context: Context): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toSet()

    /** Switches calendar [id] on or off in Omni; the phone's own visibility is not touched. */
    fun setShown(context: Context, id: Long, shown: Boolean) {
        val next = if (shown) hiddenIds(context) - id else hiddenIds(context) + id
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_HIDDEN, next.map { it.toString() }.toSet()).apply()
    }

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** One calendar on the phone. Google accounts show up with [isGoogle] once calendar sync is on. */
    data class Calendar(val id: Long, val name: String, val account: String, val isGoogle: Boolean, val visible: Boolean, val writable: Boolean)

    fun calendars(context: Context): List<Calendar> {
        if (!hasPermission(context)) return emptyList()
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val out = ArrayList<Calendar>()
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += Calendar(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "",
                        account = c.getString(2) ?: "",
                        isGoogle = c.getString(3) == "com.google",
                        visible = c.getInt(4) == 1,
                        writable = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        }
        return out
    }

    /** The Google calendar new events go to: the account's own primary calendar if there is one. */
    fun primaryWritable(context: Context): Calendar? {
        val all = calendars(context).filter { it.writable && it.visible }
        return all.firstOrNull { it.isGoogle && it.name == it.account } ?: all.firstOrNull { it.isGoogle } ?: all.firstOrNull()
    }

    /** Adds a timed event and returns its id, or null when the calendar refused it. */
    fun insert(context: Context, calendarId: Long, title: String, begin: LocalDateTime, end: LocalDateTime): Long? {
        if (!canWrite(context)) return null
        val zone = ZoneId.systemDefault()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, begin.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, end.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        return runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) } }.getOrNull()
    }

    /** Adds an all-day event on [day]; all-day events are stored as UTC midnights. */
    fun insertAllDay(context: Context, calendarId: Long, title: String, day: LocalDate): Long? {
        if (!canWrite(context)) return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.DTSTART, day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        }
        return runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) } }.getOrNull()
    }

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
            CalendarContract.Instances.CALENDAR_ID,
        )
        val hidden = hiddenIds(context)
        val out = ArrayList<CalendarEvent>()
        runCatching {
            // Calendars hidden in the calendar app stay hidden here too, and so do the ones switched off in Omni.
            context.contentResolver.query(uri, cols, CalendarContract.Instances.VISIBLE + " = 1", null, CalendarContract.Instances.BEGIN)?.use { c ->
                while (c.moveToNext()) {
                    if (isHidden(c.getLong(5), hidden)) continue
                    val allDay = c.getInt(4) == 1
                    // All-day instances are stored in UTC midnights; read them as local dates.
                    fun at(ms: Long) = if (allDay) {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                    } else {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
                    }
                    out += CalendarEvent(c.getLong(0), c.getString(1) ?: tr("(ไม่มีชื่อ)", "(No title)"), at(c.getLong(2)), at(c.getLong(3)), allDay)
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
