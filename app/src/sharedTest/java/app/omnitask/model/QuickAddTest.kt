package app.omnitask.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

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
}
