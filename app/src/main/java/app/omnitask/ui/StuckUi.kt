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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.omnitask.model.Focus
import app.omnitask.model.StuckLog
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/*
 * The stuck log drawn as a path: one line down the left, a dot for each point on it. Colours come from the theme,
 * so the Graphite look keeps them quiet: the line and notes in grey, finished subtasks and today in the accent.
 */

private enum class Dot { START, NOTE, DONE, NOW }

/** One point on the path: the dot on the line at its left, [content] beside it. */
@Composable
private fun PathPoint(dot: Dot, first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val line = C.control
    val grey = C.faint
    val accent = C.accent
    val hole = C.raised
    Box(
        Modifier.fillMaxWidth().drawBehind {
            val x = 6.dp.toPx()
            val cy = 11.dp.toPx()
            val r = 5.dp.toPx()
            val w = 2.dp.toPx()
            if (!first) drawLine(line, Offset(x, 0f), Offset(x, cy), w)
            if (!last) drawLine(line, Offset(x, cy), Offset(x, size.height), w)
            when (dot) {
                Dot.DONE, Dot.NOW -> drawCircle(accent, r, Offset(x, cy))
                Dot.NOTE, Dot.START -> {
                    drawCircle(hole, r, Offset(x, cy))
                    drawCircle(grey, r - w / 2, Offset(x, cy), style = Stroke(w))
                }
            }
        }.padding(start = 20.dp, bottom = if (last) 0.dp else 8.dp),
    ) { content() }
}

private fun dayText(day: LocalDate, today: LocalDate) = if (day == today) tr("วันนี้", "Today") else day.format(SHORT_DATE)

private fun eventText(e: StuckLog.Event) = when (e.step) {
    StuckLog.Step.START -> tr("เริ่มงาน", "Started")
    StuckLog.Step.DONE -> tr("งานย่อยเสร็จ: ", "Subtask done: ") + e.text
    StuckLog.Step.NOTE -> e.text.ifEmpty { tr("(ไม่ได้เขียนเหตุผล)", "(no reason written)") }
}

/**
 * The task's path, oldest first. With [now], the path ends at today and [now] (the box to write in, or the add
 * button) sits on that last point.
 */
@Composable
fun StuckPath(events: List<StuckLog.Event>, today: LocalDate, now: (@Composable () -> Unit)? = null) {
    Column {
        events.forEachIndexed { i, e ->
            val dot = when (e.step) {
                StuckLog.Step.START -> Dot.START
                StuckLog.Step.NOTE -> Dot.NOTE
                StuckLog.Step.DONE -> Dot.DONE
            }
            PathPoint(dot, first = i == 0, last = i == events.lastIndex && now == null) {
                Column {
                    Text(dayText(e.day, today), color = C.faint, fontSize = TS.caption)
                    Text(eventText(e), color = if (e.step == StuckLog.Step.DONE) C.text else C.text2, fontSize = TS.body)
                }
            }
        }
        if (now != null) {
            PathPoint(Dot.NOW, first = events.isEmpty(), last = true) {
                Column {
                    Text(tr("วันนี้", "Today"), color = C.faint, fontSize = TS.caption)
                    now()
                }
            }
        }
    }
}

/** The box to write the reason in. */
@Composable
fun StuckField(text: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont, lineHeight = 1.45.em),
        cursorBrush = SolidColor(C.accent),
        modifier = Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.sunken)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text(tr("ตอนนี้ติดอะไร", "What holds it up now?"), color = C.faint, fontSize = TS.body)
                inner()
            }
        },
    )
}

/**
 * Quick picks that start the reason. Picking one puts it at the front of the text (in place of another pick), so
 * "รอคนอื่น: รอรูปจากพี่เอ" stays one line and the words already typed are kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StuckChips(text: String, onChange: (String) -> Unit) {
    val labels = StuckLog.Reason.entries.map { it.label }
    val picked = labels.firstOrNull { text == it || text.startsWith("$it:") }
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            Chip(label, label == picked, {
                val rest = picked?.let { text.removePrefix(it).removePrefix(":").trim() } ?: text.trim()
                onChange(if (label == picked) rest else if (rest.isEmpty()) label else "$label: $rest")
            })
        }
    }
}

/** The reason box and the quick picks, for the review deck where there is no path to hang them on. */
@Composable
fun StuckInput(text: String, onChange: (String) -> Unit) {
    Column {
        StuckField(text, onChange)
        StuckChips(text, onChange)
    }
}

/** "ขยับล่าสุด 12 วันก่อน", or that nothing is noted yet: what Focus says under a stuck task. */
fun stuckStatus(task: Task, subtasks: List<Task>, today: LocalDate): String {
    val last = StuckLog.lastMove(task, subtasks) ?: return tr("ยังไม่ได้จด", "nothing noted")
    val days = ChronoUnit.DAYS.between(last, today)
    return if (days <= 0L) tr("ขยับล่าสุดวันนี้", "moved today") else tr("ขยับล่าสุด $days วันก่อน", "moved $days days ago")
}

/** A bar a week, oldest first: low and grey when nothing happened, a grey note, a tall accent bar for a finished subtask. */
@Composable
fun WeekBars(weeks: List<StuckLog.Week>, modifier: Modifier = Modifier) {
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEach { w ->
            Box(
                Modifier.width(6.dp).height(if (w == StuckLog.Week.DONE) 14.dp else 5.dp).clip(RoundedCornerShape(2.dp))
                    .background(
                        when (w) {
                            StuckLog.Week.DONE -> C.accent
                            StuckLog.Week.NOTE -> C.faint
                            StuckLog.Week.NONE -> C.control
                        },
                    ),
            )
        }
    }
}

/**
 * The task's path and a new note at its end: from a row on Focus or the task sheet. One pick and Save is enough.
 */
@Composable
fun StuckDialog(task: Task, subtasks: List<Task>, today: LocalDate, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StuckPath(StuckLog.events(task, subtasks), today) { StuckField(text) { text = it } }
                StuckChips(text) { text = it }
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t.title, Modifier.clickable { onDismiss(); onOpen(t) }, style = MaterialTheme.typography.titleMedium, color = C.text)
                    Focus.ageDays(t, state.today)?.let {
                        Text(tr("เปิดมา $it วัน", "open $it days"), Modifier.padding(top = 2.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption)
                    }
                    StuckPath(StuckLog.events(t, state.subtasksOf(t)).takeLast(4), state.today) { StuckField(text) { text = it } }
                    StuckChips(text) { text = it }
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

/** Every task with notes, each as its own path, the most recently explained first; tapping a task opens it. */
@Composable
fun StuckTimeline(state: UiState, onOpen: (Task) -> Unit, onDismiss: () -> Unit) {
    // Finished tasks keep their notes in the archive; this shows the tasks the app has loaded.
    val tasks = StuckLog.byTask(state.allTasks)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("บันทึกงานค้าง", "Stuck log")) },
        text = {
            if (tasks.isEmpty()) {
                Text(tr("ยังไม่มีบันทึก จดได้จากหน้างาน หรือตอนทบทวน", "Nothing yet. Add a note from a task or while reviewing."), color = C.text2)
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(tasks) { (t, events) ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(t) }.padding(top = 10.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, Modifier.weight(1f), color = if (t.isOpen) C.text else C.faint, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Focus.ageDays(t, state.today)?.let { Text(tr("เปิดมา $it วัน", "open $it days"), color = C.muted, fontSize = TS.caption) }
                            }
                            Box(Modifier.padding(top = 6.dp)) {
                                StuckPath(events.filter { it.step != StuckLog.Step.START }, state.today)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) } },
    )
}

/**
 * On the task sheet, right under the details for a task that is late, old or already explained: subtasks done, and
 * the path from the start to today with the button to add a note on today's point.
 */
@Composable
fun StuckBlock(task: Task, subtasks: List<Task>, today: LocalDate, onAdd: () -> Unit, onLog: () -> Unit) {
    val done = subtasks.count { it.status == app.omnitask.model.Status.DONE }
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("ความเป็นไป", "How it is going"), Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
            if (subtasks.isNotEmpty()) Text(tr("งานย่อย $done/${subtasks.size}", "Subtasks $done/${subtasks.size}"), color = C.muted, fontSize = TS.caption)
        }
        if (subtasks.isNotEmpty()) {
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.control)) {
                Box(Modifier.fillMaxWidth(done.toFloat() / subtasks.size).height(5.dp).background(C.accent))
            }
        }
        Box(Modifier.padding(top = 10.dp)) {
            StuckPath(StuckLog.events(task, subtasks), today) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("+ จด", "+ Note"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAdd).padding(vertical = 4.dp),
                        color = C.accentText, fontSize = TS.body,
                    )
                    Box(Modifier.weight(1f))
                    Text(
                        tr("ทุกงาน", "All tasks"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLog).padding(horizontal = 6.dp, vertical = 4.dp),
                        color = C.text2, fontSize = TS.caption,
                    )
                }
            }
        }
    }
}

/** The bars on a Focus row, a tap target of their own that opens [StuckDialog]. */
@Composable
fun WeekBarsButton(weeks: List<StuckLog.Week>, onClick: () -> Unit) {
    Box(Modifier.size(width = 80.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.CenterEnd) {
        WeekBars(weeks)
    }
}
