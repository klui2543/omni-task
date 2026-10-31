package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** The calendar rows under each day of the Focus page's two weeks (mock week-events, owner's pick ก, 2026-10-31). */
class WeekEventsTest {
    private fun at(s: String) = LocalDateTime.parse(s)
    private fun day(s: String) = LocalDate.parse(s)
    private fun titles(rows: List<DayPlan.Item.EventItem>?) = rows?.map { it.event.title }

    // The owner's calendar for 1 to 8 November, as in the mock.
    private val cpap1 = CalendarEvent(1, "ล้าง CPAP", at("2026-11-01T00:00"), at("2026-11-02T00:00"), allDay = true)
    private val shift3 = CalendarEvent(2, "O11 รอบ 3", at("2026-11-03T02:30"), at("2026-11-03T08:30"))
    private val eyes = CalendarEvent(3, "ไปตรวจตา", at("2026-11-04T16:30"), at("2026-11-04T17:30"))
    private val day7 = CalendarEvent(4, "I-8 รอบกลางวัน", at("2026-11-07T08:30"), at("2026-11-07T16:30"))
    private val shift7 = CalendarEvent(5, "O11 รอบ 3", at("2026-11-07T02:30"), at("2026-11-07T08:30"))
    private val cpap8 = CalendarEvent(6, "ล้าง CPAP", at("2026-11-08T00:00"), at("2026-11-09T00:00"), allDay = true)
    private val all = listOf(cpap1, shift3, eyes, day7, shift7, cpap8)

    @Test
    fun eachDayHoldsItsOwnEventsAndADayWithNoneIsLeftOut() {
        val week = WeekEvents.byDay(all, day("2026-11-01"), day("2026-11-08"))
        assertEquals(listOf("2026-11-01", "2026-11-03", "2026-11-04", "2026-11-07", "2026-11-08").map(::day), week.keys.toList())
        assertEquals(listOf("ล้าง CPAP"), titles(week[day("2026-11-01")]))
        assertEquals(listOf("O11 รอบ 3"), titles(week[day("2026-11-03")]))
        assertNull(week[day("2026-11-02")])
    }

    @Test
    fun anAllDayEventTakesOnlyItsOwnDayNotTheOneItEndsOn() {
        val week = WeekEvents.byDay(listOf(cpap1), day("2026-11-01"), day("2026-11-03"))
        assertEquals(listOf(day("2026-11-01")), week.keys.toList())
        assertNull(week[day("2026-11-01")]!!.single().time)
    }

    @Test
    fun aTimedEventShowsItsStartAndTheAllDayOnesComeFirst() {
        val noon = CalendarEvent(7, "ประชุม", at("2026-11-07T12:00"), at("2026-11-07T13:00"))
        val trip = CalendarEvent(8, "ไปนครปฐม", at("2026-11-07T00:00"), at("2026-11-08T00:00"), allDay = true)
        val rows = WeekEvents.byDay(all + noon + trip, day("2026-11-07"), day("2026-11-07"))[day("2026-11-07")]!!
        assertEquals(listOf("ไปนครปฐม", "O11 รอบ 3", "I-8 รอบกลางวัน", "ประชุม"), titles(rows))
        assertEquals(listOf(null, LocalTime.of(2, 30), LocalTime.of(8, 30), LocalTime.of(12, 0)), rows.map { it.time })
    }

    @Test
    fun aTimedEventAtMidnightDoesNotJumpAboveAnAllDayOne() {
        val late = CalendarEvent(9, "นัดเที่ยงคืน", at("2026-11-05T00:00"), at("2026-11-05T01:00"))
        val whole = CalendarEvent(10, "วันหยุด", at("2026-11-05T00:00"), at("2026-11-06T00:00"), allDay = true)
        assertEquals(listOf("วันหยุด", "นัดเที่ยงคืน"), titles(WeekEvents.byDay(listOf(late, whole), day("2026-11-05"), day("2026-11-05"))[day("2026-11-05")]))
    }

    @Test
    fun aNightShiftIsOnBothDaysAndTheSecondDayShowsNoStartTime() {
        val night = CalendarEvent(11, "เวรดึก", at("2026-11-06T20:00"), at("2026-11-07T08:00"))
        val week = WeekEvents.byDay(listOf(night, day7), day("2026-11-06"), day("2026-11-07"))
        assertEquals(LocalTime.of(20, 0), week[day("2026-11-06")]!!.single().time)
        assertEquals(listOf("เวรดึก", "I-8 รอบกลางวัน"), titles(week[day("2026-11-07")]))
        assertNull(week[day("2026-11-07")]!!.first().time)
    }

    @Test
    fun anEventOverSeveralDaysIsOnEachOfThem() {
        val trip = CalendarEvent(12, "ประชุมต่างจังหวัด", at("2026-11-03T00:00"), at("2026-11-06T00:00"), allDay = true)
        val week = WeekEvents.byDay(listOf(trip), day("2026-11-02"), day("2026-11-07"))
        assertEquals(listOf("2026-11-03", "2026-11-04", "2026-11-05").map(::day), week.keys.toList())
    }

    @Test
    fun theTwoEndsOfTheRangeAreBothIncludedAndNothingOutsideIs() {
        val before = CalendarEvent(13, "ก่อน", at("2026-10-31T10:00"), at("2026-10-31T11:00"))
        val after = CalendarEvent(14, "หลัง", at("2026-11-09T10:00"), at("2026-11-09T11:00"))
        val first = CalendarEvent(15, "วันแรก", at("2026-11-01T10:00"), at("2026-11-01T11:00"))
        val last = CalendarEvent(16, "วันสุดท้าย", at("2026-11-08T10:00"), at("2026-11-08T11:00"))
        val week = WeekEvents.byDay(listOf(before, after, first, last), day("2026-11-01"), day("2026-11-08"))
        assertEquals(listOf(day("2026-11-01"), day("2026-11-08")), week.keys.toList())
        assertFalse(week.values.flatten().any { it.event.title == "ก่อน" || it.event.title == "หลัง" })
    }

    @Test
    fun noEventsGivesNoDays() {
        assertEquals(emptyMap(), WeekEvents.byDay(emptyList(), day("2026-11-01"), day("2026-11-08")))
    }
}
