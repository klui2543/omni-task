package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoldersTest {

    private fun task(line: String) = TaskLine.parse(line)!!

    private fun <T> chosen(projects: Set<String>, body: () -> T): T {
        Projects.chosen = projects
        try { return body() } finally { Projects.chosen = null }
    }

    @Test
    fun statusTagsAreLabelsNotFolders() = chosen(setOf("Siriraj", "Omni", "อนาคต")) {
        listOf("kluido", "claudedo", "รอ", "รอ/พี่เอ", "สักวัน", "remind-at-due").forEach { assertTrue(Folders.isLabel(it), it) }
        listOf("Siriraj", "Siriraj/ตารางเวรเภสัช/wording", "ระบบ", "siriraj").forEach { assertFalse(Folders.isLabel(it), it) }
        // อนาคต is a kind tag, but the owner made it a project, so here it is a folder.
        assertFalse(Folders.isLabel("อนาคต"))
    }

    @Test
    fun theFutureKindIsALabelUntilItIsAProject() = chosen(setOf("Omni")) {
        assertTrue(Folders.isLabel("อนาคต"))
    }

    @Test
    fun aTasksFolderIsItsProjectTagElseItsFirstPlainTag() = chosen(setOf("Siriraj", "Omni")) {
        assertEquals("Siriraj/ตารางเวรเภสัช/wording", Folders.of(task("- [ ] เขียนถ้อยคำ #kluido #Siriraj/ตารางเวรเภสัช/wording")))
        assertEquals("ระบบ", Folders.of(task("- [ ] สำรองข้อมูล #kluido #ระบบ")))
        assertNull(Folders.of(task("- [ ] ไม่มีบ้าน #kluido #รอ")))
        assertNull(Folders.of(task("- [ ] ไม่มีแท็ก")))
    }

    @Test
    fun aNewTaskHasOneFolderAndKeepsItsLabels() = chosen(setOf("Siriraj", "Omni")) {
        assertEquals(listOf("Omni/เลขา", "kluido"), Folders.withFolder(listOf("kluido"), "Omni/เลขา"))
        // Picking another folder moves it: the old one goes, labels stay where they were.
        assertEquals(listOf("Siriraj/perf", "kluido", "รอ"), Folders.withFolder(listOf("Omni/เลขา", "kluido", "รอ"), "Siriraj/perf"))
        // No folder: only the labels are left.
        assertEquals(listOf("kluido"), Folders.withFolder(listOf("Omni/เลขา", "kluido"), null))
        // A label typed where a folder goes is added as a label; the folder stays.
        assertEquals(listOf("Omni/เลขา", "kluido", "รอ/พี่เอ"), Folders.withFolder(listOf("Omni/เลขา", "kluido"), "รอ/พี่เอ"))
        // A tag typed as "# a b" is written the way the line writes tags.
        assertEquals(listOf("Omni/งาน-บ้าน"), Folders.withFolder(emptyList(), "#Omni/งาน บ้าน"))
    }

    @Test
    fun theProjectTagIsTheFolderOnANewTaskToo() = chosen(setOf("Siriraj", "Omni")) {
        // A lone tag before the project tag: the project tag is the folder, as Folders.of reads a saved task.
        assertEquals("Siriraj/a", Folders.ofTags(listOf("ระบบ", "Siriraj/a")))
        assertEquals(listOf("Omni", "ระบบ"), Folders.withFolder(listOf("ระบบ", "Siriraj/a"), "Omni"))
        assertEquals("ระบบ", Folders.ofTags(listOf("kluido", "ระบบ")))
    }

    @Test
    fun labelsIgnoreCase() = chosen(setOf("Omni")) {
        assertTrue(Folders.isLabel("Kluido"))
        assertTrue(Folders.isLabel("ClaudeDo"))
    }

    @Test
    fun aTypedFolderIsTidied() {
        assertEquals("Omni/เลขา", Folders.clean("Omni / เลขา"))
        assertEquals("Omni", Folders.clean("Omni/"))
        assertEquals("Omni/งาน-บ้าน", Folders.clean(" #Omni//งาน บ้าน "))
        assertEquals("", Folders.clean(" / "))
    }

    @Test
    fun everyFolderOnTheWayIsListed() = chosen(setOf("Siriraj", "Omni")) {
        val tasks = listOf(
            task("- [ ] a #Siriraj/ตารางเวรเภสัช/wording #kluido"),
            task("- [ ] b #Omni"),
            task("- [ ] c #ระบบ"),
            task("- [ ] d #kluido"),
        )
        assertEquals(
            listOf("Omni", "Omni/เลขา", "Siriraj", "Siriraj/ตารางเวรเภสัช", "Siriraj/ตารางเวรเภสัช/wording", "ระบบ"),
            Folders.paths(tasks, setOf("Omni/เลขา")),
        )
    }

    @Test
    fun searchFindsAFolderByAnyPartOfItsPath() {
        val paths = listOf("Omni", "Omni/เลขา", "Siriraj/ตารางเวรเภสัช/wording", "ระบบ")
        assertEquals(listOf("Siriraj/ตารางเวรเภสัช/wording"), Folders.search(paths, "word"))
        // The folder's own name matching ranks before a match higher up its path.
        assertEquals(listOf("Omni", "Omni/เลขา"), Folders.search(paths, "omni"))
    }

    @Test
    fun usedOftenCountsTasksMadeLatelyThenFoldersPickedLately() = chosen(setOf("Siriraj", "Omni", "My-space")) {
        val today = kotlinx.datetime.LocalDate.parse("2026-10-30")
        val tasks = listOf(
            "- [ ] ก #Siriraj/ตารางเวรเภสัช ➕ 2026-10-29",
            "- [ ] ข #Siriraj/ตารางเวรเภสัช ➕ 2026-10-20",
            "- [x] ค #Siriraj/ตารางเวรเภสัช ➕ 2026-10-10 ✅ 2026-10-11",
            "- [ ] ง #Omni ➕ 2026-10-28",
            // Made more than 30 days ago, or with no made date: not counted.
            "- [ ] จ #My-space ➕ 2026-09-01",
            "- [ ] ฉ #My-space ➕ 2026-09-02",
            "- [ ] ช #My-space",
            // Not the owner's own adding: the secretary's, a cancelled one, a finished round of a repeating task.
            "- [ ] ซ #kluido #My-space ➕ 2026-10-29",
            "- [-] ฌ #My-space ➕ 2026-10-29",
            "- [x] ญ #My-space 🔁 every day ➕ 2026-10-29 ✅ 2026-10-29",
        ).map { task(it).copy(filePath = app.omnitask.data.VaultText.TASK_FILE) } +
            // A task in another note (the secretary's book) does not count either.
            task("- [ ] ฎ #My-space ➕ 2026-10-29").copy(filePath = "📁 Folder/หลังบ้าน/Claude/Claude log.md")
        val all = Folders.paths(tasks, emptySet()) + "Siriraj/ใหม่"
        assertEquals(
            listOf("Siriraj/ตารางเวรเภสัช" to 3, "Omni" to 1, "Siriraj/ใหม่" to 0, "My-space" to 0),
            // A pick of a folder that is gone is skipped; the newer pick comes first among the uncounted.
            Folders.frequent(tasks, today, listOf("Siriraj/ใหม่", "ไม่มีแล้ว", "my-space"), all),
        )
        assertEquals(1, Folders.frequent(tasks, today, emptyList(), all, n = 1).size)
    }
}
