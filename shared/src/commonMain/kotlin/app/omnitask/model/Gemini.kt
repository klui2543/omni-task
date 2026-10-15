package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import app.omnitask.time.*

/**
 * The assistant on Gemini (Google's API, the owner's own key on the free tier): the question goes out with the same
 * picture of the day "ask Claude" hands over, plus the open tasks numbered, and the answer comes back as a reply and
 * a list of changes to those tasks. Nothing is written until the owner confirms the changes picked.
 *
 * Only the request and the reading of the answer live here; each app sends it its own way (OkHttp, fetch).
 */
object Gemini {

    /** Google's name for its newest Flash model, so the app keeps up without an update; the owner can set another. */
    const val DEFAULT_MODEL = "gemini-flash-latest"

    /** Open tasks offered for changes, by number; a longer list costs time on every question. */
    const val MAX_TASKS = 200

    /** Earlier questions and answers sent along, so "and the second one?" makes sense. */
    const val MAX_TURNS = 6

    fun endpoint(model: String): String =
        "https://generativelanguage.googleapis.com/v1beta/models/${model.trim().ifEmpty { DEFAULT_MODEL }}:generateContent"

    /** The header the key goes in (never the address, so it stays out of logs). */
    const val KEY_HEADER = "x-goog-api-key"

    @Serializable
    data class Turn(val question: String, val reply: String)

    /**
     * One change Gemini suggests. [op] is one of [OPS]; [index] is the task's place in [editable] (-1 for "add");
     * [date] is ISO or null to clear, [time] `HH:mm` or null to clear, [priority] a [Priority] name.
     */
    @Serializable
    data class Change(
        val op: String,
        val index: Int = -1,
        val date: String? = null,
        val time: String? = null,
        val priority: String? = null,
        val title: String? = null,
        val why: String = "",
    )

    data class Answer(val reply: String, val changes: List<Change>)

    /** Gemini said no, or could not be reached; [message] is for the owner. */
    class Failure(message: String) : Exception(message)

    const val DONE = "done"
    const val DUE = "due"
    const val SCHEDULED = "scheduled"
    const val START = "start"
    const val REMIND = "remind"
    const val PRIORITY = "priority"
    const val ADD = "add"
    val OPS = listOf(DONE, DUE, SCHEDULED, START, REMIND, PRIORITY, ADD)

    /** The tasks Gemini may change, numbered from 1 in this order in the question. */
    fun editable(tasks: List<Task>): List<Task> = tasks.filter { it.isOpen && !it.todayCopy && it.list == null }.take(MAX_TASKS)

    private fun system(now: LocalDateTime): String = """
        คุณคือผู้ช่วยจัดการงานของเจ้าของแอป Omni Task งานอยู่ในโน้ต Obsidian รูปแบบ Tasks emoji (📅 ครบกำหนด, ⏳ วันนัดทำ, 🛫 วันเริ่ม, ⏰ หรือ 🎯 เวลาเตือน, 🔺⏫🔼🔽⏬ ความสำคัญ, 🔁 ทำซ้ำ)
        ตอนนี้ ${now.date} (${Countdown.day(now.date)}) เวลา ${Profile.hm(now.time)}

        - ตอบเป็นภาษาไทย สั้น ตรงประเด็น ใช้ข้อมูลที่ให้มาเท่านั้น ถ้าไม่รู้ให้บอกว่าไม่รู้
        - ห้ามใช้ขีดยาว (—) และจุดกลาง (·) ในคำตอบ ใช้จุลภาค ขึ้นบรรทัดใหม่ หรือวงเล็บแทน
        - บรรทัด "ค้าง วันที่ เหตุผล" ใต้งานคือบันทึกของเจ้าของว่าทำไมงานยังไม่เสร็จ ใช้แยกงานที่ยาวโดยธรรมชาติออกจากงานที่ติดจริง
        - ถ้าเจ้าของขอให้แก้งาน หรือการแก้ช่วยตอบคำถามได้ชัดเจน ให้เสนอใน changes อ้างงานด้วยเลขจากรายการ "งานที่แก้ได้" ห้ามเดาเลข
        - changes เป็นแค่ข้อเสนอ เจ้าของกดยืนยันเองก่อนบันทึก ใน reply อย่าบอกว่าแก้ไปแล้ว ให้บอกว่าเสนอให้แก้
        - ถ้าไม่ต้องแก้อะไร ให้ changes เป็นรายการว่าง
        - op ได้แค่: done (ทำเสร็จ), due (วันครบกำหนด), scheduled (วันนัดทำ), start (วันเริ่ม), remind (เวลาเตือน), priority (ความสำคัญ), add (งานใหม่)
        - วันที่เป็น YYYY-MM-DD, ว่างไว้เพื่อลบวัน เวลาเป็น HH:mm, ว่างไว้เพื่อลบการเตือน
        - priority ได้แค่: highest, high, medium, none, low, lowest
        - add ใส่ชื่องานใน title ใส่ date (ครบกำหนด) time และ priority ได้ถ้าเจ้าของบอก
        - why สั้นๆ ว่าทำไมเสนอการแก้นั้น
    """.trimIndent()

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("reply") { put("type", "STRING") }
            putJsonObject("changes") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("op") { put("type", "STRING"); put("description", OPS.joinToString(", ")) }
                        putJsonObject("task") { put("type", "INTEGER"); put("description", "เลขงานจากรายการ งานที่แก้ได้ (0 สำหรับ add)") }
                        putJsonObject("date") { put("type", "STRING"); put("description", "YYYY-MM-DD") }
                        putJsonObject("time") { put("type", "STRING"); put("description", "HH:mm") }
                        putJsonObject("priority") { put("type", "STRING") }
                        putJsonObject("title") { put("type", "STRING") }
                        putJsonObject("why") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add(JsonPrimitive("op")); add(JsonPrimitive("task")); add(JsonPrimitive("why")) }
                }
            }
        }
        putJsonArray("required") { add(JsonPrimitive("reply")); add(JsonPrimitive("changes")) }
    }

    /**
     * The body of a generateContent call: [context] is the picture of the day (what "ask Claude" sends), [tasks]
     * the [editable] list in the order its numbers mean, [history] the earlier questions and replies.
     */
    fun request(context: String, tasks: List<Task>, history: List<Turn>, question: String, now: LocalDateTime): String {
        val list = tasks.mapIndexed { i, t -> "${i + 1}. ${t.raw.trim()}" }.joinToString("\n")
        val asked = buildString {
            appendLine("[ข้อมูลตอนนี้]")
            appendLine(context.trim())
            appendLine()
            appendLine("[งานที่แก้ได้ อ้างด้วยเลข]")
            appendLine(list.ifEmpty { "(ไม่มีงานค้าง)" })
            appendLine()
            appendLine("[คำถาม]")
            append(question.trim())
        }
        return buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system(now)) } } }
            putJsonArray("contents") {
                history.takeLast(MAX_TURNS).forEach { turn ->
                    addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", turn.question) } } }
                    addJsonObject { put("role", "model"); putJsonArray("parts") { addJsonObject { put("text", turn.reply) } } }
                }
                addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", asked) } } }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", SCHEMA)
                put("temperature", 0.4)
            }
        }.toString()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }

    /** Reads a generateContent response; [count] is how many tasks were numbered. Throws [Failure] when there is no answer. */
    fun parse(body: String, count: Int): Answer {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw Failure(tr("อ่านคำตอบของ Gemini ไม่ได้", "Cannot read Gemini's answer"))
        root["error"]?.let { throw Failure(errorMessage(0, body)) }
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        val parts = (candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray
        val text = parts.orEmpty()
            .map { it.jsonObject }
            .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { it["text"].str() }
            .joinToString("")
        if (text.isBlank()) {
            val blocked = (root["promptFeedback"] as? JsonObject)?.get("blockReason").str() ?: candidate?.get("finishReason").str()
            throw Failure(tr("Gemini ไม่ตอบคำถามนี้", "Gemini gave no answer") + (blocked?.let { " ($it)" } ?: ""))
        }
        val obj = runCatching { json.parseToJsonElement(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")).jsonObject }.getOrNull()
            ?: return Answer(clean(text), emptyList())
        val reply = clean(obj["reply"].str().orEmpty())
        val changes = (obj["changes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { c -> change(c, count) } }
        return Answer(reply, changes)
    }

    private val HM = Regex("""^(\d{1,2})[.:](\d{2})$""")

    /** A change as Gemini wrote it, checked and put in the app's terms; null when it names no real task or value. */
    private fun change(c: JsonObject, count: Int): Change? {
        val op = c["op"].str()?.lowercase()?.takeIf { it in OPS } ?: return null
        val index = (c["task"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }?.minus(1) ?: -1
        if (op != ADD && index !in 0 until count) return null
        val why = clean(c["why"].str().orEmpty())
        val date = c["date"].str()?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: return null }
        val time = c["time"].str()?.let { t ->
            val m = HM.matchEntire(t) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h > 23 || min > 59) return null
            Profile.hm(LocalTime(h, min))
        }
        val priority = c["priority"].str()?.let { p ->
            when (p.lowercase()) {
                "normal", "none" -> Priority.NONE
                else -> Priority.entries.firstOrNull { it.name.equals(p, ignoreCase = true) } ?: return null
            }.name
        }
        return when (op) {
            DONE -> Change(op, index, why = why)
            DUE, SCHEDULED, START -> Change(op, index, date = date?.toString(), why = why)
            REMIND -> Change(op, index, time = time, why = why)
            PRIORITY -> Change(op, index, priority = priority ?: return null, why = why)
            else -> {
                val title = c["title"].str()?.replace(Regex("""\s+"""), " ") ?: return null
                Change(op, -1, date = date?.toString(), time = time, priority = priority, title = title, why = why)
            }
        }
    }

    /** The owner's writing rule holds for what Gemini writes too: no em dash, no middle dot. */
    fun clean(text: String): String = text
        .replace(Regex("""\s*—\s*"""), ", ")
        .replace(Regex("""\s*·\s*"""), ", ")
        .trim()

    /** What to tell the owner for an HTTP error from Gemini ([status] 0 when it came inside a 200). */
    fun errorMessage(status: Int, body: String): String {
        val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
        val code = error?.get("code")?.jsonPrimitive?.intOrNull ?: status
        val reason = error?.get("status").str().orEmpty()
        val detail = error?.get("message").str().orEmpty()
        return when {
            detail.contains("API key", ignoreCase = true) || reason == "PERMISSION_DENIED" || code == 401 || code == 403 ->
                tr("API key ของ Gemini ใช้ไม่ได้ ตรวจที่ ตั้งค่า > Gemini", "The Gemini API key does not work. Check Settings > Gemini")
            code == 429 || reason == "RESOURCE_EXHAUSTED" ->
                tr("ใช้โควตาฟรีของ Gemini เต็มแล้ว รอสักครู่แล้วลองใหม่", "Gemini's free quota is used up for now. Try again in a while")
            code == 404 || reason == "NOT_FOUND" ->
                tr("ไม่พบโมเดลนี้ ตรวจชื่อโมเดลที่ ตั้งค่า > Gemini", "Model not found. Check the model name in Settings > Gemini")
            code >= 500 -> tr("Gemini ไม่ว่างตอนนี้ ลองใหม่อีกครั้ง", "Gemini is busy. Try again")
            else -> tr("Gemini ตอบไม่ได้", "Gemini could not answer") + (detail.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
        }
    }

    /** The changes picked, by task: done wins over the rest (they no longer matter), and "add" stays apart. */
    fun byTask(changes: List<Change>): Map<Int, List<Change>> = changes.filter { it.op != ADD }.groupBy { it.index }
        .mapValues { (_, list) -> list.filter { it.op == DONE }.take(1).ifEmpty { list } }

    /** The task line after the date, priority and reminder changes in [changes] (done and add are not line edits). */
    fun line(raw: String, changes: List<Change>): String {
        var r = raw
        changes.filter { it.op in listOf(DUE, SCHEDULED, START) }.forEach { c ->
            val field = when (c.op) {
                DUE -> DateField.DUE
                SCHEDULED -> DateField.SCHEDULED
                else -> DateField.START
            }
            r = TaskLine.setDate(r, field, c.date?.let { LocalDate.parse(it) })
        }
        changes.firstOrNull { it.op == PRIORITY }?.priority?.let { r = TaskLine.setPriority(r, Priority.valueOf(it)) }
        changes.lastOrNull { it.op == REMIND }?.let { c ->
            // The reminder goes on the scheduled day when there is one, as the planner sets it, else on the due day.
            val on = if (TaskLine.parse(r)?.scheduled != null) ReminderOn.SCHEDULED else ReminderOn.DUE
            r = TaskLine.setReminder(r, c.time?.let { LocalTime.parse(it) }, on)
        }
        return r
    }

    /** The new task line for an "add" change, in TaskForge token order. */
    fun newLine(change: Change, today: LocalDate): String = QuickAdd.Draft(
        title = change.title.orEmpty(),
        due = change.date?.let { LocalDate.parse(it) },
        time = change.time?.let { LocalTime.parse(it) },
        priority = change.priority?.let { Priority.valueOf(it) } ?: Priority.NONE,
    ).line(today)

    /** The change in a few words, under the task's title on the card. */
    fun describe(change: Change): String {
        fun day(d: String?) = d?.let { Countdown.day(LocalDate.parse(it)) }
        return when (change.op) {
            DONE -> tr("ทำเสร็จแล้ว", "Mark done")
            DUE -> day(change.date)?.let { tr("ครบกำหนด $it", "Due $it") } ?: tr("ลบวันครบกำหนด", "Clear the due date")
            SCHEDULED -> day(change.date)?.let { tr("นัดทำ $it", "Do on $it") } ?: tr("ลบวันนัดทำ", "Clear the scheduled date")
            START -> day(change.date)?.let { tr("เริ่ม $it", "Start $it") } ?: tr("ลบวันเริ่ม", "Clear the start date")
            REMIND -> change.time?.let { tr("เตือน $it", "Remind at $it") } ?: tr("ไม่ต้องเตือน", "No reminder")
            PRIORITY -> tr("ความสำคัญ: ", "Priority: ") + Priority.valueOf(change.priority!!).label
            else -> listOfNotNull(
                tr("งานใหม่", "New task"),
                day(change.date)?.let { tr("ครบกำหนด $it", "due $it") },
                change.time?.let { tr("เตือน $it", "at $it") },
                change.priority?.let { Priority.valueOf(it) }?.takeIf { it != Priority.NONE }?.label,
            ).joinToString(", ")
        }
    }
}
