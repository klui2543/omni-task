package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ProjectNamesTest {
    private val today = LocalDate.parse("2026-10-24")
    private val table = """
        # ชื่อโปรเจกต์

        | ชื่อใน Omni | ชื่อเรียกอื่นที่ Klui หรือ Claude อาจใช้ |
        |---|---|
        | Siriraj/ตารางเวรเภสัช | rxshift, asstrxshift, ระบบแลกเวร, ตารางเวร |
        | Omni | omni, claude, klui, เลขา, แอป Omni |

        ## กติกา
        - ข้อความของ Klui
    """.trimIndent()

    @AfterTest
    fun reset() { ProjectNames.current = ProjectNames.Names() }

    @Test
    fun oldNamesAreReadAsTheProjectsName() {
        val n = ProjectNames.parse(table)
        assertEquals("Omni/assistant", n.canonical("omni/assistant"))
        assertEquals("Siriraj/ตารางเวรเภสัช/wording", n.canonical("rxshift/wording"))
        assertEquals("Omni", n.canonical("OMNI"))
        assertEquals("omni-task", n.canonical("omni-task"))
        assertEquals("Siriraj/peddose", n.canonical("Siriraj/peddose"))
        // A name with a space can never be a tag, so it is not read as one.
        assertEquals("แอป", n.canonical("แอป"))
    }

    @Test
    fun aMoveIsWrittenSoTheSecretaryAndTheAppFollowIt() {
        val after = ProjectNames.record(table, "Omni", "Claude/Omni", today)
        assertTrue("| Claude/Omni | omni, klui, เลขา, แอป Omni |" in after, after)
        assertTrue("- 2026-10-24 #Omni → #Claude/Omni (ย้ายในแอป)" in after)
        assertTrue("## กติกา\n- ข้อความของ Klui" in after)
        val n = ProjectNames.parse(after)
        assertEquals("Claude/Omni/assistant", n.canonical("Omni/assistant"))
        assertEquals("Claude/Omni/voice", n.canonical("omni/voice"))
        // "claude" is the owner's own project now, not another name for Omni.
        assertEquals("claude/secretary", n.canonical("claude/secretary"))
        assertEquals("Claude/Omni", n.canonical("Claude/Omni"))
    }

    @Test
    fun movingBackAndForthEndsWhereTheOwnerLeftIt() {
        val once = ProjectNames.record(table, "Omni/voice", "Omni/assistant/voice", today)
        val back = ProjectNames.record(once, "Omni/assistant/voice", "Omni/voice", today)
        val n = ProjectNames.parse(back)
        assertEquals("Omni/voice", n.canonical("Omni/voice"))
        assertEquals("Omni/voice", n.canonical("Omni/assistant/voice"))
        assertEquals("Omni/voice/mic", n.canonical("omni/voice/mic"))
    }

    @Test
    fun aProjectNotInTheTableGetsARow() {
        val after = ProjectNames.record(table, "peddose", "Siriraj/peddose", today)
        assertTrue("| Siriraj/peddose | peddose |" in after, after)
        assertEquals("Siriraj/peddose/app", ProjectNames.parse(after).canonical("peddose/app"))
        // With no note at all, the table is started.
        assertTrue("| Siriraj/peddose | peddose |" in ProjectNames.record(null, "peddose", "Siriraj/peddose", today))
    }

    @Test
    fun taskLinesAreReadAndRewrittenWithTheNewNames() {
        ProjectNames.current = ProjectNames.parse(table)
        val t = TaskLine.parse("- [ ] ทดสอบ #omni/voice #kluido")!!
        assertEquals(listOf("Omni/voice", "kluido"), t.tags)
        assertEquals("- [ ] ทดสอบ #Claude/Omni/voice #kluido", Branches.renameInLine(t.raw, "Omni", "Claude/Omni"))
        assertEquals("- [ ] ทดสอบ #Omni/focus #kluido", TaskLine.retag(t.raw, "Omni/voice", "Omni/focus"))
        assertEquals("- [ ] ทดสอบ #kluido", TaskLine.removeTag(t.raw, "Omni/voice"))
    }
}
