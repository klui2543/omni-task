package app.omnitask.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.omnitask.drive.DriveUnavailable
import app.omnitask.drive.OmniDrive
import app.omnitask.drive.sameName
import app.omnitask.model.OmniList
import app.omnitask.model.Recurrence
import app.omnitask.model.Task
import kotlinx.datetime.LocalDate
import app.omnitask.model.tr
import app.omnitask.time.*

/**
 * Reads and writes task lines in the vault folder the user picked through the system folder picker. Once the owner
 * connects Google Drive ([DriveLink]), the files of the Omni folder are read and written on Drive instead, and their
 * tasks carry a `omni-drive:` file URI.
 */
class VaultRepository(private val context: Context) {

    private class MdFile(val uri: Uri, val path: String)

    class ConflictException : Exception()

    /** Tasks only, without the items of list notes (reminders, widgets and the views never want those). */
    fun loadTasks(treeUri: Uri): List<Task> = load(treeUri).tasks.filter { it.list == null }

    /** Tasks plus the path of every note in the vault (for linking notes to tasks). */
    class Snapshot(
        val tasks: List<Task>,
        val notePaths: List<String>,
        val lists: List<OmniList> = emptyList(),
        /** Copies a sync app left after a clash, which are not read (see [VaultText.isConflictCopy]). */
        val conflicts: List<String> = emptyList(),
        /** With Drive connected: how the Omni folder was read this time; null when Drive is not in use. */
        val drive: DriveStatus? = null,
    )

    /** How the last read of the Omni folder on Drive went. */
    class DriveStatus(
        val online: Boolean,
        /** Google wants the owner to sign in again before Drive can be reached. */
        val signIn: Boolean,
        /** Files with edits made while Drive was out of reach, not sent yet. */
        val pending: Int,
        /** Lines edited offline that clashed with a change made elsewhere; Drive's version was kept. */
        val lost: List<String>,
        /** When Drive was last read in full (epoch milliseconds), 0 before the first time. */
        val lastSync: Long,
    )

    fun load(treeUri: Uri): Snapshot {
        val drive = DriveLink.isOn(context)
        // With Drive connected, the phone's own copy of the Omni folder is left alone: Drive has the live one.
        val all = listMarkdown(treeUri).let { files -> if (drive) files.filter { omniPath(it.path) == null } else files }
        val local = all.map { Note(it.uri.toString(), it.path) { readText(it.uri) } }
        var status: DriveStatus? = null
        val remote = if (!drive) emptyList() else DriveLink.use(context) { store ->
            val listing = store.refresh()
            val lost = store.takeLost().flatMap { it.lines }
            status = DriveStatus(listing.online, (listing.problem as? DriveUnavailable)?.signIn == true, store.pendingPaths.size, lost, store.state.lastSync)
            listing.texts.filterKeys { it.endsWith(".md", ignoreCase = true) }.map { (path, text) -> Note(DRIVE_URI + path, "${VaultText.OMNI_DIR}/$path") { text } }
        }
        val (conflicts, files) = (local + remote).partition { VaultText.isConflictCopy(it.path) }
        val lists = ArrayList<OmniList>()
        val tasks = files.flatMap { file ->
            // The archive keeps finished work for Obsidian; the app leaves it unread so Done stays short.
            if (Archive.isArchive(file.path)) return@flatMap emptyList<Task>()
            val text = file.text()
            // Lines in a list note (Bucket list, Watch list...) are marked with the list's name.
            val list = OmniList.parse(file.path, text)?.also { lists += it }
            VaultText.parseFile(file.uri, file.path, text).let { found -> if (list == null) found else found.map { it.copy(list = list.name) } }
        }
        // A repeating task left open past its date also shows today's round (see Recurrence.todayCopy).
        return Snapshot(
            Recurrence.withTodayCopies(tasks, LocalDate.now()), files.map { it.path }, lists.sortedBy { it.name.lowercase() },
            conflicts.map { it.path }, status,
        )
    }

    /** A note to read: where it is and how to get its text. */
    private class Note(val uri: String, val path: String, val text: () -> String)

    /**
     * The path inside the Omni folder of a vault path, when Drive is connected and the path is in that folder; null
     * otherwise. Names are compared loosely, since an emoji like 📁 can be stored in more than one way.
     */
    private fun omniPath(path: String): String? {
        if (!DriveLink.isOn(context)) return null
        val dir = VaultText.OMNI_DIR.split('/')
        val parts = path.split('/')
        if (parts.size <= dir.size || dir.indices.any { !sameName(parts[it], dir[it]) }) return null
        return parts.drop(dir.size).joinToString("/")
    }

    /** [omniPath] for a folder: "" for the Omni folder itself. */
    private fun omniDir(dir: String): String? = omniPath("$dir/.")?.removeSuffix(".")?.removeSuffix("/")

    /** The path inside the Omni folder of a task file on Drive, from its `omni-drive:` URI. */
    private fun drivePath(fileUri: String): String? = fileUri.takeIf { it.startsWith(DRIVE_URI) }?.removePrefix(DRIVE_URI)

    /**
     * Reads a file, lets [change] make its new text (null leaves it), and writes it: on Drive with the version
     * checked before writing, or in the picked folder.
     */
    private fun update(treeUri: Uri, path: String, change: (String?) -> String?) {
        val rel = omniPath(path)
        if (rel != null) {
            DriveLink.use(context) { it.edit(rel, change) }
            return
        }
        val text = readPath(treeUri, path)
        val out = change(text) ?: return
        if (out != text) writePath(treeUri, path, out)
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
     * Ticking today's round of a task left open ([Task.todayCopy]) moves the line past today.
     */
    fun completeRecurring(task: Task, today: LocalDate): Boolean {
        if (TaskLine.advanceRecurring(task.raw, today, task.todayCopy) == null) return false
        // Its subtasks are unticked in the same write, so the next round starts with the checklist open.
        editLines(task) { lines, index -> VaultText.advanceRecurring(lines, index, today, task.todayCopy) }
        RecurHistory.add(context, task.title, today)
        return true
    }

    /** Adds a subtask as the last line of the parent's block, one level deeper. */
    fun addSubtask(parent: Task, taskLine: String) = editLines(parent) { lines, index -> VaultText.insertSubtask(lines, index, taskLine) }

    /** Puts the parent's direct subtasks in the order of [rawsInOrder]; see [orderSubtasks]. */
    fun reorderSubtasks(parent: Task, rawsInOrder: List<String>) = editLines(parent) { lines, index ->
        if (!VaultText.orderSubtasks(lines, index, rawsInOrder)) throw ConflictException()
    }

    /** Replaces the task's description; see [describe]. */
    fun setDescription(task: Task, text: String) = editLines(task) { lines, index -> VaultText.describe(lines, index, text) }

    /** Appends `line` as the last indented line under the task (after its existing notes). */
    fun addSubLine(task: Task, line: String) = editLines(task) { lines, index -> VaultText.addSubLine(lines, index, line) }

    /** Removes the first indented line under the task that contains `text`. */
    fun removeSubLine(task: Task, text: String) = editLines(task) { lines, index -> VaultText.removeSubLine(lines, index, text) }

    /** Replaces the indented line under the task that starts with `prefix` (adds it if missing, removes it when `line` is null). */
    fun setSubLine(task: Task, prefix: String, line: String?) = editLines(task) { lines, index -> VaultText.setSubLine(lines, index, prefix, line) }

    /** Lines taken out of a file, and where they were, so they can be put back. */
    class Cut(val fileUri: String, val index: Int, val lines: List<String>)

    /** Removes the task's line and everything indented under it (description, links, subtasks). */
    fun deleteTask(task: Task): Cut {
        var cut: Cut? = null
        editLines(task) { lines, index -> cut = Cut(task.fileUri, index, VaultText.cutBlock(lines, index)) }
        return cut!!
    }

    /** Puts lines removed by [deleteTask] back where they were (or at the end when the file got shorter). */
    fun restore(cut: Cut) {
        drivePath(cut.fileUri)?.let { rel ->
            DriveLink.use(context) { store -> store.edit(rel) { VaultText.restore(it.orEmpty(), cut.index, cut.lines) } }
            return
        }
        val uri = Uri.parse(cut.fileUri)
        writeText(uri, VaultText.restore(readText(uri), cut.index, cut.lines)) ?: throw java.io.IOException("Cannot write")
    }

    /**
     * Moves the task with its whole block to the end of the archive note and returns what was cut, for undo.
     * If the archive cannot be written, the task is put back.
     */
    fun archiveTask(treeUri: Uri, task: Task, today: LocalDate): Cut {
        val cut = deleteTask(task)
        try {
            update(treeUri, Archive.FILE) { Archive.append(it, listOf(cut.lines), today) }
        } catch (e: Exception) {
            restore(cut)
            throw e
        }
        return cut
    }

    /** Puts an archived task back where it was and takes it out of the archive note. */
    fun unarchive(treeUri: Uri, cut: Cut) {
        restore(cut)
        update(treeUri, Archive.FILE) { text -> text?.let { Archive.remove(it, cut.lines) } }
    }

    /**
     * Moves finished tasks closed on or before [cutoff] from the live TaskForge note to the archive; see
     * [Archive.sweep]. The archive is written first and the live note is read again before it is replaced,
     * so an edit made meanwhile is never lost (the archive is put back and nothing moves). Returns the titles moved.
     */
    fun sweepDone(treeUri: Uri, cutoff: LocalDate, today: LocalDate, keep: (Task) -> Boolean): List<String> {
        // On Drive the sweep waits for a connection: a move between two notes is not something to queue.
        if (omniPath(TASK_FILE) != null && !DriveLink.use(context) { it.reachable() }) return emptyList()
        val text = readPath(treeUri, TASK_FILE) ?: return emptyList()
        val sweep = Archive.sweep(text, cutoff, keep) ?: return emptyList()
        val before = readPath(treeUri, Archive.FILE)
        writePath(treeUri, Archive.FILE, Archive.append(before, sweep.blocks, today))
        var moved = false
        update(treeUri, TASK_FILE) { now -> if (now != text) null else sweep.text.also { moved = true } }
        if (!moved) {
            writePath(treeUri, Archive.FILE, before ?: "")
            return emptyList()
        }
        return sweep.titles
    }

    /**
     * Whether a sync app left a conflict copy beside the task note, in its new place or its old one (the archive sits beside it).
     * Until the owner sorts it out, writes the owner did not ask for are better left undone.
     */
    fun hasTaskConflict(treeUri: Uri): Boolean =
        listOf(TASK_FILE, VaultText.LEGACY_TASK_FILE).any { file -> namesIn(treeUri, file.substringBeforeLast('/')).any { VaultText.isConflictCopy(it) } }

    /**
     * Renames a project in every task line of the given files: `#old` and `#old/branch` become `#new...`.
     * Each file is read and written once. Returns how many lines changed.
     */
    fun renameProject(fileUris: List<String>, old: String, new: String): Int {
        var changed = 0
        fileUris.distinct().forEach { u ->
            drivePath(u)?.let { rel ->
                DriveLink.use(context) { store ->
                    store.edit(rel) { text ->
                        val (out, n) = VaultText.renameProject(text ?: return@edit null, old, new)
                        changed += n
                        out
                    }
                }
                return@forEach
            }
            val uri = Uri.parse(u)
            val text = readText(uri)
            val (out, n) = VaultText.renameProject(text, old, new)
            if (out != text) writeText(uri, out)
            changed += n
        }
        return changed
    }

    private fun editLines(task: Task, edit: (MutableList<String>, Int) -> Unit) {
        drivePath(task.fileUri)?.let { rel ->
            DriveLink.use(context) { store ->
                store.edit(rel) { text -> VaultText.edit(text ?: throw ConflictException(), task, edit) ?: throw ConflictException() }
            }
            return
        }
        val uri = Uri.parse(task.fileUri)
        val out = VaultText.edit(readText(uri), task, edit) ?: throw ConflictException()
        writeText(uri, out) ?: throw java.io.IOException("Cannot open ${task.filePath} for writing")
    }

    /** The text of a vault file by its path, or null when it does not exist. */
    fun readPath(treeUri: Uri, path: String): String? {
        omniPath(path)?.let { rel -> return DriveLink.use(context) { it.read(rel) } }
        val dirId = if ('/' in path) findDirId(treeUri, path.substringBeforeLast('/')) ?: return null else DocumentsContract.getTreeDocumentId(treeUri)
        val file = childOf(treeUri, dirId, path.substringAfterLast('/'))?.first ?: return null
        return readText(file)
    }

    /** Replaces (or creates, with its folders) a vault file. */
    fun writePath(treeUri: Uri, path: String, text: String) {
        omniPath(path)?.let { rel ->
            DriveLink.use(context) { it.edit(rel) { text } }
            return
        }
        val dir = if ('/' in path) findOrCreateDir(treeUri, path.substringBeforeLast('/')) else
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val name = path.substringAfterLast('/')
        val existing = childOf(treeUri, DocumentsContract.getDocumentId(dir), name)?.first
        // The type must match the name's extension, or the provider adds its own: a .json made as text/markdown
        // came out as "x.json.md", was never found again, and every save made one more copy.
        val mime = if (name.endsWith(".json", ignoreCase = true)) "application/json" else "text/markdown"
        val file = existing ?: DocumentsContract.createDocument(context.contentResolver, dir, mime, name)
            ?: throw java.io.IOException("Cannot create $path")
        context.contentResolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw java.io.IOException("Cannot write $path")
    }

    /** Names of the files directly in a vault folder; empty when the folder is missing. */
    fun namesIn(treeUri: Uri, dir: String): List<String> {
        omniDir(dir)?.let { rel -> return DriveLink.use(context) { it.namesIn(rel) } }
        val dirId = findDirId(treeUri, dir) ?: return emptyList()
        val out = ArrayList<String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        context.contentResolver.query(children, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { out += it }
        }
        return out
    }

    fun deletePath(treeUri: Uri, path: String) {
        omniPath(path)?.let { rel ->
            DriveLink.use(context) { it.delete(rel) }
            return
        }
        val dirId = findDirId(treeUri, path.substringBeforeLast('/')) ?: return
        childOf(treeUri, dirId, path.substringAfterLast('/'))?.let { DocumentsContract.deleteDocument(context.contentResolver, it.first) }
    }

    /** Adds a task line at the end of a file, keeping its line endings. */
    fun appendLine(treeUri: Uri, path: String, line: String) {
        update(treeUri, path) { text -> VaultText.appendLine(text ?: throw java.io.IOException(tr("ไม่พบ $path", "Not found: $path")), line) }
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

    private fun writeText(uri: Uri, text: String): Unit? =
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }

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
        const val ATTACHMENT_DIR = VaultText.ATTACHMENT_DIR
        const val TASK_FILE = VaultText.TASK_FILE

        /** The file URI of a note in the Omni folder on Drive: this prefix and its path inside the folder. */
        const val DRIVE_URI = "omni-drive:"
    }
}
