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
) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(440.dp).clip(shape).background(C.bg).border(1.dp, C.cardBorder, shape)) {
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
    }
}

/** The picked task under the map: why the assistant points at it (or what the app knows), and what to do. */
@Composable
internal fun AiTaskCard(task: Task, reason: String?, state: UiState, onAsk: () -> Unit, onOpen: () -> Unit, onClose: () -> Unit) {
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
    Card(border = if (reason != null) C.accentLine else C.cardBorder) {
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
internal fun AiThinking() {
    Column(Modifier.fillMaxWidth()) {
        GeminiHead()
        Text(tr("กำลังคิด...", "Thinking..."), Modifier.padding(top = 6.dp, start = 2.dp), color = C.muted, fontSize = TS.body)
    }
}

/**
 * Gemini's answer and each change it proposes, with the owner's choice. A big change ([AiAssist.Action.Big]) is only
 * handed to Claude ([onClaude]) for review.
 */
@Composable
internal fun AiAnswerCard(item: Chat.AiAnswer, onAnswer: (Int, Boolean) -> Unit, onClaude: (AiAssist.Action.Big) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GeminiHead()
        Card {
            Text(
                item.failed ?: item.text.ifBlank { tr("(ไม่มีคำตอบ)", "(no answer)") },
                Modifier.padding(14.dp), color = if (item.failed != null) C.amber else C.text, fontSize = TS.body, lineHeight = 1.45.em,
            )
        }
        item.actions.forEachIndexed { i, a ->
            val big = a is AiAssist.Action.Big
            Card(border = if (big) C.control else C.accentLine) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        when (a) {
                            is AiAssist.Action.Stuck -> tr("เสนอจดเหตุผลที่ค้าง", "Note why it is stuck")
                            is AiAssist.Action.Move -> tr("เสนอเลื่อนวัน", "Move the date")
                            is AiAssist.Action.Subtasks -> tr("เสนอแบ่งเป็นงานย่อย", "Split into subtasks")
                            is AiAssist.Action.Big -> tr("เรื่องใหญ่ ส่งให้ Claude ตรวจก่อน", "Big change: Claude reviews it first")
                        },
                        color = if (big) C.amber else C.accentText, fontSize = TS.micro, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when (a) {
                            is AiAssist.Action.Stuck -> "${a.task.title}\n${tr("ค้าง", "Stuck")}: ${a.reason}"
                            is AiAssist.Action.Move -> tr("${a.task.title}\nไปวันที่ ${a.date.format(SHORT_DATE)}", "${a.task.title}\nto ${a.date.format(SHORT_DATE)}")
                            is AiAssist.Action.Subtasks -> a.task.title + "\n" + a.titles.joinToString("\n") { "  - $it" }
                            is AiAssist.Action.Big -> a.text
                        },
                        Modifier.padding(top = 4.dp), color = C.text, fontSize = TS.body, lineHeight = 1.45.em,
                    )
                    val done = item.outcome[i]
                    if (done != null) {
                        Text(
                            when {
                                !done -> tr("ไม่ทำ", "Declined")
                                big -> tr("ส่งให้ Claude แล้ว", "Sent to Claude")
                                else -> tr("ทำแล้ว", "Done")
                            },
                            Modifier.padding(top = 10.dp), color = if (done) C.tealChip else C.muted, fontSize = TS.caption,
                        )
                    } else {
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(tr("ไม่เอา", "No"), { onAnswer(i, false) }, Modifier.weight(1f))
                            PrimaryButton(
                                if (big) tr("ส่งให้ Claude ตรวจ", "Send to Claude") else if (a is AiAssist.Action.Subtasks) tr("เพิ่ม ${a.titles.size} งานย่อย", "Add ${a.titles.size}") else tr("ทำเลย", "Do it"),
                                { if (a is AiAssist.Action.Big) onClaude(a); onAnswer(i, true) },
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
