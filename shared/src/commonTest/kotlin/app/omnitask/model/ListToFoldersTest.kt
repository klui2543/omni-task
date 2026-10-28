package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Owner's pick, 2026-10-28, way ก: lists become plain project folders, tags under the list's tag. */
class ListToFoldersTest {

    private val note = listOf(
        "---",
        "omni-list: true",
        "icon: 🏔️",
        "categories: เที่ยว, ประสบการณ์, เรียนรู้",
        "tag: bucketlist",
        "folders: เที่ยว/ไต้หวัน",
        "---",
        "# Bucket list",
        "",
        "- [ ] ไปดูแสงเหนือ #bucketlist #เที่ยว",
        "- [ ] เกียวโต #bucketlist/เที่ยว/ญี่ปุ่น",
        "- [x] กระโดดร่ม #ประสบการณ์ ✅ 2026-01-02",
        "- [ ] วิ่งมาราธอน",
        "    - [ ] ซ้อม 10 กม.",
        "    - จดเวลาไว้",
        "- [ ] Dune #watchlist/หนัง",
    ).joinToString("\n")

    @Test
    fun everyLineGetsItsFolderTagAndTheListHeaderGoes() {
        val r = ListToFolders.migrate("Omni/Bucket list.md", note, listOf("watchlist"))!!
        assertEquals(
            listOf(
                "# Bucket list",
                "",
                "- [ ] ไปดูแสงเหนือ #bucketlist/เที่ยว",
                "- [ ] เกียวโต #bucketlist/เที่ยว/ญี่ปุ่น",
                "- [x] กระโดดร่ม #bucketlist/ประสบการณ์ ✅ 2026-01-02",
                "- [ ] วิ่งมาราธอน #bucketlist",
                // A subtask with no tag of the list stays under its parent as it is.
                "    - [ ] ซ้อม 10 กม.",
                "    - จดเวลาไว้",
                // Put in another list: it goes with that list.
                "- [ ] Dune #watchlist/หนัง",
            ).joinToString("\n"),
            r.text,
        )
        assertEquals(3, r.changed)
        assertEquals(listOf("bucketlist/เที่ยว", "bucketlist/ประสบการณ์", "bucketlist/เรียนรู้", "bucketlist/เที่ยว/ไต้หวัน"), r.folders)
    }

    @Test
    fun otherHeaderLinesStay() {
        val text = "---\nomni-list: true\ncategories: หนัง\ntag: watchlist\naliases: ดู\n---\n# Watch list\n- [ ] Shogun #ซีรีส์"
        val r = ListToFolders.migrate("Omni/Watch list.md", text)!!
        assertEquals("---\naliases: ดู\n---\n# Watch list\n- [ ] Shogun #ซีรีส์ #watchlist", r.text)
    }

    @Test
    fun aNoteThatIsNoListIsLeftAlone() {
        assertNull(ListToFolders.migrate("Omni/Omni note.md", "# Omni\n- [ ] งาน #omni"))
    }

    @Test
    fun aLinePulledInFromAnotherNoteGetsOneFolderTag() {
        val list = OmniList("Bucket list", "Omni/Bucket list.md", "", listOf("เที่ยว"))
        val t = TaskLine.parse("- [ ] จองตั๋ว #bucketlist #เที่ยว 📅 2026-12-01")!!
        assertEquals("- [ ] จองตั๋ว #bucketlist/เที่ยว 📅 2026-12-01", ListToFolders.pulledLine(list, t))
        assertNull(ListToFolders.pulledLine(list, TaskLine.parse("- [ ] งานอื่น #omni")!!))
    }
}
