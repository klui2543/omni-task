package app.omnitask.notify

import app.omnitask.data.TaskLine
import app.omnitask.model.ReminderOn
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

class AlarmPlanTest {

    private val now = LocalDateTime.parse("2026-10-07T10:30")
    private fun t(line: String) = TaskLine.parse(line)!!

    @Test
    fun readsTaskForgeReminderTimes() {
        val due = t("- [ ] งาน #remind-at-due ⏰ 02:30 ⏫ 📅 2026-10-08")
        assertEquals(LocalTime.of(2, 30), due.reminderTime)
        assertEquals(ReminderOn.DUE, due.reminderOn)
        assertEquals(LocalDateTime.parse("2026-10-08T02:30"), due.reminderAt)
        assertEquals("งาน", due.title)

        val sched = t("- [ ] ประชุม #remind-at-scheduled 🎯 9:05 ⏳ 2026-10-09 📅 2026-10-12")
        assertEquals(ReminderOn.SCHEDULED, sched.reminderOn)
        assertEquals(LocalDateTime.parse("2026-10-09T09:05"), sched.reminderAt)

        assertNull(t("- [ ] ไม่มีวันที่ ⏰ 08:00").reminderAt)
    }

    @Test
    fun plansOnlyOpenFutureRemindersWithinAWeek() {
        val tasks = listOf(
            t("- [ ] พรุ่งนี้ ⏰ 08:00 📅 2026-10-08"),
            t("- [ ] ผ่านไปแล้ว ⏰ 08:00 📅 2026-10-07"),
            t("- [x] เสร็จแล้ว ⏰ 12:00 📅 2026-10-07 ✅ 2026-10-07"),
            t("- [ ] ไกลเกิน ⏰ 08:00 📅 2026-10-20"),
        )
        val plan = AlarmPlan.plan(tasks, emptyList(), NotifySettings(digestTimes = emptyList(), weeklyReview = false), now)
        val taskAlarms = plan.filter { it.kind == AlarmKind.TASK }
        assertEquals(listOf("พรุ่งนี้"), taskAlarms.map { it.title })
        assertEquals(LocalDateTime.parse("2026-10-08T08:00"), taskAlarms.single().at)
        assertTrue(plan.any { it.kind == AlarmKind.RESCAN })
    }

    @Test
    fun digestsRollToTomorrowAndWeeklyIsNextSunday() {
        val s = NotifySettings(digestTimes = listOf(LocalTime.of(6, 30), LocalTime.of(17, 0)))
        val plan = AlarmPlan.plan(emptyList(), emptyList(), s, now)
        val digests = plan.filter { it.kind == AlarmKind.DIGEST }.map { it.at }.toSet()
        assertEquals(setOf(LocalDateTime.parse("2026-10-08T06:30"), LocalDateTime.parse("2026-10-07T17:00")), digests)
        assertEquals(LocalDateTime.parse("2026-10-11T20:00"), plan.single { it.kind == AlarmKind.WEEKLY }.at)
    }

    @Test
    fun calendarEventsUseLeadTimeAndCanBeTurnedOff() {
        val events = listOf(
            CalendarEvent(1, "ประชุมทีม", LocalDateTime.parse("2026-10-07T13:00"), LocalDateTime.parse("2026-10-07T14:00")),
            CalendarEvent(2, "เริ่มไปแล้ว", LocalDateTime.parse("2026-10-07T10:35"), LocalDateTime.parse("2026-10-07T11:00")),
        )
        val on = AlarmPlan.plan(emptyList(), events, NotifySettings(calendarLeadMinutes = 15), now).filter { it.kind == AlarmKind.EVENT }
        assertEquals(listOf(LocalDateTime.parse("2026-10-07T12:45")), on.map { it.at })
        assertEquals("อีก 15 นาที (13:00 ถึง 14:00)", on.single().text)
        val off = AlarmPlan.plan(emptyList(), events, NotifySettings(calendarEvents = false), now)
        assertTrue(off.none { it.kind == AlarmKind.EVENT })
    }

    @Test
    fun dailyDigestListsWhatIsSwitchedOn() {
        val today = LocalDate.parse("2026-10-07")
        val tasks = listOf(
            t("- [ ] เลย ⏫ 📅 2026-10-05"),
            t("- [ ] วันนี้ 📅 2026-10-07"),
            t("- [ ] ตอบ #รอ/พี่เอ ➕ 2026-09-25"),
        )
        val all = Digest.daily(tasks, today, NotifySettings())!!
        assertEquals("เลยกำหนด 1, วันนี้ 1, คนรอ 1", all.title)
        assertTrue(all.lines.any { it == "รอ พี่เอ 12 วัน: ตอบ" })
        val noWaiting = Digest.daily(tasks, today, NotifySettings(digestWaiting = false))!!
        assertTrue(noWaiting.lines.none { it.startsWith("รอ") })
    }
}
