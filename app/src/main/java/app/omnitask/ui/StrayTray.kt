package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.omnitask.data.TaskLine
import app.omnitask.model.Branches
import app.omnitask.model.MonthMarks
import app.omnitask.model.Task
import app.omnitask.model.Ygdrasil
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate

/** Tasks a group shows before "see more". */
private const val SHOWN = 5

/**
 * What a row in the tray does when held and dragged up onto the tree: [onStart] gets the finger's place on the
 * screen (root coordinates), [onDrag] each move in pixels, then [onEnd] or [onCancel].
 */
class StrayDrag(
    val onStart: (Task, Offset) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The tray under Ygdrasil: the tasks with no project, by their first tag (no tag first). A tap opens the picker,
 * a task held for a moment lifts and is dragged up onto a project or branch; a whole tag can be made a project.
 */
@Composable
fun StrayTray(
    strays: List<Ygdrasil.Strays>,
    today: LocalDate,
    sort: Ygdrasil.StraySort,
    onSort: (Ygdrasil.StraySort) -> Unit,
    onClose: () -> Unit,
    onPick: (Task) -> Unit,
    onMakeProject: (String) -> Unit,
    drag: StrayDrag,
    modifier: Modifier = Modifier,
) {
    var shut by remember { mutableStateOf(setOf<String>()) }
    var all by remember { mutableStateOf(setOf<String>()) }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(C.card).border(1.dp, C.cardBorder, shape)) {
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(C.control))
        }
        Row(Modifier.padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("ยังไม่มีบ้าน", "No project yet"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(
                "${strays.sumOf { it.tasks.size }}", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.amberSoft).padding(horizontal = 8.dp),
                color = C.amber, fontSize = TS.caption, fontWeight = FontWeight.SemiBold,
            )
            Box(Modifier.weight(1f))
            SquareButton(Ic.close, tr("ปิดถาด", "Close the tray"), onClose)
        }
        Text(
            tr("แตะงานเพื่อเลือกที่ไป หรือกดค้างแล้วลากขึ้นไปวางบนกิ่ง", "Tap a task to pick where it goes, or hold and drag it up onto a branch"),
            Modifier.padding(horizontal = 18.dp), color = C.faint, fontSize = TS.caption,
        )
        Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Ygdrasil.StraySort.DUE to tr("ใกล้กำหนด", "Nearest date"),
                Ygdrasil.StraySort.OLD to tr("เก่าสุด", "Oldest"),
                Ygdrasil.StraySort.AZ to tr("ก ถึง ฮ", "A to Z"),
            ).forEach { (k, label) ->
                val on = sort == k
                Text(
                    label,
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) C.accentSoft else C.card)
                        .border(1.dp, if (on) C.accentLine else C.cardBorder, RoundedCornerShape(14.dp))
                        .clickable { onSort(k) }.padding(horizontal = 12.dp, vertical = 5.dp),
                    color = if (on) C.accentText else C.muted, fontSize = TS.caption,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 12.dp)) {
            strays.forEach { g ->
                val id = g.tag ?: ""
                val closed = id in shut
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { shut = if (closed) shut - id else shut + id }.padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (closed) "▸" else "▾", color = C.faint, fontSize = TS.micro)
                        Text(g.tag?.let { "#$it" } ?: tr("ไม่มี tag", "No tag"), Modifier.padding(start = 6.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                        Text("${g.tasks.size}", Modifier.padding(start = 6.dp), color = C.faint, fontSize = TS.caption)
                    }
                    if (g.tag != null) Text(
                        tr("ตั้งเป็นโปรเจกต์", "Make it a project"),
                        Modifier.clip(RoundedCornerShape(8.dp)).background(C.accentSoft).clickable { onMakeProject(g.tag) }.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = C.accentText, fontSize = TS.caption,
                    )
                }
                if (!closed) {
                    val shown = if (id in all) g.tasks else g.tasks.take(SHOWN)
                    shown.forEach { t -> StrayRow(t, today, { onPick(t) }, drag) }
                    if (g.tasks.size > shown.size) Text(
                        tr("ดูอีก ${g.tasks.size - shown.size} งาน", "${g.tasks.size - shown.size} more"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = all + id }.padding(horizontal = 26.dp, vertical = 8.dp),
                        color = C.accentText, fontSize = TS.caption,
                    )
                }
            }
        }
    }
}

@Composable
private fun StrayRow(t: Task, today: LocalDate, onPick: () -> Unit, drag: StrayDrag) {
    var at by remember { mutableStateOf(Offset.Zero) }
    val date = t.due ?: t.scheduled
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .onGloballyPositioned { at = it.positionInRoot() }
            .clickable(onClick = onPick)
            // Held for a moment, the task lifts and follows the finger up onto the tree.
            .pointerInput(t) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> drag.onStart(t, at + o) },
                    onDrag = { change, amount -> change.consume(); drag.onDrag(amount) },
                    onDragEnd = drag.onEnd,
                    onDragCancel = drag.onCancel,
                )
            }
            .padding(start = 26.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).border(1.5.dp, C.control, RoundedCornerShape(4.dp)))
        Text(t.title, Modifier.weight(1f).padding(start = 10.dp), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (date != null) Text(
            MonthMarks.dayMonth(date), Modifier.padding(start = 8.dp),
            color = if (t.due != null && t.due < today) C.red else C.faint, fontSize = TS.caption,
        )
    }
}

private enum class HomeTab { PICK, MAIN, SUB }

/**
 * Where a task with no project goes: a project or branch already on the tree, a new project, or a new branch under
 * any project or branch. The line is shown as it is and as it will be before anything is written.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomePickerDialog(task: Task, stray: String?, tags: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var tab by remember { mutableStateOf(HomeTab.PICK) }
    var search by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    val clean = Branches.clean(name.trimStart('#'))
    val target = when (tab) {
        HomeTab.PICK -> picked
        HomeTab.MAIN -> clean.ifEmpty { null }
        HomeTab.SUB -> if (picked != null && clean.isNotEmpty()) "$picked/$clean" else null
    }
    val isNew = target != null && target !in tags
    val q = search.trim().trimStart('#').lowercase()
    val found = (if (q.isEmpty()) tags else tags.filter { q in it.lowercase() }).take(40)

    @Composable
    fun Chips() {
        DialogField(search, tr("ค้นโปรเจกต์หรือกิ่ง", "Search projects and branches")) { search = it }
        FlowRow(Modifier.padding(top = 8.dp).heightIn(max = 170.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            found.forEach { t ->
                val on = picked == t
                Text(
                    "#$t",
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) C.accent else C.accentSoft).clickable { picked = t }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    color = if (on) C.onAccent else C.accentText, fontSize = TS.caption,
                )
            }
            if (found.isEmpty()) Text(tr("ไม่พบ ลองสร้างใหม่", "Nothing found, make a new one"), color = C.faint, fontSize = TS.caption)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = {
            Column {
                Text(tr("ย้ายไปไว้ที่", "Move to"), color = C.muted, fontSize = TS.caption)
                Text(task.title, color = C.text, fontSize = TS.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(HomeTab.PICK to tr("มีอยู่แล้ว", "Existing"), HomeTab.MAIN to tr("โปรเจกต์ใหม่", "New project"), HomeTab.SUB to tr("กิ่งย่อยใหม่", "New branch")).forEach { (k, label) ->
                        val on = tab == k
                        Text(
                            label,
                            Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.accentSoft else C.card)
                                .border(1.dp, if (on) C.accentLine else C.cardBorder, RoundedCornerShape(10.dp))
                                .clickable { tab = k; picked = null; name = ""; search = "" }.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (on) C.accentText else C.muted, fontSize = TS.caption, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                Column(Modifier.padding(top = 12.dp)) {
                    when (tab) {
                        HomeTab.PICK -> Chips()
                        HomeTab.MAIN -> {
                            DialogField(name, tr("ชื่อโปรเจกต์ใหม่", "New project name")) { name = it }
                            if (stray != null) Text(
                                tr("ใช้ชื่อ tag เดิม #$stray", "Use the tag it has, #$stray"),
                                Modifier.padding(top = 8.dp).clickable { name = stray }, color = C.accentText, fontSize = TS.caption,
                            )
                        }
                        HomeTab.SUB -> {
                            Text(tr("1. เลือกว่าจะอยู่ใต้อะไร", "1. Pick what it goes under"), Modifier.padding(bottom = 6.dp), color = C.muted, fontSize = TS.caption)
                            Chips()
                            if (picked != null) {
                                Text("#$picked/", Modifier.padding(top = 10.dp, bottom = 4.dp), color = C.accentText, fontSize = TS.caption)
                                DialogField(name, tr("2. ชื่อกิ่งใหม่", "2. New branch name")) { name = it }
                            }
                        }
                    }
                }
                Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(10.dp)) {
                    Text(tr("ก่อน", "Before"), color = C.faint, fontSize = TS.micro)
                    Text(task.raw.trim(), color = C.text2, fontSize = TS.caption, fontFamily = FontFamily.Monospace)
                    Text(tr("หลัง", "After"), Modifier.padding(top = 6.dp), color = C.faint, fontSize = TS.micro)
                    Text(
                        target?.let { TaskLine.homeTag(task.raw, it).trim() } ?: tr("เลือกที่ไปก่อน", "Pick where it goes first"),
                        color = C.text, fontSize = TS.caption, fontFamily = FontFamily.Monospace,
                    )
                    if (isNew) Text(
                        if ('/' in target!!) tr("กิ่งใหม่ใต้ #${target.substringBeforeLast('/')}", "A new branch under #${target.substringBeforeLast('/')}") else tr("โปรเจกต์ใหม่ในต้นไม้", "A new project on the tree"),
                        Modifier.padding(top = 6.dp), color = C.lime, fontSize = TS.micro,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onMove) }, enabled = target != null) {
                Text(if (isNew) tr("สร้างแล้วย้าย", "Make it and move") else tr("ย้าย", "Move"), color = if (target != null) C.accent else C.faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}
