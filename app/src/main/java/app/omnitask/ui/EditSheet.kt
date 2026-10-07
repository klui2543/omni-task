package app.omnitask.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.omnitask.data.ImageAttach
import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.model.Priority
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditSheet(task: Task, state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf<DateField?>(null) }
    var addingTag by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attachImage(task, uri)
    }

    SheetFrame(onDismiss) {
        Text(task.title, style = MaterialTheme.typography.titleMedium, color = C.text)
        Text("${task.filePath.substringAfterLast('/')} บรรทัด ${task.lineIndex + 1}", color = C.faint, fontSize = 12.5.sp)
        task.textNotes.forEach { Text(it, Modifier.padding(top = 4.dp), color = C.muted, fontSize = 13.sp) }

        Segmented(
            Status.entries.map { it to it.label }, task.status, { vm.setStatus(task, it) },
            Modifier.padding(top = 14.dp).fillMaxWidth(),
        )

        Label("ความสำคัญ")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Priority.entries.forEach { p -> Chip(p.label, task.priority == p, { vm.setPriority(task, p) }, dot = p.tint) }
        }

        Label("Tag")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            task.tags.filterNot { it.startsWith("remind-at-") }.forEach { tag ->
                Row(
                    Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(C.accentSoft)
                        .clickable { vm.removeTag(task, tag) }.padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("#$tag", color = C.accentText, fontSize = 13.sp)
                    Icon(Ic.close, "ลบ #$tag", tint = C.muted, modifier = Modifier.size(13.dp))
                }
            }
            Row(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).border(1.dp, C.control, RoundedCornerShape(17.dp))
                    .clickable { addingTag = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Ic.plus, null, tint = C.text2, modifier = Modifier.size(13.dp))
                Text("เพิ่ม", color = C.text2, fontSize = 13.sp)
            }
        }

        Row(Modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("รูปแนบ (${task.attachments.size})", Modifier.weight(1f), color = C.muted, fontSize = 13.sp)
            Text("บันทึกเป็น WebP เสมอ", color = C.faint, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            task.attachments.forEach { name ->
                Thumb(name, vm, Modifier.combinedClickable(onClick = { viewing = name }, onLongClick = { confirmDelete = name }))
            }
            Column(
                Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, C.control, RoundedCornerShape(12.dp))
                    .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Ic.camera, null, tint = C.text2, modifier = Modifier.size(22.dp))
                Text("แนบรูป", color = C.text2, fontSize = 12.sp)
            }
        }
        if (task.attachments.isNotEmpty()) {
            Text("แตะเพื่อดูเต็มจอ กดค้างเพื่อลบ", Modifier.padding(top = 6.dp), color = C.faint, fontSize = 12.sp)
        }

        Label("วันที่และรายละเอียด")
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(C.sunken)) {
            FieldRow("วันครบกำหนด", task.due?.format(SHORT_DATE)) { picking = DateField.DUE }
            FieldRow("วันนัดทำ", task.scheduled?.format(SHORT_DATE)) { picking = DateField.SCHEDULED }
            FieldRow("วันเริ่ม", task.start?.format(SHORT_DATE)) { picking = DateField.START }
            FieldRow("วันที่สร้าง", task.created?.format(SHORT_DATE), null)
            FieldRow("วนซ้ำ", task.recurrence, null)
            FieldRow("เวลาเตือน", task.reminderTime?.let { "%02d:%02d".format(it.hour, it.minute) }, null)
            FieldRow("โปรเจกต์", Projects.projectOf(task), null)
            FieldRow("ต้องรอ", Projects.waitingOn(task, state.tasks), null)
        }
        Text("วนซ้ำและเวลาเตือนยังแก้ได้ใน TaskForge ก่อน", Modifier.padding(top = 8.dp), color = C.faint, fontSize = 12.sp)
    }

    picking?.let { field -> DateDialog(task, field, { vm.setDate(task, field, it) }) { picking = null } }
    if (addingTag) {
        AddTagDialog(state.tags.filterNot { it.startsWith("remind-at-") || it in task.tags }, { vm.addTag(task, it); addingTag = false }) { addingTag = false }
    }
    viewing?.let { name -> ImageViewer(name, vm) { viewing = null } }
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = C.raised,
            title = { Text("ลบรูปนี้?") },
            text = { Text("ไฟล์ $name จะถูกลบออกจากโฟลเดอร์ Attachments และบรรทัดใต้งาน", color = C.text2) },
            confirmButton = { TextButton(onClick = { vm.removeImage(task, name); confirmDelete = null }) { Text("ลบ", color = C.red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("ยกเลิก", color = C.text2) } },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(116.dp), color = C.muted, fontSize = 13.5.sp)
        Text(value ?: if (onClick != null) "ตั้งค่า" else "ไม่มี", Modifier.weight(1f), color = if (value != null) C.text else C.faint, fontSize = 14.sp)
        if (onClick != null) Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun Thumb(name: String, vm: TaskViewModel, modifier: Modifier) {
    var bmp by remember(name) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) { bmp = vm.thumbnail(name) }
    Box(modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(C.raised), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Ic.image, null, tint = C.faint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ImageViewer(name: String, vm: TaskViewModel, onDismiss: () -> Unit) {
    var bmp by remember(name) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) { bmp = vm.image(name) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), name, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
            Text(name, Modifier.align(Alignment.BottomCenter).padding(24.dp), color = C.muted, fontSize = 12.sp)
        }
    }
}

/** Shown when a photo is large: keep it full size as WebP, or shrink it to 2048 px first. */
@Composable
fun AttachChoiceDialog(pending: PendingImage, onChoose: (Boolean?) -> Unit) {
    val p = pending.prepared
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        containerColor = C.raised,
        title = { Text("รูปนี้ใหญ่ ${ImageAttach.sizeLabel(p.originalBytes)} (${p.width}×${p.height})") },
        text = {
            Text(
                "ย่อเป็น ${ImageAttach.MAX_SIDE}px แล้วแปลงเป็น WebP จะเหลือประมาณ ${ImageAttach.sizeLabel((p.reduced ?: p.full).size.toLong())}\n" +
                    "ถ้าเก็บขนาดเต็มเป็น WebP จะได้ ${ImageAttach.sizeLabel(p.full.size.toLong())}\nข้อมูลตำแหน่ง GPS ในรูปจะถูกลบทั้งสองแบบ",
                color = C.text2,
            )
        },
        confirmButton = { TextButton(onClick = { onChoose(true) }) { Text("ย่อ + WebP", color = C.accent, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = { onChoose(false) }) { Text("ขนาดเต็ม", color = C.text2) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(task: Task, field: DateField, onSet: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val current = when (field) {
        DateField.START -> task.start
        DateField.SCHEDULED -> task.scheduled
        else -> task.due
    }
    val ps = rememberDatePickerState(initialSelectedDateMillis = (current ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = C.raised),
        confirmButton = {
            TextButton(onClick = {
                ps.selectedDateMillis?.let { onSet(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("ตกลง", color = C.accent) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSet(null); onDismiss() }) { Text("ล้างวันที่", color = C.red) }
                TextButton(onClick = onDismiss) { Text("ยกเลิก", color = C.text2) }
            }
        },
    ) { DatePicker(state = ps, colors = DatePickerDefaults.colors(containerColor = C.raised)) }
}

@Composable
private fun AddTagDialog(suggestions: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val query = TaskLine.normalizeTag(text)
    val matches = suggestions.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text("เพิ่ม Tag") },
        text = {
            Column {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.text, fontSize = 15.sp, fontFamily = Prompt),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Row {
                            Text("#", color = C.faint, fontSize = 15.sp)
                            Box {
                                if (text.isEmpty()) Text("เช่น รอ/พี่เอ", color = C.faint, fontSize = 15.sp)
                                inner()
                            }
                        }
                    },
                )
                LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 240.dp)) {
                    items(matches) { tag ->
                        Text("#$tag", Modifier.fillMaxWidth().clickable { onAdd(tag) }.padding(vertical = 10.dp, horizontal = 4.dp), color = C.accentText)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (query.isNotEmpty()) onAdd(query) }, enabled = query.isNotEmpty()) { Text("เพิ่ม", color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก", color = C.text2) } },
    )
}
