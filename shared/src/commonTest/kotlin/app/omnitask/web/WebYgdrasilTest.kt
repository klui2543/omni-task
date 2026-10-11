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
}
