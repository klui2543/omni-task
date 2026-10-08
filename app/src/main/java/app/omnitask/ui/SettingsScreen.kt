package app.omnitask.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import app.omnitask.model.Lang
import app.omnitask.model.tr
import kotlin.math.roundToInt

/** The sample every font is shown with: Thai with tone marks, a date word and a time, like a real task. */
private const val SAMPLE = "ส่งรายงานเวร พรุ่งนี้ 09:00"

/**
 * Language, font and text size. Each change applies at once, because all three are Compose state.
 * [onLanguageChange] lets the caller refresh text the view model has already built in the old language.
 */
@Composable
fun SettingsScreen(onLanguageChange: () -> Unit = {}, onBack: () -> Unit) {
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
