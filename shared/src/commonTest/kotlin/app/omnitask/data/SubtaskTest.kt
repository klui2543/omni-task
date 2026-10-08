package app.omnitask.data

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

class SubtaskTest {

    private val file = listOf(
        "# งาน",
        "- [ ] เตรียมสไลด์ 📅 2026-10-12",
        "    - ประชุมทีมวันพฤหัส",
        "    - [[ประชุมทีม]]",
        "    - [x] หาข้อมูลยา ✅ 2026-10-07",
        "        - ใช้ Lexicomp",
        "    - [ ] ทำโครงสไลด์",
        "    - [ ] ซ้อมพูด",
        "- [ ] งานอื่น",
    )

    @Test
    fun indentedCheckboxesBecomeSubtasks() {
        val tasks = VaultText.parseFile("f", "TaskForge.md", file.joinToString("\n"))
        val parent = tasks.first { it.title == "เตรียมสไลด์" }
        assertNull(parent.parent)
        assertEquals(listOf("หาข้อมูลยา", "ทำโครงสไลด์", "ซ้อมพูด"), tasks.filter { it.parent == parent.key }.map { it.title })
        assertNull(tasks.first { it.title == "งานอื่น" }.parent)
        assertEquals("ประชุมทีมวันพฤหัส", parent.description)
        assertEquals("ประชุมทีมวันพฤหัส", parent.descriptionPreview)
        // Search finds a task by words in its details too.
        val q = app.omnitask.model.TaskQuery(groupBy = app.omnitask.model.GroupBy.NONE, text = "พฤหัส")
        assertEquals(listOf("เตรียมสไลด์"), q.run(tasks, LocalDate.parse("2026-10-08")).flatMap { it.tasks }.map { it.title })
    }

    @Test
    fun reorderMovesWholeBlocks() {
        val lines = file.toMutableList()
        val ok = VaultText.orderSubtasks(lines, 1, listOf("    - [ ] ซ้อมพูด", "    - [x] หาข้อมูลยา ✅ 2026-10-07", "    - [ ] ทำโครงสไลด์"))
        assertEquals(true, ok)
        assertEquals(
            listOf(
                "# งาน", "- [ ] เตรียมสไลด์ 📅 2026-10-12", "    - ประชุมทีมวันพฤหัส", "    - [[ประชุมทีม]]",
                "    - [ ] ซ้อมพูด", "    - [x] หาข้อมูลยา ✅ 2026-10-07", "        - ใช้ Lexicomp", "    - [ ] ทำโครงสไลด์", "- [ ] งานอื่น",
            ),
            lines,
        )
    }

    @Test
    fun addAndDescribe() {
        val lines = file.toMutableList()
        VaultText.insertSubtask(lines, 1, "- [ ] ส่งให้พี่เอ")
        assertEquals("    - [ ] ส่งให้พี่เอ", lines[8])
        VaultText.describe(lines, 1, "ประชุมวันพฤหัส 10:00\nห้องประชุม 3")
        assertEquals(listOf("    - ประชุมวันพฤหัส 10:00", "    - ห้องประชุม 3", "    - [[ประชุมทีม]]"), lines.subList(2, 5))
    }

    @Test
    fun deleteTakesTheWholeBlock() {
        val lines = mutableListOf(
            "- [ ] ก่อน",
            "- [ ] ลบอันนี้ #งาน",
            "    - รายละเอียด",
            "    - [ ] งานย่อย",
            "        - [ ] ย่อยอีกชั้น",
            "- [ ] หลัง",
        )
        val removed = VaultText.cutBlock(lines, 1)
        assertEquals(4, removed.size)
        assertEquals(listOf("- [ ] ก่อน", "- [ ] หลัง"), lines)
        lines.addAll(1, removed)
        assertEquals("- [ ] ลบอันนี้ #งาน", lines[1])
        assertEquals(6, lines.size)
    }
}
