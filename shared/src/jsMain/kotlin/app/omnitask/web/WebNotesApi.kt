@file:OptIn(ExperimentalJsExport::class)

package app.omnitask.web

/** The entry points for reading every note of the vault together; see [WebNotes]. */
@JsExport
object WebNotesApi {
    /** The file key that makes the page builders read their text as a JSON list of notes. */
    fun many(): String = WebNotes.MANY

    /** Whether a note is read for tasks: not an archive, not a conflict copy. */
    fun isRead(path: String): Boolean = WebNotes.isRead(path)

    /** The attachments folder, which holds no notes worth reading. */
    fun attachmentDir(): String = app.omnitask.data.VaultText.ATTACHMENT_DIR

    fun chain(notes: String, ordered: String, on: Boolean): String = WebNotes.chain(notes, ordered, on)
}
