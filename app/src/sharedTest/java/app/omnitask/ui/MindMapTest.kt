package app.omnitask.ui

import app.omnitask.data.TaskLine
import app.omnitask.model.Branches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MindMapTest {

    @Test
    fun branchesSitInTheirDepthsColumn() {
        val tasks = listOf(
            "- [x] สรุป requirement #peddose/research ✅ 2026-10-03",
            "- [ ] เลือกระหว่างมือถือกับเว็บ #peddose/แอป",
            "- [ ] ทำหน้าคำนวณ #peddose/แอป/มือถือ",
            "- [ ] ลองหน้าเว็บ #peddose/แอป/เว็บ",
            "- [ ] เขียน paper #peddose",
            "- [ ] งานอื่น #Siriraj",
        ).map { TaskLine.parse(it)!! }
        val states = mapOf(("peddose" to "แอป/แท็บเล็ต") to Branches.State.TRYING, ("peddose" to "ทุน") to Branches.State.ACTIVE)
        val root = Branches.tree("peddose", tasks, states)

        // On the map every branch sits in its depth's column, centred on what it holds.
        val (items, height) = mapLayout(root)
        val branches = items.filterIsInstance<MapItem.BranchAt>()
        assertEquals(root.flatten().size, branches.size)
        val app = branches.first { it.node.path == "แอป" }
        assertEquals(200f, app.x)
        assertTrue(height > 0f && branches.all { it.y in 0f..height })
    }
}
