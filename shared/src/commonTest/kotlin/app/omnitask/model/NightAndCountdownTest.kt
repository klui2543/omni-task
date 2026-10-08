package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.notify.CalendarEvent
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun aPassedBedtimeMeansNowAndDaytimeHasNoNight() {
        val late = DayPlan.night(emptyList(), profile, LocalDateTime.parse("2026-10-08T23:30"), LocalTime.of(22, 30))!!
        assertEquals(0L, late.toBed)
        assertEquals(LocalDateTime.parse("2026-10-08T23:30"), late.bedAt)
        assertNull(DayPlan.night(emptyList(), profile, LocalDateTime.parse("2026-10-08T14:00"), LocalTime.of(22, 30)))
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
}
