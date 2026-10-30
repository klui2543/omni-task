package app.omnitask.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

class QuickAddTest {

    // Wednesday.
    private val today = LocalDate.parse("2026-10-07")

    @Test
    fun readsDayTimeTagsAndPriority() {
        val d = QuickAdd.parse("ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ !!", today)
        assertEquals("ส่งรายงาน", d.title)
        assertEquals(LocalDate.parse("2026-10-08"), d.due)
        assertEquals(LocalTime.of(9, 0), d.time)
        assertEquals(listOf("รอ/พี่เอ"), d.tags)
        assertEquals(Priority.HIGH, d.priority)
        assertEquals(
            "- [ ] ส่งรายงาน #รอ/พี่เอ #remind-at-due ⏰ 09:00 ⏫ ➕ 2026-10-07 📅 2026-10-08",
            d.line(today),
        )
    }

    @Test
    fun weekdaysAndDates() {
        assertEquals(LocalDate.parse("2026-10-12"), QuickAdd.parse("ประชุมทีมวันจันทร์", today).due)
        assertEquals("ประชุมทีม", QuickAdd.parse("ประชุมทีมวันจันทร์", today).title)
        assertEquals(LocalDate.parse("2026-10-19"), QuickAdd.parse("ประชุม วันจันทร์หน้า", today).due)
        assertEquals(LocalDate.parse("2026-10-09"), QuickAdd.parse("call mom friday", today).due)
        assertEquals(LocalDate.parse("2026-10-25"), QuickAdd.parse("จ่ายค่าเช่า 25/10", today).due)
        assertEquals(LocalDate.parse("2027-01-05"), QuickAdd.parse("ต่อใบอนุญาต 5/1", today).due)
        assertEquals(LocalDate.parse("2026-12-31"), QuickAdd.parse("ส่งภาษี 31/12/2569", today).due)
    }

    @Test
    fun plainTextStaysPlain() {
        val d = QuickAdd.parse("อ่านหนังสือพระจันทร์เสี้ยว", today)
        assertEquals("อ่านหนังสือพระจันทร์เสี้ยว", d.title)
        assertNull(d.due)
        assertEquals("- [ ] อ่านหนังสือพระจันทร์เสี้ยว ➕ 2026-10-07", d.line(today))
    }

    @Test
    fun hashOffersListsTheirCategoriesAndTags() {
        val watch = OmniList("Watch list", "Omni/Watch list.md", "🎬", listOf("หนัง", "ซีรีส์"))
        val bucket = OmniList("Bucket list", "Omni/Bucket list.md", "🏔️", listOf("เที่ยว"))
        assertEquals("wat", QuickAdd.hashToken("Shogun #wat"))
        assertEquals(null, QuickAdd.hashToken("Shogun wat"))
        val picks = QuickAdd.hashPicks("wat", listOf(watch, bucket), listOf("water", "peddose"))
        assertEquals(
            listOf(QuickAdd.HashPick.ToList(watch), QuickAdd.HashPick.ToList(watch, "หนัง"), QuickAdd.HashPick.ToList(watch, "ซีรีส์"), QuickAdd.HashPick.ToTag("water")),
            picks,
        )
        assertEquals(listOf(QuickAdd.HashPick.ToList(watch, "ซีรีส์")), QuickAdd.hashPicks("ซีรี", listOf(watch, bucket), emptyList()))
        assertEquals(2, QuickAdd.hashPicks("", listOf(watch, bucket), emptyList()).size)
    }

    @Test
    fun sharedTextKeepsTheLinkOutOfTheTitle() {
        assertEquals(Shared.Item("ทำไม 28 Years Later", "https://youtu.be/kfZ12WcTpkM?si=Oo"), Shared.read("ทำไม 28 Years Later", "https://youtu.be/kfZ12WcTpkM?si=Oo"))
        assertEquals(Shared.Item("อ่านบทความนี้", "https://example.com/a/12/10"), Shared.read(null, "อ่านบทความนี้ https://example.com/a/12/10"))
        assertEquals(Shared.Item("แค่ข้อความ", null), Shared.read("", "แค่ข้อความ"))
    }

    @Test
    fun pageTitles() {
        assertEquals("Tom &amp; Jerry".replace("&amp;", "&"), Shared.titleFromHtml("<html><head><title>\n Tom &amp; Jerry </title></head>"))
        assertEquals("ทำไม 28 Years Later", Shared.titleFromHtml("<meta property=\"og:title\" content=\"ทำไม 28 Years Later\"><title>x - YouTube</title>"))
        assertNull(Shared.titleFromHtml("<html><body>no title</body></html>"))
    }

    @Test
    fun detailedTaskKeepsTokenOrderAndWritesNotesAndSubtasks() {
        val rule = Recurrence.build(1, "week", setOf(kotlinx.datetime.DayOfWeek.THURSDAY, kotlinx.datetime.DayOfWeek.MONDAY))
        assertEquals("every week on Monday, Thursday", rule)
        assertEquals(true, Recurrence.parse(rule) != null)
        val d = QuickAdd.Detail(
            title = "ส่งรายงาน", scheduled = true, date = LocalDate.parse("2026-10-08"), time = LocalTime.of(9, 0),
            start = LocalDate.parse("2026-10-07"), rule = rule, tags = listOf("งาน"), priority = Priority.HIGH,
            notes = listOf("ส่งทางอีเมล"), subtasks = listOf("รวบตัวเลข", " "),
        )
        assertEquals(
            "- [ ] ส่งรายงาน #งาน #remind-at-scheduled 🎯 09:00 ⏫ 🔁 every week on Monday, Thursday ➕ 2026-10-07 🛫 2026-10-07 ⏳ 2026-10-08\n    - ส่งทางอีเมล\n    - [ ] รวบตัวเลข",
            d.block(today),
        )
    }

    @Test
    fun newTaskCarriesLinkedNotesAndImagesLikeTheEditPage() {
        // Linked notes and images picked while adding go under the task as the edit page would write them.
        val d = QuickAdd.Detail(
            title = "ส่งรายงานยา", date = LocalDate.parse("2026-10-08"), notes = listOf("ส่งทางอีเมล"), subtasks = listOf("รวบตัวเลข"),
            links = listOf("รายงานยาเดือนตุลา", " ", "รายงานยาเดือนตุลา"), images = listOf("Omni-2026-10-07-091500.webp"),
        )
        val block = d.block(today)
        assertEquals(
            "- [ ] ส่งรายงานยา ➕ 2026-10-07 📅 2026-10-08\n    - ส่งทางอีเมล\n    - [[รายงานยาเดือนตุลา]]\n    - ![[Omni-2026-10-07-091500.webp]]\n    - [ ] รวบตัวเลข",
            block,
        )
        // Read back from the note, the task has the link and the image, and the subtask stays a subtask.
        val tasks = app.omnitask.data.VaultText.parseFile("u", "Omni note.md", block)
        assertEquals(listOf("รายงานยาเดือนตุลา"), tasks.first().links)
        assertEquals(listOf("Omni-2026-10-07-091500.webp"), tasks.first().attachments)
        assertEquals(listOf("ส่งทางอีเมล"), tasks.first().description.lines().filter { it.isNotBlank() })
        assertEquals(2, tasks.size)
    }

    @Test
    fun customRulesAndCalendarRules() {
        assertEquals("every 2 weeks when done", Recurrence.build(2, "week", whenDone = true))
        assertEquals("every month", Recurrence.build(1, "month"))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;COUNT=5", Recurrence.rrule(2, "week", setOf(kotlinx.datetime.DayOfWeek.THURSDAY, kotlinx.datetime.DayOfWeek.MONDAY), count = 5))
        assertEquals("FREQ=DAILY;UNTIL=20261231T235959Z", Recurrence.rrule(1, "day", until = LocalDate.parse("2026-12-31")))
        assertEquals("FREQ=YEARLY;UNTIL=20261231", Recurrence.rrule(1, "year", until = LocalDate.parse("2026-12-31"), allDay = true))
    }

    @Test
    fun timeRanges() {
        val d = QuickAdd.parse("ประชุมทีม พรุ่งนี้ 9:00-11:00", today)
        assertEquals("ประชุมทีม", d.title)
        assertEquals(LocalDate.parse("2026-10-08"), d.due)
        assertEquals(LocalTime.of(9, 0), d.time)
        assertEquals(LocalTime.of(11, 0), d.endTime)
        assertNull(d.endDue)
        assertEquals(LocalTime.of(14, 30), QuickAdd.parse("ทำฟัน 13.00 ถึง 14.30 น.", today).endTime)
        assertEquals(LocalTime.of(16, 0), QuickAdd.parse("gym 15:00 to 16:00", today).endTime)
        assertEquals("ทำฟัน", QuickAdd.parse("ทำฟัน 13.00 ถึง 14.30 น.", today).title)
        // One time is not a range.
        assertNull(QuickAdd.parse("ส่งรายงาน 9:00", today).endTime)
        // A task line has no end.
        assertEquals("- [ ] ประชุมทีม #remind-at-due ⏰ 09:00 ➕ 2026-10-07 📅 2026-10-08", d.line(today))
    }

    @Test
    fun dateRanges() {
        val d = QuickAdd.parse("เที่ยวเชียงใหม่ 20-22/10", today)
        assertEquals("เที่ยวเชียงใหม่", d.title)
        assertEquals(LocalDate.parse("2026-10-20"), d.due)
        assertEquals(LocalDate.parse("2026-10-22"), d.endDue)
        assertNull(d.time)
        val e = QuickAdd.parse("ไปญี่ปุ่น 30/10 ถึง 2/11", today)
        assertEquals(LocalDate.parse("2026-10-30"), e.due)
        assertEquals(LocalDate.parse("2026-11-02"), e.endDue)
        // Over new year.
        val f = QuickAdd.parse("ปีใหม่ 28/12-3/1", today)
        assertEquals(LocalDate.parse("2026-12-28"), f.due)
        assertEquals(LocalDate.parse("2027-01-03"), f.endDue)
        // Already over this year: next year's.
        val g = QuickAdd.parse("สงกรานต์ 13-15/4", today)
        assertEquals(LocalDate.parse("2027-04-13"), g.due)
        assertEquals(LocalDate.parse("2027-04-15"), g.endDue)
        // With a Buddhist year, and with times.
        val h = QuickAdd.parse("สัมมนา 5-6/11/2569 9:00-16:00", today)
        assertEquals(LocalDate.parse("2026-11-05"), h.due)
        assertEquals(LocalDate.parse("2026-11-06"), h.endDue)
        assertEquals(LocalTime.of(16, 0), h.endTime)
        assertEquals("สัมมนา", h.title)
        // A single date still works.
        assertNull(QuickAdd.parse("จ่ายค่าเช่า 25/10", today).endDue)
    }
}
