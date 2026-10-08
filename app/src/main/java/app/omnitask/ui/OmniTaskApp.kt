package app.omnitask.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnitask.model.Lang
import app.omnitask.model.UrgentRule
import app.omnitask.model.tr

enum class Screen(private val th: String, private val en: String, val icon: ImageVector) {
    FOCUS("โฟกัส", "Focus", Ic.focus),
    TASKS("งาน", "Tasks", Ic.tasks),
    VIEWS("มุมมอง", "Views", Ic.views),
    PROJECTS("โปรเจกต์", "Projects", Ic.folder),
    AI("ผู้ช่วย", "Assistant", Ic.spark),
    ;

    val label get() = tr(th, en)
}

@Composable
fun OmniTaskApp(vm: TaskViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
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

    // A widget or shortcut asked for quick add (or the assistant): open it once, then clear the request.
    LaunchedEffect(state.quickAdd) {
        state.quickAdd?.let { r ->
            if (r.assistant) screen = Screen.AI else adding = r
            vm.requestQuickAdd(null)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    if (notifyOpen) {
        NotifySettingsScreen(state.notify, vm::setNotify, vm::calendarChanged) { notifyOpen = false }
        return
    }

    if (settingsOpen) {
        // Reload after a language switch, so text the view model keeps (like the assistant's insight card) follows it.
        SettingsScreen(profile = state.profile, onSleepTimes = vm::setSleepTimes, onLanguageChange = vm::reload) { settingsOpen = false }
        return
    }

    BackHandler(enabled = screen != Screen.FOCUS) { screen = Screen.FOCUS }

    val open: (app.omnitask.model.Task) -> Unit = { editingKey = it.key }
    val menu: @Composable () -> Unit = {
        AppMenu(
            urgentRule = state.urgentRule,
            onUrgent = vm::setUrgentRule,
            onNotify = { notifyOpen = true },
            onSettings = { settingsOpen = true },
            onReload = vm::reload,
            onVault = { pickVault.launch(state.vault) },
        )
    }

    val width = LocalConfiguration.current.screenWidthDp
    val wide = width >= WIDE_DP
    val twoPane = width >= TWO_PANE_DP
    val editing = state.allTasks.firstOrNull { it.key == editingKey }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = C.accent, trackColor = C.bg)
            } else {
                Box(Modifier.height(2.dp))
            }
            // On wide screens reading columns stay a comfortable width; the views (Kanban, Gantt, calendar) use it all.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = if (screen == Screen.VIEWS) Dp.Unspecified else 760.dp)) {
                when (screen) {
                    Screen.FOCUS -> FocusScreen(state, vm, open, menu) { screen = Screen.AI }
                    Screen.TASKS -> TasksScreen(state, vm, open)
                    Screen.VIEWS -> ViewsScreen(state, vm, open)
                    Screen.PROJECTS -> ProjectsScreen(state, vm, open)
                    Screen.AI -> AssistantScreen(state, vm, open)
                }
                }
            }
        }
    }
    val addButton: @Composable (Modifier) -> Unit = { m ->
        if (screen != Screen.AI) {
            Box(
                m.size(56.dp).clip(RoundedCornerShape(18.dp)).background(C.accent).clickable { adding = QuickAddRequest() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.plus, tr("เพิ่มงาน", "New task"), tint = C.onAccent, modifier = Modifier.size(24.dp)) }
        }
    }

    if (wide) {
        // Landscape, tablet or unfolded: the nav becomes a rail on the left and an opened task sits on the right.
        CompositionLocalProvider(LocalNavClearance provides 24.dp) {
            Row(Modifier.fillMaxSize().background(C.bg).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                NavRail(screen) { screen = it }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    content()
                    addButton(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 18.dp))
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)) {
                        Snackbar(it, containerColor = C.raised, contentColor = C.text, shape = RoundedCornerShape(14.dp))
                    }
                }
                if (twoPane && editing != null) {
                    Box(Modifier.width(400.dp).fillMaxHeight().background(C.card).statusBarsPadding()) {
                        CompositionLocalProvider(LocalPane provides true) {
                            EditSheet(editing, state, vm, onDismiss = { editingKey = null }, onOpen = open)
                        }
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            content()
            FloatingNav(screen, { screen = it }, Modifier.align(Alignment.BottomCenter))
            addButton(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 92.dp))
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (screen != Screen.AI) 156.dp else 84.dp)) {
                Snackbar(it, containerColor = C.raised, contentColor = C.text, shape = RoundedCornerShape(14.dp))
            }
        }
    }

    if (editing != null && !(wide && twoPane)) {
        EditSheet(editing, state, vm, onDismiss = { editingKey = null }, onOpen = open)
    }
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
        QuickAddSheet(state, vm, r.voice, r.status, onAskAssistant = { vm.ask(it); screen = Screen.AI }) { adding = null }
    }
}

/** The same five destinations as a vertical pill on the left, for wide screens. */
@Composable
private fun NavRail(selected: Screen, onSelect: (Screen) -> Unit) {
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
            Screen.entries.forEach { s ->
                val on = s == selected
                Column(
                    Modifier.width(72.dp).height(itemH).clip(RoundedCornerShape(14.dp))
                        .background(if (on) C.accent else Color.Transparent).clickable { onSelect(s) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(s.icon, null, tint = if (on) C.onAccent else C.muted, modifier = Modifier.size(20.dp))
                    Text(s.label, fontSize = TS.micro, color = if (on) C.onAccent else C.muted, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/** The floating pill bar from the C+ mockup. */
@Composable
private fun FloatingNav(selected: Screen, onSelect: (Screen) -> Unit, modifier: Modifier) {
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(C.navBg)
            .border(1.dp, C.cardBorder, RoundedCornerShape(20.dp)).padding(6.dp),
    ) {
        Screen.entries.forEach { s ->
            val on = s == selected
            Column(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) C.accent else Color.Transparent).clickable { onSelect(s) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(s.icon, null, tint = if (on) C.onAccent else C.muted, modifier = Modifier.size(20.dp))
                Text(s.label, fontSize = TS.micro, color = if (on) C.onAccent else C.muted, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
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

/** Space the floating nav covers, so the last row of every list can scroll above it. */
val NavClearance: Dp
    @Composable get() = LocalNavClearance.current

/** Bottom space the floating nav needs; on wide screens the nav sits at the side and only a margin is left. */
val LocalNavClearance = staticCompositionLocalOf { 100.dp }

/** Width from which the app lays out for landscape, tablets and the unfolded Find N: nav rail on the left. */
private const val WIDE_DP = 600

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

