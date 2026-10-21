package app.omnitask.model

import app.omnitask.data.VaultText
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.sqrt

/**
 * What the assistant remembers, kept as two notes in the vault so every device and Claude can read them and the
 * owner can edit them in Obsidian, built so the right part can be found again:
 *
 * - [MEMORY_FILE]: what the owner agreed should always be remembered, one bullet each. Always sent whole.
 * - [LOG_FILE]: every exchange under its day (newest day on top), each under a `### HH:mm topic #tags` heading that
 *   Gemini writes with its answer. The headings make an index that is always sent, and a way to find old
 *   exchanges: by meaning (embeddings) or by shared words.
 *
 * Plain bullets only: no checkboxes, so nothing here is ever read as a task.
 */
object AiMemory {
    const val LOG_FILE = "${VaultText.OMNI_DIR}/Gemini log.md"
    const val MEMORY_FILE = "${VaultText.OMNI_DIR}/Gemini memory.md"

    /** The whole log goes to the model while it is this short; past that, the newest part and what is found. */
    const val WHOLE = 40_000

    /** How much of the newest log goes when the whole of it does not. */
    const val RECENT = 20_000

    /** At most this many index lines (one per exchange) go to the model. */
    const val INDEX_MAX = 500

    private const val LOG_HEAD = "# Gemini log\nบทสนทนากับผู้ช่วย Gemini ใน Omni วันใหม่สุดอยู่บน แต่ละครั้งมีหัวข้อและแท็ก ไม่ใช่งาน แก้หรือลบได้"
    private const val MEMORY_HEAD = "# Gemini memory\nสิ่งที่ผู้ช่วย Gemini ใน Omni จำไว้เสมอ แก้หรือลบบรรทัดได้ ผู้ช่วยอ่านทั้งไฟล์ทุกครั้ง"

    /** One line of text: no line breaks, and no checkbox that would make it a task. */
    private fun flat(text: String) = text.trim().replace(Regex("""\s*\n\s*"""), " / ").replace(Regex("""^\[[ xX/>-]\]\s*"""), "")

    private fun tagOf(t: String) = t.trim().removePrefix("#").replace(Regex("""\s+"""), "-").takeIf { it.isNotEmpty() }

    // ---- writing ----

    /**
     * The log with one exchange added under [day]: a `### time topic #tags` heading and its [lines] as bullets.
     * Exchanges written before headings existed ([upgrade]) are given one first.
     */
    fun addExchange(log: String?, day: LocalDate, time: String, topic: String, tags: List<String>, lines: List<String>): String {
        val heading = "### $time " + flat(topic).take(80) + tags.mapNotNull(::tagOf).distinct().joinToString("") { " #$it" }
        return insert(upgrade(log), day, listOf(heading) + lines.map { "- " + flat(it) })
    }

    /** The log with [line] added at the end of [day], under the newest exchange (what was done after an answer). */
    fun addNote(log: String?, day: LocalDate, line: String): String = insert(upgrade(log), day, listOf("- " + flat(line)))

    private fun insert(log: String, day: LocalDate, add: List<String>): String {
        val all = log.trimEnd().lines().toMutableList()
        val head = "## $day"
        val at = all.indexOfFirst { it.trim() == head }
        if (at < 0) {
            // A new day goes above the first older day, under the note's own heading and description.
            val firstDay = all.indexOfFirst { it.startsWith("## ") }
            val block = listOf(head) + add + ""
            if (firstDay < 0) {
                if (all.lastOrNull()?.isNotBlank() == true) all.add("")
                all.addAll(block)
            } else {
                all.addAll(firstDay, block)
            }
        } else {
            var end = (at + 1 until all.size).firstOrNull { all[it].startsWith("## ") } ?: all.size
            while (end > at + 1 && all[end - 1].isBlank()) end--
            all.addAll(end, add)
        }
        return all.joinToString("\n").trimEnd() + "\n"
    }

    private val OLD_ASK = Regex("""^- (\d{1,2}:\d{2}) ถาม: (.*)$""")

    /**
     * The log in today's form: an exchange from before headings existed (`- 12:34 ถาม: …` with its answer under
     * it) gets a heading made from its question, so the index and the search find it too.
     */
    fun upgrade(log: String?): String {
        val body = log?.takeIf { it.isNotBlank() } ?: return LOG_HEAD + "\n"
        // Most logs are already in today's form: a plain text check spares going through every line.
        if (!body.contains(" ถาม: ") || body.lines().none { OLD_ASK.matches(it) }) return body
        return body.lines().flatMap { line ->
            val m = OLD_ASK.matchEntire(line) ?: return@flatMap listOf(line.replace(Regex("""^  - (Gemini )"""), "- $1"))
            listOf("### ${m.groupValues[1]} ${m.groupValues[2].take(60)}", "- ถาม: ${m.groupValues[2]}")
        }.joinToString("\n")
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

    // ---- reading ----

    /** One exchange: when, its topic and tags, and its lines. [id] stays the same while the text does. */
    data class Entry(val day: String, val time: String, val topic: String, val tags: List<String>, val body: String) {
        val id: String get() = "$day $time ${(topic + body).hashCode()}"
        val heading: String get() = "$day $time $topic" + tags.joinToString("") { " #$it" }
        val text: String get() = "$heading\n$body"
    }

    private val EXCHANGE = Regex("""^### (\d{1,2}:\d{2})\s+(.*)$""")

    /** Every exchange in the log, newest first (the order the log keeps). */
    fun entries(log: String?): List<Entry> {
        val out = mutableListOf<Entry>()
        var day = ""
        var cur: Triple<String, String, MutableList<String>>? = null
        fun close() {
            cur?.let { (time, head, lines) ->
                val tags = Regex("""(?<!\S)#([^\s#]+)""").findAll(head).map { it.groupValues[1] }.toList()
                val topic = head.replace(Regex("""(?<!\S)#[^\s#]+"""), "").trim()
                out += Entry(day, time, topic, tags, lines.joinToString("\n").trim())
            }
            cur = null
        }
        upgrade(log).lines().forEach { line ->
            when {
                line.startsWith("## ") -> { close(); day = line.removePrefix("## ").trim() }
                line.startsWith("### ") -> {
                    close()
                    EXCHANGE.matchEntire(line)?.let { m -> cur = Triple(m.groupValues[1], m.groupValues[2], mutableListOf()) }
                }
                line.isNotBlank() -> cur?.third?.add(line)
            }
        }
        close()
        // Within a day exchanges are written in time order; newest first overall.
        return out.withIndex().sortedWith(compareByDescending<IndexedValue<Entry>> { it.value.day }.thenByDescending { it.index }).map { it.value }
    }

    /** The index: one line per exchange, newest first, so the model sees everything ever talked about. */
    fun index(entries: List<Entry>): String = entries.take(INDEX_MAX).joinToString("\n") { it.heading }

    /** What of the log goes whole: everything while short, else the newest exchanges up to [RECENT] characters. */
    fun recent(entries: List<Entry>): List<Entry> {
        val total = entries.sumOf { it.text.length + 1 }
        if (total <= WHOLE) return entries
        var used = 0
        return entries.takeWhile { e -> (used + e.text.length + 1 <= RECENT || used == 0).also { if (it) used += e.text.length + 1 } }
    }

    /**
     * The exchanges that share the most words with [query], best first, at most [k]. [words] splits text into
     * words; Thai has no spaces between them, so the phone passes its own dictionary-based splitter.
     */
    fun byWords(entries: List<Entry>, query: String, k: Int, words: (String) -> List<String> = ::spaceWords): List<Entry> {
        val wanted = words(query).map { it.lowercase() }.filter { it.length >= 2 && it !in STOP }.distinct()
        if (wanted.isEmpty()) return emptyList()
        return entries.map { e -> e to wanted.count { w -> e.text.contains(w, ignoreCase = true) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(k).map { it.first }
    }

    /** Words split at spaces and punctuation, for where no better splitter is at hand. */
    fun spaceWords(text: String): List<String> = text.split(Regex("""[\s,.?!:;"'()/\[\]]+""")).filter { it.isNotBlank() }

    /** Words too common to say anything about which exchange is meant. */
    private val STOP = setOf("ที่", "และ", "หรือ", "ของ", "ให้", "ได้", "ไม่", "มี", "เป็น", "จะ", "ไป", "มา", "ว่า", "แล้ว", "อะไร", "ยังไง", "ไหม", "มั้ย", "ครับ", "ค่ะ", "นะ", "the", "and", "what")

    /** Cosine similarity of two vectors of the same length; 0 when either is empty. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
    }

    /** The ids of the [k] vectors closest in meaning to [query], at least [min] alike, best first. */
    fun nearest(query: FloatArray, docs: Map<String, FloatArray>, k: Int, min: Float = 0.55f): List<String> =
        docs.map { (id, v) -> id to cosine(query, v) }.filter { it.second >= min }.sortedByDescending { it.second }.take(k).map { it.first }

    /** The text that stands for an exchange when it is turned into a vector: heading and lines, cut to a sane size. */
    fun embedText(e: Entry): String = e.text.take(2000)

    /** The body of a batchEmbedContents call for [texts]: documents to store, or a [query] to look them up with. */
    fun embedRequest(model: String, texts: List<String>, query: Boolean, dims: Int = DIMS): String = buildJsonObject {
        putJsonArray("requests") {
            texts.forEach { t ->
                add(buildJsonObject {
                    put("model", "models/$model")
                    putJsonObject("content") { putJsonArray("parts") { add(buildJsonObject { put("text", t) }) } }
                    put("taskType", if (query) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT")
                    put("outputDimensionality", dims)
                })
            }
        }
    }.toString()

    /** Vector size asked for: small enough to keep hundreds on the phone, enough to tell topics apart. */
    const val DIMS = 256

    /** The vectors in a batchEmbedContents response, in the order asked. */
    fun parseEmbeddings(response: String): List<FloatArray> {
        val root = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return emptyList()
        return (root["embeddings"] as? JsonArray).orEmpty().map { e ->
            ((e as? JsonObject)?.get("values") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat() }.toFloatArray()
        }
    }

    /**
     * What goes to the model as the past: the index of every exchange, the newest ones (all of them while short),
     * and the older ones [found] for this question that are not among the newest.
     */
    fun history(entries: List<Entry>, found: List<Entry> = emptyList()): String = buildString {
        if (entries.isEmpty()) return@buildString
        val newest = recent(entries)
        appendLine("[สารบัญทุกครั้งที่เคยคุย ใหม่สุดอยู่บน]")
        appendLine(index(entries))
        appendLine()
        appendLine(if (newest.size == entries.size) "[บทสนทนาทั้งหมด]" else "[บทสนทนาล่าสุด]")
        newest.forEach { appendLine(it.text); appendLine() }
        val ids = newest.map { it.id }.toSet()
        val older = found.filter { it.id !in ids }
        if (older.isNotEmpty()) {
            appendLine("[บทสนทนาเก่าที่เกี่ยวกับคำถามนี้]")
            older.forEach { appendLine(it.text); appendLine() }
        }
    }.trim()

    /** Whether the whole log goes to the model, so there is nothing left to search. */
    fun allSent(entries: List<Entry>): Boolean = recent(entries).size == entries.size
}
