package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

class SecretaryAiTest {

    @Test
    fun pricesFollowTheModel() {
        // Haiku 5.5: $0.10 in, $0.50 out per million tokens.
        assertEquals(0.00015 + 0.0002, SecretaryAi.usd(SecretaryAi.Mode.HAIKU, 1500, 400), 1e-12)
        // Sonnet 5.5 is twenty times that.
        assertEquals(20 * (0.00015 + 0.0002), SecretaryAi.usd(SecretaryAi.Mode.SONNET, 1500, 400), 1e-12)
        assertEquals(0.0, SecretaryAi.usd(SecretaryAi.Mode.RULES, 1500, 400), 0.0)
    }

    @Test
    fun anEstimateGuessesTheReplyByEffort() {
        val low = SecretaryAi.estimate(SecretaryAi.Mode.HAIKU, SecretaryAi.Effort.LOW, 1200)
        val high = SecretaryAi.estimate(SecretaryAi.Mode.HAIKU, SecretaryAi.Effort.HIGH, 1200)
        assertEquals(1200L, low.inTokens)
        assertTrue(high.outTokens > low.outTokens)
        assertEquals(low.inUsd, high.inUsd, 0.0)
    }

    @Test
    fun moneyReadsInBaht() {
        assertEquals("0.012", SecretaryAi.bahtText(0.0123))
        assertEquals("12.35", SecretaryAi.bahtText(12.345))
        val credit = SecretaryAi.Credit(quotaUsd = 100.0, spentUsd = 0.1, rate = 33.0)
        assertEquals("99.90 ดอลลาร์ (ราว 3,297 บาท)", SecretaryAi.leftText(credit))
    }

    @Test
    fun medicineAndPatientsStayOffTheModel() {
        assertTrue(SecretaryAi.isMedical("ยา vancomycin ปรับขนาดในไตเสื่อมยังไง"))
        assertTrue(SecretaryAi.isMedical("ผู้ป่วยรายนี้ควรได้ยาอะไร"))
        assertTrue(SecretaryAi.isMedical("dose ของ amoxicillin เด็ก"))
        // Thai words that merely contain ยา are not questions about medicines.
        assertFalse(SecretaryAi.isMedical("พยายามทำงานให้เสร็จ"))
        assertFalse(SecretaryAi.isMedical("ช่วงนี้ยุ่งมาก งานไหนพอเลื่อนได้บ้าง"))
        assertFalse(SecretaryAi.isMedical("งานนี้ยากและยาวมาก"))
    }

    @Test
    fun readsTheModelsAnswer() {
        val r = SecretaryAi.parse("""{"kind":"command","reply":"เข้าใจว่า: ดูงานที่เลื่อนได้\nเรียงจากสำคัญน้อย","command":"กรอง: ไม่มีวันที่","task":"","minutes":0}""")!!
        assertEquals(SecretaryAi.Kind.COMMAND, r.kind)
        assertEquals("เข้าใจว่า: ดูงานที่เลื่อนได้\nเรียงจากสำคัญน้อย", r.reply)
        assertEquals("กรอง: ไม่มีวันที่", r.command)
        // The command is in the guide's own words, so it can be kept as a taught word.
        assertTrue(SecretaryGuide.Action.parse(r.command) is SecretaryGuide.Action.Show)
        val t = SecretaryAi.parse("""{"kind":"find_time","reply":"หาเวลาวิ่ง","command":"","task":"วิ่ง สัปดาห์นี้","minutes":30}""")!!
        assertEquals(SecretaryAi.Kind.FIND_TIME, t.kind)
        assertEquals(30, t.minutes)
        assertEquals("ข้อความ \"ในเครื่องหมาย\"", SecretaryAi.parse("""{"kind":"answer","reply":"ข้อความ \"ในเครื่องหมาย\"","command":"","task":"","minutes":0}""")!!.reply)
        assertNull(SecretaryAi.parse("""{"kind":"something","reply":""}"""))
        assertNull(SecretaryAi.parse("not json"))
    }

    @Test
    fun theKeyWarnsAWeekBefore() {
        val expires = LocalDate.parse("2027-01-01")
        assertNull(SecretaryAi.keyWarning(expires, LocalDate.parse("2026-12-24")))
        assertEquals(7L, SecretaryAi.keyWarning(expires, LocalDate.parse("2026-12-25")))
        assertEquals(0L, SecretaryAi.keyWarning(expires, expires))
        assertEquals(-2L, SecretaryAi.keyWarning(expires, expires.plusDays(2L)))
        assertNull(SecretaryAi.keyWarning(null, expires))
    }

    @Test
    fun theInstructionsNameEveryCommandAndNoBannedMarks() {
        val s = SecretaryAi.system(LocalDate.parse("2026-10-26"))
        SecretaryGuide.Builtin.entries.forEach { assertTrue(it.th in s, it.th) }
        assertFalse('—' in s)
        assertFalse('·' in s)
    }
}
