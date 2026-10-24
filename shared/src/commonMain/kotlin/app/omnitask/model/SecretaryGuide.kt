package app.omnitask.model

import app.omnitask.data.VaultText
import app.omnitask.time.*
import kotlinx.datetime.LocalDate

/**
 * The secretary's guide, kept in the vault as "เลขา คู่มือ.md" so the owner can change it in Obsidian or in the app
 * and the app follows at the next question, with no update: the bubbles above the chat in the owner's order (and the
 * hidden ones), the words the app should understand when typed, how answers are cut and ranked, and what the
 * secretary (Claude, every few hours) learned and proposes. The app writes only the first three sections; the
 * learned lines are the secretary's, and everything else in the note is kept as written.
 */
object SecretaryGuide {
    const val PATH = "${VaultText.OMNI_DIR}/เลขา คู่มือ.md"

    private const val H_BUBBLES = "บอลลูน"
    private const val H_HIDDEN = "ซ่อนไว้"
    private const val H_WORDS = "คำที่พิมพ์"
    private const val H_STYLE = "วิธีตอบ"
    private const val H_LEARNED = "สิ่งที่เรียนรู้"

    /** What the app can do by itself, named as the note writes it after "ในแอป:". */
    enum class Builtin(val th: String, val en: String, val aliases: List<String> = emptyList()) {
        TODAY("วันนี้", "Today", listOf("วันนี้มีอะไร")),
        TOMORROW("พรุ่งนี้", "Tomorrow", listOf("พรุ่งนี้มีอะไร")),
        WEEK("สัปดาห์นี้", "This week", listOf("7 วัน")),
        MONTH("เดือนหน้า", "Next month", listOf("เดือนหน้ามีอะไร")),
        NEXT("ตอนนี้ทำอะไรดี", "What now", listOf("ทำอะไรดี")),
        LIGHT("งานเบา", "Something light", listOf("เหนื่อย ของานเบา")),
        LATE("งานค้าง", "Left over", listOf("เสนอวันใหม่")),
        SPLIT("แตกงาน", "Split a task", listOf("งานใหญ่ ไม่รู้เริ่มตรงไหน")),
        RANK_TODAY("จัดลำดับวันนี้", "Order today"),
        RANK_ALL("จัดลำดับทั้งหมด", "Order everything"),
        MORE("ต่อ", "More", listOf("ชุดถัดไป")),
        ;

        val label get() = tr(th, en)

        companion object {
            fun of(text: String): Builtin? {
                val t = squash(text)
                if (t.isEmpty()) return null
                return entries.firstOrNull { b -> (listOf(b.th, b.en) + b.aliases).any { squash(it) == t } }
            }
        }
    }

    /** Which open work a bubble shows: the task list's filters, plus "real deadline within N days" and "sat N days". */
    data class Filter(
        val tags: Set<String> = emptySet(),
        val priorities: Set<Priority> = emptySet(),
        val buckets: Set<DateBucket> = emptySet(),
        /** A real deadline (📅) from today to N days ahead; with [buckets] either one is enough. */
        val withinDays: Int? = null,
        /** Created at least N days ago and still open. */
        val staleDays: Int? = null,
        val kinds: Set<TaskKind> = emptySet(),
        /** Empty means open work (to do and in progress). */
        val statuses: Set<Status> = emptySet(),
        val lists: Set<String> = emptySet(),
        val notes: Set<String> = emptySet(),
        val text: String = "",
        val sortBy: SortBy? = null,
    ) {
        val isEmpty get() = this == Filter()

        private fun query() = TaskQuery(
            statuses = statuses.ifEmpty { setOf(Status.TODO, Status.IN_PROGRESS) }, priorities = priorities, tags = tags,
            notes = notes, kinds = kinds, lists = lists, text = text, groupBy = GroupBy.NONE, sortBy = sortBy ?: SortBy.DUE,
        )

        fun matches(t: Task, today: LocalDate): Boolean {
            val dated = (buckets.isEmpty() && withinDays == null) || t.bucket(today) in buckets ||
                (withinDays != null && t.due.let { it != null && it >= today && it <= today.plusDays(withinDays.toLong()) })
            val stale = staleDays == null || (Focus.ageDays(t, today) ?: -1L) >= staleDays
            return dated && stale && query().matches(t, today)
        }

        /** The matching work, sorted ([sortBy], else by date). */
        fun run(tasks: List<Task>, today: LocalDate): List<Task> {
            val picked = tasks.filter { matches(it, today) }
            return query().run(picked, today).flatMap { it.tasks }
        }

        /** As the note writes it: comma-separated, Thai, each part saying what it is. */
        fun render(): String = buildList {
            tags.forEach { add("#$it") }
            buckets.forEach { add("วันที่ ${BUCKET_TH.getValue(it)}") }
            withinDays?.let { add("กำหนดจริงภายใน $it วัน") }
            priorities.forEach { add("ความสำคัญ ${PRIORITY_TH.getValue(it)}") }
            kinds.forEach { add("ประเภท ${KIND_TH.getValue(it)}") }
            statuses.forEach { add("สถานะ ${STATUS_TH.getValue(it)}") }
            lists.forEach { add("List $it") }
            notes.forEach { add("โน้ต $it") }
            staleDays?.let { add("ค้างนาน $it วัน") }
            if (text.isNotBlank()) add("คำ \"${text.trim()}\"")
            sortBy?.let { add("เรียงตาม ${SORT_TH.getValue(it)}") }
        }.joinToString(", ")

        /** In a few words for a row: the same parts as [render], in the app's language. */
        fun describe(): String = buildList {
            tags.forEach { add("#$it") }
            buckets.forEach { add(it.label) }
            withinDays?.let { add(tr("ภายใน $it วัน", "within $it days")) }
            priorities.forEach { add(it.label) }
            kinds.forEach { add(it.label) }
            statuses.forEach { add(it.label) }
            lists.forEach { add(it) }
            notes.forEach { add(it) }
            staleDays?.let { add(tr("ค้าง $it วัน", "$it days old")) }
            if (text.isNotBlank()) add("\"${text.trim()}\"")
        }.joinToString(", ").ifEmpty { tr("งานเปิดทั้งหมด", "All open work") }

        companion object {
            /** Reads what the note says, leniently: labels alone ("เลยกำหนด", "สูง") and "!!" work too. */
            fun parse(text: String): Filter {
                var f = Filter()
                text.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.forEach { raw ->
                    val tok = raw.trim()
                    WITHIN.find(tok)?.let { f = f.copy(withinDays = it.groupValues[1].toInt()); return@forEach }
                    STALE.find(tok)?.let { f = f.copy(staleDays = it.groupValues[1].toInt()); return@forEach }
                    QUOTED.find(tok)?.takeIf { tok.startsWith("คำ") || tok.startsWith("\"") }?.let { f = f.copy(text = it.groupValues[1]); return@forEach }
                    if (tok.startsWith("#")) { tok.removePrefix("#").trim().takeIf { it.isNotEmpty() }?.let { f = f.copy(tags = f.tags + it) }; return@forEach }
                    fun after(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> if (tok.startsWith(k, ignoreCase = true)) tok.substring(k.length).trim() else null }
                    after("วันที่", "date")?.let { v -> lookup(BUCKET_TH, v)?.let { f = f.copy(buckets = f.buckets + it) }; return@forEach }
                    after("ความสำคัญ", "priority")?.let { v -> lookup(PRIORITY_TH, v)?.let { f = f.copy(priorities = f.priorities + it) }; return@forEach }
                    after("ประเภท", "type")?.let { v -> lookup(KIND_TH, v)?.let { f = f.copy(kinds = f.kinds + it) }; return@forEach }
                    after("สถานะ", "status")?.let { v -> lookup(STATUS_TH, v)?.let { f = f.copy(statuses = f.statuses + it) }; return@forEach }
                    after("เรียงตาม", "sort")?.let { v -> lookup(SORT_TH, v)?.let { f = f.copy(sortBy = it) }; return@forEach }
                    after("List ", "list ")?.let { v -> if (v.isNotEmpty()) f = f.copy(lists = f.lists + v); return@forEach }
                    after("โน้ต", "note ")?.let { v -> if (v.isNotEmpty()) f = f.copy(notes = f.notes + v); return@forEach }
                    when (squash(tok)) {
                        "!!!" -> { f = f.copy(priorities = f.priorities + Priority.HIGHEST); return@forEach }
                        "!!" -> { f = f.copy(priorities = f.priorities + Priority.HIGH); return@forEach }
                        "!" -> { f = f.copy(priorities = f.priorities + Priority.MEDIUM); return@forEach }
                        squash("ตาของฉัน"), squash("ฉัน"), "me" -> { f = f.copy(tags = f.tags + ME_TAG); return@forEach }
                        squash("ตาของ Claude"), "claude" -> { f = f.copy(tags = f.tags + CLAUDE_TAG); return@forEach }
                    }
                    lookup(BUCKET_TH, tok)?.let { f = f.copy(buckets = f.buckets + it); return@forEach }
                    lookup(PRIORITY_TH, tok)?.let { f = f.copy(priorities = f.priorities + it); return@forEach }
                    lookup(KIND_TH, tok)?.let { f = f.copy(kinds = f.kinds + it); return@forEach }
                    lookup(STATUS_TH, tok)?.let { f = f.copy(statuses = f.statuses + it); return@forEach }
                    // Anything else ("งานที่", "ด่วน") carries no condition and is left out.
                }
                return f
            }

            /** The filter part of a task-list query, for "save as a bubble". */
            fun of(q: TaskQuery): Filter = Filter(
                tags = q.tags, priorities = q.priorities, buckets = q.buckets, kinds = q.kinds,
                statuses = if (q.statuses == TaskQuery.DEFAULT.statuses) emptySet() else q.statuses,
                lists = q.lists, notes = q.notes, text = q.text, sortBy = q.sortBy.takeIf { it != SortBy.DUE },
            )

            private val WITHIN = Regex("""(?:ภายใน|within)\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
            private val STALE = Regex("""(?:ค้าง(?:นาน|มา)?|older than)\s*(\d{1,4})""", RegexOption.IGNORE_CASE)
            private val QUOTED = Regex("\"([^\"]+)\"")
        }
    }

    /** What a bubble or a word does: something the app knows, a list of work, or handing over to Claude. */
    sealed interface Action {
        data class App(val builtin: Builtin) : Action
        data class Show(val filter: Filter) : Action
        /** To Claude, with [text] as what to ask (empty: the chat as it is). */
        data class Claude(val text: String) : Action

        /** As the note writes it after the arrow. */
        fun render(): String = when (this) {
            is App -> "ในแอป: ${builtin.th}"
            is Show -> "กรอง: ${filter.render()}"
            is Claude -> if (text.isBlank()) "Claude" else "Claude: ${text.trim()}"
        }

        /** In a few words for a row in the app. */
        fun describe(): String = when (this) {
            is App -> tr("ในแอป: ", "In the app: ") + builtin.label
            is Show -> tr("แสดงงาน: ", "Show: ") + filter.describe()
            is Claude -> tr("ส่งให้ Claude", "To Claude") + if (text.isBlank()) "" else ": ${text.trim()}"
        }

        companion object {
            /** Reads an action; null when it says nothing the app can do (the line is then kept as written). */
            fun parse(text: String): Action? {
                val t = text.trim()
                fun after(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> if (t.startsWith(k, ignoreCase = true)) t.substring(k.length).trim().removePrefix(":").trim() else null }
                after("ส่งให้ Claude", "Claude")?.let { return Claude(it) }
                after("กรอง", "แสดงงาน", "filter")?.let { v -> return Filter.parse(v).let { Show(it) } }
                after("ในแอป", "app")?.let { v -> return Builtin.of(v)?.let { App(it) } ?: Filter.parse(v).takeIf { !it.isEmpty }?.let { Show(it) } }
                Builtin.of(t)?.let { return App(it) }
                return Filter.parse(t).takeIf { !it.isEmpty }?.let { Show(it) }
            }
        }
    }

    data class Bubble(val label: String, val action: Action, val on: Boolean = true)

    data class Word(val word: String, val action: Action)

    /** How long a list answer is before "ต่อ". */
    enum class Length(val count: Int, val th: String, val en: String) {
        FIVE(5, "5 งาน", "5 tasks"), TEN(10, "10 งาน", "10 tasks"), ALL(Int.MAX_VALUE, "ทั้งหมด", "All");

        val label get() = tr(th, en)
    }

    /** What comes first when everything is put in order, in the owner's order. */
    enum class Factor(val th: String, val en: String) {
        DUE_LATE("เลยกำหนดจริง", "Real deadline passed"),
        WAITING("มีคนรอ", "Someone waiting"),
        HIGH("ความสำคัญสูง", "High priority"),
        TARGET_LATE("เลยวันนัดทำ", "Own target passed"),
        ;

        val label get() = tr(th, en)

        fun applies(t: Task, today: LocalDate): Boolean = when (this) {
            DUE_LATE -> t.due?.let { it < today } == true
            WAITING -> TaskKind.of(t) == TaskKind.WAITING
            HIGH -> t.priority == Priority.HIGHEST || t.priority == Priority.HIGH
            TARGET_LATE -> t.scheduled?.let { it < today } == true
        }
    }

    /** A change the secretary proposes in the learned section, for the owner to take with one tap. */
    sealed interface Proposal {
        data class AddWord(val word: Word) : Proposal
        data class AddBubble(val bubble: Bubble) : Proposal
        data class HideBubble(val label: String) : Proposal
    }

    /** A learned line as written, and the proposal in it, when it is one the app can apply. */
    data class Learned(val line: String, val proposal: Proposal?) {
        /** The line without its "- ". */
        val text get() = line.trim().removePrefix("-").trim()
    }

    data class Guide(
        /** In the owner's order: the shown ones, then the hidden ones ([Bubble.on] false). */
        val bubbles: List<Bubble>,
        val words: List<Word> = emptyList(),
        val length: Length = Length.TEN,
        val order: List<Factor> = Factor.entries,
        /** Lines handed to Claude every time the chat goes to Claude. */
        val forClaude: List<String> = emptyList(),
        val learned: List<Learned> = emptyList(),
        /** Lines above the first section, kept as written. */
        val head: List<String> = DEFAULT_HEAD,
        /** Lines of a section the app cannot read (a comment, a note), kept as written, by section. */
        val extras: Map<String, List<String>> = DEFAULT_EXTRAS,
        /** Sections the app does not know, kept as written, in order. */
        val others: List<String> = emptyList(),
    ) {
        val shown get() = bubbles.filter { it.on }

        /** The word in [text] the owner taught, the longest first; null when none is in it. */
        fun wordIn(text: String): Word? {
            val t = squash(text)
            return words.filter { squash(it.word).isNotEmpty() && squash(it.word) in t }.maxByOrNull { squash(it.word).length }
        }

        fun render(): String = buildString {
            head.forEach { appendLine(it) }
            fun section(name: String, body: List<String>) {
                if (isNotEmpty() && !endsWith("\n\n")) appendLine()
                appendLine("## $name")
                body.forEach { appendLine(it) }
            }
            section(H_BUBBLES, extras[H_BUBBLES].orEmpty() + shown.mapIndexed { i, b -> "${i + 1}. ${b.label} → ${b.action.render()}" })
            val hidden = bubbles.filter { !it.on }
            if (hidden.isNotEmpty()) {
                appendLine()
                appendLine("### $H_HIDDEN")
                hidden.forEach { appendLine("- ${it.label} → ${it.action.render()}") }
            }
            section(H_WORDS, extras[H_WORDS].orEmpty() + words.map { "- ${it.word} → ${it.action.render()}" })
            section(
                H_STYLE,
                extras[H_STYLE].orEmpty() + listOf(
                    "- รายการ: ${this@Guide.length.th}",
                    "- ลำดับ: ${order.joinToString(", ") { it.th }}",
                ) + forClaude.map { "- บอก Claude: $it" },
            )
            section(H_LEARNED, extras[H_LEARNED].orEmpty() + learned.map { it.line })
            others.forEach { block ->
                if (!endsWith("\n\n")) appendLine()
                appendLine(block.trimEnd())
            }
        }.trimEnd() + "\n"
    }

    private val DEFAULT_HEAD = listOf("# เลขา คู่มือ", "แอป Omni อ่านโน้ตนี้ทุกครั้งที่ถามเลขา แก้ได้ทั้งที่นี่และในแอป")
    private val DEFAULT_EXTRAS = mapOf(
        H_BUBBLES to listOf("เรียงตามลำดับที่อยากเห็น ข้างใต้ ซ่อนไว้ คือที่ไม่โชว์ในแถว"),
        H_WORDS to listOf("ข้อความที่มีคำนี้ แอปทำตามทันที"),
        H_LEARNED to listOf("เลขาของ Claude เขียนส่วนนี้ บรรทัดที่ขึ้นต้นด้วย เสนอ: กดรับได้ในแอป"),
    )

    /** The bubbles before the owner sets any: what the secretary chat offered until now. */
    val DEFAULT_BUBBLES = listOf(
        Bubble("ตอนนี้ทำอะไรดี", Action.App(Builtin.NEXT)),
        Bubble("พรุ่งนี้มีอะไร", Action.App(Builtin.TOMORROW)),
        Bubble("งานค้าง", Action.App(Builtin.LATE)),
        Bubble("สัปดาห์นี้", Action.App(Builtin.WEEK)),
        Bubble("จัดลำดับทั้งหมด", Action.App(Builtin.RANK_ALL)),
        Bubble("งานใหญ่ ไม่รู้เริ่มตรงไหน", Action.App(Builtin.SPLIT)),
        Bubble("วันนี้มีอะไร", Action.App(Builtin.TODAY), on = false),
        Bubble("จัดลำดับวันนี้", Action.App(Builtin.RANK_TODAY), on = false),
        Bubble("เหนื่อย ของานเบา", Action.App(Builtin.LIGHT), on = false),
        Bubble("เดือนหน้ามีอะไร", Action.App(Builtin.MONTH), on = false),
    )

    val DEFAULT = Guide(DEFAULT_BUBBLES)

    /** Reads the note; a missing note, or a missing section, means the defaults for it. */
    fun parse(text: String?): Guide {
        if (text.isNullOrBlank()) return DEFAULT
        val lines = text.replace("\r\n", "\n").lines()
        val head = mutableListOf<String>()
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        lines.forEach { line ->
            if (line.startsWith("## ")) sections += line.removePrefix("## ").trim() to mutableListOf()
            else if (sections.isEmpty()) head += line else sections.last().second += line
        }
        fun body(name: String): List<String>? = sections.firstOrNull { it.first == name }?.second
        val extras = mutableMapOf<String, List<String>>()
        fun keep(name: String, lines: List<String>) { extras[name] = trimBlank(lines) }

        val bubbles: List<Bubble> = body(H_BUBBLES)?.let { b ->
            val list = mutableListOf<Bubble>()
            val left = mutableListOf<String>()
            var hidden = false
            b.forEach { line ->
                val t = line.trim()
                if (t.startsWith("### ")) { hidden = t.removePrefix("### ").trim() == H_HIDDEN; if (!hidden) left += line; return@forEach }
                val item = ITEM.matchEntire(t)?.groupValues?.get(1)
                val bubble = item?.let { pair(it) }?.let { (label, action) -> Bubble(label, action, on = !hidden) }
                if (bubble != null) list += bubble else if (t.isNotEmpty()) left += line
            }
            keep(H_BUBBLES, left)
            list
        } ?: DEFAULT_BUBBLES.also { extras[H_BUBBLES] = DEFAULT_EXTRAS.getValue(H_BUBBLES) }

        val words = body(H_WORDS)?.let { b ->
            val left = mutableListOf<String>()
            val list = b.mapNotNull { line ->
                val item = ITEM.matchEntire(line.trim())?.groupValues?.get(1)
                item?.let { pair(it) }?.let { (w, a) -> Word(w, a) }.also { if (it == null && line.isNotBlank()) left += line }
            }
            keep(H_WORDS, left)
            list
        }.orEmpty().also { if (body(H_WORDS) == null) extras[H_WORDS] = DEFAULT_EXTRAS.getValue(H_WORDS) }

        var length = Length.TEN
        var order = Factor.entries.toList()
        val forClaude = mutableListOf<String>()
        body(H_STYLE)?.let { b ->
            val left = mutableListOf<String>()
            b.forEach { line ->
                val t = line.trim().removePrefix("-").trim()
                val key = t.substringBefore(':').trim()
                val value = t.substringAfter(':', "").trim()
                when {
                    !t.contains(':') -> if (line.isNotBlank()) left += line
                    key == "รายการ" || key == "ความยาว" -> length = Length.entries.firstOrNull { squash(it.th) == squash(value) || value.startsWith(it.count.toString()) }
                        ?: if (value.contains("ทั้งหมด") || value.contains("all", ignoreCase = true)) Length.ALL else length
                    key == "ลำดับ" -> order = value.split(',').mapNotNull { v -> Factor.entries.firstOrNull { squash(it.th) == squash(v) || squash(it.en) == squash(v) } }
                        .distinct().let { it + Factor.entries.filter { f -> f !in it } }
                    key == "บอก Claude" -> if (value.isNotEmpty()) forClaude += value
                    else -> left += line
                }
            }
            keep(H_STYLE, left)
        }

        val learned = body(H_LEARNED)?.let { b ->
            val left = mutableListOf<String>()
            val list = b.mapNotNull { line ->
                if (line.trim().startsWith("- ")) Learned(line.trimEnd(), proposal(line)) else { if (line.isNotBlank()) left += line; null }
            }
            keep(H_LEARNED, left)
            list
        }.orEmpty().also { if (body(H_LEARNED) == null) extras[H_LEARNED] = DEFAULT_EXTRAS.getValue(H_LEARNED) }

        val known = setOf(H_BUBBLES, H_WORDS, H_STYLE, H_LEARNED)
        val others = sections.filter { it.first !in known }.map { (name, body) -> (listOf("## $name") + trimBlank(body)).joinToString("\n") }
        return Guide(
            bubbles = bubbles, words = words, length = length, order = order, forClaude = forClaude, learned = learned,
            head = trimBlank(head).ifEmpty { DEFAULT_HEAD }, extras = extras, others = others,
        )
    }

    /** "label → action", or null when the arrow or the action is missing. */
    private fun pair(text: String): Pair<String, Action>? {
        val m = ARROW.find(text) ?: return null
        val label = text.substring(0, m.range.first).trim().removeSurrounding("\"")
        val action = Action.parse(text.substring(m.range.last + 1)) ?: return null
        return if (label.isEmpty()) null else label to action
    }

    /** The proposal in a learned line: เสนอ: คำ "x" → ..., เสนอ: บอลลูน "x" → ..., เสนอ: ซ่อนบอลลูน "x". */
    fun proposal(line: String): Proposal? {
        val t = line.trim().removePrefix("-").trim()
        val m = PROPOSAL.matchEntire(t) ?: return null
        val (kind, name, rest) = m.destructured
        val actionText = rest.replace(SOURCE, "").trim().removePrefix("→").removePrefix("->").trim()
        return when (kind) {
            "ซ่อนบอลลูน" -> Proposal.HideBubble(name)
            "คำ" -> Action.parse(actionText)?.let { Proposal.AddWord(Word(name, it)) }
            "บอลลูน" -> Action.parse(actionText)?.let { Proposal.AddBubble(Bubble(name, it)) }
            else -> null
        }
    }

    /** [guide] with [p] applied, and the learned line it came from taken out. */
    fun accept(guide: Guide, learned: Learned): Guide {
        val p = learned.proposal ?: return guide
        val g = guide.copy(learned = guide.learned.filterNot { it.line == learned.line })
        return when (p) {
            is Proposal.AddWord -> g.copy(words = g.words.filterNot { squash(it.word) == squash(p.word.word) } + p.word)
            is Proposal.AddBubble -> g.copy(bubbles = g.bubbles.filterNot { it.label == p.bubble.label } + p.bubble)
            is Proposal.HideBubble -> g.copy(bubbles = g.bubbles.map { if (it.label == p.label) it.copy(on = false) else it })
        }
    }

    /**
     * What the app proposes by itself, from the taps in สถิติเลขา.md: hiding a shown bubble nobody tapped in the last
     * [days] days, once the taps go back that far.
     */
    fun unusedBubbles(guide: Guide, stats: String?, today: LocalDate, days: Int = 14): List<String> {
        val taps = stats.orEmpty().lines().mapNotNull { TAP.matchEntire(it.trim()) }.mapNotNull { m ->
            runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()?.let { it to m.groupValues[2] }
        }
        val oldest = taps.minOfOrNull { it.first } ?: return emptyList()
        val since = today.minusDays(days.toLong())
        if (oldest > since) return emptyList()
        val recent = taps.filter { it.first > since }.map { it.second }
        return guide.shown.map { it.label }.filter { label -> recent.none { it == label || it.startsWith("$label:") } }
    }

    /** [ranked] put in the owner's [order]: the first factor that tells two tasks apart decides; ties keep [ranked]'s order. */
    fun <T> inOrder(ranked: List<T>, task: (T) -> Task, order: List<Factor>, today: LocalDate): List<T> =
        ranked.withIndex().sortedWith(
            order.fold(Comparator<IndexedValue<T>> { _, _ -> 0 }) { c, f -> c.then(compareBy<IndexedValue<T>> { if (f.applies(task(it.value), today)) 0 else 1 }) }
                .then(compareBy<IndexedValue<T>> { it.index }),
        ).map { it.value }

    private fun trimBlank(lines: List<String>) = lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

    /** Lower case without spaces, so "ตอนนี้ ทำอะไรดี" and "ตอนนี้ทำอะไรดี" are the same words. */
    fun squash(text: String) = text.lowercase().filterNot { it.isWhitespace() }

    private fun <T> lookup(map: Map<T, String>, text: String): T? {
        val t = squash(text)
        if (t.isEmpty()) return null
        return map.entries.firstOrNull { squash(it.value) == t }?.key ?: map.keys.firstOrNull { k ->
            val en = when (k) { is Enum<*> -> k.name; else -> "" }
            squash(en) == t || squash(en.replace('_', ' ')) == t
        } ?: map.keys.firstOrNull { k -> squash(englishLabel(k)) == t }
    }

    private fun englishLabel(k: Any?): String = when (k) {
        is DateBucket -> listOf("Today", "Overdue", "This week", "Next week", "Later", "No date")[k.ordinal]
        is Priority -> listOf("Highest", "High", "Medium", "Normal", "Low", "Lowest")[k.ordinal]
        is TaskKind -> listOf("Normal", "Waiting", "Future", "Someday")[k.ordinal]
        is Status -> listOf("To do", "In progress", "Done", "Cancelled")[k.ordinal]
        is SortBy -> listOf("Due", "Scheduled", "Start", "Priority", "Created", "Status", "Project", "Note", "Title")[k.ordinal]
        else -> ""
    }

    /** Whose turn a task is: the tags the "me" and "Claude" chips stand for. */
    const val ME_TAG = "kluido"
    const val CLAUDE_TAG = "claudedo"

    private val BUCKET_TH = mapOf(
        DateBucket.TODAY to "วันนี้", DateBucket.OVERDUE to "เลยกำหนด", DateBucket.THIS_WEEK to "สัปดาห์นี้",
        DateBucket.NEXT_WEEK to "สัปดาห์หน้า", DateBucket.FUTURE to "อนาคต", DateBucket.NO_DATE to "ไม่มีวันที่",
    )
    private val PRIORITY_TH = mapOf(
        Priority.HIGHEST to "สูงสุด", Priority.HIGH to "สูง", Priority.MEDIUM to "กลาง", Priority.NONE to "ปกติ",
        Priority.LOW to "ต่ำ", Priority.LOWEST to "ต่ำสุด",
    )
    private val KIND_TH = mapOf(TaskKind.NORMAL to "ปกติ", TaskKind.WAITING to "มีคนรอ", TaskKind.FUTURE to "ลงทุนอนาคต", TaskKind.SOMEDAY to "พักไว้ก่อน")
    private val STATUS_TH = mapOf(Status.TODO to "ยังไม่เริ่ม", Status.IN_PROGRESS to "กำลังทำ", Status.DONE to "เสร็จ", Status.CANCELLED to "ยกเลิก")
    private val SORT_TH = mapOf(
        SortBy.DUE to "ครบกำหนด", SortBy.SCHEDULED to "วันนัดทำ", SortBy.START to "วันเริ่ม", SortBy.PRIORITY to "ความสำคัญ",
        SortBy.CREATED to "วันที่สร้าง", SortBy.STATUS to "สถานะ", SortBy.PROJECT to "โปรเจกต์", SortBy.NOTE to "โน้ต", SortBy.TITLE to "ชื่องาน",
    )

    /** A list line: "1. ...", "- ..." or "* ...". */
    private val ITEM = Regex("""(?:\d+[.)]|[-*])\s+(.+)""")
    private val ARROW = Regex("""\s*(?:→|->|=>)\s*""")
    private val PROPOSAL = Regex("""เสนอ\s*:\s*(ซ่อนบอลลูน|บอลลูน|คำ)\s*"([^"]+)"\s*(.*)""")
    /** The "(2026-10-24, จาก ...)" a learned line ends with. */
    private val SOURCE = Regex("""\s*\(\d{4}-\d{2}-\d{2}[^)]*\)\s*$""")
    /** A tap line of สถิติเลขา.md: "- 2026-10-24 08:23 what". */
    private val TAP = Regex("""-\s+(\d{4}-\d{2}-\d{2})\s+\d{1,2}:\d{2}\s+(.+)""")
}
