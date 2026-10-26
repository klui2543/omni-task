package app.omnitask.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.omnitask.data.CalendarReader
import app.omnitask.model.Priority
import app.omnitask.model.Profile
import app.omnitask.model.QuickAdd
import app.omnitask.model.Recurrence
import app.omnitask.model.Status
import app.omnitask.model.tr
import app.omnitask.time.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate

/** A repeat choice as the page holds it; [Recurrence.build] turns it into the note's rule, [Recurrence.rrule] into the calendar's. */
private data class Rep(
    val n: Int = 1,
    val unit: String = "week",
    val days: Set<DayOfWeek> = emptySet(),
    val whenDone: Boolean = false,
    /** What ends an event's repeat: 0 never, 1 on a day, 2 after [count] times. */
    val endType: Int = 0,
    val count: Int = 10,
    val until: LocalDate? = null,
)

private val DAY_DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", TH)

private fun dayText(day: LocalDate, today: LocalDate) = when (day) {
    today -> tr("วันนี้, ", "Today, ")
    today.plusDays(1) -> tr("พรุ่งนี้, ", "Tomorrow, ")
    else -> ""
} + day.format(DAY_DATE)

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

private fun remindText(m: Int) = when {
    m == 0 -> tr("เตือนตอนเริ่ม", "Alert at start")
    m < 60 -> tr("เตือนก่อน $m นาที", "Alert $m min before")
    m < 1440 -> tr("เตือนก่อน ${m / 60} ชม.", "Alert ${m / 60} h before")
    else -> tr("เตือนก่อน ${m / 1440} วัน", "Alert ${m / 1440} d before")
}

private fun repText(r: Rep, event: Boolean): String {
    val base = Recurrence.describe(Recurrence.build(r.n, r.unit, r.days, r.whenDone && !event))
    if (!event) return base
    return base + when (r.endType) {
        1 -> r.until?.let { tr(", ถึง ", ", until ") + it.format(SHORT_DATE) } ?: ""
        2 -> tr(", ${r.count} ครั้ง", ", ${r.count} times")
        else -> ""
    }
}

/**
 * The full-screen add page: everything the one-line sentence can't say. A task goes into the note the same
 * way as before (see [QuickAdd.Detail]); an event goes to a calendar with its own end, place, notes, repeat and alerts.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddDetailPage(
    state: UiState, vm: TaskViewModel, sentence: String, status: Status, link: String?,
    startAsEvent: Boolean = false, onClose: () -> Unit, onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val today = state.today
    val draft = remember { QuickAdd.parse(sentence, today) }
    var title by remember { mutableStateOf(draft.title) }
    // Opened from the event side of quick add, the page starts on นัด.
    var event by remember { mutableStateOf(startAsEvent) }
    var scheduled by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(draft.due ?: today) }
    var time by remember { mutableStateOf(draft.time) }
    var allDay by remember { mutableStateOf(draft.time == null) }
    // A range typed in the sentence ("9:00-11:00", "20-22/10") is the event's end.
    var endDate by remember { mutableStateOf(draft.endDue) }
    var endTime by remember { mutableStateOf(draft.endTime) }
    var begin by remember { mutableStateOf<LocalDate?>(null) }
    var rep by remember { mutableStateOf<Rep?>(null) }
    var priority by remember { mutableStateOf(draft.priority) }
    val tags = remember { mutableStateListOf<String>().also { it.addAll(draft.tags) } }
    val reminders = remember { mutableStateListOf(30) }
    var place by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val subs = remember { mutableStateListOf<String>() }
    var newSub by remember { mutableStateOf("") }
    val writable = state.calendars.filter { it.writable && it.visible }
    var calendarId by remember { mutableStateOf(CalendarReader.addTo(context)) }
    val calendar = writable.firstOrNull { it.id == calendarId } ?: writable.firstOrNull { it.isGoogle && it.name == it.account }
        ?: writable.firstOrNull { it.isGoogle } ?: writable.firstOrNull()

    // The event's start and end, as the calendar will hold them.
    val startClock = time ?: LocalTime.of(9, 0)
    val endDay = endDate ?: date
    val endClock = endTime ?: startClock.plusMinutes(60L)

    var picking by remember { mutableStateOf<String?>(null) }

    fun save() {
        val name = title.trim()
        if (name.isEmpty()) { vm.showMessage(tr("ใส่ชื่องานก่อน", "Add a title first")); return }
        if (!event) {
            val r = rep
            vm.addDetailedTask(
                QuickAdd.Detail(
                    title = name, scheduled = scheduled, date = date, time = time, start = begin,
                    rule = r?.let { Recurrence.build(it.n, it.unit, it.days, it.whenDone) },
                    tags = tags.toList(), priority = priority, notes = notes.lines(), subtasks = subs.toList(),
                ),
                status, link,
            )
            onSaved()
            return
        }
        val c = calendar
        if (c == null) {
            vm.showMessage(tr("ไม่พบปฏิทินที่เพิ่มนัดได้ในเครื่อง เปิดซิงค์ปฏิทินของบัญชี Google ในตั้งค่าเครื่อง", "No calendar on this phone takes new events: turn on calendar sync for your Google account"))
            return
        }
        val from = date.atTime(if (allDay) LocalTime.of(0, 0) else startClock)
        val to = endDay.atTime(if (allDay) LocalTime.of(0, 0) else endClock)
        val r = rep
        CalendarReader.setAddTo(context, c.id)
        vm.addDetailedEvent(
            c.id, name, from, to, allDay, place, notes,
            r?.let {
                Recurrence.rrule(
                    it.n, it.unit, it.days,
                    count = if (it.endType == 2) it.count else null,
                    until = if (it.endType == 1) it.until else null,
                    allDay = allDay,
                )
            },
            if (allDay) emptyList() else reminders.toList(),
        )
        onSaved()
    }

    // The first time, the phone asks for calendar access; once given the event goes in without a second tap.
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.calendarChanged()
        if (CalendarReader.canWrite(context)) save()
        else vm.showMessage(tr("ลงปฏิทินไม่ได้ ยังไม่ได้อนุญาตให้เข้าถึงปฏิทิน", "Cannot add the event: calendar access was not allowed"))
    }
    fun saveClicked() {
        if (event && !CalendarReader.canWrite(context)) allow.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) else save()
    }

    fun pickTime(initial: LocalTime, set: (LocalTime) -> Unit) = pickSystemTime(context, initial, set)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(C.card).systemBarsPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                // Top bar: close, task or event, save.
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(Ic.close, tr("ปิด", "Close"), tint = C.text2, modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(3.dp)) {
                            Switch2(tr("งาน", "Task"), !event) { event = false }
                            Switch2(tr("นัด", "Event"), event) { event = true }
                        }
                    }
                    PrimaryButton(tr("บันทึก", "Save"), { saveClicked() })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    BasicTextField(
                        value = title, onValueChange = { title = it },
                        textStyle = TextStyle(color = C.text, fontSize = TS.stat, fontFamily = AppFont),
                        cursorBrush = SolidColor(C.accent),
                        modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                        decorationBox = { inner ->
                            Box { if (title.isEmpty()) Text(tr("ชื่องาน", "Title"), color = C.faint, fontSize = TS.stat); inner() }
                        },
                    )

                    // When.
                    Group {
                        if (!event) {
                            FormRow(null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    KindWord(tr("ครบกำหนด", "Due"), !scheduled) { scheduled = false }
                                    KindWord(tr("นัดทำ", "Scheduled"), scheduled) { scheduled = true }
                                }
                            }
                            FormRow(Ic.clock, onClick = { picking = "date" }) {
                                Text(dayText(date, today), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                                Text(
                                    time?.let { hm(it) } ?: tr("ไม่ใส่เวลา", "No time"),
                                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = "time" }.padding(horizontal = 6.dp, vertical = 8.dp),
                                    color = if (time == null) C.faint else C.text, fontSize = TS.body,
                                )
                                if (time != null) RemoveX { time = null }
                            }
                            if (begin == null) {
                                FormRow(Ic.begin, onClick = { picking = "begin" }) { Text(tr("วันเริ่ม", "Start day"), color = C.faint, fontSize = TS.body) }
                            } else {
                                FormRow(Ic.begin, onClick = { picking = "begin" }) {
                                    Text(tr("เริ่ม ", "Starts ") + dayText(begin!!, today), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                                    RemoveX { begin = null }
                                }
                            }
                        } else {
                            FormRow(Ic.clock) {
                                Text(tr("ทั้งวัน", "All day"), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                                Box(Modifier.clip(CircleShape).clickable { allDay = !allDay; if (!allDay && time == null) time = LocalTime.of(9, 0) }.padding(4.dp)) { OnOff(allDay) }
                            }
                            FormRow(null, onClick = { picking = "date" }) {
                                Text(dayText(date, today), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                                if (!allDay) Text(hm(startClock), Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = "time" }.padding(horizontal = 6.dp, vertical = 8.dp), color = C.text, fontSize = TS.body)
                            }
                            FormRow(null, onClick = { picking = "endDate" }) {
                                Text(dayText(endDay, today), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                                if (!allDay) Text(hm(endClock), Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = "endTime" }.padding(horizontal = 6.dp, vertical = 8.dp), color = C.text, fontSize = TS.body)
                            }
                        }
                    }

                    // Repeat, and an event's alerts.
                    Group {
                        FormRow(Ic.repeat, onClick = { picking = "repeat" }) {
                            val r = rep
                            Text(if (r == null) tr("ไม่วนซ้ำ", "Does not repeat") else repText(r, event), color = if (r == null) C.faint else C.text, fontSize = TS.body)
                        }
                        if (event && !allDay) {
                            reminders.forEachIndexed { i, m ->
                                FormRow(if (i == 0) Ic.bell else null) {
                                    Text(remindText(m), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                                    RemoveX { reminders.removeAt(i) }
                                }
                            }
                            FormRow(if (reminders.isEmpty()) Ic.bell else null, onClick = { picking = "alert" }) {
                                Text(tr("เพิ่มการเตือน", "Add an alert"), color = C.faint, fontSize = TS.body)
                            }
                        }
                    }

                    // Task: importance and tags. Event: place and calendar.
                    Group {
                        if (!event) {
                            FormRow(Ic.flag, onClick = { picking = "priority" }) {
                                Text(
                                    if (priority == Priority.NONE) tr("ความสำคัญ", "Priority") else tr("ความสำคัญ ", "Priority ") + priority.label,
                                    color = if (priority == Priority.NONE) C.faint else C.text, fontSize = TS.body,
                                )
                            }
                            FormRow(Ic.tag, onClick = { picking = "tags" }) {
                                Text(
                                    if (tags.isEmpty()) tr("เพิ่ม Tag", "Add a tag") else tags.joinToString("  ") { "#$it" },
                                    color = if (tags.isEmpty()) C.faint else C.text, fontSize = TS.body,
                                )
                            }
                        } else {
                            FormRow(Ic.pin) {
                                PlainField(place, { place = it }, tr("สถานที่", "Place"), Modifier.weight(1f))
                            }
                            FormRow(Ic.calendar, onClick = if (writable.size > 1) ({ picking = "calendar" }) else null) {
                                Text(calendar?.let { it.name.ifBlank { it.account } } ?: tr("ไม่พบปฏิทิน", "No calendar"), color = if (calendar == null) C.faint else C.text, fontSize = TS.body)
                            }
                        }
                    }

                    // Details, and a task's subtasks.
                    Group(last = true) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Ic.notes, null, tint = C.muted, modifier = Modifier.padding(top = 13.dp).size(20.dp))
                            PlainField(notes, { notes = it }, tr("รายละเอียด", "Details"), Modifier.weight(1f).heightIn(min = 70.dp).padding(vertical = 12.dp), single = false)
                        }
                        if (!event) {
                            subs.forEachIndexed { i, s ->
                                FormRow(if (i == 0) Ic.checkbox else null) {
                                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(C.control))
                                    Text(s, Modifier.weight(1f).padding(start = 4.dp), color = C.text, fontSize = TS.body)
                                    RemoveX { subs.removeAt(i) }
                                }
                            }
                            FormRow(if (subs.isEmpty()) Ic.checkbox else null) {
                                PlainField(
                                    newSub, { newSub = it }, tr("เพิ่มงานย่อย", "Add a subtask"), Modifier.weight(1f), imeAction = ImeAction.Done,
                                    onDone = { if (newSub.isNotBlank()) { subs.add(newSub.trim()); newSub = "" } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // The phone's own clock dial opens once, then the choice is cleared.
    LaunchedEffect(picking) {
        when (picking) {
            "time" -> { picking = null; pickTime(time ?: LocalTime.of(9, 0)) { time = it; allDay = false } }
            "endTime" -> { picking = null; pickTime(endClock) { endTime = it } }
        }
    }
    when (picking) {
        "date" -> DayDialog(tr(if (event) "วันนัด" else if (scheduled) "วันนัดทำ" else "วันครบกำหนด", if (event) "Event day" else if (scheduled) "Scheduled day" else "Due day"), date, null, { date = it }, { picking = null })
        "begin" -> DayDialog(tr("วันเริ่ม", "Start day"), begin ?: date, null, { begin = it }, { picking = null })
        "endDate" -> DayDialog(tr("วันจบ", "End day"), endDay, null, { endDate = it }, { picking = null })
        "alert" -> OptionSheet(
            tr("เพิ่มการเตือน", "Add an alert"),
            listOf(0, 5, 10, 15, 30, 60, 120, 1440).map { it to remindText(it) }, -1,
            { m -> if (m !in reminders) { reminders.add(m); reminders.sort() } }, { picking = null },
        )
        "priority" -> OptionSheet(
            tr("ความสำคัญ", "Priority"),
            listOf(Priority.HIGHEST, Priority.HIGH, Priority.MEDIUM, Priority.NONE, Priority.LOW, Priority.LOWEST).map { it to it.label }, priority,
            { priority = it }, { picking = null },
        )
        "calendar" -> OptionSheet(
            tr("ปฏิทิน", "Calendar"), writable.map { it.id to it.name.ifBlank { it.account } }, calendar?.id ?: -1L,
            { calendarId = it }, { picking = null },
        )
        "tags" -> TagsDialog(state, tags) { picking = null }
        "repeat" -> RepeatDialog(rep, event, date, { rep = it }) { picking = null }
    }
}

@Composable
private fun Switch2(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) C.card else C.sunken).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 5.dp),
        color = if (on) C.text else C.muted, fontSize = TS.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
    )
}

@Composable
private fun KindWord(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        color = if (on) C.text else C.muted, fontSize = TS.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
        textDecoration = if (on) TextDecoration.Underline else null,
    )
}

/** A block of rows with a thin line above it. */
@Composable
private fun Group(last: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { content() }
}

/** One row: a line icon (or its width as space), then the content. */
@Composable
private fun FormRow(icon: ImageVector?, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = C.muted, modifier = Modifier.size(20.dp)) else Box(Modifier.width(20.dp))
        content()
    }
}

@Composable
private fun RemoveX(onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Ic.close, tr("เอาออก", "Remove"), tint = C.faint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun PlainField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    single: Boolean = true, imeAction: ImeAction = ImeAction.Default, onDone: () -> Unit = {},
) {
    BasicTextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = single,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body); inner() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDialog(title: String, current: LocalDate, clear: (() -> Unit)?, onSet: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val ps = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = C.raised),
        confirmButton = {
            TextButton(onClick = {
                ps.selectedDateMillis?.let { onSet(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate()) }
                onDismiss()
            }) { Text(tr("ตกลง", "OK"), color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    ) { DatePicker(state = ps, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp), color = C.muted, fontSize = TS.caption) }, colors = DatePickerDefaults.colors(containerColor = C.raised)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsDialog(state: UiState, tags: MutableList<String>, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val known = remember(state.tasks) {
        state.tasks.flatMap { it.tags }.filter { !it.startsWith("remind-at-") }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(24)
    }
    fun addTyped() {
        val t = app.omnitask.data.TaskLine.normalizeTag(typed)
        if (t.isNotEmpty() && t !in tags) tags.add(t)
        typed = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.raised,
        title = { Text("Tag") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (tags + known.filter { it !in tags }).forEach { t -> Chip("#$t", t in tags, { if (t in tags) tags.remove(t) else tags.add(t) }) }
                }
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp)) {
                    PlainField(typed, { typed = it }, tr("Tag ใหม่", "New tag"), Modifier.fillMaxWidth(), imeAction = ImeAction.Done, onDone = { addTyped() })
                }
            }
        },
        confirmButton = { TextButton(onClick = { addTyped(); onDismiss() }) { Text(tr("ตกลง", "OK"), color = C.accent) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatDialog(current: Rep?, event: Boolean, day: LocalDate, onSet: (Rep?) -> Unit, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(current) }
    var untilPicking by remember { mutableStateOf(false) }
    val week = day.dayOfWeek
    val presets = listOf(
        tr("ไม่วนซ้ำ", "Does not repeat") to null,
        tr("ทุกวัน", "Every day") to Rep(1, "day"),
        tr("ทุกวันทำงาน", "Every weekday") to Rep(1, "week", setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
        tr("ทุกสัปดาห์ ${Profile.dayName(week)}", "Every week on ${Profile.dayName(week)}") to Rep(1, "week", setOf(week)),
        tr("ทุกเดือน", "Every month") to Rep(1, "month"),
        tr("ทุกปี", "Every year") to Rep(1, "year"),
    )
    fun same(a: Rep?, b: Rep?) = (a == null && b == null) ||
        (a != null && b != null && a.n == b.n && a.unit == b.unit && a.days == b.days && !a.whenDone)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.raised,
        title = { Text(tr("วนซ้ำ", "Repeat")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (label, p) -> Chip(label, same(r, p), { r = p?.copy(until = r?.until, endType = r?.endType ?: 0, count = r?.count ?: 10) }) }
                }
                r?.let { cur ->
                    Text(tr("กำหนดเอง", "Custom"), Modifier.padding(top = 14.dp, bottom = 6.dp), color = C.muted, fontSize = TS.caption)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("ทุก", "Every"), color = C.text, fontSize = TS.body)
                        Stepper(cur.n, 1, 99) { r = cur.copy(n = it) }
                    }
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("day" to tr("วัน", "days"), "week" to tr("สัปดาห์", "weeks"), "month" to tr("เดือน", "months"), "year" to tr("ปี", "years")).forEach { (u, l) ->
                            Chip(l, cur.unit == u, { r = cur.copy(unit = u, days = if (u == "week" && cur.days.isEmpty()) setOf(week) else cur.days) })
                        }
                    }
                    if (cur.unit == "week") {
                        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.values().forEach { d ->
                                Chip(Profile.dayName(d).removePrefix("วัน"), d in cur.days, { r = cur.copy(days = if (d in cur.days) cur.days - d else cur.days + d) })
                            }
                        }
                    }
                    if (!event) {
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { r = cur.copy(whenDone = !cur.whenDone) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("นับจากวันที่ทำเสร็จ", "Count from completion"), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                            OnOff(cur.whenDone)
                        }
                    } else {
                        Text(tr("สิ้นสุด", "Ends"), Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.muted, fontSize = TS.caption)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(tr("ไม่สิ้นสุด", "Never"), cur.endType == 0, { r = cur.copy(endType = 0) })
                            Chip(cur.until?.let { tr("ถึง ", "On ") + it.format(SHORT_DATE) } ?: tr("ถึงวันที่", "On a day"), cur.endType == 1, {
                                r = cur.copy(endType = 1, until = cur.until ?: day.plusDays(30)); untilPicking = true
                            })
                            Chip(tr("ครบ ${cur.count} ครั้ง", "After ${cur.count}"), cur.endType == 2, { r = cur.copy(endType = 2) })
                        }
                        if (cur.endType == 2) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stepper(cur.count, 1, 999) { r = cur.copy(count = it) }
                            Text(tr("ครั้ง", "times"), color = C.text, fontSize = TS.body)
                        }
                    }
                    Text(repText(cur, event), Modifier.padding(top = 10.dp), color = C.accentText, fontSize = TS.caption)
                    if (!event) Text(tr("งานในโน้ตตั้งวันสิ้นสุดไม่ได้ ถ้าต้องการ ให้ลงเป็นนัด", "A task in the note can't end its repeat: add it as an event for that"), Modifier.padding(top = 4.dp), color = C.faint, fontSize = TS.caption)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(r); onDismiss() }) { Text(tr("ตกลง", "OK"), color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
    if (untilPicking) {
        DayDialog(tr("วันสิ้นสุด", "Last day"), r?.until ?: day, null, { d -> r = r?.copy(until = d, endType = 1) }) { untilPicking = false }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.sunken), verticalAlignment = Alignment.CenterVertically) {
        Text("-", Modifier.clickable { onChange((value - 1).coerceAtLeast(min)) }.padding(horizontal = 14.dp, vertical = 8.dp), color = C.text2, fontSize = TS.title, textAlign = TextAlign.Center)
        Text("$value", Modifier.widthIn(min = 28.dp), color = C.text, fontSize = TS.body, textAlign = TextAlign.Center)
        Text("+", Modifier.clickable { onChange((value + 1).coerceAtMost(max)) }.padding(horizontal = 14.dp, vertical = 8.dp), color = C.text2, fontSize = TS.title, textAlign = TextAlign.Center)
    }
}
