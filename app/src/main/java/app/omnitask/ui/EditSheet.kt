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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.omnitask.data.ImageAttach
import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.model.Focus
import app.omnitask.model.NoteLinks
import app.omnitask.model.Priority
import app.omnitask.model.Projects
import app.omnitask.model.Recurrence
import app.omnitask.model.ReminderOn
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.TaskKind
import app.omnitask.model.CustomKind
import app.omnitask.model.label
import app.omnitask.model.tr
import java.time.Instant
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.time.ZoneOffset
import app.omnitask.time.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditSheet(task: Task, state: UiState, vm: TaskViewModel, onDismiss: () -> Unit, onOpen: (Task) -> Unit = {}) {
    var picking by remember { mutableStateOf<DateField?>(null) }
    var addingTag by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var linking by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attachImage(task, uri)
    }

    // Which chip's choices are open under the chip row, and which folded row is expanded.
    var open by remember(task.key) { mutableStateOf<Prop?>(null) }
    var fold by remember(task.key) { mutableStateOf<String?>(null) }
    fun toggle(p: Prop) { open = if (open == p) null else p }
    val today = state.today

    SheetFrame(onDismiss) {
        // A subtask opens as a full task of its own; this leads back to the task it sits under.
        state.parentOf(task)?.let { parent ->
            Row(
                Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(15.dp)).background(C.raised)
                    .clickable { onOpen(parent) }.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.back, null, tint = C.accentText, modifier = Modifier.size(14.dp))
                Text(parent.title, Modifier.padding(start = 4.dp).widthIn(max = 260.dp), color = C.accentText, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 0.dp)) { TaskCheck(task, { vm.toggleDone(task) }, size = 22.dp) }
            Text(task.title, Modifier.weight(1f).padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = C.text)
            // A finished task (project work too) can be moved to the archive note by hand.
            if (!task.isOpen && task.parent == null && task.list == null) {
                Box(
                    Modifier.padding(start = 6.dp, top = 2.dp).size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.archiveTask(task); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.archive, tr("เก็บเข้าคลัง", "Archive"), tint = C.faint, modifier = Modifier.size(19.dp)) }
            }
            Box(
                Modifier.padding(start = 6.dp, top = 2.dp).size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { deleting = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.trash, tr("ลบงาน", "Delete task"), tint = C.faint, modifier = Modifier.size(19.dp)) }
        }

        DescriptionBlock(task, vm)

        // Every property in one block of chips; a chip shows its value, or its name when empty.
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PropChip(Ic.views, task.status.takeIf { it != Status.TODO }?.label, tr("สถานะ", "Status"), open == Prop.STATUS) { toggle(Prop.STATUS) }
            PropChip(Ic.calendar, task.due?.let { dayText(it, today) }, tr("ครบกำหนด", "Due"), open == Prop.DUE, late = task.isLate(today)) { toggle(Prop.DUE) }
            PropChip(Ic.hourglass, task.scheduled?.let { dayText(it, today) }, tr("นัดทำ", "Scheduled"), open == Prop.SCHEDULED) { toggle(Prop.SCHEDULED) }
            PropChip(
                Ic.bell,
                task.reminderTime?.let { "%02d:%02d".format(it.hour, it.minute) },
                tr("เตือน", "Remind"), open == Prop.REMIND,
            ) { toggle(Prop.REMIND) }
            PropChip(Ic.repeat, task.recurrence?.let { Recurrence.describe(it) }, tr("วนซ้ำ", "Repeat"), open == Prop.REPEAT) { toggle(Prop.REPEAT) }
            PropChip(Ic.flag, task.priority.takeIf { it != Priority.NONE }?.label, tr("ความสำคัญ", "Priority"), open == Prop.PRIORITY, tint = task.priority.tint) { toggle(Prop.PRIORITY) }
            val custom = CustomKind.of(task, state.customKinds)
            PropChip(Ic.target, custom?.label ?: TaskKind.of(task).takeIf { it != TaskKind.NORMAL }?.label, tr("ประเภท", "Type"), open == Prop.KIND) { toggle(Prop.KIND) }
            val tags = task.tags.filterNot { it.startsWith("remind-at-") || TaskKind.kindTags(task).contains(it) || custom?.tag.equals(it, ignoreCase = true) }
            PropChip(Ic.hash, tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }, "Tag", open == Prop.TAG) { toggle(Prop.TAG) }
        }

        // The open chip's choices, right under the chips.
        open?.let { p ->
            Column(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken)
                    .border(1.dp, C.cardBorder, RoundedCornerShape(14.dp)).padding(12.dp),
            ) {
                when (p) {
                    Prop.DUE, Prop.SCHEDULED -> {
                        val field = if (p == Prop.DUE) DateField.DUE else DateField.SCHEDULED
                        val current = if (p == Prop.DUE) task.due else task.scheduled
                        ChoiceRow(
                            listOf(
                                tr("วันนี้", "Today") to today,
                                tr("พรุ่งนี้", "Tomorrow") to today.plusDays(1),
                                tr("เสาร์นี้", "Saturday") to Focus.softDate(today),
                                tr("สัปดาห์หน้า", "Next week") to today.with(app.omnitask.time.TemporalAdjusters.next(kotlinx.datetime.DayOfWeek.MONDAY)),
                            ).map { (label, d) -> Choice(label, d == current) { vm.setDate(task, field, d); open = null } } +
                                Choice(tr("เลือกวัน", "Pick a day"), false) { picking = field } +
                                listOfNotNull(current?.let { Choice(tr("ไม่มี", "None"), false, danger = true) { vm.setDate(task, field, null); open = null } }),
                        )
                    }
                    Prop.REMIND -> {
                        var on by remember(task.key) {
                            mutableStateOf(task.reminderOn ?: if (task.due == null && task.scheduled != null) ReminderOn.SCHEDULED else ReminderOn.DUE)
                        }
                        Segmented(
                            listOf(ReminderOn.DUE to tr("วันครบกำหนด", "Due day"), ReminderOn.SCHEDULED to tr("วันนัดทำ", "Scheduled day")),
                            on, { on = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                        ChoiceRow(
                            listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(17, 30), LocalTime.of(20, 0)).map { t ->
                                Choice("%02d:%02d".format(t.hour, t.minute), t == task.reminderTime && on == task.reminderOn) { vm.setReminder(task, t, on); open = null }
                            } + Choice(tr("เลือกเวลา", "Pick a time"), false) {
                                pickSystemTime(context, task.reminderTime ?: LocalTime.of(9, 0)) { t -> vm.setReminder(task, t, on); open = null }
                            } + listOfNotNull(task.reminderTime?.let { Choice(tr("ไม่เตือน", "No reminder"), false, danger = true) { vm.setReminder(task, null, on); open = null } }),
                        )
                        if ((if (on == ReminderOn.SCHEDULED) task.scheduled ?: task.due else task.due ?: task.scheduled) == null) {
                            Text(tr("งานนี้ยังไม่มีวันที่ ตั้งวันก่อนแล้วการเตือนจะทำงาน", "Set a date first, then the reminder will fire"), Modifier.padding(top = 6.dp), color = C.amber, fontSize = TS.caption)
                        }
                    }
                    Prop.REPEAT -> ChoiceRow(
                        Recurrence.PRESETS.map { r -> Choice(Recurrence.describe(r), task.recurrence?.trim() == r) { vm.setRecurrence(task, r); open = null } } +
                            Choice(tr("แบบอื่น", "Custom"), false) { repeating = true } +
                            listOfNotNull(task.recurrence?.let { Choice(tr("ไม่วนซ้ำ", "Don't repeat"), false, danger = true) { vm.setRecurrence(task, null); open = null } }),
                    )
                    Prop.STATUS -> ChoiceRow(Status.entries.map { st -> Choice(st.label, task.status == st) { vm.setStatus(task, st); open = null } })
                    Prop.PRIORITY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Priority.entries.forEach { pr -> Chip(pr.label, task.priority == pr, { vm.setPriority(task, pr); open = null }, dot = pr.tint) }
                    }
                    Prop.KIND -> KindField(task, state, vm)
                    Prop.TAG -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.tags.filterNot { it.startsWith("remind-at-") || TaskKind.kindTags(task).contains(it) }.forEach { tag ->
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
                        Chip(tr("+ เพิ่ม Tag", "+ Add tag"), false, { addingTag = true })
                    }
                }
            }
        }

        SubtaskBlock(task, state, vm, onOpen)

        // The rarely used parts, one line each until opened.
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken)) {
            FoldRow(Ic.link, tr("โน้ตที่เกี่ยวข้อง", "Linked notes"), if (task.links.isEmpty()) "" else "${task.links.size}", fold == "links", first = true) {
                fold = if (fold == "links") null else "links"
            }
            if (fold == "links") Column(Modifier.padding(start = 30.dp, end = 4.dp, bottom = 6.dp)) {
                task.links.forEach { link ->
                    val path = NoteLinks.resolve(link, state.notePaths)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { openNote(context, state.vaultName, path ?: "$link.md") }.padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(link.substringAfterLast('/'), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                path?.substringBeforeLast('/', "")?.ifEmpty { tr("ราก Vault", "Vault root") } ?: tr("ยังไม่มีโน้ตนี้", "Note does not exist yet"),
                                color = if (path == null) C.amber else C.faint, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (link in task.linkLines) SquareButton(Ic.close, tr("เอาลิงก์ออก", "Remove link"), { vm.removeLink(task, link) })
                    }
                }
                Text(
                    tr("+ ลิงก์โน้ต", "+ Link a note"),
                    Modifier.padding(start = 14.dp, top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable { linking = true }.padding(vertical = 8.dp),
                    color = C.accentText, fontSize = TS.body,
                )
            }

            FoldRow(Ic.image, tr("รูปแนบ", "Images"), if (task.attachments.isEmpty()) "" else "${task.attachments.size}", fold == "images") {
                fold = if (fold == "images") null else "images"
            }
            if (fold == "images") Column(Modifier.padding(start = 44.dp, end = 14.dp, bottom = 12.dp)) {
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
            }

            FoldRow(Ic.more, tr("รายละเอียดอื่น", "More details"), "", fold == "more") {
                fold = if (fold == "more") null else "more"
            }
            if (fold == "more") Column(Modifier.padding(bottom = 4.dp)) {
                FieldRow(tr("วันเริ่ม", "Start"), task.start?.format(SHORT_DATE)) { picking = DateField.START }
                FieldRow(tr("วันที่สร้าง", "Created"), task.created?.format(SHORT_DATE), null)
                FieldRow(tr("โปรเจกต์", "Project"), Projects.projectOf(task), null)
                FieldRow(tr("ต้องรอ", "Waits on"), Projects.waitingOn(task, state.tasks), null)
                FieldRow(tr("ไฟล์", "File"), tr("${task.noteName} บรรทัด ${task.lineIndex + 1}", "${task.noteName} line ${task.lineIndex + 1}"), null)
            }
        }
    }

    picking?.let { field -> DateDialog(task, field, { vm.setDate(task, field, it) }) { picking = null } }
    if (repeating) RepeatDialog(task, { vm.setRecurrence(task, it) }) { repeating = false }
    if (addingTag) {
        AddTagDialog(state.tags.filterNot { it.startsWith("remind-at-") || it in task.tags }, { vm.addTag(task, it); addingTag = false }) { addingTag = false }
    }
    viewing?.let { name -> ImageViewer(name, vm) { viewing = null } }
    if (linking) {
        NotePicker(state.notePaths.filterNot { NoteLinks.linkText(it, state.notePaths) in task.links }, { vm.addLink(task, it); linking = false }) { linking = false }
    }
    if (deleting) {
        val subs = state.subtasksOf(task).size
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = C.raised,
            title = { Text(tr("ลบงานนี้?", "Delete this task?")) },
            text = {
                Text(
                    tr("ลบบรรทัดนี้ออกจาก ${task.filePath.substringAfterLast('/')} พร้อมรายละเอียด", "Removes the line from ${task.filePath.substringAfterLast('/')} with its details") +
                        (if (subs > 0) tr(" และงานย่อย $subs งาน", " and $subs subtasks") else ""),
                    color = C.text2,
                )
            },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deleteTask(task); onDismiss() }) { Text(tr("ลบ", "Delete"), color = C.red) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
        )
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

/** The description under the title: tap to write; a long one can move to its own note. */
@Composable
private fun DescriptionBlock(task: Task, vm: TaskViewModel) {
    var editing by remember(task.key) { mutableStateOf(false) }
    var text by remember(task.key, task.description) { mutableStateOf(task.description) }
    val long = text.length > 280 || text.lines().size > 6
    Column(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken)
            .border(1.dp, if (editing) C.accentLine else C.cardBorder, RoundedCornerShape(14.dp))
            .clickable(enabled = !editing) { editing = true }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.tasks, null, tint = C.muted, modifier = Modifier.size(14.dp))
            Text(tr("รายละเอียด", "Details"), Modifier.padding(start = 6.dp).weight(1f), color = C.muted, fontSize = TS.caption)
            if (editing && long) {
                Text(
                    tr("ย้ายไปโน้ตแยก", "Move to a note"),
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.moveDescriptionToNote(task, text); editing = false }.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = C.accentText, fontSize = TS.caption,
                )
            }
        }
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont, lineHeight = 1.45.em),
                cursorBrush = SolidColor(C.accent),
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 60.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(tr("เขียนรายละเอียดงาน บรรทัดละเรื่อง", "Write the details, one point per line"), color = C.faint, fontSize = TS.body)
                        inner()
                    }
                },
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f))
                GhostButton(tr("ยกเลิก", "Cancel"), { text = task.description; editing = false })
                PrimaryButton(tr("บันทึก", "Save"), { vm.setDescription(task, text); editing = false })
            }
        } else {
            Text(
                task.description.ifEmpty { tr("แตะเพื่อเขียนรายละเอียด", "Tap to add details") },
                Modifier.padding(top = 4.dp), color = if (task.description.isEmpty()) C.faint else C.text, fontSize = TS.body,
            )
        }
    }
}

/**
 * Subtasks numbered in file order, which is the order to do them in. The handle drags a row up or down;
 * tapping a row opens that subtask as a full task.
 */
@Composable
private fun SubtaskBlock(task: Task, state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val subs = state.subtasksOf(task)
    var adding by remember(task.key) { mutableStateOf("") }
    val done = subs.count { it.status == Status.DONE }

    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (subs.isEmpty()) tr("งานย่อย", "Subtasks") else tr("งานย่อย $done/${subs.size}", "Subtasks $done/${subs.size}"),
            Modifier.weight(1f), color = C.muted, fontSize = TS.caption,
        )
        if (subs.isNotEmpty()) {
            Box(Modifier.width(90.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.raised)) {
                Box(Modifier.fillMaxWidth(done.toFloat() / subs.size).height(6.dp).background(C.lime))
            }
        }
    }
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken)) {
        OrderedTaskList(
            subs,
            onToggle = { vm.toggleDone(it) },
            onOpen = onOpen,
            onReorder = { vm.reorderSubtasks(task, it) },
            meta = { sub ->
                listOfNotNull(
                    (sub.due ?: sub.scheduled)?.let { dayText(it, state.today) },
                    sub.reminderTime?.let { "%02d:%02d".format(it.hour, it.minute) },
                    state.progressOf(sub)?.let { (d, n) -> tr("งานย่อย $d/$n", "Subtasks $d/$n") },
                ).joinToString(", ")
            },
        )
        if (subs.isNotEmpty()) Divider()
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.plus, null, tint = C.accentText, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = adding,
                onValueChange = { adding = it },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addSubtask(task, adding); adding = "" }),
                decorationBox = { inner ->
                    Box {
                        if (adding.isEmpty()) Text(tr("เพิ่มงานย่อย (พรุ่งนี้ 9:00 #tag ได้)", "Add a subtask (tomorrow 9:00 #tag works)"), color = C.accentText, fontSize = TS.body)
                        inner()
                    }
                },
            )
            if (adding.isNotBlank()) SquareButton(Ic.check, tr("เพิ่ม", "Add"), { vm.addSubtask(task, adding); adding = "" }, filled = true)
        }
    }
}

private enum class Prop { STATUS, DUE, SCHEDULED, REMIND, REPEAT, PRIORITY, KIND, TAG }

private class Choice(val label: String, val selected: Boolean, val danger: Boolean = false, val onClick: () -> Unit)

private fun dayText(d: LocalDate, today: LocalDate) = when (d) {
    today -> tr("วันนี้", "Today")
    today.plusDays(1) -> tr("พรุ่งนี้", "Tomorrow")
    else -> d.format(SHORT_DATE)
}

/** A property as a chip: its icon and value when set, its name in a quieter colour when not. */
@Composable
private fun PropChip(icon: ImageVector, value: String?, name: String, open: Boolean, late: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    val set = value != null
    Row(
        Modifier.height(36.dp).widthIn(min = 36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (open) C.accentSoft else if (set) C.accentDeep else Color.Transparent)
            .border(1.dp, if (open) C.accent else if (set) C.accentLine else C.control, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = if (set) 11.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, if (set) null else name, tint = when { late -> C.red; tint != null && set -> tint; set -> C.accentText; else -> C.muted }, modifier = Modifier.size(16.dp))
        if (set) Text(value!!, color = if (late) C.red else C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(choices: List<Choice>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { c ->
            Text(
                c.label,
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                    .background(if (c.selected) C.accentSoft else Color.Transparent)
                    .border(1.dp, if (c.selected) C.accent else C.control, RoundedCornerShape(17.dp))
                    .clickable(onClick = c.onClick).padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (c.danger) C.red else if (c.selected) C.text else C.text2, fontSize = TS.body, maxLines = 1,
            )
        }
    }
}

@Composable
private fun FoldRow(icon: ImageVector, label: String, summary: String, open: Boolean, first: Boolean = false, onClick: () -> Unit) {
    if (!first) Divider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.accentText, modifier = Modifier.size(16.dp))
        Text(label, Modifier.padding(start = 14.dp).weight(1f), color = C.text, fontSize = TS.body, maxLines = 1)
        Text(summary, Modifier.padding(start = 8.dp).widthIn(max = 150.dp), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(if (open) Ic.up else Ic.down, null, tint = C.faint, modifier = Modifier.padding(start = 6.dp).size(14.dp))
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
                ps.selectedDateMillis?.let { onSet(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate()) }
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
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
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
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
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
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
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
