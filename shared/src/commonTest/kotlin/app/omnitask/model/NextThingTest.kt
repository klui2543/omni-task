package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class NextThingTest {
    private val today = LocalDate.parse("2026-11-03")
    private var line = 0
    private fun t(text: String, notes: List<String> = emptyList(), path: String = SecretaryBook.LOG_PATH) =
        TaskLine.parse(text)!!.copy(fileUri = path, filePath = path, lineIndex = line++, notes = notes)

    @Test
    fun restingIsInProgressRecentStuckOrSomeday() {
        assertTrue(Resting.of(t("- [/] ปรับระบบป้าย ⏳ 2026-11-01"), today))
        assertTrue(Resting.of(t("- [ ] ดูหุ้น ⏳ 2026-10-11", listOf("ค้าง 2026-10-25 รอคนอื่น: รอพี่เอ")), today))
        assertTrue(Resting.of(t("- [ ] เรียนภาษา #สักวัน ⏳ 2026-10-01"), today))
        // A ค้าง line older than 14 days no longer rests the task.
        assertFalse(Resting.of(t("- [ ] ดูหุ้น ⏳ 2026-10-11", listOf("ค้าง 2026-10-14 รอคนอื่น: รอพี่เอ")), today))
        assertFalse(Resting.of(t("- [ ] ยานัดควบคุม I8 ⏳ 2026-10-27 📅 2026-11-07"), today))
        assertFalse(Resting.of(t("- [x] เสร็จแล้ว ✅ 2026-11-01"), today))
    }

    @Test
    fun restingLineAndLateLine() {
        assertEquals("เดินอยู่: รอคนอื่น: รอพี่เอ (29 ต.ค.)", Resting.line(t("- [/] ป้าย", listOf("ค้าง 2026-10-29 รอคนอื่น: รอพี่เอ"))))
        assertEquals("พัก: สักวัน", Resting.line(t("- [ ] ภาษา #สักวัน")))
        assertEquals("เดินอยู่", Resting.line(t("- [/] ป้าย")))
        assertEquals("ตั้งไว้ 27 ต.ค. ส่งภายใน 7 พ.ย.", Resting.lateLine(t("- [ ] I8 ⏳ 2026-10-27 📅 2026-11-07"), today))
        assertEquals("ตั้งไว้ 27 ต.ค.", Resting.lateLine(t("- [ ] I8 ⏳ 2026-10-27"), today))
        assertEquals("เลยมา 2 วัน", Resting.lateLine(t("- [ ] ส่ง 📅 2026-11-01"), today))
    }

    @Test
    fun lateCountLeavesOutRestingWork() {
        val tasks = listOf(
            t("- [/] ปรับระบบป้าย ⏳ 2026-11-01", path = "a.md"),
            t("- [ ] ดูหุ้น ⏳ 2026-10-11", listOf("ค้าง 2026-10-25 รอคนอื่น: รอพี่เอ"), path = "a.md"),
            t("- [ ] ยานัดควบคุม I8 ⏳ 2026-10-27 📅 2026-11-07", path = "a.md"),
        )
        val plan = DayPlan.build(tasks, emptyList(), today)
        assertEquals(3, plan.first { it.part == DayPlan.Part.LATE }.items.size)
        assertEquals(1, DayPlan.lateCount(plan, today))
    }

    @Test
    fun readsDaysAsWritten() {
        assertEquals(LocalDate.parse("2026-11-05"), NextThing.day("5 พ.ย. (เดา)", today))
        assertEquals(LocalDate.parse("2026-11-19"), NextThing.day("ราว 14 ถึง 19 พ.ย. (เดา)", today))
        assertEquals(LocalDate.parse("2027-01-10"), NextThing.day("10 ม.ค.", today))
        assertEquals(LocalDate.parse("2026-12-01"), NextThing.day("2026-12-01", today))
        assertNull(NextThing.day("ก่อนประกาศตาราง", today))
    }

    @Test
    fun picksSoonestOwnerStepFromTheBook() {
        val later = t("- [ ] #rxshift #kluido ลองไฟล์ตารางเวรรอบ ธ.ค.", listOf("ต้องเริ่ม: ราว 14 ถึง 19 พ.ย. (เดา)"))
        val first = t("- [ ] #rxshift #kluido กดรันคำสั่งเปิดโหมดเงาปฏิทิน", listOf("ที่: GAS", "ต้องเริ่ม: 5 พ.ย. (เดา)", "ใช้เวลา: 1 นาที"))
        val undated = t("- [ ] #omni #kluido ลองแอปรุ่นใหม่")
        val notBook = t("- [ ] #kluido งานในโน้ตอื่น ⏳ 2026-11-01", path = "other.md")
        val claude = t("- [ ] #omni งานของ Claude ⏳ 2026-11-01")
        val paused = t("- [ ] #omni #kluido พักไว้ ⏳ 2026-11-01", listOf("ค้าง 2026-11-02 รอคนอื่น: รอผล"))
        val done = t("- [x] #omni #kluido เสร็จแล้ว ✅ 2026-11-02")
        val picks = NextThing.ranked(listOf(undated, later, notBook, claude, paused, done, first), today)
        assertEquals(listOf(first, later, undated), picks.map { it.task })
        assertEquals("ต้องเริ่มภายใน 5 พ.ย. (เดา) ใช้เวลา 1 นาที", picks[0].meta)
        assertNull(picks[2].meta)
        // "ไม่ใช่ตอนนี้" shows the one after it.
        assertEquals(later, NextThing.ranked(listOf(undated, later, first), today, skipped = setOf(first.key)).first().task)
    }

    @Test
    fun mondayLooksFourWeeksAhead() {
        val text = """
            # ไมล์สโตน
            ## Rx Shift
            - เป้า: เปิดระบบใหม่ก่อนประกาศ ม.ค.
            - ต้องเริ่ม: ปุ่มเปิดเดือน ม.ค. ภายใน 20 พ.ย. (เดา) [ที่มา: แผน-RxShift.md]
            - ต้องเริ่ม: ย้ายระบบทั้งหมด ภายใน 25 ธ.ค. [ที่มา: แผน-RxShift.md]
            ## Omni
            - ต้องเริ่ม: งานพักไม่แดง ภายใน 5 พ.ย. [ที่มา: แผน-Omni.md]
            - ต้องเริ่ม: ทบทวนรายสัปดาห์ ภายใน หลังงานพักไม่แดงเสร็จ [ที่มา: แผน-Omni.md]
        """.trimIndent()
        val ahead = NextThing.ahead(text, today)
        assertEquals(
            listOf(
                NextThing.Ahead("งานพักไม่แดง (Omni)", "5 พ.ย."),
                NextThing.Ahead("ปุ่มเปิดเดือน ม.ค. (Rx Shift)", "20 พ.ย. (เดา)"),
                NextThing.Ahead("ทบทวนรายสัปดาห์ (Omni)", "หลังงานพักไม่แดงเสร็จ"),
            ),
            ahead,
        )
        assertTrue(NextThing.monday(LocalDate.parse("2026-11-09")))
        assertFalse(NextThing.monday(today))
    }
}
