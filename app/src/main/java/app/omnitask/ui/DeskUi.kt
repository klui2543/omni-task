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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.produceState
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
import app.omnitask.model.Projects
import app.omnitask.model.SecretaryDesk
import app.omnitask.model.SecretaryGuide
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
    // When the chat was started over: "undo" shows for a few seconds after.
    var resetAt by remember { mutableStateOf(0L) }
    LaunchedEffect(resetAt) { if (resetAt > 0) { kotlinx.coroutines.delay(6000); resetAt = 0L } }

    // The guide (เลขา คู่มือ.md): read again whenever the chat opens, since it may have changed in Obsidian.
    LaunchedEffect(Unit) { vm.refreshGuide() }
    var guideOpen by remember { mutableStateOf(false) }
    var editingBubble by remember { mutableStateOf<SecretaryGuide.Bubble?>(null) }
    var teaching by remember { mutableStateOf<String?>(null) }

    fun ask(chip: SecretaryDesk.Chip) {
        val handOver = chip.action as? SecretaryGuide.Action.Claude
        if (handOver != null) { vm.deskRun(chip.label, handOver); return }
        if (chip.ask == SecretaryDesk.Ask.CLAUDE) { claudeOpen = true; return }
        val i = turns.indexOfLast { it.reply != null }
        val steps = turns.getOrNull(i)?.reply?.steps.orEmpty()
        vm.deskAsk(chip, picks[i]?.toList() ?: steps)
    }

    var sending by remember { mutableStateOf<Pair<SecretaryPretext.Note, SecretaryPretext.Part?>?>(null) }
    if (claudeOpen) ClaudeSheet(vm, { claudeOpen = false }) { note, part ->
        claudeOpen = false
        sending = note to part
    }
    sending?.let { (note, part) -> SendSheet(vm, note, part) { sending = null } }
    // A bubble or a word that hands over to Claude: straight to the send sheet, with its message as the topic.
    vm.deskClaude?.let { part ->
        LaunchedEffect(part) { sending = vm.pretext() to part; vm.deskClaude = null }
    }
    teaching?.let { said -> TeachSheet(state, vm, said) { teaching = null } }
    editingBubble?.let { b ->
        BubbleSheet(
            state, vm, b.label, b.action, isWord = false,
            onDelete = { vm.editGuide { it.copy(bubbles = it.bubbles.filterNot { x -> x.label == b.label }) }; editingBubble = null },
            onDismiss = { editingBubble = null },
        ) { label, action ->
            vm.editGuide { it.copy(bubbles = it.bubbles.map { x -> if (x.label == b.label) x.copy(label = label, action = action) else x }) }
            editingBubble = null
        }
    }
    if (guideOpen) { GuideScreen(state, vm) { guideOpen = false }; return }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("เลขา", "Secretary"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = C.text, fontWeight = FontWeight.SemiBold)
            // Start over: today's greeting only, with a moment to undo it.
            Text(
                tr("เริ่มใหม่", "Start over"),
                Modifier.padding(end = 4.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.deskReset(); resetAt = System.currentTimeMillis() }.padding(horizontal = 12.dp, vertical = 10.dp),
                color = C.accentText, fontSize = TS.body, fontWeight = FontWeight.Medium,
            )
            SquareButton(Ic.gear, tr("ตั้งค่า", "Settings"), onSettings)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(turns) { i, t ->
                t.mine?.let { Mine(it) }
                t.reply?.let { r -> Answer(r, t.done, picks[i] ?: r.steps.toSet(), { picks[i] = it }, onOpen, vm::deskApplyMoves, { teaching = it }) { claudeOpen = true } }
            }
        }
        if (resetAt > 0) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(C.raised).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("เริ่มใหม่แล้ว", "Started over"), Modifier.weight(1f), color = C.text, fontSize = TS.body)
            Text(
                tr("เลิกทำ", "Undo"),
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.deskUndoReset(); resetAt = 0L }.padding(horizontal = 12.dp, vertical = 12.dp),
                color = C.accentText, fontSize = TS.body, fontWeight = FontWeight.SemiBold,
            )
        }
        // Talking to Claude is the first button, always there and in the accent; the answer's own buttons follow.
        val chips = turns.lastOrNull { it.reply != null }?.reply?.chips.orEmpty().filter { it.ask != SecretaryDesk.Ask.CLAUDE }
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(C.accent).clickable { claudeOpen = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.mic, null, tint = C.onAccent, modifier = Modifier.size(16.dp))
                    Text(tr("คุยกับ Claude", "Talk to Claude"), Modifier.padding(start = 6.dp), color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            items(chips) { c ->
                val first = c == chips.first()
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(if (first) C.accentDeep else C.raised)
                        .border(1.dp, if (first) C.accentLine else C.cardBorder, RoundedCornerShape(20.dp)).clickable { ask(c) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(c.label, color = if (first) C.text else C.text2, fontSize = TS.body, maxLines = 1) }
            }
            // The owner's bubbles, in the guide's order (those the answer already offers are not repeated). A long
            // press changes one; the pencil at the end opens the guide.
            items(vm.guide.shown.distinctBy { it.label }.filter { b -> chips.none { it.label == b.label } }, key = { "g:" + it.label }) { b ->
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(C.raised).border(1.dp, C.cardBorder, RoundedCornerShape(20.dp))
                        .combinedClickable(onClick = { ask(SecretaryDesk.Chip.of(b.label, b.action)) }, onLongClick = { editingBubble = b }).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(b.label, color = C.text2, fontSize = TS.body, maxLines = 1) }
            }
            item(key = "guide") {
                Box(
                    Modifier.size(width = 48.dp, height = 40.dp).clip(RoundedCornerShape(20.dp)).dashed(C.accentLine).clickable { guideOpen = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.pen, tr("แก้บอลลูน", "Change the bubbles"), tint = C.accentText, modifier = Modifier.size(18.dp)) }
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
private fun Answer(
    r: SecretaryDesk.Reply, done: Boolean, picked: Set<String>, onPick: (Set<String>) -> Unit, onOpen: (Task) -> Unit,
    onApplyMoves: (List<SecretaryDesk.Move>) -> Unit, onTeach: (String) -> Unit, onClaude: () -> Unit,
) {
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
            if (r.moves.isNotEmpty()) {
                // The moves by project, each with its own button, so a long answer reads as a few short ones.
                val groups = r.moves.groupBy { Projects.projectOf(it.task) ?: "" }
                val grouped = groups.size > 1
                groups.forEach { (project, moves) ->
                    Column(Modifier.fillMaxWidth().then(if (grouped) Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(C.raised).padding(10.dp) else Modifier)) {
                        if (grouped) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(project.ifEmpty { tr("ยังไม่มีบ้าน", "No project yet") }, Modifier.weight(1f), color = C.accentText, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                            Text(tr("${moves.size} งาน", "${moves.size} tasks"), color = C.faint, fontSize = TS.caption)
                        }
                        moves.forEach { m ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(m.task) }.padding(vertical = 6.dp)) {
                                Text(m.date.format(SHORT_DATE), Modifier.width(64.dp), color = C.accentText, fontSize = TS.body)
                                Column(Modifier.weight(1f)) {
                                    Text(m.task.title, color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(m.note, color = C.muted, fontSize = TS.caption)
                                }
                            }
                        }
                        if (grouped && !done) Text(
                            tr("จัดวันให้ ${moves.size} งาน", "Set dates for ${moves.size}"),
                            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(C.accentDeep).clickable { onApplyMoves(moves) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = C.text, fontSize = TS.caption,
                        )
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
            if (r.items.isNotEmpty()) Column {
                r.items.forEachIndexed { i, it ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(it.task) }.padding(vertical = 6.dp)) {
                        Text("${i + 1}.", Modifier.width(28.dp), color = C.accentText, fontSize = TS.body)
                        Column(Modifier.weight(1f)) {
                            Text(it.task.title, color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (it.note.isNotBlank()) Text(it.note, color = C.muted, fontSize = TS.caption)
                        }
                    }
                }
            }
            // A question the rules did not know: teach the word now, or take it to Claude.
            val teach = r.teach
            if (teach != null && !done) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(C.accent).clickable { onTeach(teach) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("สอนคำนี้", "Teach this word"), color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(C.accentDeep).clickable(onClick = onClaude).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("ส่งให้ Claude", "Send to Claude"), color = C.text, fontSize = TS.body) }
            }
            if (done) Text(tr("ทำแล้ว", "Done"), color = C.muted, fontSize = TS.caption)
        }
    }
}

/**
 * Talking to Claude: every part of the pretext note is here to read and change with its pencil: the base (sent first,
 * every time), each situation, and the ending (sent last); the note in Obsidian changes with it. A tap on a situation
 * (or the button below) picks where the file goes ([onSend] null: only changing, as from Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClaudeSheet(vm: TaskViewModel, onDismiss: () -> Unit, onSend: ((SecretaryPretext.Note, SecretaryPretext.Part?) -> Unit)?) {
    var note by remember { mutableStateOf<SecretaryPretext.Note?>(null) }
    LaunchedEffect(Unit) { note = vm.pretext() }
    // What is being changed: the base, the ending, or a situation (its old title; null: a new one).
    var editing by remember { mutableStateOf<Pair<String?, SecretaryPretext.Part>?>(null) }
    var which by remember { mutableStateOf("") }
    var previewing by remember { mutableStateOf<String?>(null) }
    var knowsOpen by remember { mutableStateOf(false) }
    val knowsCount by produceState(0, knowsOpen) { value = vm.knows().lines().count { it.startsWith("- ") } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.raised) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (onSend != null) tr("คุยเสียงกับ Claude", "Talk to Claude") else tr("ข้อความที่ส่งให้ Claude", "What is sent to Claude"),
                color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (onSend != null) tr("แตะเรื่องเพื่อส่ง แตะดินสอเพื่อแก้ ทุกอย่างที่ส่งไปแก้ได้ที่นี่", "Tap a topic to send it, the pencil to change it. Everything that is sent can be changed here")
                else tr("แตะดินสอเพื่อแก้ ทุกอย่างที่ส่งไปแก้ได้ที่นี่ และตรงกับไฟล์ เลขา pretext.md ใน Obsidian", "Tap the pencil to change. It matches the file เลขา pretext.md in Obsidian"),
                color = C.muted, fontSize = TS.caption,
            )
            val n = note
            if (n == null) Text(tr("กำลังอ่าน pretext...", "Reading the pretext..."), color = C.muted, fontSize = TS.body)
            else {
                if (onSend != null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr("จะส่ง: พื้นฐาน, เรื่องที่เลือก, Claude รู้จักคุณ $knowsCount ข้อ, งาน, นัด 14 วัน, แชตนี้", "Sends: the base, the topic, what Claude knows of you ($knowsCount lines), tasks, 14 days of events, this chat"),
                        Modifier.weight(1f), color = C.text2, fontSize = TS.caption,
                    )
                    Text(
                        tr("ดูข้อความ", "Preview"),
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { previewing = "" }.padding(4.dp),
                        color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.SemiBold,
                    )
                }
                PartRow(tr("พื้นฐาน (ส่งทุกครั้ง)", "Base (always sent)"), n.base.lines().firstOrNull().orEmpty(), null) { which = SecretaryPretext.BASE; editing = SecretaryPretext.BASE to SecretaryPretext.Part(SecretaryPretext.BASE, n.base) }
                n.situations.forEach { p ->
                    PartRow(p.title, p.body.lines().firstOrNull().orEmpty(), onSend?.let { { it(n, p) } }) { which = ""; editing = p.title to p }
                }
                PartRow(tr("ตอนจบ (ส่งทุกครั้ง)", "Ending (always sent)"), n.ending.lines().firstOrNull().orEmpty(), null) { which = SecretaryPretext.ENDING; editing = SecretaryPretext.ENDING to SecretaryPretext.Part(SecretaryPretext.ENDING, n.ending) }
                PartRow(tr("Claude รู้จักคุณ", "What Claude knows of you"), tr("$knowsCount ข้อ อ่านและแก้ได้", "$knowsCount lines, read and change"), null) { knowsOpen = true }
                TextButton(onClick = { which = ""; editing = null to SecretaryPretext.Part("", "") }) { Text(tr("+ เพิ่มเรื่อง", "+ Add topic"), color = C.accentText) }
                if (onSend != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(C.accent).clickable { onSend(n, null) },
                        contentAlignment = Alignment.Center,
                    ) { Text(tr("ส่งเป็นไฟล์ .md", "Send as a .md file"), color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(C.card).clickable { onSend(n, null) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr("ส่งโดยไม่เลือกเรื่อง", "Send without a topic"), color = C.text2, fontSize = TS.body) }
                }
            }
        }
    }
    if (knowsOpen) KnowsDialog(vm) { knowsOpen = false }
    previewing?.let { _ ->
        var text by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { note?.let { text = vm.claudePack(it, null, count = false) } }
        AlertDialog(
            onDismissRequest = { previewing = null },
            containerColor = C.raised,
            title = { Text(tr("ข้อความที่จะส่ง", "What will be sent")) },
            text = { Text(text ?: "...", Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), color = C.text2, fontSize = TS.caption) },
            confirmButton = { TextButton(onClick = { previewing = null }) { Text(tr("ปิด", "Close"), color = C.accent) } },
        )
    }
    editing?.let { (old, part) ->
        var title by remember(old) { mutableStateOf(part.title) }
        var body by remember(old) { mutableStateOf(part.body) }
        val fixed = old == SecretaryPretext.BASE || old == SecretaryPretext.ENDING
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = C.raised,
            title = { Text(if (fixed) tr("แก้ ${part.title}", "Change ${part.title}") else if (old == null) tr("เพิ่มเรื่อง", "Add a topic") else tr("แก้เรื่อง", "Edit topic")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fixed) Text(
                        if (old == SecretaryPretext.BASE) tr("ส่งเป็นส่วนแรกทุกครั้ง ไม่ว่าเลือกเรื่องไหน", "Sent first, every time") else tr("ส่งเป็นส่วนท้ายทุกครั้ง", "Sent last, every time"),
                        color = C.muted, fontSize = TS.caption,
                    )
                    else Field(title, { title = it }, tr("ชื่อปุ่ม เช่น ก่อนประชุม", "Button name"), single = true)
                    Field(body, { body = it }, tr("บอก Claude ว่าเรื่องนี้ต้องช่วยแบบไหน", "Tell Claude how to help with this"), single = false)
                }
            },
            confirmButton = {
                TextButton(enabled = (fixed || title.isNotBlank()) && body.isNotBlank(), onClick = {
                    val next = when (old) {
                        SecretaryPretext.BASE -> note?.copy(base = body.trim())
                        SecretaryPretext.ENDING -> note?.copy(ending = body.trim())
                        else -> note?.with(old, SecretaryPretext.Part(title.trim(), body.trim()))
                    }
                    next?.let { note = it; vm.savePretext(it) }
                    editing = null
                }) { Text(tr("บันทึก", "Save"), color = C.accent) }
            },
            dismissButton = {
                Row {
                    if (fixed) {
                        val d = SecretaryPretext.parse(SecretaryPretext.DEFAULT)
                        TextButton(onClick = { body = if (old == SecretaryPretext.BASE) d.base else d.ending }) { Text(tr("ใช้ค่าตั้งต้น", "Use the default"), color = C.amber) }
                    } else if (old != null) TextButton(onClick = { note?.with(old, null)?.let { note = it; vm.savePretext(it) }; editing = null }) { Text(tr("ลบ", "Delete"), color = C.red) }
                    TextButton(onClick = { editing = null }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) }
                }
            },
        )
    }
}

/** One part of the pretext: its name and first line; a tap on the row does [onTap] (when there is one), the pencil changes it. */
@Composable
private fun PartRow(title: String, line: String, onTap: (() -> Unit)?, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier.clickable(onClick = onEdit))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
            Text(line, color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.raised).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
            Icon(Ic.pen, tr("แก้", "Edit"), tint = C.text2, modifier = Modifier.size(18.dp))
        }
    }
}

/** What Claude has learned of the owner (Claude รู้จักคุณ.md): the lines, to read and change. */
@Composable
internal fun KnowsDialog(vm: TaskViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { text = vm.knows() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("Claude รู้จักคุณ", "What Claude knows of you")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("เลขาเติมให้จากที่คุยกัน ข้อที่ไม่ใช่แล้ว ลบหรือแก้ได้เลย", "The secretary adds to it from your talks. Change or delete what is no longer true"), color = C.muted, fontSize = TS.caption)
                text?.let { t -> Field(t, { text = it }, tr("ยังไม่มีข้อ", "Nothing yet"), single = false) }
            }
        },
        confirmButton = { TextButton(enabled = text != null, onClick = { text?.let(vm::saveKnows); onDismiss() }) { Text(tr("บันทึก", "Save"), color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
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

/**
 * Where the file for Claude goes: the Claude app (to talk on), the secretary's room (attach it and type "รอบเลขา" for an
 * urgent round), the vault to keep, or plain text when a file cannot be attached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendSheet(vm: TaskViewModel, note: SecretaryPretext.Note, part: SecretaryPretext.Part?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { text = vm.claudePack(note, part) }
    val name = remember {
        val n = java.time.LocalDateTime.now()
        "เลขา %04d-%02d-%02d %02d%02d.md".format(n.year, n.monthValue, n.dayOfMonth, n.hour, n.minute)
    }
    fun toast(t: String) = android.widget.Toast.makeText(context, t, android.widget.Toast.LENGTH_LONG).show()
    fun asText(t: String) { shareToClaude(context, t); onDismiss() }
    fun file(t: String, claudeApp: Boolean, hint: String?) {
        if (shareFile(context, name, t, claudeApp)) { hint?.let(::toast); onDismiss() } else { toast(tr("แนบไฟล์ไม่ได้ ส่งเป็นข้อความแทน", "Could not attach the file, sending text instead")); asText(t) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.raised) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (part != null) tr("ส่งเรื่อง ${part.title}", "Send ${part.title}") else tr("ส่งให้ Claude", "Send to Claude"),
                color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold,
            )
            val t = text
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(name, color = C.text, fontSize = TS.body)
                Text(if (t == null) tr("กำลังเตรียม...", "Preparing...") else tr("${(t.toByteArray().size + 1023) / 1024} KB พร้อมส่ง", "${(t.toByteArray().size + 1023) / 1024} KB, ready"), color = C.muted, fontSize = TS.caption)
            }
            SendOption("1", tr("ส่งไปแอป Claude", "To the Claude app"), tr("แนบไฟล์ .md แล้วคุยเสียงต่อ แนะนำ", "Attach the .md file and talk on (recommended)"), true, t != null) { file(t!!, true, null) }
            SendOption("2", tr("ส่งเข้าห้องเลขา", "To the secretary's room"), tr("แนบไฟล์ แล้วพิมพ์ รอบเลขา สำหรับงานด่วน เลขาจะอ่านทันที", "Attach it and type รอบเลขา for an urgent round"), false, t != null) {
                file(t!!, false, tr("เปิดห้องเลขา แนบไฟล์ แล้วพิมพ์ รอบเลขา", "Open the secretary's room, attach the file and type รอบเลขา"))
            }
            SendOption("3", tr("บันทึกไว้ในคลัง", "Keep in the vault"), tr("เก็บที่ Omni/ส่งเลขา ไว้ดูหรือส่งทีหลัง", "Kept in Omni/ส่งเลขา to read or send later"), false, t != null) {
                vm.keepSendFile(name, t!!); toast(tr("เก็บไว้ที่ Omni/ส่งเลขา แล้ว", "Kept in Omni/ส่งเลขา")); onDismiss()
            }
            SendOption("4", tr("ส่งเป็นข้อความ", "Send as text"), tr("เฉพาะเมื่อแนบไฟล์ไม่ได้ ข้อความยาวอาจถูกตัด", "Only when a file cannot be attached; a long text may be cut"), false, t != null) { asText(t!!) }
        }
    }
}

@Composable
private fun SendOption(n: String, title: String, sub: String, main: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (main) C.accentDeep else C.card).border(1.dp, if (main) C.accentLine else C.cardBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (main) C.accent else C.raised), contentAlignment = Alignment.Center) {
            Text(n, color = if (main) C.onAccent else C.text2, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
            Text(sub, color = C.muted, fontSize = TS.caption)
        }
    }
}

/** Shares [text] as a .md file (kept in the cache for a day); to the Claude app when [claudeApp], else wherever the owner picks. False if it cannot. */
internal fun shareFile(context: Context, name: String, text: String, claudeApp: Boolean): Boolean = runCatching {
    val dir = java.io.File(context.cacheDir, "send").also { it.mkdirs() }
    dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
    val file = java.io.File(dir, name.replace(Regex("[^\\w ก-๙.-]"), "-"))
    file.writeText(text)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    share.clipData = android.content.ClipData.newRawUri(name, uri)
    val toClaude = Intent(share).setPackage("com.anthropic.claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (claudeApp) {
        try { context.startActivity(toClaude) } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(share, tr("ส่งให้ Claude", "Send to Claude")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } else context.startActivity(Intent.createChooser(share, tr("ส่งเข้าห้องเลขา", "Send to the secretary's room")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

/** Opens Claude's app with [text] ready to send; another app can take it when Claude's is not installed. */
internal fun shareToClaude(context: Context, text: String) {
    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent(share).setPackage("com.anthropic.claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(share, tr("ส่งให้ Claude", "Send to Claude")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


/** In Settings, Secretary and AI: the parts of what is sent to Claude, and what it knows, each to open and change. */
@Composable
fun ClaudeSettingsRows(vm: TaskViewModel) {
    var pretext by remember { mutableStateOf(false) }
    var knows by remember { mutableStateOf(false) }
    Card {
        NavRow(tr("ข้อความที่ส่งให้ Claude", "What is sent to Claude"), tr("พื้นฐาน เรื่องที่คุย และตอนจบ แก้ได้ทั้งหมด", "The base, the topics and the ending, all can be changed")) { pretext = true }
        Divider(start = 16.dp)
        NavRow(tr("Claude รู้จักคุณ", "What Claude knows of you"), tr("ที่ Claude เรียนรู้จากการคุย อ่านและแก้ได้", "What Claude learned from your talks, read and change")) { knows = true }
    }
    if (pretext) ClaudeSheet(vm, { pretext = false }, null)
    if (knows) KnowsDialog(vm) { knows = false }
}
