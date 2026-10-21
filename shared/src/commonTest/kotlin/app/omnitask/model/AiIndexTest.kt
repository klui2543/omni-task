package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class AiIndexTest {
    private val oct = LocalDate.parse("2026-10-21")

    @Test
    fun monthKeysAndPaths() {
        assertEquals("2026-10", AiIndex.monthKey(oct))
        assertEquals("2026-10", AiIndex.monthKeyOf(AiMemory.monthFile(oct)))
        assertEquals(AiMemory.monthFile(oct), AiIndex.pathOf("2026-10"))
        assertEquals("2025-12", AiIndex.previous("2026-01"))
        assertEquals("2026-09", AiIndex.previous("2026-10"))
        assertEquals(null, AiIndex.monthKeyOf("${AiMemory.LOG_DIR}/index.md"))
    }

    @Test
    fun theHeaderIsAddedKeptAndClosed() {
        val old = "# Gemini log\nเกริ่น\n\n## 2026-10-20\n### 09:00 ก\n- ถาม: x\n"
        val withHead = AiIndex.withFront(old, "2026-10")
        assertEquals(mapOf("format" to AiIndex.FORMAT, "month" to "2026-10"), AiIndex.front(withHead))
        assertTrue(withHead.endsWith(old))
        assertFalse(AiIndex.isClosed(withHead))
        val closed = AiIndex.withFront(withHead.replace("month: 2026-10", "month: 2026-10\nmine: yes"), "2026-10", LocalDate.parse("2026-11-01"))
        assertEquals("yes", AiIndex.front(closed)["mine"])
        assertTrue(AiIndex.isClosed(closed))
        assertEquals(1, AiMemory.entries(closed).size, "the header is not read as an exchange")
        // Writing into a note from before headers existed gives it one.
        val added = AiMemory.addExchange(old, oct, "10:00", "ข", emptyList(), listOf("ถาม: y"))
        assertEquals("2026-10", AiIndex.front(added)["month"])
        assertEquals(2, AiMemory.entries(added).size)
    }

    @Test
    fun digestChangesWithTheExchangesOnly() {
        val a = AiMemory.addExchange(null, oct, "09:00", "ก", emptyList(), listOf("ถาม: x"))
        val e = AiMemory.entries(a)
        assertEquals(AiIndex.digest(e), AiIndex.digest(AiMemory.entries(AiIndex.withFront(a, "2026-10", oct))))
        assertNotEquals(AiIndex.digest(e), AiIndex.digest(AiMemory.entries(a.replace("ถาม: x", "ถาม: z"))))
    }

    @Test
    fun monthLinesGoUnderTheirYearNewestFirstAndKeepOtherLines() {
        var idx = AiIndex.setMonth(null, AiIndex.Month("2026-09", 3, "คุยเรื่องป้าย", listOf("ป้าย")))
        idx = AiIndex.setMonth(idx, AiIndex.Month("2026-10", 5, "ตกลงทำ Flow", listOf("flow", "siriraj")))
        idx = AiIndex.setMonth(idx, AiIndex.Month("2025-12", 1, AiIndex.AUTO + "ทดลอง", emptyList()))
        idx = idx.replace("## 2026\n", "## 2026\nโน้ตของเจ้าของ\n")
        idx = AiIndex.setMonth(idx, AiIndex.Month("2026-09", 4, "คุยเรื่องป้ายและงบ", listOf("ป้าย")))
        val lines = idx.lines()
        assertEquals("---", lines[0])
        assertTrue("โน้ตของเจ้าของ" in lines, "the owner's own line stays")
        assertTrue(lines.indexOf("## 2026") < lines.indexOf("## 2025"))
        val ms = AiIndex.months(idx)
        assertEquals(listOf("2026-10", "2026-09", "2025-12"), ms.map { it.key })
        assertEquals(4, ms[1].count)
        assertEquals("คุยเรื่องป้ายและงบ", ms[1].summary)
        assertEquals(listOf("flow", "siriraj"), ms[0].tags)
        assertTrue(ms[2].auto)
        val oct = lines.indexOfFirst { "|2026-10]]" in it }
        val sep = lines.indexOfFirst { "|2026-09]]" in it }
        assertTrue(oct in 0 until sep)
        assertTrue(lines[oct].startsWith("- [[${AiMemory.LOG_DIR}/2026/10 Oct|2026-10]] 5 ครั้ง: ตกลงทำ Flow #flow #siriraj"))

        idx = AiIndex.setYear(idx, "2025", "ปีแรก ทดลองระบบ")
        idx = AiIndex.setYear(idx, "2025", "ปีแรก ทดลองระบบผู้ช่วย")
        assertEquals(mapOf("2025" to "ปีแรก ทดลองระบบผู้ช่วย"), AiIndex.years(idx))
        assertEquals(3, AiIndex.months(idx).size)
        assertFalse(idx.contains("\n\n\n"))
        val model = AiIndex.forModel(idx)
        assertTrue(model.lines().first().startsWith("2026-10"))
        assertTrue("ปี 2025: ปีแรก" in model)
        assertEquals(listOf("2026-09"), AiIndex.monthsByWords(AiIndex.months(idx), "งบ ป้าย", 1).map { it.key })
    }

    @Test
    fun autoSummaryAndYearReadiness() {
        var log: String? = null
        log = AiMemory.addExchange(log, oct, "09:00", "ตกลงทำระบบป้าย", listOf("ป้าย"), listOf("ถาม: x"))
        log = AiMemory.addExchange(log, oct, "10:00", "เลื่อน Flow", listOf("ป้าย", "flow"), listOf("ถาม: y"))
        val m = AiIndex.autoMonth("2026-10", AiMemory.entries(log))
        assertTrue(m.auto)
        assertEquals(2, m.count)
        assertEquals(listOf("ป้าย", "flow"), m.tags)
        assertTrue("เลื่อน Flow" in m.summary)
        val ms = listOf(AiIndex.Month("2025-11", 1, "ก", emptyList()), AiIndex.Month("2025-12", 1, "ข", emptyList()))
        assertTrue(AiIndex.yearReady("2025", ms, oct))
        assertFalse(AiIndex.yearReady("2026", ms + AiIndex.Month("2026-01", 1, "ค", emptyList()), oct))
        assertFalse(AiIndex.yearReady("2025", ms + m.copy(key = "2025-10"), oct), "not while a month is summed up by topics only")
    }

    @Test
    fun summaryRequestsAndAnswers() {
        val log = AiMemory.addExchange(null, oct, "09:00", "ก", emptyList(), listOf("ถาม: x"))
        val body = Json.parseToJsonElement(AiIndex.monthRequest("2026-10", AiMemory.entries(log))).jsonObject
        assertTrue(body.toString().contains("2026-10"))
        val response = """{"candidates":[{"content":{"parts":[{"text":"เดือนนี้ตกลงทำป้าย — แล้ว\nเลื่อน Flow"}]}}]}"""
        assertEquals("เดือนนี้ตกลงทำป้าย, แล้ว เลื่อน Flow", AiIndex.summaryText(response))
        assertTrue(AiIndex.readmeNeeded(null))
        assertFalse(AiIndex.readmeNeeded(AiIndex.README))
        assertFalse(AiIndex.README.contains("—") || AiIndex.README.contains("·"))
    }
}
