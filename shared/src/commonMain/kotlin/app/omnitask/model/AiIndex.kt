package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Keeps the conversation log in order for decades: plain notes anyone can read without the app.
 *
 * - [README_FILE] says what is where and how to find things, for the owner and for any AI later.
 * - [INDEX_FILE] has one line per month (how many exchanges, a summary, its main tags) under a heading per year,
 *   with the year's own summary once the year is over. Finding something starts here.
 * - Each month note carries a small header (`format`, `month`, and `closed` once the month is over), so a later
 *   version of the app knows which form it is reading.
 *
 * A month closes the first time the app runs after it ends: its line goes into the index, summed up by Gemini, or by
 * its topics when Gemini cannot be reached ([AUTO]; redone when it can). A month the owner edits later is summed up
 * again on the next weekly check.
 */
object AiIndex {
    /** The form of the notes. A later form converts the older notes and raises this. */
    const val FORMAT = "omni-log/3"

    const val README_FILE = "${AiMemory.LOG_DIR}/README.md"
    const val INDEX_FILE = "${AiMemory.LOG_DIR}/index.md"

    /** How a summary made without Gemini (from the topics) starts; such a line is summed up again when Gemini can. */
    const val AUTO = "หัวข้อ: "

    // ---- months ----

    /** `2026-10` for any day of October 2026. */
    fun monthKey(day: LocalDate): String = "${day.year}-${day.monthNumber.toString().padStart(2, '0')}"

    /** The month of a month note's path (`…/2026/10 Oct.md` is `2026-10`), or null for any other file. */
    fun monthKeyOf(path: String): String? =
        Regex("""(\d{4})/(\d{2}) [A-Za-z]{3}\.md$""").find(path)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }

    /** The first day of [key]'s month. */
    fun firstDay(key: String): LocalDate = LocalDate(key.take(4).toInt(), key.substring(5, 7).toInt(), 1)

    /** The note of [key]'s month. */
    fun pathOf(key: String): String = AiMemory.monthFile(firstDay(key))

    /** The month before [key]. */
    fun previous(key: String): String {
        val y = key.take(4).toInt()
        val m = key.substring(5, 7).toInt()
        return if (m == 1) "${y - 1}-12" else "$y-${(m - 1).toString().padStart(2, '0')}"
    }

    // ---- the header of a month note ----

    private val FRONT = Regex("""^---\n([\s\S]*?)\n---\n?""")

    /** The `key: value` lines of a note's header; empty when it has none. */
    fun front(text: String?): Map<String, String> {
        val body = FRONT.find(text.orEmpty().replace("\r\n", "\n"))?.takeIf { it.range.first == 0 }?.groupValues?.get(1) ?: return emptyMap()
        return body.lines().mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.take(it).trim() to l.substring(it + 1).trim() } }.toMap()
    }

    /**
     * [text] with its header saying this form and [key]'s month, and [closed] when given. Other header lines and the
     * whole body stay as they are.
     */
    fun withFront(text: String?, key: String, closed: LocalDate? = null): String {
        val t = text.orEmpty().replace("\r\n", "\n")
        val old = front(t)
        val head: MatchResult? = FRONT.find(t)?.takeIf { it.range.first == 0 }
        val body: String = if (head == null) t else t.substring(head.range.last + 1)
        val keys = LinkedHashMap<String, String>()
        keys["format"] = FORMAT
        keys["month"] = key
        old.forEach { (k, v) -> if (k !in keys) keys[k] = v }
        if (closed != null) keys["closed"] = closed.toString()
        return "---\n" + keys.entries.joinToString("\n") { "${it.key}: ${it.value}" } + "\n---\n" + body.trimStart('\n')
    }

    /** A new month note: its header, a title and one line about it. */
    fun newMonth(day: LocalDate): String = withFront(
        "# Gemini log ${monthKey(day)}\nบทสนทนากับผู้ช่วย Gemini ใน Omni เดือนนี้ วันใหม่สุดอยู่บน แต่ละครั้งมีหัวข้อและแท็ก ไม่ใช่งาน แก้หรือลบได้\n",
        monthKey(day),
    )

    /** Whether a month note is marked closed. */
    fun isClosed(text: String?): Boolean = front(text).containsKey("closed")

    /**
     * A short fingerprint of what a month holds (its exchanges, not its header), to tell whether it changed since
     * it was summed up. FNV-1a, so it is the same on every platform and in every version.
     */
    fun digest(entries: List<AiMemory.Entry>): String {
        var h = 0xcbf29ce484222325uL
        entries.forEach { e -> (e.text + "\n").forEach { c -> h = (h xor c.code.toULong()) * 0x100000001b3uL } }
        return h.toString(16)
    }

    // ---- the index ----

    /** One month in the index. */
    data class Month(val key: String, val count: Int, val summary: String, val tags: List<String>) {
        /** Summed up from its topics, without Gemini. */
        val auto: Boolean get() = summary.startsWith(AUTO)
    }

    private val MONTH_LINE = Regex("""^- \[\[[^|\]]*\|(\d{4}-\d{2})\]\] (\d+) ครั้ง: ?(.*)$""")
    private val YEAR_HEAD = Regex("""^## (\d{4})\s*$""")
    private const val YEAR_SUMMARY = "สรุปปี: "

    private const val INDEX_HEAD = "# สารบัญ Gemini log\nหนึ่งบรรทัดต่อเดือน ใหม่สุดอยู่บน แอปเขียนเองเมื่อปิดเดือน แก้ข้อความสรุปได้ เริ่มหาเรื่องเก่าที่นี่ ดูวิธีใน README"

    /** The months listed in [index], newest first. */
    fun months(index: String?): List<Month> = index.orEmpty().lines().mapNotNull { l ->
        MONTH_LINE.matchEntire(l.trimEnd())?.let { m ->
            val rest = m.groupValues[3]
            val tags = Regex("""(?<!\S)#([^\s#]+)""").findAll(rest).map { it.groupValues[1] }.toList()
            val summary = rest.replace(Regex("""(?<!\S)#[^\s#]+"""), "").trim()
            Month(m.groupValues[1], m.groupValues[2].toInt(), summary, tags)
        }
    }.sortedByDescending { it.key }

    /** Each year's summary in [index], by year. */
    fun years(index: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var year: String? = null
        index.orEmpty().lines().forEach { l ->
            YEAR_HEAD.matchEntire(l)?.let { year = it.groupValues[1]; return@forEach }
            if (l.startsWith("## ")) year = null
            val y = year
            if (y != null && l.startsWith(YEAR_SUMMARY)) out[y] = l.removePrefix(YEAR_SUMMARY).trim()
        }
        return out
    }

    /** The index line of [m]. Its link goes by the full path, so it can never point at another note of the same name. */
    fun line(m: Month): String =
        "- [[${pathOf(m.key).removeSuffix(".md")}|${m.key}]] ${m.count} ครั้ง: ${flat(m.summary)}" + m.tags.joinToString("") { " #$it" }

    private fun base(index: String?): MutableList<String> {
        val t = index?.takeIf { it.isNotBlank() }?.replace("\r\n", "\n") ?: "---\nformat: $FORMAT\n---\n$INDEX_HEAD\n"
        return t.trimEnd().lines().toMutableList()
    }

    /** Where year [year]'s heading is, adding it in order (newest year on top) when missing. */
    private fun yearAt(all: MutableList<String>, year: String): Int {
        all.indexOfFirst { YEAR_HEAD.matchEntire(it)?.groupValues?.get(1) == year }.takeIf { it >= 0 }?.let { return it }
        val later = all.indexOfFirst { l -> YEAR_HEAD.matchEntire(l)?.groupValues?.get(1)?.let { it < year } == true }
        val block = listOf("## $year", "")
        return if (later >= 0) {
            all.addAll(later, block); later
        } else {
            if (all.lastOrNull()?.isNotBlank() == true) all.add("")
            all.addAll(block); all.size - 2
        }
    }

    private fun yearEnd(all: List<String>, at: Int): Int = (at + 1 until all.size).firstOrNull { all[it].startsWith("## ") } ?: all.size

    /**
     * [index] with [m]'s line put in: in place of its old line, or under its year in order (newest first). Every
     * other line, the owner's own included, stays as it is.
     */
    fun setMonth(index: String?, m: Month): String {
        val all = base(index)
        val old = all.indexOfFirst { MONTH_LINE.matchEntire(it.trimEnd())?.groupValues?.get(1) == m.key }
        if (old >= 0) {
            all[old] = line(m)
        } else {
            val at = yearAt(all, m.key.take(4))
            val end = yearEnd(all, at)
            val before = (at + 1 until end).firstOrNull { i -> MONTH_LINE.matchEntire(all[i].trimEnd())?.groupValues?.get(1)?.let { it < m.key } == true }
            val lastLine = (at + 1 until end).lastOrNull { all[it].isNotBlank() } ?: at
            all.add(before ?: (lastLine + 1), line(m))
        }
        return tidy(all)
    }

    /** [index] with [year]'s summary set, on the line under its heading. */
    fun setYear(index: String?, year: String, summary: String): String {
        val all = base(index)
        val at = yearAt(all, year)
        val end = yearEnd(all, at)
        val old = (at + 1 until end).firstOrNull { all[it].startsWith(YEAR_SUMMARY) }
        val text = YEAR_SUMMARY + flat(summary)
        if (old != null) all[old] = text else all.add(at + 1, text)
        return tidy(all)
    }

    /** One blank line between blocks, none doubled, and a line break at the end. */
    private fun tidy(all: List<String>): String {
        val out = mutableListOf<String>()
        all.forEach { l ->
            if (l.startsWith("## ") && out.lastOrNull()?.isNotBlank() == true) out.add("")
            if (l.isBlank() && out.lastOrNull()?.isBlank() == true) return@forEach
            if (l.isBlank() && out.lastOrNull()?.startsWith("## ") == true) return@forEach
            out.add(l)
        }
        return out.joinToString("\n").trimEnd() + "\n"
    }

    /** What the model gets of the index: the years' summaries and the months, newest first, cut to [max] characters. */
    fun forModel(index: String?, max: Int = 30_000): String {
        val years = years(index)
        val lines = months(index).groupBy { it.key.take(4) }.entries.sortedByDescending { it.key }.flatMap { (y, ms) ->
            listOfNotNull(years[y]?.let { "ปี $y: $it" }) + ms.map { m -> "${m.key} (${m.count} ครั้ง): ${m.summary}" + m.tags.joinToString("") { " #$it" } }
        }
        var used = 0
        return lines.takeWhile { l -> (used + l.length + 1 <= max).also { if (it) used += l.length + 1 } }.joinToString("\n")
    }

    /** The months whose summary or tags share the most words with [query], best first. */
    fun monthsByWords(months: List<Month>, query: String, k: Int, words: (String) -> List<String> = AiMemory::spaceWords): List<Month> {
        val wanted = words(query).map { it.lowercase() }.filter { it.length >= 2 }.distinct()
        if (wanted.isEmpty()) return emptyList()
        return months.map { m -> m to wanted.count { w -> (m.summary + " " + m.tags.joinToString(" ")).contains(w, ignoreCase = true) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(k).map { it.first }
    }

    // ---- summing up ----

    /** The tags used most in [entries], at most [n]. */
    fun topTags(entries: List<AiMemory.Entry>, n: Int = 5): List<String> =
        entries.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(n).map { it.key }

    /** A month summed up without Gemini: its topics, newest first, as many as fit. */
    fun autoMonth(key: String, entries: List<AiMemory.Entry>): Month {
        val topics = entries.map { it.topic }.filter { it.isNotBlank() }.distinct()
        var used = 0
        val shown = topics.takeWhile { t -> (used + t.length + 2 <= 240).also { if (it) used += t.length + 2 } }
        return Month(key, entries.size, AUTO + shown.joinToString(", ").ifEmpty { "-" }, topTags(entries))
    }

    private const val MONTH_ASK = """สรุปบทสนทนาของเดือนนี้ระหว่างเจ้าของงานกับผู้ช่วย เป็นภาษาไทย 1 ถึง 2 ประโยค ไม่เกิน 250 ตัวอักษร
เน้นสิ่งที่ตัดสินใจ สิ่งที่ทำเสร็จ และเรื่องที่ยังค้าง ใช้ชื่อโปรเจกต์และคำเฉพาะตามที่คุยจริง เพื่อให้ค้นเจอภายหลัง
ห้ามใช้ em dash และจุดกลาง ตอบเฉพาะข้อความสรุป ไม่ต้องมีหัวข้อ"""

    private const val YEAR_ASK = """ด้านล่างคือสรุปรายเดือนของทั้งปี สรุปทั้งปีเป็นภาษาไทย 2 ถึง 3 ประโยค ไม่เกิน 400 ตัวอักษร
บอกเรื่องหลักของปี โปรเจกต์ที่จบ และสิ่งที่ยกไปปีหน้า ห้ามใช้ em dash และจุดกลาง ตอบเฉพาะข้อความสรุป"""

    private fun textRequest(ask: String, material: String): String = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") { add(buildJsonObject { put("text", "$ask\n\n$material") }) }
            })
        }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", 512)
            putJsonObject("thinkingConfig") { put("thinkingLevel", "minimal") }
        }
    }.toString()

    /** The body of a generateContent call that sums up [key]'s month from its [entries] (the newest part when long). */
    fun monthRequest(key: String, entries: List<AiMemory.Entry>, max: Int = 60_000): String {
        var used = 0
        val shown = entries.takeWhile { e -> (used + e.text.length + 1 <= max || used == 0).also { if (it) used += e.text.length + 1 } }
        return textRequest(MONTH_ASK, "[เดือน $key ทั้งหมด ${entries.size} ครั้ง]\n" + shown.joinToString("\n\n") { it.text })
    }

    /** The body of a generateContent call that sums up [year] from its [months]. */
    fun yearRequest(year: String, months: List<Month>): String =
        textRequest(YEAR_ASK, "[ปี $year]\n" + months.sortedBy { it.key }.joinToString("\n") { "${it.key} (${it.count} ครั้ง): ${it.summary}" })

    /** The summary in a generateContent [response]: one line, cleaned, at most [max] characters. */
    fun summaryText(response: String, max: Int = 400): String = flat(AiAssist.clean(AiAssist.answerText(response))).take(max)

    /** Whether [year] is over and every one of its months in [months] was summed up by Gemini, so the year can be. */
    fun yearReady(year: String, months: List<Month>, today: LocalDate): Boolean {
        val ms = months.filter { it.key.startsWith("$year-") }
        return year.toInt() < today.year && ms.isNotEmpty() && ms.none { it.auto }
    }

    private fun flat(text: String) = text.trim().replace(Regex("""\s*\n\s*"""), " ").replace(Regex("""\s+"""), " ")

    // ---- the README ----

    /** Whether the README is missing or written for an older form, so the app writes this one. */
    fun readmeNeeded(text: String?): Boolean = text == null || !text.contains("format: $FORMAT")

    val README: String = """---
format: $FORMAT
---
# Gemini log

ทุกครั้งที่คุยกับผู้ช่วย Gemini ในแอป Omni ถูกบันทึกไว้ที่นี่ เป็นข้อความธรรมดา (Markdown) เปิดอ่านได้ด้วยโปรแกรมอะไรก็ได้ แม้ไม่มีแอป Omni แล้ว

## อะไรอยู่ตรงไหน
- `index.md`: สารบัญ หนึ่งบรรทัดต่อเดือน (จำนวนครั้ง สรุป แท็กหลัก) ใต้หัวของแต่ละปี และสรุปทั้งปีเมื่อปีจบ เริ่มหาที่นี่
- `<ปี>/<เลขเดือน> <ชื่อเดือน>.md` เช่น `2026/10 Oct.md`: บันทึกของเดือนนั้น วันใหม่สุดอยู่บน
- `../Gemini memory.md`: สิ่งที่ให้ผู้ช่วยจำไว้เสมอ ผู้ช่วยอ่านทั้งไฟล์ทุกครั้ง

## รูปแบบในไฟล์เดือน
- หัวไฟล์ (ระหว่างเส้น `---`): `format` รุ่นของรูปแบบ, `month` เดือนของไฟล์, `closed` วันที่ปิดเดือน
- `## YYYY-MM-DD` คือวัน
- `### HH:mm หัวข้อ #แท็ก` คือการคุยหนึ่งครั้ง ตามด้วย `- ถาม: ...` และ `- Gemini (รุ่น): ...`
- `- HH:mm ทำแล้ว: ...` หรือ `- HH:mm ไม่ทำ: ...` คือสิ่งที่กดยืนยันหรือไม่ทำหลังคำตอบ
- ไม่มี checkbox เลย จึงไม่ถูกนับเป็นงาน

## กติกา
- แอปเขียนเฉพาะเดือนปัจจุบัน เดือนที่ปิดแล้วแอปไม่เขียนเพิ่ม
- แก้หรือลบบรรทัดไหนก็ได้ ไม่มีอะไรพัง แอปตรวจทุกสัปดาห์และสรุปเดือนที่ถูกแก้ใหม่
- สรุปที่ขึ้นต้นด้วย "$AUTO" ทำตอนที่ติดต่อ Gemini ไม่ได้ แอปจะสรุปใหม่เองเมื่อทำได้
- ดัชนีค้นหาตามความหมายเก็บในเครื่องเท่านั้น หายหรือเปลี่ยนเครื่องก็สร้างใหม่เองจากไฟล์เหล่านี้
- ถ้ารูปแบบเปลี่ยนในอนาคต แอปจะแปลงไฟล์เก่าให้เองและเปลี่ยนเลข `format`

## วิธีหาเรื่องเก่า (สำหรับคนหรือ AI)
1. อ่าน `index.md` หาเดือนที่เกี่ยวข้องจากสรุปและแท็ก
2. เปิดไฟล์เดือนนั้น ดูหัวข้อ `###`
3. ไม่เจอ: ค้นคำในทั้งโฟลเดอร์ (ช่องค้นหาของ Obsidian หรือ grep)
"""
}
