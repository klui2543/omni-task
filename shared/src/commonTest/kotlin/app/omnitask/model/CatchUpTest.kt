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
 * When real life does not follow the plan: the owner's mix (mock plan-catchup, agreed 2026-10-30). Friday 30 Oct, a
 * night shift at 02:30, so the plan stops at 20:00; "เขียนถ้อยคำ" was taken for 16:45, as in the mock.
 */
class CatchUpTest {
    private val shiftCal = 7L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(job = RealDay.Job.WEEKDAYS, shiftCalendars = setOf(shiftCal))
    private val events = listOf(
        CalendarEvent(1, "O11 รอบ 3 จาก พี่ปันปัน", LocalDateTime.parse("2026-10-31T02:30"), LocalDateTime.parse("2026-10-31T08:30"), calendarId = shiftCal),
    )
    private val note = listOf(
        "- [ ] เขียนถ้อยคำให้ครบ 25 ข้อ (เหลือ 22) #omni 🔼 ➕ 2026-10-20 📅 2026-10-21",
        "- [ ] How to ดูหุ้นแบบรอบด้านและลึก #My-space 🔼 ➕ 2026-09-29",
        "- [ ] ปรับ Project Omni ให้รู้ใจมากขึ้น #omni 🔼 ➕ 2026-10-20 📅 2026-10-30",
    )
    private val day = "2026-10-30"

    private fun tasks(lines: List<String>) = VaultText.parseFile("f", "📁 Folder/หลังบ้าน/Omni/Omni note.md", lines.joinToString("\n"))
    private fun m(h: Int, min: Int = 0) = h * 60 + min
    private fun id(lines: List<String>, title: String) = DayFill.ident(tasks(lines).first { it.title.startsWith(title) })
    private fun plan(now: String, lines: List<String> = note, memory: DayFill.Memory = DayFill.Memory()) =
        DayFill.plan(DayFill.Input(tasks(lines), events, profile, facts, LocalDateTime.parse(now), memory))
    private fun DayFill.Plan.card(title: String) = placed.firstOrNull { it.item.title.startsWith(title) }

    /** Taken (or put, [pinned]) the way the app does it: ⏳ today and 🎯 at [at] on the line, the memory keeps both. */
    private fun taken(
        title: String, at: Int, pinned: Boolean = false, lines: List<String> = note, extra: DayFill.Memory = DayFill.Memory(day = day),
    ): Pair<List<String>, DayFill.Memory> {
        val i = lines.indexOfFirst { TaskLine.parse(it)?.title?.startsWith(title) == true }
        val before = lines[i]
        val after = TaskLine.setReminder(TaskLine.setDate(before, TaskLine.DateField.SCHEDULED, LocalDate.parse(day)), DayFill.clock(at), ReminderOn.SCHEDULED)
        val out = lines.toMutableList().also { it[i] = after }
        val ident = id(out, title)
        return out to extra.copy(
            picks = extra.picks + (ident to DayFill.Pick.TAKE),
            accepted = extra.accepted + (ident to DayFill.Accepted(day, at, pinned = pinned, len = 15)),
            lines = extra.lines + (ident to DayFill.Line(before, after)),
        )
    }

    private var chosenBefore: Set<String>? = null
    @BeforeTest fun projects() { chosenBefore = Projects.chosen; Projects.chosen = setOf("omni") }
    @AfterTest fun restore() { Projects.chosen = chosenBefore }

    // ---- 1. At the slot's time: how long is left to make it today ----

    @Test
    fun theLatestStartIsWhenThePlanStopsLessTheLength() {
        val p = plan("$day" + "T16:45")
        assertEquals(m(20), p.end, "a night shift at 02:30: the plan stops at 20:00")
        assertEquals(m(19, 45), DayFill.latestStart(p, 15))
        assertEquals("ถ้ายังไม่เริ่ม เหลืออีก 3 ชม. ถึงจะทันวันนี้", DayFill.leftText(m(19, 45), m(16, 45)))
        assertEquals("ถ้ายังไม่เริ่ม เหลืออีก 1 ชม. 40 นาที ถึงจะทันวันนี้", DayFill.leftText(m(19, 45), m(18, 5)))
        assertEquals("ถ้ายังไม่เริ่ม เหลืออีก 5 นาที ถึงจะทันวันนี้", DayFill.leftText(m(19, 45), m(19, 40)))
        assertNull(DayFill.leftText(m(19, 45), m(19, 45)), "once the latest start went by, nothing is counted")
    }

    @Test
    fun theReminderAtTheSlotSaysHowLongIsLeft() {
        val (lines, mem) = taken("เขียนถ้อยคำ", m(16, 45))
        val t = tasks(lines).first { it.title.startsWith("เขียนถ้อยคำ") }
        val known = mem.copy(stop = m(20))
        assertEquals(
            "ถ้ายังไม่เริ่มตอนนี้ เหลืออีก 3 ชม. ถึงจะทำทันวันนี้ (เริ่มช้าสุด 19:45)",
            DayFill.reminderLeft(known, t, LocalDateTime.parse("${day}T16:45")),
        )
        // What the reminder said before stays when any of it is not known.
        assertNull(DayFill.reminderLeft(mem, t, LocalDateTime.parse("${day}T16:45")), "the plan's stop is not known today")
        assertNull(DayFill.reminderLeft(known.copy(day = "2026-10-29"), t, LocalDateTime.parse("${day}T16:45")), "the stop kept is another day's")
        val id = id(lines, "เขียนถ้อยคำ")
        assertNull(DayFill.reminderLeft(known.copy(accepted = mapOf(id to DayFill.Accepted(day, m(16, 45)))), t, LocalDateTime.parse("${day}T16:45")), "taken before the length was kept")
        assertNull(DayFill.reminderLeft(known.copy(accepted = mapOf(id to DayFill.Accepted(day, m(16, 45), timed = false, len = 15))), t, LocalDateTime.parse("${day}T16:45")), "the task's own reminder, not the slot's")
        assertNull(DayFill.reminderLeft(known.copy(accepted = mapOf(id to DayFill.Accepted("2026-10-29", m(16, 45), len = 15))), t, LocalDateTime.parse("${day}T16:45")), "taken for another day")
        assertNull(DayFill.reminderLeft(known, t, LocalDateTime.parse("${day}T19:50")), "too late today")
    }

    @Test
    fun whatThePhoneKeepsStillReadsFromBefore() {
        // CONTROL: memory written before this change (no pinned, len or stop) reads with them off.
        val old = """{"day":"2026-10-30","accepted":{"x":{"day":"2026-10-30","at":1005}}}"""
        val m0 = assertNotNull(DayFill.decode(old))
        assertEquals(DayFill.Accepted("2026-10-30", 1005, timed = true, pinned = false, len = 0), m0.accepted["x"])
        assertNull(m0.stop)
        val kept = DayFill.Memory(day = day, accepted = mapOf("x" to DayFill.Accepted(day, 1110, pinned = true, len = 15)), stop = 1200)
        assertEquals(kept, DayFill.decode(DayFill.encode(kept)))
        assertNull(kept.on(LocalDate.parse("2026-10-31")).stop, "a new day forgets the stop")
    }

    // ---- 2 and 3. Past its slot and not ticked: it stays, then free times are offered ----

    @Test
    fun aTakenSlotNotTickedStaysWhereItWasAndTheFreeTimesAfterNowAreOffered() {
        val (lines, mem) = taken("เขียนถ้อยคำ", m(16, 45))
        val p = plan("${day}T17:05", lines, mem)
        val c = assertNotNull(p.card("เขียนถ้อยคำ"))
        assertEquals(m(16, 45), c.at, "nothing chases it along the clock")
        val offered = DayFill.suggest(p, c.len, m(17, 5))
        assertTrue(offered.isNotEmpty(), "rows " + p.rows.map { (it.at?.let(DayFill::hm) ?: "") + " " + it::class.simpleName + " " + ((it as? DayFill.Row.Card)?.placed?.let { c -> c.item.title + " " + c.len } ?: "") } + " open " + p.open)
        offered.forEach { s ->
            assertTrue(s >= m(17, 5) && s + c.len <= p.end, DayFill.hm(s))
            assertEquals(emptyList(), DayFill.overlaps(p, s, c.len, c.item.id), "an offered time sits on nothing: " + DayFill.hm(s))
        }
        assertEquals(offered.sorted(), offered)
        assertEquals(DayFill.latestStart(p, c.len), offered.last(), "the free time runs to the plan's stop here, so the last is the latest start")
    }

    @Test
    fun aFullEveningOffersNothing() {
        // At 18:05 dinner, own time and the other task due fill the evening to 19:50: nothing free fits 15 minutes.
        val (lines, mem) = taken("เขียนถ้อยคำ", m(16, 45))
        val p = plan("${day}T18:05", lines, mem)
        assertEquals(m(16, 45), p.card("เขียนถ้อยคำ")?.at)
        assertEquals(emptyList(), DayFill.suggest(p, 15, m(18, 5)))
    }

    @Test
    fun theOffersAreTheFirstFreeTimeAHalfHourLaterAndTheLatest() {
        val base = plan("${day}T18:05")
        // The mock's board 3: free 19:00 to 20:00 only.
        val mock = base.copy(open = listOf(m(18, 5) to m(18, 15), m(19) to m(20)))
        assertEquals(listOf(m(19), m(19, 30), m(19, 45)), DayFill.suggest(mock, 15, m(18, 5)))
        // Two free stretches: the second one's start comes before a later half hour of the first.
        val two = base.copy(open = listOf(m(18, 20) to m(18, 50), m(20) to m(21)))
        assertEquals(listOf(m(18, 20), m(20), m(20, 45)), DayFill.suggest(two, 15, m(18, 5)))
        // A stretch that has begun counts from now, on the 5-minute step.
        assertEquals(listOf(m(18, 10), m(18, 45)), DayFill.suggest(base.copy(open = listOf(m(17) to m(19))), 15, m(18, 7)))
        // Room for one only: one offer.
        assertEquals(listOf(m(19)), DayFill.suggest(base.copy(open = listOf(m(19) to m(19, 15))), 15, m(18, 5)))
        assertEquals(emptyList(), DayFill.suggest(base.copy(open = listOf(m(19) to m(19, 10))), 15, m(18, 5)))
    }

    @Test
    fun theFreeStretchesAreTheGapsAndTheShortOnesBetween() {
        val p = plan("${day}T16:45")
        val gaps = p.rows.filterIsInstance<DayFill.Row.Gap>().map { it.at to it.at + it.len }
        assertTrue(gaps.isNotEmpty())
        assertTrue(p.open.containsAll(gaps), "${p.open} ${gaps}")
        p.open.forEach { (a, b) ->
            assertTrue(a >= p.start && b <= p.end)
            assertEquals(emptyList(), DayFill.overlaps(p, a, b - a, ""), "a free stretch sits on nothing")
        }
    }

    // ---- 4. Put by the owner: it stays, nothing is pushed, the overlap is said ----

    @Test
    fun aTaskTheOwnerPutsOnTopOfAnotherStaysAndPushesNothing() {
        val before = plan("${day}T16:45")
        val stocks = assertNotNull(before.card("How to ดูหุ้น"))
        val (lines, mem) = taken("ปรับ Project Omni", stocks.at + 15, pinned = true)
        val p = plan("${day}T16:45", lines, mem)
        val omni = assertNotNull(p.card("ปรับ Project Omni"))
        assertEquals(stocks.at + 15, omni.at, "where the owner put it")
        assertTrue(omni.pinned)
        val after = assertNotNull(p.card("How to ดูหุ้น"))
        assertEquals(stocks.at, after.at, "own time is not pushed")
        assertEquals(stocks.len, after.len)
        assertEquals(before.card("เขียนถ้อยคำ")?.at, p.card("เขียนถ้อยคำ")?.at)
        assertEquals(listOf("How to ดูหุ้นแบบรอบด้านและลึก" to 15), DayFill.overlaps(p, omni.at, omni.len, omni.item.id))
        assertEquals(listOf("ปรับ Project Omni ให้รู้ใจมากขึ้น" to 15), DayFill.overlaps(p, after.at, after.len, after.item.id))
        assertEquals("ซ้อนกับ How to ดูหุ้นแบบ… 15 นาที", DayFill.overlapText(DayFill.overlaps(p, omni.at, omni.len, omni.item.id)))
        // Both rows say it; a card nobody put sits on nothing.
        assertEquals(listOf("How to ดูหุ้นแบบรอบด้านและลึก" to 15), DayFill.overlapsOf(p, omni))
        assertEquals(listOf("ปรับ Project Omni ให้รู้ใจมากขึ้น" to 15), DayFill.overlapsOf(p, after))
        assertEquals(emptyList(), DayFill.overlapsOf(p, p.card("เขียนถ้อยคำ")!!))
        // The plan's own card on the day job (as the owner's own reminder time can be) is not flagged: only put cards are.
        val onJob = p.card("เขียนถ้อยคำ")!!.copy(at = m(16), len = 60)
        assertEquals(30, DayFill.overlaps(p, onJob.at, onJob.len, onJob.item.id).single { it.first.startsWith(RealDay.jobTitle) }.second)
        assertEquals(emptyList(), DayFill.overlapsOf(p, onJob))
        assertEquals("ซ้อนกับ How to ดูหุ้น 15 นาที", DayFill.overlapText(DayFill.overlapsOf(p, omni)) { if (it.startsWith("How to")) "How to ดูหุ้น" else it })
        assertEquals(emptyList(), p.moves, "the line already reads as put")
        assertEquals(emptyList(), p.letGo)
        // CONTROL: the same slot only taken (not put by the owner) does not fit there and moves; own time keeps its place.
        val (l2, m2) = taken("ปรับ Project Omni", stocks.at + 15, pinned = false)
        val q = plan("${day}T16:45", l2, m2)
        assertFalse(q.card("ปรับ Project Omni")!!.pinned)
        val pushed = assertNotNull(q.card("How to ดูหุ้น"))
        assertTrue(pushed.at != stocks.at || pushed.len != stocks.len, "a taken slot holds its place first and own time gives way")
    }

    @Test
    fun aPutTaskKeepsItsTimeAfterItGoesByAndBusyMovesItOn() {
        val (lines, mem) = taken("เขียนถ้อยคำ", m(17), pinned = true)
        val p = plan("${day}T18:05", lines, mem)
        val c = assertNotNull(p.card("เขียนถ้อยคำ"))
        assertEquals(m(17), c.at)
        assertTrue(c.pinned)
        assertFalse(c.running)
        val running = assertNotNull(plan("${day}T17:05", lines, mem).card("เขียนถ้อยคำ"))
        assertTrue(running.running, "under way between 17:00 and 17:15")
        // "ช่วงนี้ไม่ว่าง" on it: it moves on like any card.
        val busy = plan("${day}T16:45", lines, mem.copy(notBefore = mapOf(c.item.id to m(17, 45))))
        val moved = assertNotNull(busy.card("เขียนถ้อยคำ"))
        assertFalse(moved.pinned)
        assertTrue(moved.at >= m(17, 45), DayFill.hm(moved.at))
    }

    @Test
    fun workTheDayPlanPlacesGoesRoundAPutSlotAllEvening() {
        val (lines, mem) = taken("เขียนถ้อยคำ", m(19), pinned = true)
        var checked = 0
        for (now in m(16, 45)..m(19, 30) step 5) {
            val p = plan("${day}T" + DayFill.hm(now), lines, mem)
            val put = assertNotNull(p.card("เขียนถ้อยคำ"))
            assertEquals(m(19), put.at)
            p.placed.filter { !it.pinned && it.item.kind != DayFill.Kind.LIFE && it.item.kind != DayFill.Kind.MINE }.forEach { c ->
                assertTrue(c.at + c.len <= m(19) || c.at >= m(19, 15), DayFill.hm(now) + ": " + c.item.title + " at " + DayFill.hm(c.at))
                checked++
            }
        }
        assertTrue(checked > 0)
    }

    @Test
    fun theFreeStretchesKeepClearBeforeAnEventAndStopWithThePlan() {
        val event = CalendarEvent(9, "นัดทำฟัน", LocalDateTime.parse("${day}T19:30"), LocalDateTime.parse("${day}T19:45"), calendarId = 3)
        val p = DayFill.plan(DayFill.Input(tasks(note), events + event, profile, facts, LocalDateTime.parse("${day}T16:45")))
        assertTrue(p.open.none { (a, b) -> a < m(19, 30) && b > m(19, 15) }, "15 minutes before the event stay clear: ${p.open}")
        // Put after the plan stops (before bed): no free stretch is offered after the stop.
        val (lines, mem) = taken("เขียนถ้อยคำ", m(20, 15), pinned = true)
        val q = plan("${day}T16:45", lines, mem)
        assertEquals(m(20, 15), q.card("เขียนถ้อยคำ")?.at)
        assertTrue(q.open.all { it.second <= q.end }, "${q.open}")
    }

    @Test
    fun aPutSlotCountsAgainstTheBuffer() {
        // A plain evening (no shift tonight), 2 h 30 left at 19:00: a third is kept as the buffer.
        val lines0 = listOf(
            "- [/] งานกำลังทำ #omni 🔼 ➕ 2026-10-20",
            "- [ ] งานที่วางเอง #omni ➕ 2026-10-20",
        )
        fun plain(lines: List<String>, memory: DayFill.Memory = DayFill.Memory()) =
            DayFill.plan(DayFill.Input(tasks(lines), emptyList(), profile, facts, LocalDateTime.parse("${day}T19:00"), memory))
        val free = plain(lines0)
        assertEquals(150, free.free)
        assertNotNull(free.card("งานกำลังทำ"), "with no put slot it fits")
        // Put at 20:30 (an hour, to when the plan stops): it uses free time too, so the work under way no longer
        // fits with the buffer kept and waits.
        val (lines, mem) = taken("งานที่วางเอง", m(20, 30), pinned = true, lines = lines0)
        val p = plain(lines, mem)
        val put = assertNotNull(p.card("งานที่วางเอง"))
        assertTrue(put.pinned)
        assertEquals(p.end, put.at + put.len)
        assertNull(p.card("งานกำลังทำ"), "placed: " + p.placed.map { it.item.title + " " + DayFill.hm(it.at) })
        val waits = assertNotNull(p.later.firstOrNull { it.item.title.startsWith("งานกำลังทำ") })
        assertEquals("ย้ายไปพรุ่งนี้ เพื่อเหลือช่องกันชนไว้", waits.why)
    }

    @Test
    fun anErrandAndAnEventAreSaidAsOverlapsToo() {
        val p = plan("${day}T16:45", memory = DayFill.Memory(day = day, errand = DayFill.Errand(m(17, 30), m(18, 30))))
        val errand = p.rows.filterIsInstance<DayFill.Row.Locked>().single { it.title.startsWith("ธุระ") }
        assertEquals(m(18, 30), errand.until)
        assertEquals(listOf(errand.title to 30), DayFill.overlaps(p, m(18), 45, "x").filter { it.first == errand.title })
        val job = DayFill.overlaps(p, m(16), 60, "x").single { it.first.startsWith(RealDay.jobTitle) }
        assertEquals(30, job.second, "the day job ends 16:30")
        assertNull(DayFill.overlapText(emptyList()))
    }

    @Test
    fun aNameIsCutAtAWholeLetter() {
        assertEquals("ดู…", DayFill.short("ดูหุ้น", 1))
        assertEquals("หุ้…", DayFill.short("หุ้นแบบ", 1))
        assertEquals("ทำ…", DayFill.short("ทำงาน", 1))
        assertEquals("สั้น", DayFill.short("สั้น"))
    }
}
