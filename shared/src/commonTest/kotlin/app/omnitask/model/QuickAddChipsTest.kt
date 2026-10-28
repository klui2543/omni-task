package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

class QuickAddChipsTest {

    // Wednesday.
    private val today = LocalDate.parse("2026-10-07")

    @Test
    fun spansPointAtTheTypedWords() {
        val text = "ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ !!"
        val s = QuickAdd.spans(text, today)
        assertEquals("พรุ่งนี้", text.substring(s.due!!))
        assertEquals("9:00", text.substring(s.time!!))
        // Glued to the title, after a tag, with spaces in front: still the typed words.
        val glued = "  #Omni ประชุมทีมวันจันทร์หน้า"
        assertEquals("วันจันทร์หน้า", glued.trim().substring(QuickAdd.spans(glued, today).due!!))
        // A time alone gives today, but no day was typed.
        assertNull(QuickAdd.spans("โทรหาแม่ 18:30", today).due)
    }

    @Test
    fun parseIsUnchangedByTheSpans() {
        val d = QuickAdd.parse("ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ !!", today)
        assertEquals("ส่งรายงาน", d.title)
        assertEquals(LocalDate.parse("2026-10-08"), d.due)
        assertEquals(listOf("รอ/พี่เอ"), d.tags)
        assertEquals(Priority.HIGH, d.priority)
    }

    @Test
    fun aPickedDayTakesThePlaceOfTheTypedOne() {
        val t = QuickAddChips.withDue("ส่งรายงาน พรุ่งนี้ 9:00", LocalDate.parse("2026-10-20"), today)
        assertEquals("ส่งรายงาน 9:00 20/10 ", t)
        val d = QuickAdd.parse(t, today)
        assertEquals(LocalDate.parse("2026-10-20"), d.due)
        assertEquals(LocalTime.of(9, 0), d.time)
        assertEquals("ส่งรายงาน", d.title)
    }

    @Test
    fun dayWordsReadBackAsTheSameDay() {
        listOf("2026-10-07", "2026-10-08", "2026-12-31", "2027-01-05", "2026-10-01", "2027-10-20", "2028-02-29").forEach {
            val day = LocalDate.parse(it)
            assertEquals(day, QuickAdd.parse("x " + QuickAddChips.dayWords(day, today), today).due, it)
            assertEquals(day, QuickAdd.parse("x " + QuickAddChips.dayWords(day, today, english = true), today).due, it)
        }
        assertEquals("วันนี้", QuickAddChips.dayWords(today, today))
        assertEquals("tomorrow", QuickAddChips.dayWords(today.plusDays(1), today, english = true))
        assertEquals("5/1", QuickAddChips.dayWords(LocalDate.parse("2027-01-05"), today))
        assertEquals("1/10/2026", QuickAddChips.dayWords(LocalDate.parse("2026-10-01"), today))
    }

    @Test
    fun aPickedTimeTakesThePlaceOfTheTypedOne() {
        val t = QuickAddChips.withTime("ส่งรายงาน พรุ่งนี้ 9:00", LocalTime.of(14, 5), today)
        assertEquals("ส่งรายงาน พรุ่งนี้ 14:05 ", t)
        assertEquals(LocalTime.of(14, 5), QuickAdd.parse(t, today).time)
        assertEquals("ซื้อของ 7:30 ", QuickAddChips.withTime("ซื้อของ", LocalTime.of(7, 30), today))
    }

    @Test
    fun clearingTakesOnlyThatWordOut() {
        assertEquals("ส่งรายงาน 9:00 #รอ ", QuickAddChips.withoutDue("ส่งรายงาน พรุ่งนี้ 9:00 #รอ", today))
        assertEquals("ส่งรายงาน พรุ่งนี้ #รอ ", QuickAddChips.withoutTime("ส่งรายงาน พรุ่งนี้ 9:00 #รอ", today))
        // Nothing typed: nothing changes but the spacing.
        assertEquals("ซื้อนม ", QuickAddChips.withoutDue("ซื้อนม", today))
        assertEquals("", QuickAddChips.withoutDue("", today))
    }

    @Test
    fun aPickedFolderTakesThePlaceOfTheTypedOneAndLabelsStay() {
        val t = QuickAddChips.withFolder("แก้ปุ่ม #Omni #รอ/พี่เอ พรุ่งนี้", "Siriraj/ตารางเวรเภสัช", today)
        assertEquals("แก้ปุ่ม #รอ/พี่เอ พรุ่งนี้ #Siriraj/ตารางเวรเภสัช ", t)
        assertEquals("Siriraj/ตารางเวรเภสัช", Folders.ofTags(QuickAdd.parse(t, today).tags))
        assertEquals("แก้ปุ่ม #รอ ", QuickAddChips.withFolder("แก้ปุ่ม #Omni #รอ", null, today))
        // A folder with levels goes out whole.
        assertEquals("ก #บ้าน ", QuickAddChips.withFolder("ก #Omni/เลขา", "บ้าน", today))
    }
}
