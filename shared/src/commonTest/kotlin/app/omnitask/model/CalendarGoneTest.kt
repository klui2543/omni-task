package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarGoneTest {

    @Test
    fun onlyRowsGoogleNoLongerHasAreGone() {
        val rows = listOf(
            CalendarGone.Row(1, "me@gmail.com", "abc"),
            CalendarGone.Row(2, "me@gmail.com", "deleted1"),
            CalendarGone.Row(3, "me@gmail.com", "series9_20261017T090000Z"),
            CalendarGone.Row(4, "me@gmail.com", "series8_20261017T090000Z"),
        )
        assertEquals(setOf(2L, 4L), CalendarGone.gone(rows, setOf("abc", "series9", "other")))
    }

    @Test
    fun eventsGoogleHasThatThePhoneLacksAreNew() {
        val listed = listOf(
            CalendarGone.Listed("abc"),                                   // on the phone
            CalendarGone.Listed("new1"),                                  // added on Google, not synced yet
            CalendarGone.Listed("series9_20261125T023000Z", "series9"),   // a round of a series the phone holds
            CalendarGone.Listed("series8_20261125T023000Z", "series8"),   // a changed round the phone holds as its own row
            CalendarGone.Listed("series7_20261125T023000Z", "series7"),   // a new repeating event
            CalendarGone.Listed("del1"),                                  // deleted on the phone, not sent up yet
        )
        val phone = setOf("abc", "series9", "series8_20261125T023000Z", "del1")
        assertEquals(
            listOf("new1", "series7_20261125T023000Z"),
            CalendarGone.notOnPhone(listed, phone) { it }.map { it.id },
        )
    }

    @Test
    fun nothingNewWhenThePhoneHasEverything() {
        val listed = listOf(CalendarGone.Listed("a"), CalendarGone.Listed("b"))
        assertEquals(emptyList(), CalendarGone.notOnPhone(listed, setOf("a", "b")) { it })
    }
}
