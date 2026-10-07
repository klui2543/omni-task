package app.omnitask.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import app.omnitask.data.CalendarReader
import app.omnitask.data.ImageAttach
import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.data.VaultRepository
import app.omnitask.model.DateBucket
import app.omnitask.model.Focus
import app.omnitask.model.NoteLinks
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
import app.omnitask.model.Status
import app.omnitask.model.TaskQuery
import app.omnitask.notify.CalendarEvent
import app.omnitask.model.Task
import app.omnitask.model.UrgentRule
import app.omnitask.model.bucket
import app.omnitask.model.quadrant
import app.omnitask.notify.NotifySettings
import app.omnitask.notify.Scheduler
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The filter set shared by the List and Eisenhower views. */
data class Filters(
    val bucket: DateBucket? = null,
    val tag: String? = null,
    val note: String? = null,
    val showDone: Boolean = false,
)

data class UiState(
    val vault: Uri? = null,
    val loading: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val filters: Filters = Filters(),
    val urgentRule: UrgentRule = UrgentRule.THIS_WEEK,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
    val futureCount: Int = 1,
    val skippedToday: Set<String> = emptySet(),
    val dismissed: Set<String> = emptySet(),
    val notify: NotifySettings = NotifySettings(),
    val query: TaskQuery = TaskQuery(),
    val savedView: Int = 0,
    /** Calendar events around the shown month; empty until calendar access is granted. */
    val events: List<CalendarEvent> = emptyList(),
    /** Null until the first load; then whether the app may read the phone's calendars. */
    val calendarAccess: Boolean? = null,
    val calendars: List<CalendarReader.Calendar> = emptyList(),
    val pendingImage: PendingImage? = null,
    /** Vault-relative paths of every note, e.g. `📁 Folder/งาน/ประชุม.md`. */
    val notePaths: List<String> = emptyList(),
    val vaultName: String? = null,
) {
    val todayEvents get() = events.filter { it.begin.toLocalDate() <= today && it.end.toLocalDate() >= today && it.end > today.atStartOfDay() }

    /** The focus page reads every task in the vault, regardless of the list filters. */
    val brief get() = Focus.build(tasks, today, futureCount, skippedToday, dismissed)

    val tags get() = tasks.flatMap { it.tags }.distinct().sorted()
    val notes get() = tasks.map { it.noteName }.distinct().sorted()

    /** Everything but the date filter, so the date tabs can show a count each. */
    val scoped
        get() = tasks.filter { t ->
            (filters.showDone || t.isOpen) &&
                (filters.tag == null || filters.tag in t.tags) &&
                (filters.note == null || filters.note == t.noteName)
        }

    val visible get() = scoped.filter { filters.bucket == null || it.bucket(today) == filters.bucket }
}

/** A photo prepared for a task, waiting for the user to choose full size or reduced. */
data class PendingImage(val task: Task, val prepared: ImageAttach.Prepared)

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val thumbs = HashMap<String, Bitmap?>()

    private val repo = VaultRepository(app)
    private val prefs = app.getSharedPreferences("omnitask", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            vault = prefs.getString(KEY_VAULT, null)?.let(Uri::parse),
            urgentRule = prefs.getString(KEY_URGENT, null)
                ?.let { name -> UrgentRule.entries.firstOrNull { it.name == name } }
                ?: UrgentRule.THIS_WEEK,
            futureCount = prefs.getInt(KEY_FUTURE_COUNT, 1),
            skippedToday = prefs.getStringSet(KEY_SKIPPED, emptySet()).orEmpty()
                .filter { it.startsWith("${LocalDate.now()}|") }
                .map { it.substringAfter('|') }
                .toSet(),
            dismissed = prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet(),
            notify = Scheduler.loadSettings(app),
        )
    )
    val state: StateFlow<UiState> = _state

    fun setVault(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_VAULT, uri.toString()).apply()
        _state.update { it.copy(vault = uri, vaultName = null) }
        reload()
    }

    fun setUrgentRule(rule: UrgentRule) {
        prefs.edit().putString(KEY_URGENT, rule.name).apply()
        _state.update { it.copy(urgentRule = rule) }
    }

    fun setFilters(filters: Filters) = _state.update { it.copy(filters = filters) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun reload() {
        val vault = _state.value.vault ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repo.load(vault) }.onSuccess { snap ->
                    // Every load refreshes the reminders, so edits made anywhere reach the alarms.
                    runCatching { Scheduler.reschedule(getApplication(), snap.tasks) }
                }
            }
            val app = getApplication<Application>()
            val events = withContext(Dispatchers.IO) {
                runCatching { CalendarReader.month(app, LocalDate.now()) }.getOrDefault(emptyList())
            }
            val calendars = withContext(Dispatchers.IO) { CalendarReader.calendars(app) }
            val vaultName = _state.value.vaultName ?: withContext(Dispatchers.IO) { runCatching { repo.vaultName(vault) }.getOrNull() }
            _state.update {
                it.copy(
                    events = events,
                    calendarAccess = CalendarReader.hasPermission(app),
                    calendars = calendars,
                    loading = false,
                    today = LocalDate.now(),
                    tasks = result.getOrNull()?.tasks ?: it.tasks,
                    notePaths = result.getOrNull()?.notePaths ?: it.notePaths,
                    vaultName = vaultName,
                    message = result.exceptionOrNull()?.let { e -> "อ่านตู้โน้ตไม่ได้: ${e.message}" } ?: it.message,
                )
            }
        }
    }

    fun toggleDone(task: Task) {
        // Completing a repeating task must also create its next occurrence, which arrives in phase 2.
        if (task.recurrence != null && task.isOpen) {
            _state.update { it.copy(message = "งานวนซ้ำยังติ๊กในแอปนี้ไม่ได้ (ช่วง 2) ติ๊กใน TaskForge ไปก่อน") }
            return
        }
        edit(task) { TaskLine.setDone(it, task.isOpen, LocalDate.now()) }
    }

    fun setStatus(task: Task, status: Status) {
        if (status == Status.DONE && task.recurrence != null) return toggleDone(task)
        edit(task) { TaskLine.setStatus(it, status, LocalDate.now()) }
    }

    fun setQuery(query: TaskQuery) = _state.update { it.copy(query = query, savedView = -1) }

    fun pickSavedView(index: Int) = _state.update {
        it.copy(query = TaskQuery.SAVED[index].second(it.query), savedView = index)
    }

    /** Reads and encodes the photo off the main thread; small photos are saved straight away. */
    fun attachImage(task: Task, uri: Uri) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) { runCatching { ImageAttach.prepare(getApplication(), uri) } }
            prepared.onFailure { e -> _state.update { it.copy(message = "แนบรูปไม่ได้: ${e.message}") } }
            prepared.onSuccess { p ->
                if (p.needsChoice) _state.update { it.copy(pendingImage = PendingImage(task, p)) } else saveImage(task, p.full)
            }
        }
    }

    fun resolvePendingImage(reduced: Boolean?) {
        val pending = _state.value.pendingImage ?: return
        _state.update { it.copy(pendingImage = null) }
        if (reduced == null) return
        saveImage(pending.task, if (reduced) pending.prepared.reduced ?: pending.prepared.full else pending.prepared.full)
    }

    private fun saveImage(task: Task, bytes: ByteArray) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = repo.saveAttachment(vault, ImageAttach.fileName(), "image/webp", bytes)
                    repo.addSubLine(task, "![[$name]]")
                    name
                }
            }
            _state.update {
                it.copy(message = result.fold({ n -> "แนบรูปแล้ว ($n, ${ImageAttach.sizeLabel(bytes.size.toLong())})" }, { e -> "แนบรูปไม่ได้: ${e.message}" }))
            }
            reload()
        }
    }

    fun removeImage(task: Task, name: String) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.removeSubLine(task, "![[$name")
                    repo.deleteAttachment(vault, name)
                }
            }
            thumbs.remove(name)
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = "ลบรูปไม่ได้: ${e.message}") } }
            reload()
        }
    }

    /** Small preview of an attachment, cached for the session. */
    suspend fun thumbnail(name: String): Bitmap? {
        if (thumbs.containsKey(name)) return thumbs[name]
        val vault = _state.value.vault ?: return null
        val bmp = withContext(Dispatchers.IO) {
            runCatching { repo.readAttachment(vault, name)?.let { ImageAttach.thumbnail(it) } }.getOrNull()
        }
        thumbs[name] = bmp
        return bmp
    }

    /** Full-size image bytes for the viewer. */
    suspend fun image(name: String): Bitmap? {
        val vault = _state.value.vault ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { repo.readAttachment(vault, name)?.let { ImageAttach.thumbnail(it, 1600) } }.getOrNull()
        }
    }

    fun setPriority(task: Task, priority: Priority) = edit(task) { TaskLine.setPriority(it, priority) }

    fun setDate(task: Task, field: DateField, value: LocalDate?) = edit(task) { TaskLine.setDate(it, field, value) }

    fun setNotify(settings: NotifySettings) {
        Scheduler.saveSettings(getApplication(), settings)
        _state.update { it.copy(notify = settings) }
        val tasks = _state.value.tasks
        viewModelScope.launch(Dispatchers.IO) { runCatching { Scheduler.reschedule(getApplication(), tasks) } }
    }

    /** After a permission is granted (calendar, notifications) the plan can include more. */
    fun refreshAlarms() {
        val tasks = _state.value.tasks
        viewModelScope.launch(Dispatchers.IO) { runCatching { Scheduler.reschedule(getApplication(), tasks) } }
    }

    /** Calendar access just changed: read the events again and let the alarms pick them up. */
    fun calendarChanged() = reload()

    fun setFutureCount(count: Int) {
        prefs.edit().putInt(KEY_FUTURE_COUNT, count).apply()
        _state.update { it.copy(futureCount = count) }
    }

    /** Hides a future-work task for today only, so tomorrow's pick rotates to the next one. */
    fun skipFuture(task: Task) {
        val today = LocalDate.now().toString()
        val kept = prefs.getStringSet(KEY_SKIPPED, emptySet()).orEmpty().filter { it.startsWith("$today|") }
        prefs.edit().putStringSet(KEY_SKIPPED, (kept + "$today|${task.title}").toSet()).apply()
        _state.update { it.copy(skippedToday = it.skippedToday + task.title) }
    }

    fun dismissSuggestion(s: Focus.Suggestion) {
        val next = _state.value.dismissed + s.id
        prefs.edit().putStringSet(KEY_DISMISSED, next).apply()
        _state.update { it.copy(dismissed = next) }
    }

    fun acceptSuggestion(s: Focus.Suggestion) = when (s.kind) {
        Focus.Kind.RAISE_PRIORITY -> setPriority(s.task, Priority.HIGH)
        Focus.Kind.SOFT_DATE -> setDate(s.task, DateField.SCHEDULED, Focus.softDate(LocalDate.now()))
    }

    /** Links a note on its own line under the task, the same way Obsidian writes `[[links]]`. */
    fun addLink(task: Task, notePath: String) {
        val name = NoteLinks.linkText(notePath, _state.value.notePaths)
        if (name in task.links) return
        sub(task) { repo.addSubLine(task, "[[$name]]") }
    }

    fun removeLink(task: Task, name: String) = sub(task) { repo.removeSubLine(task, "[[$name") }

    private fun sub(task: Task, write: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(write) }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = "บันทึกไม่ได้: ${e.message}") } }
            reload()
        }
    }

    fun addTag(task: Task, tag: String) = edit(task) { TaskLine.addTag(it, tag) }

    fun removeTag(task: Task, tag: String) = edit(task) { TaskLine.removeTag(it, tag) }

    /**
     * Dragging between quadrants changes importance only (priority ⏫ in, 🔼 out).
     * Urgency comes from the dates, so a move across the urgent / not-urgent line is refused.
     */
    fun moveToQuadrant(task: Task, to: Quadrant) {
        val s = _state.value
        val from = task.quadrant(s.today, s.urgentRule)
        if (from == to) return
        if (from.urgent != to.urgent) {
            _state.update { it.copy(message = "ด่วน/ไม่ด่วนมาจากวันครบกำหนด ลากได้แค่ขึ้นลง (สำคัญ ↔ ไม่สำคัญ)") }
            return
        }
        setPriority(task, if (to.important) Priority.HIGH else Priority.MEDIUM)
    }

    private fun edit(task: Task, transform: (String) -> String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.rewriteLine(task, transform) } }
            result.exceptionOrNull()?.let { e ->
                val text = if (e is VaultRepository.ConflictException) {
                    "ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง"
                } else {
                    "บันทึกไม่ได้: ${e.message}"
                }
                _state.update { it.copy(message = text) }
            }
            reload()
        }
    }

    private companion object {
        const val KEY_VAULT = "vault"
        const val KEY_URGENT = "urgentRule"
        const val KEY_FUTURE_COUNT = "futureCount"
        const val KEY_SKIPPED = "skippedFuture"
        const val KEY_DISMISSED = "dismissedSuggestions"
    }
}
