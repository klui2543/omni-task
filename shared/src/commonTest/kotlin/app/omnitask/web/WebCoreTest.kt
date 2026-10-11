package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WebCoreTest {

    private val note = "# งาน\n- [ ] ส่งรายงาน 📅 2026-10-09\n- [ ] ซื้อนม 🔁 every day 📅 2026-10-08\n"

    @Test
    fun ticksATaskAndKeepsTheRest() {
        val r = WebCore.toggle(note, "- [ ] ส่งรายงาน 📅 2026-10-09", 1, "2026-10-08")
        assertTrue(r.contains("\"ok\":true"))
        assertTrue(r.contains("- [x] ส่งรายงาน 📅 2026-10-09 ✅ 2026-10-08"))
    }

    @Test
    fun repeatingTaskMovesOn() {
        val r = WebCore.toggle(note, "- [ ] ซื้อนม 🔁 every day 📅 2026-10-08", 2, "2026-10-08")
        assertTrue(r.contains("- [ ] ซื้อนม 🔁 every day 📅 2026-10-09"), r)
    }

    @Test
    fun aMissedRoundAlsoListsTodaysAndTickingItSkipsPastToday() {
        val missed = "- [ ] ซื้อนม 🔁 every day 📅 2026-10-08"
        val r = WebCore.loadTasks("f", "TaskForge.md", "# งาน\n$missed\n", "2026-10-09")
        assertTrue(r.contains("\"key\":\"f#1\"") && r.contains("\"key\":\"f#1@today\""), r)
        assertTrue(r.contains("\"todayCopy\":true") && r.contains("\"due\":\"2026-10-09\""), r)
        val ticked = WebCore.toggle("# งาน\n$missed\n", missed, 1, "2026-10-09", todayCopy = true)
        assertTrue(ticked.contains("- [ ] ซื้อนม 🔁 every day 📅 2026-10-10"), ticked)
    }

    @Test
    fun changedLineIsAConflictNotAnOverwrite() {
        val r = WebCore.toggle(note, "- [ ] ส่งรายงานเก่า", 1, "2026-10-08")
        assertTrue(r.contains("\"ok\":false") && r.contains("\"error\":\"conflict\""), r)
    }

    @Test
    fun quickAddAppendsAFormattedLine() {
        val r = WebCore.addTask(note, "โทรหาแม่ พรุ่งนี้ 9:00", "2026-10-08")
        assertTrue(r.contains("โทรหาแม่"), r)
        assertTrue(r.contains("2026-10-09"), r)
    }

    @Test
    fun loadsTasksAsJson() {
        val r = WebCore.loadTasks("f", "TaskForge.md", note, "2026-10-08")
        assertTrue(r.contains("\"title\":\"ส่งรายงาน\""), r)
        assertTrue(r.contains("\"bucket\":\"TODAY\""), r)
    }

    @Test
    fun rowsCarryTheirPreviewRepeatWordsAndCounts() {
        val note = "- [ ] ประชุม 🔁 every day 📅 2026-10-08\n    - ห้อง 3\n    - ![[Omni-1.webp]]\n    - [[บันทึกประชุม]]"
        val r = WebCore.loadTasks("f", "TaskForge.md", note, "2026-10-08")
        assertTrue(r.contains("\"preview\":\"ห้อง 3\""), r)
        assertTrue(r.contains("\"repeatText\":\"ทุกวัน\""), r)
        assertTrue(r.contains("\"attachments\":1"), r)
        assertTrue(r.contains("\"links\":1"), r)
    }

    @Test
    fun listGroupsByDateWithSubtaskProgress() {
        val note = "- [ ] ส่งรายงาน 📅 2026-10-07\n- [ ] สไลด์ 📅 2026-10-08\n    - [x] โครง ✅ 2026-10-07\n    - [ ] รูป\n- [ ] อ่านหนังสือ"
        val r = WebCore.list("f", "TaskForge.md", note, "2026-10-08", "{}")
        assertTrue(r.contains("{\"label\":\"เลยกำหนด\",\"tone\":\"ALERT\",\"keys\":[\"f#0\"]}"), r)
        assertTrue(r.contains("{\"label\":\"วันนี้\",\"tone\":\"ACCENT\",\"keys\":[\"f#1\"]}"), r)
        assertTrue(r.contains("\"f#1\":{\"done\":1,\"total\":2}"), r)
        val searched = WebCore.list("f", "TaskForge.md", note, "2026-10-08", "{\"text\":\"อ่าน\"}")
        assertTrue(searched.contains("\"keys\":[\"f#4\"]") && !searched.contains("f#0"), searched)
    }

    @Test
    fun parentCanCloseItsOpenSubtasksInOneWrite() {
        val text = "- [ ] เตรียมสไลด์\n    - [ ] ทำโครง\n    - [x] หาข้อมูล ✅ 2026-10-07\n- [ ] งานอื่น"
        val both = WebCore.toggle(text, "- [ ] เตรียมสไลด์", 0, "2026-10-08", withSubtasks = true)
        assertTrue(both.contains("- [x] เตรียมสไลด์ ✅ 2026-10-08\\n    - [x] ทำโครง ✅ 2026-10-08\\n    - [x] หาข้อมูล ✅ 2026-10-07\\n- [ ] งานอื่น"), both)
        val onlyParent = WebCore.toggle(text, "- [ ] เตรียมสไลด์", 0, "2026-10-08")
        assertTrue(onlyParent.contains("    - [ ] ทำโครง"), onlyParent)
    }

    private fun result(json: String) = Json.decodeFromString<WebCore.EditResult>(json)

    @Test
    fun editsKeepTaskForgeTokenOrder() {
        var text = note
        fun apply(op: String) {
            val r = result(WebCore.editTask(text, text.split("\n")[1], 1, "2026-10-08", op))
            assertTrue(r.ok, r.toString())
            text = r.text!!
        }
        apply("""{"op":"date","field":"SCHEDULED","value":"2026-10-08"}""")
        apply("""{"op":"priority","value":"HIGH"}""")
        apply("""{"op":"recurrence","value":"every week"}""")
        apply("""{"op":"reminder","value":"09:30","on":"DUE"}""")
        apply("""{"op":"addTag","value":"งาน"}""")
        assertEquals("- [ ] ส่งรายงาน #งาน #remind-at-due ⏰ 09:30 ⏫ 🔁 every week ⏳ 2026-10-08 📅 2026-10-09", text.split("\n")[1])
        apply("""{"op":"reminder","value":null,"on":"DUE"}""")
        apply("""{"op":"date","field":"SCHEDULED","value":null}""")
        assertEquals("- [ ] ส่งรายงาน #งาน ⏫ 🔁 every week 📅 2026-10-09", text.split("\n")[1])
    }

    @Test
    fun unreadableRepeatIsRefused() {
        val r = result(WebCore.editTask(note, "- [ ] ส่งรายงาน 📅 2026-10-09", 1, "2026-10-08", """{"op":"recurrence","value":"sometimes"}"""))
        assertEquals("rule", r.error)
    }

    @Test
    fun subtaskGoesUnderItsParent() {
        val r = result(WebCore.editTask(note, "- [ ] ส่งรายงาน 📅 2026-10-09", 1, "2026-10-08", """{"op":"subtask","value":"หาข้อมูล"}"""))
        assertTrue(r.text!!.contains("📅 2026-10-09\n    - [ ] หาข้อมูล"), r.text)
    }

    @Test
    fun linksImagesAndADescriptionMovedToANoteSitUnderTheTask() {
        var text = "- [ ] สไลด์ 📅 2026-10-09\n    - เนื้อหายาว\n    - [ ] โครง\n    - [ ] รูป\n- [ ] อื่น\n"
        fun apply(op: String): WebCore.EditResult {
            val r = result(WebCore.editTask(text, text.split("\n")[0], 0, "2026-10-08", op))
            if (r.ok) text = r.text!!
            return r
        }
        apply("""{"op":"link","value":"ประชุมทีม"}""")
        apply("""{"op":"image","value":"Omni-2026-10-08-090000.webp"}""")
        assertEquals("- [ ] สไลด์ 📅 2026-10-09\n    - เนื้อหายาว\n    - [[ประชุมทีม]]\n    - ![[Omni-2026-10-08-090000.webp]]\n    - [ ] โครง", text.lines().take(5).joinToString("\n"))
        // Linked once only.
        apply("""{"op":"link","value":"ประชุมทีม"}""")
        assertEquals(1, Regex("ประชุมทีม").findAll(text).count())
        val loaded = WebCore.loadTasks("f", "n.md", text, "2026-10-08")
        assertTrue(loaded.contains("\"linkNames\":[\"ประชุมทีม\"]") && loaded.contains("\"attachmentNames\":[\"Omni-2026-10-08-090000.webp\"]"), loaded)

        apply("""{"op":"unimage","value":"Omni-2026-10-08-090000.webp"}""")
        apply("""{"op":"unlink","value":"ประชุมทีม"}""")
        apply("""{"op":"moveDescription","value":"สไลด์"}""")
        assertEquals("- [ ] สไลด์ 📅 2026-10-09\n    - [[สไลด์]]\n    - [ ] โครง\n    - [ ] รูป\n- [ ] อื่น\n", text)

        // Subtasks in a new order; a list that no longer matches is refused.
        val order = Json.encodeToString(listOf("    - [ ] รูป", "    - [ ] โครง"))
        apply(Json.encodeToString(mapOf("op" to "order", "value" to order)))
        assertEquals("- [ ] สไลด์ 📅 2026-10-09\n    - [[สไลด์]]\n    - [ ] รูป\n    - [ ] โครง\n- [ ] อื่น\n", text)
        assertEquals("Task", app.omnitask.model.NoteLinks.nameFor("##"))
        assertEquals("a b c", app.omnitask.model.NoteLinks.nameFor("a/b: [c]"))
    }

    @Test
    fun aTaskCarriesItsIdAndWhatItWaitsOn() {
        val loaded = WebCore.loadTasks("f", "n.md", "- [ ] ก 🆔 aa\n- [ ] ข ⛔ aa\n", "2026-10-08")
        assertTrue(loaded.contains("\"id\":\"aa\"") && loaded.contains("\"dependsOn\":[\"aa\"]"), loaded)
    }

    @Test
    fun theListCanBeNarrowedToNotesAndToListNotesItems() {
        val bucket = "---\nomni-list: true\nicon: target\ncategories: เที่ยว\ntag: bucket\n---\n# Bucket list\n\n- [ ] ไปญี่ปุ่น #bucket\n"
        val notes = Json.encodeToString(
            listOf(
                WebNotes.Note("t", "Omni note.md", "- [ ] ส่งรายงาน\n- [ ] ดูหนัง #bucket\n"),
                WebNotes.Note("p", "Projects/วิจัย.md", "- [ ] เขียน paper\n"),
                WebNotes.Note("b", "Omni/Bucket list.md", bucket),
            ),
        )
        fun keys(q: String) = Regex("\"keys\":\\[([^\\]]*)\\]").findAll(WebCore.list(WebNotes.MANY, "", notes, "2026-10-08", q)).flatMap { m ->
            Regex("\"([^\"]+)\"").findAll(m.groupValues[1]).map { it.groupValues[1] }
        }.toSet()
        // A list's items stay out until the list is chosen.
        assertEquals(setOf("t#0", "t#1", "p#0"), keys("""{"groupBy":"NONE"}"""))
        assertEquals(setOf("p#0"), keys("""{"groupBy":"NONE","notes":["วิจัย"]}"""))
        assertEquals(setOf("b#8"), keys("""{"groupBy":"NONE","lists":["Bucket list"]}"""))
        val items = WebCore.loadListItems(WebNotes.MANY, notes, "2026-10-08")
        assertTrue(items.contains("\"list\":\"Bucket list\""), items)
        // A deleted list's tag comes off the lines that carry it.
        val dropped = WebProjects.dropTag("- [ ] ดูหนัง #bucket #เที่ยว\n- [ ] อื่น\n", "bucket")
        assertTrue(dropped.contains(Json.encodeToString("- [ ] ดูหนัง #เที่ยว\n- [ ] อื่น\n")) && dropped.contains("\"changed\":1"), dropped)
    }

    @Test
    fun cutRestoreAndArchive() {
        val withSub = "- [ ] สไลด์\n    - [ ] โครง\n- [ ] อื่น\n"
        val cut = result(WebCore.cut(withSub, "- [ ] สไลด์", 0))
        assertEquals("- [ ] อื่น\n", cut.text)
        assertEquals(listOf("- [ ] สไลด์", "    - [ ] โครง"), cut.cutLines)
        val lines = Json.encodeToString(cut.cutLines!!)
        assertEquals(withSub, result(WebCore.restore(cut.text!!, cut.cutIndex!!, lines)).text)
        val archive = WebCore.archiveAppend("", lines, "2026-10-08")
        assertTrue(archive.contains("## 2026-10\n\n- [ ] สไลด์\n    - [ ] โครง"), archive)
        assertTrue(WebCore.archiveRemove(archive, lines)!!.contains("สไลด์").not())
    }
}
