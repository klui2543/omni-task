package app.omnitask.data

import app.omnitask.data.TaskLine.DateField
import app.omnitask.model.Priority
import app.omnitask.model.Status
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Lines are copied from the real TaskForge.md in the vault.
class TaskLineTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun parsesDoneTaskWithTagsReminderAndDates() {
        val t = TaskLine.parse(
            "- [x] ตรวจดูว่าระบบยังมีอะไรที่ยังไม่ได้ทำอีกบ้าง? #ตารางเวรเภสัช #remind-at-due ⏰ 02:30 ⏫ ➕ 2026-09-29 📅 2026-09-30 ✅ 2026-10-01"
        )!!
        assertEquals("ตรวจดูว่าระบบยังมีอะไรที่ยังไม่ได้ทำอีกบ้าง?", t.title)
        assertEquals(Status.DONE, t.status)
        assertEquals(Priority.HIGH, t.priority)
        assertEquals(d("2026-09-29"), t.created)
        assertEquals(d("2026-09-30"), t.due)
        assertEquals(d("2026-10-01"), t.done)
        assertEquals(listOf("ตารางเวรเภสัช", "remind-at-due"), t.tags)
    }

    @Test
    fun parsesRecurringTask() {
        val t = TaskLine.parse("- [ ] Routine 🔺 🔁 every day ⏳ 2026-09-29")!!
        assertEquals("Routine", t.title)
        assertEquals(Priority.HIGHEST, t.priority)
        assertEquals("every day", t.recurrence)
        assertEquals(d("2026-09-29"), t.scheduled)
        assertNull(t.due)
    }

    @Test
    fun parsesInProgressAndKeepsLinkInTitle() {
        assertEquals(Status.IN_PROGRESS, TaskLine.parse("- [/] Mock UX UI #peddose ⏫ ➕ 2026-10-02")!!.status)
        val t = TaskLine.parse("- [ ] จัดการ Note #Siriraj +[[29-09-2026]] 🔼 ➕ 2026-09-29")!!
        assertEquals("จัดการ Note +29-09-2026", t.title)
        assertEquals(Priority.MEDIUM, t.priority)
    }

    @Test
    fun ignoresNonTaskLines() {
        assertNull(TaskLine.parse("  - https://claude.ai/share/abc"))
        assertNull(TaskLine.parse("- [[📁 Folder/หลังบ้าน/TaskNotes/Tasks/Test]]"))
        assertNull(TaskLine.parse(""))
    }

    @Test
    fun setDateInsertsInTaskForgeOrder() {
        val raw = "- [ ] Flow น้ำเกลือ #Siriraj ⏫ ➕ 2026-10-02"
        val withDue = TaskLine.setDate(raw, DateField.DUE, d("2026-10-10"))
        assertEquals("- [ ] Flow น้ำเกลือ #Siriraj ⏫ ➕ 2026-10-02 📅 2026-10-10", withDue)
        val withScheduled = TaskLine.setDate(withDue, DateField.SCHEDULED, d("2026-10-08"))
        assertEquals("- [ ] Flow น้ำเกลือ #Siriraj ⏫ ➕ 2026-10-02 ⏳ 2026-10-08 📅 2026-10-10", withScheduled)
    }

    @Test
    fun setDateReplacesAndRemoves() {
        val raw = "- [ ] Routine 🔺 🔁 every day ⏳ 2026-09-29"
        assertEquals(
            "- [ ] Routine 🔺 🔁 every day ⏳ 2026-10-07",
            TaskLine.setDate(raw, DateField.SCHEDULED, d("2026-10-07")),
        )
        assertEquals("- [ ] Routine 🔺 🔁 every day", TaskLine.setDate(raw, DateField.SCHEDULED, null))
    }

    @Test
    fun setPriorityAddsChangesAndClears() {
        val raw = "- [ ] ย้าย Ticktick to taskforge ➕ 2026-09-29"
        val high = TaskLine.setPriority(raw, Priority.HIGH)
        assertEquals("- [ ] ย้าย Ticktick to taskforge ⏫ ➕ 2026-09-29", high)
        assertEquals("- [ ] ย้าย Ticktick to taskforge 🔺 ➕ 2026-09-29", TaskLine.setPriority(high, Priority.HIGHEST))
        assertEquals(raw, TaskLine.setPriority(high, Priority.NONE))
    }

    @Test
    fun setDoneRoundTrips() {
        val raw = "  - [ ] คู่มืออุปกรณ์? #Siriraj ➕ 2026-09-29"
        val done = TaskLine.setDone(raw, true, d("2026-10-07"))
        assertEquals("  - [x] คู่มืออุปกรณ์? #Siriraj ➕ 2026-09-29 ✅ 2026-10-07", done)
        assertEquals(raw, TaskLine.setDone(done, false, d("2026-10-07")))
    }

    @Test
    fun addTagGoesAfterPlainTagsAndBeforeReminder() {
        assertEquals(
            "- [ ] งาน #Siriraj #รอ/พี่เอ ⏫ ➕ 2026-10-02",
            TaskLine.addTag("- [ ] งาน #Siriraj ⏫ ➕ 2026-10-02", "#รอ/พี่เอ"),
        )
        assertEquals(
            "- [ ] งาน #a #new #remind-at-due ⏰ 02:30 ⏫ 📅 2026-09-30",
            TaskLine.addTag("- [ ] งาน #a #remind-at-due ⏰ 02:30 ⏫ 📅 2026-09-30", "new"),
        )
        assertEquals("- [ ] งาน #ร้าน-ยา 🔼 ➕ 2026-10-02", TaskLine.addTag("- [ ] งาน 🔼 ➕ 2026-10-02", "ร้าน ยา"))
        assertEquals("- [ ] งาน #x", TaskLine.addTag("- [ ] งาน", "x"))
    }

    @Test
    fun addTagSkipsDuplicatesAndRemoveTagKeepsTheRest() {
        val raw = "- [ ] งาน #a #b ⏫ ➕ 2026-10-02"
        assertEquals(raw, TaskLine.addTag(raw, "A"))
        assertEquals("- [ ] งาน #b ⏫ ➕ 2026-10-02", TaskLine.removeTag(raw, "a"))
        assertEquals("- [ ] งาน #a ⏫ ➕ 2026-10-02", TaskLine.removeTag(raw, "b"))
        assertEquals(raw, TaskLine.removeTag(raw, "missing"))
    }
}
