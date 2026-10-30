package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The day plan's wake and bed times from the watch (owner's pick, 2026-10-30), on the owner's real weeks: the day job
 * on weekdays, night shifts "O11 รอบ 3" at 02:30 in the shift calendar. The profile says 07:00 and 22:30.
 */
class SleepTimesTest {

    private val shiftCal = 7L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, shiftCalendars = setOf(shiftCal))

    private fun shift(id: Long, day: String) =
        CalendarEvent(id, "O11 รอบ 3", LocalDateTime.parse("${day}T02:30"), LocalDateTime.parse("${day}T08:30"), calendarId = shiftCal)

    private val events = listOf(shift(1, "2026-10-31"), shift(2, "2026-11-03"))

    private fun s(from: String, to: String) = SleepSession(LocalDateTime.parse(from), LocalDateTime.parse(to))

    /** A night from the evening of [eve] at [bed] to [wake] the next morning. */
    private fun night(eve: String, bed: String, wake: String): SleepSession {
        val d = LocalDate.parse(eve)
        val b = LocalTime.parse(bed)
        val start = if (b.hour < 12) d.plusDays(1).atTime(b) else d.atTime(b)
        return SleepSession(start, d.plusDays(1).atTime(LocalTime.parse(wake)))
    }

    /** Evenings of 15 to 27 Oct at 23:00, up at 07:30 (a week before the shift weeks). */
    private val usual = (15..27).map { night("2026-10-" + it.toString().padStart(2, '0'), "23:00", "07:30") }

    private fun input(now: String, sleeps: List<SleepSession>?, extra: List<CalendarEvent> = emptyList(), problem: String? = null) =
        DayFill.Input(emptyList(), events + extra, profile, facts, LocalDateTime.parse(now), sleeps = sleeps, sleepProblem = problem)

    private fun m(h: Int, min: Int = 0) = h * 60 + min

    @Test
    fun switchOffKeepsTheProfile() {
        // CONTROL: passes before and after the change; the switch off is the plan as it was.
        val fr = DayFill.frame(LocalDate.parse("2026-10-29"), input("2026-10-29T06:00", null))
        assertEquals(m(7, 30), fr.start)
        assertEquals(m(22, 30), fr.bed)
        assertNull(fr.wakeWhy)
        assertTrue(DayFill.plan(input("2026-10-29T08:00", null)).rows.none { it is DayFill.Row.Locked && it.title == "ตื่น" })
    }

    @Test
    fun thisMorningsRealWakeStartsTheDay() {
        val sleeps = usual + night("2026-10-28", "23:05", "07:42")
        val fr = DayFill.frame(LocalDate.parse("2026-10-29"), input("2026-10-29T06:00", sleeps))
        assertEquals(m(7, 42), fr.wake)
        assertEquals(m(8, 12), fr.start)
        assertTrue(assertNotNull(fr.wakeWhy).contains("เมื่อเช้า"))
        val row = DayFill.plan(input("2026-10-29T08:00", sleeps)).rows.filterIsInstance<DayFill.Row.Locked>().first { it.title == "ตื่น" }
        assertEquals(m(7, 42), row.at)
        assertTrue(row.past)
    }

    @Test
    fun noNightYetUsesTheUsualWake() {
        val fr = DayFill.frame(LocalDate.parse("2026-10-29"), input("2026-10-29T06:00", usual))
        assertEquals(m(7, 30), fr.wake)
        assertTrue(assertNotNull(fr.wakeWhy).contains("ปกติ"))
    }

    @Test
    fun tonightsBedIsTheUsualBeforeAWorkDay() {
        // One late night does not move it: the middle value.
        val sleeps = usual + night("2026-10-28", "01:30", "08:10")
        val fr = DayFill.frame(LocalDate.parse("2026-10-29"), input("2026-10-29T06:00", sleeps))
        assertEquals(m(23), fr.bed)
        assertEquals(m(22), fr.end)
        assertTrue(assertNotNull(fr.bedWhy).contains("นาฬิกา"))
        val bed = DayFill.plan(input("2026-10-29T08:00", sleeps)).rows.filterIsInstance<DayFill.Row.Locked>().first { it.title == "นอน" }
        assertTrue(bed.why.contains("นาฬิกา"))
    }

    @Test
    fun underThreeNightsTheProfileStands() {
        val sleeps = listOf(night("2026-10-26", "23:40", "08:00"), night("2026-10-27", "23:40", "08:00"))
        val fr = DayFill.frame(LocalDate.parse("2026-10-29"), input("2026-10-29T06:00", sleeps))
        assertEquals(m(7), fr.wake)
        assertEquals(m(22, 30), fr.bed)
        assertTrue(assertNotNull(fr.wakeWhy).contains("โปรไฟล์"))
        assertTrue(assertNotNull(fr.bedWhy).contains("โปรไฟล์"))
    }

    @Test
    fun eveningsAroundANightShiftAreNotTheUsual() {
        // Shifts at 02:30 on 27 Oct and 3 Nov: early beds the evening before and after each are set by the shift.
        val sleeps = listOf(
            night("2026-10-26", "20:30", "01:45"), night("2026-10-27", "21:00", "06:00"),
            night("2026-11-02", "20:30", "01:45"), night("2026-11-03", "21:00", "06:00"),
            night("2026-10-28", "23:30", "07:30"), night("2026-11-01", "23:30", "07:30"), night("2026-11-04", "23:30", "07:30"),
        )
        val fr = DayFill.frame(LocalDate.parse("2026-11-05"), input("2026-11-05T06:00", sleeps, listOf(shift(3, "2026-10-27"))))
        assertEquals(m(23, 30), fr.bed)
    }

    @Test
    fun theShiftStillSetsBedTheNightBefore() {
        // Fri 30 Oct, shift at 02:30 on Saturday: 21:00 whatever the watch says.
        val fr = DayFill.frame(LocalDate.parse("2026-10-30"), input("2026-10-30T06:00", usual))
        assertEquals(m(21), fr.bed)
    }

    @Test
    fun aNapIsNotTheNight() {
        val sleeps = listOf(s("2026-10-28T23:00", "2026-10-29T07:20"), s("2026-10-29T01:00", "2026-10-29T02:00"))
        assertEquals(m(7, 20), SleepTimes.wokeOn(sleeps, LocalDate.parse("2026-10-29")))
        assertNull(SleepTimes.wokeOn(listOf(s("2026-10-29T13:00", "2026-10-29T14:30")), LocalDate.parse("2026-10-29")))
        // A nap alone in the night's hours is not a night either.
        assertNull(SleepTimes.wokeOn(listOf(s("2026-10-29T01:00", "2026-10-29T02:00")), LocalDate.parse("2026-10-29")))
    }

    @Test
    fun aNightWokenInTheMiddleIsOneNight() {
        // Up at 03:30 for a while, back to sleep 04:10 to 07:30: the night ends at 07:30 and began at 23:00.
        val sleeps = listOf(s("2026-10-28T23:00", "2026-10-29T03:30"), s("2026-10-29T04:10", "2026-10-29T07:30"))
        assertEquals(m(7, 30), SleepTimes.wokeOn(sleeps, LocalDate.parse("2026-10-29")))
        assertEquals(m(23), SleepTimes.bedOn(sleeps, LocalDate.parse("2026-10-28")))
    }

    @Test
    fun theSameNightFromTwoAppsIsOneNight() {
        val sleeps = listOf(s("2026-10-28T23:10", "2026-10-29T07:20"), s("2026-10-28T23:00", "2026-10-29T07:40"))
        assertEquals(m(7, 40), SleepTimes.wokeOn(sleeps, LocalDate.parse("2026-10-29")))
        assertEquals(m(23), SleepTimes.bedOn(sleeps, LocalDate.parse("2026-10-28")))
    }

    @Test
    fun aSleepAfterTheShiftIsNotLastNight() {
        // Shift day: up 01:45 for 02:30, then a longer sleep 09:00 to 14:30. Last night ended at 01:45.
        val sleeps = listOf(s("2026-10-30T21:00", "2026-10-31T01:45"), s("2026-10-31T09:00", "2026-10-31T14:30"))
        assertEquals(m(1, 45), SleepTimes.wokeOn(sleeps, LocalDate.parse("2026-10-31")))
    }

    @Test
    fun whyTheWatchCouldNotBeReadIsSaid() {
        val fr = DayFill.frame(LocalDate.parse("2026-10-29"), input("2026-10-29T06:00", emptyList(), problem = "ยังไม่ได้อนุญาตให้อ่านการนอน"))
        assertEquals(m(7), fr.wake)
        assertTrue(assertNotNull(fr.wakeWhy).contains("ยังไม่ได้อนุญาต"))
        assertTrue(assertNotNull(fr.bedWhy).contains("ยังไม่ได้อนุญาต"))
    }

    @Test
    fun otherDaysStayOnTheProfile() {
        // Only today's plan follows the watch: tomorrow's frame (a day to keep a task for) is as before.
        val fr = DayFill.frame(LocalDate.parse("2026-10-30"), input("2026-10-29T08:00", usual + night("2026-10-28", "23:05", "07:42")))
        assertNull(fr.wakeWhy)
        assertEquals(m(7, 30), fr.start)
    }

    @Test
    fun theMorningAfterAShiftsEarlyNightIsNotTheUsualWake() {
        // Shifts at 02:30 on 22 Oct, 27 Oct and 3 Nov: the night after each is early, and so is the morning after it.
        val sleeps = listOf(
            night("2026-10-22", "20:30", "04:30"), night("2026-10-27", "20:30", "04:30"), night("2026-11-03", "20:30", "04:30"),
            night("2026-10-28", "23:00", "07:30"), night("2026-10-29", "23:00", "07:30"), night("2026-11-01", "23:00", "07:30"),
        )
        val fr = DayFill.frame(LocalDate.parse("2026-11-05"), input("2026-11-05T06:00", sleeps, listOf(shift(3, "2026-10-27"), shift(4, "2026-10-22"))))
        assertEquals(m(7, 30), fr.wake)
    }
}
