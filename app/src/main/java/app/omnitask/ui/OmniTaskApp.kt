package app.omnitask.ui

import android.content.ClipData
import android.content.ClipDescription
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import app.omnitask.data.TaskLine
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnitask.data.TaskLine.DateField
import app.omnitask.model.DateBucket
import app.omnitask.model.Priority
import app.omnitask.model.Quadrant
import app.omnitask.model.Status
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

    var screen by rememberSaveable { mutableStateOf(Screen.FOCUS) }
    var notifyOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    if (notifyOpen) {
        NotifySettingsScreen(state.notify, vm::setNotify, vm::refreshAlarms) { notifyOpen = false }
        return
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                title = {
                    Column {
                        Text(screen.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("th"))),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::reload) {
                        Icon(Icons.Default.Refresh, contentDescription = "โหลดใหม่", tint = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "เมนู", tint = colors.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.filters.showDone) "ซ่อนงานที่เสร็จแล้ว" else "แสดงงานที่เสร็จแล้ว") },
                            onClick = {
                                vm.setFilters(state.filters.copy(showDone = !state.filters.showDone))
                                menuOpen = false
                            },
                        )
                        HorizontalDivider(color = colors.outlineVariant)
                        UrgentRule.entries.forEach { rule ->
                            DropdownMenuItem(
                                text = { Text("ด่วน = " + rule.label) },
                                trailingIcon = {
                                    if (rule == state.urgentRule) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = {
                                    vm.setUrgentRule(rule)
                                    menuOpen = false
                                },
                            )
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                        DropdownMenuItem(
                            text = { Text("การแจ้งเตือน") },
                            onClick = {
                                menuOpen = false
                                notifyOpen = true
                            },
                        )
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
            Column {
                HorizontalDivider(color = colors.outlineVariant)
                NavigationBar(containerColor = colors.background, tonalElevation = 0.dp) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                    )
                    Screen.entries.forEach { s ->
                        NavigationBarItem(
                            selected = screen == s,
                            onClick = { screen = s },
                            icon = { Icon(s.icon, contentDescription = null) },
                            label = { Text(s.label) },
                            colors = itemColors,
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (screen == Screen.FOCUS) {
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.outlineVariant)
                }
                FocusScreen(state, vm) { editingKey = it.key }
                return@Column
            }
            FilterBar(state, vm::setFilters)
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.outlineVariant)
            }
            val tasks = state.visible.sortedWith(TASK_ORDER)
            if (screen == Screen.MATRIX) {
                MatrixView(tasks, state, vm::toggleDone, vm::moveToQuadrant) { editingKey = it.key }
            } else if (tasks.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ไม่มีงานตรงตัวกรอง", color = colors.outline)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(tasks, key = { it.key }) { task ->
                        TaskRow(task, state.today, onToggle = { vm.toggleDone(task) }, onOpen = { editingKey = task.key })
                        HorizontalDivider(Modifier.padding(start = 52.dp), color = colors.outlineVariant)
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
            knownTags = state.tags.filterNot { it.startsWith("remind-at-") },
            onAddTag = { vm.addTag(task, it) },
            onRemoveTag = { vm.removeTag(task, it) },
        )
    }
}

private enum class Screen(val label: String, val title: String, val icon: ImageVector) {
    FOCUS("โฟกัส", "โฟกัสวันนี้", Icons.Default.Home),
    LIST("รายการ", "งาน", Icons.AutoMirrored.Filled.List),
    MATRIX("Eisenhower", "Eisenhower", Icons.Default.Star),
}

@Composable
private fun Welcome(onPick: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Omni Task", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "เลือกโฟลเดอร์ตู้โน้ต Obsidian บนเครื่องนี้ แอปจะอ่านและแก้ไฟล์งานในโฟลเดอร์นั้นโดยตรง",
                Modifier.padding(top = 8.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onPick, shape = RoundedCornerShape(10.dp)) { Text("เลือกโฟลเดอร์ตู้โน้ต") }
        }
    }
}

@Composable
private fun FilterBar(state: UiState, onChange: (Filters) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val filters = state.filters
    val scoped = state.scoped
    val tabs: List<DateBucket?> = listOf<DateBucket?>(null) + DateBucket.entries
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(filters.bucket),
        edgePadding = 12.dp,
        containerColor = colors.background,
        contentColor = colors.primary,
        divider = { HorizontalDivider(color = colors.outlineVariant) },
    ) {
        tabs.forEach { bucket ->
            val count = if (bucket == null) scoped.size else scoped.count { it.bucket(state.today) == bucket }
            val selected = filters.bucket == bucket
            Tab(
                selected = selected,
                onClick = { onChange(filters.copy(bucket = bucket)) },
                selectedContentColor = colors.primary,
                unselectedContentColor = colors.onSurfaceVariant,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bucket?.label ?: "ทั้งหมด", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        Text(
                            " $count",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (bucket == DateBucket.OVERDUE && count > 0) colors.error else colors.outline,
                        )
                    }
                },
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PickerChip("Tag", filters.tag?.let { "#$it" }, state.tags.map { it to "#$it" }) { onChange(filters.copy(tag = it)) }
        PickerChip("โน้ต", filters.note, state.notes.map { it to it }) { onChange(filters.copy(note = it)) }
    }
}

@Composable
private fun PickerChip(label: String, selected: String?, options: List<Pair<String, String>>, onPick: (String?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: "$label: ทั้งหมด", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
            shape = RoundedCornerShape(8.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = colors.background,
                labelColor = colors.onSurfaceVariant,
                selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected != null,
                borderColor = colors.outlineVariant,
                selectedBorderColor = Color.Transparent,
            ),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("ทั้งหมด") }, onClick = { onPick(null); open = false })
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onPick(value); open = false })
            }
        }
    }
}

/** A round check that takes the priority's colour, like the Obsidian Tasks plugins. */
@Composable
private fun TaskCheck(task: Task, onToggle: () -> Unit, size: Int = 20) {
    val colors = MaterialTheme.colorScheme
    val tint = task.priority.tint ?: colors.outline
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(
                if (task.isOpen) {
                    Modifier
                        .background(if (task.status == Status.IN_PROGRESS) tint.copy(alpha = 0.25f) else Color.Transparent)
                        .border(1.5.dp, tint, CircleShape)
                } else {
                    Modifier.background(colors.outline)
                },
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (!task.isOpen) {
            Icon(Icons.Default.Check, contentDescription = "เสร็จแล้ว", tint = colors.background, modifier = Modifier.size((size - 6).dp))
        }
    }
}


@Composable
private fun TaskMeta(task: Task, today: LocalDate, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    val overdueColor = colors.error
    val muted = colors.onSurfaceVariant
    val items = buildList<Pair<String, Color>> {
        if (task.status == Status.IN_PROGRESS) add("กำลังทำ" to colors.primary)
        task.due?.let { d ->
            val text = if (d == today) "ครบวันนี้" else "ครบ " + d.format(SHORT_DATE)
            add(text to if (task.isOpen && d < today) overdueColor else muted)
        }
        if (!compact || task.due == null) {
            task.scheduled?.let { d ->
                val text = if (d == today) "นัดวันนี้" else "นัด " + d.format(SHORT_DATE)
                add(text to if (task.isOpen && task.due == null && d < today) overdueColor else muted)
            }
        }
        if (!compact) task.start?.let { add("เริ่ม " + it.format(SHORT_DATE) to muted) }
        if (task.recurrence != null) add("↻" to muted)
        if (!compact) task.tags.filterNot { it.startsWith("remind-at-") }.forEach { add("#$it" to colors.primary.copy(alpha = 0.8f)) }
    }
    if (items.isEmpty()) return
    Row(
        Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (text, color) ->
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    }
}

@Composable
internal fun TaskRow(task: Task, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { TaskCheck(task, onToggle) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (task.isOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                textDecoration = if (task.isOpen) null else TextDecoration.LineThrough,
            )
            TaskMeta(task, today, compact = false)
        }
    }
}

private val Quadrant.numeral get() = listOf("I", "II", "III", "IV")[ordinal]

/**
 * A 2×2 grid in the style of TickTick: each quadrant is a card with its own scrolling list.
 * Long-press a task and drag it up or down to change its importance; tap a header to open that quadrant full screen.
 */
@Composable
private fun MatrixView(
    tasks: List<Task>,
    state: UiState,
    onToggle: (Task) -> Unit,
    onMove: (Task, Quadrant) -> Unit,
    onOpen: (Task) -> Unit,
) {
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
        onDropKey = { key -> tasks.firstOrNull { it.key == key }?.let { onMove(it, quadrant) } },
        modifier = modifier,
    )

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val open = expanded
        if (open != null) {
            Cell(open, Modifier.fillMaxSize())
        } else {
            listOf(Quadrant.DO to Quadrant.PLAN, Quadrant.QUICK to Quadrant.LATER).forEach { (left, right) ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Cell(left, Modifier.weight(1f).fillMaxHeight())
                    Cell(right, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuadrantCard(
    quadrant: Quadrant,
    tasks: List<Task>,
    today: LocalDate,
    expanded: Boolean,
    onHeader: () -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (Task) -> Unit,
    onDropKey: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = quadrant.accent
    var hovering by remember { mutableStateOf(false) }
    val currentOnDrop by rememberUpdatedState(onDropKey)
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val key = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() ?: return false
                currentOnDrop(key)
                return true
            }
        }
    }

    Surface(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
            target = dropTarget,
        ),
        shape = RoundedCornerShape(14.dp),
        color = if (hovering) accent.copy(alpha = 0.10f).compositeOver(colors.surfaceContainerLow) else colors.surfaceContainerLow,
        border = BorderStroke(1.dp, if (hovering) accent.copy(alpha = 0.6f) else colors.outlineVariant),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onHeader).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Text(
                    quadrant.numeral,
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                )
                Text(
                    quadrant.label,
                    Modifier.weight(1f).padding(start = 6.dp),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(tasks.size.toString(), style = MaterialTheme.typography.labelMedium, color = colors.outline)
                Icon(
                    if (expanded) Icons.Default.Close else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "ย่อ" else "ขยาย",
                    tint = colors.outline,
                    modifier = Modifier.padding(start = 2.dp).size(16.dp),
                )
            }
            HorizontalDivider(color = colors.outlineVariant)
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ไม่มีงาน", style = MaterialTheme.typography.labelMedium, color = colors.outline)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTaskRow(task: Task, today: LocalDate, accent: Color, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .dragAndDropSource(
                drawDragDecoration = {
                    drawRoundRect(accent.copy(alpha = 0.35f), cornerRadius = CornerRadius(8.dp.toPx()))
                },
            ) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = {
                        startTransfer(DragAndDropTransferData(ClipData.newPlainText("task", task.key)))
                    },
                )
            }
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { TaskCheck(task, onToggle, size = 16) }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (task.isOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                textDecoration = if (task.isOpen) null else TextDecoration.LineThrough,
            )
            TaskMeta(task, today, compact = true)
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
    knownTags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf<DateField?>(null) }
    var addingTag by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(task.noteName, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            task.notes.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }

            SectionLabel("ความสำคัญ")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    val selected = task.priority == p
                    FilterChip(
                        selected = selected,
                        onClick = { onPriority(p) },
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = {
                            Box(Modifier.size(8.dp).background(p.tint ?: colors.outlineVariant, CircleShape))
                        },
                        label = { Text(p.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.background,
                            selectedContainerColor = colors.primaryContainer,
                            selectedLabelColor = colors.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = colors.outlineVariant,
                            selectedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }

            SectionLabel("Tag")
            val ownTags = task.tags.filterNot { it.startsWith("remind-at-") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ownTags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveTag(tag) },
                        label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "ลบ #$tag", modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = colors.background,
                            labelColor = colors.primary,
                            trailingIconColor = colors.outline,
                        ),
                        border = InputChipDefaults.inputChipBorder(enabled = true, selected = false, borderColor = colors.outlineVariant),
                    )
                }
                AssistChip(
                    onClick = { addingTag = true },
                    label = { Text("เพิ่ม", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    shape = RoundedCornerShape(8.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.background,
                        labelColor = colors.onSurfaceVariant,
                        leadingIconContentColor = colors.onSurfaceVariant,
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = colors.outlineVariant),
                )
            }

            SectionLabel("วันที่")
            DateRow("วันเริ่ม", task.start, { picking = DateField.START }) { onDate(DateField.START, null) }
            DateRow("วันนัดทำ", task.scheduled, { picking = DateField.SCHEDULED }) { onDate(DateField.SCHEDULED, null) }
            DateRow("วันครบกำหนด", task.due, { picking = DateField.DUE }) { onDate(DateField.DUE, null) }
        }
    }

    if (addingTag) {
        AddTagDialog(
            suggestions = knownTags.filterNot { it in task.tags },
            onDismiss = { addingTag = false },
            onAdd = {
                onAddTag(it)
                addingTag = false
            },
        )
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

/** Type a new tag, or tap one already used in the vault; the list narrows as you type. */
@Composable
private fun AddTagDialog(suggestions: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val query = TaskLine.normalizeTag(text)
    val matches = suggestions.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text("เพิ่ม Tag", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    prefix = { Text("#", color = colors.outline) },
                    placeholder = { Text("เช่น รอ/พี่เอ", color = colors.outline) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (query.isNotEmpty()) onAdd(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 240.dp)) {
                    items(matches) { tag ->
                        Text(
                            "#$tag",
                            Modifier.fillMaxWidth().clickable { onAdd(tag) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            color = colors.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(query) }, enabled = query.isNotEmpty()) { Text("เพิ่ม") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun DateRow(label: String, value: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(112.dp), color = colors.onSurfaceVariant)
        TextButton(onClick = onPick) {
            Text(value?.format(SHORT_DATE) ?: "ตั้งวันที่", color = if (value == null) colors.outline else colors.onSurface)
        }
        Spacer(Modifier.weight(1f))
        if (value != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "ล้าง$label", tint = colors.outline, modifier = Modifier.size(18.dp))
            }
        }
    }
}
