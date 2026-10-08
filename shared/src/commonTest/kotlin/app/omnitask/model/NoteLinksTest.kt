package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class NoteLinksTest {

    private val notes = listOf(
        "📁 Folder/งาน/ประชุมทีม.md",
        "📁 Folder/บ้าน/ประชุมทีม.md",
        "📁 Folder/งาน/สรุปยา.md",
        "Inbox.md",
    )

    @Test
    fun linkTextIsTheNameUnlessItClashes() {
        assertEquals("สรุปยา", NoteLinks.linkText("📁 Folder/งาน/สรุปยา.md", notes))
        assertEquals("📁 Folder/บ้าน/ประชุมทีม", NoteLinks.linkText("📁 Folder/บ้าน/ประชุมทีม.md", notes))
    }

    @Test
    fun resolvesNamesAndPaths() {
        assertEquals("📁 Folder/งาน/สรุปยา.md", NoteLinks.resolve("สรุปยา", notes))
        assertEquals("📁 Folder/บ้าน/ประชุมทีม.md", NoteLinks.resolve("📁 Folder/บ้าน/ประชุมทีม", notes))
        assertNull(NoteLinks.resolve("ไม่มีโน้ตนี้", notes))
    }

    @Test
    fun searchPutsNameMatchesFirst() {
        assertEquals(listOf("📁 Folder/งาน/สรุปยา.md"), NoteLinks.search("สรุป", notes))
        assertEquals(3, NoteLinks.search("Folder", notes).size)
    }

    @Test
    fun taskReadsLinkLinesAndInlineLinks() {
        val task = TaskLine.parse("- [ ] เตรียมสไลด์ [[สรุปยา|ยา]] 📅 2026-10-09")!!
            .copy(notes = listOf("[[ประชุมทีม]]", "![[Omni-1.webp]]", "จดไว้"))
        assertEquals(listOf("ประชุมทีม"), task.linkLines)
        assertEquals(listOf("ประชุมทีม", "สรุปยา"), task.links)
        assertEquals(listOf("จดไว้"), task.textNotes)
        assertEquals(listOf("Omni-1.webp"), task.attachments)
    }
}
