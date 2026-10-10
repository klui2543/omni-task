package app.omnitask.web

import app.omnitask.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebAssistantTest {

    private val note = listOf(
        "- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-12",
        "- [ ] ทบทวนเคส 🔺 ➕ 2026-10-01 📅 2026-10-08",
        "- [ ] ตอบอีเมลทุน #รอ/พี่เอ ➕ 2026-09-26",
        "- [ ] อ่าน Deep Work #อนาคต ➕ 2026-09-17",
        "- [ ] ทำเว็บคำนวณยา ➕ 2026-08-01",
        "- [ ] จัดตู้หนังสือ ➕ 2026-09-01",
    ).joinToString("\n")

    private val profileText = Profile(exists = true).render()

    private fun state(extra: String = "", now: String = "2026-10-08T09:20") = """{"now":"$now"$extra}"""

    // ---- Profile ----

    @Test
    fun noProfileNoteReadsAsDefaultsThatDoNotExist() {
        val r = WebAssistant.profile(null, "2026-10-08")
        assertTrue(r.contains("\"exists\":false"), r)
        assertTrue(r.contains("\"wake\":\"06:30\"") && r.contains("\"sleep\":\"22:30\""), r)
    }

    @Test
    fun interviewAnswersLandInTheNoteAndSurviveReading() {
        val text = WebAssistant.profileApply(
            null,
            """{"wake":"05:30","sleep":"23:00","focusFrom":"13:00","focusTo":"16:00","exercise":"06:00","described":{"ตื่น":"แล้วแต่เวร"}}""",
            "2026-10-08",
        )
        val p = Profile.parse(text)
        assertTrue(p.exists)
        assertEquals("05:30", Profile.hm(p.wake))
        assertEquals("23:00", Profile.hm(p.sleep))
        assertEquals("13:00", Profile.hm(p.focusFrom))
        assertEquals("16:00", Profile.hm(p.focusTo))
        assertEquals("06:00", Profile.hm(p.exercise))
        assertEquals(listOf("ตื่น: แล้วแต่เวร"), p.remembered)
        assertEquals("2026-10-08", p.updated.toString())
        assertTrue(text.contains("- ตื่น: 05:30"), text)
    }

    @Test
    fun aChangeKeepsWhatElseWasEditedInObsidian() {
        // The owner edited the focus window and a memory in Obsidian; the settings page then changes only the sleep time.
        val edited = profileText.replace("- ช่วงสมองดี: 08:00-11:00", "- ช่วงสมองดี: 10:00-13:00") + "- วันเสาร์เขียนงานได้ดี\n"
        val text = WebAssistant.profileApply(edited, """{"sleep":"22:00","wake":"07:00"}""", "2026-10-08")
        val p = Profile.parse(text)
        assertEquals("22:00", Profile.hm(p.sleep))
        assertEquals("07:00", Profile.hm(p.wake))
        assertEquals("10:00", Profile.hm(p.focusFrom))
        assertEquals(listOf("วันเสาร์เขียนงานได้ดี"), p.remembered)
    }

    @Test
    fun aDescribedAnswerReplacesTheEarlierOneUnderTheSameQuestion() {
        val first = WebAssistant.profileApply(null, """{"described":{"นอน":"ตามเวร"}}""", "2026-10-01")
        val second = WebAssistant.profileApply(first, """{"described":{"นอน":"หลังลงเวร"},"remember":["ชอบเดินเช้า"]}""", "2026-10-08")
        assertEquals(listOf("นอน: หลังลงเวร", "ชอบเดินเช้า"), Profile.parse(second).remembered)
    }

    @Test
    fun theProfileGoesStaleAfterAMonth() {
        val old = WebAssistant.profileApply(null, "{}", "2026-09-01")
        assertTrue(WebAssistant.profile(old, "2026-10-08").contains("\"stale\":true"))
        assertTrue(WebAssistant.profile(old, "2026-09-15").contains("\"stale\":false"))
    }

    // ---- Insight ----

    private fun deepLog(hour: Int, n: Int) = (1..n).joinToString(",") { """"2026-10-0${it}T$hour:10:00|DEEP"""" }

    @Test
    fun anInsightIsAskedThenRememberedOnlyOnYes() {
        val s = state(""","profile":${profileText.jsonString()},"doneLog":[${deepLog(14, 6)}]""")
        val ask = WebAssistant.insight("f", "TaskForge.md", note, s)
        assertTrue(ask.contains("\"id\":\"focus:12\""), ask)
        assertTrue(ask.contains("ช่วงสมองดีคือ 12:00 ถึง 15:00"), ask)
        // Nothing is written by asking; saying yes writes the new window and the sentence.
        val yes = WebAssistant.insightYes("f", "TaskForge.md", note, state(""","profile":${profileText.jsonString()},"doneLog":[${deepLog(14, 6)}],"insightId":"focus:12""""))
        val p = Profile.parse(yes)
        assertEquals("12:00", Profile.hm(p.focusFrom))
        assertEquals("15:00", Profile.hm(p.focusTo))
        assertEquals(listOf("ช่วงสมองดีคือ 12:00 ถึง 15:00"), p.remembered)
        // A question already answered no is not asked again.
        val declined = WebAssistant.insight("f", "TaskForge.md", note, state(""","profile":${profileText.jsonString()},"doneLog":[${deepLog(14, 6)}],"declined":["focus:12"]"""))
        assertEquals("null", declined)
    }

    @Test
    fun yesToAQuestionNoLongerAskedWritesNothing() {
        val r = WebAssistant.insightYes("f", "TaskForge.md", note, state(""","profile":${profileText.jsonString()},"insightId":"focus:12""""))
        assertEquals("", r)
    }

    @Test
    fun kindsOfWorkAreNamedForTheDoneLog() {
        assertEquals("MOVE", WebAssistant.kindOf("ไปวิ่ง 5 กม."))
        assertEquals("DEEP", WebAssistant.kindOf("เขียน proposal"))
        assertEquals("GENERAL", WebAssistant.kindOf("จัดตู้"))
    }

    // ---- The chat ----

    @Test
    fun aSentenceIsRoutedLikeAndroidReadsIt() {
        fun kind(t: String) = WebAssistant.route(t, "2026-10-08")
        assertTrue(kind("วางแผนสัปดาห์หน้าให้หน่อย").contains("\"kind\":\"plan\",\"from\":\"2026-10-12\",\"to\":\"2026-10-18\""), kind("วางแผนสัปดาห์หน้าให้หน่อย"))
        assertTrue(kind("สัปดาห์หน้ามีอะไรบ้าง").contains("\"kind\":\"agenda\""))
        assertTrue(kind("จัดลำดับงานทั้งหมดให้หน่อย").contains("\"kind\":\"rank\""))
        assertTrue(kind("อยากเขียน proposal 2 ชม. ควรทำตอนไหน").contains("\"kind\":\"slots\""))
        assertTrue(kind("อยากไปวิ่งสัปดาห์นี้ ควรไปตอนไหนดี").contains("\"kind\":\"duration\""))
        // With no range named, a look-ahead covers the next seven days.
        assertTrue(kind("มีอะไรบ้าง").contains("\"from\":\"2026-10-08\",\"to\":\"2026-10-14\""))
    }

    @Test
    fun slotsAvoidEventsAndGiveReasons() {
        val s = state(""","events":[{"id":1,"title":"เวร OPD","begin":"2026-10-08T08:00","end":"2026-10-08T16:00"}]""")
        val r = WebAssistant.slots("f", "TaskForge.md", note, s, "อยากไปวิ่งวันนี้", 45)
        assertTrue(r.contains("\"minutes\":45"), r)
        assertTrue(r.contains("\"title\":\"ไปวิ่ง\""), r)
        assertTrue(r.contains("ตรงกับเวลาออกกำลังกาย"), r)
        // Nothing is placed inside the shift or within a quarter hour of it.
        assertFalse(r.contains("\"start\":\"09:") || r.contains("\"start\":\"12:") || r.contains("\"start\":\"15:"), r)
    }

    @Test
    fun rankingPutsOverdueFirstWithItsReason() {
        val r = WebAssistant.rank("f", "TaskForge.md", note, state())
        assertTrue(r.startsWith("[{\"key\":\"f#1\",\"title\":\"ทบทวนเคส\",\"reason\":\"ครบวันนี้\"}"), r)
    }

    @Test
    fun todayListHoldsMustThenWaitingThenFuture() {
        val r = WebAssistant.today("f", "TaskForge.md", note, state())
        val keys = Regex("\"key\":\"(f#\\d+)\"").findAll(r).map { it.groupValues[1] }.toList()
        assertEquals(listOf("f#1", "f#2", "f#3"), keys)
        assertTrue(r.contains("พี่เอ รอ 12 วัน"), r)
        assertTrue(r.contains("ลงทุนอนาคต ไม่มีเดดไลน์แต่สำคัญ"), r)
    }

    @Test
    fun agendaGroupsTasksAndEventsByDayAndLeavesEmptyDaysOut() {
        val s = state(""","events":[{"id":1,"title":"ประชุมทีม","begin":"2026-10-12T10:00","end":"2026-10-12T11:00"},{"id":2,"title":"วันหยุด","begin":"2026-10-13T00:00","end":"2026-10-14T00:00","allDay":true}]""")
        val r = WebAssistant.agenda("f", "TaskForge.md", note, s, "2026-10-12", "2026-10-18")
        assertTrue(r.startsWith("{\"events\":2,\"due\":1,"), r)
        assertTrue(r.contains("{\"day\":\"2026-10-12\",\"events\":[{\"title\":\"ประชุมทีม\",\"allDay\":false,\"time\":\"10:00\"}],\"tasks\":[{\"key\":\"f#0\",\"title\":\"ส่งรายงาน\",\"due\":true}]}"), r)
        assertTrue(r.contains("{\"day\":\"2026-10-13\",\"events\":[{\"title\":\"วันหยุด\",\"allDay\":true}],\"tasks\":[]}"), r)
        assertFalse(r.contains("2026-10-14\",\"events"), r)
    }

    @Test
    fun aRangePlanPlacesUndatedWorkAndNeverTheDatedOnes() {
        val r = WebAssistant.planRange("f", "TaskForge.md", note, state(), "2026-10-12", "2026-10-18")
        // Work already due before the range (the case) is not placed; undated work and work due in it is, each on its own time.
        assertFalse(r.contains("\"key\":\"f#1\""), r)
        assertTrue(r.contains("\"key\":\"f#0\"") && r.contains("\"key\":\"f#4\""), r)
        val days = Regex("\"day\":\"(2026-10-\\d\\d)\",\"start\":\"(\\d\\d:\\d\\d)\"").findAll(r).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(days.size, days.toSet().size, r)
        assertTrue(days.all { it.first in "2026-10-12".."2026-10-18" }, r)
    }

    @Test
    fun weeklyReviewCountsDoneAndOverdue() {
        val r = WebAssistant.weekly("f", "TaskForge.md", note + "\n- [x] เสร็จแล้ว ✅ 2026-10-07", state())
        assertTrue(r.contains("สัปดาห์นี้เสร็จ 1 งาน"), r)
        assertTrue(r.contains("เลยกำหนดค้างอยู่ 0"), r)
    }

    @Test
    fun theSnapshotForClaudeIsAndroidsText() {
        val s = state(""","profile":${profileText.jsonString()},"events":[{"id":1,"title":"ประชุมทีม","begin":"2026-10-08T10:00","end":"2026-10-08T11:00"}]""")
        val r = WebAssistant.snapshot("f", "TaskForge.md", note, s)
        assertTrue(r.startsWith("วันนี้ 2026-10-08\n[โปรไฟล์]\n- อัปเดต: \n- ตื่น: 06:30"), r)
        assertTrue(r.contains("[เช้า]\n- นัด: ประชุมทีม 10:00 ถึง 11:00"), r)
        assertTrue(r.contains("[ไม่ระบุเวลา]\n- งาน: - [ ] ทบทวนเคส"), r)
        assertTrue(r.contains("[งานที่ยังไม่เสร็จทั้งหมด]\n- [ ] ส่งรายงาน"), r)
    }

    // ---- Writing to the note ----

    @Test
    fun taskLinesFollowTaskForgeOrder() {
        assertEquals(
            "- [ ] ไปวิ่ง #remind-at-scheduled 🎯 17:30 ➕ 2026-10-08 ⏳ 2026-10-09",
            WebAssistant.slotLine("ไปวิ่ง", "2026-10-09", "17:30", "2026-10-08"),
        )
        assertEquals("- [ ] ไปธนาคาร ➕ 2026-10-08 ⏳ 2026-10-09", WebAssistant.slotLine("  ไปธนาคาร\n", "2026-10-09", null, "2026-10-08"))
    }

    @Test
    fun addedLinesGoAtTheEnd() {
        val r = WebAssistant.addLines("- [ ] a\n", """["- [ ] b"]""")
        assertTrue(r.contains("\"text\":\"- [ ] a\\n- [ ] b\\n\""), r)
    }

    @Test
    fun acceptedProposalsGetADayAndAReminderTime() {
        val text = "- [ ] ทำเว็บ ➕ 2026-08-01\n- [ ] อ่านหนังสือ #อนาคต ➕ 2026-09-01"
        val items = """[{"raw":"- [ ] ทำเว็บ ➕ 2026-08-01","lineIndex":0,"day":"2026-10-12","time":"13:30"},{"raw":"- [ ] ถูกแก้ไปแล้ว","lineIndex":1,"day":"2026-10-12","time":"09:00"}]"""
        val r = WebAssistant.scheduleTasks(text, items)
        assertTrue(r.contains("\"done\":[true,false]"), r)
        assertTrue(r.contains("- [ ] ทำเว็บ #remind-at-scheduled 🎯 13:30 ➕ 2026-08-01 ⏳ 2026-10-12\\n- [ ] อ่านหนังสือ #อนาคต ➕ 2026-09-01"), r)
    }

    @Test
    fun theSweepMovesOldFinishedTasksToTheArchiveAndKeepsProjectWork() {
        val live = listOf(
            "- [x] เสร็จเมื่อวานนี้ ✅ 2026-10-07",
            "- [x] เสร็จเมื่อสองสัปดาห์ก่อน ✅ 2026-09-20",
            "- [x] งานโปรเจกต์ #proj/เว็บ ✅ 2026-09-01",
            "- [ ] ยังไม่เสร็จ",
            "",
        ).joinToString("\n")
        val r = WebAssistant.sweep(live, "", 7, "2026-10-08")
        assertTrue(r.contains("\"titles\":[\"เสร็จเมื่อสองสัปดาห์ก่อน\"]"), r)
        assertTrue(r.contains("# Omni note Archive\\n\\n## 2026-10\\n\\n- [x] เสร็จเมื่อสองสัปดาห์ก่อน ✅ 2026-09-20"), r)
        assertFalse(r.contains("\"text\":\"- [x] เสร็จเมื่อสองสัปดาห์ก่อน"), r)
        assertTrue(r.contains("งานโปรเจกต์"), r)
        assertEquals("null", WebAssistant.sweep("- [ ] ยังไม่เสร็จ\n", "", 7, "2026-10-08"))
    }

    private fun String.jsonString() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
