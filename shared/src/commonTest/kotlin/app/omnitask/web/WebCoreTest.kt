package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertTrue

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
}
