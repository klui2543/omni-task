package app.omnitask.data

import android.content.Context
import android.provider.CalendarContract
import app.omnitask.model.CalendarGone
import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Asks Google which of the phone's Google events still exist, and remembers the ones that don't ([gone]), so every
 * view leaves them out. The phone's calendar store on ColorOS can keep an event deleted on Google for good, while the
 * Google Calendar app (which reads Google itself) shows it gone. Uses the Drive sign-in's token, which also carries
 * read-only calendar access. Anything that fails (no sign-in, no network, a calendar of another account) leaves
 * the events as they are.
 */
object GoogleCalendarCheck {

    const val SCOPE = "https://www.googleapis.com/auth/calendar.readonly"

    private const val PREFS = "omnitask"
    private const val KEY_GONE = "calendar.gone"
    private const val KEY_STATUS = "calendar.gone.status"
    private const val MIN_GAP_MS = 2 * 60_000L

    @Volatile private var cached: Set<Long>? = null
    @Volatile private var lastRun = 0L

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    }

    /** The phone's event ids Google no longer has, as of the last check. */
    fun gone(context: Context): Set<Long> =
        cached ?: prefs(context).getStringSet(KEY_GONE, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet().also { cached = it }

    /** How the last check went, in words, for the event inspector. */
    fun status(context: Context): String? = prefs(context).getString(KEY_STATUS, null)

    /**
     * Events Google has that the phone's calendar store has not synced yet, as of the last check, with ids below zero
     * so they never meet a phone row's id. Kept in memory only: the next check (each pull to refresh) replaces them.
     */
    @Volatile private var pending: List<CalendarEvent> = emptyList()

    /** What [pending] holds; [CalendarReader.events] adds the ones in its window. */
    fun pending(): List<CalendarEvent> = pending

    /** A stable id below zero for Google's event [googleId], apart from every phone row's id. */
    private fun pendingId(googleId: String): Long = -1L - (googleId.hashCode().toLong() and 0x7fffffffL)

    /**
     * One phone calendar that comes from Google: [google] is Google's id for it, [owns] whether the owner owns it, and
     * [shown] whether it is shown in the calendar app and not switched off in Omni.
     */
    private class PhoneCalendar(val id: Long, val google: String, val owns: Boolean, val shown: Boolean)

    /** One event Google lists, with what the views need to show it before the phone has it. */
    private class LiveEvent(val id: String, val seriesId: String?, val title: String, val begin: LocalDateTime?, val end: LocalDateTime?, val allDay: Boolean)

    /**
     * Checks again (at most every two minutes unless [force]). Returns true when the set of gone events or the events
     * still to sync changed, so the caller reloads. Blocks: call it off the main thread.
     */
    fun refresh(context: Context, force: Boolean = false): Boolean {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force && now - lastRun < MIN_GAP_MS) return false
        lastRun = now
        if (!CalendarReader.hasPermission(app)) return false
        val token = DriveLink.silentToken(app) ?: return note(app, "ยังไม่ได้ลงชื่อ Google (ตั้งค่า > Drive)", "Not signed in to Google (Settings > Drive)")

        val from = Instant.ofEpochMilli(now).minus(45, ChronoUnit.DAYS)
        val to = Instant.ofEpochMilli(now).plus(400, ChronoUnit.DAYS)
        val calendars = phoneCalendars(app)
        val rowsByCalendar = rows(app, calendars.filter { it.owns }, from.toEpochMilli(), to.toEpochMilli()).groupBy { it.calendar }
        val phoneIds = syncIds(app, calendars.filter { it.shown }, from.toEpochMilli(), to.toEpochMilli())
        val before = gone(app)
        val beforePending = pending
        val next = HashSet<Long>()
        val nextPending = ArrayList<CalendarEvent>()
        var failed: String? = null
        // Each Google calendar is asked once, by Google's id; a calendar synced twice on the phone shares the answer.
        for ((google, sameCalendar) in calendars.groupBy { it.google }) {
            val owns = sameCalendar.any { it.owns }
            val shown = sameCalendar.filter { it.shown }
            if (!owns && shown.isEmpty()) continue
            val live = live(app, token, google, from, to)
            val rows = rowsByCalendar[google].orEmpty()
            if (live == null) {
                // Couldn't ask about this calendar: keep what was known about it. A calendar shared with the owner
                // may not list its events at all, so only one the owner owns counts as a failed check.
                if (owns) failed = google
                next += before.filter { id -> rows.any { it.eventId == id } }
                val ids = shown.map { it.id }.toSet()
                nextPending += beforePending.filter { it.calendarId in ids }
                continue
            }
            // Only calendars the owner owns can say an event is gone (see [rows]); any calendar can say one is new.
            if (owns) {
                next += CalendarGone.gone(rows, live.flatMap { listOfNotNull(it.id, it.seriesId) }.toSet())
            }
            if (shown.isEmpty()) continue
            val phone = phoneIds[google].orEmpty()
            val calendarId = shown.minOf { it.id }
            CalendarGone.notOnPhone(live, phone) { CalendarGone.Listed(it.id, it.seriesId) }.forEach { e ->
                if (e.begin != null && e.end != null) nextPending += CalendarEvent(pendingId(e.id), e.title, e.begin, e.end, e.allDay, calendarId)
            }
        }
        val changed = next != before || nextPending != beforePending
        cached = next
        pending = nextPending
        prefs(app).edit().putStringSet(KEY_GONE, next.map { it.toString() }.toSet()).apply()
        val time = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        if (failed == null) note(app, "เช็กกับ Google แล้ว $time ซ่อน ${next.size} นัดที่ Google ลบแล้ว เพิ่ม ${nextPending.size} นัดที่เครื่องยังไม่ซิงก์", "Checked with Google at $time, hiding ${next.size} events Google deleted, adding ${nextPending.size} the phone has not synced")
        else note(app, "เช็กกับ Google $time ได้ไม่ครบ ($failed)", "Checked with Google at $time, not all calendars answered ($failed)")
        return changed
    }

    /** The phone's Google calendars, with Google's id for each. */
    private fun phoneCalendars(context: Context): List<PhoneCalendar> {
        val hidden = CalendarReader.hiddenIds(context)
        val out = ArrayList<PhoneCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.OWNER_ACCOUNT, CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.VISIBLE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val owner = c.getString(1)
                    if (c.getString(2) != "com.google" || owner.isNullOrBlank()) continue
                    val shown = c.getInt(4) == 1 && !CalendarReader.isHidden(c.getLong(0), hidden)
                    out += PhoneCalendar(c.getLong(0), owner, c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_OWNER, shown)
                }
            }
        }
        return out
    }

    /** Where the window starts and ends, as the phone's event rows say it. */
    private fun window(from: Long, to: Long) =
        CalendarContract.Events.DTSTART + " < " + to + " AND (" + CalendarContract.Events.LAST_DATE + " IS NULL OR " +
            CalendarContract.Events.LAST_DATE + " > " + from + ")"

    /**
     * The phone's Google event rows in the window, with Google's ids for them and their calendar. Only calendars the
     * owner owns: Google lists a shared calendar's events only in part (free/busy, or no listing at all), so every
     * event of it would look deleted and vanish from every view.
     */
    private fun rows(context: Context, calendars: List<PhoneCalendar>, from: Long, to: Long): List<CalendarGone.Row> {
        if (calendars.isEmpty()) return emptyList()
        val byId = calendars.associate { it.id to it.google }
        val out = ArrayList<CalendarGone.Row>()
        runCatching {
            val where = CalendarContract.Events.DELETED + " = 0 AND " + CalendarContract.Events._SYNC_ID + " IS NOT NULL AND " +
                CalendarContract.Events.CALENDAR_ID + " IN (" + byId.keys.joinToString(",") + ") AND " + window(from, to)
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events._SYNC_ID),
                where, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val calendar = byId[c.getLong(1)] ?: continue
                    val sync = c.getString(2) ?: continue
                    out += CalendarGone.Row(c.getLong(0), calendar, sync)
                }
            }
        }
        return out
    }

    /**
     * Google's ids of every phone row in the window, by Google's calendar id, deleted rows too (one deleted on the
     * phone and not yet sent up must not come back as new).
     */
    private fun syncIds(context: Context, calendars: List<PhoneCalendar>, from: Long, to: Long): Map<String, Set<String>> {
        if (calendars.isEmpty()) return emptyMap()
        val byId = calendars.associate { it.id to it.google }
        val out = HashMap<String, HashSet<String>>()
        runCatching {
            val where = CalendarContract.Events._SYNC_ID + " IS NOT NULL AND " +
                CalendarContract.Events.CALENDAR_ID + " IN (" + byId.keys.joinToString(",") + ") AND " + window(from, to)
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.CALENDAR_ID, CalendarContract.Events._SYNC_ID),
                where, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val calendar = byId[c.getLong(0)] ?: continue
                    val sync = c.getString(1) ?: continue
                    out.getOrPut(calendar) { HashSet() } += sync
                }
            }
        }
        return out
    }

    /** The events Google lists for [calendar] in the window, one per round of a repeating event, or null when it can't say. */
    private fun live(context: Context, token: String, calendar: String, from: Instant, to: Instant): List<LiveEvent>? {
        val out = ArrayList<LiveEvent>()
        val zone = ZoneId.systemDefault()
        var page: String? = null
        do {
            val url = "https://www.googleapis.com/calendar/v3/calendars".toHttpUrl().newBuilder()
                .addPathSegment(calendar).addPathSegment("events")
                .addQueryParameter("singleEvents", "true")
                .addQueryParameter("showDeleted", "false")
                .addQueryParameter("maxResults", "2500")
                .addQueryParameter("timeMin", from.toString())
                .addQueryParameter("timeMax", to.toString())
                .addQueryParameter("fields", "items(id,recurringEventId,summary,start,end),nextPageToken")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val body = runCatching {
                client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
                    if (r.code == 401) DriveLink.dropToken(context, token)
                    if (!r.isSuccessful) null else r.body?.string()
                }
            }.getOrNull() ?: return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val items = json.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.optString("id").takeIf { it.isNotEmpty() } ?: continue
                val series = item.optString("recurringEventId").takeIf { it.isNotEmpty() }
                val start = item.optJSONObject("start")
                val end = item.optJSONObject("end")
                val title = item.optString("summary").ifBlank { app.omnitask.model.tr("(ไม่มีชื่อ)", "(No title)") }
                // An all-day event has dates, ending the day after its last day, as the phone stores it; a timed one
                // has times with their offset, read in the phone's zone. One whose times can't be read only counts
                // as live, so a deleted-elsewhere check still sees it.
                val dates = runCatching {
                    val day = start?.optString("date").orEmpty()
                    if (day.isNotEmpty()) {
                        Triple(LocalDate.parse(day).atStartOfDay(), LocalDate.parse(end?.optString("date").orEmpty().ifEmpty { day }).atStartOfDay(), true)
                    } else {
                        fun at(s: String) = LocalDateTime.ofInstant(OffsetDateTime.parse(s).toInstant(), zone)
                        Triple(at(start!!.getString("dateTime")), at(end!!.getString("dateTime")), false)
                    }
                }.getOrNull()
                out += LiveEvent(id, series, title, dates?.first, dates?.second, dates?.third ?: false)
            }
            page = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (page != null)
        return out
    }

    private fun note(context: Context, th: String, en: String): Boolean {
        prefs(context).edit().putString(KEY_STATUS, app.omnitask.model.tr(th, en)).apply()
        return false
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
