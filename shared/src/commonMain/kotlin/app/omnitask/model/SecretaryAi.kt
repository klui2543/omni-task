package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*
import kotlin.math.roundToLong

/**
 * Claude in the secretary chat (owner, 2026-10-26): only when the owner picks a model in the chat box, and every
 * question is confirmed first with what it will cost and what the credit left will be after it. The rules of the app
 * stay the default and cost nothing. The model only reads what the sentence asks for: a command of the app (in the
 * guide's own words, so the owner can keep it as a taught word and never pay for that sentence again), a task to add,
 * time to find (the app's own [Planner] finds it), a short answer, or "take it to Claude". Nothing changes until the
 * owner taps.
 */
object SecretaryAi {

    /** What answers in the chat. [modelId] null: the app's rules, free. Prices are US dollars per million tokens. */
    enum class Mode(val th: String, val en: String, val modelId: String?, val inPerM: Double, val outPerM: Double) {
        RULES("เลขา", "Secretary", null, 0.0, 0.0),
        HAIKU("Haiku 5.5", "Haiku 5.5", "claude-haiku-5-5", 0.10, 0.50),
        SONNET("Sonnet 5.5", "Sonnet 5.5", "claude-sonnet-5-5", 2.00, 10.00),
        ;

        val label get() = tr(th, en)
        val isAi get() = modelId != null

        /** The small line under a typed sentence: what it was sent with. */
        val sentWith: String get() = if (isAi) tr("ส่งด้วย $th", "Sent with $en") else tr("ส่งด้วย เลขา (ฟรี)", "Sent with the secretary (free)")

        val note: String
            get() = when (this) {
                RULES -> tr("กฎในแอป ทำในเครื่อง ไม่ใช้ AI", "The app's rules, on the phone, no AI")
                HAIKU -> tr("AI เร็ว ถูก แปลประโยคและถามตอบทั่วไป", "Fast and cheap: reads sentences, everyday questions")
                SONNET -> tr("AI คิดละเอียด ให้เหตุผลดีกว่า", "Thinks things through, better reasons")
            }

        companion object {
            fun of(name: String?): Mode = entries.firstOrNull { it.name == name } ?: RULES
        }
    }

    /** How hard the model thinks. [api] is the API's effort level; [replyGuess] the reply's tokens to expect, thinking included. */
    enum class Effort(val th: String, val en: String, val api: String, val replyGuess: Int) {
        LOW("ต่ำ", "Low", "low", 400),
        MEDIUM("กลาง", "Medium", "medium", 800),
        HIGH("สูง", "High", "high", 1600),
        ;

        val label get() = tr(th, en)

        companion object {
            fun of(name: String?): Effort = entries.firstOrNull { it.name == name } ?: LOW
        }
    }

    /** Credit for the API and what this app has spent of it, in US dollars; [rate] is baht per dollar. */
    data class Credit(
        val quotaUsd: Double = 100.0,
        val spentUsd: Double = 0.0,
        val creditExpires: LocalDate? = null,
        val rate: Double = 33.0,
    ) {
        val leftUsd get() = quotaUsd - spentUsd
        fun baht(usd: Double) = usd * rate
    }

    /** Dollars for [inTokens] sent and [outTokens] back on [mode]. */
    fun usd(mode: Mode, inTokens: Long, outTokens: Long): Double = inTokens * mode.inPerM / 1e6 + outTokens * mode.outPerM / 1e6

    /** What one question will cost, before it is sent: [inTokens] counted (or guessed), the reply guessed from [effort]. */
    data class Estimate(val inTokens: Long, val outTokens: Long, val inUsd: Double, val outUsd: Double) {
        val usd get() = inUsd + outUsd
    }

    fun estimate(mode: Mode, effort: Effort, inTokens: Long): Estimate {
        val out = effort.replyGuess.toLong()
        return Estimate(inTokens, out, inTokens * mode.inPerM / 1e6, out * mode.outPerM / 1e6)
    }

    /** A rough count of what a question sends, for when the API cannot count it: the instructions plus the sentence. */
    fun guessInTokens(system: String, question: String): Long = ((system.length + question.length) * 0.9).toLong() + 40

    /** "0.012 บาท": small sums keep three places, so a question that costs a fraction of a satang still shows. */
    fun bahtText(baht: Double): String = if (baht < 10) formatFixed(baht, 3) else formatFixed(baht, 2)

    /** "99.90 ดอลลาร์ (ราว 3,297 บาท)". */
    fun leftText(credit: Credit, usd: Double = credit.leftUsd): String =
        tr("${formatFixed(usd, 2)} ดอลลาร์ (ราว ${grouped(credit.baht(usd).roundToLong())} บาท)", "$${formatFixed(usd, 2)} (about ${grouped(credit.baht(usd).roundToLong())} baht)")

    /**
     * Questions about medicines and patients never go to a model, whatever is picked: an answer there cannot be wrong
     * (owner's work as a pharmacist). Words, not a judgement: a question it misses is still the owner's to judge.
     */
    fun isMedical(text: String): Boolean {
        val t = text.lowercase()
        val words = listOf(
            "ขนาดยา", "ปรับขนาด", "ยาตีกัน", "interaction", "พาราเซตามอล", "อินซูลิน",
            "dose", "dosing", "mg/kg", "มิลลิกรัม", "ผู้ป่วย", "คนไข้", "เลขบัตร", "patient", "drug", "medicine", "medication", "antibiotic",
        )
        if (words.any { it in t }) return true
        return MEDICAL.any { it.containsMatchIn(t) }
    }

    private val MEDICAL = listOf(
        // A hospital number: "hn" as its own Latin word ("HN 1234", "hn:5678", "hn123"), not inside "john".
        Regex("""(^|[^a-z])hn([^a-z]|$)"""),
        // "ยา" as a word: at the start, after a space, or after a verb (กินยา, ขอยาแก้ปวด, ทานยาก่อนอาหาร), but not the
        // start of ยาก, ยาว, ยาม, ยาย, ยาง, ยาน (ใช้ยาก, ยามเช้า), nor inside พยายาม, พยาบาล, วิทยา, ภรรยา.
        Regex("""(^|[\s\d.,!?()"']|เรื่อง|กิน|ทาน|ใช้|ให้|จ่าย|สั่ง|ขอ|ฉีด|แพ้|หยอด|พ่น|ปรับ|เปลี่ยน|หยุด|เก็บ|ซื้อ|ตัว|ชื่อ)ยา(?!ก(?![่้])|ว|ม|ย|ง|น(?!้))"""),
        // A strength after a number: "500 mg", "5 มก.", "10 ยูนิต" (not "มก." alone, which is also a university).
        Regex("""\d\s*(mg|mcg|มก|iu|ยูนิต)"""),
        // Medicines by name: common names and the endings drug names share (warfarin, amoxicillin, omeprazole).
        Regex("""aspirin|paracetamol|acetaminophen|metformin|insulin|morphine|heparin|digoxin|ibuprofen"""),
        Regex("""[a-z]{3,}(cillin|mycin|micin|floxacin|cycline|pril|sartan|olol|statin|azole|vir|mab|farin|parin|profen|pam|lam|tidine|dronate|gliptin|gliflozin|xaban|platin|taxel|rubicin|olone|sone|triptan|dipine|setron)\b"""),
    )

    /** What the model may answer with. */
    enum class Kind { COMMAND, ADD_TASK, FIND_TIME, ANSWER, CLAUDE }

    /** The model's reading of a sentence. [command] is in the guide's words; [task] is a quick-add sentence. */
    data class Reading(val kind: Kind, val reply: String, val command: String, val task: String, val minutes: Int)

    /** The JSON schema the answer must follow (structured output), as JSON text. */
    val SCHEMA: String = """
        {"type":"object","additionalProperties":false,
         "required":["kind","reply","command","task","minutes"],
         "properties":{
          "kind":{"type":"string","enum":["command","add_task","find_time","answer","claude"]},
          "reply":{"type":"string"},
          "command":{"type":"string"},
          "task":{"type":"string"},
          "minutes":{"type":"integer"}}}
    """.trimIndent()

    /** The instructions: the same every time but for the date, so they cost the same and stay short. */
    fun system(today: LocalDate): String {
        val builtins = SecretaryGuide.Builtin.entries.joinToString(", ") { it.th }
        return """
            คุณคือผู้ช่วยในแชตเลขาของแอป Omni ของเภสัชกรคนหนึ่ง วันนี้คือ ${today} (ปฏิทินสากล)
            อ่านประโยคที่เจ้าของพิมพ์ แล้วตอบเป็น JSON ตามแบบที่กำหนดเท่านั้น
            kind เลือกหนึ่งอย่าง:
            - command: ประโยคขอดูงานหรือจัดงานที่แอปทำเองได้ ใส่ command เป็นคำสั่งของแอปแบบใดแบบหนึ่ง
              "ในแอป: <คำสั่ง>" โดยคำสั่งคือ $builtins
              หรือ "กรอง: <เงื่อนไขคั่นด้วยจุลภาค>" เงื่อนไขที่ใช้ได้ #tag, วันที่ เลยกำหนด (หรือ วันนี้ สัปดาห์นี้ สัปดาห์หน้า อนาคต ไม่มีวันที่), กำหนดจริงภายใน N วัน, ความสำคัญ สูง (สูงสุด สูง กลาง ปกติ ต่ำ ต่ำสุด), ประเภท มีคนรอ (ปกติ มีคนรอ ลงทุนอนาคต พักไว้ก่อน), สถานะ กำลังทำ, ค้างนาน N วัน, คำ "<คำ>", เรียงตาม ความสำคัญ
            - add_task: ประโยคบอกงานที่จะทำ ใส่ task เป็นประโยคเพิ่มงานสั้น ๆ เช่น "ไปหาหมอ พรุ่งนี้ 9:00" (วันแบบ วันนี้ พรุ่งนี้ วันจันทร์ 25/10 เวลา 9:00)
            - find_time: อยากทำบางอย่างแต่ยังไม่รู้ว่าตอนไหน ใส่ task เป็นชื่องานพร้อมช่วงเวลาที่พูดถึง และ minutes เป็นเวลาที่ใช้ (ไม่รู้ใส่ 0)
            - answer: คำถามทั่วไปที่ตอบได้สั้น ๆ ใส่คำตอบใน reply
            - claude: เรื่องยาวหรือต้องใช้ข้อมูลมาก เช่นวางแผนทั้งเดือน
            reply: ภาษาไทยสั้น ๆ ไม่เกิน 5 บรรทัด บอกว่าเข้าใจว่าอะไร หรือคำตอบ ห้ามใช้ขีดยาวและจุดกลาง
            ช่องที่ไม่ใช้ ใส่ "" หรือ 0
            ห้ามตอบเรื่องขนาดยา การใช้ยา หรือข้อมูลผู้ป่วย ให้ kind เป็น answer และบอกว่าให้ใช้แหล่งอ้างอิง
            คุณมองไม่เห็นงานหรือปฏิทินของเจ้าของ เรื่องงานให้ใช้ command แทนการเดา
        """.trimIndent()
    }

    /** Reads the model's JSON; null when it is not in the agreed form. */
    fun parse(json: String): Reading? {
        fun str(name: String): String? = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json)?.groupValues?.get(1)?.let(::unescape)
        fun int(name: String): Int? = Regex(""""$name"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()
        val kind = when (str("kind")) {
            "command" -> Kind.COMMAND
            "add_task" -> Kind.ADD_TASK
            "find_time" -> Kind.FIND_TIME
            "answer" -> Kind.ANSWER
            "claude" -> Kind.CLAUDE
            else -> return null
        }
        return Reading(kind, str("reply").orEmpty().trim(), str("command").orEmpty().trim(), str("task").orEmpty().trim(), int("minutes") ?: 0)
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> {}
                    'u' -> if (i + 5 < s.length) { s.substring(i + 2, i + 6).toIntOrNull(16)?.let { out.append(it.toChar()) }; i += 4 }
                    else -> out.append(n)
                }
                i += 2
            } else { out.append(c); i++ }
        }
        return out.toString()
    }

    /** Days left before the API key runs out, when it is within a week (so the secretary page warns); null otherwise. */
    fun keyWarning(expires: LocalDate?, today: LocalDate): Long? {
        expires ?: return null
        val left = ChronoUnit.DAYS.between(today, expires)
        return left.takeIf { it <= 7 }
    }

    private fun formatFixed(x: Double, places: Int): String {
        var p = 1L
        repeat(places) { p *= 10 }
        val n = (x * p).roundToLong()
        val neg = n < 0
        val a = if (neg) -n else n
        val whole = a / p
        val frac = (a % p).toString().padStart(places, '0')
        return (if (neg) "-" else "") + grouped(whole) + "." + frac
    }

    private fun grouped(n: Long): String {
        val s = (if (n < 0) -n else n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, c -> if (i > 0 && (s.length - i) % 3 == 0) out.append(','); out.append(c) }
        return (if (n < 0) "-" else "") + out
    }
}
