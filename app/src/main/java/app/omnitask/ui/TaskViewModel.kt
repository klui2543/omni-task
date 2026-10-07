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
import app.omnitask.model.Insight
import app.omnitask.model.Planner
import app.omnitask.model.Profile
import app.omnitask.notify.Digest
import java.time.LocalDateTime
import app.omnitask.model.NoteLinks
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
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
    val profile: Profile = Profile(),
    val insight: Insight.Ask? = null,
    /** The assistant conversation for this session, newest last. */
    val chat: List<Chat> = emptyList(),
    /** When each no-deadline task was last reviewed, by title. */
    val reviewed: Map<String, LocalDate> = emptyMap(),
) {
    val toReview get() = Focus.toReview(tasks, today, reviewed)

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

/** One entry in the assistant conversation. */
sealed interface Chat {
    data class Asked(val text: String) : Chat
    data class Slots(val plan: Planner.Plan, val page: Int = 0, val picked: Int = 0, val done: String? = null) : Chat
    data class Today(val tasks: List<Task>) : Chat
    data class Review(val title: String, val lines: List<String>) : Chat
}

/** A photo prepared for a task, waiting for the user to choose full size or reduced. */
data class PendingImage(val task: Task, val prepared: ImageAttach.Prepared)

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val thumbs = HashMap<String, Bitmap?>()

    /** The month the calendar view shows, so a reload keeps its events. */
    private var shownMonth: LocalDate? = null

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
            reviewed = prefs.getStringSet(KEY_REVIEWED, emptySet()).orEmpty().mapNotNull { e ->
                runCatching { e.substringBeforeLast('|') to LocalDate.parse(e.substringAfterLast('|')) }.getOrNull()
            }.toMap(),
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
                runCatching {
                    val now = CalendarReader.month(app, LocalDate.now())
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
                    tasks = result.getOrNull()?.tasks ?: it.tasks,
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
        if (task.recurrence != null && task.isOpen) return completeRecurring(task)
        if (task.isOpen) logDone(task)
        edit(task) { TaskLine.setDone(it, task.isOpen, LocalDate.now()) }
    }

    /** Ticks a repeating task and adds its next occurrence above it, as the Tasks plugin does. */
    private fun completeRecurring(task: Task) {
        logDone(task)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.completeRecurring(task, LocalDate.now()) } }
            val text = result.fold(
                { made ->
                    if (made) null else tr(
                        "อ่านรอบวนซ้ำ \"${task.recurrence}\" ไม่ได้ จึงยังไม่ได้ติ๊ก ลองติ๊กใน TaskForge",
                        "Could not read the repeat rule \"${task.recurrence}\", so the task was not ticked. Try TaskForge.",
                    )
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

    fun setQuery(query: TaskQuery) = _state.update { it.copy(query = query, savedView = -1) }

    fun pickSavedView(index: Int) = _state.update {
        it.copy(query = TaskQuery.SAVED[index].second(it.query), savedView = index)
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

    fun setReminder(task: Task, time: java.time.LocalTime?, on: app.omnitask.model.ReminderOn) = edit(task) { TaskLine.setReminder(it, time, on) }

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

    // ---- Assistant ----

    fun ask(text: String) {
        val s = _state.value
        val plan = Planner.plan(text, s.tasks, s.events, s.profile, LocalDateTime.now())
        _state.update { it.copy(chat = it.chat + Chat.Asked(text) + Chat.Slots(plan)) }
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
    }
}
