package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebStuckTest {

    private val note = listOf(
        "- [/] ทำเว็บร้าน ➕ 2026-08-01 📅 2026-10-01",
        "    - ใช้ธีมเดิม",
        "    - ค้าง 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ",
        "    - [x] ได้รูปสินค้า ✅ 2026-09-20",
        "    - [ ] เลือกธีม",
        "- [ ] เขียนบทความ ➕ 2026-09-01",
        "- [ ] จองตั๋ว ➕ 2026-10-10",
    ).joinToString("\n")

    private fun view() = WebStuck.view("f", "Omni note.md", note, "2026-10-13", "[]")
    private fun task(key: String) = WebStuck.task("f", "Omni note.md", note, key, "2026-10-13", "[]")

    @Test
    fun viewHasThePicksTheTasksToAskAndTheLog() {
        assertEquals("[\"รอคนอื่น\",\"ไม่รู้จะเริ่มยังไง\",\"งานใหญ่เกิน\",\"ไม่มีเวลา\",\"ไม่มีแรง\",\"เป็นงานยาวอยู่แล้ว\"]", WebStuck.reasons())
        val r = view()
        // Open 42 days with no note, and explained 41 days ago: both asked, the one waiting longest first. The new one is too young.
        assertTrue(r.contains("\"toAsk\":[{\"key\":\"f#5\",\"title\":\"เขียนบทความ\",\"age\":42},{\"key\":\"f#0\""), r)
        // The log leaves out the start; a finished subtask reads as one.
        assertTrue(r.contains("\"log\":[{\"key\":\"f#0\",\"title\":\"ทำเว็บร้าน\",\"open\":true,\"age\":73,\"events\":["), r)
        assertTrue(r.contains("{\"day\":\"2026-09-20\",\"step\":\"DONE\",\"text\":\"งานย่อยเสร็จ: ได้รูปสินค้า\"}"), r)
        assertFalse(r.contains("\"step\":\"START\""), r)
    }

    @Test
    fun aTaskHasItsPathBarsAndWhenItLastMoved() {
        val r = task("f#0")
        assertTrue(r.contains("\"matters\":true,\"notes\":1,\"last\":\"2026-09-02\""), r)
        assertTrue(r.contains("{\"day\":\"2026-08-01\",\"step\":\"START\",\"text\":\"เริ่มงาน\"}"), r)
        assertTrue(r.contains("\"status\":\"ขยับล่าสุด 23 วันก่อน\""), r)
        assertTrue(r.contains("\"weeks\":[\"NONE\",\"NONE\",\"NOTE\",\"NONE\",\"DONE\",\"NONE\",\"NONE\",\"NONE\"]"), r)
        assertTrue(r.contains("\"done\":1,\"total\":2"), r)
        assertTrue(task("f#6").contains("\"matters\":false"))
        assertTrue(task("f#6").contains("\"status\":\"ยังไม่ได้จด\""))
        assertEquals("null", task("f#99"))
    }

    @Test
    fun writingANoteAddsTheDatedLineUnderTheTask() {
        val raw = "- [ ] เขียนบทความ ➕ 2026-09-01"
        val r = WebCore.editTask(note, raw, 5, "2026-10-13", """{"op":"stuck","value":"ไม่มีเวลา: งานด่วนแทรก\nทั้งสัปดาห์"}""")
        assertTrue(r.contains("\"ok\":true"), r)
        assertTrue(r.contains("$raw\\n    - ค้าง 2026-10-13 ไม่มีเวลา: งานด่วนแทรก ทั้งสัปดาห์\\n- [ ] จองตั๋ว"), r)
        // Under a task with lines already, it goes after them, before the subtasks.
        val web = WebCore.editTask(note, "- [/] ทำเว็บร้าน ➕ 2026-08-01 📅 2026-10-01", 0, "2026-10-13", """{"op":"stuck","value":"รอคนอื่น"}""")
        assertTrue(web.contains("รอรูปสินค้าจากพี่เอ\\n    - ค้าง 2026-10-13 รอคนอื่น\\n    - [x] ได้รูปสินค้า"), web)
        assertTrue(WebCore.editTask(note, raw, 5, "2026-10-13", """{"op":"stuck","value":"  "}""").contains("\"error\":\"empty\""))
    }

    @Test
    fun focusRowsOfLateWorkSayWhenTheyLastMoved() {
        val r = WebFocus.build("f", "Omni note.md", note, """{"now":"2026-10-13T09:00"}""")
        assertTrue(r.contains("\"extra\":\"ขยับล่าสุด 23 วันก่อน, กำลังทำ, งานย่อย 1/2\""), r)
        assertTrue(r.contains("\"stuck\":true,\"weeks\":[\"NONE\",\"NONE\",\"NOTE\""), r)
    }

    @Test
    fun theWeeklyReviewPointsAtItsStuckLine() {
        val r = WebAssistant.weekly("f", "Omni note.md", note, """{"now":"2026-10-13T09:00"}""")
        assertTrue(r.contains("ค้างนาน 2 งาน แตะเพื่อจดว่าติดอะไร"), r)
        assertTrue(r.contains("\"stuckAt\":3"), r)
    }
}
