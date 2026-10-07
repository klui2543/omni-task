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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnitask.model.UrgentRule

enum class Screen(val label: String, val icon: ImageVector) {
    FOCUS("โฟกัส", Ic.focus),
    TASKS("งาน", Ic.tasks),
    VIEWS("มุมมอง", Ic.views),
    PROJECTS("โปรเจกต์", Ic.folder),
    AI("ผู้ช่วย", Ic.spark),
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
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    if (notifyOpen) {
        NotifySettingsScreen(state.notify, vm::setNotify, vm::refreshAlarms) { notifyOpen = false }
        return
    }

    BackHandler(enabled = screen != Screen.FOCUS) { screen = Screen.FOCUS }

    val open: (app.omnitask.model.Task) -> Unit = { editingKey = it.key }
    val menu: @Composable () -> Unit = {
        AppMenu(
            urgentRule = state.urgentRule,
            onUrgent = vm::setUrgentRule,
            onNotify = { notifyOpen = true },
            onReload = vm::reload,
            onVault = { pickVault.launch(state.vault) },
        )
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = C.accent, trackColor = C.bg)
            } else {
                Box(Modifier.height(2.dp))
            }
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.FOCUS -> FocusScreen(state, vm, open, menu) { screen = Screen.AI }
                    Screen.TASKS -> TasksScreen(state, vm, open)
                    Screen.VIEWS -> ViewsScreen(state, vm, open)
                    Screen.PROJECTS -> ProjectsScreen(state, vm, open)
                    Screen.AI -> AssistantScreen(state, vm, open)
                }
            }
        }
        FloatingNav(screen, { screen = it }, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)) {
            Snackbar(it, containerColor = C.raised, contentColor = C.text, shape = RoundedCornerShape(14.dp))
        }
    }

    state.tasks.firstOrNull { it.key == editingKey }?.let { task ->
        EditSheet(task, state, vm, onDismiss = { editingKey = null })
    }
    state.pendingImage?.let { AttachChoiceDialog(it, vm::resolvePendingImage) }
}

/** The floating pill bar from the C+ mockup. */
@Composable
private fun FloatingNav(selected: Screen, onSelect: (Screen) -> Unit, modifier: Modifier) {
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(Color(0xF0171A23))
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
                Text(s.label, fontSize = 11.sp, color = if (on) C.onAccent else C.muted, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun AppMenu(urgentRule: UrgentRule, onUrgent: (UrgentRule) -> Unit, onNotify: () -> Unit, onReload: () -> Unit, onVault: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SquareButton(Icons.Default.MoreVert, "เมนู", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = C.raised) {
            DropdownMenuItem(text = { Text("การแจ้งเตือน") }, onClick = { open = false; onNotify() })
            DropdownMenuItem(text = { Text("โหลดใหม่") }, onClick = { open = false; onReload() })
            UrgentRule.entries.forEach { rule ->
                DropdownMenuItem(
                    text = { Text("ด่วน = " + rule.label, color = if (rule == urgentRule) C.accentText else C.text) },
                    onClick = { open = false; onUrgent(rule) },
                )
            }
            DropdownMenuItem(text = { Text("เปลี่ยนโฟลเดอร์ตู้โน้ต") }, onClick = { open = false; onVault() })
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
            "เลือกโฟลเดอร์ตู้โน้ต Obsidian บนเครื่องนี้ แอปจะอ่านและแก้ไฟล์งานในโฟลเดอร์นั้นโดยตรง",
            Modifier.padding(top = 8.dp, bottom = 24.dp),
            color = C.muted,
        )
        PrimaryButton("เลือกโฟลเดอร์ตู้โน้ต", onPick, Modifier.fillMaxWidth())
    }
}

/** Space the floating nav covers, so the last row of every list can scroll above it. */
val NavClearance = 100.dp
