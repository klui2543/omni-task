package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.notify.CalendarEvent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

class QueryAndPlanTest {

    private val today = LocalDate.parse("2026-10-07")
    private fun t(line: String) = TaskLine.parse(line)!!

    @Test
    fun parsesDependenciesStatusEditsAndEmbeds() {
        val a = t("- [ ] Mock UX UI #peddose 🆔 ux1 ⏫ 📅 2026-10-09")
        val b = t("- [ ] API คำนวณขนาดยา #peddose ⛔ ux1,db2 📅 2026-10-20")
        assertEquals("ux1", a.id)
        assertEquals(listOf("ux1", "db2"), b.dependsOn)
        assertEquals("API คำนวณขนาดยา", b.title)
        assertEquals(setOf(b), Projects.blocked(listOf(a, b)))
        assertEquals("Mock UX UI", Projects.waitingOn(b, listOf(a, b)))

        assertEquals("- [/] งาน 📅 2026-10-09", TaskLine.setStatus("- [ ] งาน 📅 2026-10-09", Status.IN_PROGRESS, today))
        assertEquals("- [x] งาน 📅 2026-10-09 ✅ 2026-10-07", TaskLine.setStatus("- [/] งาน 📅 2026-10-09", Status.DONE, today))
        assertEquals("- [-] งาน", TaskLine.setStatus("- [x] งาน ✅ 2026-10-07", Status.CANCELLED, today))

        val withImg = a.copy(notes = listOf("ดูรูปนี้", "![[Omni-2026-10-07-143000.webp]]"))
        assertEquals(listOf("Omni-2026-10-07-143000.webp"), withImg.attachments)
        assertEquals(listOf("ดูรูปนี้"), withImg.textNotes)
    }

    @Test
    fun queryFiltersNestedTagsGroupsAndSortsBothWays() {
        val tasks = listOf(
            t("- [ ] ตอบพี่เอ #รอ/พี่เอ 🔼 📅 2026-10-08"),
            t("- [ ] เลยแล้ว ⏫ 📅 2026-10-05"),
            t("- [ ] วันนี้ 🔺 📅 2026-10-07"),
            t("- [x] เสร็จแล้ว 📅 2026-10-06 ✅ 2026-10-06"),
        )
        val waiting = TaskQuery(tags = setOf("รอ")).run(tasks, today).flatMap { it.tasks }
        assertEquals(listOf("ตอบพี่เอ"), waiting.map { it.title })

        val groups = TaskQuery().run(tasks, today)
        assertEquals(listOf("เลยกำหนด", "วันนี้", "สัปดาห์นี้"), groups.map { it.label })
        assertEquals(Tone.ALERT, groups.first().tone)

        val desc = TaskQuery(groupBy = GroupBy.NONE, ascending = false).run(tasks, today).single().tasks
        assertEquals(listOf("ตอบพี่เอ", "วันนี้", "เลยแล้ว"), desc.map { it.title })
        assertEquals(1, TaskQuery(tags = setOf("x")).activeFilters)
    }

    @Test
    fun allDayAndLongEventsGoOnTopApartFromTheTasks() {
        val events = listOf(
            CalendarEvent(1, "วันหยุด", LocalDateTime.parse("2026-10-07T00:00"), LocalDateTime.parse("2026-10-08T00:00"), allDay = true),
            CalendarEvent(2, "ประชุมวิชาการ", LocalDateTime.parse("2026-10-06T00:00"), LocalDateTime.parse("2026-10-09T00:00"), allDay = true),
            CalendarEvent(3, "เวรดึก", LocalDateTime.parse("2026-10-06T20:00"), LocalDateTime.parse("2026-10-07T08:00")),
        )
        val plan = DayPlan.build(listOf(t("- [ ] ไม่มีเวลา ⏳ 2026-10-07")), events, today)
        assertEquals(listOf(DayPlan.Part.ALL_DAY, DayPlan.Part.ANYTIME), plan.map { it.part })
        assertEquals(3, plan[0].items.size)
        assertTrue(plan[1].items.all { it is DayPlan.Item.TaskItem })
        assertEquals(null, DayPlan.dayOf(events[0], today))
        assertEquals(2 to 3, DayPlan.dayOf(events[1], today))
        assertEquals(2 to 2, DayPlan.dayOf(events[2], today))
    }

    @Test
    fun dayPlanSplitsTheDayAndCountsFreeTime() {
        val tasks = listOf(
            t("- [ ] ค้าง 📅 2026-10-05"),
            t("- [ ] เช้า #remind-at-due ⏰ 07:00 📅 2026-10-07"),
            t("- [ ] ไม่มีเวลา ⏳ 2026-10-07"),
            t("- [ ] พรุ่งนี้ 📅 2026-10-08"),
        )
        val events = listOf(
            CalendarEvent(1, "เวร OPD", LocalDateTime.parse("2026-10-07T08:00"), LocalDateTime.parse("2026-10-07T12:00")),
            CalendarEvent(2, "ประชุม", LocalDateTime.parse("2026-10-07T11:30"), LocalDateTime.parse("2026-10-07T13:00")),
        )
        val plan = DayPlan.build(tasks, events, today)
        assertEquals(listOf(DayPlan.Part.LATE, DayPlan.Part.MORNING, DayPlan.Part.ANYTIME), plan.map { it.part })
        val morning = plan[1].items
        assertEquals(LocalTime.of(7, 0), morning.first().time)
        assertTrue(morning.any { it is DayPlan.Item.EventItem })
        // 07:00 to 21:00 is 840 minutes; 08:00 to 13:00 is busy.
        assertEquals(540L, DayPlan.freeMinutes(events, today))
    }

    @Test
    fun dayPlanKeepsWorkScheduledForADayGoneBy() {
        val tasks = listOf(
            t("- [ ] นัดไว้เมื่อวาน ⏳ 2026-10-06"),
            t("- [ ] นัดไว้แล้วครบทีหลัง ⏳ 2026-10-05 📅 2026-10-10"),
            t("- [ ] นัดไว้แล้วครบวันนี้ ⏳ 2026-10-05 📅 2026-10-07"),
            t("- [ ] นัดพรุ่งนี้ ⏳ 2026-10-08"),
            t("- [ ] How to ดูหุ้น #อนาคต #remind-at-scheduled 🎯 13:30 ⏳ 2026-10-05"),
        )
        val plan = DayPlan.build(tasks, emptyList(), today)
        assertEquals(listOf(DayPlan.Part.LATE, DayPlan.Part.ANYTIME), plan.map { it.part })
        assertEquals(listOf("นัดไว้เมื่อวาน", "นัดไว้แล้วครบทีหลัง", "How to ดูหุ้น"), plan[0].items.map { (it as DayPlan.Item.TaskItem).task.title })
        assertEquals(listOf("นัดไว้แล้วครบวันนี้"), plan[1].items.map { (it as DayPlan.Item.TaskItem).task.title })
    }

    @Test
    fun dayPlanKeepsDoneTasksAndTracksEventsAgainstTheClock() {
        val tasks = listOf(
            t("- [x] เสร็จแล้ว #remind-at-due ⏰ 07:00 📅 2026-10-07 ✅ 2026-10-07"),
            t("- [x] ค้างที่เพิ่งเสร็จ 📅 2026-10-05 ✅ 2026-10-07"),
            t("- [x] เสร็จเมื่อวาน 📅 2026-10-06 ✅ 2026-10-06"),
            t("- [ ] ยังไม่เสร็จ #remind-at-due ⏰ 09:00 📅 2026-10-07"),
        )
        val withoutDone = DayPlan.build(tasks, emptyList(), today).flatMap { it.items }.map { (it as DayPlan.Item.TaskItem).task.title }
        assertEquals(listOf("ยังไม่เสร็จ"), withoutDone)
        val plan = DayPlan.build(tasks, emptyList(), today, includeDone = true)
        assertEquals(listOf(DayPlan.Part.LATE, DayPlan.Part.MORNING), plan.map { it.part })
        assertEquals(listOf("เสร็จแล้ว", "ยังไม่เสร็จ"), plan[1].items.map { (it as DayPlan.Item.TaskItem).task.title })
        assertEquals(listOf("ค้างที่เพิ่งเสร็จ"), plan[0].items.map { (it as DayPlan.Item.TaskItem).task.title })

        val e = CalendarEvent(1, "ประชุม", LocalDateTime.parse("2026-10-07T10:00"), LocalDateTime.parse("2026-10-07T11:00"))
        assertEquals(DayPlan.Phase.UPCOMING, DayPlan.phase(e, LocalDateTime.parse("2026-10-07T09:59")))
        assertEquals(DayPlan.Phase.ACTIVE, DayPlan.phase(e, LocalDateTime.parse("2026-10-07T10:00")))
        assertEquals(DayPlan.Phase.PAST, DayPlan.phase(e, LocalDateTime.parse("2026-10-07T11:00")))
        assertEquals(0.5f, DayPlan.progress(e, LocalDateTime.parse("2026-10-07T10:30")))
        assertEquals(DayPlan.Phase.UPCOMING, DayPlan.phase(e.copy(allDay = true), LocalDateTime.parse("2026-10-07T10:30")))
    }

    @Test
    fun multiLevelSortKeepsMissingValuesLast() {
        val today = LocalDate.parse("2026-10-07")
        val a = TaskLine.parse("- [ ] ก ⏫ 📅 2026-10-09")!!
        val b = TaskLine.parse("- [ ] ข ⏫ 📅 2026-10-08")!!
        val c = TaskLine.parse("- [ ] ค 🔼 📅 2026-10-07")!!
        val d = TaskLine.parse("- [ ] ง ⏫")!!
        val q = TaskQuery(groupBy = GroupBy.NONE, sortBy = SortBy.PRIORITY, thenBy = listOf(SortBy.DUE to false))
        assertEquals(listOf(a, b, d, c), q.run(listOf(c, d, a, b), today).single().tasks)
    }
}

