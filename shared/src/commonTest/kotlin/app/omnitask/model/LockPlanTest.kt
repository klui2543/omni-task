package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The lock-screen card reads the today page's plan (owner's pick ก, 2026-11-01, mock lock-now-next). Real evening:
 * Sunday 1 November at 18:35, "กินข้าว พัก" 18:35 to 19:20, then two late tasks the owner put at 20:15 and 21:30, bed
 * at 22:45. The old card said "ไม่มีอะไรเหลือวันนี้" because both tasks are past their 📅 and it skips late work.
 */
class LockPlanTest {
    private val day = LocalDate.parse("2026-11-01")
    private fun m(h: Int, mm: Int) = h * 60 + mm
    private val evening = LockPlan.Copy(
        day,
        listOf(
            LockPlan.Slot("ไปช่วยงานลุงจอร์จ", null, null, card = false),
            LockPlan.Slot("ตื่น", m(9, 59), null, card = false),
            LockPlan.Slot("กินข้าว พัก", m(18, 35), m(19, 20), card = false),
            LockPlan.Slot("เขียนถ้อยคำให้ครบ 25 ข้อ (เหลือ 22)", m(20, 15), m(20, 30), card = true, ident = "Omni note\u0001เขียนถ้อยคำ"),
            LockPlan.Slot("ปรับระบบใหม่เป็นระบบ \"ป้าย\"", m(21, 30), m(22, 15), card = true, ident = "Omni note\u0001ปรับระบบ"),
            LockPlan.Slot("นอน", m(22, 45), null, card = false),
        ),
    )

    @Test
    fun at1835ItSaysWhatIsOnAndWhatIsNextWithItsStart() {
        assertEquals(
            "ตอนนี้: กินข้าว พัก ถึง 19:20" to "ต่อไป 20:15 เขียนถ้อยคำให้ครบ 25 ข้อ (เหลือ 22)",
            LockPlan.lines(evening, m(18, 35), emptySet()),
        )
    }

    @Test
    fun betweenBlocksItSaysOnlyWhatIsNext() {
        assertEquals("ต่อไป 20:15 เขียนถ้อยคำให้ครบ 25 ข้อ (เหลือ 22)" to null, LockPlan.lines(evening, m(19, 40), emptySet()))
    }

    @Test
    fun workTickedSinceIsSkipped() {
        assertEquals("ต่อไป 21:30 ปรับระบบใหม่เป็นระบบ \"ป้าย\"" to null, LockPlan.lines(evening, m(19, 40), setOf("Omni note\u0001เขียนถ้อยคำ")))
    }

    @Test
    fun afterBedThereIsNothingLeft() {
        assertNull(LockPlan.lines(evening, m(22, 50), emptySet()))
    }

    @Test
    fun theCopyKeepsAsText() {
        assertEquals(evening, LockPlan.decode(LockPlan.encode(evening)))
        assertNull(LockPlan.decode(null))
        assertNull(LockPlan.decode("not a day"))
    }

    @Test
    fun theCopyHoldsTheOwnersEventsNotAnotherPersonsOrFreeTimeKeptClear() {
        val partner = 5L
        val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
        val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, othersCalendars = setOf(partner), calendarNames = mapOf(partner to "เบบี้รักยิ้ม"))
        fun ev(id: Long, title: String, from: String, to: String, cal: Long = 1L) =
            CalendarEvent(id, title, LocalDateTime.parse("2026-11-01T$from"), LocalDateTime.parse("2026-11-01T$to"), calendarId = cal)
        val plan = DayFill.plan(
            DayFill.Input(
                emptyList(), listOf(ev(1, "ร้านยาโลตัส", "11:00", "13:00"), ev(2, "กินข้าวกับที่บ้าน", "14:00", "15:00", partner)),
                profile, facts, LocalDateTime.parse("2026-11-01T09:00"), DayFill.Memory(),
            ),
        )
        val copy = LockPlan.of(plan)
        val lotus = copy.slots.single { it.title == "ร้านยาโลตัส" }
        assertEquals(m(11, 0) to m(13, 0), lotus.at to lotus.end)
        assertTrue(copy.slots.none { it.title.startsWith("กินข้าวกับที่บ้าน") })
        assertEquals("ตอนนี้: ร้านยาโลตัส ถึง 13:00", LockPlan.lines(copy, m(11, 30), emptySet())?.first)
    }
}
