package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.omnitask.model.UrgentRule
import app.omnitask.model.tr

/**
 * What the Settings tab holds beyond the settings themselves: the assistant and notifications on top, what counts
 * as urgent, and at the end reloading the note and the vault folder.
 */
@Composable
fun SettingsLinks(onAssistant: () -> Unit, onNotify: () -> Unit) {
    Card {
        MoreRow(Screen.AI.label, onClick = onAssistant)
        Divider(start = 16.dp)
        MoreRow(tr("แจ้งเตือน", "Notifications"), onClick = onNotify)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UrgentGroup(urgentRule: UrgentRule, onUrgent: (UrgentRule) -> Unit) {
    SettingsGroup(tr("งานที่นับว่า \"ด่วน\"", "What counts as urgent")) {
        FlowRow(Modifier.padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UrgentRule.entries.forEach { rule -> Chip(rule.label, rule == urgentRule, { onUrgent(rule) }) }
        }
    }
}

@Composable
fun VaultLinks(onReload: () -> Unit, onVault: () -> Unit) {
    Card {
        MoreRow(tr("โหลดใหม่", "Reload"), onClick = onReload)
        Divider(start = 16.dp)
        MoreRow(tr("เปลี่ยนโฟลเดอร์ตู้โน้ต", "Change vault folder"), onClick = onVault)
    }
}

/** Only the notifications row (the assistant is a tab of its own in the new look). */
@Composable
fun NotifyLink(onNotify: () -> Unit) {
    Card { MoreRow(tr("แจ้งเตือน", "Notifications"), onClick = onNotify) }
}

/** A row that opens a page: its name, a line under it, an arrow; [badge] says what needs a look. */
@Composable
internal fun NavRow(title: String, sub: String, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = TS.title)
            Text(sub, Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.caption)
        }
        if (badge != null) Text(
            badge, Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).background(C.amberSoft).padding(horizontal = 8.dp, vertical = 2.dp),
            color = C.amber, fontSize = TS.micro,
        )
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(16.dp))
    }
}

/**
 * The owner's time words for spoken tasks ("เย็น" is 17:00): a tap on a time changes it with the phone's clock, the
 * cross takes the word out, and new words can be added. Words not here stay in a task's title as said.
 */
@Composable
fun TimeWordsGroup(words: List<app.omnitask.model.TimeWords.Word>, onChange: (List<app.omnitask.model.TimeWords.Word>) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var adding by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    SettingsGroup(tr("คำบอกเวลา", "Time words")) {
        Text(
            tr("พูดคำเหล่านี้ตอนจดเสียง แอปใส่เวลาให้และตัดคำออกจากชื่องาน คำที่ไม่อยู่ในนี้อยู่ในชื่องานตามเดิม", "Say one of these in a voice note and the task gets that time; other words stay in its title"),
            Modifier.padding(top = 4.dp, bottom = 4.dp), color = C.muted, fontSize = TS.caption,
        )
        words.forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(w.word, Modifier.weight(1f), color = C.text, fontSize = TS.body)
                Text(
                    "%02d:%02d".format(w.time.hour, w.time.minute),
                    Modifier.clip(RoundedCornerShape(12.dp)).background(C.raised).clickable {
                        pickSystemTime(context, w.time) { t -> onChange(words.toMutableList().also { it[i] = w.copy(time = t) }) }
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = C.text, fontSize = TS.body,
                )
                Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { onChange(words.filterIndexed { j, _ -> j != i }) }, contentAlignment = Alignment.Center) {
                    Icon(Ic.close, tr("เอาออก", "Remove"), tint = C.muted, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            tr("+ เพิ่มคำ", "+ Add a word"),
            Modifier.padding(top = 4.dp, bottom = 12.dp).clip(RoundedCornerShape(12.dp)).clickable { adding = true }.padding(horizontal = 4.dp, vertical = 8.dp),
            color = C.accentText, fontSize = TS.body,
        )
        Text(tr("เก็บในไฟล์ Omni/กฎเวลา.md แก้ใน Obsidian ได้ตรงกัน", "Kept in Omni/กฎเวลา.md, the same list Obsidian shows"), Modifier.padding(bottom = 12.dp), color = C.faint, fontSize = TS.caption)
    }
    if (adding) {
        var word by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = C.raised,
            title = { Text(tr("เพิ่มคำบอกเวลา", "Add a time word")) },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    word, { word = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(C.accent),
                    decorationBox = { inner -> Box { if (word.isEmpty()) Text(tr("เช่น หลังเวร", "For example after the shift"), color = C.faint, fontSize = TS.body); inner() } },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = word.isNotBlank(), onClick = {
                    val w = word.trim()
                    adding = false
                    pickSystemTime(context, kotlinx.datetime.LocalTime(17, 0)) { t -> onChange(words.filter { it.word != w } + app.omnitask.model.TimeWords.Word(w, t)) }
                }) { Text(tr("เลือกเวลา", "Pick its time"), color = C.accent) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { adding = false }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
        )
    }
}

/** One row: the name and an arrow. */
@Composable
private fun MoreRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = C.text, fontSize = TS.body)
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(16.dp))
    }
}
