package app.omnitask.web

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebProjectsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val note = listOf(
        "# งาน",
        "- [ ] ส่งรายงาน #peddose 🆔 rep ⏫ 📅 2026-10-07",
        "- [ ] เตรียมสไลด์ #peddose ⛔ rep 📅 2026-10-09",
        "- [x] สรุป requirement #peddose ✅ 2026-10-03",
        "- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ",
        "- [ ] ลองหน้าเว็บ #peddose/แอป/เว็บ",
        "    - [ ] ซ้อม",
        "- [ ] ทำแอปมือถือ #peddose/แอป/มือถือ",
        "- [ ] ทบทวนเคส #Siriraj 📅 2026-10-08",
        "- [ ] ดู Shogun #watchlist",
        "- [ ] จองตั๋ว #บ้าน #bucketlist",
        "",
    ).joinToString("\n")

    private val bucket = "---\nomni-list: true\nicon: 🏔️\ncategories: เที่ยว, เรียนรู้\ntag: bucketlist\n---\n# Bucket list\n\n- [ ] ดำน้ำ #bucketlist #เที่ยว\n- [x] ปีนภู #bucketlist #เที่ยว ✅ 2026-10-01\n"
    private val bucketPath = "Omni/Bucket list.md"
    private val notes = """[{"key":"n1","path":"$bucketPath","text":${json.encodeToString(kotlinx.serialization.serializer<String>(), bucket)}}]"""

    private fun build(state: String = "{}", n: String = notes): WebProjects.Out =
        json.decodeFromString(WebProjects.build("f", "TaskForge.md", note, n, state, "2026-10-08"))

    @Test
    fun projectsKeepTheOwnersOrderAndStarsOnTop() {
        // Default order: the project with most open work first.
        assertEquals(listOf("peddose", "Siriraj", "บ้าน"), build().projects.map { it.name }.filter { it != "watchlist" })
        val r = build("""{"order":["Siriraj","peddose"],"starred":["บ้าน"]}""")
        assertEquals(listOf("บ้าน", "Siriraj", "peddose"), r.projects.map { it.name }.take(3))
        assertTrue(r.projects.first().starred)
    }

    @Test
    fun listTagsAndCategoriesNeverNameAProject() {
        val r = build()
        assertFalse(r.projects.any { it.name == "bucketlist" || it.name == "เที่ยว" })
        assertTrue(r.ignored.containsAll(listOf("bucketlist", "เที่ยว", "เรียนรู้")))
        // #watchlist names a project until there is a Watch list note to own the tag.
        assertTrue(r.projects.any { it.name == "watchlist" })
        val watch = "---\nomni-list: true\nicon: 🎬\ncategories: หนัง\ntag: watchlist\n---\n# Watch list\n"
        val both = notes.dropLast(1) + """,{"key":"n2","path":"Omni/Watch list.md","text":${json.encodeToString(kotlinx.serialization.serializer<String>(), watch)}}]"""
        assertFalse(build(n = both).projects.any { it.name == "watchlist" })
    }

    @Test
    fun cardsCountDoneOverdueAndBlocked() {
        val p = build().projects.first { it.name == "peddose" }
        assertEquals(6, p.total)
        assertEquals(1, p.done)
        assertEquals(16, p.pct)
        assertEquals(1, p.overdue)
        assertEquals(listOf("f#2"), p.blocked)
        assertEquals("f#1", p.next?.key)
        // Renaming would touch every peddose line, branches included.
        assertEquals(6, p.renameLines)
        assertEquals(1, p.renameFiles)
    }

    @Test
    fun overviewOrdersOpenWorkAndSaysWhyATaskWaits() {
        val p = build("""{"strict":["peddose"]}""").projects.first { it.name == "peddose" }
        // Dated work first, then the rest in the file's order; the done task and the subtask are not in it.
        assertEquals(listOf("f#1", "f#2", "f#4", "f#5", "f#7"), p.order.map { it.key })
        val first = p.order.first()
        assertTrue(first.next)
        assertEquals("plain", first.meta)
        assertEquals("2026-10-07", first.date)
        val second = p.order.first { it.key == "f#2" }
        // Waiting on the first one, but not the task before it in the order, so it names the task.
        assertTrue(second.locked)
        assertEquals(1, second.pos)
        assertEquals("strict", second.meta)
        assertEquals(listOf("f#3"), p.finished)
        // Their own order wins over dates.
        val own = build("""{"taskOrder":{"peddose":["ทำแอปมือถือ","ส่งรายงาน"]}}""").projects.first { it.name == "peddose" }
        assertEquals("f#7", own.order.first().key)
    }

    @Test
    fun withoutStrictAWaitingTaskNamesWhatItWaitsFor() {
        val p = build().projects.first { it.name == "peddose" }
        val waiting = p.order.first { it.key == "f#2" }
        assertEquals("waiting", waiting.meta)
        assertEquals("ส่งรายงาน", waiting.waitingOn)
    }

    @Test
    fun branchesFormATreeEvenForPathsWithoutTasks() {
        val states = """["peddose\tแอป\tACTIVE","peddose\tแอป/แท็บเล็ต\tPARKED"]"""
        val p = build("""{"branches":$states}""").projects.first { it.name == "peddose" }
        assertEquals(listOf("", "แอป", "แอป/มือถือ", "แอป/เว็บ", "แอป/แท็บเล็ต"), p.branches.map { it.path })
        val root = p.branches.first()
        assertEquals(listOf("แอป"), root.children)
        val web = p.branches.first { it.path == "แอป/เว็บ" }
        assertEquals("peddose/แอป/เว็บ", web.tag)
        assertEquals(listOf("f#4", "f#5"), web.own)
        assertEquals(2, web.depth)
        assertEquals("PARKED", p.branches.first { it.path == "แอป/แท็บเล็ต" }.state)
    }

    @Test
    fun parkedBranchTasksStayInTheProjectButAreMarked() {
        val states = """["peddose\tแอป/เว็บ\tPARKED"]"""
        val r = build("""{"branches":$states}""")
        assertEquals(listOf("f#4", "f#5"), r.parked)
        val p = r.projects.first { it.name == "peddose" }
        assertEquals(6, p.total)
        // Parked work is out of the ordered list.
        assertFalse(p.order.any { it.key == "f#4" })
    }

    @Test
    fun listsShowOpenItemsFirstWithTaggedTasksFromTheNote() {
        val l = build().lists.single()
        assertEquals("n1", l.key)
        assertEquals("Bucket list", l.name)
        assertEquals("🏔️", l.emoji)
        assertEquals(listOf("เที่ยว", "เรียนรู้"), l.categories)
        // Two lines in the list note and one task tagged #bucketlist in TaskForge.
        assertEquals(3, l.total)
        assertEquals(1, l.done)
        assertEquals(listOf("f#10", "n1#8", "n1#9"), l.items.map { it.key })
        assertEquals("เที่ยว", l.items.first { it.key == "n1#8" }.sub)
        assertEquals("TaskForge", l.items.first { it.key == "f#10" }.sub)
        assertEquals(2, build().noteTasks.size)
        assertEquals("n1", build().noteTasks.first().key.substringBefore('#'))
    }

    @Test
    fun renamingChangesProjectAndBranchTagsOnly() {
        val r = WebProjects.renameTag(note, "peddose", "peddose-app")
        assertTrue(r.contains("\"changed\":6"), r)
        assertTrue(r.contains("#peddose-app/แอป/เว็บ"), r)
        assertTrue(r.contains("#peddose-app 🆔 rep"), r)
        assertFalse(r.contains("#peddose "), r)
        assertTrue(r.contains("#Siriraj"), r)
        // A branch is renamed by its full tag.
        val b = WebProjects.renameTag(note, "peddose/แอป/เว็บ", "peddose/แอป/ไซต์")
        assertTrue(b.contains("\"changed\":2"), b)
        assertTrue(b.contains("#peddose/แอป/มือถือ"), b)
        assertEquals("peddose-app", WebProjects.cleanProjectName("  #peddose app "))
    }

    @Test
    fun branchStatesChangeThroughOneEntryPoint() {
        fun change(states: List<String>, op: String): WebProjects.BranchResult =
            json.decodeFromString(WebProjects.branchChange("f", "TaskForge.md", note, json.encodeToString(kotlinx.serialization.serializer<List<String>>(), states), op))

        val set = change(emptyList(), """{"op":"set","project":"peddose","path":"แอป/เว็บ","value":"TRYING"}""")
        assertEquals(listOf("peddose\tแอป/เว็บ\tTRYING"), set.states)

        // Choosing one parks the siblings still being tried.
        val trying = listOf("peddose\tแอป/เว็บ\tTRYING", "peddose\tแอป/มือถือ\tTRYING")
        val chosen = change(trying, """{"op":"choose","project":"peddose","path":"แอป/เว็บ"}""")
        assertTrue("peddose\tแอป/เว็บ\tCHOSEN" in chosen.states)
        assertTrue("peddose\tแอป/มือถือ\tPARKED" in chosen.states)

        // A new empty branch is worked on under the project and tried deeper down.
        assertTrue("peddose\tทุน\tACTIVE" in change(emptyList(), """{"op":"add","project":"peddose","path":"","value":"ทุน"}""").states)
        val deep = change(emptyList(), """{"op":"add","project":"peddose","path":"แอป","value":"แท็บ เล็ต"}""")
        assertTrue("peddose\tแอป/แท็บ-เล็ต\tTRYING" in deep.states)
        assertEquals("แอป/แท็บ-เล็ต", deep.path)

        // Renaming says which tags to rewrite, and the states under it move along.
        val renamed = change(listOf("peddose\tแอป/เว็บ\tTRYING"), """{"op":"rename","project":"peddose","path":"แอป/เว็บ","value":"ไซต์"}""")
        assertEquals("peddose/แอป/เว็บ", renamed.oldTag)
        assertEquals("peddose/แอป/ไซต์", renamed.newTag)
        assertEquals(listOf("peddose\tแอป/ไซต์\tTRYING"), renamed.states)

        // A branch with tasks cannot be deleted; an empty one can.
        assertEquals("hasTasks", change(emptyList(), """{"op":"delete","project":"peddose","path":"แอป/เว็บ"}""").error)
        val empty = change(listOf("peddose\tทุน\tACTIVE"), """{"op":"delete","project":"peddose","path":"ทุน"}""")
        assertTrue(empty.ok)
        assertEquals(emptyList(), empty.states)

        // A renamed project takes its branch states along.
        val moved = change(listOf("peddose\tแอป\tACTIVE", "x\ty\tACTIVE"), """{"op":"moveProject","project":"peddose","value":"peddose-app"}""")
        assertEquals(listOf("peddose-app\tแอป\tACTIVE", "x\ty\tACTIVE"), moved.states)
    }

    @Test
    fun doInOrderWritesIdsAndWaitsAndTakesThemBack() {
        val ordered = """[{"raw":"- [ ] เตรียมสไลด์ #peddose ⛔ rep 📅 2026-10-09","lineIndex":2},{"raw":"- [ ] ส่งรายงาน #peddose 🆔 rep ⏫ 📅 2026-10-07","lineIndex":1}]"""
        val on = json.decodeFromString<WebProjects.EditOut>(WebProjects.chain(note, ordered, true))
        assertTrue(on.ok)
        val lines = on.text!!.split("\n")
        // The first task waits for nothing; the second waits for the first one's new id.
        val firstId = Regex("🆔 (\\S+)").find(lines[2])!!.groupValues[1]
        assertFalse(lines[2].contains("⛔"), lines[2])
        assertTrue(lines[1].contains("⛔ $firstId"), lines[1])
        assertTrue(lines[1].contains("🆔 rep"), lines[1])
        // Off: the waits inside the project go, the ids stay.
        val raws = lines.slice(1..2).mapIndexed { i, raw -> """{"raw":${json.encodeToString(kotlinx.serialization.serializer<String>(), raw)},"lineIndex":${i + 1}}""" }
        val off = json.decodeFromString<WebProjects.EditOut>(WebProjects.chain(on.text!!, "[" + raws.joinToString(",") + "]", false))
        assertFalse(off.text!!.contains("⛔"), off.text)
        // A line somebody changed since is a conflict and nothing is written.
        val stale = """[{"raw":"- [ ] ส่งรายงานเก่า","lineIndex":1}]"""
        assertTrue(WebProjects.chain(note, stale, true).contains("\"error\":\"conflict\""))
    }

    @Test
    fun aNewListIsANoteWithAHeader() {
        val r = json.decodeFromString<WebProjects.EditOut>(WebProjects.createList("หนังสือ/ที่อยากอ่าน", "📚", """["นิยาย"," ธุรกิจ",""]"""))
        assertTrue(r.ok)
        assertEquals("📁 Folder/หลังบ้าน/Omni/หนังสือ ที่อยากอ่าน.md", r.path)
        assertTrue(r.text!!.startsWith("---\nomni-list: true\nicon: 📚\ncategories: นิยาย, ธุรกิจ\ntag: "), r.text)
        assertFalse(json.decodeFromString<WebProjects.EditOut>(WebProjects.createList("  ", "📚", "[]")).ok)
    }

    @Test
    fun itemsAreAddedWithTheListTagCategoryAndDay() {
        val r = json.decodeFromString<WebProjects.EditOut>(WebProjects.addListItem(bucket, bucketPath, " เรียนทำขนมปัง ", "เรียนรู้", "2026-10-08"))
        assertTrue(r.ok)
        assertTrue(r.text!!.contains("- [ ] เรียนทำขนมปัง #bucketlist #เรียนรู้ ➕ 2026-10-08"), r.text)
        assertTrue(r.text!!.startsWith(bucket.trimEnd()))
        assertFalse(json.decodeFromString<WebProjects.EditOut>(WebProjects.addListItem(bucket, bucketPath, "  ", null, "2026-10-08")).ok)
        assertFalse(json.decodeFromString<WebProjects.EditOut>(WebProjects.addListItem("# not a list", bucketPath, "x", null, "2026-10-08")).ok)
    }

    @Test
    fun theHeaderChangesButTheItemsStay() {
        val r = json.decodeFromString<WebProjects.EditOut>(WebProjects.updateList(bucket, bucketPath, "✈️", """["เที่ยว","เรียนรู้","ชีวิต"]"""))
        assertTrue(r.text!!.contains("icon: ✈️\ncategories: เที่ยว, เรียนรู้, ชีวิต\ntag: bucketlist"), r.text)
        assertTrue(r.text!!.contains("- [ ] ดำน้ำ #bucketlist #เที่ยว"), r.text)
        assertEquals(1, Regex("# Bucket list").findAll(r.text!!).count())
    }

    @Test
    fun pullingTasksInTagsThemWhereTheyAre() {
        val tasks = """[{"raw":"- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ","lineIndex":4},{"raw":"- [ ] หายไปแล้ว","lineIndex":99}]"""
        val r = json.decodeFromString<WebProjects.EditOut>(WebProjects.includeInList(note, tasks, "bucketlist", "เที่ยว"))
        assertEquals(1, r.changed)
        assertTrue(r.text!!.contains("- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ #bucketlist #เที่ยว"), r.text)
        assertEquals(note.split("\n").size, r.text!!.split("\n").size)
    }

    @Test
    fun branchTasksAreReadLikeQuickAddAndTagged() {
        val r = json.decodeFromString<WebProjects.EditOut>(WebProjects.addTagged(note, "peddose/แอป", "เขียนสเปก พรุ่งนี้", "2026-10-08"))
        assertTrue(r.text!!.contains("เขียนสเปก") && r.text!!.contains("#peddose/แอป") && r.text!!.contains("2026-10-09"), r.text)
        assertNull(json.decodeFromString<WebProjects.EditOut>(WebProjects.addTagged(note, "x", " ", "2026-10-08")).text)
    }

    @Test
    fun startersAndEmojiAreOffered() {
        assertTrue(WebProjects.starters().contains("📁 Folder/หลังบ้าน/Omni/Bucket list.md") && WebProjects.starters().contains("📁 Folder/หลังบ้าน/Omni/Watch list.md"))
        val themes = json.decodeFromString<List<WebProjects.IconTheme>>(WebProjects.iconThemes())
        assertEquals(4, themes.size)
        assertNotNull(themes.first().emoji.firstOrNull())
    }
}
