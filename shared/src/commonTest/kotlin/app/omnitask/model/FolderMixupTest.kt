package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** The owner's report of 2026-10-28: folders that did not match the Projects page. */
class FolderMixupTest {

    private fun task(line: String) = TaskLine.parse(line)!!

    private val table = """
        | ชื่อใน Omni | ชื่อเรียกอื่นที่ Klui หรือ Claude อาจใช้ |
        |---|---|
        | Siriraj/ตารางเวรเภสัช | rxshift, ตารางเวร |
        | Siriraj/Peddose | peddose, ยาเด็ก |
        | Omni | omni, เลขา |
    """.trimIndent()

    @AfterTest
    fun reset() {
        ProjectNames.current = ProjectNames.Names()
        Projects.chosen = null
    }

    @Test
    fun aNumberAfterHashInTextIsNotATag() {
        // "(PR #79)" in a line the secretary wrote made a project called "79)".
        val t = task("- [ ] รวม PR (PR #79) เข้า main #Omni/ygdrasil ➕ 2026-10-27")
        assertEquals(listOf("Omni/ygdrasil"), t.tags)
        assertTrue("#79" in t.title, t.title)
        // Obsidian's own rule: a tag stops at a bracket or comma, and a tag of digits only is no tag.
        assertEquals(listOf("Omni"), task("- [ ] ดู #123 และ #Omni)").tags)
        assertEquals(listOf("v2", "2026ปี"), task("- [ ] งาน #v2, #2026ปี").tags)
        assertEquals(emptyList(), task("- [ ] รวมแล้วใน PR #79.").tags)
    }

    @Test
    fun aProjectsNameIsReadWhateverItsCase() {
        ProjectNames.current = ProjectNames.parse(table)
        // The table names Siriraj only as part of a row; #siriraj on its own still goes to Siriraj.
        assertEquals(listOf("Siriraj"), task("- [ ] งาน #siriraj").tags)
        assertEquals(listOf("Siriraj/flow"), task("- [ ] งาน #siriraj/flow").tags)
    }

    @Test
    fun aChosenProjectTakesTagsInAnyCase() {
        Projects.chosen = setOf("Siriraj", "ระบบ")
        val t = task("- [ ] งาน #siriraj/flow ➕ 2026-10-27")
        assertEquals("Siriraj", Projects.projectOf(t))
        assertEquals("Siriraj/flow", Folders.of(t))
        // Not made a project again in another spelling.
        assertEquals(emptySet(), Projects.newOnes(listOf(t), Projects.chosen!!, emptySet(), LocalDate(2026, 10, 27)))
        assertEquals(emptyList(), Projects.others(listOf(t)))
    }

    @Test
    fun foldersInAnotherCaseAreOneFolder() {
        Projects.chosen = setOf("Siriraj")
        val tasks = listOf(task("- [ ] a #Siriraj/x"), task("- [ ] b #siriraj/y"))
        assertEquals(listOf("Siriraj", "Siriraj/x", "Siriraj/y"), Folders.paths(tasks, setOf("siriraj/z")).filter { it != "Siriraj/z" })
        assertTrue("Siriraj/z" in Folders.paths(tasks, setOf("siriraj/z")))
    }

    @Test
    fun aProjectChosenInTwoSpellingsIsOne() {
        assertEquals(setOf("Siriraj", "omni"), Projects.oneSpelling(setOf("siriraj", "Siriraj", "omni")))
        ProjectNames.current = ProjectNames.parse(table)
        assertEquals(setOf("Omni"), Projects.oneSpelling(setOf("omni", "OMNI", "Omni")))
    }

    @Test
    fun branchesKeepTheirStateUnderTheNewSpelling() {
        Projects.chosen = setOf("Siriraj")
        val states = mapOf(("siriraj" to "z") to Branches.State.PARKED, ("Omni" to "") to Branches.State.ACTIVE)
        assertEquals(mapOf(("Siriraj" to "z") to Branches.State.PARKED, ("Omni" to "") to Branches.State.ACTIVE), Branches.respell(states))
    }

    @Test
    fun aNameTheOldReaderMadeCanNoLongerBeATag() {
        assertTrue(TaskLine.canBeTag("Siriraj"))
        assertTrue(TaskLine.canBeTag("2026ปี"))
        listOf("79)", "79", "79.").forEach { assertTrue(!TaskLine.canBeTag(it), it) }
    }

    @Test
    fun usedLatelyIsWhatTheOwnerPicked() {
        var picked = emptyList<String>()
        listOf("Omni", "Siriraj/flow", "Omni", "อนาคต", "Siriraj/x").forEach { picked = Folders.picked(picked, it, 3) }
        assertEquals(listOf("Siriraj/x", "อนาคต", "Omni"), picked)
    }
}
