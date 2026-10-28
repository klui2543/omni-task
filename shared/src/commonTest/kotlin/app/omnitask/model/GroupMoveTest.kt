package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Owner, 2026-10-28: "ต้องทำไปหมดทุกคุณสมบัติ", a list's folders do all that a project's branches do on Ygdrasil. */
class GroupMoveTest {

    private val bucket = OmniList("Bucket list", "Omni/Bucket list.md", "", listOf("เที่ยว", "ประสบการณ์"), folders = listOf("เที่ยว/ไต้หวัน"))
    private val watch = OmniList("Watch list", "Omni/Watch list.md", "", listOf("หนัง"))
    private val taskFile = "TaskForge.md"

    private var chosen: Set<String>? = null
    private var roots: Set<String> = emptySet()

    @BeforeTest
    fun setUp() {
        chosen = Projects.chosen
        roots = Projects.listRoots
        Projects.chosen = setOf("omni", "บ้าน")
        Projects.listRoots = setOf("bucketlist", "watchlist")
    }

    @AfterTest
    fun tearDown() {
        Projects.chosen = chosen
        Projects.listRoots = roots
    }

    private var line = 0
    private fun task(raw: String, list: String? = null, file: String = if (list != null) "Omni/$list.md" else taskFile, parent: String? = null) =
        TaskLine.parse(raw)!!.copy(list = list, filePath = file, fileUri = file, lineIndex = line++, parent = parent)

    private fun GroupMove.Plan.lines() = steps.associate { it.task.title to it.line(it.task.raw) }

    @Test
    fun aListFolderBecomesAProjectBranch() {
        val kyoto = task("- [ ] เกียวโต #bucketlist/เที่ยว/ญี่ปุ่น", "Bucket list")
        val north = task("- [ ] แสงเหนือ #bucketlist #เที่ยว", "Bucket list")
        val run = task("- [ ] มาราธอน #bucketlist", "Bucket list")
        val plan = GroupMove.plan(GroupMove.From.Folder(bucket, "เที่ยว"), GroupMove.To.Branch("omni"), listOf(kyoto, north, run), emptyList(), taskFile)!!
        assertEquals("omni/เที่ยว", plan.at)
        assertEquals(mapOf("เกียวโต" to "- [ ] เกียวโต #omni/เที่ยว/ญี่ปุ่น", "แสงเหนือ" to "- [ ] แสงเหนือ #omni/เที่ยว"), plan.lines())
        // Written in the list's note, they go to the task note, as a list item dragged onto a project does.
        assertTrue(plan.steps.all { it.note == taskFile })
        // The saved empty folder under it becomes an empty branch.
        assertEquals(listOf("omni/เที่ยว/ไต้หวัน"), plan.empty)
    }

    @Test
    fun aFolderOnTheTrunkBecomesAProjectOfItsOwn() {
        val kyoto = task("- [ ] เกียวโต #bucketlist/เที่ยว/ญี่ปุ่น", "Bucket list")
        val plan = GroupMove.plan(GroupMove.From.Folder(bucket, "เที่ยว/ญี่ปุ่น"), GroupMove.To.Branch(""), listOf(kyoto), emptyList(), taskFile)!!
        assertEquals("ญี่ปุ่น", plan.at)
        assertEquals("- [ ] เกียวโต #ญี่ปุ่น", plan.lines()["เกียวโต"])
    }

    @Test
    fun aProjectBranchBecomesAListFolder() {
        val a = task("- [ ] หาที่พัก #omni/ทริป/โตเกียว 📅 2026-11-01")
        val b = task("- [ ] จองตั๋ว #omni/ทริป")
        val other = task("- [ ] งานอื่น #omni/แอป")
        val plan = GroupMove.plan(GroupMove.From.Branch("omni/ทริป"), GroupMove.To.Folder(bucket, "เที่ยว"), listOf(a, b, other), listOf("omni/ทริป/โอซาก้า"), taskFile)!!
        assertEquals("เที่ยว/ทริป", plan.at)
        assertEquals(
            mapOf("หาที่พัก" to "- [ ] หาที่พัก #bucketlist/เที่ยว/ทริป/โตเกียว 📅 2026-11-01", "จองตั๋ว" to "- [ ] จองตั๋ว #bucketlist/เที่ยว/ทริป"),
            plan.lines(),
        )
        // They stay in their own notes, like a task pulled into a list.
        assertTrue(plan.steps.all { it.note == null })
        assertEquals(listOf("เที่ยว/ทริป/โอซาก้า"), plan.empty)
    }

    @Test
    fun aWholeProjectGoesIntoAList() {
        val a = task("- [ ] ทาสีรั้ว #บ้าน")
        val plan = GroupMove.plan(GroupMove.From.Branch("บ้าน"), GroupMove.To.Folder(watch, ""), listOf(a), emptyList(), taskFile)!!
        assertEquals("- [ ] ทาสีรั้ว #watchlist/บ้าน", plan.lines()["ทาสีรั้ว"])
    }

    @Test
    fun aFolderOnTheListCardBecomesANewList() {
        val made = OmniList("ญี่ปุ่น", "Omni/ญี่ปุ่น.md", "", emptyList())
        val kyoto = task("- [ ] เกียวโต #bucketlist/เที่ยว/ญี่ปุ่น/คันไซ", "Bucket list")
        val tokyo = task("- [ ] โตเกียว #bucketlist/เที่ยว/ญี่ปุ่น")
        val plan = GroupMove.plan(GroupMove.From.Folder(bucket, "เที่ยว/ญี่ปุ่น"), GroupMove.To.Folder(made, "", made = true), listOf(kyoto, tokyo), emptyList(), taskFile)!!
        assertEquals("", plan.at)
        assertEquals(mapOf("เกียวโต" to "- [ ] เกียวโต #ญี่ปุ่น/คันไซ", "โตเกียว" to "- [ ] โตเกียว #ญี่ปุ่น"), plan.lines())
        // The item written in Bucket list's note moves into the new note; the one pulled in stays in its own.
        assertEquals(mapOf("เกียวโต" to "Omni/ญี่ปุ่น.md", "โตเกียว" to null), plan.steps.associate { it.task.title to it.note })
    }

    @Test
    fun aWholeListGoesUnderAnotherListsFolder() {
        val film = task("- [ ] Dune #watchlist #หนัง", "Watch list")
        val plain = task("- [ ] ดูสารคดี", "Watch list")
        val plan = GroupMove.plan(GroupMove.From.Folder(watch, ""), GroupMove.To.Folder(bucket, "ประสบการณ์"), listOf(film, plain), emptyList(), taskFile)!!
        assertEquals("ประสบการณ์/Watch-list", plan.at)
        assertEquals(
            mapOf("Dune" to "- [ ] Dune #bucketlist/ประสบการณ์/Watch-list/หนัง", "ดูสารคดี" to "- [ ] ดูสารคดี #bucketlist/ประสบการณ์/Watch-list"),
            plan.lines(),
        )
        assertTrue(plan.steps.all { it.note == bucket.path })
        // Its saved one-level folder goes along as a saved folder.
        assertEquals(listOf("ประสบการณ์/Watch-list/หนัง"), plan.empty)
    }

    @Test
    fun aListItemWithAProjectTooKeepsOneHome() {
        val both = task("- [ ] เขียนรีวิว #bucketlist/เที่ยว #omni/แอป")
        val plan = GroupMove.plan(GroupMove.From.Folder(bucket, "เที่ยว"), GroupMove.To.Branch("บ้าน"), listOf(both), emptyList(), taskFile)!!
        assertEquals("- [ ] เขียนรีวิว #บ้าน/เที่ยว", plan.lines()["เขียนรีวิว"])
    }

    @Test
    fun aSubtaskGoesInsideItsParentsBlock() {
        val parent = task("- [ ] ญี่ปุ่น 7 วัน #bucketlist/เที่ยว", "Bucket list")
        val sub = task("    - [ ] จองเรียวกัง #bucketlist/เที่ยว", "Bucket list", parent = parent.key)
        val plan = GroupMove.plan(GroupMove.From.Folder(bucket, "เที่ยว"), GroupMove.To.Branch("omni"), listOf(parent, sub), emptyList(), taskFile)!!
        assertEquals(listOf("ญี่ปุ่น 7 วัน"), plan.steps.map { it.task.title })
        // The subtask's own tag is changed in the block that moves.
        assertEquals("    - [ ] จองเรียวกัง #omni/เที่ยว", plan.steps.single().rest(sub.raw))
        assertEquals("    - plain note", plan.steps.single().rest("    - plain note"))
    }

    @Test
    fun anEmptyFolderStaysAnEmptyBranch() {
        val plan = GroupMove.plan(GroupMove.From.Folder(bucket, "เที่ยว/ไต้หวัน"), GroupMove.To.Branch("omni"), emptyList(), emptyList(), taskFile)!!
        assertEquals(listOf("omni/ไต้หวัน"), plan.empty)
    }

    @Test
    fun nothingHappensWhereNothingWouldChange() {
        // A list on its own folder, or a folder on the folder it is already in.
        assertNull(GroupMove.placeOf(GroupMove.From.Folder(bucket, ""), GroupMove.To.Folder(bucket, "เที่ยว")))
        assertNull(GroupMove.placeOf(GroupMove.From.Folder(bucket, "เที่ยว/ไต้หวัน"), GroupMove.To.Folder(bucket, "เที่ยว")))
        assertNull(GroupMove.placeOf(GroupMove.From.Folder(bucket, "เที่ยว"), GroupMove.To.Folder(bucket, "เที่ยว/ไต้หวัน")))
        // A project's branch onto a branch is Ygdrasil's own graft, not this.
        assertNull(GroupMove.placeOf(GroupMove.From.Branch("omni/แอป"), GroupMove.To.Branch("บ้าน")))
    }

    @Test
    fun otherTagsStay() {
        val t = task("- [ ] ดูหนัง #watchlist/หนัง #กับแฟน ⏫", "Watch list")
        val plan = GroupMove.plan(GroupMove.From.Folder(watch, "หนัง"), GroupMove.To.Folder(bucket, ""), listOf(t), emptyList(), taskFile)!!
        assertEquals("- [ ] ดูหนัง #กับแฟน #bucketlist/หนัง ⏫", plan.lines()["ดูหนัง"])
    }
}
