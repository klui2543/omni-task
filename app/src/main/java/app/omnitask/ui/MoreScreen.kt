package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.omnitask.model.UrgentRule
import app.omnitask.model.tr

/**
 * The bottom bar's "More": a page like Settings, with what no tab holds: the assistant, notifications,
 * settings, reloading the note, the vault folder and what counts as urgent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    urgentRule: UrgentRule,
    onUrgent: (UrgentRule) -> Unit,
    onScreen: (Screen) -> Unit,
    onNotify: () -> Unit,
    onSettings: () -> Unit,
    onReload: () -> Unit,
    onVault: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = NavClearance + FabClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(tr("เพิ่มเติม", "More"), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.headlineSmall, color = C.text) }
        item {
            Card {
                MoreRow(Ic.spark, Screen.AI.label) { onScreen(Screen.AI) }
            }
        }
        item {
            Card {
                MoreRow(Ic.bell, tr("แจ้งเตือน", "Notifications"), onClick = onNotify)
                Divider(start = 64.dp)
                MoreRow(Ic.gear, tr("ตั้งค่า", "Settings"), highlight = true, onClick = onSettings)
            }
        }
        item {
            SettingsGroup(tr("งานที่นับว่า \"ด่วน\"", "What counts as urgent")) {
                FlowRow(Modifier.padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UrgentRule.entries.forEach { rule -> Chip(rule.label, rule == urgentRule, { onUrgent(rule) }) }
                }
            }
        }
        item {
            Card {
                MoreRow(Ic.refresh, tr("โหลดใหม่", "Reload"), onClick = onReload)
                Divider(start = 64.dp)
                MoreRow(Ic.folderOpen, tr("เปลี่ยนโฟลเดอร์ตู้โน้ต", "Change vault folder"), onClick = onVault)
            }
        }
    }
}

/** One row of the page: an icon in a soft square, the name, and an arrow. Settings is picked out in the accent colour. */
@Composable
private fun MoreRow(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (highlight) C.accentSoft else C.raised),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (highlight) C.accentText else C.text2, modifier = Modifier.size(19.dp)) }
        Text(label, Modifier.weight(1f).padding(start = 14.dp), color = if (highlight) C.accentText else C.text, fontSize = TS.body)
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(16.dp))
    }
}
