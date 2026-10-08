package app.omnitask.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.omnitask.model.Branches
import app.omnitask.model.Task
import app.omnitask.model.tr

/** The three ways to see a project's branches, as approved: family chart, mind map, and side-by-side. */
private enum class BranchView(private val th: String, private val en: String) {
    FAMILY("ผังครอบครัว", "Family"), MAP("Mind map", "Mind map"), COMPARE("เทียบทาง", "Compare");

    val label get() = tr(th, en)
}

private val Branches.State.color: Color
    get() = when (this) {
        Branches.State.ACTIVE -> C.accent
        Branches.State.TRYING -> C.amber
        Branches.State.CHOSEN -> C.lime
        Branches.State.PARKED -> C.faint
    }

/**
 * A project's branches (nested tags) as a tree to pick from, with the picked branch's state, progress and
 * tasks under it. Adding a branch creates its first task with the nested tag.
 */
@Composable
fun BranchSection(root: Branches.Node, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var view by rememberSaveable { mutableStateOf(BranchView.FAMILY) }
    var selPath by rememberSaveable(root.project) { mutableStateOf("") }
    var adding by remember { mutableStateOf<Branches.Node?>(null) }
    val all = root.flatten()
    val sel = all.firstOrNull { it.path == selPath } ?: root
    fun parentOf(n: Branches.Node) = all.firstOrNull { it.path == n.parentPath && n.path.isNotEmpty() }

    adding?.let { parent -> AddBranchDialog(parent, { name, first -> vm.addBranch(parent, name, first); adding = null }) { adding = null } }

    Card {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("สายของโปรเจกต์", "Branches"), Modifier.weight(1f), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
            Text("${all.size - 1}", color = C.muted, fontSize = TS.caption)
        }
        Segmented(BranchView.entries.map { it to it.label }, view, { view = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
        if (all.size == 1) {
            Text(
                tr(
                    "ยังไม่มี branch แตก branch เพื่อลองหลายทางพร้อมกัน เช่น #${root.project}/มือถือ กับ #${root.project}/เว็บ",
                    "No branches yet. Branch out to try several ways at once, e.g. #${root.project}/mobile and #${root.project}/web",
                ),
                Modifier.padding(horizontal = 16.dp), color = C.muted, fontSize = TS.caption,
            )
        } else {
            when (view) {
                BranchView.FAMILY -> FamilyChart(root, sel.path) { selPath = it.path }
                BranchView.MAP -> MindMap(root, sel.path) { selPath = it.path }
                BranchView.COMPARE -> {
                    // The choices under the picked branch; a branch without any shows itself with its siblings.
                    val level = if (sel.children.isNotEmpty()) sel else parentOf(sel) ?: root
                    CompareCards(level, sel.path, vm, onPick = { selPath = it.path }, onAdd = { adding = it })
                }
            }
        }
        BranchPanel(sel, parentOf(sel), vm, onOpen, onAdd = { adding = it })
    }
}

@Composable
private fun NodeBox(n: Branches.Node, selected: Boolean, width: Dp, modifier: Modifier, onClick: () -> Unit) {
    val ratio = if (n.all.isEmpty()) 0f else n.done.toFloat() / n.all.size
    Column(
        modifier.width(width).clip(RoundedCornerShape(10.dp)).background(C.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) C.accent else C.cardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).alpha(if (n.state == Branches.State.PARKED) 0.55f else 1f)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(n.state.color))
            Text(
                if (n.path.isEmpty()) "#" + n.name else n.name, Modifier.padding(start = 6.dp), color = C.text, fontSize = TS.caption,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(n.state.label, color = n.state.color, fontSize = TS.micro, maxLines = 1)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.raised)) {
                Box(Modifier.fillMaxWidth(ratio).height(3.dp).background(n.state.color))
            }
            Text("${n.done}/${n.all.size}", Modifier.padding(start = 5.dp), color = C.muted, fontSize = TS.micro)
        }
    }
}

/** Positions for a tree: leaves take slots in order, parents sit centred over their children. */
private fun layout(root: Branches.Node): Pair<Map<String, Pair<Int, Float>>, Int> {
    val pos = HashMap<String, Pair<Int, Float>>()
    var slot = 0
    fun place(n: Branches.Node) {
        if (n.children.isEmpty()) {
            pos[n.path] = n.depth to slot.toFloat(); slot++
        } else {
            n.children.forEach { place(it) }
            val s = n.children.map { pos.getValue(it.path).second }
            pos[n.path] = n.depth to (s.min() + s.max()) / 2f
        }
    }
    place(root)
    return pos to slot
}

/** Top to bottom: the project on top, its branches under it, joined by elbow lines in each branch's colour. */
@Composable
private fun FamilyChart(root: Branches.Node, selected: String, onPick: (Branches.Node) -> Unit) {
    val (pos, slots) = layout(root)
    val nodes = root.flatten()
    val w = 112.dp; val h = 62.dp; val gx = 12.dp; val gy = 40.dp; val pad = 12.dp
    val depth = nodes.maxOf { it.depth }
    val width = pad * 2 + (w + gx) * slots - gx
    val height = pad * 2 + (h + gy) * (depth + 1) - gy
    val lineColor = C.control
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Box(Modifier.width(width).height(height)) {
            Canvas(Modifier.width(width).height(height)) {
                nodes.filter { it.path.isNotEmpty() }.forEach { n ->
                    val p = pos.getValue(n.parentPath); val q = pos.getValue(n.path)
                    val x1 = (pad + (w + gx) * p.second + w / 2).toPx(); val y1 = (pad + (h + gy) * p.first + h).toPx()
                    val x2 = (pad + (w + gx) * q.second + w / 2).toPx(); val y2 = (pad + (h + gy) * q.first).toPx()
                    val my = (y1 + y2) / 2
                    val path = Path().apply { moveTo(x1, y1); lineTo(x1, my); lineTo(x2, my); lineTo(x2, y2) }
                    val parked = n.state == Branches.State.PARKED
                    drawPath(
                        path, if (parked) lineColor else n.state.color.copy(alpha = 0.8f),
                        style = Stroke(1.6.dp.toPx(), pathEffect = if (parked) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null),
                    )
                }
            }
            nodes.forEach { n ->
                val p = pos.getValue(n.path)
                NodeBox(n, n.path == selected, w, Modifier.offset(x = pad + (w + gx) * p.second, y = pad + (h + gy) * p.first)) { onPick(n) }
            }
        }
    }
}

/** Left to right: the project as a filled pill, branches fanning out with curves; parked ones dashed. */
@Composable
private fun MindMap(root: Branches.Node, selected: String, onPick: (Branches.Node) -> Unit) {
    val (pos, slots) = layout(root)
    val nodes = root.flatten()
    val w = 116.dp; val h = 36.dp; val gx = 28.dp; val row = 48.dp; val pad = 12.dp
    val depth = nodes.maxOf { it.depth }
    val width = pad * 2 + (w + gx) * (depth + 1) - gx
    val height = pad * 2 + row * slots - (row - h)
    val lineColor = C.control
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Box(Modifier.width(width).height(height)) {
            Canvas(Modifier.width(width).height(height)) {
                nodes.filter { it.path.isNotEmpty() }.forEach { n ->
                    val p = pos.getValue(n.parentPath); val q = pos.getValue(n.path)
                    val x1 = (pad + (w + gx) * p.first + w).toPx(); val y1 = (pad + row * p.second + h / 2).toPx()
                    val x2 = (pad + (w + gx) * q.first).toPx(); val y2 = (pad + row * q.second + h / 2).toPx()
                    val mx = (x1 + x2) / 2
                    val path = Path().apply { moveTo(x1, y1); cubicTo(mx, y1, mx, y2, x2, y2) }
                    val parked = n.state == Branches.State.PARKED
                    drawPath(
                        path, if (parked) lineColor else n.state.color.copy(alpha = 0.85f),
                        style = Stroke(
                            (if (n.state == Branches.State.CHOSEN) 2.6.dp else 1.8.dp).toPx(),
                            pathEffect = if (parked) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null,
                        ),
                    )
                }
            }
            nodes.forEach { n ->
                val p = pos.getValue(n.path)
                val isRoot = n.path.isEmpty()
                val on = n.path == selected
                Row(
                    Modifier.offset(x = pad + (w + gx) * p.first, y = pad + row * p.second).width(w).height(h)
                        .clip(RoundedCornerShape(h / 2)).background(if (isRoot) C.accent else C.card)
                        .border(if (on) 2.dp else 1.dp, if (on) C.text else if (isRoot) C.accent else C.cardBorder, RoundedCornerShape(h / 2))
                        .clickable { onPick(n) }.alpha(if (n.state == Branches.State.PARKED) 0.55f else 1f)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isRoot) Box(Modifier.size(7.dp).clip(CircleShape).background(n.state.color))
                    Text(
                        if (isRoot) "#" + n.name else n.name, Modifier.weight(1f).padding(start = if (isRoot) 0.dp else 6.dp),
                        color = if (isRoot) C.onAccent else C.text, fontSize = TS.caption, fontWeight = if (isRoot) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("${n.done}/${n.all.size}", color = if (isRoot) C.onAccent else C.muted, fontSize = TS.micro)
                }
            }
        }
    }
}

/** Sibling choices side by side, each with its progress, next task and what to do with it. */
@Composable
private fun CompareCards(level: Branches.Node, selected: String, vm: TaskViewModel, onPick: (Branches.Node) -> Unit, onAdd: (Branches.Node) -> Unit) {
    Text(
        tr("${level.children.size} ทางภายใต้ ", "${level.children.size} ways under ") + (if (level.path.isEmpty()) "#" + level.name else level.name),
        Modifier.padding(horizontal = 16.dp), color = C.muted, fontSize = TS.caption,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        level.children.forEach { n ->
            val ratio = if (n.all.isEmpty()) 0f else n.done.toFloat() / n.all.size
            Column(
                Modifier.width(240.dp).clip(RoundedCornerShape(C.radius)).background(C.sunken)
                    .border(1.dp, if (n.path == selected) C.accent else C.cardBorder, RoundedCornerShape(C.radius))
                    .alpha(if (n.state == Branches.State.PARKED) 0.6f else 1f).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.name, Modifier.weight(1f), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Pill(n.state.label, n.state.color.copy(alpha = 0.16f), n.state.color)
                }
                Text("#" + n.tag, color = C.muted, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${(ratio * 100).toInt()}%", color = C.text, fontSize = TS.stat, fontWeight = FontWeight.SemiBold)
                    Text(tr(" ${n.done} จาก ${n.all.size} งาน", " ${n.done} of ${n.all.size}"), Modifier.padding(bottom = 3.dp), color = C.muted, fontSize = TS.caption)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.raised)) {
                    Box(Modifier.fillMaxWidth(ratio).height(4.dp).background(n.state.color))
                }
                Text(tr("ถัดไป", "Next"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.micro)
                Text(n.open.firstOrNull()?.title ?: tr("ยังไม่มีงานค้าง", "Nothing open"), color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (n.children.isEmpty()) tr("ยังไม่แตกต่อ", "No branches under it") else tr("แตกต่ออีก ${n.children.size} ทาง", "${n.children.size} branches under it"),
                    Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.micro,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton(tr("เลือกทางนี้", "Choose"), filled = true, Modifier.weight(1f)) { vm.chooseBranch(n, level.children) }
                    MiniButton(tr("พับเก็บ", "Park"), filled = false, Modifier.weight(1f)) { vm.setBranchState(n, Branches.State.PARKED) }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton(tr("เข้าไปดู", "Open"), filled = false, Modifier.weight(1f)) { onPick(n) }
                    MiniButton(tr("+ แตกต่อ", "+ Branch"), filled = false, Modifier.weight(1f)) { onAdd(n) }
                }
            }
        }
        Box(
            Modifier.width(120.dp).height(120.dp).clip(RoundedCornerShape(C.radius)).border(1.dp, C.accentLine, RoundedCornerShape(C.radius)).clickable { onAdd(level) },
            contentAlignment = Alignment.Center,
        ) { Text(tr("+ เพิ่มทาง", "+ Add a way"), color = C.accentText, fontSize = TS.body) }
    }
}

/** The picked branch: where it sits, its state, and its open tasks. */
@Composable
private fun BranchPanel(n: Branches.Node, parent: Branches.Node?, vm: TaskViewModel, onOpen: (Task) -> Unit, onAdd: (Branches.Node) -> Unit) {
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(C.divider))
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(n.state.color))
            Text(if (n.path.isEmpty()) "#" + n.name else n.name, Modifier.weight(1f).padding(start = 8.dp), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text("${n.done}/${n.all.size}", color = C.muted, fontSize = TS.caption)
        }
        Text("#" + n.tag, color = C.muted, fontSize = TS.caption)
        if (n.path.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Branches.State.entries.forEach { s ->
                    val on = n.state == s
                    Text(
                        s.label,
                        Modifier.clip(RoundedCornerShape(15.dp)).background(if (on) s.color else C.raised)
                            .clickable { if (s == Branches.State.CHOSEN && parent != null) vm.chooseBranch(n, parent.children) else vm.setBranchState(n, s) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        color = if (on) C.bg else C.text2, fontSize = TS.caption,
                    )
                }
            }
            Text(
                when (n.state) {
                    Branches.State.ACTIVE -> tr("สายงานที่ทำจริง", "Work under way")
                    Branches.State.TRYING -> tr("ทางเลือกที่กำลังลอง เลือกอันหนึ่งแล้ว ทางที่กำลังลองที่เหลือจะพับเก็บ", "A way being tried; choosing one parks the others being tried")
                    Branches.State.CHOSEN -> tr("ทางที่เลือกแล้ว", "The way chosen")
                    Branches.State.PARKED -> tr("พับเก็บ: งานซ่อนจากโฟกัส ปฏิทิน และรายการงาน แต่ยังอยู่ในไฟล์", "Parked: hidden from Focus, the calendar and lists, still in the file")
                },
                Modifier.padding(top = 6.dp), color = C.muted, fontSize = TS.caption,
            )
        }
        val open = n.open.take(6)
        open.forEachIndexed { i, t ->
            if (i == 0) Box(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(C.faint))
                Text(t.title, Modifier.weight(1f).padding(start = 10.dp), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val path = Branches.pathOf(t)
                if (path != n.path && path.isNotEmpty()) Text(path.substringAfterLast('/'), color = C.muted, fontSize = TS.caption)
            }
        }
        if (n.open.size > open.size) Text(tr("และอีก ${n.open.size - open.size} งาน", "and ${n.open.size - open.size} more"), color = C.muted, fontSize = TS.caption)
        MiniButton(tr("+ แตก branch จาก ", "+ Branch from ") + n.name, filled = true, Modifier.padding(top = 10.dp)) { onAdd(n) }
    }
}

@Composable
private fun MiniButton(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(if (filled) C.accent else C.raised).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) C.onAccent else C.text, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** A new branch needs a name and its first task, which carries the nested tag into the file. */
@Composable
private fun AddBranchDialog(parent: Branches.Node, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    val ok = name.isNotBlank() && first.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("แตก branch จาก ", "Branch from ") + (if (parent.path.isEmpty()) "#" + parent.name else parent.name)) },
        text = {
            Column {
                DialogField(name, tr("ชื่อ branch เช่น มือถือ", "Branch name, e.g. mobile")) { name = it }
                Text(
                    "#" + parent.tag + "/" + name.trim().replace(Regex("""[\s#]+"""), "-").ifEmpty { "…" },
                    Modifier.padding(top = 6.dp, bottom = 12.dp), color = C.accentText, fontSize = TS.caption,
                )
                DialogField(first, tr("งานแรก (พิมพ์ พรุ่งนี้ 9:00 ได้)", "First task (tomorrow 9:00 works)")) { first = it }
                Text(tr("branch ใหม่เริ่มที่สถานะ กำลังลอง", "A new branch starts as Trying"), Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, first) }, enabled = ok) { Text(tr("สร้าง", "Create"), color = if (ok) C.accent else C.faint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}

@Composable
fun DialogField(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body)
                inner()
            }
        },
    )
}

/** Renaming a project shows what will change before anything is written. */
@Composable
fun RenameProjectDialog(old: String, counts: Pair<Int, Int>, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(old) }
    val clean = name.trim().removePrefix("#").replace(Regex("""\s+"""), "-")
    val ok = clean.isNotEmpty() && clean != old && '/' !in clean
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("เปลี่ยนชื่อโปรเจกต์", "Rename project")) },
        text = {
            Column {
                DialogField(name, tr("ชื่อใหม่", "New name")) { name = it }
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(12.dp)) {
                    Row {
                        Text("#$old", Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
                        Text("#$clean", color = C.accentText, fontSize = TS.caption)
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("#$old/…", Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
                        Text("#$clean/…", color = C.accentText, fontSize = TS.caption)
                    }
                }
                Text(
                    tr("แก้ ${counts.second} บรรทัดงานใน ${counts.first} ไฟล์ รวมทุก branch ลำดับงาน ดาว และสถานะ branch ย้ายตามไปด้วย", "Changes ${counts.second} task lines in ${counts.first} files, branches included; order, star and branch states move along"),
                    Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
                )
                if ('/' in clean) Text(tr("ชื่อโปรเจกต์มี / ไม่ได้", "A project name cannot contain /"), Modifier.padding(top = 6.dp), color = C.red, fontSize = TS.caption)
            }
        },
        confirmButton = { TextButton(onClick = { onRename(clean) }, enabled = ok) { Text(tr("เปลี่ยนชื่อ", "Rename"), color = if (ok) C.accent else C.faint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}
