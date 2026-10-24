package app.omnitask.model

import app.omnitask.data.VaultText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class VoiceNotesTest {

    private val today = LocalDate.parse("2026-10-22")

    @Test
    fun parsesTheTaskList() {
        val got = VoiceNotes.parse("""{"tasks":["ซื้อแบตโน้ตบุ๊ก","  พรุ่งนี้ถามพี่ปันปันเรื่องเวร  ","","ซื้อแบตโน้ตบุ๊ก"]}""")
        assertEquals(listOf("ซื้อแบตโน้ตบุ๊ก", "พรุ่งนี้ถามพี่ปันปันเรื่องเวร"), got)
    }

    @Test
    fun parsesABareArrayAndCodeFences() {
        assertEquals(listOf("โทรหาแม่"), VoiceNotes.parse("```json\n[\"โทรหาแม่\"]\n```"))
        assertEquals(emptyList(), VoiceNotes.parse("""{"tasks":[]}"""))
    }

    @Test
    fun cleanKeepsWordsButDropsWhatTheLineWouldRead() {
        assertEquals("ส่งรายงาน พรุ่งนี้ 9:00 รอ", VoiceNotes.clean("- ส่งรายงาน 📅 พรุ่งนี้ 9:00 #รอ"))
        assertEquals("ตรวจภาษา, รอบสอง", VoiceNotes.clean("ตรวจภาษา — รอบสอง"))
        assertEquals("สองบรรทัด รวมกัน", VoiceNotes.clean("สองบรรทัด\nรวมกัน"))
    }

    @Test
    fun theLineHasOnlyTheDayItWasMade() {
        val line = VoiceNotes.line("พรุ่งนี้ถามพี่ปันปันเรื่องเวร", today)
        assertEquals("- [ ] พรุ่งนี้ถามพี่ปันปันเรื่องเวร ➕ 2026-10-22", line)
        val task = VaultText.parseFile("f", "Omni note.md", line).single()
        assertEquals("พรุ่งนี้ถามพี่ปันปันเรื่องเวร", task.title)
        assertNull(task.due)
        assertNull(task.scheduled)
        assertTrue(task.tags.isEmpty())
        assertEquals(today, task.created)
    }

    @Test
    fun similarCatchesTheSameTaskSaidAnotherWay() {
        assertTrue(VoiceNotes.similar("เปลี่ยนแบตโน้ตบุ๊ก", "เปลี่ยนแบตโน๊ตบุ๊ค"))
        assertTrue(VoiceNotes.similar("แบตโน้ตบุ๊ก", "เปลี่ยนแบตโน้ตบุ๊ก"))
        assertTrue(VoiceNotes.similar("Call Mom", "call mom"))
        assertFalse(VoiceNotes.similar("ซื้อนม", "ตรวจภาษา 25 ข้อ"))
        assertFalse(VoiceNotes.similar("", "อะไรก็ได้"))
    }

    @Test
    fun duplicateOnlyAmongOpenTopLevelTasks() {
        val tasks = VaultText.parseFile(
            "f", "Omni note.md",
            listOf(
                "- [ ] เปลี่ยนแบตโน๊ตบุ๊ค #สักวัน ➕ 2026-10-19",
                "- [x] ซื้อนม ✅ 2026-10-20",
                "- [ ] งานใหญ่",
                "    - [ ] ตรวจขนาดจอ",
            ).joinToString("\n"),
        )
        assertNotNull(VoiceNotes.duplicateOf("เปลี่ยนแบตโน้ตบุ๊ก", tasks))
        assertNull(VoiceNotes.duplicateOf("ซื้อนม", tasks))
        assertNull(VoiceNotes.duplicateOf("ตรวจขนาดจอ", tasks))
    }

    @Test
    fun requestCarriesTheAudio() {
        val body = VoiceNotes.request("QUJD", "audio/mp4")
        assertTrue("\"mimeType\":\"audio/mp4\"" in body)
        assertTrue("\"data\":\"QUJD\"" in body)
        assertTrue("responseSchema" in body)
    }

    @Test
    fun parsesAPieceWithItsWords() {
        val (text, tasks) = VoiceNotes.parsePiece("""{"text":"  อีกอย่างคือ   จองห้องประชุม ","tasks":["ซื้อแบตโน้ตบุ๊ก","จองห้องประชุม"]}""")
        assertEquals("อีกอย่างคือ จองห้องประชุม", text)
        assertEquals(listOf("ซื้อแบตโน้ตบุ๊ก", "จองห้องประชุม"), tasks)
    }

    @Test
    fun mergeAddsNewOnesAsFreshAndKeepsOrder() {
        val first = VoiceNotes.merge(emptyList(), listOf("ซื้อแบตโน้ตบุ๊ก"), emptyList(), 1)
        assertEquals(listOf(VoiceNotes.Live(1, "ซื้อแบตโน้ตบุ๊ก", fresh = true)), first)
        val next = VoiceNotes.merge(first, listOf("ซื้อแบตโน้ตบุ๊ก", "จองห้องประชุม"), emptyList(), 2)
        assertEquals(listOf("ซื้อแบตโน้ตบุ๊ก", "จองห้องประชุม"), next.map { it.text })
        assertEquals(listOf(false, true), next.map { it.fresh })
        assertEquals(listOf(1, 2), next.map { it.id })
    }

    @Test
    fun mergeTakesGeminisNewerWordsUnlessTheOwnerEdited() {
        val list = listOf(
            VoiceNotes.Live(1, "ลองสูตรโดสยาเด็ก"),
            VoiceNotes.Live(2, "ถามพี่ปันปันเรื่องเวรเดือนหน้า", heard = "ถามพี่ปันเรื่องเวร", locked = true),
        )
        val next = VoiceNotes.merge(list, listOf("ลองสูตรโดสยาเด็กกับเคสจริง 3 เคส", "ถามพี่ปันเรื่องเวร"), emptyList(), 3)
        assertEquals(listOf("ลองสูตรโดสยาเด็กกับเคสจริง 3 เคส", "ถามพี่ปันปันเรื่องเวรเดือนหน้า"), next.map { it.text })
    }

    @Test
    fun mergeNeverBringsBackWhatTheOwnerRemoved() {
        val next = VoiceNotes.merge(emptyList(), listOf("ซื้อนม", "จองห้องประชุม"), listOf("ซื้อนม"), 1)
        assertEquals(listOf("จองห้องประชุม"), next.map { it.text })
    }

    @Test
    fun mergeDropsWhatGeminiNoLongerLists() {
        val list = listOf(VoiceNotes.Live(1, "ซื้อนม"), VoiceNotes.Live(2, "ล้างรถ", locked = true))
        val next = VoiceNotes.merge(list, listOf("จองห้องประชุม"), emptyList(), 3)
        assertEquals(listOf("ล้างรถ", "จองห้องประชุม"), next.map { it.text })
    }

    @Test
    fun pieceRequestCarriesTheListAndWhatWasRemoved() {
        val body = VoiceNotes.pieceRequest("QUJD", "audio/wav", "ข้อความเดิม", listOf("ซื้อนม"), listOf("ล้างรถ"))
        assertTrue("audio/wav" in body && "ซื้อนม" in body && "ล้างรถ" in body && "ข้อความเดิม" in body)
        val tidy = VoiceNotes.tidyRequest("ทั้งหมด", listOf("ซื้อนม"), emptyList())
        assertTrue("ทั้งหมด" in tidy && "inlineData" !in tidy)
    }
}
