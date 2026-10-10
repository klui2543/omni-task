package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

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

    @Test
    fun editsRuleAndReminderInPlace() {
        val raw = "- [ ] ส่งรายงาน #งาน ⏫ ➕ 2026-09-01 📅 2026-10-07"
        val repeating = TaskLine.setRecurrence(raw, "every week")
        assertEquals("- [ ] ส่งรายงาน #งาน ⏫ 🔁 every week ➕ 2026-09-01 📅 2026-10-07", repeating)
        assertEquals(raw, TaskLine.setRecurrence(repeating, null))

        val reminded = TaskLine.setReminder(raw, kotlinx.datetime.LocalTime.of(9, 0), ReminderOn.DUE)
        assertEquals("- [ ] ส่งรายงาน #งาน #remind-at-due ⏰ 09:00 ⏫ ➕ 2026-09-01 📅 2026-10-07", reminded)
        assertEquals(
            "- [ ] ส่งรายงาน #งาน #remind-at-scheduled 🎯 17:30 ⏫ ➕ 2026-09-01 📅 2026-10-07",
            TaskLine.setReminder(reminded, kotlinx.datetime.LocalTime.of(17, 30), ReminderOn.SCHEDULED),
        )
        assertEquals(raw, TaskLine.setReminder(reminded, null, ReminderOn.DUE))
        assertEquals(
            "- [ ] โทรหาแม่ #remind-at-due ⏰ 20:00 📅 2026-10-07",
            TaskLine.setReminder("- [ ] โทรหาแม่ 📅 2026-10-07", kotlinx.datetime.LocalTime.of(20, 0), ReminderOn.DUE),
        )
    }

    @Test
    fun advanceKeepsOneLine() {
        val raw = "- [ ] กินยา #remind-at-due ⏰ 08:00 🔁 every day ➕ 2026-09-01 📅 2026-10-07"
        assertEquals(
            "- [ ] กินยา #remind-at-due ⏰ 08:00 🔁 every day ➕ 2026-09-01 📅 2026-10-08",
            TaskLine.advanceRecurring(raw, d("2026-10-07")),
        )
    }

    private fun task(raw: String) = TaskLine.parse(raw)!!.copy(fileUri = "f", lineIndex = 3)

    @Test
    fun anOpenRepeatingTaskPastItsDateAlsoHasTodaysRound() {
        // Yesterday's round was never ticked, so the line still says 2026-10-09; today is the 10th.
        val missed = task("- [ ] กินยา 🔁 every day ➕ 2026-09-01 ⏳ 2026-10-08 📅 2026-10-09")
        val copy = Recurrence.todayCopy(missed, d("2026-10-10"))!!
        assertEquals(d("2026-10-10"), copy.due)
        assertEquals(d("2026-10-09"), copy.scheduled)
        assertEquals(true, copy.todayCopy)
        assertEquals("f#3@today", copy.key)
        assertEquals("f#3", missed.key)
        assertEquals(missed.raw, copy.raw)
        assertEquals(listOf(missed, copy), Recurrence.withTodayCopies(listOf(missed), d("2026-10-10")))
    }

    @Test
    fun noTodaysRoundWhenNothingFallsOnToday() {
        val today = d("2026-10-10")
        // Due today, or still ahead: the line is the round.
        assertNull(Recurrence.todayCopy(task("- [ ] ก 🔁 every day 📅 2026-10-10"), today))
        assertNull(Recurrence.todayCopy(task("- [ ] ก 🔁 every day 📅 2026-10-12"), today))
        // Weekly on the 5th: its next round (the 12th) is not today.
        assertNull(Recurrence.todayCopy(task("- [ ] ก 🔁 every week 📅 2026-10-05"), today))
        // Weekly on the 3rd: the 10th is a round.
        assertEquals(d("2026-10-10"), Recurrence.todayCopy(task("- [ ] ก 🔁 every week 📅 2026-10-03"), today)!!.due)
        // Counting from completion has no rounds of its own, a done task and a plain task have none.
        assertNull(Recurrence.todayCopy(task("- [ ] ก 🔁 every day when done 📅 2026-10-08"), today))
        assertNull(Recurrence.todayCopy(task("- [x] ก 🔁 every day 📅 2026-10-08 ✅ 2026-10-09"), today))
        assertNull(Recurrence.todayCopy(task("- [ ] ก 📅 2026-10-08"), today))
    }

    @Test
    fun tickingTodaysRoundSkipsPastToday() {
        val raw = "- [ ] กินยา 🔁 every day ➕ 2026-09-01 📅 2026-10-07"
        // The old round moves one step, the copy of today's round moves past today.
        assertEquals("- [ ] กินยา 🔁 every day ➕ 2026-09-01 📅 2026-10-08", TaskLine.advanceRecurring(raw, d("2026-10-10")))
        assertEquals("- [ ] กินยา 🔁 every day ➕ 2026-09-01 📅 2026-10-11", TaskLine.advanceRecurring(raw, d("2026-10-10"), pastToday = true))
    }
}
