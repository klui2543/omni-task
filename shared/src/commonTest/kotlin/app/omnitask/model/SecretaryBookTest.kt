package app.omnitask.model

import app.omnitask.data.VaultText
import app.omnitask.web.WebNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class SecretaryBookTest {
    private val today = LocalDate.parse("2026-10-25")

    private val log = """
        # ภาพรวม
        - Omni/ygdrasil: รวมเข้าแอปแล้ว ➜ Claude

        # งานค้าง
        - [ ] ลองแอปรุ่นใหม่ 0.2.296 #Omni/projects #kluido ➕ 2026-10-24
            - ที่: มือถือ ไฟล์อยู่ในแชต Claude วันที่ 24
            - เสร็จเมื่อ: บอก Claude ว่าการลากโปรเจกต์ใช้ได้ไหม
        - [ ] เขียนถ้อยคำให้ครบ 25 ข้อ #Siriraj/ตารางเวรเภสัช/wording #kluido ➕ 2026-10-20 📅 2026-10-27
        - [ ] ดูว่าปิดเดือนทำงาน #Omni/เลขา #kluido ➕ 2026-10-22 ⏳ 2026-11-03
        - [ ] ขอ token ใหม่ #Omni/เลขา #kluido ➕ 2026-10-22
        - [ ] ตอบเรื่องปฏิทินแฟน #Omni/calendar #kluido ➕ 2026-10-23
        - [x] ทดสอบ APK ตัวกรอง 2 ชั้น #Omni/ygdrasil #kluido ➕ 2026-10-19 ✅ 2026-10-24
            - ปิดโดยเลขา: รวมเข้าแอปแล้วใน PR #72
        - [x] ดู mock เก่า #Omni/assistant #kluido ➕ 2026-10-20 ✅ 2026-10-20
            - ปิดโดยเลขา: มีของใหม่มาแทน
        - [x] ทดสอบเสียงไทย #Omni/voice #kluido ➕ 2026-10-19 ✅ 2026-10-24

        # งานของ Claude
        - Omni/งานของฉัน: ทำ mock การ์ด วันนี้ 3 งาน (พร้อมเริ่ม) เพิ่ม 2026-10-22
        - Omni/assistant: แก้ตามผลลองแอป 0.2.295 (รอ Klui ลองแอป) เพิ่ม 2026-10-24

        # ไทม์ไลน์
        ## 2026-10-24
        - Omni: ทำแล้ว
    """.trimIndent()

    private val book = VaultText.parseFile("log", SecretaryBook.LOG_PATH, log)
    private val omniNote = VaultText.parseFile("note", "${VaultText.OMNI_DIR}/Omni note.md", "- [ ] ซื้อของขวัญ 📅 2026-10-26\n- [ ] อ่านเปเปอร์ ⏳ 2026-10-20\n")

    @Test
    fun waitingIsTheOwnersTurnRealDeadlinesFirstAtMostThree() {
        val b = SecretaryBook.brief(book + omniNote, log, today)
        assertEquals(listOf("ซื้อของขวัญ", "เขียนถ้อยคำให้ครบ 25 ข้อ", "ขอ token ใหม่"), b.waiting.map { it.task.title })
        // The task waiting for November is not counted yet; the self-set ⏳ in the owner's note is left to the other answers.
        assertEquals(5, b.total)
    }

    @Test
    fun whereAndDoneWhenComeFromTheLinesUnderTheTask() {
        val two = book.filter { it.title.startsWith("ลองแอป") || it.title.startsWith("ขอ token") }
        val b = SecretaryBook.brief(two, log, today)
        val app = b.waiting.first { it.task.title.startsWith("ลองแอป") }
        assertEquals("มือถือ ไฟล์อยู่ในแชต Claude วันที่ 24", app.where)
        assertEquals("บอก Claude ว่าการลากโปรเจกต์ใช้ได้ไหม", app.doneWhen)
        assertEquals("Omni/projects", app.project)
        assertNull(b.waiting.first { it.task.title.startsWith("ขอ token") }.where)
    }

    @Test
    fun closedIsWhatTheSecretaryClosedSinceYesterdayWithItsReason() {
        val b = SecretaryBook.brief(book, log, today)
        assertEquals(listOf("ทดสอบ APK ตัวกรอง 2 ชั้น" to "รวมเข้าแอปแล้วใน PR #72"), b.closed.map { it.task.title to it.reason })
    }

    @Test
    fun claudesWorkIsPlainBulletsAndOnlyReadyOnesShow() {
        assertEquals(2, SecretaryBook.claudeWork(log).size)
        assertEquals(listOf("Omni/งานของฉัน: ทำ mock การ์ด วันนี้ 3 งาน"), SecretaryBook.brief(book, log, today).claudeReady)
        // Plain bullets are never tasks, so Claude's work never shows as the owner's.
        assertTrue(book.none { it.title.contains("mock การ์ด") })
    }

    @Test
    fun theOpeningAnswerCarriesTheBrief() {
        val ctx = SecretaryDesk.Ctx(book + omniNote, emptyList(), today, LocalDateTime(2026, 10, 25, 8, 0), claudeLog = log)
        val r = SecretaryDesk.opening(ctx)
        assertEquals(3, r.brief?.waiting?.size)
        assertTrue(r.lines.any { it.contains("3 เรื่องรอคุณ") })
        // With nothing in the book there is no brief.
        assertNull(SecretaryDesk.opening(ctx.copy(tasks = emptyList(), claudeLog = null)).brief)
    }

    @Test
    fun keptSendFilesAreCopiesNotTasks() {
        assertTrue(SecretaryBook.isSentCopy("${SecretaryBook.SENT_DIR}/เลขา 2026-10-24 0823.md"))
        assertFalse(SecretaryBook.isSentCopy("${VaultText.OMNI_DIR}/Omni note.md"))
        assertFalse(WebNotes.isRead("${SecretaryBook.SENT_DIR}/เลขา 2026-10-24 0823.md"))
        assertTrue(WebNotes.isRead(SecretaryBook.LOG_PATH))
    }

    @Test
    fun keptSendFilesOlderThanAWeekGo() {
        val names = listOf("เลขา 2026-10-24 0823.md", "เลขา 2026-10-17 0900.md", "เลขา 2026-10-18 0900.md", "อื่นๆ.md")
        assertEquals(listOf("เลขา 2026-10-17 0900.md"), SecretaryBook.staleSent(names, today))
    }

    @Test
    fun aCopyOfTheBookHasNoCheckboxes() {
        val copy = SecretaryBook.plain("- [ ] ลองแอป #kluido\n- [x] ทดสอบ ✅ 2026-10-24\n    - [ ] ย่อย\n- ธรรมดา")
        assertEquals("- ลองแอป #kluido\n- เสร็จแล้ว: ทดสอบ ✅ 2026-10-24\n    - ย่อย\n- ธรรมดา", copy)
        assertTrue(VaultText.parseFile("c", "c.md", copy).isEmpty())
    }
}
