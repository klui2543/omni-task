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

    fun loadTasks(treeUri: Uri): List<Task> = load(treeUri).tasks

    /** Tasks plus the path of every note in the vault (for linking notes to tasks). */
    class Snapshot(val tasks: List<Task>, val notePaths: List<String>)

    fun load(treeUri: Uri): Snapshot {
        val files = listMarkdown(treeUri)
        return Snapshot(files.flatMap { file -> parseFile(file.uri.toString(), file.path, readText(file.uri)) }, files.map { it.path })
    }

    /** The vault's folder name, which is also its name in Obsidian. */
    fun vaultName(treeUri: Uri): String? =
        queryName(DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)))

    /**
     * Rewrites one line of the task's file. The file is read again right before writing, so a change
     * made by Obsidian or the sync app since the last load is never overwritten: if the line is no
     * longer there, nothing is written and [ConflictException] is thrown.
     */
    fun rewriteLine(task: Task, transform: (String) -> String) = editLines(task) { lines, index ->
        lines[index] = transform(task.raw)
    }

    /** Appends `line` as the last indented line under the task (after its existing notes). */
    fun addSubLine(task: Task, line: String) = editLines(task) { lines, index ->
        val indent = lines[index].takeWhile { it.isWhitespace() } + "    "
        var end = index + 1
        while (end < lines.size && lines[end].isNotBlank() && lines[end].first().isWhitespace() && !TaskLine.isTask(lines[end])) end++
        lines.add(end, "$indent- $line")
    }

    /** Removes the first indented line under the task that contains `text`. */
    fun removeSubLine(task: Task, text: String) = editLines(task) { lines, index ->
        var i = index + 1
        while (i < lines.size && lines[i].isNotBlank() && lines[i].first().isWhitespace() && !TaskLine.isTask(lines[i])) {
            if (lines[i].contains(text)) {
                lines.removeAt(i)
                return@editLines
            }
            i++
        }
    }

    /** Replaces the indented line under the task that starts with `prefix` (adds it if missing, removes it when `line` is null). */
    fun setSubLine(task: Task, prefix: String, line: String?) = editLines(task) { lines, index ->
        val indent = lines[index].takeWhile { it.isWhitespace() } + "    "
        var i = index + 1
        var found = -1
        while (i < lines.size && lines[i].isNotBlank() && lines[i].first().isWhitespace() && !TaskLine.isTask(lines[i])) {
            if (found < 0 && lines[i].trim().removePrefix("- ").trim().startsWith(prefix)) found = i
            i++
        }
        when {
            found >= 0 && line == null -> lines.removeAt(found)
            found >= 0 -> lines[found] = "$indent- $line"
            line != null -> lines.add(i, "$indent- $line")
        }
    }

    private fun editLines(task: Task, edit: (MutableList<String>, Int) -> Unit) {
        val uri = Uri.parse(task.fileUri)
        val text = readText(uri)
        val separator = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = text.split(separator).toMutableList()
        val index = if (lines.getOrNull(task.lineIndex) == task.raw) task.lineIndex else lines.indexOf(task.raw)
        if (index < 0) throw ConflictException()
        edit(lines, index)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(lines.joinToString(separator).toByteArray()) }
            ?: throw java.io.IOException("Cannot open ${task.filePath} for writing")
    }

    /** The text of a vault file by its path, or null when it does not exist. */
    fun readPath(treeUri: Uri, path: String): String? {
        val dirId = if ('/' in path) findDirId(treeUri, path.substringBeforeLast('/')) ?: return null else DocumentsContract.getTreeDocumentId(treeUri)
        val file = childOf(treeUri, dirId, path.substringAfterLast('/'))?.first ?: return null
        return readText(file)
    }

    /** Replaces (or creates, with its folders) a vault file. */
    fun writePath(treeUri: Uri, path: String, text: String) {
        val dir = if ('/' in path) findOrCreateDir(treeUri, path.substringBeforeLast('/')) else
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val name = path.substringAfterLast('/')
        val existing = childOf(treeUri, DocumentsContract.getDocumentId(dir), name)?.first
        val file = existing ?: DocumentsContract.createDocument(context.contentResolver, dir, "text/markdown", name)
            ?: throw java.io.IOException("Cannot create $path")
        context.contentResolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw java.io.IOException("Cannot write $path")
    }

    /** Adds a task line at the end of a file, keeping its line endings. */
    fun appendLine(treeUri: Uri, path: String, line: String) {
        val text = readPath(treeUri, path) ?: throw java.io.IOException("ไม่พบ $path")
        val separator = if (text.contains("\r\n")) "\r\n" else "\n"
        val body = text.trimEnd('\r', '\n')
        writePath(treeUri, path, body + separator + line + separator)
    }

    /** Writes an image into the vault's attachment folder (made if missing) and returns its file name. */
    fun saveAttachment(treeUri: Uri, name: String, mime: String, bytes: ByteArray): String {
        val dir = findOrCreateDir(treeUri, ATTACHMENT_DIR)
        val file = DocumentsContract.createDocument(context.contentResolver, dir, mime, name)
            ?: throw java.io.IOException("Cannot create $name")
        context.contentResolver.openOutputStream(file, "w")?.use { it.write(bytes) }
            ?: throw java.io.IOException("Cannot write $name")
        // The provider may rename on clash (e.g. "x (1).webp"), so report the name it actually used.
        return queryName(file) ?: name
    }

    fun readAttachment(treeUri: Uri, name: String): ByteArray? {
        val uri = findAttachment(treeUri, name) ?: return null
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    fun deleteAttachment(treeUri: Uri, name: String) {
        findAttachment(treeUri, name)?.let { DocumentsContract.deleteDocument(context.contentResolver, it) }
    }

    private fun findAttachment(treeUri: Uri, name: String): Uri? {
        val dirId = findDirId(treeUri, ATTACHMENT_DIR) ?: return null
        return childOf(treeUri, dirId, name)?.first
    }

    private fun findOrCreateDir(treeUri: Uri, path: String): Uri {
        var parentId = DocumentsContract.getTreeDocumentId(treeUri)
        for (segment in path.split('/')) {
            val existing = childOf(treeUri, parentId, segment)
            parentId = if (existing != null) {
                existing.second
            } else {
                val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                val made = DocumentsContract.createDocument(context.contentResolver, parent, Document.MIME_TYPE_DIR, segment)
                    ?: throw java.io.IOException("Cannot create folder $segment")
                DocumentsContract.getDocumentId(made)
            }
        }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
    }

    private fun findDirId(treeUri: Uri, path: String): String? {
        var parentId = DocumentsContract.getTreeDocumentId(treeUri)
        for (segment in path.split('/')) parentId = childOf(treeUri, parentId, segment)?.second ?: return null
        return parentId
    }

    /** The child with this display name: its document URI and id. */
    private fun childOf(treeUri: Uri, parentId: String, name: String): Pair<Uri, String>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) {
                    val id = c.getString(0)
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, id) to id
                }
            }
        }
        return null
    }

    private fun queryName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
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
        /** Where the owner's Obsidian keeps attachments, relative to the vault root. */
        const val ATTACHMENT_DIR = "📁 Folder/หลังบ้าน/Attachments"

        /** The live TaskForge file, where new tasks are added. */
        const val TASK_FILE = "📁 Folder/หลังบ้าน/TaskForge/TaskForge.md"

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
