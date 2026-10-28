package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.omnitask.model.Focus
import app.omnitask.model.ReviewLog
import app.omnitask.model.SecretaryAsk
import app.omnitask.model.Task
import app.omnitask.model.tr

/**
 * The deck at the top of the secretary's page (owner's pick ก, 2026-10-27): everything that waits on Klui to decide,
 * one card a kind, swiped or stepped through like the deck on Focus. A kind with nothing in it has no card, and with
 * nothing left the deck is gone.
 */
@Composable
fun DecisionDeck(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, reviewCount: Int = 0, onReview: () -> Unit = {}) {
    val asks = SecretaryAsk.open(state.secretaryAsks, state.tasks, state.today)
    var asking by remember { mutableStateOf(false) }
    if (asking) SecretaryAskSheet(asks, vm, onOpen) { asking = false }
    var waitingOpen by remember { mutableStateOf(false) }
    if (waitingOpen) WaitingSheet(state, vm, onOpen) { waitingOpen = false }
    var picking by remember { mutableStateOf(false) }
    if (picking) FuturePicker(state, vm) { picking = false }

    val suggestions = state.brief.suggestions
    val warnings = Decisions.warnings(state)
    val waiting = Decisions.waiting(state)
    val future = Decisions.future(state)
    val cards = buildList {
        // The review mode, first and always there while anything is open (owner, 2026-10-28: on, no switch).
        if (reviewCount > 0) add(
            DeckCard("review", tr("ทบทวนงาน", "Review")) {
                // How many were reviewed today shows only when answers are written into the notes; without that it is unknown.
                val reviewedToday = if (ReviewMode.writes) state.tasks.count { it.isOpen && ReviewLog.last(it) == state.today } else null
                Text(
                    tr("งานเปิดอยู่ $reviewCount งาน", "$reviewCount open tasks") +
                        (reviewedToday?.let { tr(" ทบทวนวันนี้แล้ว $it", ", $it reviewed today") } ?: ""),
                    color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(tr("ทีละงาน ตอบเป็นปุ่ม ทำกี่งานก็ได้ หยุดเมื่อไรก็ได้", "One at a time, answered with buttons, as many as you like"), color = C.muted, fontSize = TS.caption)
                DeckButtons { DeckButton(tr("เริ่มทบทวน", "Start"), true, onReview) }
            },
        )
        asks.firstOrNull()?.let { first ->
            add(
                DeckCard("asks", tr("เลขาถาม ${asks.size}", "Questions ${asks.size}")) {
                    Text(tr("เสร็จหรือยัง", "Done yet?"), color = C.muted, fontSize = TS.caption)
                    Text(
                        first.task.title, Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(first.task) },
                        color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    DeckButtons {
                        // Not done: the sheet, where the reason is picked or written, as before.
                        DeckButton(tr("ยัง เพราะ...", "Not yet, because..."), false) { asking = true }
                        DeckButton(tr("เสร็จแล้ว", "Done"), true) { vm.toggleDone(first.task) }
                    }
                },
            )
        }
        suggestions.firstOrNull()?.let { s ->
            add(
                DeckCard("suggest", tr("ข้อเสนอ ${suggestions.size}", "Suggestions ${suggestions.size}"), C.amber) {
                    Text(
                        s.task.title, Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(s.task) },
                        color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(s.text, color = C.muted, fontSize = TS.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    DeckButtons {
                        DeckButton(tr("ไม่เอา", "Dismiss"), false) { vm.dismissSuggestion(s) }
                        DeckButton(tr("ตกลง", "Accept"), true) { vm.acceptSuggestion(s) }
                    }
                },
            )
        }
        if (warnings.isNotEmpty()) add(
            DeckCard("warn", tr("เตือน ${warnings.size}", "Warnings ${warnings.size}"), C.amber) {
                // One row each, an amber dot before it and a line between (owner, 2026-10-28: the block of text looked odd).
                warnings.forEachIndexed { i, w ->
                    if (i > 0) Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(C.divider))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 8.dp, end = 10.dp).size(7.dp).clip(CircleShape).background(C.amber))
                        Text(w, color = C.text, fontSize = TS.body)
                    }
                }
                DeckButtons {
                    DeckButton(tr("ซ่อน", "Hide"), waiting.isEmpty()) { Decisions.hideWarnings(warnings) }
                    if (waiting.isNotEmpty()) DeckButton(tr("ดูงานที่มีคนรอ", "See who waits"), true) { waitingOpen = true }
                }
            },
        )
        if (waiting.isNotEmpty() || future.isNotEmpty()) add(
            DeckCard("waiting", tr("คนรอ และ อนาคต", "Waiting and future"), if (waiting.isNotEmpty()) C.red else null) {
                val rows = (waiting.take(2).map { it to true } + future.take(1).map { it to false }).take(3)
                rows.forEach { (t, isWaiting) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onOpen(t) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, Modifier.weight(1f), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val age = Focus.ageDays(t, state.today)
                        Text(
                            if (isWaiting) (Focus.waitingFor(t)?.let { "$it " } ?: "") + (age?.let { tr("รอ $it วัน", "waiting $it days") } ?: tr("รออยู่", "waiting"))
                            else tr("อนาคต", "future") + (age?.let { tr(", ค้าง $it วัน", ", open $it days") } ?: ""),
                            Modifier.padding(start = 8.dp), color = if (isWaiting && (age ?: 0) >= 7) C.red else C.muted, fontSize = TS.caption, maxLines = 1,
                        )
                    }
                }
                DeckButtons {
                    DeckButton(tr("+ เลือกงานอนาคต", "+ Pick future work"), false) { picking = true }
                    DeckButton(tr("ดูทั้งหมด", "See all"), true) { waitingOpen = true }
                }
            },
        )
    }
    CardDeck(cards, 128.dp)
}

/**
 * Everyone waiting and today's work for the future, as the two Focus cards of the old look showed them: a tap opens a
 * task, "ข้ามวันนี้" puts a future task off for today, and how many future tasks come up a day can be changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaitingSheet(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (picking) FuturePicker(state, vm) { picking = false }
    val waiting = Decisions.waiting(state)
    val future = Decisions.future(state)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.raised) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(tr("คนรออยู่ ${waiting.size}", "Waiting ${waiting.size}"), color = C.amber, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            if (waiting.isEmpty()) Text(tr("ติด #รอ/ชื่อ ให้งานที่มีคนรอ", "Tag tasks people wait on with #รอ/name"), color = C.muted, fontSize = TS.body)
            waiting.forEach { t ->
                val age = Focus.ageDays(t, state.today)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onDismiss(); onOpen(t) }.padding(vertical = 8.dp)) {
                    Text(t.title, color = C.text, fontSize = TS.body)
                    Text(
                        (Focus.waitingFor(t)?.let { "$it " } ?: "") + (age?.let { tr("รอ $it วัน", "waiting $it days") } ?: tr("รออยู่", "waiting")),
                        color = if ((age ?: 0) >= 7) C.red else C.amber, fontSize = TS.caption,
                    )
                }
            }
            Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(C.divider))
            Text(tr("ลงทุนอนาคต ${future.size}", "Future ${future.size}"), color = C.accentText, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            if (future.isEmpty()) Text(tr("เลือกงานที่สำคัญต่ออนาคต แต่ไม่มีเดดไลน์", "Pick work that matters for the future but has no deadline"), color = C.muted, fontSize = TS.body)
            future.forEach { t ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onDismiss(); onOpen(t) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = C.text, fontSize = TS.body)
                        Focus.ageDays(t, state.today)?.let { Text(tr("ค้าง $it วัน", "open $it days"), color = C.muted, fontSize = TS.caption) }
                    }
                    Text(
                        tr("ข้ามวันนี้", "Skip today"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.skipFuture(t) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = C.accentText, fontSize = TS.caption,
                    )
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("+ เลือกงาน", "+ Pick"),
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = true }.padding(vertical = 8.dp),
                    color = C.accentText, fontSize = TS.body,
                )
                Spacer(Modifier.weight(1f))
                Stepper(state.futureCount, { vm.setFutureCount((state.futureCount + it).coerceIn(1, 5)) })
            }
        }
    }
}
