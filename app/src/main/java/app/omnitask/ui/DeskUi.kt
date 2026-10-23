package app.omnitask.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.omnitask.model.SecretaryDesk
import app.omnitask.model.SecretaryPretext
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.coroutines.launch
import app.omnitask.time.*

/**
 * The secretary in the new look: a chat whose answers come from rules (no AI), with the buttons the owner likely
 * wants next above the field. A typed question the rules know is answered the same way; anything else, and the
 * Claude button, hands the chat to Claude in its app with the pretext the owner keeps in "เลขา pretext.md".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeskScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.tasks.isNotEmpty()) { if (state.tasks.isNotEmpty()) vm.deskOpen() }
    val turns = vm.desk
    val list = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex) }
    // Steps ticked in a split answer, by turn; all ticked to begin with.
    val picks = remember { mutableStateMapOf<Int, Set<String>>() }
    var claudeOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    fun ask(chip: SecretaryDesk.Chip) {
        if (chip.ask == SecretaryDesk.Ask.CLAUDE) { claudeOpen = true; return }
        val i = turns.indexOfLast { it.reply != null }
        val steps = turns.getOrNull(i)?.reply?.steps.orEmpty()
        vm.deskAsk(chip, picks[i]?.toList() ?: steps)
    }

    if (claudeOpen) ClaudeSheet(vm, { claudeOpen = false }) { note, part ->
        claudeOpen = false
        scope.launch { shareToClaude(context, vm.claudePack(note, part)) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("เลขา", "Secretary"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = C.text, fontWeight = FontWeight.SemiBold)
            SquareButton(Ic.gear, tr("ตั้งค่า", "Settings"), onSettings)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(turns) { i, t ->
                t.mine?.let { Mine(it) }
                t.reply?.let { r -> Answer(r, t.done, picks[i] ?: r.steps.toSet(), { picks[i] = it }, onOpen) }
            }
        }
        val chips = turns.lastOrNull { it.reply != null }?.reply?.chips.orEmpty()
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips) { c ->
                val first = c == chips.first()
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(if (first) C.accentDeep else C.raised)
                        .border(1.dp, if (first) C.accentLine else C.cardBorder, RoundedCornerShape(20.dp)).clickable { ask(c) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(c.label, color = if (first) C.text else C.text2, fontSize = TS.body, maxLines = 1) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .padding(bottom = if (keyboard) 8.dp else NavClearance - 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val send = { if (input.isNotBlank()) { vm.deskType(input.trim()); input = "" } }
            BasicTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(C.card)
                    .border(1.dp, C.cardBorder, RoundedCornerShape(23.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                decorationBox = { inner -> Box { if (input.isEmpty()) Text(tr("พิมพ์ถามเอง", "Type a question"), color = C.faint, fontSize = TS.body); inner() } },
            )
            if (input.isNotBlank()) Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.accent).clickable { send() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.send, tr("ส่ง", "Send"), tint = C.onAccent, modifier = Modifier.size(20.dp)) }
            else Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.raised).clickable { claudeOpen = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.mic, tr("คุยเสียงกับ Claude", "Talk to Claude"), tint = C.text2, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun Mine(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).background(C.accentDeep).padding(horizontal = 12.dp, vertical = 8.dp),
            color = C.text, fontSize = TS.body,
        )
    }
}

@Composable
private fun Answer(r: SecretaryDesk.Reply, done: Boolean, picked: Set<String>, onPick: (Set<String>) -> Unit, onOpen: (Task) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("เลขา", "Secretary"), color = C.faint, fontSize = TS.micro)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(C.card).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val task = r.task
            r.lines.forEachIndexed { i, line ->
                val isTask = task != null && line == task.title
                Text(
                    line,
                    if (task != null && isTask) Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(task) } else Modifier,
                    color = if (i == 0 || isTask) C.text else C.text2, fontSize = TS.body,
                    fontWeight = if (isTask) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (r.moves.isNotEmpty()) Column {
                r.moves.forEach { m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(m.task) }.padding(vertical = 6.dp)) {
                        Text(m.date.format(SHORT_DATE), Modifier.width(64.dp), color = C.accentText, fontSize = TS.body)
                        Column(Modifier.weight(1f)) {
                            Text(m.task.title, color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(m.note, color = C.muted, fontSize = TS.caption)
                        }
                    }
                }
            }
            if (r.steps.isNotEmpty()) Column {
                r.steps.forEach { s ->
                    val on = s in picked
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !done) { onPick(if (on) picked - s else picked + s) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (on) C.accent else C.card).border(2.dp, if (on) C.accent else C.control, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(14.dp)) }
                        Text(s, Modifier.padding(start = 10.dp), color = C.text, fontSize = TS.body)
                    }
                }
            }
            if (done) Text(tr("ทำแล้ว", "Done"), color = C.muted, fontSize = TS.caption)
        }
    }
}

/**
 * Talking to Claude: the situations from the pretext note, one tap to send. Hold one to change or delete it, or add
 * a new one; the note in Obsidian changes with it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ClaudeSheet(vm: TaskViewModel, onDismiss: () -> Unit, onSend: (SecretaryPretext.Note, SecretaryPretext.Part?) -> Unit) {
    var note by remember { mutableStateOf<SecretaryPretext.Note?>(null) }
    LaunchedEffect(Unit) { note = vm.pretext() }
    var editing by remember { mutableStateOf<Pair<String?, SecretaryPretext.Part>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.raised) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("คุยเสียงกับ Claude", "Talk to Claude"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(
                tr("เลือกเรื่อง แอปส่ง pretext, ที่ Claude รู้จักคุณ, งาน, นัด และแชตนี้ไปให้ คุยจบพูดว่า บันทึก กดค้างที่เรื่องเพื่อแก้หรือลบ", "Pick a topic. The pretext, what Claude knows of you, your work, calendar and this chat go along. Say บันทึก to finish. Hold a topic to edit or delete it."),
                color = C.muted, fontSize = TS.caption,
            )
            val n = note
            if (n == null) Text(tr("กำลังอ่าน pretext...", "Reading the pretext..."), color = C.muted, fontSize = TS.body)
            else {
                n.situations.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
                            .combinedClickable(onClick = { onSend(n, p) }, onLongClick = { editing = p.title to p }).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(p.title, color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                        Text(p.body.lines().firstOrNull().orEmpty(), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = null to SecretaryPretext.Part("", "") }) { Text(tr("+ เพิ่มเรื่อง", "+ Add topic"), color = C.accentText) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onSend(n, null) }) { Text(tr("ส่งโดยไม่เลือกเรื่อง", "Send without a topic"), color = C.text2) }
                }
            }
        }
    }
    editing?.let { (old, part) ->
        var title by remember(old) { mutableStateOf(part.title) }
        var body by remember(old) { mutableStateOf(part.body) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = C.raised,
            title = { Text(if (old == null) tr("เพิ่มเรื่อง", "Add a topic") else tr("แก้เรื่อง", "Edit topic")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(title, { title = it }, tr("ชื่อปุ่ม เช่น ก่อนประชุม", "Button name"), single = true)
                    Field(body, { body = it }, tr("บอก Claude ว่าเรื่องนี้ต้องช่วยแบบไหน", "Tell Claude how to help with this"), single = false)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && body.isNotBlank(), onClick = {
                    note?.with(old, SecretaryPretext.Part(title.trim(), body.trim()))?.let { note = it; vm.savePretext(it) }
                    editing = null
                }) { Text(tr("บันทึก", "Save"), color = C.accent) }
            },
            dismissButton = {
                Row {
                    if (old != null) TextButton(onClick = { note?.with(old, null)?.let { note = it; vm.savePretext(it) }; editing = null }) { Text(tr("ลบ", "Delete"), color = C.red) }
                    TextButton(onClick = { editing = null }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) }
                }
            },
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, single: Boolean) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = single,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (single) 44.dp else 120.dp).clip(RoundedCornerShape(12.dp)).background(C.card)
            .border(1.dp, C.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp),
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body); inner() } },
    )
}

/** Opens Claude's app with [text] ready to send; another app can take it when Claude's is not installed. */
internal fun shareToClaude(context: Context, text: String) {
    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent(share).setPackage("com.anthropic.claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(share, tr("ส่งให้ Claude", "Send to Claude")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
