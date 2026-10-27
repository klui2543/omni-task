package app.omnitask.model

import app.omnitask.data.VaultText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ReviewTest {

    private val today = LocalDate.parse("2026-10-27")

    // Shaped like the real task note (Omni note.md): Tasks emoji order, ค้าง and ทบทวน lines under tasks.
    private val file = listOf(
        "- [ ] Mock UX UI และ จัดการโครงสร้างหลังบ้าน #รอ #peddose ⏫ 🆔 mock1 ➕ 2026-10-02",
        "    - ค้าง 2026-10-26 งานใหญ่เกิน",
        "- [ ] เขียนถ้อยคำให้ครบ 25 ข้อ #peddose ➕ 2026-10-20 📅 2026-10-21",
        "    - ทบทวน 2026-10-25",
        "- [ ] ทดสอบหน้าแรก #peddose ⛔ mock1 ➕ 2026-10-22",
        "- [ ] นับยา ➕ 2026-10-24",
        "- [ ] จัดการ Note ➕ 2026-09-29",
        "- [ ] เปลี่ยนแบตโน๊ตบุ๊ค #สักวัน ➕ 2026-10-19",
        "- [ ] ล้างรถ 🔁 every week ➕ 2026-01-01",
        "- [x] ส่งรายงาน ➕ 2026-10-01 ✅ 2026-10-10",
        "- [ ] ดูหุ้นแบบรอบด้าน #อนาคต ➕ 2026-10-20",
    )

    private fun tasks() = VaultText.parseFile("f", "Omni note.md", file.joinToString("\n"))

    private fun <T> withProjects(body: () -> T): T {
        val before = Projects.chosen
        Projects.chosen = setOf("peddose")
        try { return body() } finally { Projects.chosen = before }
    }

    private fun task(title: String) = tasks().first { it.title.startsWith(title) }

    @Test
    fun theDeckGoesProjectThenOtherThenNotMoved() = withProjects {
        val deck = Review.deck(tasks(), today)
        assertEquals(
            listOf(
                // Project: never reviewed first (waiting before the rest), the one reviewed 2 days ago last.
                "Mock UX UI และ จัดการโครงสร้างหลังบ้าน" to Review.Group.PROJECT,
                "ทดสอบหน้าแรก" to Review.Group.PROJECT,
                "เขียนถ้อยคำให้ครบ 25 ข้อ" to Review.Group.PROJECT,
                "ดูหุ้นแบบรอบด้าน" to Review.Group.OTHER,
                "นับยา" to Review.Group.OTHER,
                // Not moved: 28 days quiet, and parked on purpose.
                "จัดการ Note" to Review.Group.STALE,
                "เปลี่ยนแบตโน๊ตบุ๊ค" to Review.Group.STALE,
            ),
            deck.map { it.task.title to it.group },
        )
    }

    @Test
    fun aReviewedCardGoesToTheEndOfItsGroup() = withProjects {
        val lines = file.toMutableList()
        ReviewLog.write(lines, 0, today)
        val deck = Review.deck(VaultText.parseFile("f", "Omni note.md", lines.joinToString("\n")), today)
        assertEquals("Mock UX UI และ จัดการโครงสร้างหลังบ้าน", deck.filter { it.group == Review.Group.PROJECT }.last().task.title)
    }

    @Test
    fun importanceAddsUpWhatHangsOnTheTask() = withProjects {
        // Others hurt 30 + waiting 15 + project 10 + one task waits on it 10 = 65.
        val w = Review.weigh(task("Mock UX UI"), tasks(), today, impact = Review.Impact.OTHERS)
        assertEquals(65, w.score)
        assertEquals(Priority.HIGHEST, w.priority)
        assertEquals(listOf(30, 15, 10, 10), w.parts.map { it.points })
        assertTrue(w.flags.isEmpty())
    }

    @Test
    fun theGuessIsUsedUntilTheOwnerAnswers() = withProjects {
        val w = Review.weigh(task("Mock UX UI"), tasks(), today)
        assertTrue(w.parts.first().label.contains("ระบบเดา"))
        assertEquals(Review.Impact.OTHERS, Review.guess(task("Mock UX UI"), today).impact)
        assertEquals(Review.Impact.NONE, Review.guess(task("จัดการ Note"), today).impact)
    }

    @Test
    fun anAnswerLowerThanTheSignalsAndManyMovesAreFlagged() = withProjects {
        val w = Review.weigh(task("Mock UX UI"), tasks(), today, impact = Review.Impact.NONE, moves = 4)
        assertEquals(2, w.flags.size)
        assertTrue(w.flags[0].contains("แน่ใจไหม"))
        assertTrue(w.flags[1].contains("4 ครั้ง"))
    }

    @Test
    fun urgencyComesFromTimeOnly() = withProjects {
        assertEquals(Review.Urgency.TOP, Review.weigh(task("เขียนถ้อยคำ"), tasks(), today).urgency) // 6 days late
        assertEquals(Review.Urgency.MID, Review.weigh(task("นับยา"), tasks(), today, delay = Review.Delay.MUCH).urgency)
        assertEquals(Review.Urgency.MID, Review.weigh(task("Mock UX UI"), tasks(), today).urgency) // waiting, no 📅
        assertEquals(Review.Urgency.HIGH, Review.weigh(task("Mock UX UI"), tasks(), today, due = LocalDate.parse("2026-10-30")).urgency)
        assertEquals(Review.Urgency.LOW, Review.weigh(task("นับยา"), tasks(), today).urgency)
    }

    @Test
    fun aNameInTheTitleIsOfferedAsTheOneWaiting() {
        assertEquals("พี่ต่าย", Review.nameIn("ช่วยดูระบบให้พี่ต่าย"))
        assertEquals("อาจารย์สม", Review.nameIn("ส่งสไลด์ให้อาจารย์สม ก่อนประชุม"))
        assertEquals(null, Review.nameIn("ให้อาหารแมว"))
        val forHer = app.omnitask.data.TaskLine.parse("- [ ] ช่วยดูระบบให้พี่ต่าย ➕ 2026-10-27")!!
        assertEquals(Review.Impact.OTHERS, Review.guess(forHer, today).impact)
    }

    @Test
    fun levels() {
        assertEquals(
            listOf(Priority.HIGHEST, Priority.HIGH, Priority.MEDIUM, Priority.NONE, Priority.LOW),
            listOf(60, 45, 25, 10, 9).map(Review::levelOf),
        )
    }
}
