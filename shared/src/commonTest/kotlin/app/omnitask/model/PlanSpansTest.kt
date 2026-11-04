package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class PlanSpansTest {

    // Shaped like the secretary's ไมล์สโตน.md, with the real Rx Shift plan of 2026-11-03.
    private val file = listOf(
        "---", "format: milestones/1", "---", "# ไมล์สโตน",
        "## Rx Shift",
        "- เป้า: ระบบแลกเวรใหม่ใช้จริงเดือน ม.ค. 2570 [ที่มา: แผน-RxShift.md]",
        "- ช่วงแผน: #Siriraj/ตารางเวรเภสัช | เปิดโหมดเงาปฏิทิน | 2026-11-03 | 2026-11-30 | เดา | ขั้นถัดไป: Klui กดรันปุ่มเปิดโหมดเงา 1 ครั้ง [ที่มา: แผน-RxShift.md]",
        "- ช่วงแผน: #Siriraj/ตารางเวรเภสัช | ปุ่มเปิดเดือนจริง | 2026-11-20 | 2026-12-15 | จริง [ที่มา: แผน-RxShift.md]",
        "- ช่วงแผน: #Siriraj/ตารางเวรเภสัช | วันไม่ถูก | 15 พ.ย. | 2026-12-15 | เดา",
        "💬 - ช่วงแผน: #Siriraj | ของ Klui | 2026-11-01 | 2026-11-02",
        "## Omni",
        "- ช่วงแผน: omni | พักป้ายแดง | 2026-11-05 | 2026-11-04",
    ).joinToString("\n")

    @Test fun readsLinesWithProjectGuessAndNextStep() {
        val all = PlanSpans.parse(file)
        assertEquals(3, all.size)
        val a = all[0]
        assertEquals("Rx Shift", a.project)
        assertEquals("Siriraj/ตารางเวรเภสัช", a.tag)
        assertEquals("เปิดโหมดเงาปฏิทิน", a.title)
        assertEquals(LocalDate(2026, 11, 3), a.start)
        assertEquals(LocalDate(2026, 11, 30), a.end)
        assertTrue(a.guess)
        assertEquals("Klui กดรันปุ่มเปิดโหมดเงา 1 ครั้ง", a.next)
        assertEquals(false, all[1].guess)
        assertNull(all[1].next)
    }

    @Test fun swapsBackwardDatesAndKeepsTheOmniBlock() {
        val o = PlanSpans.parse(file).last()
        assertEquals("Omni", o.project)
        assertEquals(LocalDate(2026, 11, 4), o.start)
        assertEquals(LocalDate(2026, 11, 5), o.end)
    }

    @Test fun showsOnlyForTheFilteredProject() {
        val all = PlanSpans.parse(file)
        assertEquals(2, PlanSpans.shown(all, setOf("Siriraj/ตารางเวรเภสัช")).size)
        assertEquals(2, PlanSpans.shown(all, setOf("Siriraj")).size)
        assertEquals(2, PlanSpans.shown(all, setOf("siriraj/ตารางเวรเภสัช/ปฏิทิน")).size)
        assertEquals(1, PlanSpans.shown(all, setOf("omni")).size)
        assertEquals(0, PlanSpans.shown(all, emptySet()).size)
        assertEquals(0, PlanSpans.shown(all, setOf("Sirirajx")).size)
    }

    @Test fun nothingWithoutAFile() = assertEquals(0, PlanSpans.parse(null).size)
}
