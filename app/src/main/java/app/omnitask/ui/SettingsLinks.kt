package app.omnitask.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
