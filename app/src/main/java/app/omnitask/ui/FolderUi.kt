package app.omnitask.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.omnitask.data.TaskLine
import app.omnitask.model.Folders
import app.omnitask.model.Task
import app.omnitask.model.tr

/**
 * Folders on screen (owner's pick, 2026-10-26, mock way 1): a tag path is a folder, drawn as one. The colour belongs
 * to the top folder, so a project keeps its colour on the desktop, in the picker and in Ygdrasil.
 */
private val FOLDER_TINTS get() = listOf(C.blue, C.amber, C.tealChip, C.accent, C.lime)

fun folderTint(path: String): Color {
    val root = path.substringBefore('/').lowercase()
    val h = root.fold(7) { a, c -> a * 31 + c.code }
    return FOLDER_TINTS[Math.floorMod(h, FOLDER_TINTS.size)]
}

/** A folder: a tab and a body, as on a computer. [dashed] marks a lone tag that is not a project yet. */
@Composable
fun FolderGlyph(color: Color, width: Dp, modifier: Modifier = Modifier, dashed: Boolean = false) {
    Canvas(modifier.size(width, width * 0.78f)) {
        val w = size.width
        val h = size.height
        val tabW = w * 0.42f
        val tabH = h * 0.2f
        val r = CornerRadius(w * 0.09f)
        if (dashed) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.04f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(w * 0.08f, w * 0.06f)),
            )
            drawRoundRect(color, Offset(0f, tabH * 0.6f), Size(w, h - tabH * 0.6f), r, style = stroke)
        } else {
            drawRoundRect(color.copy(alpha = 0.75f), Offset(w * 0.06f, 0f), Size(tabW, tabH * 1.6f), CornerRadius(w * 0.06f))
            drawRoundRect(color, Offset(0f, tabH * 0.6f), Size(w, h - tabH * 0.6f), r)
        }
    }
}

/** The pin on a folder kept on the desktop. */
@Composable
fun PinMark(modifier: Modifier = Modifier, color: Color = C.amber) {
    Canvas(modifier.size(14.dp)) {
        val s = size.width / 24f
        val p = Path().apply {
            moveTo(14.5f * s, 2.5f * s); lineTo(21.5f * s, 9.5f * s); lineTo(18.9f * s, 10.1f * s); lineTo(15.3f * s, 13.7f * s)
            lineTo(15.7f * s, 18.3f * s); lineTo(13.7f * s, 20.3f * s); lineTo(10.1f * s, 16.7f * s); lineTo(5f * s, 21.8f * s)
            lineTo(3.2f * s, 20f * s); lineTo(8.2f * s, 14.9f * s); lineTo(4.6f * s, 11.3f * s); lineTo(6.6f * s, 9.3f * s)
            lineTo(11.2f * s, 9.7f * s); close()
        }
        drawPath(p, color)
    }
}

/** Folder names as a path to read: `Siriraj › ตารางเวรเภสัช`. */
fun folderTrail(path: String) = path.split('/').joinToString(" › ")

/**
 * Choosing a task's one folder (owner's pick, mock way 1): search, the folders used lately, or step through them
 * one level at a time; a new folder can be made where you stand. With [labels], the labels row toggles them too.
 * [current] is the folder the task is in now; [onPick] gets the folder chosen, or null to take the task out of it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolderPicker(
    state: UiState,
    current: String?,
    labels: List<String>?,
    onLabel: ((String) -> Unit)?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = remember(state.tasks, state.branchStates) {
        val branches = state.branchStates.keys.map { (p, b) -> if (b.isEmpty()) p else "$p/$b" }.toSet()
        Folders.paths(state.tasks + state.parked, branches)
    }
    val counts = remember(state.tasks) {
        val open = state.tasks.filter { it.isOpen && it.list == null }.mapNotNull { Folders.of(it) }
        all.associateWith { p -> open.count { it == p || it.startsWith("$p/") } }
    }
    val recent = remember(state.tasks) { Folders.recent(state.tasks.filter { it.list == null }, 3) }
    var query by remember { mutableStateOf("") }
    var at by remember { mutableStateOf(current?.substringBeforeLast('/', "") ?: "") }
    var naming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    fun under(p: String) = all.filter { if (p.isEmpty()) '/' !in it else it.startsWith("$p/") && it.count { c -> c == '/' } == p.count { c -> c == '/' } + 1 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.raised) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(tr("เก็บในโฟลเดอร์", "Keep in a folder"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            current?.let { Text(tr("ตอนนี้อยู่ใน ", "Now in ") + folderTrail(it), Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.caption) }
            PickerField(query, tr("ค้นหาโฟลเดอร์ เช่น wording", "Search folders, e.g. wording"), Modifier.padding(top = 12.dp)) { query = it }

            if (query.isNotBlank()) {
                val hits = Folders.search(all, query)
                Hint(tr("ผลค้นหา ${hits.size}", "${hits.size} found"))
                hits.forEach { p -> FolderRow(p, p.substringAfterLast('/'), p.substringBeforeLast('/', "").let { if (it.isEmpty()) tr("ชั้นบนสุด", "Top level") else folderTrail(it) }, p == current) { onPick(p) } }
                val typed = TaskLine.normalizeTag(query)
                if (typed.isNotEmpty() && all.none { it.equals(typed, ignoreCase = true) }) {
                    NewRow(tr("โฟลเดอร์ใหม่ \"${folderTrail(typed)}\"", "New folder \"${folderTrail(typed)}\"")) { onPick(typed) }
                }
            } else {
                if (recent.isNotEmpty()) {
                    Hint(tr("ใช้ล่าสุด", "Used lately"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { p -> Chip(p.substringAfterLast('/'), p == current, { onPick(p) }, dot = folderTint(p)) }
                    }
                }
                Hint(tr("ไล่เปิดทีละชั้น", "Step through"))
                // Where you stand: every level on the way back is a tap.
                FlowRow(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("ทั้งหมด", "All"), Modifier.clip(RoundedCornerShape(8.dp)).clickable { at = ""; naming = false }.padding(horizontal = 4.dp, vertical = 2.dp), color = C.accentText, fontSize = TS.caption)
                    if (at.isNotEmpty()) at.split('/').forEachIndexed { i, name ->
                        Text(" › ", color = C.faint, fontSize = TS.caption)
                        val to = at.split('/').take(i + 1).joinToString("/")
                        Text(name, Modifier.clip(RoundedCornerShape(8.dp)).clickable { at = to; naming = false }.padding(horizontal = 4.dp, vertical = 2.dp), color = if (to == at) C.text else C.accentText, fontSize = TS.caption)
                    }
                }
                if (at.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(C.accentSoft).border(1.dp, C.accentLine, RoundedCornerShape(12.dp))
                            .clickable { onPick(at) }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FolderGlyph(folderTint(at), 24.dp)
                        Text(tr("เก็บที่ ", "Keep in ") + at.substringAfterLast('/'), Modifier.weight(1f).padding(start = 10.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (at == current) tr("อยู่ที่นี่", "Here now") else tr("เลือก", "Choose"), color = C.accentText, fontSize = TS.body)
                    }
                }
                val kids = under(at)
                kids.forEach { p ->
                    val sub = under(p).size
                    val n = counts[p] ?: 0
                    FolderRow(
                        p, p.substringAfterLast('/'),
                        listOfNotNull(if (sub > 0) tr("$sub โฟลเดอร์ย่อย", "$sub folders") else null, tr("$n งาน", "$n tasks")).joinToString(", "),
                        p == current, step = true,
                    ) { at = p; naming = false }
                }
                if (kids.isEmpty()) Text(tr("ไม่มีโฟลเดอร์ย่อย", "No folders inside"), Modifier.padding(vertical = 10.dp, horizontal = 4.dp), color = C.faint, fontSize = TS.body)
                if (naming) {
                    PickerField(newName, tr("ชื่อโฟลเดอร์ใหม่", "New folder name"), Modifier.padding(top = 8.dp), onDone = {
                        val name = TaskLine.normalizeTag(newName).trim('/')
                        if (name.isNotEmpty()) onPick(if (at.isEmpty()) name else "$at/$name")
                    }) { newName = it }
                } else {
                    NewRow(if (at.isEmpty()) tr("โฟลเดอร์ใหม่", "New folder") else tr("โฟลเดอร์ใหม่ใน ", "New folder in ") + at.substringAfterLast('/')) { naming = true; newName = "" }
                }
            }

            if (labels != null && onLabel != null) {
                Hint(tr("ป้าย (ติดได้หลายอัน แยกจากโฟลเดอร์)", "Labels (any number, apart from the folder)"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shown = (Folders.LABELS.filter { Folders.isLabel(it) } + labels).distinct()
                    shown.forEach { l -> Chip("#$l", l in labels, { onLabel(l) }) }
                }
            }
            if (current != null) {
                Text(
                    tr("เอาออกจากโฟลเดอร์", "Take out of the folder"),
                    Modifier.padding(top = 16.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(null) }.padding(horizontal = 4.dp, vertical = 6.dp),
                    color = C.red, fontSize = TS.body,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, Modifier.padding(top = 16.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption)

@Composable
private fun FolderRow(path: String, name: String, sub: String, here: Boolean, step: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderGlyph(folderTint(path), 28.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, color = if (here) C.accentText else C.text, fontSize = TS.body, fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = C.faint, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (step) Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
    }
    Box(Modifier.padding(start = 44.dp).fillMaxWidth().height(1.dp).background(C.divider))
}

@Composable
private fun NewRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+", Modifier.width(28.dp), color = C.accentText, fontSize = TS.title)
        Text(label, Modifier.padding(start = 12.dp), color = C.accentText, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PickerField(value: String, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Search),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp)).padding(14.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body)
                    inner()
                }
                if (onDone != null) Text(tr("สร้าง", "Make"), Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDone() }.padding(horizontal = 8.dp, vertical = 2.dp), color = C.accentText, fontSize = TS.body)
            }
        },
    )
}

/** The folder of a task as a short label for a chip: its own name, or null when it sits in none. */
fun folderChipText(t: Task): String? = Folders.of(t)?.substringAfterLast('/')
