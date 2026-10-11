@file:OptIn(ExperimentalJsExport::class)

package app.omnitask.web

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The entry points for reading every note of the vault together; see [WebNotes]. */
@JsExport
object WebNotesApi {
    /** The file key that makes the page builders read their text as a JSON list of notes. */
    fun many(): String = WebNotes.MANY

    /** Whether a note is read for tasks: not an archive, not a conflict copy. */
    fun isRead(path: String): Boolean = WebNotes.isRead(path)

    /** The attachments folder, which holds no notes worth reading. */
    fun attachmentDir(): String = app.omnitask.data.VaultText.ATTACHMENT_DIR

    /** The link text Obsidian would write for the note at [path], among every note path ([pathsJson]). */
    fun linkText(path: String, pathsJson: String): String =
        app.omnitask.model.NoteLinks.linkText(path, Json.decodeFromString<List<String>>(pathsJson))

    /** The notes matching [text], names first (JSON list of paths). */
    fun searchNotes(text: String, pathsJson: String): String = Json.encodeToString(
        app.omnitask.model.NoteLinks.search(text, Json.decodeFromString<List<String>>(pathsJson)),
    )

    /** The note a link points to, or null when it is not in the vault. */
    fun resolveLink(link: String, pathsJson: String): String? =
        app.omnitask.model.NoteLinks.resolve(link, Json.decodeFromString<List<String>>(pathsJson))

    /** The file name of the note a task's description moves to (from its title), as Android names it. */
    fun descriptionNoteName(title: String): String = app.omnitask.model.NoteLinks.nameFor(title)

    fun chain(notes: String, ordered: String, on: Boolean): String = WebNotes.chain(notes, ordered, on)

    /**
     * Joins two edits of one note made from [base], line by line, as Android joins an offline edit ([Merge3]):
     * [local] is this device's, [remote] what Drive holds now. JSON `{text, lost}`, where `lost` holds the groups of
     * local lines that clashed (the remote ones were kept).
     */
    fun merge3(base: String, local: String, remote: String): String {
        val r = app.omnitask.drive.Merge3.merge(base, local, remote)
        return kotlinx.serialization.json.buildJsonObject {
            put("text", kotlinx.serialization.json.JsonPrimitive(r.text))
            put("lost", kotlinx.serialization.json.JsonArray(r.lost.map { g -> kotlinx.serialization.json.JsonArray(g.map { kotlinx.serialization.json.JsonPrimitive(it) }) }))
        }.toString()
    }
}
