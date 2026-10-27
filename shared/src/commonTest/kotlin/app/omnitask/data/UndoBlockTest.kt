package app.omnitask.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Undo and redo of a tick or a move put a task's block back as it was, and never write over an edit made elsewhere. */
class UndoBlockTest {

    private val day = LocalDate(2026, 10, 27)
    private val note = listOf(
        "# งาน",
        "- [ ] เตรียมสไลด์ #omni 📅 2026-10-28",
        "    - ประชุมทีมวันพฤหัส",
        "    - [ ] ทำโครงสไลด์",
        "- [ ] งานอื่น",
    ).joinToString("\n")

    /** Ticks the task at line 1 the way the app does, and returns the note after with the block before and after. */
    private fun tick(text: String): Triple<String, List<String>, List<String>> {
        val lines = text.split("\n").toMutableList()
        val before = VaultText.blockAt(lines, 1)
        lines[1] = TaskLine.setDone(lines[1], true, day)
        return Triple(lines.joinToString("\n"), before, VaultText.blockAt(lines, 1))
    }

    @Test
    fun undoPutsTheTickBackAndRedoTicksAgain() {
        val (ticked, before, after) = tick(note)
        assertEquals(listOf("- [ ] เตรียมสไลด์ #omni 📅 2026-10-28", "    - ประชุมทีมวันพฤหัส", "    - [ ] ทำโครงสไลด์"), before)
        val undone = VaultText.swapBlock(ticked, 1, after, before)
        assertEquals(note, undone)
        assertEquals(ticked, VaultText.swapBlock(undone!!, 1, before, after))
    }

    @Test
    fun theBlockIsFoundWhenALineWasAddedAboveIt() {
        val (ticked, before, after) = tick(note)
        val shifted = ticked.replace("# งาน\n", "# งาน\n- [ ] งานใหม่จาก Obsidian\n")
        assertEquals(note.replace("# งาน\n", "# งาน\n- [ ] งานใหม่จาก Obsidian\n"), VaultText.swapBlock(shifted, 1, after, before))
    }

    @Test
    fun anEditMadeElsewhereIsNeverOverwritten() {
        val (ticked, before, after) = tick(note)
        // The task was renamed in Obsidian after the tick: undo must write nothing.
        assertNull(VaultText.swapBlock(ticked.replace("เตรียมสไลด์", "เตรียมสไลด์ใหม่"), 1, after, before))
        // A subtask added under it in Obsidian also changes the block.
        assertNull(VaultText.swapBlock(ticked.replace("    - [ ] ทำโครงสไลด์", "    - [ ] ทำโครงสไลด์\n    - [ ] ซ้อม"), 1, after, before))
    }

    @Test
    fun windowsLineEndingsStay() {
        val crlf = note.replace("\n", "\r\n")
        val lines = crlf.split("\r\n").toMutableList()
        val before = VaultText.blockAt(lines, 1)
        lines[1] = TaskLine.setDone(lines[1], true, day)
        val after = VaultText.blockAt(lines, 1)
        assertEquals(crlf, VaultText.swapBlock(lines.joinToString("\r\n"), 1, after, before))
    }

    @Test
    fun aRepeatingTaskGoesBackToItsOldDateWithItsSubtasksTicked() {
        val text = listOf("- [ ] รดน้ำต้นไม้ 🔁 every day 📅 2026-10-27", "    - [x] ต้นมะนาว ✅ 2026-10-27").joinToString("\n")
        val lines = text.split("\n").toMutableList()
        val before = VaultText.blockAt(lines, 0)
        VaultText.advanceRecurring(lines, 0, day)
        val after = VaultText.blockAt(lines, 0)
        val undone = VaultText.swapBlock(lines.joinToString("\n"), 0, after, before)
        assertEquals(text, undone)
    }
}
