package app.omnitask.model

import app.omnitask.data.VaultText
import kotlinx.datetime.LocalDate

/**
 * What the assistant remembers, kept as two notes in the vault so every device and Claude can read them and the
 * owner can edit them in Obsidian.
 *
 * [LOG_FILE] holds every conversation, newest day on top, as plain bullets (no checkboxes, so they are never read as
 * tasks). [MEMORY_FILE] holds what the owner agreed should always be remembered, one bullet each.
 */
object AiMemory {
    const val LOG_FILE = "${VaultText.OMNI_DIR}/Gemini log.md"
    const val MEMORY_FILE = "${VaultText.OMNI_DIR}/Gemini memory.md"

    /** Up to this many characters of the log go to the model whole; past that the newest part and the matching older lines go. */
    const val LOG_MAX = 120_000

    private const val LOG_HEAD = "# Gemini log\nบทสนทนากับผู้ช่วย Gemini ใน Omni วันใหม่สุดอยู่บน ไม่ใช่งาน แก้หรือลบได้"
    private const val MEMORY_HEAD = "# Gemini memory\nสิ่งที่ผู้ช่วย Gemini ใน Omni จำไว้เสมอ แก้หรือลบบรรทัดได้ ผู้ช่วยอ่านทั้งไฟล์ทุกครั้ง"

    /** One line of text: no line breaks, and no checkbox that would make it a task. */
    private fun flat(text: String) = text.trim().replace(Regex("""\s*\n\s*"""), " / ").replace(Regex("""^\[[ xX/>-]\]\s*"""), "")

    /**
     * The log with [lines] added at the end of [day]'s section (made at the top when it is a new day). Each line is
     * written as a bullet; a line starting with two spaces stays under the one before it.
     */
    fun addToLog(log: String?, day: LocalDate, lines: List<String>): String {
        val body = log?.takeIf { it.isNotBlank() }?.trimEnd() ?: LOG_HEAD
        val entry = lines.joinToString("\n") { l -> if (l.startsWith("  ")) "  - " + flat(l) else "- " + flat(l) }
        val all = body.lines().toMutableList()
        val head = "## $day"
        val at = all.indexOfFirst { it.trim() == head }
        if (at < 0) {
            // A new day goes above the first older day, under the note's own heading and description.
            val firstDay = all.indexOfFirst { it.startsWith("## ") }
            val insert = listOf(head) + entry.lines() + ""
            if (firstDay < 0) {
                if (all.lastOrNull()?.isNotBlank() == true) all.add("")
                all.addAll(insert)
            } else {
                all.addAll(firstDay, insert)
            }
        } else {
            // The end of today's section: before the next day, past any blank lines that close it.
            var end = all.indexOfFirst(at + 1) { it.startsWith("## ") }.let { if (it < 0) all.size else it }
            while (end > at + 1 && all[end - 1].isBlank()) end--
            all.addAll(end, entry.lines())
        }
        return all.joinToString("\n").trimEnd() + "\n"
    }

    private fun <T> List<T>.indexOfFirst(from: Int, test: (T) -> Boolean): Int {
        for (i in from until size) if (test(this[i])) return i
        return -1
    }

    /** The memory with [text] added as a bullet, dated [day]. Nothing changes when the same text is already there. */
    fun remember(memory: String?, text: String, day: LocalDate): String {
        val body = memory?.takeIf { it.isNotBlank() }?.trimEnd() ?: MEMORY_HEAD
        val line = flat(text)
        if (line.isEmpty() || body.lines().any { it.removePrefix("- ").substringBeforeLast(" (").trim() == line }) return body + "\n"
        return "$body\n- $line ($day)\n"
    }

    /** The bullets of the memory, without the heading, for the model. */
    fun memoryForModel(memory: String?): String =
        memory?.lines()?.filter { it.trimStart().startsWith("- ") }?.joinToString("\n").orEmpty()

    /**
     * The log as the model reads it: all of it while it fits [max]; past that the newest part, and the older lines
     * that share a word (three letters or more) with the [question], so an old agreement can still be found.
     */
    fun logForModel(log: String?, question: String, max: Int = LOG_MAX): String {
        val text = log?.lines()?.dropWhile { !it.startsWith("## ") }?.joinToString("\n")?.trim().orEmpty()
        if (text.length <= max) return text
        val newest = text.take(max * 3 / 4).substringBeforeLast("\n")
        val words = question.split(Regex("""[\s,.?!:;"'()]+""")).map { it.trim() }.filter { it.length >= 3 }.distinct()
        if (words.isEmpty()) return newest
        var day = ""
        val older = buildList {
            text.substring(newest.length).lines().forEach { line ->
                if (line.startsWith("## ")) day = line.removePrefix("## ").trim()
                else if (words.any { w -> line.contains(w, ignoreCase = true) }) add("$day $line")
            }
        }
        val room = max - newest.length
        val picked = StringBuilder()
        for (l in older) { if (picked.length + l.length + 1 > room) break; picked.appendLine(l) }
        return if (picked.isEmpty()) newest else "$newest\n\n[บทสนทนาเก่าที่เกี่ยวกับคำถามนี้]\n${picked.toString().trimEnd()}"
    }
}
