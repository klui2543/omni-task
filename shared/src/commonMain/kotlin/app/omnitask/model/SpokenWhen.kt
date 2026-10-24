package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

/**
 * The day and time said in a spoken task ("นัดยาตอนเย็น", "ส่งตารางพรุ่งนี้บ่ายสองโมง"), taken out of its words so
 * they become the task's date and reminder. A time said plainly wins over a time word; a time word is read from the
 * owner's table ([TimeWords]); words the table does not know stay in the title, as said. A date is a real deadline
 * (📅) unless the owner calls it a target (⏳).
 */
object SpokenWhen {

    /** What was heard: the title left, and the day, time and kind of date found (all may be missing). */
    data class Heard(val title: String, val date: LocalDate?, val time: LocalTime?, val field: TaskLine.DateField = TaskLine.DateField.DUE)

    private val NUM = mapOf(
        "หนึ่ง" to 1, "สอง" to 2, "สาม" to 3, "สี่" to 4, "ห้า" to 5, "หก" to 6, "เจ็ด" to 7, "แปด" to 8, "เก้า" to 9,
        "สิบ" to 10, "สิบเอ็ด" to 11, "สิบสอง" to 12,
    )
    private const val N = "(\\d{1,2}|สิบเอ็ด|สิบสอง|หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า|สิบ)"
    private fun n(s: String) = s.toIntOrNull() ?: NUM.getValue(s)

    private val DAYS = listOf("จันทร์", "อังคาร", "พุธ", "พฤหัสบดี|พฤหัส", "ศุกร์", "เสาร์", "อาทิตย์")

    // Times said plainly, most exact first; each gives the hour and minute from its match.
    private val TIMES: List<Pair<Regex, (MatchResult) -> LocalTime?>> = listOf(
        Regex("(\\d{1,2})[:.](\\d{2})\\s*(?:น\\.|นาฬิกา)?") to { m -> t(m.groupValues[1].toInt(), m.groupValues[2].toInt()) },
        Regex("บ่าย\\s*${N}?\\s*โมง\\s*(ครึ่ง)?") to { m -> t(12 + (m.groupValues[1].ifEmpty { "1" }.let(::n)), half(m.groupValues[2])) },
        Regex("${N}\\s*โมง\\s*เย็น\\s*(ครึ่ง)?") to { m -> t(12 + n(m.groupValues[1]), half(m.groupValues[2])) },
        Regex("${N}\\s*ทุ่ม\\s*(ครึ่ง)?") to { m -> t(18 + n(m.groupValues[1]), half(m.groupValues[2])) },
        Regex("ตี\\s*${N}\\s*(ครึ่ง)?") to { m -> t(n(m.groupValues[1]), half(m.groupValues[2])) },
        Regex("เที่ยง\\s*ครึ่ง") to { _ -> t(12, 30) },
        Regex("เที่ยงคืน") to { _ -> t(0, 0) },
        Regex("${N}\\s*โมง\\s*(?:เช้า)?\\s*(ครึ่ง)?") to { m -> n(m.groupValues[1]).let { h -> t(if (h in 1..6) h + 12 else h, half(m.groupValues[2])) } },
    )

    private fun half(s: String) = if (s.isNotEmpty()) 30 else 0
    private fun t(h: Int, m: Int) = if (h in 0..23 && m in 0..59) LocalTime(h, m) else null

    /** Words that make the date the owner's own target instead of a deadline. */
    private val TARGET = Regex("เป้า|ว่าจะ")

    fun read(words: String, table: List<TimeWords.Word>, today: LocalDate, now: LocalTime): Heard {
        var s = " $words "
        var date: LocalDate? = null
        var time: LocalTime? = null
        fun cut(r: IntRange) { s = s.removeRange(r).let { it.substring(0, r.first) + " " + it.substring(r.first) } }

        // The day.
        listOf(
            Regex("วันนี้|คืนนี้") to { _: MatchResult -> today },
            Regex("มะรืน(?:นี้)?") to { _: MatchResult -> today.plusDays(2) },
            Regex("พรุ่งนี้") to { _: MatchResult -> today.plusDays(1) },
            Regex("(?:อาทิตย์|สัปดาห์)หน้า") to { _: MatchResult -> today.plusDays((8 - today.dayOfWeek.isoDayNumber).toLong()) },
        ).forEach { (r, f) -> if (date == null) r.find(s)?.let { m -> date = f(m); cut(m.range) } }
        if (date == null) DAYS.forEachIndexed { i, names ->
            if (date != null) return@forEachIndexed
            Regex("วัน(?:$names)\\s*(นี้|หน้า)?").find(s)?.let { m ->
                val ahead = ((i + 1) - today.dayOfWeek.isoDayNumber + 7) % 7
                date = today.plusDays(ahead.toLong() + if (m.groupValues[1] == "หน้า") 7 else 0)
                cut(m.range)
            }
        }
        // A time said plainly.
        for ((r, f) in TIMES) {
            val m = r.find(s) ?: continue
            val v = f(m) ?: continue
            time = v
            cut(m.range)
            break
        }
        // The owner's time words: taken out of the title; their time counts only when none was said plainly.
        table.sortedByDescending { it.word.length }.forEach { w ->
            Regex("(?:ตอน|ช่วง)?\\s*${Regex.escape(w.word)}(?:นี้)?").find(s)?.let { m ->
                if (time == null) time = w.time
                cut(m.range)
            }
        }
        if (time != null && date == null) date = if (time!! >= now) today else today.plusDays(1)
        val field = if (TARGET.containsMatchIn(s)) TaskLine.DateField.SCHEDULED else TaskLine.DateField.DUE
        // The joining words left hanging once the day and time are out.
        val title = s.replace(Regex("\\s+"), " ").trim()
            .replace(Regex("(?:\\s|^)(?:เวลา|ตอน|ช่วง|ภายใน)(?=\\s|$)"), " ")
            .replace(Regex("(?:เวลา|ตอน|ช่วง)$"), "")
            .replace(Regex("\\s+"), " ").trim()
        return Heard(title.ifEmpty { words.trim() }, date, time, field)
    }

    /** The task line for [h] (with an optional [project] tag), added today. */
    fun line(h: Heard, project: String?, today: LocalDate): String {
        var raw = VoiceNotes.line(h.title, today)
        if (project != null) raw = TaskLine.homeTag(raw, project)
        if (h.date != null) raw = TaskLine.setDate(raw, h.field, h.date)
        if (h.date != null && h.time != null) {
            raw = TaskLine.setReminder(raw, h.time, if (h.field == TaskLine.DateField.SCHEDULED) ReminderOn.SCHEDULED else ReminderOn.DUE)
        }
        return raw
    }
}

/**
 * The owner's time words ("เย็น" is 17:00): a spoken task that says one gets that time. Kept in the vault as
 * `- เย็น 17:00` lines, so the app and Obsidian change the same list.
 */
object TimeWords {
    const val PATH = "${app.omnitask.data.VaultText.OMNI_DIR}/กฎเวลา.md"

    data class Word(val word: String, val time: LocalTime)

    val DEFAULT = listOf(
        Word("เช้า", LocalTime(8, 0)), Word("เที่ยง", LocalTime(12, 0)), Word("บ่าย", LocalTime(14, 0)),
        Word("เย็น", LocalTime(17, 0)), Word("ค่ำ", LocalTime(19, 0)),
    )

    private val LINE = Regex("""^\s*-\s+(.+?)\s+(\d{1,2}):(\d{2})\s*$""")

    fun parse(text: String?): List<Word> {
        if (text.isNullOrBlank()) return DEFAULT
        return text.lines().mapNotNull { l ->
            LINE.find(l)?.let { m ->
                val h = m.groupValues[2].toInt(); val mi = m.groupValues[3].toInt()
                if (h in 0..23 && mi in 0..59) Word(m.groupValues[1].trim(), LocalTime(h, mi)) else null
            }
        }
    }

    fun render(words: List<Word>): String = buildString {
        appendLine("# กฎเวลา")
        appendLine()
        appendLine("คำบอกเวลาที่ Omni ใช้ตอนจดด้วยเสียง พูดคำเหล่านี้แล้วงานได้เวลานั้น และคำถูกตัดออกจากชื่องาน คำที่ไม่อยู่ในนี้อยู่ในชื่องานตามเดิม แก้ได้ทั้งในนี้และในแอป (ตั้งค่า > งานของฉัน > คำบอกเวลา)")
        appendLine()
        words.forEach { appendLine("- ${it.word} ${it.time.hour.toString().padStart(2, '0')}:${it.time.minute.toString().padStart(2, '0')}") }
    }
}
