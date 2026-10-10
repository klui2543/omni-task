package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebViewsTest {

    // Today is Thursday 2026-10-08, so this week ends on Sunday 2026-10-11.
    private val note = listOf(
        "# งาน",
        "- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-07",
        "- [ ] ทบทวนเคส 🔺 📅 2026-10-08",
        "- [/] สไลด์ #peddose 🔼 🛫 2026-10-07 📅 2026-10-09",
        "    - [ ] ซ้อม ⏳ 2026-10-10",
        "- [ ] ทำเว็บ ⏫",
        "- [ ] จองตั๋ว 📅 2026-10-20",
        "- [x] จองห้อง ✅ 2026-10-06 📅 2026-10-08",
        "- [x] งานเก่า ✅ 2026-09-20",
        "- [-] ยกเลิก 📅 2026-10-09",
        "- [ ] โทรหาแม่ #remind-at-due ⏰ 10:00 📅 2026-10-09",
        "",
    ).joinToString("\n")

    private fun views(extra: String = "") = WebViews.build("f", "TaskForge.md", note, """{"today":"2026-10-08"$extra}""")

    @Test
    fun matrixPutsOpenTasksInQuadrantsSoonestFirst() {
        val r = views()
        // Urgent means due (or scheduled) by the end of this week; important means highest or high priority.
        assertTrue(r.contains("{\"id\":\"DO\",\"label\":\"ด่วนและสำคัญ\",\"urgent\":true,\"important\":true,\"keys\":[\"f#1\",\"f#2\"]}"), r)
        assertTrue(r.contains("{\"id\":\"PLAN\",\"label\":\"ไม่ด่วนแต่สำคัญ\",\"urgent\":false,\"important\":true,\"keys\":[\"f#5\"]}"), r)
        assertTrue(r.contains("\"id\":\"QUICK\",\"label\":\"ด่วนแต่ไม่สำคัญ\",\"urgent\":true,\"important\":false,\"keys\":[\"f#3\",\"f#10\"]"), r)
        assertTrue(r.contains("\"id\":\"LATER\",\"label\":\"ไม่ด่วนไม่สำคัญ\",\"urgent\":false,\"important\":false,\"keys\":[\"f#6\"]"), r)
    }

    @Test
    fun urgentRuleMovesTheLine() {
        val two = "- [ ] a \uD83D\uDCC5 2026-10-11\n- [ ] b \uD83D\uDCC5 2026-10-12"
        // This week ends on the 11th: a is urgent, b is not.
        val week = WebViews.build("f", "TaskForge.md", two, """{"today":"2026-10-08","urgent":"THIS_WEEK"}""")
        assertTrue(week.contains("\"id\":\"QUICK\",\"label\":\"ด่วนแต่ไม่สำคัญ\",\"urgent\":true,\"important\":false,\"keys\":[\"f#0\"]"), week)
        assertTrue(week.contains("\"id\":\"LATER\",\"label\":\"ไม่ด่วนไม่สำคัญ\",\"urgent\":false,\"important\":false,\"keys\":[\"f#1\"]"), week)
        // Within 2 days ends on the 10th: neither is.
        val days = WebViews.build("f", "TaskForge.md", two, """{"today":"2026-10-08","urgent":"TWO_DAYS"}""")
        assertTrue(days.contains("\"id\":\"QUICK\",\"label\":\"ด่วนแต่ไม่สำคัญ\",\"urgent\":true,\"important\":false,\"keys\":[]"), days)
        assertTrue(days.contains("\"keys\":[\"f#0\",\"f#1\"]"), days)
    }

    @Test
    fun kanbanKeepsEveryStatusAndLastWeeksDoneWork() {
        val r = views()
        // To do in the list's sort (due date first, undated last); the cancelled task has no column.
        assertTrue(r.contains("{\"status\":\"TODO\",\"keys\":[\"f#1\",\"f#2\",\"f#10\",\"f#6\",\"f#5\"]}"), r)
        assertTrue(r.contains("{\"status\":\"IN_PROGRESS\",\"keys\":[\"f#3\"]}"), r)
        // Done shows work finished in the last 7 days only, even though "hide done" is on.
        assertTrue(r.contains("{\"status\":\"DONE\",\"keys\":[\"f#7\"]}"), r)
        assertFalse(r.contains("f#9"), "cancelled work has no card")
    }

    @Test
    fun kanbanFollowsTheSortAndIgnoresTheStatusFilter() {
        val r = views(""","query":{"statuses":["DONE"],"sorts":[{"by":"PRIORITY","ascending":true}]}""")
        assertTrue(r.contains("{\"status\":\"TODO\",\"keys\":[\"f#2\",\"f#5\",\"f#1\",\"f#6\",\"f#10\"]}"), r)
        assertTrue(r.contains("{\"status\":\"IN_PROGRESS\",\"keys\":[\"f#3\"]}"), r)
    }

    @Test
    fun statusFilterNarrowsTheOtherViewsAndHideDoneDropsFinishedWork() {
        // "Hide done" off with the default statuses: finished work comes into the calendar and the Gantt.
        val shown = views(""","hideDone":false,"ganttFirst":"2026-10-06"""")
        assertTrue(shown.contains("{\"key\":\"f#7\"}"), shown)
        assertTrue(shown.contains("{\"key\":\"f#9\"}"), "cancelled work stays on the calendar list: $shown")
        assertTrue(shown.contains("{\"key\":\"f#7\",\"start\":\"2026-10-08\",\"end\":\"2026-10-08\"}"), shown)
        assertFalse(shown.contains("{\"key\":\"f#9\",\"start\""), "but not in the Gantt")
        // A chosen status narrows them to just that status.
        val doneOnly = views(""","query":{"statuses":["DONE"]},"hideDone":false""")
        assertTrue(doneOnly.contains("\"calendar\":[{\"key\":\"f#7\"},{\"key\":\"f#8\"},{\"key\":\"f#4\"}]"), doneOnly)
        // The default hides finished work.
        assertFalse(views().contains("{\"key\":\"f#7\"}"), "done work hidden by default")
    }

    @Test
    fun calendarAddsDatedSubtasksAndReminderTimes() {
        val r = views()
        assertTrue(r.contains("{\"key\":\"f#4\"}"), "a dated subtask is on the calendar: $r")
        assertTrue(r.contains("{\"key\":\"f#10\",\"at\":\"2026-10-09T10:00\"}"), r)
        // Without a date a subtask stays out.
        val plain = WebViews.build("f", "TaskForge.md", "- [ ] a\n    - [ ] b", """{"today":"2026-10-08"}""")
        assertTrue(plain.contains("\"calendar\":[{\"key\":\"f#0\"}]"), plain)
    }

    @Test
    fun ganttGroupsByProjectWithBarsFromStartToDue() {
        val r = views()
        // The project first, tasks without one last under their own caption.
        assertTrue(r.contains("{\"project\":\"peddose\",\"none\":false,\"spans\":[{\"key\":\"f#3\",\"start\":\"2026-10-07\",\"end\":\"2026-10-09\"}]}"), r)
        assertTrue(r.contains("\"project\":\"ไม่มีโปรเจกต์\",\"none\":true,\"spans\":[{\"key\":\"f#1\",\"start\":\"2026-10-07\",\"end\":\"2026-10-07\"}"), r)
        assertTrue(r.indexOf("peddose") < r.indexOf("ไม่มีโปรเจกต์"), r)
        // No dates at all: no bar.
        assertFalse(r.contains("{\"key\":\"f#4\",\"start"), "subtasks live inside their parent: $r")
        assertFalse(r.contains("{\"key\":\"f#5\",\"start"), r)
    }

    @Test
    fun ganttKeepsDoneWorkOnlyFromTheFirstDay() {
        val inRange = views(""","hideDone":false,"ganttFirst":"2026-10-06"""")
        assertTrue(inRange.contains("{\"key\":\"f#7\",\"start\""), inRange)
        val after = views(""","hideDone":false,"ganttFirst":"2026-10-07"""")
        assertFalse(after.contains("{\"key\":\"f#7\",\"start\""), after)
    }

    @Test
    fun draggingAcrossTheUrgentLineIsRefusedAndUpDownChangesImportance() {
        // Up: in progress, urgent, not important (QUICK) into DO becomes High.
        val up = WebCore.editTask(note, "- [/] สไลด์ #peddose 🔼 🛫 2026-10-07 📅 2026-10-09", 3, "2026-10-08", """{"op":"quadrant","value":"DO","field":"THIS_WEEK"}""")
        assertTrue(up.contains("\"ok\":true") && up.contains("- [/] สไลด์ #peddose ⏫ 🛫 2026-10-07 📅 2026-10-09"), up)
        // Down: DO into QUICK becomes Medium.
        val down = WebCore.editTask(note, "- [ ] ทบทวนเคส 🔺 📅 2026-10-08", 2, "2026-10-08", """{"op":"quadrant","value":"QUICK","field":"THIS_WEEK"}""")
        assertTrue(down.contains("\"ok\":true") && down.contains("- [ ] ทบทวนเคส 🔼 📅 2026-10-08"), down)
        // Sideways: urgency comes from the date, so nothing changes.
        val side = WebCore.editTask(note, "- [ ] ทบทวนเคส 🔺 📅 2026-10-08", 2, "2026-10-08", """{"op":"quadrant","value":"PLAN","field":"THIS_WEEK"}""")
        assertTrue(side.contains("\"ok\":false") && side.contains("\"error\":\"sideways\"") && side.contains("ลากได้แค่ขึ้นลง"), side)
        // The rule decides what is urgent: within 2 days, the 9th is, so this is up, not sideways.
        val rule = WebCore.editTask(note, "- [/] สไลด์ #peddose 🔼 🛫 2026-10-07 📅 2026-10-09", 3, "2026-10-08", """{"op":"quadrant","value":"DO","field":"TWO_DAYS"}""")
        assertTrue(rule.contains("\"ok\":true"), rule)
        // A task someone else changed is a conflict, as for every edit.
        val gone = WebCore.editTask(note, "- [ ] ทบทวนเคสเก่า", 2, "2026-10-08", """{"op":"quadrant","value":"QUICK","field":"THIS_WEEK"}""")
        assertTrue(gone.contains("\"error\":\"conflict\""), gone)
    }

    @Test
    fun quickAddFromAColumnStartsInThatStatus() {
        val r = WebViews.addTask("- [ ] a\n", "ซื้อนม พรุ่งนี้", "2026-10-08", "IN_PROGRESS")
        assertTrue(r.contains("\"ok\":true") && r.contains("- [/] ซื้อนม") && r.contains("2026-10-09"), r)
        assertTrue(WebViews.addTask("", "  ", "2026-10-08", "TODO").contains("\"error\":\"empty\""))
    }
}
