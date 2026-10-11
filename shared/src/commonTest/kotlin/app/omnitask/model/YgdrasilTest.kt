package app.omnitask.model

import app.omnitask.data.VaultText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class YgdrasilTest {

    private val today = LocalDate.parse("2026-10-11")
    private val tasks = VaultText.parseFile(
        "f", "Omni note.md",
        listOf(
            "- [x] สรุป requirement #peddose/research ✅ 2026-10-03",
            "- [ ] ทำหน้าคำนวณ #peddose/แอป/มือถือ 🆔 calc 📅 2026-10-09",
            "- [ ] ลองหน้าเว็บ #peddose/แอป/เว็บ ⏫",
            "- [ ] เขียน paper #peddose ⛔ calc",
            "- [ ] ส่งเอกสาร #Siriraj ⛔ calc 📅 2026-10-12",
            "- [ ] งานไม่มี tag",
            "- [ ] งานในกิ่งที่พับ #Siriraj/เก่า",
        ).joinToString("\n"),
    )
    private val states = mapOf(("Siriraj" to "เก่า") to Branches.State.PARKED)

    @Test
    fun theWholeTreeHoldsEveryProjectAndTiesWaitsAcrossThem() {
        val tree = Ygdrasil.grow(tasks, states, today, Ygdrasil.Filter(), starred = setOf("Siriraj"))
        // Starred first; the parked branch stays out, its project does not.
        assertEquals(listOf("Siriraj", "peddose"), tree.worlds.map { it.node.project })
        assertEquals(emptyList(), tree.worlds[0].children)
        // Open only: the done research task is gone, its branch stays (the plain filter keeps the shape).
        val ped = tree.worlds[1]
        assertEquals(listOf("research", "แอป"), ped.children.map { it.node.name })
        assertEquals(0, ped.children[0].matched)
        assertEquals(4, tree.matched)
        // Two tasks wait for "calc": one in its own project, one across.
        assertEquals(2, tree.links.size)
        assertEquals(1, tree.links.count { it.across })
    }

    @Test
    fun narrowingFiltersPruneEmptyBranchesAndWorlds() {
        val overdue = Ygdrasil.grow(tasks, states, today, Ygdrasil.Filter(dates = Ygdrasil.Dates.OVERDUE))
        assertEquals(listOf("peddose"), overdue.worlds.map { it.node.project })
        assertEquals(listOf("แอป"), overdue.worlds[0].children.map { it.node.name })
        assertEquals(1, overdue.matched)

        val high = Ygdrasil.grow(tasks, states, today, Ygdrasil.Filter(priorities = setOf(Priority.HIGH)))
        assertEquals(listOf("ลองหน้าเว็บ"), high.worlds.flatMap { w -> w.flatten().flatMap { it.tasks } }.map { it.title })

        val blocked = Ygdrasil.grow(tasks, states, today, Ygdrasil.Filter(blockedOnly = true))
        assertEquals(setOf("เขียน paper", "ส่งเอกสาร"), blocked.worlds.flatMap { w -> w.flatten().flatMap { it.tasks } }.map { it.title }.toSet())
        // The blocker is filtered out, yet the waits still show, ending at its branch.
        assertEquals(2, blocked.links.size)

        val parked = Ygdrasil.grow(tasks, states, today, Ygdrasil.Filter(parked = true, text = "พับ"))
        assertEquals(listOf("เก่า"), parked.worlds.single().children.map { it.node.name })
    }

    @Test
    fun layoutPlacesEveryLimbInItsColumnAndFoldsWhatIsAsked() {
        val tree = Ygdrasil.grow(tasks, states, today, Ygdrasil.Filter())
        val open = Ygdrasil.layout(tree, emptySet())
        val limbs = open.spots.filter { it.kind == Ygdrasil.Kind.LIMB }
        assertEquals(tree.worlds.sumOf { it.flatten().size }, limbs.size)
        assertEquals(Ygdrasil.COL, limbs.first { it.limb!!.node.tag == "peddose" }.x)
        assertEquals(3 * Ygdrasil.COL, limbs.first { it.limb!!.node.tag == "peddose/แอป/มือถือ" }.x)
        assertTrue(open.spots.all { it.y in 0f..open.height })
        assertEquals(1, open.spots.count { it.kind == Ygdrasil.Kind.TRUNK })
        // A branch edge for every limb, a task edge for every task, and both links.
        assertEquals(limbs.size, open.edges.count { it.kind == Ygdrasil.EdgeKind.BRANCH })
        assertEquals(4, open.edges.count { it.kind == Ygdrasil.EdgeKind.TASK })
        assertEquals(2, open.edges.count { it.kind == Ygdrasil.EdgeKind.LINK || it.kind == Ygdrasil.EdgeKind.LINK_ACROSS })

        // Folding peddose hides its limbs and tasks; the link across now ends on the folded world.
        val folded = Ygdrasil.layout(tree, setOf("peddose"))
        val world = folded.spots.single { it.kind == Ygdrasil.Kind.LIMB && it.limb!!.node.tag == "peddose" }
        assertTrue(world.folded)
        assertEquals(3, world.count)
        assertFalse(folded.spots.any { it.limb?.node?.project == "peddose" && it !== world })
        assertEquals(1, folded.edges.count { it.kind == Ygdrasil.EdgeKind.LINK_ACROSS })
        assertEquals(0, folded.edges.count { it.kind == Ygdrasil.EdgeKind.LINK })
    }

    @Test
    fun filterKeepsItsChoicesThroughText() {
        val f = Ygdrasil.Filter(Ygdrasil.Show.ALL, Ygdrasil.Dates.WEEK, setOf(Priority.HIGH, Priority.HIGHEST), blockedOnly = true, links = false, text = "a=b")
        assertEquals(f, Ygdrasil.Filter.decode(f.encode()))
        assertEquals(Ygdrasil.Filter(), Ygdrasil.Filter.decode(null))
        assertEquals(6, f.changed)
    }
}
