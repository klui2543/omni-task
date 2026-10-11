package app.omnitask.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.omnitask.model.Branches
import app.omnitask.model.Priority
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.Ygdrasil
import app.omnitask.model.tr

/** The scale a tree too big for the screen opens at: small enough to see a lot, big enough to read. */
private const val READABLE = 0.62f

/**
 * Project Ygdrasil: every project in one tree that fills the screen, from the trunk through each project (a world)
 * and its branches to the tasks, with the tasks that wait for one another tied across. Drag to move, pinch or the
 * buttons to zoom; tap a world or branch for its actions underneath; tap a task to tick it, long-press to open it.
 * The filter and the folded limbs come from the Projects tab, so they stay while it is open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YgdrasilScreen(
    state: UiState,
    vm: TaskViewModel,
    filter: Ygdrasil.Filter,
    onFilter: (Ygdrasil.Filter) -> Unit,
    folded: Set<String>,
    onFolded: (Set<String>) -> Unit,
    onOpen: (Task) -> Unit,
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tree = remember(state.tasks, state.parked, state.branchStates, state.today, state.starred, state.projectOrder, filter) {
        Ygdrasil.grow(state.tasks + state.parked, state.branchStates, state.today, filter, state.starred, state.projectOrder)
    }
    val layout = remember(tree, folded) { Ygdrasil.layout(tree, folded) }
    var selTag by rememberSaveable { mutableStateOf("") }
    val sel = layout.spots.firstOrNull { it.kind == Ygdrasil.Kind.LIMB && it.limb!!.node.tag == selTag }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var touched by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    // A tree too big for the screen opens readable, from the trunk, instead of shrunk until its text cannot be read;
    // "Fit" shows all of it, and the tree keeps that way until the owner moves it.
    var whole by remember { mutableStateOf(false) }
    fun place() {
        if (box.width == 0) return
        val pad = 24f * density
        val s = minOf(1.2f, (box.width - pad * 2) / (layout.width * density), (box.height - pad * 2) / (layout.height * density))
        val trunkY = layout.spots.firstOrNull { it.kind == Ygdrasil.Kind.TRUNK }?.y ?: (layout.height / 2f)
        if (!whole && s < READABLE) {
            scale = READABLE
            pan = Offset(pad, box.height / 2f - trunkY * density * READABLE)
        } else {
            val k = s.coerceIn(0.15f, 2.5f)
            scale = k
            pan = Offset((box.width - layout.width * density * k) / 2f, (box.height - layout.height * density * k) / 2f)
        }
        touched = false
    }
    fun fit() { whole = true; place() }
    fun zoomBy(f: Float, at: Offset) {
        val s = (scale * f).coerceIn(0.15f, 2.5f)
        pan = at - (at - pan) * (s / scale)
        scale = s
        touched = true
    }
    // Until the owner moves it, the tree keeps fitting the screen as filters and folds change its size.
    LaunchedEffect(layout.width, layout.height, box) { if (!touched) place() }
    // Zoomed far out, tasks show as their rings only.
    val far by remember { derivedStateOf { scale < 0.45f } }

    // A task, project or branch carried across the tree (held, then dragged): where it is, in dp, and the project or
    // branch (or the trunk, "") under it. A task let go on another branch moves there, and let go where it was opens;
    // a project or branch let go on another goes under it, and on the trunk becomes a project of its own.
    var carry by remember { mutableStateOf<Carry?>(null) }
    var carryAt by remember { mutableStateOf(Offset.Zero) }
    var carryMoved by remember { mutableStateOf(false) }
    val spots by rememberUpdatedState(layout.spots)
    fun targetAt(p: Offset): String? = spots.firstOrNull { s ->
        (s.kind == Ygdrasil.Kind.LIMB || s.kind == Ygdrasil.Kind.TRUNK) && p.x in s.x..(s.x + s.w) && p.y in (s.y - s.h / 2)..(s.y + s.h / 2)
    }?.let { it.limb?.node?.tag ?: "" }
    /** Where the carried thing would go if let go now, or null. */
    fun goesTo(c: Carry, to: String?): String? = when {
        to == null -> null
        c.task != null -> to.takeIf { it.isNotEmpty() && it != c.from }
        else -> to.takeIf { Branches.graftTag(c.from, it) != null }
    }
    val dropTag = carry?.let { goesTo(it, targetAt(carryAt)) }
    val baseConfig = LocalViewConfiguration.current
    val quickHold = remember(baseConfig) { object : ViewConfiguration by baseConfig { override val longPressTimeoutMillis: Long get() = HOLD_MS } }
    fun drop() {
        val c = carry ?: return
        val to = goesTo(c, targetAt(carryAt))
        when {
            c.task != null && to != null -> vm.moveTask(c.task, to)
            c.task != null && !carryMoved -> onOpen(c.task)
            to != null -> vm.graftBranch(c.from, to)
        }
        carry = null
    }
    /** Lifts [what] when held, and carries it until let go. */
    fun Modifier.carries(what: Carry, top: Float, left: Float): Modifier = pointerInput(what, top, left) {
        detectDragGesturesAfterLongPress(
            onDragStart = { o ->
                carry = what
                carryMoved = false
                carryAt = Offset(left + o.x / density, top + o.y / density)
            },
            onDrag = { change, amount ->
                change.consume()
                carryAt += Offset(amount.x / density, amount.y / density)
                if (amount.getDistance() > 0f) carryMoved = true
            },
            onDragEnd = { drop() },
            onDragCancel = { carry = null },
        )
    }

    var naming by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    naming?.let { (title, done) -> MapNameDialog(title, { done(it); naming = null }) { naming = null } }
    var filtering by remember { mutableStateOf(false) }
    if (filtering) YgdrasilFilterDialog(filter, onFilter) { filtering = false }

    val dock = LocalDockClearance.current
    FullPage()
    Column(Modifier.fillMaxSize().padding(bottom = dock)) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Ic.back, tr("กลับ", "Back"), onBack)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Project Ygdrasil", style = MaterialTheme.typography.headlineSmall, color = C.text, maxLines = 1)
                Text(
                    tr("${tree.worlds.size} โปรเจกต์, ${tree.matched} งาน", "${tree.worlds.size} projects, ${tree.matched} tasks") +
                        if (tree.links.isNotEmpty()) tr(", งานที่รอกัน ${tree.links.size} เส้น", ", ${tree.links.size} waits") else "",
                    color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val changed = filter.changed
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (changed > 0) C.accent else C.card)
                    .border(1.dp, if (changed > 0) C.accent else C.cardBorder, RoundedCornerShape(12.dp))
                    .clickable { filtering = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.filter, null, tint = if (changed > 0) C.onAccent else C.text2, modifier = Modifier.size(16.dp))
                Text(
                    tr("ตัวกรอง", "Filter") + if (changed > 0) " ($changed)" else "",
                    Modifier.padding(start = 6.dp), color = if (changed > 0) C.onAccent else C.text2, fontSize = TS.caption,
                )
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().background(C.sunken)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, move, zoom, _ ->
                        pan += move
                        touched = true
                        if (zoom != 1f) zoomBy(zoom, centroid)
                    }
                },
        ) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = pan.x; translationY = pan.y; scaleX = scale; scaleY = scale
                }.size(layout.width.dp, layout.height.dp),
            ) {
                val line = C.control
                val faintLine = C.cardBorder
                val linkColor = C.muted
                val acrossColor = C.amber
                Canvas(Modifier.fillMaxSize()) {
                    layout.edges.forEach { e ->
                        val path = Path().apply {
                            moveTo(e.x1.dp.toPx(), e.y1.dp.toPx())
                            cubicTo(e.cx1.dp.toPx(), e.cy1.dp.toPx(), e.cx2.dp.toPx(), e.cy2.dp.toPx(), e.x2.dp.toPx(), e.y2.dp.toPx())
                        }
                        when (e.kind) {
                            Ygdrasil.EdgeKind.BRANCH -> {
                                val parked = e.state == Branches.State.PARKED
                                drawPath(
                                    path, if (parked) line else (e.state ?: Branches.State.ACTIVE).color.copy(alpha = 0.8f),
                                    style = Stroke(1.8.dp.toPx(), pathEffect = if (parked) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null),
                                )
                            }
                            Ygdrasil.EdgeKind.TASK -> drawPath(path, faintLine, style = Stroke(1.2.dp.toPx()))
                            Ygdrasil.EdgeKind.LINK -> drawPath(
                                path, linkColor.copy(alpha = 0.7f),
                                style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))),
                            )
                            Ygdrasil.EdgeKind.LINK_ACROSS -> drawPath(
                                path, acrossColor.copy(alpha = 0.9f),
                                style = Stroke(1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
                            )
                        }
                    }
                }
                CompositionLocalProvider(LocalViewConfiguration provides quickHold) {
                layout.spots.forEach { s ->
                    when (s.kind) {
                        Ygdrasil.Kind.TRUNK -> Row(
                            Modifier.offset(s.x.dp, (s.y - s.h / 2).dp).width(s.w.dp).height(s.h.dp)
                                .clip(RoundedCornerShape(12.dp)).background(C.accent)
                                .then(if (sel == null) Modifier.border(2.dp, C.accentText, RoundedCornerShape(12.dp)) else Modifier)
                                .then(if (carry != null && dropTag == "") Modifier.border(2.dp, C.amber, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { selTag = "" }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Ygdrasil", Modifier.weight(1f), color = C.onAccent, fontSize = TS.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${s.count}", color = C.onAccent, fontSize = TS.caption)
                        }
                        Ygdrasil.Kind.LIMB -> {
                            val n = s.limb!!.node
                            val world = n.path.isEmpty()
                            val picked = sel === s
                            val target = carry != null && dropTag == n.tag
                            Row(
                                Modifier.offset(s.x.dp, (s.y - s.h / 2).dp).width(s.w.dp).height(s.h.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(if (target) C.amberSoft else if (world) C.accentSoft else C.card)
                                    .border(
                                        if (picked || target) 2.dp else 1.dp,
                                        if (target) C.amber else if (picked) C.accent else C.cardBorder, RoundedCornerShape(10.dp),
                                    )
                                    .alpha(if (n.state == Branches.State.PARKED) 0.5f else if (carry?.task == null && carry?.from == n.tag) 0.35f else 1f)
                                    .pointerInput(n.tag) { detectTapGestures(onTap = { selTag = n.tag }) }
                                    .carries(Carry(null, n.tag, if (world) "#" + n.name else n.name), s.y - s.h / 2, s.x)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!world) Box(Modifier.size(8.dp).clip(CircleShape).background(n.state.color))
                                Text(
                                    if (world) "#" + n.name else n.name, Modifier.weight(1f).padding(start = if (world) 0.dp else 7.dp),
                                    color = if (world) C.accentText else C.text, fontSize = TS.caption,
                                    fontWeight = if (world) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (s.folded) {
                                    Text("+${s.count}", Modifier.clip(RoundedCornerShape(8.dp)).background(C.raised).padding(horizontal = 6.dp, vertical = 1.dp), color = C.text2, fontSize = TS.micro)
                                } else if (n.all.isNotEmpty()) {
                                    Text("${n.done}/${n.all.size}", color = C.muted, fontSize = TS.micro)
                                }
                            }
                        }
                        Ygdrasil.Kind.TASK -> {
                            val t = s.task!!
                            val from = s.limb!!.node.tag
                            val done = t.status == Status.DONE
                            Row(
                                Modifier.offset(s.x.dp, (s.y - s.h / 2).dp).width(s.w.dp).height(s.h.dp).clip(RoundedCornerShape(8.dp))
                                    .alpha(if (carry?.task == t) 0.35f else 1f)
                                    // A tap ticks; held, the task lifts and follows the finger (the drag consumes the
                                    // touch, so neither the tap nor the map's own drag sees it).
                                    .pointerInput(t) { detectTapGestures(onTap = { vm.toggleDone(t) }) }
                                    .carries(Carry(t, from, t.title), s.y - s.h / 2, s.x)
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(15.dp).clip(CircleShape).background(if (done) C.lime else C.sunken)
                                        .border(1.5.dp, if (done) C.lime else priorityTint(t.priority), CircleShape),
                                )
                                if (!far) Text(
                                    t.title, Modifier.padding(start = 8.dp), color = if (done) C.faint else C.text2, fontSize = TS.caption,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (done) TextDecoration.LineThrough else null,
                                )
                            }
                        }
                        Ygdrasil.Kind.MORE -> if (!far) Text(
                            tr("และอีก ${s.count} งาน", "and ${s.count} more"),
                            Modifier.offset((s.x + 6).dp, (s.y - 9).dp), color = C.faint, fontSize = TS.caption,
                        )
                    }
                }
                // What is carried, under the finger, saying where it would go.
                carry?.let { c ->
                    Column(
                        Modifier.offset((carryAt.x + 10).dp, (carryAt.y + 10).dp).width(200.dp).clip(RoundedCornerShape(10.dp))
                            .background(C.card).border(1.5.dp, C.accent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(c.label, color = C.text, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                dropTag == null -> if (c.task != null) tr("ลากไปวางบนกิ่ง", "Drop it on a branch") else tr("ลากไปวางบนโปรเจกต์ กิ่ง หรือลำต้น", "Drop it on a project, branch or the trunk")
                                c.task != null -> tr("ย้ายไป #$dropTag", "Move to #$dropTag")
                                dropTag.isEmpty() -> tr("แยกเป็นโปรเจกต์ของตัวเอง", "Make it a project of its own")
                                else -> tr("ไปไว้ใต้ #$dropTag", "Put it under #$dropTag")
                            },
                            color = C.muted, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                }
            }
            if (tree.worlds.isEmpty()) {
                Text(tr("ไม่มีงานตรงกับตัวกรอง", "Nothing matches the filter"), Modifier.align(Alignment.Center), color = C.muted, fontSize = TS.body)
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(C.card)
                    .border(1.dp, C.cardBorder, RoundedCornerShape(12.dp)).padding(3.dp),
            ) {
                val mid = Offset(box.width / 2f, box.height / 2f)
                ZoomKey("+", tr("ซูมเข้า", "Zoom in")) { zoomBy(1.25f, mid) }
                ZoomKey("−", tr("ซูมออก", "Zoom out")) { zoomBy(0.8f, mid) }
                ZoomKey(tr("พอดี", "Fit"), tr("พอดีจอ", "Fit")) { fit() }
            }
        }

        // The trunk or the picked world or branch: what to do with it.
        Column(Modifier.fillMaxWidth().background(C.card).padding(horizontal = 14.dp, vertical = 12.dp).then(if (dock == 0.dp) Modifier.navigationBarsPadding() else Modifier)) {
            val node = sel?.limb?.node
            if (node == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ygdrasil", Modifier.weight(1f), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
                    Text(tr("ทุกโปรเจกต์ ${tree.matched} งาน", "${tree.matched} tasks in all"), color = C.faint, fontSize = TS.caption)
                }
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapButton(tr("กางทั้งหมด", "Unfold all")) { onFolded(emptySet()) }
                    MapButton(tr("พับทุกโปรเจกต์", "Fold every project")) { onFolded(tree.worlds.map { it.node.tag }.toSet()) }
                }
                Text(
                    tr("แตะโปรเจกต์หรือกิ่งเพื่อจัดการ เส้นประสีส้มคืองานที่รองานในโปรเจกต์อื่น", "Tap a project or branch. Orange dashes: a task waiting on another project"),
                    Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.path.isNotEmpty()) Box(Modifier.size(9.dp).clip(CircleShape).background(node.state.color))
                    Text(
                        if (node.path.isEmpty()) "#" + node.name else node.name, Modifier.weight(1f).padding(start = if (node.path.isEmpty()) 0.dp else 8.dp),
                        color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("#" + node.tag + if (node.path.isNotEmpty()) ", " + node.state.label else "", color = C.faint, fontSize = TS.caption, maxLines = 1)
                }
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapButton(tr("+ งาน", "+ Task"), filled = true) { naming = tr("งานใหม่ใน ${node.name}", "New task in ${node.name}") to { title: String -> vm.addBranchTask(node, title) } }
                    MapButton(tr("+ กิ่ง", "+ Branch")) { naming = tr("กิ่งใหม่ใต้ ${node.name}", "New branch under ${node.name}") to { name: String -> vm.addEmptyBranch(node, name) } }
                    MapButton(if (node.tag in folded) tr("กาง", "Unfold") else tr("พับ", "Fold")) { onFolded(if (node.tag in folded) folded - node.tag else folded + node.tag) }
                    MapButton(tr("เปิด Mind map", "Open mind map")) { onOpenProject(node.project) }
                }
                Text(tr("แตะงานเพื่อติ๊ก กดค้างแล้วลากไปวางบนโปรเจกต์หรือกิ่งอื่นเพื่อย้าย ปล่อยที่เดิมเพื่อเปิด", "Tap a task to tick it. Hold and drag it onto another project or branch to move it, or let go to open it"), Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption)
            }
        }
    }
}

/** What is carried across the tree: a task ([task] set), or a project or branch; [from] is its tag. */
private data class Carry(val task: Task?, val from: String, val label: String)

/** Tasks, projects and branches lift after this short a hold (the system's long press is slower). */
private const val HOLD_MS = 250L

/** The tick ring's edge carries the priority, as the task rows do. */
private fun priorityTint(p: Priority) = when (p) {
    Priority.HIGHEST -> C.red
    Priority.HIGH -> C.amber
    Priority.MEDIUM -> C.blue
    else -> C.faint
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YgdrasilFilterDialog(filter: Ygdrasil.Filter, onFilter: (Ygdrasil.Filter) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ตัวกรอง", "Filter")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(Ygdrasil.Show.entries.map { it to it.label }, filter.show, { onFilter(filter.copy(show = it)) }, Modifier.fillMaxWidth())
                FilterGroup(tr("วันที่", "Dates")) {
                    Ygdrasil.Dates.entries.forEach { d -> Chip(d.label, filter.dates == d, { onFilter(filter.copy(dates = d)) }) }
                }
                FilterGroup(tr("ความสำคัญ", "Priority")) {
                    Priority.entries.forEach { p ->
                        val on = p in filter.priorities
                        Chip(p.label, on, { onFilter(filter.copy(priorities = if (on) filter.priorities - p else filter.priorities + p)) })
                    }
                }
                FilterGroup(tr("อื่น ๆ", "Other")) {
                    Chip(tr("ติดรองานอื่น", "Blocked"), filter.blockedOnly, { onFilter(filter.copy(blockedOnly = !filter.blockedOnly)) })
                    Chip(tr("เฉพาะติดดาว", "Starred only"), filter.starredOnly, { onFilter(filter.copy(starredOnly = !filter.starredOnly)) })
                    Chip(tr("รวมกิ่งที่พับเก็บ", "Parked branches"), filter.parked, { onFilter(filter.copy(parked = !filter.parked)) })
                    Chip(tr("เส้นงานที่รอกัน", "Wait lines"), filter.links, { onFilter(filter.copy(links = !filter.links)) })
                }
                DialogField(filter.text, tr("ค้นชื่องานหรือ tag", "Search titles or tags")) { onFilter(filter.copy(text = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("เสร็จ", "Done"), color = C.accent) } },
        dismissButton = {
            if (filter.changed > 0) TextButton(onClick = { onFilter(Ygdrasil.Filter()) }) { Text(tr("ล้างตัวกรอง", "Clear"), color = C.text2) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = C.muted, fontSize = TS.caption, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** On the Projects tab, above the project cards: the way into Project Ygdrasil. */
@Composable
fun YgdrasilEntry(onClick: () -> Unit) {
    Card(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.accentSoft), contentAlignment = Alignment.Center) {
                Icon(Ic.branch, null, tint = C.accentText, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Project Ygdrasil", color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                Text(
                    tr("ทุกโปรเจกต์ในต้นไม้เดียว กรองงานได้ เห็นงานที่รอกันข้ามโปรเจกต์", "Every project in one tree, filtered, with waits across projects"),
                    color = C.muted, fontSize = TS.caption,
                )
            }
            Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
        }
    }
}
