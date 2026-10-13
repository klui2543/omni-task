package app.omnitask.model

import app.omnitask.data.VaultText
import app.omnitask.notify.Digest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

class StuckLogTest {

    private val today = LocalDate.parse("2026-10-13")

    private val file = listOf(
        "- [ ] ทำเว็บร้าน #peddose ➕ 2026-08-01",
        "    - ใช้ธีมเดิม",
        "    - ค้าง 2026-10-01 ได้รูปแล้ว แต่ยังไม่รู้จะจัดหน้ายังไง",
        "    - ค้าง 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ",
        "    - [ ] เลือกธีม",
        "- [ ] เขียนบทความ ➕ 2026-09-01",
        "- [ ] ล้างรถ 🔁 every week ➕ 2026-01-01",
        "- [ ] เรียนเปียโน #สักวัน ➕ 2026-01-01",
        "- [ ] จองตั๋ว ➕ 2026-10-10",
    )

    private fun tasks() = VaultText.parseFile("f", "Omni note.md", file.joinToString("\n"))

    @Test
    fun readsNotesOldestFirstAndKeepsThemOutOfTheDescription() {
        val web = tasks().first { it.title == "ทำเว็บร้าน" }
        assertEquals(
            listOf(
                StuckLog.Entry(LocalDate.parse("2026-09-02"), "รอคนอื่น: รอรูปสินค้าจากพี่เอ"),
                StuckLog.Entry(LocalDate.parse("2026-10-01"), "ได้รูปแล้ว แต่ยังไม่รู้จะจัดหน้ายังไง"),
            ),
            StuckLog.entries(web),
        )
        assertEquals("ใช้ธีมเดิม", web.description)
    }

    @Test
    fun editingTheDescriptionKeepsTheNotes() {
        val lines = file.toMutableList()
        VaultText.describe(lines, 0, "ธีมใหม่")
        assertEquals("    - ธีมใหม่", lines[1])
        assertTrue(lines.contains("    - ค้าง 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ"))
        assertTrue(lines.contains("    - ค้าง 2026-10-01 ได้รูปแล้ว แต่ยังไม่รู้จะจัดหน้ายังไง"))
    }

    @Test
    fun lineIsOneLineAndReadsBack() {
        val line = StuckLog.line(today, "  รอสเปก\nจากลูกค้า ")
        assertEquals("ค้าง 2026-10-13 รอสเปก จากลูกค้า", line)
        assertEquals(StuckLog.Entry(today, "รอสเปก จากลูกค้า"), StuckLog.parse(line))
        assertNull(StuckLog.parse("ใช้ธีมเดิม"))
        // Starting with the word is not enough: the date has to follow it.
        assertNull(StuckLog.parse("ค้างจ่ายค่าโฮสต์ 300 บาท"))
    }

    @Test
    fun timelineGroupsByDayNewestFirst() {
        val days = StuckLog.timeline(tasks()).map { it.first.toString() }
        assertEquals(listOf("2026-10-01", "2026-09-02"), days)
    }

    @Test
    fun asksAboutLongOpenTasksWithNoRecentNote() {
        // The web task was explained 12 days ago; repeating, parked and new ones are not asked.
        assertEquals(listOf("เขียนบทความ"), StuckLog.toAsk(tasks(), today).map { it.title })
        assertEquals(listOf("เขียนบทความ", "ทำเว็บร้าน"), StuckLog.toAsk(tasks(), today.plusDays(10)).map { it.title })
    }

    @Test
    fun aNoteCountsAsAReview() {
        val review = Focus.toReview(tasks(), today, emptyMap()).map { it.title }
        assertTrue("ทำเว็บร้าน" !in review)
        assertTrue("เขียนบทความ" in review)
    }

    @Test
    fun weeklyReviewMentionsThem() {
        assertTrue(Digest.weekly(tasks(), today).lines.any { it.contains("ค้างนาน 1 งาน") })
    }

    @Test
    fun summaryForClaudeListsExplainedTasks() {
        assertEquals(
            listOf("ทำเว็บร้าน: 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ; 2026-10-01 ได้รูปแล้ว แต่ยังไม่รู้จะจัดหน้ายังไง"),
            StuckLog.summary(tasks()),
        )
    }

    @Test
    fun mattersWhenLateOldOrExplained() {
        val byTitle = tasks().associateBy { it.title }
        assertTrue(StuckLog.matters(byTitle.getValue("ทำเว็บร้าน"), today))
        assertTrue(StuckLog.matters(byTitle.getValue("เขียนบทความ"), today))
        assertTrue(!StuckLog.matters(byTitle.getValue("จองตั๋ว"), today))
        assertTrue(!StuckLog.matters(byTitle.getValue("ล้างรถ"), today))
        val late = app.omnitask.data.TaskLine.parse("- [ ] ส่งงาน ➕ 2026-10-10 📅 2026-10-12")!!
        assertTrue(StuckLog.matters(late, today))
    }

    private val withSteps = listOf(
        "- [/] ทำเว็บร้าน ➕ 2026-08-01",
        "    - ค้าง 2026-09-02 รอรูปสินค้าจากพี่เอ",
        "    - ค้าง 2026-10-01 ยังไม่รู้จะจัดหน้ายังไง",
        "    - [x] ได้รูปสินค้า ✅ 2026-09-20",
        "    - [ ] เลือกธีม",
    )

    private fun stepTasks() = VaultText.parseFile("f", "Omni note.md", withSteps.joinToString("\n"))

    @Test
    fun pathHasStartNotesAndFinishedSubtasksInOrder() {
        val all = stepTasks()
        val web = all.first { it.parent == null }
        val subs = all.filter { it.parent == web.key }
        assertEquals(
            listOf(
                StuckLog.Event(LocalDate.parse("2026-08-01"), StuckLog.Step.START, ""),
                StuckLog.Event(LocalDate.parse("2026-09-02"), StuckLog.Step.NOTE, "รอรูปสินค้าจากพี่เอ"),
                StuckLog.Event(LocalDate.parse("2026-09-20"), StuckLog.Step.DONE, "ได้รูปสินค้า"),
                StuckLog.Event(LocalDate.parse("2026-10-01"), StuckLog.Step.NOTE, "ยังไม่รู้จะจัดหน้ายังไง"),
            ),
            StuckLog.events(web, subs),
        )
        assertEquals(LocalDate.parse("2026-10-01"), StuckLog.lastMove(web, subs))
        assertEquals(listOf(web.title), StuckLog.byTask(all).map { it.first.title })
    }

    @Test
    fun weeksRunBackFromTodayAndStopAtTheStart() {
        val all = stepTasks()
        val web = all.first { it.parent == null }
        val subs = all.filter { it.parent == web.key }
        // Today 2026-10-13, eight weeks oldest first: 08-19..08-25, 08-26..09-01, 09-02..09-08 (note), 09-09..09-15,
        // 09-16..09-22 (subtask done), 09-23..09-29, 09-30..10-06 (note), 10-07..10-13.
        assertEquals(
            listOf(
                StuckLog.Week.NONE, StuckLog.Week.NONE, StuckLog.Week.NOTE, StuckLog.Week.NONE,
                StuckLog.Week.DONE, StuckLog.Week.NONE, StuckLog.Week.NOTE, StuckLog.Week.NONE,
            ),
            StuckLog.weeks(web, subs, today),
        )
        val fresh = app.omnitask.data.TaskLine.parse("- [ ] จองตั๋ว ➕ 2026-10-10")!!
        assertEquals(listOf(StuckLog.Week.NONE), StuckLog.weeks(fresh, emptyList(), today))
    }
}
