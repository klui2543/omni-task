package app.omnitask.quest

import app.omnitask.data.TaskLine
import app.omnitask.model.Behavior
import app.omnitask.model.BehaviorEvent
import app.omnitask.model.Task
import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestTest {

    private val today = LocalDate.parse("2026-10-13")
    private fun task(line: String): Task = TaskLine.parse(line)!!

    @Test
    fun levelCurve() {
        assertEquals(0, QuestXp.totalFor(1))
        assertEquals(842, QuestXp.totalFor(10))
        assertEquals(1, QuestXp.levelOf(0))
        assertEquals(9, QuestXp.levelOf(841))
        assertEquals(10, QuestXp.levelOf(842))
        assertEquals(99, QuestXp.levelOf(10_000_000))
    }

    @Test
    fun realmsMatchTheOwnersRealWork() {
        // Titles from the owner's own task note and archive.
        assertEquals(Tribe.SYLVA, QuestXp.realmOf(task("- [x] Routine 🔺 🔁 every day ⏳ 2026-09-29 ✅ 2026-10-08")))
        assertEquals(Tribe.AQUA, QuestXp.realmOf(task("- [ ] How to ดูหุ้นแบบรอบด้านและลึก #อนาคต 🔼")))
        assertEquals(Tribe.AQUA, QuestXp.realmOf(task("- [ ] หนังสือที่ควรอ่าน ถ้าชอบ How to decide + ลงทุน #readinglist #อนาคต")))
        assertEquals(Tribe.IGNIS, QuestXp.realmOf(task("- [x] หาวันตรวจสุขภาพเดือนหน้า ✅ 2026-09-29")))
        assertEquals(Tribe.ZEPHYR, QuestXp.realmOf(task("- [x] ส่งใบลา 9190 + ใบเจตจำนง 🔺 ✅ 2026-09-30")))
        assertEquals(Tribe.ZEPHYR, QuestXp.realmOf(task("- [x] ยกเวร 103 วันที่ 10 และNM5 วันที่ 31 ⏫ ✅ 2026-10-06")))
        assertEquals(Tribe.LUNA, QuestXp.realmOf(task("- [x] นับยาประจำเดือนแบบ automation #Siriraj 🔼 ✅ 2026-09-30")))
        assertEquals(Tribe.LUNA, QuestXp.realmOf(task("- [ ] Mock UX UI และ จัดการโครงสร้างหลังบ้าน #รอ #Siriraj/Peddose ⏫")))
        assertEquals(Tribe.LUNA, QuestXp.realmOf(task("- [x] VBScript creation from guidelines ✅ 2026-09-30")))
        assertEquals(Tribe.IGNIS, QuestXp.realmOf(task("- [ ] วิ่ง 5 กม.")))
        assertNull(QuestXp.realmOf(task("- [ ] คิดเรื่องของขวัญ")))
    }

    @Test
    fun xpFollowsPriorityOnTimeAndSubtasks() {
        val r = QuestXp.compute(
            listOf(
                task("- [x] ส่งใบลา 🔺 📅 2026-10-13 ✅ 2026-10-13"), // 30 x 1.2 = 36
                task("- [x] งานธรรมดา ✅ 2026-10-13"), // 10
                task("- [x] งานย่อย ✅ 2026-10-13").copy(parent = "x#1"), // 5
                task("- [ ] ยังไม่เสร็จ ⏫"),
            ),
            emptyMap(), start = today, tribe = null, today = today,
        )
        assertEquals(51, r.xp)
        assertEquals(51.0, r.today)
        assertEquals(51, r.gold)
    }

    @Test
    fun dailyBandsAndRepeatsCutFarming() {
        val many = (1..30).map { task("- [x] งาน $it 🔺 ✅ 2026-10-13") } // 900 raw
        val r = QuestXp.compute(many, emptyMap(), today, null, today)
        // 200 full + 100 (half of 200) + 20% of 500.
        assertEquals(400, r.xp)
        val same = (1..3).map { task("- [x] ล้างจาน ✅ 2026-10-13") }
        assertEquals(10, QuestXp.compute(same, emptyMap(), today, null, today).xp)
    }

    @Test
    fun legendCountsHalfUpToLevelTwenty() {
        val old = task("- [x] งานเก่า 🔺 ✅ 2026-09-01")
        assertEquals(15, QuestXp.compute(listOf(old), emptyMap(), today, null, today).xp)
        val lots = (0 until 400).map { d -> task("- [x] งานเก่า $d 🔺 ✅ ${LocalDate.parse("2025-01-01").let { LocalDate.fromEpochDays(it.toEpochDays() + d) }}") }
        val r = QuestXp.compute(lots, emptyMap(), today, null, today)
        assertEquals(QuestXp.totalFor(QuestXp.LEGEND_CAP_LEVEL), r.xp)
        assertEquals(20, r.level)
    }

    @Test
    fun bountyPaysMoreAndTribeGrowsItsStat() {
        val moves = (1..3).map {
            BehaviorEvent("m$it", "2026-10-0${it}T09:00:00", Behavior.MOVE, title = "ยกเวร", field = "DUE", from = "2026-10-0$it", to = "2026-10-1$it")
        }
        val r = QuestXp.compute(listOf(task("- [x] ยกเวร ✅ 2026-10-13")), QuestXp.moves(moves), today, Tribe.ZEPHYR, today)
        assertEquals(15, r.xp)
        assertTrue(r.earned.single().bounty)
        assertTrue(r.stats.getValue(Stat.AGI) > 1)
        assertEquals(1, r.stats.getValue(Stat.STR))
    }

    @Test
    fun logReplaysPurchasesEquipmentAndSkills() {
        val at = LocalDateTime.parse("2026-10-13T08:00")
        val acts = listOf(
            QuestLog.action(QuestLog.START, at, "t"),
            QuestLog.action(QuestLog.TRIBE, at, "t", a = "LUNA"),
            QuestLog.action(QuestLog.BUY, at, "t", a = "w-rod", gold = 380),
            QuestLog.action(QuestLog.EQUIP, at, "t", a = "w-rod"),
            QuestLog.action(QuestLog.EQUIP, at, "t", a = "w-bow"), // not owned: ignored
            QuestLog.action(QuestLog.LEARN, at, "t", a = "star-fall"),
            QuestLog.action(QuestLog.CLAIM, at, "t", a = "d:x", gold = 40),
            QuestLog.action(QuestLog.CLAIM, at, "t", a = "d:x", gold = 40), // twice: once
            QuestLog.action(QuestLog.IDLE, at, "t", a = "เขียนรายงาน", n = 1800, gold = 90, kills = 60, b = "c-leaf"),
        )
        val text = QuestLog.merge(null, acts)
        val s = QuestState.replay(QuestLog.parse(text))
        assertTrue(s.begun)
        assertEquals(Tribe.LUNA, s.tribe)
        assertEquals(Tribe.LUNA, s.mascot)
        assertEquals("w-rod", s.equipped[Slot.WEAPON])
        assertEquals(380, s.goldOut)
        assertEquals(130, s.goldIn)
        assertTrue("c-leaf" in s.owned)
        assertEquals(1800, s.secondsOn(today))
        assertEquals(setOf("star-fall"), s.learned)
        // Merging the same actions again changes nothing.
        assertEquals(text, QuestLog.merge(text, acts))
    }

    @Test
    fun idleIsTheSameForTheSameSeedAndGrowsWithTime() {
        val xp = QuestXp.compute(emptyList(), emptyMap(), today, null, today)
        val f = Fighter.of(xp, QuestState())
        val a = Idle.frame(42, f, 600)
        assertEquals(a, Idle.frame(42, f, 600))
        val b = Idle.frame(42, f, 1800)
        assertTrue(b.kills > a.kills)
        assertTrue(b.gold >= a.gold)
        assertEquals(Idle.frame(42, f, Idle.MAX_SECONDS).kills, Idle.frame(42, f, Idle.MAX_SECONDS * 2).kills)
        assertTrue(a.hpLeft in 0.0..1.0)
    }

    @Test
    fun boardComesFromTasksCalendarAndTimer() {
        val now = LocalDateTime.parse("2026-10-13T15:00")
        val tasks = listOf(
            task("- [x] ส่งใบลา 📅 2026-10-13 ✅ 2026-10-13"),
            task("- [ ] เตรียมสไลด์ 📅 2026-10-13 #Siriraj"),
            task("- [ ] ปรับระบบป้าย #Siriraj/ตารางเวรเภสัช 📅 2026-10-10"),
        )
        val events = listOf(
            CalendarEvent(1, "ประชุมทีม", LocalDateTime.parse("2026-10-13T09:00"), LocalDateTime.parse("2026-10-13T10:00")),
            CalendarEvent(2, "เวรดึก", LocalDateTime.parse("2026-10-13T00:00"), LocalDateTime.parse("2026-10-14T00:00"), allDay = true),
            CalendarEvent(3, "วันหยุด", LocalDateTime.parse("2026-10-13T00:00"), LocalDateTime.parse("2026-10-14T00:00"), allDay = true),
        )
        val board = Quests.board(tasks, events, QuestState(), emptyMap(), listOf("เวร"), now)
        val main = board.first { it.id == "d:2026-10-13:due" }
        assertEquals(1, main.progress)
        assertEquals(2, main.goal)
        assertTrue(board.first { it.kind == Quests.Kind.BOSS }.title.contains("ปรับระบบป้าย"))
        assertTrue(board.first { it.id == "c:2026-10-13:1" }.done)
        assertFalse(board.first { it.id == "c:2026-10-13:2" }.done)
        assertTrue(board.none { it.id == "c:2026-10-13:3" })
        assertEquals("Siriraj", Quests.mainBranch(tasks, now.date))
        assertFalse(board.first { it.id == "d:2026-10-13:focus" }.done)
    }
}
