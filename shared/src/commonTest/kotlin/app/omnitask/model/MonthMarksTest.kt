package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

class MonthMarksTest {

    private fun d(day: Int) = LocalDate(2026, 10, day)
    private fun days(vararg n: Int) = n.map(::d).toSet()
    private fun range(a: Int, b: Int) = (a..b).map(::d).toSet()

    @Test
    fun daysInOneWeekMakeOneFrame() {
        val m = MonthMarks().withFrame(range(12, 16), FrameColor.VIOLET, " อบรม Excel ")
        assertEquals(1, m.frames.size)
        assertEquals(range(12, 16).sorted(), m.frames[0].days)
        assertEquals("อบรม Excel", m.frames[0].label)
        assertEquals(4 to 5, m.frameOn(d(15))?.position(d(15)))
    }

    @Test
    fun aBlockAcrossTwoWeeksIsOneFrame() {
        // Wednesday to Friday, two weeks running: they touch a week apart.
        val m = MonthMarks().withFrame(days(7, 8, 9, 14, 15, 16), FrameColor.BLUE, "")
        assertEquals(1, m.frames.size)
    }

    @Test
    fun saturdayIntoSundayStaysOneFrame() {
        // Friday 23 to Monday 26 crosses the end of the week.
        val m = MonthMarks().withFrame(range(23, 26), FrameColor.AMBER, "เชียงใหม่")
        assertEquals(1, m.frames.size)
        assertEquals(1 to 4, m.frames[0].position(d(23)))
    }

    @Test
    fun daysThatDoNotTouchAreSeparateFrames() {
        val m = MonthMarks().withFrame(days(2, 3, 5, 6), FrameColor.RED, "สอบ")
        assertEquals(listOf(listOf(d(2), d(3)), listOf(d(5), d(6))), m.frames.map { it.days })
        assertTrue(m.frames.all { it.label == "สอบ" && it.color == FrameColor.RED })
    }

    @Test
    fun aDayBelongsToOneFrame() {
        val m = MonthMarks()
            .withFrame(range(12, 16), FrameColor.VIOLET, "อบรม")
            .withFrame(days(14), FrameColor.RED, "นัดหมอ")
        // The training is cut in two around the 14th, which now has a frame of its own.
        assertEquals(listOf(listOf(d(12), d(13)), listOf(d(14)), listOf(d(15), d(16))), m.frames.map { it.days })
        assertEquals("นัดหมอ", m.frameOn(d(14))?.label)
        assertEquals("อบรม", m.frameOn(d(15))?.label)
    }

    @Test
    fun clearingTakesDaysOut() {
        val m = MonthMarks().withFrame(range(12, 16), FrameColor.VIOLET, "").withoutFrames(range(12, 16))
        assertTrue(m.frames.isEmpty())
        assertNull(m.frameOn(d(12)))
    }

    @Test
    fun editingAFrameReplacesIt() {
        val m = MonthMarks().withFrame(range(12, 14), FrameColor.VIOLET, "อบรม")
        val edited = m.without(m.frames[0]).withFrame(range(12, 16), FrameColor.GREEN, "อบรม 5 วัน")
        assertEquals(1, edited.frames.size)
        assertEquals(FrameColor.GREEN, edited.frames[0].color)
        assertEquals(5, edited.frames[0].days.size)
    }

    @Test
    fun stickersKeepTheirOrderAndClear() {
        val m = MonthMarks().withStickers(d(15), listOf("🎂", " ☕ ", ""))
        assertEquals(listOf("🎂", "☕"), m.stickersOn(d(15)))
        assertTrue(m.withStickers(d(15), emptyList()).stickers.isEmpty())
    }

    @Test
    fun jsonRoundTripsAndIgnoresWhatItDoesNotKnow() {
        val m = MonthMarks(savedAt = 42)
            .withFrame(range(12, 16), FrameColor.VIOLET, "อบรม Excel")
            .withStickers(d(24), listOf("✈️", "🏖️"))
        val text = m.encode()
        assertTrue(text.contains("\"2026-10-12\""), text)
        assertTrue(text.contains("\"violet\""), text)
        assertEquals(m, MonthMarks.decode(text))
        val later = MonthMarks.decode("""{"savedAt": 7, "frames": [], "stickers": {}, "something": 1}""")
        assertEquals(7, later?.savedAt)
        assertNull(MonthMarks.decode("not json"))
        assertNull(MonthMarks.decode(null))
    }

    @Test
    fun theLaterCopyWins() {
        val phone = MonthMarks(savedAt = 10)
        assertEquals(phone, MonthMarks.newer(phone, null))
        assertEquals(20, MonthMarks.newer(phone, MonthMarks(savedAt = 20)).savedAt)
        assertEquals(10, MonthMarks.newer(phone, MonthMarks(savedAt = 5)).savedAt)
    }

    @Test
    fun gridStartsOnSunday() {
        // October 2026 starts on a Thursday: the grid opens on Sunday 27 September and runs five weeks.
        assertEquals(LocalDate(2026, 9, 27), MonthGrid.start(d(15)))
        assertEquals(5, MonthGrid.weeks(d(15)))
        assertEquals(35, MonthGrid.days(d(1)).size)
        // February 2026 starts on a Sunday and fills exactly four weeks; August 2026 needs six.
        assertEquals(LocalDate(2026, 2, 1), MonthGrid.start(LocalDate(2026, 2, 10)))
        assertEquals(4, MonthGrid.weeks(LocalDate(2026, 2, 1)))
        assertEquals(6, MonthGrid.weeks(LocalDate(2026, 8, 1)))
        assertTrue(MonthGrid.isSunday(d(25)))
    }

    @Test
    fun sidesOpenWhereTheFrameCarriesOn() {
        val f = range(23, 26)
        // Saturday 24: the frame goes on to Sunday 25 on the next row, so the right side stays open.
        val sat = MonthGrid.sides(d(24), f)
        assertFalse(sat.right)
        assertTrue(sat.left.not())
        assertTrue(sat.top && sat.bottom)
        // Sunday 25 is open on the left for the same reason, closed at the end of the frame.
        assertFalse(MonthGrid.sides(d(25), f).left)
        assertTrue(MonthGrid.sides(d(26), f).right)
        assertTrue(MonthGrid.sides(d(23), f).left)
    }

    @Test
    fun titleUsesTheBuddhistYear() {
        Lang.english = false
        assertEquals("ตุลาคม 2569", MonthGrid.title(d(15)))
        assertEquals("15 ต.ค.", MonthGrid.dayMonth(d(15)))
        assertEquals("อา", MonthGrid.heads.first())
    }
}
