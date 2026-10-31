package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.omnitask.model.Projects
import app.omnitask.model.SecretaryAsk
import app.omnitask.model.StuckLog
import app.omnitask.model.Task
import app.omnitask.model.tr

/**
 * The secretary's questions on the today page (owner's pick, 2026-10-26): one line however many there are, so the
 * page stays the same height; a tap opens them on the review card.
 */
@Composable
fun SecretaryAskStrip(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(C.accentDeep)
            .border(1.dp, C.accentLine, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.chat, null, tint = C.accentText, modifier = Modifier.size(18.dp))
        Text(tr("เลขาถาม $count ข้อ", "The secretary asks $count"), Modifier.weight(1f).padding(start = 10.dp), color = C.text, fontSize = TS.body)
        Text(tr("ตอบ", "Answer"), color = C.muted, fontSize = TS.caption)
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.padding(start = 6.dp).size(14.dp))
    }
}

// The questions themselves open on the review card (ReviewScreen with asks), Klui's pick ข of 2026-10-30; the old
// sheet of done / not done buttons is gone, and what it did is on the card: done ticks, stuck writes a ค้าง line, the
// proposed due date is its own question.
