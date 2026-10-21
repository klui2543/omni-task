package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import app.omnitask.time.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiMemoryTest {
    private val d20 = LocalDate.parse("2026-10-20")
    private val d21 = LocalDate.parse("2026-10-21")

    @Test
    fun exchangesGetAHeadingWithTagsAndDaysStayNewestFirst() {
        var log = AiMemory.addExchange(null, d20, "09:00", "งานไหนค้าง", listOf("#siriraj"), listOf("ถาม: อะไรค้าง", "Gemini (Flash-Lite): งานป้าย\nเลยกำหนด"))
        log = AiMemory.addExchange(log, d21, "08:00", "ตกลงทำระบบป้ายก่อน Flow", listOf("siriraj", "ป้าย"), listOf("ถาม: ทำอะไรก่อน"))
        log = AiMemory.addNote(log, d21, "08:05 ทำแล้ว: เลื่อน \"Flow\" ไป 2026-10-24")
        val lines = log.lines()
        assertTrue(lines[0].startsWith("# Gemini log"))
        assertTrue(lines.indexOf("## 2026-10-21") < lines.indexOf("## 2026-10-20"))
        assertTrue("### 08:00 ตกลงทำระบบป้ายก่อน Flow #siriraj #ป้าย" in lines)
        assertTrue(lines.indexOf("- ถาม: ทำอะไรก่อน") < lines.indexOf("- 08:05 ทำแล้ว: เลื่อน \"Flow\" ไป 2026-10-24"))
        assertTrue("- Gemini (Flash-Lite): งานป้าย / เลยกำหนด" in lines)
        assertFalse(log.contains("- [ ]"), "never a checkbox")

        val e = AiMemory.entries(log)
        assertEquals(listOf("ตกลงทำระบบป้ายก่อน Flow", "งานไหนค้าง"), e.map { it.topic })
        assertEquals(listOf("siriraj", "ป้าย"), e[0].tags)
        assertTrue("ทำแล้ว" in e[0].body)
        assertEquals("2026-10-21 08:00 ตกลงทำระบบป้ายก่อน Flow #siriraj #ป้าย\n2026-10-20 09:00 งานไหนค้าง #siriraj", AiMemory.index(e))
    }

    @Test
    fun theOldFormGetsHeadingsFromItsQuestions() {
        val old = "# Gemini log\n\n## 2026-10-20\n- 09:00 ถาม: อะไรค้างนานสุด\n  - Gemini (Flash-Lite): งานป้าย\n- 09:02 ทำแล้ว: จดค้าง"
        val e = AiMemory.entries(old)
        assertEquals(1, e.size)
        assertEquals("อะไรค้างนานสุด", e[0].topic)
        assertTrue("Gemini (Flash-Lite): งานป้าย" in e[0].body && "ทำแล้ว" in e[0].body)
        val up = AiMemory.addExchange(old, d21, "10:00", "ใหม่", emptyList(), listOf("ถาม: x"))
        assertTrue("### 09:00 อะไรค้างนานสุด" in up.lines())
        assertFalse(up.lines().any { it.startsWith("- 09:00 ถาม") })
    }

    @Test
    fun memoryAddsOnceAndGoesToTheModelAsBullets() {
        var m = AiMemory.remember(null, "ไม่ชอบงานด่วนหลัง 4 ทุ่ม", d20)
        m = AiMemory.remember(m, "ไม่ชอบงานด่วนหลัง 4 ทุ่ม", d21)
        assertEquals(1, m.lines().count { it.startsWith("- ") })
        assertEquals("- ไม่ชอบงานด่วนหลัง 4 ทุ่ม (2026-10-20)", AiMemory.memoryForModel(m))
    }

    private fun longLog(): String {
        var log: String? = null
        log = AiMemory.addExchange(log, LocalDate.parse("2026-01-02"), "11:00", "ตกลงทำระบบป้ายก่อน Flow", listOf("ป้าย"), listOf("ถาม: ระบบป้าย ตกลงทำก่อน Flow"))
        for (i in 2..90) log = AiMemory.addExchange(log, LocalDate(2026, 1, 1).plusDays(i.toLong()), "10:00", "เรื่องทั่วไป $i", emptyList(), listOf("ถาม: " + "x".repeat(600)))
        return log!!
    }

    @Test
    fun aLongLogSendsTheIndexTheNewestAndWhatIsFound() {
        val e = AiMemory.entries(longLog())
        assertFalse(AiMemory.allSent(e))
        val found = AiMemory.byWords(e, "ระบบป้าย ตกลงอะไรไว้", 3)
        assertEquals("ตกลงทำระบบป้ายก่อน Flow", found.first().topic)
        val h = AiMemory.history(e, found)
        assertTrue(h.startsWith("[สารบัญทุกครั้งที่เคยคุย"))
        assertTrue("2026-01-02 11:00 ตกลงทำระบบป้ายก่อน Flow #ป้าย" in h, "the index reaches back to the first one")
        assertTrue("[บทสนทนาเก่าที่เกี่ยวกับคำถามนี้]" in h && "ถาม: ระบบป้าย ตกลงทำก่อน Flow" in h)
        assertTrue(h.length < AiMemory.RECENT + 20_000)
        // A short log goes whole, with nothing to search.
        val short = AiMemory.entries(AiMemory.addExchange(null, d21, "08:00", "ทักทาย", emptyList(), listOf("ถาม: hi")))
        assertTrue(AiMemory.allSent(short))
        assertTrue("[บทสนทนาทั้งหมด]" in AiMemory.history(short))
    }

    @Test
    fun theClosestInMeaningComeFirst() {
        val docs = mapOf("a" to floatArrayOf(1f, 0f, 0f), "b" to floatArrayOf(0.9f, 0.1f, 0f), "c" to floatArrayOf(0f, 1f, 0f))
        assertEquals(listOf("a", "b"), AiMemory.nearest(floatArrayOf(1f, 0f, 0f), docs, 5))
        assertEquals(0f, AiMemory.cosine(floatArrayOf(), floatArrayOf()))
        val body = Json.parseToJsonElement(AiMemory.embedRequest("gemini-embedding-001", listOf("x", "y"), query = true)).jsonObject
        val r = body["requests"]!!.jsonArray
        assertEquals(2, r.size)
        assertEquals("RETRIEVAL_QUERY", r[0].jsonObject["taskType"]!!.jsonPrimitive.content)
        assertEquals("models/gemini-embedding-001", r[0].jsonObject["model"]!!.jsonPrimitive.content)
        val v = AiMemory.parseEmbeddings("""{"embeddings":[{"values":[0.5,-0.25]},{"values":[1]}]}""")
        assertEquals(listOf(0.5f, -0.25f), v[0].toList())
        assertEquals(1, v[1].size)
    }

    @Test
    fun memoryHistoryAndSearchGoIntoTheRequest() {
        val pic = AiAssist.Picture("t1: งาน", emptyMap())
        val body = Json.parseToJsonElement(AiAssist.chatRequest(pic, emptyList(), "hi", memory = "- จำนี้", history = "[สารบัญ]\n2026-10-21 เมื่อเช้า", canSearch = true)).jsonObject
        val sys = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(sys.indexOf("- จำนี้") in 0 until sys.indexOf("t1: งาน"))
        assertTrue(sys.indexOf("เมื่อเช้า") in 0 until sys.indexOf("t1: งาน"))
        val ask = body["contents"]!!.jsonArray.last().jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("search" in ask)
        val again = Json.parseToJsonElement(AiAssist.chatRequest(pic, emptyList(), "hi", searched = "ผลค้น")).jsonObject
        val ask2 = again["contents"]!!.jsonArray.last().jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("ห้ามขอค้นเพิ่ม" in ask2 && "ผลค้น" in ask2)
    }
}
