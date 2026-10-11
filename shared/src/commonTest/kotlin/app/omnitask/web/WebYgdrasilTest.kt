package app.omnitask.web

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebYgdrasilTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val note = listOf(
        "- [ ] ส่งรายงาน #peddose 🆔 rep ⏫ 📅 2026-10-07",
        "- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ",
        "- [ ] ทบทวนเคส #Siriraj ⛔ rep 📅 2026-10-12",
        "- [ ] จองตั๋ว #bucketlist",
        "",
    ).joinToString("\n")

    private val bucket = "---\nomni-list: true\nicon: 🏔️\ntag: bucketlist\n---\n# Bucket list\n"
    private val notes = """[{"key":"n1","path":"Omni/Bucket list.md","text":${json.encodeToString(kotlinx.serialization.serializer<String>(), bucket)}}]"""

    private fun build(filter: String = "{}", folded: String = "[]", state: String = "{}"): WebYgdrasil.Out =
        json.decodeFromString(WebYgdrasil.build("f", "Omni note.md", note, notes, state, filter, folded, "2026-10-11"))

    @Test
    fun everyProjectShowsInOneTreeWithItsWaitsTied() {
        val out = build(state = """{"starred":["Siriraj"]}""")
        // The list's tag is not a project.
        assertEquals(2, out.worlds)
        assertEquals(listOf("Siriraj", "peddose", "peddose/แอป", "peddose/แอป/เว็บ"), out.tags)
        assertEquals(3, out.matched)
        assertEquals(1, out.links)
        assertEquals(1, out.edges.count { it.kind == "LINK_ACROSS" })
        // A wait says which tasks it ties: the report (f#0) waited for by the case review (f#2).
        assertEquals("f#0" to "f#2", out.edges.single { it.kind == "LINK_ACROSS" }.let { it.blocker to it.waiting })
        assertTrue(out.edges.all { it.d.startsWith("M") && " C" in it.d })
        assertEquals(setOf("f#0", "f#1", "f#2"), out.spots.mapNotNull { it.key }.toSet())
        assertEquals(1, out.spots.count { it.kind == "TRUNK" })
    }

    @Test
    fun theFilterAndFoldingComeFromThePage() {
        val overdue = build(filter = """{"dates":"OVERDUE"}""")
        assertEquals(1, overdue.worlds)
        assertEquals(setOf("f#0"), overdue.spots.mapNotNull { it.key }.toSet())

        val folded = build(folded = """["peddose"]""")
        val world = folded.spots.single { it.tag == "peddose" && it.kind == "LIMB" }
        assertTrue(world.folded)
        assertEquals(2, world.count)
        assertEquals(setOf("f#2"), folded.spots.mapNotNull { it.key }.toSet())
    }

    private fun linkOut(s: String) = json.decodeFromString<WebYgdrasil.LinkOut>(s)
    private fun ref(text: String, line: Int) = """{"raw":${json.encodeToString(kotlinx.serialization.serializer<String>(), text.lines()[line])},"lineIndex":$line}"""

    @Test
    fun anArrowBetweenTwoTasksBecomesAWaitAndBackAgain() {
        // ทบทวนเคส (line 2) waits for ทำหน้าเว็บ (line 1), which has no id yet.
        val linked = linkOut(WebYgdrasil.link(note, ref(note, 1), ref(note, 2)))
        assertTrue(linked.ok)
        val id = linked.id!!
        val lines = linked.text!!.lines()
        assertEquals("- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ 🆔 $id", lines[1])
        assertTrue(lines[2].contains("⛔ rep,$id"), lines[2])
        // The other way round now closes a loop, and is refused.
        assertEquals("cycle", linkOut(WebYgdrasil.link(linked.text!!, ref(linked.text!!, 2), ref(linked.text!!, 1))).error)
        assertEquals("self", linkOut(WebYgdrasil.link(note, ref(note, 1), ref(note, 1))).error)
        // Taken away, the wait goes and the id stays.
        val back = linkOut(WebYgdrasil.unlink(linked.text!!, ref(linked.text!!, 1), ref(linked.text!!, 2)))
        assertTrue(back.ok)
        assertEquals(listOf("rep"), app.omnitask.data.TaskLine.parse(back.text!!.lines()[2])!!.dependsOn)
        assertTrue(back.text!!.lines()[1].contains("🆔 $id"))
    }
}
