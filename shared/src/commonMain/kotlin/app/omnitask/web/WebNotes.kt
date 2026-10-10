package app.omnitask.web

import app.omnitask.data.Archive
import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.model.OmniList
import app.omnitask.model.Task
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Many notes read as one, as Android reads every note of the vault. The page builders take one note as
 * (fileKey, path, text); when the key is [MANY], the text is instead a JSON list of [Note]s and their tasks are
 * read together, each keyed by its own note, so an edit goes back to the note the task came from.
 */
object WebNotes {

    /** The file key that says the text is a JSON list of notes rather than one note's text. */
    const val MANY = "@notes"

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Serializable
    data class Note(val key: String, val path: String, val text: String)

    /** Whether the note at [path] is read for tasks: not an archive and not a sync app's conflict copy. */
    fun isRead(path: String): Boolean = !Archive.isArchive(path) && !VaultText.isConflictCopy(path)

    /**
     * The tasks of [text]: one note's, or with [fileKey] [MANY] those of every note in the list. Archives, conflict
     * copies and list notes (whose items belong to their list, as on Android) are left out of a list of notes.
     */
    fun tasks(fileKey: String, path: String, text: String): List<Task> {
        if (fileKey != MANY) return VaultText.parseFile(fileKey, path, text)
        return json.decodeFromString<List<Note>>(text)
            .filter { isRead(it.path) && OmniList.parse(it.path, it.text) == null }
            .flatMap { VaultText.parseFile(it.key, it.path, it.text) }
    }

    /** A task as an edit finds it again: its note, its line as read and where that line was. */
    @Serializable
    data class Ref(val key: String, val raw: String, val lineIndex: Int)

    @Serializable
    data class ManyOut(val ok: Boolean, val texts: Map<String, String> = emptyMap(), val error: String? = null, val changed: Int = 0)

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    /**
     * "Do in order" across notes: as [WebProjects.chain], but the tasks may sit in different notes, so the
     * answer is the new text of each note that changed, by its key. [notesJson] holds the notes the tasks are in,
     * as read right now; a task whose line is gone makes the whole change a conflict.
     */
    fun chain(notesJson: String, orderedJson: String, on: Boolean): String {
        val notes = json.decodeFromString<List<Note>>(notesJson).associateBy { it.key }
        val ordered = json.decodeFromString<List<Ref>>(orderedJson)
        val lines = notes.mapValues { (_, n) -> n.text.split(VaultText.separatorOf(n.text)).toMutableList() }
        val parsed = notes.mapValues { (_, n) -> VaultText.parseFile("", "", n.text).associateBy { it.lineIndex } }
        val at = ordered.map { r ->
            val ls = lines[r.key] ?: return fail("conflict")
            val i = if (ls.getOrNull(r.lineIndex) == r.raw) r.lineIndex else ls.indexOf(r.raw)
            if (i < 0) return fail("conflict")
            i
        }
        val tasks = ordered.mapIndexed { k, r -> parsed[r.key]?.get(at[k]) ?: return fail("conflict") }
        val taken = parsed.values.flatMap { m -> m.values.mapNotNull { it.id } }.toMutableSet()
        val ids = tasks.map { t ->
            t.id ?: run {
                var id: String
                do { id = "o" + (1..5).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("") } while (id in taken)
                taken += id
                id
            }
        }
        val own = ids.toSet()
        val touched = LinkedHashSet<String>()
        tasks.forEachIndexed { k, t ->
            val outside = t.dependsOn.filter { it !in own }
            val deps = if (on && k > 0) outside + ids[k - 1] else outside
            val ls = lines.getValue(ordered[k].key)
            ls[at[k]] = TaskLine.setDependsOn(if (on) TaskLine.setId(ls[at[k]], ids[k]) else ls[at[k]], deps)
            touched += ordered[k].key
        }
        val texts = touched.associateWith { key -> lines.getValue(key).joinToString(VaultText.separatorOf(notes.getValue(key).text)) }
            .filter { (key, text) -> text != notes.getValue(key).text }
        return json.encodeToString(ManyOut(true, texts, changed = tasks.size))
    }

    private fun fail(error: String) = json.encodeToString(ManyOut(false, error = error))
}
