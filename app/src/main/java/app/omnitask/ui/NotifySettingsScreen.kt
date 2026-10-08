package app.omnitask.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.Lang
import app.omnitask.model.tr
import app.omnitask.notify.AlarmReceiver
import app.omnitask.notify.NotifySettings
import app.omnitask.notify.Scheduler
import java.time.LocalTime

private class Perm(val label: String, val granted: Boolean, val fix: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotifySettingsScreen(settings: NotifySettings, onChange: (NotifySettings) -> Unit, onPermissionChanged: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    // Bumped after returning from a system screen so the permission rows re-read their state.
    var tick by remember { mutableIntStateOf(0) }
    var tested by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        onPermissionChanged()
    }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        onPermissionChanged()
    }
    val openSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
        onPermissionChanged()
    }
    LaunchedEffect(Unit) { AlarmReceiver.ensureChannels(context) }

    val perms = remember(tick, Lang.english) {
        listOf(
            Perm(
                tr("แสดงการแจ้งเตือน", "Show notifications"),
                Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            ) { if (Build.VERSION.SDK_INT >= 33) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            Perm(tr("เตือนตรงเวลา", "Exact alarms"), Scheduler.canScheduleExact(context)) {
                if (Build.VERSION.SDK_INT >= 31) {
                    openSettings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            },
            Perm(tr("อ่านปฏิทิน (Google Calendar)", "Read calendar (Google Calendar)"), Scheduler.hasCalendarPermission(context)) {
                requestCalendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            },
            Perm(tr("ไม่จำกัดแบตเตอรี่", "Unrestricted battery"), ignoringBattery(context)) {
                openSettings.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            },
        )
    }
    val missing = perms.count { !it.granted }

    LazyColumn(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquareButton(Ic.back, tr("กลับ", "Back"), onBack)
                Text(tr("การแจ้งเตือน", "Notifications"), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, color = C.text)
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("สิทธิ์ที่ต้องใช้", "Required permissions"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = C.text)
                        Text(if (missing > 0) tr("ยังขาด $missing ข้อ", "$missing missing") else tr("ครบแล้ว", "All set"), color = if (missing > 0) C.amber else C.lime, fontSize = TS.caption)
                    }
                    perms.forEach { p ->
                        Divider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, Modifier.weight(1f), color = C.text, fontSize = TS.body)
                            if (p.granted) Text(tr("อนุญาตแล้ว", "Allowed"), color = C.lime, fontSize = TS.caption) else PrimaryButton(tr("อนุญาต", "Allow"), p.fix)
                        }
                    }
                    Text(
                        tr(
                            "มือถือ OPPO ต้องเปิด “เริ่มอัตโนมัติ” ให้ Omni Task ด้วย ไม่อย่างนั้นการแจ้งเตือนอาจไม่ดังตอนปิดแอป",
                            "On OPPO phones, turn on “Auto-launch” for Omni Task, or notifications may not ring while the app is closed.",
                        ),
                        Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(C.accentDeep).padding(12.dp),
                        color = C.accentText, fontSize = TS.body,
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(tr("เปิดการเริ่มอัตโนมัติ", "Open auto-launch"), {
                            runCatching { openSettings.launch(autoStartIntent(context)) }.onFailure {
                                openSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            }
                        }, Modifier.weight(1f))
                        PrimaryButton(
                            if (tested) tr("จะเด้งในอีก 1 นาที", "Arrives in 1 minute") else tr("ทดสอบแจ้งเตือน", "Test notification"),
                            { AlarmReceiver.scheduleTest(context); tested = true },
                            Modifier.weight(1f), color = if (tested) C.lime else C.accent,
                        )
                    }
                }
            }
        }
        item {
            Group(tr("งาน", "Tasks")) {
                SwitchRow(
                    tr("เตือนตามเวลาในงาน", "Task reminders"),
                    tr("อ่านเวลาเตือนจาก TaskForge กดเสร็จหรือเลื่อนได้จากแจ้งเตือน", "Reads reminder times from TaskForge. Mark done or snooze from the notification."),
                    settings.taskReminders,
                ) {
                    onChange(settings.copy(taskReminders = it))
                }
            }
        }
        item {
            Group(tr("สรุปงาน", "Summaries")) {
                Text(tr("เวลาสรุป เลือกได้หลายรอบต่อวัน", "Summary times, pick as many as you like"), Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.body)
                FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // The presets plus any time the owner added; tapping a chip turns it on or off.
                    (NotifySettings.DIGEST_PRESETS + settings.digestTimes).distinct().sorted().forEach { time ->
                        val on = time in settings.digestTimes
                        Chip(hhmm(time), on, {
                            val next = if (on) settings.digestTimes - time else settings.digestTimes + time
                            onChange(settings.copy(digestTimes = next.sorted()))
                        })
                    }
                    Chip(tr("+ เพิ่มเวลา", "+ Add time"), false, {
                        pickSystemTime(context, LocalTime.of(8, 0)) { t ->
                            if (t !in settings.digestTimes) onChange(settings.copy(digestTimes = (settings.digestTimes + t).sorted()))
                        }
                    })
                }
                SwitchRow(tr("งานเลยกำหนด", "Overdue tasks"), null, settings.digestOverdue) { onChange(settings.copy(digestOverdue = it)) }
                SwitchRow(tr("งานครบวันนี้", "Due today"), null, settings.digestDueToday) { onChange(settings.copy(digestDueToday = it)) }
                SwitchRow(tr("งานที่มีคนรอ", "Waiting tasks"), tr("งานที่ติด #รอ เรียงตามที่รอนานสุด", "Tasks tagged #รอ, longest wait first"), settings.digestWaiting) { onChange(settings.copy(digestWaiting = it)) }
                SwitchRow(tr("ทบทวนสัปดาห์", "Weekly review"), tr("ทุกวันอาทิตย์ 20:00", "Sundays at 20:00"), settings.weeklyReview) { onChange(settings.copy(weeklyReview = it)) }
            }
        }
        item {
            Group(tr("ปฏิทิน", "Calendar")) {
                SwitchRow(
                    tr("เตือนนัดใน Google Calendar", "Google Calendar events"),
                    tr("อ่านจากปฏิทินในเครื่อง ไม่ต้องล็อกอินเพิ่ม", "Reads the calendars on this phone, no extra sign-in"),
                    settings.calendarEvents,
                ) {
                    onChange(settings.copy(calendarEvents = it))
                }
                if (settings.calendarEvents) {
                    FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NotifySettings.LEAD_PRESETS.forEach { m ->
                            Chip(tr("ก่อน $m นาที", "$m min before"), settings.calendarLeadMinutes == m, { onChange(settings.copy(calendarLeadMinutes = m)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, Modifier.padding(top = 6.dp, bottom = 2.dp), color = C.accentText, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = TS.body)
            subtitle?.let { Text(it, color = C.muted, fontSize = TS.caption) }
        }
        Box(Modifier.width(12.dp))
        OnOff(checked)
    }
}

private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

private fun ignoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** ColorOS keeps its auto-start list in its own security app; other phones fall back to the app's settings page. */
private fun autoStartIntent(context: Context): Intent {
    val candidates = listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
    )
    candidates.forEach { c ->
        val i = Intent().setComponent(c)
        if (i.resolveActivity(context.packageManager) != null) return i
    }
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
