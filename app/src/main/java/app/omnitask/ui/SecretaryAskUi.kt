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
 * page stays the same height; a tap opens them all in a sheet.
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

/** How the owner answered one question in this sheet, so the card says what was written instead of vanishing. */
private sealed interface Answer {
    data object Picking : Answer
    data object Done : Answer
    data class Noted(val reason: String) : Answer
}

/**
 * Every open question, each answered on its own card without opening the task: done ticks it; not done shows why it
 * is still open (the [StuckLog] reasons, or own words) and writes that on the task, dated today. A question answered
 * today comes back the next day until the task is done (owner: nothing is hushed or put off).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecretaryAskSheet(asks: List<SecretaryAsk.Open>, vm: TaskViewModel, onOpen: (Task) -> Unit, onDismiss: () -> Unit) {
    // The questions as they were when the sheet opened: answered ones stay, showing the answer, until it is closed.
    val shown = remember { asks }
    val today = remember { java.time.LocalDate.now() }
    val answers = remember { mutableStateMapOf<String, Answer>() }
    val left = shown.count { answers[it.task.key].let { a -> a == null || a == Answer.Picking } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.raised,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr("เลขาถาม $left ข้อ", "The secretary asks $left"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(tr("ข้อที่ยังไม่เสร็จ เลขาถามทุกวันจนกว่าจะเสร็จ", "Until a task is done, the secretary asks about it every day"), color = C.muted, fontSize = TS.caption)
            shown.forEach { o ->
                val key = o.task.key
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Projects.projectOf(o.task)?.let { Text(it, color = C.muted, fontSize = TS.caption) }
                    Text(o.task.title, Modifier.clickable { onDismiss(); onOpen(o.task) }, color = C.text, fontSize = TS.body)
                    o.ask.why?.let { Text(it, color = C.faint, fontSize = TS.caption) }
                    when (val a = answers[key]) {
                        null -> Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AskButton(tr("เสร็จแล้ว", "Done"), filled = true, Modifier.weight(1f)) {
                                vm.toggleDone(o.task)
                                answers[key] = Answer.Done
                            }
                            AskButton(tr("ยังไม่เสร็จ", "Not done"), filled = false, Modifier.weight(1f)) { answers[key] = Answer.Picking }
                        }
                        Answer.Picking -> {
                            var own by remember(key) { mutableStateOf("") }
                            var typing by remember(key) { mutableStateOf(false) }
                            Text(tr("ติดอะไร (จดลงความเป็นไปของงานนี้)", "What holds it up (noted on this task)"), color = C.muted, fontSize = TS.caption)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StuckLog.Reason.entries.forEach { r ->
                                    Chip(r.label, false, {
                                        // Words typed first ride along after the reason, as the task page writes them.
                                        val note = if (own.isBlank()) r.label else "${r.label}: ${own.trim()}"
                                        vm.noteStuck(o.task, note)
                                        answers[key] = Answer.Noted(note)
                                    })
                                }
                                // Own words (the keyboard's microphone speaks them): the box opens only when asked.
                                if (!typing) Chip(tr("พิมพ์หรือพูดเอง", "Type or say it"), false, { typing = true })
                            }
                            if (typing) {
                                StuckField(own) { own = it }
                                if (own.isNotBlank()) AskButton(tr("บันทึก", "Save"), filled = true, Modifier.fillMaxWidth()) {
                                    vm.noteStuck(o.task, own)
                                    answers[key] = Answer.Noted(own.trim())
                                }
                            }
                        }
                        Answer.Done -> Text(tr("ติ๊กว่าเสร็จแล้ว", "Ticked as done"), color = C.lime, fontSize = TS.caption)
                        is Answer.Noted -> Text(
                            tr("จดลงความเป็นไปแล้ว: ค้าง ${today.format(SHORT_DATE)} ${a.reason} พรุ่งนี้เลขาถามข้อนี้อีก", "Noted on the task: ${a.reason}. Asked again tomorrow"),
                            color = C.lime, fontSize = TS.caption,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AskButton(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .background(if (filled) C.accent else C.raised)
            .border(1.dp, if (filled) C.accent else C.control, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) C.onAccent else C.text, fontSize = TS.body, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal)
    }
}
