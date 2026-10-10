@file:OptIn(ExperimentalJsExport::class)

package app.omnitask.web

import app.omnitask.model.Lang

/** The entry points the browser calls; see [WebCore]. */
@JsExport
object WebApi {
    fun setEnglish(english: Boolean) {
        Lang.english = english
    }

    fun loadTasks(fileKey: String, path: String, text: String, today: String): String = WebCore.loadTasks(fileKey, path, text, today)

    fun list(fileKey: String, path: String, text: String, today: String, query: String): String = WebCore.list(fileKey, path, text, today, query)

    fun toggle(text: String, raw: String, lineIndex: Int, today: String, withSubtasks: Boolean): String =
        WebCore.guarded { WebCore.toggle(text, raw, lineIndex, today, withSubtasks) }

    fun setStatus(text: String, raw: String, lineIndex: Int, status: String, today: String): String = WebCore.setStatus(text, raw, lineIndex, status, today)

    fun addTask(text: String, sentence: String, today: String): String = WebCore.addTask(text, sentence, today)

    fun editTask(text: String, raw: String, lineIndex: Int, today: String, op: String): String = WebCore.editTask(text, raw, lineIndex, today, op)

    fun cut(text: String, raw: String, lineIndex: Int): String = WebCore.cut(text, raw, lineIndex)

    fun restore(text: String, index: Int, lines: String): String = WebCore.restore(text, index, lines)

    fun archiveAppend(archive: String, lines: String, today: String): String = WebCore.archiveAppend(archive, lines, today)

    fun archiveRemove(archive: String, lines: String): String? = WebCore.archiveRemove(archive, lines)

    fun describeRule(rule: String): String? = WebCore.describeRule(rule)

    fun isConflictCopy(name: String): Boolean = app.omnitask.data.VaultText.isConflictCopy(name)

    /** The vault path of the archive note. */
    fun archiveFile(): String = app.omnitask.data.Archive.FILE

    /** The vault path of the live task file. */
    fun taskFile(): String = app.omnitask.data.VaultText.TASK_FILE
}
