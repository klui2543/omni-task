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

    fun focus(fileKey: String, path: String, text: String, state: String): String = WebFocus.build(fileKey, path, text, state)

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

    /* ---------- Views ---------- */

    fun views(fileKey: String, path: String, text: String, state: String): String = WebViews.build(fileKey, path, text, state)

    fun addTaskInStatus(text: String, sentence: String, today: String, status: String): String = WebViews.addTask(text, sentence, today, status)

    // ---- Projects and lists ----

    fun projects(fileKey: String, path: String, text: String, notes: String, state: String, today: String): String =
        WebProjects.build(fileKey, path, text, notes, state, today)

    fun projectIgnoreTags(tags: String) = WebProjects.ignoreTags(tags)

    fun projectRenameTag(text: String, old: String, new: String): String = WebProjects.renameTag(text, old, new)

    fun projectCleanName(name: String): String = WebProjects.cleanProjectName(name)

    fun projectAddTagged(text: String, tag: String, title: String, today: String): String = WebProjects.addTagged(text, tag, title, today)

    fun projectChain(text: String, ordered: String, on: Boolean): String = WebProjects.chain(text, ordered, on)

    fun projectBranchChange(fileKey: String, path: String, text: String, states: String, op: String): String =
        WebProjects.branchChange(fileKey, path, text, states, op)

    fun listStarters(): String = WebProjects.starters()

    fun listIconThemes(): String = WebProjects.iconThemes()

    fun listCreate(name: String, icon: String, categories: String): String = WebProjects.createList(name, icon, categories)

    fun listUpdate(text: String, path: String, icon: String, categories: String): String = WebProjects.updateList(text, path, icon, categories)

    fun listAddItem(text: String, path: String, title: String, category: String?, today: String): String =
        WebProjects.addListItem(text, path, title, category, today)

    fun listInclude(text: String, tasks: String, tag: String, category: String?): String = WebProjects.includeInList(text, tasks, tag, category)
}
