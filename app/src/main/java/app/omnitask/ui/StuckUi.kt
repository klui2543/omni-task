package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.omnitask.model.Focus
import app.omnitask.model.StuckLog
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.time.*

/**
 * Why a task is still open: quick picks that start the reason, and the owner's own words after them. Picking one
 * puts it at the front of the text (in place of another pick), so "รอคนอื่น: รอรูปจากพี่เอ" stays one line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StuckInput(text: String, onChange: (String) -> Unit) {
    val labels = StuckLog.Reason.entries.map { it.label }
    val picked = labels.firstOrNull { text == it || text.startsWith("$it:") }
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { label ->
                Chip(label, label == picked, {
                    val rest = picked?.let { text.removePrefix(it).removePrefix(":").trim() } ?: text.trim()
                    onChange(if (label == picked) rest else if (rest.isEmpty()) label else "$label: $rest")
                })
            }
        }
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont, lineHeight = 1.45.em),
            cursorBrush = SolidColor(C.accent),
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(12.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(tr("ติดอะไรอยู่ เขียนสั้นๆ ก็พอ", "What holds it up? A few words will do"), color = C.faint, fontSize = TS.body)
                    inner()
                }
            },
        )
    }
}

/** The task's notes so far, newest first, each with its day. */
@Composable
fun StuckHistory(task: Task, limit: Int = Int.MAX_VALUE) {
    StuckLog.entries(task).reversed().take(limit).forEach { e ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Text(e.day.format(SHORT_DATE), Modifier.padding(end = 10.dp), color = C.faint, fontSize = TS.caption)
            Text(e.reason.ifEmpty { tr("(ไม่ได้เขียนเหตุผล)", "(no reason written)") }, color = C.text2, fontSize = TS.caption)
        }
    }
}

/** Writes one note for [task]: from the task sheet, whenever the owner wants to. */
@Composable
fun StuckDialog(task: Task, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ทำไมยังค้างอยู่", "Why is it still open?")) },
        text = {
            Column {
                Text(task.title, Modifier.padding(bottom = 10.dp), color = C.muted, fontSize = TS.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StuckInput(text) { text = it }
                Text(
                    tr("บันทึกเป็นบรรทัด ${StuckLog.MARK} ใต้งานใน Obsidian พร้อมวันที่", "Saved as a dated ${StuckLog.MARK} line under the task in Obsidian"),
                    Modifier.padding(top = 8.dp), color = C.faint, fontSize = TS.caption,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }, enabled = text.isNotBlank()) { Text(tr("บันทึก", "Save"), color = if (text.isNotBlank()) C.accent else C.faint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}

/**
 * What the weekly notification opens: each task open a long while with no word on why, one at a time. Writing the
 * reason or skipping moves on; the review deck's choices (park, drop...) stay where they are.
 */
@Composable
fun StuckDeck(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, onDismiss: () -> Unit) {
    // Notes reach the list on the next load, so the ones handled here are passed over until then.
    var passed by remember { mutableStateOf(emptySet<String>()) }
    var written by remember { mutableStateOf(0) }
    var showLog by remember { mutableStateOf(false) }
    val queue = StuckLog.toAsk(state.tasks, state.today).filter { it.key !in passed }
    val t = queue.firstOrNull()
    var text by remember(t?.key) { mutableStateOf("") }
    if (showLog) {
        StuckTimeline(state, onOpen = { showLog = false; onDismiss(); onOpen(it) }) { showLog = false }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(if (t == null) tr("จดครบแล้ว", "All noted") else tr("ติดอะไรอยู่ (เหลือ ${queue.size})", "What holds it up? (${queue.size} left)")) },
        text = {
            if (t == null) {
                Text(
                    if (written > 0) tr("จดไป $written งาน ดูย้อนหลังได้ที่บันทึกงานค้าง", "Noted $written. See them all in the stuck log.") else tr("ไม่มีงานค้างนานที่ยังไม่ได้จด", "No long-open task is waiting for a note"),
                    color = C.text2,
                )
            } else {
                Column {
                    Text(t.title, Modifier.clickable { onDismiss(); onOpen(t) }, style = MaterialTheme.typography.titleMedium, color = C.text)
                    Text(
                        listOfNotNull(
                            Focus.ageDays(t, state.today)?.let { tr("เปิดมา $it วัน", "open $it days") },
                            state.progressOf(t)?.let { (d, n) -> tr("งานย่อย $d/$n", "subtasks $d/$n") },
                        ).joinToString(", "),
                        Modifier.padding(top = 4.dp, bottom = 6.dp), color = C.muted, fontSize = TS.caption,
                    )
                    StuckHistory(t, limit = 3)
                    Box(Modifier.padding(top = 8.dp)) { StuckInput(text) { text = it } }
                }
            }
        },
        confirmButton = {
            if (t == null) {
                TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) }
            } else {
                TextButton(onClick = { vm.noteStuck(t, text); written++; passed = passed + t.key }, enabled = text.isNotBlank()) {
                    Text(tr("บันทึก", "Save"), color = if (text.isNotBlank()) C.accent else C.faint)
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showLog = true }) { Text(tr("ดูบันทึก", "Log"), color = C.text2) }
                if (t != null) TextButton(onClick = { passed = passed + t.key }) { Text(tr("ข้าม", "Skip"), color = C.text2) }
            }
        },
    )
}

/** Every note of every task by day, newest first; tapping one opens its task. */
@Composable
fun StuckTimeline(state: UiState, onOpen: (Task) -> Unit, onDismiss: () -> Unit) {
    // The archive keeps finished tasks' notes too; the live tasks are the ones the app has loaded.
    val days = StuckLog.timeline(state.allTasks)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("บันทึกงานค้าง", "Stuck log")) },
        text = {
            if (days.isEmpty()) {
                Text(tr("ยังไม่มีบันทึก จดได้จากหน้างาน หรือตอนทบทวน", "Nothing yet. Add a note from a task or while reviewing."), color = C.text2)
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    days.forEach { (day, notes) ->
                        item {
                            Text(day.format(SHORT_DATE), Modifier.padding(top = 12.dp, bottom = 4.dp), color = C.muted, fontSize = TS.caption)
                        }
                        items(notes) { (t, e) ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(t) }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                                Text(t.title, color = if (t.isOpen) C.text else C.faint, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (e.reason.isNotEmpty()) Text(e.reason, color = C.text2, fontSize = TS.caption)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) } },
    )
}
