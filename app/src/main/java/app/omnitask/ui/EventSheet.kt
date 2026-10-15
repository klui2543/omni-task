package app.omnitask.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.omnitask.data.CalendarReader
import app.omnitask.model.QuickAdd
import app.omnitask.model.tr
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * A new event straight into a calendar on the phone, which Google Calendar then syncs. Day words and a time typed in
 * the title are read out of it, as in quick add; the chips below set them by hand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventSheet(state: UiState, vm: TaskViewModel, initial: String = "", day: LocalDate? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = state.today
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val draft = QuickAdd.parse(field.text, today)
    var date by remember { mutableStateOf(day ?: today) }
    var allDay by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(defaultStart(day ?: today, today)) }
    var minutes by remember { mutableStateOf(60) }
    // A day or time typed in the title wins over the chips, but only when it changes, so a chip tapped later stays.
    LaunchedEffect(draft.due) { draft.due?.let { date = it } }
    LaunchedEffect(draft.time) { draft.time?.let { start = it; allDay = false } }

    val calendars = state.calendars.filter { it.writable && it.visible }
    var chosen by remember { mutableStateOf(EventTarget.saved(context)) }
    val target = calendars.firstOrNull { it.id == chosen } ?: calendars.firstOrNull { it.isGoogle && it.name == it.account }
        ?: calendars.firstOrNull { it.isGoogle } ?: calendars.firstOrNull()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.calendarChanged() }
    val canWrite = CalendarReader.canWrite(context)

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focus.requestFocus() }
    }
    fun save() {
        val title = draft.title.trim()
        val cal = target ?: return
        if (title.isEmpty() || !canWrite) return
        EventTarget.save(context, cal.id)
        vm.addEvent(cal.id, title, date, if (allDay) null else start, minutes)
        onDismiss()
    }

    SheetFrame(onDismiss) {
        Text(tr("เพิ่มนัดลงปฏิทิน", "New calendar event"), style = MaterialTheme.typography.titleMedium, color = C.text)
        BasicTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                .background(C.sunken).padding(14.dp).focusRequester(focus),
            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
            cursorBrush = SolidColor(C.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            decorationBox = { inner ->
                Box {
                    if (field.text.isEmpty()) Text(tr("เช่น ประชุมทีม พรุ่งนี้ 14:00", "e.g. Team meeting tomorrow 14:00"), color = C.faint, fontSize = TS.body)
                    inner()
                }
            },
        )

        Label(tr("วัน", "Day"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("วันนี้", "Today"), date == today, { date = today })
            Chip(tr("พรุ่งนี้", "Tomorrow"), date == today.plusDays(1), { date = today.plusDays(1) })
            val other = date != today && date != today.plusDays(1)
            Chip(if (other) date.format(SHORT_DATE) else tr("เลือกวัน", "Pick a day"), other, { pickDate(context, date) { date = it } })
        }

        Label(tr("เวลา", "Time"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("ทั้งวัน", "All day"), allDay, { allDay = !allDay })
            if (!allDay) {
                Chip(tr("เริ่ม ", "Starts ") + hm(start), true, { pickSystemTime(context, start) { start = it } })
                Chip(tr("ถึง ", "Ends ") + hm(start.plusMinutes(minutes.toLong())), false, {
                    pickSystemTime(context, start.plusMinutes(minutes.toLong())) { end ->
                        // An end before the start runs past midnight.
                        val diff = (end.hour * 60 + end.minute) - (start.hour * 60 + start.minute)
                        minutes = if (diff > 0) diff else diff + 24 * 60
                    }
                })
            }
        }
        if (!allDay) {
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 60, 90, 120, 180).forEach { m -> Chip(length(m), minutes == m, { minutes = m }) }
            }
        }

        Label(tr("ปฏิทิน", "Calendar"))
        when {
            !canWrite -> {
                Text(
                    tr("ต้องอนุญาตให้แอปเขียนปฏิทินก่อน", "Allow the app to write to your calendars first"),
                    color = C.text2, fontSize = TS.body,
                )
                Box(Modifier.padding(top = 8.dp)) {
                    GhostButton(tr("อนุญาต", "Allow"), { ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) })
                }
            }
            calendars.isEmpty() -> Text(
                tr("ไม่พบปฏิทินที่เพิ่มนัดได้ในเครื่อง\nเปิดซิงค์ปฏิทินของบัญชี Google ในตั้งค่าเครื่อง", "No calendar on this phone takes new events.\nTurn on calendar sync for your Google account in the phone's settings."),
                color = C.text2, fontSize = TS.body,
            )
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                calendars.forEach { c -> Chip(c.name.ifBlank { c.account }, c.id == target?.id, { chosen = c.id }) }
            }
        }

        val ready = canWrite && target != null && draft.title.isNotBlank()
        Text(
            when {
                draft.title.isBlank() -> tr("พิมพ์ชื่อนัดก่อน", "Type the event's name first")
                allDay -> date.format(SHORT_DATE) + tr(" ทั้งวัน", ", all day")
                else -> date.format(SHORT_DATE) + ", " + hm(start) + tr(" ถึง ", " to ") + hm(start.plusMinutes(minutes.toLong()))
            },
            Modifier.padding(top = 16.dp), color = C.muted, fontSize = TS.caption,
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            PrimaryButton(tr("ลงปฏิทิน", "Add to calendar"), { save() }, color = if (ready) C.accent else C.control)
        }
    }
}

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

private fun length(m: Int) = when {
    m < 60 -> tr("$m นาที", "$m min")
    m % 60 == 0 -> tr("${m / 60} ชม.", "${m / 60} h")
    else -> tr("${m / 60} ชม. ${m % 60} นาที", "${m / 60} h ${m % 60} min")
}

/** Today: the next full hour. Another day: 9:00. */
private fun defaultStart(day: LocalDate, today: LocalDate): LocalTime {
    if (day != today) return LocalTime.of(9, 0)
    val next = LocalTime.now().hour + 1
    return if (next > 23) LocalTime.of(23, 0) else LocalTime.of(next, 0)
}

private fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    android.app.DatePickerDialog(
        context, android.R.style.Theme_DeviceDefault_Dialog,
        { _, y, m, d -> onPick(LocalDate(y, m + 1, d)) },
        initial.year, initial.monthNumber - 1, initial.dayOfMonth,
    ).show()
}

/** The calendar the last event went to, picked first next time. Ids differ per phone, so it stays out of the synced settings. */
object EventTarget {
    const val KEY = "calendar.addTo"
    fun saved(context: Context): Long? =
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).getLong(KEY, -1L).takeIf { it >= 0 }
    fun save(context: Context, id: Long) {
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit().putLong(KEY, id).apply()
    }
}
