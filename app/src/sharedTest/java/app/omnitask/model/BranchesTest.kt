package app.omnitask.model

import app.omnitask.data.TaskLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchesTest {

    private val tasks = listOf(
        "- [x] สรุป requirement #peddose/research ✅ 2026-10-03",
        "- [ ] เลือกระหว่างมือถือกับเว็บ #peddose/แอป",
        "- [ ] ทำหน้าคำนวณ #peddose/แอป/มือถือ",
        "- [ ] ลองหน้าเว็บ #peddose/แอป/เว็บ",
        "- [ ] เขียน paper #peddose",
        "- [ ] งานอื่น #Siriraj",
    ).map { TaskLine.parse(it)!! }

    @Test
    fun nestedTagsMakeOneProjectWithBranches() {
        assertEquals("peddose", Projects.projectOf(tasks[2]))
        assertEquals("แอป/มือถือ", Branches.pathOf(tasks[2]))
        val root = Branches.tree("peddose", tasks, emptyMap())
        assertEquals(listOf("research", "แอป"), root.children.map { it.name })
        assertEquals(listOf("มือถือ", "เว็บ"), root.children[1].children.map { it.name })
        assertEquals(5, root.all.size)
        assertEquals(3, root.children[1].all.size)
        assertEquals(1, root.own.size)
        assertEquals(setOf("peddose", "Siriraj"), Projects.build(tasks, java.time.LocalDate.parse("2026-10-08")).map { it.name }.toSet())
    }

    @Test
    fun choosingParksTriedSiblingsAndParkingHidesTasks() {
        val states = mapOf(("peddose" to "แอป/มือถือ") to Branches.State.TRYING, ("peddose" to "แอป/เว็บ") to Branches.State.TRYING)
        val root = Branches.tree("peddose", tasks, states)
        val app = root.children[1]
        val next = Branches.choose(app.children[0], app.children, states)
        assertEquals(Branches.State.CHOSEN, next["peddose" to "แอป/มือถือ"])
        assertEquals(Branches.State.PARKED, next["peddose" to "แอป/เว็บ"])
        assertTrue(Branches.isParked(tasks[3], next))
        assertFalse(Branches.isParked(tasks[2], next))
        assertEquals(next, Branches.parse(Branches.encode(next)))
    }

    @Test
    fun renameKeepsBranchesAndOtherTags() {
        assertEquals("- [ ] ทำหน้า #pedcalc/แอป/มือถือ #รอ/พี่เอ", Branches.renameInLine("- [ ] ทำหน้า #peddose/แอป/มือถือ #รอ/พี่เอ", "peddose", "pedcalc"))
        assertEquals("- [ ] x #pedcalc 📅 2026-10-10", Branches.renameInLine("- [ ] x #peddose 📅 2026-10-10", "peddose", "pedcalc"))
        assertEquals("- [ ] x #peddoses", Branches.renameInLine("- [ ] x #peddoses", "peddose", "pedcalc"))
    }
}
