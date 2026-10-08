package app.omnitask.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.omnitask.model.Branches
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr

internal val Branches.State.color
    get() = when (this) {
        Branches.State.ACTIVE -> C.accent
        Branches.State.TRYING -> C.amber
        Branches.State.CHOSEN -> C.lime
        Branches.State.PARKED -> C.faint
    }

/** Sizes on the map, in dp. Tasks sit in the next column, above the branch's own sub-branches. */
private const val COL = 200f
private const val NODE_W = 156f
private const val NODE_H = 40f
private const val ROOT_H = 46f
private const val TASK_W = 190f
private const val TASK_H = 32f
private const val TASKS_SHOWN = 8

/** Something placed on the map: a branch, or one of a branch's tasks (or the "and N more" line under them). */
internal sealed interface MapItem {
    val x: Float
    val y: Float

    data class BranchAt(val node: Branches.Node, override val x: Float, override val y: Float) : MapItem
    data class TaskAt(val task: Task, val owner: Branches.Node, override val x: Float, override val y: Float) : MapItem
    data class MoreAt(val count: Int, val owner: Branches.Node, override val x: Float, override val y: Float) : MapItem
}

/**
 * A tidy tree read left to right: each branch sits in the middle of the rows its tasks and sub-branches take.
 * [y] is each item's centre line. Returns the items and the map's height.
 */
internal fun mapLayout(root: Branches.Node): Pair<List<MapItem>, Float> {
    val out = ArrayList<MapItem>()
    fun place(n: Branches.Node, depth: Int, top: Float): Float {
        var y = top
        val tasks = n.own.sortedWith(compareBy({ it.status == Status.DONE }, { it.lineIndex }))
        val shown = tasks.take(TASKS_SHOWN)
        val x = (depth + 1) * COL
        shown.forEach { t -> out += MapItem.TaskAt(t, n, x, y + TASK_H / 2); y += TASK_H }
        if (tasks.size > shown.size) { out += MapItem.MoreAt(tasks.size - shown.size, n, x, y + TASK_H / 2); y += TASK_H }
        if (shown.isNotEmpty() && n.children.isNotEmpty()) y += 6f
        n.children.forEachIndexed { i, k -> if (i > 0) y += 12f; y += place(k, depth + 1, y) }
        val h = maxOf(if (depth == 0) ROOT_H else NODE_H, y - top)
        out += MapItem.BranchAt(n, depth * COL, top + h / 2)
        return h
    }
    val height = place(root, 0, 0f)
    return out to height
}

/**
 * A project's branches as a mind map that fills the screen: drag to move, pinch or the buttons to zoom.
 * Tap a branch for its actions underneath; tap a task to tick it, long-press to open it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MindMapScreen(root: Branches.Node, vm: TaskViewModel, onOpen: (Task) -> Unit, onBack: () -> Unit) {
    var selPath by rememberSaveable(root.project) { mutableStateOf("") }
    val all = root.flatten()
    val sel = all.firstOrNull { it.path == selPath } ?: root
    val parent = all.firstOrNull { it.path == sel.parentPath && sel.path.isNotEmpty() }
    val (items, mapH) = mapLayout(root)
    val mapW = (root.flatten().maxOf { it.depth } + 1) * COL + TASK_W

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    fun fit() {
        if (box.width == 0) return
        val pad = 24f * density
        val s = minOf(1.2f, (box.width - pad * 2) / (mapW * density), (box.height - pad * 2) / (mapH * density)).coerceIn(0.3f, 2.5f)
        scale = s
        pan = Offset((box.width - mapW * density * s) / 2f, (box.height - mapH * density * s) / 2f)
    }
    fun zoomBy(f: Float, at: Offset) {
        val s = (scale * f).coerceIn(0.3f, 2.5f)
        pan = at - (at - pan) * (s / scale)
        scale = s
    }

    var naming by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    naming?.let { (title, done) -> NameDialog(title, { done(it); naming = null }) { naming = null } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Ic.back, tr("กลับ", "Back"), onBack)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("#" + root.project, style = MaterialTheme.typography.headlineSmall, color = C.text, maxLines = 1)
                Text(tr("ลากเพื่อเลื่อน บีบเพื่อซูม แตะกิ่งเพื่อจัดการ", "Drag to move, pinch to zoom, tap a branch"), color = C.muted, fontSize = TS.caption)
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().background(C.sunken)
                .onSizeChanged { box = it; if (!fitted) { fit(); fitted = true } }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, move, zoom, _ ->
                        pan += move
                        if (zoom != 1f) zoomBy(zoom, centroid)
                    }
                },
        ) {
            // Laid out at full size from the top-left corner, then moved and scaled as one layer.
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = pan.x; translationY = pan.y; scaleX = scale; scaleY = scale
                }.size(mapW.dp, mapH.dp),
            ) {
                val line = C.control
                val faintLine = C.cardBorder
                Canvas(Modifier.fillMaxSize()) {
                    val branchAt = items.filterIsInstance<MapItem.BranchAt>().associateBy { it.node.path }
                    fun curve(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply {
                        val mx = (x1 + x2) / 2
                        moveTo(x1.dp.toPx(), y1.dp.toPx()); cubicTo(mx.dp.toPx(), y1.dp.toPx(), mx.dp.toPx(), y2.dp.toPx(), x2.dp.toPx(), y2.dp.toPx())
                    }
                    items.forEach { m ->
                        when (m) {
                            is MapItem.BranchAt -> if (m.node.path.isNotEmpty()) {
                                val p = branchAt.getValue(m.node.parentPath)
                                val parked = m.node.state == Branches.State.PARKED
                                drawPath(
                                    curve(p.x + NODE_W, p.y, m.x, m.y),
                                    if (parked) line else m.node.state.color.copy(alpha = 0.8f),
                                    style = Stroke(1.8.dp.toPx(), pathEffect = if (parked) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null),
                                )
                            }
                            is MapItem.TaskAt -> branchAt[m.owner.path]?.let { p -> drawPath(curve(p.x + NODE_W, p.y, m.x, m.y), faintLine, style = Stroke(1.2.dp.toPx())) }
                            is MapItem.MoreAt -> Unit
                        }
                    }
                }
                items.forEach { m ->
                    when (m) {
                        is MapItem.BranchAt -> {
                            val n = m.node
                            val isRoot = n.path.isEmpty()
                            val h = if (isRoot) ROOT_H else NODE_H
                            Row(
                                Modifier.offset(m.x.dp, (m.y - h / 2).dp).width(NODE_W.dp).height(h.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(if (isRoot) C.accentSoft else C.card)
                                    .border(if (n.path == sel.path) 2.dp else 1.dp, if (n.path == sel.path) C.accent else C.cardBorder, RoundedCornerShape(10.dp))
                                    .clickable { selPath = n.path }.alpha(if (n.state == Branches.State.PARKED) 0.5f else 1f)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!isRoot) Box(Modifier.size(8.dp).clip(CircleShape).background(n.state.color))
                                Text(
                                    if (isRoot) "#" + n.name else n.name, Modifier.weight(1f).padding(start = if (isRoot) 0.dp else 7.dp),
                                    color = if (isRoot) C.accentText else C.text, fontSize = if (isRoot) TS.body else TS.caption,
                                    fontWeight = if (isRoot) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (n.all.isNotEmpty()) Text("${n.done}/${n.all.size}", color = C.muted, fontSize = TS.micro)
                            }
                        }
                        is MapItem.TaskAt -> {
                            val t = m.task
                            val done = t.status == Status.DONE
                            Row(
                                Modifier.offset(m.x.dp, (m.y - TASK_H / 2).dp).width(TASK_W.dp).height(TASK_H.dp).clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(onClick = { vm.toggleDone(t) }, onLongClick = { onOpen(t) }).padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(15.dp).clip(CircleShape).background(if (done) C.lime else C.sunken)
                                        .border(1.5.dp, if (done) C.lime else C.faint, CircleShape),
                                )
                                Text(
                                    t.title, Modifier.padding(start = 8.dp), color = if (done) C.faint else C.text2, fontSize = TS.caption,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (done) TextDecoration.LineThrough else null,
                                )
                            }
                        }
                        is MapItem.MoreAt -> Text(
                            tr("และอีก ${m.count} งาน", "and ${m.count} more"),
                            Modifier.offset((m.x + 6).dp, (m.y - 9).dp), color = C.faint, fontSize = TS.caption,
                        )
                    }
                }
            }
            // Zoom: in, out, and fit the whole map.
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

        // The picked branch: its state and what to do with it.
        Column(Modifier.fillMaxWidth().background(C.card).padding(horizontal = 14.dp, vertical = 12.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sel.path.isNotEmpty()) Box(Modifier.size(9.dp).clip(CircleShape).background(sel.state.color))
                Text(
                    if (sel.path.isEmpty()) "#" + sel.name else sel.name, Modifier.weight(1f).padding(start = if (sel.path.isEmpty()) 0.dp else 8.dp),
                    color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("#" + sel.tag, color = C.faint, fontSize = TS.caption, maxLines = 1)
            }
            if (sel.path.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Branches.State.entries.forEach { s ->
                        val on = sel.state == s
                        Text(
                            s.label,
                            Modifier.clip(RoundedCornerShape(15.dp)).background(if (on) s.color else C.raised)
                                .clickable { if (s == Branches.State.CHOSEN && parent != null) vm.chooseBranch(sel, parent.children) else vm.setBranchState(sel, s) }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            color = if (on) C.bg else C.text2, fontSize = TS.caption,
                        )
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapButton(tr("+ กิ่ง", "+ Branch"), filled = true) { naming = tr("กิ่งใหม่ใต้ ${sel.name}", "New branch under ${sel.name}") to { name: String -> vm.addEmptyBranch(sel, name) } }
                MapButton(tr("+ งาน", "+ Task")) { naming = tr("งานใหม่ใน ${sel.name}", "New task in ${sel.name}") to { title: String -> vm.addBranchTask(sel, title) } }
                if (sel.path.isNotEmpty()) {
                    MapButton(tr("เปลี่ยนชื่อ", "Rename")) { naming = tr("ชื่อใหม่ของ ${sel.name}", "New name for ${sel.name}") to { name: String -> vm.renameBranch(sel, name) } }
                    if (sel.all.isEmpty()) MapButton(tr("ลบกิ่ง", "Delete")) { vm.deleteBranch(sel); selPath = sel.parentPath }
                }
            }
            Text(
                when {
                    sel.path.isEmpty() -> tr("แตะ + กิ่ง เพื่อแตกทางใหม่ ซ้อนได้ไม่จำกัดชั้น", "Tap + Branch to branch out, as deep as you like")
                    sel.state == Branches.State.TRYING -> tr("เลือกแล้ว ทางที่กำลังลองข้างกันจะพับเก็บ", "Choosing it parks the others being tried")
                    sel.state == Branches.State.PARKED -> tr("พับเก็บ: งานซ่อนจากโฟกัส ปฏิทิน และรายการงาน แต่ยังอยู่ในไฟล์", "Parked: hidden from Focus, the calendar and lists, still in the file")
                    else -> tr("แตะงานเพื่อติ๊ก กดค้างเพื่อเปิด", "Tap a task to tick it, long-press to open it")
                },
                Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
            )
        }
    }
}

@Composable
private fun ZoomKey(text: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = C.text, fontSize = if (text.length == 1) TS.title else TS.caption)
    }
}

@Composable
private fun MapButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (filled) C.accent else C.raised).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) C.onAccent else C.text, fontSize = TS.caption, maxLines = 1) }
}

@Composable
private fun NameDialog(title: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(title) },
        text = { DialogField(text, tr("พิมพ์ชื่อ", "Type a name")) { text = it } },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(tr("ตกลง", "OK"), color = if (text.isNotBlank()) C.accent else C.faint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}

/** On the project's overview: how its branches stand, and the way into the mind map. */
@Composable
fun BranchSection(root: Branches.Node, onMap: () -> Unit) {
    val branches = root.flatten().drop(1)
    Card(Modifier.clickable(onClick = onMap)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.accentSoft), contentAlignment = Alignment.Center) {
                Icon(Ic.branch, null, tint = C.accentText, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(tr("Mind map ของโปรเจกต์", "Project mind map"), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                Text(
                    if (branches.isEmpty()) tr("ยังไม่มีกิ่ง แตะเพื่อแตกกิ่งแรก", "No branches yet. Tap to add the first")
                    else Branches.State.entries.mapNotNull { s -> branches.count { it.state == s }.takeIf { it > 0 }?.let { "${s.label} $it" } }.joinToString(", "),
                    color = C.muted, fontSize = TS.caption,
                )
            }
            Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
        }
    }
}
