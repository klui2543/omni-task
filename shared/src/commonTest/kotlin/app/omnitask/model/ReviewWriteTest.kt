package app.omnitask.model

import app.omnitask.data.VaultText
import app.omnitask.model.ReviewWrite.Answers
import app.omnitask.model.ReviewWrite.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class ReviewWriteTest {

    private val today = LocalDate.parse("2026-10-27")

    // Shaped like the real task note: notes, a ค้าง line and a subtask under the first task.
    private val file = listOf(
        "- [ ] Mock UX UI #peddose #รอ ⏫ 🆔 mock1 ➕ 2026-10-02",
        "    - ใช้ธีมเดิม",
        "    - ค้าง 2026-10-26 งานใหญ่เกิน",
        "    - [ ] ร่างหน้าแรก",
        "- [ ] นับยา ➕ 2026-10-24",
    )

    private fun run(i: Int, a: Answers, priority: Priority? = null, lines: MutableList<String> = file.toMutableList()): Pair<List<String>, String?> {
        val task = VaultText.parseFile("f", "Omni note.md", lines.joinToString("\n")).first { it.lineIndex == i }
        val ask = ReviewWrite.apply(lines, i, task, a, priority, today)
        return lines to ask
    }

    @Test
    fun movingWithANoteAndNextStepWritesTheLinesUnderTheNotesAndAboveTheSubtasks() {
        val (lines, ask) = run(0, Answers(Progress.GO, note = "ได้สีหลักแล้ว", nextStep = "ส่ง mock ให้พี่ดู"), Priority.HIGHEST)
        assertEquals(
            listOf(
                // The app's own priority writer puts the level after 🆔, as it does everywhere today.
                "- [ ] Mock UX UI #peddose #รอ 🆔 mock1 🔺 ➕ 2026-10-02",
                "    - ใช้ธีมเดิม",
                "    - ค้าง 2026-10-26 งานใหญ่เกิน",
                "    - ค้าง 2026-10-27 เดินอยู่: ได้สีหลักแล้ว",
                "    - ก้าวถัดไป: ส่ง mock ให้พี่ดู",
                "    - ทบทวน 2026-10-27",
                "    - [ ] ร่างหน้าแรก",
                "- [ ] นับยา ➕ 2026-10-24",
            ),
            lines,
        )
        assertNull(ask)
        // Read back: description and the subtask untouched, the new lines in their places.
        val t = VaultText.parseFile("f", "Omni note.md", lines.joinToString("\n")).first { it.lineIndex == 0 }
        assertEquals("ใช้ธีมเดิม", t.description)
        assertEquals("ส่ง mock ให้พี่ดู", t.nextStep)
        assertEquals(today, ReviewLog.last(t))
        assertEquals(2, StuckLog.entries(t).size)
    }

    @Test
    fun stuckWithoutANoteWritesTheReasonOnly() {
        val (lines, _) = run(4, Answers(Progress.STUCK, why = StuckLog.Reason.NO_TIME))
        assertEquals(listOf("- [ ] นับยา ➕ 2026-10-24", "    - ค้าง 2026-10-27 ไม่มีเวลา", "    - ทบทวน 2026-10-27"), lines.subList(4, 7))
    }

    @Test
    fun nothingNewWritesOnlyTheReviewDay() {
        val (lines, _) = run(4, Answers(Progress.GO))
        assertEquals(listOf("- [ ] นับยา ➕ 2026-10-24", "    - ทบทวน 2026-10-27"), lines.subList(4, 6))
    }

    @Test
    fun doneAndDroppedCloseTheTaskAndLeaveItsImportance() {
        assertEquals("- [x] นับยา ➕ 2026-10-24 ✅ 2026-10-27", run(4, Answers(Progress.DONE), Priority.HIGH).first[4])
        assertEquals("- [-] นับยา ➕ 2026-10-24", run(4, Answers(Progress.DROP), Priority.HIGH).first[4])
        assertEquals("- [ ] นับยา #สักวัน ➕ 2026-10-24", run(4, Answers(Progress.PARK)).first[4])
    }

    @Test
    fun datesWaitingAndFolderGoOnTheTaskLine() {
        val (lines, _) = run(
            4,
            Answers(Progress.GO, scheduled = LocalDate.parse("2026-10-31"), waiting = true, waitName = "พี่ เอ", due = LocalDate.parse("2026-11-05"), folder = "siriraj/คลัง"),
            Priority.MEDIUM,
        )
        assertEquals("- [ ] นับยา #siriraj/คลัง #รอ/พี่-เอ 🔼 ➕ 2026-10-24 ⏳ 2026-10-31 📅 2026-11-05", lines[4])
    }

    @Test
    fun aNameTurnsPlainWaitingIntoWaitingForThemAndNoOneRemovesIt() {
        assertEquals("- [ ] Mock UX UI #peddose #รอ/พี่เอ ⏫ 🆔 mock1 ➕ 2026-10-02", run(0, Answers(Progress.GO, waiting = true, waitName = "พี่เอ")).first[0])
        assertEquals("- [ ] Mock UX UI #peddose ⏫ 🆔 mock1 ➕ 2026-10-02", run(0, Answers(Progress.GO, waiting = false)).first[0])
        // Moving to another folder keeps the labels where they are.
        assertEquals("- [ ] Mock UX UI #omni #รอ ⏫ 🆔 mock1 ➕ 2026-10-02", run(0, Answers(Progress.GO, folder = "omni")).first[0])
        // "ยังไม่มีบ้าน" takes it out of its folder, labels stay.
        assertEquals("- [ ] Mock UX UI #รอ ⏫ 🆔 mock1 ➕ 2026-10-02", run(0, Answers(Progress.GO, folder = "")).first[0])
    }

    @Test
    fun notSureWhenTheyNeedItAddsATaskToAskThemToday() {
        val (_, ask) = run(0, Answers(Progress.GO, waiting = true, waitName = "พี่เอ", askWhen = true))
        assertEquals("- [ ] ถามพี่เอว่าต้องการ \"Mock UX UI\" เมื่อไร #peddose ➕ 2026-10-27 ⏳ 2026-10-27", ask)
        assertNull(run(0, Answers(Progress.DONE, askWhen = true)).second)
    }

    @Test
    fun aSecondReviewReplacesTheDayAndTheNextStep() {
        val first = run(4, Answers(Progress.GO, nextStep = "นับตู้ 1")).first.toMutableList()
        val (lines, _) = run(4, Answers(Progress.GO, nextStep = "นับตู้ 2"), lines = first)
        assertEquals(listOf("- [ ] นับยา ➕ 2026-10-24", "    - ก้าวถัดไป: นับตู้ 2", "    - ทบทวน 2026-10-27"), lines.subList(4, lines.size))
    }
}
