package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.notify.Digest
import app.omnitask.notify.NotifySettings
import app.omnitask.time.minusDays
import app.omnitask.web.WebAssistant
import app.omnitask.web.WebBehavior
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnedTest {

    private val today = LocalDate(2026, 10, 12)
    private var n = 0

    private fun at(daysAgo: Int, h: Int = 9) = today.minusDays(daysAgo.toLong()).let { LocalDateTime(it.year, it.monthNumber, it.dayOfMonth, h, 0, 0) }

    private fun task(line: String) = TaskLine.parse(line)!!

    private fun ev(kind: String, t: Task, daysAgo: Int, h: Int = 9, from: String? = null, to: String? = null, min: Int? = null): BehaviorEvent {
        val base = Behavior.of(kind, t, at(daysAgo, h), "android", id = "e${n++}")
        return base.copy(from = from, to = to, min = min)
    }

    private fun moves(t: Task, times: Int) = (1..times).map { ev(Behavior.MOVE, t, it, from = "2026-10-0$it", to = "2026-10-0${it + 1}") }

    @Test
    fun aTaskMovedThreeTimesIsSlipping() {
        val report = task("- [ ] เขียนบทความ 📅 2026-10-12")
        val other = task("- [ ] ซื้อนม 📅 2026-10-12")
        val log = moves(report, 3) + moves(other, 2) +
            // Moved to an earlier day, or cleared: not putting it off.
            ev(Behavior.MOVE, other, 1, from = "2026-10-09", to = "2026-10-08") + ev(Behavior.MOVE, other, 1, from = "2026-10-09")
        val slips = Learned.slipping(listOf(report, other, report.copy(status = Status.DONE)), log, today)
        assertEquals(listOf("เขียนบทความ" to 3), slips.map { it.task.title to it.moves })
    }

    @Test
    fun workThatUsuallySlipsIsFlaggedWhenItIsDueSoon() {
        val past = (1..6).map { task("- [ ] รายงาน $it #งาน") }
        val log = past.flatMap { listOf(ev(Behavior.CREATE, it, 20)) } + past.take(4).flatMap { moves(it, 1) }
        val due = task("- [ ] รายงานใหม่ #งาน 📅 2026-10-13")
        val far = task("- [ ] รายงานไกล #งาน 📅 2026-10-30")
        val home = task("- [ ] ล้างรถ #บ้าน 📅 2026-10-12")
        val risks = Learned.atRisk(listOf(due, far, home), log, today)
        assertEquals(listOf("รายงานใหม่"), risks.map { it.task.title })
        assertEquals("#งาน", risks.single().key)
    }

    @Test
    fun aKindWhoseRemindersArePutOffIsAskedAbout() {
        val t = task("- [ ] อ่านเปเปอร์ #remind-at-due ⏰ 09:00 📅 2026-10-12")
        val kind = Planner.kindOf(t.title)
        val log = (1..4).map { ev(Behavior.SNOOZE, t, it, min = 60) }
        assertEquals(mapOf(kind to 4), Learned.snoozedKinds(log, today))
        assertEquals(emptyMap(), Learned.snoozedKinds(log.take(3), today))
        // A day's snooze is not an hour's.
        assertEquals(emptyMap(), Learned.snoozedKinds((1..4).map { ev(Behavior.SNOOZE, t, it, min = 24 * 60) }, today))

        val profile = Profile(exists = true, updated = today)
        val ask = Insight.next(emptyList(), emptyList(), profile, today, emptySet(), log)!!
        assertEquals("later:${kind.name}:60", ask.id)
        val after = ask.apply(profile)
        assertEquals(mapOf(kind to 60), after.later)
        // Once in the profile, not asked again.
        assertNull(Insight.next(emptyList(), emptyList(), after, today, emptySet(), log))
        assertNull(Insight.next(emptyList(), emptyList(), profile, today, setOf(ask.id), log))
    }

    @Test
    fun theProfileKeepsRemindersSetLater() {
        val p = Profile(exists = true, later = mapOf(Planner.Kind.DEEP to 60, Planner.Kind.ERRAND to 30))
        val text = p.render()
        assertTrue(text.contains("- เตือนช้าลง: ใช้สมอง 60 นาที, ธุระนอกบ้าน 30 นาที"), text)
        assertEquals(p.later, Profile.parse(text).later)
        assertEquals(emptyMap(), Profile.parse(Profile(exists = true).render()).later)
        assertTrue(!Profile(exists = true).render().contains("เตือนช้าลง"))
    }

    @Test
    fun aNewTaskGetsWhatAlikeTasksUsuallyHad() {
        val made = (1..3).map { d ->
            ev(Behavior.CREATE, task("- [ ] ซื้อของเข้าบ้าน #บ้าน #remind-at-due ⏰ 18:00 ➕ 2026-10-0$d 📅 2026-10-0${d + 1}"), 12 - d)
                .copy(due = today.minusDays((12 - d - 1).toLong()).toString())
        }
        val draft = QuickAdd.parse("ซื้อของเข้าบ้าน", today)
        val hints = Learned.hints(draft, made, Profile(), today)
        assertEquals(listOf("#บ้าน", "พรุ่งนี้", "18:00"), hints.map { it.words })
        // A kind set later in the profile moves the usual time.
        val later = Profile(later = mapOf(Planner.kindOf("ซื้อของเข้าบ้าน") to 60))
        assertEquals("19:00", Learned.hints(draft, made, later, today).last().words)
        // What the sentence already says is left alone.
        assertEquals(emptyList(), Learned.hints(QuickAdd.parse("ซื้อของเข้าบ้าน พรุ่งนี้ 17:00 #บ้าน", today), made, Profile(), today))
        // A title unlike the past ones gets nothing.
        assertEquals(emptyList(), Learned.hints(QuickAdd.parse("ประชุมทีม", today), made, Profile(), today))
        val web = WebBehavior.hints("ซื้อของเข้าบ้าน", today.toString(), Behavior.merge(null, made, today), null)
        assertTrue(web.contains("\"words\":\"#บ้าน\""), web)
    }

    @Test
    fun theDailyAndWeeklySummariesPointOutWorkThatSlips() {
        val report = task("- [ ] เขียนบทความ 📅 2026-10-12")
        val log = moves(report, 3)
        val daily = Digest.daily(listOf(report), today, NotifySettings(), log)!!
        assertTrue(daily.lines.any { it.contains("เขียนบทความ") && it.contains("3") && it.contains("#อนาคต") }, daily.lines.toString())
        val weekly = Digest.weekly(listOf(report), today, log)
        assertTrue(weekly.lines.last().startsWith("เลื่อนบ่อย: เขียนบทความ"), weekly.lines.toString())
        assertEquals(3, Digest.weekly(listOf(report), today).lines.size)
    }

    @Test
    fun claudeGetsTheHabitsInThePicture() {
        val t = task("- [ ] วิเคราะห์ข้อมูล 📅 2026-10-12")
        val log = (1..6).map { ev(Behavior.DONE, t, it, h = 10) } + moves(t, 3)
        val lines = Learned.summary(listOf(t), log, today)
        assertTrue(lines.first().startsWith("เพิ่มงาน 0 ทำเสร็จ 6"), lines.toString())
        assertTrue(lines.any { it.startsWith("ปิดงานบ่อยช่วง") }, lines.toString())
        assertTrue(lines.any { it == "เลื่อนบ่อย: วิเคราะห์ข้อมูล (3 ครั้ง)" }, lines.toString())
        assertEquals(emptyList(), Learned.summary(listOf(t), emptyList(), today))

        val behavior = Behavior.merge(null, log, today).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val picture = WebAssistant.snapshot("k", "n.md", t.raw, """{"now":"2026-10-12T09:00","behavior":"$behavior"}""")
        assertTrue(picture.contains("[พฤติกรรม 4 สัปดาห์]\n- เพิ่มงาน"), picture)
    }
}
