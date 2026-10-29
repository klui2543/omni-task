package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.notify.CalendarEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * The secretary's plan for the rest of today (step 2), on the owner's real week of 29 Oct 2026: the day job on
 * weekdays, the night shifts "O11 รอบ 3" at 02:30 on 31 Oct and 3 Nov in the shift calendar, the pharmacy shift at
 * Lotus on Saturday, and a calendar of someone else's that takes none of the owner's time. The tasks are shaped like
 * the lines in the owner's task note. The three days are the three boards of mock v12, which the owner agreed.
 */
class DayFillTest {

    private val shiftCal = 7L
    private val ownCal = 3L
    private val partnerCal = 5L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(
        job = RealDay.Job.WEEKDAYS,
        shiftCalendars = setOf(shiftCal),
        othersCalendars = setOf(partnerCal),
    )

    private fun timed(id: Long, cal: Long, title: String, from: String, to: String) =
        CalendarEvent(id, title, LocalDateTime.parse(from), LocalDateTime.parse(to), calendarId = cal)

    private val events = listOf(
        timed(1, shiftCal, "O11 รอบ 3 จาก พี่ปันปัน", "2026-10-31T02:30", "2026-10-31T08:30"),
        timed(2, shiftCal, "O11 รอบ 3 จาก อิง", "2026-11-03T02:30", "2026-11-03T08:30"),
        timed(10, ownCal, "ร้านยาโลตัส", "2026-10-31T11:00", "2026-10-31T20:00"),
        timed(20, partnerCal, "คลังยาดี ท่าพระ", "2026-10-29T17:00", "2026-10-29T22:00"),
    )

    /** The task note on 29 Oct, as its lines read (two myspace tasks, work under way, quiet work, a weekly routine). */
    private val note = listOf(
        "- [ ] ปรับ Project Omni ให้รู้ใจมากขึ้น #omni 🔼 ➕ 2026-10-20 📅 2026-10-29",
        "- [ ] แก้ไขระบบแจ้งเตือนของ PD manager #Siriraj #remind-at-due ⏰ 06:00 ➕ 2026-10-25 📅 2026-10-30",
        "- [ ] จัดการ Note #Siriraj #remind-at-scheduled 🎯 08:00 🔼 ➕ 2026-09-29",
        "- [ ] Routine 🔺 🔁 every week ⏳ 2026-10-17",
        "- [ ] How to ดูหุ้นแบบรอบด้านและลึก #My-space #remind-at-scheduled 🎯 13:30 🔼 ➕ 2026-09-29 ⏳ 2026-10-11",
        "    - ค้าง 2026-10-14 ยังติดงานที่มีคนรออยู่ และกลับห้องไปก็เหนื่อยแล้ว",
        "- [ ] หนังสือที่ควรอ่าน ถ้าชอบ How to decide #My-space ➕ 2026-10-01",
        "- [ ] Mock UX UI และ จัดการโครงสร้างหลังบ้าน #รอ #Siriraj/Peddose ⏫ ➕ 2026-10-02",
        "    - ค้าง 2026-10-26 งานใหญ่กว่าที่คิด",
        "- [/] Flow น้ำเกลือ #Siriraj #รอ ⏫ ➕ 2026-10-02",
        "    - เหลือแค่จัดองค์ประกอบ + แปะรูป งานต่อไปจริงๆ คือการอัดวิดีโอประกอบการสอน",
        "    - ค้าง 2026-10-26 น้องๆที่ห้องพอทำเสร็จแล้วจากตัวเวอร์ชั่นโปรโตไทล์",
        "- [ ] Super-app-รวม หลายๆ โปรแกรมให้เปิดง่ายไม่ต้อง รีเฟรชข้ามแอป #omni ➕ 2026-10-05",
        "- [x] นับยาประจำเดือนแบบ automation #Siriraj 🔼 ➕ 2026-09-29 📅 2026-09-30 ✅ 2026-09-30",
    )

    private fun tasks(lines: List<String> = note) = VaultText.parseFile("f", "📁 Folder/หลังบ้าน/Omni/Omni note.md", lines.joinToString("\n"))

    private fun input(
        now: String,
        lines: List<String> = note,
        extra: List<CalendarEvent> = emptyList(),
        memory: DayFill.Memory = DayFill.Memory(),
        moves: (Task) -> Int = { 0 },
    ) = DayFill.Input(tasks(lines), events + extra, profile, facts, LocalDateTime.parse(now), memory, moves)

    private fun m(h: Int, min: Int = 0) = h * 60 + min

    private fun DayFill.Plan.card(title: String) = placed.firstOrNull { it.item.title.startsWith(title) }

    private fun DayFill.Plan.work() = placed.filter { it.item.kind != DayFill.Kind.LIFE && it.item.kind != DayFill.Kind.MINE }

    private fun id(lines: List<String>, title: String) = DayFill.ident(tasks(lines).first { it.title.startsWith(title) })

    /**
     * A slot taken the way the app takes it: the task's line gets ⏳ [day] and 🎯 at [at], and the memory keeps what the
     * line was and what was written ([extra] is the rest of the memory).
     */
    private fun taken(
        title: String, at: Int, day: String = "2026-10-29", lines: List<String> = note, extra: DayFill.Memory = DayFill.Memory(day = day),
    ): Pair<List<String>, DayFill.Memory> {
        val i = lines.indexOfFirst { it.startsWith("- [") && TaskLine.parse(it)?.title?.startsWith(title) == true }
        val before = lines[i]
        val after = TaskLine.setReminder(TaskLine.setDate(before, TaskLine.DateField.SCHEDULED, LocalDate.parse(day)), DayFill.clock(at), ReminderOn.SCHEDULED)
        val out = lines.toMutableList().also { it[i] = after }
        val ident = id(out, title)
        return out to extra.copy(accepted = extra.accepted + (ident to DayFill.Accepted(day, at)), lines = extra.lines + (ident to DayFill.Line(before, after)))
    }

    private var chosenBefore: Set<String>? = null

    @BeforeTest
    fun projects() {
        chosenBefore = Projects.chosen
        Projects.chosen = setOf("omni", "Siriraj")
    }

    @AfterTest
    fun restore() {
        Projects.chosen = chosenBefore
    }

    // ---- Board 1: Thursday 29 Oct, a normal day, opened at 16:45 ----

    @Test
    fun aNormalThursdayRunsFromTheEndOfTheJobToAnHourBeforeBed() {
        val plan = DayFill.plan(input("2026-10-29T16:45"))
        assertEquals(DayFill.Mode.NORMAL, plan.mode)
        assertEquals(m(16, 45), plan.start, "the job ends 16:30, 15 minutes clear after it")
        assertEquals(m(21, 30), plan.end, "an hour before bed at 22:30")
        assertNull(plan.banner)
        // The other person's calendar (17:00 to 22:00 that evening) takes none of the owner's time.
        assertEquals(m(21, 30) - m(16, 45), plan.free)
        assertTrue(plan.rows.none { it is DayFill.Row.Locked && it.title.startsWith("คลังยาดี") })
        val job = plan.rows.filterIsInstance<DayFill.Row.Locked>().single { it.title.startsWith(RealDay.jobTitle) }
        assertEquals(m(8, 30), job.at)
        assertTrue(job.past, "the job is over by 16:45")
        val sleep = plan.rows.filterIsInstance<DayFill.Row.Locked>().last()
        assertEquals(m(22, 30), sleep.at)
        assertTrue(sleep.why.contains("21:30 ถึง 22:30"), sleep.why)
    }

    @Test
    fun theTwoThingsDueGoFirstForFifteenMinutesEach() {
        val plan = DayFill.plan(input("2026-10-29T16:45"))
        val omni = assertNotNull(plan.card("ปรับ Project Omni"))
        val pd = assertNotNull(plan.card("แก้ไขระบบแจ้งเตือนของ PD manager"))
        assertEquals(DayFill.Kind.DUE, omni.item.kind)
        assertEquals(m(16, 45), omni.at, "due today, so first")
        assertEquals(15, omni.len)
        assertTrue(omni.item.why.startsWith("ครบกำหนดวันนี้"), omni.item.why)
        assertEquals(m(17, 0), pd.at)
        assertTrue(pd.item.why.startsWith("ส่งพรุ่งนี้"), pd.item.why)
        // PD manager has its own reminder, but for tomorrow 06:00: today it is just work due tomorrow.
        assertTrue(!pd.item.own)
        assertEquals(omni.item.id, plan.next, "the first work card is shown open")
    }

    @Test
    fun dinnerMovesInItsWindowAndMyspaceStartsOnTime() {
        val plan = DayFill.plan(input("2026-10-29T16:45"))
        val dinner = assertNotNull(plan.placed.firstOrNull { it.item.id == "dinner" })
        assertTrue(dinner.at >= m(18) && dinner.at + dinner.len <= m(19, 45), "dinner ${DayFill.hm(dinner.at)}")
        assertEquals(45, dinner.len)
        val mine = assertNotNull(plan.placed.firstOrNull { it.item.kind == DayFill.Kind.MINE })
        assertEquals(m(19), mine.at, "myspace starts on time")
        assertEquals(60, mine.len)
        assertTrue(mine.item.title.startsWith("How to ดูหุ้น") || mine.item.title.startsWith("หนังสือ"))
        assertEquals(1, mine.item.more, "one other myspace task to swap to")
    }

    @Test
    fun workUnderWayContinuesFromTheNoteWrittenLast() {
        val plan = DayFill.plan(input("2026-10-29T16:45"))
        val flow = assertNotNull(plan.card("Flow น้ำเกลือ"))
        assertEquals(45, flow.len)
        assertTrue(flow.item.why.startsWith("ต่อจากที่จดไว้"), flow.item.why)
        assertTrue(flow.item.why.contains("26 ต.ค.") && flow.item.why.contains("น้องๆที่ห้อง"), flow.item.why)
    }

    @Test
    fun aThirdOfTheFreeTimeStaysClear() {
        val plan = DayFill.plan(input("2026-10-29T16:45"))
        assertTrue(plan.buffer >= plan.free / 3, "buffer ${plan.buffer} of ${plan.free}")
        assertEquals(plan.free - plan.planned - plan.placed.filter { it.item.kind == DayFill.Kind.LIFE }.sumOf { it.len }, plan.buffer)
        // With more work under way than fits, the rest moves to tomorrow and says why.
        val busy = note + (1..6).map { "- [/] งานที่ทำค้าง $it #omni ➕ 2026-10-2$it\n    - ค้าง 2026-10-28 ทำไปครึ่งหนึ่ง" }
        val full = DayFill.plan(input("2026-10-29T16:45", busy))
        assertTrue(full.buffer >= full.free / 3, "buffer ${full.buffer} of ${full.free}")
        assertTrue(full.later.any { it.why.contains("เหลือช่องกันชน") }, full.later.joinToString { it.why })
    }

    @Test
    fun nothingPlacedOverlapsAnotherOrTheJob() {
        val plan = DayFill.plan(input("2026-10-29T16:45"))
        val spans = plan.placed.map { it.at until it.at + it.len }
        spans.forEachIndexed { i, a -> spans.drop(i + 1).forEach { b -> assertTrue(a.intersect(b).isEmpty(), "$a and $b") } }
        assertTrue(plan.placed.all { it.at >= plan.start && it.at + it.len <= plan.end })
    }

    // ---- At most two must-dos a day ----

    @Test
    fun aThirdThingDueTodayIsOnlyAnOffer() {
        val lines = note + "- [ ] ส่งสรุปยอดยาให้หัวหน้า #Siriraj ➕ 2026-10-22 📅 2026-10-29"
        val plan = DayFill.plan(input("2026-10-29T16:45", lines))
        val items = plan.placed.map { it.item } + plan.later.map { it.item }
        val musts = items.filter { it.must && (it.kind == DayFill.Kind.DUE || it.kind == DayFill.Kind.URGENT || it.kind == DayFill.Kind.AGAIN) }
        assertEquals(2, musts.size, musts.joinToString { it.title })
        // The two due today stay; PD manager, due tomorrow, becomes the offer.
        assertEquals(setOf("ปรับ Project Omni ให้รู้ใจมากขึ้น", "ส่งสรุปยอดยาให้หัวหน้า"), musts.map { it.title }.toSet())
        val third = items.single { it.kind == DayFill.Kind.DUE && !it.must }
        assertTrue(third.title.startsWith("แก้ไขระบบแจ้งเตือน"), third.title)
        assertTrue(third.why.contains("ครบ 2 อย่างแล้ว"), third.why)
    }

    @Test
    fun newUrgentWorkGoesBeforeEverythingElse() {
        val lines = note + "- [ ] หัวหน้าขอสรุปยอดยา #Siriraj ➕ 2026-10-29 📅 2026-10-30"
        val plan = DayFill.plan(input("2026-10-29T16:45", lines))
        val urgent = assertNotNull(plan.card("หัวหน้าขอสรุปยอดยา"))
        assertEquals(DayFill.Kind.URGENT, urgent.item.kind)
        assertEquals(m(16, 45), urgent.at)
        // It counts as one of the two must-dos: PD manager (due tomorrow) becomes an offer.
        val pd = (plan.placed.map { it.item } + plan.later.map { it.item }).single { it.title.startsWith("แก้ไขระบบแจ้งเตือน") }
        assertTrue(!pd.must)
    }

    // ---- Board 2: Monday 2 Nov, a night shift at 02:30 ----

    @Test
    fun theEveningBeforeA0230ShiftIsLightAndStopsAt2000() {
        val lines = note.map { it.replace("📅 2026-10-29", "📅 2026-11-09").replace("📅 2026-10-30", "📅 2026-11-09") }
        val plan = DayFill.plan(input("2026-11-02T16:45", lines))
        assertEquals(DayFill.Mode.BEFORE_NIGHT, plan.mode)
        assertEquals(m(20), plan.end)
        assertTrue(plan.banner!!.contains("02:30") && plan.banner!!.contains("20:00"), plan.banner)
        val after = plan.rows.filterIsInstance<DayFill.Row.Locked>().filter { (it.at ?: 0) >= plan.end }
        assertEquals(listOf(m(21), 1440 + m(2, 30)), after.map { it.at }, "sleep at 21:00, then the shift at 02:30")
        assertEquals("02:30", DayFill.hm(after.last().at!!))
        assertTrue(after.last().title.startsWith("เวร O11 รอบ 3"), after.last().title)
        val mine = assertNotNull(plan.placed.firstOrNull { it.item.kind == DayFill.Kind.MINE })
        assertEquals(45, mine.len)
        assertTrue(mine.at >= m(18) && mine.at + mine.len <= m(20))
        val flow = assertNotNull(plan.card("Flow น้ำเกลือ"))
        assertEquals(30, flow.len, "work is shorter before a night shift")
    }

    // ---- Board 3: Tuesday 3 Nov, just off the night shift ----

    @Test
    fun theDayAfterANightShiftIsARecoveryDay() {
        val lines = note.map { it.replace("📅 2026-10-29", "📅 2026-11-03").replace("📅 2026-10-30", "📅 2026-11-04") }
        val week = DayFill.weekOf(LocalDate.parse("2026-11-03")).toString()
        val memory = DayFill.Memory(sprint = DayFill.SprintPick(week, listOf("omni", "Siriraj"), taken = true))
        val plan = DayFill.plan(input("2026-11-03T16:45", lines, memory = memory))
        assertEquals(DayFill.Mode.AFTER_NIGHT, plan.mode)
        assertEquals(m(20, 30), plan.end, "bed an hour earlier, at 21:30")
        assertTrue(plan.banner!!.startsWith("เพิ่งลงเวรดึก"))
        val items = plan.placed.map { it.item } + plan.later.map { it.item }
        assertTrue(items.none { it.must && it.kind != DayFill.Kind.LIFE }, items.filter { it.must }.joinToString { it.title })
        val omni = items.single { it.title.startsWith("ปรับ Project Omni") }
        assertTrue(omni.why.startsWith("วันพักฟื้น"), omni.why)
        assertTrue(items.none { it.kind == DayFill.Kind.PROJECT || it.kind == DayFill.Kind.NET }, "no sprint or net after a night shift")
        assertNull(plan.sprint)
        val before = plan.rows.filterIsInstance<DayFill.Row.Locked>().first { it.title.startsWith("เวร O11") }
        assertEquals(m(2, 30), before.at)
        assertTrue(before.past)
        val mine = assertNotNull(plan.placed.firstOrNull { it.item.kind == DayFill.Kind.MINE })
        assertEquals(30, mine.len)
    }

    // ---- Changes during the day ----

    @Test
    fun anErrandMovesWorkOutOfItAndBackWhenItEnds() {
        val before = DayFill.plan(input("2026-10-29T16:45"))
        val errand = DayFill.Memory(day = "2026-10-29", errand = DayFill.Errand(m(16, 45), m(18, 45)))
        val during = DayFill.plan(input("2026-10-29T16:45", memory = errand))
        assertTrue(during.placed.none { it.at < m(18, 45) }, during.placed.joinToString { "${it.item.title} ${DayFill.hm(it.at)}" })
        assertTrue(during.rows.any { it is DayFill.Row.Locked && it.title.startsWith("ธุระ ถึง 18:45") })
        // The two due today still fit after it.
        assertNotNull(during.card("ปรับ Project Omni"))
        assertNotNull(during.card("แก้ไขระบบแจ้งเตือนของ PD manager"))
        assertTrue(DayFill.news("มีธุระ 2 ชม.", before, during).contains("เลขาจัดใหม่ให้แล้ว"))
        val back = DayFill.plan(input("2026-10-29T16:45", memory = errand.copy(errand = null)))
        assertEquals(before.placed.map { it.item.id to it.at }, back.placed.map { it.item.id to it.at })
    }

    @Test
    fun ownTimeShrinksOnlyWhenTheMustDosWouldNotFit() {
        // A meeting until 20:00 leaves 20:15 to 21:30: myspace shrinks to its shortest so both due tasks fit.
        val meeting = timed(30, ownCal, "ประชุมทีม", "2026-10-29T17:00", "2026-10-29T20:00")
        val plan = DayFill.plan(input("2026-10-29T16:45", extra = listOf(meeting)))
        assertNotNull(plan.card("ปรับ Project Omni"))
        assertNotNull(plan.card("แก้ไขระบบแจ้งเตือนของ PD manager"))
        val mine = plan.placed.single { it.item.kind == DayFill.Kind.MINE }
        assertTrue(mine.short)
        assertEquals(30, mine.len)
        assertEquals(m(20, 15), mine.at)
        // Without the meeting nothing is short.
        assertTrue(DayFill.plan(input("2026-10-29T16:45")).placed.none { it.short })
    }

    @Test
    fun busyRightNowPushesTheSlotLaterOnTheSameDay() {
        val omni = id(note, "ปรับ Project Omni")
        val memory = DayFill.Memory(day = "2026-10-29", notBefore = mapOf(omni to m(17, 45)))
        val plan = DayFill.plan(input("2026-10-29T16:45", memory = memory))
        val card = assertNotNull(plan.card("ปรับ Project Omni"))
        assertTrue(card.at >= m(17, 45), DayFill.hm(card.at))
    }

    @Test
    fun aTakenSlotKeepsItsTimeAndFollowsANewEvent() {
        val (lines, memory) = taken("Flow น้ำเกลือ", m(17, 15))
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        assertEquals(m(17, 15), plan.card("Flow น้ำเกลือ")!!.at)
        assertTrue(plan.moves.isEmpty() && plan.letGo.isEmpty())
        // A new event lands on it: it moves, and its reminder is to follow.
        val call = timed(31, ownCal, "โทรคุยกับร้าน", "2026-10-29T17:00", "2026-10-29T18:00")
        val moved = DayFill.plan(input("2026-10-29T16:45", lines, extra = listOf(call), memory = memory))
        val at = moved.card("Flow น้ำเกลือ")!!.at
        assertTrue(at >= m(18, 15), DayFill.hm(at))
        assertEquals(listOf(at), moved.moves.filter { it.task.title.startsWith("Flow") }.map { it.at })
    }

    @Test
    fun busyOnATakenSlotMovesItAndItsReminderFollows() {
        val flow = id(note, "Flow น้ำเกลือ")
        val (lines, memory) = taken("Flow น้ำเกลือ", m(17, 15), extra = DayFill.Memory(day = "2026-10-29", notBefore = mapOf(flow to m(18, 30))))
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        val at = plan.card("Flow น้ำเกลือ")!!.at
        assertTrue(at >= m(18, 30), DayFill.hm(at))
        assertEquals(listOf(at), plan.moves.map { it.at })
    }

    @Test
    fun aTakenNetSlotIsNotOfferedAgainWithAnotherTask() {
        val reviewed = mapOf("- [ ] จัดการ Note" to "2026-10-10", "- [ ] Super-app" to "2026-10-12")
        val lines = note.flatMap { line -> reviewed.entries.firstOrNull { line.startsWith(it.key) }?.let { listOf(line, "    - ทบทวน ${it.value}") } ?: listOf(line) }
            .map { if (it.startsWith("- [ ] จัดการ Note")) it.replace("➕ 2026-09-29", "➕ 2026-09-29 ⏳ 2026-10-29") else it }
        val note2 = id(lines, "จัดการ Note")
        val memory = DayFill.Memory(
            day = "2026-10-29",
            picks = mapOf(note2 to DayFill.Pick.TAKE),
            accepted = mapOf(note2 to DayFill.Accepted("2026-10-29", m(17, 30), timed = false)),
            slots = mapOf("net" to note2),
        )
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        val items = plan.placed.map { it.item } + plan.later.map { it.item }
        assertTrue(items.none { it.kind == DayFill.Kind.NET && !it.title.startsWith("จัดการ Note") }, "Super-app is not brought up in its place")
        val taken = assertNotNull(plan.card("จัดการ Note"))
        assertEquals(m(17, 30), taken.at)
        // It stays the net's card, as it was offered, so "แตะการ์ดเพื่อเปลี่ยนใจ" frees the slot again.
        assertEquals(DayFill.Kind.NET, taken.item.kind)
        assertEquals("net", taken.item.slot)
        assertEquals(DayFill.Pick.TAKE, plan.rows.filterIsInstance<DayFill.Row.Card>().single { it.placed.item.title.startsWith("จัดการ Note") }.picked)
    }

    @Test
    fun anUndoBringsTheReminderBackToTheCard() {
        // Taken at 17:15; an errand moved it to 18:30 and the plan wrote 🎯 18:30. The errand is undone: the memory
        // says 17:15 again, the note still says 18:30, so the reminder is to follow back.
        val (taken, memory) = taken("Flow น้ำเกลือ", m(17, 15))
        val i = taken.indexOfFirst { it.startsWith("- [/] Flow") }
        val wrote = TaskLine.setReminder(taken[i], DayFill.clock(m(18, 30)), ReminderOn.SCHEDULED)
        val lines = taken.toMutableList().also { it[i] = wrote }
        val flow = id(lines, "Flow น้ำเกลือ")
        val undone = memory.copy(lines = memory.lines + (flow to memory.lines.getValue(flow).copy(after = wrote)))
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = undone))
        assertEquals(m(17, 15), plan.card("Flow น้ำเกลือ")!!.at)
        assertEquals(listOf(m(17, 15)), plan.moves.map { it.at })
        assertEquals(LocalDate.parse("2026-10-29"), plan.moves.single().day)
        assertTrue(plan.letGo.isEmpty())
    }

    @Test
    fun aLineTheOwnerChangedIsLeftAloneAndLetGo() {
        // Taken at 17:15, then the owner set 🎯 19:00 in Obsidian: the plan does not write over it.
        val (taken, memory) = taken("Flow น้ำเกลือ", m(17, 15))
        val lines = taken.map { if (it.startsWith("- [/] Flow")) TaskLine.setReminder(it, LocalTime.of(19, 0), ReminderOn.SCHEDULED) else it }
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        assertTrue(plan.moves.isEmpty(), plan.moves.joinToString())
        assertEquals(listOf(id(lines, "Flow น้ำเกลือ")), plan.letGo)
    }

    @Test
    fun aTakenSlotThatWentByIsNotChasedAlongTheClock() {
        val (lines, memory) = taken("Flow น้ำเกลือ", m(17, 15))
        val plan = DayFill.plan(input("2026-10-29T18:40", lines, memory = memory))
        assertTrue(plan.moves.isEmpty() && plan.letGo.isEmpty(), plan.moves.joinToString())
        assertEquals(m(17, 15), plan.card("Flow น้ำเกลือ")!!.at, "shown where it was")
    }

    @Test
    fun aTakenSlotWithNoRoomLeftMovesToTomorrowButNeverPastItsDueDate() {
        // Flow (no 📅) and Project Omni (📅 today) were taken; then an errand for the whole evening.
        val (once, first) = taken("Flow น้ำเกลือ", m(17, 15))
        val (lines, memory) = taken("ปรับ Project Omni", m(17, 0), lines = once, extra = first.copy(errand = DayFill.Errand(m(16, 45), null)))
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        assertNull(plan.card("Flow น้ำเกลือ"))
        val flow = plan.moves.single { it.task.title.startsWith("Flow") }
        assertEquals(LocalDate.parse("2026-10-30"), flow.day)
        assertEquals(m(17, 15), flow.at)
        assertTrue(plan.moves.none { it.task.title.startsWith("ปรับ Project Omni") }, "⏳ never goes past the 📅")
        assertTrue(plan.later.single { it.item.title.startsWith("ปรับ Project Omni") }.risk)
    }

    @Test
    fun aSwappedThenTakenOwnTimeKeepsTheTaskThatWasTaken() {
        val book = "หนังสือที่ควรอ่าน"
        val swapped = DayFill.Memory(day = "2026-10-29", swaps = mapOf("mine" to 1))
        val offered = DayFill.plan(input("2026-10-29T16:45", memory = swapped)).placed.single { it.item.kind == DayFill.Kind.MINE }
        assertTrue(offered.item.title.startsWith(book), offered.item.title)
        // Taken: it gets ⏳ today, so it now sorts first among the owner's own tasks.
        val (lines, taken) = taken(book, offered.at, extra = swapped.copy(picks = mapOf(offered.item.id to DayFill.Pick.TAKE)))
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = taken.copy(slots = mapOf("mine" to id(lines, book)))))
        val mine = plan.placed.single { it.item.kind == DayFill.Kind.MINE }
        assertTrue(mine.item.title.startsWith(book), mine.item.title)
        assertEquals(offered.at, mine.at)
        assertEquals("mine", mine.item.slot)
        assertTrue(plan.moves.isEmpty() && plan.letGo.isEmpty())
    }

    @Test
    fun quietWorkReviewedTodayKeepsItsPlaceInTheNet() {
        // The review card said "ทบทวนแล้วจะลงตารางให้": a review from the plan today does not make the task fresh.
        val lines = note.flatMap { if (it.startsWith("- [ ] จัดการ Note")) listOf(it, "    - ทบทวน 2026-10-29") else listOf(it) }
        val plan = DayFill.plan(input("2026-10-29T16:45", lines))
        val net = (plan.placed.map { it.item } + plan.later.map { it.item }).single { it.kind == DayFill.Kind.NET }
        assertTrue(net.title.startsWith("จัดการ Note"), net.title)
        assertEquals(15, net.len)
    }

    @Test
    fun oneQuestionADayEvenAfterOneIsAnswered() {
        val mock = "Mock UX UI"
        val superApp = "Super-app"
        val moves = { t: Task -> if (t.title.startsWith(mock)) 4 else if (t.title.startsWith(superApp)) 3 else 0 }
        val first = DayFill.plan(input("2026-10-29T16:45", moves = moves))
        assertTrue((first.placed.map { it.item } + first.later.map { it.item }).single { it.kind == DayFill.Kind.ASK }.title.startsWith(mock))
        // "ปล่อยไป": the task is cancelled and moved to the archive, so it is gone from the note. No new question today.
        val ask = "ask:" + id(note, mock)
        val answered = DayFill.Memory(day = "2026-10-29", picks = mapOf(ask to DayFill.Pick.DROP), asked = mapOf(id(note, mock) to "2026-10-29"))
        val left = note.filterNot { it.startsWith("- [ ] Mock UX UI") || it.startsWith("    - ค้าง 2026-10-26 งานใหญ่") }
        val plan = DayFill.plan(input("2026-10-29T16:45", left, memory = answered, moves = moves))
        assertTrue((plan.placed.map { it.item } + plan.later.map { it.item }).none { it.kind == DayFill.Kind.ASK })
        // The next day it may ask about the next one.
        val tomorrow = DayFill.plan(input("2026-10-30T16:45", left, memory = answered, moves = moves))
        assertTrue((tomorrow.placed.map { it.item } + tomorrow.later.map { it.item }).single { it.kind == DayFill.Kind.ASK }.title.startsWith(superApp))
    }

    @Test
    fun aChangeElsewhereInTheLineIsNotTheOwnerTakingTheSlotBack() {
        // Taken at 17:15; then its priority changed (the review card, or the owner). An event lands on the slot.
        val (taken, memory) = taken("Flow น้ำเกลือ", m(17, 15))
        val lines = taken.map { if (it.startsWith("- [/] Flow")) it.replace("⏫", "🔼") else it }
        val call = timed(31, ownCal, "โทรคุยกับร้าน", "2026-10-29T17:00", "2026-10-29T18:00")
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, extra = listOf(call), memory = memory))
        assertTrue(plan.letGo.isEmpty(), plan.letGo.joinToString())
        assertEquals(1, plan.moves.count { it.task.title.startsWith("Flow") })
    }

    @Test
    fun theLineBeforeThePlansWriteLandsIsWaitedFor() {
        // Taken, but the note still reads as before (the write is on its way): nothing to follow, nothing let go of.
        val (_, memory) = taken("Flow น้ำเกลือ", m(17, 15))
        val plan = DayFill.plan(input("2026-10-29T16:45", memory = memory))
        assertTrue(plan.moves.isEmpty() && plan.letGo.isEmpty(), plan.moves.joinToString() + plan.letGo)
    }

    @Test
    fun puttingBackTheMarksKeepsTheRestOfTheLine() {
        val (taken, memory) = taken("Flow น้ำเกลือ", m(17, 15))
        val line = memory.lines.getValue(id(taken, "Flow น้ำเกลือ"))
        val changed = line.after.replace("⏫", "🔼")
        val back = DayFill.withMarks(changed, DayFill.marks(line.before)!!)
        assertEquals(line.before.replace("⏫", "🔼"), back)
    }

    @Test
    fun aTakenSprintCardStaysWhenTheSprintIsOpenedAgain() {
        val week = DayFill.weekOf(LocalDate.parse("2026-10-29")).toString()
        val superApp = "Super-app"
        val (lines, taken) = taken(superApp, m(18, 0))
        val ident = id(lines, superApp)
        // "รับแล้ว" was tapped to choose the sprint again: it is not taken now, but the slot taken today still is.
        val memory = taken.copy(
            sprint = DayFill.SprintPick(week, listOf("omni", "Siriraj")),
            picks = mapOf(ident to DayFill.Pick.TAKE), slots = mapOf("sprint:omni" to ident),
        )
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        val card = assertNotNull(plan.card(superApp))
        assertEquals(DayFill.Kind.PROJECT, card.item.kind)
        assertEquals("sprint:omni", card.item.slot)
        assertEquals(m(18, 0), card.at)
    }

    @Test
    fun aTakenSlotMovedToTomorrowStaysTomorrows() {
        // No room was left today, so the plan wrote ⏳ tomorrow at the same time. The errand is over: it does not come back.
        val (lines, taken) = taken("Flow น้ำเกลือ", m(17, 15), day = "2026-10-30", extra = DayFill.Memory(day = "2026-10-29"))
        val ident = id(lines, "Flow น้ำเกลือ")
        val memory = taken.copy(picks = mapOf(ident to DayFill.Pick.TAKE))
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        assertNull(plan.card("Flow น้ำเกลือ"))
        assertTrue(plan.later.none { it.item.title.startsWith("Flow") })
        assertTrue(plan.moves.isEmpty() && plan.letGo.isEmpty())
        // Tomorrow it is that day's taken slot, at its time.
        val next = DayFill.plan(input("2026-10-30T16:45", lines, memory = memory.on(LocalDate.parse("2026-10-30"))))
        assertEquals(m(17, 15), next.card("Flow น้ำเกลือ")?.at)
    }

    @Test
    fun clockTimesWrapRoundMidnight() {
        assertEquals("22:00", DayFill.hm(-120))
        assertEquals("02:30", DayFill.hm(1440 + m(2, 30)))
        assertEquals(LocalTime.of(23, 59), DayFill.clock(-1))
    }

    @Test
    fun theOwnersOwnReminderTimeNeverMoves() {
        val lines = note + "- [ ] โทรหาร้านซ่อมโน้ตบุ๊ค #remind-at-scheduled 🎯 17:00 ➕ 2026-10-27 ⏳ 2026-10-29"
        val call = timed(31, ownCal, "คุยงาน", "2026-10-29T16:50", "2026-10-29T17:30")
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, extra = listOf(call)))
        val own = assertNotNull(plan.card("โทรหาร้านซ่อม"))
        assertEquals(m(17), own.at)
        assertTrue(own.item.own)
        assertTrue(plan.moves.none { it.task.title.startsWith("โทรหาร้านซ่อม") })
    }

    // ---- The review card, the 14-day net and the question after three moves ----

    @Test
    fun quietWorkComesUpThroughTheReviewCardFirst() {
        val plan = DayFill.plan(input("2026-10-29T16:45"))
        val items = plan.placed.map { it.item } + plan.later.map { it.item }
        // จัดการ Note has been quiet the longest (30 days) and was never reviewed.
        val review = items.single { it.kind == DayFill.Kind.REVIEW }
        assertTrue(review.title.startsWith("จัดการ Note"), review.title)
        assertTrue(review.why.startsWith("ยังไม่ได้ทบทวน"))
        assertEquals(5, review.len)
        assertTrue(items.none { it.kind == DayFill.Kind.NET })
        // The weekly routine repeats, so the review card never takes it: it is planned as it is.
        val routine = items.single { it.title.startsWith("Routine") }
        assertEquals(DayFill.Kind.WORK, routine.kind)
    }

    @Test
    fun reviewedQuietWorkGetsFifteenMinutesFromTheNet() {
        val reviewed = mapOf("- [ ] จัดการ Note" to "2026-10-10", "- [ ] Super-app" to "2026-10-12")
        val lines = note.flatMap { line -> reviewed.entries.firstOrNull { line.startsWith(it.key) }?.let { listOf(line, "    - ทบทวน ${it.value}") } ?: listOf(line) }
        val plan = DayFill.plan(input("2026-10-29T16:45", lines))
        val items = plan.placed.map { it.item } + plan.later.map { it.item }
        val net = items.single { it.kind == DayFill.Kind.NET }
        assertTrue(net.title.startsWith("จัดการ Note"), "the quietest first (19 days since its review)")
        assertEquals(15, net.len)
        assertTrue(net.must)
        assertTrue(net.why.contains("ค้างมา 19 วัน"), net.why)
        assertEquals(1, net.more, "Super-app (17 days quiet) is next")
        assertTrue(items.none { it.kind == DayFill.Kind.REVIEW })
        // "ไม่ใช่ เปลี่ยนให้" takes the next one.
        val swapped = DayFill.plan(input("2026-10-29T16:45", lines, memory = DayFill.Memory(day = "2026-10-29", swaps = mapOf("net" to 1))))
        val again = (swapped.placed.map { it.item } + swapped.later.map { it.item }).single { it.kind == DayFill.Kind.NET }
        assertTrue(again.title.startsWith("Super-app"), again.title)
        assertTrue(again.why.contains("ค้างมา 17 วัน"), again.why)
        // Work that moved in the last two weeks (Mock UX UI, a progress note on 26 Oct) never comes from the net.
        assertTrue((items + again).none { it.title.startsWith("Mock UX UI") })
    }

    @Test
    fun atMostTwoReviewCardsADay() {
        val week = DayFill.weekOf(LocalDate.parse("2026-10-29")).toString()
        // The sprint's two projects bring Super-app (omni) and จัดการ Note (Siriraj); the net brings an old laptop job.
        val lines = note + "- [ ] เปลี่ยนแบตโน๊ตบุ๊ค ➕ 2026-10-01"
        val memory = DayFill.Memory(sprint = DayFill.SprintPick(week, listOf("omni", "Siriraj"), taken = true))
        val plan = DayFill.plan(input("2026-10-29T16:45", lines, memory = memory))
        val reviews = (plan.placed.map { it.item } + plan.later.map { it.item }).filter { it.kind == DayFill.Kind.REVIEW }
        assertEquals(3, reviews.size, reviews.joinToString { it.title })
        assertEquals(2, reviews.count { it.prio < 9.0 })
        val waiting = plan.later.single { it.item.kind == DayFill.Kind.REVIEW && it.item.prio >= 9.0 }
        assertEquals("ยังไม่ได้ทบทวน ทบทวนแล้วเลขาลงตารางให้", waiting.why)
    }

    @Test
    fun aTaskMovedThreeTimesBringsOneQuestion() {
        val mock = "Mock UX UI"
        val plan = DayFill.plan(input("2026-11-03T16:45", moves = { if (it.title.startsWith(mock)) 3 else 0 }))
        val ask = (plan.placed.map { it.item } + plan.later.map { it.item }).single { it.kind == DayFill.Kind.ASK }
        assertTrue(ask.title.startsWith(mock))
        assertTrue(ask.why.startsWith("เลื่อนมา 3 ครั้งแล้ว"), ask.why)
        // Two moves in the behaviour log and one "ช่วงนี้ไม่ว่าง" on the phone count the same.
        val ident = id(note, mock)
        val pushed = DayFill.Memory(pushed = mapOf(ident to listOf("2026-11-01")))
        val plan2 = DayFill.plan(input("2026-11-03T16:45", memory = pushed, moves = { if (it.title.startsWith(mock)) 2 else 0 }))
        assertTrue((plan2.placed.map { it.item } + plan2.later.map { it.item }).any { it.kind == DayFill.Kind.ASK })
        // Answered last week: not asked again for 14 days.
        val asked = DayFill.Memory(asked = mapOf(ident to "2026-10-27"))
        val plan3 = DayFill.plan(input("2026-11-03T16:45", memory = asked, moves = { if (it.title.startsWith(mock)) 3 else 0 }))
        assertTrue((plan3.placed.map { it.item } + plan3.later.map { it.item }).none { it.kind == DayFill.Kind.ASK })
    }

    @Test
    fun aPickTakesTheCardOutAndSaysWhy() {
        val omni = id(note, "ปรับ Project Omni")
        val memory = DayFill.Memory(day = "2026-10-29", picks = mapOf(omni to DayFill.Pick.TIRED))
        val plan = DayFill.plan(input("2026-10-29T16:45", memory = memory))
        assertNull(plan.card("ปรับ Project Omni"))
        val later = plan.later.single { it.item.title.startsWith("ปรับ Project Omni") }
        assertEquals(DayFill.Pick.TIRED, later.picked)
        assertTrue(later.why.contains("ไม่นับว่าข้าม"))
    }

    @Test
    fun outOfEnergyYesterdayComesBackFirstForFifteenMinutes() {
        val flow = id(note, "Flow น้ำเกลือ")
        val yesterday = DayFill.Memory(day = "2026-10-28", picks = mapOf(flow to DayFill.Pick.TIRED), tired = mapOf(flow to "2026-10-28"))
        val plan = DayFill.plan(input("2026-10-29T16:45", memory = yesterday))
        val card = assertNotNull(plan.card("Flow น้ำเกลือ"))
        assertEquals(DayFill.Kind.AGAIN, card.item.kind)
        assertEquals(15, card.len)
        assertTrue(card.item.must)
        assertTrue(card.item.why.startsWith("เมื่อวานเลื่อนไว้"), card.item.why)
        assertEquals(0, DayFill.plan(input("2026-10-29T16:45", memory = yesterday)).let { p -> p.later.count { it.picked != null } }, "yesterday's tap is gone")
    }

    // ---- What lasts on the phone ----

    @Test
    fun aNewDayClearsTheTapsAndKeepsWhatLasts() {
        val old = DayFill.Memory(
            day = "2026-10-28",
            picks = mapOf("a" to DayFill.Pick.TIRED),
            swaps = mapOf("net" to 2),
            notBefore = mapOf("a" to 1000),
            errand = DayFill.Errand(1000, 1100),
            accepted = mapOf("a" to DayFill.Accepted("2026-10-28", 1000), "b" to DayFill.Accepted("2026-10-20", 900)),
            pushed = mapOf("a" to listOf("2026-09-01", "2026-10-28")),
            asked = mapOf("c" to "2026-09-01", "d" to "2026-10-20"),
            slots = mapOf("net" to "a"),
            lines = mapOf("a" to DayFill.Line("x", "y"), "b" to DayFill.Line("x", "z")),
        )
        val now = old.on(LocalDate.parse("2026-10-29"))
        assertEquals("2026-10-29", now.day)
        assertTrue(now.picks.isEmpty() && now.swaps.isEmpty() && now.notBefore.isEmpty())
        assertNull(now.errand)
        assertEquals(setOf("a"), now.accepted.keys, "yesterday's slot stays one day, to be taken back")
        assertEquals(mapOf("a" to listOf("2026-10-28")), now.pushed)
        assertEquals(mapOf("d" to "2026-10-20"), now.asked)
        assertTrue(now.slots.isEmpty(), "a slot is taken for one day")
        assertEquals(setOf("a"), now.lines.keys, "the line of a slot still followed stays known")
        assertTrue(now.withPush("a", LocalDate.parse("2026-10-29")).pushedOn("a", LocalDate.parse("2026-10-29")))
    }

    @Test
    fun keptForLaterGoesToTheNextNormalDayWithRoom() {
        // Fri 30: a night shift at 02:30 that night. Sat 31: just off it, and the pharmacy until 20:00. Sun 1 Nov: free.
        assertEquals(LocalDate.parse("2026-11-01"), DayFill.laterDay(input("2026-10-29T16:45"), 60))
        // Never past the 📅: due Saturday 31, so not Sunday 1 Nov.
        assertTrue(DayFill.laterDay(input("2026-10-29T16:45"), 60, due = LocalDate.parse("2026-10-31")) <= LocalDate.parse("2026-10-31"))
    }

    // ---- The weekly sprint ----

    @Test
    fun theSprintOffersTwoProjectsAndSwapsTheSecond() {
        Projects.chosen = setOf("omni", "Siriraj", "ygdrasil")
        val inp = input("2026-11-02T08:00", note + "- [ ] วาดผังต้นไม้งาน #ygdrasil ➕ 2026-10-20")
        val two = Sprint.propose(inp, 0)
        assertEquals(2, two.size, two.joinToString { it.project })
        val swapped = Sprint.propose(inp, 1)
        assertEquals(two[0].project, swapped[0].project)
        assertTrue(two[1].project != swapped[1].project, "the second project changes")
        val line = assertNotNull(Sprint.line(inp, DayFill.frame(inp.now.date, inp, inp.now), DayFill.Memory()))
        assertTrue(line.lines.first().startsWith("เวลาว่างนอกเวรสัปดาห์นี้ราว"))
        val week = DayFill.weekOf(inp.now.date).toString()
        assertNull(Sprint.line(inp, DayFill.frame(inp.now.date, inp, inp.now), DayFill.Memory(sprint = DayFill.SprintPick(week, line.projects, taken = true))))
    }

    @Test
    fun aDeadlineThatWillBeMissedComesFirst() {
        val lines = note + listOf(
            "- [ ] Flow น้ำเกลือ ส่งให้ทีม 6 ชม. #Siriraj ➕ 2026-10-28 📅 2026-11-04",
        )
        val ranked = Sprint.rank(input("2026-11-02T08:00", lines))
        assertEquals("Siriraj", ranked.first().project)
        assertEquals(0, ranked.first().rank, "about 12 hours of work by Wednesday, with less free time than that")
    }
}
