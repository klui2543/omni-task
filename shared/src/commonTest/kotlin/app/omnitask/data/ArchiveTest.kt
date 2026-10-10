package app.omnitask.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class ArchiveTest {

    private fun d(s: String) = LocalDate.parse(s)

    private val live = listOf(
        "# งาน",
        "- [x] ซื้อของ ✅ 2026-09-20",
        "    - นม ไข่",
        "- [x] งานโปรเจกต์ #peddose ✅ 2026-09-20",
        "- [x] เพิ่งเสร็จ ✅ 2026-10-07",
        "- [x] ยังมีงานย่อยค้าง ✅ 2026-09-01",
        "    - [ ] ขั้นที่สอง",
        "- [-] ยกเลิกแล้ว ❌ 2026-09-02",
        "- [ ] รดน้ำต้นไม้ 🔁 every day 📅 2026-10-08",
        "- [x] รดน้ำต้นไม้ 🔁 every day 📅 2026-10-07 ✅ 2026-10-07",
        "    - ใช้ปุ๋ยสูตรเสมอ",
        "    - ![[Omni-1.webp]]",
        "- [ ] งานเปิด",
    ).joinToString("\n")

    @Test
    fun sweepMovesOldFinishedWorkAndKeepsProjects() {
        val sweep = Archive.sweep(live, d("2026-10-01")) { it.tags.isNotEmpty() }!!
        assertEquals(listOf("ซื้อของ", "ยกเลิกแล้ว", "รดน้ำต้นไม้"), sweep.titles)
        assertEquals(
            listOf(
                "# งาน",
                "- [x] งานโปรเจกต์ #peddose ✅ 2026-09-20",
                "- [x] เพิ่งเสร็จ ✅ 2026-10-07",
                "- [x] ยังมีงานย่อยค้าง ✅ 2026-09-01",
                "    - [ ] ขั้นที่สอง",
                // The open repeating line takes on the details its done copy had.
                "- [ ] รดน้ำต้นไม้ 🔁 every day 📅 2026-10-08",
                "    - ใช้ปุ๋ยสูตรเสมอ",
                "    - ![[Omni-1.webp]]",
                "- [ ] งานเปิด",
            ).joinToString("\n"),
            sweep.text,
        )
        assertEquals(listOf("- [x] ซื้อของ ✅ 2026-09-20", "    - นม ไข่"), sweep.blocks.first())
    }

    @Test
    fun detailsStayWhenTheOpenRepeatHasItsOwn() {
        val text = listOf(
            "- [ ] รดน้ำ 🔁 every day 📅 2026-10-08",
            "    - ของรอบนี้",
            "- [x] รดน้ำ 🔁 every day 📅 2026-10-07 ✅ 2026-10-07",
            "    - ของรอบก่อน",
        ).joinToString("\n")
        val sweep = Archive.sweep(text, d("2026-09-01")) { false }!!
        assertEquals("- [ ] รดน้ำ 🔁 every day 📅 2026-10-08\n    - ของรอบนี้", sweep.text)
    }

    @Test
    fun nothingToMoveIsNull() {
        assertNull(Archive.sweep("- [ ] a\n- [x] b ✅ 2026-10-07", d("2026-10-01")) { false })
    }

    @Test
    fun appendGroupsByMonthAndRemoveUndoes() {
        val block = listOf("    - [x] ซื้อของ ✅ 2026-09-20", "        - นม ไข่")
        val first = Archive.append(null, listOf(block), d("2026-10-08"))
        assertEquals("# Omni note Archive\n\n## 2026-10\n\n- [x] ซื้อของ ✅ 2026-09-20\n    - นม ไข่\n", first)
        val second = Archive.append(first, listOf(listOf("- [x] อีกงาน ✅ 2026-10-08")), d("2026-10-09"))
        assertEquals(first + "- [x] อีกงาน ✅ 2026-10-08\n", second)
        val nextMonth = Archive.append(second, listOf(listOf("- [x] พฤศจิกา ✅ 2026-11-01")), d("2026-11-02"))
        assertEquals(second + "\n## 2026-11\n\n- [x] พฤศจิกา ✅ 2026-11-01\n", nextMonth)
        assertEquals(first, Archive.remove(second, listOf("- [x] อีกงาน ✅ 2026-10-08")))
        assertNull(Archive.remove(first, listOf("- [x] ไม่มี")))
    }

    @Test
    fun repeatMovesOnWithItsChecklistOpen() {
        val lines = mutableListOf(
            "- [ ] ทำความสะอาดบ้าน 🔁 every week 📅 2026-10-08",
            "    - ทุกห้อง",
            "    - [x] ดูดฝุ่น ✅ 2026-10-08",
            "    - [ ] ถูพื้น",
            "- [x] งานอื่น ✅ 2026-10-01",
        )
        assertEquals(true, VaultText.advanceRecurring(lines, 0, d("2026-10-08")))
        assertEquals(
            listOf(
                "- [ ] ทำความสะอาดบ้าน 🔁 every week 📅 2026-10-15",
                "    - ทุกห้อง",
                "    - [ ] ดูดฝุ่น",
                "    - [ ] ถูพื้น",
                "- [x] งานอื่น ✅ 2026-10-01",
            ),
            lines,
        )
    }
}
