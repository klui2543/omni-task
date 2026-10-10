package app.omnitask.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

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
