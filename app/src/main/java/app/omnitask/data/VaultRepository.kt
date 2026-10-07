package app.omnitask.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.omnitask.model.Task

/** Reads and writes task lines in the vault folder the user picked through the system folder picker. */
class VaultRepository(private val context: Context) {

    private class MdFile(val uri: Uri, val path: String)

    class ConflictException : Exception()

    fun loadTasks(treeUri: Uri): List<Task> =
        listMarkdown(treeUri).flatMap { file -> parseFile(file.uri.toString(), file.path, readText(file.uri)) }

    /**
     * Rewrites one line of the task's file. The file is read again right before writing, so a change
     * made by Obsidian or the sync app since the last load is never overwritten: if the line is no
     * longer there, nothing is written and [ConflictException] is thrown.
     */
    fun rewriteLine(task: Task, transform: (String) -> String) {
        val uri = Uri.parse(task.fileUri)
        val text = readText(uri)
        val separator = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = text.split(separator).toMutableList()
        val index = if (lines.getOrNull(task.lineIndex) == task.raw) task.lineIndex else lines.indexOf(task.raw)
        if (index < 0) throw ConflictException()
        lines[index] = transform(task.raw)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(lines.joinToString(separator).toByteArray()) }
            ?: throw java.io.IOException("Cannot open ${task.filePath} for writing")
    }

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

    private fun listMarkdown(treeUri: Uri): List<MdFile> {
        val out = ArrayList<MdFile>()
        walk(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", out)
        return out
    }

    private fun walk(treeUri: Uri, parentId: String, prefix: String, out: MutableList<MdFile>) {
        val folders = ArrayList<Pair<String, String>>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                // .obsidian, .trash and other hidden folders hold no live tasks.
                if (name.startsWith(".")) continue
                if (c.getString(2) == Document.MIME_TYPE_DIR) {
                    folders += id to "$prefix$name/"
                } else if (name.endsWith(".md", ignoreCase = true)) {
                    out += MdFile(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), prefix + name)
                }
            }
        }
        folders.forEach { (id, path) -> walk(treeUri, id, path, out) }
    }

    companion object {
        fun parseFile(fileUri: String, filePath: String, text: String): List<Task> {
            val lines = text.split("\r\n", "\n")
            val tasks = ArrayList<Task>()
            var i = 0
            while (i < lines.size) {
                val parsed = TaskLine.parse(lines[i])
                if (parsed == null) {
                    i++
                    continue
                }
                // Indented plain lines right under a task are its notes.
                val notes = ArrayList<String>()
                var j = i + 1
                while (j < lines.size && lines[j].isNotBlank() && lines[j].first().isWhitespace() && !TaskLine.isTask(lines[j])) {
                    notes += lines[j].trim().removePrefix("- ").trim()
                    j++
                }
                tasks += parsed.copy(filePath = filePath, fileUri = fileUri, lineIndex = i, notes = notes)
                i = j
            }
            return tasks
        }
    }
}
