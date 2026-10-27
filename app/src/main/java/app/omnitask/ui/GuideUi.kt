package app.omnitask.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.omnitask.model.DateBucket
import app.omnitask.model.Priority
import app.omnitask.model.SecretaryGuide
import app.omnitask.model.SecretaryGuide.Action
import app.omnitask.model.SecretaryGuide.Bubble
import app.omnitask.model.SecretaryGuide.Builtin
import app.omnitask.model.SecretaryGuide.Filter
import app.omnitask.model.SortBy
import app.omnitask.model.Status
import app.omnitask.model.TaskKind
import app.omnitask.model.label
import app.omnitask.model.tr

/**
 * The secretary's guide in the app, as in the mock "คู่มือเลขา": four tabs over the same note the owner can edit in
 * Obsidian (เลขา คู่มือ.md). Every change is written to the note at once.
 */
@Composable
fun GuideScreen(state: UiState, vm: TaskViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var tab by rememberSaveable { mutableStateOf(0) }
    val g = vm.guide
    var editing by remember { mutableStateOf<Bubble?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<SecretaryGuide.Word?>(null) }
    var addingWord by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Ic.back, tr("กลับ", "Back"), tint = C.text, modifier = Modifier.size(22.dp))
            }
            Text(tr("คู่มือเลขา", "Secretary guide"), Modifier.weight(1f), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
        }
        GuideTabs(tab) { tab = it }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                0 -> BubblesTab(g, vm, onEdit = { editing = it }, onAdd = { adding = true })
                1 -> WordsTab(g, onEdit = { editingWord = it }, onAdd = { addingWord = true })
                2 -> StyleTab(g, vm)
                else -> LearnTab(g, vm)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp)
                .padding(bottom = NavClearance - 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.notes, null, tint = C.muted, modifier = Modifier.size(16.dp))
            Text(tr("บันทึกลงโน้ต เลขา คู่มือ.md แก้ใน Obsidian ได้", "Saved to the note เลขา คู่มือ.md, editable in Obsidian"), Modifier.padding(start = 8.dp), color = C.muted, fontSize = TS.caption)
        }
    }

    editing?.let { b ->
        BubbleSheet(state, vm, b.label, b.action, isWord = false, onDelete = { vm.editGuide { it.copy(bubbles = it.bubbles.filterNot { x -> x.label == b.label }) }; editing = null }, onDismiss = { editing = null }) { label, action ->
            vm.editGuide { it.copy(bubbles = it.bubbles.map { x -> if (x.label == b.label) x.copy(label = label, action = action) else x }) }
            editing = null
        }
    }
    if (adding) BubbleSheet(state, vm, "", Action.App(Builtin.NEXT), isWord = false, onDelete = null, onDismiss = { adding = false }) { label, action ->
        vm.editGuide { it.copy(bubbles = it.bubbles.filterNot { x -> x.label == label }.let { all -> all.filter { x -> x.on } + Bubble(label, action) + all.filter { x -> !x.on } }) }
        adding = false
    }
    editingWord?.let { w ->
        BubbleSheet(state, vm, w.word, w.action, isWord = true, onDelete = { vm.editGuide { it.copy(words = it.words.filterNot { x -> x.word == w.word }) }; editingWord = null }, onDismiss = { editingWord = null }) { word, action ->
            vm.editGuide { it.copy(words = it.words.map { x -> if (x.word == w.word) SecretaryGuide.Word(word, action) else x }) }
            editingWord = null
        }
    }
    if (addingWord) BubbleSheet(state, vm, "", Action.App(Builtin.NEXT), isWord = true, onDelete = null, onDismiss = { addingWord = false }) { word, action ->
        vm.editGuide { it.copy(words = it.words.filterNot { x -> SecretaryGuide.squash(x.word) == SecretaryGuide.squash(word) } + SecretaryGuide.Word(word, action)) }
        addingWord = false
    }
}

@Composable
private fun GuideTabs(tab: Int, onTab: (Int) -> Unit) {
    val names = listOf(tr("บอลลูน", "Bubbles"), tr("คำที่พิมพ์", "Words"), tr("วิธีตอบ", "Answers"), tr("เรียนรู้", "Learned"))
    Row(
        Modifier.padding(horizontal = 16.dp).padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        names.forEachIndexed { i, n ->
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(if (i == tab) C.accentDeep else Color.Transparent).clickable { onTab(i) },
                contentAlignment = Alignment.Center,
            ) { Text(n, color = if (i == tab) C.text else C.muted, fontSize = TS.body, fontWeight = if (i == tab) FontWeight.Medium else FontWeight.Normal, maxLines = 1) }
        }
    }
}

// ---- Bubbles ----

@Composable
private fun BubblesTab(g: SecretaryGuide.Guide, vm: TaskViewModel, onEdit: (Bubble) -> Unit, onAdd: () -> Unit) {
    // While a row is dragged the order lives here; it is written to the note when the finger lifts.
    var draft by remember { mutableStateOf<List<Bubble>?>(null) }
    val shown = draft ?: g.shown
    val hidden = g.bubbles.filter { !it.on }
    Text(tr("ลากที่ขีดด้านซ้ายเพื่อเรียงลำดับ แตะเพื่อแก้", "Drag the lines on the left to reorder, tap to change"), color = C.muted, fontSize = TS.caption)
    ReorderColumn(
        items = shown, keyOf = { it.label },
        onMove = { from, to -> draft = shown.toMutableList().apply { add(to, removeAt(from)) } },
        onDrop = { draft?.let { order -> vm.editGuide { it.copy(bubbles = order + it.bubbles.filter { b -> !b.on && order.none { o -> o.label == b.label } }) } }; draft = null },
    ) { b, handle, dragging ->
        BubbleRow(b, handle, dragging, onClick = { onEdit(b) }) {
            vm.editGuide { it.copy(bubbles = it.bubbles.filterNot { x -> x.label == b.label } + b.copy(on = false)) }
        }
    }
    if (hidden.isNotEmpty()) {
        Text(tr("ซ่อนไว้ (ไม่โชว์ในแถว)", "Hidden (not in the row)"), Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption)
        hidden.forEach { b ->
            BubbleRow(b, Modifier, false, onClick = { onEdit(b) }) {
                vm.editGuide { it.copy(bubbles = it.bubbles.filterNot { x -> x.label == b.label }.let { all -> all.filter { x -> x.on } + b.copy(on = true) + all.filter { x -> !x.on } }) }
            }
        }
    }
    AddButton(tr("เพิ่มบอลลูน", "Add a bubble"), onAdd)
}

@Composable
private fun BubbleRow(b: Bubble, handle: Modifier, dragging: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (dragging) C.accentDeep else if (b.on) C.card else C.sunken)
            .then(if (dragging) Modifier.border(1.dp, C.accentLine, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(handle.size(44.dp), contentAlignment = Alignment.Center) {
            if (b.on) Icon(Ic.notes, tr("ลากเพื่อเรียง", "Drag to reorder"), tint = if (dragging) C.text else C.faint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(b.label, color = if (b.on) C.text else C.text2, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(b.action.describe(), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(6.dp)) { OnOff(b.on) }
    }
}

/**
 * A column whose rows move by dragging their handle: [onMove] as a row passes its neighbour's middle, [onDrop] when
 * the finger lifts. Rows are kept by [key], so a drag goes on while the order changes under it.
 */
@Composable
private fun <T> ReorderColumn(
    items: List<T>, keyOf: (T) -> Any, onMove: (Int, Int) -> Unit, onDrop: () -> Unit,
    row: @Composable (T, Modifier, Boolean) -> Unit,
) {
    var dragged by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val gap = with(LocalDensity.current) { 6.dp.toPx() }
    val latest by rememberUpdatedState(items)
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val k = keyOf(item)
            key(k) {
                val dragging = dragged == k
                val handle = Modifier.pointerInput(k) {
                    detectDragGestures(
                        onDragStart = { dragged = k; offset = 0f },
                        onDragEnd = { dragged = null; offset = 0f; drop() },
                        onDragCancel = { dragged = null; offset = 0f; drop() },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val at = latest.indexOfFirst { keyOf(it) == k }
                        if (at < 0) return@detectDragGestures
                        val step = (heights[k] ?: 0) + gap
                        if (offset > step / 2 && at < latest.size - 1) { move(at, at + 1); offset -= step }
                        else if (offset < -step / 2 && at > 0) { move(at, at - 1); offset += step }
                    }
                }
                Box(
                    Modifier.onSizeChanged { heights[k] = it.height }.zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) offset else 0f },
                ) { row(item, handle, dragging) }
            }
        }
    }
}

// ---- Words ----

@Composable
private fun WordsTab(g: SecretaryGuide.Guide, onEdit: (SecretaryGuide.Word) -> Unit, onAdd: () -> Unit) {
    Text(tr("ข้อความที่มีคำนี้ แอปทำตามทันที คำที่ไม่มีในนี้ เลขาจะเสนอให้สอน หรือส่งให้ Claude", "A message with one of these words is done at once; others can be taught or sent to Claude"), color = C.muted, fontSize = TS.caption)
    if (g.words.isEmpty()) Text(tr("ยังไม่มีคำที่สอนไว้ สอนได้ในแชตตอนที่เลขาไม่รู้จักคำ หรือกดเพิ่มคำ", "No words yet. Teach one in the chat when a word is not known, or add one here"), color = C.text2, fontSize = TS.body)
    g.words.forEach { w ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).clickable { onEdit(w) }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("ถ้าพิมพ์", "If typed"), color = C.muted, fontSize = TS.caption)
                Text(w.word, Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.accentDeep).padding(horizontal = 10.dp, vertical = 2.dp), color = C.accentText, fontSize = TS.body)
            }
            Text(w.action.describe(), color = C.text, fontSize = TS.body)
        }
    }
    AddButton(tr("เพิ่มคำ", "Add a word"), onAdd)
}

// ---- How to answer ----

@Composable
private fun StyleTab(g: SecretaryGuide.Guide, vm: TaskViewModel) {
    Text(tr("รายการยาวแค่ไหน", "How long a list is"), color = C.muted, fontSize = TS.caption)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SecretaryGuide.Length.entries.forEach { l ->
            val on = l == g.length
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.accentDeep else C.card)
                    .then(if (on) Modifier.border(1.dp, C.accentLine, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { vm.editGuide { it.copy(length = l) } },
                contentAlignment = Alignment.Center,
            ) { Text(l.label, color = if (on) C.text else C.text2, fontSize = TS.body) }
        }
    }
    Text(tr("ลำดับงาน: อะไรมาก่อน (ลากเพื่อเรียง)", "What comes first (drag to reorder)"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
    var draft by remember { mutableStateOf<List<SecretaryGuide.Factor>?>(null) }
    val order = draft ?: g.order
    ReorderColumn(
        items = order, keyOf = { it.name },
        onMove = { from, to -> draft = order.toMutableList().apply { add(to, removeAt(from)) } },
        onDrop = { draft?.let { d -> vm.editGuide { it.copy(order = d) } }; draft = null },
    ) { f, handle, dragging ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (dragging) C.accentDeep else C.card).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(handle.size(44.dp), contentAlignment = Alignment.Center) { Icon(Ic.notes, tr("ลากเพื่อเรียง", "Drag to reorder"), tint = C.faint, modifier = Modifier.size(20.dp)) }
            Text(f.label, Modifier.weight(1f), color = C.text, fontSize = TS.body)
        }
    }
    Text(tr("บอก Claude ทุกครั้งที่ส่งต่อ", "Tell Claude every time"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
    g.forClaude.forEach { line ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(line, Modifier.weight(1f).padding(vertical = 10.dp), color = C.text, fontSize = TS.body)
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { vm.editGuide { it.copy(forClaude = it.forClaude - line) } }, contentAlignment = Alignment.Center) {
                Icon(Ic.close, tr("ลบข้อนี้", "Remove this"), tint = C.muted, modifier = Modifier.size(16.dp))
            }
        }
    }
    var line by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextBox(line, { line = it }, tr("เช่น ถ้าไม่แน่ใจ ให้ถามกลับ 1 ข้อ", "e.g. Ask one question back when unsure"), Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (line.isNotBlank()) C.accent else C.raised)
                .clickable(enabled = line.isNotBlank()) { val l = line.trim(); vm.editGuide { it.copy(forClaude = it.forClaude + l) }; line = "" },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.plus, tr("เพิ่มข้อ", "Add"), tint = if (line.isNotBlank()) C.onAccent else C.muted, modifier = Modifier.size(18.dp)) }
    }
}

// ---- Learned ----

@Composable
private fun LearnTab(g: SecretaryGuide.Guide, vm: TaskViewModel) {
    var refresh by remember { mutableStateOf(0) }
    val hints by produceState(emptyList<String>(), g, refresh) { value = vm.guideHints() }
    Text(tr("เลขาของ Claude อ่านสิ่งที่คุณกดและพิมพ์ทุก 3 ชั่วโมง แล้วเขียนไว้ที่นี่ แอปก็เสนอเองด้วย", "Claude's secretary reads what you tap and type every 3 hours and writes here; the app offers some too"), color = C.muted, fontSize = TS.caption)
    val proposals = g.learned.filter { it.proposal != null }
    if (proposals.isNotEmpty() || hints.isNotEmpty()) Text(tr("ข้อเสนอ รอคุณตอบ", "Proposals waiting for you"), Modifier.padding(top = 6.dp), color = C.muted, fontSize = TS.caption)
    proposals.forEach { l ->
        val p = l.proposal!!
        val (title, yes) = when (p) {
            is SecretaryGuide.Proposal.AddWord -> tr("เพิ่มคำ \"${p.word.word}\" ให้หมายถึง ${p.word.action.describe()}", "Add the word \"${p.word.word}\": ${p.word.action.describe()}") to tr("เพิ่มเลย", "Add")
            is SecretaryGuide.Proposal.AddBubble -> tr("เพิ่มบอลลูน \"${p.bubble.label}\": ${p.bubble.action.describe()}", "Add the bubble \"${p.bubble.label}\": ${p.bubble.action.describe()}") to tr("เพิ่มเลย", "Add")
            is SecretaryGuide.Proposal.HideBubble -> tr("ซ่อนบอลลูน \"${p.label}\"", "Hide the bubble \"${p.label}\"") to tr("ซ่อนเลย", "Hide")
        }
        ProposalCard(title, sourceOf(l.text), yes, onNo = { vm.editGuide { it.copy(learned = it.learned.filterNot { x -> x.line == l.line }) } }) {
            vm.editGuide { SecretaryGuide.accept(it, l) }
        }
    }
    hints.forEach { label ->
        ProposalCard(
            tr("ซ่อนบอลลูน \"$label\"", "Hide the bubble \"$label\""), tr("2 สัปดาห์แล้วไม่ได้แตะเลย (แอปเห็นเอง)", "Not tapped in 2 weeks (seen by the app)"),
            tr("ซ่อนเลย", "Hide"), onNo = { vm.dismissGuideHint(label); refresh++ },
        ) { vm.editGuide { it.copy(bubbles = it.bubbles.map { b -> if (b.label == label) b.copy(on = false) else b }) } }
    }
    val lessons = g.learned.filter { it.proposal == null }
    Text(tr("ที่เรียนรู้แล้ว", "Learned"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
    if (lessons.isEmpty()) Text(tr("ยังไม่มี เลขาจะเริ่มเขียนรอบถัดไป", "Nothing yet; the secretary writes from its next round"), color = C.text2, fontSize = TS.body)
    lessons.forEach { l ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(l.text.replace(SOURCE, "").trim(), color = C.text, fontSize = TS.body)
            sourceOf(l.text)?.let { Text(it, color = C.muted, fontSize = TS.caption) }
        }
    }
}

private val SOURCE = Regex("""\s*\((\d{4}-\d{2}-\d{2})(?:,\s*จาก\s*([^)]*))?\)\s*$""")

/** "(2026-10-24, จาก แชตเลขา)" at the end of a learned line, as "2026-10-24 จาก แชตเลขา". */
private fun sourceOf(text: String): String? = SOURCE.find(text)?.let { m ->
    m.groupValues[1] + m.groupValues[2].takeIf { it.isNotBlank() }?.let { tr(" จาก ", " from ") + it }.orEmpty()
}

@Composable
private fun ProposalCard(title: String, note: String?, yes: String, onNo: () -> Unit, onYes: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.accentDeep).border(1.dp, C.accentLine, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = C.text, fontSize = TS.body)
        note?.let { Text(it, color = C.text2, fontSize = TS.caption) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(tr("ไม่เอา", "No"), onNo, Modifier.weight(1f))
            PrimaryButton(yes, onYes, Modifier.weight(1f))
        }
    }
}

// ---- One bubble, or one word ----

private enum class Kind { APP, SHOW, CLAUDE }

/**
 * Changes a bubble ([isWord] false) or a taught word: its name and what it does, which is something the app does, a
 * list of work by conditions, or handing over to Claude with a message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleSheet(
    state: UiState, vm: TaskViewModel, name: String, action: Action, isWord: Boolean,
    onDelete: (() -> Unit)?, onDismiss: () -> Unit, onSave: (String, Action) -> Unit,
) {
    var label by remember { mutableStateOf(name) }
    var kind by remember { mutableStateOf(when (action) { is Action.App -> Kind.APP; is Action.Show -> Kind.SHOW; is Action.Claude -> Kind.CLAUDE }) }
    var builtin by remember { mutableStateOf((action as? Action.App)?.builtin ?: Builtin.NEXT) }
    var filter by remember { mutableStateOf((action as? Action.Show)?.filter ?: Filter(withinDays = 3)) }
    var message by remember { mutableStateOf((action as? Action.Claude)?.text.orEmpty()) }
    fun current(): Action = when (kind) {
        Kind.APP -> Action.App(builtin)
        Kind.SHOW -> Action.Show(filter)
        Kind.CLAUDE -> Action.Claude(message.trim())
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.raised) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    isWord && name.isEmpty() -> tr("เพิ่มคำ", "Add a word")
                    isWord -> tr("แก้คำ", "Change the word")
                    name.isEmpty() -> tr("เพิ่มบอลลูน", "Add a bubble")
                    else -> tr("แก้บอลลูน", "Change the bubble")
                },
                color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold,
            )
            Text(if (isWord) tr("คำที่พิมพ์", "The word") else tr("ข้อความบนบอลลูน", "Text on the bubble"), color = C.muted, fontSize = TS.caption)
            TextBox(label, { label = it }, if (isWord) tr("เช่น เลื่อน", "e.g. move") else tr("เช่น ด่วน Siriraj", "e.g. Urgent work"), Modifier.fillMaxWidth())
            Text(tr("แตะแล้วให้", "When tapped"), color = C.muted, fontSize = TS.caption)
            Choice(kind == Kind.APP, tr("ทำคำสั่งในแอป", "Do something in the app"), tr("เร็ว ไม่ต้องใช้เน็ต", "Quick, no internet needed"), { kind = Kind.APP }) {
                BuiltinPicker(builtin) { builtin = it }
            }
            Choice(kind == Kind.SHOW, tr("แสดงงานตามเงื่อนไข", "Show work by conditions"), null, { kind = Kind.SHOW }) {
                FilterEditor(state, filter) { filter = it }
            }
            Choice(kind == Kind.CLAUDE, tr("ส่งให้ Claude พร้อมข้อความ", "Send to Claude with a message"), tr("เปิดแอป Claude พร้อมงานและบทสนทนานี้", "Opens Claude with your work and this chat"), { kind = Kind.CLAUDE }) {
                TextBox(message, { message = it }, tr("เช่น ช่วยวางแผนพรุ่งนี้", "e.g. Help me plan tomorrow"), Modifier.fillMaxWidth(), single = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.red.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isWord) tr("ลบคำ", "Delete") else tr("ลบบอลลูน", "Delete"), color = C.red, fontSize = TS.body) }
                val ok = label.isNotBlank() && !(kind == Kind.SHOW && filter.isEmpty)
                Box(
                    Modifier.weight(2f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (ok) C.accent else C.card).clickable(enabled = ok) { onSave(label.trim(), current()) },
                    contentAlignment = Alignment.Center,
                ) { Text(tr("บันทึก", "Save"), color = if (ok) C.onAccent else C.muted, fontSize = TS.body, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** One way a bubble can work: a round mark and a title, and its own settings under it while picked. */
@Composable
private fun Choice(on: Boolean, title: String, note: String?, onPick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) C.accentDeep else C.bg)
            .border(1.dp, if (on) C.accentLine else C.cardBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) C.accent else C.control, CircleShape), contentAlignment = Alignment.Center) {
                if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(C.accent))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, color = C.text, fontSize = TS.body)
                note?.let { Text(it, color = C.muted, fontSize = TS.caption) }
            }
        }
        if (on) content()
    }
}

/** What the app can do, as a row that opens the list. */
@Composable
internal fun BuiltinPicker(value: Builtin, onPick: (Builtin) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).border(1.dp, C.accentLine, RoundedCornerShape(10.dp))
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value.label, Modifier.weight(1f), color = C.text, fontSize = TS.body)
            Icon(Ic.down, null, tint = C.text2, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(open, { open = false }, containerColor = C.raised) {
            Builtin.entries.forEach { b -> DropdownMenuItem(text = { Text(b.label, color = C.text, fontSize = TS.body) }, onClick = { onPick(b); open = false }) }
        }
    }
}

/** The conditions of a list bubble, laid out as in the mock: tags, real deadlines, priority, order, and more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterEditor(state: UiState, f: Filter, onChange: (Filter) -> Unit) {
    var more by remember { mutableStateOf(f.kinds.isNotEmpty() || f.statuses.isNotEmpty() || f.lists.isNotEmpty() || f.text.isNotBlank() || f.staleDays != null) }
    val count = remember(f, state.tasks) { state.tasks.count { it.parent == null && f.matches(it, state.today) } }
    fun <T> Set<T>.flip(v: T) = if (v in this) this - v else this + v

    FieldLabel(tr("โปรเจกต์และแท็ก", "Projects and tags"))
    var tagsOpen by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        f.tags.forEach { t -> Chip(tagLabel(t), true, { onChange(f.copy(tags = f.tags - t)) }) }
        Box {
            Text(
                tr("+ เพิ่ม", "+ Add"),
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).dashed(C.accentLine).clickable { tagsOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                color = C.accentText, fontSize = TS.body,
            )
            DropdownMenu(tagsOpen, { tagsOpen = false }, containerColor = C.raised) {
                (listOf(SecretaryGuide.ME_TAG, SecretaryGuide.CLAUDE_TAG) + state.tags.filterNot { it.startsWith("remind-at-") || it == SecretaryGuide.ME_TAG || it == SecretaryGuide.CLAUDE_TAG })
                    .filter { it !in f.tags }.forEach { t ->
                        DropdownMenuItem(text = { Text(tagLabel(t), color = C.text, fontSize = TS.body) }, onClick = { onChange(f.copy(tags = f.tags + t)); tagsOpen = false })
                    }
            }
        }
    }
    FieldLabel(tr("กำหนดจริง", "Real deadline"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(DateBucket.OVERDUE, DateBucket.TODAY, DateBucket.THIS_WEEK, DateBucket.NEXT_WEEK, DateBucket.NO_DATE).forEach { b ->
            Chip(b.label, b in f.buckets, { onChange(f.copy(buckets = f.buckets.flip(b))) })
        }
        Chip(tr("ภายใน ${f.withinDays ?: 7} วัน", "Within ${f.withinDays ?: 7} days"), f.withinDays != null, { onChange(f.copy(withinDays = if (f.withinDays == null) 7 else null)) })
    }
    f.withinDays?.let { n -> Stepper(tr("ภายในกี่วัน", "Within how many days"), n, 1..60) { onChange(f.copy(withinDays = it)) } }
    FieldLabel(tr("ความสำคัญ", "Priority"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Priority.HIGHEST, Priority.HIGH, Priority.MEDIUM, Priority.LOW).forEach { p ->
            Chip(p.label, p in f.priorities, { onChange(f.copy(priorities = f.priorities.flip(p))) }, dot = p.tint)
        }
    }
    var sortOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("เรียงตาม", "Sort by"), color = C.muted, fontSize = TS.caption)
        Box(Modifier.padding(start = 8.dp)) {
            Text(
                (f.sortBy ?: SortBy.DUE).label,
                Modifier.clip(RoundedCornerShape(8.dp)).background(C.bg).border(1.dp, C.accentLine, RoundedCornerShape(8.dp)).clickable { sortOpen = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                color = C.text, fontSize = TS.body,
            )
            DropdownMenu(sortOpen, { sortOpen = false }, containerColor = C.raised) {
                SortBy.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label, color = C.text, fontSize = TS.body) }, onClick = { onChange(f.copy(sortBy = s.takeIf { it != SortBy.DUE })); sortOpen = false }) }
            }
        }
        Box(Modifier.weight(1f))
        Text(tr("ตรงเงื่อนไข $count งาน", "$count match"), color = C.accentText, fontSize = TS.body)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { more = !more }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("เงื่อนไขเพิ่ม: ประเภท, สถานะ, List, คำในชื่อ, ค้างมากี่วัน", "More: type, status, list, words, days old"), Modifier.weight(1f), color = C.accentText, fontSize = TS.caption)
        Icon(if (more) Ic.up else Ic.down, null, tint = C.accentText, modifier = Modifier.size(16.dp))
    }
    if (more) {
        FieldLabel(tr("ประเภทงาน", "Task type"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskKind.entries.forEach { k -> Chip(k.label, k in f.kinds, { onChange(f.copy(kinds = f.kinds.flip(k))) }) }
            state.listNames.forEach { l -> Chip(l, l in f.lists, { onChange(f.copy(lists = f.lists.flip(l))) }) }
        }
        FieldLabel(tr("สถานะ (ไม่เลือก = งานที่ยังเปิด)", "Status (none: open work)"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Status.entries.forEach { s -> Chip(s.label, s in f.statuses, { onChange(f.copy(statuses = f.statuses.flip(s))) }) }
        }
        FieldLabel(tr("คำในชื่องาน", "Words in the title"))
        TextBox(f.text, { onChange(f.copy(text = it)) }, tr("เช่น ตาราง", "e.g. report"), Modifier.fillMaxWidth())
        Chip(tr("ค้างนาน ${f.staleDays ?: 14} วันขึ้นไป", "${f.staleDays ?: 14}+ days old"), f.staleDays != null, { onChange(f.copy(staleDays = if (f.staleDays == null) 14 else null)) })
        f.staleDays?.let { n -> Stepper(tr("ค้างนานกี่วัน", "How many days old"), n, 1..365) { onChange(f.copy(staleDays = it)) } }
    }
}

private fun tagLabel(t: String) = when (t) {
    SecretaryGuide.ME_TAG -> tr("ตาของฉัน", "My turn")
    SecretaryGuide.CLAUDE_TAG -> tr("ตาของ Claude", "Claude's turn")
    else -> "#$t"
}

@Composable
private fun FieldLabel(text: String) = Text(text, color = C.text2, fontSize = TS.caption)

/** A number with − and + beside it. */
@Composable
private fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = C.text2, fontSize = TS.caption)
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.bg).clickable(enabled = value > range.first) { onChange(value - 1) }, contentAlignment = Alignment.Center) { Text("−", color = C.text, fontSize = TS.title) }
        Text("$value", Modifier.width(44.dp), color = C.text, fontSize = TS.body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.bg).clickable(enabled = value < range.last) { onChange(value + 1) }, contentAlignment = Alignment.Center) { Text("+", color = C.text, fontSize = TS.title) }
    }
}

@Composable
private fun TextBox(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, single: Boolean = true) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = single, maxLines = if (single) 1 else 4,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body); inner() } },
    )
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).dashed(C.accentLine).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Ic.plus, null, tint = C.accentText, modifier = Modifier.size(18.dp))
        Text(text, Modifier.padding(start = 8.dp), color = C.accentText, fontSize = TS.body)
    }
}

/** A dashed rounded outline: something to add, or the pencil at the end of the bubbles. */
fun Modifier.dashed(color: Color, radius: Float = 999f): Modifier = drawBehind {
    val r = minOf(radius, size.height / 2)
    drawRoundRect(
        color = color, cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

// ---- Teaching a word in the chat ----

/**
 * "สอนคำนี้": the words of what was typed to tap, the word itself to change, what it should do, and whether it is a
 * bubble too. Saved to the guide at once, and done right away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TeachSheet(state: UiState, vm: TaskViewModel, said: String, onDismiss: () -> Unit) {
    val pieces = remember(said) { thaiWords(said) }
    var word by remember { mutableStateOf(pieces.firstOrNull() ?: said.trim()) }
    var kind by remember { mutableStateOf(Kind.APP) }
    var builtin by remember { mutableStateOf(Builtin.LATE) }
    var filter by remember { mutableStateOf(Filter(withinDays = 3)) }
    var asBubble by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.raised) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("สอนคำนี้", "Teach this word"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(tr("แตะคำที่จะสอน", "Tap the word to teach"), color = C.muted, fontSize = TS.caption)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pieces.forEach { p -> Chip(p, p == word, { word = p }) }
            }
            TextBox(word, { word = it }, tr("หรือพิมพ์คำเอง", "Or type the word"), Modifier.fillMaxWidth())
            Text(tr("\"$word\" ให้หมายถึง", "\"$word\" means"), color = C.muted, fontSize = TS.caption)
            Choice(kind == Kind.APP, tr("คำสั่งในแอป", "Something in the app"), null, { kind = Kind.APP }) { BuiltinPicker(builtin) { builtin = it } }
            Choice(kind == Kind.SHOW, tr("แสดงงานตามเงื่อนไข", "Show work by conditions"), null, { kind = Kind.SHOW }) { FilterEditor(state, filter) { filter = it } }
            Choice(kind == Kind.CLAUDE, tr("ส่งให้ Claude ทุกครั้ง", "Always send to Claude"), null, { kind = Kind.CLAUDE }) {}
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable { asBubble = !asBubble }, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (asBubble) C.accent else Color.Transparent).border(2.dp, if (asBubble) C.accent else C.control, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (asBubble) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(14.dp)) }
                Text(tr("ทำเป็นบอลลูนด้วย", "Make it a bubble too"), Modifier.padding(start = 10.dp), color = C.text, fontSize = TS.body)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(tr("ยกเลิก", "Cancel"), onDismiss, Modifier.weight(1f).heightIn(min = 48.dp))
                val ok = word.isNotBlank() && !(kind == Kind.SHOW && filter.isEmpty)
                Box(
                    Modifier.weight(2f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (ok) C.accent else C.card).clickable(enabled = ok) {
                        val action = when (kind) {
                            Kind.APP -> Action.App(builtin)
                            Kind.SHOW -> Action.Show(filter)
                            Kind.CLAUDE -> Action.Claude("")
                        }
                        vm.teach(said, word, action, asBubble)
                        onDismiss()
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(tr("จำไว้ แล้วทำเลย", "Remember and do it"), color = if (ok) C.onAccent else C.muted, fontSize = TS.body, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** The words of a Thai sentence (Thai has no spaces), by the phone's own word breaker; short bits are left out. */
private fun thaiWords(text: String): List<String> {
    val it = java.text.BreakIterator.getWordInstance(java.util.Locale("th"))
    it.setText(text)
    val out = mutableListOf<String>()
    var start = it.first()
    var end = it.next()
    while (end != java.text.BreakIterator.DONE) {
        val w = text.substring(start, end).trim()
        if (w.length >= 2 && w.any { c -> c.isLetter() }) out += w
        start = end
        end = it.next()
    }
    return out.distinct()
}
