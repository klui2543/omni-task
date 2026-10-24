package app.omnitask.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.omnitask.data.CalendarReader
import app.omnitask.data.TaskLine
import app.omnitask.model.QuickAdd
import app.omnitask.model.StuckLog
import app.omnitask.model.Swap
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

/**
 * "งาน | นัด" at the top of a task's sheet and an event's details, as on the add sheet: the side it is now is lit;
 * a tap on the other side opens the confirming sheet (nothing changes before it). [note] says why the other side
 * cannot be picked when [enabled] is false, or what the event left behind.
 */
@Composable
fun KindToggle(isEvent: Boolean, enabled: Boolean = true, note: String? = null, onSwitch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(false to tr("งาน", "Task"), true to tr("นัด", "Event")).forEach { (event, name) ->
                val on = event == isEvent
                val other = !on && enabled
                Row(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) C.accentDeep else C.sunken)
                        .clickable(enabled = other, onClick = onSwitch),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    val tint = when { on -> C.text; enabled -> C.muted; else -> C.faint }
                    if (event) Icon(Ic.calendar, null, tint = tint, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                    Text(name, color = tint, fontSize = TS.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        note?.let { Text(it, color = if (enabled) C.tealText else C.muted, fontSize = TS.caption, maxLines = 2) }
    }
}

/** The button in the task sheet: "เปลี่ยนเป็นนัด", or "เปลี่ยนกลับเป็นนัด" with what the event left behind. */
@Composable
fun SwapToEventButton(back: List<String>?, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.accentSoft)
            .border(1.dp, C.accentLine, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ic.calendar, null, tint = C.accentText, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(if (back != null) tr("เปลี่ยนกลับเป็นนัด", "Back to an event") else tr("เปลี่ยนเป็นนัด", "Make it an event"), color = C.accentText, fontSize = TS.body)
            if (!back.isNullOrEmpty()) Text(back.joinToString(", ") + tr(" ยังเก็บไว้", " still kept"), color = C.tealText, fontSize = TS.caption, maxLines = 2)
        }
        Icon(Ic.next, null, tint = C.accentText, modifier = Modifier.size(16.dp))
    }
}

/** What waits in the swap file, listed in a soft box: "เก็บไว้ให้" before a swap, "ได้คืน" when it comes back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeptBox(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(title, color = C.tealText, fontSize = TS.caption)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach {
                Text(
                    it, Modifier.clip(RoundedCornerShape(10.dp)).background(C.card).border(1.dp, C.divider, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 2.dp),
                    color = C.text2, fontSize = TS.caption, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SheetHead(title: String, sub: String, name: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = C.text)
    Text(sub, Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.caption)
    Text(name, Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken).padding(14.dp), color = C.text, fontSize = TS.body)
}

@Composable
private fun InfoRow(icon: ImageVector?, text: String, faint: Boolean = false, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = C.muted, modifier = Modifier.size(20.dp)) else Spacer(Modifier.width(20.dp))
        Text(text, Modifier.weight(1f), color = if (faint) C.faint else C.text, fontSize = TS.body, maxLines = 2)
        trailing()
    }
}

@Composable
private fun SheetButtons(confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel) { Text(tr("ยกเลิก", "Cancel"), color = C.text2, fontSize = TS.body) }
        PrimaryButton(confirm, onConfirm)
    }
}

/**
 * Turns a task into an event: the start and end rows as in the quick-add sheet, filled from the task's day and
 * reminder, or from the event it was before ([kept]): its length, place, alerts and calendar.
 */
@Composable
fun TaskToEventSheet(task: Task, state: UiState, vm: TaskViewModel, kept: Swap.KeptEvent?, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val today = state.today
    val draft = remember(task.key, kept) {
        val day = Swap.eventDay(task, today)
        val keptTime = kept?.takeIf { !it.allDay }?.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val time = if (kept?.allDay == true && task.reminderTime == null) null else task.reminderTime ?: keptTime
        val end = time?.let { day.atTime(it).plusMinutes(kept?.takeIf { k -> !k.allDay }?.minutes ?: 60L) }
        QuickAdd.Draft(
            title = task.title, due = day, time = time,
            endTime = end?.time,
            endDue = if (time == null) kept?.days?.takeIf { it > 0 }?.let { day.plusDays(it) } else end?.date?.takeIf { it != day },
        )
    }
    val pick = remember(task.key) { QuickEventState() }
    val writable = state.calendars.filter { it.writable && it.visible }
    var chosen by remember { mutableStateOf(kept?.calendarId?.takeIf { id -> writable.any { it.id == id } } ?: CalendarReader.addTo(context)) }
    val calendar = writable.firstOrNull { it.id == chosen } ?: writable.firstOrNull { it.isGoogle && it.name == it.account }
        ?: writable.firstOrNull { it.isGoogle } ?: writable.firstOrNull()
    val place = kept?.place.orEmpty()
    val reminders = kept?.reminders ?: if (task.reminderTime != null) listOf(0) else emptyList()
    val subtasks = state.subtasksOf(task).size

    fun save() {
        val c = calendar
        if (c == null) {
            vm.showMessage(tr("ไม่พบปฏิทินที่เพิ่มนัดได้ในเครื่อง เปิดซิงค์ปฏิทินของบัญชี Google ในตั้งค่าเครื่อง", "No calendar on this phone takes new events: turn on calendar sync for your Google account"))
            return
        }
        CalendarReader.setAddTo(context, c.id)
        vm.taskToEvent(task, c.id, quickEvent(draft, pick, today), place, reminders)
        onDone()
    }
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.calendarChanged()
        if (CalendarReader.canWrite(context)) save()
        else vm.showMessage(tr("ลงปฏิทินไม่ได้ ยังไม่ได้อนุญาตให้เข้าถึงปฏิทิน", "Cannot add the event: calendar access was not allowed"))
    }

    SheetFrame(onClose) {
        SheetHead(
            if (kept != null) tr("เปลี่ยนกลับเป็นนัด", "Back to an event") else tr("เปลี่ยนเป็นนัด", "Make it an event"),
            tr("งานนี้จะออกจาก ${task.noteName}.md แล้วไปอยู่ในปฏิทิน", "This task leaves ${task.noteName}.md and goes to the calendar"),
            task.title,
        )
        QuickEventRows(draft, pick, today, writable, calendar, onCalendar = { chosen = it }, onMore = null)
        if (place.isNotBlank()) InfoRow(Ic.pin, place)
        val allDay = quickEvent(draft, pick, today).allDay
        if (!allDay) reminders.forEachIndexed { i, m -> InfoRow(if (i == 0) Ic.bell else null, Swap.alert(m)) }
        KeptBox(tr("เก็บไว้ให้ เปลี่ยนกลับเป็นงานเมื่อไหร่ก็ได้คืน", "Kept for when it turns back into a task"), Swap.keptOfTask(task, subtasks) { it.format(SHORT_DATE) })
        SheetButtons(if (kept != null) tr("เปลี่ยนกลับเป็นนัด", "Back to an event") else tr("เปลี่ยนเป็นนัด", "Make it an event"), onClose) {
            if (CalendarReader.canWrite(context)) save() else allow.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }
}

/**
 * Turns an event into a task. An event that was a task before gets that task back (only its day and reminder
 * change); any other becomes a new task, due or scheduled on its day. Either way the event's end, place, alerts and
 * calendar wait for the way back.
 */
@Composable
fun EventToTaskSheet(d: CalendarReader.EventDetails, store: Swap.Store, vm: TaskViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val found = remember(d.id, store) { Swap.keptTask(store, d.id, d.title, d.begin) }
    var scheduled by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(d.begin.date) }
    var time by remember { mutableStateOf(if (d.allDay) null else d.begin.time) }
    var pickingDay by remember { mutableStateOf(false) }
    val calendarName = state.calendars.firstOrNull { it.id == d.calendarId }?.let { it.name.ifBlank { it.account } }
    val keptEvent = Swap.KeptEvent(
        time = if (d.allDay) null else d.begin.time.toString(),
        minutes = if (d.allDay) 60 else maxOf(1L, ChronoUnit.MINUTES.between(d.begin, d.end)),
        allDay = d.allDay, days = if (d.allDay) ChronoUnit.DAYS.between(d.begin.date, d.end.date) else 0,
        place = d.place, reminders = d.reminders, calendarId = d.calendarId,
    )
    // What comes back with the task it was: read from its kept lines.
    val back = remember(found) {
        found?.second?.lines?.let { lines ->
            TaskLine.parse(lines[0])?.let { t ->
                val rest = lines.drop(1)
                val notes = rest.filter { !TaskLine.isTask(it) }.map { it.trim().removePrefix("- ").trim() }
                Swap.keptOfTask(t.copy(notes = notes), rest.count { TaskLine.isTask(it) }) { it.format(SHORT_DATE) }
            }
        }
    }
    val file = found?.second?.file?.substringAfterLast('/')?.ifBlank { null } ?: VaultTaskFileName

    SheetFrame(onClose) {
        SheetHead(
            if (found != null) tr("เปลี่ยนกลับเป็นงาน", "Back to a task") else tr("เปลี่ยนเป็นงาน", "Make it a task"),
            tr("นัดนี้จะออกจากปฏิทิน แล้วไปเป็นงานใน $file", "This event leaves the calendar and becomes a task in $file"),
            d.title,
        )
        if (found == null) {
            Segmented(
                listOf(false to tr("วันครบกำหนด", "Due day"), true to tr("วันนัดทำ", "Scheduled day")),
                scheduled, { scheduled = it }, Modifier.padding(top = 10.dp).fillMaxWidth(),
            )
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(C.divider))
        InfoRow(Ic.calendar, dayLabelFor(day, state.today), onClick = { pickingDay = true })
        val t = time
        if (t != null) {
            InfoRow(Ic.bell, tr("เตือน ", "Remind ") + hm(t), onClick = { pickSystemTime(context, t) { time = it } }) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable { time = null }, contentAlignment = Alignment.Center) {
                    Icon(Ic.close, tr("ไม่เตือน", "No reminder"), tint = C.faint, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            InfoRow(Ic.bell, tr("ไม่เตือน", "No reminder"), faint = true, onClick = { pickSystemTime(context, LocalTime.of(9, 0)) { time = it } })
        }
        if (found == null && d.notes.isNotBlank()) InfoRow(Ic.notes, d.notes.lines().first { it.isNotBlank() })
        KeptBox(tr("ได้คืน", "Coming back"), back.orEmpty())
        KeptBox(tr("เก็บไว้ให้ เปลี่ยนกลับเป็นนัดเมื่อไหร่ก็ได้คืน", "Kept for when it turns back into an event"), Swap.keptOfEvent(keptEvent, calendarName))
        SheetButtons(if (found != null) tr("เปลี่ยนกลับเป็นงาน", "Back to a task") else tr("เปลี่ยนเป็นงาน", "Make it a task"), onClose) {
            vm.eventToTask(d, scheduled, day, time)
            onClose()
        }
    }
    if (pickingDay) DayDialog(tr("วันของงาน", "Task day"), day, null, { day = it }, { pickingDay = false })
}

private val VaultTaskFileName = app.omnitask.data.VaultText.TASK_FILE.substringAfterLast('/')

private fun dayLabelFor(day: LocalDate, today: LocalDate) = when (day) {
    today -> tr("วันนี้, ", "Today, ")
    today.plusDays(1) -> tr("พรุ่งนี้, ", "Tomorrow, ")
    else -> ""
} + day.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", TH))
