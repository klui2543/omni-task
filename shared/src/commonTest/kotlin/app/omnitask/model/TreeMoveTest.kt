package app.omnitask.model

import app.omnitask.data.VaultText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

/** Owner, 2026-10-27: on Ygdrasil everything can be moved, and a moved task shows in one place only. */
class TreeMoveTest {

    private val lists = listOf("bucketlist", "watchlist")

    @Test
    fun aProjectTaskDroppedOnAListLeavesItsProject() {
        assertEquals(
            "- [ ] ไปญี่ปุ่น #อ่าน #bucketlist 📅 2026-12-01",
            TreeMove.line("- [ ] ไปญี่ปุ่น #omni/แอป #อ่าน 📅 2026-12-01", "omni/แอป", emptyList(), TreeMove.To.InList("bucketlist")),
        )
    }

    @Test
    fun aListTaskDroppedOnAProjectLeavesItsList() {
        assertEquals(
            "- [ ] ดู Shogun #omni #ซีรีส์",
            TreeMove.line("- [ ] ดู Shogun #Watchlist #ซีรีส์", null, listOf("Watchlist"), TreeMove.To.Branch("omni")),
        )
    }

    @Test
    fun aListTaskDroppedOnAnotherListChangesList() {
        assertEquals(
            "- [ ] ดู Shogun #ซีรีส์ #bucketlist",
            TreeMove.line("- [ ] ดู Shogun #watchlist #ซีรีส์", null, listOf("watchlist"), TreeMove.To.InList("bucketlist")),
        )
    }

    @Test
    fun aProjectTaskMovedToAnotherProjectKeepsOneProjectTag() {
        assertEquals(
            "- [ ] ส่งเอกสาร #siriraj #อ่าน ⏫",
            TreeMove.line("- [ ] ส่งเอกสาร #omni/แอป #อ่าน ⏫", "omni/แอป", emptyList(), TreeMove.To.Branch("siriraj")),
        )
    }

    @Test
    fun noProjectYetTakesTasksFromProjectsAndLists() {
        // On the whole of "no project yet": only the project (and list) tags go, the others stay.
        assertEquals("- [ ] งาน #อ่าน", TreeMove.line("- [ ] งาน #omni #อ่าน", "omni", emptyList(), TreeMove.To.NoHome(null)))
        assertEquals("- [ ] งาน", TreeMove.line("- [ ] งาน #bucketlist", null, listOf("bucketlist"), TreeMove.To.NoHome(null)))
        // On one of its groups: that group's tag goes first, so the task lands in it.
        assertEquals("- [ ] งาน #ร้านค้า #อ่าน", TreeMove.line("- [ ] งาน #omni #อ่าน", "omni", emptyList(), TreeMove.To.NoHome("ร้านค้า")))
        assertEquals("- [ ] งาน #ร้านค้า #อ่าน", TreeMove.line("- [ ] งาน #อ่าน", null, emptyList(), TreeMove.To.NoHome("ร้านค้า")))
        // Already in that group: nothing is written twice.
        assertEquals("- [ ] งาน #อ่าน/หนังสือ", TreeMove.line("- [ ] งาน #omni #อ่าน/หนังสือ", "omni", emptyList(), TreeMove.To.NoHome("อ่าน")))
    }

    @Test
    fun aProjectTaskMovedToAnotherProjectKeepsItsListTag() {
        assertEquals("- [ ] งาน #siriraj #bucketlist", TreeMove.line("- [ ] งาน #omni #bucketlist", "omni", listOf("bucketlist"), TreeMove.To.Branch("siriraj")))
    }

    @Test
    fun aTaskOnAListIsNotAlsoInNoProjectYet() {
        val tasks = VaultText.parseFile(
            "f", "Omni note.md",
            listOf("- [ ] ไปญี่ปุ่น #bucketlist", "- [ ] งานลอย #อ่าน", "- [ ] งานไม่มีแท็ก").joinToString("\n"),
        )
        Projects.chosen = setOf("omni")
        Projects.ignoredTags = lists.toSet()
        try {
            val shown = Ygdrasil.strays(tasks, LocalDate.parse("2026-10-27"), Ygdrasil.Filter(), lists = lists).flatMap { it.tasks }.map { it.title }
            assertEquals(listOf("งานไม่มีแท็ก", "งานลอย"), shown)
        } finally {
            Projects.chosen = null
            Projects.ignoredTags = emptySet()
        }
    }

    @Test
    fun aBlockMovedToAnotherNoteCanBeTakenOutAgain() {
        val note = "# Omni\r\n- [ ] เดิม\r\n"
        val block = listOf("- [ ] ย้ายมา #omni", "    - รายละเอียด")
        val moved = VaultText.appendBlock(note, block)
        assertEquals("# Omni\r\n- [ ] เดิม\r\n- [ ] ย้ายมา #omni\r\n    - รายละเอียด\r\n", moved)
        // Undo takes exactly that block out again.
        assertEquals("# Omni\r\n- [ ] เดิม\r\n", VaultText.swapBlock(moved, -1, block, emptyList()))
    }
}
