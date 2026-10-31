package app.omnitask.widget

import app.omnitask.data.TaskLine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** The lock-screen card: today's widget rows without what is over, at most four, "more" for the rest. */
class LockCardTest {
    private val day = LocalDate(2026, 10, 31)
    private fun ev(t: String, past: Boolean = false) = TodayWeek.Row.Event(t, "11:00", null, past)
    private fun todo(t: String, done: Boolean = false) =
        TodayWeek.Row.Todo(TaskLine.parse("- [${if (done) "x" else " "}] $t")!!, "", late = false)
    private val now = TodayWeek.Row.Now(LocalTime(14, 0))

    private fun titles(c: LockCard.Card) = c.rows.map {
        when (it) { is TodayWeek.Row.Event -> it.title; is TodayWeek.Row.Todo -> it.task.title; is TodayWeek.Row.Now -> "NOW" }
    }

    @Test fun pastEventsAndDoneWorkAreLeftOut() {
        val c = LockCard.card(TodayWeek.Day(day, listOf(ev("เวร", past = true), todo("เสร็จแล้ว", done = true), now, ev("ร้านยา"), todo("ไปรับบัตร"))))
        assertEquals(listOf("NOW", "ร้านยา", "ไปรับบัตร"), titles(c))
        assertEquals(0, c.more)
    }

    @Test fun moreThanFourRowsFoldIntoMore() {
        val rows = listOf(now) + (1..7).map { ev("งาน$it") }
        val c = LockCard.card(TodayWeek.Day(day, rows))
        assertEquals(listOf("NOW", "งาน1", "งาน2", "งาน3", "งาน4"), titles(c))
        assertEquals(3, c.more)
    }

    @Test fun nowLineAfterTheCutIsDropped() {
        val rows = (1..5).map { ev("งาน$it") } + now
        val c = LockCard.card(TodayWeek.Day(day, rows))
        assertEquals(listOf("งาน1", "งาน2", "งาน3", "งาน4"), titles(c))
        assertEquals(1, c.more)
    }

    @Test fun anEmptyDayKeepsOnlyTheNowLine() {
        val c = LockCard.card(TodayWeek.Day(day, listOf(ev("จบแล้ว", past = true), now)))
        assertEquals(listOf("NOW"), titles(c))
    }
}
