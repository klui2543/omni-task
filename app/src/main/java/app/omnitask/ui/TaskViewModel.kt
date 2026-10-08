package app.omnitask.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import app.omnitask.CrashLog
import app.omnitask.data.CalendarReader
import app.omnitask.data.ImageAttach
import app.omnitask.data.SettingsSync
import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.data.VaultRepository
import app.omnitask.model.DateBucket
import app.omnitask.model.Focus
import app.omnitask.model.Insight
import app.omnitask.model.Lang
import app.omnitask.model.Planner
import app.omnitask.model.Profile
import app.omnitask.model.QuickAdd
import app.omnitask.notify.Digest
import java.time.LocalDateTime
import app.omnitask.model.NoteLinks
import app.omnitask.model.OmniList
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
import app.omnitask.model.ReminderOn
import app.omnitask.model.Status
import app.omnitask.model.TaskKind
import app.omnitask.model.TaskQuery
import app.omnitask.notify.CalendarEvent
import app.omnitask.model.Task
import app.omnitask.model.UrgentRule
import app.omnitask.model.bucket
import app.omnitask.model.quadrant
import app.omnitask.model.tr
import app.omnitask.notify.NotifySettings
import app.omnitask.notify.Scheduler
import app.omnitask.widget.OmniWidgets
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Named filters the owner saved, as name to encoded filter. */
    val savedFilters: Map<String, String> = emptyMap(),
    /** List notes and their items, kept apart from the tasks so they never crowd the task views. */
    val lists: List<OmniList> = emptyList(),
    val listItems: List<Task> = emptyList(),
    /** Calendar events around the shown month; empty until calendar access is granted. */
    val events: List<CalendarEvent> = emptyList(),
    /** Null until the first load; then whether the app may read the phone's calendars. */
    val calendarAccess: Boolean? = null,
    val calendars: List<CalendarReader.Calendar> = emptyList(),
    val pendingImage: PendingImage? = null,
    /** The report of a crash since the app was last open, shown once so it can be copied. */
    val crash: String? = null,
    /** A parent being ticked while some subtasks are still open: ask whether to tick them too. */
    val closingParent: Task? = null,
    /** Vault-relative paths of every note, e.g. `📁 Folder/งาน/ประชุม.md`. */
    val notePaths: List<String> = emptyList(),
    val vaultName: String? = null,
    val profile: Profile = Profile(),
    val insight: Insight.Ask? = null,
    /** The assistant conversation for this session, newest last. */
    val chat: List<Chat> = emptyList(),
    /** When each no-deadline task was last reviewed, by title. */
    val reviewed: Map<String, LocalDate> = emptyMap(),
    /** Set when something outside the screens (a widget, a shortcut) asks for the quick-add sheet. */
    val quickAdd: QuickAddRequest? = null,
    /** The owner's own project order, and the starred ones that always sit on top. */
    val projectOrder: List<String> = emptyList(),
    val starred: Set<String> = emptySet(),
    /** Each project's task order (by title) and the projects whose order is enforced with 🆔/⛔. */
    val projectTaskOrder: Map<String, List<String>> = emptyMap(),
    val strictProjects: Set<String> = emptySet(),
) {
    val toReview get() = Focus.toReview(tasks, today, reviewed)

    val todayEvents get() = events.filter { it.begin.toLocalDate() <= today && it.end.toLocalDate() >= today && it.end > today.atStartOfDay() }

    /** The focus page reads every task in the vault, regardless of the list filters. */
    val brief get() = Focus.build(tasks, today, futureCount, skippedToday, dismissed)

    val tags get() = tasks.filter { it.list == null }.flatMap { it.tags }.distinct().sorted()

    /** Names of the list notes (Bucket list, Watch list...). */
    val listNames get() = lists.map { it.name }

    /** Tasks and list items together, for the task list when a list is chosen in the filter. */
    val allTasks get() = tasks + listItems

    /** A task's direct subtasks, in file order (which is their 1, 2, 3 order). */
    fun subtasksOf(task: Task): List<Task> = allTasks.filter { it.parent == task.key }.sortedBy { it.lineIndex }

    fun parentOf(task: Task): Task? = task.parent?.let { p -> allTasks.firstOrNull { it.key == p } }

    private val progressIndex: Map<String, Pair<Int, Int>> by lazy {
        allTasks.filter { it.parent != null && it.status != Status.CANCELLED }.groupBy { it.parent!! }
            .mapValues { (_, kids) -> kids.count { it.status == Status.DONE } to kids.size }
    }

    /** Done and total subtasks, for the "2/4" on a parent; null when it has none. */
    fun progressOf(task: Task): Pair<Int, Int>? = progressIndex[task.key]
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

/** Opens the quick-add sheet, typing or listening first; [assistant] jumps to the assistant instead. */
data class QuickAddRequest(val voice: Boolean = false, val assistant: Boolean = false)

/** One entry in the assistant conversation. */
sealed interface Chat {
    data class Asked(val text: String) : Chat
    data class Slots(val plan: Planner.Plan, val page: Int = 0, val picked: Int = 0, val done: String? = null) : Chat
    data class Today(val tasks: List<Task>) : Chat
    data class Review(val title: String, val lines: List<String>) : Chat
    /** "How long will it take?" before looking for time; [answered] is the minutes picked, -1 for "don't know". */
    data class Duration(val request: String, val answered: Int? = null) : Chat
    data class Agenda(val title: String, val summary: String, val days: List<AgendaDay>) : Chat
    data class RangePlan(
        val title: String,
        val proposals: List<Planner.Proposal>,
        val accepted: Set<Int> = emptySet(),
        val toCalendar: Boolean = false,
    ) : Chat
    data class Ranked(val items: List<Pair<Task, String>>) : Chat
}

data class AgendaDay(val day: LocalDate, val events: List<CalendarEvent>, val tasks: List<Task>)

/** A photo prepared for a task, waiting for the user to choose full size or reduced. */
data class PendingImage(val task: Task, val prepared: ImageAttach.Prepared)

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val thumbs = HashMap<String, Bitmap?>()

    /** The month the calendar view shows, so a reload keeps its events. */
    private var shownMonth: LocalDate? = null

    private val repo = VaultRepository(app)
    private val prefs = app.getSharedPreferences("omnitask", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(fromPrefs(UiState(vault = prefs.getString(KEY_VAULT, null)?.let(Uri::parse))))

    /** The settings kept in preferences (and synced through the vault file), laid over [base]. */
    private fun fromPrefs(base: UiState) = base.copy(
        urgentRule = prefs.getString(KEY_URGENT, null)
            ?.let { name -> UrgentRule.entries.firstOrNull { it.name == name } }
            ?: UrgentRule.THIS_WEEK,
        futureCount = prefs.getInt(KEY_FUTURE_COUNT, 1),
        skippedToday = prefs.getStringSet(KEY_SKIPPED, emptySet()).orEmpty()
            .filter { it.startsWith("${LocalDate.now()}|") }
            .map { it.substringAfter('|') }
            .toSet(),
        dismissed = prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet(),
        reviewed = prefs.getStringSet(KEY_REVIEWED, emptySet()).orEmpty().mapNotNull { e ->
            runCatching { e.substringBeforeLast('|') to LocalDate.parse(e.substringAfterLast('|')) }.getOrNull()
        }.toMap(),
        notify = Scheduler.loadSettings(getApplication()),
        projectOrder = prefs.getString(KEY_PROJECT_ORDER, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
        starred = prefs.getStringSet(KEY_STARRED, emptySet()).orEmpty().toSet(),
        projectTaskOrder = prefs.getStringSet(KEY_PROJECT_TASKS, emptySet()).orEmpty().associate { e ->
            e.substringBefore('\t') to e.substringAfter('\t', "").split('\u001F').filter { it.isNotEmpty() }
        },
        strictProjects = prefs.getStringSet(KEY_STRICT, emptySet()).orEmpty().toSet(),
        savedFilters = prefs.getStringSet(KEY_SAVED_FILTERS, emptySet()).orEmpty()
            .associate { it.substringBefore('\t') to it.substringAfter('\t', "") },
    )

    // Any settings change is copied to the vault file a moment later, batching quick taps into one write.
    private var saveJob: Job? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in SYNC_IGNORED) return@OnSharedPreferenceChangeListener
        val vault = _state.value.vault ?: return@OnSharedPreferenceChangeListener
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500)
            withContext(Dispatchers.IO) { runCatching { SettingsSync.save(getApplication(), repo, vault) } }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
    }

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

    fun showCrash(report: String) = _state.update { it.copy(crash = report) }

    fun dismissCrash() {
        CrashLog.clear(getApplication())
        _state.update { it.copy(crash = null) }
    }

    fun reload() {
        val vault = _state.value.vault ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // Settings saved from another phone (or before the app was cleared) come in first.
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    SettingsSync.load(getApplication(), repo, vault).also { loaded ->
                        // First run with this vault: start the file from what this phone has.
                        if (!loaded && repo.readPath(vault, SettingsSync.PATH) == null) SettingsSync.save(getApplication(), repo, vault)
                    }
                }.getOrDefault(false)
            }
            if (imported) {
                Lang.load(getApplication())
                _state.update { fromPrefs(it) }
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { repo.load(vault) }.onSuccess { snap ->
                    // Every load refreshes the reminders and the widgets, so edits made anywhere reach both.
                    runCatching { Scheduler.reschedule(getApplication(), snap.tasks) }
                }
            }
            val app = getApplication<Application>()
            launch { runCatching { OmniWidgets.refresh(app) } }
            val events = withContext(Dispatchers.IO) {
                runCatching {
                    val today = LocalDate.now()
                    // The month view plus the Gantt's three weeks, which can run into next month.
                    val now = CalendarReader.month(app, today) + CalendarReader.events(app, today.minusDays(7).atStartOfDay(), today.plusDays(35).atStartOfDay())
                    val other = shownMonth?.takeIf { it.withDayOfMonth(1) != LocalDate.now().withDayOfMonth(1) }?.let { CalendarReader.month(app, it) }.orEmpty()
                    (now + other).distinctBy { it.id to it.begin }
                }.getOrDefault(emptyList())
            }
            val calendars = withContext(Dispatchers.IO) { CalendarReader.calendars(app) }
            val profile = withContext(Dispatchers.IO) { runCatching { Profile.parse(repo.readPath(vault, Profile.PATH)) }.getOrDefault(_state.value.profile) }
            val vaultName = _state.value.vaultName ?: withContext(Dispatchers.IO) { runCatching { repo.vaultName(vault) }.getOrNull() }
            _state.update {
                it.copy(
                    events = events,
                    calendarAccess = CalendarReader.hasPermission(app),
                    calendars = calendars,
                    loading = false,
                    today = LocalDate.now(),
                    tasks = result.getOrNull()?.tasks?.filter { t -> t.list == null } ?: it.tasks,
                    listItems = result.getOrNull()?.tasks?.filter { t -> t.list != null } ?: it.listItems,
                    lists = result.getOrNull()?.lists ?: it.lists,
                    notePaths = result.getOrNull()?.notePaths ?: it.notePaths,
                    vaultName = vaultName,
                    profile = profile,
                    insight = Insight.next(result.getOrNull()?.tasks ?: it.tasks, doneLog(), profile, LocalDate.now(), declined()),
                    message = result.exceptionOrNull()?.let { e -> tr("อ่านตู้โน้ตไม่ได้: ${e.message}", "Cannot read the vault: ${e.message}") } ?: it.message,
                )
            }
        }
    }

    fun toggleDone(task: Task) {
        if (task.isOpen && _state.value.subtasksOf(task).any { it.isOpen }) {
            _state.update { it.copy(closingParent = task) }
            return
        }
        if (task.recurrence != null && task.isOpen) return completeRecurring(task)
        if (task.isOpen) logDone(task)
        edit(task) { TaskLine.setDone(it, task.isOpen, LocalDate.now()) }
    }

    /** Completes a repeating task by moving its line on to the next occurrence; no copy is added. */
    private fun completeRecurring(task: Task) {
        logDone(task)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.completeRecurring(task, LocalDate.now()) } }
            val text = result.fold(
                { made ->
                    if (made) {
                        val next = TaskLine.parse(TaskLine.advanceRecurring(task.raw, LocalDate.now()) ?: "")?.let { it.due ?: it.scheduled ?: it.start }
                        tr("เสร็จแล้ว รอบถัดไป ", "Done. Next: ") + (next?.format(SHORT_DATE) ?: "")
                    } else {
                        tr(
                            "อ่านรอบวนซ้ำ \"${task.recurrence}\" ไม่ได้ จึงยังไม่ได้ติ๊ก ลองติ๊กใน TaskForge",
                            "Could not read the repeat rule \"${task.recurrence}\", so the task was not ticked. Try TaskForge.",
                        )
                    }
                },
                { e ->
                    if (e is VaultRepository.ConflictException) {
                        tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere. Reloaded, please try again.")
                    } else {
                        tr("บันทึกไม่ได้: ", "Could not save: ") + e.message
                    }
                },
            )
            text?.let { t -> _state.update { it.copy(message = t) } }
            reload()
        }
    }

    fun setStatus(task: Task, status: Status) {
        if (status == Status.DONE && task.recurrence != null) return toggleDone(task)
        if (status == Status.DONE && task.isOpen) logDone(task)
        edit(task) { TaskLine.setStatus(it, status, LocalDate.now()) }
    }

    fun setQuery(query: TaskQuery) = _state.update { it.copy(query = query) }

    fun saveFilter(name: String) {
        val next = _state.value.savedFilters + (name.trim() to _state.value.query.encode())
        prefs.edit().putStringSet(KEY_SAVED_FILTERS, next.map { (k, v) -> "$k\t$v" }.toSet()).apply()
        _state.update { it.copy(savedFilters = next) }
    }

    fun deleteFilter(name: String) {
        val next = _state.value.savedFilters - name
        prefs.edit().putStringSet(KEY_SAVED_FILTERS, next.map { (k, v) -> "$k\t$v" }.toSet()).apply()
        _state.update { it.copy(savedFilters = next) }
    }

    fun applyFilter(name: String) {
        val line = _state.value.savedFilters[name] ?: return
        _state.update { it.copy(query = TaskQuery.decode(line, it.query)) }
    }

    /** Reads and encodes the photo off the main thread; small photos are saved straight away. */
    fun attachImage(task: Task, uri: Uri) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) { runCatching { ImageAttach.prepare(getApplication(), uri) } }
            prepared.onFailure { e -> _state.update { it.copy(message = tr("แนบรูปไม่ได้: ${e.message}", "Cannot attach image: ${e.message}")) } }
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
                it.copy(
                    message = result.fold(
                        { n -> tr("แนบรูปแล้ว ($n, ${ImageAttach.sizeLabel(bytes.size.toLong())})", "Image attached ($n, ${ImageAttach.sizeLabel(bytes.size.toLong())})") },
                        { e -> tr("แนบรูปไม่ได้: ${e.message}", "Cannot attach image: ${e.message}") },
                    ),
                )
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
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("ลบรูปไม่ได้: ${e.message}", "Cannot delete image: ${e.message}")) } }
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

    fun setRecurrence(task: Task, rule: String?) = edit(task) { TaskLine.setRecurrence(it, rule) }

    fun setReminder(task: Task, time: java.time.LocalTime?, on: ReminderOn) = edit(task) { TaskLine.setReminder(it, time, on) }

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

    /** Adds the events around another month, for the calendar view's month arrows. */
    fun loadMonth(day: LocalDate) {
        shownMonth = day
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) { runCatching { CalendarReader.month(getApplication(), day) }.getOrDefault(emptyList()) }
            if (more.isEmpty()) return@launch
            _state.update { s -> s.copy(events = (s.events + more).distinctBy { it.id to it.begin }) }
        }
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
        Focus.Kind.MARK_FUTURE -> setKind(s.task, TaskKind.FUTURE)
    }

    /** Switches the task's kind: the old kind tag goes, the new one is added. `who` names the person waiting. */
    fun setKind(task: Task, kind: TaskKind, who: String? = null) {
        markReviewed(task)
        edit(task) { raw ->
            var line = TaskKind.kindTags(task).fold(raw) { acc, tag -> TaskLine.removeTag(acc, tag) }
            kind.tag?.let { tag -> line = TaskLine.addTag(line, if (kind == TaskKind.WAITING && !who.isNullOrBlank()) "$tag/${who.trim()}" else tag) }
            line
        }
    }

    fun setFirstStep(task: Task, step: String?) =
        sub(task) { repo.setSubLine(task, Task.FIRST_STEP, step?.trim()?.ifEmpty { null }?.let { "${Task.FIRST_STEP} $it" }) }

    /** Restarts the review clock for a task without a deadline. */
    fun markReviewed(task: Task) {
        val next = _state.value.reviewed + (task.title to LocalDate.now())
        // Old entries for tasks that no longer exist are dropped as the set is rewritten.
        val live = _state.value.tasks.map { it.title }.toSet()
        val kept = next.filterKeys { it in live || it == task.title }
        prefs.edit().putStringSet(KEY_REVIEWED, kept.map { (k, v) -> "$k|$v" }.toSet()).apply()
        _state.update { it.copy(reviewed = kept) }
    }

    enum class ReviewAction { THIS_WEEK, FUTURE, SOMEDAY, KEEP, DROP }

    fun review(task: Task, action: ReviewAction) {
        when (action) {
            ReviewAction.THIS_WEEK -> { markReviewed(task); setDate(task, DateField.SCHEDULED, Focus.softDate(LocalDate.now())) }
            ReviewAction.FUTURE -> setKind(task, TaskKind.FUTURE)
            ReviewAction.SOMEDAY -> setKind(task, TaskKind.SOMEDAY)
            ReviewAction.KEEP -> markReviewed(task)
            ReviewAction.DROP -> { markReviewed(task); setStatus(task, Status.CANCELLED) }
        }
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
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")) } }
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
            val text = tr(
                "ด่วน/ไม่ด่วนมาจากวันครบกำหนด ลากได้แค่ขึ้นลง (สำคัญ ↔ ไม่สำคัญ)",
                "Urgency comes from the due date, so drag only up or down (important ↔ not important)",
            )
            _state.update { it.copy(message = text) }
            return
        }
        setPriority(task, if (to.important) Priority.HIGH else Priority.MEDIUM)
    }

    fun toggleStar(project: String) {
        val next = _state.value.starred.let { if (project in it) it - project else it + project }
        prefs.edit().putStringSet(KEY_STARRED, next).apply()
        _state.update { it.copy(starred = next) }
    }

    /**
     * A project's open tasks in the owner's order: tasks never placed come after, by date. The first is
     * the one to do next.
     */
    fun orderedProjectTasks(project: String, tasks: List<Task>): List<Task> {
        val rank = _state.value.projectTaskOrder[project].orEmpty().withIndex().associate { it.value to it.index }
        return tasks.sortedWith(compareBy<Task>({ rank[it.title] ?: Int.MAX_VALUE }, { it.due ?: it.scheduled ?: LocalDate.MAX }))
    }

    fun setProjectTaskOrder(project: String, ordered: List<Task>) {
        val next = _state.value.projectTaskOrder + (project to ordered.map { it.title })
        prefs.edit().putStringSet(KEY_PROJECT_TASKS, next.map { (k, v) -> k + "\t" + v.joinToString("\u001F") }.toSet()).apply()
        _state.update { it.copy(projectTaskOrder = next) }
        if (project in _state.value.strictProjects) writeChain(ordered, on = true)
    }

    /** Turns "do in order" on (each task waits for the one before it, via 🆔/⛔ in the file) or off. */
    fun setStrict(project: String, on: Boolean, ordered: List<Task>) {
        val next = _state.value.strictProjects.let { if (on) it + project else it - project }
        prefs.edit().putStringSet(KEY_STRICT, next).apply()
        _state.update { it.copy(strictProjects = next) }
        writeChain(ordered, on)
    }

    private fun writeChain(ordered: List<Task>, on: Boolean) {
        val ids = ordered.map { it.id ?: ("o" + java.util.UUID.randomUUID().toString().filter { c -> c.isLetterOrDigit() }.take(5)) }
        val own = ids.toSet()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ordered.forEachIndexed { i, t ->
                        // Keep any waits on tasks outside this project; replace the ones inside it.
                        val outside = t.dependsOn.filter { it !in own }
                        val deps = if (on && i > 0) outside + ids[i - 1] else outside
                        repo.rewriteLine(t) { raw -> TaskLine.setDependsOn(if (on) TaskLine.setId(raw, ids[i]) else raw, deps) }
                    }
                }
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกลำดับไม่ได้: ", "Could not save the order: ") + e.message) } }
            reload()
        }
    }

    /** Saves the order the owner dragged the projects into. */
    fun setProjectOrder(order: List<String>) {
        prefs.edit().putString(KEY_PROJECT_ORDER, order.joinToString("\n")).apply()
        _state.update { it.copy(projectOrder = order) }
    }

    /** Moves a project one place up or down within the list as currently shown. */
    fun moveProject(shown: List<String>, project: String, delta: Int) {
        val list = shown.toMutableList()
        val i = list.indexOf(project)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        prefs.edit().putString(KEY_PROJECT_ORDER, list.joinToString("\n")).apply()
        _state.update { it.copy(projectOrder = list) }
    }

    // ---- Subtasks and description ----

    /** Answers the "tick the open subtasks too?" question; null cancels. */
    fun closeParent(withSubtasks: Boolean?) {
        val parent = _state.value.closingParent ?: return
        _state.update { it.copy(closingParent = null) }
        if (withSubtasks == null) return
        val open = if (withSubtasks) _state.value.subtasksOf(parent).filter { it.isOpen } else emptyList()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val today = LocalDate.now()
                    open.forEach { child -> repo.rewriteLine(child) { TaskLine.setDone(it, true, today) } }
                    if (parent.recurrence != null) repo.completeRecurring(parent, today) else repo.rewriteLine(parent) { TaskLine.setDone(it, true, today) }
                }
            }
            logDone(parent)
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกไม่ได้: ", "Could not save: ") + e.message) } }
            reload()
        }
    }

    /** Adds a subtask; the text is read like quick add, so "พรุ่งนี้ 9:00 #tag" works here too. */
    fun addSubtask(parent: Task, text: String) {
        if (text.isBlank()) return
        val line = QuickAdd.parse(text, LocalDate.now()).line(LocalDate.now())
        sub(parent) { repo.addSubtask(parent, line) }
    }

    fun reorderSubtasks(parent: Task, ordered: List<Task>) = sub(parent) { repo.reorderSubtasks(parent, ordered.map { it.raw }) }

    fun setDescription(task: Task, text: String) {
        if (text.trim() == task.description.trim()) return
        sub(task) { repo.setDescription(task, text) }
    }

    /** A long description moves to its own note in the vault, linked under the task. */
    fun moveDescriptionToNote(task: Task, text: String) {
        val vault = _state.value.vault ?: return
        val name = task.title.replace(Regex("""[\\/:*?"<>|#^\[\]]"""), " ").replace(Regex("""\s+"""), " ").trim().take(80).ifEmpty { "Task" }
        val path = "${OmniList.FOLDER}/Notes/$name.md"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val existing = repo.readPath(vault, path)
                    repo.writePath(vault, path, (existing?.trimEnd()?.plus("\n\n") ?: "# $name\n\n") + text.trim() + "\n")
                    repo.setDescription(task, "")
                }
            }
            val message = result.exceptionOrNull()?.let { e -> tr("ย้ายไม่ได้: ", "Could not move: ") + e.message } ?: run {
                // The task line is unchanged by the description edit, so the link can go under it right after.
                withContext(Dispatchers.IO) { runCatching { repo.addSubLine(task, "[[$name]]") } }
                tr("ย้ายรายละเอียดไปที่ $path แล้ว", "Moved the description to $path")
            }
            _state.update { it.copy(message = message) }
            reload()
        }
    }

    // ---- Lists ----

    /** Writes the starter lists (Bucket list, Watch list) the first time the owner opens the lists. */
    fun ensureStarterLists() {
        val vault = _state.value.vault ?: return
        if (_state.value.lists.isNotEmpty() || prefs.getBoolean(KEY_LISTS_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_LISTS_SEEDED, true).apply()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OmniList.starters().forEach { l -> runCatching { if (repo.readPath(vault, l.path) == null) repo.writePath(vault, l.path, l.render()) } }
            }
            reload()
        }
    }

    fun createList(name: String, icon: String, categories: List<String>) {
        val vault = _state.value.vault ?: return
        val clean = name.trim().replace(Regex("""[\\/:*?"<>|#^\[\]]"""), " ").trim()
        if (clean.isEmpty()) return
        val list = OmniList(clean, "${OmniList.FOLDER}/$clean.md", icon, categories)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { if (repo.readPath(vault, list.path) == null) repo.writePath(vault, list.path, list.render()) else error(tr("มีรายการชื่อนี้แล้ว", "A list with this name exists")) }
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = e.message) } }
            reload()
        }
    }

    /** Changes a list's icon or categories by rewriting its header; the items stay as they are. */
    fun updateList(list: OmniList, icon: String = list.icon, categories: List<String> = list.categories) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val text = repo.readPath(vault, list.path) ?: return@runCatching
                    val end = text.indexOf("\n---", 3)
                    if (!text.startsWith("---") || end < 0) return@runCatching
                    val body = text.substring(end + 4).trimStart('\r', '\n')
                    val head = list.copy(icon = icon, categories = categories).render().substringBefore("# ")
                    repo.writePath(vault, list.path, head + body)
                }
            }
            reload()
        }
    }

    fun addListItem(list: OmniList, title: String, category: String?) {
        val vault = _state.value.vault ?: return
        if (title.isBlank()) return
        var line = "- [ ] ${title.trim()}"
        if (category != null) line = TaskLine.addTag(line, category)
        line = TaskLine.setDate(line, DateField.CREATED, LocalDate.now())
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.appendLine(vault, list.path, line) } }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("เพิ่มไม่ได้: ", "Cannot add: ") + e.message) } }
            reload()
        }
    }

    fun requestQuickAdd(request: QuickAddRequest?) = _state.update { it.copy(quickAdd = request) }

    /** Adds a task typed in the quick-add sheet to the TaskForge file. */
    fun quickAdd(draft: QuickAdd.Draft) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repo.appendLine(vault, VaultRepository.TASK_FILE, draft.line(LocalDate.now())) }
            }
            val text = result.fold(
                { tr("เพิ่มงานแล้ว: ${draft.title}", "Added: ${draft.title}") },
                { e -> tr("เพิ่มงานไม่ได้: ${e.message}", "Cannot add task: ${e.message}") },
            )
            _state.update { it.copy(message = text) }
            reload()
        }
    }

    // ---- Assistant ----

    /** Reads what the owner asked for: a look-ahead, a plan for a range, a ranking, or time for something new. */
    fun ask(text: String) {
        val t = text.lowercase()
        val today = LocalDate.now()
        val range = Planner.rangeOf(text, today)
        when {
            listOf("วางแผน", "plan ").any { it in "$t " } && !t.startsWith("plan my day") ->
                planRange(range ?: (today to today.plusDays(6)), text)
            listOf("มีอะไร", "what's on", "whats on", "what do i have", "agenda").any { it in t } ->
                showAgenda(range ?: (today to today.plusDays(6)), text)
            listOf("จัดลำดับ", "ทำอะไรก่อน", "ควรทำอะไร", "prioritize", "priorities", "what first").any { it in t } -> rankAll(text)
            Planner.explicitMinutes(text) != null -> {
                val s = _state.value
                val plan = Planner.plan(text, s.tasks, s.events, s.profile, LocalDateTime.now())
                _state.update { it.copy(chat = it.chat + Chat.Asked(text) + Chat.Slots(plan)) }
            }
            else -> _state.update { it.copy(chat = it.chat + Chat.Asked(text) + Chat.Duration(text)) }
        }
    }

    /** The owner said how long it takes (or -1: "don't know"); now find the time. */
    fun answerDuration(index: Int, minutes: Int) {
        val item = _state.value.chat.getOrNull(index) as? Chat.Duration ?: return
        if (item.answered != null) return
        val s = _state.value
        val plan = Planner.plan(item.request, s.tasks, s.events, s.profile, LocalDateTime.now(), minutes.takeIf { it > 0 })
        _state.update {
            it.copy(chat = it.chat.toMutableList().also { list -> list[index] = item.copy(answered = minutes) } + Chat.Slots(plan))
        }
    }

    fun rankAll(asked: String = tr("จัดลำดับงานทั้งหมดให้หน่อย", "Prioritize all my tasks")) {
        val s = _state.value
        _state.update { it.copy(chat = it.chat + Chat.Asked(asked) + Chat.Ranked(Focus.rank(s.tasks, s.today).take(12))) }
    }

    /** What is coming in a range, from both the vault and Google Calendar, day by day. */
    fun showAgenda(range: Pair<LocalDate, LocalDate>, asked: String) {
        val (from, to) = range
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) {
                runCatching { CalendarReader.events(getApplication(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()) }.getOrDefault(emptyList())
            }
            val tasks = _state.value.tasks.filter { it.status != Status.CANCELLED && !Focus.isSomeday(it) }
            val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { d ->
                AgendaDay(
                    d,
                    events.filter { it.begin.toLocalDate() <= d && (it.end.minusNanos(1).toLocalDate() >= d) }.sortedBy { it.begin },
                    tasks.filter { t -> t.isOpen && (t.due == d || t.scheduled == d) }.sortedBy { it.priority.ordinal },
                )
            }.toList()
            val busiest = days.maxByOrNull { it.events.count { e -> !e.allDay } + it.tasks.size }
            val due = days.sumOf { d -> d.tasks.count { it.due == d.day } }
            val summary = tr(
                "นัด ${events.size} รายการ งานครบกำหนด $due งาน",
                "${events.size} events, $due tasks due",
            ) + (busiest?.takeIf { it.events.size + it.tasks.size > 2 }?.let { b ->
                tr(" วันที่แน่นสุดคือ ", ". Busiest: ") + b.day.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Lang.locale))
            } ?: "")
            val title = rangeTitle(from, to)
            _state.update { it.copy(chat = it.chat + Chat.Asked(asked) + Chat.Agenda(title, summary, days.filter { d -> d.events.isNotEmpty() || d.tasks.isNotEmpty() })) }
        }
    }

    /** Places undated work into the free time of a range; nothing is written until a proposal is accepted. */
    fun planRange(range: Pair<LocalDate, LocalDate>, asked: String) {
        val (from, to) = range
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) {
                runCatching { CalendarReader.events(getApplication(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()) }.getOrDefault(emptyList())
            }
            val s = _state.value
            val candidates = Focus.rank(s.tasks, today).map { it.first }.filter { t ->
                (t.scheduled == null || t.scheduled < today) && (t.due == null || t.due >= from) && t.reminderTime == null
            }.take(12)
            val days = generateSequence(maxOf(from, today)) { it.plusDays(1) }.takeWhile { it <= to }.toList()
            val proposals = withContext(Dispatchers.Default) { Planner.planRange(candidates, days, s.tasks, events, s.profile, now) }
            _state.update { it.copy(chat = it.chat + Chat.Asked(asked) + Chat.RangePlan(rangeTitle(from, to), proposals)) }
        }
    }

    private fun rangeTitle(from: LocalDate, to: LocalDate): String {
        val f = java.time.format.DateTimeFormatter.ofPattern("d MMM", Lang.locale)
        return if (from == to) from.format(f) else from.format(f) + tr(" ถึง ", " to ") + to.format(f)
    }

    private fun updatePlan(index: Int, change: (Chat.RangePlan) -> Chat.RangePlan) = _state.update {
        val item = it.chat.getOrNull(index) as? Chat.RangePlan ?: return@update it
        it.copy(chat = it.chat.toMutableList().also { list -> list[index] = change(item) })
    }

    fun toggleRangeCalendar(index: Int) = updatePlan(index) { it.copy(toCalendar = !it.toCalendar) }

    /** Writes accepted proposals: ⏳ the day and 🎯 the time on each task, and a calendar event if asked. */
    fun acceptProposals(index: Int, which: List<Int>) {
        val item = _state.value.chat.getOrNull(index) as? Chat.RangePlan ?: return
        val todo = which.filter { it !in item.accepted }
        if (todo.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) {
                val cal = if (item.toCalendar) CalendarReader.primaryWritable(app) else null
                todo.filter { i ->
                    val p = item.proposals[i]
                    runCatching {
                        repo.rewriteLine(p.task) { raw ->
                            TaskLine.setReminder(TaskLine.setDate(raw, DateField.SCHEDULED, p.slot.day), p.slot.start, ReminderOn.SCHEDULED)
                        }
                        cal?.let { c ->
                            val start = p.slot.day.atTime(p.slot.start)
                            CalendarReader.insert(app, c.id, p.task.title, start, start.plusMinutes(p.minutes.toLong()))
                        }
                    }.isSuccess
                }
            }
            updatePlan(index) { it.copy(accepted = it.accepted + done) }
            if (done.size < todo.size) _state.update { it.copy(message = tr("บางงานบันทึกไม่ได้ ไฟล์อาจถูกแก้จากที่อื่น", "Some tasks could not be saved; the file may have changed")) }
            reload()
        }
    }

    /** "None of these": add the task as an all-day item on [day], or at a time the owner picks. */
    fun confirmCustom(index: Int, title: String, day: LocalDate, time: java.time.LocalTime?, minutes: Int) {
        val vault = _state.value.vault ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val today = LocalDate.now()
                    val line = if (time != null) {
                        Planner.taskLine(title, Planner.Slot(day, time, time.plusMinutes(minutes.toLong()), "", "", 0), today)
                    } else {
                        "- [ ] $title ➕ $today ⏳ $day"
                    }
                    repo.appendLine(vault, VaultRepository.TASK_FILE, line)
                    CalendarReader.primaryWritable(app)?.let { cal ->
                        if (time != null) CalendarReader.insert(app, cal.id, title, day.atTime(time), day.atTime(time).plusMinutes(minutes.toLong()))
                        else CalendarReader.insertAllDay(app, cal.id, title, day)
                    }
                }
            }
            result.fold(
                { eventId ->
                    updateSlots(index) { it.copy(done = if (eventId != null) tr("เพิ่มงาน + ลงปฏิทินแล้ว", "Task added + on calendar") else tr("เพิ่มงานแล้ว (ยังลงปฏิทินไม่ได้)", "Task added (not on calendar)")) }
                },
                { e -> _state.update { it.copy(message = tr("เพิ่มงานไม่ได้: ${e.message}", "Cannot add task: ${e.message}")) } },
            )
            reload()
        }
    }

    fun planToday() {
        val b = _state.value.brief
        _state.update { it.copy(chat = it.chat + Chat.Asked(tr("จัดลำดับวันนี้ให้หน่อย", "Plan my day")) + Chat.Today((b.must + b.waiting + b.future).distinct())) }
    }

    fun reviewWeek() {
        val s = _state.value
        val m = Digest.weekly(s.tasks, s.today)
        _state.update { it.copy(chat = it.chat + Chat.Asked(tr("ทบทวนสัปดาห์นี้", "Review this week")) + Chat.Review(m.title, m.lines)) }
    }

    fun clearChat() = _state.update { it.copy(chat = emptyList()) }

    private fun updateSlots(index: Int, change: (Chat.Slots) -> Chat.Slots) = _state.update {
        val item = it.chat.getOrNull(index) as? Chat.Slots ?: return@update it
        it.copy(chat = it.chat.toMutableList().also { list -> list[index] = change(item) })
    }

    fun pickSlot(index: Int, slot: Int) = updateSlots(index) { it.copy(picked = slot, done = null) }

    fun moreSlots(index: Int) = updateSlots(index) {
        val pages = (it.plan.slots.size + 2) / 3
        val next = if (pages == 0) 0 else (it.page + 1) % pages
        it.copy(page = next, picked = next * 3, done = null)
    }

    /** Adds the chosen slot as a TaskForge task, and as a calendar event when the calendar can be written. */
    fun confirmSlot(index: Int, title: String) {
        val vault = _state.value.vault ?: return
        val item = _state.value.chat.getOrNull(index) as? Chat.Slots ?: return
        val slot = item.plan.slots.getOrNull(item.picked) ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.appendLine(vault, VaultRepository.TASK_FILE, Planner.taskLine(title, slot, LocalDate.now()))
                    CalendarReader.primaryWritable(app)?.let { cal ->
                        CalendarReader.insert(app, cal.id, title, slot.day.atTime(slot.start), slot.day.atTime(slot.end))
                    }
                }
            }
            result.fold(
                { eventId -> updateSlots(index) { it.copy(
                        done = if (eventId != null) {
                            tr("เพิ่มงาน + ลงปฏิทินแล้ว", "Task added + on calendar")
                        } else {
                            tr("เพิ่มงานแล้ว (ยังลงปฏิทินไม่ได้)", "Task added (not on calendar)")
                        },
                    ) } },
                { e -> _state.update { it.copy(message = tr("เพิ่มงานไม่ได้: ${e.message}", "Cannot add task: ${e.message}")) } },
            )
            reload()
        }
    }

    fun answerInsight(yes: Boolean) {
        val ask = _state.value.insight ?: return
        if (yes) {
            val p = _state.value.profile
            saveProfile(ask.apply(p).copy(remembered = (p.remembered + ask.remember).distinct()))
        } else {
            prefs.edit().putStringSet(KEY_DECLINED, declined() + ask.id).apply()
        }
        _state.update { it.copy(insight = null) }
    }

    /** Writes the profile note; saving also counts as "still true", so the re-ask clock restarts. */
    fun saveProfile(profile: Profile) {
        val vault = _state.value.vault ?: return
        val next = profile.copy(exists = true, updated = LocalDate.now())
        _state.update { it.copy(profile = next) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.writePath(vault, Profile.PATH, next.render()) } }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกโปรไฟล์ไม่ได้: ${e.message}", "Cannot save profile: ${e.message}")) } }
        }
    }

    private fun declined() = prefs.getStringSet(KEY_DECLINED, emptySet()).orEmpty().toSet()

    private fun doneLog(): List<Insight.Done> = prefs.getStringSet(KEY_DONE_LOG, emptySet()).orEmpty().mapNotNull { e ->
        val at = runCatching { LocalDateTime.parse(e.substringBefore('|')) }.getOrNull() ?: return@mapNotNull null
        val kind = Planner.Kind.entries.firstOrNull { it.name == e.substringAfter('|') } ?: return@mapNotNull null
        Insight.Done(at, kind)
    }

    /** Remembers when work gets done, so the assistant can notice the owner's rhythm. */
    private fun logDone(task: Task) {
        val entry = "${LocalDateTime.now().withNano(0)}|${Planner.kindOf(task.title).name}"
        val kept = prefs.getStringSet(KEY_DONE_LOG, emptySet()).orEmpty().sorted().takeLast(199)
        prefs.edit().putStringSet(KEY_DONE_LOG, (kept + entry).toSet()).apply()
    }

    private fun edit(task: Task, transform: (String) -> String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.rewriteLine(task, transform) } }
            result.exceptionOrNull()?.let { e ->
                val text = if (e is VaultRepository.ConflictException) {
                    tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere and was reloaded. Try again.")
                } else {
                    tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")
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
        const val KEY_DECLINED = "declinedInsights"
        const val KEY_DONE_LOG = "doneLog"
        const val KEY_REVIEWED = "reviewedTasks"
        const val KEY_PROJECT_ORDER = "projectOrder"
        const val KEY_STARRED = "starredProjects"
        const val KEY_PROJECT_TASKS = "projectTaskOrder"
        const val KEY_STRICT = "strictProjects"
        const val KEY_SAVED_FILTERS = "savedFilters"
        const val KEY_LISTS_SEEDED = "lists.seeded"

        /** Keys that change on their own (alarm bookkeeping, sync stamps) and must not trigger a settings save. */
        val SYNC_IGNORED = setOf("alarmIds", "settings.syncedAt", KEY_VAULT)
    }
}
