package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.notify.CalendarEvent
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

class NightAndCountdownTest {

    private val profile = Profile(wake = LocalTime.of(6, 0), sleep = LocalTime.of(22, 30))

    @Test
    fun bedtimeTonightAndTheSleepAfterIt() {
        val now = LocalDateTime.parse("2026-10-08T20:00")
        val n = DayPlan.night(emptyList(), profile, now, LocalTime.of(23, 0))!!
        assertEquals(180L, n.toBed)
        assertEquals(LocalDateTime.parse("2026-10-09T06:00"), n.wakeAt)
        assertEquals(7 * 60L, n.sleep)
    }

    @Test
    fun aBedtimeAfterMidnightAndAnEarlyShift() {
        val now = LocalDateTime.parse("2026-10-08T22:00")
        val shift = CalendarEvent(1, "เวรเช้า", LocalDateTime.parse("2026-10-09T05:30"), LocalDateTime.parse("2026-10-09T13:00"))
        val n = DayPlan.night(listOf(shift), profile, now, LocalTime.of(0, 30))!!
        assertEquals(LocalDateTime.parse("2026-10-09T00:30"), n.bedAt)
        // An hour before the shift beats the usual 06:00.
        assertEquals(LocalDateTime.parse("2026-10-09T04:30"), n.wakeAt)
        assertEquals(4 * 60L, n.sleep)
    }

    @Test
    fun aWakeTimePickedForTonightBeatsTheMorningEvent() {
        val now = LocalDateTime.parse("2026-10-08T22:00")
        val shift = CalendarEvent(1, "เวรเช้า", LocalDateTime.parse("2026-10-09T05:30"), LocalDateTime.parse("2026-10-09T13:00"))
        val n = DayPlan.night(listOf(shift), profile, now, LocalTime.of(23, 0), wake = LocalTime.of(5, 0))!!
        assertEquals(LocalDateTime.parse("2026-10-09T05:00"), n.wakeAt)
        assertEquals(6 * 60L, n.sleep)
        assertNull(n.because)
        // A wake later than the usual one keeps the night going past 06:00.
        assertEquals(LocalDateTime.parse("2026-10-09T08:00"), DayPlan.night(emptyList(), profile, LocalDateTime.parse("2026-10-09T06:30"), LocalTime.of(23, 0), wake = LocalTime.of(8, 0))!!.wakeAt)
    }

    @Test
    fun aPassedBedtimeMeansNowAndDaytimeHasNoNight() {
        val late = DayPlan.night(emptyList(), profile, LocalDateTime.parse("2026-10-08T23:30"), LocalTime.of(22, 30))!!
        assertEquals(0L, late.toBed)
        assertEquals(LocalDateTime.parse("2026-10-08T23:30"), late.bedAt)
        assertNull(DayPlan.night(emptyList(), profile, LocalDateTime.parse("2026-10-08T14:00"), LocalTime.of(22, 30)))
    }

    @Test
    fun givenTheEveningTheNightIsPlannedInTheDaytime() {
        val now = LocalDateTime.parse("2026-10-08T14:00")
        val n = DayPlan.night(emptyList(), profile, now, LocalTime.of(22, 30), evening = now.toLocalDate())!!
        assertEquals(LocalDateTime.parse("2026-10-08T22:30"), n.bedAt)
        assertEquals(LocalDateTime.parse("2026-10-09T06:00"), n.wakeAt)
        assertEquals(450L, n.sleep)
    }

    @Test
    fun countdownTexts() {
        val now = LocalDateTime.parse("2026-10-08T10:00")
        val exam = TaskLine.parse("- [ ] สอบ BCP 📅 2026-10-20")!!
        assertEquals("อีก 12 วัน", Countdown.text(exam, now))
        val meeting = TaskLine.parse("- [ ] ประชุม #remind-at-due ⏰ 13:30 📅 2026-10-08")!!
        assertEquals("อีก 3 ชม. 30 นาที", Countdown.text(meeting, now))
        val late = TaskLine.parse("- [ ] ส่งรายงาน 📅 2026-10-06")!!
        assertEquals("เลยมา 2 วัน", Countdown.text(late, now))
        assertNull(Countdown.text(TaskLine.parse("- [ ] ไม่มีวัน")!!, now))
    }

    @Test
    fun severalCountdownsFromTasksAndEvents() {
        val now = LocalDateTime.parse("2026-10-14T07:55")
        val tasks = listOf(
            TaskLine.parse("- [ ] สอบ BCP 📅 2026-10-26")!!,
            TaskLine.parse("- [ ] ส่งรายงานเวร 📅 2026-10-15")!!,
            TaskLine.parse("- [x] เสร็จแล้ว 📅 2026-10-20 ✅ 2026-10-13")!!,
            TaskLine.parse("- [ ] ไม่ได้ปักไว้ 📅 2026-10-18")!!,
        )
        val meeting = CalendarEvent(41, "ประชุมวิชาการ", LocalDateTime.parse("2026-10-20T00:00"), LocalDateTime.parse("2026-10-21T00:00"), allDay = true)
        val nails = CalendarEvent(7, "ทำเล็บ", LocalDateTime.parse("2026-10-14T18:00"), LocalDateTime.parse("2026-10-14T19:00"))
        val over = CalendarEvent(9, "จบไปแล้ว", LocalDateTime.parse("2026-10-13T09:00"), LocalDateTime.parse("2026-10-13T10:00"))
        // The single countdown of before (a bare title) still reads, as a task.
        val pins = Countdown.parseAll(listOf("สอบ BCP", "task:ส่งรายงานเวร", "task:เสร็จแล้ว", Countdown.pinOf(meeting).key, Countdown.pinOf(over).key, "task:สอบ BCP"))
        assertEquals(5, pins.size)
        val shown = Countdown.shown(pins, tasks, listOf(meeting, nails, over), now)
        // Soonest first; the ticked-off task and the event that is over are left out.
        assertEquals(listOf("ส่งรายงานเวร", "ประชุมวิชาการ", "สอบ BCP"), shown.map { it.title })
        assertEquals(listOf("พรุ่งนี้", "อีก 6 วัน", "อีก 12 วัน"), shown.map { it.text })
        assertEquals(listOf(false, true, false), shown.map { it.isEvent })

        // Moved in the calendar: the same event id is followed to its new day.
        val moved = meeting.copy(begin = LocalDateTime.parse("2026-10-22T00:00"), end = LocalDateTime.parse("2026-10-23T00:00"))
        assertEquals("อีก 8 วัน", Countdown.shown(pins, tasks, listOf(moved), now).first { it.isEvent }.text)
        // Another phone's event with the same id is not taken for it.
        assertEquals("อีก 6 วัน", Countdown.shown(pins, tasks, listOf(moved.copy(title = "อย่างอื่น")), now).first { it.isEvent }.text)
        // Not read this time (further ahead than what was loaded): the pin as saved.
        assertEquals("อีก 6 วัน", Countdown.shown(pins, tasks, emptyList(), now).first { it.isEvent }.text)
        assertEquals("อีก 10 ชม. 5 นาที", Countdown.text(nails, now))
        assertEquals("ตอนนี้", Countdown.text(nails, LocalDateTime.parse("2026-10-14T18:30")))
    }

    @Test
    fun thePickerHasATabForTasksAndOneForEvents() {
        val now = LocalDateTime.parse("2026-10-14T07:55")
        val tasks = listOf(
            TaskLine.parse("- [ ] สอบ BCP 📅 2026-10-26")!!,
            TaskLine.parse("- [ ] ดูหุ้น ⏳ 2026-10-12")!!,
            TaskLine.parse("- [ ] ไม่มีวัน")!!,
        )
        val meeting = CalendarEvent(41, "ประชุมวิชาการ", LocalDateTime.parse("2026-10-20T00:00"), LocalDateTime.parse("2026-10-21T00:00"), allDay = true)
        val shift = CalendarEvent(5, "เวร O11", LocalDateTime.parse("2026-10-15T08:00"), LocalDateTime.parse("2026-10-15T16:00"))
        val wedding = CalendarEvent(6, "งานแต่ง", LocalDateTime.parse("2027-01-09T00:00"), LocalDateTime.parse("2027-01-10T00:00"), allDay = true)
        var pins: List<Countdown.Pin> = listOf(Countdown.TaskPin("สอบ BCP"))

        val t = Countdown.taskChoices(pins, tasks, now)
        assertEquals(listOf("สอบ BCP", "ดูหุ้น"), t.map { it.title })
        assertEquals(listOf(true, false), t.map { it.on })
        assertEquals("นัดทำ จ. 12 ต.ค.", t[1].sub)
        assertTrue(t[1].late)

        val e = Countdown.eventChoices(pins, listOf(wedding, meeting, shift, shift.copy(id = 8)), now)
        // The same shift in two calendars is offered once.
        assertEquals(listOf("เวร O11", "ประชุมวิชาการ", "งานแต่ง"), e.map { it.title })
        assertEquals(listOf("ตุลาคม", "ตุลาคม", "มกราคม 2027"), e.map { it.group })
        assertEquals("พฤ. 15 ต.ค. 08:00", e[0].sub)

        // Ticking puts it on top of its tab; ticking again takes it off.
        pins = Countdown.toggle(pins, e[1], now)
        val after = Countdown.eventChoices(pins, listOf(wedding, meeting, shift), now)
        assertEquals(listOf("ประชุมวิชาการ", "เวร O11", "งานแต่ง"), after.map { it.title })
        assertTrue(after[0].on)
        pins = Countdown.toggle(pins, after[0], now)
        assertEquals(listOf("task:สอบ BCP"), pins.map { it.key })
        // The web keeps no event ids, as they do not outlast its page.
        assertEquals(0L, (Countdown.eventChoices(emptyList(), listOf(meeting), now, keepIds = false)[0].pin as Countdown.EventPin).id)
    }
}
