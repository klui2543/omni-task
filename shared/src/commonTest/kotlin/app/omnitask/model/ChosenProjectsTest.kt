package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChosenProjectsTest {

    private fun task(line: String) = TaskLine.parse(line)!!

    @Test
    fun onlyChosenTagsNameAProject() {
        val read = task("- [ ] Atomic Habits #อ่าน #peddose/แอป")
        val plain = task("- [ ] ล้างรถ #บ้าน")
        // Before the owner chooses, the first tag names the project as it always did.
        assertEquals("อ่าน", Projects.projectOf(read))
        assertEquals(setOf("อ่าน", "บ้าน"), Projects.seed(listOf(read, plain)))

        Projects.chosen = setOf("peddose")
        try {
            // A plain tag first no longer takes the task: the first chosen one does, branch and all.
            assertEquals("peddose/แอป", Projects.tagOf(read))
            assertEquals("peddose", Projects.projectOf(read))
            assertNull(Projects.projectOf(plain))
            assertEquals(listOf("peddose"), Projects.build(listOf(read, plain), kotlinx.datetime.LocalDate(2026, 10, 13)).map { it.name })
            // The tags that could be made projects, most used first.
            val more = task("- [ ] ซื้อน้ำยา #บ้าน")
            assertEquals(listOf("บ้าน" to 2, "อ่าน" to 1), Projects.others(listOf(read, plain, more)))
        } finally {
            Projects.chosen = null
        }
        assertEquals(emptyList(), Projects.others(listOf(read, plain)))
    }

    @Test
    fun aNewTasksProjectTagBecomesABranchUnlessTakenOff() {
        val today = kotlinx.datetime.LocalDate(2026, 10, 23)
        val fresh = task("- [ ] ตรวจสูตร #peddose/verify ➕ 2026-10-23")
        val known = task("- [ ] ยืนยันตาราง #rxshift ➕ 2026-10-23")
        val old = task("- [ ] งานเก่า #อ่าน ➕ 2026-09-01")
        val taken = task("- [ ] ล้างรถ #บ้าน ➕ 2026-10-22")
        val tasks = listOf(fresh, known, old, taken)
        // Only a new task's project, once; not a known project, an old task's tag, or one the owner took off.
        assertEquals(setOf("peddose"), Projects.newOnes(tasks, setOf("rxshift"), setOf("บ้าน"), today))
    }
}
