package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlannerTest {

    // Wednesday morning.
    private val now = LocalDateTime.parse("2026-10-07T07:10")
    private val profile = Profile(exists = true)

    private fun event(id: Long, title: String, from: String, to: String) =
        CalendarEvent(id, title, LocalDateTime.parse(from), LocalDateTime.parse(to))

    @Test
    fun readsKindDurationAndTitle() {
        val text = "อยากไปวิ่ง 5 กม. สักครั้งสัปดาห์นี้ ควรไปตอนไหนดี"
        assertEquals(Planner.Kind.MOVE, Planner.kindOf(text))
        assertEquals(45, Planner.minutesOf(text, Planner.Kind.MOVE))
        assertEquals("ไปวิ่ง 5 กม.", Planner.titleOf(text))
        assertEquals(120, Planner.minutesOf("อยากเขียน proposal 2 ชม.", Planner.Kind.DEEP))
    }

    @Test
    fun avoidsEventsAndNightShifts() {
        val events = listOf(
            event(1, "เวร OPD", "2026-10-07T08:00", "2026-10-07T16:00"),
            event(2, "เวรดึก", "2026-10-08T00:00", "2026-10-08T08:00"),
            event(3, "เวรดึก ER", "2026-10-09T16:00", "2026-10-09T23:59"),
        )
        val plan = Planner.plan("อยากไปวิ่งสัปดาห์นี้", emptyList(), events, profile, now)
        assertTrue(plan.slots.isNotEmpty())
        plan.slots.forEach { s ->
            val start = s.day.atTime(s.start)
            val end = s.day.atTime(s.end)
            events.forEach { e -> assertFalse("${s.dayLabel} ${s.start} overlaps ${e.title}", start < e.end && end > e.begin) }
        }
        // The night-shift day is never the top pick.
        assertTrue(plan.slots.first().day != LocalDate.parse("2026-10-09"))
        assertEquals(LocalTime.of(17, 30), plan.slots.first().start)
    }

    @Test
    fun deepWorkPrefersTheFocusWindow() {
        val plan = Planner.plan("อยากเขียน proposal พรุ่งนี้", emptyList(), emptyList(), profile, now)
        assertEquals(LocalDate.parse("2026-10-08"), plan.slots.first().day)
        assertEquals(profile.focusFrom, plan.slots.first().start)
    }

    @Test
    fun taskLineFollowsTaskForgeOrder() {
        val slot = Planner.Slot(LocalDate.parse("2026-10-08"), LocalTime.of(17, 30), LocalTime.of(18, 15), "", "", 0)
        assertEquals(
            "- [ ] ไปวิ่ง #remind-at-scheduled 🎯 17:30 ➕ 2026-10-07 ⏳ 2026-10-08",
            Planner.taskLine("ไปวิ่ง", slot, LocalDate.parse("2026-10-07")),
        )
    }

    @Test
    fun profileRoundTrips() {
        val p = Profile(exists = true, wake = LocalTime.of(6, 0), focusFrom = LocalTime.of(10, 0), focusTo = LocalTime.of(13, 0),
            remembered = listOf("วันเสาร์เขียนงานได้ดี"), updated = LocalDate.parse("2026-10-07"))
        assertEquals(p, Profile.parse(p.render()))
    }
}
