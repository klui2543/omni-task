package app.omnitask.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.omnitask.model.Branches
import app.omnitask.model.OmniList
import app.omnitask.model.Task
import java.time.LocalDate
import app.omnitask.model.tr

/** Reads and writes task lines in the vault folder the user picked through the system folder picker. */
class VaultRepository(private val context: Context) {

    private class MdFile(val uri: Uri, val path: String)

    class ConflictException : Exception()

    /** Tasks only, without the items of list notes (reminders, widgets and the views never want those). */
    fun loadTasks(treeUri: Uri): List<Task> = load(treeUri).tasks.filter { it.list == null }

    /** Tasks plus the path of every note in the vault (for linking notes to tasks). */
    class Snapshot(val tasks: List<Task>, val notePaths: List<String>, val lists: List<OmniList> = emptyList())

    fun load(treeUri: Uri): Snapshot {
        val files = listMarkdown(treeUri)
        val lists = ArrayList<OmniList>()
        val tasks = files.flatMap { file ->
            val text = readText(file.uri)
            // Lines in a list note (Bucket list, Watch list...) are marked with the list's name.
            val list = OmniList.parse(file.path, text)?.also { lists += it }
            parseFile(file.uri.toString(), file.path, text).let { found -> if (list == null) found else found.map { it.copy(list = list.name) } }
        }
        return Snapshot(tasks, files.map { it.path }, lists.sortedBy { it.name.lowercase() })
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

    /**
     * Completes a repeating task by moving the same line on to its next occurrence (no copy is added),
     * and notes the completion in the app's history. Returns false (and writes nothing) when the rule cannot be read.
     */
    fun completeRecurring(task: Task, today: LocalDate): Boolean {
        val next = TaskLine.advanceRecurring(task.raw, today) ?: return false
        rewriteLine(task) { next }
        RecurHistory.add(context, task.title, today)
        return true
    }

    /** Adds a subtask as the last line of the parent's block, one level deeper. */
    fun addSubtask(parent: Task, taskLine: String) = editLines(parent) { lines, index -> insertSubtask(lines, index, taskLine) }

    /** Puts the parent's direct subtasks in the order of [rawsInOrder]; see [orderSubtasks]. */
    fun reorderSubtasks(parent: Task, rawsInOrder: List<String>) = editLines(parent) { lines, index ->
        if (!orderSubtasks(lines, index, rawsInOrder)) throw ConflictException()
    }

    /** Replaces the task's description; see [describe]. */
    fun setDescription(task: Task, text: String) = editLines(task) { lines, index -> describe(lines, index, text) }

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

    /** Lines taken out of a file, and where they were, so they can be put back. */
    class Cut(val fileUri: String, val index: Int, val lines: List<String>)

    /** Removes the task's line and everything indented under it (description, links, subtasks). */
    fun deleteTask(task: Task): Cut {
        var cut: Cut? = null
        editLines(task) { lines, index -> cut = Cut(task.fileUri, index, cutBlock(lines, index)) }
        return cut!!
    }

    /** Puts lines removed by [deleteTask] back where they were (or at the end when the file got shorter). */
    fun restore(cut: Cut) {
        val (index, block) = cut.index to cut.lines
        val uri = Uri.parse(cut.fileUri)
        val text = readText(uri)
        val separator = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = text.split(separator).toMutableList()
        lines.addAll(index.coerceIn(0, lines.size), block)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(lines.joinToString(separator).toByteArray()) }
            ?: throw java.io.IOException("Cannot write")
    }

    /**
     * Renames a project in every task line of the given files: `#old` and `#old/branch` become `#new...`.
     * Each file is read and written once. Returns how many lines changed.
     */
    fun renameProject(fileUris: List<String>, old: String, new: String): Int {
        var changed = 0
        fileUris.distinct().forEach { u ->
            val uri = Uri.parse(u)
            val text = readText(uri)
            val separator = if (text.contains("\r\n")) "\r\n" else "\n"
            val lines = text.split(separator)
            val out = lines.map { l -> if (TaskLine.isTask(l)) Branches.renameInLine(l, old, new).also { if (it != l) changed++ } else l }
            if (out != lines) context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out.joinToString(separator).toByteArray()) }
        }
        return changed
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
        val text = readPath(treeUri, path) ?: throw java.io.IOException(tr("ไม่พบ $path", "Not found: $path"))
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

        private fun indentOf(line: String) = line.takeWhile { it == ' ' || it == '\t' }.fold(0) { n, c -> n + (if (c == '\t') 4 else 1) }

        /** Where the task's block ends: the first line after it that is not indented deeper (exclusive). */
        private fun blockEnd(lines: List<String>, index: Int): Int {
            val base = indentOf(lines[index])
            var end = index + 1
            while (end < lines.size && lines[end].isNotBlank() && indentOf(lines[end]) > base) end++
            return end
        }

        /** Removes the task at [index] with its whole block and returns the removed lines. */
        fun cutBlock(lines: MutableList<String>, index: Int): List<String> {
            val block = lines.subList(index, blockEnd(lines, index))
            val removed = block.toList()
            block.clear()
            return removed
        }

        fun insertSubtask(lines: MutableList<String>, index: Int, taskLine: String) {
            val indent = lines[index].takeWhile { it == ' ' || it == '\t' } + "    "
            lines.add(blockEnd(lines, index), indent + taskLine.trimStart())
        }

        /**
         * Puts the direct subtasks of the task at [index] in the order of [rawsInOrder] (their lines as they are
         * now). Each subtask moves with everything indented under it; the parent's own notes stay on top.
         * Returns false when the subtasks no longer match.
         */
        fun orderSubtasks(lines: MutableList<String>, index: Int, rawsInOrder: List<String>): Boolean {
            val end = blockEnd(lines, index)
            val region = lines.subList(index + 1, end).toList()
            val childIndent = region.filter { TaskLine.isTask(it) }.minOfOrNull { indentOf(it) } ?: return false
            val head = region.takeWhile { !(TaskLine.isTask(it) && indentOf(it) == childIndent) }
            val blocks = ArrayList<MutableList<String>>()
            region.drop(head.size).forEach { line ->
                if (TaskLine.isTask(line) && indentOf(line) == childIndent) blocks += mutableListOf(line) else blocks.lastOrNull()?.add(line)
            }
            val ordered = rawsInOrder.mapNotNull { raw -> blocks.firstOrNull { it.first() == raw } } + blocks.filter { b -> b.first() !in rawsInOrder }
            if (ordered.size != blocks.size) return false
            val rebuilt = head + ordered.flatten()
            for (k in rebuilt.indices) lines[index + 1 + k] = rebuilt[k]
            return true
        }

        /**
         * Replaces the description of the task at [index]: its plain note lines (not links, images, the first
         * step or subtasks). Each line of [text] becomes a `- ` line, which Obsidian shows nested under the task.
         */
        fun describe(lines: MutableList<String>, index: Int, text: String) {
            val indent = lines[index].takeWhile { it == ' ' || it == '\t' } + "    "
            var i = index + 1
            var at = -1
            while (i < lines.size && lines[i].isNotBlank() && lines[i].first().isWhitespace() && !TaskLine.isTask(lines[i])) {
                if (Task.isPlainNote(lines[i].trim().removePrefix("- ").trim())) {
                    if (at < 0) at = i
                    lines.removeAt(i)
                } else {
                    i++
                }
            }
            val fresh = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { "$indent- $it" }
            lines.addAll(if (at >= 0) at else index + 1, fresh)
        }

        fun parseFile(fileUri: String, filePath: String, text: String): List<Task> {
            val lines = text.split("\r\n", "\n")
            val tasks = ArrayList<Task>()
            // Open parents by indent: a deeper checkbox line right under a task is its subtask.
            val stack = ArrayList<Task>()
            var i = 0
            while (i < lines.size) {
                val parsed = TaskLine.parse(lines[i])
                if (parsed == null) {
                    // Anything at the left margin (a heading, a paragraph) ends the nesting.
                    if (lines[i].isNotBlank() && !lines[i].first().isWhitespace()) stack.clear()
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
                val placed = parsed.copy(filePath = filePath, fileUri = fileUri, lineIndex = i, notes = notes)
                while (stack.isNotEmpty() && stack.last().indent >= placed.indent) stack.removeAt(stack.lastIndex)
                val task = placed.copy(parent = stack.lastOrNull()?.key)
                tasks += task
                stack += task
                i = j
            }
            return tasks
        }
    }
}
