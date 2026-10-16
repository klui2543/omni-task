package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DayWorkTest {
    private val october = (1..31).map { LocalDate(2026, 10, it) }

    @Test
    fun aDailyRoutineIsLeftOut() {
        val titles = october.associateWith { d -> listOf("ออกกำลังกาย") + if (d.dayOfMonth == 22) listOf("ประชุมทีม") else emptyList() }
        assertEquals(setOf("ออกกำลังกาย"), DayWork.routine(titles, october))
    }

    @Test
    fun aWeeklyMeetingIsNotARoutine() {
        val titles = october.associateWith { d -> if (d.dayOfMonth % 7 == 1) listOf("ประชุมทีม") else emptyList() }
        assertEquals(emptySet(), DayWork.routine(titles, october))
    }

    @Test
    fun aShortRangeHasNoRoutine() {
        val week = october.take(7)
        assertEquals(emptySet(), DayWork.routine(week.associateWith { listOf("ออกกำลังกาย") }, week))
    }

    @Test
    fun levelsGrowWithTheDay() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3), (0..6).map(DayWork::level))
    }

    @Test
    fun anUnknownStyleIsTheTint() {
        assertEquals(CellStyle.TINT, CellStyle.of(null))
        assertEquals(CellStyle.BARS, CellStyle.of("bars"))
    }
}
