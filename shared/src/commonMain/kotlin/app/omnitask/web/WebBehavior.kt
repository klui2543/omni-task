package app.omnitask.web

import app.omnitask.data.TaskLine
import app.omnitask.model.Behavior
import app.omnitask.model.Learned
import app.omnitask.model.Profile
import app.omnitask.model.QuickAdd
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The behavior log for the web ([Behavior]): events made from a task line, and the file's text with the events this
 * device kept. Everything crosses as text: an event is one JSON line, "" when there is nothing to note.
 */
object WebBehavior {

    const val SRC = "web"

    private val json = Json { ignoreUnknownKeys = true }

    /** What else an event needs: the date moved ([field] and [to]) or the minutes a reminder was put off. */
    @Serializable
    data class Extra(val field: String? = null, val to: String? = null, val min: Int? = null)

    /** An event [ev] about the task line [raw] at [at] (local `yyyy-MM-ddTHH:mm:ss`). */
    fun event(ev: String, raw: String, at: String, extra: String?): String {
        val task = TaskLine.parse(raw) ?: return ""
        val time = LocalDateTime.parse(at)
        val x = extra?.takeIf { it.isNotBlank() }?.let { json.decodeFromString(Extra.serializer(), it) } ?: Extra()
        val e = if (ev == Behavior.MOVE) {
            Behavior.moved(task, x.field ?: return "", x.to?.let { LocalDate.parse(it) }, time, SRC)
        } else {
            Behavior.of(ev, task, time, SRC, min = x.min)
        }
        return e?.let { Behavior.line(it) }.orEmpty()
    }

    /** The task the quick-add [sentence] makes, as a "create" event. */
    fun created(sentence: String, at: String): String {
        val time = LocalDateTime.parse(at)
        val draft = QuickAdd.parse(sentence, time.date)
        if (draft.title.isBlank()) return ""
        val task = TaskLine.parse(draft.line(time.date)) ?: return ""
        return Behavior.line(Behavior.of(Behavior.CREATE, task, time, SRC))
    }

    fun declined(ask: String, at: String): String = Behavior.line(Behavior.declined(ask, LocalDateTime.parse(at), SRC))

    /** The log this device kept before the file (`at|KIND` entries, as a JSON list) as event lines. */
    fun legacy(entries: String): String =
        json.decodeFromString<List<String>>(entries).mapNotNull { Behavior.legacyDone(it, SRC) }.joinToString("\n") { Behavior.line(it) }

    @Serializable
    data class HintOut(val label: String, val words: String)

    /**
     * What tasks like the quick-add [sentence] usually came with ([Learned.hints]), as chips that add their words to
     * the sentence. [behavior] is the log's text, [profile] the profile note's.
     */
    fun hints(sentence: String, today: String, behavior: String?, profile: String?): String {
        val day = LocalDate.parse(today)
        val hints = Learned.hints(QuickAdd.parse(sentence, day), Behavior.parse(behavior), Profile.parse(profile), day)
        return json.encodeToString(ListSerializer(HintOut.serializer()), hints.map { HintOut(it.label, it.words) })
    }

    /** The file's text (null when there is none yet) with the event [lines] added. */
    fun merge(text: String?, lines: String, today: String): String =
        Behavior.merge(text, Behavior.parse(lines), LocalDate.parse(today))
}
