package app.omnitask.quest

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryTest {

    private val today = LocalDate.parse("2026-10-13")

    private fun facts(
        level: Int = 1, sessions: Int = 0, focus: Int = 0, boss: Boolean = false, weekly: Boolean = false,
        seen: Set<String> = emptySet(), day: LocalDate = today, shift: Boolean = false, busy: Boolean = false,
    ) = Story.Facts(level, sessions, focus, boss, weekly, seen, day, shift, busy)

    @Test
    fun chapterOneOpensFromRealProgress() {
        assertEquals(listOf("c1-1"), Story.open(facts()).map { it.id })
        assertEquals(listOf("c1-2"), Story.open(facts(seen = setOf("c1-1"))).map { it.id })
        // The guild's third scene needs a timed session, not just time passing.
        val two = setOf("c1-1", "c1-2")
        assertTrue(Story.open(facts(level = 30, seen = two)).none { it.id == "c1-3" })
        assertEquals("c1-3", Story.next(facts(seen = two))?.id)
        assertTrue(Story.open(facts(sessions = 1, seen = two)).any { it.id == "c1-3" })
        val four = two + "c1-3" + "c1-4"
        assertFalse(Story.open(facts(level = 9, seen = four)).any { it.id == "c1-5" })
        assertTrue(Story.open(facts(level = 9, boss = true, seen = four)).any { it.id == "c1-5" })
    }

    @Test
    fun eventsFollowTheCalendarAndTheDate() {
        assertTrue(Story.open(facts(shift = true, seen = setOf("c1-1"))).any { it.id == "ev-night-shift" })
        assertTrue(Story.open(facts(day = LocalDate.parse("2026-10-30"), seen = setOf("c1-1"))).any { it.id == "ev-halloween" })
        assertTrue(Story.open(facts(day = LocalDate.parse("2027-01-02"), seen = setOf("c1-1"))).any { it.id == "ev-new-year" })
        assertFalse(Story.open(facts(seen = setOf("c1-1"))).any { it.event })
        assertEquals(listOf("c1-1"), Story.open(facts(shift = true)).map { it.id })
        // An event never blocks the chapter's next scene.
        assertTrue(Story.open(facts(seen = setOf("c1-1"), shift = true)).map { it.id }.containsAll(listOf("c1-2", "ev-night-shift")))
    }

    @Test
    fun watchedScenesPayOnceAndStaySeen() {
        val at = LocalDateTime.parse("2026-10-13T08:00")
        val s = QuestState.replay(
            listOf(
                QuestLog.action(QuestLog.SCENE, at, "t", a = "c1-1", gold = 30),
                QuestLog.action(QuestLog.SCENE, at, "t", a = "c1-1", gold = 30),
            ),
        )
        assertEquals(setOf("c1-1"), s.seen)
        assertEquals(30, s.goldIn)
    }

    @Test
    fun fillAndTalkUseTheOwnersWorld() {
        val cast = Story.Cast("กลุ้ย", Tribe.LUNA, Tribe.LUNA, "Siriraj")
        assertEquals("กลุ้ย กับ มิมิ เผ่าจันทรา #Siriraj", Story.fill("{name} กับ {mascot} เผ่า{tribe} {branch}", cast))
        val day = Story.Day(today, doneToday = 0, lastDone = null, overdue = 0, focusMinutes = 0, topRealm = null, shiftToday = true, nextEvent = null)
        assertTrue(Story.talk(Story.Speaker.LEO, day, cast).contains("เวร"))
        assertEquals(Story.talk(Story.Speaker.CELINE, day, cast), Story.talk(Story.Speaker.CELINE, day, cast))
        // Every line of every scene is filled with no placeholder left behind.
        Story.scenes.flatMap { it.lines }.forEach { line ->
            val text = Story.fill(line.text, cast)
            assertFalse(text.contains("{"), text)
            assertFalse(text.contains("—") || text.contains("·"), text)
        }
    }
}
