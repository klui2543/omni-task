package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * Events at the same time sit side by side in the today timeline (owner, 2026-10-31, mock timeline-lanes, way ข):
 * another person's timed events show at their time, faint and named, and take none of the owner's time. Real day:
 * Saturday 31 October, "ร้านยาโลตัส" 11:00 to 20:00 in the owner's calendar; the 14:00 one of "เบบี้รักยิ้ม" is the
 * mock's example.
 */
class TimelineLanesTest {
    private val partner = 5L
    private val mine = 1L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, othersCalendars = setOf(partner), calendarNames = mapOf(partner to "เบบี้รักยิ้ม"))
    private val now = LocalDateTime.parse("2026-10-31T09:00")
    private fun ev(id: Long, title: String, from: String, to: String, cal: Long = mine) =
        CalendarEvent(id, title, LocalDateTime.parse("2026-10-31T$from"), LocalDateTime.parse("2026-10-31T$to"), calendarId = cal)
    private val lotus = ev(10, "ร้านยาโลตัส", "11:00", "20:00")
    private val lunch = ev(11, "กินข้าวกับที่บ้าน", "14:00", "15:00", partner)
    private val meet = ev(12, "ประชุมออนไลน์", "16:00", "17:00")

    private fun plan(events: List<CalendarEvent>) = DayFill.plan(DayFill.Input(emptyList(), events, profile, facts, now, DayFill.Memory()))
    private fun names(g: List<DayFill.Row>) = g.filterIsInstance<DayFill.Row.Locked>().map { it.title.substringBefore(" ถึง ") }

    @Test
    fun anotherCalendarsTimedEventShowsAtItsTimeWithItsName() {
        val row = plan(listOf(lotus, lunch)).rows.filterIsInstance<DayFill.Row.Locked>().single { it.event == lunch }
        assertEquals(14 * 60, row.at)
        assertEquals("เบบี้รักยิ้ม", row.from)
        assertEquals("กินข้าวกับที่บ้าน ถึง 15:00", row.title)
    }

    @Test
    fun overlappingEventsShareOneRow() {
        val groups = DayFill.lanes(plan(listOf(lotus, lunch)).rows)
        assertTrue(groups.any { names(it) == listOf("ร้านยาโลตัส", "กินข้าวกับที่บ้าน") }, groups.map(::names).toString())
        val three = DayFill.lanes(plan(listOf(lotus, lunch, meet)).rows)
        assertTrue(three.any { names(it) == listOf("ร้านยาโลตัส", "กินข้าวกับที่บ้าน", "ประชุมออนไลน์") }, three.map(::names).toString())
    }

    @Test
    fun aFourthOverlappingEventStartsANewRow() {
        val more = ev(13, "รับของ", "18:00", "18:30", partner)
        val groups = DayFill.lanes(plan(listOf(lotus, lunch, meet, more)).rows).filter { it.size > 1 || names(it) == listOf("รับของ") }
        assertEquals(listOf(3, 1), groups.map { it.size }, groups.map(::names).toString())
    }

    @Test
    fun eventsThatDoNotOverlapStandAlone() {
        val morning = ev(14, "ตลาดเช้า", "08:00", "09:00", partner)
        val groups = DayFill.lanes(plan(listOf(morning, lotus)).rows)
        assertTrue(groups.filter { names(it).isNotEmpty() }.all { it.size == 1 }, groups.map(::names).toString())
    }

    // CONTROL: also true before the change; another person's event never makes the owner busy.
    @Test
    fun theyTakeNoneOfTheOwnersTime() {
        assertEquals(plan(listOf(lotus)).free, plan(listOf(lotus, lunch, ev(15, "ดูหนัง", "20:30", "22:00", partner))).free)
    }

    @Test
    fun aTaskOnAnotherPersonsEventIsNotFlaggedAsOverlapping() {
        val p = plan(listOf(lunch))
        assertTrue(DayFill.overlaps(p, 14 * 60, 30, "x").isEmpty())
    }
}
