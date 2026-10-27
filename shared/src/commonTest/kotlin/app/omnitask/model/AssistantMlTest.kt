package app.omnitask.model

import app.omnitask.model.AssistantMl.Example
import app.omnitask.model.AssistantMl.Intent
import app.omnitask.model.AssistantMl.Label
import app.omnitask.model.AssistantMl.Level
import app.omnitask.model.AssistantMl.Span
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantMlTest {

    private val day = LocalDate.parse("2026-10-26")
    private fun ex(line: String) = AssistantMl.parseLine(line)!!

    // The sentences the owner typed on 26 Oct that the app did not know (สถิติเลขา, "ไม่รู้จัก"), taught as the plan says.
    private val taught = listOf(
        "- {เพิ่มงาน|คำสั่ง} {โทรหาร้านซ่อมรถ|ชื่องาน} {วันศุกร์|วันที่} {9 โมง|เวลา} → เพิ่มงาน (2026-10-26, จาก สอนในแอป)",
        "- {สร้างงาน|คำสั่ง} {ส่งรายงานเวร|ชื่องาน} → เพิ่มงาน (2026-10-26, จาก สอนในแอป)",
        "- {วันนี้|วันที่}{อยากวิ่ง|ชื่องาน} → หาเวลา (2026-10-26, จาก สอนในแอป)",
        "- {มีเวลาว่าง|คำสั่ง}{วันนี้|วันที่}ไหม → หาเวลา (2026-10-26, จาก สอนในแอป)",
        "- {มีงานอะไร|คำสั่ง}{ด่วน|ความเร่งด่วน=สูง}บ้าง → กรอง: ความสำคัญ สูง (2026-10-26, จาก Haiku)",
    ).map(::ex)

    @Test
    fun aNoteLineReadsBackAsWritten() {
        val line = "- {เพิ่มงาน|คำสั่ง} {โทรหาร้านซ่อมรถ|ชื่องาน} {วันศุกร์|วันที่} {ด่วน|ความเร่งด่วน=สูง} → เพิ่มงาน (2026-10-26, จาก สอนในแอป)"
        val e = ex(line)
        assertEquals(Intent.AddTask, e.intent)
        assertEquals(day, e.date)
        assertEquals("สอนในแอป", e.source)
        assertEquals("เพิ่มงาน โทรหาร้านซ่อมรถ วันศุกร์ ด่วน", e.text)
        assertEquals(Span("ด่วน", Label.PRIORITY, "สูง"), e.spans.last())
        assertEquals(line, e.render())
        // A command of the app, a list by conditions, and Claude read as the guide writes them.
        assertEquals(Intent.Do(SecretaryGuide.Action.App(SecretaryGuide.Builtin.WEEK)), ex("- {สัปดาห์นี้มีอะไร|คำสั่ง} → ในแอป: สัปดาห์นี้").intent)
        assertTrue(ex("- {มีงานอะไร|คำสั่ง}{ด่วน|ความเร่งด่วน=สูง}บ้าง → กรอง: ความสำคัญ สูง").intent.let { it is Intent.Do && it.action is SecretaryGuide.Action.Show })
        assertEquals(Intent.FindTime, ex("- วันนี้อยากวิ่ง → หาเวลา").intent)
        // Not examples: kept as written by the note, never read as one.
        assertNull(AssistantMl.parseLine("ประโยคที่สอนเลขา"))
        assertNull(AssistantMl.parseLine("- ไม่มีลูกศร"))
        assertNull(AssistantMl.parseLine("- อะไรสักอย่าง → ทำอะไรก็ไม่รู้"))
    }

    @Test
    fun addingAnExampleKeepsTheRestOfTheNote() {
        val note = "# Assistant ML\nบรรทัดของ Klui\n\n## ตัวอย่าง\n- {เพิ่มงาน|คำสั่ง} ซื้อนม → เพิ่มงาน\nโน้ตที่แอปอ่านไม่ออก\n\n## อื่น ๆ\nเก็บไว้\n"
        val e = Example(listOf(Span("จด", Label.COMMAND), Span(" "), Span("ซื้อไข่", Label.TITLE)), Intent.AddTask, day, "สอนในแอป")
        val out = AssistantMl.add(note, e)
        assertEquals(
            "# Assistant ML\nบรรทัดของ Klui\n\n## ตัวอย่าง\n- {เพิ่มงาน|คำสั่ง} ซื้อนม → เพิ่มงาน\nโน้ตที่แอปอ่านไม่ออก\n" +
                "- {จด|คำสั่ง} {ซื้อไข่|ชื่องาน} → เพิ่มงาน (2026-10-26, จาก สอนในแอป)\n\n## อื่น ๆ\nเก็บไว้\n",
            out,
        )
        assertEquals(2, AssistantMl.examples(out).size)
        // A note without the section (as it was created) gets one.
        val made = AssistantMl.add("# Assistant ML\nคำอธิบาย\n", e)
        assertEquals("# Assistant ML\nคำอธิบาย\n\n## ตัวอย่าง\n- {จด|คำสั่ง} {ซื้อไข่|ชื่องาน} → เพิ่มงาน (2026-10-26, จาก สอนในแอป)\n", made)
        assertEquals(1, AssistantMl.examples(AssistantMl.add(null, e)).size)
    }

    @Test
    fun aTaughtCommandWorksForAnyTaskAfterIt() {
        val m = AssistantMl.Model(taught, AssistantMl.seeds(SecretaryGuide.DEFAULT))
        val r = m.read("เพิ่มงาน ซื้อนมให้แม่ พรุ่งนี้")
        assertEquals(Level.HIGH, r.level)
        assertEquals(Intent.AddTask, r.best!!.intent)
        // Quick add gets the task without the command; it reads the day itself.
        assertEquals("ซื้อนมให้แม่ พรุ่งนี้", m.taskText("เพิ่มงาน ซื้อนมให้แม่ พรุ่งนี้"))
        assertEquals("ส่งตาราง", m.taskText("สร้างงาน ส่งตาราง"))
        // The owner's own words that the app did not know: "สั่งสร้างงาน" asks to add a task, as "สร้างงาน" was taught.
        assertEquals(Intent.AddTask, m.read("สั่งสร้างงาน").best!!.intent)
    }

    @Test
    fun urgencyIsPartOfWhatIsAsked() {
        val m = AssistantMl.Model(taught, AssistantMl.seeds(SecretaryGuide.DEFAULT))
        val urgent = m.read("วันนี้มีงานอะไรด่วนบ้าง")
        assertTrue(urgent.best!!.intent.let { it is Intent.Do && it.action is SecretaryGuide.Action.Show })
        assertTrue(urgent.level != Level.LOW)
        // Without "ด่วน" the same words are not enough to be sure it means urgent work.
        assertNotEquals(Level.HIGH, m.read("มีงานอะไรบ้าง").level)
    }

    @Test
    fun theAppsOwnCommandsSuggestButNeverMakeItSure() {
        val seedsOnly = AssistantMl.Model(emptyList(), AssistantMl.seeds(SecretaryGuide.DEFAULT))
        // "วันนี้อยากวิ่ง" holds "วันนี้" (the Today command) but asks for time to run: asked, not done.
        val r = seedsOnly.read("วันนี้อยากวิ่ง")
        assertNotEquals(Level.HIGH, r.level)
        // Once taught, the same sentence is understood.
        val m = AssistantMl.Model(taught, AssistantMl.seeds(SecretaryGuide.DEFAULT))
        val t = m.read("วันนี้อยากวิ่ง")
        assertEquals(Intent.FindTime, t.best!!.intent)
        assertEquals(Level.HIGH, t.level)
        // A short command inside another word ("ต่อ" in "ติดต่อ") is never sure.
        assertNotEquals(Level.HIGH, seedsOnly.read("ติดต่อร้านซ่อมรถ").level)
    }

    @Test
    fun aLaterExampleWithTheSameWordsReplacesTheEarlierOne() {
        val first = ex("- งานด่วน → กรอง: ความสำคัญ สูง")
        val fixed = ex("- งานด่วน → กรอง: กำหนดจริงภายใน 3 วัน")
        val m = AssistantMl.Model(listOf(first, fixed))
        val r = m.read("งานด่วน")
        assertEquals(fixed.intent, r.best!!.intent)
        assertEquals(1, r.guesses.size)
    }

    @Test
    fun daysAndTimesAreGuessedForTheTeachPage() {
        val text = "เพิ่มงาน โทรหาร้านซ่อมรถ วันศุกร์ 9 โมง"
        val g = AssistantMl.guessLabels(text).map { text.substring(it.first) to it.second }
        assertEquals(listOf("วันศุกร์" to Label.DATE, "9 โมง" to Label.TIME), g)
        assertEquals(listOf("พรุ่งนี้" to Label.DATE, "14:30" to Label.TIME), AssistantMl.guessLabels("ส่งงานพรุ่งนี้ 14:30").map { "ส่งงานพรุ่งนี้ 14:30".substring(it.first) to it.second })
        assertEquals(listOf("บ่ายสองโมง" to Label.TIME), AssistantMl.guessLabels("ประชุมบ่ายสองโมง").map { "ประชุมบ่ายสองโมง".substring(it.first) to it.second })
    }

    @Test
    fun aPickedSentenceIsKeptWithItsParts() {
        val m = AssistantMl.Model(taught)
        assertEquals(
            listOf(Span("เพิ่มงาน", Label.COMMAND), Span(" "), Span("ซื้อนม", Label.TITLE), Span(" "), Span("พรุ่งนี้", Label.DATE)),
            m.spansFor("เพิ่มงาน ซื้อนม พรุ่งนี้", Intent.AddTask),
        )
        assertEquals(listOf(Span("สัปดาห์นี้มีอะไร")), m.spansFor("สัปดาห์นี้มีอะไร", Intent.Do(SecretaryGuide.Action.App(SecretaryGuide.Builtin.WEEK))))
    }

    @Test
    fun numbersThatMayBeAPatientsAreNeverKept() {
        assertTrue(AssistantMl.sensitive("โทรหาผู้ป่วย HN 1234567"))
        assertTrue(AssistantMl.sensitive("โทร 081-234-5678 พรุ่งนี้"))
        assertTrue(AssistantMl.sensitive("ขอเลขบัตรประชาชน"))
        assertTrue(AssistantMl.sensitive("1 2345 67890 12 3"))
        assertFalse(AssistantMl.sensitive("ประชุม 9:00 วันที่ 30/10/2569"))
        assertFalse(AssistantMl.sensitive("เพิ่มงาน ซื้อนม"))
    }

    @Test
    fun aSentenceTaughtWithAShortCommandIsDoneTheNextTime() {
        // What the screen promises after teaching ("จำแล้ว ครั้งหน้าพิมพ์แบบนี้ เลขาทำเลย") must be true for short Thai commands too.
        val jot = ex("- {จด|คำสั่ง} {ซื้อไข่|ชื่องาน} → เพิ่มงาน (2026-10-26, จาก สอนในแอป)")
        val appt = ex("- {นัด|คำสั่ง} {หมอฟัน|ชื่องาน} {พรุ่งนี้|วันที่} → เพิ่มงาน (2026-10-26, จาก สอนในแอป)")
        val m = AssistantMl.Model(listOf(jot, appt), AssistantMl.seeds(SecretaryGuide.DEFAULT))
        listOf("จด ซื้อไข่", "นัด หมอฟัน พรุ่งนี้", "จด ซื้อนม", "นัด ทำฟัน วันศุกร์").forEach { said ->
            val r = m.read(said)
            assertEquals(Level.HIGH, r.level, said)
            assertEquals(Intent.AddTask, r.best!!.intent, said)
        }
    }

    @Test
    fun aLongerSentenceThatMerelyHoldsAPickedOneIsNotTheSameQuestion() {
        // Picked from "หมายถึงอันนี้ไหม" and kept whole.
        val today = ex("- วันนี้ → ในแอป: วันนี้ (2026-10-26, จาก หมายถึงอันนี้ไหม)")
        val urgent = ex("- งานด่วน → กรอง: ความสำคัญ สูง (2026-10-26, จาก หมายถึงอันนี้ไหม)")
        val m = AssistantMl.Model(listOf(today, urgent), AssistantMl.seeds(SecretaryGuide.DEFAULT))
        assertEquals(Level.HIGH, m.read("วันนี้").level)
        assertEquals(Level.HIGH, m.read("งานด่วน").level)
        listOf("ส่งรายงานวันนี้", "โทรหาแม่วันนี้ตอนเย็น", "ส่งงานด่วนให้หัวหน้าพรุ่งนี้").forEach { said ->
            assertNotEquals(Level.HIGH, m.read(said).level, said)
        }
    }

    @Test
    fun aTaskNameBeforeTheCommandIsUnderstoodToo() {
        val m = AssistantMl.Model(listOf(ex("- {ซื้อนม|ชื่องาน} {จดไว้|คำสั่ง} → เพิ่มงาน")), AssistantMl.seeds(SecretaryGuide.DEFAULT))
        listOf("ซื้อนม จดไว้", "ซื้อไข่ จดไว้").forEach { said ->
            assertEquals(Level.HIGH, m.read(said).level, said)
            assertEquals(Intent.AddTask, m.read(said).best!!.intent, said)
        }
    }

    @Test
    fun aShortCommandMustBeAWordOfItsOwn() {
        val m = AssistantMl.Model(
            listOf(
                ex("- {จด|คำสั่ง} {ซื้อไข่|ชื่องาน} → เพิ่มงาน"),
                ex("- {ดู|คำสั่ง}{พรุ่งนี้|วันที่} → ในแอป: พรุ่งนี้"),
                ex("- {สรุป|คำสั่ง} → ในแอป: วันนี้"),
            ),
            AssistantMl.seeds(SecretaryGuide.DEFAULT),
        )
        listOf("จดหมายถึงแม่ พรุ่งนี้", "จดทะเบียนรถ", "ดูหนังกับแฟน", "พรุ่งนี้ดูหนัง", "สรุปประชุมให้หัวหน้า").forEach { said ->
            assertNotEquals(Level.HIGH, m.read(said).level, said)
        }
        listOf("จด ซื้อนม", "ดูพรุ่งนี้", "พรุ่งนี้ จด โทรหาแม่").forEach { said -> assertEquals(Level.HIGH, m.read(said).level, said) }
    }

    @Test
    fun aSentenceTaughtWithoutSpacesIsDoneWhenTypedTheSameWay() {
        val m = AssistantMl.Model(
            listOf(ex("- {จด|คำสั่ง}{ซื้อไข่|ชื่องาน} → เพิ่มงาน"), ex("- {นัด|คำสั่ง}{หมอฟัน|ชื่องาน}{พรุ่งนี้|วันที่} → เพิ่มงาน")),
            AssistantMl.seeds(SecretaryGuide.DEFAULT),
        )
        assertEquals(Level.HIGH, m.read("จดซื้อไข่").level)
        assertEquals(Level.HIGH, m.read("นัดหมอฟันพรุ่งนี้").level)
        assertEquals(Intent.AddTask, m.read("นัดหมอฟันพรุ่งนี้").best!!.intent)
        // A new task joined to the short command is asked, not done ("จดหมาย" may be a letter).
        assertEquals(Level.MEDIUM, m.read("จดซื้อนม").level)
    }

    @Test
    fun aShortCommandAfterTheTaskNameMustStandAlone() {
        val m = AssistantMl.Model(listOf(ex("- {ซื้อนม|ชื่องาน} {จด|คำสั่ง} → เพิ่มงาน"), ex("- {เพิ่ม|คำสั่ง} {ซื้อนม|ชื่องาน} → เพิ่มงาน")), AssistantMl.seeds(SecretaryGuide.DEFAULT))
        assertEquals(Level.HIGH, m.read("ซื้อไข่ จด").level)
        listOf("ส่งจดหมายถึงแม่", "จดทะเบียนรถ", "เพิ่มเติมรายละเอียดงาน", "เพิ่มน้ำหนัก").forEach { said -> assertNotEquals(Level.HIGH, m.read(said).level, said) }
    }

    @Test
    fun aDayBeforeTheTaskNameStillCountsAsAskingAfterIt() {
        val m = AssistantMl.Model(listOf(ex("- {พรุ่งนี้|วันที่}{ประชุมทีม|ชื่องาน}{ลงปฏิทิน|คำสั่ง} → เพิ่มงาน")), AssistantMl.seeds(SecretaryGuide.DEFAULT))
        assertEquals(Level.HIGH, m.read("วันศุกร์ไปหาหมอลงปฏิทิน").level)
    }

    @Test
    fun aListIsAskedForByItsWholeSentence() {
        val m = AssistantMl.Model(
            listOf(ex("- {งานค้าง|ชื่องาน}{มีอะไรบ้าง|คำสั่ง} → ในแอป: งานค้าง"), ex("- {ขอดูงาน|คำสั่ง}{ค้าง|ชื่องาน} → ในแอป: งานค้าง")),
            AssistantMl.seeds(SecretaryGuide.DEFAULT),
        )
        listOf("พรุ่งนี้มีอะไรบ้าง", "ตู้เย็นมีอะไรบ้าง", "ขอดูงานด่วน", "ขอดูงานพรุ่งนี้").forEach { said -> assertNotEquals(Level.HIGH, m.read(said).level, said) }
        assertEquals(Level.HIGH, m.read("งานค้างมีอะไรบ้าง").level)
    }

    @Test
    fun aTaughtSentenceTypedAgainIsDoneEvenNextToAnotherLikeIt() {
        val m = AssistantMl.Model(
            listOf(
                ex("- {จด|คำสั่ง} {ซื้อไข่|ชื่องาน} → เพิ่มงาน"), ex("- {จด|คำสั่ง} {ประชุม|ชื่องาน} → หาเวลา"),
                ex("- {งานค้าง|ชื่องาน}{มีอะไรบ้าง|คำสั่ง} → ในแอป: งานค้าง"), ex("- {งานด่วน|ชื่องาน}{มีอะไรบ้าง|คำสั่ง} → กรอง: ความสำคัญ สูง"),
            ),
            AssistantMl.seeds(SecretaryGuide.DEFAULT),
        )
        assertEquals(Intent.AddTask, m.read("จด ซื้อไข่").let { assertEquals(Level.HIGH, it.level); it.best!!.intent })
        assertEquals(Intent.FindTime, m.read("จด ประชุม").let { assertEquals(Level.HIGH, it.level); it.best!!.intent })
        assertEquals(Level.HIGH, m.read("งานด่วนมีอะไรบ้าง").level)
        assertTrue(m.read("งานด่วนมีอะไรบ้าง").best!!.intent.let { it is Intent.Do && it.action is SecretaryGuide.Action.Show })
    }
}
