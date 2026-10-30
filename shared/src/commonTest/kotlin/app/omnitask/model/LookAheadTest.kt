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

    /**
     * The two months before, as the look-ahead's own read finds them in the shift calendar: [perWeek] night shifts
     * every week from 7 Sep to 1 Nov (the owner had about twelve in October).
     */
    private fun past(perWeek: Int) = (0L until 8L).flatMap { w ->
        val monday = LocalDate.parse("2026-09-07").plusDays(7 * w)
        listOf(1L, 3L, 5L).take(perWeek).map { night(monday.plusDays(it).toString()) }
    }

    private fun input(
        now: String, lines: List<String> = note, memory: DayFill.Memory = DayFill.Memory(), ahead: Boolean = true,
        far: List<CalendarEvent>? = null, extra: List<CalendarEvent> = emptyList(), own: Map<LocalDate, LookAhead.OwnDay>? = null,
    ) = DayFill.Input(tasks(lines), events + extra, profile, facts, LocalDateTime.parse(now), memory, { 0 }, ahead = ahead, far = far, own = own)

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
    fun aMonthNotPostedYetLosesWhatShiftsTookInTheTwoMonthsBefore() {
        // Shifts are posted up to 28 Nov: December is estimated, never tagged.
        val busy = input("2026-11-02T08:00", far = past(3) + events)
        val ws = LookAhead.weeks(busy)
        val dec = ws.drop(4)
        assertTrue(dec.all { !it.known && !it.heavy }, "$dec")
        assertTrue(dec.indices.none { LookAhead.weekHint(ws, 4 + it) != null })
        // Three nights a week in September and October: the week of 30 Nov loses that share of its time...
        val bare = (0L..6L).sumOf { LookAhead.usable(d("2026-11-30").plusDays(it), busy) }
        // Only December's days: Monday 30 Nov is in a posted month, and its own shifts are counted already.
        val mon30 = LookAhead.usable(d("2026-11-30"), busy)
        assertEquals(mon30 + (1L..6L).sumOf { (LookAhead.usable(d("2026-11-30").plusDays(it), busy) * (1 - ws[4].cut)).toInt() }, ws[4].usable)
        assertEquals(mon30, LookAhead.usableCounted(d("2026-11-30"), busy, ws))
        assertTrue(ws[4].cut > 0.4, "${ws[4].cut}")
        // ...and with no night shift in those two months (31 Oct's taken out too), nothing.
        val quiet = LookAhead.weeks(input("2026-11-02T08:00", far = past(0) + events.filterNot { it.calendarId == shiftCal && it.begin.date < d("2026-11-02") }))
        assertEquals(0.0, quiet[4].cut)
        assertEquals(bare, quiet[4].usable)
        // One night a week then: a smaller share than three.
        val one = LookAhead.weeks(input("2026-11-02T08:00", far = past(1) + events))
        assertTrue(one[4].cut > 0.0 && one[4].cut < ws[4].cut, "${one[4].cut} vs ${ws[4].cut}")
        // The holidays of 7 and 10 Dec stay in their week.
        assertTrue(ws[5].usable > ws[4].usable)
    }

    @Test
    fun withoutItsOwnReadThePostedWeeksStandInForTheMonthsBefore() {
        // Until the look-ahead's calendar read comes in: the weeks posted ahead, their shifts only (not the trip).
        val ws = LookAhead.weeks(input("2026-11-02T08:00"))
        assertTrue(ws[4].cut in 0.3..0.45, "${ws[4].cut}")
    }

    @Test
    fun aWeekWithOnlyItsMondayPostedIsStillAGuess() {
        val ws = LookAhead.weeks(input("2026-11-02T08:00", extra = listOf(night("2026-11-30"))))
        assertFalse(ws[4].known, "only Monday 30 Nov is posted")
        assertTrue(ws[3].known)
    }

    @Test
    fun aMonthPostedToItsEndIsKnownWithoutAShiftInItsLastWeek() {
        // The shifts of 26 and 28 Nov taken out: the last one is Tuesday 24, yet November is posted to its end.
        val all = input("2026-11-02T08:00")
        val few = DayFill.Input(all.tasks, all.events.filterNot { it.calendarId == shiftCal && it.begin.date > d("2026-11-24") }, profile, facts, all.now, ahead = true)
        val ws = LookAhead.weeks(few)
        assertTrue(ws[3].known, "the week of 23 Nov")
        assertEquals(0.0, ws[3].cut)
        assertFalse(ws[4].known, "December is not posted")
    }

    @Test
    fun theCountIsKeptUntilSomethingChanges() {
        val inp = input("2026-11-02T08:00")
        assertTrue(LookAhead.weeks(inp) === LookAhead.weeks(inp), "the same calendar, tasks and half hour")
        // The week card's free days are counted in between: today's count is still kept.
        LookAhead.booked(inp, d("2026-11-08"))
        assertTrue(LookAhead.weeks(inp) === LookAhead.weeks(inp))
        val ticked = DayFill.Input(tasks(note.drop(2)), inp.events, profile, facts, inp.now, ahead = true)
        assertTrue(LookAhead.weeks(ticked) !== LookAhead.weeks(inp), "new tasks: counted again")
        val later = DayFill.Input(inp.tasks, inp.events, profile, facts, LocalDateTime.parse("2026-11-02T08:30"), ahead = true)
        assertTrue(LookAhead.weeks(later) !== LookAhead.weeks(inp), "the next half hour: counted again")
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
        assertEquals("วันว่างทั้งวัน ช่วงเช้าเป็นของ Klui เรื่องนี้เคยค้างเพราะยังติดงานที่มีคนรออยู่ และกลับห้องไปก็เหนื่อยแล้ว", want.why, "as mock v2")
        // Two hours of work before the tight week, after the morning.
        val work = assertNotNull(plan.placed.firstOrNull { it.item.slot == "ahead" })
        assertEquals(DayFill.Kind.PROJECT, work.item.kind)
        assertEquals(120, work.len)
        assertTrue(work.at >= mine.at + mine.len)
        val tight = work.item.hints.single()
        assertEquals("ก่อนสัปดาห์แน่น", tight.label)
        assertEquals("สัปดาห์หน้าว่างแค่ราว 12 ชม.", tight.why, "as mock v2")
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
        // Rule 3: the morning is the owner's before work. The second sprint slot would otherwise start at 07:30.
        val mine = plan.placed.first { it.item.kind == DayFill.Kind.MINE }
        assertEquals(9 * 60, mine.at)
        val work = plan.placed.filter { it.item.kind != DayFill.Kind.MINE && it.item.kind != DayFill.Kind.LIFE }
        assertTrue(work.size >= 2, work.joinToString { it.item.title })
        assertTrue(work.all { it.at >= 11 * 60 }, work.joinToString { "${it.item.title} ${DayFill.hm(it.at)}" })
    }

    @Test
    fun theFreeDaysMorningIsOnlyTheMorning() {
        // Opened at 15:00 on the free Sunday without taking it: own time is the evening's, as on any day.
        val late = DayFill.plan(input("2026-11-01T15:00"))
        val mine = late.placed.first { it.item.kind == DayFill.Kind.MINE }
        assertTrue(mine.len < 120, "${mine.len}")
        assertTrue(mine.item.hints.none { "ช่วงเช้า" in it.why }, mine.item.hints.toString())
        // The stock study done this morning: the next want waits for the evening, and work is not held back two more hours.
        val ticked = note.map { if (it.startsWith("- [ ] How to ดูหุ้น")) it.replace("- [ ]", "- [x]") + " ✅ 2026-11-01" else it }
        val after = DayFill.plan(input("2026-11-01T11:00", ticked))
        val next = after.placed.first { it.item.kind == DayFill.Kind.MINE }
        assertTrue(next.item.title.startsWith("เรียนวาดรูปสีน้ำ"), next.item.title)
        assertTrue(next.len < 120, "${next.len}")
        val work = after.placed.filter { it.item.kind != DayFill.Kind.MINE && it.item.kind != DayFill.Kind.LIFE }
        assertTrue(work.any { it.at < 13 * 60 }, work.joinToString { "${it.item.title} ${DayFill.hm(it.at)}" })
        // A daily habit ticked early is not the morning's want: the two hours stay.
        val habit = note + "- [x] ยืดเส้นตอนเช้า #My-space 🔁 every day ✅ 2026-11-01"
        val mine2 = DayFill.plan(input("2026-11-01T07:30", habit)).placed.first { it.item.kind == DayFill.Kind.MINE }
        assertEquals(120, mine2.len)
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
    fun ownTimeIsGivenBackWholeInTheNextLightWeek() {
        // No myspace task done, stuck or taken in the two weeks before 16 Nov.
        val mon16 = DayFill.plan(input("2026-11-16T17:00"))
        val mine = mon16.placed.first { it.item.kind == DayFill.Kind.MINE }
        val back = mine.item.hints.first { it.label == "คืนเวลา" }
        assertEquals(DayFill.Tone.LIGHT, back.tone)
        assertEquals("2 สัปดาห์ล่าสุดได้ทำเรื่องที่อยากทำแค่ 0 วัน", back.why)
        assertEquals(mine.item.len, mine.item.min, "not cut short")
        // In a tight week it waits; a week the shift calendar has not reached is not known to be light.
        val mon2 = DayFill.plan(input("2026-11-02T17:00"))
        assertTrue(mon2.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.isEmpty())
        val mon30 = DayFill.plan(input("2026-11-30T17:00"))
        assertTrue(mon30.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.isEmpty())
        // Five days of own time in the two weeks: nothing is owed.
        val done = (3..12 step 2).map { "- [x] วิ่งเช้า $it #My-space ✅ 2026-11-${it.toString().padStart(2, '0')}" }
        val enough = DayFill.plan(input("2026-11-16T17:00", note + done))
        assertTrue(enough.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.none { it.label == "คืนเวลา" })
        assertEquals(5, LookAhead.ownDays(input("2026-11-16T17:00", note + done)))
        // To the light week's last evening: Sunday 22 Nov still gives it back.
        val sun22 = DayFill.plan(input("2026-11-22T17:00"))
        assertTrue(sun22.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.any { it.label == "คืนเวลา" })
        // A note that says why it got stuck is not own time; a note of progress is.
        val notes = note + listOf("- [ ] ฝึกกีตาร์ #My-space ➕ 2026-10-01", "    - ค้าง 2026-11-10 ไม่มีแรง", "    - ค้าง 2026-11-11 เล่นได้ครึ่งเพลงแล้ว")
        assertEquals(1, LookAhead.ownDays(input("2026-11-16T17:00", notes)))
    }

    @Test
    fun onceKeptForTwoWeeksOwnTimeIsHalfOfWhatThePlanOffered() {
        // The owner's pick ก (2026-10-30): the phone keeps what the plan offered and what was done, day by day.
        fun kept(offered: Int, done: Int) = (1L..15L).associate { d("2026-11-01").plusDays(it) to LookAhead.OwnDay(offered, done) }
            .filterKeys { it < d("2026-11-16") } + (d("2026-11-01") to LookAhead.OwnDay(60, 0))
        // An hour a day offered, a quarter done: owed, said in hours as mock v2 does.
        val little = DayFill.plan(input("2026-11-16T17:00", own = kept(60, 15)))
        val back = little.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.first { it.label == "คืนเวลา" }
        assertEquals("2 สัปดาห์ล่าสุด ได้ทำเรื่องที่อยากทำแค่ราว 4 ชม.", back.why)
        // Half or more done: nothing owed, though no want was ticked (the count of days would say owed).
        val enough = DayFill.plan(input("2026-11-16T17:00", own = kept(60, 30)))
        assertTrue(enough.placed.first { it.item.kind == DayFill.Kind.MINE }.item.hints.none { it.label == "คืนเวลา" })
        // Kept for less than two weeks: the count of days stands in.
        val short = kept(60, 60).filterKeys { it > d("2026-11-05") }
        val days = DayFill.plan(input("2026-11-16T17:00", own = short)).placed.first { it.item.kind == DayFill.Kind.MINE }
        assertEquals("2 สัปดาห์ล่าสุดได้ทำเรื่องที่อยากทำแค่ 0 วัน", days.item.hints.first { it.label == "คืนเวลา" }.why)
    }

    @Test
    fun todaysOwnTimeIsKeptAsThePlanOffersItAndTheOwnerDoesIt() {
        val inp = input("2026-11-16T17:00")
        val plan = DayFill.plan(inp)
        val slot = plan.placed.first { it.item.kind == DayFill.Kind.MINE }.len
        assertEquals(LookAhead.OwnDay(slot, 0), LookAhead.ownToday(plan, inp, null))
        // What was offered earlier today stays, though the slot is shorter now.
        assertEquals(LookAhead.OwnDay(120, 0), LookAhead.ownToday(plan, inp, LookAhead.OwnDay(120, 0)))
        // A want ticked today, or progress written on one: done.
        val ticked = note.map { if (it.startsWith("- [ ] เรียนวาดรูปสีน้ำ")) it.replace("- [ ]", "- [x]") + " ✅ 2026-11-16" else it }
        val t = input("2026-11-16T17:00", ticked)
        assertEquals(LookAhead.OwnDay(120, 120), LookAhead.ownToday(DayFill.plan(t), t, LookAhead.OwnDay(120, 0)))
        // Notes go under the stock study, a want.
        fun under(line: String) = note.flatMap { if (it.startsWith("    - ค้าง 2026-10-14")) listOf(it, line) else listOf(it) }
        val progress = input("2026-11-16T17:00", under("    - ค้าง 2026-11-16 อ่านงบการเงินไปหนึ่งบริษัท"))
        // (The plan offered the slot earlier today; with progress written it may not offer it again.)
        val earlier = LookAhead.OwnDay(slot, 0)
        assertEquals(slot, LookAhead.ownToday(DayFill.plan(progress), progress, earlier).done)
        // A note of why it got stuck is not own time done.
        val stuck = input("2026-11-16T17:00", under("    - ค้าง 2026-11-16 ไม่มีแรง"))
        assertEquals(0, LookAhead.ownToday(DayFill.plan(stuck), stuck, earlier).done)
        // The slot taken today: done.
        val mine = plan.placed.first { it.item.kind == DayFill.Kind.MINE }.item.task!!
        val taken = input("2026-11-16T17:00", memory = DayFill.Memory(day = "2026-11-16", slots = mapOf("mine" to DayFill.ident(mine))))
        assertEquals(slot, LookAhead.ownToday(DayFill.plan(taken), taken, null).done)
    }

    @Test
    fun ownTimeIsKeptAsShortTextOnThePhone() {
        val days = mapOf(d("2026-10-30") to LookAhead.OwnDay(60, 45), d("2026-09-01") to LookAhead.OwnDay(60, 0), d("2026-10-29") to LookAhead.OwnDay(0, 0))
        val text = LookAhead.ownText(days, d("2026-10-30"))
        assertEquals("2026-10-29=0,0;2026-10-30=60,45", text, "the last 30 days, by date")
        assertEquals(days - d("2026-09-01") to 0, LookAhead.ownParse(text))
        // A part that does not read is left out and counted, so the phone can say so.
        assertEquals(mapOf(d("2026-10-30") to LookAhead.OwnDay(60, 45)) to 2, LookAhead.ownParse("2026-10-30=60,45;2026-13-01=1,1;junk"))
        assertEquals(emptyMap<LocalDate, LookAhead.OwnDay>() to 0, LookAhead.ownParse(null))
    }

    // ---- The sprint line's tag, and rule 5: a far deadline ----

    @Test
    fun theSprintLineTagsATightWeekAndTheLightestWeek() {
        val tight = assertNotNull(DayFill.plan(input("2026-11-02T08:00")).sprint)
        val hint = assertNotNull(tight.hint)
        assertEquals("แน่น 12 ชม.", hint.label)
        assertEquals(DayFill.Tone.HEAVY, hint.tone)
        assertEquals("เวรดึก 3 คืน ปกติว่างราว 28 ชม.", hint.why, "as mock v2")
        // The hours under the tag are the tag's.
        assertTrue(tight.lines.first().startsWith("เวลาว่างนอกเวรสัปดาห์นี้ราว 12 ชม."), tight.lines.first())
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
        assertEquals("ไม่มีเวรดึกทั้งสัปดาห์ สัปดาห์ถัดไปเวรดึก 3 คืน", light.why, "as mock v2")
        // A week that is neither (here one the calendar has not reached) keeps the sprint line as it was: no tag.
        assertNull(DayFill.plan(input("2026-11-30T08:00")).sprint?.hint)
        // A Saturday with two days left is not "tight" for the two days.
        assertFalse(LookAhead.weeks(input("2026-11-14T07:30")).first().heavy)
        // Nor is a Friday with three days left "the lightest week".
        val fri = LookAhead.Week(d("2026-11-20"), 1000, 1000, 0, known = true, days = 3)
        val after = (1L..3L).map { LookAhead.Week(d("2026-11-23").plusDays(7 * (it - 1)), 900, 1700, 0, known = true) }
        assertFalse(LookAhead.lightest(listOf(fri) + after, 0))
        assertTrue(LookAhead.lightest(listOf(fri.copy(days = 4)) + after, 0), "CONTROL: four days left")
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
        assertEquals("ต้องใช้ราว 36 ชม. ว่างก่อนวันส่งเหลือราว 42 ชม.", start.hint?.why, "as mock v2")
        // With the easiest way to make it (rule 5): the freest days first, 36 hours in all.
        val way = start.lines.first()
        assertTrue(way.startsWith("ทางที่ทันสบายสุด: ") && way.contains("อา 8 พ.ย."), way)
        assertFalse(way.contains("พ.ย. 0"), way)
        // As mock round 1 says it: the whole free days by date, the other days as their week's sprint.
        assertTrue(way.contains("อา 22 พ.ย.") && way.contains("สปรินต์ 16 ถึง 20 พ.ย."), way)
        assertTrue(start.note.startsWith("Siriraj ต้องเริ่มสัปดาห์นี้ถึงจะทันวันส่ง"), start.note)
        assertEquals("สไลด์สอนน้องใหม่ 18 ชม. ส่ง 27 พ.ย.", start.lead, "the row names the task, as mock board ง")
        // 25 hours (50 doubled): more than the time left. The line warns as it always did (no tag of its own in the mock).
        val late = assertNotNull(DayFill.plan(input("2026-11-02T08:00", slides(25))).sprint)
        assertNull(late.hint)
        assertTrue(late.late)
        assertTrue(late.note.startsWith("Siriraj อาจไม่ทันวันส่ง ศ 27 พ.ย. ขาดราว"), late.note)
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
