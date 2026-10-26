package app.omnitask.model

import app.omnitask.data.VaultText
import kotlinx.datetime.LocalDate
import kotlin.math.ln
import kotlin.math.min

/**
 * The secretary that learns from examples (owner, 2026-10-26; plan in docs/PLAN_assistant_ml_2026-10-26.md, stage 1).
 *
 * The owner teaches by highlighting a typed sentence: which part is the command, and which parts are the task's name,
 * day, time, urgency or project. Each taught sentence is an example, kept one a line in the vault note
 * "Assistant ML.md" (the owner can read and change it in Obsidian). The phone learns from all of them at once, with no
 * network and no cost: a new sentence is compared, piece by piece of its letters, with every example, and the closest
 * ones say what it most likely asks and how sure that is.
 *
 * Why letters and not words: Thai has no spaces between words, so pieces of two and three letters match "งานด่วน"
 * inside "มีงานอะไรด่วนบ้าง" without cutting words first. Why examples and not a trained network: there are tens of
 * them, a new one counts the moment it is taught, and the answer can say which taught sentence it came from.
 */
object AssistantMl {
    const val PATH = "${VaultText.OMNI_DIR}/Assistant ML.md"
    private const val SECTION = "ตัวอย่าง"

    /** What a highlighted part is. The day and time are read by the app's own readers when the example is used. */
    enum class Label(val th: String, val en: String) {
        COMMAND("คำสั่ง", "Command"),
        TITLE("ชื่องาน", "Task name"),
        DATE("วันที่", "Date"),
        TIME("เวลา", "Time"),
        PRIORITY("ความเร่งด่วน", "Urgency"),
        PROJECT("โปรเจกต์", "Project"),
        ;

        val label get() = tr(th, en)

        /** What the task is (its name, day and time), not what is asked: left out when learning what a sentence asks. */
        val content get() = this == TITLE || this == DATE || this == TIME

        companion object {
            fun of(text: String): Label? = text.trim().let { t -> entries.firstOrNull { it.th == t || it.en.equals(t, ignoreCase = true) } }
        }
    }

    /** Urgency levels a highlighted word can mean, as the note writes them after "=". */
    val LEVELS = listOf("สูงสุด", "สูง", "กลาง", "ต่ำ")

    /** A part of a sentence: plain ([label] null) or highlighted. [value] is the urgency level or the project's name. */
    data class Span(val text: String, val label: Label? = null, val value: String? = null) {
        fun render(): String = if (label == null) text else "{" + text + "|" + label.th + (value?.let { "=$it" } ?: "") + "}"
    }

    /** What a sentence asks the app to do. */
    sealed interface Intent {
        /** Open quick add with the task filled in; the owner checks it and adds it (nothing is added on a guess). */
        data object AddTask : Intent
        /** Find free time for it, as the planner does. */
        data object FindTime : Intent
        /** A command of the app, a list of work by conditions, or Claude: what a bubble or a taught word does. */
        data class Do(val action: SecretaryGuide.Action) : Intent

        /** As the note writes it after the arrow. */
        fun render(): String = when (this) {
            AddTask -> "เพิ่มงาน"
            FindTime -> "หาเวลา"
            is Do -> action.render()
        }

        /** In a few words for a button. */
        fun describe(): String = when (this) {
            AddTask -> tr("เพิ่มงาน", "Add a task")
            FindTime -> tr("หาเวลาว่าง", "Find free time")
            is Do -> action.describe()
        }

        companion object {
            fun parse(text: String): Intent? {
                val t = text.trim()
                return when {
                    t.isEmpty() -> null
                    t == "เพิ่มงาน" || t.equals("add task", ignoreCase = true) -> AddTask
                    t == "หาเวลา" || t.equals("find time", ignoreCase = true) -> FindTime
                    else -> SecretaryGuide.Action.parse(t)?.let { Do(it) }
                }
            }
        }
    }

    /** One taught sentence. [source] says where it came from (สอนในแอป, หมายถึงอันนี้ไหม, Haiku, รอบเลขา). */
    data class Example(val spans: List<Span>, val intent: Intent, val date: LocalDate? = null, val source: String = "") {
        val text get() = spans.joinToString("") { it.text }

        fun render(): String = "- " + spans.joinToString("") { it.render() }.trim() + " → " + intent.render() +
            (date?.let { " ($it, จาก $source)" } ?: "")
    }

    // ---- The note ----

    private val TAIL = Regex("""\s*\((\d{4}-\d{2}-\d{2}),\s*จาก\s*([^)]*)\)\s*$""")
    private val MARK = Regex("""\{([^{}|]+)\|([^{}=]+)(?:=([^{}]*))?\}""")

    /** A line of the note as an example; null when it is not one (it is then kept as written). */
    fun parseLine(line: String): Example? {
        val t = line.trim()
        if (!t.startsWith("- ")) return null
        var body = t.removePrefix("- ")
        var date: LocalDate? = null
        var source = ""
        TAIL.find(body)?.let { m ->
            date = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()
            source = m.groupValues[2].trim()
            body = body.substring(0, m.range.first)
        }
        val arrow = body.indexOf(" → ").takeIf { it >= 0 } ?: return null
        val intent = Intent.parse(body.substring(arrow + 3)) ?: return null
        val marked = body.substring(0, arrow)
        val spans = mutableListOf<Span>()
        var i = 0
        MARK.findAll(marked).forEach { m ->
            if (m.range.first > i) spans += Span(marked.substring(i, m.range.first))
            val label = Label.of(m.groupValues[2])
            spans += if (label == null) Span(m.groupValues[1]) else Span(m.groupValues[1], label, m.groupValues[3].trim().ifEmpty { null })
            i = m.range.last + 1
        }
        if (i < marked.length) spans += Span(marked.substring(i))
        if (spans.all { it.text.isBlank() }) return null
        return Example(spans, intent, date, source)
    }

    /** The examples in the note's "## ตัวอย่าง" section, in order (a later one wins over an earlier one with the same words). */
    fun examples(note: String?): List<Example> = section(note).mapNotNull(::parseLine)

    private fun section(note: String?): List<String> {
        val lines = note?.split("\n") ?: return emptyList()
        val h = lines.indexOfFirst { it.trim() == "## $SECTION" }
        if (h < 0) return emptyList()
        return lines.drop(h + 1).takeWhile { !it.startsWith("## ") }
    }

    private val DEFAULT_NOTE = """
        # Assistant ML
        ประโยคที่สอนเลขาในแอป Omni บรรทัดละประโยค แอปเอาไปฝึกให้เข้าใจประโยคใหม่ที่คล้ายกัน Klui อ่านและแก้ได้ใน Obsidian
        ส่วนที่ไฮไลต์อยู่ในวงเล็บปีกกา {ข้อความ|ป้าย} หรือ {ข้อความ|ป้าย=ค่า} ตามด้วยลูกศรและสิ่งที่ให้ทำ
    """.trimIndent()

    /** The note with [e] added at the end of its examples; everything else is kept as written. */
    fun add(note: String?, e: Example): String {
        val base = note?.takeIf { it.isNotBlank() } ?: DEFAULT_NOTE
        val lines = base.trimEnd('\n').split("\n").toMutableList()
        val h = lines.indexOfFirst { it.trim() == "## $SECTION" }
        if (h < 0) {
            lines += ""
            lines += "## $SECTION"
            lines += e.render()
        } else {
            var end = h + 1
            while (end < lines.size && !lines[end].startsWith("## ")) end++
            var at = end
            while (at > h + 1 && lines[at - 1].isBlank()) at--
            lines.add(at, e.render())
        }
        return lines.joinToString("\n") + "\n"
    }

    // ---- Guesses the teach page starts from ----

    private const val THAI_DAYS = "จันทร์|อังคาร|พุธ|พฤหัสบดี|พฤหัส|ศุกร์|เสาร์|อาทิตย์"
    private const val THAI_NUMS = "\\d{1,2}|หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า|สิบเอ็ด|สิบสอง|สิบ"
    private val DATE_GUESS = Regex(
        "มะรืนนี้|มะรืน|พรุ่งนี้|วันนี้|คืนนี้|เย็นนี้|เช้านี้|บ่ายนี้|สิ้นเดือน|(?:สัปดาห์|อาทิตย์|เดือน)(?:นี้|หน้า)|" +
            "วัน(?:$THAI_DAYS)(?:นี้|หน้า)?|(?<![\\d/])\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?(?![\\d/])",
    )
    private val TIME_GUESS = Regex(
        "(?<![\\d:.])(?:[01]?\\d|2[0-3])[.:][0-5]\\d(?:\\s*น\\.)?|เที่ยงคืน|เที่ยง|บ่าย\\s*(?:$THAI_NUMS)?\\s*โมง(?:ครึ่ง)?|" +
            "(?:$THAI_NUMS)\\s*โมง(?:\\s*(?:เช้า|เย็น))?(?:ครึ่ง)?|(?:$THAI_NUMS)\\s*ทุ่ม(?:ครึ่ง)?",
    )

    /**
     * Where a day or a time seems to be in [text], for the teach page to highlight first (dashed, for the owner to keep
     * or change). Only a first guess: the day and time are read by quick add's own reader when the task is added.
     */
    fun guessLabels(text: String): List<Pair<IntRange, Label>> =
        (DATE_GUESS.findAll(text).map { it.range to Label.DATE } + TIME_GUESS.findAll(text).map { it.range to Label.TIME })
            .sortedBy { it.first.first }.toList()

    // ---- Privacy ----

    private val LONG_NUMBER = Regex("""\d{6,}""")
    private val ID_WORDS = Regex("""(?<![a-z])hn(?![a-z])|เลขบัตร|บัตรประชาชน|เบอร์โทร""", RegexOption.IGNORE_CASE)

    /**
     * A sentence that may hold a patient's number (HN), an ID card number or a phone number: never kept as an example,
     * since the owner's rule keeps them out of the vault. Six digits in a row, spaces and dashes aside, or the words.
     */
    fun sensitive(text: String): Boolean = LONG_NUMBER.containsMatchIn(text.replace(Regex("""[\s-]"""), "")) || ID_WORDS.containsMatchIn(text)

    // ---- Learning and guessing ----

    enum class Level { HIGH, MEDIUM, LOW }

    /** What a sentence may ask: [near] is the example it is closest to; [score] from 0 to 1. */
    data class Guess(val intent: Intent, val score: Double, val near: Example)

    /** The guesses, best first (one for each thing it may ask), and how sure the best one is. */
    data class Reading(val level: Level, val guesses: List<Guess>) {
        val best get() = guesses.firstOrNull()
    }

    /** At or above this, with [MARGIN] over the next guess, the app is sure. Starting values, to be tuned from use. */
    const val SURE = 0.75
    const val MARGIN = 0.2
    /** At or above this, the app asks "หมายถึงอันนี้ไหม". */
    const val MAYBE = 0.4
    /** The app's own commands, bubbles and taught words are hints, not taught sentences: they never make the app sure. */
    private const val SEED_WEIGHT = 0.7

    /** Pieces of two and three letters of [text], spaces and case aside. */
    fun grams(text: String): Set<String> {
        val t = SecretaryGuide.squash(text)
        if (t.length < 2) return if (t.isEmpty()) emptySet() else setOf(t)
        return buildSet { for (n in 2..3) for (i in 0..t.length - n) add(t.substring(i, i + n)) }
    }

    /**
     * What an example teaches about asking: the command counts double, so do urgency and project (they change what is
     * shown), the plain words count once, and the task's own name, day and time are left out (they change every time).
     */
    private fun features(e: Example): Map<String, Double> {
        val out = HashMap<String, Double>()
        // Plain words run on between highlights, so they are read as one stretch; a highlight breaks the stretch.
        val plain = StringBuilder()
        fun flush() { grams(plain.toString()).forEach { g -> out[g] = maxOf(out[g] ?: 0.0, 1.0) }; plain.clear() }
        e.spans.forEach { s ->
            when {
                s.label == null -> plain.append(s.text)
                s.label.content -> flush()
                else -> { flush(); grams(s.text).forEach { g -> out[g] = 2.0 } }
            }
        }
        flush()
        // An example that is all name, day and time still says something: its whole text, once.
        if (out.isEmpty()) grams(e.text).forEach { out[it] = 1.0 }
        return out
    }

    /** Examples the app starts from without being taught: its own commands, the owner's bubbles and taught words. */
    fun seeds(guide: SecretaryGuide.Guide): List<Example> = buildList {
        SecretaryGuide.Builtin.entries.forEach { b ->
            (listOf(b.th, b.en) + b.aliases).forEach { add(Example(listOf(Span(it)), Intent.Do(SecretaryGuide.Action.App(b)))) }
        }
        guide.bubbles.forEach { add(Example(listOf(Span(it.label)), Intent.Do(it.action))) }
        guide.words.forEach { add(Example(listOf(Span(it.word)), Intent.Do(it.action))) }
    }

    /** All the examples, learned at once. Taught examples with the same words as an earlier one replace it. */
    class Model(taught: List<Example>, seeds: List<Example> = emptyList()) {
        private class Item(val example: Example, val features: Map<String, Double>, val seed: Boolean)

        private val items: List<Item>
        private val idf: Map<String, Double>

        init {
            val last = taught.withIndex().associateBy({ SecretaryGuide.squash(it.value.text) }, { it.index })
            val kept = taught.filterIndexed { i, e -> last[SecretaryGuide.squash(e.text)] == i }
            items = kept.map { Item(it, features(it), false) } + seeds.map { Item(it, features(it), true) }
            val df = HashMap<String, Int>()
            items.forEach { it.features.keys.forEach { g -> df[g] = (df[g] ?: 0) + 1 } }
            idf = df.mapValues { (_, n) -> ln(1.0 + items.size.toDouble() / n) }
        }

        /** The command words taught for adding a task or finding time, longest first: taken out of the task's name. */
        val commandWords: List<String> = taught.filter { it.intent == Intent.AddTask || it.intent == Intent.FindTime }
            .flatMap { e -> e.spans.filter { it.label == Label.COMMAND }.map { it.text.trim() } }
            .filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }

        /**
         * How much of what [item] teaches is in [q]: the share of its pieces found, weighted by how rare each piece is.
         * A short example (a few pieces) is trusted less, so "ต่อ" inside "ติดต่อ" does not make the app sure.
         */
        private fun score(item: Item, q: Set<String>): Double {
            var num = 0.0
            var den = 0.0
            item.features.forEach { (g, w) ->
                val x = w * (idf[g] ?: 0.0)
                den += x
                if (g in q) num += x
            }
            if (den == 0.0) return 0.0
            val short = min(1.0, item.features.size / 6.0)
            return num / den * short * if (item.seed) SEED_WEIGHT else 1.0
        }

        fun read(text: String): Reading {
            val q = grams(text)
            if (q.isEmpty() || items.isEmpty()) return Reading(Level.LOW, emptyList())
            val best = items.map { it to score(it, q) }.filter { it.second > 0.0 }
                .groupBy { it.first.example.intent.render() }
                .map { (_, list) -> list.maxBy { it.second }.let { (item, s) -> Guess(item.example.intent, s, item.example) } }
                .sortedByDescending { it.score }
                .take(3)
            val top = best.firstOrNull()?.score ?: 0.0
            val second = best.getOrNull(1)?.score ?: 0.0
            val level = when {
                top >= SURE && top - second >= MARGIN -> Level.HIGH
                top >= MAYBE -> Level.MEDIUM
                else -> Level.LOW
            }
            return Reading(level, best)
        }

        /** [text] without the command words taught for adding a task, for quick add to read the rest. */
        fun taskText(text: String): String {
            val w = commandWords.firstOrNull { text.contains(it, ignoreCase = true) } ?: return text.trim()
            val i = text.indexOf(w, ignoreCase = true)
            return (text.substring(0, i) + " " + text.substring(i + w.length)).replace(Regex("""\s+"""), " ").trim()
        }

        /**
         * The highlights for a sentence the owner picked [intent] for in "หมายถึงอันนี้ไหม", so it is kept as an example:
         * for a task, the taught command words, the day and time guessed, and the rest as its name; for anything else, the
         * sentence as it is (its words are what asks).
         */
        fun spansFor(text: String, intent: Intent): List<Span> {
            if (intent !is Intent.AddTask && intent !is Intent.FindTime) return listOf(Span(text.trim()))
            val marks = mutableListOf<Pair<IntRange, Label>>()
            commandWords.firstOrNull { text.contains(it, ignoreCase = true) }?.let { w ->
                val i = text.indexOf(w, ignoreCase = true)
                marks += (i until i + w.length) to Label.COMMAND
            }
            guessLabels(text).forEach { (r, l) -> if (marks.none { it.first.first <= r.last && r.first <= it.first.last }) marks += r to l }
            marks.sortBy { it.first.first }
            val out = mutableListOf<Span>()
            var i = 0
            fun rest(until: Int) {
                val s = text.substring(i, until)
                if (s.isBlank()) { if (s.isNotEmpty()) out += Span(s); return }
                val lead = s.takeWhile { it.isWhitespace() }
                val trail = s.takeLastWhile { it.isWhitespace() }
                if (lead.isNotEmpty()) out += Span(lead)
                out += Span(s.trim(), Label.TITLE)
                if (trail.isNotEmpty()) out += Span(trail)
            }
            marks.forEach { (r, l) ->
                if (r.first > i) rest(r.first)
                out += Span(text.substring(r.first, r.last + 1), l)
                i = r.last + 1
            }
            if (i < text.length) rest(text.length)
            return out
        }
    }
}
