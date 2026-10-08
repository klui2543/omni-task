package app.omnitask.ui

import androidx.compose.foundation.background
import app.omnitask.model.CustomKind
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.Focus
import app.omnitask.model.Task
import app.omnitask.model.TaskKind
import app.omnitask.model.tr

/** The "ประเภทงาน" field in the edit sheet: a choice, stored as a tag behind the scenes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindField(task: Task, state: UiState, vm: TaskViewModel) {
    var askingWho by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    val custom = CustomKind.of(task, state.customKinds)
    val kind = if (custom != null) null else TaskKind.of(task)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TaskKind.entries.filter { it == TaskKind.NORMAL || it == kind || it !in state.hiddenKinds }.forEach { k ->
            Chip(k.label, k == kind, {
                when {
                    k == TaskKind.WAITING -> askingWho = true
                    k != kind -> vm.setKind(task, k)
                }
            })
        }
        state.customKinds.forEach { c -> Chip(c.label, c == custom, { if (c != custom) vm.setCustomKind(task, c) }) }
        Chip(tr("จัดการประเภท", "Manage kinds"), false, { managing = true })
    }
    val hint = when (kind) {
        null -> null
        TaskKind.NORMAL -> null
        TaskKind.WAITING -> (Focus.waitingFor(task)?.let { tr("$it รออยู่", "$it is waiting.") } ?: tr("มีคนรออยู่", "Someone is waiting.")) +
            tr(" ขึ้นในการ์ด \"คนรออยู่\" ตามที่รอนานสุด", " Shows in the \"Waiting\" card, longest wait first")
        TaskKind.FUTURE -> tr("หมุนเวียนขึ้นหน้าโฟกัสวันละงาน ทบทวนทุก 14 วัน", "Rotates onto Focus one a day, reviewed every 14 days")
        TaskKind.SOMEDAY -> tr("ไม่ขึ้นในรายการหลัก แต่จะกลับมาให้ทบทวนทุก 30 วัน ไม่หายไปไหน", "Hidden from the main lists, but comes back for review every 30 days")
    }
    hint?.let { Text(it, Modifier.padding(top = 6.dp), color = C.faint, fontSize = TS.caption) }

    if (askingWho) {
        val names = state.tasks.mapNotNull { Focus.waitingFor(it) }.distinct().sorted()
        TextDialog(
            title = tr("ใครรองานนี้", "Who is waiting on this?"),
            initial = Focus.waitingFor(task) ?: "",
            placeholder = tr("ชื่อ (เว้นว่างได้)", "Name (optional)"),
            suggestions = names,
            confirm = tr("ตั้งเป็นมีคนรอ", "Mark as waiting"),
            onConfirm = { vm.setKind(task, TaskKind.WAITING, it); askingWho = false },
            onDismiss = { askingWho = false },
        )
    }
    if (managing) KindManager(state, vm) { managing = false }
}

/** Make new kinds, forget them, and hide the built-in ones that are not used. */
@Composable
private fun KindManager(state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ประเภทงาน", "Task kinds")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(tr("ประเภทที่มากับแอป", "Built in"), color = C.muted, fontSize = TS.caption)
                TaskKind.entries.filter { it != TaskKind.NORMAL }.forEach { k ->
                    val hidden = k in state.hiddenKinds
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k.label, Modifier.weight(1f), color = if (hidden) C.faint else C.text, fontSize = TS.body)
                        Text(
                            if (hidden) tr("แสดง", "Show") else tr("ซ่อน", "Hide"),
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.toggleHiddenKind(k) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = C.accentText, fontSize = TS.body,
                        )
                    }
                }
                Text(
                    tr("ซ่อนแล้วการ์ดของประเภทนั้นในหน้าโฟกัสจะหายไปด้วย", "Hiding one also hides its card on Focus"),
                    color = C.faint, fontSize = TS.caption,
                )
                Text(tr("ประเภทของคุณ", "Yours"), Modifier.padding(top = 14.dp), color = C.muted, fontSize = TS.caption)
                if (state.customKinds.isEmpty()) Text(tr("ยังไม่มี", "None yet"), Modifier.padding(vertical = 8.dp), color = C.faint, fontSize = TS.body)
                state.customKinds.forEach { c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.label, color = C.text, fontSize = TS.body)
                            Text("#${c.tag}", color = C.faint, fontSize = TS.caption)
                        }
                        Text(
                            tr("ลบ", "Remove"),
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.removeCustomKind(c) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = C.red, fontSize = TS.body,
                        )
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallField(emoji, "🏷️", Modifier.width(56.dp)) { emoji = firstGlyph(it) }
                    SmallField(name, tr("ชื่อประเภทใหม่", "New kind"), Modifier.padding(start = 8.dp).weight(1f)) { name = it }
                }
                if (name.isNotBlank()) Text(tr("จะติดแท็ก #${CustomKind.tagFor(name)}", "Tags tasks #${CustomKind.tagFor(name)}"), Modifier.padding(top = 4.dp), color = C.faint, fontSize = TS.caption)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) { vm.addCustomKind(name, emoji); name = ""; emoji = "" } else onDismiss() }) {
                Text(if (name.isNotBlank()) tr("เพิ่ม", "Add") else tr("เสร็จ", "Done"), color = C.accent)
            }
        },
    )
}

@Composable
private fun SmallField(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body)
                inner()
            }
        },
    )
}

@Composable
private fun TextDialog(
    title: String, initial: String, placeholder: String, suggestions: List<String>,
    confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(title) },
        text = {
            Column {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(placeholder, color = C.faint, fontSize = TS.body)
                            inner()
                        }
                    },
                )
                if (suggestions.isNotEmpty()) {
                    LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 200.dp)) {
                        items(suggestions.filter { text.isBlank() || it.contains(text, ignoreCase = true) }) { s ->
                            Text(s, Modifier.fillMaxWidth().clickable { onConfirm(s) }.padding(vertical = 10.dp, horizontal = 4.dp), color = C.accentText)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm, color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}

/** Picks which tasks are "ลงทุนอนาคต": open work without a deadline, searchable. */
@Composable
fun FuturePicker(state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val candidates = state.tasks.filter { it.isOpen && it.due == null && !Focus.isWaiting(it) && !Focus.isFutureWork(it) }
        .filter { text.isBlank() || it.title.contains(text.trim(), ignoreCase = true) }
        .sortedWith(compareBy<Task>({ it.priority.ordinal }, { it.created }))
    val chosen = state.tasks.filter { it.isOpen && Focus.isFutureWork(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("เลือกงานลงทุนอนาคต", "Pick future work")) },
        text = {
            Column {
                Text(tr("งานที่ไม่มีเดดไลน์แต่สำคัญกับชีวิต หน้าโฟกัสจะหยิบขึ้นมาวันละงาน", "Work with no deadline that matters for your life. Focus brings up one a day."), color = C.text2, fontSize = TS.body)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(tr("ค้นหางาน", "Search tasks"), color = C.faint, fontSize = TS.body)
                            inner()
                        }
                    },
                )
                LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 340.dp)) {
                    if (chosen.isNotEmpty()) {
                        item { Text(tr("เลือกไว้แล้ว ${chosen.size} งาน (แตะเพื่อเอาออก)", "${chosen.size} picked (tap to remove)"), Modifier.padding(vertical = 6.dp), color = C.muted, fontSize = TS.caption) }
                        items(chosen, key = { "c" + it.key }) { t -> PickRow(t, true) { vm.setKind(t, TaskKind.NORMAL) } }
                        item { Text(tr("งานอื่นที่ไม่มีเดดไลน์", "Other tasks without a deadline"), Modifier.padding(top = 10.dp, bottom = 6.dp), color = C.muted, fontSize = TS.caption) }
                    }
                    items(candidates, key = { it.key }) { t -> PickRow(t, false) { vm.setKind(t, TaskKind.FUTURE) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("เสร็จ", "Done"), color = C.accent) } },
    )
}

@Composable
private fun PickRow(t: Task, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (on) C.accent else C.sunken),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(13.dp)) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(t.title, color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(if (Focus.isSomeday(t)) tr("พักไว้", "Someday") else null, t.noteName.takeIf { it.isNotEmpty() }).joinToString(", ")
            if (sub.isNotEmpty()) Text(sub, color = C.faint, fontSize = TS.caption)
        }
    }
}

/** On Focus: "N งานไม่มีเดดไลน์รอทบทวน", opening a one-at-a-time review. */
@Composable
fun ReviewPrompt(state: UiState, onStart: () -> Unit) {
    val n = state.toReview.size
    if (n == 0) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).clickable(onClick = onStart).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.refresh, null, tint = C.accentText, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(tr("$n งานไม่มีเดดไลน์ถึงรอบทบทวน", "$n tasks without a deadline are due for review"), color = C.text, fontSize = TS.body)
            Text(tr("ทีละงาน ทำ พัก หรือทิ้ง ใช้ไม่ถึง 2 นาที", "One at a time: do, park or drop. Under 2 minutes."), color = C.muted, fontSize = TS.caption)
        }
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
    }
}

/** One task at a time: decide, and it disappears from the queue until its next review. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewDeck(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, onDismiss: () -> Unit) {
    val queue = state.toReview
    val t = queue.firstOrNull()
    var handled by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(if (t == null) tr("ทบทวนครบแล้ว", "All reviewed") else tr("ทบทวนงาน (เหลือ ${queue.size})", "Review (${queue.size} left)")) },
        text = {
            if (t == null) {
                Text(
                    if (handled > 0) {
                        tr("จัดการไป $handled งาน งานที่เหลือจะกลับมาเมื่อถึงรอบ", "Handled $handled tasks. The rest come back when they are due.")
                    } else {
                        tr("ไม่มีงานค้างทบทวน", "Nothing to review")
                    },
                    color = C.text2,
                )
            } else {
                Column {
                    Text(t.title, Modifier.clickable { onOpen(t) }, style = MaterialTheme.typography.titleMedium, color = C.text)
                    val age = Focus.ageDays(t, state.today)
                    val since = state.reviewed[t.title]
                    Text(
                        listOfNotNull(
                            TaskKind.of(t).takeIf { it != TaskKind.NORMAL }?.label,
                            age?.let { tr("สร้างมา $it วัน", "created $it days ago") },
                            since?.let { tr("ทบทวนล่าสุด ${it.format(SHORT_DATE)}", "last reviewed ${it.format(SHORT_DATE)}") },
                        ).joinToString(", "),
                        Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.caption,
                    )
                    Text(tr("ยังอยากทำไหม", "Still want to do it?"), Modifier.padding(top = 14.dp, bottom = 8.dp), color = C.text2, fontSize = TS.body)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fun act(a: TaskViewModel.ReviewAction) { vm.review(t, a); handled++ }
                        ReviewButton(tr("ทำเสาร์นี้", "Do this Saturday"), C.lime) { act(TaskViewModel.ReviewAction.THIS_WEEK) }
                        if (!Focus.isFutureWork(t)) ReviewButton(tr("ลงทุนอนาคต", "Future"), C.accent) { act(TaskViewModel.ReviewAction.FUTURE) }
                        if (!Focus.isSomeday(t)) ReviewButton(tr("พักไว้ก่อน", "Someday"), C.amber) { act(TaskViewModel.ReviewAction.SOMEDAY) }
                        ReviewButton(tr("เก็บไว้แบบเดิม", "Keep as is"), C.text2) { act(TaskViewModel.ReviewAction.KEEP) }
                        ReviewButton(tr("ทิ้ง", "Drop"), C.red) { act(TaskViewModel.ReviewAction.DROP) }
                    }
                    Text(tr("ทิ้ง = ทำเครื่องหมายยกเลิก [-] บรรทัดยังอยู่ในไฟล์", "Drop = mark cancelled [-]. The line stays in the file."), Modifier.padding(top = 10.dp), color = C.faint, fontSize = TS.caption)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (t == null) tr("ปิด", "Close") else tr("พอก่อน", "Stop for now"), color = C.text2) } },
    )
}

@Composable
private fun ReviewButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        color = color, fontSize = TS.body,
    )
}
