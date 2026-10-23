package app.omnitask.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.data.CalendarReader
import app.omnitask.model.tr
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.omnitask.notify.CalendarEvent

/**
 * Shown until Google Calendar events can actually be read: first asks for calendar access,
 * then, if the phone holds no Google calendar, points to the account's calendar sync switch.
 */
@Composable
fun CalendarConnect(state: UiState, vm: TaskViewModel, modifier: Modifier = Modifier) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.calendarChanged() }
    val sync = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.calendarChanged() }
    if (!calendarConnectShows(state)) return
    val access = state.calendarAccess == true

    Card(modifier, color = C.accentDeep, border = C.accentLine) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (access) tr("ยังไม่พบ Google Calendar ในเครื่อง", "No Google Calendar on this phone") else tr("เชื่อมต่อ Google Calendar", "Connect Google Calendar"),
                    color = C.accentText, style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                if (access) {
                    tr(
                        "เปิด ตั้งค่า > ผู้ใช้และบัญชี > Google > ซิงค์ \"ปฏิทิน\" แล้วกลับมาที่แอป",
                        "Open Settings > Users & accounts > Google > sync \"Calendar\", then come back to the app",
                    ) + if (state.calendars.isNotEmpty()) {
                        tr("\nตอนนี้เห็น ${state.calendars.size} ปฏิทิน แต่ไม่ใช่ของ Google", "\nFound ${state.calendars.size} calendars, but none from Google")
                    } else {
                        ""
                    }
                } else {
                    tr(
                        "ให้แอปอ่านนัดและเวรจากปฏิทินในเครื่อง เพื่อจัดเวลาว่าง เตือนนัด และให้ผู้ช่วยลงนัดให้ได้",
                        "Let the app read events and shifts from your phone's calendars to find free time, remind you of events and let the assistant book them",
                    )
                },
                Modifier.padding(top = 6.dp), color = C.text2, fontSize = TS.body,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (access) {
                    PrimaryButton(tr("เปิดการซิงค์บัญชี", "Open account sync"), { sync.launch(Intent(Settings.ACTION_SYNC_SETTINGS)) })
                    GhostButton(tr("ตรวจอีกครั้ง", "Check again"), { vm.calendarChanged() })
                } else {
                    PrimaryButton(tr("อนุญาต", "Allow"), { ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) })
                }
            }
        }
    }
}

/** Whether [CalendarConnect] has something to say: calendar access not given yet, or given but no Google calendar found. */
fun calendarConnectShows(state: UiState): Boolean {
    val access = state.calendarAccess ?: return false
    return !(access && state.calendars.any { it.isGoogle })
}

/**
 * The phone's calendars that its calendar app shows, each with a switch for Omni. [mark] is the calendar of the event
 * the list was opened from: it is pointed out by id, since two calendars can carry the same name.
 */
@Composable
fun CalendarRows(choices: CalendarChoices, mark: Long? = null) {
    val visible = choices.calendars.filter { it.visible }
    if (visible.isEmpty()) {
        Text(tr("ไม่พบปฏิทินที่เปิดไว้ในเครื่อง", "No calendars are switched on in the phone's calendar app"), Modifier.padding(vertical = 12.dp), color = C.muted, fontSize = TS.body)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var styling by remember { mutableStateOf<Long?>(null) }
    visible.forEach { cal ->
        val on = !CalendarReader.isHidden(cal.id, choices.hidden)
        Row(Modifier.fillMaxWidth().clickable { choices.onShown(cal.id, !on) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The calendar's colour and label: whose events these are, at a glance (see CalendarStyles).
            CalendarDot(cal.id) { styling = if (styling == cal.id) null else cal.id }
            Box(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cal.name.ifBlank { cal.account }, color = if (cal.id == mark) C.accentText else C.text, fontSize = TS.body)
                val notes = listOfNotNull(
                    CalendarStyles.label(cal.id)?.let { tr("ป้าย: $it", "Label: $it") },
                    cal.account.takeIf { it.isNotBlank() && it != cal.name },
                    if (cal.id == mark) tr("นัดที่แตะมาจากปฏิทินนี้", "The event you tapped is in this one") else null,
                )
                if (notes.isNotEmpty()) Text(notes.joinToString("\n"), color = C.muted, fontSize = TS.caption)
            }
            Box(Modifier.width(12.dp))
            OnOff(on)
        }
        if (styling == cal.id) CalendarStyleEditor(context, cal.id)
    }
}

/** The calendar choice from Settings, opened on Focus by tapping an [event] (its calendar is pointed out) or the line under the day. */
@Composable
fun CalendarsDialog(choices: CalendarChoices, event: CalendarEvent?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ปฏิทินที่แสดง", "Calendars shown")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (event != null) Text(event.title, Modifier.padding(bottom = 4.dp), color = C.text2, fontSize = TS.caption)
                CalendarRows(choices, event?.calendarId)
                Text(
                    tr(
                        "ปฏิทินที่ปิดจะไม่โผล่ในโฟกัส ปฏิทินเดือน แกนต์ วิดเจ็ต การแจ้งเตือน และผู้ช่วย ปรับได้อีกที่ ตั้งค่า",
                        "A calendar turned off is left out of Focus, the month view, Gantt, widgets, reminders and the assistant. Also in Settings.",
                    ),
                    Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.caption,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.accentText) } },
    )
}
