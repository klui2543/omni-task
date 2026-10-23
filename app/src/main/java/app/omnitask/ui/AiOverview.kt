package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.omnitask.model.AiAssist
import app.omnitask.model.StuckLog
import app.omnitask.model.Task
import app.omnitask.model.Ygdrasil
import app.omnitask.model.tr
import app.omnitask.time.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Which picture of the work the assistant shows: the Timeline or the Ygdrasil tree. */
enum class AiView { TIMELINE, YGDRASIL }

internal fun isClaudeTask(t: Task) = t.tags.any { it.equals(CLAUDE_TAG, ignoreCase = true) }

/** Long-sat or late work: what the "ค้างนาน" chip keeps. */
internal fun needsLook(t: Task, state: UiState): Boolean =
    t.isOpen && (StuckLog.staleDays(t, state.subtasksOf(t), state.today) != null || t.due?.let { it < state.today } == true)

/** The assistant's picks as they are now: the current task key for each pick, and the reason. */
internal fun currentPicks(brief: AiAssist.Brief?, tasks: List<Task>): Map<String, String> =
    brief?.picks.orEmpty().mapNotNull { p ->
        val now = tasks.firstOrNull { it.key == p.task.key && it.title == p.task.title }
            ?: tasks.firstOrNull { it.title == p.task.title && it.isOpen }
        now?.let { it.key to p.reason }
    }.toMap()

@Composable
private fun GeminiHead(trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(C.accent))
        Text("Gemini", Modifier.padding(start = 7.dp).weight(1f), color = C.muted, fontSize = TS.caption)
        if (trailing != null) Text(trailing, color = C.faint, fontSize = TS.micro)
    }
}

/** The brief at the top of the assistant page: what Gemini sees, or how to switch it on. */
@Composable
internal fun AiBriefCard(ai: AiUi, picks: Int, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Card {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(top = 6.dp)) {
                    GeminiHead(if (ai.briefing) tr("กำลังอ่านงาน...", "Reading your work...") else null)
                }
                if (ai.hasKey) Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(enabled = !ai.briefing, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.refresh, tr("อ่านใหม่", "Read again"), tint = C.muted, modifier = Modifier.size(18.dp)) }
            }
            Column(Modifier.padding(end = 10.dp)) {
                when {
                    !ai.hasKey -> {
                        Text(
                            tr("ใส่ Gemini API key แล้วผู้ช่วยจะสรุปภาพรวม และวงงานที่ควรดูก่อนให้ คุยถามเรื่องงานได้ด้วย", "Add a Gemini API key and the assistant sums up your work, rings what to look at first, and answers questions"),
                            Modifier.padding(top = 4.dp), color = C.text2, fontSize = TS.body, lineHeight = 1.45.em,
                        )
                        PrimaryButton(tr("ใส่ API key", "Add API key"), onSettings, Modifier.padding(top = 10.dp))
                    }
                    ai.brief == null && ai.briefError != null -> {
                        Text(ai.briefError, Modifier.padding(top = 4.dp), color = C.amber, fontSize = TS.body, lineHeight = 1.45.em)
                        GhostButton(tr("ลองใหม่", "Try again"), onRefresh, Modifier.padding(top = 10.dp))
                    }
                    ai.brief == null -> Text(
                        if (ai.briefing) tr("กำลังอ่านงานทั้งหมด", "Reading all your work") else tr("กดอ่านใหม่ เพื่อให้ผู้ช่วยสรุปภาพรวม", "Tap read again for a summary"),
                        Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.body,
                    )
                    else -> {
                        Text(ai.brief.summary, Modifier.padding(top = 4.dp), color = C.text, fontSize = TS.body, lineHeight = 1.45.em)
                        if (picks > 0) Text(
                            tr("วงสีม่วงคืองาน $picks งานที่ควรดูก่อน แตะเพื่อดูเหตุผล", "The $picks ringed in purple are worth a look first. Tap one for why."),
                            Modifier.padding(top = 6.dp), color = C.muted, fontSize = TS.caption,
                        )
                        if (ai.briefError != null) Text(ai.briefError, Modifier.padding(top = 6.dp), color = C.amber, fontSize = TS.caption)
                    }
                }
            }
        }
    }
}

/**
 * The brief folded to one line, so the map below gets the screen: a tap unfolds it. Without a key it says how to
 * switch the assistant on; while reading or after an error it says so.
 */
@Composable
internal fun AiBriefLine(ai: AiUi, picks: Int, open: Boolean, onToggle: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    val text = when {
        !ai.hasKey -> tr("ใส่ Gemini API key แล้วผู้ช่วยจะสรุปและวงงานที่ควรดูให้", "Add a Gemini API key for a summary and picks")
        ai.brief == null && ai.briefing -> tr("กำลังอ่านงานทั้งหมด...", "Reading all your work...")
        ai.brief == null && ai.briefError != null -> ai.briefError
        ai.brief == null -> tr("แตะอ่านใหม่ เพื่อให้ผู้ช่วยสรุปภาพรวม", "Tap read again for a summary")
        else -> ai.brief?.summary.orEmpty() + if (open && picks > 0) tr("\nวงสีม่วงคืองาน $picks งานที่ควรดูก่อน แตะเพื่อดูเหตุผล", "\nThe $picks ringed in purple are worth a look first.") else ""
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
            .clickable(enabled = ai.brief != null, onClick = onToggle).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = if (open) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(top = if (open) 12.dp else 0.dp).size(14.dp).clip(CircleShape).background(C.accent))
        Text(
            text, Modifier.weight(1f).padding(start = 9.dp, top = 8.dp, bottom = 8.dp),
            color = if (ai.brief == null && ai.briefError != null) C.amber else C.text2, fontSize = TS.body,
            maxLines = if (open) 20 else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = 1.4.em,
        )
        when {
            !ai.hasKey -> Text(
                tr("ใส่ key", "Add key"),
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onSettings).padding(horizontal = 12.dp, vertical = 12.dp),
                color = C.accentText, fontSize = TS.caption,
            )
            ai.brief != null && !open -> Text(tr("อ่านต่อ", "More"), Modifier.padding(start = 6.dp), color = C.accentText, fontSize = TS.caption)
        }
        if (ai.hasKey) Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(enabled = !ai.briefing, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.refresh, tr("อ่านใหม่", "Read again"), tint = C.muted, modifier = Modifier.size(18.dp)) }
    }
}

/** Timeline or Ygdrasil, with how to move around in it. */
@Composable
internal fun AiViewSwitch(view: AiView, onView: (AiView) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Segmented(listOf(AiView.TIMELINE to "Timeline", AiView.YGDRASIL to "Ygdrasil"), view, onView, Modifier.width(240.dp))
        Text(
            if (view == AiView.TIMELINE) tr("ถ่างสองนิ้วเพื่อซูม", "Pinch to zoom") else tr("ลากเพื่อเลื่อนดูกิ่ง", "Drag to move"),
            Modifier.weight(1f).padding(start = 10.dp), color = C.faint, fontSize = TS.micro, maxLines = 2,
        )
    }
}

/**
 * The overview's one row of controls: the view switch, the two filters used most (mine, sat too long) and a "?" that
 * opens the rest: everyone's or Claude's work, what each mark means, and how to move around.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AiViewBar(
    view: AiView, onView: (AiView) -> Unit,
    who: TimelineWho, staleOnly: Boolean, stuck: Int, onWho: (TimelineWho) -> Unit, onStale: () -> Unit,
) {
    var help by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Segmented(listOf(AiView.TIMELINE to "Timeline", AiView.YGDRASIL to "Ygdrasil"), view, onView, Modifier.width(190.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("ฉัน", "Me"), who == TimelineWho.ME && !staleOnly, { onWho(if (who == TimelineWho.ME) TimelineWho.ALL else TimelineWho.ME) }, dot = C.tealChip)
            Chip(tr("ค้าง $stuck", "Stuck $stuck"), staleOnly, onStale, dot = C.amber)
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).border(1.dp, C.cardBorder, CircleShape).clickable(onClickLabel = tr("ตัวกรองอื่นและความหมาย", "More filters and what the marks mean")) { help = true },
            contentAlignment = Alignment.Center,
        ) { Text("?", color = C.muted, fontSize = TS.body) }
    }
    if (help) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { help = false }, containerColor = C.card, contentColor = C.text,
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("ตัวกรองและความหมาย", "Filters and marks"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(tr("งานของใคร", "Whose work"), color = C.muted, fontSize = TS.caption)
            AiChips(who, staleOnly, stuck, onWho, onStale)
            Text(tr("สัญลักษณ์", "Marks"), color = C.muted, fontSize = TS.caption)
            AiLegend(view)
            Text(
                if (view == AiView.TIMELINE) tr("ถ่างสองนิ้วเพื่อซูม", "Pinch to zoom") else tr("ลากเพื่อเลื่อนดูกิ่ง", "Drag to move"),
                color = C.faint, fontSize = TS.caption,
            )
        }
    }
}

/** The chips both views share: everyone's work, mine, Claude's, or only work that sat too long. */
@Composable
internal fun AiChips(who: TimelineWho, staleOnly: Boolean, stuck: Int, onWho: (TimelineWho) -> Unit, onStale: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(tr("ทั้งหมด", "All"), who == TimelineWho.ALL && !staleOnly, { onWho(TimelineWho.ALL) })
        Chip(tr("ฉัน", "Me"), who == TimelineWho.ME && !staleOnly, { onWho(TimelineWho.ME) }, dot = C.tealChip)
        Chip("Claude", who == TimelineWho.CLAUDE && !staleOnly, { onWho(TimelineWho.CLAUDE) }, dot = C.accentText)
        Chip(tr("ค้างนาน $stuck", "Stuck $stuck"), staleOnly, onStale, dot = C.amber)
    }
}

/** What each mark means, for the view that is showing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiLegend(view: AiView) {
    @Composable
    fun item(text: String, mark: @Composable () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            mark()
            Text(text, color = C.muted, fontSize = TS.micro)
        }
    }
    val ring = @Composable { Box(Modifier.width(16.dp).height(10.dp).border(2.dp, C.accent, RoundedCornerShape(4.dp))) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(tr("ผู้ช่วยชี้", "Assistant's pick"), ring)
        if (view == AiView.TIMELINE) {
            item(tr("รออีกงาน", "Waits for another")) {
                Box(
                    Modifier.width(16.dp).height(10.dp).drawBehind {
                        drawRoundRect(C.accentText, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
                    },
                )
            }
            item(tr("ค้างนาน", "Sat too long")) {
                Row(Modifier.width(16.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(C.tealSoft)) {
                    Box(Modifier.width(4.dp).height(10.dp).background(C.amber))
                }
            }
            item(tr("วันนี้", "Today")) { Box(Modifier.padding(horizontal = 6.dp).width(2.dp).height(12.dp).background(C.accent)) }
        } else {
            item(tr("รออีกงานเสร็จก่อน", "Waits for another")) {
                Box(
                    Modifier.width(16.dp).height(2.dp).drawBehind {
                        drawLine(C.muted, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                    },
                )
            }
            item(tr("ฉัน", "Me")) { Box(Modifier.size(8.dp).clip(CircleShape).background(C.tealChip)) }
            item("Claude") { Box(Modifier.size(8.dp).clip(CircleShape).background(C.accentText)) }
            item(tr("ค้างมา 21 วัน", "Sat 21 days")) { Text(tr("21 วัน", "21d"), color = C.amber, fontSize = TS.micro, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** The Timeline or the tree, sized to sit on the assistant's page, sharing the chips and the picked task. */
@Composable
internal fun AiMap(
    view: AiView, state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit,
    who: TimelineWho, staleOnly: Boolean, picks: Set<String>, selKey: String?, onSelect: (Task?) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(440.dp),
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.clip(shape).background(C.bg).border(1.dp, C.cardBorder, shape)) {
        when (view) {
            AiView.TIMELINE -> Box(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                GanttView(state, state.tasks, vm, onOpen, embed = TimelineEmbed(who, staleOnly, picks, selKey, onSelect))
            }
            AiView.YGDRASIL -> YgdrasilScreen(
                state, vm, Ygdrasil.Filter(), {}, emptySet(), {}, onOpen, {}, {},
                embed = YgdrasilEmbed(
                    dim = { t ->
                        (staleOnly && !needsLook(t, state)) || when (who) {
                            TimelineWho.ALL -> false
                            TimelineWho.ME -> isClaudeTask(t)
                            TimelineWho.CLAUDE -> !isClaudeTask(t)
                        }
                    },
                    picks = picks, selKey = selKey, mine = { !isClaudeTask(it) },
                    stale = { StuckLog.staleDays(it, state.subtasksOf(it), state.today) },
                    onSelect = onSelect,
                ),
            )
        }
        overlay()
    }
}

/** The picked task under the map: why the assistant points at it (or what the app knows), and what to do. */
@Composable
internal fun AiTaskCard(
    task: Task, reason: String?, state: UiState, onAsk: () -> Unit, onOpen: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, floating: Boolean = false,
) {
    val claude = isClaudeTask(task)
    val today = state.today
    val byId = state.allTasks.filter { it.id != null }.associateBy { it.id }
    val blocker = task.dependsOn.mapNotNull { byId[it] }.firstOrNull { it.isOpen }
    val stale = StuckLog.staleDays(task, state.subtasksOf(task), today)
    val facts = listOfNotNull(
        stale?.let { tr("ค้าง $it วัน", "Stuck $it days") },
        task.due?.takeIf { it < today && task.isOpen }?.let { tr("เลยกำหนด ${ChronoUnit.DAYS.between(it, today)} วัน", "${ChronoUnit.DAYS.between(it, today)} days late") },
        blocker?.let { tr("รอ: ${it.title}", "Waiting on ${it.title}") },
    )
    // Floating over the map it is raised, with a shadow, so it reads as on top of the tasks under it.
    Card(
        if (floating) modifier.shadow(12.dp, RoundedCornerShape(18.dp)) else modifier,
        color = if (floating) C.raised else C.card,
        border = if (reason != null) C.accentLine else C.cardBorder,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(top = 6.dp)) { if (reason != null) GeminiHead() }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Ic.close, tr("ปิด", "Close"), tint = C.muted, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(end = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, Modifier.weight(1f), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (claude) "Claude" else tr("ฉัน", "Me"),
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(if (claude) C.accentSoft else C.tealSoft).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = if (claude) C.accentText else C.tealChip, fontSize = TS.micro,
                    )
                }
                if (facts.isNotEmpty()) Text(facts.joinToString("   "), Modifier.padding(top = 3.dp), color = C.amber, fontSize = TS.caption)
                Text(
                    reason ?: tr("ผู้ช่วยไม่ได้ชี้งานนี้ ถามเรื่องงานนี้ได้", "The assistant did not pick this one. You can ask about it."),
                    Modifier.padding(top = 6.dp), color = if (reason != null) C.text2 else C.muted, fontSize = TS.body, lineHeight = 1.45.em,
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(tr("เปิดงาน", "Open"), onOpen, Modifier.weight(1f))
                    PrimaryButton(tr("ถามเรื่องนี้", "Ask about it"), onAsk, Modifier.weight(1f))
                }
            }
        }
    }
}

/** "Gemini is thinking" while a typed question is answered. */
@Composable
internal fun AiThinking(searching: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        GeminiHead()
        Text(
            if (searching) tr("กำลังค้นบันทึกเก่า...", "Searching older talks...") else tr("กำลังคิด...", "Thinking..."),
            Modifier.padding(top = 6.dp, start = 2.dp), color = C.muted, fontSize = TS.body,
        )
    }
}

/**
 * Gemini's answer and each change it proposes, with the owner's choice. A big change ([AiAssist.Action.Big]) is only
 * handed to Claude ([onClaude]) for review.
 */
@Composable
internal fun AiAnswerCard(item: Chat.AiAnswer, onAnswer: (Int, Boolean) -> Unit, onClaude: (AiAssist.Action.Big) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GeminiHead(
            listOfNotNull(
                item.model?.let(::shortModel), item.seconds?.let { tr("$it วินาที", "$it s") },
                if (item.searched) tr("ค้นบันทึกเก่า", "searched older talks") else null,
            ).joinToString(", ").ifEmpty { null },
        )
        Card {
            Text(
                item.failed ?: item.text.ifBlank { tr("(ไม่มีคำตอบ)", "(no answer)") },
                Modifier.padding(14.dp), color = if (item.failed != null) C.amber else C.text, fontSize = TS.body, lineHeight = 1.45.em,
            )
        }
        item.actions.forEachIndexed { i, a ->
            val big = a is AiAssist.Action.Big
            val danger = a is AiAssist.Action.Delete
            Card(border = if (big) C.control else C.accentLine) {
                Column(Modifier.padding(14.dp)) {
                    Text(actionKind(a), color = if (big) C.amber else if (danger) C.red else C.accentText, fontSize = TS.micro, fontWeight = FontWeight.SemiBold)
                    Text(actionText(a), Modifier.padding(top = 4.dp), color = C.text, fontSize = TS.body, lineHeight = 1.45.em)
                    val done = item.outcome[i]
                    if (done != null) {
                        Text(
                            when {
                                !done -> tr("ไม่ทำ", "Declined")
                                big -> tr("ส่งให้ Claude แล้ว", "Sent to Claude")
                                danger -> tr("ลบแล้ว ย้อนกลับได้จากแถบด้านล่าง", "Deleted; undo from the bar below")
                                else -> tr("ทำแล้ว", "Done")
                            },
                            Modifier.padding(top = 10.dp), color = if (done) C.tealChip else C.muted, fontSize = TS.caption,
                        )
                    } else {
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(if (danger) tr("ไม่ลบ", "Keep it") else tr("ไม่เอา", "No"), { onAnswer(i, false) }, Modifier.weight(1f))
                            PrimaryButton(
                                actionButton(a),
                                { if (a is AiAssist.Action.Big) onClaude(a); onAnswer(i, true) },
                                Modifier.weight(1f),
                                color = if (danger) C.red else C.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The short label above a proposal: what kind of change it is. */
private fun actionKind(a: AiAssist.Action): String = when (a) {
    is AiAssist.Action.Add -> tr("เพิ่มงาน", "New task")
    is AiAssist.Action.Move -> tr("เลื่อนวัน", "Move the date")
    is AiAssist.Action.Rename -> tr("เปลี่ยนชื่อ", "Rename")
    is AiAssist.Action.SetPriority -> tr("ความสำคัญ", "Priority")
    is AiAssist.Action.Remind -> tr("ตั้งเวลาเตือน", "Reminder")
    is AiAssist.Action.Done -> tr("ติ๊กว่าเสร็จ", "Mark done")
    is AiAssist.Action.Delete -> tr("ลบงาน", "Delete")
    is AiAssist.Action.Project -> tr("ย้ายโปรเจกต์", "Move to a project")
    is AiAssist.Action.Stuck -> tr("จดเหตุผลที่ค้าง", "Note why it is stuck")
    is AiAssist.Action.Subtasks -> tr("เพิ่มงานย่อย", "Add subtasks")
    is AiAssist.Action.Remember -> tr("จำไว้ถาวร", "Remember for good")
    is AiAssist.Action.Big -> tr("เรื่องใหญ่ ส่งให้ Claude ตรวจก่อน", "Big change: Claude reviews it first")
}

private fun dateText(d: kotlinx.datetime.LocalDate?) = d?.format(SHORT_DATE) ?: tr("ไม่มีวันที่", "no date")

/** What the proposal changes, in words the owner can check before confirming. */
private fun actionText(a: AiAssist.Action): String = when (a) {
    is AiAssist.Action.Add -> buildList {
        add(a.title)
        a.date?.let { add((if (a.scheduled) tr("นัดทำ ", "On ") else tr("กำหนด ", "Due ")) + dateText(it)) }
        a.time?.let { add(tr("เตือน ", "Remind ") + "%02d:%02d".format(it.hour, it.minute)) }
        a.project?.let { add(tr("โปรเจกต์ ", "Project ") + it) }
        if (a.priority != app.omnitask.model.Priority.NONE) add(tr("ความสำคัญ ", "Priority ") + a.priority.label)
    }.joinToString("\n")
    is AiAssist.Action.Move -> {
        val (name, old) = when (a.kind) {
            AiAssist.DateKind.DUE -> tr("กำหนด", "Due") to a.task.due
            AiAssist.DateKind.SCHEDULED -> tr("นัดทำ", "Scheduled") to a.task.scheduled
            AiAssist.DateKind.START -> tr("เริ่ม", "Start") to a.task.start
        }
        "${a.task.title}\n$name: ${dateText(old)} ${tr("ไป", "to")} ${dateText(a.date)}"
    }
    is AiAssist.Action.Rename -> "${a.task.title}\n${tr("เป็น", "to")}: ${a.title}"
    is AiAssist.Action.SetPriority -> "${a.task.title}\n${a.task.priority.label} ${tr("เป็น", "to")} ${a.priority.label}"
    is AiAssist.Action.Remind -> a.task.title + "\n" + tr("เตือน ", "Remind at ") + "%02d:%02d".format(a.time.hour, a.time.minute)
    is AiAssist.Action.Done -> a.task.title
    is AiAssist.Action.Delete -> a.task.title
    is AiAssist.Action.Project -> "${a.task.title}\n${tr("ไป", "to")} #${a.tag}"
    is AiAssist.Action.Stuck -> "${a.task.title}\n${tr("ค้าง", "Stuck")}: ${a.reason}"
    is AiAssist.Action.Subtasks -> a.task.title + "\n" + a.titles.joinToString("\n") { "  - $it" }
    is AiAssist.Action.Remember -> a.text
    is AiAssist.Action.Big -> a.text
}

private fun actionButton(a: AiAssist.Action): String = when (a) {
    is AiAssist.Action.Add -> tr("เพิ่มงาน", "Add")
    is AiAssist.Action.Move -> tr("เลื่อน", "Move")
    is AiAssist.Action.Delete -> tr("ลบงาน", "Delete")
    is AiAssist.Action.Done -> tr("ติ๊กเสร็จ", "Mark done")
    is AiAssist.Action.Subtasks -> tr("เพิ่ม ${a.titles.size} งานย่อย", "Add ${a.titles.size}")
    is AiAssist.Action.Big -> tr("ส่งให้ Claude ตรวจ", "Send to Claude")
    is AiAssist.Action.Remember -> tr("จำไว้", "Remember")
    else -> tr("ทำเลย", "Do it")
}

/** A model id as people say it: "gemini-flash-lite-latest" is "Flash-Lite", "gemini-3.1-pro" is "3.1 Pro". */
internal fun shortModel(id: String): String = id.removePrefix("models/").removePrefix("gemini-").removeSuffix("-latest")
    .split('-').joinToString(" ") { part ->
        when (part) {
            "flash" -> "Flash"
            "lite" -> "Lite"
            "pro" -> "Pro"
            else -> part
        }
    }.replace("Flash Lite", "Flash-Lite")

/** The model in use, in the field where questions are typed; a tap opens the list. */
@Composable
internal fun AiModelChip(model: String, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(C.accentSoft).border(1.dp, C.accentLine, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shortModel(model), color = C.accentText, fontSize = TS.caption, maxLines = 1)
        Icon(Ic.next, tr("เลือกรุ่น", "Pick a model"), tint = C.accentText, modifier = Modifier.padding(start = 2.dp).size(14.dp).graphicsLayer { rotationZ = 90f })
    }
}

/** Every model Google offers this key, fast to smart; the one in use is marked. */
@Composable
internal fun AiModelSheet(ai: AiUi, onLoad: () -> Unit, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onLoad() }
    SheetFrame(onDismiss) {
        Text(tr("เลือกรุ่น", "Pick a model"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.Medium)
        Text(tr("รายการจาก Google ตามที่ key ของคุณใช้ได้ จำไว้จนกว่าจะเปลี่ยน", "From Google, as your key allows. Kept until you change it."), Modifier.padding(top = 2.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption)
        // The one in use stays on the list even when Google's list leaves it out (an alias such as "latest").
        val list = if (ai.models.none { it.id == ai.model }) listOf(AiAssist.Model(ai.model, shortModel(ai.model), tierOf(ai.model))) + ai.models else ai.models
        val tiers = listOf(
            AiAssist.Tier.FAST to tr("เร็ว", "Fast"), AiAssist.Tier.BALANCED to tr("สมดุล", "Balanced"),
            AiAssist.Tier.SMART to tr("ฉลาด", "Smart"), AiAssist.Tier.OTHER to tr("อื่น ๆ", "Other"),
        )
        tiers.forEach { (tier, label) ->
            val rows = list.filter { it.tier == tier }
            if (rows.isNotEmpty()) {
                Text(label, Modifier.padding(top = 12.dp, bottom = 2.dp), color = C.muted, fontSize = TS.micro)
                rows.forEach { m ->
                    val on = m.id == ai.model
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) C.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(m.id); onDismiss() }.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) C.accent else C.faint, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(C.accent)) }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(m.name, color = C.text, fontSize = TS.body)
                            Text(m.id, color = C.faint, fontSize = TS.micro)
                        }
                    }
                }
            }
        }
        when {
            ai.modelsLoading -> Text(tr("กำลังอ่านรายชื่อจาก Google...", "Reading the list from Google..."), Modifier.padding(top = 12.dp), color = C.muted, fontSize = TS.caption)
            ai.modelsError != null -> {
                Text(ai.modelsError, Modifier.padding(top = 12.dp), color = C.amber, fontSize = TS.caption)
                GhostButton(tr("ลองใหม่", "Try again"), onLoad, Modifier.padding(top = 8.dp))
            }
        }
        Text(
            tr("ทุกรุ่นที่ key ใช้ได้อยู่ในรายการนี้ รวมรุ่นที่ใช้ฟรีได้ ถ้ารุ่นไหนเกินโควตาฟรี ผู้ช่วยจะบอกให้เปลี่ยนรุ่น", "Every model your key can use is here, free ones included. If one runs out of free quota, the assistant says so."),
            Modifier.padding(top = 14.dp), color = C.muted, fontSize = TS.micro,
        )
    }
}

private fun tierOf(id: String) = when {
    "flash-lite" in id -> AiAssist.Tier.FAST
    "flash" in id -> AiAssist.Tier.BALANCED
    "pro" in id -> AiAssist.Tier.SMART
    else -> AiAssist.Tier.OTHER
}
