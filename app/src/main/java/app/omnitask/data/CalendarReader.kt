package app.omnitask.data

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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

    private var lastSync = 0L

    /**
     * Asks Android to pull Google Calendar now. Left to itself the sync runs every few hours, so an event made on
     * another device took that long to show up here. At most once a minute unless [force] (the owner pulled to
     * refresh); call it off the main thread.
     */
    fun requestSync(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!hasPermission(context) || (!force && now - lastSync < 60_000)) return
        lastSync = now
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        calendars(context).filter { it.isGoogle }.map { it.account }.distinct().forEach { name ->
            runCatching { ContentResolver.requestSync(Account(name, "com.google"), CalendarContract.AUTHORITY, extras) }
        }
    }

    /**
     * One calendar on the phone. Google accounts show up with [isGoogle] once calendar sync is on. [holiday] marks a
     * public-holiday calendar (Google's "Holidays in Thailand" and the like), whose days the month widget colours red.
     */
    data class Calendar(val id: Long, val name: String, val account: String, val isGoogle: Boolean, val visible: Boolean, val writable: Boolean, val holiday: Boolean = false)

    /** Google's holiday calendars are owned by an address like `th.th#holiday@group.v.calendar.google.com`. */
    fun isHolidayCalendar(name: String, owner: String) =
        "#holiday@" in owner || Regex("holiday|วันหยุด", RegexOption.IGNORE_CASE).containsMatchIn(name)

    fun calendars(context: Context): List<Calendar> {
        if (!hasPermission(context)) return emptyList()
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.OWNER_ACCOUNT,
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
                        holiday = isHolidayCalendar(c.getString(1) ?: "", c.getString(6) ?: ""),
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

    /**
     * Events overlapping [from, to). All-day events come back starting at midnight with [CalendarEvent.allDay] set.
     * With [ids], only those events (the countdown's pinned ones). With [calendars], only those calendars, shown in
     * Omni or not (the holidays).
     */
    fun events(context: Context, from: LocalDateTime, to: LocalDateTime, ids: Collection<Long>? = null, calendars: Collection<Long>? = null): List<CalendarEvent> {
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
            val only = (ids?.let { " AND " + CalendarContract.Instances.EVENT_ID + " IN (" + it.joinToString(",") + ")" } ?: "") +
                (calendars?.let { " AND " + CalendarContract.Instances.CALENDAR_ID + " IN (" + it.joinToString(",") + ")" } ?: "")
            val where = CalendarContract.Instances.VISIBLE + " = 1" + only
            // An event deleted on the phone keeps its row, marked deleted, until the next sync with Google; the
            // calendar app already hides it, so skip it too. A provider without the column gets the plain query.
            val cursor = runCatching {
                context.contentResolver.query(uri, cols, where + " AND " + CalendarContract.Events.DELETED + " = 0", null, CalendarContract.Instances.BEGIN)
            }.getOrNull() ?: context.contentResolver.query(uri, cols, where, null, CalendarContract.Instances.BEGIN)
            cursor?.use { c ->
                while (c.moveToNext()) {
                    if (calendars == null && isHidden(c.getLong(5), hidden)) continue
                    val allDay = c.getInt(4) == 1
                    // All-day instances are stored in UTC midnights; read them as local dates.
                    fun at(ms: Long) = if (allDay) {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                    } else {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
                    }
                    out += CalendarEvent(c.getLong(0), c.getString(1) ?: tr("(ไม่มีชื่อ)", "(No title)"), at(c.getLong(2)), at(c.getLong(3)), allDay, c.getLong(5))
                }
            }
        }
        return CalendarEvent.distinct(out)
    }

    /**
     * Public holidays from [from] to [to] (both included), by day, from the holiday calendars on the phone, whether or
     * not they are switched off in Omni: the month widget colours them even when Focus leaves them out.
     */
    fun holidays(context: Context, from: LocalDate, to: LocalDate): Map<LocalDate, String> {
        val ids = calendars(context).filter { it.holiday }.map { it.id }
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<LocalDate, String>()
        events(context, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), calendars = ids).forEach { e ->
            var d = e.begin.date
            // An all-day event ends at the next midnight; a timed one still counts on the day it starts.
            val end = if (e.allDay) e.end.date.minusDays(1) else e.begin.date
            while (d <= end) {
                if (d >= from && d <= to) out.putIfAbsent(d, e.title)
                d = d.plusDays(1)
            }
        }
        return out
    }

    /** What the phone's calendar store holds for one event, to tell why it shows in Omni and not in the calendar app. */
    data class EventInfo(
        val id: Long,
        val title: String,
        val calendar: Calendar?,
        val calendarId: Long,
        val deleted: Boolean,
        val dirty: Boolean,
        val syncId: String?,
        val recurring: Boolean,
        val hiddenInOmni: Boolean,
    )

    /** Reads event [id] straight from the Events table, deleted rows included; null when the phone has no such row. */
    fun inspect(context: Context, id: Long): EventInfo? {
        if (!hasPermission(context)) return null
        val cols = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.DELETED,
            CalendarContract.Events.DIRTY,
            CalendarContract.Events._SYNC_ID,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.ORIGINAL_ID,
        )
        return runCatching {
            context.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), cols, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val calendarId = c.getLong(1)
                EventInfo(
                    id = id,
                    title = c.getString(0) ?: "",
                    calendar = calendars(context).firstOrNull { it.id == calendarId },
                    calendarId = calendarId,
                    deleted = c.getInt(2) == 1,
                    dirty = c.getInt(3) == 1,
                    syncId = c.getString(4),
                    recurring = !c.getString(5).isNullOrBlank() || !c.isNull(6),
                    hiddenInOmni = isHidden(calendarId, hiddenIds(context)),
                )
            }
        }.getOrNull()
    }

    fun month(context: Context, anyDay: LocalDate): List<CalendarEvent> {
        val first = anyDay.withDayOfMonth(1)
        return events(context, first.minusDays(7).atStartOfDay(), first.plusMonths(1).plusDays(7).atStartOfDay())
    }
}
