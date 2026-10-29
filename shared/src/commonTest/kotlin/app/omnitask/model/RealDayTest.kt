package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The owner's real two weeks, 29 Oct to 12 Nov 2026, as the calendars held them on 29 Oct (shift calendar
 * "SiRxShift", the owner's own calendar, Google's Thai holidays). The owner checked this picture in chat on
 * 29 Oct and said the trip to Nakhon Pathom on 10 to 11 Nov is leave from the day job.
 */
class RealDayTest {

    private val shiftCal = 7L
    private val ownCal = 3L
    private val profile = Profile(exists = true)
    private val facts = RealDay.Facts(
        job = RealDay.Job.WEEKDAYS,
        daysOff = mapOf(
            LocalDate.parse("2026-10-13") to "วันคล้ายวันสวรรคต",
            LocalDate.parse("2026-10-23") to "วันปิยมหาราช",
        ),
        shiftCalendars = setOf(shiftCal),
    )

    private fun timed(id: Long, cal: Long, title: String, from: String, to: String) =
        CalendarEvent(id, title, LocalDateTime.parse(from), LocalDateTime.parse(to), calendarId = cal)

    /** All-day from [first] to [last], both included; the phone stores the end as the next midnight. */
    private fun allDay(id: Long, cal: Long, title: String, first: String, last: String = first) =
        CalendarEvent(id, title, LocalDate.parse(first).atStartOfDay(), LocalDate.parse(last).plusDays(1).atStartOfDay(), allDay = true, calendarId = cal)

    private val events = listOf(
        timed(1, shiftCal, "O11 รอบ 3 จาก พี่ปันปัน", "2026-10-31T02:30", "2026-10-31T08:30"),
        timed(2, shiftCal, "O11 รอบ 3 จาก อิง", "2026-11-03T02:30", "2026-11-03T08:30"),
        timed(3, shiftCal, "O11 รอบ 3 จาก ตัซนีม", "2026-11-06T02:30", "2026-11-06T08:30"),
        timed(4, shiftCal, "O11 รอบ 3 จาก ณัฐธิดา", "2026-11-07T02:30", "2026-11-07T08:30"),
        timed(5, shiftCal, "I-8 รอบกลางวัน จาก จังคนิภา", "2026-11-07T08:30", "2026-11-07T16:30"),
        timed(6, shiftCal, "O9 รอบ 2 จาก พี่มิ้น", "2026-11-12T21:30", "2026-11-13T02:30"),
        timed(10, ownCal, "ร้านยาโลตัส", "2026-10-31T11:00", "2026-10-31T20:00"),
        timed(11, ownCal, "จัดการใบปริญญา", "2026-11-01T00:00", "2026-11-01T00:00"),
        allDay(12, ownCal, "ล้าง CPAP", "2026-11-01"),
        timed(13, ownCal, "ไปตรวจตา รพ.", "2026-11-04T16:30", "2026-11-04T17:30"),
        allDay(14, ownCal, "ล้าง CPAP", "2026-11-08"),
        allDay(15, ownCal, "ไปนครปฐม", "2026-11-10", "2026-11-11"),
        timed(16, ownCal, "เบบี้รักยิ้มรับปริญญา", "2026-11-11T00:00", "2026-11-11T00:00"),
        allDay(17, ownCal, "วันหยุดประชุม World bank", "2026-10-16"),
        allDay(18, ownCal, "ตารางเวรออก", "2026-10-14"),
    )

    private fun day(s: String) = LocalDate.parse(s)

    @Test
    fun theJobRunsOnTheWeekdaysOfThePicture() {
        val days = generateSequence(day("2026-10-29")) { it.plusDays(1) }.takeWhile { it <= day("2026-11-12") }.toList()
        val job = days.filter { RealDay.jobOn(it, events, facts) }
        assertEquals(
            listOf("2026-10-29", "2026-10-30", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-09", "2026-11-12").map(::day),
            job,
        )
    }

    @Test
    fun holidaysAndDaysTheOwnerWroteInAreOff() {
        assertFalse(RealDay.jobOn(day("2026-10-13"), events, facts), "public holiday")
        assertFalse(RealDay.jobOn(day("2026-10-23"), events, facts), "public holiday")
        assertFalse(RealDay.jobOn(day("2026-10-16"), events, facts), "the cabinet's special day off, written in by the owner")
        assertTrue(RealDay.jobOn(day("2026-10-14"), events, facts), "an all-day reminder is not a day off")
        assertTrue(RealDay.isDayOff("วันหยุดนักขัตฤกษ์"))
        assertTrue(RealDay.isDayOff(null))
        assertFalse(RealDay.isDayOff("วันสำคัญ\nหากต้องการซ่อนวันสำคัญ ให้ไปที่การตั้งค่า Google ปฏิทิน > วันหยุดในไทย"))
        assertFalse(RealDay.isDayOff("Observance\nTo hide observances, go to Google Calendar Settings"))
    }

    @Test
    fun everyEventOfTheShiftCalendarIsAShiftAndItsHoursTellNight() {
        fun e(id: Long) = events.first { it.id == id }
        assertTrue(RealDay.isShiftCalendar("SiRxShift", profile))
        assertTrue(RealDay.isShiftCalendar("เวรรพ. Rx", profile))
        assertFalse(RealDay.isShiftCalendar("norapol.uttho@gmail.com", profile))
        assertTrue(RealDay.isNight(e(1), profile, facts), "02:30 to 08:30")
        assertTrue(RealDay.isNight(e(6), profile, facts), "21:30 to 02:30")
        assertTrue(RealDay.isShift(e(5), profile, facts))
        assertFalse(RealDay.isNight(e(5), profile, facts), "08:30 to 16:30 is a day shift")
        assertFalse(RealDay.isShift(e(10), profile, facts), "the pharmacy job is in the owner's own calendar")
    }

    // Thursday 29 Oct, 07:00.
    private val now = LocalDateTime.parse("2026-10-29T07:00")

    @Test
    fun noWorkIsPlacedInTheDayJob() {
        val plan = Planner.plan("อยากเขียน proposal", emptyList(), events, profile, now, facts = facts)
        assertTrue(plan.slots.isNotEmpty())
        plan.slots.filter { RealDay.jobOn(it.day, events, facts) }.forEach { s ->
            assertFalse(s.start < LocalTime.of(16, 30) && s.end > LocalTime.of(8, 30), "${s.day} ${s.start} is in the day job")
        }
        val tasks = listOf("เขียน proposal", "อ่าน paper", "โทรหาแม่").map { app.omnitask.data.TaskLine.parse("- [ ] $it")!! }
        val days = (0L..6L).map { now.toLocalDate().plusDays(it) }
        Planner.planRange(tasks, days, tasks, events, profile, now, facts).filter { RealDay.jobOn(it.slot.day, events, facts) }.forEach { p ->
            val end = p.slot.start.plusMinutes(p.minutes.toLong())
            assertFalse(p.slot.start < LocalTime.of(16, 30) && end > LocalTime.of(8, 30), "${p.task.title} on ${p.slot.day} is in the day job")
        }
    }

    @Test
    fun theEveningBeforeA0230ShiftEndsEarlyAndTheShiftDayIsNotTheTopPick() {
        // Monday 2 Nov, with "O11 รอบ 3" at 02:30 on Tuesday 3 Nov.
        val mon = LocalDateTime.parse("2026-11-02T07:00")
        val plan = Planner.plan("อยากไปวิ่ง", emptyList(), events, profile, mon, facts = facts)
        plan.slots.filter { it.day == day("2026-11-02") }.forEach { s ->
            assertTrue(s.end <= LocalTime.of(20, 0), "${s.start} to ${s.end} on the evening before the 02:30 shift")
        }
        // Three and a half hours after work on Monday no longer fits: bed is at 21:00 before the 02:30 shift.
        val late = Planner.plan("อ่าน paper 3.5 ชม. วันนี้", emptyList(), events, profile, LocalDateTime.parse("2026-11-02T16:40"), facts = facts)
        late.slots.forEach { s -> assertTrue(s.end <= LocalTime.of(20, 0), "${s.start} to ${s.end} runs into the early night") }
        assertTrue(plan.slots.first().day != day("2026-11-03"), "the night-shift day is never the top pick")
        val tue = plan.slots.filter { it.day == day("2026-11-03") }
        assertTrue(tue.isNotEmpty() && tue.all { "เวรดึก" in it.why || "night shift" in it.why }, tue.joinToString { it.why })
    }

    @Test
    fun withoutFactsThePlannerIsUnchanged() {
        // CONTROL: the web passes no facts, so it plans exactly as before.
        val before = Planner.plan("อยากเขียน proposal", emptyList(), events, profile, now)
        val same = Planner.plan("อยากเขียน proposal", emptyList(), events, profile, now, facts = RealDay.Facts())
        assertEquals(before, same)
    }
}
