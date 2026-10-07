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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.omnitask.notify.AlarmReceiver
import app.omnitask.notify.NotifySettings
import app.omnitask.notify.Scheduler
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotifySettingsScreen(settings: NotifySettings, onChange: (NotifySettings) -> Unit, onPermissionChanged: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)

    // Bumped after returning from a system screen so the permission rows re-read their state.
    var tick by remember { mutableIntStateOf(0) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        onPermissionChanged()
    }
    val openSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
        onPermissionChanged()
    }
    LaunchedEffect(Unit) { AlarmReceiver.ensureChannels(context) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                title = { Text("การแจ้งเตือน") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            Section("สิทธิ์ที่ต้องใช้")
            key(tick) {
                PermissionRow(
                    "แสดงการแจ้งเตือน",
                    granted = Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                ) { if (Build.VERSION.SDK_INT >= 33) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                PermissionRow("ตั้งเวลาเตือนแบบตรงเวลา", granted = Scheduler.canScheduleExact(context)) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        openSettings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
                PermissionRow("อ่านปฏิทิน (Google Calendar)", granted = Scheduler.hasCalendarPermission(context)) {
                    requestPermission.launch(Manifest.permission.READ_CALENDAR)
                }
                PermissionRow("ไม่จำกัดแบตเตอรี่", granted = ignoringBattery(context)) {
                    openSettings.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
            Text(
                "มือถือ OPPO (ColorOS): เปิด “อนุญาตให้เริ่มอัตโนมัติ” และตั้งแบตเตอรี่ของแอปเป็น “ไม่จำกัด” ไม่อย่างนั้นการแจ้งเตือนอาจไม่ดังตอนปิดแอป",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    // ColorOS may refuse its own screen; the app's settings page always opens.
                    runCatching { openSettings.launch(autoStartIntent(context)) }.onFailure {
                        openSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }, shape = RoundedCornerShape(10.dp)) {
                    Text("เปิดการเริ่มอัตโนมัติ")
                }
                Button(onClick = { AlarmReceiver.scheduleTest(context) }, shape = RoundedCornerShape(10.dp)) {
                    Text("ทดสอบ (อีก 1 นาที)")
                }
            }

            Section("งาน")
            Toggle("เตือนตามเวลาในงาน (⏰ / 🎯)", "ปุ่ม เสร็จ · อีก 1 ชม. · พรุ่งนี้", settings.taskReminders) {
                onChange(settings.copy(taskReminders = it))
            }

            Section("สรุปงาน")
            Text("เวลาสรุป (เลือกได้หลายเวลา)", style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotifySettings.DIGEST_PRESETS.forEach { time ->
                    val on = time in settings.digestTimes
                    FilterChip(
                        selected = on,
                        onClick = {
                            val next = if (on) settings.digestTimes - time else settings.digestTimes + time
                            onChange(settings.copy(digestTimes = next.sorted()))
                        },
                        label = { Text(hhmm(time)) },
                    )
                }
            }
            Toggle("งานเลยกำหนด", null, settings.digestOverdue) { onChange(settings.copy(digestOverdue = it)) }
            Toggle("งานครบวันนี้", null, settings.digestDueToday) { onChange(settings.copy(digestDueToday = it)) }
            Toggle("งานที่มีคนรอ (#รอ)", null, settings.digestWaiting) { onChange(settings.copy(digestWaiting = it)) }
            Toggle("ทบทวนสัปดาห์", "วันอาทิตย์ 20:00", settings.weeklyReview) { onChange(settings.copy(weeklyReview = it)) }

            Section("ปฏิทิน")
            Toggle("เตือนนัดใน Google Calendar", "อ่านจากปฏิทินในเครื่อง ไม่ต้องล็อกอินเพิ่ม", settings.calendarEvents) {
                onChange(settings.copy(calendarEvents = it))
            }
            if (settings.calendarEvents) {
                Text("เตือนล่วงหน้า", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotifySettings.LEAD_PRESETS.forEach { m ->
                        FilterChip(
                            selected = settings.calendarLeadMinutes == m,
                            onClick = { onChange(settings.copy(calendarLeadMinutes = m)) },
                            label = { Text("$m นาที") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun key(tick: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(tick) { content() }

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (granted) {
            Text("✓ อนุญาตแล้ว", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        } else {
            OutlinedButton(onClick = onFix, shape = RoundedCornerShape(10.dp)) { Text("อนุญาต") }
        }
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
