package app.omnitask.data

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.omnitask.time.*
import app.omnitask.ui.TaskViewModel
import app.omnitask.widget.CalendarWatch
import app.omnitask.widget.OmniWidgets
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Google Calendar syncs into the phone's calendar provider; what Omni does about a change there is tested here on a
 * real provider, with a calendar of the phone's own standing in for Google's (the provider treats both alike).
 * Android emulator only: the JVM has no calendar provider.
 */
@RunWith(AndroidJUnit4::class)
class CalendarSyncTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()
    private var calendarId = -1L

    private fun grant() {
        val auto = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR").forEach { auto.grantRuntimePermission(context.packageName, it) }
    }

    private fun syncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "omni-test")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    @Before
    fun before() {
        grant()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "omni-test")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "omni-test")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Omni test")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "omni-test")
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
        }
        val uri = context.contentResolver.insert(syncAdapter(CalendarContract.Calendars.CONTENT_URI), values)
        calendarId = ContentUris.parseId(checkNotNull(uri) { "the provider refused the test calendar" })
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit()
            .putString("vault", "content://com.android.externalstorage.documents/tree/primary%3AVault").commit()
    }

    @After
    fun after() {
        if (calendarId >= 0) context.contentResolver.delete(syncAdapter(CalendarContract.Calendars.CONTENT_URI), "${CalendarContract.Calendars._ID} = ?", arrayOf("$calendarId"))
    }

    private fun waitFor(what: String, seconds: Int = 30, detail: () -> String = { "" }, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            if (ok()) return
            Thread.sleep(250)
        }
        error("gave up waiting for $what${detail().let { if (it.isEmpty()) "" else ": $it" }}")
    }

    /** Every row of a provider table as column=value text, for the failure message. */
    private fun rows(uri: Uri, selection: String?, args: Array<String>?): String = runCatching {
        buildList {
            context.contentResolver.query(uri, null, selection, args, null)?.use { c ->
                while (c.moveToNext()) add((0 until c.columnCount).filter { !c.isNull(it) }.joinToString(",") { "${c.getColumnName(it)}=${c.getString(it)}" }.take(500))
            }
        }.toString()
    }.getOrElse { "error $it" }

    private fun addEvent(title: String): Long {
        val begin = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        return checkNotNull(CalendarReader.insert(context, calendarId, title, begin, begin.plusHours(1))) { "the provider refused the event" }
    }

    @Test
    fun anEventAppearsInTheAppWithoutAReload() {
        val vm = TaskViewModel(context as Application)
        // Nothing is loaded or reloaded: only the provider changes.
        check(vm.state.value.events.none { it.title == "Omni sync test" })
        val id = addEvent("Omni sync test")
        // On a failure, say what the app holds and what the provider gives, so the cause shows in the log.
        val detail = {
            val now = LocalDateTime.now()
            val provider = CalendarReader.events(context, now.minusDays(1), now.plusDays(2))
            "event $id in calendar $calendarId at ${now.plusHours(1)}; app has ${vm.state.value.events.size} events " +
                "${vm.state.value.events.map { it.title }.take(5)}; provider has ${provider.map { it.title }}, hidden ${CalendarReader.hiddenIds(context)}, " +
                "zone ${java.time.ZoneId.systemDefault()}; calendars ${CalendarReader.calendars(context)}; " +
                "event row ${rows(CalendarContract.Events.CONTENT_URI, "${CalendarContract.Events._ID} = ?", arrayOf("$id"))}; " +
                "instances ${rows(CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, 0); android.content.ContentUris.appendId(it, Long.MAX_VALUE / 4) }.build(), null, null)}"
        }
        waitFor("the event to reach the app's state", detail = detail) { vm.state.value.events.any { it.title == "Omni sync test" } }
    }

    @Test
    fun theWidgetWatchIsArmedAndWakesOnAChange() {
        runBlocking { OmniWidgets.refresh(context) }
        check(CalendarWatch.isArmed(context)) { "the content-trigger job was not scheduled" }
        val before = CalendarWatch.wakes.get()
        addEvent("Omni widget test")
        waitFor("the watch to wake") { CalendarWatch.wakes.get() > before }
        // It sets itself again after every wake, or the next change would be missed.
        waitFor("the watch to be armed again") { CalendarWatch.isArmed(context) }
    }

    @Test
    fun askingForASyncWithoutAGoogleAccountDoesNothingWrong() {
        CalendarReader.requestSync(context)
        check(CalendarReader.calendars(context).any { it.id == calendarId && !it.isGoogle })
    }
}
