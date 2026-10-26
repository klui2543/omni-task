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
    fun recentFoldersComeFromTheNewestTasks() = chosen(setOf("Siriraj", "Omni")) {
        val tasks = listOf(
            task("- [ ] a #Siriraj/perf ➕ 2026-10-20"),
            task("- [ ] b #Omni/เลขา ➕ 2026-10-26"),
            task("- [ ] c #Siriraj/perf ➕ 2026-10-25"),
            task("- [ ] d #kluido ➕ 2026-10-26"),
        )
        assertEquals(listOf("Omni/เลขา", "Siriraj/perf"), Folders.recent(tasks, 3))
    }

    @Test
    fun searchFindsAFolderByAnyPartOfItsPath() {
        val paths = listOf("Omni", "Omni/เลขา", "Siriraj/ตารางเวรเภสัช/wording", "ระบบ")
        assertEquals(listOf("Siriraj/ตารางเวรเภสัช/wording"), Folders.search(paths, "word"))
        // The folder's own name matching ranks before a match higher up its path.
        assertEquals(listOf("Omni", "Omni/เลขา"), Folders.search(paths, "omni"))
    }
}
