package app.omnitask.model

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
 * The secretary looking eight weeks ahead (mock look-ahead v2, owner's OK of 2026-10-30), on a November shaped like
 * the owner's real one: night shifts "O11 รอบ 3" at 02:30 three times in the weeks of 2, 9 and 23 Nov, a trip to
 * Nakhon Pathom on 10 and 11 Nov, the pharmacy at Lotus on Saturdays, no night shift in the week of 16 Nov, the shift
 * calendar posted up to 28 Nov only, and the December holidays. The task lines are shaped like the owner's task note.
 */
class LookAheadTest {

    private val shiftCal = 7L
    private val ownCal = 3L
    private val profile = Profile(exists = true, wake = LocalTime.of(7, 0), sleep = LocalTime.of(22, 30))
    private val facts = RealDay.Facts(
        job = RealDay.Job.WEEKDAYS,
        shiftCalendars = setOf(shiftCal),
        daysOff = mapOf(
            LocalDate.parse("2026-12-05") to "วันพ่อแห่งชาติ",
            LocalDate.parse("2026-12-07") to "วันหยุดชดเชยวันพ่อแห่งชาติ",
            LocalDate.parse("2026-12-10") to "วันรัฐธรรมนูญ",
        ),
    )

    private var id = 100L
    private fun timed(cal: Long, title: String, from: String, to: String) =
        CalendarEvent(id++, title, LocalDateTime.parse(from), LocalDateTime.parse(to), calendarId = cal)

    private fun night(day: String, from: String = "อิง") = timed(shiftCal, "O11 รอบ 3 จาก $from", "${day}T02:30", "${day}T08:30")

    private val events = listOf(
        night("2026-10-31"),
        // The week of 2 Nov: three nights.
        night("2026-11-03"), night("2026-11-05"), night("2026-11-07"),
        // The week of 9 Nov: a trip and two nights.
        CalendarEvent(id++, "ไปนครปฐม", LocalDateTime.parse("2026-11-10T00:00"), LocalDateTime.parse("2026-11-12T00:00"), allDay = true, calendarId = ownCal),
        night("2026-11-13"), night("2026-11-15"),
        // The week of 23 Nov: three nights, the last shift posted.
        night("2026-11-24"), night("2026-11-26"), night("2026-11-28"),
    ) + listOf("2026-10-31", "2026-11-07", "2026-11-14", "2026-11-21", "2026-11-28").map { timed(ownCal, "ร้านยาโลตัส", "${it}T11:00", "${it}T20:00") } +
        timed(ownCal, "ร้านยาพิลลาร์", "2026-11-15T17:00", "2026-11-15T22:00")

    /** The task note as its lines read: work under way in two projects, two myspace tasks (one stuck), quiet work. */
    private val note = listOf(
        "- [ ] จัดการ Note #Siriraj #remind-at-scheduled 🎯 08:00 🔼 ➕ 2026-09-29",
        "    - ทบทวน 2026-10-29",
        "- [ ] ปรับระบบใหม่เป็นระบบ ป้าย #omni ➕ 2026-10-12",
        "    - ทบทวน 2026-10-29",
        "- [ ] Super-app-รวม หลายๆ โปรแกรมให้เปิดง่ายไม่ต้อง รีเฟรชข้ามแอป #omni ➕ 2026-10-05",
        "    - ทบทวน 2026-10-29",
        "- [ ] เรียนวาดรูปสีน้ำ #My-space ➕ 2026-09-01",
        "- [ ] How to ดูหุ้นแบบรอบด้านและลึก #My-space ➕ 2026-09-29",
        "    - ค้าง 2026-10-14 ยังติดงานที่มีคนรออยู่ และกลับห้องไปก็เหนื่อยแล้ว",
        "- [ ] Mock UX UI และ จัดการโครงสร้างหลังบ้าน #Siriraj/Peddose ⏫ ➕ 2026-10-02",
        "    - ทบทวน 2026-10-29",
    )

    private fun tasks(lines: List<String>) = VaultText.parseFile("f", "📁 Folder/หลังบ้าน/Omni/Omni note.md", lines.joinToString("\n"))

    private fun input(now: String, lines: List<String> = note, memory: DayFill.Memory = DayFill.Memory(), ahead: Boolean = true) =
        DayFill.Input(tasks(lines), events, profile, facts, LocalDateTime.parse(now), memory, { 0 }, ahead = ahead)

    private fun DayFill.Plan.card(title: String) = placed.firstOrNull { it.item.title.startsWith(title) }

    private fun d(s: String) = LocalDate.parse(s)

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

    private fun labels(p: DayFill.Placed?) = p?.item?.hints?.map { it.label } ?: emptyList()

    // ---- Rule 1: the real free time of eight weeks ----

    @Test
    fun theWeeksCountTheRealFreeTimeFromShiftsTheTripAndTheJob() {
        val inp = input("2026-11-02T08:00")
        val ws = LookAhead.weeks(inp)
        assertEquals(8, ws.size)
        assertEquals(d("2026-11-02"), ws[0].start)
        // A usual week (the day job only) has about 28 hours.
        assertEquals(28, ws[2].plainHours)
        // Three nights: tight. The trip week is tighter still: the two days away count no time.
        assertEquals(3, ws[0].nights)
        assertTrue(ws[0].heavy, "${ws[0]}")
        assertTrue(ws[1].heavy, "${ws[1]}")
        assertTrue(LookAhead.away(d("2026-11-10"), inp) && LookAhead.away(d("2026-11-11"), inp))
        assertFalse(LookAhead.away(d("2026-11-12"), inp), "the trip is 10 and 11 Nov")
        assertEquals(0, LookAhead.usable(d("2026-11-10"), inp), "no job that day, but away")
        // No night shift in the week of 16 Nov: the lightest of the four, with three quarters of its usual time.
        assertFalse(ws[2].heavy)
        assertTrue(LookAhead.lightest(ws, 2))
        assertTrue(ws[3].heavy)
        assertTrue(ws.take(4).all { it.known })
    }

    @Test
    fun aWeekTheShiftCalendarHasNotReachedLosesWhatShiftsTookFromTheKnownWeeks() {
        val inp = input("2026-11-02T08:00")
        val ws = LookAhead.weeks(inp)
        // Shifts are posted up to 28 Nov: December is estimated, never tagged.
        val dec = ws.drop(4)
        assertTrue(dec.all { !it.known && !it.heavy }, "$dec")
        assertTrue(dec.indices.none { LookAhead.weekHint(ws, 4 + it) != null })
        // The week of 30 Nov has only the job: it loses the known weeks' average share to shifts...
        val bare = (0L..6L).sumOf { LookAhead.usable(d("2026-11-30").plusDays(it), inp) }
        assertEquals((bare * (1 - ws[4].cut)).toInt(), ws[4].usable)
        // ...about 38 percent here; the trip is the trip week's own and is not counted in it (with it, 55 percent).
        assertTrue(ws[4].cut in 0.3..0.45, "${ws[4].cut}")
        // The holidays of 7 and 10 Dec stay in their week.
        assertTrue(ws[5].usable > ws[4].usable)
    }

    @Test
    fun aTripDayOrADayWithThePharmacyIsNotAWholeFreeDay() {
        val inp = input("2026-11-02T08:00")
        assertTrue(LookAhead.freeDay(d("2026-11-22"), inp), "a Sunday at home")
        assertFalse(LookAhead.freeDay(d("2026-11-10"), inp), "away in Nakhon Pathom")
        assertFalse(LookAhead.freeDay(d("2026-11-21"), inp), "the Lotus pharmacy 11:00 to 20:00")
        assertFalse(LookAhead.freeDay(d("2026-11-15"), inp), "after a night shift")
        assertFalse(LookAhead.freeDay(d("2026-11-17"), inp), "the day job")
        assertTrue(LookAhead.freeDay(d("2026-12-10"), inp), "Constitution Day")
    }

    // ---- Rules 2 and 3: a whole free day before a tight week (board ข of the mock) ----

    @Test
    fun aFreeSundayBeforeATightWeekGivesTheMorningToAWantAndTwoHoursToWork() {
        val plan = DayFill.plan(input("2026-11-01T07:30"))
        // The stock study got stuck before ("กลับห้องไปก็เหนื่อยแล้ว"): on a whole free day it comes first, at 09:00 for 2 hours.
        val mine = assertNotNull(plan.card("How to ดูหุ้น"))
        assertEquals(DayFill.Kind.MINE, mine.item.kind)
        assertEquals(9 * 60, mine.at)
        assertEquals(120, mine.len)
        val want = mine.item.hints.first()
        assertEquals("อยากทำ", want.label)
        assertEquals(DayFill.Tone.WANT, want.tone)
        assertTrue(want.why.contains("เคยค้างเพราะ ยังติดงานที่มีคนรออยู่"), want.why)
        // Two hours of work before the tight week, after the morning.
        val work = assertNotNull(plan.placed.firstOrNull { it.item.slot == "ahead" })
        assertEquals(DayFill.Kind.PROJECT, work.item.kind)
        assertEquals(120, work.len)
        assertTrue(work.at >= mine.at + mine.len)
        val tight = work.item.hints.single()
        assertEquals("ก่อนสัปดาห์แน่น", tight.label)
        assertEquals("สัปดาห์หน้า เวรดึก 3 คืน ว่างราว 12 ชม. ปกติว่างราว 28 ชม. ทำวันนี้ตอนยังมีแรง", tight.why)
        assertFalse(work.item.must, "tiredness can still take it out")
    }

    @Test
    fun withTheSwitchOffTheSundayIsPlannedAsBefore() {
        // CONTROL: the look-ahead off is the plan the owner has today.
        val plan = DayFill.plan(input("2026-11-01T07:30", ahead = false))
        assertTrue(plan.placed.all { it.item.hints.isEmpty() })
        assertNull(plan.placed.firstOrNull { it.item.slot == "ahead" })
        val mine = assertNotNull(plan.placed.firstOrNull { it.item.kind == DayFill.Kind.MINE })
        assertTrue(mine.item.title.startsWith("เรียนวาดรูปสีน้ำ"), "the oldest myspace task, as before")
        assertTrue(mine.len < 120)
        assertNull(plan.sprint?.hint)
    }

    @Test
    fun aTakenSprintSlotGrowsInsteadOfASecondCard() {
        val memory = DayFill.Memory(sprint = DayFill.SprintPick("2026-10-26", listOf("Siriraj", "omni"), taken = true))
        val plan = DayFill.plan(input("2026-11-01T07:30", memory = memory))
        val sprint = plan.placed.filter { it.item.slot?.startsWith("sprint:") == true }
        assertEquals(1, sprint.count { "ก่อนสัปดาห์แน่น" in labels(it) }, sprint.joinToString { it.item.title })
        assertEquals(120, sprint.first { "ก่อนสัปดาห์แน่น" in labels(it) }.len)
        assertNull(plan.placed.firstOrNull { it.item.slot == "ahead" })
    }

    @Test
    fun aFreeDayBeforeAUsualWeekHasNoWorkSlot() {
        // Sunday 6 Dec is a whole free day before a week the shift calendar has not reached: no work slot on a guess.
        val plan = DayFill.plan(input("2026-12-06T07:30"))
        assertNull(plan.placed.firstOrNull { it.item.slot == "ahead" })
        assertEquals("อยากทำ", plan.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.first().label, "still a free day's morning")
    }

    // ---- Rule 4: own time given back ----

    @Test
    fun ownTimeIsGivenBackWholeInAWeekThatIsNotTight() {
        // No myspace task done, stuck or taken in the two weeks before 16 Nov.
        val mon16 = DayFill.plan(input("2026-11-16T17:00"))
        val mine = mon16.placed.first { it.item.kind == DayFill.Kind.MINE }
        val back = mine.item.hints.first { it.label == "คืนเวลา" }
        assertEquals(DayFill.Tone.LIGHT, back.tone)
        assertTrue(back.why.startsWith("2 สัปดาห์ล่าสุดได้ทำเรื่องที่อยากทำ 0 วัน"), back.why)
        assertEquals(mine.item.len, mine.item.min, "not cut short")
        // In a tight week it waits.
        val mon2 = DayFill.plan(input("2026-11-02T17:00"))
        assertTrue(mon2.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.isEmpty())
        // Five days of own time in the two weeks: nothing is owed.
        val done = (3..12 step 2).map { "- [x] วิ่งเช้า $it #My-space ✅ 2026-11-${it.toString().padStart(2, '0')}" }
        val enough = DayFill.plan(input("2026-11-16T17:00", note + done))
        assertTrue(enough.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.none { it.label == "คืนเวลา" })
        assertEquals(5, LookAhead.ownDays(input("2026-11-16T17:00", note + done)))
    }

    // ---- The sprint line's tag, and rule 5: a far deadline ----

    @Test
    fun theSprintLineTagsATightWeekAndTheLightestWeek() {
        val tight = assertNotNull(DayFill.plan(input("2026-11-02T08:00")).sprint)
        val hint = assertNotNull(tight.hint)
        assertEquals("แน่น 12 ชม.", hint.label)
        assertEquals(DayFill.Tone.HEAVY, hint.tone)
        assertTrue(hint.why.startsWith("เวรดึก 3 คืน ว่างราว 12 ชม."), hint.why)
        assertEquals(1, tight.projects.size, "a tight week has one project (rule 2)")
        // CONTROL: the switch off keeps two projects and no tag.
        val before = assertNotNull(DayFill.plan(input("2026-11-02T08:00", ahead = false)).sprint)
        assertEquals(2, before.projects.size)
        assertNull(before.hint)
        assertNull(before.lead)
        // The week of 16 Nov: the lightest, and the next one is tight.
        val light = assertNotNull(DayFill.plan(input("2026-11-16T08:00")).sprint?.hint)
        assertEquals(DayFill.Tone.LIGHT, light.tone)
        assertTrue(light.label.startsWith("เบาสุด "), light.label)
        assertTrue(light.why.startsWith("เบาสุดใน 4 สัปดาห์ข้างหน้า สัปดาห์ จ 23 พ.ย. แน่น เวรดึก 3 คืน"), light.why)
    }

    private fun slides(hours: Int) = note + "- [ ] สไลด์สอนน้องใหม่ $hours ชม. #Siriraj ➕ 2026-10-20 📅 2026-11-27"

    @Test
    fun aFarDeadlineWarnsOnlyWhenTheTimeLeftIsNearlyNotEnough() {
        // About 42 hours free from 2 Nov to 27 Nov, counted as rule 1 counts them; the need is doubled as always.
        val inp = input("2026-11-02T08:00", slides(18))
        val ws = LookAhead.weeks(inp)
        assertEquals(2540, (0L..25L).sumOf { LookAhead.usableCounted(d("2026-11-02").plusDays(it), inp, ws) })
        // 8 hours (16 doubled): plenty of time, no warning.
        val easy = Sprint.rank(input("2026-11-02T08:00", slides(8))).first { it.project == "Siriraj" }
        assertNull(easy.due)
        assertTrue(easy.rank > 0)
        // 18 hours (36 doubled, at 85 percent of the time): start this week.
        val start = assertNotNull(DayFill.plan(inp).sprint)
        assertEquals("Siriraj", start.projects.first())
        assertEquals("เริ่มสัปดาห์นี้", start.hint?.label)
        assertEquals(DayFill.Tone.HEAVY, start.hint?.tone)
        assertTrue(start.note.startsWith("Siriraj ต้องเริ่มสัปดาห์นี้ถึงจะทันวันส่ง"), start.note)
        assertEquals("สไลด์สอนน้องใหม่ 18 ชม. ส่ง 27 พ.ย.", start.lead, "the row names the task, as mock board ง")
        // 25 hours (50 doubled): more than the time left.
        val late = assertNotNull(DayFill.plan(input("2026-11-02T08:00", slides(25))).sprint)
        assertEquals("อาจไม่ทัน", late.hint?.label)
        assertTrue(late.note.contains("อาจไม่ทันวันส่ง"), late.note)
        // CONTROL: the switch off does not see past two weeks.
        val off = Sprint.rank(input("2026-11-02T08:00", slides(25), ahead = false)).first { it.project == "Siriraj" }
        assertNull(off.due)
    }

    // ---- Rule 6: what a free day ahead holds, for the week card (board ก of the mock) ----

    @Test
    fun theWeekCardShowsWhatTheFreeSundayWillHold() {
        val inp = input("2026-10-30T17:00")
        val sunday = LookAhead.booked(inp, d("2026-11-01"))
        assertEquals(listOf("How to ดูหุ้นแบบรอบด้านและลึก" to 120, "จัดการ Note" to 120), sunday.map { it.item.title to it.len })
        assertTrue(LookAhead.booked(inp, d("2026-10-31")).isEmpty(), "after a night shift, then the pharmacy")
        assertTrue(LookAhead.booked(inp, d("2026-10-30")).isEmpty(), "today is today's plan")
        assertTrue(LookAhead.booked(inp, d("2026-11-02")).isEmpty(), "the day job")
        // The week card's tag for the week of 2 Nov.
        val next = assertNotNull(LookAhead.weekHint(LookAhead.weeks(inp), 1))
        assertEquals("แน่น 12 ชม.", next.label)
    }
}
