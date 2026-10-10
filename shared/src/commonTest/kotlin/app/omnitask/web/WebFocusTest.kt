package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebFocusTest {

    private val note = listOf(
        "- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-07",
        "- [ ] ทบทวนเคส 🔺 ➕ 2026-10-01 📅 2026-10-08",
        "- [/] สไลด์ประชุม #remind-at-due ⏰ 10:00 ➕ 2026-10-01 📅 2026-10-08",
        "    - [x] โครง ✅ 2026-10-07",
        "    - [ ] ซ้อม",
        "- [ ] โทรหาแม่ #remind-at-due ⏰ 19:30 ➕ 2026-10-01 📅 2026-10-08",
        "- [ ] ตอบอีเมลทุน #รอ/พี่เอ ➕ 2026-09-26",
        "- [ ] อ่าน Deep Work #อนาคต ➕ 2026-09-17",
        "- [ ] ทำเว็บคำนวณยา ➕ 2026-08-01",
        "- [x] เสร็จเมื่อกี้ ✅ 2026-10-08 📅 2026-10-08",
    ).joinToString("\n")

    private fun state(extra: String = "", now: String = "2026-10-08T09:20") = """{"now":"$now"$extra}"""
    private fun focus(extra: String = "", now: String = "2026-10-08T09:20") = WebFocus.build("f", "TaskForge.md", note, state(extra, now))

    @Test
    fun briefHoldsWhatIsDueWhoWaitsAndTheFuturePick() {
        val r = focus()
        // Late work sits in its own section, today's by time of day, with the red line before the first timed task to come.
        assertTrue(r.contains("\"label\":\"ค้างอยู่\",\"late\":true,\"tasks\":1"), r)
        assertTrue(r.contains("{\"type\":\"now\",\"time\":\"09:20\""), r)
        assertTrue(r.contains("\"lead\":\"เลย 1 วัน\",\"late\":true"), r)
        assertTrue(r.contains("\"extra\":\", กำลังทำ, งานย่อย 1/2\""), r)
        // 3 open tasks for today plus 1 overdue, and 1 done today.
        assertTrue(r.contains("\"ring\":{\"done\":1,\"total\":5,\"overdue\":1,\"events\":0}"), r)
        assertTrue(r.contains("\"waiting\":[{\"key\":\"f#6\",\"who\":\"พี่เอ\",\"age\":12}]"), r)
        assertTrue(r.contains("\"future\":[\"f#7\"]"), r)
        assertTrue(r.contains("\"softDate\":\"2026-10-10\""), r)
    }

    @Test
    fun suggestionsAndReviewFollowTheSharedRules() {
        val r = focus()
        // Waiting 12 days and not marked important: offer to raise it.
        assertTrue(r.contains("\"kind\":\"RAISE_PRIORITY\""), r)
        assertTrue(r.contains("มีคนรอมา 12 วันแล้ว"), r)
        // The no-deadline web task, 68 days old, is due for review; so is the 21 day old future one.
        assertTrue(r.contains("\"review\":[{\"key\":\"f#8\""), r)
        val reviewed = focus(""","reviewed":{"ทำเว็บคำนวณยา":"2026-10-01","อ่าน Deep Work":"2026-10-01"}""")
        assertTrue(reviewed.contains("\"review\":[]"), reviewed)
        val dismissed = focus(""","dismissed":["RAISE_PRIORITY:ตอบอีเมลทุน"]""")
        assertFalse(dismissed.contains("RAISE_PRIORITY"), dismissed)
    }

    @Test
    fun skippedFutureWorkRotatesAndTheCountdownCountsDown() {
        val skipped = focus(""","skippedToday":["อ่าน Deep Work"]""")
        assertTrue(skipped.contains("\"future\":[]"), skipped)
        val r = focus(""","countdown":"ส่งรายงาน"""")
        assertTrue(r.contains("\"countdown\":{\"key\":\"f#0\",\"title\":\"ส่งรายงาน\",\"text\":\"เลยมา 1 วัน\",\"late\":true}"), r)
    }

    @Test
    fun calendarEventsJoinThePlanAndTakeFreeTime() {
        val events = ""","events":[{"id":1,"title":"ประชุมทีม","begin":"2026-10-08T13:00","end":"2026-10-08T14:30"}]"""
        val r = focus(events)
        assertTrue(r.contains("\"type\":\"event\",\"time\":\"13:00\",\"title\":\"ประชุมทีม\""), r)
        assertTrue(r.contains("\"range\":\"13:00 ถึง 14:30, Google Calendar\""), r)
        assertTrue(r.contains("\"events\":1"), r)
        // From 09:20 to the usual 22:30 bedtime, less the 90 minute meeting.
        assertTrue(r.contains("\"third\":{\"kind\":\"free\",\"minutes\":${13 * 60 + 10 - 90}"), r)
    }

    @Test
    fun anEventCarriesItsGoogleCalendarLinkIntoThePlan() {
        val events = ""","events":[{"id":7,"title":"ประชุมทีม","begin":"2026-10-08T13:00","end":"2026-10-08T14:30","link":"https://www.google.com/calendar/event?eid=abc"},{"id":8,"title":"ไม่มีลิงก์","begin":"2026-10-08T16:00","end":"2026-10-08T17:00"}]"""
        val r = focus(events)
        assertTrue(r.contains("\"range\":\"13:00 ถึง 14:30, Google Calendar\",\"link\":\"https://www.google.com/calendar/event?eid=abc\""), r)
        assertTrue(r.contains("\"range\":\"16:00 ถึง 17:00, Google Calendar\"}"), r)
    }

    @Test
    fun theEveningShowsTheNightAndTonightsBedtimeCanBeMoved() {
        val usual = focus(now = "2026-10-08T20:00")
        assertTrue(usual.contains("\"third\":{\"kind\":\"night\""), usual)
        assertTrue(usual.contains("\"bedAt\":\"22:30\""), usual)
        assertTrue(usual.contains("\"bedtime\":\"22:30\",\"evening\":\"2026-10-08\""), usual)
        val moved = focus(""","tonightBed":{"evening":"2026-10-08","time":"23:30"}""", now = "2026-10-08T20:00")
        assertTrue(moved.contains("\"bedAt\":\"23:30\""), moved)
        // A pick for another evening is ignored.
        val old = focus(""","tonightBed":{"evening":"2026-10-07","time":"23:30"}""", now = "2026-10-08T20:00")
        assertTrue(old.contains("\"bedAt\":\"22:30\""), old)
    }

    @Test
    fun theProfileNoteSetsTheUsualSleepTimes() {
        val profile = "# โปรไฟล์\\n- ตื่น: 06:00\\n- นอน: 23:00\\n"
        val r = focus(""","profile":"$profile"""", now = "2026-10-08T20:00")
        assertTrue(r.contains("\"bedAt\":\"23:00\""), r)
    }

    @Test
    fun kindOfATaskIsSwappedWithoutTouchingOtherTags() {
        val line = "- [ ] ทำเว็บ #peddose #รอ/พี่เอ"
        val r = WebCore.editTask(line, line, 0, "2026-10-08", """{"op":"kind","value":"FUTURE"}""")
        assertTrue(r.contains("- [ ] ทำเว็บ #peddose #อนาคต"), r)
        val back = WebCore.editTask(line, line, 0, "2026-10-08", """{"op":"kind","value":"NORMAL"}""")
        assertTrue(back.contains("- [ ] ทำเว็บ #peddose\""), back)
    }

    @Test
    fun tasksInAParkedBranchAreLeftOutOfFocus() {
        val note = "- [ ] ทำเว็บ #peddose/แอป 📅 2026-10-08\n- [ ] เขียนรายงาน #peddose 📅 2026-10-08"
        val plain = WebFocus.build("f", "TaskForge.md", note, state())
        assertTrue(plain.contains("f#0") && plain.contains("f#1"), plain)
        val parked = WebFocus.build("f", "TaskForge.md", note, state(""","branches":["peddose\tแอป\tPARKED"]"""))
        assertFalse(parked.contains("f#0"), parked)
        assertTrue(parked.contains("f#1"), parked)
    }
}
