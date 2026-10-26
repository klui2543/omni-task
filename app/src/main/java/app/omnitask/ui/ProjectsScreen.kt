package app.omnitask.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
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
import app.omnitask.model.Folders
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.Ygdrasil
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
    val built = remember(state.tasks, state.parked, state.today, state.projectTags) { Projects.build(projectTasks, state.today) }
    // Tags that are not projects; the owner makes one a project here.
    val others = remember(state.tasks, state.parked, state.projectTags) { Projects.others(projectTasks) }
    val projects = built.sortedWith(compareBy({ it.name !in state.starred }, { rank[it.name] ?: Int.MAX_VALUE }))
    var arranging by rememberSaveable { mutableStateOf(false) }
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val open = projects.firstOrNull { it.name == openName }
    var openListPath by rememberSaveable { mutableStateOf<String?>(null) }
    val openList = state.lists.firstOrNull { it.path == openListPath }
    var listCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingList by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    var deletingList by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Inside a project: the overview (null) or one of the views over just its tasks.
    var projectView by rememberSaveable { mutableStateOf<Mode?>(null) }
    var mapping by rememberSaveable { mutableStateOf(false) }
    // Project Ygdrasil, with its filter and folded limbs kept while the tab is open.
    var world by rememberSaveable { mutableStateOf(false) }
    var ygFilter by rememberSaveable { mutableStateOf("") }
    var ygFolded by rememberSaveable { mutableStateOf("") }
    // A project opened from Ygdrasil: back goes back to the tree, not to the project cards.
    var fromWorld by rememberSaveable { mutableStateOf(false) }
    // The desktop (owner's pick, 2026-10-26): a folder inside a project ("Siriraj/ตารางเวรเภสัช"), and the folders
    // "อื่นๆ" ("other") and "List" ("list") that hold what is not pinned.
    var folderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var shelf by rememberSaveable { mutableStateOf<String?>(null) }
    val pinned = if (state.starred.isEmpty()) projects.map { it.name }.toSet() else state.starred
    fun folderUp() { val p = folderPath ?: return; folderPath = p.substringBeforeLast('/').takeIf { '/' in it } }
    // A folder belongs to the project it was opened in: another project (or none) starts at its own page. Keyed on the
    // name, so a project missing for a moment while the tasks reload keeps its folder.
    LaunchedEffect(openName) { if (folderPath?.startsWith("$openName/") != true) folderPath = null }
    // A tag in "อื่นๆ" opened to see its tasks, before it is made a project.
    var tagShown by remember { mutableStateOf<String?>(null) }
    fun backToWorld() { fromWorld = false; mapping = false; projectView = null; openName = null; openListPath = null; listCategory = null; world = true }
    BackHandler(enabled = open != null || openList != null || world || shelf != null) {
        when {
            world -> world = false
            mapping && fromWorld -> backToWorld()
            mapping -> mapping = false
            projectView != null -> projectView = null
            folderPath != null -> folderUp()
            fromWorld -> backToWorld()
            open == null && openList == null -> shelf = null
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
    tagShown?.let { tag ->
        TagTasksSheet(
            state, tag, canMakeProject = tag != app.omnitask.model.Focus.WAITING_TAG,
            onOpen = { tagShown = null; onOpen(it) },
            onToggle = { vm.toggleDone(it) },
            onMakeProject = { vm.setProject(tag, true); tagShown = null },
        ) { tagShown = null }
    }
    if (creatingList) CreateListDialog({ n, i, c -> vm.createList(n, i, c); creatingList = false }) { creatingList = false }
    if (pickingIcon && openList != null) IconPickerDialog(openList.icon, { vm.updateList(openList, icon = it); pickingIcon = false }) { pickingIcon = false }
    if (deletingList && openList != null) {
        val own = state.itemsOf(openList).count { it.list != null }
        val pulled = state.itemsOf(openList).size - own
        DeleteListDialog(openList, own, pulled, {
            vm.deleteList(openList); deletingList = false; openListPath = null; listCategory = null
            if (fromWorld) backToWorld()
        }) { deletingList = false }
    }

    if (world && open == null && openList == null) {
        YgdrasilScreen(
            state, vm,
            filter = Ygdrasil.Filter.decode(ygFilter), onFilter = { ygFilter = it.encode() },
            folded = ygFolded.split('\n').filter { it.isNotEmpty() }.toSet(), onFolded = { ygFolded = it.joinToString("\n") },
            onOpen = onOpen,
            onOpenProject = { name -> world = false; fromWorld = true; openName = name; projectView = null; mapping = true },
            onBack = { world = false },
            onOpenList = { l -> world = false; fromWorld = true; openListPath = l.path },
        )
        return
    }
    if (open != null && mapping) {
        MindMapScreen(Branches.tree(open.name, projectTasks, state.branchStates), vm, onOpen) { if (fromWorld) backToWorld() else mapping = false }
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
                if (open != null || openList != null || shelf != null) {
                    SquareButton(Ic.back, tr("กลับ", "Back"), {
                        when {
                            folderPath != null -> folderUp()
                            fromWorld -> backToWorld()
                            open == null && openList == null -> shelf = null
                            else -> { openName = null; openListPath = null; listCategory = null; projectView = null; mapping = false }
                        }
                    })
                    Box(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    val shelfName = when (shelf) { "other" -> tr("อื่นๆ", "Other"); "list" -> "List"; else -> null }
                    Text(
                        folderPath?.takeIf { open != null }?.substringAfterLast('/') ?: open?.name ?: openList?.name ?: shelfName ?: tr("โปรเจกต์", "Projects"),
                        style = MaterialTheme.typography.headlineSmall, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (open != null && folderPath != null) {
                        FolderCrumbs(folderPath ?: open.name, { folderPath = null; openName = null; shelf = null }) { to -> folderPath = to.takeIf { '/' in it } }
                    } else Text(
                        when {
                            open != null -> tr("ติ๊กงานต้นทางเพื่อปลดล็อกงานที่รออยู่", "Tick the blocking tasks to unlock the ones waiting")
                            openList != null -> tr("แตะวงกลมเพื่อติ๊กว่าทำหรือดูแล้ว", "Tap the ring to tick what you've done or watched")
                            shelf == "other" -> tr("โปรเจกต์ที่ไม่ได้ปักหมุด แท็กเดี่ยว และป้าย", "Projects not pinned, lone tags and labels")
                            shelf == "list" -> tr("รายการทั้งหมดอยู่ในโฟลเดอร์นี้", "Every list lives in this folder")
                            arranging -> tr("ติดดาวให้ขึ้นบนสุด กดจัดลำดับเพื่อลากขึ้นลง", "Star to keep on top, Arrange to drag")
                            else -> tr("หมุดอยู่บนเดสก์ท็อป ที่เหลืออยู่ในโฟลเดอร์ อื่นๆ", "Pinned folders stay on the desktop; the rest are in Other")
                        },
                        color = C.muted, fontSize = TS.caption,
                    )
                }
                if (open != null && folderPath == null) {
                    val on = open.name in pinned
                    Text(
                        if (on) tr("ถอดหมุด", "Unpin") else tr("ปักหมุด", "Pin"),
                        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(12.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp))
                            .clickable { vm.setPinned(open.name, !on, projects.map { it.name }) }.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = C.text2, fontSize = TS.caption,
                    )
                }
                if (open != null && folderPath == null) SquareButton(Ic.pen, tr("เปลี่ยนชื่อโปรเจกต์", "Rename project"), { renaming = true })
                if (openList != null) SquareButton(Ic.trash, tr("ลบรายการนี้", "Delete this list"), { deletingList = true })
                if (open == null && openList == null && shelf == null && projects.size > 1) {
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
        } else if (open == null && shelf == "list") {
            listCards(state, { openListPath = it.path }) { creatingList = true }
        } else if (open == null && shelf == "other") {
            val unpinned = projects.filter { it.name !in pinned }
            val loose = others.filter { !Folders.isLabel(it.first) }
            // #รอ is a label too, though it never names a project.
            val waits = (state.tasks + state.parked).count { t -> t.status != app.omnitask.model.Status.CANCELLED && t.tags.any { it == app.omnitask.model.Focus.WAITING_TAG || it.startsWith(app.omnitask.model.Focus.WAITING_TAG + "/") } }
            val labels = others.filter { Folders.isLabel(it.first) } + listOfNotNull(if (waits > 0) app.omnitask.model.Focus.WAITING_TAG to waits else null)
            if (unpinned.isNotEmpty()) item(key = "other projects") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeskHead(tr("โปรเจกต์ที่ไม่ได้ปักหมุด", "Projects not pinned"))
                    TileGrid(unpinned.map { p -> Tile(p.name, p.name, "${p.done}/${p.tasks.size}", folderTint(p.name), { openName = p.name }, late = p.overdue) })
                }
            }
            // Tags that are not projects yet; a tap shows their tasks, with the way to make one a project.
            if (loose.isNotEmpty()) item(key = "other loose") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeskHead(tr("แท็กเดี่ยว", "Lone tags"))
                    TileGrid(loose.map { (tag, n) -> Tile(tag, tag, tr("$n งาน", "$n tasks"), folderTint(tag), { tagShown = tag }, dashed = true) })
                }
            }
            if (labels.isNotEmpty()) item(key = "other labels") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeskHead(tr("ป้าย (ไม่ใช่โฟลเดอร์ ติดได้หลายอัน)", "Labels (not folders, any number per task)"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.forEach { (tag, n) -> Chip("#$tag  $n", false, { tagShown = tag }) }
                    }
                }
            }
            if (unpinned.isEmpty() && loose.isEmpty() && labels.isEmpty()) item(key = "other empty") {
                Text(tr("ยังไม่มีอะไรในโฟลเดอร์นี้", "Nothing in this folder yet"), Modifier.padding(6.dp), color = C.muted, fontSize = TS.body)
            }
        } else if (open == null) {
            if (projects.isEmpty()) {
                item {
                    Card {
                        Text(
                            if (others.isEmpty()) tr("ยังไม่มีงานที่ติด Tag", "No tagged tasks yet")
                            else tr("ยังไม่มีโปรเจกต์ เปิดโฟลเดอร์ อื่นๆ แล้วเลือกแท็กเพื่อทำเป็นโปรเจกต์", "No projects yet. Open Other and pick a tag to make it one."),
                            Modifier.padding(16.dp), color = C.muted,
                        )
                    }
                }
            }
            if (projects.isNotEmpty() && !arranging) item(key = "ygdrasil entry") { YgdrasilEntry { world = true } }
            if (!arranging) {
                val pins = projects.filter { it.name in pinned }
                val unpinnedCount = projects.count { it.name !in pinned } + others.count { !Folders.isLabel(it.first) }
                val hasLabels = others.any { Folders.isLabel(it.first) }
                item(key = "desk pins") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeskHead(tr("ปักหมุด", "Pinned"))
                        if (pins.isEmpty()) Text(
                            tr("ยังไม่มีโฟลเดอร์ที่ปักหมุด เปิดโฟลเดอร์ใน อื่นๆ แล้วกด ปักหมุด", "Nothing pinned yet. Open a folder in Other and tap Pin"),
                            color = C.faint, fontSize = TS.caption,
                        )
                        else TileGrid(pins.map { p -> Tile(p.name, p.name, "${p.done}/${p.tasks.size}", folderTint(p.name), { openName = p.name }, pinned = true, late = p.overdue) })
                    }
                }
                item(key = "desk folders") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeskHead(tr("โฟลเดอร์", "Folders"))
                        TileGrid(
                            listOf(
                                Tile(
                                    "other", tr("อื่นๆ", "Other"),
                                    tr("$unpinnedCount โฟลเดอร์", "$unpinnedCount folders") + if (hasLabels) tr(" + ป้าย", " + labels") else "",
                                    C.muted, { shelf = "other" }, mark = "⋯",
                                ),
                                Tile("list", "List", tr("${state.lists.size} รายการ", "${state.lists.size} lists"), C.accent, { shelf = "list" }, mark = "☰"),
                            ),
                        )
                    }
                }
                // What the pinned folders hold next, as the project rows showed it.
                if (pins.isNotEmpty()) item(key = "desk next") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DeskHead(tr("ถัดไปของที่ปักหมุด", "Next in the pinned folders"))
                        pins.forEach { p ->
                            val tint = folderTint(p.name)
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openName = p.name }.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FolderGlyph(tint, 18.dp)
                                    Text(p.name, Modifier.weight(1f).padding(start = 10.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${p.done}/${p.tasks.size}", Modifier.padding(end = 8.dp), color = C.muted, fontSize = TS.caption)
                                }
                                Box(Modifier.padding(start = 28.dp, end = 12.dp, top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.divider)) {
                                    Box(Modifier.fillMaxWidth(p.ratio.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                                }
                                Row(Modifier.padding(start = 28.dp, top = 6.dp, end = 12.dp)) {
                                    Text(
                                        p.next?.let { tr("ถัดไป: ", "Next: ") + it.title } ?: tr("ไม่มีงานค้าง", "Nothing open"),
                                        Modifier.weight(1f, fill = false), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (p.overdue > 0) Text(tr("  เลยกำหนดจริง ${p.overdue}", "  ${p.overdue} past deadline"), color = C.red, fontSize = TS.caption, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (arranging) shown.forEachIndexed { i, p ->
                item(key = p.name) {
                    val lifted = dragging == p.name
                    // The new look: a row, not a card. Name, steps done, a thin bar, and the next step.
                    if (NewLook.on && !arranging) {
                        val tint = RING[i % RING.size]
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openName = p.name }.padding(start = 4.dp, end = 0.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                                Text(p.name, Modifier.weight(1f).padding(start = 10.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.done}/${p.tasks.size}", color = C.muted, fontSize = TS.caption)
                                val on = p.name in pinned
                                IconTap(Ic.star, if (on) tr("เอาดาวออก", "Unstar") else tr("ติดดาว", "Star"), if (on) C.amber else C.faint) { vm.setPinned(p.name, !on, projects.map { it.name }) }
                            }
                            Box(Modifier.padding(start = 18.dp, end = 12.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.divider)) {
                                Box(Modifier.fillMaxWidth(p.ratio.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                            }
                            Row(Modifier.padding(start = 18.dp, top = 6.dp, end = 12.dp)) {
                                Text(
                                    p.next?.let { tr("ถัดไป: ", "Next: ") + it.title } ?: tr("ไม่มีงานค้าง", "Nothing open"),
                                    Modifier.weight(1f, fill = false), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (p.overdue > 0) Text(tr("  เลยกำหนดจริง ${p.overdue}", "  ${p.overdue} past deadline"), color = C.red, fontSize = TS.caption, maxLines = 1)
                            }
                            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(C.divider))
                        }
                    } else Card(
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
                                    Text(tr("เสร็จ ${p.done} จาก ${p.tasks.size} งาน", "${p.done} of ${p.tasks.size} done"), color = C.muted, fontSize = TS.caption)
                                }
                                val on = p.name in pinned
                                IconTap(Ic.star, if (on) tr("เอาดาวออก", "Unstar") else tr("ติดดาว", "Star"), if (on) C.amber else C.faint) { vm.setPinned(p.name, !on, projects.map { it.name }) }
                            }
                            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                p.next?.let { Pill(tr("ถัดไป: ${it.title}", "Next: ${it.title}"), C.raised, C.text2) }
                                if (p.overdue > 0) Pill(tr("เลยกำหนด ${p.overdue}", "Overdue ${p.overdue}"), C.redSoft, C.red)
                                if (p.blocked.isNotEmpty()) Pill(tr("ติดรองานอื่น ${p.blocked.size}", "Blocked ${p.blocked.size}"), C.amberSoft, C.amber)
                            }
                        }
                    }
                }
            }
        } else if (folderPath != null) {
            // A folder inside a project: its folders, then its own open work, then the work further in.
            val tree = Branches.tree(open.name, projectTasks, state.branchStates)
            val node = tree.flatten().firstOrNull { it.tag == folderPath }
            if (node == null) item(key = "folder gone") {
                Text(tr("ไม่พบโฟลเดอร์นี้แล้ว อาจถูกย้ายหรือเปลี่ยนชื่อ", "This folder is gone; it may have been moved or renamed"), Modifier.padding(6.dp), color = C.muted, fontSize = TS.body)
            } else {
                item(key = "folder progress") {
                    val tint = folderTint(node.tag)
                    Column {
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.divider)) {
                            val r = if (node.all.isEmpty()) 0f else node.done.toFloat() / node.all.size
                            Box(Modifier.fillMaxWidth(r.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                        }
                        Text(
                            tr("ทำแล้ว ${node.done} จาก ${node.all.size}", "${node.done} of ${node.all.size} done") +
                                if (node.state == Branches.State.PARKED) tr(", พับเก็บอยู่", ", parked") else "",
                            Modifier.padding(top = 6.dp), color = C.muted, fontSize = TS.caption,
                        )
                    }
                }
                if (node.children.isNotEmpty()) item(key = "folder kids") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeskHead(tr("โฟลเดอร์ย่อย", "Folders inside"))
                        TileGrid(node.children.map { n -> subTile(n, state.today) { folderPath = n.tag } }, small = true)
                    }
                }
                val own = node.own.filter { it.isOpen && it.parent == null }
                item(key = "folder own") {
                    Card {
                        SectionHead(tr("งานในโฟลเดอร์นี้", "In this folder"), C.accentText, "${own.size}")
                        if (own.isEmpty()) Text(
                            tr("ไม่มีงานค้างตรงนี้ เปิดโฟลเดอร์ย่อยเพื่อดูงานข้างใน", "Nothing open here. Open a folder inside to see its work"),
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), color = C.muted, fontSize = TS.caption,
                        )
                        own.forEachIndexed { i, t ->
                            if (i > 0) Divider(start = 48.dp)
                            TaskRow(t, state.today, { vm.toggleDone(t) }, { onOpen(t) })
                        }
                        Box(Modifier.height(4.dp))
                    }
                }
                val deeper = node.all.filter { it.isOpen && it.parent == null && it !in node.own }
                if (deeper.isNotEmpty()) item(key = "folder deeper") {
                    Card {
                        SectionHead(tr("ในโฟลเดอร์ย่อย", "In the folders inside"), C.text2, "${deeper.size}")
                        deeper.forEachIndexed { i, t ->
                            if (i > 0) Divider(start = 48.dp)
                            TaskRow(t, state.today, { vm.toggleDone(t) }, { onOpen(t) })
                        }
                        Box(Modifier.height(4.dp))
                    }
                }
                item(key = "folder map") { BranchSection(tree) { mapping = true } }
            }
        } else {
            item(key = "views") { ProjectViewSwitch(null) { projectView = it } }
            if (!NewLook.on) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${(open.ratio * 100).toInt()}%", tr("เสร็จแล้ว", "Done"), C.lime, Modifier.weight(1f))
                    StatCard("${open.overdue}", tr("เลยกำหนด", "Overdue"), if (open.overdue > 0) C.red else C.text, Modifier.weight(1f))
                    StatCard("${open.blocked.size}", tr("ติดรองานอื่น", "Blocked"), if (open.blocked.isNotEmpty()) C.amber else C.text, Modifier.weight(1f))
                }
            }
            val tree = Branches.tree(open.name, projectTasks, state.branchStates)
            if (tree.children.isNotEmpty()) item(key = "subfolders") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeskHead(tr("โฟลเดอร์ย่อย", "Folders inside"))
                    TileGrid(tree.children.map { n -> subTile(n, state.today) { folderPath = n.tag } }, small = true)
                }
            }
            item(key = "branches") { BranchSection(tree) { mapping = true } }
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
                        Text(
                            if (strict) tr("งานถัดไปรองานก่อนหน้า (เขียนรหัสงานลงไฟล์ให้ปลั๊กอิน Tasks เห็นด้วย)", "Each task waits for the one before (written as task ids for the Tasks plugin)")
                            else tr("ปิดอยู่: ลำดับเป็นแค่คำแนะนำ ทำข้ามได้", "Off: the order is a suggestion, skip ahead freely"),
                            color = C.muted, fontSize = TS.caption,
                        )
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
                                        state.progressOf(t)?.let { (d, n) -> tr("งานย่อย $d/$n", "Subtasks $d/$n") },
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
            item(key = "not a project") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { vm.setProject(open.name, false); openName = null }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Column {
                        Text(tr("เลิกเป็นโปรเจกต์", "Stop being a project"), color = C.text, fontSize = TS.body)
                        Text(
                            tr("#${open.name} กลับเป็นแท็กธรรมดา งานยังอยู่ครบ", "#${open.name} goes back to a plain tag; its tasks stay as they are"),
                            color = C.muted, fontSize = TS.caption,
                        )
                    }
                }
            }
            item {
                Text(
                    tr(
                        "Milestone จะมาพร้อมรูปแบบไฟล์ dotpm ส่วนงานที่ต้องรองานอื่นใช้รหัสงานแบบปลั๊กอิน Tasks",
                        "Milestones will come with the dotpm file format. Dependencies use task ids like the Tasks plugin.",
                    ),
                    Modifier.padding(horizontal = 6.dp), color = C.faint, fontSize = TS.caption,
                )
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
    // The new look: one button for the view, as on the Tasks page, instead of six side by side.
    if (NewLook.on) {
        val views = listOf<Pair<Mode?, String>>(null to tr("ขั้นตอน", "Steps")) + Mode.entries.map { it to it.label }
        var choosing by remember { mutableStateOf(false) }
        Box(modifier) {
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(C.card).clickable { choosing = true }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(tr("มุมมอง: ", "View: ") + views.first { it.first == selected }.second, color = C.text2, fontSize = TS.body) }
            androidx.compose.material3.DropdownMenu(choosing, { choosing = false }, containerColor = C.raised) {
                views.forEach { (m, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label, color = if (m == selected) C.accentText else C.text, fontSize = TS.body, fontWeight = if (m == selected) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { choosing = false; onSelect(m) },
                    )
                }
            }
        }
        return
    }
    Segmented(listOf<Pair<Mode?, String>>(null to tr("ภาพรวม", "Overview")) + Mode.entries.map { it to it.label }, selected, onSelect, modifier.fillMaxWidth())
}

/** A folder inside a project, as an icon: its own colour stays its project's, and a parked one is faded. */
private fun subTile(n: Branches.Node, today: kotlinx.datetime.LocalDate, onClick: () -> Unit) = Tile(
    n.tag, n.name, "${n.done}/${n.all.size}", folderTint(n.tag), onClick,
    late = n.all.count { t -> t.isOpen && t.due?.let { it < today } == true },
    dim = n.state == Branches.State.PARKED,
)
