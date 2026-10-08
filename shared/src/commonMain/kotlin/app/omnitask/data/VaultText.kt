package app.omnitask.data

import app.omnitask.model.Branches
import app.omnitask.model.Task

/**
 * The text side of reading and writing vault notes: how a note's lines become tasks and how each edit changes
 * the lines. Where the files live (the Android folder picker, Google Drive) is up to each app.
 */
object VaultText {

    /** Where the owner's Obsidian keeps attachments, relative to the vault root. */
    const val ATTACHMENT_DIR = "📁 Folder/หลังบ้าน/Attachments"

    /** The live TaskForge file, where new tasks are added. */
    const val TASK_FILE = "📁 Folder/หลังบ้าน/TaskForge/TaskForge.md"

    /** The line ending the file already uses, so an edit never changes it. */
    fun separatorOf(text: String) = if (text.contains("\r\n")) "\r\n" else "\n"

    /**
     * Applies [edit] to the task's file text and returns the new text. The task's line is looked for where it
     * was and then anywhere, so a change made elsewhere since the last load is never overwritten: null means
     * the line is no longer there and nothing should be written.
     */
    fun edit(text: String, task: Task, edit: (MutableList<String>, Int) -> Unit): String? {
        val separator = separatorOf(text)
        val lines = text.split(separator).toMutableList()
        val index = if (lines.getOrNull(task.lineIndex) == task.raw) task.lineIndex else lines.indexOf(task.raw)
        if (index < 0) return null
        edit(lines, index)
        return lines.joinToString(separator)
    }

    /** Puts lines back at [index] (or at the end when the file got shorter). */
    fun restore(text: String, index: Int, block: List<String>): String {
        val separator = separatorOf(text)
        val lines = text.split(separator).toMutableList()
        lines.addAll(index.coerceIn(0, lines.size), block)
        return lines.joinToString(separator)
    }

    /** Renames a project in every task line: `#old` and `#old/branch` become `#new...`. Returns the text and how many lines changed. */
    fun renameProject(text: String, old: String, new: String): Pair<String, Int> {
        var changed = 0
        val separator = separatorOf(text)
        val out = text.split(separator).map { l -> if (TaskLine.isTask(l)) Branches.renameInLine(l, old, new).also { if (it != l) changed++ } else l }
        return out.joinToString(separator) to changed
    }

    /** Adds a task line at the end of the text, keeping its line endings. */
    fun appendLine(text: String, line: String): String {
        val separator = separatorOf(text)
        return text.trimEnd('\r', '\n') + separator + line + separator
    }

    /** Appends `line` as the last indented line under the task at [index] (after its existing notes). */
    fun addSubLine(lines: MutableList<String>, index: Int, line: String) {
        val indent = lines[index].takeWhile { it.isWhitespace() } + "    "
        var end = index + 1
        while (end < lines.size && lines[end].isNotBlank() && lines[end].first().isWhitespace() && !TaskLine.isTask(lines[end])) end++
        lines.add(end, "$indent- $line")
    }

    /** Removes the first indented line under the task at [index] that contains `text`. */
    fun removeSubLine(lines: MutableList<String>, index: Int, text: String) {
        var i = index + 1
        while (i < lines.size && lines[i].isNotBlank() && lines[i].first().isWhitespace() && !TaskLine.isTask(lines[i])) {
            if (lines[i].contains(text)) {
                lines.removeAt(i)
                return
            }
            i++
        }
    }

    /** Replaces the indented line under the task at [index] that starts with `prefix` (adds it if missing, removes it when `line` is null). */
    fun setSubLine(lines: MutableList<String>, index: Int, prefix: String, line: String?) {
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
