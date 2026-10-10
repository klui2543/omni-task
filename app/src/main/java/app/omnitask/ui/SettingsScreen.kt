package app.omnitask.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.Appearance
import app.omnitask.model.FontChoice
import app.omnitask.model.PaletteChoice
import app.omnitask.model.ThemeMode
import app.omnitask.model.Lang
import app.omnitask.model.set
import app.omnitask.model.tr
import kotlin.math.roundToInt

/** The sample every font is shown with: Thai with tone marks, a date word and a time, like a real task. */
private const val SAMPLE = "ส่งรายงานเวร พรุ่งนี้ 09:00"

/** What happens to finished tasks: how many days before they move to the archive note (0 is off), and whether ticking asks. */
class ArchiveSettings(val days: Int, val ask: Boolean, val onDays: (Int) -> Unit, val onAsk: (Boolean) -> Unit)

/**
 * Which of the phone's calendars Omni shows: [calendars] are the ones visible in the phone's calendar app, [hidden] the ids
 * switched off here. [onPermissionChanged] runs after the calendar permission prompt closes.
 */
class CalendarChoices(
    val calendars: List<app.omnitask.data.CalendarReader.Calendar>,
    val hidden: Set<Long>,
    val onShown: (Long, Boolean) -> Unit,
    val onPermissionChanged: () -> Unit,
)

/** The Omni folder on Google Drive: its state and what the card's buttons do (see [DriveUi]). */
class DriveSettings(
    val state: DriveUi,
    val onConnect: () -> Unit,
    val onSignInShown: () -> Unit,
    val onSignedIn: (android.content.Intent?) -> Unit,
    val onConfirm: (Boolean) -> Unit,
    val onDisconnect: () -> Unit,
)

/** The choices for [ArchiveSettings.days]. */
private val ARCHIVE_DAYS = listOf(0, 1, 3, 7, 14, 30)

/**
 * Language, font and text size. Each change applies at once, because all three are Compose state.
 * [onLanguageChange] lets the caller refresh text the view model has already built in the old language.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    profile: app.omnitask.model.Profile? = null,
    onSleepTimes: (kotlinx.datetime.LocalTime, kotlinx.datetime.LocalTime) -> Unit = { _, _ -> },
    onLanguageChange: () -> Unit = {},
    archive: ArchiveSettings? = null,
    calendars: CalendarChoices? = null,
    drive: DriveSettings? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    // Bumped after the permission prompt closes so the section re-reads whether access is granted.
    var permissionTick by remember { mutableIntStateOf(0) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        calendars?.onPermissionChanged?.invoke()
    }

    // Google's own sign-in screen, when connecting Drive needs one.
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { drive?.onSignedIn?.invoke(it.data) }
    val signInIntent = drive?.state?.signInIntent
    LaunchedEffect(signInIntent) {
        if (signInIntent != null) {
            drive?.onSignInShown()
            signIn.launch(IntentSenderRequest.Builder(signInIntent.intentSender).build())
        }
    }
    if (drive != null && drive.state.confirmDir != null) {
        AlertDialog(
            onDismissRequest = { drive.onConfirm(false) },
            containerColor = C.raised,
            title = { Text(tr("ไฟล์ในเครื่องยังไม่ตรงกับ Drive", "The phone's copy differs from Drive")) },
            text = {
                Text(
                    tr(
                        "Omni note ในเครื่องต่างจากใน Google Drive ถ้ามีงานที่แก้บนมือถือแต่ยังไม่ได้ซิงก์ ให้กด sync ใน DriveSync ก่อน แล้วค่อยกลับมาเชื่อม\n\nถ้าเชื่อมเลย Omni จะใช้ไฟล์ใน Drive",
                        "Omni note on this phone differs from the one in Google Drive. If you edited tasks on the phone that have not synced yet, sync in DriveSync first and connect afterwards.\n\nIf you connect now, Omni uses the file in Drive.",
                    ),
                    color = C.text2, fontSize = TS.body,
                )
            },
            confirmButton = { TextButton(onClick = { drive.onConfirm(true) }) { Text(tr("เชื่อมเลย", "Connect now"), color = C.accent) } },
            dismissButton = { TextButton(onClick = { drive.onConfirm(false) }) { Text(tr("ไว้ก่อน", "Not now"), color = C.text2) } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquareButton(Ic.back, tr("กลับ", "Back"), onBack)
                Text(tr("ตั้งค่า", "Settings"), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, color = C.text)
            }
        }
        item {
            SettingsGroup(tr("ภาษา", "Language")) {
                // Each language is named in itself, so it can be found whichever one is showing.
                Segmented(
                    listOf(false to "ไทย", true to "English"),
                    Lang.english,
                    { english ->
                        if (english != Lang.english) {
                            Lang.set(context, english)
                            onLanguageChange()
                        }
                    },
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                )
            }
        }
        if (drive != null) {
            item { DriveGroup(drive) }
        }
        if (profile != null) {
            item {
                // The usual night; tonight alone can be moved from the Focus screen.
                SettingsGroup(tr("การนอน", "Sleep")) {
                    TimeRow(tr("เวลานอนประจำ", "Usual bedtime"), profile.sleep) { pickSystemTime(context, profile.sleep) { onSleepTimes(it, profile.wake) } }
                    TimeRow(tr("เวลาตื่นประจำ", "Usual wake time"), profile.wake) { pickSystemTime(context, profile.wake) { onSleepTimes(profile.sleep, it) } }
                    Text(
                        tr("หลัง 6 โมงเย็น หน้าโฟกัสบอกเวลาก่อนนอนและชั่วโมงที่ได้นอน แตะที่บรรทัดนั้นเพื่อเปลี่ยนเฉพาะคืนนี้", "After 6 pm Focus shows the time until bed and the hours of sleep. Tap that line to change tonight only."),
                        Modifier.padding(top = 4.dp, bottom = 12.dp), color = C.muted, fontSize = TS.caption,
                    )
                }
            }
        }
        if (archive != null) {
            item {
                SettingsGroup(tr("งานที่เสร็จแล้ว", "Finished tasks")) {
                    Text(tr("ย้ายเข้าคลังอัตโนมัติหลัง", "Move to the archive after"), Modifier.padding(top = 8.dp), color = C.text, fontSize = TS.body)
                    FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ARCHIVE_DAYS.forEach { d ->
                            Chip(if (d == 0) tr("ไม่ย้าย", "Never") else tr("$d วัน", if (d == 1) "1 day" else "$d days"), archive.days == d, { archive.onDays(d) })
                        }
                    }
                    Text(
                        tr(
                            "ย้ายวันละครั้งไปที่ Omni note Archive.md ข้างไฟล์ Omni note งานโปรเจกต์ไม่ถูกย้าย ยังติ๊กเสร็จอยู่ที่เดิม",
                            "Once a day, to Omni note Archive.md next to the Omni note. Project tasks stay ticked where they are.",
                        ),
                        color = C.muted, fontSize = TS.caption,
                    )
                    Row(Modifier.fillMaxWidth().clickable { archive.onAsk(!archive.ask) }.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("ถามเมื่อติ๊กเสร็จ", "Ask when a task is done"), color = C.text, fontSize = TS.body)
                            Text(tr("เก็บเข้าคลัง ลบ หรือไว้ก่อน", "Archive, delete or keep it"), color = C.muted, fontSize = TS.caption)
                        }
                        OnOff(archive.ask)
                    }
                }
            }
        }
        if (calendars != null) {
            item {
                SettingsGroup(tr("ปฏิทินที่แสดง", "Calendars shown")) {
                    val granted = permissionTick >= 0 && app.omnitask.data.CalendarReader.hasPermission(context)
                    val visible = calendars.calendars.filter { it.visible }
                    if (!granted) {
                        Text(
                            tr("อนุญาตให้อ่านปฏิทินในเครื่องก่อน แล้วเลือกได้ว่าจะแสดงปฏิทินไหน", "Allow reading the calendars on this phone, then choose which ones to show"),
                            Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
                        )
                        PrimaryButton(
                            tr("อนุญาต", "Allow"),
                            { askCalendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
                            Modifier.padding(top = 8.dp, bottom = 12.dp),
                        )
                    } else {
                        if (visible.isEmpty()) {
                            Text(tr("ไม่พบปฏิทินที่เปิดไว้ในเครื่อง", "No calendars are switched on in the phone's calendar app"), Modifier.padding(vertical = 12.dp), color = C.muted, fontSize = TS.body)
                        }
                        visible.forEach { cal ->
                            val on = !app.omnitask.data.CalendarReader.isHidden(cal.id, calendars.hidden)
                            Row(Modifier.fillMaxWidth().clickable { calendars.onShown(cal.id, !on) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(cal.name.ifBlank { cal.account }, color = C.text, fontSize = TS.body)
                                    if (cal.account.isNotBlank() && cal.account != cal.name) Text(cal.account, color = C.muted, fontSize = TS.caption)
                                }
                                Box(Modifier.width(12.dp))
                                OnOff(on)
                            }
                        }
                        Text(
                            tr(
                                "เลือกได้เฉพาะปฏิทินที่เปิดแสดงไว้ในแอปปฏิทินของเครื่อง ปฏิทินที่ปิดตรงนี้จะไม่โผล่ในปฏิทินเดือน แกนต์ ไทม์ไลน์ วิดเจ็ต การแจ้งเตือน และผู้ช่วย",
                                "Only calendars switched on in the phone's calendar app are listed. A calendar turned off here is left out of the month view, Gantt, timeline, widgets, reminders and the assistant.",
                            ),
                            Modifier.padding(bottom = 12.dp), color = C.muted, fontSize = TS.caption,
                        )
                    }
                }
            }
        }
        item {
            SettingsGroup(tr("ธีม", "Theme")) {
                Segmented(
                    ThemeMode.entries.map { it to it.label },
                    Appearance.themeMode,
                    { Appearance.setThemeMode(context, it) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Segmented(
                    PaletteChoice.entries.map { it to it.label },
                    Appearance.palette,
                    { Appearance.setPalette(context, it) },
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                )
            }
        }
        item {
            SettingsGroup(tr("ฟอนต์", "Font")) {
                Column(Modifier.selectableGroup().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChoice.entries.forEach { choice ->
                        FontOption(choice, choice == Appearance.font) { Appearance.setFont(context, choice) }
                    }
                }
            }
        }
        item {
            SettingsGroup(tr("ขนาดตัวอักษร", "Text size")) {
                Segmented(
                    Appearance.SCALES.map { it to "${(it * 100).roundToInt()}%" },
                    Appearance.scale,
                    { Appearance.setScale(context, it) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SizePreview(Modifier.padding(top = 10.dp, bottom = 12.dp))
            }
        }
    }
}

/** Connecting the Omni folder on Google Drive, and how it is going once connected. */
@Composable
private fun DriveGroup(drive: DriveSettings) {
    val d = drive.state
    SettingsGroup("Google Drive") {
        if (!d.on) DriveConnect(drive) else DriveConnected(drive)
    }
}

@Composable
private fun DriveConnect(drive: DriveSettings) {
    val d = drive.state
    Column {
        Text(
            tr(
                "ให้ Omni อ่านและเขียนไฟล์ในโฟลเดอร์ Omni บน Google Drive โดยตรงเหมือนเว็บ ไม่ต้องรอแอปซิงก์ และไม่ชนกับการแก้จาก iPad โน้ตอื่นยังอ่านจากโฟลเดอร์ในเครื่องเหมือนเดิม",
                "Omni reads and writes the files of the Omni folder on Google Drive directly, as the web does: no waiting for the sync app, and no clash with edits from the iPad. Other notes are still read from the folder on the phone.",
            ),
            Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
        )
        Text(
            tr("ก่อนเชื่อม กด sync ใน DriveSync หนึ่งครั้ง", "Before connecting, sync once in DriveSync."),
            Modifier.padding(top = 6.dp), color = C.text2, fontSize = TS.caption,
        )
        PrimaryButton(
            if (d.busy) tr("กำลังเชื่อม", "Connecting") else tr("เชื่อม Google Drive", "Connect Google Drive"),
            { if (!d.busy) drive.onConnect() },
            Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun DriveConnected(drive: DriveSettings) {
    val d = drive.state
    Column {
        val time = if (d.lastSync > 0) {
            java.time.Instant.ofEpochMilli(d.lastSync).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
        } else null
        val status = when {
            d.signIn -> tr("ต้องลงชื่อ Google อีกครั้ง", "Sign in to Google again")
            !d.online -> tr("ออฟไลน์ ใช้ข้อมูลที่โหลดไว้ล่าสุด", "Offline, showing what was last read")
            time != null -> tr("เชื่อมแล้ว อ่านล่าสุด $time", "Connected, last read at $time")
            else -> tr("เชื่อมแล้ว", "Connected")
        }
        Text(status, Modifier.padding(top = 8.dp), color = if (d.signIn) C.red else C.text, fontSize = TS.body)
        if (d.pending > 0) {
            Text(
                tr("มีการแก้รอส่งขึ้น Drive ${d.pending} ไฟล์ จะส่งเองเมื่อต่อ Drive ได้", "${d.pending} edited files wait to go to Drive; they are sent once Drive answers."),
                Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.caption,
            )
        }
        if (d.signIn) {
            PrimaryButton(
                if (d.busy) tr("กำลังลงชื่อ", "Signing in") else tr("ลงชื่อเข้าใช้", "Sign in"),
                { if (!d.busy) drive.onConnect() },
                Modifier.padding(top = 10.dp),
            )
        }
        Text(
            tr(
                "ไฟล์ในโฟลเดอร์ Omni อ่านและเขียนบน Drive โดยตรง ตอนออฟไลน์ การแก้จะเก็บไว้ในเครื่องแล้วส่งขึ้นไปเองทีหลัง",
                "Files in the Omni folder are read and written on Drive directly. Offline, edits are kept on the phone and sent later.",
            ),
            Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
        )
        var asking by remember { mutableStateOf(false) }
        GhostButton(tr("เลิกเชื่อม", "Disconnect"), { if (d.pending > 0) asking = true else drive.onDisconnect() }, Modifier.padding(top = 10.dp, bottom = 12.dp))
        if (asking) {
            AlertDialog(
                onDismissRequest = { asking = false },
                containerColor = C.raised,
                title = { Text(tr("เลิกเชื่อม Google Drive", "Disconnect Google Drive")) },
                text = {
                    Text(
                        tr("ยังมีการแก้ ${d.pending} ไฟล์ที่ยังไม่ได้ส่งขึ้น Drive ถ้าเลิกเชื่อมตอนนี้ การแก้นั้นจะหายไป", "${d.pending} edited files have not reached Drive yet. Disconnecting now drops those edits."),
                        color = C.text2, fontSize = TS.body,
                    )
                },
                confirmButton = { TextButton(onClick = { asking = false; drive.onDisconnect() }) { Text(tr("เลิกเชื่อม", "Disconnect"), color = C.red) } },
                dismissButton = { TextButton(onClick = { asking = false }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
            )
        }
    }
}

@Composable
private fun TimeRow(label: String, time: kotlinx.datetime.LocalTime, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = C.text, fontSize = TS.body)
        Text("%02d:%02d".format(time.hour, time.minute), color = C.accentText, fontSize = TS.body, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, Modifier.padding(top = 6.dp, bottom = 2.dp), color = C.accentText, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** One font, its name and the sample both drawn in it, at the size it would have if picked. */
@Composable
private fun FontOption(choice: FontChoice, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val family = choice.family
    val k = Appearance.scale * choice.sizeFactor
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) C.accentDeep else C.sunken)
            .border(1.dp, if (selected) C.accent else C.cardBorder, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(choice.label, color = C.text, fontFamily = family, fontSize = (14f * k).sp, fontWeight = FontWeight.Medium)
            Text(SAMPLE, color = C.text2, fontFamily = family, fontSize = (16f * k).sp)
        }
        Box(Modifier.padding(start = 12.dp)) {
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                    Icon(Ic.check, tr("เลือกอยู่", "Selected"), tint = C.onAccent, modifier = Modifier.size(14.dp))
                }
            } else {
                Box(Modifier.size(24.dp).border(1.5.dp, C.control, CircleShape))
            }
        }
    }
}

/** A small task card in the app's real text sizes, so the size can be judged before leaving the page. */
@Composable
private fun SizePreview(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(C.sunken).border(1.dp, C.cardBorder, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(tr("ตัวอย่าง", "Preview"), color = C.faint, fontSize = TS.caption)
        Text(tr("ส่งรายงานเวร", "Send the shift report"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.Medium)
        Text(
            tr("แนบตารางเวรเดือนหน้า แล้วส่งให้หัวหน้าตึกก่อนเที่ยง", "Attach next month's roster and send it to the ward head before noon"),
            color = C.text2, fontSize = TS.body,
        )
        Text(tr("พรุ่งนี้ 09:00", "Tomorrow 09:00"), color = C.accentText, fontSize = TS.caption)
    }
}
