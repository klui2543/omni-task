package app.omnitask.widget

import app.omnitask.data.TaskLine
import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*
import org.junit.Test

/** The Today widget's days and pages: whole days per page, never a day cut unless it alone is too tall. */
class TodayWeekTest {

    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun at(m: Int, day: Int, h: Int, min: Int = 0) = LocalDateTime(d(m, day), LocalTime(h, min))
    private fun allDay(title: String, m: Int, day: Int, days: Int = 1): CalendarEvent {
        val end = d(m, day).plusDays(days.toLong())
        return CalendarEvent(title.hashCode().toLong(), title, LocalDateTime(d(m, day), LocalTime(0, 0)), LocalDateTime(end, LocalTime(0, 0)), allDay = true)
    }
    private fun timed(title: String, from: LocalDateTime, to: LocalDateTime) = CalendarEvent(title.hashCode().toLong(), title, from, to)

    // The owner's real fortnight from 30 Oct 2026 (calendar, read 2026-10-30), and today's tasks from the widget screenshot.
    private val events = listOf(
        allDay("ซ้อมเวรเภสัช ห้องยา 103", 10, 30),
        allDay("ไปรับบัตรเปนเพื่อนพี่กาน", 10, 31),
        timed("O11 รอบ 3", at(10, 31, 2, 30), at(10, 31, 8, 30)),
        timed("ร้านยาโลตัส", at(10, 31, 11), at(10, 31, 20)),
        allDay("ไปช่วยงานลุงจอร์จ", 11, 1),
        allDay("ล้าง CPAP", 11, 1),
        timed("จัดการใบปริญญา", at(11, 1, 0), at(11, 1, 0)),
        allDay("จ่ายบัตร Bangkok", 11, 3),
        timed("O11 รอบ 3 จาก อิง", at(11, 3, 2, 30), at(11, 3, 8, 30)),
        timed("ไปตรวจตา รพ.", at(11, 4, 16, 30), at(11, 4, 17, 30)),
        timed("นัดล้างเล็บ", at(11, 5, 17), at(11, 5, 18)),
        allDay("ไปนครปฐม", 11, 10, days = 2),
    )
    private val tasks = listOfNotNull(
        TaskLine.parse("- [x] แก้ไขระบบแจ้งเตือนของ PD manager 📅 2026-10-30 ✅ 2026-10-30"),
        TaskLine.parse("- [ ] Routine #remind-at-due ⏰ 18:30 📅 2026-10-30"),
        TaskLine.parse("- [ ] 900+ hours of Learning AI Agents #My-space 📅 2026-10-30"),
        TaskLine.parse("- [ ] เขียนถ้อยคำให้ครบ 25 ข้อ #Siriraj 📅 2026-10-21"),
        TaskLine.parse("- [ ] ส่งรายงาน 📅 2026-11-04"),
    )
    private val today = d(10, 30)
    private val days = TodayWeek.days(today, at(10, 30, 17, 22), tasks, events, 14) { null }

    @Test
    fun todayHasItsWorkAndTheNowLineButNotOverdueWork() {
        val rows = days[0].rows
        check(rows.any { it is TodayWeek.Row.Now }) { "no now line: $rows" }
        val titles = rows.mapNotNull { (it as? TodayWeek.Row.Todo)?.task?.title ?: (it as? TodayWeek.Row.Event)?.title }
        check(titles.first() == "ซ้อมเวรเภสัช ห้องยา 103") { titles.toString() }
        check(titles.none { it.startsWith("เขียนถ้อยคำ") }) { "overdue work belongs in the header: $titles" }
        // Routine at 18:30 comes after the now line at 17:22.
        check(rows.indexOfFirst { it is TodayWeek.Row.Now } < rows.indexOfFirst { (it as? TodayWeek.Row.Todo)?.task?.title == "Routine" })
    }

    @Test
    fun laterDaysHaveTheirEventsAndDatedWork() {
        check(days[2].rows.size == 3) { days[2].rows.toString() }
        check(days[3].count == 0) { "2 Nov is free: ${days[3].rows}" }
        check(days[5].rows.any { (it as? TodayWeek.Row.Todo)?.task?.title == "ส่งรายงาน" }) { days[5].rows.toString() }
        // A two-day event shows on both days, with which day it is.
        check((days[11].rows.single() as TodayWeek.Row.Event).time == "วันที่ 1/2" || (days[11].rows.single() as TodayWeek.Row.Event).time == "day 1/2")
        check(days[12].rows.isNotEmpty())
    }

    @Test
    fun pagesHoldWholeDaysThatFit() {
        val space = 402 - 56
        val pages = TodayWeek.pages(days, space)
        // Every day appears once, in order, and no page is cut.
        check(pages.flatMap { it.days } == days)
        pages.forEach { p ->
            check(p.cap == null)
            val h = p.days.sumOf { TodayWeek.height(it) } + TodayWeek.DAY_GAP * (p.days.size - 1)
            check(h <= space) { "page from ${p.days.first().date} is $h dp in $space" }
        }
        // The busy start fits three days; the next page takes more of the quiet ones.
        check(pages[0].days.size == 3) { pages.map { it.days.size }.toString() }
        check(pages[1].days.size > 3) { pages.map { it.days.size }.toString() }
    }

    @Test
    fun aDayTallerThanTheWidgetIsCutWithMore() {
        val busy = TodayWeek.Day(today, List(12) { TodayWeek.Row.Event("นัด $it", "", null, false) })
        val pages = TodayWeek.pages(listOf(busy, days[1]), 200)
        val cap = pages[0].cap ?: error("not cut")
        check(TodayWeek.height(busy, cap) <= 200 && TodayWeek.height(busy, cap + 1) > 200)
        check(pages[1].days == listOf(days[1]))
    }
}
