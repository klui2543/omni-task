package app.omnitask.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.omnitask.data.ImageAttach
import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.model.NoteLinks
import app.omnitask.model.Priority
import app.omnitask.model.Recurrence
import app.omnitask.model.ReminderOn
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.label
import app.omnitask.model.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditSheet(task: Task, state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf<DateField?>(null) }
    var addingTag by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var linking by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    var reminding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attachImage(task, uri)
    }

    SheetFrame(onDismiss) {
        Text(task.title, style = MaterialTheme.typography.titleMedium, color = C.text)
        Text(tr("${task.filePath.substringAfterLast('/')} บรรทัด ${task.lineIndex + 1}", "${task.filePath.substringAfterLast('/')} line ${task.lineIndex + 1}"), color = C.faint, fontSize = TS.caption)
        task.textNotes.forEach { Text(it, Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.body) }

        Segmented(
            Status.entries.map { it to it.label }, task.status, { vm.setStatus(task, it) },
            Modifier.padding(top = 14.dp).fillMaxWidth(),
        )

        Label(tr("ความสำคัญ", "Priority"))
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
                    Text("#$tag", color = C.accentText, fontSize = TS.body)
                    Icon(Ic.close, tr("ลบ #$tag", "Remove #$tag"), tint = C.muted, modifier = Modifier.size(13.dp))
                }
            }
            Row(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).border(1.dp, C.control, RoundedCornerShape(17.dp))
                    .clickable { addingTag = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Ic.plus, null, tint = C.text2, modifier = Modifier.size(13.dp))
                Text(tr("เพิ่ม", "Add"), color = C.text2, fontSize = TS.body)
            }
        }

        Label(tr("ประเภทงาน", "Task type"))
        KindField(task, state, vm)

        Label(tr("โน้ตที่เกี่ยวข้อง", "Linked notes"))
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(C.sunken)) {
            task.links.forEach { link ->
                val path = NoteLinks.resolve(link, state.notePaths)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { openNote(context, state.vaultName, path ?: "$link.md") }
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.link, null, tint = C.accentText, modifier = Modifier.size(16.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(link.substringAfterLast('/'), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            path?.substringBeforeLast('/', "")?.ifEmpty { tr("ราก Vault", "Vault root") } ?: tr("ยังไม่มีโน้ตนี้", "Note does not exist yet"),
                            color = if (path == null) C.amber else C.faint, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (link in task.linkLines) SquareButton(Ic.close, tr("เอาลิงก์ออก", "Remove link"), { vm.removeLink(task, link) })
                }
                Divider(start = 40.dp)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { linking = true }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.plus, null, tint = C.text2, modifier = Modifier.size(16.dp))
                Text(tr("ลิงก์โน้ต", "Link a note"), Modifier.padding(start = 10.dp), color = C.text2, fontSize = TS.body)
            }
        }
        if (task.links.isNotEmpty()) Text(tr("แตะเพื่อเปิดใน Obsidian", "Tap to open in Obsidian"), Modifier.padding(top = 6.dp), color = C.faint, fontSize = TS.caption)

        Row(Modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("รูปแนบ (${task.attachments.size})", "Images (${task.attachments.size})"), Modifier.weight(1f), color = C.muted, fontSize = TS.body)
            Text(tr("บันทึกเป็น WebP เสมอ", "Always saved as WebP"), color = C.faint, fontSize = TS.caption)
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
                Text(tr("แนบรูป", "Add image"), color = C.text2, fontSize = TS.caption)
            }
        }
        if (task.attachments.isNotEmpty()) {
            Text(tr("แตะเพื่อดูเต็มจอ กดค้างเพื่อลบ", "Tap to view full screen, long-press to delete"), Modifier.padding(top = 6.dp), color = C.faint, fontSize = TS.caption)
        }

        Label(tr("วันที่และรายละเอียด", "Dates and details"))
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(C.sunken)) {
            FieldRow(tr("วันครบกำหนด", "Due"), task.due?.format(SHORT_DATE)) { picking = DateField.DUE }
            FieldRow(tr("วันนัดทำ", "Scheduled"), task.scheduled?.format(SHORT_DATE)) { picking = DateField.SCHEDULED }
            FieldRow(tr("วันเริ่ม", "Start"), task.start?.format(SHORT_DATE)) { picking = DateField.START }
            FieldRow(tr("วันที่สร้าง", "Created"), task.created?.format(SHORT_DATE), null)
            FieldRow(tr("วนซ้ำ", "Repeats"), task.recurrence?.let { Recurrence.describe(it) }) { repeating = true }
            FieldRow(
                tr("เวลาเตือน", "Reminder"),
                task.reminderTime?.let {
                    "%02d:%02d".format(it.hour, it.minute) +
                        if (task.reminderOn == ReminderOn.SCHEDULED) tr(" วันนัดทำ", ", scheduled day") else tr(" วันครบกำหนด", ", due day")
                },
            ) { reminding = true }
            FieldRow(tr("โปรเจกต์", "Project"), Projects.projectOf(task), null)
            FieldRow(tr("ต้องรอ", "Waits on"), Projects.waitingOn(task, state.tasks), null)
        }
    }

    picking?.let { field -> DateDialog(task, field, { vm.setDate(task, field, it) }) { picking = null } }
    if (repeating) RepeatDialog(task, { vm.setRecurrence(task, it) }) { repeating = false }
    if (reminding) ReminderDialog(task, { time, on -> vm.setReminder(task, time, on) }) { reminding = false }
    if (addingTag) {
        AddTagDialog(state.tags.filterNot { it.startsWith("remind-at-") || it in task.tags }, { vm.addTag(task, it); addingTag = false }) { addingTag = false }
    }
    viewing?.let { name -> ImageViewer(name, vm) { viewing = null } }
    if (linking) {
        NotePicker(state.notePaths.filterNot { NoteLinks.linkText(it, state.notePaths) in task.links }, { vm.addLink(task, it); linking = false }) { linking = false }
    }
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = C.raised,
            title = { Text(tr("ลบรูปนี้?", "Delete this image?")) },
            text = { Text(
                    tr("ไฟล์ $name จะถูกลบออกจากโฟลเดอร์ Attachments และบรรทัดใต้งาน", "$name will be removed from the Attachments folder and from under the task"),
                    color = C.text2,
                ) },
            confirmButton = { TextButton(onClick = { vm.removeImage(task, name); confirmDelete = null }) { Text(tr("ลบ", "Delete"), color = C.red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
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
        Text(label, Modifier.width(116.dp), color = C.muted, fontSize = TS.body)
        Text(value ?: if (onClick != null) tr("ตั้งค่า", "Set") else tr("ไม่มี", "None"), Modifier.weight(1f), color = if (value != null) C.text else C.faint, fontSize = TS.body)
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
            Text(name, Modifier.align(Alignment.BottomCenter).padding(24.dp), color = C.muted, fontSize = TS.caption)
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
        title = { Text(tr("รูปนี้ใหญ่ ${ImageAttach.sizeLabel(p.originalBytes)} (${p.width}×${p.height})", "Large image: ${ImageAttach.sizeLabel(p.originalBytes)} (${p.width}×${p.height})")) },
        text = {
            Text(
                tr(
                    "ย่อเป็น ${ImageAttach.MAX_SIDE}px แล้วแปลงเป็น WebP จะเหลือประมาณ ${ImageAttach.sizeLabel((p.reduced ?: p.full).size.toLong())}\n" +
                        "ถ้าเก็บขนาดเต็มเป็น WebP จะได้ ${ImageAttach.sizeLabel(p.full.size.toLong())}\nข้อมูลตำแหน่ง GPS ในรูปจะถูกลบทั้งสองแบบ",
                    "Shrunk to ${ImageAttach.MAX_SIDE}px as WebP: about ${ImageAttach.sizeLabel((p.reduced ?: p.full).size.toLong())}\n" +
                        "Full size as WebP: ${ImageAttach.sizeLabel(p.full.size.toLong())}\nGPS location is removed either way",
                ),
                color = C.text2,
            )
        },
        confirmButton = { TextButton(onClick = { onChoose(true) }) { Text(tr("ย่อ + WebP", "Shrink + WebP"), color = C.accent, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = { onChoose(false) }) { Text(tr("ขนาดเต็ม", "Full size"), color = C.text2) } },
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
            }) { Text(tr("ตกลง", "OK"), color = C.accent) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSet(null); onDismiss() }) { Text(tr("ล้างวันที่", "Clear date"), color = C.red) }
                TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) }
            }
        },
    ) { DatePicker(state = ps, colors = DatePickerDefaults.colors(containerColor = C.raised)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatDialog(task: Task, onSet: (String?) -> Unit, onDismiss: () -> Unit) {
    val current = task.recurrence?.removeSuffix(" when done")?.trim()
    var rule by remember { mutableStateOf(current ?: "") }
    var whenDone by remember { mutableStateOf(task.recurrence?.trim()?.endsWith("when done") == true) }
    val full = rule.trim().let { if (it.isEmpty()) "" else if (whenDone) "$it when done" else it }
    val valid = full.isEmpty() || Recurrence.parse(full) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("วนซ้ำ", "Repeats")) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.PRESETS.forEach { p -> Chip(Recurrence.describe(p), rule.trim() == p, { rule = p }) }
                }
                Text(tr("หรือพิมพ์เองแบบปลั๊กอิน Tasks", "Or type a Tasks plugin rule"), Modifier.padding(top = 14.dp, bottom = 6.dp), color = C.muted, fontSize = TS.caption)
                BasicTextField(
                    value = rule,
                    onValueChange = { rule = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = Prompt),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (rule.isEmpty()) Text("every week on Monday", color = C.faint, fontSize = TS.body)
                            inner()
                        }
                    },
                )
                Text(
                    when {
                        full.isEmpty() -> tr("ไม่วนซ้ำ", "Does not repeat")
                        valid -> Recurrence.describe(full)
                        else -> tr("ยังอ่านรูปแบบนี้ไม่ได้", "This rule can't be read")
                    },
                    Modifier.padding(top = 6.dp), color = if (valid) C.accentText else C.red, fontSize = TS.caption,
                )
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { whenDone = !whenDone }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("นับจากวันที่ทำเสร็จ", "Count from completion"), color = C.text, fontSize = TS.body)
                        Text(tr("เช่น ตัดผมทุก 4 สัปดาห์หลังตัดครั้งล่าสุด", "e.g. a haircut 4 weeks after the last one"), color = C.muted, fontSize = TS.caption)
                    }
                    OnOff(whenDone)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(full.ifEmpty { null }); onDismiss() }, enabled = valid) { Text(tr("บันทึก", "Save"), color = if (valid) C.accent else C.faint) }
        },
        dismissButton = {
            Row {
                if (task.recurrence != null) TextButton(onClick = { onSet(null); onDismiss() }) { Text(tr("ไม่วนซ้ำ", "Stop repeating"), color = C.red) }
                TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(task: Task, onSet: (LocalTime?, ReminderOn) -> Unit, onDismiss: () -> Unit) {
    val start = task.reminderTime ?: LocalTime.of(9, 0)
    val time = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    var on by remember { mutableStateOf(task.reminderOn ?: if (task.due == null && task.scheduled != null) ReminderOn.SCHEDULED else ReminderOn.DUE) }
    val day = if (on == ReminderOn.SCHEDULED) task.scheduled ?: task.due else task.due ?: task.scheduled
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("เวลาเตือน", "Reminder")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Segmented(
                    listOf(ReminderOn.DUE to tr("วันครบกำหนด", "Due day"), ReminderOn.SCHEDULED to tr("วันนัดทำ", "Scheduled day")),
                    on, { on = it }, Modifier.fillMaxWidth().padding(bottom = 14.dp),
                )
                TimeInput(
                    state = time,
                    colors = TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor = C.accentSoft, timeSelectorSelectedContentColor = C.accentText,
                        timeSelectorUnselectedContainerColor = C.sunken, timeSelectorUnselectedContentColor = C.text,
                    ),
                )
                Text(
                    if (day == null) tr("งานนี้ยังไม่มีวันที่ ตั้งวันก่อนแล้วการเตือนจะทำงาน", "Set a date first, then the reminder will fire")
                    else tr("จะเตือน ", "Fires ") + day.format(SHORT_DATE),
                    color = if (day == null) C.amber else C.muted, fontSize = TS.caption,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSet(LocalTime.of(time.hour, time.minute), on); onDismiss() }) { Text(tr("บันทึก", "Save"), color = C.accent) } },
        dismissButton = {
            Row {
                if (task.reminderTime != null) TextButton(onClick = { onSet(null, on); onDismiss() }) { Text(tr("ไม่เตือน", "Remove"), color = C.red) }
                TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) }
            }
        },
    )
}

@Composable
private fun NotePicker(notes: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val matches = NoteLinks.search(text, notes)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ลิงก์โน้ต", "Link a note")) },
        text = {
            Column {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = Prompt),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(tr("ค้นหาชื่อโน้ต", "Search notes"), color = C.faint, fontSize = TS.body)
                            inner()
                        }
                    },
                )
                if (notes.isEmpty()) Text(tr("ยังไม่พบโน้ตใน Vault", "No notes found in the vault"), Modifier.padding(top = 12.dp), color = C.muted, fontSize = TS.body)
                LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 320.dp)) {
                    items(matches) { path ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(path) }.padding(vertical = 9.dp, horizontal = 4.dp)) {
                            Text(NoteLinks.displayName(path), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(path.substringBeforeLast('/', "").ifEmpty { tr("ราก Vault", "Vault root") }, color = C.faint, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) } },
    )
}

/** Opens a vault note in Obsidian; the vault is named after its folder. */
private fun openNote(context: Context, vault: String?, path: String) {
    val file = Uri.encode(path.removeSuffix(".md"))
    val uri = if (vault != null) "obsidian://open?vault=${Uri.encode(vault)}&file=$file" else "obsidian://open?file=$file"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, tr("ไม่พบแอป Obsidian", "Obsidian app not found"), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AddTagDialog(suggestions: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val query = TaskLine.normalizeTag(text)
    val matches = suggestions.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("เพิ่ม Tag", "Add tag")) },
        text = {
            Column {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = Prompt),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
                    decorationBox = { inner ->
                        Row {
                            Text("#", color = C.faint, fontSize = TS.body)
                            Box {
                                if (text.isEmpty()) Text(tr("เช่น รอ/พี่เอ", "e.g. รอ/Alex"), color = C.faint, fontSize = TS.body)
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
        confirmButton = { TextButton(onClick = { if (query.isNotEmpty()) onAdd(query) }, enabled = query.isNotEmpty()) { Text(tr("เพิ่ม", "Add"), color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}
