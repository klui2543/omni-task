package app.omnitask.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import app.omnitask.time.*

/**
 * What the assistant knows about the owner, kept as a plain note in the vault (`หลังบ้าน/Omni/โปรไฟล์.md`)
 * so it can be read and corrected in Obsidian. Lines are `- key: value`; unknown lines are kept.
 */
data class Profile(
    val exists: Boolean = false,
    val wake: LocalTime = LocalTime.of(6, 30),
    val sleep: LocalTime = LocalTime.of(22, 30),
    val focusFrom: LocalTime = LocalTime.of(8, 0),
    val focusTo: LocalTime = LocalTime.of(11, 0),
    val exercise: LocalTime = LocalTime.of(17, 30),
    val shiftWords: List<String> = listOf("เวร"),
    val nightWords: List<String> = listOf("ดึก"),
    val bestDay: DayOfWeek? = null,
    val remembered: List<String> = emptyList(),
    val updated: LocalDate? = null,
) {
    /** Lifestyles change, so the assistant re-asks once the profile is a month old. */
    fun stale(today: LocalDate) = exists && (updated == null || updated.plusDays(30) <= today)

    fun render(): String = buildString {
        appendLine("# โปรไฟล์")
        appendLine()
        appendLine("ผู้ช่วยใน Omni Task อ่านไฟล์นี้ แก้ได้ตามสบาย")
        appendLine()
        appendLine("- อัปเดต: ${updated ?: ""}")
        appendLine("- ตื่น: ${hm(wake)}")
        appendLine("- นอน: ${hm(sleep)}")
        appendLine("- ช่วงสมองดี: ${hm(focusFrom)}-${hm(focusTo)}")
        appendLine("- ออกกำลังกาย: ${hm(exercise)}")
        appendLine("- คำที่หมายถึงเวร: ${shiftWords.joinToString(", ")}")
        appendLine("- คำที่หมายถึงเวรดึก: ${nightWords.joinToString(", ")}")
        appendLine("- วันที่ทำงานได้ดี: ${bestDay?.let { DAY_NAMES[it.isoDayNumber - 1] } ?: ""}")
        appendLine()
        appendLine("## จำไว้")
        remembered.forEach { appendLine("- $it") }
    }

    companion object {
        const val PATH = "📁 Folder/หลังบ้าน/Omni/โปรไฟล์.md"

        /** Where the profile was kept before the Omni folder moved into the back-office folder; read when the new one is not there. */
        const val LEGACY_PATH = "Omni/โปรไฟล์.md"
        /** Day names as written in the profile note; always Thai, since the note is parsed back. */
        val DAY_NAMES = listOf("จันทร์", "อังคาร", "พุธ", "พฤหัส", "ศุกร์", "เสาร์", "อาทิตย์")

        /** A day's full name for display, in the app's language. */
        fun dayName(day: DayOfWeek): String =
            tr(DAY_NAMES[day.isoDayNumber - 1], day.englishName)

        fun hm(t: LocalTime) = hhmm(t.hour, t.minute)

        private val TIME = Regex("""(\d{1,2})[.:](\d{2})""")
        private fun time(s: String): LocalTime? = TIME.find(s)?.let {
            val h = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
        }

        fun parse(text: String?): Profile {
            if (text == null) return Profile()
            var p = Profile(exists = true)
            var inMemory = false
            text.lines().forEach { raw ->
                val line = raw.trim()
                if (line.startsWith("#")) {
                    inMemory = line.contains("จำไว้")
                    return@forEach
                }
                if (!line.startsWith("- ")) return@forEach
                val body = line.removePrefix("- ").trim()
                if (inMemory) {
                    if (body.isNotEmpty()) p = p.copy(remembered = p.remembered + body)
                    return@forEach
                }
                val key = body.substringBefore(':').trim()
                val value = body.substringAfter(':', "").trim()
                p = when (key) {
                    "อัปเดต" -> p.copy(updated = runCatching { LocalDate.parse(value) }.getOrNull())
                    "ตื่น" -> time(value)?.let { p.copy(wake = it) } ?: p
                    "นอน" -> time(value)?.let { p.copy(sleep = it) } ?: p
                    "ช่วงสมองดี" -> {
                        val all = TIME.findAll(value).mapNotNull { time(it.value) }.toList()
                        if (all.size >= 2) p.copy(focusFrom = all[0], focusTo = all[1]) else p
                    }
                    "ออกกำลังกาย" -> time(value)?.let { p.copy(exercise = it) } ?: p
                    "คำที่หมายถึงเวร" -> p.copy(shiftWords = words(value).ifEmpty { p.shiftWords })
                    "คำที่หมายถึงเวรดึก" -> p.copy(nightWords = words(value).ifEmpty { p.nightWords })
                    "วันที่ทำงานได้ดี" -> p.copy(bestDay = DAY_NAMES.indexOfFirst { value.startsWith(it) }.takeIf { it >= 0 }?.let { DayOfWeek(it + 1) })
                    else -> p
                }
            }
            return p
        }

        private fun words(s: String) = s.split(',', '،', '/').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
