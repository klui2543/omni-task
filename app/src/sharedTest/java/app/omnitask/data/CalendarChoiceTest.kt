package app.omnitask.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Which calendars the owner switched off in Omni: the pure decision, the preference round trip, and that the ids stay on this phone. */
@RunWith(AndroidJUnit4::class)
class CalendarChoiceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun clear() {
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit().remove(CalendarReader.KEY_HIDDEN).commit()
    }

    @Before fun before() = clear()

    @After fun after() = clear()

    @Test
    fun hiddenIsDecidedByTheSwitchedOffIds() {
        val hidden = setOf(3L, 7L)
        check(CalendarReader.isHidden(3, hidden))
        check(CalendarReader.isHidden(7, hidden))
        check(!CalendarReader.isHidden(4, hidden))
        // Nothing switched off: every calendar shows, so a calendar added later appears by default.
        check(!CalendarReader.isHidden(3, emptySet()))
    }

    @Test
    fun preferenceRoundTrip() {
        check(CalendarReader.hiddenIds(context).isEmpty())
        CalendarReader.setShown(context, 5, false)
        CalendarReader.setShown(context, 9, false)
        check(CalendarReader.hiddenIds(context) == setOf(5L, 9L)) { "got ${CalendarReader.hiddenIds(context)}" }
        CalendarReader.setShown(context, 5, true)
        check(CalendarReader.hiddenIds(context) == setOf(9L))
        // Switching off twice, or on something never switched off, changes nothing more.
        CalendarReader.setShown(context, 9, false)
        CalendarReader.setShown(context, 1, true)
        check(CalendarReader.hiddenIds(context) == setOf(9L))
        CalendarReader.setShown(context, 9, true)
        check(CalendarReader.hiddenIds(context).isEmpty())
    }

    @Test
    fun hiddenCalendarsDoNotTravelThroughTheSettingsFile() {
        CalendarReader.setShown(context, 5, false)
        check(context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).contains(CalendarReader.KEY_HIDDEN))
        val settings = JSONObject(SettingsSync.toJson(context)).getJSONObject("settings")
        check(!settings.has(CalendarReader.KEY_HIDDEN)) { "calendar ids leaked into the synced settings" }
    }
}
