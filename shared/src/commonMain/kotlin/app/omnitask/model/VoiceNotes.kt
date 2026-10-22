package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Tasks spoken into the phone: a recording goes to Gemini, which writes it down and splits it into tasks. They are
 * kept rough, as the owner said them: no date, tag or priority is set, even when the words say "tomorrow" (most of
 * the owner's dates are their own targets, so they are placed later). The lines land in the no-project tray.
 */
object VoiceNotes {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val RULES = """ไฟล์เสียงนี้คือเจ้าของงานพูดงานที่ต้องทำ เป็นภาษาไทยปนอังกฤษ
ถอดเสียงแล้วแยกเป็นรายการงาน งานละหนึ่งรายการ
- ใช้คำของเจ้าของงานให้มากที่สุด ตัดคำเติมอย่าง เอ่อ อ่า แล้วก็ ออก แต่ไม่แต่งเพิ่ม
- ถ้าพูดถึงวันหรือเวลา เช่น พรุ่งนี้ วันศุกร์ ให้คงไว้ในข้อความของงาน
- เรื่องเดียวที่พูดยาวหลายประโยค ให้เป็นงานเดียว
- ไม่ใส่วันที่ แท็ก ความสำคัญ หรืออีโมจิ
- ถ้าไม่มีงานเลย ตอบรายการว่าง"""

    /** The generateContent body for one recording, [audioBase64] in [mime] (for example audio/mp4). */
    fun request(audioBase64: String, mime: String): String = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", RULES) })
                    add(buildJsonObject { putJsonObject("inlineData") { put("mimeType", mime); put("data", audioBase64) } })
                }
            })
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("tasks") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
                }
                putJsonArray("required") { add(JsonPrimitive("tasks")) }
            }
            put("temperature", 0.2)
            put("maxOutputTokens", 4096)
            putJsonObject("thinkingConfig") { put("thinkingLevel", "minimal") }
        }
    }.toString()

    // Live notes: the recording goes to Gemini a few seconds at a time, each piece with what came before, so the
    // words and the task list show while the owner speaks.

    private const val LIVE_RULES = """เจ้าของงานกำลังพูดงานที่ต้องทำ ไฟล์เสียงนี้คือช่วงล่าสุดไม่กี่วินาที เป็นภาษาไทยปนอังกฤษ
- text: ถอดเสียงช่วงนี้ตามที่พูดจริง ต่อจากข้อความก่อนหน้า ไม่ต้องซ้ำข้อความก่อนหน้า ถ้าเงียบให้เป็นข้อความว่าง
- tasks: รายการงานทั้งหมดหลังฟังช่วงนี้ คงงานเดิมไว้ เพิ่มงานใหม่ที่เพิ่งพูด ถ้าช่วงนี้พูดแก้งานเดิมให้แก้งานนั้น
- ใช้คำของเจ้าของงาน ตัดคำเติมอย่าง เอ่อ อ่า ออก ถ้าพูดถึงวันหรือเวลาให้คงไว้ในข้อความ ไม่ใส่วันที่ แท็ก หรืออีโมจิ
- ห้ามใส่งานที่เจ้าของงานลบทิ้งแล้วกลับมา"""

    private fun context(before: String, tasks: List<String>, removed: List<String>) = buildString {
        if (before.isNotBlank()) append("ข้อความก่อนหน้า (ท้ายสุด): ").append(before.takeLast(600)).append('\n')
        append("รายการงานตอนนี้:\n").append(if (tasks.isEmpty()) "(ยังไม่มี)" else tasks.joinToString("\n") { "- $it" }).append('\n')
        if (removed.isNotEmpty()) append("งานที่เจ้าของงานลบทิ้ง ห้ามใส่กลับ:\n").append(removed.joinToString("\n") { "- $it" }).append('\n')
    }

    private fun schema(withText: Boolean) = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            if (withText) putJsonObject("text") { put("type", "STRING") }
            putJsonObject("tasks") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
        }
        putJsonArray("required") { if (withText) add(JsonPrimitive("text")); add(JsonPrimitive("tasks")) }
    }

    private fun body(parts: kotlinx.serialization.json.JsonArray, withText: Boolean) = buildJsonObject {
        putJsonArray("contents") { add(buildJsonObject { put("role", "user"); put("parts", parts) }) }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("responseSchema", schema(withText))
            put("temperature", 0.2)
            put("maxOutputTokens", 4096)
            putJsonObject("thinkingConfig") { put("thinkingLevel", "minimal") }
        }
    }.toString()

    /** One piece of a live recording, with the words before it, the list so far and what the owner took out. */
    fun pieceRequest(audioBase64: String, mime: String, before: String, tasks: List<String>, removed: List<String>): String =
        body(
            kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject { put("text", LIVE_RULES + "\n\n" + context(before, tasks, removed)) })
                add(buildJsonObject { putJsonObject("inlineData") { put("mimeType", mime); put("data", audioBase64) } })
            },
            withText = true,
        )

    /** The words of a whole recording read once more, to tidy the list when the owner stops. */
    fun tidyRequest(transcript: String, tasks: List<String>, removed: List<String>): String =
        body(
            kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put(
                        "text",
                        "นี่คือข้อความทั้งหมดที่เจ้าของงานพูด อ่านอีกรอบแล้วตอบ tasks เป็นรายการงานทั้งหมด " +
                            "คงงานในรายการตอนนี้ เก็บตกงานที่ยังขาด รวมงานที่ซ้ำกัน ใช้คำของเจ้าของงาน ไม่ใส่วันที่ แท็ก หรืออีโมจิ\n\n" +
                            "ข้อความ: " + transcript + "\n\n" + context("", tasks, removed),
                    )
                })
            },
            withText = false,
        )

    /** The words heard in a piece and the task list after it. */
    fun parsePiece(answer: String): Pair<String, List<String>> {
        val body = answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw AiAssist.Failure(tr("คำตอบของ Gemini ไม่ใช่รูปแบบที่ตกลงไว้", "Gemini answered in an unexpected form"))
        val text = (root["text"] as? JsonPrimitive)?.contentOrNull?.let { AiAssist.clean(it) }?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return text to parse(body)
    }

    /**
     * A task in the live list. [heard] is how Gemini last put it, to find it again; [locked] once the owner edited
     * it, so later pieces leave its words alone; [fresh] while it is new.
     */
    data class Live(val id: Int, val text: String, val heard: String = text, val locked: Boolean = false, val fresh: Boolean = false)

    /**
     * The list after a piece: Gemini's [heard] list laid over [current]. An item the owner edited stays as they wrote
     * it; an item Gemini still lists takes its newest words; one it no longer lists goes; a task like one in
     * [removed] never comes back; the rest are new, numbered from [nextId].
     */
    fun merge(current: List<Live>, heard: List<String>, removed: List<String>, nextId: Int): List<Live> {
        val left = heard.map(::clean).filter { h -> h.isNotEmpty() && removed.none { similar(h, it) } }.toMutableList()
        val out = ArrayList<Live>()
        for (c in current) {
            if (c.locked) {
                out += c.copy(fresh = false)
                left.removeAll { similar(it, c.heard) || similar(it, c.text) }
                continue
            }
            val match = left.firstOrNull { similar(it, c.heard) || similar(it, c.text) } ?: continue
            out += c.copy(text = match, heard = match, fresh = false)
            left.remove(match)
        }
        var id = nextId
        left.distinct().forEach { out += Live(id++, it, fresh = true) }
        return out
    }

    /** The tasks in the model's [answer] (the JSON it wrote), cleaned; empty when it heard none. */
    fun parse(answer: String): List<String> {
        val body = answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { json.parseToJsonElement(body) }.getOrElse {
            throw AiAssist.Failure(tr("คำตอบของ Gemini ไม่ใช่รูปแบบที่ตกลงไว้", "Gemini answered in an unexpected form"))
        }
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root["tasks"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return items.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.map(::clean).filter { it.isNotEmpty() }.distinct()
    }

    /** Tokens the task line reads as dates, priority or ids. Alternatives, not a [class], so JS keeps emoji whole. */
    private val TOKENS = Regex(listOf("📅", "⏳", "🛫", "➕", "✅", "❌", "🔁", "⏫", "🔼", "🔽", "🔺", "⏬", "🎯", "⏰", "🆔", "⛔").joinToString("|"))

    /**
     * One task's words, safe for a task line: one line, no list marker, no tag (`#` would make one) and none of the
     * symbols the line reads as dates or priority, so "tomorrow" stays words and nothing is set by accident.
     */
    fun clean(text: String): String = AiAssist.clean(text)
        .replace(TOKENS, "")
        .replace("#", "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .removePrefix("- [ ]").removePrefix("-").removePrefix("*").removePrefix("•").trim()
        .take(300)
        .trim()

    /** The line added to the task note: open, with only the day it was made. */
    fun line(title: String, today: LocalDate): String = "- [ ] ${clean(title)} ➕ $today"

    /** Letters and digits only, lower case, for comparing two titles. */
    private fun key(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun pairs(s: String): List<String> = if (s.length < 2) listOf(s) else s.windowed(2)

    /**
     * Whether two titles look like the same task: one holds the other, or most of their letter pairs match. Thai has
     * no spaces between words, so the comparison works on letters, not words.
     */
    fun similar(a: String, b: String): Boolean {
        val x = key(a)
        val y = key(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val short = if (x.length <= y.length) x else y
        val long = if (short === x) y else x
        if (short.length >= 4 && short in long) return true
        val px = pairs(x).groupingBy { it }.eachCount().toMutableMap()
        var common = 0
        pairs(y).forEach { p -> val n = px[p] ?: 0; if (n > 0) { common++; px[p] = n - 1 } }
        return 2.0 * common / (pairs(x).size + pairs(y).size) >= 0.75
    }

    /** The open task [title] may repeat, if any. */
    fun duplicateOf(title: String, tasks: List<Task>): Task? =
        tasks.firstOrNull { it.isOpen && it.parent == null && similar(title, it.title) }
}
