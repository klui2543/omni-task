package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class NextUpTest {
    private val today = LocalDate.parse("2026-10-23")
    private fun t(line: String) = TaskLine.parse(line)!!

    private val dueLate = t("- [ ] จ่ายบัตร 📅 2026-10-20")
    private val dueToday = t("- [ ] ส่งรายงาน 📅 2026-10-23")
    private val doing = t("- [/] เขียนป้าย")
    private val plannedHigh = t("- [ ] ตอบ merge ⏫ ⏳ 2026-10-23")
    private val planned = t("- [ ] อ่านเปเปอร์ ⏳ 2026-10-23")
    private val targetLate = t("- [ ] ระบบป้าย ⏳ 2026-10-14")
    private val someday = t("- [ ] เรียนภาษา")
    private val future = t("- [ ] ซื้อของขวัญ 📅 2026-10-30")

    @Test
    fun ordersByReasonThenPriority() {
        val picks = NextUp.ranked(listOf(someday, targetLate, planned, plannedHigh, doing, dueToday, dueLate, future), today)
        assertEquals(listOf(dueLate, dueToday, doing, plannedHigh, planned, targetLate), picks.map { it.task })
        assertEquals(3, picks[0].days)
        assertEquals(9, picks.last().days)
    }

    @Test
    fun skippedAndDoneAreLeftOut() {
        val done = t("- [x] จ่ายบัตร 📅 2026-10-20 ✅ 2026-10-21")
        val picks = NextUp.ranked(listOf(done, dueToday, planned), today, skipped = setOf(dueToday.key))
        assertEquals(listOf(planned), picks.map { it.task })
    }

    @Test
    fun onlyTargetLateIsQuiet() {
        assertTrue(NextUp.onlyTargetLate(targetLate, today))
        assertFalse(NextUp.onlyTargetLate(dueLate, today))
        assertFalse(NextUp.onlyTargetLate(planned, today))
        assertFalse(NextUp.onlyTargetLate(t("- [ ] ทั้งสอง ⏳ 2026-10-10 📅 2026-10-20"), today))
    }
}
