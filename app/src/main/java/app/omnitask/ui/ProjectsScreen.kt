package app.omnitask.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.omnitask.model.Branches
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.label
import app.omnitask.model.tr
import app.omnitask.time.*

private val RING get() = listOf(C.accent, C.amber, C.tealChip, C.blue, C.red)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    // Starred first, then the owner's own order; projects never placed keep the default order after them.
    val rank = state.projectOrder.withIndex().associate { it.value to it.index }
    // Parked branches still belong to their project, so the project (and the way back to it) never disappears.
    val projectTasks = state.tasks + state.parked
    val projects = Projects.build(projectTasks, state.today)
        .sortedWith(compareBy({ it.name !in state.starred }, { rank[it.name] ?: Int.MAX_VALUE }))
    var arranging by rememberSaveable { mutableStateOf(false) }
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val open = projects.firstOrNull { it.name == openName }
    var openListPath by rememberSaveable { mutableStateOf<String?>(null) }
    val openList = state.lists.firstOrNull { it.path == openListPath }
    var listCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingList by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Inside a project: the overview (null) or one of the views over just its tasks.
    var projectView by rememberSaveable { mutableStateOf<Mode?>(null) }
    var mapping by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = open != null || openList != null) {
        when {
            mapping -> mapping = false
            projectView != null -> projectView = null
            else -> { openName = null; openListPath = null; listCategory = null }
        }
    }
    LaunchedEffect(Unit) { vm.ensureStarterLists() }

    // Drag to arrange: the order being dragged, the dragged project and how far it has moved.
    val listState = rememberLazyListState()
    var dragOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    val shown = if (dragging != null) dragOrder.mapNotNull { n -> projects.firstOrNull { it.name == n } } else projects

    if (renaming && open != null) {
        RenameProjectDialog(open.name, vm.renameCount(open.name), { new -> vm.renameProject(open.name, new); openName = new; renaming = false }) { renaming = false }
    }
    if (creatingList) CreateListDialog({ n, i, c -> vm.createList(n, i, c); creatingList = false }) { creatingList = false }
    if (pickingIcon && openList != null) IconPickerDialog(openList.icon, { vm.updateList(openList, icon = it); pickingIcon = false }) { pickingIcon = false }

    if (open != null && mapping) {
        MindMapScreen(Branches.tree(open.name, projectTasks, state.branchStates), vm, onOpen) { mapping = false }
        return
    }
    val view = projectView
    if (open != null && view != null) {
        // A view needs the whole screen (Kanban and Gantt scroll sideways), so it replaces the list.
        val parked = state.parked.toSet()
        val mine = open.tasks.filter { it !in parked }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SquareButton(Ic.back, tr("กลับ", "Back"), { projectView = null })
                    Text(open.name, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.headlineSmall, color = C.text, maxLines = 1)
                }
                ProjectViewSwitch(view, Modifier.padding(top = 12.dp)) { projectView = it }
            }
            Box(Modifier.weight(1f)) { ViewBody(view, state, mine, mine.filter { !state.query.hideDone || it.isOpen }, vm, onOpen, scoped = true) }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = NavClearance + FabClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (open != null || openList != null) {
                    SquareButton(Ic.back, tr("กลับ", "Back"), { openName = null; openListPath = null; listCategory = null; projectView = null; mapping = false })
                    Box(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(open?.name ?: openList?.name ?: tr("โปรเจกต์/ลิสต์", "Projects/Lists"), style = MaterialTheme.typography.headlineSmall, color = C.text)
                }
                if (open != null) SquareButton(Ic.pen, tr("เปลี่ยนชื่อโปรเจกต์", "Rename project"), { renaming = true })
                if (open == null && openList == null && projects.size > 1) {
                    Text(
                        if (arranging) tr("เสร็จ", "Done") else tr("จัดลำดับ", "Arrange"),
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (arranging) C.accent else C.card)
                            .clickable { arranging = !arranging }.padding(horizontal = 14.dp, vertical = 9.dp),
                        color = if (arranging) C.onAccent else C.text2, fontSize = TS.caption,
                    )
                }
            }
        }

        if (openList != null) {
            listDetail(openList, state, vm, listCategory, { listCategory = it }, onOpen) { pickingIcon = true }
        } else if (open == null) {
            if (projects.isEmpty()) item { Card { Text(tr("ยังไม่มีงานที่ติด Tag", "No tagged tasks yet"), Modifier.padding(16.dp), color = C.muted) } }
            shown.forEachIndexed { i, p ->
                item(key = p.name) {
                    val lifted = dragging == p.name
                    Card(
                        Modifier.zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) dragOffset else 0f; shadowElevation = if (lifted) 16f else 0f }
                            .clickable(enabled = !arranging) { openName = p.name },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (arranging) {
                                    // The handle: drag it up or down; neighbours swap once the card passes their middle.
                                    Box(
                                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).pointerInput(p.name) {
                                            detectDragGestures(
                                                onDragStart = { dragOrder = projects.map { it.name }; dragging = p.name; dragOffset = 0f },
                                                onDragEnd = { vm.setProjectOrder(dragOrder); dragging = null; dragOffset = 0f },
                                                onDragCancel = { dragging = null; dragOffset = 0f },
                                            ) { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val items = listState.layoutInfo.visibleItemsInfo
                                                val at = dragOrder.indexOf(p.name)
                                                val next = dragOrder.getOrNull(at + 1)?.let { n -> items.firstOrNull { it.key == n } }
                                                val prev = dragOrder.getOrNull(at - 1)?.let { n -> items.firstOrNull { it.key == n } }
                                                if (dragOffset > 0 && next != null && dragOffset > next.size / 2f) {
                                                    dragOrder = dragOrder.toMutableList().also { it[at] = it[at + 1]; it[at + 1] = p.name }
                                                    dragOffset -= next.size + gap
                                                } else if (dragOffset < 0 && prev != null && -dragOffset > prev.size / 2f) {
                                                    dragOrder = dragOrder.toMutableList().also { it[at] = it[at - 1]; it[at - 1] = p.name }
                                                    dragOffset += prev.size + gap
                                                }
                                            }
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Ic.grip, tr("ลากเพื่อย้าย", "Drag to move"), tint = C.text2, modifier = Modifier.size(20.dp)) }
                                    Box(Modifier.width(6.dp))
                                }
                                ProgressRing(p.ratio, 52.dp, 5.dp, RING[i % RING.size], "${(p.ratio * 100).toInt()}%", 12)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, color = C.text)
                                    Text("${p.done}/${p.tasks.size}", color = C.muted, fontSize = TS.caption)
                                }
                                val on = p.name in state.starred
                                IconTap(Ic.star, if (on) tr("เอาดาวออก", "Unstar") else tr("ติดดาว", "Star"), if (on) C.amber else C.faint) { vm.toggleStar(p.name) }
                            }
                            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                p.next?.let { Pill(it.title, C.raised, C.text2, Ic.next) }
                                if (p.overdue > 0) Pill("${p.overdue}", C.redSoft, C.red, Ic.clock)
                                if (p.blocked.isNotEmpty()) Pill("${p.blocked.size}", C.amberSoft, C.amber, Ic.lock)
                            }
                        }
                    }
                }
            }
            if (!arranging) listCards(state, { openListPath = it.path }) { creatingList = true }
        } else {
            item(key = "views") { ProjectViewSwitch(null) { projectView = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${(open.ratio * 100).toInt()}%", tr("เสร็จแล้ว", "Done"), C.lime, Modifier.weight(1f))
                    StatCard("${open.overdue}", tr("เลยกำหนด", "Overdue"), if (open.overdue > 0) C.red else C.text, Modifier.weight(1f))
                    StatCard("${open.blocked.size}", tr("ติดรองานอื่น", "Blocked"), if (open.blocked.isNotEmpty()) C.amber else C.text, Modifier.weight(1f))
                }
            }
            item(key = "branches") { BranchSection(Branches.tree(open.name, projectTasks, state.branchStates)) { mapping = true } }
            // Open work in the owner's order (1, 2, 3...), then what is done; parked branches stay out.
            val parked = state.parked.toSet()
            val openTasks = vm.orderedProjectTasks(open.name, open.tasks.filter { it.isOpen && it.parent == null && it !in parked })
            val strict = open.name in state.strictProjects
            item(key = "strict") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { vm.setStrict(open.name, !strict, openTasks) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("ต้องทำตามลำดับ", "Do in order"), color = C.text, fontSize = TS.body)
                    }
                    OnOff(strict)
                }
            }
            if (openTasks.isNotEmpty()) {
                item(key = "ordered") {
                    Card {
                        SectionHead(tr("ลำดับงาน", "Order"), C.accentText, "${openTasks.size}")
                        OrderedTaskList(
                            openTasks,
                            onToggle = { vm.toggleDone(it) },
                            onOpen = onOpen,
                            onReorder = { vm.setProjectTaskOrder(open.name, it) },
                            meta = { t ->
                                val pos = openTasks.indexOf(t)
                                val waiting = t in open.blocked
                                when {
                                    waiting && strict && pos > 0 -> tr("รองานข้อ $pos ก่อน", "Waits for task $pos")
                                    waiting -> tr("รอ ", "Waiting on ") + (Projects.waitingOn(t, state.tasks) ?: "")
                                    else -> listOfNotNull(
                                        if (pos == 0) tr("ทำถัดไป", "Next") else null,
                                        (t.due ?: t.scheduled)?.format(SHORT_DATE),
                                        state.progressOf(t)?.let { (d, n) -> "$d/$n" },
                                    ).joinToString(", ")
                                }
                            },
                            locked = { it in open.blocked },
                        )
                        Box(Modifier.height(4.dp))
                    }
                }
            }
            val finished = open.tasks.filter { !it.isOpen && it.parent == null }.sortedByDescending { it.done }
            if (finished.isNotEmpty()) {
                item(key = "finished") {
                    Card {
                        SectionHead(tr("เสร็จแล้ว", "Done"), C.text2, "${finished.size}")
                        finished.forEachIndexed { i, t ->
                            if (i > 0) Divider(start = 48.dp)
                            TaskRow(t, state.today, { vm.toggleDone(t) }, { onOpen(t) })
                        }
                        Box(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = TS.stat, fontWeight = FontWeight.Medium, color = color)
            Text(label, fontSize = TS.caption, color = C.muted)
        }
    }
}

@Composable
private fun IconTap(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Overview, or one of the views over just this project's tasks. */
@Composable
private fun ProjectViewSwitch(selected: Mode?, modifier: Modifier = Modifier, onSelect: (Mode?) -> Unit) {
    Segmented(listOf<Pair<Mode?, String>>(null to tr("ภาพรวม", "Overview")) + Mode.entries.map { it to it.label }, selected, onSelect, modifier.fillMaxWidth())
}
