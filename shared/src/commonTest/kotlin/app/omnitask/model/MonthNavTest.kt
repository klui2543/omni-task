package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/** The tap cases from the widget's test plan (docs: G, N and B rows), each as a sequence of taps with their times. */
class MonthNavTest {

    private val today = LocalDate(2026, 10, 15)
    private fun d(day: Int, month: Int = 10) = LocalDate(2026, month, day)
    private val t0 = 1_000_000L

    @Test
    fun g1AndG2ATapListsThatDay() {
        val a = MonthNav().tapDay(d(28), today, t0)
        assertEquals(d(28), a.listed(today))
        assertEquals(0, a.offset)
        val b = a.tapDay(d(26), today, t0 + 1000)
        assertEquals(d(26), b.listed(today))
    }

    @Test
    fun g3AndB2TappingTheChosenDayAgainKeepsIt() {
        val once = MonthNav().tapDay(d(20), today, t0)
        // A slow second tap and a quick accidental one both keep the day.
        assertEquals(d(20), once.tapDay(d(20), today, t0 + 2000).listed(today))
        assertEquals(d(20), once.tapDay(d(20), today, t0 + 120).listed(today))
    }

    @Test
    fun g4TodayOrTheTitleGoesHome() {
        val away = MonthNav().tapDay(d(28), today, t0).shift(2, t0 + 500)
        val viaToday = away.tapDay(today, today, t0 + 1000)
        assertNull(viaToday.listed(today))
        assertEquals(0, viaToday.offset)
        val viaTitle = away.home(t0 + 1000)
        assertEquals(MonthNav(touchedAt = t0 + 1000), viaTitle)
    }

    @Test
    fun g5AndB3ArrowsMoveMonthsAndAddUp() {
        var nav = MonthNav()
        repeat(5) { nav = nav.shift(1, t0 + it * 100L) }
        assertEquals(5, nav.offset)
        assertEquals(3, nav.shift(-2, t0 + 600).offset)
    }

    @Test
    fun n1ADayInAMonthMovedTo() {
        val nav = MonthNav().shift(1, t0).tapDay(d(12, 11), today, t0 + 800)
        assertEquals(1, nav.offset)
        assertEquals(d(12, 11), nav.listed(today))
    }

    @Test
    fun n2AFaintDayBringsItsMonthUp() {
        // 1 November sits faint at the end of October's grid; 27 September at its start.
        assertEquals(1, MonthNav().tapDay(d(1, 11), today, t0).offset)
        assertEquals(-1, MonthNav().tapDay(LocalDate(2026, 9, 27), today, t0).offset)
        // Across a year: December's grid shows 2 January.
        assertEquals(3, MonthNav().tapDay(LocalDate(2027, 1, 2), today, t0).offset)
    }

    @Test
    fun n5LeftAloneTenMinutesGoesBackToToday() {
        val nav = MonthNav().shift(2, t0).tapDay(d(20, 12), today, t0 + 1000)
        assertEquals(nav, nav.fresh(t0 + 1000 + MonthNav.IDLE_MS))
        assertEquals(MonthNav(), nav.fresh(t0 + 1000 + MonthNav.IDLE_MS + 1))
        // An arrow after the wait starts from today's month, not the old one.
        assertEquals(1, nav.shift(1, t0 + 1000 + MonthNav.IDLE_MS + 5000).offset)
        // Never tapped: nothing to go back from.
        assertEquals(MonthNav(), MonthNav().fresh(t0))
    }

    @Test
    fun b1RapidTapsEndOnTheLastOne() {
        var nav = MonthNav()
        listOf(28, 26, 27, 29, 20, 21).forEachIndexed { i, day -> nav = nav.tapDay(d(day), today, t0 + i * 150L) }
        assertEquals(d(21), nav.listed(today))
        assertEquals(0, nav.offset)
    }

    @Test
    fun b4AnArrowAfterATapKeepsTheDay() {
        val nav = MonthNav().tapDay(d(22), today, t0).shift(1, t0 + 200)
        assertEquals(1, nav.offset)
        assertEquals(d(22), nav.listed(today))
    }
}
