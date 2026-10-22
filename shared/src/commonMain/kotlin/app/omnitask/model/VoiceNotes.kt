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
