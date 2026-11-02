package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The slot's one line while it is dragged or edited (owner's pick ก, 2026-11-01, mock drag-oneline), in the owner's
 * words: "15:35 - 17:05 (1 ชม. 30 นาที)" and "16:25 - 17:55 (ซ้อนทับงานอื่น 65 นาที)"; and how the ruler snaps a
 * moved start and a stretched length.
 */
class DragLabelTest {
    private val partner = 5L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, othersCalendars = setOf(partner), calendarNames = mapOf(partner to "เบบี้รักยิ้ม"))
    private val now = LocalDateTime.parse("2026-11-01T09:00")
    private fun ev(id: Long, title: String, from: String, to: String, cal: Long = 1L) =
        CalendarEvent(id, title, LocalDateTime.parse("2026-11-01T$from"), LocalDateTime.parse("2026-11-01T$to"), calendarId = cal)
    private fun plan(events: List<CalendarEvent>) = DayFill.plan(DayFill.Input(emptyList(), events, profile, facts, now, DayFill.Memory()))
    private fun m(h: Int, mm: Int) = h * 60 + mm

    @Test
    fun theLineReadsAsTheOwnerWroteIt() {
        assertEquals("15:35 - 17:05" to "(1 ชม. 30 นาที)", DayFill.dragLabel(m(15, 35), 90, 0))
        assertEquals("16:25 - 17:55" to "(ซ้อนทับงานอื่น 65 นาที)", DayFill.dragLabel(m(16, 25), 90, 65))
        assertEquals("15:35 - 16:05" to "(30 นาที)", DayFill.dragLabel(m(15, 35), 30, 0))
    }

    @Test
    fun minutesUnderTwoThingsAtOnceCountOnce() {
        // Exercise 16:50 to 18:10 and a call 17:30 to 17:45 inside it: 16:25 for 90 minutes sits on 65 minutes, not 80.
        val p = plan(listOf(ev(1, "ออกกำลังกาย", "16:50", "18:10"), ev(2, "โทรหาแม่", "17:30", "17:45")))
        assertEquals(65, DayFill.overlapMinutes(p, m(16, 25), 90, "x"))
        assertEquals(80, DayFill.overlaps(p, m(16, 25), 90, "x").sumOf { it.second }) // CONTROL: the per-name list counts twice
    }

    @Test
    fun anotherPersonsEventIsNoOnesTime() {
        val p = plan(listOf(ev(3, "กินข้าวกับที่บ้าน", "16:00", "17:00", partner)))
        assertEquals(0, DayFill.overlapMinutes(p, m(16, 0), 60, "x"))
        assertEquals("(1 ชม.)", DayFill.dragLabel(m(16, 0), 60, DayFill.overlapMinutes(p, m(16, 0), 60, "x")).second)
    }

    @Test
    fun aMovedStartGoesTo5MinutesNotBeforeNowAndEndsByBed() {
        assertEquals(m(15, 35), DayFill.snapAt(m(15, 36).toFloat(), 30, m(9, 2), m(22, 30)))
        assertEquals(m(9, 5), DayFill.snapAt(m(7, 0).toFloat(), 30, m(9, 2), m(22, 30)))
        assertEquals(m(22, 0), DayFill.snapAt(m(23, 0).toFloat(), 30, m(9, 2), m(22, 30)))
    }

    @Test
    fun aStretchedLengthGoesTo15MinutesBetween15And4HoursAndEndsByBed() {
        assertEquals(90, DayFill.snapLen(97f, m(15, 35), m(22, 30)))
        assertEquals(15, DayFill.snapLen(3f, m(15, 35), m(22, 30)))
        assertEquals(240, DayFill.snapLen(400f, m(10, 0), m(22, 30)))
        // 21:35 to bed at 22:30 leaves 55 minutes: 45 is the longest whole quarter that fits.
        assertEquals(45, DayFill.snapLen(120f, m(21, 35), m(22, 30)))
    }
}
