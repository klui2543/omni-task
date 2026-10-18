package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwapTest {

    private val today = LocalDate.parse("2026-10-18")

    @Test
    fun storeRoundTrips() {
        val store = Swap.Store(
            events = mapOf("42" to Swap.KeptTask(listOf("- [ ] a ⏫", "    - [ ] b"), "x.md", "a", "2026-10-19T09:00")),
            tasks = mapOf("abc123" to Swap.KeptEvent(time = "09:00", minutes = 120, place = "ห้อง 3", reminders = listOf(10), calendarId = 5)),
        )
        assertEquals(store, Swap.read(Swap.write(store)))
        assertEquals(Swap.Store(), Swap.read(null))
        assertEquals(Swap.Store(), Swap.read("not json"))
    }

    @Test
    fun findsTheKeptTaskByIdOrByTitleAndStart() {
        val start = LocalDateTime.parse("2026-10-19T09:00")
        val kept = Swap.KeptTask(listOf("- [ ] a"), title = "ส่งรายงาน", start = start.toString())
        val store = Swap.Store(events = mapOf("42" to kept))
        assertEquals("42", Swap.keptTask(store, 42, "x", start)?.first)
        // Another phone: a different id, the same title and start.
        assertEquals("42", Swap.keptTask(store, 7, "ส่งรายงาน", start)?.first)
        assertNull(Swap.keptTask(store, 7, "ส่งรายงาน", start.plusMinutes(30)))
    }

    @Test
    fun aTaskComesBackWithEverythingAndTheEventsNewTime() {
        val kept = Swap.KeptTask(
            listOf(
                "- [ ] ส่งรายงาน #งาน #remind-at-due ⏰ 09:00 ⏫ ➕ 2026-10-07 📅 2026-10-19",
                "    - ค้าง 2026-10-13 รอคนอื่น: รอรูป",
                "    - [ ] รวบตัวเลข",
            ),
        )
        val (lines, id) = Swap.restoredLines(kept, LocalDate.parse("2026-10-20"), LocalTime.of(14, 0), Random(1))
        assertEquals(6, id.length)
        assertEquals("- [ ] ส่งรายงาน #งาน #remind-at-due ⏰ 14:00 ⏫ ➕ 2026-10-07 📅 2026-10-20 🆔 $id", lines[0])
        assertEquals(kept.lines.drop(1), lines.drop(1))
        // Its 🆔 stays the same the next time.
        val (again, sameId) = Swap.restoredLines(Swap.KeptTask(lines), LocalDate.parse("2026-10-21"), null)
        assertEquals(id, sameId)
        assertEquals(LocalTime.of(14, 0), TaskLine.parse(again[0])?.reminderTime)
    }

    @Test
    fun aScheduledOnlyTaskKeepsItsScheduledDay() {
        val kept = Swap.KeptTask(listOf("- [ ] อ่านหนังสือ ⏳ 2026-10-19 🆔 aaa111"))
        val (lines, id) = Swap.restoredLines(kept, LocalDate.parse("2026-10-22"), null)
        assertEquals("aaa111", id)
        assertEquals("- [ ] อ่านหนังสือ ⏳ 2026-10-22 🆔 aaa111", lines[0])
    }

    @Test
    fun anEventBecomesANewTask() {
        val lines = Swap.newTaskLines("ประชุมทีม", LocalDate.parse("2026-10-19"), LocalTime.of(9, 0), false, "วาระ: งบ\n\nห้อง 3", today, "q1w2e3")
        assertEquals("- [ ] ประชุมทีม #remind-at-due ⏰ 09:00 ➕ 2026-10-18 📅 2026-10-19 🆔 q1w2e3", lines[0])
        assertEquals(listOf("    - วาระ: งบ", "    - ห้อง 3"), lines.drop(1))
        val t = TaskLine.parse(lines[0])!!
        assertEquals("q1w2e3", t.id)
        assertEquals("ประชุมทีม", t.title)
        val s = Swap.newTaskLines("อ่าน", LocalDate.parse("2026-10-19"), null, true, "", today, "zz")
        assertEquals("- [ ] อ่าน ➕ 2026-10-18 ⏳ 2026-10-19 🆔 zz", s[0])
    }

    @Test
    fun theEventDayFollowsTheReminder() {
        val t = TaskLine.parse("- [ ] a #remind-at-scheduled 🎯 08:00 ⏳ 2026-10-20 📅 2026-10-25")!!
        assertEquals(LocalDate.parse("2026-10-20"), Swap.eventDay(t, today))
        assertEquals(LocalDate.parse("2026-10-25"), Swap.eventDay(TaskLine.parse("- [ ] a ⏳ 2026-10-20 📅 2026-10-25")!!, today))
        assertEquals(today, Swap.eventDay(TaskLine.parse("- [ ] a")!!, today))
    }
}
