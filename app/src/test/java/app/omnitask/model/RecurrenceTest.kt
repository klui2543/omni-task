package app.omnitask.model

import app.omnitask.data.TaskLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RecurrenceTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun next(rule: String, from: String) = Recurrence.next(Recurrence.parse(rule)!!, d(from))

    @Test
    fun parsesTheTasksPluginWords() {
        assertEquals(Recurrence.Rule(ChronoUnit.DAYS, 3), Recurrence.parse("every 3 days"))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), Recurrence.parse("every week on Monday, Thursday")!!.weekdays)
        assertEquals(setOf(DayOfWeek.FRIDAY), Recurrence.parse("every Friday")!!.weekdays)
        assertEquals(true, Recurrence.parse("every month when done")!!.whenDone)
        assertEquals(-1, Recurrence.parse("every month on the last")!!.monthDay)
        assertNull(Recurrence.parse("ทุกวัน"))
    }

    @Test
    fun nextDates() {
        // 2026-10-07 is a Wednesday.
        assertEquals(d("2026-10-08"), next("every day", "2026-10-07"))
        assertEquals(d("2026-10-08"), next("every weekday", "2026-10-07"))
        assertEquals(d("2026-10-12"), next("every weekday", "2026-10-09"))
        assertEquals(d("2026-10-08"), next("every week on Monday, Thursday", "2026-10-07"))
        assertEquals(d("2026-10-12"), next("every week on Monday, Thursday", "2026-10-08"))
        assertEquals(d("2026-10-19"), next("every 2 weeks on Monday", "2026-10-07"))
        assertEquals(d("2026-10-15"), next("every month on the 15th", "2026-10-07"))
        assertEquals(d("2026-11-15"), next("every month on the 15th", "2026-10-15"))
        assertEquals(d("2026-11-30"), next("every month on the last", "2026-10-31"))
        assertEquals(d("2027-02-28"), next("every month", "2027-01-31"))
    }

    @Test
    fun nextOccurrenceMovesEveryDateBySameAmount() {
        val raw = "- [ ] ส่งรายงาน #งาน 🔁 every week ➕ 2026-09-01 ⏳ 2026-10-05 📅 2026-10-07"
        assertEquals(
            "- [ ] ส่งรายงาน #งาน 🔁 every week ➕ 2026-10-07 ⏳ 2026-10-12 📅 2026-10-14",
            TaskLine.nextOccurrence(raw, d("2026-10-07")),
        )
    }

    @Test
    fun whenDoneCountsFromToday() {
        val raw = "- [ ] ตัดผม 🔁 every 4 weeks when done 📅 2026-09-01"
        assertEquals("- [ ] ตัดผม 🔁 every 4 weeks when done 📅 2026-11-04", TaskLine.nextOccurrence(raw, d("2026-10-07")))
        assertNull(TaskLine.nextOccurrence("- [ ] ไม่มีวันที่ 🔁 every day", d("2026-10-07")))
    }
}
