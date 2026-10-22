package app.omnitask.data

import android.content.Context
import android.provider.CalendarContract
import app.omnitask.model.CalendarGone
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
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
     * Checks again (at most every two minutes unless [force]). Returns true when the set of gone events changed, so
     * the caller reloads. Blocks: call it off the main thread.
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
        val rowsByCalendar = rows(app, from.toEpochMilli(), to.toEpochMilli()).groupBy { it.calendar }
        val before = gone(app)
        val next = HashSet<Long>()
        var failed: String? = null
        for ((calendar, rows) in rowsByCalendar) {
            val live = live(app, token, calendar, from, to)
            if (live == null) {
                // Couldn't ask about this calendar: keep what was known about it.
                failed = calendar
                next += before.filter { id -> rows.any { it.eventId == id } }
            } else {
                next += CalendarGone.gone(rows, live)
            }
        }
        val changed = next != before
        cached = next
        prefs(app).edit().putStringSet(KEY_GONE, next.map { it.toString() }.toSet()).apply()
        val time = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        if (failed == null) note(app, "เช็กกับ Google แล้ว $time ซ่อน ${next.size} นัดที่ Google ลบแล้ว", "Checked with Google at $time, hiding ${next.size} events Google deleted")
        else note(app, "เช็กกับ Google $time ได้ไม่ครบ ($failed)", "Checked with Google at $time, not all calendars answered ($failed)")
        return changed
    }

    /** The phone's Google event rows in the window, with Google's ids for them and their calendar. */
    private fun rows(context: Context, from: Long, to: Long): List<CalendarGone.Row> {
        val calendars = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.OWNER_ACCOUNT, CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val owner = c.getString(1)
                    // Only calendars the owner owns: Google lists a shared calendar's events only in part (free/busy, or
                    // no listing at all), so every event of it would look deleted and vanish from every view.
                    val owns = c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_OWNER
                    if (c.getString(2) == "com.google" && !owner.isNullOrBlank() && owns) calendars[c.getLong(0)] = owner
                }
            }
        }
        if (calendars.isEmpty()) return emptyList()
        val out = ArrayList<CalendarGone.Row>()
        runCatching {
            val where = CalendarContract.Events.DELETED + " = 0 AND " + CalendarContract.Events._SYNC_ID + " IS NOT NULL AND " +
                CalendarContract.Events.CALENDAR_ID + " IN (" + calendars.keys.joinToString(",") + ") AND " +
                CalendarContract.Events.DTSTART + " < " + to + " AND (" + CalendarContract.Events.LAST_DATE + " IS NULL OR " +
                CalendarContract.Events.LAST_DATE + " > " + from + ")"
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events._SYNC_ID),
                where, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val calendar = calendars[c.getLong(1)] ?: continue
                    val sync = c.getString(2) ?: continue
                    out += CalendarGone.Row(c.getLong(0), calendar, sync)
                }
            }
        }
        return out
    }

    /** The ids Google lists for [calendar] in the window (with each round's series id), or null when it can't say. */
    private fun live(context: Context, token: String, calendar: String, from: Instant, to: Instant): Set<String>? {
        val ids = HashSet<String>()
        var page: String? = null
        do {
            val url = "https://www.googleapis.com/calendar/v3/calendars".toHttpUrl().newBuilder()
                .addPathSegment(calendar).addPathSegment("events")
                .addQueryParameter("singleEvents", "true")
                .addQueryParameter("showDeleted", "false")
                .addQueryParameter("maxResults", "2500")
                .addQueryParameter("timeMin", from.toString())
                .addQueryParameter("timeMax", to.toString())
                .addQueryParameter("fields", "items(id,recurringEventId),nextPageToken")
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
                item.optString("id").takeIf { it.isNotEmpty() }?.let(ids::add)
                item.optString("recurringEventId").takeIf { it.isNotEmpty() }?.let(ids::add)
            }
            page = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (page != null)
        return ids
    }

    private fun note(context: Context, th: String, en: String): Boolean {
        prefs(context).edit().putString(KEY_STATUS, app.omnitask.model.tr(th, en)).apply()
        return false
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
