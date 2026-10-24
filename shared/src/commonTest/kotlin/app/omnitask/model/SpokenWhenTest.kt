package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

class SpokenWhenTest {
    private val sat = LocalDate.parse("2026-10-24")
    private val morning = LocalTime(8, 42)
    private fun read(s: String, now: LocalTime = morning) = SpokenWhen.read(s, TimeWords.DEFAULT, sat, now)

    @Test
    fun aPlainTimeWinsAndTheTimeWordLeavesTheTitle() {
        val h = read("นัดยาตอนเย็นเวลาบ่ายสามโมงครึ่ง")
        assertEquals("นัดยา", h.title)
        assertEquals(sat, h.date)
        assertEquals(LocalTime(15, 30), h.time)
        assertEquals(TaskLine.DateField.DUE, h.field)
    }

    @Test
    fun aTimeWordFromTheTableGivesItsTime() {
        val h = read("นัดยาตอนเย็น")
        assertEquals("นัดยา", h.title)
        assertEquals(LocalTime(17, 0), h.time)
        // Said after the time has passed, it is tomorrow's.
        assertEquals(sat.plusDays(1), read("นัดยาตอนเย็น", LocalTime(18, 0)).date)
    }

    @Test
    fun daysAndClockWords() {
        val h = read("ส่งตารางให้พี่เอพรุ่งนี้บ่ายสองโมง")
        assertEquals("ส่งตารางให้พี่เอ", h.title)
        assertEquals(LocalDate.parse("2026-10-25"), h.date)
        assertEquals(LocalTime(14, 0), h.time)
        assertEquals(LocalDate.parse("2026-10-30"), read("ส่งงานวันศุกร์นี้").date)
        assertEquals(LocalTime(20, 0), read("โทรหาแม่สองทุ่ม").time)
        assertEquals(LocalTime(9, 30), read("ประชุม 9:30").time)
    }

    @Test
    fun wordsTheTableDoesNotKnowStayInTheTitle() {
        val h = read("ซื้อของหลังเวร")
        assertEquals("ซื้อของหลังเวร", h.title)
        assertNull(h.date)
        assertNull(h.time)
    }

    @Test
    fun aTargetIsTheOwnersOwnDateAndTheLineIsWritten() {
        val h = read("ว่าจะอ่านเปเปอร์พรุ่งนี้")
        assertEquals(TaskLine.DateField.SCHEDULED, h.field)
        val due = read("นัดยาตอนเย็น")
        assertEquals("- [ ] นัดยา #Siriraj #remind-at-due ⏰ 17:00 ➕ 2026-10-24 📅 2026-10-24", SpokenWhen.line(due, "Siriraj", sat))
    }

    @Test
    fun theTableReadsAndWritesItsLines() {
        val words = TimeWords.parse(TimeWords.render(TimeWords.DEFAULT + TimeWords.Word("หลังเวร", LocalTime(16, 45))))
        assertEquals(6, words.size)
        assertEquals(LocalTime(16, 45), words.last().time)
        assertEquals("นัดยา", SpokenWhen.read("นัดยาหลังเวร", words, sat, morning).title)
    }

}
