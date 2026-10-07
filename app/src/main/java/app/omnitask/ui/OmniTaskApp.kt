package app.omnitask.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnitask.data.TaskLine.DateField
import app.omnitask.model.DateBucket
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
import app.omnitask.model.Task
import app.omnitask.model.UrgentRule
import app.omnitask.model.bucket
import app.omnitask.model.quadrant
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale("th"))

private val TASK_ORDER = compareBy<Task>(
    { it.due ?: it.scheduled ?: it.start ?: LocalDate.MAX },
    { it.priority.ordinal },
)

@OptIn(ExperimentalMaterial3Api::class)
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

    var matrix by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (matrix) "Eisenhower" else "รายการงาน") },
                actions = {
                    IconButton(onClick = vm::reload) { Icon(Icons.Default.Refresh, contentDescription = "โหลดใหม่") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "เมนู") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.filters.showDone) "ซ่อนงานที่เสร็จแล้ว" else "แสดงงานที่เสร็จแล้ว") },
                            onClick = {
                                vm.setFilters(state.filters.copy(showDone = !state.filters.showDone))
                                menuOpen = false
                            },
                        )
                        HorizontalDivider()
                        UrgentRule.entries.forEach { rule ->
                            DropdownMenuItem(
                                text = { Text((if (rule == state.urgentRule) "● " else "○ ") + "ด่วน = " + rule.label) },
                                onClick = {
                                    vm.setUrgentRule(rule)
                                    menuOpen = false
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("เปลี่ยนโฟลเดอร์ตู้โน้ต") },
                            onClick = {
                                menuOpen = false
                                pickVault.launch(state.vault)
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !matrix,
                    onClick = { matrix = false },
                    icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                    label = { Text("รายการ") },
                )
                NavigationBarItem(
                    selected = matrix,
                    onClick = { matrix = true },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("Eisenhower") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            FilterBar(state, vm::setFilters)
            val tasks = state.visible.sortedWith(TASK_ORDER)
            if (tasks.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ไม่มีงานตรงตัวกรอง", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (matrix) {
                MatrixView(tasks, state, vm::toggleDone) { editingKey = it.key }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(tasks, key = { it.key }) { task ->
                        TaskRow(task, state.today, onToggle = { vm.toggleDone(task) }, onOpen = { editingKey = task.key })
                    }
                }
            }
        }
    }

    state.tasks.firstOrNull { it.key == editingKey }?.let { task ->
        EditSheet(
            task = task,
            onDismiss = { editingKey = null },
            onPriority = { vm.setPriority(task, it) },
            onDate = { field, value -> vm.setDate(task, field, value) },
        )
    }
}

@Composable
private fun Welcome(onPick: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Omni Task", style = MaterialTheme.typography.headlineMedium)
            Text(
                "เลือกโฟลเดอร์ตู้โน้ต Obsidian บนเครื่องนี้ แอปจะอ่านและแก้ไฟล์งานในโฟลเดอร์นั้นโดยตรง",
                Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onPick) { Text("เลือกโฟลเดอร์ตู้โน้ต") }
        }
    }
}

@Composable
private fun FilterBar(state: UiState, onChange: (Filters) -> Unit) {
    val filters = state.filters
    val scoped = state.scoped
    val tabs: List<DateBucket?> = listOf<DateBucket?>(null) + DateBucket.entries
    ScrollableTabRow(selectedTabIndex = tabs.indexOf(filters.bucket), edgePadding = 8.dp) {
        tabs.forEach { bucket ->
            val count = if (bucket == null) scoped.size else scoped.count { it.bucket(state.today) == bucket }
            Tab(
                selected = filters.bucket == bucket,
                onClick = { onChange(filters.copy(bucket = bucket)) },
                text = { Text("${bucket?.label ?: "ทั้งหมด"} $count", maxLines = 1) },
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PickerChip("Tag", filters.tag?.let { "#$it" }, state.tags.map { it to "#$it" }) { onChange(filters.copy(tag = it)) }
        PickerChip("โน้ต", filters.note, state.notes.map { it to it }) { onChange(filters.copy(note = it)) }
    }
}

@Composable
private fun PickerChip(label: String, selected: String?, options: List<Pair<String, String>>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: "$label: ทั้งหมด", maxLines = 1) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("ทั้งหมด") }, onClick = { onPick(null); open = false })
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onPick(value); open = false })
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = !task.isOpen, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                (task.priority.emoji?.plus(" ") ?: "") + task.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isOpen) null else TextDecoration.LineThrough,
            )
            val overdue = task.isOpen && task.bucket(today) == DateBucket.OVERDUE
            val meta = buildList {
                task.due?.let { add("📅 " + it.format(SHORT_DATE)) }
                task.scheduled?.let { add("⏳ " + it.format(SHORT_DATE)) }
                task.start?.let { add("🛫 " + it.format(SHORT_DATE)) }
                if (task.recurrence != null) add("🔁")
                task.tags.filterNot { it.startsWith("remind-at-") }.forEach { add("#$it") }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The four quadrants stacked top to bottom, so each keeps the full width of a phone screen. */
@Composable
private fun MatrixView(tasks: List<Task>, state: UiState, onToggle: (Task) -> Unit, onOpen: (Task) -> Unit) {
    val byQuadrant = tasks.groupBy { it.quadrant(state.today, state.urgentRule) }
    LazyColumn(Modifier.fillMaxSize()) {
        Quadrant.entries.forEach { quadrant ->
            val group = byQuadrant[quadrant].orEmpty()
            item(key = quadrant.name) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${quadrant.label}  ·  ${group.size}",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(group, key = { it.key }) { task ->
                TaskRow(task, state.today, onToggle = { onToggle(task) }, onOpen = { onOpen(task) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    task: Task,
    onDismiss: () -> Unit,
    onPriority: (Priority) -> Unit,
    onDate: (DateField, LocalDate?) -> Unit,
) {
    var picking by remember { mutableStateOf<DateField?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(
                task.noteName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            task.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }

            Text("Priority", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(
                        selected = task.priority == p,
                        onClick = { onPriority(p) },
                        label = { Text((p.emoji?.plus(" ") ?: "") + p.label) },
                    )
                }
            }

            DateRow("วันเริ่ม", task.start, { picking = DateField.START }) { onDate(DateField.START, null) }
            DateRow("วันนัดทำ", task.scheduled, { picking = DateField.SCHEDULED }) { onDate(DateField.SCHEDULED, null) }
            DateRow("วันครบกำหนด", task.due, { picking = DateField.DUE }) { onDate(DateField.DUE, null) }
        }
    }

    picking?.let { field ->
        val current = when (field) {
            DateField.START -> task.start
            DateField.SCHEDULED -> task.scheduled
            else -> task.due
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (current ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDate(field, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = null
                }) { Text("ตกลง") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("ยกเลิก") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateRow(label: String, value: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(112.dp))
        TextButton(onClick = onPick) { Text(value?.format(SHORT_DATE.withLocale(Locale("th"))) ?: "ตั้งวันที่") }
        Spacer(Modifier.weight(1f))
        if (value != null) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "ล้าง$label") }
        }
    }
}
