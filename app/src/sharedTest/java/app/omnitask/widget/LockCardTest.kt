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

    // CONTROL: LockCard.card did not change; this only pins that a running event is kept like any other.
    @Test fun anEventHappeningNowStaysWithItsLine() {
        val shop = TodayWeek.Row.Event("ร้านยาโลตัส", "11:00 ถึง 20:00", null, past = false, run = 0.26f)
        val c = LockCard.card(TodayWeek.Day(day, listOf(ev("เวร", past = true), shop, todo("ไปรับบัตร"))))
        assertEquals(listOf(shop), c.rows.take(1))
        assertEquals(listOf("ร้านยาโลตัส", "ไปรับบัตร"), titles(c))
    }

    @Test fun anEmptyDayKeepsOnlyTheNowLine() {
        val c = LockCard.card(TodayWeek.Day(day, listOf(ev("จบแล้ว", past = true), now)))
        assertEquals(listOf("NOW"), titles(c))
    }

    /**
     * Real case (owner, 2026-10-31 19:58): the folded card's headline was "ไปรับบัตรเปนเพื่อนพี่กาน", an all-day
     * event of the calendar "เบบี้รักยิ้ม". With way ค the card holds only the owner's own.
     */
    @Test fun anotherPersonsCalendarIsLeftOffTheCard() {
        val partner = 5L
        fun e(id: Long, t: String, a: String, b: String, cal: Long, allDay: Boolean = false) = app.omnitask.notify.CalendarEvent(
            id, t, kotlinx.datetime.LocalDateTime.parse(a), kotlinx.datetime.LocalDateTime.parse(b), allDay = allDay, calendarId = cal,
        )
        val events = listOf(
            e(1, "ไปรับบัตรเปนเพื่อนพี่กาน", "2026-10-31T00:00", "2026-11-01T00:00", partner, allDay = true),
            e(2, "กินข้าวกับที่บ้าน", "2026-10-31T21:00", "2026-10-31T21:30", partner),
            e(3, "ร้านยาโลตัส", "2026-10-31T11:00", "2026-10-31T21:00", 1),
        )
        val now = kotlinx.datetime.LocalDateTime.parse("2026-10-31T19:58")
        val c = LockCard.card(LockCard.today(day, now, emptyList(), events, { it == partner }) { null })
        assertEquals(listOf("ร้านยาโลตัส"), titles(c).filter { it != "NOW" })
        // As before the change (nothing counted as another person's): their all-day event led the card.
        val before = LockCard.card(LockCard.today(day, now, emptyList(), events, { false }) { null })
        assertEquals("ไปรับบัตรเปนเพื่อนพี่กาน", titles(before).first())
    }
}
