package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.notify.CalendarEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The day after a night shift with an all-day-ish event ("ร้านยาโลตัส" 11:00 to 20:00) on it, as the owner's screenshot of
 * Saturday 31 October at 11:12 shows: the event takes the whole day, so every task moves to another day. The owner's picks
 * (2026-10-31, ก+ข): an event can be called "ว่าง ทำงานได้" for today, and a moved task can be put back today at a time.
 */
class FreeEventTest {
    private val shiftCal = 7L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, shiftCalendars = setOf(shiftCal))
    private val shift = CalendarEvent(1, "O11 รอบ 3 จาก ปัญจพร", LocalDateTime.parse("2026-10-31T02:30"), LocalDateTime.parse("2026-10-31T08:30"), calendarId = shiftCal)
    private val lotus = CalendarEvent(2, "ร้านยาโลตัส", LocalDateTime.parse("2026-10-31T11:00"), LocalDateTime.parse("2026-10-31T20:00"), calendarId = 9L)
    private val note = listOf(
        "- [ ] How to ดูหุ้นแบบรอบด้านและลึก #My-space 🔼 ➕ 2026-09-29",
        "- [ ] เขียนถ้อยคำให้ครบ 25 ข้อ (เหลือ 22) #omni 🔼 ➕ 2026-10-20 📅 2026-10-21",
        "- [ ] ปรับระบบใหม่เป็นระบบ \"ป้าย\" #omni 🔼 ➕ 2026-10-20 📅 2026-10-30",
    )
    private val day = "2026-10-31"
    private val now = LocalDateTime.parse("2026-10-31T11:12")

    private fun tasks(lines: List<String> = note) = VaultText.parseFile("f", "📁 Folder/หลังบ้าน/Omni/Omni note.md", lines.joinToString("\n"))
    private fun plan(memory: DayFill.Memory = DayFill.Memory(), lines: List<String> = note, events: List<CalendarEvent> = listOf(shift, lotus)) =
        DayFill.plan(DayFill.Input(tasks(lines), events, profile, facts, now, memory))
    private fun freed() = DayFill.Memory(day = day, freeEvents = setOf(DayFill.freeKey(lotus)))
    private fun DayFill.Plan.card(title: String) = placed.firstOrNull { it.item.title.startsWith(title) }
    private fun DayFill.Plan.moved(title: String) = later.firstOrNull { it.item.title.startsWith(title) }

    private var chosenBefore: Set<String>? = null
    @BeforeTest fun projects() { chosenBefore = Projects.chosen; Projects.chosen = setOf("omni") }
    @AfterTest fun restore() { Projects.chosen = chosenBefore }

    // ---- ก. An event the owner calls free takes no time ----

    @Test
    fun theEventTakesTheWholeDayAndEveryTaskMovesAway() {
        val p = plan()
        assertEquals(DayFill.Mode.AFTER_NIGHT, p.mode)
        assertEquals(15, p.free, "only 20:15 to 20:30 is left")
        assertTrue(p.placed.none { it.item.kind != DayFill.Kind.LIFE })
        assertEquals("วันพักฟื้น ไม่ต้องเริ่มวันนี้ ถ้าไหวค่อยทำ", p.moved("เขียนถ้อยคำ")?.why)
    }

    @Test
    fun anEventCalledFreeLeavesItsHoursToTheTasks() {
        val p = plan(freed())
        assertTrue(p.free > 400, "the hours of the event are free: ${p.free}")
        assertTrue(p.open.any { (from, to) -> from < 13 * 60 && to >= 17 * 60 }, "the free stretches run on through the event: ${p.open}")
        assertNotNull(p.card("เขียนถ้อยคำ"), "the recovery-day task is offered again")
        assertNotNull(p.card("ปรับระบบ"))
        assertNull(p.moved("เขียนถ้อยคำ"))
        val row = p.rows.filterIsInstance<DayFill.Row.Locked>().first { it.event == lotus }
        assertTrue(row.free, "the event stays on the plan, marked free")
        assertTrue(row.why.startsWith("ว่าง ทำงานได้"), row.why)
    }

    @Test
    fun onlyThatEventIsFreedAndOnlyToday() {
        val twin = lotus.copy(begin = LocalDateTime.parse("2026-11-07T11:00"), end = LocalDateTime.parse("2026-11-07T20:00"))
        assertTrue(DayFill.freeKey(lotus) != DayFill.freeKey(twin), "a repeating event is known by its start too")
        val other = CalendarEvent(3, "ประชุม", LocalDateTime.parse("2026-10-31T13:00"), LocalDateTime.parse("2026-10-31T14:00"), calendarId = 9L)
        val p = plan(freed(), events = listOf(shift, lotus, other))
        assertFalse(p.rows.filterIsInstance<DayFill.Row.Locked>().first { it.event == other }.free)
        assertTrue(freed().on(LocalDate.parse("2026-11-01")).freeEvents.isEmpty(), "a new day forgets it")
    }

    @Test
    fun theMemoryKeepsItAcrossARestart() {
        val back = DayFill.decode(DayFill.encode(freed()))
        assertEquals(setOf(DayFill.freeKey(lotus)), back?.freeEvents)
    }

    @Test
    fun workPutOnAFreeEventSitsOnNoOnesTime() {
        val (lines, mem) = put("เขียนถ้อยคำ", 15 * 60)
        val busy = plan(mem, lines)
        val onEvent = busy.card("เขียนถ้อยคำ")!!
        assertTrue(onEvent.pinned)
        assertEquals(listOf("ร้านยาโลตัส ถึง 20:00" to 15), DayFill.overlapsOf(busy, onEvent), "on a busy event it says it overlaps")
        val free = plan(mem.copy(freeEvents = setOf(DayFill.freeKey(lotus))), lines)
        assertEquals(emptyList(), DayFill.overlapsOf(free, free.card("เขียนถ้อยคำ")!!))
    }

    // ---- ข. A moved task put back today at a time of the owner's own ----

    /** What the app writes when the owner puts a task at [at] (VM fillPlace): ⏳ today, the reminder, a pinned slot. */
    private fun put(title: String, at: Int): Pair<List<String>, DayFill.Memory> {
        val i = note.indexOfFirst { TaskLine.parse(it)?.title?.startsWith(title) == true }
        val after = TaskLine.setReminder(TaskLine.setDate(note[i], TaskLine.DateField.SCHEDULED, LocalDate.parse(day)), DayFill.clock(at), ReminderOn.SCHEDULED)
        val lines = note.toMutableList().also { it[i] = after }
        val id = DayFill.ident(tasks(lines).first { it.title.startsWith(title) })
        return lines to DayFill.Memory(
            day = day,
            picks = mapOf(id to DayFill.Pick.TAKE),
            accepted = mapOf(id to DayFill.Accepted(day, at, timed = true, pinned = true, len = 15)),
            lines = mapOf(id to DayFill.Line(note[i], after)),
        )
    }

    @Test
    fun aMovedTaskPutBackStaysWhereItIsPutEvenOnABusyEvent() {
        val (lines, mem) = put("เขียนถ้อยคำ", 11 * 60 + 15)
        val p = plan(mem, lines)
        val card = p.card("เขียนถ้อยคำ")
        assertNotNull(card, "it is back on today's plan")
        assertEquals(11 * 60 + 15, card.at)
        assertTrue(card.pinned)
        assertNull(p.moved("เขียนถ้อยคำ"))
        assertNotNull(p.moved("ปรับระบบ"), "the others stay moved")
    }
}
