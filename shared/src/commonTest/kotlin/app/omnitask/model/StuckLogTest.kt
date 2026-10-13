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
        "    - 🧱 2026-10-01 ได้รูปแล้ว แต่ยังไม่รู้จะจัดหน้ายังไง",
        "    - 🧱 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ",
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
        assertTrue(lines.contains("    - 🧱 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ"))
        assertTrue(lines.contains("    - 🧱 2026-10-01 ได้รูปแล้ว แต่ยังไม่รู้จะจัดหน้ายังไง"))
    }

    @Test
    fun lineIsOneLineAndReadsBack() {
        val line = StuckLog.line(today, "  รอสเปก\nจากลูกค้า ")
        assertEquals("🧱 2026-10-13 รอสเปก จากลูกค้า", line)
        assertEquals(StuckLog.Entry(today, "รอสเปก จากลูกค้า"), StuckLog.parse(line))
        assertNull(StuckLog.parse("ใช้ธีมเดิม"))
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
}
