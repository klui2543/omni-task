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
}
