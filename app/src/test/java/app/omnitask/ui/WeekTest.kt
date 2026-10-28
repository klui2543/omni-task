package app.omnitask.ui

import app.omnitask.model.Status
import app.omnitask.model.Task
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The next week on the today page: tasks from tomorrow to 7 days ahead, by the day they are due or scheduled. */
class WeekTest {
    private val today = LocalDate(2026, 10, 28)
    private fun task(title: String, due: LocalDate? = null, scheduled: LocalDate? = null) =
        Task("- [ ] $title", title, Status.TODO, due = due, scheduled = scheduled)

    @Test
    fun keepsTomorrowToTheEndOfNextWeekInDayOrder() {
        // Wednesday 28 Oct: this week ends Sunday 1 Nov, next week is Monday 2 to Sunday 8 Nov.
        val tasks = listOf(
            task("today", due = today),
            task("next sunday", due = LocalDate(2026, 11, 8)),
            task("next-next monday", due = LocalDate(2026, 11, 9)),
            task("this sunday", due = LocalDate(2026, 11, 1)),
            task("tomorrow", scheduled = LocalDate(2026, 10, 29)),
            task("no date"),
        )
        assertEquals(listOf("tomorrow", "this sunday", "next sunday"), Week.ahead(tasks, today).map { it.task.title })
    }

    @Test
    fun weeksEndOnSunday() {
        assertEquals(LocalDate(2026, 11, 1), Week.sunday(today))
        assertEquals(LocalDate(2026, 11, 1), Week.sunday(LocalDate(2026, 11, 1)))
        assertEquals(LocalDate(2026, 11, 1), Week.sunday(LocalDate(2026, 10, 26)))
    }

    @Test
    fun aDueDateInTheWeekWinsOverAScheduledOne() {
        val t = task("both", due = LocalDate(2026, 11, 2), scheduled = LocalDate(2026, 10, 30))
        val item = Week.ahead(listOf(t), today).single()
        assertEquals(LocalDate(2026, 11, 2), item.day)
        assertEquals(true, item.due)
        // Due later than the week: the scheduled day inside it is used.
        val later = task("later", due = LocalDate(2026, 11, 20), scheduled = LocalDate(2026, 10, 30))
        assertEquals(false, Week.ahead(listOf(later), today).single().due)
    }
}
