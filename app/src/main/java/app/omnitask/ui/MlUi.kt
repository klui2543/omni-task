package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.AssistantMl
import app.omnitask.model.AssistantMl.Label
import app.omnitask.model.Countdown
import app.omnitask.model.Planner
import app.omnitask.model.QuickAdd
import app.omnitask.model.SecretaryGuide
import app.omnitask.model.tr
import app.omnitask.time.*

/** The colour of each highlight, from the app's palette. */
private fun Label.color(): Color = when (this) {
    Label.COMMAND -> C.accent
    Label.TITLE -> C.teal
    Label.DATE -> C.blue
    Label.TIME -> C.amber
    Label.PRIORITY -> C.red
    Label.PROJECT -> C.lime
}

/**
 * An answer of the learning secretary (owner's pick, mock 2026-10-26). Sure and done: a line saying what it understood
 * and from which taught sentence, with "ไม่ใช่" to teach it right. Not sure: the closest guesses to pick, then two large
 * buttons at the bottom, ask Haiku and teach. Picked or taught: whether the sentence was kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MlAnswer(
    t: TaskViewModel.MlTurn, onPick: (AssistantMl.Guess) -> Unit, onTeach: () -> Unit, onSlot: (Planner.Slot) -> Unit,
    onAskAi: (() -> Unit)?,
) {
    val acted = t.acted
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(C.card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (acted != null) {
            when (acted.intent) {
                AssistantMl.Intent.AddTask -> Text(tr("เปิดหน้าเพิ่มงานให้ตรวจแล้ว กดเพิ่มเมื่อถูกต้อง", "Quick add is open to check: add it when it is right"), color = C.text, fontSize = TS.body)
                AssistantMl.Intent.FindTime -> {
                    Text(tr("เวลาว่างสำหรับ ", "Free time for ") + t.slotTitle.ifBlank { t.question }, color = C.text, fontSize = TS.body)
                    if (t.slots.isEmpty()) Text(tr("ยังไม่เจอช่องว่างที่พอ", "No free time found yet"), color = C.muted, fontSize = TS.caption)
                    t.slots.forEach { s ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.accentDeep).border(1.dp, C.accentLine, RoundedCornerShape(12.dp))
                                .clickable { onSlot(s) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("${s.dayLabel} ${"%d:%02d".format(s.start.hour, s.start.minute)} ${tr("ถึง", "to")} ${"%d:%02d".format(s.end.hour, s.end.minute)}", color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                            Text(s.why, color = C.muted, fontSize = TS.caption)
                        }
                    }
                }
                is AssistantMl.Intent.Do -> {}
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val near = acted.near.text.trim()
                Text(
                    tr("เข้าใจว่า: ", "Read as: ") + acted.intent.describe() +
                        if (near.isNotEmpty() && t.note == null) tr(" (ใกล้กับ \"$near\")", " (like \"$near\")") else "",
                    Modifier.weight(1f), color = C.muted, fontSize = TS.caption,
                )
                // Gone once the sentence is taught again.
                if (!t.done) Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onTeach).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("ไม่ใช่", "Not this"), color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.SemiBold) }
            }
            t.note?.let { Text(it, color = if (it.startsWith(tr("จำแล้ว", "Kept"))) C.lime else C.amber, fontSize = TS.caption) }
            return@Column
        }
        if (t.done) { Text(tr("ทำแล้ว", "Done"), color = C.muted, fontSize = TS.caption); return@Column }
        val shown = if (t.reading.level == AssistantMl.Level.MEDIUM) t.reading.guesses else t.reading.guesses.filter { it.score >= 0.15 }.take(2)
        Text(
            when {
                t.reading.level == AssistantMl.Level.MEDIUM -> tr("ยังไม่แน่ใจ หมายถึงอันไหนครับ", "Not sure: which one do you mean?")
                shown.isNotEmpty() -> tr("ยังไม่เข้าใจประโยคนี้ ใกล้ที่สุดคือ", "I don't understand this yet. Closest:")
                else -> tr("ยังไม่เข้าใจประโยคนี้", "I don't understand this yet")
            },
            color = C.text, fontSize = TS.body,
        )
        shown.forEach { g ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (g == shown.first()) C.accentDeep else C.raised)
                    .border(1.dp, if (g == shown.first()) C.accentLine else C.cardBorder, RoundedCornerShape(12.dp))
                    .clickable { onPick(g) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(g.intent.describe(), color = C.text, fontSize = TS.body)
                val near = g.near.text.trim()
                if (near.isNotEmpty() && near != g.intent.describe()) Text(tr("ใกล้กับ \"$near\"", "Like \"$near\""), color = C.muted, fontSize = TS.caption)
            }
        }
        // The two large buttons, always at the bottom (owner, 2026-10-26).
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onAskAi != null) BigButton(tr("ถามด้วย Haiku", "Ask Haiku"), false, Modifier.weight(1f), onAskAi)
            BigButton(tr("สอน", "Teach"), true, Modifier.weight(1f), onTeach)
        }
    }
}

@Composable
private fun BigButton(label: String, main: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (main) C.accent else C.accentDeep)
            .border(1.dp, if (main) C.accent else C.accentLine, RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (main) C.onAccent else C.text, fontSize = TS.body, fontWeight = if (main) FontWeight.SemiBold else FontWeight.Normal) }
}

/** A piece of the sentence on the teach page: a word (by the phone's Thai word breaker) or the space between words. */
private data class Piece(val text: String, val range: IntRange, val space: Boolean)

private fun pieces(text: String): List<Piece> {
    val it = java.text.BreakIterator.getWordInstance(java.util.Locale("th"))
    it.setText(text)
    val out = mutableListOf<Piece>()
    var start = it.first()
    var end = it.next()
    while (end != java.text.BreakIterator.DONE) {
        val s = text.substring(start, end)
        out += Piece(s, start until end, s.isBlank())
        start = end
        end = it.next()
    }
    return out
}

/** What the "หมายถึง" chips offer once a command is highlighted. */
private enum class MlKind { ADD, SHOW, FIND, APP, CLAUDE }

/**
 * Teaching by highlight (owner's pick, mock 2026-10-26): drag across words, or tap one and then another, and give them a
 * label; the day and time are guessed first (dashed). Once a command is highlighted, say what it means. "จำไว้ แล้วทำเลย"
 * keeps the sentence in Assistant ML.md and does it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MlTeachSheet(state: UiState, vm: TaskViewModel, said: String, onDismiss: () -> Unit) {
    val parts = remember(said) { pieces(said) }
    val guessed = remember(said) {
        val g = AssistantMl.guessLabels(said)
        parts.indices.associateWith { i -> if (parts[i].space) null else g.firstOrNull { (r, _) -> r.first <= parts[i].range.last && parts[i].range.first <= r.last }?.second }
    }
    // A piece's label: set by the owner (a null value: "ไม่มีป้าย"), else the guess.
    val set = remember(said) { mutableStateMapOf<Int, Label?>() }
    fun labelOf(i: Int): Label? = if (set.containsKey(i)) set[i] else guessed[i]
    var sel by remember(said) { mutableStateOf<IntRange?>(null) }
    var level by remember(said) { mutableStateOf("สูง") }
    var kind by remember(said) { mutableStateOf<MlKind?>(null) }
    var builtin by remember(said) { mutableStateOf(SecretaryGuide.Builtin.TODAY) }
    var filter by remember(said) { mutableStateOf(SecretaryGuide.Filter()) }
    val bounds = remember(said) { mutableStateMapOf<Int, Rect>() }
    val corner = with(LocalDensity.current) { 8.dp.toPx() }
    fun at(x: Float, y: Float): Int? = bounds.entries.firstOrNull { (i, r) -> !parts[i].space && r.contains(androidx.compose.ui.geometry.Offset(x, y)) }?.key

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.raised) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr("สอนเลขา", "Teach the secretary"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
                Text(tr("  ลากผ่านคำ แล้วเลือกป้าย", "  drag across words, then pick a label"), color = C.muted, fontSize = TS.caption)
            }
            // The sentence: drag across words, or tap one then another to take the words between.
            FlowRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken).padding(horizontal = 10.dp, vertical = 12.dp)
                    .pointerInput(said) {
                        detectTapGestures { o ->
                            val i = at(o.x, o.y) ?: return@detectTapGestures
                            val s = sel
                            sel = if (s != null && s.first == s.last && s.first != i) minOf(s.first, i)..maxOf(s.first, i) else i..i
                        }
                    }
                    .pointerInput(said) {
                        var start = -1
                        detectDragGestures(
                            onDragStart = { o -> start = at(o.x, o.y) ?: -1; if (start >= 0) sel = start..start },
                            onDrag = { change, _ ->
                                change.consume()
                                val i = at(change.position.x, change.position.y)
                                if (start >= 0 && i != null) sel = minOf(start, i)..maxOf(start, i)
                            },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                parts.forEachIndexed { i, p ->
                    if (p.space) { Box(Modifier.width(4.dp)); return@forEachIndexed }
                    val l = labelOf(i)
                    val guess = !set.containsKey(i) && guessed[i] != null
                    val picked = sel?.contains(i) == true
                    val prev = (i - 1 downTo 0).firstOrNull { !parts[it].space }?.let(::labelOf)
                    Column(
                        Modifier.onGloballyPositioned { bounds[i] = it.boundsInParent() }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) C.control else l?.color()?.copy(alpha = 0.22f) ?: Color.Transparent)
                            .then(
                                when {
                                    picked -> Modifier.border(1.dp, C.accent, RoundedCornerShape(8.dp))
                                    l != null && guess -> Modifier.dashed(l.color(), radius = corner)
                                    l != null -> Modifier.border(1.dp, l.color().copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    else -> Modifier
                                },
                            )
                            .padding(horizontal = 5.dp, vertical = 4.dp),
                    ) {
                        Text(p.text, color = C.text, fontSize = 17.sp)
                        // The label's name under the first word of each highlight.
                        Text(if (l != null && l != prev) l.label else " ", color = l?.color() ?: Color.Transparent, fontSize = 10.sp)
                    }
                }
            }
            val s = sel
            if (s != null) {
                Text(tr("ป้ายของคำที่เลือก", "Label for the words picked"), color = C.muted, fontSize = TS.caption)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label.entries.forEach { l ->
                        Chip(l.label, false, { s.forEach { i -> if (!parts[i].space) set[i] = l }; sel = null }, dot = l.color())
                    }
                    Chip(tr("ไม่มีป้าย", "No label"), false, { s.forEach { i -> if (!parts[i].space) set[i] = null }; sel = null })
                }
            } else {
                Text(tr("ลองลากนิ้วผ่านคำที่เป็นคำสั่ง แล้วเลือก คำสั่ง", "Try dragging across the command words, then pick Command"), color = C.faint, fontSize = TS.caption)
            }
            val labels = parts.indices.map { labelOf(it) }
            if (Label.PRIORITY in labels) {
                Text(tr("ความเร่งด่วนระดับ", "Urgency level"), color = C.muted, fontSize = TS.caption)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AssistantMl.LEVELS.forEach { v -> Chip(v, v == level, { level = v }) } }
            }
            val hasCmd = Label.COMMAND in labels
            if (hasCmd) {
                Text(tr("คำสั่งนี้หมายถึง", "This command means"), color = C.muted, fontSize = TS.caption)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        MlKind.ADD to tr("เพิ่มงาน", "Add a task"), MlKind.SHOW to tr("ดูงาน", "Show work"), MlKind.FIND to tr("หาเวลาว่าง", "Find time"),
                        MlKind.APP to tr("คำสั่งในแอป", "App command"), MlKind.CLAUDE to tr("ส่งให้ Claude", "To Claude"),
                    ).forEach { (k, name) -> Chip(name, kind == k, { kind = k }) }
                }
                when (kind) {
                    MlKind.SHOW -> FilterEditor(state, filter) { filter = it }
                    MlKind.APP -> BuiltinPicker(builtin) { builtin = it }
                    else -> {}
                }
            }
            val intent: AssistantMl.Intent? = if (!hasCmd) null else when (kind) {
                MlKind.ADD -> AssistantMl.Intent.AddTask
                MlKind.FIND -> AssistantMl.Intent.FindTime
                MlKind.SHOW -> if (filter.isEmpty) null else AssistantMl.Intent.Do(SecretaryGuide.Action.Show(filter))
                MlKind.APP -> AssistantMl.Intent.Do(SecretaryGuide.Action.App(builtin))
                MlKind.CLAUDE -> AssistantMl.Intent.Do(SecretaryGuide.Action.Claude(""))
                null -> null
            }
            // What the app will read next time.
            val spans = spansOf(parts, ::labelOf, level)
            val rows = buildList {
                if (hasCmd) add(tr("คำสั่ง", "Command") to (intent?.describe() ?: tr("ยังไม่ได้เลือก", "Not picked yet")))
                Label.entries.filter { it != Label.COMMAND }.forEach { l ->
                    val words = spans.filter { it.label == l }.joinToString(" ") { it.text.trim() }
                    if (words.isNotEmpty()) add(l.label to valueOf(l, words, level, state))
                }
            }
            if (rows.isNotEmpty()) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(tr("ครั้งหน้าแอปจะเข้าใจว่า", "Next time the app reads"), color = C.muted, fontSize = TS.caption)
                rows.forEach { (k, v) ->
                    Row { Text(k, Modifier.width(100.dp), color = C.muted, fontSize = TS.body); Text(v, color = C.text, fontSize = TS.body) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(tr("ล้าง", "Clear"), { set.clear(); parts.indices.forEach { if (guessed[it] != null) set[it] = null }; sel = null; kind = null }, Modifier.weight(1f).heightIn(min = 48.dp))
                val ok = intent != null
                Box(
                    Modifier.weight(2f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (ok) C.accent else C.card)
                        .clickable(enabled = ok) { vm.mlTeach(said, spans, intent!!); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text(tr("จำไว้ แล้วทำเลย", "Remember and do it"), color = if (ok) C.onAccent else C.muted, fontSize = TS.body, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/**
 * The sentence as highlighted parts: words next to each other with the same label are one part, and a space between
 * two of them joins it; urgency carries its [level].
 */
private fun spansOf(parts: List<Piece>, labelOf: (Int) -> Label?, level: String): List<AssistantMl.Span> {
    fun effective(i: Int): Label? {
        if (!parts[i].space) return labelOf(i)
        val before = (i - 1 downTo 0).firstOrNull { !parts[it].space }?.let(labelOf)
        val after = (i + 1 until parts.size).firstOrNull { !parts[it].space }?.let(labelOf)
        return if (before != null && before == after) before else null
    }
    val out = mutableListOf<AssistantMl.Span>()
    var text = StringBuilder()
    var current: Label? = null
    fun flush() {
        if (text.isEmpty()) return
        out += AssistantMl.Span(text.toString(), current, if (current == Label.PRIORITY) level else null)
        text = StringBuilder()
    }
    parts.indices.forEach { i ->
        val l = effective(i)
        if (l != current) { flush(); current = l }
        text.append(parts[i].text)
    }
    flush()
    return out
}

/**
 * A highlight as the app will read it: the day and time as quick add gets them (spoken times like "9 โมง" made into
 * 9:00 first), urgency by its level; the project's words stay in the task's name (its tag comes in stage 2).
 */
private fun valueOf(l: Label, words: String, level: String, state: UiState): String {
    val now = java.time.LocalTime.now().let { kotlinx.datetime.LocalTime(it.hour, it.minute) }
    val quick = QuickAdd.parse(AssistantMl.forQuickAdd(words, state.timeWords, state.today, now), state.today)
    return when (l) {
        Label.DATE -> quick.due?.let { Countdown.day(it) } ?: words
        Label.TIME -> quick.time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: words
        Label.PRIORITY -> level
        Label.PROJECT -> words + tr(" (อยู่ในชื่องาน ยังไม่ใส่แท็ก)", " (in the task's name, no tag yet)")
        else -> words
    }
}
