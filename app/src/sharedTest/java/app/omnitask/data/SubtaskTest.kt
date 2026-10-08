package app.omnitask.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        val tasks = VaultRepository.parseFile("f", "TaskForge.md", file.joinToString("\n"))
        val parent = tasks.first { it.title == "เตรียมสไลด์" }
        assertNull(parent.parent)
        assertEquals(listOf("หาข้อมูลยา", "ทำโครงสไลด์", "ซ้อมพูด"), tasks.filter { it.parent == parent.key }.map { it.title })
        assertNull(tasks.first { it.title == "งานอื่น" }.parent)
        assertEquals("ประชุมทีมวันพฤหัส", parent.description)
    }

    @Test
    fun reorderMovesWholeBlocks() {
        val lines = file.toMutableList()
        val ok = VaultRepository.orderSubtasks(lines, 1, listOf("    - [ ] ซ้อมพูด", "    - [x] หาข้อมูลยา ✅ 2026-10-07", "    - [ ] ทำโครงสไลด์"))
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
        VaultRepository.insertSubtask(lines, 1, "- [ ] ส่งให้พี่เอ")
        assertEquals("    - [ ] ส่งให้พี่เอ", lines[8])
        VaultRepository.describe(lines, 1, "ประชุมวันพฤหัส 10:00\nห้องประชุม 3")
        assertEquals(listOf("    - ประชุมวันพฤหัส 10:00", "    - ห้องประชุม 3", "    - [[ประชุมทีม]]"), lines.subList(2, 5))
    }
}
