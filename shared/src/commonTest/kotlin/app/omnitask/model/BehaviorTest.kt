package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.drive.Merge3
import app.omnitask.web.WebAssistant
import app.omnitask.web.WebBehavior
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import app.omnitask.time.minusDays
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BehaviorTest {

    private val today = LocalDate(2026, 10, 12)
    private val task = TaskLine.parse("- [ ] เขียนรายงานวิจัย #งาน ⏫ 📅 2026-10-12")!!

    private fun at(day: LocalDate = today, h: Int = 9, m: Int = 0) = LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, h, m, 0)

    private fun done(id: String, day: LocalDate = today) = Behavior.of(Behavior.DONE, task, at(day), "android", id = id)

    @Test
    fun aNewFileStartsWithItsHeaderAndReadsBack() {
        val text = Behavior.merge(null, listOf(done("a")), today)
        assertEquals(Behavior.HEADER, text.lines().first())
        val e = Behavior.parse(text).single()
        assertEquals("a", e.id)
        assertEquals(Behavior.DONE, e.ev)
        assertEquals("2026-10-12T09:00:00", e.at)
        assertEquals("เขียนรายงานวิจัย", e.title)
        assertEquals(Planner.kindOf("เขียนรายงานวิจัย").name, e.kind)
        assertEquals("2026-10-12", e.due)
    }

    @Test
    fun linesAlreadyThereKeepTheirTextAndNewOnesGoAtTheEnd() {
        // A line written by another device, with its keys in another order: kept exactly as it is.
        val theirs = """{"at":"2026-10-11T20:00:00","id":"x","ev":"snooze","min":60}"""
        val text = Behavior.HEADER + "\n" + theirs + "\n"
        val out = Behavior.merge(text, listOf(done("a"), done("a"), done("x")), today)
        assertEquals(listOf(Behavior.HEADER, theirs), out.lines().take(2))
        assertEquals(listOf("x", "a"), Behavior.parse(out).map { it.id })
    }

    @Test
    fun nothingNewLeavesTheTextAsItIs() {
        val text = Behavior.merge(null, listOf(done("a")), today)
        assertSame(text, Behavior.merge(text, listOf(done("a")), today))
    }

    @Test
    fun brokenLinesAreSkipped() {
        val text = Behavior.HEADER + "\n{not json\n" + """{"id":"","at":"2026-10-11T20:00:00","ev":"done"}""" + "\n" +
            """{"id":"ok","at":"2026-10-11T20:00:00","ev":"done"},""" + "\n"
        assertEquals(listOf("ok"), Behavior.parse(text).map { it.id })
    }

    @Test
    fun twoDevicesAddingAtOnceJoinLineByLine() {
        val base = Behavior.merge(null, listOf(done("a")), today)
        val phone = Behavior.merge(base, listOf(done("p")), today)
        val web = Behavior.merge(base, listOf(done("w")), today)
        val joined = Merge3.merge(base, phone, web)
        assertTrue(joined.lost.isEmpty())
        assertEquals(setOf("a", "p", "w"), Behavior.parse(joined.text).map { it.id }.toSet())
    }

    @Test
    fun oldEventsGoOnlyOnceTheOldestIsWellPast() {
        val e100 = done("old100", today.minusDays(100))
        val e10 = done("new10", today.minusDays(10))
        // Nothing is 120 days old yet: everything stays.
        assertEquals(2, Behavior.parse(Behavior.merge(null, listOf(e100, e10), today)).size)
        // Once something is, all past 90 days go.
        val e130 = done("old130", today.minusDays(130))
        assertEquals(listOf("new10"), Behavior.parse(Behavior.merge(null, listOf(e130, e100, e10), today)).map { it.id })
    }

    @Test
    fun aMoveNotesBothDaysAndOnlyWhenTheDayChanges() {
        val e = assertNotNull(Behavior.moved(task, "DUE", LocalDate(2026, 10, 14), at(), "android"))
        assertEquals(Behavior.MOVE, e.ev)
        assertEquals("DUE", e.field)
        assertEquals("2026-10-12", e.from)
        assertEquals("2026-10-14", e.to)
        assertNull(Behavior.moved(task, "DUE", LocalDate(2026, 10, 12), at(), "android"))
        assertNull(Behavior.moved(task.copy(status = Status.DONE), "DUE", LocalDate(2026, 10, 14), at(), "android"))
        assertNull(Behavior.moved(task, "SCHEDULED", null, at(), "android"))
    }

    @Test
    fun theOldDeviceLogComesInOnceFromEveryDevice() {
        val entry = "2026-10-01T08:30:00|DEEP"
        val phone = assertNotNull(Behavior.legacyDone(entry, "android"))
        val web = assertNotNull(Behavior.legacyDone(entry, "web"))
        assertEquals(phone.id, web.id)
        assertNull(Behavior.legacyDone("nonsense", "web"))
        val text = Behavior.merge(Behavior.merge(null, listOf(phone), today), listOf(web), today)
        assertEquals(listOf(Insight.Done(LocalDateTime(2026, 10, 1, 8, 30, 0), Planner.Kind.DEEP)), Behavior.doneLog(Behavior.parse(text)))
    }

    @Test
    fun aNoCountsOnceOnEveryDevice() {
        val a = Behavior.declined("focus:9", at(), "android")
        val b = Behavior.declined("focus:9", at(h = 10), "web")
        val text = Behavior.merge(null, listOf(a, b), today)
        assertEquals(1, Behavior.parse(text).size)
        assertEquals(setOf("focus:9"), Behavior.declined(Behavior.parse(text)))
    }

    @Test
    fun idsDifferBetweenDevicesAndDraws() {
        val r = Random(1)
        val ids = (1..50).map { Behavior.newId("web", at(), r) }.toSet()
        assertEquals(50, ids.size)
        assertTrue(ids.all { it.startsWith("web-20261012090000-") })
    }

    // ---- The web side ----

    @Test
    fun theWebMakesEventsFromLinesAndSentences() {
        val move = Behavior.parse(WebBehavior.event(Behavior.MOVE, task.raw, "2026-10-12T09:00:00", """{"field":"DUE","to":"2026-10-13"}""")).single()
        assertEquals("2026-10-13", move.to)
        assertEquals("web", move.src)
        assertEquals("", WebBehavior.event(Behavior.MOVE, task.raw, "2026-10-12T09:00:00", """{"field":"DUE","to":"2026-10-12"}"""))
        val snooze = Behavior.parse(WebBehavior.event(Behavior.SNOOZE, task.raw, "2026-10-12T09:00:00", """{"min":60}""")).single()
        assertEquals(60, snooze.min)
        val made = Behavior.parse(WebBehavior.created("ซื้อนม พรุ่งนี้", "2026-10-12T09:00:00")).single()
        assertEquals(Behavior.CREATE, made.ev)
        assertEquals("ซื้อนม", made.title)
        assertEquals("", WebBehavior.created("   ", "2026-10-12T09:00:00"))
        val legacy = Behavior.parse(WebBehavior.legacy("""["2026-10-01T08:30:00|DEEP","bad"]"""))
        assertEquals(listOf(Behavior.DONE), legacy.map { it.ev })
        val file = WebBehavior.merge(null, WebBehavior.declined("day:MONDAY", "2026-10-12T09:00:00"), "2026-10-12")
        assertEquals(setOf("day:MONDAY"), Behavior.declined(Behavior.parse(file)))
    }

    @Test
    fun theWebAssistantLearnsFromTheFile() {
        // Five deep tasks done around 19:00: the assistant asks about the focus window, unless that was declined.
        val events = (1..6).map { d ->
            Behavior.of(Behavior.DONE, task, at(today.minusDays(d.toLong()), h = 19), "android", id = "d$d")
        }
        val log = Behavior.merge(null, events, today)
        val profile = Profile(exists = true, updated = today).render()
        fun ask(behavior: String) = WebAssistant.insight(
            "k", "n.md", "", """{"now":"2026-10-12T09:00","profile":${quote(profile)},"behavior":${quote(behavior)}}""",
        )
        val asked = ask(log)
        assertTrue(asked.contains("focus:"), asked)
        val id = Regex("\"id\":\"([^\"]+)\"").find(asked)!!.groupValues[1]
        val declined = Behavior.merge(log, listOf(Behavior.declined(id, at(), "web")), today)
        assertTrue(!ask(declined).contains(id), ask(declined))
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
