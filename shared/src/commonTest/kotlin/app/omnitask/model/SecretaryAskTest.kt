package app.omnitask.model

import app.omnitask.data.VaultText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

class SecretaryAskTest {

    private val today = LocalDate.parse("2026-10-26")

    private val note = listOf(
        "- [/] ปรับระบบใหม่เป็นระบบ \"ป้าย\" #Siriraj/ตารางเวรเภสัช #รอ ⏫ ➕ 2026-10-04 📅 2026-10-14",
        "- [ ] จองตั๋ว ➕ 2026-10-10",
        "    - ค้าง 2026-10-26 รอคนอื่น: รอพี่เอส่งวัน",
        "- [x] ส่งรายงาน ➕ 2026-10-01 ✅ 2026-10-20",
    ).joinToString("\n")

    private val book = listOf(
        "# งานค้าง",
        "- [ ] ปิดปลั๊กอิน 10x-Team #Siriraj/ตารางเวรเภสัช/rules #kluido ➕ 2026-10-25",
        "    - ที่: หน้าตั้งค่าปลั๊กอิน",
    ).joinToString("\n")

    private fun tasks() =
        VaultText.parseFile("n", "📁 Folder/หลังบ้าน/Omni/Omni note.md", note) +
            VaultText.parseFile("b", "📁 Folder/หลังบ้าน/Claude/Claude log.md", book)

    private val file = listOf(
        "---",
        "format: secretary-ask/1",
        "date: 2026-10-26",
        "---",
        "# เลขาถาม",
        "- ปรับระบบใหม่เป็นระบบ \"ป้าย\" #Siriraj/ตารางเวรเภสัช #รอ ⏫ ➕ 2026-10-04 📅 2026-10-14",
        "    - ไฟล์: 📁 Folder/หลังบ้าน/Omni/Omni note.md",
        "    - เหตุ: เลยกำหนดมา 12 วัน",
        "- ปิดปลั๊กอิน 10x-Team #Siriraj/ตารางเวรเภสัช/rules #kluido",
        "    - เหตุ: Klui เลือกปิดไว้ตั้งแต่ 25 ต.ค.",
        "- จองตั๋ว",
        "    - เหตุ: ค้างมา 16 วัน",
        "- ส่งรายงาน",
        "    - เหตุ: ถามซ้ำงานที่เสร็จแล้ว",
        "- งานที่ไม่มีอยู่จริง",
        "    - เหตุ: หาไม่เจอ",
        "- [ ] กล่องติ๊กไม่ใช่คำถาม",
    ).joinToString("\n")

    @Test
    fun readsTheDueDateTheSecretaryProposes() {
        val text = listOf(
            "# เลขาถาม",
            "- Flow น้ำเกลือ #Siriraj #รอ ⏫ ➕ 2026-10-02",
            "    - ไฟล์: 📁 Folder/หลังบ้าน/Omni/Omni note.md",
            "    - เหตุ: ในบันทึกงานมีนัดคุยกับทีมวันพฤหัส 5 พ.ย. งานนี้น่าจะต้องพร้อมก่อนวันนั้น",
            "    - วันส่ง: 2026-11-04",
            "- จองตั๋ว",
            "    - วันส่ง: ยังไม่รู้",
        ).joinToString("\n")
        val asks = SecretaryAsk.parse(text)
        assertEquals(LocalDate.parse("2026-11-04"), asks[0].due)
        assertEquals("ในบันทึกงานมีนัดคุยกับทีมวันพฤหัส 5 พ.ย. งานนี้น่าจะต้องพร้อมก่อนวันนั้น", asks[0].why)
        assertEquals(null, asks[1].due, "a line with no date proposes nothing")
        // Questions without the line read as before.
        assertEquals(null, SecretaryAsk.parse(file)[0].due)
    }

    @Test
    fun readsEachQuestionWithItsFileAndReason() {
        val asks = SecretaryAsk.parse(file)
        assertEquals(5, asks.size)
        assertEquals(SecretaryAsk.Ask("ปรับระบบใหม่เป็นระบบ \"ป้าย\"", "📁 Folder/หลังบ้าน/Omni/Omni note.md", "เลยกำหนดมา 12 วัน"), asks[0])
        assertEquals("ปิดปลั๊กอิน 10x-Team", asks[1].title)
        assertEquals(null, asks[1].file)
        assertTrue(asks.none { it.title.contains("กล่องติ๊ก") })
    }

    @Test
    fun keepsOnlyOpenTasksNotAnsweredToday() {
        val open = SecretaryAsk.open(SecretaryAsk.parse(file), tasks(), today)
        // จองตั๋ว was answered today (a ค้าง line dated today), ส่งรายงาน is done, the last one has no task.
        assertEquals(listOf("ปรับระบบใหม่เป็นระบบ \"ป้าย\"", "ปิดปลั๊กอิน 10x-Team"), open.map { it.task.title })
        assertEquals("เลยกำหนดมา 12 วัน", open[0].ask.why)
    }

    @Test
    fun askedAgainTheNextDay() {
        // The day after the owner said not done, the same question is open again: nothing is hushed.
        val open = SecretaryAsk.open(SecretaryAsk.parse(file), tasks(), today.plusDays(1L))
        assertTrue(open.any { it.task.title == "จองตั๋ว" })
    }

    @Test
    fun noFileNoQuestions() {
        assertEquals(emptyList(), SecretaryAsk.parse(null))
        assertEquals(emptyList(), SecretaryAsk.parse(""))
    }
}
