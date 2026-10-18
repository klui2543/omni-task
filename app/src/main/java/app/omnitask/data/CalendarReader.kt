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
import app.omnitask.model.Recurrence
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

    /** The calendar the last event typed in quick add went to, picked first next time; per phone like [KEY_HIDDEN]. */
    const val KEY_ADD_TO = "calendar.addTo"

    fun addTo(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ADD_TO, -1L).takeIf { it >= 0 }

    fun setAddTo(context: Context, id: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_ADD_TO, id).apply()

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

    /**
     * The detailed add page's event: [begin] to [end] (or [begin]'s day to [end]'s day when [allDay]), with an
     * optional place, description, repeat rule ([rrule], as [Recurrence.rrule] writes it) and alerts ([reminders], minutes before).
     */
    fun insertDetailed(
        context: Context, calendarId: Long, title: String, begin: LocalDateTime, end: LocalDateTime, allDay: Boolean,
        place: String = "", notes: String = "", rrule: String? = null, reminders: List<Int> = emptyList(),
    ): Long? {
        if (!canWrite(context)) return null
        val zone = ZoneId.systemDefault()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            if (place.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, place.trim())
            if (notes.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, notes.trim())
            val from: Long
            val to: Long
            if (allDay) {
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                from = begin.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                to = maxOf(end.date, begin.date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } else {
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                from = begin.atZone(zone).toInstant().toEpochMilli()
                to = maxOf(end.atZone(zone).toInstant().toEpochMilli(), from + 60_000)
            }
            put(CalendarContract.Events.DTSTART, from)
            if (rrule != null) {
                // A repeating event is stored with a length instead of an end.
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, if (allDay) "P${(to - from) / 86_400_000L}D" else "PT${(to - from) / 1000}S")
            } else {
                put(CalendarContract.Events.DTEND, to)
            }
            put(CalendarContract.Events.HAS_ALARM, if (reminders.isEmpty()) 0 else 1)
        }
        val id = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) } }.getOrNull()
            ?: return null
        reminders.forEach { minutes ->
            val r = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, id)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, r) }
        }
        return id
    }

    /** Adds an all-day event from [day] through [lastDay]; all-day events are stored as UTC midnights. */
    fun insertAllDay(context: Context, calendarId: Long, title: String, day: LocalDate, lastDay: LocalDate = day): Long? {
        if (!canWrite(context)) return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.DTSTART, day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, maxOf(day, lastDay).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        }
        return runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) } }.getOrNull()
    }

    /**
     * Events overlapping [from, to). All-day events come back starting at midnight with [CalendarEvent.allDay] set.
     * With [ids], only those events (the countdown's pinned ones). With [calendars], only those calendars, shown in
     * Omni or not (the holidays).
     */
    fun events(
        context: Context, from: LocalDateTime, to: LocalDateTime, ids: Collection<Long>? = null, calendars: Collection<Long>? = null,
        titleContains: String? = null,
    ): List<CalendarEvent> {
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
        // Events Google no longer has, though the phone's store still holds them (see GoogleCalendarCheck).
        val gone = GoogleCalendarCheck.gone(context)
        val out = ArrayList<CalendarEvent>()
        runCatching {
            // Calendars hidden in the calendar app stay hidden here too, and so do the ones switched off in Omni.
            val only = (ids?.let { " AND " + CalendarContract.Instances.EVENT_ID + " IN (" + it.joinToString(",") + ")" } ?: "") +
                (calendars?.let { " AND " + CalendarContract.Instances.CALENDAR_ID + " IN (" + it.joinToString(",") + ")" } ?: "")
            // A title search narrows the rows in the provider; Kotlin checks again below, as LIKE only folds ASCII case.
            val like = titleContains?.let { " AND " + CalendarContract.Instances.TITLE + " LIKE ? ESCAPE '\\'" } ?: ""
            val args = titleContains?.let { t -> arrayOf("%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%") }
            val where = CalendarContract.Instances.VISIBLE + " = 1" + only + like
            // An event deleted on the phone keeps its row, marked deleted, until the next sync with Google; the
            // calendar app already hides it, so skip it too. A provider without the column gets the plain query.
            val cursor = runCatching {
                context.contentResolver.query(uri, cols, where + " AND " + CalendarContract.Events.DELETED + " = 0", args, CalendarContract.Instances.BEGIN)
            }.getOrNull() ?: context.contentResolver.query(uri, cols, where, args, CalendarContract.Instances.BEGIN)
            cursor?.use { c ->
                while (c.moveToNext()) {
                    if (calendars == null && isHidden(c.getLong(5), hidden)) continue
                    if (titleContains != null && c.getString(1)?.contains(titleContains, ignoreCase = true) != true) continue
                    if (c.getLong(0) in gone) continue
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

    /**
     * Deletes event [id] the way a calendar app does: the provider marks it deleted and the next sync removes it from
     * Google too. For an event the phone kept after it was deleted elsewhere. False when the calendar refused it.
     */
    fun delete(context: Context, id: Long): Boolean {
        if (!canWrite(context)) return false
        return runCatching { context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0 }.getOrDefault(false)
    }

    /** What turning an event into a task needs: its times, place, description, alerts and calendar. */
    data class EventDetails(
        val id: Long,
        val title: String,
        val begin: LocalDateTime,
        /** The end; for an all-day event, its last day at midnight. */
        val end: LocalDateTime,
        val allDay: Boolean,
        val place: String,
        val notes: String,
        val calendarId: Long,
        val recurring: Boolean,
        val deleted: Boolean,
        val reminders: List<Int>,
    )

    fun details(context: Context, id: Long): EventDetails? {
        if (!hasPermission(context)) return null
        val zone = ZoneId.systemDefault()
        val cols = arrayOf(
            CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DESCRIPTION, CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.RRULE, CalendarContract.Events.ORIGINAL_ID, CalendarContract.Events.DELETED,
        )
        val base = runCatching {
            context.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), cols, null, null, null)?.use { c ->
                if (!c.moveToFirst() || c.isNull(1)) return@use null
                val allDay = c.getInt(3) == 1
                val startMs = c.getLong(1)
                val endMs = if (c.isNull(2)) startMs + 3_600_000L else c.getLong(2)
                fun at(ms: Long, z: java.time.ZoneId) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), z)
                val begin = if (allDay) at(startMs, ZoneOffset.UTC) else at(startMs, zone)
                val end = if (allDay) at(endMs, ZoneOffset.UTC).date.minusDays(1).let { maxOf(it, begin.date) }.atStartOfDay() else at(endMs, zone)
                EventDetails(
                    id = id, title = c.getString(0) ?: "", begin = begin, end = end, allDay = allDay,
                    place = c.getString(4) ?: "", notes = c.getString(5) ?: "", calendarId = c.getLong(6),
                    recurring = !c.getString(7).isNullOrBlank() || !c.isNull(8), deleted = c.getInt(9) == 1, reminders = emptyList(),
                )
            }
        }.getOrNull() ?: return null
        val reminders = runCatching {
            CalendarContract.Reminders.query(context.contentResolver, id, arrayOf(CalendarContract.Reminders.MINUTES))?.use { c ->
                buildList { while (c.moveToNext()) add(c.getInt(0)) }
            }
        }.getOrNull().orEmpty()
        return base.copy(reminders = reminders.distinct().sorted())
    }

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

    /** Every event whose title holds [text], a year either side of [today], for the search in the Tasks tab. */
    fun search(context: Context, text: String, today: LocalDate): List<CalendarEvent> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        return events(context, today.minusYears(1).atStartOfDay(), today.plusYears(1).plusDays(1).atStartOfDay(), titleContains = t)
    }

    fun month(context: Context, anyDay: LocalDate): List<CalendarEvent> {
        val first = anyDay.withDayOfMonth(1)
        return events(context, first.minusDays(7).atStartOfDay(), first.plusMonths(1).plusDays(7).atStartOfDay())
    }
}
