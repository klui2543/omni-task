package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class SecretaryGuideTest {
    private val today = LocalDate.parse("2026-10-24")
    private var line = 0
    private fun t(text: String) = TaskLine.parse(text)!!.copy(fileUri = "note", lineIndex = line++)

    private val note = """
        # เลขา คู่มือ
        แอป Omni อ่านโน้ตนี้ทุกครั้งที่ถามเลขา

        ## บอลลูน
        เรียงตามลำดับที่อยากเห็น
        1. วันนี้มีอะไร → ในแอป: วันนี้
        2. ด่วนอะไรบ้าง → ในแอป: งานที่กำหนดจริงภายใน 3 วัน
        3. ด่วน Siriraj → กรอง: #Siriraj, วันที่ เลยกำหนด, กำหนดจริงภายใน 7 วัน, ความสำคัญ สูง
        4. วางแผนพรุ่งนี้ → Claude: ช่วยวางแผนพรุ่งนี้
        💬 อันนี้ Klui เขียนเอง

        ### ซ่อนไว้
        - เดือนหน้ามีอะไร → ในแอป: เดือนหน้า

        ## คำที่พิมพ์
        - ทั้งหมด → ในแอป: จัดลำดับทั้งหมด
        - เลื่อน → ในแอป: งานค้าง

        ## วิธีตอบ
        - รายการ: 5 งาน
        - ลำดับ: มีคนรอ, เลยกำหนดจริง
        - บอก Claude: ถ้าไม่แน่ใจ ให้ถามกลับ 1 ข้อ

        ## สิ่งที่เรียนรู้
        - เสนอ: คำ "ด่วน" → กรอง: กำหนดจริงภายใน 3 วัน (2026-10-24, จาก แชตเลขา)
        - เสนอ: ซ่อนบอลลูน "สัปดาห์นี้" (2026-10-24, จาก ปุ่มในแอป)
        - ตอนเช้าแตะ พรุ่งนี้มีอะไร บ่อยกว่า (2026-10-24, จาก ปุ่มในแอป)

        ## ของ Klui
        อะไรก็ได้ที่ Klui จด
    """.trimIndent()

    @Test
    fun readsEverySection() {
        val g = SecretaryGuide.parse(note)
        assertEquals(listOf("วันนี้มีอะไร", "ด่วนอะไรบ้าง", "ด่วน Siriraj", "วางแผนพรุ่งนี้"), g.shown.map { it.label })
        assertEquals(SecretaryGuide.Action.App(SecretaryGuide.Builtin.TODAY), g.bubbles[0].action)
        val soon = g.bubbles[1].action as SecretaryGuide.Action.Show
        assertEquals(3, soon.filter.withinDays)
        val siriraj = (g.bubbles[2].action as SecretaryGuide.Action.Show).filter
        assertEquals(setOf("Siriraj"), siriraj.tags)
        assertEquals(setOf(DateBucket.OVERDUE), siriraj.buckets)
        assertEquals(7, siriraj.withinDays)
        assertEquals(setOf(Priority.HIGH), siriraj.priorities)
        assertEquals(SecretaryGuide.Action.Claude("ช่วยวางแผนพรุ่งนี้"), g.bubbles[3].action)
        assertEquals(listOf("เดือนหน้ามีอะไร"), g.bubbles.filter { !it.on }.map { it.label })
        assertEquals(listOf("ทั้งหมด", "เลื่อน"), g.words.map { it.word })
        assertEquals(SecretaryGuide.Length.FIVE, g.length)
        assertEquals(SecretaryGuide.Factor.WAITING, g.order.first())
        assertEquals(SecretaryGuide.Factor.entries.size, g.order.size)
        assertEquals(listOf("ถ้าไม่แน่ใจ ให้ถามกลับ 1 ข้อ"), g.forClaude)
        assertEquals(3, g.learned.size)
        assertTrue(g.learned[0].proposal is SecretaryGuide.Proposal.AddWord)
        assertEquals(SecretaryGuide.Proposal.HideBubble("สัปดาห์นี้"), g.learned[1].proposal)
        assertNull(g.learned[2].proposal)
    }

    @Test
    fun writingKeepsWhatItCannotRead() {
        val g = SecretaryGuide.parse(note)
        val text = g.render()
        assertTrue("💬 อันนี้ Klui เขียนเอง" in text)
        assertTrue("## ของ Klui\nอะไรก็ได้ที่ Klui จด" in text)
        assertTrue("- ตอนเช้าแตะ พรุ่งนี้มีอะไร บ่อยกว่า (2026-10-24, จาก ปุ่มในแอป)" in text)
        // Reading what was written gives the same guide, and writing it again the same text.
        assertEquals(g, SecretaryGuide.parse(text))
        assertEquals(text, SecretaryGuide.parse(text).render())
        assertTrue('—' !in text && '·' !in text)
    }

    @Test
    fun aMissingNoteOrSectionMeansTheDefaults() {
        assertEquals(SecretaryGuide.DEFAULT_BUBBLES, SecretaryGuide.parse(null).bubbles)
        val onlyLearned = SecretaryGuide.parse("# เลขา คู่มือ\n\n## สิ่งที่เรียนรู้\n- อะไรสักอย่าง (2026-10-24, จาก แชตเลขา)\n")
        assertEquals(SecretaryGuide.DEFAULT_BUBBLES, onlyLearned.bubbles)
        assertEquals(1, onlyLearned.learned.size)
        // An empty bubble section is the owner's choice: no bubbles.
        assertEquals(emptyList(), SecretaryGuide.parse("## บอลลูน\n\n## คำที่พิมพ์\n").bubbles)
        // The default guide writes and reads back the same.
        val text = SecretaryGuide.DEFAULT.render()
        assertEquals(text, SecretaryGuide.parse(text).render())
    }

    @Test
    fun aTaughtWordIsFoundInWhatIsTyped() {
        val g = SecretaryGuide.parse(note)
        assertEquals("เลื่อน", g.wordIn("เลื่อนงานวิจัยหน่อย")?.word)
        assertEquals("ทั้งหมด", g.wordIn("จัด ทั้ง หมด ให้หน่อย")?.word)
        assertNull(g.wordIn("สวัสดี"))
    }

    @Test
    fun acceptingAProposalChangesTheGuideAndDropsTheLine() {
        val g = SecretaryGuide.parse(note)
        val word = SecretaryGuide.accept(g, g.learned[0])
        assertEquals("ด่วน", word.words.last().word)
        assertEquals(2, word.learned.size)
        val withWeek = g.copy(bubbles = g.bubbles + SecretaryGuide.Bubble("สัปดาห์นี้", SecretaryGuide.Action.App(SecretaryGuide.Builtin.WEEK)))
        val hidden = SecretaryGuide.accept(withWeek, withWeek.learned[1])
        assertTrue(hidden.bubbles.first { it.label == "สัปดาห์นี้" }.on.not())
    }

    @Test
    fun filtersReadLooselyAndWriteBackTheSame() {
        val f = SecretaryGuide.Filter.parse("ด่วน, #Siriraj, เลยกำหนด, สูง, !!!, ตาของฉัน, มีคนรอ, ค้างนาน 14 วัน, คำ \"ตาราง\", เรียงตาม ความสำคัญ")
        assertEquals(setOf("Siriraj", SecretaryGuide.ME_TAG), f.tags)
        assertEquals(setOf(DateBucket.OVERDUE), f.buckets)
        assertEquals(setOf(Priority.HIGH, Priority.HIGHEST), f.priorities)
        assertEquals(setOf(TaskKind.WAITING), f.kinds)
        assertEquals(14, f.staleDays)
        assertEquals("ตาราง", f.text)
        assertEquals(SortBy.PRIORITY, f.sortBy)
        assertEquals(f, SecretaryGuide.Filter.parse(f.render()))
        assertTrue(SecretaryGuide.Filter.parse("ด่วนมาก").isEmpty)
    }

    @Test
    fun aFilterPicksDeadlinesWithinDaysOrItsDateBuckets() {
        val late = t("- [ ] ส่งรายงาน #Siriraj 📅 2026-10-20")
        val soon = t("- [ ] ประชุม #Siriraj 📅 2026-10-26")
        val far = t("- [ ] งานไกล #Siriraj 📅 2026-11-20")
        val target = t("- [ ] เป้าเอง #Siriraj ⏳ 2026-10-25")
        val other = t("- [ ] งานอื่น 📅 2026-10-25")
        val tasks = listOf(late, soon, far, target, other)
        val within = SecretaryGuide.Filter(tags = setOf("Siriraj"), withinDays = 3)
        assertEquals(listOf(soon), within.run(tasks, today))
        val both = within.copy(buckets = setOf(DateBucket.OVERDUE))
        assertEquals(setOf(late, soon), both.run(tasks, today).toSet())
    }

    @Test
    fun anOrderPutsTheOwnersFactorFirst() {
        val waiting = t("- [ ] รอพี่เอ #รอ/พี่เอ")
        val late = t("- [ ] เลยกำหนด 📅 2026-10-20")
        val ranked = listOf(late, waiting)
        val order = listOf(SecretaryGuide.Factor.WAITING, SecretaryGuide.Factor.DUE_LATE, SecretaryGuide.Factor.HIGH, SecretaryGuide.Factor.TARGET_LATE)
        assertEquals(listOf(waiting, late), SecretaryGuide.inOrder(ranked, { it }, order, today))
        assertEquals(ranked, SecretaryGuide.inOrder(ranked, { it }, SecretaryGuide.Factor.entries, today))
    }

    @Test
    fun aBubbleNobodyTappedForTwoWeeksIsOfferedForHiding() {
        val g = SecretaryGuide.DEFAULT
        val stats = """
            # สถิติเลขา
            - 2026-10-01 08:00 ตอนนี้ทำอะไรดี
            - 2026-10-20 08:00 พรุ่งนี้มีอะไร
            - 2026-10-22 09:00 ตอนนี้ทำอะไรดี: ยืนยันตาราง
        """.trimIndent()
        val unused = SecretaryGuide.unusedBubbles(g, stats, today)
        assertTrue("งานค้าง" in unused)
        assertTrue("ตอนนี้ทำอะไรดี" !in unused && "พรุ่งนี้มีอะไร" !in unused)
        // Taps that do not go back two weeks yet say nothing.
        assertEquals(emptyList(), SecretaryGuide.unusedBubbles(g, "- 2026-10-20 08:00 พรุ่งนี้มีอะไร", today))
    }

    @Test
    fun theDeskAnswersListsAPageAtATime() {
        val tasks = (1..12).map { t("- [ ] งาน $it 📅 2026-10-${(10 + it).toString().padStart(2, '0')}") }
        val ctx = SecretaryDesk.Ctx(tasks, emptyList(), today, LocalDateTime(2026, 10, 24, 9, 0))
        val all = SecretaryDesk.rankAll(ctx, SecretaryGuide.Factor.entries)
        assertEquals(12, all.size)
        val first = SecretaryDesk.list("ลำดับ", all, 0, 5, "ไม่มี")
        assertEquals(5, first.items.size)
        assertEquals(7, first.more)
        assertNotNull(first.chips.firstOrNull { it.ask == SecretaryDesk.Ask.MORE })
        val last = SecretaryDesk.list("ลำดับ", all, 10, 5, "ไม่มี")
        assertEquals(2, last.items.size)
        assertEquals(0, last.more)
        assertEquals(SecretaryDesk.Ask.MORE, SecretaryDesk.match("ต่อ"))
        assertEquals(SecretaryDesk.Ask.RANK_ALL, SecretaryDesk.match("จัดลำดับงานทั้งหมด"))
        assertEquals(SecretaryDesk.Ask.RANK_TODAY, SecretaryDesk.match("จัดลำดับวันนี้"))
        assertEquals(SecretaryDesk.Ask.TODAY, SecretaryDesk.match("วันนี้มีอะไร"))
    }
}
