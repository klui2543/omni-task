package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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

    /** The model asked for by default: Google's alias for its newest Flash-Lite, the quickest to answer. */
    const val DEFAULT_MODEL = "gemini-flash-lite-latest"

    /** At most this many tasks go to the model, so the request stays small and cheap. */
    const val MAX_TASKS = 80

    /** The open work as text, and which task each handle stands for. */
    class Picture(val text: String, val refs: Map<String, Task>)

    /** A task the assistant thinks the owner should look at, and why. */
    data class Pick(val task: Task, val reason: String)

    /** The brief at the top of the assistant page. */
    data class Brief(val summary: String, val picks: List<Pick>)

    /** Which date a [Action.Move] changes. */
    enum class DateKind { DUE, SCHEDULED, START }

    /**
     * A change the assistant proposes, from a question or an order. The owner confirms each one before it is made;
     * a [Big] one only goes to Claude for review.
     */
    sealed interface Action {
        val task: Task?

        /** A new task: its title, and the day it is due (or [scheduled] for), a reminder time, project and priority. */
        data class Add(
            val title: String,
            val date: LocalDate? = null,
            val scheduled: Boolean = false,
            val time: LocalTime? = null,
            val project: String? = null,
            val priority: Priority = Priority.NONE,
        ) : Action {
            override val task: Task? get() = null
        }

        /** Move one of the task's dates ([DateKind.DUE] when the model did not say). Null [date] clears it. */
        data class Move(override val task: Task, val date: LocalDate?, val kind: DateKind = DateKind.DUE) : Action

        data class Rename(override val task: Task, val title: String) : Action

        data class SetPriority(override val task: Task, val priority: Priority) : Action

        /** A reminder at [time] on the task's due day (or scheduled day when it has only that). */
        data class Remind(override val task: Task, val time: LocalTime) : Action

        /** Tick it done. */
        data class Done(override val task: Task) : Action

        data class Delete(override val task: Task) : Action

        /** Move it to another project (its first tag). */
        data class Project(override val task: Task, val tag: String) : Action

        /** Write why the task is still open, as a dated `ค้าง` line under it. */
        data class Stuck(override val task: Task, val reason: String) : Action

        /** Add these subtasks under the task. */
        data class Subtasks(override val task: Task, val titles: List<String>) : Action

        /** Keep [text] in the assistant's lasting memory ([AiMemory.MEMORY_FILE]). */
        data class Remember(val text: String) : Action {
            override val task: Task? get() = null
        }

        /** Too big to do with one tap (several tasks, parking, dropping): written up for Claude to review. */
        data class Big(val text: String) : Action {
            override val task: Task? get() = null
        }
    }

    /**
     * The answer, the changes proposed, a short [topic] and [tags] for the log's index, and [search]: words to look
     * up in older conversations when the model needs them before it can answer.
     */
    data class Reply(
        val text: String,
        val actions: List<Action>,
        val topic: String? = null,
        val tags: List<String> = emptyList(),
        val search: String? = null,
    )

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
        claudeLog: String? = null,
        /** Whose calendar an event is in, where the owner labelled it (such as "แฟน"); null for the owner's own. */
        calendarOf: (CalendarEvent) -> String? = { null },
    ): Picture {
        val open = tasks.filter { it.isOpen && !it.isSubtask }
        // Parked (someday) work is only counted: it is not stuck, and leaving it out keeps the request small and quick.
        val someday = open.count { isSomeday(it) }
        val ordered = open.filterNot { isSomeday(it) }.sortedWith(
            compareBy<Task>(
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
                if (week.any { calendarOf(it) != null }) {
                    appendLine("นัดที่มี (ปฏิทินของ ...) เป็นนัดของคนนั้น ไม่ใช่ของเจ้าของงาน ห้ามบอกว่าเจ้าของมีนัดนั้น ใช้แค่รู้ว่าคนนั้นไม่ว่าง")
                }
                week.take(40).forEach { e ->
                    val time = if (e.allDay) "ทั้งวัน" else "${hm(e.begin.time.hour, e.begin.time.minute)} ถึง ${hm(e.end.time.hour, e.end.time.minute)}"
                    appendLine("- ${e.begin.date} $time ${e.title}" + (calendarOf(e)?.let { " (ปฏิทินของ $it)" } ?: ""))
                }
            }
            projectList(tasks).takeIf { it.isNotBlank() }?.let {
                appendLine("[โปรเจกต์และกิ่งที่มี ใช้แท็กตามนี้เท่านั้น]")
                appendLine(it)
            }
            claudeLog?.let(::claudeDigest)?.takeIf { it.isNotBlank() }?.let { log ->
                appendLine("[ความคืบหน้าที่ Claude จดไว้ (Claude log)]")
                appendLine(log)
            }
            appendLine("[งานที่ยังไม่เสร็จ] ${open.size - someday} งาน" + if (open.size - someday > ordered.size) " (แสดง ${ordered.size} งานแรก)" else "")
            if (someday > 0) appendLine("(งานพักไว้ #สักวัน อีก $someday งาน ไม่ได้แสดง)")
            refs.forEach { (ref, t) -> appendLine(line(ref, t, subtasksOf(t), today, byId, refOf)) }
        }
        return Picture(text.trimEnd(), refs)
    }

    /**
     * Every project the owner keeps (the ones made on the Projects page, and any other first tag that names one), with
     * its branches and how many open tasks each holds: the only tags new work may be filed under, so it shows in
     * Ygdrasil. Branch names come from every task that is not cancelled, so an empty branch is still offered.
     */
    fun projectList(tasks: List<Task>): String {
        val live = tasks.filter { it.status != Status.CANCELLED }
        val open = live.filter { it.isOpen }.mapNotNull { Projects.tagOf(it) }.groupingBy { it }.eachCount()
        val names = live.mapNotNull { Projects.tagOf(it) }.distinct()
        val roots = (Projects.chosen.orEmpty() + names.map { it.substringBefore('/') }).filter { it.isNotBlank() }.distinct().sorted()
        return roots.joinToString("\n") { r ->
            val total = open.filterKeys { it == r || it.startsWith("$r/") }.values.sum()
            val branches = names.filter { it.startsWith("$r/") }.sorted().joinToString(", ") { b -> "$b (${open[b] ?: 0})" }
            "- $r ($total งาน)" + if (branches.isNotEmpty()) " กิ่ง: $branches" else ""
        }
    }

    /**
     * What goes to the model from Claude log.md: the overview and the open work in full, and the newest two
     * sessions of the timeline, cut to a few thousand characters.
     */
    fun claudeDigest(text: String, max: Int = 3000): String {
        val out = StringBuilder()
        var inTimeline = false
        var sessions = 0
        for (line in text.lines()) {
            val t = line.trimEnd()
            if (t.startsWith("# ")) inTimeline = t.contains("ไทม์ไลน์") || t.contains("Timeline", ignoreCase = true)
            if (inTimeline && t.startsWith("## ")) sessions++
            if (inTimeline && sessions > 2) continue
            if (t.isBlank() || t.trim() == "- 💬 Klui:" || t.trim() == "💬 Klui:") continue
            out.appendLine(t)
        }
        return out.toString().trim().let { if (it.length > max) it.take(max) + "\n(ตัดส่วนที่เหลือ)" else it }
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
        t.descriptionPreview?.let { parts += "รายละเอียด: ${it.take(60)}" }
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
- summary: ประโยคเดียวสั้น ๆ บอกสภาพวันนี้ เช่น ว่างถึงกี่โมง หรือพรุ่งนี้เข้าเวรอะไร ห้ามพูดกว้าง ๆ ว่า "ควรเริ่มจากงานที่เลยกำหนด" เพราะชื่องานที่ควรทำอยู่ใน picks แล้ว
- picks: งานที่ควรทำก่อน 1 ถึง 4 งาน เรียงตามลำดับที่ควรทำ งานแรกคือ "ทำก่อน" งานที่สองคือ "แล้วต่อ" แต่ละงานมี reason สั้นไม่เกิน 10 คำ ว่าทำไมงานนี้ (เช่น มีคนรอ 2 วัน, กำหนดจริงพรุ่งนี้)
- งานที่เลยแค่วัน "นัดทำ" (เป้าที่เจ้าของตั้งเอง) ไม่ด่วนเท่างานที่เลย "กำหนด" (กำหนดจริง)"""

    private const val CHAT_ASK = """ตอบหรือทำตามข้อความล่าสุดของเจ้าของงาน ตอบสั้นในช่อง answer
เจ้าของสั่งแก้งานได้ทุกอย่าง ใส่สิ่งที่จะแก้ใน actions (ไม่เกิน 8 ข้อ) แอปจะแสดงให้เจ้าของกดยืนยันทีละข้อก่อนแก้จริง ใน answer ให้บอกสั้น ๆ ว่าเตรียมอะไรไว้ให้ยืนยัน ห้ามบอกว่าทำเสร็จแล้ว
- "add": เพิ่มงานใหม่ ชื่อใน text, date (YYYY-MM-DD) ถ้ามี, field "scheduled" ถ้าเป็นวันนัดทำ ไม่ใช่วันกำหนดส่ง, time (HH:mm) ถ้าต้องการเตือน, priority ถ้าระบุ, project ต้องมีทุกครั้ง (ดูกติกาโปรเจกต์ด้านล่าง)
- "move": เปลี่ยนวันของงาน ref เป็น date, field "due" (กำหนดส่ง ค่าเริ่มต้น) หรือ "scheduled" (วันนัดทำ) หรือ "start" (วันเริ่ม), date ว่างคือลบวันนั้น
- "rename": เปลี่ยนชื่องาน ref เป็น text
- "priority": ตั้งความสำคัญงาน ref เป็น priority (highest, high, medium, none, low, lowest)
- "remind": ตั้งเวลาเตือนงาน ref เป็น time (HH:mm)
- "done": ติ๊กงาน ref ว่าเสร็จ
- "delete": ลบงาน ref ใช้เมื่อเจ้าของสั่งลบชัดเจนเท่านั้น
- "project": ย้ายงาน ref ไปโปรเจกต์หรือกิ่ง project (ชื่อแท็กไม่มี # ตามกติกาโปรเจกต์ด้านล่าง)
- "stuck": จดเหตุผลที่งาน ref ยังค้าง ใส่ใน text
- "subtasks": เพิ่มงานย่อยใต้งาน ref ตาม items (สั้น ทำได้จริง)
- "remember": สิ่งที่ควรจำไว้เสมอเกี่ยวกับเจ้าของ เช่น ความชอบ นิสัย ข้อตกลง ใส่ใน text เสนอเมื่อเจ้าของบอกเรื่องแบบนี้หรือสั่งให้จำ ห้ามเสนอซ้ำกับความจำที่มีอยู่แล้ว
- "big": เรื่องที่ควรให้ Claude ช่วยคิดก่อน เช่น จัดแผนใหม่ทั้งโปรเจกต์ เขียนข้อเสนอใน text
กติกาโปรเจกต์: project ต้องเป็นชื่อจากรายการ [โปรเจกต์และกิ่งที่มี] เท่านั้น สะกดตรงตัว เลือกกิ่งที่ตรงที่สุด เช่น "peddose/แอป" ถ้ามีกิ่งนั้น ไม่งั้นใช้ชื่อโปรเจกต์ ห้ามแต่งชื่อใหม่ ห้ามแปลหรือย่อ ถ้าไม่แน่ใจว่าเข้าโปรเจกต์ไหน ให้ถามใน answer พร้อมเสนอ 2 ถึง 3 ชื่อจากรายการ และยังไม่ต้องใส่ action นั้น ใช้ชื่อที่ไม่อยู่ในรายการได้เฉพาะเมื่อเจ้าของพิมพ์ชื่อโปรเจกต์ใหม่นั้นเอง
วันที่แบบ "พรุ่งนี้" หรือ "ศุกร์นี้" ให้คิดจากวันนี้ในภาพรวมงาน ถ้าคำสั่งกำกวม เช่น มีหลายงานชื่อคล้ายกัน ให้ถามกลับใน answer และไม่ต้องใส่ actions
ถ้าเป็นคำถามที่ไม่ต้องแก้อะไร ให้ actions เป็นรายการว่าง
topic: หัวข้อสั้นของการคุยครั้งนี้ ไม่เกิน 8 คำ เช่น "ตกลงทำระบบป้ายก่อน Flow"
tags: 1 ถึง 3 แท็กไม่มี # ใช้ชื่อโปรเจกต์หรือแท็กที่มีอยู่ในภาพรวมงานก่อน แล้วค่อยเป็นเรื่อง เช่น siriraj, ป้าย"""

    private const val SEARCH_ASK = """ถ้าสารบัญบอกว่าเคยคุยเรื่องที่ถาม แต่รายละเอียดไม่อยู่ในบทสนทนาที่ให้มา ให้ใส่คำค้นสั้น ๆ ใน search แล้วเว้น answer และ actions ว่างไว้ แอปจะค้นบันทึกเก่าแล้วส่งผลกลับมาให้ตอบ ถ้ามีข้อมูลพอแล้ว ให้ search ว่าง"""

    private const val SEARCHED = """ด้านล่างคือผลค้นบันทึกเก่าที่คุณขอ ตอบเลยจากข้อมูลที่มี ห้ามขอค้นเพิ่ม ถ้ายังไม่เจอให้บอกตรง ๆ"""

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun obj(type: String, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject { put("type", type); block() }
    private fun str() = obj("STRING")

    private val briefSchema = obj("OBJECT") {
        putJsonObject("properties") {
            put("summary", str())
            put("picks", obj("ARRAY") {
                put("maxItems", 4)
                put("items", obj("OBJECT") {
                    putJsonObject("properties") { put("ref", str()); put("reason", str()) }
                    putJsonArray("required") { add(JsonPrimitive("ref")); add(JsonPrimitive("reason")) }
                })
            })
        }
        putJsonArray("required") { add(JsonPrimitive("summary")); add(JsonPrimitive("picks")) }
    }

    private val KINDS = listOf("add", "move", "rename", "priority", "remind", "done", "delete", "project", "stuck", "subtasks", "remember", "big")

    private val replySchema = obj("OBJECT") {
        putJsonObject("properties") {
            put("answer", str())
            put("topic", str())
            put("tags", obj("ARRAY") { put("maxItems", 5); put("items", str()) })
            put("search", str())
            put("actions", obj("ARRAY") {
                put("maxItems", 8)
                put("items", obj("OBJECT") {
                    putJsonObject("properties") {
                        put("kind", obj("STRING") { putJsonArray("enum") { KINDS.forEach { add(JsonPrimitive(it)) } } })
                        put("ref", str()); put("text", str()); put("date", str()); put("field", str())
                        put("time", str()); put("project", str()); put("priority", str())
                        put("items", obj("ARRAY") { put("maxItems", 12); put("items", str()) })
                    }
                    putJsonArray("required") { add(JsonPrimitive("kind")) }
                })
            })
        }
        putJsonArray("required") { add(JsonPrimitive("answer")); add(JsonPrimitive("actions")) }
    }

    private fun request(picture: Picture, turns: List<Turn>, ask: String, schema: JsonObject, memory: String?, history: String?): String = buildJsonObject {
        // What stays the same between calls goes first, so Google can reuse it (cheaper and quicker).
        val system = buildString {
            append(RULES)
            memory?.takeIf { it.isNotBlank() }?.let { append("\n\n[สิ่งที่จำไว้เกี่ยวกับเจ้าของงาน]\n").append(it) }
            history?.takeIf { it.isNotBlank() }?.let { append("\n\n[บทสนทนาก่อนหน้ากับเจ้าของ (วันใหม่สุดอยู่บน)]\n").append(it) }
            append("\n\n[ภาพรวมงาน]\n").append(picture.text)
        }
        putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } }
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
            // No temperature: Gemini 3 models loop (repeat until cut off, or refuse as recitation) below their default.
            // Thai takes many tokens, and a model that thinks counts its thinking here too: room for a long proposal.
            put("maxOutputTokens", 8192)
            // Thinking made answers slow on the phone; task questions need almost none.
            putJsonObject("thinkingConfig") { put("thinkingLevel", "minimal") }
        }
    }.toString()

    /** The body of a generateContent call that asks for the brief. */
    fun briefRequest(picture: Picture, memory: String? = null): String = request(picture, emptyList(), BRIEF_ASK, briefSchema, memory, null)

    /** The body of a generateContent call that answers [question], after the [earlier] turns (only the last few are kept). */
    fun chatRequest(
        picture: Picture, earlier: List<Turn>, question: String, memory: String? = null, history: String? = null,
        canSearch: Boolean = false, searched: String? = null,
        /** A second try after an answer came back broken: keep it short and do not repeat. */
        short: Boolean = false,
    ): String {
        val ask = buildString {
            append(CHAT_ASK)
            if (short) append("\n\nคำตอบรอบก่อนยาวเกินหรือวนซ้ำจนใช้ไม่ได้ รอบนี้ answer ไม่เกิน 2 ประโยค actions ไม่เกิน 5 ข้อ text ของแต่ละข้อสั้น ห้ามพูดซ้ำ")
            if (searched != null) append("\n\n").append(SEARCHED).append("\n[ผลค้นบันทึกเก่า]\n").append(searched.ifBlank { "(ไม่เจอ)" })
            else if (canSearch) append("\n").append(SEARCH_ASK)
            append("\n\nคำถาม: ").append(question)
        }
        return request(picture, earlier.takeLast(8), ask, replySchema, memory, history)
    }

    /** The same request without the thinking setting, for a model that does not take it. */
    fun withoutThinking(body: String): String {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return body
        val config = o["generationConfig"] as? JsonObject ?: return body
        return JsonObject(o + ("generationConfig" to JsonObject(config - "thinkingConfig"))).toString()
    }

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
        if (cand["finishReason"]?.jsonPrimitive?.contentOrNull == "MAX_TOKENS") {
            throw Failure(tr("คำตอบยาวเกินจนถูกตัดกลางทาง ลองถามทีละไม่กี่งาน", "The answer ran too long and was cut off. Ask about fewer tasks at a time."))
        }
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

    private fun time(v: String?): LocalTime? = v?.let { Regex("""^(\d{1,2}):(\d{2})""").find(it.trim()) }?.let { m ->
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        if (h in 0..23 && min in 0..59) LocalTime(h, min) else null
    }

    private fun date(v: String?): LocalDate? = v?.let { runCatching { LocalDate.parse(it.trim().take(10)) }.getOrNull() }

    private fun priority(v: String?): Priority? = v?.let { p -> Priority.entries.firstOrNull { it.name.equals(p.trim(), ignoreCase = true) } }

    private fun tag(v: String?): String? = v?.trim()?.removePrefix("#")?.replace(Regex("""\s+"""), "-")?.takeIf { it.isNotEmpty() }

    /**
     * The reply, from a generateContent [response] to [chatRequest]. A proposal that names no task, or lacks what it
     * needs (a date, a title), is dropped.
     */
    fun parseReply(response: String, picture: Picture): Reply {
        val o = parseObject(answerText(response))
        val actions = (o["actions"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = e as? JsonObject ?: return@mapNotNull null
            val task = picture.refs[a["ref"].s()]
            val text = a["text"].s()?.let(::clean)
            when (a["kind"].s()) {
                "add" -> text?.let { title ->
                    Action.Add(
                        title, date(a["date"].s()), a["field"].s().equals("scheduled", ignoreCase = true), time(a["time"].s()),
                        tag(a["project"].s()), priority(a["priority"].s()) ?: Priority.NONE,
                    )
                }
                "move" -> task?.let { t ->
                    val kind = when (a["field"].s()?.lowercase()) { "scheduled" -> DateKind.SCHEDULED; "start" -> DateKind.START; else -> DateKind.DUE }
                    val d = date(a["date"].s())
                    // An empty date clears it; a date that does not read is a mistake, not a clear.
                    if (d == null && a["date"].s() != null) null else Action.Move(t, d, kind)
                }
                "rename" -> task?.let { t -> text?.takeIf { it != t.title }?.let { Action.Rename(t, it) } }
                "priority" -> task?.let { t -> priority(a["priority"].s())?.let { Action.SetPriority(t, it) } }
                "remind" -> task?.let { t -> time(a["time"].s())?.let { Action.Remind(t, it) } }
                "done" -> task?.let { Action.Done(it) }
                "delete" -> task?.let { Action.Delete(it) }
                "project" -> task?.let { t -> tag(a["project"].s())?.let { Action.Project(t, it) } }
                "stuck" -> task?.let { t -> text?.let { Action.Stuck(t, it) } }
                "subtasks" -> task?.let { t ->
                    val items = (a["items"] as? JsonArray).orEmpty().mapNotNull { it.s()?.let(::clean) }.take(8)
                    if (items.isEmpty()) null else Action.Subtasks(t, items)
                }
                "remember" -> text?.let { Action.Remember(it) }
                "big" -> text?.let { Action.Big(it) }
                else -> null
            }
        }
        val tags = (o["tags"] as? JsonArray).orEmpty().mapNotNull { it.s()?.removePrefix("#")?.trim()?.replace(Regex("""\s+"""), "-")?.takeIf { t -> t.isNotEmpty() } }.distinct().take(3)
        return Reply(clean(o["answer"].s() ?: ""), actions.take(8), o["topic"].s()?.let(::clean)?.take(80), tags, o["search"].s())
    }

    /** How quick a model is meant to be, to group the list the owner picks from. */
    enum class Tier { FAST, BALANCED, SMART, OTHER }

    /** A model Google offers this key: its id for the call, its name to show, and its tier. */
    data class Model(val id: String, val name: String, val tier: Tier)

    /**
     * Every Gemini model in a models.list [response] that can answer in text (no embedding, image, speech or live
     * models), grouped by tier and newest first within each.
     */
    fun models(response: String): List<Model> {
        val list = runCatching { json.parseToJsonElement(response).jsonObject["models"]?.jsonArray }.getOrNull() ?: return emptyList()
        val skip = listOf("embedding", "tts", "image", "live", "audio", "aqa", "veo", "imagen", "robotics", "computer-use", "learnlm")
        fun version(id: String) = Regex("""gemini-(\d+)(?:\.(\d+))?""").find(id)?.groupValues?.let { (it[1].toIntOrNull() ?: 0) * 100 + (it[2].toIntOrNull() ?: 0) } ?: 0
        return list.mapNotNull { m ->
            val o = m as? JsonObject ?: return@mapNotNull null
            val id = o["name"].s()?.removePrefix("models/") ?: return@mapNotNull null
            val methods = (o["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { it.s() }.orEmpty()
            if (!id.startsWith("gemini-") || "generateContent" !in methods || skip.any { it in id }) return@mapNotNull null
            val tier = when {
                "flash-lite" in id -> Tier.FAST
                "flash" in id -> Tier.BALANCED
                "pro" in id -> Tier.SMART
                else -> Tier.OTHER
            }
            Model(id, o["displayName"].s() ?: id, tier)
        }.distinctBy { it.id }.sortedWith(compareBy<Model>({ it.tier.ordinal }, { it.id.endsWith("-latest").not() }, { -version(it.id) }, { it.id }))
    }

    /**
     * What to use when the default alias is not offered, from a models.list [response]: the newest stable Flash-Lite,
     * else the newest stable Flash. Null when neither is listed.
     */
    fun fallbackModel(response: String): String? {
        val stable = models(response).filter { m -> listOf("preview", "exp", "latest").none { it in m.id } }
        return (stable.firstOrNull { it.tier == Tier.FAST } ?: stable.firstOrNull { it.tier == Tier.BALANCED })?.id
    }

    /** A change in a few words, for the conversation log. */
    fun describe(a: Action): String = when (a) {
        is Action.Add -> "เพิ่มงาน \"${a.title}\"" + (a.date?.let { " วันที่ $it" } ?: "")
        is Action.Move -> "เลื่อน \"${a.task.title}\" ไป ${a.date ?: "ไม่มีวันที่"}"
        is Action.Rename -> "เปลี่ยนชื่อ \"${a.task.title}\" เป็น \"${a.title}\""
        is Action.SetPriority -> "ตั้งความสำคัญ \"${a.task.title}\" เป็น ${a.priority.name.lowercase()}"
        is Action.Remind -> "ตั้งเตือน \"${a.task.title}\" ${a.time}"
        is Action.Done -> "ติ๊กเสร็จ \"${a.task.title}\""
        is Action.Delete -> "ลบ \"${a.task.title}\""
        is Action.Project -> "ย้าย \"${a.task.title}\" ไป #${a.tag}"
        is Action.Stuck -> "จดค้าง \"${a.task.title}\": ${a.reason}"
        is Action.Subtasks -> "เพิ่มงานย่อยใต้ \"${a.task.title}\": ${a.titles.joinToString(", ")}"
        is Action.Remember -> "จำไว้: ${a.text}"
        is Action.Big -> "ส่งให้ Claude ตรวจ: ${a.text}"
    }

    /** The text the owner sends Claude for a [Action.Big] proposal. */
    fun forClaude(big: Action.Big, today: LocalDate): String =
        "#claudecheck ข้อเสนอจากผู้ช่วย Gemini ใน Omni ($today)\n${big.text}\n\nช่วยตรวจว่าเห็นด้วยหรือควรแก้ตรงไหน ก่อนที่ฉันจะอนุมัติ"
}
