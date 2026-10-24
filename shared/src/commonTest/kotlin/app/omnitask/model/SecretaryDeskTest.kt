package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class SecretaryDeskTest {
    private val today = LocalDate.parse("2026-10-23")
    private var line = 0
    private fun t(text: String) = TaskLine.parse(text)!!.copy(fileUri = "note", lineIndex = line++)

    private val dueLate = t("- [ ] ยืนยันตาราง 📅 2026-10-21")
    private val targetLate = t("- [ ] ระบบป้าย ⏳ 2026-10-14")
    private val targetLate2 = t("- [ ] อ่านเปเปอร์ ⏳ 2026-10-20")
    private val tomorrowTask = t("- [ ] ซื้อของขวัญ 📅 2026-10-24")
    private val all = listOf(dueLate, targetLate, targetLate2, tomorrowTask)

    private fun ev(title: String, from: String, to: String, allDay: Boolean = false, cal: Long = 1) =
        CalendarEvent(title.hashCode().toLong(), title, LocalDateTime.parse(from), LocalDateTime.parse(to), allDay, cal)

    private fun ctx(events: List<CalendarEvent> = emptyList(), hour: Int = 18) = SecretaryDesk.Ctx(
        all, events, today, LocalDateTime(2026, 10, 23, hour, 0), othersEvent = { it.calendarId == 9L },
    )

    @Test
    fun lateWorkGoesToFreeDaysOneADayRealDeadlinesFirst() {
        // 24th has a task already, 25th an all-day event of the owner's, 26th a long shift; 27th is free.
        val events = listOf(
            ev("ไปเที่ยว", "2026-10-25T00:00", "2026-10-26T00:00", allDay = true),
            ev("เวร", "2026-10-26T08:00", "2026-10-26T16:30"),
            // Someone else's trip does not take the owner's day.
            ev("แฟนไปต่างจังหวัด", "2026-10-27T00:00", "2026-10-28T00:00", allDay = true, cal = 9),
        )
        val moves = SecretaryDesk.late(ctx(events)).moves
        assertEquals(listOf(dueLate, targetLate, targetLate2), moves.map { it.task })
        assertEquals(listOf("2026-10-27", "2026-10-28", "2026-10-29"), moves.map { it.date.toString() })
        assertEquals(TaskLine.DateField.DUE, moves[0].field)
        assertEquals(TaskLine.DateField.SCHEDULED, moves[1].field)
    }

    @Test
    fun beforeFiveTodayCanTakeWork() {
        val moves = SecretaryDesk.late(ctx(hour = 10)).moves
        assertEquals(today, moves.first().date)
    }

    @Test
    fun theNextTaskComesWithItsFollowUps() {
        val r = SecretaryDesk.next(ctx())
        assertEquals(dueLate, r.task)
        assertTrue(r.chips.any { it.ask == SecretaryDesk.Ask.OTHER })
        val other = SecretaryDesk.next(ctx(), exclude = setOf(dueLate.key))
        assertEquals(targetLate, other.task)
    }

    @Test
    fun typedQuestionsTheRulesKnow() {
        assertEquals(SecretaryDesk.Ask.TOMORROW, SecretaryDesk.match("พรุ่งนี้มีอะไรบ้าง"))
        assertEquals(SecretaryDesk.Ask.LATE, SecretaryDesk.match("งานค้างเยอะมาก"))
        assertNull(SecretaryDesk.match("ควรรับทำระบบให้แผนกยาเด็กเพิ่มไหม"))
    }

    @Test
    fun theOpeningOffersTomorrowFirstInTheEvening() {
        val r = SecretaryDesk.opening(ctx(hour = 19))
        assertEquals(SecretaryDesk.Ask.TOMORROW, r.chips.first().ask)
        assertTrue(r.chips.any { it.ask == SecretaryDesk.Ask.CLAUDE })
    }

    @Test
    fun aPretextNoteRoundTripsAndEdits() {
        val note = SecretaryPretext.parse(null)
        assertTrue(note.situations.any { it.title == "งานเยอะจนงง" })
        assertTrue(note.base.isNotBlank() && note.ending.isNotBlank())
        val again = SecretaryPretext.parse(note.render())
        assertEquals(note.situations, again.situations)
        val added = again.with(null, SecretaryPretext.Part("ก่อนประชุม", "ช่วยเตรียม"))
        assertEquals("ก่อนประชุม", added.situations.last().title)
        val removed = added.with("ก่อนประชุม", null)
        assertEquals(note.situations, removed.situations)
    }

    @Test
    fun thePackCarriesThePretextTheChatAndWhoseEventsTheyAre() {
        val note = SecretaryPretext.parse(null)
        val pack = SecretaryPretext.pack(
            note, note.situations.first(), "- งานเขียนใช้เวลา 2 เท่า (2026-10-23, จาก คุยเสียง)", null, all,
            listOf(ev("ไปเที่ยว", "2026-10-24T00:00", "2026-10-25T00:00", allDay = true, cal = 9)),
            { if (it.calendarId == 9L) "แฟน" else null }, today, chat = listOf("Klui: ตอนนี้ทำอะไรดี"),
        )
        assertTrue("เรื่องที่อยากคุยตอนนี้: งานเยอะจนงง" in pack)
        assertTrue("Klui: ตอนนี้ทำอะไรดี" in pack)
        assertTrue("(ปฏิทินของ แฟน)" in pack)
        assertTrue("งานเขียนใช้เวลา 2 เท่า" in pack)
        assertNotNull(pack.lines().firstOrNull { it.startsWith("- ยืนยันตาราง") })
        assertTrue('—' !in pack && '·' !in pack)
    }
}
