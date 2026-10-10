package app.omnitask.web

import app.omnitask.data.Archive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WebNotesTest {

    private val main = "- [ ] ส่งรายงาน 📅 2026-10-09\n"
    private val other = "# โปรเจกต์\n- [ ] เขียนบท 📅 2026-10-08\n    - [ ] หาข้อมูล\n"
    private val list = "---\nomni-list: true\n---\n- [ ] ไปญี่ปุ่น\n"

    private fun notes(vararg n: WebNotes.Note) = Json.encodeToString(n.toList())

    private val vault = notes(
        WebNotes.Note("idMain", "📁 Folder/หลังบ้าน/Omni/Omni note.md", main),
        WebNotes.Note("idOther", "Projects/Book.md", other),
        WebNotes.Note("idArchive", Archive.FILE, "- [x] เก่า ✅ 2026-01-01\n"),
        WebNotes.Note("idCopy", "Projects/Book (conflict 2026-10-09-05-55-31).md", other),
        WebNotes.Note("idList", "📁 Folder/หลังบ้าน/Omni/Bucket list.md", list),
    )

    @Test
    fun readsEveryNoteWithItsOwnKey() {
        val tasks = WebNotes.tasks(WebNotes.MANY, "", vault)
        assertEquals(listOf("ส่งรายงาน", "เขียนบท", "หาข้อมูล"), tasks.map { it.title })
        assertEquals(listOf("idMain#0", "idOther#1", "idOther#2"), tasks.map { it.key })
        assertEquals("idOther#1", tasks[2].parent)
        assertEquals("Projects/Book.md", tasks[1].filePath)
    }

    @Test
    fun oneNoteIsReadAsBefore() {
        val tasks = WebNotes.tasks("idOther", "Projects/Book.md", other)
        assertEquals(listOf("idOther#1", "idOther#2"), tasks.map { it.key })
    }

    @Test
    fun leavesOutArchivesAndConflictCopies() {
        assertFalse(WebNotes.isRead(Archive.FILE))
        assertFalse(WebNotes.isRead(Archive.LEGACY_FILE))
        assertFalse(WebNotes.isRead("A/Note (conflicted copy 2026-10-09).md"))
        assertTrue(WebNotes.isRead("A/Note.md"))
    }

    @Test
    fun pagesSeeTasksFromEveryNote() {
        val loaded = WebCore.loadTasks(WebNotes.MANY, "", vault, "2026-10-08")
        assertTrue(loaded.contains("\"note\":\"Projects/Book.md\""), loaded)
        val listed = WebCore.list(WebNotes.MANY, "", vault, "2026-10-08", "{}")
        assertTrue(listed.contains("idOther#1") && listed.contains("idMain#0"), listed)
        assertFalse(listed.contains("idList"), listed)
        val views = WebViews.build(WebNotes.MANY, "", vault, """{"today":"2026-10-08"}""")
        assertTrue(views.contains("idOther#1"), views)
    }

    @Test
    fun chainsTasksAcrossNotes() {
        val two = notes(WebNotes.Note("a", "A.md", "- [ ] หนึ่ง\n"), WebNotes.Note("b", "B.md", "- [ ] สอง 🆔 abc\n"))
        val order = """[{"key":"a","raw":"- [ ] หนึ่ง","lineIndex":0},{"key":"b","raw":"- [ ] สอง 🆔 abc","lineIndex":0}]"""
        val out = Json { ignoreUnknownKeys = true }.decodeFromString(WebNotes.ManyOut.serializer(), WebNotes.chain(two, order, true))
        assertTrue(out.ok)
        val a = out.texts.getValue("a")
        val id = Regex("""🆔 (\w+)""").find(a)!!.groupValues[1]
        assertTrue(out.texts.getValue("b").contains("⛔ $id"), out.texts.toString())

        val off = Json.encodeToString(listOf(WebNotes.Note("a", "A.md", a), WebNotes.Note("b", "B.md", out.texts.getValue("b"))))
        val order2 = """[{"key":"a","raw":"${a.trim()}","lineIndex":0},{"key":"b","raw":"${out.texts.getValue("b").trim()}","lineIndex":0}]"""
        val undone = Json { ignoreUnknownKeys = true }.decodeFromString(WebNotes.ManyOut.serializer(), WebNotes.chain(off, order2, false))
        assertFalse(undone.texts.getValue("b").contains("⛔"), undone.texts.toString())
    }

    @Test
    fun aChangedLineIsAConflict() {
        val two = notes(WebNotes.Note("a", "A.md", "- [ ] หนึ่ง\n"))
        val out = WebNotes.chain(two, """[{"key":"a","raw":"- [ ] เก่า","lineIndex":0}]""", true)
        assertTrue(out.contains("conflict"), out)
    }
}
