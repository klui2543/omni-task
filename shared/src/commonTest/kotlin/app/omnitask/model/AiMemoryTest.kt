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
    fun theLogKeepsDaysNewestFirstAndEntriesInOrder() {
        var log = AiMemory.addToLog(null, d20, listOf("09:00 ถาม: อะไรค้าง", "  Gemini (Flash-Lite): งานป้าย\nเลยกำหนด"))
        log = AiMemory.addToLog(log, d21, listOf("08:00 ถาม: วันนี้มีอะไร"))
        log = AiMemory.addToLog(log, d21, listOf("08:05 ทำแล้ว: เพิ่มงาน \"ซื้อน้ำเกลือ\""))
        val lines = log.lines()
        assertTrue(lines[0].startsWith("# Gemini log"))
        assertTrue(lines.indexOf("## 2026-10-21") < lines.indexOf("## 2026-10-20"))
        assertTrue(lines.indexOf("- 08:00 ถาม: วันนี้มีอะไร") < lines.indexOf("- 08:05 ทำแล้ว: เพิ่มงาน \"ซื้อน้ำเกลือ\""))
        assertTrue("  - Gemini (Flash-Lite): งานป้าย / เลยกำหนด" in lines)
        assertFalse(log.contains("- [ ]"), "never a checkbox")
        assertFalse(AiMemory.addToLog(null, d20, listOf("[ ] แอบเป็นงาน")).contains("- [ ]"))
    }

    @Test
    fun memoryAddsOnceAndGoesToTheModelAsBullets() {
        var m = AiMemory.remember(null, "ไม่ชอบงานด่วนหลัง 4 ทุ่ม", d20)
        m = AiMemory.remember(m, "ไม่ชอบงานด่วนหลัง 4 ทุ่ม", d21)
        assertEquals(1, m.lines().count { it.startsWith("- ") })
        assertEquals("- ไม่ชอบงานด่วนหลัง 4 ทุ่ม (2026-10-20)", AiMemory.memoryForModel(m))
    }

    @Test
    fun aLongLogSendsTheNewestPartAndMatchingOlderLines() {
        var log: String? = null
        for (i in 1..60) log = AiMemory.addToLog(log, LocalDate(2026, 1, 1).plusDays(i.toLong()), listOf("10:00 ถาม: เรื่องทั่วไป ${"x".repeat(200)}"))
        log = AiMemory.addToLog(log, LocalDate.parse("2026-01-02"), listOf("11:00 ถาม: ระบบป้าย ตกลงทำก่อน Flow"))
        val small = AiMemory.logForModel(log, "ระบบป้าย ตกลงอะไรไว้", max = 3000)
        assertTrue(small.length <= 3200)
        assertTrue("ตกลงทำก่อน Flow" in small, small)
        assertTrue(AiMemory.logForModel(log, "x", max = 1_000_000).startsWith("## "))
    }

    @Test
    fun memoryAndHistoryGoIntoTheRequestBeforeTheTasks() {
        val pic = AiAssist.Picture("t1: งาน", emptyMap())
        val body = Json.parseToJsonElement(AiAssist.chatRequest(pic, emptyList(), "hi", memory = "- จำนี้", history = "## 2026-10-21\n- ถาม: เมื่อเช้า")).jsonObject
        val sys = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(sys.indexOf("- จำนี้") in 0 until sys.indexOf("t1: งาน"))
        assertTrue(sys.indexOf("ถาม: เมื่อเช้า") in 0 until sys.indexOf("t1: งาน"))
    }
}
