package app.omnitask.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
            if (matrix) {
                MatrixView(tasks, state, vm::toggleDone) { editingKey = it.key }
            } else if (tasks.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ไม่มีงานตรงตัวกรอง", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

private val Quadrant.accent
    get() = when (this) {
        Quadrant.DO -> Color(0xFFE5484D)
        Quadrant.PLAN -> Color(0xFFF59E0B)
        Quadrant.QUICK -> Color(0xFF3B82F6)
        Quadrant.LATER -> Color(0xFF22A06B)
    }

private val Quadrant.numeral get() = listOf("I", "II", "III", "IV")[ordinal]

/**
 * A 2×2 grid in the style of TickTick: each quadrant is a tinted card with its own scrolling list.
 * Tapping a quadrant's header opens it full screen; back returns to the grid.
 */
@Composable
private fun MatrixView(tasks: List<Task>, state: UiState, onToggle: (Task) -> Unit, onOpen: (Task) -> Unit) {
    val byQuadrant = tasks.groupBy { it.quadrant(state.today, state.urgentRule) }
    var expanded by rememberSaveable { mutableStateOf<Quadrant?>(null) }
    BackHandler(enabled = expanded != null) { expanded = null }

    @Composable
    fun Cell(quadrant: Quadrant, modifier: Modifier) = QuadrantCard(
        quadrant = quadrant,
        tasks = byQuadrant[quadrant].orEmpty(),
        today = state.today,
        expanded = expanded == quadrant,
        onHeader = { expanded = if (expanded == quadrant) null else quadrant },
        onToggle = onToggle,
        onOpen = onOpen,
        modifier = modifier,
    )

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val open = expanded
        if (open != null) {
            Cell(open, Modifier.fillMaxSize())
        } else {
            listOf(Quadrant.DO to Quadrant.PLAN, Quadrant.QUICK to Quadrant.LATER).forEach { (left, right) ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cell(left, Modifier.weight(1f).fillMaxHeight())
                    Cell(right, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun QuadrantCard(
    quadrant: Quadrant,
    tasks: List<Task>,
    today: LocalDate,
    expanded: Boolean,
    onHeader: () -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (Task) -> Unit,
    modifier: Modifier,
) {
    val accent = quadrant.accent
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onHeader).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(quadrant.numeral, color = accent, fontWeight = FontWeight.Bold)
                Text(
                    quadrant.label,
                    Modifier.weight(1f).padding(start = 6.dp),
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tasks.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Default.Close else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "ย่อ" else "ขยาย",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp).size(18.dp),
                )
            }
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ไม่มีงาน", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(tasks, key = { it.key }) { task ->
                        if (expanded) {
                            TaskRow(task, today, onToggle = { onToggle(task) }, onOpen = { onOpen(task) })
                        } else {
                            CompactTaskRow(task, today, accent, onToggle = { onToggle(task) }, onOpen = { onOpen(task) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTaskRow(task: Task, today: LocalDate, accent: Color, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Checkbox(
                checked = !task.isOpen,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(uncheckedColor = accent, checkedColor = accent),
                modifier = Modifier.padding(4.dp).size(20.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 3.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isOpen) null else TextDecoration.LineThrough,
            )
            (task.due ?: task.scheduled)?.let { date ->
                val overdue = task.isOpen && date < today
                Text(
                    if (date == today) "วันนี้" else date.format(SHORT_DATE),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
