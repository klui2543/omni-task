package app.omnitask.model

import app.omnitask.model.AssistantMl.Intent
import app.omnitask.model.AssistantMl.Label
import app.omnitask.model.AssistantMl.Span
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the learning secretary hands to quick add, and what it keeps out of the vault. */
class AssistantMlQuickAddTest {

    private val today = LocalDate.parse("2026-10-26")
    private val now = LocalTime(8, 0)
    private fun ex(line: String) = AssistantMl.parseLine(line)!!

    @Test
    fun spokenTimesBecomeTimesQuickAddReads() {
        val q = AssistantMl.forQuickAdd("โทรหาร้านซ่อมรถ วันศุกร์ 9 โมง", TimeWords.DEFAULT, today, now)
        assertEquals("โทรหาร้านซ่อมรถ วันศุกร์ 9:00", q)
        val d = QuickAdd.parse(q, today)
        assertEquals("โทรหาร้านซ่อมรถ", d.title)
        assertEquals(LocalDate.parse("2026-10-30"), d.due)
        assertEquals(LocalTime(9, 0), d.time)
        assertEquals(LocalTime(14, 0), QuickAdd.parse(AssistantMl.forQuickAdd("ประชุมบ่ายสองโมง", TimeWords.DEFAULT, today, now), today).time)
        // What quick add reads already stays as typed.
        assertEquals("ส่งงานพรุ่งนี้ 14:30", AssistantMl.forQuickAdd("ส่งงานพรุ่งนี้ 14:30", TimeWords.DEFAULT, today, now))
    }

    @Test
    fun aTaughtUrgencyWordBecomesQuickAddsMark() {
        val m = AssistantMl.Model(listOf(ex("- {เพิ่มงาน|คำสั่ง} {ส่งเวร|ชื่องาน} {ด่วน|ความเร่งด่วน=สูง} → เพิ่มงาน")))
        assertEquals("โทรหาหัวหน้า !!", m.taskText("เพิ่มงาน โทรหาหัวหน้า ด่วน"))
        assertEquals(Priority.HIGH, QuickAdd.parse(m.taskText("เพิ่มงาน โทรหาหัวหน้า ด่วน"), today).priority)
    }

    @Test
    fun wordsBeforeATaughtCommandStayPartOfTheAsking() {
        val m = AssistantMl.Model(listOf(ex("- {สร้างงาน|คำสั่ง} {ส่งรายงาน|ชื่องาน} → เพิ่มงาน")))
        assertEquals(
            listOf(Span("สั่ง"), Span("สร้างงาน", Label.COMMAND), Span(" "), Span("ส่งตาราง", Label.TITLE)),
            m.spansFor("สั่งสร้างงาน ส่งตาราง", Intent.AddTask),
        )
        // Kept that way, the same start is understood next time.
        val learned = AssistantMl.Model(listOf(ex("- {สร้างงาน|คำสั่ง} {ส่งรายงาน|ชื่องาน} → เพิ่มงาน"), AssistantMl.Example(m.spansFor("สั่งสร้างงาน ส่งตาราง", Intent.AddTask), Intent.AddTask)))
        assertEquals(AssistantMl.Level.HIGH, learned.read("สั่งสร้างงาน ซื้อนม").level)
    }

    @Test
    fun patientNumbersInOtherShapesAreCaughtAndDatesAreNot() {
        assertTrue(AssistantMl.sensitive("โทร 081.234.5678"))
        assertTrue(AssistantMl.sensitive("ผู้ป่วย ๑๒๓๔๕๖๗"))
        assertFalse(AssistantMl.sensitive("ส่งงาน 2026-10-30"))
        assertFalse(AssistantMl.sensitive("นัด 30/10/2569 9:00"))
    }

    @Test
    fun aDayWordKeepsTheTimeTypedWithIt() {
        listOf(LocalTime(7, 0), LocalTime(20, 0)).forEach { at ->
            val d = QuickAdd.parse(AssistantMl.forQuickAdd("เย็นนี้ 6 โมงเย็น ไปวิ่ง", TimeWords.DEFAULT, today, at), today)
            assertEquals("ไปวิ่ง", d.title, "$at")
            assertEquals(today, d.due, "$at")
            assertEquals(LocalTime(18, 0), d.time, "$at")
        }
        // Said on its own, the day word gives its own time, and stays today late in the day.
        val alone = QuickAdd.parse(AssistantMl.forQuickAdd("เย็นนี้ ไปวิ่ง", TimeWords.DEFAULT, today, LocalTime(20, 0)), today)
        assertEquals(today, alone.due)
        assertEquals("ไปวิ่ง", alone.title)
    }

    @Test
    fun aRepeatMarkGoesWithItsUrgencyWord() {
        val m = AssistantMl.Model(listOf(ex("- {เพิ่มงาน|คำสั่ง} {ส่งเวร|ชื่องาน} {ด่วน|ความเร่งด่วน=สูง} → เพิ่มงาน")))
        assertEquals("ไปรับพัสดุ !!", m.taskText("เพิ่มงาน ไปรับพัสดุด่วนๆ"))
        assertTrue(AssistantMl.sensitive("โทร 081.23.4567"))
        assertTrue(AssistantMl.sensitive("โทร 0812.34.5678"))
    }
}

