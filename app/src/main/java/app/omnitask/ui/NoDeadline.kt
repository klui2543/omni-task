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

/** The "ประเภทงาน" field in the edit sheet: a choice, stored as a tag behind the scenes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindField(task: Task, state: UiState, vm: TaskViewModel) {
    var askingWho by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf(false) }
    val kind = TaskKind.of(task)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TaskKind.entries.forEach { k ->
            Chip(k.label, k == kind, {
                when {
                    k == TaskKind.WAITING -> askingWho = true
                    k != kind -> vm.setKind(task, k)
                }
            })
        }
    }
    val hint = when (kind) {
        TaskKind.NORMAL -> null
        TaskKind.WAITING -> (Focus.waitingFor(task)?.let { "$it รออยู่" } ?: "มีคนรออยู่") + " ขึ้นในการ์ด \"คนรออยู่\" ตามที่รอนานสุด"
        TaskKind.FUTURE -> "หมุนเวียนขึ้นหน้าโฟกัสวันละงาน ทบทวนทุก 14 วัน"
        TaskKind.SOMEDAY -> "ไม่ขึ้นในรายการหลัก แต่จะกลับมาให้ทบทวนทุก 30 วัน ไม่หายไปไหน"
    }
    hint?.let { Text(it, Modifier.padding(top = 6.dp), color = C.faint, fontSize = 12.sp) }

    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken)
            .clickable { editingStep = true }.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("ก้าวแรกที่เล็กที่สุด", color = C.muted, fontSize = 12.5.sp)
            Text(task.firstStep ?: "งานใหญ่เริ่มยาก เขียนสิ่งที่ทำได้ใน 10 นาที", color = if (task.firstStep != null) C.text else C.faint, fontSize = 14.sp)
        }
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
    }

    if (askingWho) {
        val names = state.tasks.mapNotNull { Focus.waitingFor(it) }.distinct().sorted()
        TextDialog(
            title = "ใครรองานนี้",
            initial = Focus.waitingFor(task) ?: "",
            placeholder = "ชื่อ (เว้นว่างได้)",
            suggestions = names,
            confirm = "ตั้งเป็นมีคนรอ",
            onConfirm = { vm.setKind(task, TaskKind.WAITING, it); askingWho = false },
            onDismiss = { askingWho = false },
        )
    }
    if (editingStep) {
        TextDialog(
            title = "ก้าวแรกที่เล็กที่สุด",
            initial = task.firstStep ?: "",
            placeholder = "เช่น เปิดไฟล์แล้วเขียนหัวข้อ 3 ข้อ",
            suggestions = emptyList(),
            confirm = "บันทึก",
            onConfirm = { vm.setFirstStep(task, it); editingStep = false },
            onDismiss = { editingStep = false },
        )
    }
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
                    textStyle = TextStyle(color = C.text, fontSize = 15.sp, fontFamily = Prompt),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(placeholder, color = C.faint, fontSize = 15.sp)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก", color = C.text2) } },
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
        title = { Text("เลือกงานลงทุนอนาคต") },
        text = {
            Column {
                Text("งานที่ไม่มีเดดไลน์แต่สำคัญกับชีวิต หน้าโฟกัสจะหยิบขึ้นมาวันละงาน", color = C.text2, fontSize = 13.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.text, fontSize = 15.sp, fontFamily = Prompt),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text("ค้นหางาน", color = C.faint, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 340.dp)) {
                    if (chosen.isNotEmpty()) {
                        item { Text("เลือกไว้แล้ว ${chosen.size} งาน (แตะเพื่อเอาออก)", Modifier.padding(vertical = 6.dp), color = C.muted, fontSize = 12.5.sp) }
                        items(chosen, key = { "c" + it.key }) { t -> PickRow(t, true) { vm.setKind(t, TaskKind.NORMAL) } }
                        item { Text("งานอื่นที่ไม่มีเดดไลน์", Modifier.padding(top = 10.dp, bottom = 6.dp), color = C.muted, fontSize = 12.5.sp) }
                    }
                    items(candidates, key = { it.key }) { t -> PickRow(t, false) { vm.setKind(t, TaskKind.FUTURE) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("เสร็จ", color = C.accent) } },
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
            Text(t.title, color = C.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(if (Focus.isSomeday(t)) "พักไว้" else null, t.noteName.takeIf { it.isNotEmpty() }).joinToString(", ")
            if (sub.isNotEmpty()) Text(sub, color = C.faint, fontSize = 12.sp)
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
            Text("$n งานไม่มีเดดไลน์ถึงรอบทบทวน", color = C.text, fontSize = 14.sp)
            Text("ทีละงาน ทำ พัก หรือทิ้ง ใช้ไม่ถึง 2 นาที", color = C.muted, fontSize = 12.5.sp)
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
        title = { Text(if (t == null) "ทบทวนครบแล้ว" else "ทบทวนงาน (เหลือ ${queue.size})") },
        text = {
            if (t == null) {
                Text(if (handled > 0) "จัดการไป $handled งาน งานที่เหลือจะกลับมาเมื่อถึงรอบ" else "ไม่มีงานค้างทบทวน", color = C.text2)
            } else {
                Column {
                    Text(t.title, Modifier.clickable { onOpen(t) }, style = MaterialTheme.typography.titleMedium, color = C.text)
                    val age = Focus.ageDays(t, state.today)
                    val since = state.reviewed[t.title]
                    Text(
                        listOfNotNull(
                            TaskKind.of(t).takeIf { it != TaskKind.NORMAL }?.label,
                            age?.let { "สร้างมา $it วัน" },
                            since?.let { "ทบทวนล่าสุด ${it.format(SHORT_DATE)}" },
                        ).joinToString(", "),
                        Modifier.padding(top = 4.dp), color = C.muted, fontSize = 12.5.sp,
                    )
                    t.firstStep?.let { Text("ก้าวแรก: $it", Modifier.padding(top = 6.dp), color = C.accentText, fontSize = 13.sp) }
                    Text("ยังอยากทำไหม", Modifier.padding(top = 14.dp, bottom = 8.dp), color = C.text2, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fun act(a: TaskViewModel.ReviewAction) { vm.review(t, a); handled++ }
                        ReviewButton("ทำเสาร์นี้", C.lime) { act(TaskViewModel.ReviewAction.THIS_WEEK) }
                        if (!Focus.isFutureWork(t)) ReviewButton("ลงทุนอนาคต", C.accent) { act(TaskViewModel.ReviewAction.FUTURE) }
                        if (!Focus.isSomeday(t)) ReviewButton("พักไว้ก่อน", C.amber) { act(TaskViewModel.ReviewAction.SOMEDAY) }
                        ReviewButton("เก็บไว้แบบเดิม", C.text2) { act(TaskViewModel.ReviewAction.KEEP) }
                        ReviewButton("ทิ้ง", C.red) { act(TaskViewModel.ReviewAction.DROP) }
                    }
                    Text("ทิ้ง = ทำเครื่องหมายยกเลิก [-] บรรทัดยังอยู่ในไฟล์", Modifier.padding(top = 10.dp), color = C.faint, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (t == null) "ปิด" else "พอก่อน", color = C.text2) } },
    )
}

@Composable
private fun ReviewButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        color = color, fontSize = 13.5.sp,
    )
}
