package app.omnitask.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.omnitask.model.DayFill
import app.omnitask.model.Lang
import app.omnitask.model.tr

/**
 * "ไม่ว่างถึงไหน" (owner's pick ก, 2026-11-03, mock six-pains screen 2): where a busy card goes, past the busy block
 * ("จบช่วงนี้"), this evening ("เย็นนี้") or tomorrow ("พรุ่งนี้"), with a mic for the reason in a few words, which the
 * secretary keeps; saying nothing is fine.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun BusySheet(p: DayFill.Placed, plan: DayFill.Plan, input: () -> DayFill.Input, vm: TaskViewModel, onDone: () -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    val span = remember(p, plan) { DayFill.busyUntil(plan, input(), p, DayFill.Busy.SPAN) }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && heard != null) reason = heard
    }
    fun speak() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Lang.english) "en-US" else "th-TH")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tr("ไม่ว่างเพราะอะไร", "Why are you busy?"))
        try {
            listen.launch(intent)
        } catch (_: ActivityNotFoundException) {
            reason = tr("(เครื่องนี้ไม่มีตัวฟังเสียง)", "(no speech input on this phone)")
        }
    }
    fun go(to: DayFill.Busy) {
        val said = reason?.takeUnless { it.startsWith("(") }
        vm.fillBusy(p, plan, input(), to, said)
        onDone()
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.raised) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("ไม่ว่างถึงไหน", "Busy until when"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val spanAt = span.at?.let { DayFill.hm(it) } ?: ""
                BusyChip(
                    tr("จบช่วงนี้", "After this"),
                    if (span.byBlock) tr("ถึง $spanAt ตามปฏิทิน", "until $spanAt, by the calendar") else tr("ถึง $spanAt", "until $spanAt"),
                    Modifier.weight(1f),
                ) { go(DayFill.Busy.SPAN) }
                BusyChip(tr("เย็นนี้", "This evening"), tr("หลังกลับถึงบ้าน", "after getting home"), Modifier.weight(1f)) { go(DayFill.Busy.EVENING) }
                BusyChip(tr("พรุ่งนี้", "Tomorrow"), tr("ช่วงว่างแรก", "first free time"), Modifier.weight(1f)) { go(DayFill.Busy.TOMORROW) }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.card).border(1.dp, C.control, RoundedCornerShape(10.dp))
                    .combinedClickable(onClick = { speak() }, onLongClick = { speak() }).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    tr("● กดค้างแล้วพูดเหตุสั้น ๆ ได้ เช่น \"ไปยิม\" เลขาจะจำไว้ ไม่พูดก็ได้", "● Hold and say why in a few words, like \"gym\"; the secretary keeps it. Or say nothing."),
                    color = C.muted, fontSize = TS.caption,
                )
                reason?.let { Text(tr("เหตุ: ", "Why: ") + it, Modifier.padding(top = 4.dp), color = C.text, fontSize = TS.body) }
            }
        }
    }
}

@Composable
private fun BusyChip(label: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).background(C.card).border(1.dp, C.control, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
        Text(sub, color = C.muted, fontSize = TS.caption)
    }
}
