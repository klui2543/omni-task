package app.omnitask.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.tr

/**
 * Shown until Google Calendar events can actually be read: first asks for calendar access,
 * then, if the phone holds no Google calendar, points to the account's calendar sync switch.
 */
@Composable
fun CalendarConnect(state: UiState, vm: TaskViewModel, modifier: Modifier = Modifier) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.calendarChanged() }
    val sync = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.calendarChanged() }
    val access = state.calendarAccess ?: return
    val google = state.calendars.filter { it.isGoogle }
    if (access && google.isNotEmpty()) return

    Card(modifier, color = C.accentDeep, border = Color(0xFF2D2852)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.calendar, null, tint = C.accentText, modifier = Modifier.size(18.dp))
                Text(
                    if (access) tr("ยังไม่พบ Google Calendar ในเครื่อง", "No Google Calendar on this phone") else tr("เชื่อมต่อ Google Calendar", "Connect Google Calendar"),
                    Modifier.padding(start = 8.dp), color = C.accentText, style = MaterialTheme.typography.titleSmall,
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
