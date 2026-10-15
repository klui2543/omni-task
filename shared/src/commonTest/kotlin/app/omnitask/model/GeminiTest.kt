package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.web.WebAssistant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiTest {

    private val today = LocalDate(2026, 10, 15)
    private val now = LocalDateTime(2026, 10, 15, 9, 30)

    private fun task(raw: String, line: Int) = TaskLine.parse(raw)!!.copy(fileUri = "f", lineIndex = line)

    private val tasks = listOf(
        task("- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-16", 0),
        task("- [x] จ่ายค่าไฟ ✅ 2026-10-10", 1),
        task("- [ ] ตอบอีเมลทุน #รอ/พี่เอ ➕ 2026-09-26", 2),
    )

    /** A generateContent response whose text is [answer]. */
    private fun response(answer: String) = """{"candidates":[{"content":{"role":"model","parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), answer)}}]},"finishReason":"STOP"}]}"""

    @Test
    fun onlyOpenTasksAreNumbered() {
        val editable = Gemini.editable(tasks)
        assertEquals(listOf("ส่งรายงาน", "ตอบอีเมลทุน"), editable.map { it.title })
    }

    @Test
    fun theRequestCarriesTheDayTheNumberedTasksAndTheHistory() {
        val body = Gemini.request("วันนี้ 2026-10-15", Gemini.editable(tasks), listOf(Gemini.Turn("ถามก่อน", "ตอบก่อน")), "เลื่อนรายงานไปพรุ่งนี้", now)
        val root = Json.parseToJsonElement(body).jsonObject
        val contents = root["contents"]!!.jsonArray
        assertEquals(listOf("user", "model", "user"), contents.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        val last = contents.last().jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("1. - [ ] ส่งรายงาน" in last, last)
        assertTrue("2. - [ ] ตอบอีเมลทุน" in last, last)
        assertTrue(last.endsWith("เลื่อนรายงานไปพรุ่งนี้"), last)
        val system = root["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("2026-10-15" in system && "09:30" in system, system)
        val config = root["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertTrue(config["responseSchema"] is JsonObject)
    }

    @Test
    fun changesAreCheckedAgainstTheList() {
        val answer = """
            {"reply":"เสนอเลื่อนรายงาน — และปิดงานที่ 2 · ครับ","changes":[
              {"op":"due","task":1,"date":"2026-10-17","why":"ยังไม่ทัน"},
              {"op":"remind","task":1,"time":"9:00","why":"เช้า"},
              {"op":"done","task":2,"why":"ได้คำตอบแล้ว"},
              {"op":"due","task":9,"date":"2026-10-17","why":"เลขไม่มี"},
              {"op":"due","task":1,"date":"พรุ่งนี้","why":"วันอ่านไม่ได้"},
              {"op":"priority","task":2,"priority":"High","why":"สำคัญ"},
              {"op":"priority","task":2,"priority":"urgent","why":"ไม่มีระดับนี้"},
              {"op":"delete","task":1,"why":"ทำไม่ได้"},
              {"op":"add","task":0,"title":"  นัด  หมอ ","date":"2026-10-20","why":"ใหม่"},
              {"op":"scheduled","task":"2","date":"","why":"ลบวัน"}
            ]}
        """.trimIndent()
        val a = Gemini.parse(response(answer), 2)
        assertEquals("เสนอเลื่อนรายงาน, และปิดงานที่ 2, ครับ", a.reply)
        assertEquals(
            listOf(
                Gemini.Change(Gemini.DUE, 0, date = "2026-10-17", why = "ยังไม่ทัน"),
                Gemini.Change(Gemini.REMIND, 0, time = "09:00", why = "เช้า"),
                Gemini.Change(Gemini.DONE, 1, why = "ได้คำตอบแล้ว"),
                Gemini.Change(Gemini.PRIORITY, 1, priority = "HIGH", why = "สำคัญ"),
                Gemini.Change(Gemini.ADD, -1, date = "2026-10-20", title = "นัด หมอ", why = "ใหม่"),
                Gemini.Change(Gemini.SCHEDULED, 1, date = null, why = "ลบวัน"),
            ),
            a.changes,
        )
    }

    @Test
    fun thoughtsAreLeftOutAndPlainTextStillReads() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"คิดอยู่","thought":true},{"text":"ไม่ใช่ JSON"}]}}]}"""
        assertEquals(Gemini.Answer("ไม่ใช่ JSON", emptyList()), Gemini.parse(body, 2))
    }

    @Test
    fun noAnswerSaysWhy() {
        val e = assertFailsWith<Gemini.Failure> { Gemini.parse("""{"promptFeedback":{"blockReason":"SAFETY"}}""", 0) }
        assertTrue("SAFETY" in e.message!!, e.message)
    }

    @Test
    fun errorsReadAsPlainWords() {
        val key = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        assertTrue("API key" in Gemini.errorMessage(400, key))
        val quota = """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""
        assertTrue("โควตา" in Gemini.errorMessage(429, quota))
        assertTrue("ไม่ว่าง" in Gemini.errorMessage(503, "Service Unavailable"))
    }

    @Test
    fun aLineTakesItsChangesInTaskForgeOrder() {
        val raw = "- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-16"
        val changes = listOf(
            Gemini.Change(Gemini.REMIND, 0, time = "09:00"),
            Gemini.Change(Gemini.SCHEDULED, 0, date = "2026-10-16"),
            Gemini.Change(Gemini.DUE, 0, date = "2026-10-17"),
            Gemini.Change(Gemini.PRIORITY, 0, priority = "NONE"),
        )
        assertEquals("- [ ] ส่งรายงาน #remind-at-scheduled 🎯 09:00 ➕ 2026-10-01 ⏳ 2026-10-16 📅 2026-10-17", Gemini.line(raw, changes))
        assertEquals("- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01", Gemini.line(raw, listOf(Gemini.Change(Gemini.DUE, 0))))
    }

    @Test
    fun doneWinsOverTheOtherChangesOfItsTask() {
        val picked = listOf(
            Gemini.Change(Gemini.DUE, 0, date = "2026-10-17"),
            Gemini.Change(Gemini.DONE, 0),
            Gemini.Change(Gemini.PRIORITY, 1, priority = "HIGH"),
            Gemini.Change(Gemini.ADD, -1, title = "ใหม่"),
        )
        assertEquals(mapOf(0 to listOf(Gemini.Change(Gemini.DONE, 0)), 1 to listOf(picked[2])), Gemini.byTask(picked))
    }

    @Test
    fun aNewTaskIsAQuickAddLine() {
        val c = Gemini.Change(Gemini.ADD, title = "นัดหมอ", date = "2026-10-20", time = "14:00", priority = "HIGH")
        assertEquals("- [ ] นัดหมอ #remind-at-due ⏰ 14:00 ⏫ ➕ 2026-10-15 📅 2026-10-20", Gemini.newLine(c, today))
        assertFalse("—" in Gemini.describe(c) || "·" in Gemini.describe(c))
    }

    @Test
    fun theWebGetsTheKeysInTheOrderOfTheNumbers() {
        val note = tasks.joinToString("\n") { it.raw }
        val out = Json.parseToJsonElement(WebAssistant.geminiRequest("n", "n.md", note, """{"now":"2026-10-15T09:30"}""", "[]", "สวัสดี")).jsonObject
        assertEquals(listOf("n#0", "n#2"), out["keys"]!!.jsonArray.map { it.jsonPrimitive.content })
        val edited = Json.parseToJsonElement(WebAssistant.geminiLine(note, tasks[0].raw, 0, """[{"op":"due","index":0,"date":"2026-10-20"}]""")).jsonObject
        assertEquals(
            "- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-20",
            edited["text"]!!.jsonPrimitive.content.lines().first(),
        )
        val gone = Json.parseToJsonElement(WebAssistant.geminiLine(note, "- [ ] ไม่มีแล้ว", 0, "[]")).jsonObject
        assertEquals("conflict", gone["error"]!!.jsonPrimitive.content)
    }
}
