package app.omnitask.model

import app.omnitask.data.VaultText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class ReviewLogTest {

    private val file = listOf(
        "- [ ] ทำเว็บร้าน #peddose ➕ 2026-08-01",
        "    - ใช้ธีมเดิม",
        "    - ทบทวน 2026-10-20",
        "    - ก้าวถัดไป: ส่ง mock ให้พี่ดู",
        "    - ค้าง 2026-10-21 เดินอยู่: ทำหน้าแรกเสร็จ",
        "- [ ] อ่านหนังสือ ➕ 2026-09-01",
        "    - ทบทวน บทที่ 3 ก่อนสอบ",
        "    - ทบทวน 2026-10-01 กับหัวหน้า",
    )

    private fun tasks(lines: List<String> = file) = VaultText.parseFile("f", "Omni note.md", lines.joinToString("\n"))

    @Test
    fun reviewAndNextStepLinesStayOutOfTheDescription() {
        val web = tasks().first { it.title == "ทำเว็บร้าน" }
        assertEquals("ใช้ธีมเดิม", web.description)
        assertEquals(LocalDate.parse("2026-10-20"), ReviewLog.last(web))
        assertEquals("ส่ง mock ให้พี่ดู", web.nextStep)
    }

    @Test
    fun aDescriptionLineThatOnlyStartsWithTheWordIsLeftAlone() {
        // Only "ทบทวน" followed by a date and nothing else is the app's line; the owner's own lines stay description.
        val book = tasks().first { it.title == "อ่านหนังสือ" }
        assertEquals("ทบทวน บทที่ 3 ก่อนสอบ\nทบทวน 2026-10-01 กับหัวหน้า", book.description)
        assertNull(ReviewLog.last(book))
        assertNull(book.nextStep)
    }

    @Test
    fun editingTheDescriptionKeepsTheReviewAndNextStepLines() {
        val lines = file.toMutableList()
        VaultText.describe(lines, 0, "ใช้ธีมใหม่")
        val web = tasks(lines).first { it.title == "ทำเว็บร้าน" }
        assertEquals("ใช้ธีมใหม่", web.description)
        assertEquals(LocalDate.parse("2026-10-20"), ReviewLog.last(web))
        assertEquals("ส่ง mock ให้พี่ดู", web.nextStep)
        assertEquals(1, StuckLog.entries(web).size)
    }

    @Test
    fun writingTheReviewDateReplacesTheOldLineOnly() {
        val lines = file.toMutableList()
        ReviewLog.write(lines, 0, LocalDate.parse("2026-10-27"))
        assertEquals(file.size, lines.size)
        assertEquals("    - ทบทวน 2026-10-27", lines[2])
        // A task without the line gets one added; its own "ทบทวน ..." description lines are not touched.
        ReviewLog.write(lines, 5, LocalDate.parse("2026-10-27"))
        assertEquals(file.size + 1, lines.size)
        assertEquals(listOf("    - ทบทวน บทที่ 3 ก่อนสอบ", "    - ทบทวน 2026-10-01 กับหัวหน้า", "    - ทบทวน 2026-10-27"), lines.subList(6, 9))
    }

    @Test
    fun writingTheNextStepReplacesTheOldOneOrRemovesIt() {
        val lines = file.toMutableList()
        VaultText.setSubLine(lines, 0, Task.NEXT_STEP, Task.NEXT_STEP + " นัดพี่ดูงาน")
        assertEquals("    - ก้าวถัดไป: นัดพี่ดูงาน", lines[3])
        VaultText.setSubLine(lines, 0, Task.NEXT_STEP, null)
        assertEquals(file.size - 1, lines.size)
        assertNull(tasks(lines).first { it.title == "ทำเว็บร้าน" }.nextStep)
    }
}
