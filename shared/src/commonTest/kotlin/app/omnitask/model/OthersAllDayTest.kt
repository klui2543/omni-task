package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * Another person's calendar on today's plan (owner, 2026-10-31, way ข, mock others-today): its all-day events show at the
 * top, faint and named by that calendar; its timed events show at their time (lanes ข, see TimelineLanesTest); none
 * takes any of the owner's time. Real case:
 * "ไปรับบัตรเปนเพื่อนพี่กาน", all day Saturday 31 October in the calendar "เบบี้รักยิ้ม", was on the widget but not the plan.
 */
class OthersAllDayTest {
    private val partner = 5L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, othersCalendars = setOf(partner), calendarNames = mapOf(partner to "เบบี้รักยิ้ม"))
    private val now = LocalDateTime.parse("2026-10-31T09:00")
    private val card = CalendarEvent(1, "ไปรับบัตรเปนเพื่อนพี่กาน", LocalDateTime.parse("2026-10-31T00:00"), LocalDateTime.parse("2026-11-01T00:00"), allDay = true, calendarId = partner)
    private val bkk = CalendarEvent(2, "จ่ายบัตร Bangkok", LocalDateTime.parse("2026-10-31T00:00"), LocalDateTime.parse("2026-11-01T00:00"), allDay = true, calendarId = partner)
    private val lunch = CalendarEvent(3, "กินข้าวกับที่บ้าน", LocalDateTime.parse("2026-10-31T14:00"), LocalDateTime.parse("2026-10-31T15:00"), calendarId = partner)
    private val tomorrow = CalendarEvent(4, "ล้างรถ", LocalDateTime.parse("2026-11-01T00:00"), LocalDateTime.parse("2026-11-02T00:00"), allDay = true, calendarId = partner)

    private fun plan(events: List<CalendarEvent>) = DayFill.plan(DayFill.Input(emptyList(), events, profile, facts, now, DayFill.Memory()))
    private fun others(p: DayFill.Plan) = p.rows.filterIsInstance<DayFill.Row.Locked>().filter { it.from != null }

    @Test
    fun allDayEventsOfAnotherCalendarShowAtTheTopWithItsName() {
        val p = plan(listOf(card, bkk, lunch, tomorrow))
        assertEquals(listOf("ไปรับบัตรเปนเพื่อนพี่กาน", "จ่ายบัตร Bangkok"), others(p).filter { it.at == null }.map { it.title })
        assertTrue(others(p).all { it.from == "เบบี้รักยิ้ม" })
        // Tomorrow's is not today's.
        assertTrue(p.rows.filterIsInstance<DayFill.Row.Locked>().none { it.title.startsWith("ล้างรถ") })
        // At the top: nothing comes before them.
        assertTrue(p.rows.take(2).all { it is DayFill.Row.Locked && it.from != null }, p.rows.take(3).toString())
    }

    // CONTROL: also true before the change; it guards that showing them never makes them busy.
    @Test
    fun theyTakeNoneOfTheOwnersTime() {
        assertEquals(plan(emptyList()).free, plan(listOf(card, bkk, lunch)).free)
    }
}
