package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The assistant's brain on Gemini, kept free of the network so the phone and the Obsidian tab share it.
 *
 * The model is handed a plain picture of the open work in which every task has a short handle ("t12"), so it can
 * point at tasks without copying their text. It answers in JSON: a brief (a line or two, and the few tasks worth a
 * look, each with a reason) or a reply to a question (text, and changes it proposes). Nothing here changes a task:
 * every proposal is shown to the owner, who confirms it.
 */
object AiAssist {

    /** The model asked for by default: Google's alias for its newest Flash model. */
    const val DEFAULT_MODEL = "gemini-flash-latest"

    /** At most this many tasks go to the model, so the request stays small and cheap. */
    const val MAX_TASKS = 150

    /** The open work as text, and which task each handle stands for. */
    class Picture(val text: String, val refs: Map<String, Task>)

    /** A task the assistant thinks the owner should look at, and why. */
    data class Pick(val task: Task, val reason: String)

    /** The brief at the top of the assistant page. */
    data class Brief(val summary: String, val picks: List<Pick>)

    /** A change the assistant proposes. The owner confirms each one; a [Big] one only goes to Claude for review. */
    sealed interface Action {
        val task: Task?

        /** Write why the task is still open, as a dated `ค้าง` line under it. */
        data class Stuck(override val task: Task, val reason: String) : Action

        /** Move the task's date. */
        data class Move(override val task: Task, val date: LocalDate) : Action

        /** Add these subtasks under the task. */
        data class Subtasks(override val task: Task, val titles: List<String>) : Action

        /** Too big to do with one tap (several tasks, parking, dropping): written up for Claude to review. */
        data class Big(val text: String) : Action {
            override val task: Task? get() = null
        }
    }

    data class Reply(val text: String, val actions: List<Action>)

    /** One turn of the conversation so far, for the model's memory: the owner's question, or the assistant's answer. */
    data class Turn(val fromOwner: Boolean, val text: String)

    private fun isClaude(t: Task) = t.tags.any { it.equals("claudedo", ignoreCase = true) }
    private fun isSomeday(t: Task) = t.tags.any { it == "สักวัน" || it.equals("someday", ignoreCase = true) }

    /**
     * The picture of the open work: [today], what the owner said about themself ([profile] lines), calendar
     * [events] for the next week, and each open top-level task (most pressing first) with its project, dates, whose
     * turn it is, how long it has sat, why the owner says it is stuck, what it waits for, and its subtasks.
     */
    fun picture(
        tasks: List<Task>,
        subtasksOf: (Task) -> List<Task>,
        today: LocalDate,
        events: List<CalendarEvent> = emptyList(),
        profile: List<String> = emptyList(),
    ): Picture {
        val open = tasks.filter { it.isOpen && !it.isSubtask }
        val ordered = open.sortedWith(
            compareBy<Task>(
                { isSomeday(it) },
                { (it.due ?: it.scheduled ?: it.start)?.let { d -> ChronoUnit.DAYS.between(today, d) } ?: Long.MAX_VALUE },
                { it.lineIndex },
            ),
        ).take(MAX_TASKS)
        val refs = LinkedHashMap<String, Task>()
        ordered.forEachIndexed { i, t -> refs["t${i + 1}"] = t }
        val refOf = refs.entries.associate { (k, v) -> v.key to k }
        val byId = tasks.filter { it.id != null }.associateBy { it.id }
        val text = buildString {
            appendLine("วันนี้ $today (${today.dayOfWeek.name.lowercase()})")
            if (profile.isNotEmpty()) {
                appendLine("[เกี่ยวกับเจ้าของงาน]")
                profile.forEach { appendLine("- $it") }
            }
            val week = events.filter { e -> e.begin.date >= today && e.begin.date <= today.plusDays(7) }.sortedBy { it.begin }
            if (week.isNotEmpty()) {
                appendLine("[นัดหมาย 7 วันข้างหน้า]")
                week.take(40).forEach { e ->
                    val time = if (e.allDay) "ทั้งวัน" else "${hm(e.begin.time.hour, e.begin.time.minute)} ถึง ${hm(e.end.time.hour, e.end.time.minute)}"
                    appendLine("- ${e.begin.date} $time ${e.title}")
                }
            }
            appendLine("[งานที่ยังไม่เสร็จ] ${open.size} งาน" + if (open.size > ordered.size) " (แสดง ${ordered.size} งานแรก)" else "")
            refs.forEach { (ref, t) -> appendLine(line(ref, t, subtasksOf(t), today, byId, refOf)) }
        }
        return Picture(text.trimEnd(), refs)
    }

    private fun hm(h: Int, m: Int) = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"

    private fun line(ref: String, t: Task, subs: List<Task>, today: LocalDate, byId: Map<String?, Task>, refOf: Map<String, String>): String {
        val parts = mutableListOf("$ref: ${t.title}")
        Projects.projectOf(t)?.let { parts += "โปรเจกต์ $it" }
        parts += if (isClaude(t)) "ตาของ Claude" else "ตาของเจ้าของ"
        if (isSomeday(t)) parts += "พักไว้ (สักวัน)"
        if (t.status == Status.IN_PROGRESS) parts += "กำลังทำ"
        if (t.priority != Priority.NONE) parts += "ความสำคัญ ${t.priority.name.lowercase()}"
        t.start?.let { parts += "เริ่ม $it" }
        t.scheduled?.let { parts += "นัดทำ $it" }
        t.due?.let { d ->
            val late = ChronoUnit.DAYS.between(d, today)
            parts += "กำหนด $d" + if (late > 0) " (เลยมา $late วัน)" else ""
        }
        t.recurrence?.let { parts += "ทำซ้ำ $it" }
        StuckLog.staleDays(t, subs, today)?.let { parts += "ไม่ขยับมา $it วัน" }
        val waits = t.dependsOn.mapNotNull { byId[it] }.filter { it.isOpen }
        if (waits.isNotEmpty()) parts += "รอ " + waits.joinToString(", ") { w -> refOf[w.key] ?: w.title }
        if (subs.isNotEmpty()) parts += "งานย่อยเสร็จ ${subs.count { !it.isOpen }}/${subs.size}"
        val notes = StuckLog.entries(t).takeLast(3)
        if (notes.isNotEmpty()) parts += "บันทึกค้าง: " + notes.joinToString("; ") { "${it.day} ${it.reason}" }
        t.descriptionPreview?.let { parts += "รายละเอียด: ${it.take(120)}" }
        return parts.joinToString(" | ")
    }

    private const val RULES = """คุณเป็นผู้ช่วยจัดการงานในแอป Omni Task ของเจ้าของงาน เป้าหมายคือช่วยให้งานขยับ ลดความกังวล และชี้คอขวด
- ตอบเป็นภาษาไทย สั้น ตรง เป็นกันเอง เรียกเจ้าของงานว่า "คุณ"
- ใช้เฉพาะข้อมูลในภาพรวมงานที่ให้ ห้ามแต่งงาน วันที่ หรือเหตุผลขึ้นเอง ถ้าไม่รู้ให้ถาม
- อ้างถึงงานด้วยรหัส เช่น t3 ในช่อง ref เท่านั้น ในข้อความให้ใช้ชื่องาน ห้ามพิมพ์รหัส t3 ให้เจ้าของเห็น
- ห้ามใช้เครื่องหมาย em dash และจุดกลาง (·) ใช้การขึ้นบรรทัดใหม่หรือจุลภาคแทน
- งานที่ "ตาของ Claude" คือ Claude (AI อีกตัว) ต้องทำ งานอื่นเป็นตาของเจ้าของ
- งานที่ "พักไว้ (สักวัน)" ไม่นับเป็นงานค้าง
- แยกให้ชัดว่าอะไรคือข้อเท็จจริงจากข้อมูล อะไรคือคำแนะนำของคุณ"""

    private const val BRIEF_ASK = """สรุปภาพรวมงานตอนนี้ให้เจ้าของงานเห็นใน 3 วินาที
- summary: 1 ถึง 2 ประโยค บอกว่างานติดที่ไหน (ที่เจ้าของ ที่ Claude หรือเลยกำหนด) และควรเริ่มตรงไหน
- picks: งานที่ควรดูก่อน 1 ถึง 4 งาน เรียงจากสำคัญสุด แต่ละงานมี reason สั้นไม่เกิน 1 ประโยค ว่าทำไมควรดูและควรทำอะไรต่อ"""

    private const val CHAT_ASK = """ตอบคำถามล่าสุดของเจ้าของงานในช่อง answer
ถ้าการแก้งานจะช่วยได้ เสนอใน actions ได้ไม่เกิน 3 ข้อ เจ้าของจะกดยืนยันเองทีละข้อ
- kind "stuck": จดเหตุผลที่งาน ref ยังค้าง ใส่เหตุผลใน text
- kind "move": เลื่อนวันของงาน ref ไปวันที่ date (รูปแบบ YYYY-MM-DD) ได้ทีละ 1 งาน
- kind "subtasks": แบ่งงาน ref เป็นงานย่อยตาม items (ไม่เกิน 5 ข้อ สั้น ทำได้จริง)
- kind "big": เรื่องใหญ่ เช่น เลื่อนหลายงาน พักงาน เลิกทำ ให้เขียนข้อเสนอใน text เพื่อส่งให้ Claude ตรวจก่อน
ถ้าไม่ต้องแก้อะไร ให้ actions เป็นรายการว่าง"""

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun obj(type: String, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject { put("type", type); block() }
    private fun str() = obj("STRING")

    private val briefSchema = obj("OBJECT") {
        putJsonObject("properties") {
            put("summary", str())
            put("picks", obj("ARRAY") {
                put("items", obj("OBJECT") {
                    putJsonObject("properties") { put("ref", str()); put("reason", str()) }
                    putJsonArray("required") { add(JsonPrimitive("ref")); add(JsonPrimitive("reason")) }
                })
            })
        }
        putJsonArray("required") { add(JsonPrimitive("summary")); add(JsonPrimitive("picks")) }
    }

    private val replySchema = obj("OBJECT") {
        putJsonObject("properties") {
            put("answer", str())
            put("actions", obj("ARRAY") {
                put("items", obj("OBJECT") {
                    putJsonObject("properties") {
                        put("kind", obj("STRING") { putJsonArray("enum") { listOf("stuck", "move", "subtasks", "big").forEach { add(JsonPrimitive(it)) } } })
                        put("ref", str()); put("text", str()); put("date", str())
                        put("items", obj("ARRAY") { put("items", str()) })
                    }
                    putJsonArray("required") { add(JsonPrimitive("kind")) }
                })
            })
        }
        putJsonArray("required") { add(JsonPrimitive("answer")); add(JsonPrimitive("actions")) }
    }

    private fun request(picture: Picture, turns: List<Turn>, ask: String, schema: JsonObject): String = buildJsonObject {
        putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", RULES + "\n\n[ภาพรวมงาน]\n" + picture.text) }) } }
        putJsonArray("contents") {
            turns.forEach { t ->
                add(buildJsonObject {
                    put("role", if (t.fromOwner) "user" else "model")
                    putJsonArray("parts") { add(buildJsonObject { put("text", t.text) }) }
                })
            }
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") { add(buildJsonObject { put("text", ask) }) }
            })
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("responseSchema", schema)
            put("temperature", 0.4)
        }
    }.toString()

    /** The body of a generateContent call that asks for the brief. */
    fun briefRequest(picture: Picture): String = request(picture, emptyList(), BRIEF_ASK, briefSchema)

    /** The body of a generateContent call that answers [question], after the [earlier] turns (only the last few are kept). */
    fun chatRequest(picture: Picture, earlier: List<Turn>, question: String): String =
        request(picture, earlier.takeLast(8), CHAT_ASK + "\n\nคำถาม: " + question, replySchema)

    /** What went wrong, from Gemini's own words where it gave them. */
    class Failure(message: String) : Exception(message)

    /** The JSON the model wrote, out of a generateContent response, or a [Failure]. */
    fun answerText(response: String): String {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrElse { throw Failure(tr("ตอบกลับมาอ่านไม่ออก", "Could not read the reply")) }
        (root["error"] as? JsonObject)?.let { e -> throw Failure(e["message"]?.jsonPrimitive?.contentOrNull ?: tr("Gemini ตอบว่าผิดพลาด", "Gemini returned an error")) }
        val cand = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        if (cand == null) {
            val why = (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
            throw Failure(if (why != null) tr("Gemini ไม่ตอบ ($why)", "Gemini declined ($why)") else tr("Gemini ไม่ได้ตอบอะไรกลับมา", "Gemini sent nothing back"))
        }
        val parts = (cand["content"] as? JsonObject)?.get("parts") as? JsonArray
        val text = parts?.mapNotNull { p -> (p as? JsonObject)?.takeIf { it["thought"]?.jsonPrimitive?.contentOrNull != "true" }?.get("text")?.jsonPrimitive?.contentOrNull }?.joinToString("")
        if (text.isNullOrBlank()) throw Failure(tr("Gemini ไม่ได้ตอบอะไรกลับมา", "Gemini sent nothing back") + " (${cand["finishReason"]?.jsonPrimitive?.contentOrNull ?: "?"})")
        return text
    }

    private fun parseObject(text: String): JsonObject {
        val body = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw Failure(tr("คำตอบของ Gemini ไม่ใช่รูปแบบที่ตกลงไว้", "Gemini answered in an unexpected form")) }
    }

    private fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** Takes out the em dash and middle dot the owner does not want to read, in case the model used them anyway. */
    fun clean(text: String): String = text.replace(" — ", ", ").replace("—", ", ").replace(" · ", ", ").replace("·", " ").trim()

    /** The brief, from a generateContent [response] to [briefRequest]. Picks that name no task are dropped. */
    fun parseBrief(response: String, picture: Picture): Brief {
        val o = parseObject(answerText(response))
        val picks = (o["picks"] as? JsonArray).orEmpty().mapNotNull { e ->
            val p = e as? JsonObject ?: return@mapNotNull null
            val task = picture.refs[p["ref"].s()] ?: return@mapNotNull null
            Pick(task, clean(p["reason"].s() ?: return@mapNotNull null))
        }.distinctBy { it.task.key }.take(4)
        return Brief(clean(o["summary"].s() ?: ""), picks)
    }

    /**
     * The reply, from a generateContent [response] to [chatRequest]. Proposals that name no task or carry no date are
     * dropped. Moving more than one task is a big change by the agreement with the owner, so several moves become a
     * single proposal for Claude to review.
     */
    fun parseReply(response: String, picture: Picture): Reply {
        val o = parseObject(answerText(response))
        val actions = (o["actions"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = e as? JsonObject ?: return@mapNotNull null
            val task = picture.refs[a["ref"].s()]
            when (a["kind"].s()) {
                "stuck" -> task?.let { t -> a["text"].s()?.let { Action.Stuck(t, clean(it)) } }
                "move" -> task?.let { t -> a["date"].s()?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }?.let { Action.Move(t, it) } }
                "subtasks" -> task?.let { t ->
                    val items = (a["items"] as? JsonArray).orEmpty().mapNotNull { it.s()?.let(::clean) }.take(5)
                    if (items.isEmpty()) null else Action.Subtasks(t, items)
                }
                "big" -> a["text"].s()?.let { Action.Big(clean(it)) }
                else -> null
            }
        }
        val moves = actions.filterIsInstance<Action.Move>()
        val kept = if (moves.size > 1) {
            actions.filter { it !is Action.Move } + Action.Big(moves.joinToString("\n") { "เลื่อน \"${it.task.title}\" ไป ${it.date}" })
        } else actions
        return Reply(clean(o["answer"].s() ?: ""), kept.take(3))
    }

    /**
     * The newest Flash model in a models.list [response], for when [DEFAULT_MODEL] is not offered: a stable
     * "gemini-X.Y-flash" (no lite, preview or dated variant), highest version first. Null when none is listed.
     */
    fun newestFlash(response: String): String? {
        val models = runCatching { json.parseToJsonElement(response).jsonObject["models"]?.jsonArray }.getOrNull() ?: return null
        val pattern = Regex("""^models/gemini-(\d+)(?:\.(\d+))?-flash$""")
        return models.mapNotNull { m ->
            val o = m as? JsonObject ?: return@mapNotNull null
            val name = o["name"].s() ?: return@mapNotNull null
            val methods = (o["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { it.s() }.orEmpty()
            if ("generateContent" !in methods) return@mapNotNull null
            val g = pattern.find(name)?.groupValues ?: return@mapNotNull null
            Triple(name.removePrefix("models/"), g[1].toInt(), g[2].toIntOrNull() ?: 0)
        }.maxWithOrNull(compareBy({ it.second }, { it.third }))?.first
    }

    /** The text the owner sends Claude for a [Action.Big] proposal. */
    fun forClaude(big: Action.Big, today: LocalDate): String =
        "#claudecheck ข้อเสนอจากผู้ช่วย Gemini ใน Omni ($today)\n${big.text}\n\nช่วยตรวจว่าเห็นด้วยหรือควรแก้ตรงไหน ก่อนที่ฉันจะอนุมัติ"
}
