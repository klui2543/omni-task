package app.omnitask.model

import app.omnitask.data.VaultText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import app.omnitask.time.*

/**
 * One thing the owner did that tells how they work: a task added, ticked, moved to another day, a reminder put off,
 * swiped away or opened, a question of the assistant answered "no". [at] is the device's own time, `yyyy-MM-ddTHH:mm:ss`.
 * The task's title, kind, tags, dates and reminder are copied as they were at that moment.
 */
@Serializable
data class BehaviorEvent(
    val id: String,
    val at: String,
    val ev: String,
    val src: String? = null,
    val title: String? = null,
    val kind: String? = null,
    val tags: List<String> = emptyList(),
    val due: String? = null,
    val scheduled: String? = null,
    val remind: String? = null,
    /** For a move: which date ("DUE", "SCHEDULED", "START"), and the day before and after (null when cleared or set). */
    val field: String? = null,
    val from: String? = null,
    val to: String? = null,
    /** For a snooze: how many minutes the reminder was put off. */
    val min: Int? = null,
    /** For a "no" to the assistant: the question's id. */
    val ask: String? = null,
) {
    val time: LocalDateTime? get() = runCatching { LocalDateTime.parse(at) }.getOrNull()
}

/**
 * The log every device adds to, so the assistant learns from the phone, the watch and the web alike: one file in the
 * Omni folder, `omni-behavior.json`. Each line is one event as JSON (the first line names the file), so the file is
 * never rewritten, only added to: two devices adding at once join line by line ([app.omnitask.drive.Merge3] keeps
 * both sides' new lines), and an event seen twice (same [BehaviorEvent.id]) counts once.
 */
object Behavior {
    const val NAME = "omni-behavior.json"
    const val PATH = "${VaultText.OMNI_DIR}/$NAME"

    const val CREATE = "create"
    const val DONE = "done"
    const val MOVE = "move"
    const val SNOOZE = "snooze"
    const val DISMISS = "dismiss"
    const val OPEN = "open"
    const val DECLINE = "decline"

    const val HEADER = """{"omni":"behavior","version":1}"""

    /** Events older than this many days are let go, once the oldest is [PRUNE_DAYS] old (so the file is cut about once a month, not every day). */
    const val KEEP_DAYS = 90
    const val PRUNE_DAYS = 120

    /** At most this many events are kept, the oldest going first. */
    const val MAX_EVENTS = 6000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    /** The events of a file (or of lines kept on a device), in the order they were added, each id once. */
    fun parse(text: String?): List<BehaviorEvent> = entries(text).map { it.second }

    private fun entries(text: String?): List<Pair<String, BehaviorEvent>> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        return text.lines().mapNotNull { raw ->
            // A comma at the end is let through, should the file ever be written as one JSON list.
            val line = raw.trim().removeSuffix(",")
            if (!line.startsWith("{")) return@mapNotNull null
            val e = runCatching { json.decodeFromString(BehaviorEvent.serializer(), line) }.getOrNull() ?: return@mapNotNull null
            if (e.id.isBlank() || e.ev.isBlank() || e.time == null || !seen.add(e.id)) null else line to e
        }
    }

    fun line(e: BehaviorEvent): String = json.encodeToString(BehaviorEvent.serializer(), e)

    /**
     * The file's text with [add] in it. Lines already there keep their place and their exact text (a device that
     * added offline then joins cleanly), new events go at the end. Once the oldest event is [PRUNE_DAYS] old, those
     * older than [KEEP_DAYS] are let go. Returns [text] itself when nothing changes.
     */
    fun merge(text: String?, add: List<BehaviorEvent>, today: LocalDate): String {
        val kept = entries(text)
        val known = kept.mapTo(HashSet()) { it.second.id }
        val fresh = add.filter { it.time != null && known.add(it.id) }.map { line(it) to it }
        var all = kept + fresh
        val oldest = all.mapNotNull { it.second.time?.date }.minOrNull()
        if (oldest != null && oldest < today.minusDays(PRUNE_DAYS.toLong())) {
            val cutoff = today.minusDays(KEEP_DAYS.toLong())
            all = all.filter { (it.second.time?.date ?: today) >= cutoff }
        }
        if (all.size > MAX_EVENTS) all = all.drop(all.size - MAX_EVENTS)
        val out = (listOf(HEADER) + all.map { it.first }).joinToString("\n", postfix = "\n")
        return if (text != null && fresh.isEmpty() && all.size == kept.size && text.trimEnd() == out.trimEnd()) text else out
    }

    /**
     * The file's text without the events [ids]: a tick taken back with "undo" was never done, so the assistant must not
     * learn from it. Other lines keep their exact text. Returns [text] itself when none of them is there.
     */
    fun drop(text: String?, ids: Set<String>): String? {
        text ?: return null
        val kept = entries(text)
        val left = kept.filter { it.second.id !in ids }
        if (left.size == kept.size) return text
        return (listOf(HEADER) + left.map { it.first }).joinToString("\n", postfix = "\n")
    }

    // ---- Making events ----

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    /** [at] as `yyyy-MM-ddTHH:mm:ss`, seconds always written. */
    fun stamp(at: LocalDateTime): String = "${at.date}T${pad(at.hour)}:${pad(at.minute)}:${pad(at.second)}"

    /** A fresh id: the device, the moment and a few random letters, so two devices never make the same one. */
    fun newId(src: String, at: LocalDateTime, random: Random = Random): String =
        "$src-${stamp(at).filter { it.isDigit() }}-${random.nextInt(36 * 36 * 36 * 36).toString(36).padStart(4, '0')}"

    /** An event about [task]: what it was called, its kind, tags, dates and reminder at [at]. */
    fun of(
        ev: String, task: Task, at: LocalDateTime, src: String,
        field: String? = null, from: LocalDate? = null, to: LocalDate? = null, min: Int? = null,
        id: String = newId(src, at),
    ) = BehaviorEvent(
        id = id, at = stamp(at), ev = ev, src = src,
        title = task.title.take(120), kind = Planner.kindOf(task.title).name, tags = task.tags,
        due = task.due?.toString(), scheduled = task.scheduled?.toString(), remind = task.reminderTime?.let { Profile.hm(it) },
        field = field, from = from?.toString(), to = to?.toString(), min = min,
    )

    /**
     * A move of one of [task]'s dates from what it has to [to]; null when the date stays, or the task is closed.
     * [field] is "DUE", "SCHEDULED" or "START".
     */
    fun moved(task: Task, field: String, to: LocalDate?, at: LocalDateTime, src: String): BehaviorEvent? {
        if (!task.isOpen) return null
        val from = when (field) {
            "DUE" -> task.due
            "SCHEDULED" -> task.scheduled
            "START" -> task.start
            else -> return null
        }
        return if (from == to) null else of(MOVE, task, at, src, field = field, from = from, to = to)
    }

    /** A "no" to the assistant's question [ask]; the same id on every device, so a no said twice counts once. */
    fun declined(ask: String, at: LocalDateTime, src: String) =
        BehaviorEvent(id = "decline:$ask", at = stamp(at), ev = DECLINE, src = src, ask = ask)

    /**
     * An entry of the log kept on a device before this file (`at|KIND`) as an event. Its id comes from the entry
     * itself, so the same entry brought in from two devices counts once.
     */
    fun legacyDone(entry: String, src: String): BehaviorEvent? {
        val at = runCatching { LocalDateTime.parse(entry.substringBefore('|')) }.getOrNull() ?: return null
        val kind = Planner.Kind.entries.firstOrNull { it.name == entry.substringAfter('|') } ?: return null
        return BehaviorEvent(id = "done:${stamp(at)}:${kind.name}", at = stamp(at), ev = DONE, src = src, kind = kind.name)
    }

    // ---- Reading them ----

    /** When work got done and what kind it was, for [Insight]. */
    fun doneLog(events: List<BehaviorEvent>): List<Insight.Done> = events.filter { it.ev == DONE }.mapNotNull { e ->
        val at = e.time ?: return@mapNotNull null
        val kind = Planner.Kind.entries.firstOrNull { it.name == e.kind } ?: return@mapNotNull null
        Insight.Done(at, kind)
    }

    /** The assistant's questions answered "no" on any device. */
    fun declined(events: List<BehaviorEvent>): Set<String> = events.filter { it.ev == DECLINE }.mapNotNullTo(HashSet()) { it.ask }
}
