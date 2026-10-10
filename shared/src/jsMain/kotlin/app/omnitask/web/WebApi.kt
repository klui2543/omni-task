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

    /** Whether a file name is a copy a sync app left after a clash. */
    fun isConflictCopy(path: String): Boolean = app.omnitask.data.VaultText.isConflictCopy(path)

    /** The vault path of the live task file. */
    fun taskFile(): String = app.omnitask.data.VaultText.TASK_FILE
}
