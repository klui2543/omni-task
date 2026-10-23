@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.omnitask.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnitask.model.Lang
import app.omnitask.model.UrgentRule
import app.omnitask.model.tr

enum class Screen(private val th: String, private val en: String, val icon: ImageVector) {
    FOCUS("โฟกัส", "Focus", Ic.focus),
    TASKS("งาน", "Tasks", Ic.tasks),
    PROJECTS("โปรเจกต์/ลิสต์", "Projects/Lists", Ic.folder),
    AI("ผู้ช่วย", "Assistant", Ic.spark),
    /** The bottom bar's Settings tab; the side rail opens Settings from its menu, so it leaves this one out. */
    MORE("ตั้งค่า", "Settings", Ic.gear),
    ;

    val label get() = tr(th, en)
}

@Composable
fun OmniTaskApp(vm: TaskViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Each calendar's colour and label, read once (see CalendarStyles).
    CalendarStyles.load(LocalContext.current)
    NewLook.load(LocalContext.current)
    val pickVault = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setVault(uri)
    }

    if (state.vault == null) {
        Welcome(onPick = { pickVault.launch(null) })
        return
    }

    var screen by rememberSaveable { mutableStateOf(Screen.FOCUS) }
    var notifyOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf<QuickAddRequest?>(null) }

    // Holding + records tasks by voice; the mic is asked for the first time.
    val voice by vm.voice.collectAsStateWithLifecycle()
    val micContext = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startVoice() else vm.showMessage(tr("ต้องอนุญาตไมค์ก่อน ถึงจะจดด้วยเสียงได้", "Allow the microphone to note tasks by voice"))
    }
    fun startVoice() {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (voice.recording != null) return
        val granted = micContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) vm.startVoice() else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // The frames and emoji page and the settings pages are drawn alone, in place of the tabs and their sheets. What a
    // widget asks for closes them first, or quick add or the calendar would wait behind them.
    fun leavePages() {
        if (state.month != null) vm.requestMonth(null)
        notifyOpen = false
        settingsOpen = false
    }

    // A widget or shortcut asked for quick add (or the assistant): open it once, then clear the request.
    LaunchedEffect(state.quickAdd) {
        state.quickAdd?.let { r ->
            leavePages()
            if (r.assistant) screen = Screen.AI else adding = r
            vm.requestQuickAdd(null)
        }
    }

    // The month widget asked for the calendar: go to the Tasks tab, whose calendar takes the day.
    LaunchedEffect(state.calendarDay) {
        if (state.calendarDay != null) {
            leavePages()
            screen = Screen.TASKS
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    LaunchedEffect(state.deleted) {
        state.deleted?.let { d ->
            vm.clearDeleted()
            val r = snackbar.showSnackbar(tr("ลบ \"${d.title}\" แล้ว", "Deleted \"${d.title}\""), actionLabel = tr("เลิกทำ", "Undo"), duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete(d)
        }
    }

    LaunchedEffect(state.archived) {
        state.archived?.let { d ->
            vm.clearArchived()
            val r = snackbar.showSnackbar(tr("ย้าย \"${d.title}\" เข้าคลังแล้ว", "Archived \"${d.title}\""), actionLabel = tr("เลิกทำ", "Undo"), duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoArchive(d)
        }
    }

    LaunchedEffect(state.swapped) {
        state.swapped?.let { u ->
            val text = if (u.toEvent) tr("เปลี่ยน \"${u.title}\" เป็นนัดแล้ว", "\"${u.title}\" is now an event") else tr("เปลี่ยน \"${u.title}\" เป็นงานแล้ว", "\"${u.title}\" is now a task")
            val r = snackbar.showSnackbar(text, actionLabel = tr("เลิกทำ", "Undo"), duration = SnackbarDuration.Short)
            // Cleared only after the snackbar closes: clearing first would restart this effect and cancel it.
            vm.clearSwapped()
            if (r == SnackbarResult.ActionPerformed) u.undo()
        }
    }

    // The frames and emoji page, from the month widget or the calendar's pencil; closing it lands on the calendar.
    state.month?.let { r ->
        MonthScreen(r, state) { day -> vm.requestCalendar(day); vm.requestMonth(null) }
        return
    }

    if (notifyOpen) {
        NotifySettingsScreen(state.notify, vm::setNotify, vm::calendarChanged) { notifyOpen = false }
        return
    }

    if (settingsOpen) {
        // Reload after a language switch, so text the view model keeps (like the assistant's insight card) follows it.
        SettingsScreen(
            profile = state.profile, onSleepTimes = vm::setSleepTimes, onLanguageChange = vm::reload,
            archive = ArchiveSettings(state.archiveDays, state.askOnDone, vm::setArchiveDays, vm::setAskOnDone),
            calendars = CalendarChoices(state.calendars, state.hiddenCalendars, vm::setCalendarShown, vm::calendarChanged),
            drive = DriveSettings(state.drive, vm::connectDrive, vm::driveSignInShown, vm::driveSignedIn, vm::confirmDrive, vm::disconnectDrive, vm::putBackClash, vm::dismissClash),
            ai = AiSettings(state.ai, vm::setAiKey, vm::testAiKey, vm::setAutoBrief),
        ) { settingsOpen = false }
        return
    }

    BackHandler(enabled = screen != Screen.FOCUS) { screen = Screen.FOCUS }

    val config = LocalConfiguration.current
    val width = config.screenWidthDp
    val wide = width >= WIDE_DP
    val twoPane = width >= TWO_PANE_DP
    // The unfolded Find N is wide but close to square: its menu stays in the bottom bar, as when folded, and only an
    // opened task sits beside the page. Tablets and landscape (a wider shape) keep the rail on the left.
    val rail = wide && maxOf(width, config.screenHeightDp).toFloat() / minOf(width, config.screenHeightDp).coerceAtLeast(1) > SQUARE_RATIO
    val open: (app.omnitask.model.Task) -> Unit = { editingKey = it.key }
    // On a phone the menu's items live in the Settings tab on the bottom bar, where the thumb reaches; the rail layout keeps the menu at the top.
    val menu: @Composable () -> Unit = {
        if (rail) AppMenu(
            urgentRule = state.urgentRule,
            onUrgent = vm::setUrgentRule,
            onNotify = { notifyOpen = true },
            onSettings = { settingsOpen = true },
            onReload = vm::reload,
            onVault = { pickVault.launch(state.vault) },
        )
    }

    val editing = state.allTasks.firstOrNull { it.key == editingKey } ?: state.parked.firstOrNull { it.key == editingKey }

    val fullPage = remember { mutableIntStateOf(0) }
    // On a phone the bottom bar floats over the page: full pages (the mind map) keep their own controls above it.
    val askShown = screen != Screen.AI
    val dockHeight = DOCK_HEIGHT + 1.dp + if (askShown) ASK_HEIGHT else 0.dp
    val dock = if (rail || fullPage.intValue > 0) 0.dp else dockHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalFullPage provides fullPage, LocalDockClearance provides dock) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = C.accent, trackColor = C.bg)
                } else {
                    Box(Modifier.height(2.dp))
                }
                // On wide screens reading columns stay a comfortable width; the views (Kanban, Gantt, calendar) use it all.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    // The task views (Kanban, Gantt, calendar) use all of it; the task list sets its own reading width.
                    Box(Modifier.widthIn(max = if (screen == Screen.TASKS) Dp.Unspecified else 760.dp)) {
                    when (screen) {
                        Screen.FOCUS -> PullToReload(state.loading, vm::pullRefresh) { FocusScreen(state, vm, open, menu) }
                        Screen.TASKS -> PullToReload(state.loading, vm::pullRefresh) { TasksScreen(state, vm, open) }
                        Screen.PROJECTS -> PullToReload(state.loading, vm::pullRefresh) { ProjectsScreen(state, vm, open) }
                        Screen.AI -> AssistantScreen(state, vm, open, onSettings = { if (rail) settingsOpen = true else screen = Screen.MORE })
                        // The Settings tab: the settings, with the assistant, notifications, urgency and the vault around them.
                        Screen.MORE -> SettingsScreen(
                            profile = state.profile, onSleepTimes = vm::setSleepTimes, onLanguageChange = vm::reload,
                            archive = ArchiveSettings(state.archiveDays, state.askOnDone, vm::setArchiveDays, vm::setAskOnDone),
                            calendars = CalendarChoices(state.calendars, state.hiddenCalendars, vm::setCalendarShown, vm::calendarChanged),
                            drive = DriveSettings(state.drive, vm::connectDrive, vm::driveSignInShown, vm::driveSignedIn, vm::confirmDrive, vm::disconnectDrive, vm::putBackClash, vm::dismissClash),
                            ai = AiSettings(state.ai, vm::setAiKey, vm::testAiKey, vm::setAutoBrief),
                            head = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SettingsLinks(onAssistant = { screen = Screen.AI }, onNotify = { notifyOpen = true })
                                    UrgentGroup(state.urgentRule, vm::setUrgentRule)
                                }
                            },
                            tail = { VaultLinks(onReload = vm::reload, onVault = { pickVault.launch(state.vault) }) },
                        )
                    }
                    }
                }
            }
        }
    }
    // On a phone the assistant opens from the line on the bottom bar; on the side rail it floats at the bottom corner.
    // A page that fills the screen (the project mind map) hides it and the bottom bar ([FullPage]).
    val assistantButton: @Composable (Modifier) -> Unit = { m ->
        if (screen != Screen.AI && fullPage.intValue == 0) {
            Box(
                m.size(56.dp).clip(RoundedCornerShape(18.dp)).background(if (C.calm) C.raised else C.accentSoft)
                    .border(1.dp, if (C.calm) C.control else C.accentLine, RoundedCornerShape(18.dp)).clickable { screen = Screen.AI },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.spark, tr("ผู้ช่วย", "Assistant"), tint = C.accentText, modifier = Modifier.size(24.dp)) }
        }
    }
    // Room above the bottom bar for what pops up over the lists.
    val popupLift = (if (fullPage.intValue > 0) 0.dp else dockHeight) + 12.dp

    // An opened task beside the page (wide screens), under the same close button as the sheet.
    val editPane: @Composable () -> Unit = {
        if (wide && twoPane && editing != null) {
            Box(Modifier.width(400.dp).fillMaxHeight().background(C.card).statusBarsPadding()) {
                CompositionLocalProvider(LocalPane provides true) {
                    EditSheet(editing, state, vm, onDismiss = { editingKey = null }, onOpen = open)
                }
            }
        }
    }

    if (rail) {
        // Landscape or tablet: the nav becomes a rail on the left and an opened task sits on the right.
        // Room at the end of the lists for the assistant button floating at the bottom corner.
        CompositionLocalProvider(LocalNavClearance provides 76.dp) {
            Row(Modifier.fillMaxSize().background(C.bg).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                NavRail(screen, onAdd = { adding = QuickAddRequest() }, onVoice = ::startVoice) { screen = it }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    content()
                    assistantButton(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 18.dp))
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)) {
                        Snackbar(it, containerColor = C.raised, contentColor = C.text, shape = RoundedCornerShape(14.dp))
                    }
                    state.finished?.let { t -> FinishedBar(t, state.archiveDays, vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)) }
                }
                editPane()
            }
        }
    } else {
        // The lists end clear of the bottom bar, which is taller while the assistant line shows.
        CompositionLocalProvider(LocalNavClearance provides dockHeight + 16.dp) {
        Row(Modifier.fillMaxSize().background(C.bg).then(if (wide) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)) else Modifier)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
                if (fullPage.intValue == 0) DockNav(
                    selected = screen, showAsk = askShown,
                    onSelect = { screen = it }, onAdd = { adding = QuickAddRequest() }, onVoice = ::startVoice, onAsk = { vm.askAi(it); screen = Screen.AI },
                    presets = askPresets(state, vm),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = popupLift)) {
                    Snackbar(it, containerColor = C.raised, contentColor = C.text, shape = RoundedCornerShape(14.dp))
                }
                state.finished?.let { t ->
                    FinishedBar(t, state.archiveDays, vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = popupLift))
                }
            }
            editPane()
        }
        }
    }

    if (editing != null && !(wide && twoPane)) {
        EditSheet(editing, state, vm, onDismiss = { editingKey = null }, onOpen = open)
    }
    // Waits for the tasks to load, so opening from the notification never shows an empty deck first.
    if (state.askStuck && state.tasks.isNotEmpty()) StuckDeck(state, vm, onOpen = open) { vm.requestStuck(false) }
    state.pendingImage?.let { AttachChoiceDialog(it, vm::resolvePendingImage) }
    state.crash?.let { report -> CrashDialog(report, vm::dismissCrash) }
    state.closingParent?.let { parent ->
        val left = state.subtasksOf(parent).count { it.isOpen }
        AlertDialog(
            onDismissRequest = { vm.closeParent(null) },
            containerColor = C.raised,
            title = { Text(tr("ยังมีงานย่อยค้าง $left งาน", "$left subtasks are still open")) },
            text = { Text(tr("ติ๊กงานย่อยที่เหลือให้เสร็จไปด้วยไหม", "Tick the remaining subtasks as done too?"), color = C.text2) },
            confirmButton = { TextButton(onClick = { vm.closeParent(true) }) { Text(tr("ติ๊กทั้งหมด", "Tick all"), color = C.accent) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.closeParent(null) }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) }
                    TextButton(onClick = { vm.closeParent(false) }) { Text(tr("เฉพาะงานแม่", "Only this task"), color = C.text2) }
                }
            },
        )
    }
    adding?.let { r ->
        QuickAddSheet(state, vm, r.voice, r.status, r.text, r.link, onAskAssistant = { vm.askAi(it); screen = Screen.AI }) { adding = null }
    }
    VoiceSheet(voice, vm)
}

/**
 * Asked once a task is ticked done: move it to the archive note, delete it, or leave it ticked. It goes
 * away on its own after a while, which leaves the task as it is (the daily sweep archives it later).
 */
@Composable
private fun FinishedBar(task: app.omnitask.model.Task, archiveDays: Int, vm: TaskViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(task.raw) {
        delay(10_000)
        vm.clearFinished()
    }
    Column(
        modifier.padding(horizontal = 12.dp).widthIn(max = 520.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(C.raised).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(tr("เสร็จแล้ว: ${task.title}", "Done: ${task.title}"), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (archiveDays > 0) tr("ถ้าไม่เลือก จะย้ายเข้าคลังเองหลัง $archiveDays วัน", "If left, it moves to the archive after $archiveDays days")
            else tr("เก็บเข้าคลัง หรือลบออกจากโน้ตเลยไหม", "Archive it, or delete it from the note?"),
            Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.caption,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = vm::clearFinished) { Text(tr("ไว้ก่อน", "Keep"), color = C.text2) }
            TextButton(onClick = { vm.resolveFinished(archive = false) }) { Text(tr("ลบ", "Delete"), color = C.red) }
            TextButton(onClick = { vm.resolveFinished(archive = true) }) { Text(tr("เก็บเข้าคลัง", "Archive"), color = C.accent) }
        }
    }
}

/** The same destinations as a vertical pill on the left, for wide screens. */
@Composable
private fun NavRail(selected: Screen, onAdd: () -> Unit, onVoice: () -> Unit, onSelect: (Screen) -> Unit) {
    // A phone on its side is short: smaller items, and the rail scrolls rather than clips.
    val itemH = if (LocalConfiguration.current.screenHeightDp < 440) 46.dp else 56.dp
    Column(
        Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(C.navBg)
                .border(1.dp, C.cardBorder, RoundedCornerShape(20.dp)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Adding a task, where the bottom bar of a phone has it in the middle.
            Box(
                Modifier.width(72.dp).height(itemH).clip(RoundedCornerShape(14.dp)).background(if (C.calm) C.raised else C.accent)
                    .combinedClickable(onClick = onAdd, onLongClick = onVoice, onLongClickLabel = tr("จดด้วยเสียง", "Note by voice")),
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.plus, tr("เพิ่มงาน", "New task"), tint = if (C.calm) C.accentText else C.onAccent, modifier = Modifier.size(24.dp)) }
            Screen.entries.filter { it != Screen.MORE }.forEach { s ->
                val on = s == selected
                // The quiet look marks the current place softly, as the bottom bar does, instead of a filled pill.
                val onBg = if (C.calm) C.accentSoft else C.accent
                val onFg = if (C.calm) C.accentText else C.onAccent
                Column(
                    Modifier.width(72.dp).height(itemH).clip(RoundedCornerShape(14.dp))
                        .background(if (on) onBg else Color.Transparent).clickable { onSelect(s) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(s.icon, null, tint = if (on) onFg else C.muted, modifier = Modifier.size(20.dp))
                    Text(s.label, fontSize = TS.micro, color = if (on) onFg else C.muted, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/** Height of the bottom bar's tabs, without the system navigation inset. */
private val DOCK_HEIGHT = 52.dp

/**
 * Height of the line above the tabs for the assistant: the field, with room above and below it and a hairline under
 * it, so a tap meant for a tab does not land in the field (or the other way round).
 */
private val ASK_HEIGHT = 67.dp

/**
 * The bottom bar on a phone, flush with the screen's edge so every button is in the thumb's reach. On top, a line to
 * type a question for the assistant, with Send once something is typed (left out on the assistant's own page, which
 * has its input there); under it the tabs as words around the add button in the middle. While the keyboard is up the
 * tabs step aside: the line rides up with it when it is the one typed in, with ready questions above it, and otherwise
 * the whole bar goes, so it never covers the field being typed in (the assistant's own, a search).
 */
@Composable
private fun DockNav(
    selected: Screen,
    showAsk: Boolean,
    onSelect: (Screen) -> Unit,
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    onAsk: (String) -> Unit,
    presets: List<AskPreset>,
    modifier: Modifier = Modifier,
) {
    var question by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    var typingHere by remember { mutableStateOf(false) }
    // The line leaves with the assistant's page while focused, and would otherwise be thought typed in still.
    LaunchedEffect(showAsk) { if (!showAsk) typingHere = false }
    // By the keyboard's height: isImeVisible reads true until the first insets arrive, which hid the bar at start.
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (keyboard && !(showAsk && typingHere)) return
    val send = {
        if (question.isNotBlank()) {
            onAsk(question.trim())
            question = ""
        }
    }
    Column(modifier.fillMaxWidth().background(C.card).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.cardBorder))
        // While the line is typed in, the ready questions float up above it with the keyboard; one opens the assistant.
        if (showAsk && typingHere) PresetRow(presets, Modifier.padding(top = 10.dp)) {
            question = ""
            focus.clearFocus()
            onSelect(Screen.AI)
        }
        if (showAsk) Row(
            Modifier.fillMaxWidth().height(ASK_HEIGHT - 1.dp).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(C.bg)
                    .border(1.dp, C.control, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp)
                    .onFocusChanged { typingHere = it.isFocused },
                textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                cursorBrush = SolidColor(C.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        if (question.isEmpty()) Text(tr("ถามผู้ช่วย", "Ask the assistant"), color = C.faint, fontSize = TS.body, maxLines = 1)
                        inner()
                    }
                },
            )
            // On the right: with nothing typed, a speech bubble that opens the assistant's page; once typed, send.
            if (question.isNotBlank()) Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.accent).clickable { send() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.send, tr("ส่งถึงผู้ช่วย", "Send to the assistant"), tint = C.onAccent, modifier = Modifier.size(20.dp)) }
            else Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.accentSoft).border(1.dp, C.accentLine, RoundedCornerShape(14.dp))
                    .clickable { focus.clearFocus(); onSelect(Screen.AI) },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.chat, tr("เปิดหน้าผู้ช่วย", "Open the assistant"), tint = C.accentText, modifier = Modifier.size(20.dp)) }
        }
        if (keyboard) return@Column
        if (showAsk) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(C.divider))
        Row(Modifier.fillMaxWidth().height(DOCK_HEIGHT).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DockItem(Screen.FOCUS.label, selected == Screen.FOCUS, { onSelect(Screen.FOCUS) }, Modifier.weight(1f))
            DockItem(Screen.TASKS.label, selected == Screen.TASKS, { onSelect(Screen.TASKS) }, Modifier.weight(1f))
            // Adding a task, in the middle of the tabs where it always was.
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(width = 56.dp, height = 38.dp).clip(RoundedCornerShape(14.dp)).background(if (C.calm) C.raised else C.accent)
                        .combinedClickable(onClick = onAdd, onLongClick = onVoice, onLongClickLabel = tr("จดด้วยเสียง", "Note by voice")),
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.plus, tr("เพิ่มงาน", "New task"), tint = if (C.calm) C.accentText else C.onAccent, modifier = Modifier.size(22.dp)) }
            }
            DockItem(tr("โปรเจกต์", "Projects"), selected == Screen.PROJECTS, { onSelect(Screen.PROJECTS) }, Modifier.weight(1f))
            DockItem(Screen.MORE.label, selected == Screen.MORE, { onSelect(Screen.MORE) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockItem(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = TS.body, color = if (on) C.text else C.faint, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false)
        // A short line under the current tab; the weight of the word is not the only sign.
        Box(Modifier.padding(top = 4.dp).size(width = 18.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(if (on) C.accent else Color.Transparent))
    }
}

@Composable
private fun AppMenu(urgentRule: UrgentRule, onUrgent: (UrgentRule) -> Unit, onNotify: () -> Unit, onSettings: () -> Unit, onReload: () -> Unit, onVault: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SquareButton(Icons.Default.MoreVert, tr("เมนู", "Menu"), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = C.raised) {
            DropdownMenuItem(text = { Text(tr("การแจ้งเตือน", "Notifications")) }, onClick = { open = false; onNotify() })
            DropdownMenuItem(text = { Text(tr("โหลดใหม่", "Reload")) }, onClick = { open = false; onReload() })
            UrgentRule.entries.forEach { rule ->
                DropdownMenuItem(
                    text = { Text(tr("ด่วน = ", "Urgent = ") + rule.label, color = if (rule == urgentRule) C.accentText else C.text) },
                    onClick = { open = false; onUrgent(rule) },
                )
            }
            DropdownMenuItem(text = { Text(tr("เปลี่ยนโฟลเดอร์ตู้โน้ต", "Change vault folder")) }, onClick = { open = false; onVault() })
            // Language, font and text size live on the settings page.
            DropdownMenuItem(text = { Text(tr("ตั้งค่า", "Settings")) }, onClick = { open = false; onSettings() })
        }
    }
}

@Composable
private fun Welcome(onPick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(C.bg).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ProgressRing(0.7f, 64.dp, 7.dp)
        Text("Omni Task", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.headlineMedium, color = C.text)
        Text(
            tr(
                "เลือกโฟลเดอร์ตู้โน้ต Obsidian บนเครื่องนี้ แอปจะอ่านและแก้ไฟล์งานในโฟลเดอร์นั้นโดยตรง",
                "Pick your Obsidian vault folder on this phone. The app reads and edits the task files there directly.",
            ),
            Modifier.padding(top = 8.dp, bottom = 24.dp),
            color = C.muted,
        )
        PrimaryButton(tr("เลือกโฟลเดอร์ตู้โน้ต", "Choose vault folder"), onPick, Modifier.fillMaxWidth())
    }
}

/** Height the bottom bar covers when it floats over the page (nothing beside the side rail), for pages that fill the screen. */
val LocalDockClearance = staticCompositionLocalOf { 0.dp }

/** Lets a page take the whole screen while it shows: the bottom bar and the floating assistant button step aside (a counter, so pages can nest). */
val LocalFullPage = staticCompositionLocalOf<MutableIntState?> { null }

@Composable
fun FullPage() {
    val hidden = LocalFullPage.current ?: return
    DisposableEffect(hidden) {
        hidden.intValue++
        onDispose { hidden.intValue-- }
    }
}

/** Space the floating nav covers, so the last row of every list can scroll above it. */
val NavClearance: Dp
    @Composable get() = LocalNavClearance.current

/** Extra room at the end of a screen past the bottom bar, so the last card does not sit flush against it. */
val FabClearance = 12.dp

/** Bottom space the floating nav needs; on wide screens the nav sits at the side and only a margin is left. */
val LocalNavClearance = staticCompositionLocalOf { 100.dp }

/** Width from which the app lays out for landscape, tablets and the unfolded Find N: an opened task beside the page, and a rail on the left unless the shape is square (see [SQUARE_RATIO]). */
private const val WIDE_DP = 600

/** Longest side over the shortest, up to which a wide screen counts as square (the unfolded Find N is about 1.1) and keeps the bottom bar. */
private const val SQUARE_RATIO = 1.3f

/** Width from which an opened task shows beside the list instead of in a sheet. */
private const val TWO_PANE_DP = 720

/** Shown once after a crash: the report can be copied or shared, so the bug can be fixed from the real error. */
@Composable
private fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("แอปปิดไปเองเมื่อครั้งก่อน", "The app closed unexpectedly last time")) },
        text = {
            Column {
                Text(
                    tr("คัดลอกข้อความนี้ส่งให้ผู้พัฒนา จะช่วยแก้ได้ตรงจุด", "Copy this report and send it to the developer to get it fixed"),
                    color = C.text2, fontSize = TS.caption,
                )
                Text(
                    report,
                    Modifier.padding(top = 8.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(10.dp),
                    color = C.muted, fontSize = TS.micro,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report))
                runCatching {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, report),
                            tr("ส่งรายงาน", "Send report"),
                        ),
                    )
                }
                onDismiss()
            }) { Text(tr("คัดลอกและส่ง", "Copy and share"), color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) } },
    )
}


/**
 * Pulling the page down past its top reloads the vault and asks Google Calendar to sync, as in Apple's Calendar.
 * The spinner shows only for a reload the owner pulled for, not for the ones the app starts on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToReload(loading: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }
    val refreshing = pulled && loading
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { pulled = true; onRefresh() },
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pull, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter),
                containerColor = C.raised, color = C.accent,
            )
        },
    ) { content() }
}
