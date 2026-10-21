package app.omnitask.model

import app.omnitask.data.VaultText
import app.omnitask.notify.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiAssistTest {

    private val today = LocalDate.parse("2026-10-20")

    private val file = listOf(
        "- [ ] ตอบอีเมลลูกค้า #งานประจำ #kluido 📅 2026-10-18 ➕ 2026-09-01",
        "    - ค้าง 2026-09-29 รอตัดสินใจเรื่องราคา",
        "- [ ] ใส่ token ให้ Claude #claude #kluido ➕ 2026-10-01 🆔 tok",
        "- [ ] ตั้ง sync vault #claude #claudedo ➕ 2026-10-01 ⛔ tok",
        "- [ ] เรียนเปียโน #สักวัน ➕ 2026-01-01",
        "    - [ ] หาครู",
        "- [x] งานเสร็จแล้ว ✅ 2026-10-19",
    )

    private fun tasks() = VaultText.parseFile("f", "Omni note.md", file.joinToString("\n"))

    private fun picture(): AiAssist.Picture {
        val all = tasks()
        return AiAssist.picture(
            all, { t -> all.filter { it.parent == t.key } }, today,
            events = listOf(CalendarEvent(1, "ประชุมลูกค้า", LocalDateTime.parse("2026-10-21T14:00"), LocalDateTime.parse("2026-10-21T15:00"))),
            profile = listOf("ตื่น 07:00"),
        )
    }

    /** Wraps [text] the way generateContent returns the model's words. */
    private fun response(text: String) = """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(text))}}]},"finishReason":"STOP"}]}"""

    @Test
    fun thePictureGivesEachOpenTopLevelTaskAHandleAndItsStory() {
        val p = picture()
        assertEquals(3, p.refs.size, "open top-level tasks only: no subtask, nothing done, someday only counted")
        // Dated work first.
        assertEquals("ตอบอีเมลลูกค้า", p.refs["t1"]!!.title)
        assertTrue("งานพักไว้ #สักวัน อีก 1 งาน" in p.text)
        val line = p.text.lines().first { it.startsWith("t1:") }
        assertTrue("เลยมา 2 วัน" in line, line)
        assertTrue("บันทึกค้าง: 2026-09-29 รอตัดสินใจเรื่องราคา" in line, line)
        assertTrue("ตาของเจ้าของ" in line)
        val sync = p.text.lines().first { "ตั้ง sync vault" in it }
        assertTrue("ตาของ Claude" in sync)
        // What it waits for is named by its handle.
        val tokRef = p.refs.entries.first { it.value.title == "ใส่ token ให้ Claude" }.key
        assertTrue("รอ $tokRef" in sync, sync)
        assertTrue("2026-10-21 14:00 ถึง 15:00 ประชุมลูกค้า" in p.text)
        assertTrue("- ตื่น 07:00" in p.text)
    }

    @Test
    fun theBriefRequestAsksForJsonWithTheRulesAndThePicture() {
        val body = Json.parseToJsonElement(AiAssist.briefRequest(picture())).jsonObject
        val sys = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("ภาษาไทย" in sys && "t1: ตอบอีเมลลูกค้า" in sys)
        val config = body["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertTrue("picks" in config["responseSchema"]!!.jsonObject["properties"]!!.jsonObject)
        assertEquals("minimal", config["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
        val plain = Json.parseToJsonElement(AiAssist.withoutThinking(AiAssist.briefRequest(picture()))).jsonObject
        assertTrue("thinkingConfig" !in plain["generationConfig"]!!.jsonObject)
        assertTrue("responseSchema" in plain["generationConfig"]!!.jsonObject)
    }

    @Test
    fun theChatRequestKeepsOnlyTheLastTurns() {
        val turns = (1..12).map { AiAssist.Turn(it % 2 == 1, "turn $it") }
        val body = Json.parseToJsonElement(AiAssist.chatRequest(picture(), turns, "อะไรค้างนานสุด")).jsonObject
        val contents = body["contents"]!!.jsonArray
        assertEquals(9, contents.size, "8 earlier turns and the question")
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue("อะไรค้างนานสุด" in contents.last().jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun theBriefKeepsPicksThatNameATask() {
        val p = picture()
        val brief = AiAssist.parseBrief(
            response("""{"summary":"งานติดที่คุณ — มากกว่าที่ Claude","picks":[{"ref":"t1","reason":"ลูกค้ารอคำตอบ"},{"ref":"t99","reason":"ไม่มีงานนี้"},{"ref":"t1","reason":"ซ้ำ"}]}"""),
            p,
        )
        assertEquals("งานติดที่คุณ, มากกว่าที่ Claude", brief.summary)
        assertEquals(listOf("ตอบอีเมลลูกค้า"), brief.picks.map { it.task.title })
        assertEquals("ลูกค้ารอคำตอบ", brief.picks[0].reason)
    }

    @Test
    fun theReplyTurnsProposalsIntoActionsAndSeveralMovesIntoOneForClaude() {
        val p = picture()
        val one = AiAssist.parseReply(
            response("""{"answer":"ลองแบ่งงานก่อน","actions":[{"kind":"subtasks","ref":"t1","items":["ร่างราคา","ส่งอีเมล"]},{"kind":"stuck","ref":"t2","text":"ยังไม่ได้ติดตั้ง"},{"kind":"move","ref":"t1","date":"2026-10-22"},{"kind":"move","ref":"t9","date":"2026-10-22"}]}"""),
            p,
        )
        assertEquals("ลองแบ่งงานก่อน", one.text)
        assertEquals(3, one.actions.size)
        assertEquals(listOf("ร่างราคา", "ส่งอีเมล"), (one.actions[0] as AiAssist.Action.Subtasks).titles)
        assertEquals("ยังไม่ได้ติดตั้ง", (one.actions[1] as AiAssist.Action.Stuck).reason)
        assertEquals(LocalDate.parse("2026-10-22"), (one.actions[2] as AiAssist.Action.Move).date)

        val many = AiAssist.parseReply(
            response("""{"answer":"เลื่อนสองงาน","actions":[{"kind":"move","ref":"t1","date":"2026-10-27"},{"kind":"move","ref":"t2","date":"2026-10-28"},{"kind":"move","ref":"t3","date":"bad"}]}"""),
            p,
        )
        val big = many.actions.single() as AiAssist.Action.Big
        assertTrue("ตอบอีเมลลูกค้า" in big.text && "2026-10-28" in big.text, big.text)
        assertTrue(AiAssist.forClaude(big, today).startsWith("#claudecheck"))
    }

    @Test
    fun errorsFromGeminiAreSaidInItsOwnWords() {
        val e = assertFailsWith<AiAssist.Failure> { AiAssist.parseBrief("""{"error":{"code":400,"message":"API key not valid."}}""", picture()) }
        assertEquals("API key not valid.", e.message)
        assertFailsWith<AiAssist.Failure> { AiAssist.parseBrief("""{"promptFeedback":{"blockReason":"SAFETY"}}""", picture()) }
        assertFailsWith<AiAssist.Failure> { AiAssist.parseBrief(response("not json"), picture()) }
    }

    @Test
    fun thoughtsAreLeftOutOfTheAnswer() {
        val r = """{"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"{\"summary\":\"ok\",\"picks\":[]}"}]}}]}"""
        assertEquals("ok", AiAssist.parseBrief(r, picture()).summary)
    }

    @Test
    fun theNewestStableFlashIsPickedFromTheModelList() {
        val list = """{"models":[
            {"name":"models/gemini-3.5-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.9-flash-preview","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.8-flash-lite","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-4-flash","supportedGenerationMethods":["embedContent"]}
        ]}"""
        assertEquals("gemini-3.8-flash", AiAssist.newestFlash(list))
        assertNull(AiAssist.newestFlash("""{"models":[]}"""))
        assertFalse(AiAssist.newestFlash("oops") != null)
    }
}
