package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebNotifyTest {

    private val note = listOf(
        "- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-07",
        "- [/] สไลด์ประชุม #remind-at-due ⏰ 10:00 ➕ 2026-10-01 📅 2026-10-08",
        "- [ ] โทรหาแม่ #remind-at-due ⏰ 19:30 ➕ 2026-10-01 📅 2026-10-08",
        "- [x] เสร็จแล้ว #remind-at-due ⏰ 09:00 📅 2026-10-08 ✅ 2026-10-08",
        "- [ ] ประชุมโปรเจกต์ #proj/ทางก #remind-at-scheduled 🎯 11:00 ⏳ 2026-10-08",
        "- [ ] งานไม่มีวัน",
    ).joinToString("\n")

    private fun run(since: String, until: String, extra: String = "") =
        WebNotify.reminders("f", "Omni note.md", note, """{"now":"2026-10-08T09:20","since":"$since","until":"$until"$extra}""")

    @Test
    fun remindersBetweenTwoMomentsFollowThePhonesRules() {
        val r = run("2026-10-08T09:00", "2026-10-08T23:59")
        assertTrue(r.contains("{\"id\":\"task:f:- [/] สไลด์ประชุม #remind-at-due ⏰ 10:00 ➕ 2026-10-01 📅 2026-10-08\",\"key\":\"f#1\""), r)
        assertTrue(r.contains("\"title\":\"สไลด์ประชุม\",\"text\":\"ครบ 2026-10-08\",\"at\":\"2026-10-08T10:00\""), r)
        assertTrue(r.contains("\"at\":\"2026-10-08T19:30\""), r)
        assertTrue(r.contains("ประชุมโปรเจกต์"), r)
        // A finished task never rings, and one that rang before [since] is not asked again.
        assertFalse(r.contains("เสร็จแล้ว"), r)
        assertFalse(run("2026-10-08T10:00", "2026-10-08T12:00").contains("สไลด์ประชุม"))
        // Overdue and due today, for the badge.
        assertTrue(r.contains("\"badge\":3"), r)
    }

    @Test
    fun aParkedBranchStaysQuiet() {
        val r = run("2026-10-08T09:00", "2026-10-08T23:59", ""","branches":["proj\tทางก\tPARKED"]""")
        assertTrue(r.contains("สไลด์ประชุม"), r)
        assertFalse(r.contains("ประชุมโปรเจกต์"), r)
    }
}
