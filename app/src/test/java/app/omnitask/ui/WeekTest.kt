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
    fun keepsTomorrowToSevenDaysAheadInDayOrder() {
        val tasks = listOf(
            task("today", due = today),
            task("in 7", due = LocalDate(2026, 11, 4)),
            task("in 8", due = LocalDate(2026, 11, 5)),
            task("tomorrow", scheduled = LocalDate(2026, 10, 29)),
            task("no date"),
        )
        assertEquals(listOf("tomorrow", "in 7"), Week.ahead(tasks, today).map { it.task.title })
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
