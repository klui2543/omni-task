package app.omnitask.model

import app.omnitask.data.TaskLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FocusTest {

    private val today = LocalDate.parse("2026-10-07")
    private fun t(line: String) = TaskLine.parse(line)!!

    private val overdue = t("- [ ] ส่งเอกสาร 🔼 ➕ 2026-09-20 📅 2026-10-05")
    private val waitingOld = t("- [ ] ตอบเรื่องตารางเวร #รอ/พี่เอ ➕ 2026-09-25")
    private val waitingNew = t("- [ ] รีวิวสไลด์ #รอ ➕ 2026-10-06")
    private val waitingThird = t("- [ ] เช็คยอด #รอ/บี ➕ 2026-10-01")
    private val futureOld = t("- [ ] เรียนภาษา ⏫ ➕ 2026-08-01")
    private val futureNew = t("- [ ] เขียน paper ⏫ ➕ 2026-10-01")
    // Has its own deadline, so finishing it is not "future work".
    private val showcase = t("- [x] Mock UX UI ⏫ ➕ 2026-10-01 📅 2026-10-06 ✅ 2026-10-06")
    private val all = listOf(overdue, waitingOld, waitingNew, waitingThird, futureOld, futureNew, showcase)

    @Test
    fun sectionsAreSeparateAndOrdered() {
        val b = Focus.build(all, today)
        assertEquals(listOf(overdue), b.must)
        assertEquals(listOf(waitingOld, waitingThird), b.waiting)
        assertEquals(listOf(futureOld), b.future)
    }

    @Test
    fun futureCountAndSkipRotate() {
        assertEquals(listOf(futureOld, futureNew), Focus.build(all, today, futureCount = 2).future)
        assertEquals(listOf(futureNew), Focus.build(all, today, skippedToday = setOf(futureOld.title)).future)
    }

    @Test
    fun warnsWhenRecentWorkIgnoresWaitingAndFuture() {
        val w = Focus.build(all, today).warnings
        assertTrue(w.any { "คนรอ" in it && "พี่เอ" in it })
        assertTrue(w.any { "อนาคต" in it })
    }

    @Test
    fun suggestsRaisingOldWaitingAndSoftDateForNeglectedFuture() {
        val s = Focus.build(all, today).suggestions
        assertEquals(setOf(waitingOld to Focus.Kind.RAISE_PRIORITY, futureOld to Focus.Kind.SOFT_DATE), s.map { it.task to it.kind }.toSet())
        val dismissed = Focus.build(all, today, dismissed = s.map { it.id }.toSet()).suggestions
        assertTrue(dismissed.isEmpty())
    }

    @Test
    fun softDateIsComingSaturday() {
        assertEquals(LocalDate.parse("2026-10-10"), Focus.softDate(today))
    }
}
