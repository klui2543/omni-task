package app.omnitask.model

import app.omnitask.data.TaskLine
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

/** Tiredness over days (owner's pick ก, 2026-10-30) on the owner's real weeks: day job, night shifts "O11 รอบ 3" at 02:30. */
class FatigueTest {
    private val shiftCal = 7L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, shiftCalendars = setOf(shiftCal))
    private fun shift(id: Long, day: String) =
        CalendarEvent(id, "O11 รอบ 3", LocalDateTime.parse("${day}T02:30"), LocalDateTime.parse("${day}T08:30"), calendarId = shiftCal)

    private fun night(eve: LocalDate, bedH: Int, bedM: Int, len: Int): SleepSession {
        val start = eve.atTime(bedH, bedM)
        val endMin = bedH * 60 + bedM + len
        val end = eve.atStartOfDay().let { kotlinx.datetime.LocalDateTime(eve.plusDays((endMin / 1440).toLong()), LocalTime(endMin % 1440 / 60, endMin % 60)) }
        return SleepSession(start, end)
    }

    private val day = LocalDate.parse("2026-10-29")
    /** 14 nights of 8 h (23:00 to 07:00) before [day], the last [short] of them [len] minutes long. */
    private fun weeks(short: Int = 0, len: Int = 480) = (1..14).map { i -> night(day.minusDays(i.toLong()), 23, 0, if (i <= short) len else 480) }

    private fun task(title: String) = TaskLine.parse("- [ ] $title")!!

    private fun input(sleeps: List<SleepSession>?, events: List<CalendarEvent> = emptyList(), tasks: List<Task> = emptyList(), moves: (Task) -> Int = { 0 }) =
        DayFill.Input(tasks, events, profile, facts, LocalDateTime.parse("2026-10-29T06:00"), sleeps = sleeps, moves = moves)

    @Test
    fun switchOffReadsNothing() {
        // CONTROL: the watch switch off is the plan as it was.
        assertNull(Fatigue.read(day, null, 3, 5))
        val fr = DayFill.frame(day, input(null, listOf(shift(1, "2026-10-24"), shift(2, "2026-10-26"), shift(3, "2026-10-28"))))
        assertNull(fr.tired)
        assertEquals(DayFill.frame(day, input(null)).end, fr.end)
    }

    @Test
    fun usualNightsAreNotTired() {
        val r = assertNotNull(Fatigue.read(day, weeks(), 0, 0))
        assertEquals(Fatigue.Level.OK, r.level)
        assertEquals(0, r.owed)
        assertNull(Fatigue.text(r))
    }

    @Test
    fun oneShortNightAloneIsNotTired() {
        // The owner's point: one night does not say it.
        val r = assertNotNull(Fatigue.read(day, weeks(short = 1, len = 360), 0, 0))
        assertTrue(r.lastShort)
        assertEquals(Fatigue.Level.OK, r.level)
    }

    @Test
    fun sleepOwedOverTheWeekIsTired() {
        // Five nights of 7 h: 5 h owed against the usual 8 h.
        val r = assertNotNull(Fatigue.read(day, weeks(short = 5, len = 420), 0, 0))
        assertEquals(300, r.owed)
        assertEquals(Fatigue.Level.TIRED, r.level)
        assertTrue(assertNotNull(Fatigue.text(r)).contains("5 ชม."))
    }

    @Test
    fun theUsualIsTheOwnersOwn() {
        // Someone who always sleeps 6 h owes nothing on a 6 h night.
        val six = (1..14).map { i -> night(day.minusDays(i.toLong()), 1, 0, 360) }
        assertEquals(0, assertNotNull(Fatigue.read(day, six, 0, 0)).owed)
    }

    @Test
    fun nightShiftsAndShortSleepAreVeryTired() {
        val r = assertNotNull(Fatigue.read(day, weeks(short = 5, len = 420), 3, 0))
        assertEquals(Fatigue.Level.VERY_TIRED, r.level)
    }

    @Test
    fun theFrameCountsTheWeeksNightShifts() {
        val events = listOf(shift(1, "2026-10-24"), shift(2, "2026-10-26"), shift(9, "2026-10-15"))
        val fr = DayFill.frame(day, input(weeks(), events))
        assertEquals(2, assertNotNull(fr.tired).nights)
        assertEquals(Fatigue.Level.OK, fr.tired!!.level)
    }

    @Test
    fun tiredStopsEarlierAndSaysWhy() {
        val tasks = listOf(task("ก"), task("ข"), task("ค"))
        val inp = input(weeks(short = 5, len = 420), listOf(shift(1, "2026-10-24"), shift(2, "2026-10-26")), tasks, moves = { _: Task -> 3 })
        val fr = DayFill.frame(day, inp)
        assertEquals(Fatigue.Level.VERY_TIRED, fr.tired!!.level)
        val plain = DayFill.frame(day, input(weeks()))
        assertEquals(plain.end - 60, fr.end)
        val banner = assertNotNull(DayFill.plan(inp).banner)
        assertTrue(banner.contains("ล้ามาก"))
        assertTrue(banner.contains("เวรดึก 2 ครั้ง"))
        assertTrue(banner.contains("ถูกเลื่อนซ้ำ 3 งาน"))
    }

    @Test
    fun otherDaysAreNotRead() {
        val fr = DayFill.frame(day.plusDays(1), input(weeks(short = 5, len = 420), listOf(shift(1, "2026-10-24"), shift(2, "2026-10-26"), shift(3, "2026-10-27"))))
        assertNull(fr.tired)
    }
}
