package app.omnitask.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import app.omnitask.CrashLog
import app.omnitask.data.CalendarReader
import app.omnitask.data.ClaudeAi
import app.omnitask.data.ClaudeSettings
import app.omnitask.data.DriveLink
import app.omnitask.data.ImageAttach
import app.omnitask.data.BehaviorLog
import app.omnitask.data.RecurHistory
import app.omnitask.data.SettingsSync
import app.omnitask.data.MonthMarksStore
import app.omnitask.data.GoogleCalendarCheck
import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.data.VaultRepository
import app.omnitask.data.VaultText
import app.omnitask.drive.OmniDrive
import app.omnitask.drive.sameName
import app.omnitask.model.Countdown
import app.omnitask.model.DateBucket
import app.omnitask.model.Focus
import app.omnitask.model.Folders
import app.omnitask.model.SpokenWhen
import app.omnitask.model.TimeWords
import app.omnitask.model.Ygdrasil
import app.omnitask.model.Behavior
import app.omnitask.model.BehaviorEvent
import app.omnitask.model.Insight
import app.omnitask.model.Learned
import app.omnitask.model.Appearance
import app.omnitask.model.Branches
import app.omnitask.model.Projects
import app.omnitask.model.Lang
import app.omnitask.model.locale
import app.omnitask.model.load
import app.omnitask.model.AssistantMl
import app.omnitask.model.Planner
import app.omnitask.model.Profile
import app.omnitask.model.QuickAdd
import app.omnitask.model.AiAssist
import app.omnitask.model.VoiceNotes
import app.omnitask.data.VoiceRec
import app.omnitask.data.Gemini
import app.omnitask.model.AiMemory
import app.omnitask.data.AiKey
import app.omnitask.data.AiLibrary
import app.omnitask.model.AiIndex
import app.omnitask.model.Swap
import app.omnitask.notify.Digest
import kotlinx.datetime.LocalDateTime
import app.omnitask.model.NoteLinks
import app.omnitask.model.OmniList
import app.omnitask.notify.WatchAlarm
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
import app.omnitask.model.ReminderOn
import app.omnitask.model.Status
import app.omnitask.model.StuckLog
import app.omnitask.model.ReviewWrite
import app.omnitask.model.SecretaryDesk
import app.omnitask.model.SecretaryPretext
import app.omnitask.model.SecretaryBook
import app.omnitask.model.SecretaryGuide
import app.omnitask.model.ProjectNames
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.omnitask.model.TaskKind
import app.omnitask.model.CustomKind
import app.omnitask.model.TaskQuery
import app.omnitask.notify.CalendarEvent
import app.omnitask.model.Task
import app.omnitask.model.TreeMove
import app.omnitask.model.UrgentRule
import app.omnitask.model.bucket
import app.omnitask.model.quadrant
import app.omnitask.model.SecretaryAi
import app.omnitask.model.SecretaryAsk
import app.omnitask.model.tr
import app.omnitask.notify.NotifySettings
import app.omnitask.notify.Scheduler
import app.omnitask.widget.CalendarPrint
import app.omnitask.widget.OmniWidgets
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.omnitask.time.*

const val DEFAULT_ARCHIVE_DAYS = 7

/** Where a broken Gemini answer is kept, for Claude to read. */

/** The secretary chat's taps, a line each, for the secretary to learn habits from. */
private const val DESK_STATS = "${app.omnitask.data.VaultText.OMNI_DIR}/สถิติเลขา.md"

/** The filter set shared by the List and Eisenhower views. */
data class Filters(
    val bucket: DateBucket? = null,
    val tag: String? = null,
    val note: String? = null,
    val showDone: Boolean = false,
)

data class Deleted(val title: String, val cut: VaultRepository.Cut)

/** A task just turned into an event ([toEvent]) or an event into a task, offered for undo once. */
class SwapUndo(val title: String, val toEvent: Boolean, val undo: () -> Unit)

data class UiState(
    val vault: Uri? = null,
    val loading: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val filters: Filters = Filters(),
    val urgentRule: UrgentRule = UrgentRule.THIS_WEEK,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
    /** A task just deleted, offered for undo once (its title and the removed lines). */
    val deleted: Deleted? = null,
    /** A task just moved to the archive note, offered for undo once. */
    val archived: Deleted? = null,
    /** A task just turned into an event, or an event into a task, offered for undo once. */
    val swapped: SwapUndo? = null,
    /** A move made in Ygdrasil, offered for undo once. */
    val moved: TaskViewModel.MoveUndo? = null,
    /** A task just ticked done: ask whether to archive it, delete it or leave it. */
    val finished: Task? = null,
    /** Ticks offered for undo in the bar, newest last; the bar goes after 30 seconds (owner, 2026-10-27). */
    val ticked: List<TaskViewModel.UndoStep> = emptyList(),
    /** Ygdrasil's own history, kept until the app is closed: steps to undo (newest last), and steps undone, to redo. */
    val treeUndo: List<TaskViewModel.UndoStep> = emptyList(),
    val treeRedo: List<TaskViewModel.UndoStep> = emptyList(),
    /** Finished tasks move to the archive note after this many days; 0 leaves them in place. */
    val archiveDays: Int = DEFAULT_ARCHIVE_DAYS,
    /** Whether ticking a task done asks to archive or delete it. */
    val askOnDone: Boolean = true,
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
    /** Ids of the phone's calendars the owner switched off in Omni (this phone only). */
    val hiddenCalendars: Set<Long> = emptySet(),
    val pendingImage: PendingImage? = null,
    /** The report of a crash since the app was last open, shown once so it can be copied. */
    val crash: String? = null,
    /** A parent being ticked while some subtasks are still open: ask whether to tick them too. */
    val closingParent: Task? = null,
    /** Copies a sync app left after a clash (not read, so their tasks don't show twice). */
    val conflicts: List<String> = emptyList(),
    /** Vault-relative paths of every note, e.g. `📁 Folder/งาน/ประชุม.md`. */
    val notePaths: List<String> = emptyList(),
    val vaultName: String? = null,
    /** The secretary's questions (`Claude/เลขาถาม.md`), as last read; the today page matches them to open tasks. */
    val secretaryAsks: List<SecretaryAsk.Ask> = emptyList(),
    val profile: Profile = Profile(),
    val insight: Insight.Ask? = null,
    /** The assistant conversation for this session, newest last. */
    val chat: List<Chat> = emptyList(),
    /** When each no-deadline task was last reviewed, by title. */
    val reviewed: Map<String, LocalDate> = emptyMap(),
    /** Set when something outside the screens (a widget, a shortcut) asks for the quick-add sheet. */
    val quickAdd: QuickAddRequest? = null,
    /** Set when the weekly notification is opened: ask why each long-open task is still open. */
    val askStuck: Boolean = false,
    /** The month widget asked for the frames and emoji page, at this day. */
    val month: MonthRequest? = null,
    /** The month widget asked for the calendar at this day; the calendar takes it and clears it. */
    val calendarDay: LocalDate? = null,
    /** Whether the calendar should bring [calendarDay]'s list into view: when the widget opened the day, not when a page closed. */
    val calendarReveal: Boolean = false,
    /** The Tasks tab's view (null: the list) and the calendar's span, kept while the app is open, not saved. */
    val tasksLayout: Mode? = null,
    val calendarSpan: CalendarSpan = CalendarSpan.MONTH,
    /** The owner's own project order, and the starred ones that always sit on top. */
    val projectOrder: List<String> = emptyList(),
    val starred: Set<String> = emptySet(),
    /** The tags the owner made projects (see [Projects.chosen]); null until chosen once. */
    val projectTags: Set<String>? = null,
    /** Branch states by (project, path), and the tasks hidden because their branch is parked. */
    val branchStates: Map<Pair<String, String>, Branches.State> = emptyMap(),
    val parked: List<Task> = emptyList(),
    /** Kinds the owner made, and the built-in ones they hid. */
    val customKinds: List<CustomKind> = emptyList(),
    val hiddenKinds: Set<TaskKind> = emptySet(),
    /** Folded task groups ("groupBy:label") and folded Kanban columns (status names). */
    val foldedGroups: Set<String> = emptySet(),
    val foldedColumns: Set<String> = emptySet(),
    /** What the Focus screen counts down to: any tasks and calendar events, not only today's. */
    val countdowns: List<Countdown.Pin> = emptyList(),
    /** The pinned events as the calendar holds them now, read over the year ahead, so a moved one is followed. */
    val pinnedEvents: List<CalendarEvent> = emptyList(),
    /** The year ahead in the calendars shown, read when the countdown picker opens its calendar tab. */
    val aheadEvents: List<CalendarEvent> = emptyList(),
    /** Tonight's bedtime when it differs from the usual one: the evening's date and the time. */
    val tonightBed: Pair<LocalDate, kotlinx.datetime.LocalTime>? = null,
    val tonightWake: Pair<LocalDate, kotlinx.datetime.LocalTime>? = null,
    /** The wake alarm last sent to the watch, as the night's evening and the time. */
    val alarmSet: Pair<LocalDate, kotlinx.datetime.LocalTime>? = null,
    /** The day and time the watch alarm rings, set here or on the watch. */
    val alarmAt: LocalDateTime? = null,
    /** The last send did not reach a watch with Omni. */
    val alarmFailed: Boolean = false,
    /** Tonight's wake time was set on the watch. */
    val wakeFromWatch: Boolean = false,
    /** Each project's task order (by title) and the projects whose order is enforced with 🆔/⛔. */
    val projectTaskOrder: Map<String, List<String>> = emptyMap(),
    val strictProjects: Set<String> = emptySet(),
    /** How far ahead a project's work opens it in Ygdrasil (work past its 📅 date always does). */
    val treeReach: Ygdrasil.Reach = Ygdrasil.Reach.WEEK,
    /** The owner's time words for spoken tasks ("เย็น" is 17:00), from กฎเวลา.md. */
    val timeWords: List<TimeWords.Word> = TimeWords.DEFAULT,
    /** The Omni folder on Google Drive: whether this phone uses it and how the last read went. */
    val drive: DriveUi = DriveUi(),
    /** The Gemini assistant: the key, the brief at the top of the assistant page, and calls under way. */
    val ai: AiUi = AiUi(),
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

    /** A list's items: the lines in its note, and tasks anywhere else tagged with the list's tag. */
    fun itemsOf(list: OmniList): List<Task> =
        listItems.filter { it.list == list.name } + (tasks + parked).filter { t -> t.tags.any { it.equals(list.tag, ignoreCase = true) } }

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

/** The Omni folder on Google Drive as Settings shows it (see [DriveLink]). */
data class DriveUi(
    val on: Boolean = false,
    val online: Boolean = true,
    /** Google wants the owner to sign in again; edits wait on the phone meanwhile. */
    val signIn: Boolean = false,
    /** Files with edits not on Drive yet. */
    val pending: Int = 0,
    /** When Drive was last read in full (epoch milliseconds). */
    val lastSync: Long = 0,
    /** Connecting or signing in is under way. */
    val busy: Boolean = false,
    /** Google's sign-in screen, to show once. */
    val signInIntent: PendingIntent? = null,
    /** The Omni folder found on Drive while the phone's copy of the task note differs: asks before connecting. */
    val confirmDir: String? = null,
    /** Edits that clashed with a change made elsewhere, kept until the owner puts them back or lets them go. */
    val kept: List<app.omnitask.drive.LostEdit> = emptyList(),
)

/** Opens the quick-add sheet, typing or listening first; [assistant] jumps to the assistant instead. */
/** Opens the month page at [day]; with [pick], that day comes selected. */
data class MonthRequest(val day: LocalDate, val pick: Boolean)

data class QuickAddRequest(
    val voice: Boolean = false,
    val assistant: Boolean = false,
    val status: Status = Status.TODO,
    /** Text shared from another app, and its link, which goes into the task's details. */
    val text: String = "",
    val link: String? = null,
)

/**
 * The Gemini assistant. [keyTail] shows which key is in without showing it; [brief] is read when the assistant page
 * opens (at most once an hour, when [autoBrief]) or on request; [thinking] while a typed question is being answered.
 */
data class AiUi(
    val hasKey: Boolean = false,
    val keyTail: String? = null,
    val autoBrief: Boolean = true,
    val brief: AiAssist.Brief? = null,
    val briefAt: Long = 0L,
    val briefing: Boolean = false,
    val briefError: String? = null,
    val thinking: Boolean = false,
    /** While a typed question waits on a search of older conversations that Gemini asked for. */
    val searching: Boolean = false,
    val testing: Boolean = false,
    /** The last key test: true when it worked, with what it said. */
    val test: Pair<Boolean, String>? = null,
    /** The model in use, and the ones Google offers this key (read when the picker opens). */
    val model: String = AiAssist.DEFAULT_MODEL,
    val models: List<AiAssist.Model> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    /** Whether Claude log.md was found in the vault, so the assistant says it reads it. */
    val claudeLog: Boolean = false,
    /** When the secretary last wrote Claude log.md (its marker line), and until when its warning is hidden. */
    val secretaryLast: Long? = null,
    val secretaryHiddenUntil: Long = 0L,
)

/** One entry in the assistant conversation. */
sealed interface Chat {
    /**
     * Gemini's answer to a typed question, the changes it proposes, and what the owner did with each
     * ([outcome]: index to true for done, false for declined). [failed] when the call did not work.
     */
    data class AiAnswer(
        val text: String,
        val actions: List<AiAssist.Action> = emptyList(),
        val outcome: Map<Int, Boolean> = emptyMap(),
        val failed: String? = null,
        /** Which model answered and how long it took, shown under its name, and whether it searched older talks. */
        val model: String? = null,
        val seconds: Double? = null,
        val searched: Boolean = false,
    ) : Chat

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

    /** The behavior log as last read from the vault ([BehaviorLog]), and the pending send of what was just done. */
    private var behavior: List<BehaviorEvent> = emptyList()
    private var behaviorSend: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(
        fromPrefs(UiState(vault = prefs.getString(KEY_VAULT, null)?.let(Uri::parse), drive = DriveUi(on = DriveLink.isOn(app)))),
    )

    /** The settings kept in preferences (and synced through the vault file), laid over [base]. */
    private fun fromPrefs(base: UiState) = base.copy(
        urgentRule = prefs.getString(KEY_URGENT, null)
            ?.let { name -> UrgentRule.entries.firstOrNull { it.name == name } }
            ?: UrgentRule.THIS_WEEK,
        futureCount = prefs.getInt(KEY_FUTURE_COUNT, 1),
        archiveDays = prefs.getInt(KEY_ARCHIVE_DAYS, DEFAULT_ARCHIVE_DAYS),
        askOnDone = prefs.getBoolean(KEY_ASK_ON_DONE, true),
        skippedToday = prefs.getStringSet(KEY_SKIPPED, emptySet()).orEmpty()
            .filter { it.startsWith("${LocalDate.now()}|") }
            .map { it.substringAfter('|') }
            .toSet(),
        dismissed = prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet(),
        reviewed = prefs.getStringSet(KEY_REVIEWED, emptySet()).orEmpty().mapNotNull { e ->
            runCatching { e.substringBeforeLast('|') to LocalDate.parse(e.substringAfterLast('|')) }.getOrNull()
        }.toMap(),
        notify = Scheduler.loadSettings(getApplication()),
        hiddenCalendars = CalendarReader.hiddenIds(getApplication()),
        projectOrder = prefs.getString(KEY_PROJECT_ORDER, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
        starred = prefs.getStringSet(KEY_STARRED, emptySet()).orEmpty().toSet(),
        // The project logic reads the choice too, so it is applied wherever the settings are.
        projectTags = prefs.getStringSet(KEY_PROJECT_TAGS, null)?.toSet().also { Projects.chosen = it },
        countdowns = Countdown.parseAll(prefs.getStringSet(KEY_COUNTDOWNS, null)?.toList() ?: listOfNotNull(prefs.getString(KEY_COUNTDOWN, null))),
        foldedGroups = prefs.getStringSet(KEY_FOLDED_GROUPS, emptySet()).orEmpty().toSet(),
        branchStates = Branches.parse(prefs.getStringSet(KEY_BRANCHES, emptySet()).orEmpty()),
        foldedColumns = prefs.getStringSet(KEY_FOLDED_COLUMNS, emptySet()).orEmpty().toSet(),
        customKinds = CustomKind.parse(prefs.getStringSet(KEY_CUSTOM_KINDS, emptySet()).orEmpty()),
        hiddenKinds = prefs.getStringSet(KEY_HIDDEN_KINDS, emptySet()).orEmpty().mapNotNull { n -> TaskKind.entries.firstOrNull { it.name == n } }.toSet(),
        tonightBed = nightPref(KEY_TONIGHT_BED),
        tonightWake = nightPref(KEY_TONIGHT_WAKE),
        alarmSet = nightPref(KEY_ALARM_SET),
        alarmAt = alarmAtPref(),
        wakeFromWatch = prefs.getBoolean(KEY_WAKE_FROM_WATCH, false),
        projectTaskOrder = prefs.getStringSet(KEY_PROJECT_TASKS, emptySet()).orEmpty().associate { e ->
            e.substringBefore('\t') to e.substringAfter('\t', "").split('\u001F').filter { it.isNotEmpty() }
        },
        strictProjects = prefs.getStringSet(KEY_STRICT, emptySet()).orEmpty().toSet(),
        treeReach = Ygdrasil.Reach.of(prefs.getString(KEY_TREE_REACH, null)),
        savedFilters = prefs.getStringSet(KEY_SAVED_FILTERS, emptySet()).orEmpty()
            .associate { it.substringBefore('\t') to it.substringAfter('\t', "") },
    )

    // Any settings change is copied to the vault file a moment later, batching quick taps into one write.
    private var saveJob: Job? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // A wake time set on the watch lands here from WatchListener.
        if (key in WATCH_KEYS) {
            _state.update {
                it.copy(tonightWake = nightPref(KEY_TONIGHT_WAKE), alarmSet = nightPref(KEY_ALARM_SET), alarmAt = alarmAtPref(), wakeFromWatch = prefs.getBoolean(KEY_WAKE_FROM_WATCH, false), alarmFailed = false)
            }
        }
        if (key == null || key in SYNC_IGNORED) return@OnSharedPreferenceChangeListener
        val vault = _state.value.vault ?: return@OnSharedPreferenceChangeListener
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500)
            withContext(Dispatchers.IO) { runCatching { SettingsSync.save(getApplication(), repo, vault) } }
        }
    }

    /** Google Calendar syncs into the phone's calendar on its own time: follow it as the events land instead of waiting for the next reload. */
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshEvents()
    }
    private var eventsJob: Job? = null

    init {
        readAiKey()
        prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        runCatching { getApplication<Application>().contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver) }
    }

    // Tasks spoken into the phone (see VoiceRecorder.kt and VoiceNotes).

    /**
     * What the voice sheet and the focus page show: a recording with its words and the tasks heard so far, the last
     * tidy after it stops, the tasks to check, or recordings left over.
     */
    data class VoiceUi(
        val recording: VoiceRec.Recording? = null,
        val words: String = "",
        val live: List<VoiceNotes.Live> = emptyList(),
        /** Pieces sent and not answered yet, and pieces that could not be written down (no network). */
        val sending: Int = 0,
        val unsent: Int = 0,
        val busy: Boolean = false,
        val review: VoiceReview? = null,
        val waiting: List<java.io.File> = emptyList(),
        val failed: String? = null,
    )

    /** The tasks heard in the recording in [dir], to check before they go to the tray. [dup] is an open task each may repeat. */
    data class VoiceReview(val dir: java.io.File, val seconds: Long, val items: List<VoiceItem>)

    /** A heard task: its words, the day and time read from them ([heard]), and an open task it may repeat. */
    data class VoiceItem(val text: String, val dup: Task?, val heard: SpokenWhen.Heard)

    /** A heard task as the owner leaves it in the check: the words, the day, time and kind of date, and a project. */
    data class VoiceDraft(val heard: SpokenWhen.Heard, val project: String? = null)

    private val _voice = MutableStateFlow(VoiceUi())
    val voice: StateFlow<VoiceUi> = _voice

    /** Tasks the owner took out while recording; Gemini is told so it does not bring them back. */
    private val voiceRemoved = mutableListOf<String>()
    private var voiceNextId = 1

    /** One piece at a time, in order, so each is sent with the words and the list before it. */
    private val voiceLock = kotlinx.coroutines.sync.Mutex()

    /** Titles saved from a recording, with when, so the tray can show them first for a day. */
    private val voiceFresh get() = getApplication<Application>().getSharedPreferences("omni-voice", android.content.Context.MODE_PRIVATE)

    // ---- The secretary's own chat (new look): answers by rules, buttons for what comes next, Claude for the rest. ----

    /** One turn of the secretary chat: the owner's words, or the secretary's [reply]. [done] once its changes were made. */
    data class DeskTurn(val mine: String? = null, val reply: SecretaryDesk.Reply? = null, val done: Boolean = false, val ai: AiTurn? = null, val via: String? = null, val ml: MlTurn? = null)

    /**
     * An answer of the learning secretary ("เลขาเรียนรู้เอง"): what it read in [question]. [acted] is the guess it acted
     * on (it was sure, or the owner picked or taught it); without one it asks "หมายถึงอันไหน", or offers Haiku and
     * teaching. [slots] are free times found for "หาเวลา"; [note] says whether the sentence was kept as an example.
     */
    data class MlTurn(
        val question: String,
        val reading: app.omnitask.model.AssistantMl.Reading,
        val acted: app.omnitask.model.AssistantMl.Guess? = null,
        val slots: List<Planner.Slot> = emptyList(),
        val slotTitle: String = "",
        val note: String? = null,
        val done: Boolean = false,
        /** Finds the answer again when the note is written (the chat may have moved on). */
        val id: Long = 0,
    )

    /**
     * One answer from Claude in the secretary chat: while it is asked ([waiting]), then what the model read, what
     * the owner can tap from it, and what it cost (real tokens from the API) with the credit left after it. [id] finds
     * it again when the answer arrives, wherever the chat has moved meanwhile.
     */
    data class AiTurn(
        val id: Long,
        val mode: SecretaryAi.Mode,
        val effort: SecretaryAi.Effort,
        val waiting: Boolean = true,
        val question: String = "",
        val reading: SecretaryAi.Reading? = null,
        /** A command of the app the model named, as a button; null when it named none the app knows. */
        val command: SecretaryGuide.Action? = null,
        /** Times the app found for a find_time answer. */
        val slots: List<Planner.Slot> = emptyList(),
        val slotTitle: String = "",
        val error: String? = null,
        val inTokens: Long = 0,
        val outTokens: Long = 0,
        val inUsd: Double = 0.0,
        val outUsd: Double = 0.0,
        val leftUsd: Double = 0.0,
        /** What a tap already did ("สอนแล้ว"...), so the buttons give way to it. */
        val done: String? = null,
    ) {
        val usd get() = inUsd + outUsd
    }

    /**
     * A question waiting for the owner to confirm what Claude will cost. Nothing has left the phone yet: the cost is
     * worked out here (the instructions as the last real answer measured them, plus the sentence), so it reads "ราว".
     * [fromTurn] is the rules' answer it came from (the Haiku button), so the question is not shown twice.
     */
    data class AiPending(
        val question: String,
        val mode: SecretaryAi.Mode,
        val effort: SecretaryAi.Effort,
        val estimate: SecretaryAi.Estimate,
        val fromTurn: Int? = null,
    )

    /**
     * The model picked in the chat box: the app's rules (free) every time the app starts or the chat starts over, as
     * the owner asked ("AI only when picked"); the effort is kept.
     */
    var deskMode by androidx.compose.runtime.mutableStateOf(SecretaryAi.Mode.RULES)
        private set
    var deskEffort by androidx.compose.runtime.mutableStateOf(ClaudeSettings.effort(app))
        private set
    var aiPending by androidx.compose.runtime.mutableStateOf<AiPending?>(null)
        private set
    /** Bumped when the AI settings change, so what shows them reads them again. */
    var aiSettingsVersion by androidx.compose.runtime.mutableStateOf(0)
        private set
    private var aiNextId = 1L

    fun pickDeskMode(mode: SecretaryAi.Mode) { deskMode = mode }

    fun pickDeskEffort(effort: SecretaryAi.Effort) { deskEffort = effort; ClaudeSettings.setEffort(getApplication(), effort) }

    fun aiSettingsChanged() { aiSettingsVersion++ }

    /** The chat box's send: the rules when no model is picked, otherwise Claude after the owner confirms the cost. */
    fun deskSend(text: String) {
        if (!deskMode.isAi) deskType(text) else aiStart(text, deskMode, deskEffort)
    }

    /**
     * Gets a question ready for Claude: without a key the owner is told where to put one; otherwise the confirmation
     * opens with what it will cost. Nothing is sent before "ยืนยันส่ง". Any sentence may go (owner, 2026-10-26).
     */
    fun aiStart(text: String, mode: SecretaryAi.Mode, effort: SecretaryAi.Effort, fromTurn: Int? = null) {
        val app = getApplication<Application>()
        fun say(line: String) {
            // Stopped here: nothing went out, so the line under it says so.
            if (fromTurn == null) desk.add(DeskTurn(mine = text, via = tr("ไม่ได้ส่งให้ AI", "Not sent to the AI")))
            desk.add(DeskTurn(reply = SecretaryDesk.Reply(listOf(line), emptyList())))
        }
        if (ClaudeSettings.key(app) == null) {
            say(tr("ยังไม่ได้ใส่กุญแจ API ของ Anthropic ใส่ได้ที่ปุ่มเลือกโหมดในกล่องพิมพ์ แล้วเลือก ตั้งค่า AI และกฎถามตอบ", "No Anthropic API key yet: add it from the mode button in the chat box, AI and rules settings"))
            return
        }
        aiPending = AiPending(text, mode, effort, aiEstimate(mode, effort, text), fromTurn)
    }

    /**
     * What [text] will cost on [mode], worked out on the phone: the instructions as the last real answer measured them,
     * plus the sentence; before any answer, a guess from the instructions and the answer's form.
     */
    fun aiEstimate(mode: SecretaryAi.Mode, effort: SecretaryAi.Effort, text: String): SecretaryAi.Estimate {
        val app = getApplication<Application>()
        val inTokens = ClaudeSettings.baseTokens(app, mode)?.let { it + SecretaryAi.guessInTokens("", text) }
            ?: SecretaryAi.guessInTokens(SecretaryAi.system(LocalDate.now()) + SecretaryAi.SCHEMA, text)
        return SecretaryAi.estimate(mode, effort, inTokens)
    }

    /** A typed sentence the owner cancelled, handed back to the chat box so it need not be typed again. */
    var deskDraft by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun deskDraftTaken() { deskDraft = null }

    fun aiCancel() {
        val p = aiPending ?: return
        aiPending = null
        if (p.fromTurn == null) deskDraft = p.question
    }

    private fun aiTurnIndex(id: Long) = desk.indexOfFirst { it.ai?.id == id }

    /** The owner confirmed: asks Claude, counts what it really cost, and turns the answer into buttons. */
    fun aiConfirm() {
        val p = aiPending ?: return
        aiPending = null
        val app = getApplication<Application>()
        val key = ClaudeSettings.key(app) ?: return
        // The rules' answer the Haiku button came from has done its part: its buttons give way.
        p.fromTurn?.let { i -> if (i in desk.indices && desk[i].reply?.teach == p.question) desk[i] = desk[i].copy(done = true) }
        p.fromTurn?.let { i -> desk.getOrNull(i)?.ml?.takeIf { it.question == p.question }?.let { m -> desk[i] = desk[i].copy(ml = m.copy(done = true)) } }
        if (p.fromTurn == null) desk.add(DeskTurn(mine = p.question, via = p.mode.sentWith))
        val id = aiNextId++
        desk.add(DeskTurn(ai = AiTurn(id, p.mode, p.effort, question = p.question)))
        deskNote(tr("ถาม ${p.mode.th}: ", "Asked ${p.mode.en}: ") + p.question)
        viewModelScope.launch {
            val system = SecretaryAi.system(LocalDate.now())
            val result = withContext(Dispatchers.IO) { runCatching { ClaudeAi(key).ask(p.mode, p.effort, system, p.question) } }
            val base = AiTurn(id, p.mode, p.effort, question = p.question)
            fun spend(inT: Long, outT: Long): AiTurn {
                val inUsd = inT * p.mode.inPerM / 1e6
                val outUsd = outT * p.mode.outPerM / 1e6
                ClaudeSettings.addSpent(app, inUsd + outUsd)
                // What the instructions really cost, so the next confirmation is closer.
                ClaudeSettings.setBaseTokens(app, p.mode, inT - SecretaryAi.guessInTokens("", p.question))
                aiSettingsChanged()
                return base.copy(waiting = false, inTokens = inT, outTokens = outT, inUsd = inUsd, outUsd = outUsd, leftUsd = ClaudeSettings.credit(app).leftUsd)
            }
            val done = result.fold(
                onSuccess = { a ->
                    val t = spend(a.inTokens, a.outTokens)
                    val reading = SecretaryAi.parse(a.json)
                    if (reading == null) t.copy(error = tr("อ่านคำตอบของ AI ไม่ได้", "Could not read the model's answer")) else aiRead(t, reading)
                },
                onFailure = { e ->
                    when (e) {
                        is ClaudeAi.Spent -> spend(e.inTokens, e.outTokens).copy(error = e.failure.message)
                        else -> base.copy(waiting = false, error = e.message ?: tr("เรียก AI ไม่สำเร็จ", "The call failed"), leftUsd = ClaudeSettings.credit(app).leftUsd)
                    }
                },
            )
            // Found by its id: the chat may have moved on, or started over. Then the answer, and its cost, show in the
            // new chat, and also in the old one kept for "undo", so undo does not bring back "กำลังถาม..." for good.
            deskBefore = deskBefore.map { if (it.ai?.id == id) it.copy(ai = done) else it }
            val at = aiTurnIndex(id)
            if (at >= 0) desk[at] = desk[at].copy(ai = done) else desk.add(DeskTurn(ai = done))
        }
    }

    /** What the owner can tap from a [reading]: a command of the app, times to pick, a task to add, or Claude. */
    private fun aiRead(turn: AiTurn, reading: SecretaryAi.Reading): AiTurn {
        val s = _state.value
        return when (reading.kind) {
            SecretaryAi.Kind.COMMAND -> turn.copy(reading = reading, command = SecretaryGuide.Action.parse(reading.command))
            SecretaryAi.Kind.FIND_TIME -> {
                val plan = Planner.plan(reading.task.ifBlank { turn.question }, s.tasks, s.events, s.profile, LocalDateTime.now(), reading.minutes.takeIf { it > 0 })
                turn.copy(reading = reading, slots = plan.slots.take(3), slotTitle = plan.title)
            }
            else -> turn.copy(reading = reading)
        }
    }

    /** Runs the command Claude named, as if its bubble were tapped (the app's own rules: free). */
    fun aiRunCommand(id: Long) {
        val t = desk.getOrNull(aiTurnIndex(id))?.ai ?: return
        val action = t.command ?: return
        deskRun(action.describe(), action)
    }

    /** Keeps the sentence and the command Claude found as a taught word, so the rules answer it next time for free. */
    fun aiTeach(id: Long) {
        val at = aiTurnIndex(id)
        val t = desk.getOrNull(at)?.ai ?: return
        val action = t.command ?: return
        editGuide { g -> g.copy(words = g.words.filterNot { SecretaryGuide.squash(it.word) == SecretaryGuide.squash(t.question) } + SecretaryGuide.Word(t.question, action)) }
        desk[at] = desk[at].copy(ai = t.copy(done = tr("สอนแล้ว ครั้งหน้าโหมดเลขาตอบได้เอง ไม่ต้องใช้ AI", "Taught: the rules answer this next time, no AI")))
    }

    /** Opens quick add with [sentence] typed in, so the owner checks it and taps add (nothing is added by the model). */
    fun aiAddTask(sentence: String) = requestQuickAdd(QuickAddRequest(text = sentence))

    /** A time Claude's question found: opens quick add with the title, day and time typed in. */
    fun aiPickSlot(id: Long, slot: Planner.Slot) {
        val t = desk.getOrNull(aiTurnIndex(id))?.ai ?: return
        val day = "${slot.day.dayOfMonth}/${slot.day.monthNumber}"
        val time = "%d:%02d".format(slot.start.hour, slot.start.minute)
        aiAddTask("${t.slotTitle.ifBlank { t.question }} $day $time")
    }

    /** Takes the question to Claude in its app, as the Claude bubble does. */
    fun aiToClaude(id: Long) {
        val t = desk.getOrNull(aiTurnIndex(id))?.ai ?: return
        deskRun(tr("ส่งให้ Claude", "Send to Claude"), SecretaryGuide.Action.Claude(t.question))
    }

    val desk = androidx.compose.runtime.mutableStateListOf<DeskTurn>()
    private var deskOffered = emptySet<String>()

    private fun deskCtx(): SecretaryDesk.Ctx {
        val s = _state.value
        return SecretaryDesk.Ctx(
            s.tasks, s.events, s.today, LocalDateTime.now(), NewLook.skippedFor(s.today),
            othersEvent = { CalendarStyles.label(it.calendarId) != null },
            claudeLog = claudeLogText,
        )
    }

    /** Opens the chat with a greeting and the first buttons, unless it has been used in the last hour. */
    fun deskOpen() {
        val last = desk.lastOrNull()
        if (last != null && System.currentTimeMillis() - deskOpenedAt < 60 * 60 * 1000L) return
        deskOpenedAt = System.currentTimeMillis()
        deskOffered = emptySet()
        desk.add(DeskTurn(reply = SecretaryDesk.opening(deskCtx())))
    }
    private var deskOpenedAt = 0L

    /** Claude log as last read, for the morning brief (Claude's work is plain bullets there, not tasks). */
    private var claudeLogText: String? = null

    /** The chat as it was before "start over", for "undo". */
    private var deskBefore: List<DeskTurn> = emptyList()

    /** Starts the secretary's chat over: today's greeting only, so what goes to Claude no longer carries the old talk. */
    fun deskReset() {
        deskBefore = desk.toList()
        desk.clear()
        deskOffered = emptySet()
        deskOpenedAt = System.currentTimeMillis()
        // A new chat starts on the free rules again ("AI only when picked").
        deskMode = SecretaryAi.Mode.RULES
        desk.add(DeskTurn(reply = SecretaryDesk.opening(deskCtx())))
    }

    fun deskUndoReset() {
        if (deskBefore.isEmpty()) return
        desk.clear()
        desk.addAll(deskBefore)
        deskBefore = emptyList()
    }

    /**
     * The owner tapped a button: say it in the chat, act on it, and answer with the next buttons. [via] is what a typed
     * sentence was sent with, shown small under it ("ส่งด้วย เลขา (ฟรี)"); a tapped bubble has none.
     */
    fun deskAsk(chip: SecretaryDesk.Chip, picked: List<String> = emptyList(), via: String? = null) {
        val ctx = deskCtx()
        val lastReply = desk.lastOrNull { it.reply != null }?.reply
        desk.add(DeskTurn(mine = chip.label, via = via))
        deskNote(chip.label + (lastReply?.task?.let { ": ${it.title}" } ?: ""))
        val n = guide.length.count
        val reply: SecretaryDesk.Reply = when (chip.ask) {
            SecretaryDesk.Ask.OPEN -> SecretaryDesk.opening(ctx)
            SecretaryDesk.Ask.NEXT -> SecretaryDesk.next(ctx).also { r -> r.task?.let { deskOffered = setOf(it.key) } }
            SecretaryDesk.Ask.OTHER, SecretaryDesk.Ask.LIGHT -> {
                lastReply?.task?.let { NewLook.skip(getApplication(), it.key, ctx.today) }
                SecretaryDesk.next(ctx, deskOffered, light = chip.ask == SecretaryDesk.Ask.LIGHT).also { r -> r.task?.let { deskOffered = deskOffered + it.key } }
            }
            SecretaryDesk.Ask.START -> {
                lastReply?.task?.let { setStatus(it, Status.IN_PROGRESS) }
                SecretaryDesk.Reply(listOf(tr("เริ่มแล้ว ทำเสร็จกด เสร็จแล้ว", "Started. Tap Done when finished")), listOf(SecretaryDesk.Chip(tr("เสร็จแล้ว", "Done"), SecretaryDesk.Ask.DONE), SecretaryDesk.Chip(tr("ติดอยู่ ขออันอื่น", "Stuck, something else"), SecretaryDesk.Ask.OTHER)), task = lastReply?.task)
            }
            SecretaryDesk.Ask.DONE -> {
                lastReply?.task?.let { t -> if (t.isOpen) toggleDone(t) }
                val after = SecretaryDesk.next(deskCtx(), deskOffered)
                after.copy(lines = listOf(tr("เก่งมาก ต่อไป:", "Nice. Next:")) + after.lines)
            }
            SecretaryDesk.Ask.LATE -> SecretaryDesk.late(ctx)
            SecretaryDesk.Ask.APPLY -> {
                val moves = lastReply?.moves.orEmpty()
                moves.forEach { m -> current(m.task)?.let { setDate(it, m.field, m.date) } }
                markLastDone(lastReply)
                SecretaryDesk.Reply(listOf(tr("ลงวันใหม่แล้ว ${moves.size} งาน", "${moves.size} tasks moved")), listOf(SecretaryDesk.Chip(tr("ตอนนี้ทำอะไรดี", "What now"), SecretaryDesk.Ask.NEXT), SecretaryDesk.Chip(tr("สัปดาห์นี้", "This week"), SecretaryDesk.Ask.WEEK)))
            }
            SecretaryDesk.Ask.STEP_BY_STEP -> SecretaryDesk.Reply(
                listOf(tr("แตะแต่ละงานในรายการด้านบนเพื่อเปิดแล้วเลือกวันเอง", "Tap each task above to open it and pick its day")),
                listOf(SecretaryDesk.Chip(tr("ลงตามนี้", "Apply"), SecretaryDesk.Ask.APPLY)), moves = lastReply?.moves.orEmpty(),
            )
            SecretaryDesk.Ask.TOMORROW -> SecretaryDesk.tomorrow(ctx)
            SecretaryDesk.Ask.WEEK -> SecretaryDesk.week(ctx)
            SecretaryDesk.Ask.SPLIT -> SecretaryDesk.split(ctx, lastReply?.task)
            SecretaryDesk.Ask.APPLY_SPLIT -> {
                val parent = lastReply?.task?.let { current(it) }
                if (parent != null && picked.isNotEmpty()) addSubtasks(parent, picked)
                markLastDone(lastReply)
                SecretaryDesk.Reply(listOf(tr("ใส่เป็นงานย่อยแล้ว ${picked.size} ข้อ เริ่มที่ข้อแรกพอ", "${picked.size} subtasks added. Start with the first")), listOf(SecretaryDesk.Chip(tr("ตอนนี้ทำอะไรดี", "What now"), SecretaryDesk.Ask.NEXT)), task = parent)
            }
            SecretaryDesk.Ask.TODAY -> SecretaryDesk.today(ctx)
            SecretaryDesk.Ask.MONTH -> SecretaryDesk.month(ctx)
            SecretaryDesk.Ask.RANK_TODAY -> deskList(tr("วันนี้ทำตามลำดับนี้", "Today, in this order"), SecretaryDesk.rankToday(ctx), tr("วันนี้ไม่มีงานที่ต้องทำแล้ว", "Nothing needs doing today"))
            SecretaryDesk.Ask.RANK_ALL -> {
                val all = SecretaryDesk.rankAll(ctx, guide.order)
                deskList(tr("งานเปิดอยู่ ${all.size} งาน ทำตามลำดับนี้", "${all.size} open tasks, in this order"), all, tr("ไม่มีงานเปิดอยู่", "No open work"))
            }
            SecretaryDesk.Ask.MORE -> {
                val list = deskPage
                if (list == null || list.from + n >= list.items.size) SecretaryDesk.Reply(listOf(tr("ไม่มีชุดถัดไปแล้ว", "Nothing more to show")), listOf(SecretaryDesk.Chip(tr("ตอนนี้ทำอะไรดี", "What now"), SecretaryDesk.Ask.NEXT)))
                else {
                    deskPage = list.copy(from = list.from + n)
                    SecretaryDesk.list(list.title + tr(" (ต่อ)", " (more)"), list.items, list.from + n, n, "")
                }
            }
            SecretaryDesk.Ask.SHOW -> {
                val filter = (chip.action as? SecretaryGuide.Action.Show)?.filter ?: SecretaryGuide.Filter()
                val found = SecretaryDesk.show(ctx, filter)
                deskList(filter.describe() + tr(": ${found.size} งาน", ": ${found.size} tasks"), found, tr("ไม่มีงานตรงเงื่อนไขนี้", "No work matches"))
            }
            SecretaryDesk.Ask.CLAUDE -> return
        }
        desk.add(DeskTurn(reply = reply))
    }

    /** The list behind the last list answer, for "ต่อ": its title, every item, and where the shown page starts. */
    private data class DeskPage(val title: String, val items: List<SecretaryDesk.Item>, val from: Int)
    private var deskPage: DeskPage? = null

    private fun deskList(title: String, items: List<SecretaryDesk.Item>, empty: String): SecretaryDesk.Reply {
        deskPage = DeskPage(title, items, 0)
        return SecretaryDesk.list(title, items, 0, guide.length.count, empty)
    }

    /**
     * A typed question, read fresh against the guide: a bubble's own words, a word the owner taught, then what the rules
     * know; anything else offers to teach the word, or to take it to Claude.
     */
    fun deskType(text: String) {
        viewModelScope.launch {
            loadGuide()
            deskNote(tr("พิมพ์: ", "Typed: ") + text)
            val g = guide
            val via = SecretaryAi.Mode.RULES.sentWith
            val bubble = g.bubbles.firstOrNull { SecretaryGuide.squash(it.label) == SecretaryGuide.squash(text) }
            val word = bubble?.action ?: g.wordIn(text)?.action
            if (word != null) { deskRun(text, word, via); return@launch }
            // The learning secretary, when it is on: sure from what the owner taught, it acts before the built-in words
            // (which read "พรุ่งนี้" in any sentence as tomorrow's list).
            val reading = if (ClaudeSettings.learnOn(getApplication())) loadMl().read(text) else null
            if (reading?.level == AssistantMl.Level.HIGH) {
                val best = reading.best!!
                deskNote(tr("ทายเอง: ", "Guessed: ") + "${mlSaid(text)} → ${best.intent.render()}")
                mlDo(text, reading, best, via = via, note = null)
                return@launch
            }
            val ask = SecretaryDesk.match(text)
            if (ask != null) { deskAsk(SecretaryDesk.Chip(text, ask), via = via); return@launch }
            deskNote(tr("ไม่รู้จัก: ", "Not understood: ") + text)
            desk.add(DeskTurn(mine = text, via = via))
            if (reading != null) { desk.add(DeskTurn(ml = MlTurn(text, reading))); return@launch }
            desk.add(DeskTurn(reply = SecretaryDesk.Reply(
                listOf(tr("ยังไม่รู้จักคำนี้ สอนผมไว้ใช้ครั้งหน้า หรือส่งให้ Claude ช่วยคิดก็ได้", "I don't know this yet. Teach me for next time, or take it to Claude")),
                listOf(SecretaryDesk.Chip(tr("ตอนนี้ทำอะไรดี", "What now"), SecretaryDesk.Ask.NEXT)),
                teach = text,
            )))
        }
    }

    /** The examples as last read, learned; kept for the buttons of an answer (pick, teach). */
    private var mlModel: AssistantMl.Model = AssistantMl.Model(emptyList())

    /** Reads Assistant ML.md again (the owner may have changed it in Obsidian) and learns from it with the guide's hints. */
    private suspend fun loadMl(): AssistantMl.Model {
        val vault = _state.value.vault
        val read = if (vault == null) Result.success(null) else withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, AssistantMl.PATH) } }
        // Not read: the chat still answers from the app's own hints, and says the taught examples were left out.
        read.exceptionOrNull()?.let { e -> showMessage(tr("อ่านโน้ต Assistant ML ไม่ได้: ${e.message}", "Could not read Assistant ML: ${e.message}")) }
        if (read.getOrNull() != null) mlNoteSeenIn = vault
        return AssistantMl.Model(AssistantMl.examples(read.getOrNull()), AssistantMl.seeds(guide)).also { mlModel = it }
    }

    /** The vault whose note was read in this run: a later read there that finds nothing is a failed lookup, not a missing note. */
    private var mlNoteSeenIn: Any? = null

    /**
     * Keeps [e] in Assistant ML.md (read fresh, added at the end of the examples, written back) and says whether it
     * was kept, once the write is done. Not kept: a sentence that may hold a patient's number, and a note that was
     * there before but is not found now (writing then would replace every taught example).
     */
    private suspend fun mlKeep(e: AssistantMl.Example): String {
        if (AssistantMl.sensitive(e.text)) {
            return tr("ไม่ได้จำ เพราะมีเลขยาวหรือคำที่อาจเป็นข้อมูลผู้ป่วย", "Not kept: it has a long number or words that may be a patient's")
        }
        val vault = _state.value.vault ?: return tr("ยังไม่ได้เลือก vault จึงจำไม่ได้", "No vault picked, so it was not kept")
        val r = mlLock.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val old = repo.readPath(vault, AssistantMl.PATH)
                    if (old == null && mlNoteSeenIn == vault) error(tr("เปิดโน้ต Assistant ML ไม่เจอ จึงไม่เขียนทับ", "Assistant ML was not found, so nothing was written over it"))
                    repo.writePath(vault, AssistantMl.PATH, AssistantMl.add(old, e))
                    mlNoteSeenIn = vault
                }
            }
        }
        r.exceptionOrNull()?.let { x -> return tr("ไม่ได้จำ: ", "Not kept: ") + (x.message ?: x.toString()) }
        deskNote(tr("สอนตัวอย่าง: ", "Taught: ") + e.render().removePrefix("- "))
        return tr("จำแล้ว ครั้งหน้าพิมพ์แบบนี้ เลขาทำเลย", "Kept: next time this is done right away")
    }
    private val mlLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Does what [guess] says for [text]: a command or a list as its bubble would; a task opens quick add filled in for
     * the owner to check (nothing is added on a guess); "หาเวลา" shows free times. [spans] are the parts the owner
     * highlighted, when taught. [via] is set when [text] is said here for the first time.
     */
    private fun mlDo(
        text: String, reading: AssistantMl.Reading, guess: AssistantMl.Guess, via: String?, note: String?,
        spans: List<AssistantMl.Span>? = null, id: Long = 0,
    ) {
        val model = mlModel
        // The task's own words: what was highlighted, or the sentence without the taught command words.
        fun taskText(): String = AssistantMl.forQuickAdd(spans?.let { ss ->
            // The task's own words and its project's (no tag yet: stage 2); the command and urgency words go.
            val words = ss.filter { it.label != AssistantMl.Label.COMMAND && it.label != AssistantMl.Label.PRIORITY }.joinToString("") { it.text }.replace(Regex("""\s+"""), " ").trim()
            // Urgency as quick add reads it: !!! highest, !! high, ! medium.
            val level = ss.firstOrNull { it.label == AssistantMl.Label.PRIORITY }?.let { it.value ?: "สูง" }
            words + when (level) { "สูงสุด" -> " !!!"; "สูง" -> " !!"; "กลาง" -> " !"; else -> "" }
        } ?: model.taskText(text), _state.value.timeWords, LocalDate.now(), java.time.LocalTime.now().let { kotlinx.datetime.LocalTime(it.hour, it.minute) })
        when (val intent = guess.intent) {
            is AssistantMl.Intent.Do -> {
                deskRun(text, intent.action, via)
                desk.add(DeskTurn(ml = MlTurn(text, reading, acted = guess, note = note, id = id)))
            }
            AssistantMl.Intent.AddTask -> {
                // Said here for the first time ([via] set); a pick or a teach is about a sentence already in the chat.
                if (via != null) desk.add(DeskTurn(mine = text, via = via))
                requestQuickAdd(QuickAddRequest(text = taskText()))
                desk.add(DeskTurn(ml = MlTurn(text, reading, acted = guess, note = note, id = id)))
            }
            AssistantMl.Intent.FindTime -> {
                if (via != null) desk.add(DeskTurn(mine = text, via = via))
                val s = _state.value
                val plan = Planner.plan(taskText().ifBlank { text }, s.tasks, s.events, s.profile, LocalDateTime.now())
                desk.add(DeskTurn(ml = MlTurn(text, reading, acted = guess, slots = plan.slots.take(3), slotTitle = plan.title, note = note, id = id)))
            }
        }
    }

    /**
     * Does it at once, then keeps [e] in the note; the line under the answer says "กำลังจำ" until the write is done,
     * then whether it was kept.
     */
    private fun mlDoThenKeep(text: String, reading: AssistantMl.Reading, guess: AssistantMl.Guess, e: AssistantMl.Example, spans: List<AssistantMl.Span>?) {
        val id = ++mlNextId
        mlDo(text, reading, guess, via = null, note = tr("กำลังจำ...", "Keeping..."), spans = spans, id = id)
        viewModelScope.launch {
            val note = mlKeep(e)
            val at = desk.indexOfFirst { it.ml?.id == id }
            if (at >= 0) desk[at] = desk[at].copy(ml = desk[at].ml!!.copy(note = note))
        }
    }
    private var mlNextId = 0L

    /** A sentence for the secretary's stats: left out when it may hold a patient's number. */
    private fun mlSaid(text: String) = if (AssistantMl.sensitive(text)) tr("(ประโยคมีเลขยาว ไม่จด)", "(has a long number, not written)") else text

    /** "หมายถึงอันนี้ไหม": the owner picked [guess] for the sentence of turn [index]; it is done and kept as an example. */
    fun mlPick(index: Int, guess: AssistantMl.Guess) {
        val t = desk.getOrNull(index)?.ml ?: return
        if (t.done) return
        desk[index] = desk[index].copy(ml = t.copy(done = true))
        deskNote(tr("เลือก: ", "Picked: ") + "${mlSaid(t.question)} → ${guess.intent.render()}")
        val e = AssistantMl.Example(mlModel.spansFor(t.question, guess.intent), guess.intent, LocalDate.now(), "หมายถึงอันนี้ไหม")
        mlDoThenKeep(t.question, t.reading, guess, e, spans = null)
    }

    /** Taught on the highlight page: kept as an example, then done for [said]. */
    fun mlTeach(said: String, spans: List<AssistantMl.Span>, intent: AssistantMl.Intent) {
        desk.indices.filter { desk[it].ml?.question == said && desk[it].ml?.done == false }.forEach { i -> desk[i] = desk[i].copy(ml = desk[i].ml!!.copy(done = true)) }
        val e = AssistantMl.Example(spans, intent, LocalDate.now(), "สอนในแอป")
        val guess = AssistantMl.Guess(intent, 1.0, e)
        mlDoThenKeep(said, AssistantMl.Reading(AssistantMl.Level.HIGH, listOf(guess)), guess, e, spans)
    }

    /** A free time the learning secretary found: quick add with the title, day and time typed in. */
    fun mlPickSlot(title: String, slot: Planner.Slot) {
        val day = "${slot.day.dayOfMonth}/${slot.day.monthNumber}"
        val time = "%d:%02d".format(slot.start.hour, slot.start.minute)
        aiAddTask("$title $day $time")
    }

    /** How many examples are taught, for the settings (read when the sheet opens). */
    suspend fun mlCount(): Int? {
        val vault = _state.value.vault ?: return 0
        return withContext(Dispatchers.IO) { runCatching { AssistantMl.examples(repo.readPath(vault, AssistantMl.PATH)).size }.getOrNull() }
    }

    /**
     * Does a guide [action] as if [label] were tapped. A hand-over to Claude only says it in the chat; the screen opens
     * the sheet to send it ([deskClaude] holds what to ask).
     */
    fun deskRun(label: String, action: SecretaryGuide.Action, via: String? = null) {
        if (action is SecretaryGuide.Action.Claude) {
            desk.add(DeskTurn(mine = label, via = via))
            deskNote(tr("ส่งให้ Claude: ", "Sent to Claude: ") + label)
            deskClaude = SecretaryPretext.Part(label, action.text.ifBlank { label })
            return
        }
        deskAsk(SecretaryDesk.Chip.of(label, action), via = via)
    }

    /** A hand-over to Claude a bubble or a word asked for, waiting for the screen to open the send sheet. */
    var deskClaude by androidx.compose.runtime.mutableStateOf<SecretaryPretext.Part?>(null)

    /** The guide as last read from the vault ([SecretaryGuide.PATH]); the defaults until it is read. */
    var guide by androidx.compose.runtime.mutableStateOf(SecretaryGuide.DEFAULT)
        private set

    /** Reads the guide again (the owner may have changed it in Obsidian). */
    suspend fun loadGuide(): SecretaryGuide.Guide {
        val vault = _state.value.vault ?: return guide
        val text = withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, SecretaryGuide.PATH) }.getOrNull() }
        return SecretaryGuide.parse(text).also { guide = it }
    }

    fun refreshGuide() { viewModelScope.launch { loadGuide() } }

    /**
     * Changes the guide: read fresh, [change] applied, written back, so an edit made in Obsidian meanwhile is kept.
     * One change at a time.
     */
    fun editGuide(change: (SecretaryGuide.Guide) -> SecretaryGuide.Guide) {
        val vault = _state.value.vault
        viewModelScope.launch {
            guideLock.withLock {
                val fresh = if (vault == null) guide else withContext(Dispatchers.IO) {
                    SecretaryGuide.parse(runCatching { repo.readPath(vault, SecretaryGuide.PATH) }.getOrNull())
                }
                val next = change(fresh)
                guide = next
                if (vault != null) {
                    val r = withContext(Dispatchers.IO) { runCatching { repo.writePath(vault, SecretaryGuide.PATH, next.render()) } }
                    r.exceptionOrNull()?.let { e -> showMessage(tr("บันทึกคู่มือไม่ได้: ${e.message}", "Could not save the guide: ${e.message}")) }
                }
            }
        }
    }
    private val guideLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The owner taught a word on the spot: [word] now does [action] (and is a bubble too when [asBubble]), and it is
     * done right away for [said], what was typed.
     */
    fun teach(said: String, word: String, action: SecretaryGuide.Action, asBubble: Boolean) {
        val w = word.trim()
        if (w.isEmpty()) return
        editGuide { g ->
            val words = g.words.filterNot { SecretaryGuide.squash(it.word) == SecretaryGuide.squash(w) } + SecretaryGuide.Word(w, action)
            val bubbles = if (asBubble && g.bubbles.none { it.label == w }) g.bubbles + SecretaryGuide.Bubble(w, action) else g.bubbles
            g.copy(words = words, bubbles = bubbles)
        }
        deskNote(tr("สอนคำ: ", "Taught: ") + "$w → ${action.render()}")
        // The question is answered now, by what was just taught.
        val i = desk.indexOfLast { it.reply?.teach == said }
        if (i >= 0) desk[i] = desk[i].copy(done = true)
        deskRun(said, action)
    }

    /** A task-list filter kept as a bubble of the secretary chat, at the end of the shown ones. */
    fun addFilterBubble(name: String, q: app.omnitask.model.TaskQuery) {
        val label = name.trim()
        if (label.isEmpty()) return
        val bubble = SecretaryGuide.Bubble(label, SecretaryGuide.Action.Show(SecretaryGuide.Filter.of(q)))
        editGuide { g -> g.copy(bubbles = g.bubbles.filterNot { it.label == label } + bubble) }
        showMessage(tr("เพิ่มบอลลูน \"$label\" ในห้องเลขาแล้ว", "Bubble \"$label\" added to the secretary"))
    }

    /** What the taps say about the guide: shown bubbles nobody used for two weeks, unless dismissed. */
    suspend fun guideHints(): List<String> {
        val vault = _state.value.vault ?: return emptyList()
        val stats = withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, DESK_STATS) }.getOrNull() }
        val dismissed = prefs.getStringSet(KEY_GUIDE_DISMISSED, null).orEmpty()
        return SecretaryGuide.unusedBubbles(guide, stats, LocalDate.now()).filter { it !in dismissed }
    }

    /** "ไม่เอา" on an app hint: not offered again. */
    fun dismissGuideHint(label: String) {
        prefs.edit().putStringSet(KEY_GUIDE_DISMISSED, prefs.getStringSet(KEY_GUIDE_DISMISSED, null).orEmpty() + label).apply()
    }

    private fun markLastDone(reply: SecretaryDesk.Reply?) {
        val i = desk.indexOfLast { it.reply === reply }
        if (i >= 0) desk[i] = desk[i].copy(done = true)
    }

    /** The chat so far in plain lines, for Claude to carry on from. */
    fun deskTranscript(): List<String> = desk.flatMap { t ->
        t.mine?.let { listOf("Klui: $it") } ?: t.reply?.let { r ->
            listOf("เลขา: " + (r.lines + listOfNotNull(r.task?.title?.let { "($it)" })).joinToString(" ")) +
                r.moves.map { "  เสนอ ${it.task.title} ไป ${it.date}" } +
                r.items.map { "  ${it.task.title} (${it.note})" }
        }.orEmpty()
    }

    /** The pretext note; written from the defaults the first time it is missing. */
    suspend fun pretext(): SecretaryPretext.Note {
        val vault = _state.value.vault ?: return SecretaryPretext.parse(null)
        val text = withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, SecretaryPretext.PATH) }.getOrNull() }
        if (text == null) withContext(Dispatchers.IO) { runCatching { repo.writePath(vault, SecretaryPretext.PATH, SecretaryPretext.DEFAULT) } }
        return SecretaryPretext.parse(text)
    }

    /** What Claude has learned about the owner (Claude รู้จักคุณ.md), for the owner to read and change. */
    suspend fun knows(): String {
        val vault = _state.value.vault ?: return ""
        return withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, SecretaryPretext.KNOWS_PATH) }.getOrNull().orEmpty() }
    }

    fun saveKnows(text: String) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.writePath(vault, SecretaryPretext.KNOWS_PATH, text.trimEnd() + "\n") } }
    }

    /**
     * The file handed to Claude, kept in the vault ("ส่งเลขา") to read or send again later. Kept files older than a
     * week go, so the folder does not grow: each is a copy of what the vault already holds.
     */
    fun keepSendFile(name: String, text: String) {
        val vault = _state.value.vault ?: return
        val dir = SecretaryBook.SENT_DIR
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.writePath(vault, "$dir/$name", text) }
            runCatching { SecretaryBook.staleSent(repo.namesIn(vault, dir), LocalDate.now()).forEach { repo.deletePath(vault, "$dir/$it") } }
        }
    }

    /** The dates of one project's moves from the secretary's answer, taken together. */
    fun deskApplyMoves(moves: List<SecretaryDesk.Move>) {
        moves.forEach { m -> current(m.task)?.let { setDate(it, m.field, m.date) } }
        desk.add(DeskTurn(reply = SecretaryDesk.Reply(listOf(tr("ลงวันใหม่แล้ว ${moves.size} งาน", "${moves.size} tasks moved")), listOf(SecretaryDesk.Chip(tr("ตอนนี้ทำอะไรดี", "What now"), SecretaryDesk.Ask.NEXT)))))
    }

    fun savePretext(note: SecretaryPretext.Note) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.writePath(vault, SecretaryPretext.PATH, note.render()) } }
    }

    /** Everything Claude gets for [situation]: the pretext, what it knows of the owner, the log, work and calendar, this chat. */
    suspend fun claudePack(note: SecretaryPretext.Note, situation: SecretaryPretext.Part?, count: Boolean = true): String {
        val s = _state.value
        fun read(path: String): String? = s.vault?.let { v -> runCatching { repo.readPath(v, path) }.getOrNull() }
        val (knows, log) = withContext(Dispatchers.IO) { read(SecretaryPretext.KNOWS_PATH) to read(CLAUDE_LOG) }
        if (count) deskNote(tr("ส่งให้ Claude: ", "Sent to Claude: ") + (situation?.title ?: tr("ไม่ระบุเรื่อง", "no topic")))
        return SecretaryPretext.pack(note, situation, knows, log, s.tasks, s.events, { CalendarStyles.label(it.calendarId) }, s.today, deskTranscript(), loadGuide().forClaude)
    }

    /** What the owner taps in the secretary chat, a line a tap, for the secretary to learn habits from (สถิติเลขา.md). */
    private fun deskNote(what: String) {
        val vault = _state.value.vault ?: return
        val line = "- ${LocalDate.now()} ${nowHm()} $what"
        viewModelScope.launch(Dispatchers.IO) {
            deskLock.withLock {
                runCatching {
                    val old = repo.readPath(vault, DESK_STATS) ?: "# สถิติเลขา\n\nสิ่งที่กดในแชตเลขาของ Omni บรรทัดละครั้ง ใหม่สุดอยู่ล่าง เลขาอ่านเพื่อเรียนรู้นิสัย แล้วเขียนสิ่งที่พบลง Claude รู้จักคุณ.md\n"
                    // Keep the last 400 taps; older ones have been read by then.
                    val lines = (old.trimEnd() + "\n" + line).lines()
                    val head = lines.takeWhile { !it.startsWith("- ") }
                    val taps = lines.filter { it.startsWith("- ") }.takeLast(400)
                    repo.writePath(vault, DESK_STATS, (head + taps).joinToString("\n") + "\n")
                }
            }
        }
    }
    private val deskLock = kotlinx.coroutines.sync.Mutex()

    fun freshVoiceTitles(): Set<String> {
        val cut = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return voiceFresh.all.filter { (it.value as? Long ?: 0L) > cut }.keys
    }

    private fun watchVoice() {
        val app = getApplication<Application>()
        _voice.update { it.copy(waiting = VoiceRec.waiting(app)) }
        // Once, after the update that brought it: how to start.
        if (!voiceFresh.getBoolean("voice.hinted", false)) {
            voiceFresh.edit().putBoolean("voice.hinted", true).apply()
            showMessage(tr("ใหม่: กดค้างที่ปุ่ม + เพื่อจดงานด้วยเสียง", "New: hold the + button to note tasks by voice"))
        }
        viewModelScope.launch {
            VoiceRec.now.collect { r ->
                if (r != null) {
                    voiceRemoved.clear()
                    voiceNextId = 1
                    _voice.update { it.copy(recording = r, words = "", live = emptyList(), sending = 0, unsent = 0, review = null, failed = null) }
                } else _voice.update { it.copy(recording = null, waiting = VoiceRec.waiting(app)) }
            }
        }
        viewModelScope.launch { VoiceRec.pieces.collect { piece -> launch { sendPiece(piece) } } }
        viewModelScope.launch { VoiceRec.done.collect { dir -> launch { finish(dir) } } }
        viewModelScope.launch { VoiceRec.failed.collect { showMessage(it) } }
    }

    fun startVoice() = VoiceRec.start(getApplication())

    fun stopVoice() = VoiceRec.stop(getApplication())

    fun discardRecording() = VoiceRec.discard(getApplication())

    /** Writes one piece down and lays Gemini's list over the live one; false when it could not be sent. */
    private suspend fun sendPiece(piece: java.io.File): Boolean {
        val app = getApplication<Application>()
        _voice.update { it.copy(sending = it.sending + 1) }
        return voiceLock.withLock {
            val v = _voice.value
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val audio = android.util.Base64.encodeToString(piece.readBytes(), android.util.Base64.NO_WRAP)
                    val body = VoiceNotes.pieceRequest(audio, "audio/wav", v.words, v.live.map { it.text }, voiceRemoved.toList())
                    VoiceNotes.parsePiece(AiAssist.answerText(Gemini(app).generate(body))).also { (text, _) -> VoiceRec.keepHeard(piece, text) }
                }
            }
            result.fold(
                { (text, tasks) ->
                    _voice.update { now ->
                        val merged = VoiceNotes.merge(now.live, tasks, voiceRemoved, voiceNextId)
                        voiceNextId = (merged.maxOfOrNull { it.id } ?: 0).coerceAtLeast(voiceNextId - 1) + 1
                        now.copy(words = listOf(now.words, text).filter { it.isNotBlank() }.joinToString(" "), live = merged, sending = now.sending - 1)
                    }
                    true
                },
                { e -> _voice.update { it.copy(sending = it.sending - 1, unsent = it.unsent + 1, failed = e.message) }; false },
            )
        }
    }

    /** The owner changed a task's words while recording or checking: later pieces leave it as written. */
    fun editLive(id: Int, text: String) = _voice.update { v -> v.copy(live = v.live.map { if (it.id == id) it.copy(text = text, locked = true, fresh = false) else it }) }

    fun removeLive(id: Int) = _voice.update { v ->
        v.live.firstOrNull { it.id == id }?.let { voiceRemoved += it.heard; if (it.text != it.heard) voiceRemoved += it.text }
        v.copy(live = v.live.filterNot { it.id == id })
    }

    /**
     * After a recording stops (or when one left over is tried again): pieces not written down yet are sent, the
     * words are read once more to tidy the list, then the tasks wait in [VoiceUi.review].
     */
    private suspend fun finish(dir: java.io.File, again: Boolean = false) {
        val app = getApplication<Application>()
        _voice.update { it.copy(busy = true, failed = null) }
        if (again) {
            // A recording from an earlier launch: rebuild its words from the pieces already written down.
            voiceRemoved.clear()
            voiceNextId = 1
            _voice.update { it.copy(words = VoiceRec.pieces(dir).mapNotNull(VoiceRec::heard).filter { t -> t.isNotBlank() }.joinToString(" "), live = emptyList(), sending = 0, unsent = 0) }
        }
        // Pieces cut just before the stop may still be on their way: wait for them, so none is sent twice.
        if (!again) { kotlinx.coroutines.delay(400); _voice.first { it.sending <= 0 } }
        val missing = VoiceRec.pieces(dir).filter { VoiceRec.heard(it) == null }
        var ok = true
        for (p in missing) if (!sendPieceAgain(p, again)) ok = false
        if (!ok) {
            _voice.update { it.copy(busy = false, waiting = VoiceRec.waiting(app), failed = it.failed ?: tr("ถอดเสียงไม่ครบ", "Some of it was not written down")) }
            return
        }
        val v = _voice.value
        val tidy = withContext(Dispatchers.IO) {
            runCatching { VoiceNotes.parse(AiAssist.answerText(Gemini(app).generate(VoiceNotes.tidyRequest(v.words, v.live.map { it.text }, voiceRemoved.toList())))) }
        }.getOrNull()
        val live = if (tidy != null) VoiceNotes.merge(v.live, tidy, voiceRemoved, voiceNextId) else v.live
        val since = VoiceRec.since(dir) ?: dir.lastModified()
        val seconds = (VoiceRec.pieces(dir).maxOfOrNull { it.lastModified() } ?: since).minus(since).coerceAtLeast(0) / 1000
        val open = _state.value.tasks
        val now = java.time.LocalTime.now().let { kotlinx.datetime.LocalTime(it.hour, it.minute) }
        val items = live.map {
            val heard = SpokenWhen.read(it.text, _state.value.timeWords, LocalDate.now(), now)
            VoiceItem(it.text, VoiceNotes.duplicateOf(heard.title, open), heard)
        }
        _voice.update { it.copy(busy = false, live = live, unsent = 0, review = VoiceReview(dir, seconds, items), waiting = VoiceRec.waiting(app)) }
    }

    /** Sends a piece that was missed: during a recording's own finish its unsent count goes back down. */
    private suspend fun sendPieceAgain(piece: java.io.File, again: Boolean): Boolean {
        if (!again) _voice.update { it.copy(unsent = (it.unsent - 1).coerceAtLeast(0)) }
        return sendPiece(piece)
    }

    /** Tries a left-over recording again. */
    fun transcribe(dir: java.io.File) {
        if (_voice.value.busy || _voice.value.recording != null) return
        viewModelScope.launch { finish(dir, again = true) }
    }

    /**
     * Puts the checked tasks at the end of the task note, with the day, time and project the owner left on each (a
     * task with none is a bare line in the no-project tray), then drops the recording.
     */
    fun saveVoice(drafts: List<VoiceDraft>) {
        val review = _voice.value.review ?: return
        val vault = _state.value.vault ?: return
        val app = getApplication<Application>()
        val today = LocalDate.now()
        val all = drafts.map { it.copy(heard = it.heard.copy(title = VoiceNotes.clean(it.heard.title))) }.filter { it.heard.title.isNotEmpty() }
        // "ลงปฏิทิน" items go to the calendar quick add uses (an hour at the time said, or all day).
        val events = all.filter { it.heard.event && it.heard.date != null }
        val kept = all.filter { !it.heard.event }
        if (events.isNotEmpty()) {
            val cals = _state.value.calendars.filter { it.writable && it.visible }
            val chosen = CalendarReader.addTo(app)
            val cal = cals.firstOrNull { it.id == chosen } ?: cals.firstOrNull { it.isGoogle && it.name == it.account } ?: cals.firstOrNull { it.isGoogle } ?: cals.firstOrNull()
            if (cal == null || !CalendarReader.canWrite(app)) {
                showMessage(tr("ลงปฏิทินไม่ได้: ยังไม่มีปฏิทินที่เพิ่มนัดได้ หรือยังไม่อนุญาต ลองเพิ่มนัดจากปุ่ม + ก่อนหนึ่งครั้ง", "No calendar to add events to yet: add one event from + first"))
                return
            }
            events.forEach { e ->
                val h = e.heard
                val t = h.time
                if (t == null) addEvent(cal.id, h.title, kotlinx.datetime.LocalDateTime(h.date!!, kotlinx.datetime.LocalTime(0, 0)), kotlinx.datetime.LocalDateTime(h.date!!, kotlinx.datetime.LocalTime(0, 0)), true)
                else {
                    val start = kotlinx.datetime.LocalDateTime(h.date!!, t)
                    val endDay = if (t.hour == 23) h.date!!.plusDays(1) else h.date!!
                    addEvent(cal.id, h.title, start, kotlinx.datetime.LocalDateTime(endDay, kotlinx.datetime.LocalTime((t.hour + 1) % 24, t.minute)), false)
                }
            }
        }
        val lines = kept.map { SpokenWhen.line(it.heard, it.project, today) }
        val titles = kept.map { it.heard.title }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { if (lines.isNotEmpty()) repo.appendLine(vault, VaultRepository.TASK_FILE, lines.joinToString("\n")) }
            }
            result.fold(
                {
                    review.dir.deleteRecursively()
                    val now = System.currentTimeMillis()
                    val stale = voiceFresh.all.filter { (it.value as? Long)?.let { at -> now - at > 2 * 24 * 60 * 60 * 1000L } == true }.keys
                    voiceFresh.edit().apply { stale.forEach { remove(it) }; titles.forEach { putLong(it, now) } }.apply()
                    lines.forEach { noteCreated(it) }
                    _voice.update { it.copy(review = null, live = emptyList(), words = "", waiting = VoiceRec.waiting(app)) }
                    showMessage(tr("บันทึก ${lines.size} งาน ${events.size} นัดแล้ว", "Saved ${lines.size} tasks, ${events.size} events"))
                    reload()
                },
                { e -> showMessage(tr("บันทึกไม่ได้: ${e.message}", "Could not save: ${e.message}")) },
            )
        }
    }

    fun setTimeWords(words: List<TimeWords.Word>) {
        _state.update { it.copy(timeWords = words) }
        val vault = _state.value.vault ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.writePath(vault, TimeWords.PATH, TimeWords.render(words)) } }
    }

    /** Throws away a recording that is under review or waiting. */
    fun dropVoice(dir: java.io.File) {
        val app = getApplication<Application>()
        dir.deleteRecursively()
        _voice.update { v -> v.copy(review = v.review?.takeIf { it.dir != dir }, live = emptyList(), words = "", failed = null, waiting = VoiceRec.waiting(app)) }
    }

    /** Closes the check without saving: the recording waits on the focus page. */
    fun closeVoiceReview() = _voice.update { it.copy(review = null) }

    // After the voice properties above, which an init block placed earlier would find not yet set.
    init { watchVoice() }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        getApplication<Application>().contentResolver.unregisterContentObserver(calendarObserver)
    }

    /** The events every screen needs: the month view plus the Gantt's three weeks, which can run into next month. */
    private fun loadEvents(app: Application): List<CalendarEvent> = runCatching {
        val today = LocalDate.now()
        val now = CalendarReader.month(app, today) + CalendarReader.events(app, today.minusDays(7).atStartOfDay(), today.plusDays(35).atStartOfDay())
        val other = shownMonth?.takeIf { it.withDayOfMonth(1) != LocalDate.now().withDayOfMonth(1) }?.let { CalendarReader.month(app, it) }.orEmpty()
        CalendarEvent.distinct(now + other)
    }.getOrDefault(emptyList())

    /** The pinned countdown events over the year ahead, by their ids on this phone. */
    private fun loadPinned(app: Application, pins: List<Countdown.Pin>): List<CalendarEvent> = runCatching {
        val ids = pins.filterIsInstance<Countdown.EventPin>().map { it.id }.filter { it != 0L }.toSet()
        if (ids.isEmpty()) return@runCatching emptyList<CalendarEvent>()
        val today = LocalDate.now()
        CalendarReader.events(app, today.minusDays(1).atStartOfDay(), today.plusDays(400).atStartOfDay(), ids)
    }.getOrDefault(emptyList())

    /** Re-reads only the calendar (not the vault) once a sync settles, then redraws the widgets so they follow too. */
    private fun refreshEvents() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            delay(1500)
            val app = getApplication<Application>()
            val (events, calendars) = withContext(Dispatchers.IO) { loadEvents(app) to CalendarReader.calendars(app) }
            val pinned = withContext(Dispatchers.IO) { loadPinned(app, _state.value.countdowns) }
            _state.update { it.copy(events = events, calendars = calendars, pinnedEvents = pinned) }
            // A sync that changed nothing leaves the widgets as they are.
            runCatching { withContext<Unit>(Dispatchers.IO) { CalendarPrint.refreshIfChanged(app, "app saw the calendar") } }
        }
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
    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun showCrash(report: String) = _state.update { it.copy(crash = report) }

    fun dismissCrash() {
        CrashLog.clear(getApplication())
        _state.update { it.copy(crash = null) }
    }

    /** Pull to refresh: asks Google Calendar to sync now, past the once-a-minute limit, then reloads everything. */
    fun pullRefresh() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { runCatching { CalendarReader.requestSync(app, force = true) } }
        reload()
        checkWithGoogle(force = true)
    }

    /**
     * Asks Google which events still exist and hides the ones the phone kept after they were deleted there
     * ([GoogleCalendarCheck]); when that changes anything, the calendar and the widgets are read again.
     */
    private fun checkWithGoogle(force: Boolean = false) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) { runCatching { GoogleCalendarCheck.refresh(app, force) }.getOrDefault(false) }
            if (changed) {
                refreshEvents()
                launch { runCatching { OmniWidgets.refresh(app) } }
            }
        }
    }


    /** Set when the app comes to the front, so the next load checks once whether Obsidian has synced the book. */
    @Volatile private var checkSyncOnLoad = false

    /** The app came to the front: read the vault again, and check whether this phone's copy is behind the secretary. */
    fun resumed() {
        checkSyncOnLoad = true
        reload()
    }

    /**
     * When this phone's Claude log is older than the newest secretary round that should be in it, Obsidian has not
     * synced (it syncs only while open): open Obsidian so it does (owner, 2026-10-26). Once per round, so a secretary
     * that really stopped does not send the owner to Obsidian again and again; the warning card covers that case.
     */
    private fun openObsidianIfBehind(last: Long?, attempt: Long?) {
        val now = System.currentTimeMillis()
        if (!Secretary.behind(last, now, attempt)) return
        val round = Secretary.expectedRound(now)
        if (prefs.getLong(KEY_SYNC_OPENED_FOR, 0L) == round) return
        prefs.edit().putLong(KEY_SYNC_OPENED_FOR, round).apply()
        val vault = _state.value.vaultName
        val uri = if (vault != null) "obsidian://open?vault=${Uri.encode(vault)}" else "obsidian://open"
        val opened = runCatching {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        if (opened.isSuccess) {
            showMessage(tr("งานในเครื่องเก่ากว่ารอบเลขาล่าสุด เปิด Obsidian ให้ซิงก์ ซิงก์เสร็จแล้วกลับมาที่ Omni ได้เลย", "Your copy is older than the secretary's last round. Opened Obsidian to sync; come back when it is done"))
        } else {
            showMessage(tr("งานในเครื่องเก่ากว่ารอบเลขาล่าสุด แต่เปิด Obsidian ไม่ได้ เปิด Obsidian เองให้ซิงก์", "Your copy is older than the secretary's last round, and Obsidian could not be opened: open it yourself to sync"))
        }
    }

    fun reload() {
        val vault = _state.value.vault ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // Settings saved from another phone (or before the app was cleared) come in first.
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    SettingsSync.tidy(repo, vault)
                    SettingsSync.load(getApplication(), repo, vault).also { loaded ->
                        // First run with this vault: start the file from what this phone has.
                        if (!loaded && SettingsSync.read(repo, vault) == null) SettingsSync.save(getApplication(), repo, vault)
                    }
                }.getOrDefault(false)
            }
            // The month widget's frames and emoji, saved on another phone or before the app was cleared.
            withContext(Dispatchers.IO) { runCatching { MonthMarksStore.sync(getApplication(), repo, vault) } }
            if (imported) {
                Lang.load(getApplication())
                Appearance.load(getApplication())
                _state.update { fromPrefs(it) }
            }
            // What the owner did on every device, for the assistant to learn from; what this phone kept goes out first.
            behavior = withContext(Dispatchers.IO) {
                runCatching { BehaviorLog.sync(getApplication(), repo, vault) }.getOrElse { (behavior + BehaviorLog.waiting(getApplication())).distinctBy { e -> e.id } }
            }
            val swept = withContext(Dispatchers.IO) { runCatching { sweepIfDue(vault) }.getOrDefault(emptyList()) }
            // The owner's project names and moves, read before the tasks so an old name counts as the new one.
            loadProjectNames(vault)
            val result = withContext(Dispatchers.IO) {
                runCatching { repo.load(vault) }.onSuccess { snap ->
                    // Every load refreshes the reminders and the widgets, so edits made anywhere reach both.
                    // Tasks in parked branches stay quiet.
                    val states = _state.value.branchStates
                    runCatching { Scheduler.reschedule(getApplication(), snap.tasks.filterNot { Branches.isParked(it, states) }) }
                }
            }
            val app = getApplication<Application>()
            launch { runCatching { OmniWidgets.refresh(app) } }
            val events = withContext(Dispatchers.IO) {
                runCatching { CalendarReader.requestSync(app) }
                loadEvents(app)
            }
            checkWithGoogle()
            val calendars = withContext(Dispatchers.IO) { CalendarReader.calendars(app) }
            val pinned = withContext(Dispatchers.IO) { loadPinned(app, _state.value.countdowns) }
            val timeWords = withContext(Dispatchers.IO) { runCatching { TimeWords.parse(repo.readPath(vault, TimeWords.PATH)) }.getOrDefault(_state.value.timeWords) }
            val profile = withContext(Dispatchers.IO) { runCatching { Profile.parse(repo.readPath(vault, Profile.PATH) ?: repo.readPath(vault, Profile.LEGACY_PATH)) }.getOrDefault(_state.value.profile) }
            result.getOrNull()?.lists?.let { ls -> ignoreTags(ls, _state.value.customKinds) }
            // Never chosen: the projects every first tag named so far are kept as the choice, so none disappears.
            if (_state.value.projectTags == null) {
                result.getOrNull()?.tasks?.filter { it.list == null }?.let { saveProjectTags(Projects.seed(it)) }
            } else {
                // A task the secretary just wrote with a new project's tag: that project gets its branch.
                val fresh = result.getOrNull()?.tasks?.let { Projects.newOnes(it, _state.value.projectTags.orEmpty(), prefs.getStringSet(KEY_OFF_PROJECTS, null).orEmpty(), LocalDate.now()) }.orEmpty()
                if (fresh.isNotEmpty()) saveProjectTags(_state.value.projectTags.orEmpty() + fresh)
            }
            // When the secretary last wrote Claude log, so the today page can say when it stopped.
            val logText = withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, CLAUDE_LOG) }.getOrNull() }
            claudeLogText = logText
            val secretary = Secretary.lastRun(logText)
            _state.update { it.copy(ai = it.ai.copy(secretaryLast = secretary)) }
            // The secretary's questions; a file that cannot be read keeps the last ones rather than hiding them.
            val askText = withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, SecretaryAsk.PATH) } }
            askText.getOrNull().let { t -> if (askText.isSuccess) _state.update { it.copy(secretaryAsks = SecretaryAsk.parse(t)) } }
            if (checkSyncOnLoad) {
                checkSyncOnLoad = false
                val vaultName = _state.value.vaultName ?: withContext(Dispatchers.IO) { runCatching { repo.vaultName(vault) }.getOrNull() }
                if (vaultName != null && _state.value.vaultName == null) _state.update { it.copy(vaultName = vaultName) }
                val health = withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, Secretary.HEALTH_PATH) }.getOrNull() }
                openObsidianIfBehind(secretary, Secretary.lastAttempt(health))
            }
            val vaultName = _state.value.vaultName ?: withContext(Dispatchers.IO) { runCatching { repo.vaultName(vault) }.getOrNull() }
            _state.update {
                it.copy(
                    events = events,
                    pinnedEvents = pinned,
                    calendarAccess = CalendarReader.hasPermission(app),
                    calendars = calendars,
                    loading = false,
                    today = LocalDate.now(),
                    tasks = result.getOrNull()?.tasks?.filter { t -> t.list == null && !Branches.isParked(t, it.branchStates) } ?: it.tasks,
                    parked = result.getOrNull()?.tasks?.filter { t -> t.list == null && Branches.isParked(t, it.branchStates) } ?: it.parked,
                    listItems = result.getOrNull()?.tasks?.filter { t -> t.list != null } ?: it.listItems,
                    lists = result.getOrNull()?.lists ?: it.lists,
                    notePaths = result.getOrNull()?.notePaths ?: it.notePaths,
                    conflicts = result.getOrNull()?.conflicts ?: it.conflicts,
                    vaultName = vaultName,
                    profile = profile,
                    timeWords = timeWords,
                    insight = Insight.next(result.getOrNull()?.tasks ?: it.tasks, doneLog(), profile, LocalDate.now(), declined(), behaviorNow()),
                    drive = driveUi(it.drive, result.getOrNull()?.drive),
                    message = result.exceptionOrNull()?.let { e -> tr("อ่านตู้โน้ตไม่ได้: ${e.message}", "Cannot read the vault: ${e.message}") }
                        ?: result.getOrNull()?.drive?.lost?.takeIf { l -> l.isNotEmpty() }?.let { l ->
                            tr(
                                "การแก้ตอนออฟไลน์ชนกับการแก้จากที่อื่น จึงใช้ของใน Drive ไว้ ที่แก้ไว้คือ:\n${l.joinToString("\n")}",
                                "An offline edit clashed with a change made elsewhere, so Drive's version was kept. Your edit was:\n${l.joinToString("\n")}",
                            )
                        }
                        ?: result.getOrNull()?.drive?.takeIf { d -> d.signIn && !it.drive.signIn }?.let {
                            tr("ต้องลงชื่อ Google อีกครั้งในหน้าตั้งค่า การแก้ยังเก็บไว้ในเครื่อง", "Sign in to Google again in Settings. Your edits are kept on the phone.")
                        }
                        ?: swept.takeIf { s -> s.isNotEmpty() }?.let { s -> tr("ย้ายงานที่เสร็จ ${s.size} งานเข้าคลังแล้ว", "Moved ${s.size} finished tasks to the archive") }
                        ?: it.message,
                )
            }
        }
    }

    /** The Drive card's state after a load: [status] is null when Drive is not in use. */
    private fun driveUi(old: DriveUi, status: VaultRepository.DriveStatus?): DriveUi =
        if (status == null) old.copy(on = DriveLink.isOn(getApplication()))
        else old.copy(on = true, online = status.online, signIn = status.signIn, pending = status.pending, lastSync = status.lastSync, kept = status.kept)

    /** Adds the phone's lines of a kept clash to the end of their note. */
    fun putBackClash(id: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.putBackClash(id) } }
            _state.update {
                it.copy(
                    message = if (result.getOrNull() == true) tr("ใส่บรรทัดไว้ท้ายโน้ตแล้ว", "Added the lines to the end of the note")
                    else tr("ใส่กลับไม่ได้: ", "Could not add them back: ") + (result.exceptionOrNull()?.message ?: ""),
                )
            }
            reload()
        }
    }

    /** Lets a kept clash go. */
    fun dismissClash(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { repo.dismissClash(id) } }
            _state.update { it.copy(drive = it.drive.copy(kept = it.drive.kept.filterNot { k -> k.id == id })) }
        }
    }

    /**
     * Connects the Omni folder on Google Drive: asks Google for access, showing its screen when it needs to
     * ([DriveUi.signInIntent], answered through [driveSignedIn]), then finds the folder.
     */
    fun connectDrive() {
        _state.update { it.copy(drive = it.drive.copy(busy = true)) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DriveLink.authorize(getApplication()) } }
            result.fold(
                { r -> if (r.hasResolution()) _state.update { it.copy(drive = it.drive.copy(signInIntent = r.pendingIntent)) } else findDriveFolder() },
                { e -> driveFailed(e) },
            )
        }
    }

    fun driveSignInShown() = _state.update { it.copy(drive = it.drive.copy(signInIntent = null)) }

    /** Google's sign-in screen closed; [data] carries its answer. */
    fun driveSignedIn(data: Intent?) {
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { runCatching { DriveLink.tokenFrom(getApplication(), data) }.getOrNull() }
            if (token == null) {
                _state.update { it.copy(drive = it.drive.copy(busy = false), message = tr("ยังไม่ได้อนุญาตให้ใช้ Google Drive", "Google Drive access was not given")) }
            } else {
                findDriveFolder()
            }
        }
    }

    /**
     * Finds the Omni folder on Drive. When the phone's copy of the task note differs from Drive's, the sync app may
     * still hold edits made on the phone, so the owner is asked first ([DriveUi.confirmDir]).
     */
    private suspend fun findDriveFolder() {
        val app = getApplication<Application>()
        // Signed in again for a folder already connected: read again and send what waited.
        if (DriveLink.isOn(app)) {
            _state.update { it.copy(drive = it.drive.copy(busy = false, signIn = false)) }
            reload()
            return
        }
        val vault = _state.value.vault
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val drive = DriveLink.drive(app)
                val dir = OmniDrive.locate(drive, VaultText.OMNI_DIR, vault?.let { repo.vaultName(it) }) ?: return@runCatching null
                val name = OmniDrive.nameOf(VaultRepository.TASK_FILE)
                val remote = drive.childrenOfMany(listOf(dir)).firstOrNull { !it.isFolder && sameName(it.name, name) }?.let { drive.read(it.id).text }
                val local = vault?.let { repo.readPath(it, VaultRepository.TASK_FILE) }
                dir to (local == null || remote == null || local == remote)
            }
        }
        found.fold(
            { pair ->
                when {
                    pair == null -> _state.update {
                        it.copy(
                            drive = it.drive.copy(busy = false),
                            message = tr("ไม่พบโฟลเดอร์ ${VaultText.OMNI_DIR} ใน Google Drive", "Folder ${VaultText.OMNI_DIR} not found in Google Drive"),
                        )
                    }
                    pair.second -> finishDriveConnect(pair.first)
                    else -> _state.update { it.copy(drive = it.drive.copy(busy = false, confirmDir = pair.first)) }
                }
            },
            { e -> driveFailed(e) },
        )
    }

    /** The answer to "the phone's copy differs": connect anyway (Drive's copy is used) or not now. */
    fun confirmDrive(connect: Boolean) {
        val dir = _state.value.drive.confirmDir ?: return
        _state.update { it.copy(drive = it.drive.copy(confirmDir = null)) }
        if (connect) viewModelScope.launch { finishDriveConnect(dir) }
    }

    private suspend fun finishDriveConnect(dir: String) {
        withContext(Dispatchers.IO) { DriveLink.connect(getApplication(), dir) }
        _state.update {
            it.copy(
                drive = it.drive.copy(on = true, busy = false, signIn = false),
                message = tr("เชื่อม Google Drive แล้ว ไฟล์ในโฟลเดอร์ Omni อ่านและเขียนบน Drive โดยตรง", "Google Drive connected. Files in the Omni folder are read and written on Drive directly."),
            )
        }
        reload()
    }

    /** Back to the picked folder for the Omni files; edits not sent yet are dropped, which Settings warns about. */
    fun disconnectDrive() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DriveLink.disconnect(getApplication()) }
            _state.update { it.copy(drive = DriveUi()) }
            reload()
        }
    }

    private fun driveFailed(e: Throwable) {
        val cause = (e as? java.util.concurrent.ExecutionException)?.cause ?: e
        val detail = if (cause is com.google.android.gms.common.api.ApiException) "${cause.statusCode} ${cause.message.orEmpty()}" else cause.message.orEmpty()
        _state.update {
            it.copy(
                drive = it.drive.copy(busy = false),
                message = tr("เชื่อม Google Drive ไม่ได้: $detail", "Cannot connect Google Drive: $detail"),
            )
        }
    }

    /** Ticks [task] done, or opens it again; [inTree] when ticked on Ygdrasil, which keeps its own undo history. */
    fun toggleDone(task: Task, inTree: Boolean = false) {
        // A repeating task moves on with its subtasks opened again, so there is nothing to ask about them.
        if (task.recurrence != null && task.isOpen) return completeRecurring(task, inTree)
        if (task.isOpen && _state.value.subtasksOf(task).any { it.isOpen }) {
            closingInTree = inTree
            _state.update { it.copy(closingParent = task) }
            return
        }
        val event = if (task.isOpen) logDone(task) else null
        val next = TaskLine.setDone(task.raw, task.isOpen, LocalDate.now())
        val kind = if (task.isOpen) UndoStep.Kind.DONE else UndoStep.Kind.OPENED
        editRecorded(task, kind, inTree, listOfNotNull(event), onSaved = { if (task.isOpen) offerFinish(task, next) }) { next }
    }

    /** Whether the "tick the open subtasks too?" question was asked from Ygdrasil. */
    private var closingInTree = false

    private fun descendants(task: Task): List<Task> = _state.value.subtasksOf(task).flatMap { listOf(it) + descendants(it) }

    /**
     * After a task is ticked done, asks whether to archive or delete it. Project work stays done in place, and
     * so do subtasks, list items, repeating tasks and anything with work still open under it.
     * [ticked] are the subtasks closed in the same step.
     */
    private fun offerFinish(task: Task, raw: String, ticked: Set<String> = emptySet()) {
        if (!_state.value.askOnDone || task.parent != null || task.list != null || task.recurrence != null || Projects.projectOf(task) != null) return
        if (descendants(task).any { it.isOpen && it.key !in ticked }) return
        _state.update { it.copy(finished = task.copy(raw = raw, status = Status.DONE)) }
    }

    /** The "archive or delete?" bar closes, and with it the tick's undo, which it carries. */
    fun clearFinished() = _state.update { it.copy(finished = null, ticked = emptyList()) }

    /** Answers the "archive or delete?" question for the task just finished. */
    fun resolveFinished(archive: Boolean) {
        val task = _state.value.finished ?: return
        _state.update { it.copy(finished = null, ticked = emptyList()) }
        if (archive) archiveTask(task) else deleteTask(task)
    }

    /** Moves a finished task with its whole block to the archive note; the snackbar offers undo. */
    fun archiveTask(task: Task) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.archiveTask(vault, task, LocalDate.now()) } }
            result.fold(
                { cut -> _state.update { it.copy(archived = Deleted(task.title, cut)) } },
                { e ->
                    val text = if (e is VaultRepository.ConflictException) {
                        tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere and was reloaded. Try again.")
                    } else {
                        tr("ย้ายเข้าคลังไม่ได้: ${e.message}", "Cannot archive: ${e.message}")
                    }
                    _state.update { it.copy(message = text) }
                },
            )
            reload()
        }
    }

    fun clearArchived() = _state.update { it.copy(archived = null) }

    fun undoArchive(d: Deleted) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.unarchive(vault, d.cut) } }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("เอากลับไม่ได้: ${e.message}", "Cannot undo: ${e.message}")) } }
            reload()
        }
    }

    /** How many days finished tasks wait before moving to the archive; 0 turns it off. The next load sweeps. */
    fun setArchiveDays(days: Int) {
        prefs.edit().putInt(KEY_ARCHIVE_DAYS, days).remove(KEY_ARCHIVE_SWEPT).apply()
        _state.update { it.copy(archiveDays = days) }
        reload()
    }

    fun setAskOnDone(ask: Boolean) {
        prefs.edit().putBoolean(KEY_ASK_ON_DONE, ask).apply()
        _state.update { it.copy(askOnDone = ask) }
    }

    /**
     * Once a day, moves finished tasks closed at least [UiState.archiveDays] days ago from the TaskForge note
     * to the archive note. Project work stays. Returns the titles moved.
     */
    private fun sweepIfDue(vault: Uri): List<String> {
        val days = prefs.getInt(KEY_ARCHIVE_DAYS, DEFAULT_ARCHIVE_DAYS)
        val today = LocalDate.now()
        if (days <= 0 || prefs.getString(KEY_ARCHIVE_SWEPT, null) == today.toString()) return emptyList()
        // A conflict copy means the phone may hold an older note than Drive; the sweep waits (and tries again next open).
        if (repo.hasTaskConflict(vault)) return emptyList()
        val moved = repo.sweepDone(vault, today.minusDays(days.toLong()), today) { Projects.projectOf(it) != null }
        prefs.edit().putString(KEY_ARCHIVE_SWEPT, today.toString()).apply()
        return moved
    }

    /** Completes a repeating task by moving its line on to the next occurrence; no copy is added. */
    private fun completeRecurring(task: Task, inTree: Boolean) {
        val event = logDone(task)
        TaskLine.advanceRecurring(task.raw, LocalDate.now(), task.todayCopy)?.let { showEdited(task, it) }
        viewModelScope.launch {
            val today = LocalDate.now()
            var block: VaultRepository.BlockChange? = null
            val result = writeLock.withLock { withContext(Dispatchers.IO) { runCatching { repo.completeRecurring(task, today) { block = it } } } }
            block?.let { b -> remember(UndoStep(UndoStep.Kind.DONE, task.title, listOf(b), listOfNotNull(event), listOf(task.title to today)), inTree) }
            val text = result.fold(
                { made ->
                    if (made) {
                        val next = TaskLine.parse(TaskLine.advanceRecurring(task.raw, LocalDate.now(), task.todayCopy) ?: "")?.let { it.due ?: it.scheduled ?: it.start }
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
            if (result.getOrNull() == true) reloadNote(task) else reload()
        }
    }

    fun setStatus(task: Task, status: Status) {
        if (status == Status.DONE && task.recurrence != null) return toggleDone(task)
        if (status == Status.DONE && task.isOpen) logDone(task)
        val next = TaskLine.setStatus(task.raw, status, LocalDate.now())
        edit(task, onSaved = { if (status == Status.DONE && task.isOpen) offerFinish(task, next) }) { next }
    }

    fun setQuery(query: TaskQuery) = _state.update { it.copy(query = query) }

    /** Saves [query] (the task list's own, unless another page passes its filter) under [name]. */
    fun saveFilter(name: String, query: TaskQuery = _state.value.query) {
        val next = _state.value.savedFilters + (name.trim() to query.encode())
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

    fun setReminder(task: Task, time: kotlinx.datetime.LocalTime?, on: ReminderOn) = edit(task) { TaskLine.setReminder(it, time, on) }

    fun setDate(task: Task, field: DateField, value: LocalDate?) {
        learn(Behavior.moved(task, field.name, value, LocalDateTime.now(), BehaviorLog.SRC))
        edit(task) { TaskLine.setDate(it, field, value) }
    }

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
            _state.update { s -> s.copy(events = CalendarEvent.distinct(s.events + more)) }
        }
    }

    /** Calendar access just changed: read the events again and let the alarms pick them up. */
    fun calendarChanged() = reload()

    /** Switches a calendar on or off in Omni, then reads the events again so every screen follows. */
    fun setCalendarShown(id: Long, shown: Boolean) {
        CalendarReader.setShown(getApplication(), id, shown)
        _state.update { it.copy(hiddenCalendars = CalendarReader.hiddenIds(getApplication())) }
        reload()
    }

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
            var line = clearKinds(task, raw)
            kind.tag?.let { tag -> line = TaskLine.addTag(line, if (kind == TaskKind.WAITING && !who.isNullOrBlank()) "$tag/${who.trim()}" else tag) }
            line
        }
    }

    /** Gives the task one of the owner's own kinds, in place of any other kind. */
    fun setCustomKind(task: Task, kind: CustomKind) = edit(task) { raw -> TaskLine.addTag(clearKinds(task, raw), kind.tag) }

    private fun clearKinds(task: Task, raw: String): String {
        val custom = _state.value.customKinds.map { it.tag.lowercase() }
        val tags = TaskKind.kindTags(task) + task.tags.filter { it.lowercase() in custom }
        return tags.fold(raw) { acc, tag -> TaskLine.removeTag(acc, tag) }
    }

    fun addCustomKind(name: String, emoji: String) {
        val tag = CustomKind.tagFor(name)
        if (tag.isEmpty() || _state.value.customKinds.any { it.tag.equals(tag, ignoreCase = true) }) return
        saveKinds(_state.value.customKinds + CustomKind(name.trim(), emoji, tag))
    }

    /** Forgets a kind; the tags already on tasks stay in the vault as ordinary tags. */
    fun removeCustomKind(kind: CustomKind) = saveKinds(_state.value.customKinds - kind)

    private fun saveKinds(kinds: List<CustomKind>) {
        prefs.edit().putStringSet(KEY_CUSTOM_KINDS, CustomKind.encode(kinds)).apply()
        val sorted = kinds.sortedBy { it.name.lowercase() }
        _state.update { it.copy(customKinds = sorted) }
        ignoreTags(_state.value.lists, sorted)
    }

    /** Hides a built-in kind from the choices (and its card on Focus), or brings it back. */
    fun toggleHiddenKind(kind: TaskKind) {
        val next = _state.value.hiddenKinds.let { if (kind in it) it - kind else it + kind }
        prefs.edit().putStringSet(KEY_HIDDEN_KINDS, next.map { it.name }.toSet()).apply()
        _state.update { it.copy(hiddenKinds = next) }
    }

    private fun ignoreTags(lists: List<OmniList>, kinds: List<CustomKind>) {
        Projects.ignoredTags = (lists.flatMap { l -> listOf(l.tag) + l.categories } + kinds.map { it.tag }).toSet()
    }

    /** Restarts the review clock for a task without a deadline. */
    fun markReviewed(task: Task) {
        val next = _state.value.reviewed + (task.title to LocalDate.now())
        // Old entries for tasks that no longer exist are dropped as the set is rewritten.
        val live = _state.value.tasks.map { it.title }.toSet()
        val kept = next.filterKeys { it in live || it == task.title }
        prefs.edit().putStringSet(KEY_REVIEWED, kept.map { (k, v) -> "$k|$v" }.toSet()).apply()
        _state.update { it.copy(reviewed = kept) }
    }

    /**
     * Writes why the task is still open, dated today, on a line under it (see [StuckLog]). It takes the write lock
     * first, so a review choice made with it is written after it and neither overwrites the other.
     */
    fun noteStuck(task: Task, reason: String) {
        if (reason.isBlank()) return
        val line = StuckLog.line(LocalDate.now(), reason)
        viewModelScope.launch {
            val result = writeLock.withLock { withContext(Dispatchers.IO) { runCatching { repo.addSubLine(task, line) } } }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")) } }
            if (result.isSuccess) reloadNote(task) else reload()
        }
    }

    // ---- Review mode (โหมดทบทวนงาน), on the secretary page ----

    /** How often the task was moved to a later day lately, for the review card's warning. */
    fun reviewMoves(task: Task): Int = Learned.movesLater(task.title, behaviorNow(), _state.value.today)

    /** What one saved review card changed, so "ย้อน" can put it back. */
    class ReviewSaved(val task: Task, val after: String, val before: List<String>, val ask: String?)

    /**
     * Writes one review card's answers into the task's note ([ReviewWrite]) and, when the owner was not sure when
     * the one waiting needs it, adds a task to ask them today. [onDone] gets what is needed to undo it, or null
     * when nothing was written (the message says why).
     */
    fun reviewSave(task: Task, answers: ReviewWrite.Answers, priority: Priority?, onDone: (ReviewSaved?) -> Unit) {
        // Done with work still open under it: the usual tick asks about the subtasks first, so the review does not close it.
        if (answers.progress == ReviewWrite.Progress.DONE && _state.value.subtasksOf(task).any { it.isOpen }) {
            _state.update { it.copy(message = tr("งานนี้ยังมีงานย่อยค้างอยู่ ปิดงานย่อยก่อน หรือเลือกสถานะอื่น", "This task still has open subtasks. Close them first, or pick another status.")) }
            onDone(null)
            return
        }
        val vault = _state.value.vault
        val today = LocalDate.now()
        viewModelScope.launch {
            var ask: String? = null
            var askFailed: Throwable? = null
            val result = writeLock.withLock {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val (before, after) = repo.editBlock(task) { lines, index -> ask = ReviewWrite.apply(lines, index, task, answers, priority, today) }
                        // The task's own lines are written by now: a failure here is told, and the card still moves on with its undo.
                        val added = ask?.let { line ->
                            runCatching { repo.appendLine(vault ?: throw java.io.IOException(tr("ยังไม่ได้เลือก vault", "No vault chosen")), VaultRepository.TASK_FILE, line) }
                                .onFailure { askFailed = it }.getOrNull()?.let { line }
                        }
                        ReviewSaved(task, after, before, added)
                    }
                }
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = saveError(e)) } }
            askFailed?.let { e -> _state.update { it.copy(message = tr("จดงานนี้แล้ว แต่เพิ่มงานถามไม่ได้: ${e.message}", "Saved, but the task to ask them was not added: ${e.message}")) } }
            result.getOrNull()?.let { saved ->
                answers.scheduled?.let { d -> learn(Behavior.moved(task, DateField.SCHEDULED.name, d, LocalDateTime.now(), BehaviorLog.SRC)) }
                answers.due?.let { d -> learn(Behavior.moved(task, DateField.DUE.name, d, LocalDateTime.now(), BehaviorLog.SRC)) }
                if (answers.progress == ReviewWrite.Progress.DONE) { logDone(task); offerFinish(task, saved.after) }
                answers.folder?.let(Folders::clean)?.takeIf { it.isNotEmpty() && !Folders.isLabel(it) }?.let { to -> newProjectRoot(to) }
                saved.ask?.let(::noteCreated)
            }
            onDone(result.getOrNull())
            reload()
        }
    }

    /** Puts back what [saved] changed: the task's block as it was, and the asking task it added. */
    fun reviewUndo(saved: ReviewSaved, onDone: (Boolean) -> Unit) {
        val vault = _state.value.vault
        val now = TaskLine.parse(saved.after)?.copy(
            filePath = saved.task.filePath, fileUri = saved.task.fileUri, lineIndex = saved.task.lineIndex,
            parent = saved.task.parent, list = saved.task.list,
        )
        viewModelScope.launch {
            var askLeft = false
            val result = writeLock.withLock {
                withContext(Dispatchers.IO) {
                    runCatching {
                        repo.restoreBlock(now ?: throw VaultRepository.ConflictException(), saved.before)
                        val ask = saved.ask
                        if (ask != null) {
                            var found = false
                            if (vault != null) repo.editPath(vault, VaultRepository.TASK_FILE) { text ->
                                text?.let { t ->
                                    val sep = if ("\r\n" in t) "\r\n" else "\n"
                                    val lines = t.split(sep).toMutableList()
                                    val i = lines.lastIndexOf(ask)
                                    if (i < 0) null else { found = true; lines.removeAt(i); lines.joinToString(sep) }
                                }
                            }
                            askLeft = !found
                        }
                    }
                }
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = saveError(e)) } }
            // Not silent: the task's lines are back, but the added ask task was changed or is gone, so it is left for the owner.
            if (result.isSuccess && askLeft) _state.update {
                it.copy(message = tr("ย้อนงานนี้แล้ว แต่หางานถามที่เพิ่มไว้ไม่เจอ (อาจถูกแก้แล้ว) ลบเองได้ในโน้ตงาน", "Undone, but the added ask task was not found (it may have changed). Remove it in the task note if needed."))
            }
            onDone(result.isSuccess)
            reload()
        }
    }

    private fun saveError(e: Throwable) = if (e is VaultRepository.ConflictException) {
        tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere and was reloaded. Try again.")
    } else {
        tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")
    }

    /** A top folder no tag has used yet becomes a project, as [moveToFolder] does. */
    private fun newProjectRoot(folder: String) {
        val root = folder.substringBefore('/')
        val s = _state.value
        val known = s.projectTags.orEmpty().any { it.equals(root, ignoreCase = true) } ||
            (s.tasks + s.parked).any { t -> t.tags.any { it.substringBefore('/').equals(root, ignoreCase = true) } }
        if (!known) s.projectTags?.let { saveProjectTags(it + root) }
    }

    /** Links a note on its own line under the task, the same way Obsidian writes `[[links]]`. */
    fun addLink(task: Task, notePath: String) {
        val name = NoteLinks.linkText(notePath, _state.value.notePaths)
        if (name in task.links) return
        sub(task) { repo.addSubLine(task, "[[$name]]") }
    }

    fun removeLink(task: Task, name: String) = sub(task) { repo.removeSubLine(task, "[[$name") }

    /** Deletes the task's line from its file, with its description and subtasks; the snackbar offers undo. */
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.deleteTask(task) } }
            result.fold(
                { cut -> _state.update { it.copy(deleted = Deleted(task.title, cut)) } },
                { e ->
                    val text = if (e is VaultRepository.ConflictException) {
                        tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere and was reloaded. Try again.")
                    } else {
                        tr("ลบไม่ได้: ${e.message}", "Cannot delete: ${e.message}")
                    }
                    _state.update { it.copy(message = text) }
                },
            )
            reload()
        }
    }

    fun clearDeleted() = _state.update { it.copy(deleted = null) }

    fun undoDelete(d: Deleted) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.restore(d.cut) } }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("เอากลับไม่ได้: ${e.message}", "Cannot undo: ${e.message}")) } }
            reload()
        }
    }

    private fun sub(task: Task, write: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(write) }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")) } }
            if (result.isSuccess) reloadNote(task) else reload()
        }
    }

    private var settleJob: Job? = null

    /**
     * After a write to the one note [task] sits in: reads that note back and swaps its tasks into the state, instead of
     * walking and parsing every note in the vault (and asking the calendar, the profile and Drive again), which made
     * each edit wait seconds. The reminders and the widgets catch up a moment later, in the background.
     */
    private suspend fun reloadNote(task: Task) {
        val fresh = withContext(Dispatchers.IO) { runCatching { repo.loadNote(task) }.getOrNull() }
        if (fresh == null) {
            reload()
            return
        }
        _state.update { s ->
            val here = fresh.filter { it.list == null }
            val tasks = swapNote(s.tasks, task.fileUri, here.filterNot { Branches.isParked(it, s.branchStates) })
            val parked = swapNote(s.parked, task.fileUri, here.filter { Branches.isParked(it, s.branchStates) })
            val items = swapNote(s.listItems, task.fileUri, fresh.filter { it.list != null })
            s.copy(tasks = tasks, parked = parked, listItems = items, insight = Insight.next(tasks + parked + items, doneLog(), s.profile, LocalDate.now(), declined(), behaviorNow()))
        }
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            delay(800)
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                val s = _state.value
                runCatching { Scheduler.reschedule(app, (s.tasks + s.listItems).filterNot { Branches.isParked(it, s.branchStates) }) }
            }
            runCatching { OmniWidgets.refresh(app) }
        }
    }

    /** [old] with the tasks of the note [fileUri] replaced by [fresh], in the place the note's tasks had (file order). */
    private fun swapNote(old: List<Task>, fileUri: String, fresh: List<Task>): List<Task> {
        val at = old.indexOfFirst { it.fileUri == fileUri }
        val rest = old.filterNot { it.fileUri == fileUri }
        return if (at < 0) rest + fresh else rest.subList(0, at) + fresh + rest.subList(at, rest.size)
    }

    fun addTag(task: Task, tag: String) = edit(task) { TaskLine.addTag(it, tag) }

    fun removeTag(task: Task, tag: String) = edit(task) { TaskLine.removeTag(it, tag) }

    /** Project Ygdrasil: the task moves to another project or branch; its project tag becomes [tag] where it stands. */
    fun moveTask(task: Task, tag: String) {
        val from = Projects.tagOf(task)
        if (from == tag) return
        editRecorded(task, UndoStep.Kind.MOVED, inTree = true) { TaskLine.retag(it, from, tag) }
    }

    /**
     * Project Ygdrasil: [task] carried to [to] moves for real and shows in one place only (owner's pick, 2026-10-27):
     * see [TreeMove]. A task written in a list note leaves that note: to another list it goes into that list's note,
     * anywhere else into the task note. Every move is a step in Ygdrasil's undo history.
     */
    fun moveInTree(task: Task, to: TreeMove.To) {
        val s = _state.value
        val lists = s.lists.map { it.tag }
        val project = Projects.tagOf(task)
        val transform = { raw: String -> TreeMove.line(raw, project, TreeMove.listTagsOn(task, lists), to) }
        val home = task.list?.let { name -> s.lists.firstOrNull { it.name == name } }
        if (home == null) {
            if (transform(task.raw) == task.raw) return
            editRecorded(task, UndoStep.Kind.MOVED, inTree = true) { transform(it) }
            return
        }
        val target = if (to is TreeMove.To.InList) s.lists.firstOrNull { it.tag.equals(to.tag, ignoreCase = true) } else null
        if (target == home) return
        val toPath = target?.path ?: VaultRepository.TASK_FILE
        val vault = s.vault ?: return
        viewModelScope.launch {
            val result = writeLock.withLock { withContext(Dispatchers.IO) { runCatching { repo.moveToNote(vault, task, toPath, transform) } } }
            result.onSuccess { m -> remember(UndoStep(UndoStep.Kind.MOVED, task.title, moved = m), inTree = true) }
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

    /**
     * The folder picker (owner's pick, 2026-10-26): the task's one folder becomes [folder] where its old one stood, or
     * goes in front when it had none; null takes it out of its folder. Labels stay. A brand-new top folder becomes a
     * project, so the task shows on the desktop and in Ygdrasil at once.
     */
    fun moveToFolder(task: Task, folder: String?) {
        val from = Folders.of(task)
        val to = folder?.let(Folders::clean)?.takeIf { it.isNotEmpty() }
        when {
            // A label typed into the picker (#รอ/พี่เอ) is added; the folder stays.
            to != null && Folders.isLabel(to) -> addTag(task, to)
            to == null -> if (from != null) removeTag(task, from)
            from == to -> return
            else -> {
                // Only a top folder no tag has used yet becomes a project; a tag already in use (#siriraj) stays as it is.
                val root = to.substringBefore('/')
                val s = _state.value
                val known = s.projectTags.orEmpty().any { it.equals(root, ignoreCase = true) } ||
                    (s.tasks + s.parked).any { t -> t.tags.any { it.substringBefore('/').equals(root, ignoreCase = true) } }
                if (from == null) edit(task) { TaskLine.homeTag(it, to) } else edit(task) { TaskLine.retag(it, from, to) }
                if (!known) s.projectTags?.let { saveProjectTags(it + root) }
            }
        }
    }

    /**
     * A task with no project gets one (from the tray under Ygdrasil): [tag] goes in front of its tags, which stay. A
     * new project's name is made a project, so the task shows on the tree at once.
     */
    fun homeTask(task: Task, tag: String) {
        editRecorded(task, UndoStep.Kind.MOVED, inTree = true) { TaskLine.homeTag(it, tag) }
        val root = tag.substringBefore('/')
        _state.value.projectTags?.let { if (root !in it) saveProjectTags(it + root) }
    }

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
     * Desktop pins are the stars (owner's pick, 2026-10-26). Before any star every project sits on the desktop, so the
     * first unpin stars all the others; unpinning the last one keeps [NO_PIN], so the desktop does not fill up again.
     */
    fun setPinned(project: String, on: Boolean, all: List<String>) {
        val s = _state.value.starred
        val next = when {
            on -> s - NO_PIN + project
            s.isEmpty() -> all.toSet() - project
            else -> s - project
        }.ifEmpty { setOf(NO_PIN) }
        prefs.edit().putStringSet(KEY_STARRED, next).apply()
        _state.update { it.copy(starred = next) }
    }

    /** Makes [tag] a project, or (off) a plain tag again; the task lines stay as they are. */
    fun setProject(tag: String, on: Boolean) {
        val s = _state.value
        val now = s.projectTags ?: Projects.seed(s.tasks + s.parked)
        // Taking a project off is remembered, so a new task with its tag does not bring it back.
        val root = tag.substringBefore('/')
        val declined = prefs.getStringSet(KEY_OFF_PROJECTS, null).orEmpty()
        prefs.edit().putStringSet(KEY_OFF_PROJECTS, if (on) declined - root else declined + root).apply()
        saveProjectTags(if (on) now + tag else now - tag)
    }

    /**
     * A project the owner confirmed through the assistant becomes one of the chosen projects when it is not yet, so the
     * task shows in Ygdrasil and on the Projects page instead of under a plain tag.
     */
    private fun keepProject(tag: String?) {
        val root = tag?.removePrefix("#")?.substringBefore('/')?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val now = _state.value.projectTags ?: return
        if (root !in now) saveProjectTags(now + root)
    }

    private fun saveProjectTags(tags: Set<String>) {
        prefs.edit().putStringSet(KEY_PROJECT_TAGS, tags).apply()
        Projects.chosen = tags
        _state.update { it.copy(projectTags = tags) }
    }

    /**
     * A project's open tasks in the owner's order: tasks never placed come after, by date. The first is
     * the one to do next.
     */
    fun orderedProjectTasks(project: String, tasks: List<Task>): List<Task> {
        val rank = _state.value.projectTaskOrder[project].orEmpty().withIndex().associate { it.value to it.index }
        return tasks.sortedWith(compareBy<Task>({ rank[it.title] ?: Int.MAX_VALUE }, { it.due ?: it.scheduled ?: LocalDate.LATEST }))
    }

    fun setProjectTaskOrder(project: String, ordered: List<Task>) {
        val next = _state.value.projectTaskOrder + (project to ordered.map { it.title })
        prefs.edit().putStringSet(KEY_PROJECT_TASKS, next.map { (k, v) -> k + "\t" + v.joinToString("\u001F") }.toSet()).apply()
        _state.update { it.copy(projectTaskOrder = next) }
        if (project in _state.value.strictProjects) writeChain(ordered, on = true)
    }

    fun setTreeReach(reach: Ygdrasil.Reach) {
        prefs.edit().putString(KEY_TREE_REACH, reach.name).apply()
        _state.update { it.copy(treeReach = reach) }
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
        val day = LocalDate.now()
        open.forEach { child -> showEdited(child, TaskLine.setDone(child.raw, true, day)) }
        if (parent.recurrence == null) showEdited(parent, TaskLine.setDone(parent.raw, true, day))
        val inTree = closingInTree
        viewModelScope.launch {
            val blocks = ArrayList<VaultRepository.BlockChange>()
            val result = writeLock.withLock { withContext(Dispatchers.IO) {
                runCatching {
                    val today = LocalDate.now()
                    open.forEach { child -> blocks += repo.rewriteLineRecorded(child) { TaskLine.setDone(it, true, today) } }
                    if (parent.recurrence != null) repo.completeRecurring(parent, today) { blocks += it }
                    else blocks += repo.rewriteLineRecorded(parent) { TaskLine.setDone(it, true, today) }
                }
            } }
            val event = logDone(parent)
            // Every line ticked here goes back with one undo, even when a later write failed.
            if (blocks.isNotEmpty()) remember(
                UndoStep(UndoStep.Kind.DONE, parent.title, blocks, listOfNotNull(event), if (parent.recurrence != null) listOf(parent.title to LocalDate.now()) else emptyList()),
                inTree,
            )
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกไม่ได้: ", "Could not save: ") + e.message) } }
            if (result.isSuccess) offerFinish(parent, TaskLine.setDone(parent.raw, true, LocalDate.now()), open.map { it.key }.toSet())
            if (result.isSuccess) reloadNote(parent) else reload()
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
        val name = NoteLinks.nameFor(task.title)
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

    /**
     * Deletes a list note with its items. Tasks pulled in from other notes stay where they are and only lose
     * the list's tag, so they do not turn into a project of their own.
     */
    fun deleteList(list: OmniList) {
        val vault = _state.value.vault ?: return
        val pulled = _state.value.itemsOf(list).filter { it.list == null }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    pulled.forEach { t -> runCatching { repo.rewriteLine(t) { raw -> TaskLine.removeTag(raw, list.tag) } } }
                    repo.deletePath(vault, list.path)
                }
            }
            _state.update { it.copy(message = result.exceptionOrNull()?.let { e -> tr("ลบไม่ได้: ", "Cannot delete: ") + e.message } ?: tr("ลบ ${list.name} แล้ว", "Deleted ${list.name}")) }
            reload()
        }
    }

    fun addListItem(list: OmniList, title: String, category: String?, link: String? = null) {
        val vault = _state.value.vault ?: return
        if (title.isBlank()) return
        var line = TaskLine.addTag("- [ ] ${title.trim()}", list.tag)
        if (category != null) line = TaskLine.addTag(line, category)
        line = TaskLine.setDate(line, DateField.CREATED, LocalDate.now())
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.appendLine(vault, list.path, line + (link?.let { "\n    - $it" } ?: "")) } }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("เพิ่มไม่ได้: ", "Cannot add: ") + e.message) } }
            reload()
        }
    }

    /** Puts tasks from the vault in a list by tagging them where they are; they stay in their own file. */
    fun includeInList(list: OmniList, picked: List<Task>, category: String?) {
        if (picked.isEmpty()) return
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) {
                picked.count { t ->
                    runCatching { repo.rewriteLine(t) { raw -> TaskLine.addTag(raw, list.tag).let { if (category != null) TaskLine.addTag(it, category) else it } } }.isSuccess
                }
            }
            _state.update {
                it.copy(
                    message = if (done == picked.size) tr("เพิ่ม $done งานเข้า ${list.name} แล้ว", "Added $done tasks to ${list.name}")
                    else tr("เพิ่มได้ $done จาก ${picked.size} งาน ไฟล์อาจถูกแก้จากที่อื่น", "Added $done of ${picked.size}; a file may have changed"),
                )
            }
            reload()
        }
    }

    fun requestQuickAdd(request: QuickAddRequest?) = _state.update { it.copy(quickAdd = request) }

    fun requestStuck(on: Boolean) = _state.update { it.copy(askStuck = on) }

    fun requestMonth(request: MonthRequest?) = _state.update { it.copy(month = request) }

    fun requestCalendar(day: LocalDate?, reveal: Boolean = false) = _state.update { it.copy(calendarDay = day, calendarReveal = reveal) }

    fun setTasksLayout(mode: Mode?) = _state.update { it.copy(tasksLayout = mode) }

    fun setCalendarSpan(span: CalendarSpan) = _state.update { it.copy(calendarSpan = span) }

    /** Adds a task typed in the quick-add sheet to the TaskForge file. */
    fun quickAdd(draft: QuickAdd.Draft, status: Status = Status.TODO, link: String? = null) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                val line = draft.line(today).let { if (status == Status.TODO) it else TaskLine.setStatus(it, status, today) } +
                    (link?.let { "\n    - $it" } ?: "")
                runCatching { repo.appendLine(vault, VaultRepository.TASK_FILE, line) }.map { line }
            }
            result.getOrNull()?.let { noteCreated(it) }
            val text = result.fold(
                { tr("เพิ่มงานแล้ว: ${draft.title}", "Added: ${draft.title}") },
                { e -> tr("เพิ่มงานไม่ได้: ${e.message}", "Cannot add task: ${e.message}") },
            )
            _state.update { it.copy(message = text) }
            reload()
        }
    }

    /** A task from the detailed add page: the line, its description and its subtasks go in together. */
    fun addDetailedTask(detail: QuickAdd.Detail, status: Status = Status.TODO, link: String? = null) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                val lines = detail.block(today).split("\n").toMutableList()
                if (status != Status.TODO) lines[0] = TaskLine.setStatus(lines[0], status, today)
                if (link != null) lines.add(1, "    - $link")
                runCatching { repo.appendLine(vault, VaultRepository.TASK_FILE, lines.joinToString("\n")) }.map { lines[0] }
            }
            result.getOrNull()?.let { noteCreated(it) }
            val text = result.fold(
                { tr("เพิ่มงานแล้ว: ${detail.title}", "Added: ${detail.title}") },
                { e -> tr("เพิ่มงานไม่ได้: ${e.message}", "Cannot add task: ${e.message}") },
            )
            _state.update { it.copy(message = text) }
            reload()
        }
    }

    /** An event from the detailed add page: its own start and end, place, notes, repeat and alerts. */
    fun addDetailedEvent(
        calendarId: Long, title: String, begin: LocalDateTime, end: LocalDateTime, allDay: Boolean,
        place: String, notes: String, rrule: String?, reminders: List<Int>,
    ) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val id = CalendarReader.insertDetailed(app, calendarId, title, begin, end, allDay, place, notes, rrule, reminders)
                if (id != null) {
                    CalendarReader.requestSync(app, force = true)
                    runCatching { OmniWidgets.refresh(app) }
                }
                id
            }
            _state.update { it.copy(message = if (id != null) tr("ลงปฏิทินแล้ว: $title", "Added to calendar: $title") else tr("ลงปฏิทินไม่ได้", "Cannot add the event")) }
        }
    }

    // ---- Task and event, back and forth ----

    /** What tasks and events left behind when they turned into each other ([Swap]). */
    suspend fun readSwap(): Swap.Store {
        val vault = _state.value.vault ?: return Swap.Store()
        return withContext(Dispatchers.IO) { runCatching { Swap.read(repo.readPath(vault, Swap.PATH)) }.getOrDefault(Swap.Store()) }
    }

    private fun editSwap(vault: Uri, change: (Swap.Store) -> Swap.Store) {
        val before = Swap.read(repo.readPath(vault, Swap.PATH))
        val after = change(before)
        if (after != before) repo.writePath(vault, Swap.PATH, Swap.write(after))
    }

    private suspend fun calendarTouched(app: Application) {
        CalendarReader.requestSync(app, force = true)
        runCatching { OmniWidgets.refresh(app) }
    }

    /**
     * Turns [task] into an event from [ev]: the event goes in first, then the task's block comes out of its note and
     * waits in the swap file under the event's id, so turning the event back gives the same task again.
     */
    fun taskToEvent(task: Task, calendarId: Long, ev: QuickEvent, place: String, reminders: List<Int>) {
        val vault = _state.value.vault ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                val id = CalendarReader.insertDetailed(
                    app, calendarId, task.title, ev.start, ev.end, ev.allDay, place, Swap.eventNotes(task), null,
                    if (ev.allDay) emptyList() else reminders,
                ) ?: return@withContext tr("ลงปฏิทินไม่ได้", "Cannot add the event")
                val cut = runCatching { repo.deleteTask(task) }.getOrElse { e ->
                    CalendarReader.delete(app, id)
                    return@withContext if (e is VaultRepository.ConflictException) tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere and was reloaded. Try again.")
                    else tr("เปลี่ยนเป็นนัดไม่ได้: ${e.message}", "Cannot turn it into an event: ${e.message}")
                }
                val kept = Swap.KeptTask(cut.lines, task.filePath, task.title, ev.start.toString())
                val saved = runCatching { editSwap(vault) { it.copy(events = it.events + (id.toString() to kept)) } }.isSuccess
                calendarTouched(app)
                _state.update {
                    it.copy(swapped = SwapUndo(task.title, toEvent = true) {
                        swapBack {
                            CalendarReader.delete(app, id)
                            repo.restore(cut)
                            runCatching { editSwap(vault) { s -> s.copy(events = s.events - id.toString()) } }
                        }
                    })
                }
                if (saved) null else tr("เป็นนัดแล้ว แต่เก็บค่าเดิมของงานไว้ไม่ได้", "Now an event, but the task's details could not be kept")
            }
            text?.let { t -> _state.update { it.copy(message = t) } }
            reload()
        }
    }

    /**
     * Turns event [d] into a task on [day] at [time]: the task it came from when there is one (its kept block, at the
     * end of the note it was in), else a new task in the task note. The event's own details wait under the task's 🆔,
     * then the event is deleted.
     */
    fun eventToTask(d: CalendarReader.EventDetails, scheduled: Boolean, day: LocalDate, time: kotlinx.datetime.LocalTime?) {
        val vault = _state.value.vault ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                val store = runCatching { Swap.read(repo.readPath(vault, Swap.PATH)) }.getOrDefault(Swap.Store())
                val found = Swap.keptTask(store, d.id, d.title, d.begin)
                val (lines, taskId) = found?.let { Swap.restoredLines(it.second, day, time) }
                    ?: Swap.newId().let { id -> Swap.newTaskLines(d.title, day, time, scheduled, d.notes, LocalDate.now(), id) to id }
                val block = lines.joinToString("\n")
                val file = found?.second?.file?.takeIf { it.isNotBlank() } ?: VaultRepository.TASK_FILE
                runCatching { repo.appendLine(vault, file, block) }
                    .recoverCatching { e -> if (file != VaultRepository.TASK_FILE) repo.appendLine(vault, VaultRepository.TASK_FILE, block) else throw e }
                    .onFailure { e -> return@withContext tr("เปลี่ยนเป็นงานไม่ได้: ${e.message}", "Cannot turn it into a task: ${e.message}") }
                val keptEvent = Swap.KeptEvent(
                    time = if (d.allDay) null else d.begin.time.toString(),
                    minutes = if (d.allDay) 60 else maxOf(1L, ChronoUnit.MINUTES.between(d.begin, d.end)),
                    allDay = d.allDay,
                    days = if (d.allDay) maxOf(0L, ChronoUnit.DAYS.between(d.begin.date, d.end.date)) else 0,
                    place = d.place, reminders = d.reminders, calendarId = d.calendarId,
                )
                val saved = runCatching {
                    editSwap(vault) { s -> s.copy(events = found?.let { s.events - it.first } ?: s.events, tasks = s.tasks + (taskId to keptEvent)) }
                }.isSuccess
                val gone = CalendarReader.delete(app, d.id)
                if (gone) calendarTouched(app)
                _state.update {
                    it.copy(swapped = SwapUndo(d.title, toEvent = false) {
                        swapBack {
                            val back = _state.value.tasks.firstOrNull { t -> t.id == taskId && t.parent == null && !t.todayCopy }
                            if (back != null) repo.deleteTask(back)
                            if (gone) {
                                val id = CalendarReader.insertDetailed(app, d.calendarId, d.title, d.begin, d.end, d.allDay, d.place, d.notes, null, d.reminders)
                                if (id != null && found != null) runCatching { editSwap(vault) { s -> s.copy(events = s.events + (id.toString() to found.second)) } }
                                calendarTouched(app)
                            }
                        }
                    })
                }
                when {
                    !gone -> tr("เป็นงานแล้ว แต่ลบนัดเดิมไม่ได้ ลบเองในปฏิทินได้", "Now a task, but the event could not be deleted; delete it in the calendar")
                    !saved -> tr("เป็นงานแล้ว แต่เก็บค่าเดิมของนัดไว้ไม่ได้", "Now a task, but the event's details could not be kept")
                    else -> null
                }
            }
            text?.let { t -> _state.update { it.copy(message = t) } }
            reload()
        }
    }

    private fun swapBack(work: suspend () -> Unit) {
        viewModelScope.launch {
            val failed = withContext(Dispatchers.IO) { runCatching { work() }.exceptionOrNull() }
            failed?.let { e -> _state.update { it.copy(message = tr("เลิกทำไม่ได้: ${e.message}", "Cannot undo: ${e.message}")) } }
            reload()
        }
    }

    fun clearSwapped() = _state.update { it.copy(swapped = null) }

    // ---- Assistant ----

    /** Reads what the owner asked for ([Planner.askOf]): a look-ahead, a plan for a range, a ranking, or time for something new. */
    fun ask(text: String) {
        val today = LocalDate.now()
        val range = Planner.rangeOf(text, today)
        when (Planner.askOf(text)) {
            Planner.Ask.PLAN -> planRange(range ?: (today to today.plusDays(6)), text)
            Planner.Ask.AGENDA -> showAgenda(range ?: (today to today.plusDays(6)), text)
            Planner.Ask.RANK -> rankAll(text)
            Planner.Ask.SLOTS -> {
                val s = _state.value
                val plan = Planner.plan(text, s.tasks, s.events, s.profile, LocalDateTime.now())
                _state.update { it.copy(chat = it.chat + Chat.Asked(text) + Chat.Slots(plan)) }
            }
            Planner.Ask.DURATION -> _state.update { it.copy(chat = it.chat + Chat.Asked(text) + Chat.Duration(text)) }
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
                t.scheduled.let { it == null || it < today } && t.due.let { it == null || it >= from } && t.reminderTime == null
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
    fun confirmCustom(index: Int, title: String, day: LocalDate, time: kotlinx.datetime.LocalTime?, minutes: Int) {
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
        val m = Digest.weekly(s.tasks, s.today, behaviorNow())
        _state.update { it.copy(chat = it.chat + Chat.Asked(tr("ทบทวนสัปดาห์นี้", "Review this week")) + Chat.Review(m.title, m.lines)) }
    }

    fun clearChat() = _state.update { it.copy(chat = emptyList(), ai = it.ai.copy(thinking = false)) }

    // ---- Gemini ----

    private val gemini by lazy { Gemini(getApplication()) }

    private fun readAiKey() {
        val app = getApplication<Application>()
        val key = AiKey.get(app)
        _state.update { it.copy(ai = it.ai.copy(hasKey = key != null, keyTail = key?.let(AiKey::tail), autoBrief = AiKey.autoBrief(app), model = AiKey.model(app), secretaryHiddenUntil = AiKey.secretaryHiddenUntil(app))) }
    }

    /** Reads the models Google offers this key, for the picker. */
    fun loadAiModels() {
        if (!_state.value.ai.hasKey || _state.value.ai.modelsLoading) return
        _state.update { it.copy(ai = it.ai.copy(modelsLoading = true, modelsError = null)) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { gemini.models() } }
            _state.update {
                it.copy(ai = it.ai.copy(modelsLoading = false, models = r.getOrDefault(it.ai.models), modelsError = r.exceptionOrNull()?.message))
            }
        }
    }

    /** Uses [model] from now on, here and in later questions; the brief is read again with it next time. */
    fun setAiModel(model: String) {
        AiKey.setModel(getApplication(), model)
        readAiKey()
    }

    /** Saves the owner's key (null removes it), forgets the brief made with the old one, and tries the new one. */
    fun setAiKey(key: String?) {
        AiKey.set(getApplication(), key?.trim()?.takeIf { it.isNotEmpty() })
        _state.update { it.copy(ai = it.ai.copy(brief = null, briefAt = 0L, briefError = null, test = null)) }
        readAiKey()
        if (_state.value.ai.hasKey) testAiKey()
    }

    fun setAutoBrief(on: Boolean) {
        AiKey.setAutoBrief(getApplication(), on)
        readAiKey()
    }

    fun testAiKey() {
        val key = AiKey.get(getApplication()) ?: return
        if (_state.value.ai.testing) return
        _state.update { it.copy(ai = it.ai.copy(testing = true, test = null)) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { gemini.test(key) } }
            val test = r.fold({ true to tr("ใช้ได้ ($it)", "Works ($it)") }, { false to (it.message ?: tr("ใช้ไม่ได้", "Does not work")) })
            _state.update { it.copy(ai = it.ai.copy(testing = false, test = test)) }
        }
    }

    /**
     * The open work as Gemini sees it, from what is loaded now, with what Claude wrote in Claude log.md (read fresh
     * each time, off the main thread, so a log Obsidian Sync just brought in is used).
     */
    /** The picture, and the assistant's lasting memory and conversation log, read fresh from the vault. */
    private class AiContext(val picture: AiAssist.Picture, val memory: String?, val entries: List<AiMemory.Entry>, val index: String?)

    private suspend fun aiContext(): AiContext {
        val s = _state.value
        fun read(path: String): String? = s.vault?.let { v -> runCatching { repo.readPath(v, path) }.getOrNull() }
        val (claude, memory, entries) = withContext(Dispatchers.IO) {
            Triple(read(CLAUDE_LOG), read(AiMemory.MEMORY_FILE), s.vault?.let { v -> runCatching { library.recent(v, LocalDate.now()) }.getOrNull() }.orEmpty())
        }
        val index = withContext(Dispatchers.IO) { s.vault?.let { v -> library.index(v) } }
        _state.update { it.copy(ai = it.ai.copy(claudeLog = claude != null, secretaryLast = Secretary.lastRun(claude) ?: it.ai.secretaryLast)) }
        val profile = if (s.profile.exists) s.profile.render().lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ") } else emptyList()
        return AiContext(AiAssist.picture(s.tasks, { s.subtasksOf(it) }, s.today, s.events, profile, claude, calendarOf = { CalendarStyles.label(it.calendarId) }), memory, entries, index)
    }

    private val logLock = kotlinx.coroutines.sync.Mutex()
    private val library by lazy { AiLibrary(getApplication(), repo, gemini) }

    /** Hides the "secretary stopped" warning for a day. */
    fun hideSecretaryWarning() {
        val until = System.currentTimeMillis() + Secretary.STALE_MS
        AiKey.setSecretaryHiddenUntil(getApplication(), until)
        _state.update { it.copy(ai = it.ai.copy(secretaryHiddenUntil = until)) }
    }
    private val upkeeping = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Keeps the assistant's log in order ([AiLibrary.upkeep]), in the background, one run at a time. */
    fun aiUpkeep(force: Boolean = false) {
        val vault = _state.value.vault ?: return
        if (!upkeeping.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                logLock.withLock { runCatching { library.upkeep(vault, LocalDate.now(), force) } }
            } finally {
                upkeeping.set(false)
            }
        }
    }

    /**
     * Changes the conversation log with [change], off the main thread and one write at a time (an answer and the
     * change confirmed right after it would otherwise read the same old file). A failed write only loses the line.
     */
    private fun editLog(day: LocalDate, change: (String?) -> String) {
        val vault = _state.value.vault ?: return
        viewModelScope.launch(Dispatchers.IO) { logLock.withLock { runCatching { repo.editPath(vault, AiMemory.monthFile(day), change) } } }
    }

    /** A line under the newest exchange of today: what was done or declined after an answer. */
    private fun logAi(line: String) {
        val today = LocalDate.now()
        editLog(today) { old -> AiMemory.addNote(old, today, line) }
    }

    private fun nowHm(): String = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

    /**
     * The brief at the top of the assistant page, worked out by rules (no AI): the day in a line and the work to do
     * first, with why. Cheap, so it is simply made again when asked.
     */
    fun aiBrief(@Suppress("UNUSED_PARAMETER") force: Boolean = false) {
        val s = _state.value
        if (s.tasks.isEmpty()) return
        val ctx = deskCtx()
        val summary = SecretaryDesk.opening(ctx).lines.joinToString(", ")
        val picks = app.omnitask.model.NextUp.ranked(s.tasks, s.today, ctx.skipped).take(4).map { AiAssist.Pick(it.task, it.reason) }
        _state.update { it.copy(ai = it.ai.copy(briefing = false, briefError = null, brief = AiAssist.Brief(summary, picks), briefAt = System.currentTimeMillis())) }
    }

    /**
     * A question typed to the assistant. Gemini no longer answers (the owner stopped it, 2026-10-24): the secretary's
     * rules and guide do in the new look, the assistant's rules otherwise; what they cannot answer goes to Claude.
     */
    fun askAi(text: String) {
        if (NewLook.on) deskType(text) else ask(text)
    }

    /** The task as it is now: by its place in the note, or by title when lines moved since Gemini answered. */
    private fun current(task: Task): Task? {
        val all = _state.value.allTasks
        return all.firstOrNull { it.key == task.key && it.title == task.title }
            ?: all.firstOrNull { it.title == task.title && it.filePath == task.filePath && it.isOpen }
    }

    /**
     * The owner confirmed ([accept]) or declined proposal [i] of the answer at [index]. A big change is not made here:
     * the screen hands it to Claude, and this only records that.
     */
    fun answerAiAction(index: Int, i: Int, accept: Boolean) {
        val item = _state.value.chat.getOrNull(index) as? Chat.AiAnswer ?: return
        if (i in item.outcome) return
        val action = item.actions.getOrNull(i) ?: return
        logAi("${nowHm()} " + (if (accept) "ทำแล้ว: " else "ไม่ทำ: ") + AiAssist.describe(action))
        if (accept && action is AiAssist.Action.Remember) {
            val vault = _state.value.vault
            val today = LocalDate.now()
            if (vault != null) viewModelScope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { repo.editPath(vault, AiMemory.MEMORY_FILE) { old -> AiMemory.remember(old, action.text, today) } } }
                r.exceptionOrNull()?.let { e -> showMessage(tr("จำไว้ไม่ได้: ${e.message}", "Could not remember it: ${e.message}")) }
            }
        } else if (accept && action is AiAssist.Action.Add) {
            keepProject(action.project)
            addDetailedTask(
                QuickAdd.Detail(
                    title = action.title, scheduled = action.scheduled, date = action.date, time = action.time,
                    tags = listOfNotNull(action.project), priority = action.priority,
                ),
            )
        } else if (accept && action !is AiAssist.Action.Big) {
            val task = action.task?.let(::current)
            if (task == null) {
                showMessage(tr("หางานนี้ไม่เจอแล้ว อาจถูกแก้หรือลบไป", "That task is gone; it may have been edited or deleted"))
                return
            }
            when (action) {
                is AiAssist.Action.Move -> setDate(
                    task,
                    when (action.kind) { AiAssist.DateKind.DUE -> DateField.DUE; AiAssist.DateKind.SCHEDULED -> DateField.SCHEDULED; AiAssist.DateKind.START -> DateField.START },
                    action.date,
                )
                is AiAssist.Action.Rename -> {
                    val next = TaskLine.setTitle(task.raw, action.title)
                    if (next == task.raw) { showMessage(tr("เปลี่ยนชื่อไม่ได้ ลองแก้เองในหน้างาน", "Could not rename it; edit it on the task page")); return }
                    edit(task) { TaskLine.setTitle(it, action.title) }
                }
                is AiAssist.Action.SetPriority -> setPriority(task, action.priority)
                is AiAssist.Action.Remind -> setReminder(task, action.time, if (task.due == null && task.scheduled != null) ReminderOn.SCHEDULED else ReminderOn.DUE)
                is AiAssist.Action.Done -> if (task.isOpen) toggleDone(task)
                is AiAssist.Action.Delete -> deleteTask(task)
                is AiAssist.Action.Project -> { keepProject(action.tag); moveTask(task, action.tag) }
                is AiAssist.Action.Stuck -> noteStuck(task, action.reason)
                is AiAssist.Action.Subtasks -> addSubtasks(task, action.titles)
                is AiAssist.Action.Add, is AiAssist.Action.Big, is AiAssist.Action.Remember -> Unit
            }
        }
        _state.update {
            it.copy(chat = it.chat.toMutableList().also { list -> list[index] = item.copy(outcome = item.outcome + (i to accept)) })
        }
    }

    /** Several subtasks under [parent], written one after another under the write lock. */
    private fun addSubtasks(parent: Task, titles: List<String>) {
        val today = LocalDate.now()
        viewModelScope.launch {
            val result = writeLock.withLock {
                withContext(Dispatchers.IO) { runCatching { titles.filter { it.isNotBlank() }.forEach { repo.addSubtask(parent, QuickAdd.parse(it, today).line(today)) } } }
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")) } }
            if (result.isSuccess) reloadNote(parent) else reload()
        }
    }

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

    /** Adds an event to calendar [calendarId]: all day when [start] is null, else [minutes] long from [start]. */
    /** The quick-add sheet's event: [start] to [end], or [start]'s day to [end]'s day when [allDay]. */
    fun addEvent(calendarId: Long, title: String, start: kotlinx.datetime.LocalDateTime, end: kotlinx.datetime.LocalDateTime, allDay: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val id = if (allDay) CalendarReader.insertAllDay(app, calendarId, title, start.date, end.date)
                else CalendarReader.insert(app, calendarId, title, start, end)
                if (id != null) {
                    // Up to Google at once, so the event shows on the owner's other devices too.
                    CalendarReader.requestSync(app, force = true)
                    runCatching { OmniWidgets.refresh(app) }
                }
                id
            }
            _state.update { it.copy(message = if (id != null) tr("ลงปฏิทินแล้ว: $title", "Added to calendar: $title") else tr("ลงปฏิทินไม่ได้", "Cannot add the event")) }
        }
    }

    fun answerInsight(yes: Boolean) {
        val ask = _state.value.insight ?: return
        if (yes) {
            val p = _state.value.profile
            saveProfile(ask.apply(p).copy(remembered = (p.remembered + ask.remember).distinct()))
        } else {
            prefs.edit().putStringSet(KEY_DECLINED, declined() + ask.id).apply()
            learn(Behavior.declined(ask.id, LocalDateTime.now(), BehaviorLog.SRC))
        }
        _state.update { it.copy(insight = null) }
    }

    /** Folds or unfolds a group in the task list; remembered, and synced like the other settings. */
    fun toggleGroup(key: String) {
        val next = _state.value.foldedGroups.let { if (key in it) it - key else it + key }
        prefs.edit().putStringSet(KEY_FOLDED_GROUPS, next).apply()
        _state.update { it.copy(foldedGroups = next) }
    }

    fun toggleColumn(status: Status) {
        val next = _state.value.foldedColumns.let { if (status.name in it) it - status.name else it + status.name }
        prefs.edit().putStringSet(KEY_FOLDED_COLUMNS, next).apply()
        _state.update { it.copy(foldedColumns = next) }
    }

    // ---- Branches and renaming projects ----

    private fun saveBranchStates(next: Map<Pair<String, String>, Branches.State>) {
        prefs.edit().putStringSet(KEY_BRANCHES, Branches.encode(next)).apply()
        _state.update { it.copy(branchStates = next) }
        // Parking hides or shows tasks everywhere, so the lists are split again.
        reload()
    }

    fun setBranchState(node: Branches.Node, state: Branches.State) =
        saveBranchStates(_state.value.branchStates + ((node.project to node.path) to state))

    /** Picks one branch among its siblings; the others still being tried are parked. */
    fun chooseBranch(node: Branches.Node, siblings: List<Branches.Node>) =
        saveBranchStates(Branches.choose(node, siblings, _state.value.branchStates))

    /**
     * Starts a branch under [parent] with its first task: the task gets the nested tag, e.g.
     * `#peddose/แอป/มือถือ`. Read like quick add, so dates and times work in [firstTask].
     */
    fun addBranch(parent: Branches.Node, name: String, firstTask: String) {
        val vault = _state.value.vault ?: return
        val clean = name.trim().replace(Regex("""[\s#]+"""), "-").trim('-', '/')
        if (clean.isEmpty() || firstTask.isBlank()) return
        val today = LocalDate.now()
        val line = TaskLine.addTag(QuickAdd.parse(firstTask, today).line(today), parent.tag + "/" + clean)
        val path = if (parent.path.isEmpty()) clean else parent.path + "/" + clean
        val next = _state.value.branchStates + ((parent.project to path) to Branches.State.TRYING)
        prefs.edit().putStringSet(KEY_BRANCHES, Branches.encode(next)).apply()
        _state.update { it.copy(branchStates = next) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.appendLine(vault, VaultRepository.TASK_FILE, line) } }
            if (result.isSuccess) noteCreated(line)
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("สร้าง branch ไม่ได้: ", "Cannot add the branch: ") + e.message) } }
            reload()
        }
    }

    /** A branch made in the mind map, before it has tasks: under the project it is worked on, deeper it is tried. */
    fun addEmptyBranch(parent: Branches.Node, name: String) {
        val clean = Branches.clean(name)
        if (clean.isEmpty()) return
        val path = if (parent.path.isEmpty()) clean else parent.path + "/" + clean
        if (parent.children.any { it.path == path }) return
        saveBranchStates(_state.value.branchStates + ((parent.project to path) to if (parent.path.isEmpty()) Branches.State.ACTIVE else Branches.State.TRYING))
    }

    /** A task in a branch: the title is read like quick add and gets the branch's tag. */
    fun addBranchTask(node: Branches.Node, title: String) {
        val vault = _state.value.vault ?: return
        if (title.isBlank()) return
        val today = LocalDate.now()
        val line = TaskLine.addTag(QuickAdd.parse(title, today).line(today), node.tag)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.appendLine(vault, VaultRepository.TASK_FILE, line) } }
            if (result.isSuccess) noteCreated(line)
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("เพิ่มงานไม่ได้: ", "Cannot add the task: ") + e.message) } }
            reload()
        }
    }

    /**
     * Reads `ชื่อโปรเจกต์.md` ([ProjectNames]) for the task lines read next, and keeps the chosen projects under the
     * names they have now (a project the owner merged or moved is not left behind as an empty one).
     */
    private suspend fun loadProjectNames(vault: android.net.Uri) {
        val text = withContext(Dispatchers.IO) { runCatching { repo.readPath(vault, ProjectNames.PATH) }.getOrNull() }
        ProjectNames.current = ProjectNames.parse(text)
        _state.value.projectTags?.let { tags ->
            val now = tags.map { ProjectNames.canonical(it).substringBefore('/') }.toSet()
            if (now != tags) saveProjectTags(now)
        }
    }

    // ---- Undo and redo of ticks and Ygdrasil moves (owner, 2026-10-27) ----

    /**
     * A change that can be taken back and done again: the note blocks it changed (put back in reverse order), the
     * "done" events and repeat days it added, or a project or branch moved in every note ([retag] from, to).
     */
    class UndoStep(
        val kind: Kind,
        val title: String,
        val blocks: List<VaultRepository.BlockChange> = emptyList(),
        val events: List<BehaviorEvent> = emptyList(),
        val recurred: List<Pair<String, LocalDate>> = emptyList(),
        val retag: Pair<String, String>? = null,
        val moved: VaultRepository.Moved? = null,
    ) {
        enum class Kind { DONE, OPENED, MOVED }
    }

    /**
     * Ticks go in the bar's list; on Ygdrasil they join its history, and a new step there ends what could be redone.
     * The "archive or delete?" bar of an earlier tick closes, so the bar on screen always undoes the newest tick.
     */
    private fun remember(step: UndoStep, inTree: Boolean) = _state.update {
        if (inTree) it.copy(treeUndo = (it.treeUndo + step).takeLast(UNDO_KEEP), treeRedo = emptyList())
        else it.copy(ticked = (it.ticked + step).takeLast(UNDO_KEEP), finished = null)
    }

    /** Like [edit], keeping the line's block before and after so the change can be undone. */
    private fun editRecorded(
        task: Task, kind: UndoStep.Kind, inTree: Boolean, events: List<BehaviorEvent> = emptyList(),
        onSaved: (() -> Unit)? = null, transform: (String) -> String,
    ) {
        runCatching { transform(task.raw) }.getOrNull()?.let { showEdited(task, it) }
        viewModelScope.launch {
            val result = writeLock.withLock { withContext(Dispatchers.IO) { runCatching { repo.rewriteLineRecorded(task, transform) } } }
            result.onSuccess { block ->
                remember(UndoStep(kind, task.title, listOf(block), events), inTree)
                onSaved?.invoke()
            }
            result.exceptionOrNull()?.let { e ->
                val text = if (e is VaultRepository.ConflictException) {
                    tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere and was reloaded. Try again.")
                } else {
                    tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")
                }
                _state.update { it.copy(message = text) }
            }
            if (result.isSuccess) reloadNote(task) else reload()
        }
    }

    /** The bar's time is up. */
    fun clearTicked() = _state.update { it.copy(ticked = emptyList()) }

    /** "เลิกทำ" on the bar: the newest tick goes back; the one before it then waits in the bar. */
    fun undoTick() {
        val step = _state.value.ticked.lastOrNull() ?: return
        _state.update { it.copy(ticked = it.ticked.dropLast(1), finished = null) }
        applyStep(step, undo = true) { }
    }

    /** Ygdrasil's ↶: the newest step goes back and can be done again with ↷. */
    fun treeUndo() {
        val step = _state.value.treeUndo.lastOrNull() ?: return
        _state.update { it.copy(treeUndo = it.treeUndo.dropLast(1), finished = null) }
        applyStep(step, undo = true) { ok -> if (ok) _state.update { it.copy(treeRedo = it.treeRedo + step) } }
    }

    /** Ygdrasil's ↷: the step undone last is done again. */
    fun treeRedo() {
        val step = _state.value.treeRedo.lastOrNull() ?: return
        _state.update { it.copy(treeRedo = it.treeRedo.dropLast(1), finished = null) }
        applyStep(step, undo = false) { ok -> if (ok) _state.update { it.copy(treeUndo = it.treeUndo + step) } }
    }

    /**
     * Undoes ([undo] true) or does again one step. A block that no longer stands as the step left it (edited in Obsidian
     * since) is not written over: the step is dropped and the owner told. [done] gets whether it all went through.
     */
    private fun applyStep(step: UndoStep, undo: Boolean, done: (Boolean) -> Unit) {
        step.retag?.let { (from, to) ->
            if (undo) retagAll(to, from, done) else retagAll(from, to, done)
            return
        }
        step.moved?.let { m ->
            val vault = _state.value.vault ?: return done(false)
            viewModelScope.launch {
                val result = writeLock.withLock { withContext(Dispatchers.IO) { runCatching { repo.swapMoved(vault, m, undo) } } }
                result.exceptionOrNull()?.let { e ->
                    val text = if (e is VaultRepository.ConflictException) {
                        tr("\"${step.title}\" ถูกแก้จากที่อื่นแล้ว จึงไม่ได้เขียนทับ", "\"${step.title}\" was changed elsewhere, so it was not written over")
                    } else {
                        tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")
                    }
                    _state.update { it.copy(message = text) }
                }
                done(result.isSuccess)
                reload()
            }
            return
        }
        val blocks = if (undo) step.blocks.asReversed() else step.blocks
        // The screen shows the change at once; the write and the read back follow.
        blocks.forEach { b ->
            val (expect, put) = if (undo) b.after to b.before else b.before to b.after
            visibleTask(b.fileUri, expect.first())?.let { showEdited(it, put.first()) }
        }
        val vault = _state.value.vault
        viewModelScope.launch {
            val result = writeLock.withLock { withContext(Dispatchers.IO) { runCatching { blocks.forEach { repo.swapBlock(it, undo) } } } }
            val app = getApplication<Application>()
            if (result.isSuccess) {
                if (undo) {
                    step.recurred.forEach { (title, day) -> RecurHistory.remove(app, title, day) }
                    val ids = step.events.mapTo(HashSet()) { it.id }
                    if (ids.isNotEmpty()) {
                        behavior = behavior.filter { it.id !in ids }
                        if (vault != null) withContext(Dispatchers.IO) { runCatching { BehaviorLog.forget(app, repo, vault, ids) } }.exceptionOrNull()?.let { e ->
                            _state.update { it.copy(message = tr("เลิกทำแล้ว แต่ลบบันทึกการทำเสร็จออกไม่ได้: ${e.message}", "Undone, but the done record could not be removed: ${e.message}")) }
                        }
                    }
                } else {
                    step.recurred.forEach { (title, day) -> RecurHistory.add(app, title, day) }
                    step.events.forEach { learn(it) }
                }
            } else {
                val e = result.exceptionOrNull()
                val text = if (e is VaultRepository.ConflictException) {
                    tr("\"${step.title}\" ถูกแก้จากที่อื่นแล้ว จึงไม่ได้เขียนทับ", "\"${step.title}\" was changed elsewhere, so it was not written over")
                } else {
                    tr("บันทึกไม่ได้: ${e?.message}", "Cannot save: ${e?.message}")
                }
                _state.update { it.copy(message = text) }
            }
            done(result.isSuccess)
            reload()
        }
    }

    /** The task on screen whose line in [fileUri] is [raw], if any. */
    private fun visibleTask(fileUri: String, raw: String): Task? = _state.value.let { s ->
        (s.tasks + s.parked + s.listItems).firstOrNull { it.fileUri == fileUri && it.raw == raw }
    }

    /** A move made in Ygdrasil, told in the bar, with "เลิกทำ" when [undo] is there. */
    class MoveUndo(val text: String, val undo: (() -> Unit)?)

    /**
     * Writes the owner's move of [old] to [new] into `ชื่อโปรเจกต์.md`, so the secretary writes the new names and old
     * ones are read as new, and offers to undo it. [n] is how many task lines moved.
     */
    private suspend fun recordMove(old: String, new: String, n: Int, undoable: Boolean = true, tell: Boolean = undoable) {
        val vault = _state.value.vault ?: return
        val today = LocalDate.now()
        val r = withContext(Dispatchers.IO) {
            runCatching {
                repo.editPath(vault, ProjectNames.PATH) { text -> ProjectNames.record(text, old, new, today) }
                repo.readPath(vault, ProjectNames.PATH)
            }
        }
        r.getOrNull()?.let { ProjectNames.current = ProjectNames.parse(it) }
        val text = tr("ย้าย #$old ไปเป็น #$new แล้ว ($n งาน)\nจดลงตาราง ชื่อโปรเจกต์.md แล้ว เลขาจะเขียนชื่อใหม่ตามนี้", "Moved #$old to #$new ($n tasks)\nNoted in ชื่อโปรเจกต์.md: the secretary writes the new name")
        if (tell) _state.update { it.copy(moved = MoveUndo(text, if (undoable) { { retagAll(new, old) } } else null)) }
    }

    fun clearMoved() = _state.update { it.copy(moved = null) }

    /** Everything tagged [old] (and under it) gets [new] in every note, as an undo of a move; [done] gets whether it went through. */
    private fun retagAll(old: String, new: String, done: (Boolean) -> Unit = {}) {
        val files = (_state.value.allTasks + _state.value.parked + _state.value.listItems)
            .filter { t -> t.tags.any { it == old || it.startsWith("$old/") } }.map { it.fileUri }.distinct()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.renameProject(files, old, new) } }
            result.onSuccess { n ->
                saveBranchStates(Branches.moveTag(_state.value.branchStates, old, new))
                _state.value.projectTags?.let { tags -> saveProjectTags(tags + new.substringBefore('/')) }
                recordMove(old, new, n, undoable = false)
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("ย้ายกลับไม่ได้: ", "Cannot move back: ") + e.message) } }
            done(result.isSuccess)
            reload()
        }
    }

    /** Renames a branch: every task line under it gets the new tag, and the states under it move along. */
    /**
     * Project Ygdrasil: a project or branch tagged [oldTag] dropped on [target] (a tag, or "" for the trunk) goes under
     * it with everything below it, in every note (see [Branches.graftTag]); its branch states go along.
     */
    fun graftBranch(oldTag: String, target: String) {
        val newTag = Branches.graftTag(oldTag, target) ?: return
        // Cancelled tasks are out of the tree but keep their tag, so they move too.
        val files = (_state.value.allTasks + _state.value.parked)
            .filter { t -> t.tags.any { it == oldTag || it.startsWith("$oldTag/") } }.map { it.fileUri }.distinct()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.renameProject(files, oldTag, newTag) } }
            result.onSuccess { n ->
                saveBranchStates(Branches.moveTag(_state.value.branchStates, oldTag, newTag))
                // A branch dropped on the trunk becomes a project of its own.
                _state.value.projectTags?.let { tags -> saveProjectTags(tags + newTag.substringBefore('/')) }
                // Undone with Ygdrasil's own button, so the bar only tells what moved.
                recordMove(oldTag, newTag, n, undoable = false, tell = true)
                remember(UndoStep(UndoStep.Kind.MOVED, "#$oldTag", retag = oldTag to newTag), inTree = true)
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("ย้ายไม่ได้: ", "Cannot move: ") + e.message) } }
            reload()
        }
    }

    fun renameBranch(node: Branches.Node, name: String) {
        val clean = Branches.clean(name)
        if (clean.isEmpty() || node.path.isEmpty() || clean == node.name) return
        val newPath = if ('/' in node.path) node.parentPath + "/" + clean else clean
        val oldTag = node.tag
        val newTag = node.project + "/" + newPath
        // Cancelled tasks are out of the tree but keep their tag, so they move too.
        val files = (_state.value.allTasks + _state.value.parked)
            .filter { t -> t.tags.any { it == oldTag || it.startsWith("$oldTag/") } }.map { it.fileUri }.distinct()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.renameProject(files, oldTag, newTag) } }
            result.onSuccess { n ->
                saveBranchStates(Branches.movePaths(_state.value.branchStates, node.project, node.path, newPath))
                recordMove(oldTag, newTag, n)
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("เปลี่ยนชื่อไม่ได้: ", "Cannot rename: ") + e.message) } }
            reload()
        }
    }

    /** Removes an empty branch (one with tasks keeps them safe: move or delete them first). */
    fun deleteBranch(node: Branches.Node) {
        if (node.path.isEmpty()) return
        if (node.all.isNotEmpty()) {
            _state.update { it.copy(message = tr("ยังมีงานในกิ่งนี้ ย้ายหรือลบงานก่อน", "This branch still has tasks; move or delete them first")) }
            return
        }
        saveBranchStates(Branches.dropPaths(_state.value.branchStates, node.project, node.path))
    }

    /** Lines that renaming [old] would change, for the preview: (files, lines). */
    fun renameCount(old: String): Pair<Int, Int> {
        val tasks = (_state.value.tasks + _state.value.parked + _state.value.listItems).filter { Projects.projectOf(it) == old }
        return tasks.map { it.fileUri }.distinct().size to tasks.size
    }

    /**
     * Renames a project: every task line in the vault with `#old` or `#old/...` gets the new name, and the
     * order, stars, task order, strict mode and branch states move with it.
     */
    fun renameProject(old: String, new: String) {
        // A name with "/" puts the project under another one (Omni as Claude/Omni); each part is cleaned on its own.
        val name = new.trim().removePrefix("#").split('/').map { it.trim().replace(Regex("""\s+"""), "-") }.filter { it.isNotEmpty() }.joinToString("/")
        if (name.isEmpty() || name == old || name.startsWith("$old/")) return
        val s0 = _state.value
        val files = (s0.tasks + s0.parked + s0.listItems).filter { Projects.projectOf(it) == old }.map { it.fileUri }.distinct()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.renameProject(files, old, name) } }
            result.onSuccess { n ->
                val s = _state.value
                val root = name.substringBefore('/')
                val order = s.projectOrder.map { if (it == old) root else it }.distinct()
                val starred = s.starred.map { if (it == old) root else it }.toSet()
                val taskOrder = s.projectTaskOrder.mapKeys { (k, _) -> if (k == old) root else k }
                val strict = s.strictProjects.map { if (it == old) root else it }.toSet()
                val branches = Branches.moveTag(s.branchStates, old, name)
                s.projectTags?.let { saveProjectTags(it - old + name.substringBefore('/')) }
                recordMove(old, name, n)
                prefs.edit()
                    .putString(KEY_PROJECT_ORDER, order.joinToString("\n"))
                    .putStringSet(KEY_STARRED, starred)
                    .putStringSet(KEY_PROJECT_TASKS, taskOrder.map { (k, v) -> k + "\t" + v.joinToString("\u001F") }.toSet())
                    .putStringSet(KEY_STRICT, strict)
                    .putStringSet(KEY_BRANCHES, Branches.encode(branches))
                    .apply()
                _state.update {
                    it.copy(
                        projectOrder = order, starred = starred, projectTaskOrder = taskOrder, strictProjects = strict, branchStates = branches,
                    )
                }
            }
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = tr("เปลี่ยนชื่อไม่ได้: ", "Cannot rename: ") + e.message) } }
            reload()
        }
    }

    /** Saves what the Focus screen counts down to; the single countdown kept before goes. */
    fun setCountdowns(pins: List<Countdown.Pin>) {
        prefs.edit().putStringSet(KEY_COUNTDOWNS, pins.map { it.key }.toSet()).remove(KEY_COUNTDOWN).apply()
        _state.update { it.copy(countdowns = pins) }
        viewModelScope.launch {
            val pinned = withContext(Dispatchers.IO) { loadPinned(getApplication(), pins) }
            _state.update { it.copy(pinnedEvents = pinned) }
        }
    }

    /** Reads the year ahead for the countdown picker's calendar tab. */
    fun loadAhead() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val ahead = withContext(Dispatchers.IO) {
                runCatching { CalendarReader.events(getApplication(), today.atStartOfDay(), today.plusDays(366).atStartOfDay()) }.getOrDefault(emptyList())
            }
            _state.update { it.copy(aheadEvents = ahead) }
        }
    }

    /** Tonight's bedtime; the usual one comes back tomorrow. [evening] is the date the night starts on. */
    fun setTonightBedtime(evening: LocalDate, time: kotlinx.datetime.LocalTime) {
        prefs.edit().putString(KEY_TONIGHT_BED, nightValue(evening, time)).apply()
        _state.update { it.copy(tonightBed = evening to time) }
    }

    /** Tonight's wake time, or null to go back to the usual one (or an hour before the morning's first event). */
    fun setTonightWake(evening: LocalDate, time: kotlinx.datetime.LocalTime?) {
        prefs.edit().apply { if (time == null) remove(KEY_TONIGHT_WAKE) else putString(KEY_TONIGHT_WAKE, nightValue(evening, time)) }.putBoolean(KEY_WAKE_FROM_WATCH, false).apply()
        _state.update { it.copy(tonightWake = time?.let { evening to it }, wakeFromWatch = false) }
    }

    /** Sends tonight's wake time to the watch, which rings it (the phone stays quiet); null [wakeAt] cancels it. */
    fun setWakeAlarm(evening: LocalDate?, wakeAt: LocalDateTime?, sleepMinutes: Long = 0) {
        viewModelScope.launch {
            val reached = withContext(Dispatchers.IO) { WatchAlarm.send(getApplication(), wakeAt, sleepMinutes) }
            // Tied to a night only when it is that night's wake time; an alarm later in the day stands alone.
            val set = if (evening == null) null else wakeAt?.let { evening to it.time }
            if (reached) prefs.edit().apply {
                if (set == null) remove(KEY_ALARM_SET) else putString(KEY_ALARM_SET, nightValue(set.first, set.second))
                if (wakeAt == null) remove(KEY_ALARM_AT) else putString(KEY_ALARM_AT, wakeAt.toString())
            }.apply()
            _state.update {
                if (reached) it.copy(alarmSet = set, alarmAt = wakeAt, alarmFailed = false)
                else it.copy(alarmFailed = true, message = tr("ส่งไม่ถึงนาฬิกา ดูว่าเชื่อมต่ออยู่และลง Omni บนนาฬิกาแล้ว", "Could not reach the watch. Check it is connected and has Omni"))
            }
        }
    }

    /**
     * Sets the watch alarm for any day and time, as an alarm clock does (null turns it off). A morning alarm is also
     * that night's wake time, so the sleep card counts from it.
     */
    fun setAlarm(at: LocalDateTime?, sleepMinutes: Long = 0) {
        if (at != null && at.time < kotlinx.datetime.LocalTime(12, 0)) {
            val evening = at.date.minusDays(1)
            setTonightWake(evening, at.time)
            setWakeAlarm(evening, at, sleepMinutes)
        } else {
            setWakeAlarm(null, at, sleepMinutes)
        }
    }

    private fun alarmAtPref(): LocalDateTime? =
        prefs.getString(KEY_ALARM_AT, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }?.takeIf { it > LocalDateTime.now() }

    private fun nightValue(evening: LocalDate, time: kotlinx.datetime.LocalTime) = "$evening ${"%02d:%02d".format(time.hour, time.minute)}"

    private fun nightPref(key: String): Pair<LocalDate, kotlinx.datetime.LocalTime>? = prefs.getString(key, null)?.let { v ->
        runCatching { LocalDate.parse(v.substringBefore(' ')) to kotlinx.datetime.LocalTime.parse(v.substringAfter(' ')) }.getOrNull()
    }

    /** The usual bedtime and wake time, kept in the profile note the assistant also reads. */
    fun setSleepTimes(sleep: kotlinx.datetime.LocalTime, wake: kotlinx.datetime.LocalTime) = saveProfile(_state.value.profile.copy(sleep = sleep, wake = wake))

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

    private fun declined() = prefs.getStringSet(KEY_DECLINED, emptySet()).orEmpty().toSet() + Behavior.declined(behaviorNow())

    /** Every event of the behavior log: the vault's, any this phone has not sent yet, and the old preference log until it is brought in. */
    private fun behaviorNow(): List<BehaviorEvent> {
        val old = prefs.getStringSet(KEY_DONE_LOG, emptySet()).orEmpty().mapNotNull { Behavior.legacyDone(it, BehaviorLog.SRC) }
        return (behavior + BehaviorLog.waiting(getApplication()) + old).distinctBy { it.id }
    }

    private fun doneLog(): List<Insight.Done> = Behavior.doneLog(behaviorNow())

    /** What tasks like [draft] usually came with, as chips for the quick-add sheet ([Learned.hints]). */
    fun quickHints(draft: QuickAdd.Draft): List<Learned.Hint> = Learned.hints(draft, behaviorNow(), _state.value.profile, _state.value.today)

    /** How the owner has worked lately, for the picture handed to Claude ([Learned.summary]). */
    fun habits(): List<String> = Learned.summary(_state.value.tasks, behaviorNow(), _state.value.today)

    /** Remembers when work gets done, so the assistant can notice the owner's rhythm. */
    private fun logDone(task: Task): BehaviorEvent? = Behavior.of(Behavior.DONE, task, LocalDateTime.now(), BehaviorLog.SRC).also { learn(it) }

    /** A task line just added, as a "create" event. */
    private fun noteCreated(line: String) {
        TaskLine.parse(line.substringBefore('\n'))?.let { learn(Behavior.of(Behavior.CREATE, it, LocalDateTime.now(), BehaviorLog.SRC)) }
    }

    /** Keeps [event] for the behavior log and sends it to the vault a little later, with any others made meanwhile. */
    private fun learn(event: BehaviorEvent?) {
        event ?: return
        val app = getApplication<Application>()
        BehaviorLog.record(app, event)
        val vault = _state.value.vault ?: return
        behaviorSend?.cancel()
        behaviorSend = viewModelScope.launch {
            delay(5_000)
            withContext(Dispatchers.IO) { runCatching { BehaviorLog.sync(app, repo, vault) } }.onSuccess { behavior = it }
        }
    }

    /** One write to the vault at a time, so a second quick edit finds the line the first one wrote. */
    private val writeLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Shows the edited line at once (the screen does not wait for the write, which on Drive takes a few round trips),
     * then writes it and reads the note back. A failed write reloads everything, which puts the old line back.
     */
    private fun showEdited(task: Task, raw: String) {
        if (task.todayCopy) return
        val parsed = TaskLine.parse(raw) ?: return
        val edited = parsed.copy(
            filePath = task.filePath, fileUri = task.fileUri, lineIndex = task.lineIndex, notes = task.notes, parent = task.parent, list = task.list,
        )
        fun List<Task>.swap() = map { if (it.key == task.key) edited else it }
        _state.update { it.copy(tasks = it.tasks.swap(), parked = it.parked.swap(), listItems = it.listItems.swap()) }
    }

    private fun edit(task: Task, onSaved: (() -> Unit)? = null, transform: (String) -> String) {
        runCatching { transform(task.raw) }.getOrNull()?.let { showEdited(task, it) }
        viewModelScope.launch {
            val result = writeLock.withLock { withContext(Dispatchers.IO) { runCatching { repo.rewriteLine(task, transform) } } }
            if (result.isSuccess) onSaved?.invoke()
            result.exceptionOrNull()?.let { e ->
                val text = if (e is VaultRepository.ConflictException) {
                    tr("ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง", "The file changed elsewhere and was reloaded. Try again.")
                } else {
                    tr("บันทึกไม่ได้: ${e.message}", "Cannot save: ${e.message}")
                }
                _state.update { it.copy(message = text) }
            }
            if (result.isSuccess) reloadNote(task) else reload()
        }
    }

    private companion object {
        /** Where Claude keeps its work log, read into what Gemini sees. */
        const val CLAUDE_LOG = "📁 Folder/หลังบ้าน/Claude/Claude log.md"

        /** The brief is read again on opening the assistant only when it is older than this. */
        const val BRIEF_EVERY_MS = 60 * 60 * 1000L
        const val KEY_VAULT = "vault"
        /** The secretary round (its start, epoch ms) for which Obsidian was last opened to sync the book. */
        const val KEY_SYNC_OPENED_FOR = "secretary.syncOpenedFor"
        const val KEY_URGENT = "urgentRule"
        const val KEY_FUTURE_COUNT = "futureCount"
        const val KEY_SKIPPED = "skippedFuture"
        const val KEY_DISMISSED = "dismissedSuggestions"
        const val KEY_DECLINED = "declinedInsights"
        const val KEY_DONE_LOG = "doneLog"
        const val KEY_REVIEWED = "reviewedTasks"
        const val KEY_PROJECT_ORDER = "projectOrder"
        const val KEY_STARRED = "starredProjects"
        const val KEY_PROJECT_TAGS = "projectTags"
        /** Kept among the stars when the owner has unpinned every project; no tag can hold a tab. */
        const val NO_PIN = "\t"
        const val KEY_PROJECT_TASKS = "projectTaskOrder"
        const val KEY_STRICT = "strictProjects"
        const val KEY_OFF_PROJECTS = "declinedProjects"
        /** Bubbles the owner said "no" to hiding (the app's own hints in the guide's learned tab). */
        const val KEY_GUIDE_DISMISSED = "guideDismissed"
        const val KEY_TREE_REACH = "treeReach"
        /** The single countdown (a task title) kept before there could be several; read once, then removed. */
        const val KEY_COUNTDOWN = "focus.countdown"
        const val KEY_COUNTDOWNS = "focus.countdowns"
        const val KEY_FOLDED_GROUPS = "foldedGroups"
        const val KEY_BRANCHES = "branchStates"
        const val KEY_FOLDED_COLUMNS = "foldedColumns"
        const val KEY_CUSTOM_KINDS = "customKinds"
        const val KEY_HIDDEN_KINDS = "hiddenKinds"
        const val KEY_TONIGHT_BED = "sleep.tonight"
        // WatchListener writes these too, when a wake time is set on the watch.
        const val KEY_TONIGHT_WAKE = WatchAlarm.KEY_TONIGHT_WAKE
        const val KEY_ALARM_SET = WatchAlarm.KEY_ALARM_SET
        const val KEY_WAKE_FROM_WATCH = WatchAlarm.KEY_WAKE_FROM_WATCH
        const val KEY_ALARM_AT = WatchAlarm.KEY_ALARM_AT
        private val WATCH_KEYS = setOf(KEY_TONIGHT_WAKE, KEY_ALARM_SET, KEY_WAKE_FROM_WATCH, KEY_ALARM_AT)
        const val KEY_SAVED_FILTERS = "savedFilters"
        const val KEY_LISTS_SEEDED = "lists.seeded"
        const val KEY_ARCHIVE_DAYS = "archive.days"
        const val KEY_ARCHIVE_SWEPT = "archive.sweptOn"
        const val KEY_ASK_ON_DONE = "archive.askOnDone"
        /** How many steps the tick bar and Ygdrasil's history keep. */
        const val UNDO_KEEP = 500

        /** Keys that change on their own (alarm bookkeeping, sync stamps) and must not trigger a settings save. */
        val SYNC_IGNORED = setOf("alarmIds", "settings.syncedAt", KEY_VAULT, KEY_ARCHIVE_SWEPT, CalendarReader.KEY_HIDDEN, DriveLink.KEY_ON)
    }
}
