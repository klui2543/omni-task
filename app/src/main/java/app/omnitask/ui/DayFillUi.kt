package app.omnitask.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.omnitask.model.DayFill
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.datetime.LocalDateTime
import app.omnitask.time.*

/**
 * "เลขาจัดตารางวันนี้" (step 2, owner's picks of 2026-10-29, mock v12): the secretary fills today's real free time on
 * the today page. A Settings switch, off at first; this is its one reader.
 */
object DayFillMode {
    private const val PREFS = "omni-dayfill"
    private const val ON = "on"
    var on by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ON, false)
    }

    fun setOn(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ON, value).apply()
    }
}

@Composable
fun DayFillSetting(context: Context) {
    LaunchedEffect(Unit) { DayFillMode.load(context) }
    SettingsGroup(tr("เลขาจัดตารางวันนี้", "The secretary plans today")) {
        SettingSwitch(
            tr("จัดเวลาว่างวันนี้ให้ในหน้าวันนี้", "Fill today's free time on the today page"),
            tr("เปิด: หน้าวันนี้แสดงตารางที่เลขาจัดให้แทนรายการเดิม  ปิด: หน้าวันนี้แบบเดิม", "On: the today page shows the secretary's plan in place of the list. Off: as before"),
            DayFillMode.on,
        ) { DayFillMode.setOn(context, it) }
    }
}

/** The colours of a card's kind, as in the mock, with a dark version. */
private class KindLook(val chipBg: Color, val chipFg: Color, val edge: Color, val dot: Color)

private fun look(kind: DayFill.Kind): KindLook {
    val dark = Palettes.current.dark
    fun of(light: List<Long>, base: Long): KindLook =
        if (!dark) KindLook(Color(light[0]), Color(light[1]), Color(light[2]), Color(light[3]))
        else Color(base).let { KindLook(it.copy(alpha = 0.22f), it, it.copy(alpha = 0.45f), it) }
    return when (kind) {
        DayFill.Kind.DUE, DayFill.Kind.AGAIN -> of(listOf(0xFFFBEFD0, 0xFF7A5200, 0xFFE5C77A, 0xFFC8941E), 0xFFE0B04A)
        DayFill.Kind.URGENT -> of(listOf(0xFFF8E0DA, 0xFF8A2D0E, 0xFFE0A27A, 0xFFC0502A), 0xFFE58A66)
        DayFill.Kind.MINE -> of(listOf(0xFFF8E6DA, 0xFF8A3D0E, 0xFFDAD7CE, 0xFFD2793F), 0xFFE39A68)
        DayFill.Kind.PROJECT -> of(listOf(0xFFE6F1E9, 0xFF1D5B33, 0xFFBFDCC8, 0xFF2F8A50), 0xFF6CC48C)
        DayFill.Kind.REVIEW, DayFill.Kind.ASK -> of(listOf(0xFFECE6F6, 0xFF4B2D80, 0xFFCDBFE6, 0xFF7A5AB8), 0xFFB49BE6)
        DayFill.Kind.LIFE -> of(listOf(0xFFEFEDE7, 0xFF3F3E3A, 0xFFDAD7CE, 0xFF9A968C), 0xFFB5B2AA)
        DayFill.Kind.WORK, DayFill.Kind.NET -> of(listOf(0xFFE3EAF8, 0xFF1A3F91, 0xFFDAD7CE, 0xFF2457C5), 0xFF8FB0EE)
    }
}

private val tagGreen get() = if (Palettes.current.dark) Color(0xFF6CC48C) else Color(0xFF1D5B33)
private val warmText get() = if (Palettes.current.dark) Color(0xFFF0A070) else Color(0xFF8A3D0E)
private val warmSoft get() = if (Palettes.current.dark) Color(0x33F0A070) else Color(0xFFF8E6DA)
private val newsText get() = if (Palettes.current.dark) Color(0xFF9DB8F2) else Color(0xFF1A3F91)
private val newsSoft get() = if (Palettes.current.dark) Color(0x339DB8F2) else Color(0xFFE3EAF8)
private val violetText get() = if (Palettes.current.dark) Color(0xFFB49BE6) else Color(0xFF4B2D80)
private val violetEdge get() = if (Palettes.current.dark) Color(0x66B49BE6) else Color(0xFFCDBFE6)
private val okText get() = if (Palettes.current.dark) Color(0xFF6CC48C) else Color(0xFF1D6B3A)

/** "1 ชม. 30 นาที", "45 นาที", as in the mock. */
private fun dur(m: Int): String = if (m >= 60) {
    tr("${m / 60} ชม.", "${m / 60} h") + if (m % 60 != 0) tr(" ${m % 60} นาที", " ${m % 60} min") else ""
} else tr("$m นาที", "$m min")

/**
 * The plan on the today page: the free time in numbers, the day's notices, the errand and question buttons, the day
 * as rows (blocks it works around, cards, free time) and what moved to another day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayFillCard(
    state: UiState, vm: TaskViewModel, plan: DayFill.Plan, now: LocalDateTime, input: () -> DayFill.Input,
    asks: Int, onAsks: () -> Unit, onOpen: (Task) -> Unit, onReview: (Task) -> Unit,
) {
    var open by remember { mutableStateOf<String?>(null) }
    var chips by remember { mutableStateOf(false) }
    var sprintDetail by remember { mutableStateOf(false) }
    // Taken slots the plan moved: their reminder follows (written once, see TaskViewModel.fillApplyMoves).
    LaunchedEffect(plan.moves) { vm.fillApplyMoves(plan.moves) }
    // A new event or a new urgent task changed the plan without a tap: say what moved.
    var seen by remember { mutableStateOf<DayFill.Plan?>(null) }
    LaunchedEffect(plan) {
        val before = seen
        seen = plan
        if (before == null || before.day != plan.day) return@LaunchedEffect
        val events = before.rows.mapNotNull { (it as? DayFill.Row.Locked)?.event?.id }.toSet()
        val newEvent = plan.rows.mapNotNull { (it as? DayFill.Row.Locked)?.event }.firstOrNull { it.id !in events && it.id > 0 }
        val urgent = before.placed.map { it.item.id }.toSet() + before.later.map { it.item.id }
        val newUrgent = (plan.placed.map { it.item } + plan.later.map { it.item }).firstOrNull { it.kind == DayFill.Kind.URGENT && it.id !in urgent }
        val what = when {
            newUrgent != null -> tr("มีงานด่วนเพิ่ม: ", "New urgent work: ") + newUrgent.title
            newEvent != null -> tr("มีนัดใหม่ในปฏิทิน: ", "New event: ") + newEvent.title
            else -> null
        }
        if (what != null) vm.fillNews(DayFill.news(what, before, plan))
    }

    Card {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                tr("ตอนนี้ ", "Now ") + DayFill.hm(now.hour * 60 + now.minute) + ", " +
                    tr("ว่างจริง ${dur(plan.free)} จัดไว้ ${dur(plan.free - plan.buffer)} เหลือกันชน ${dur(plan.buffer)}", "free ${dur(plan.free)}, planned ${dur(plan.free - plan.buffer)}, buffer ${dur(plan.buffer)}"),
                color = C.text2, fontSize = TS.caption,
            )
            plan.banner?.let { Notice(it, warmText, warmSoft, bold = true) }
            state.fillNews?.let { Notice(it, newsText, newsSoft) }
            plan.sprint?.let { sp ->
                val rule = C.divider
                Column(
                    Modifier.fillMaxWidth().drawBehind {
                        drawLine(rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx())
                        drawLine(rule, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
                    }.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = tagGreen, fontWeight = FontWeight.SemiBold))
                                append(tr("สปรินต์สัปดาห์นี้ ", "This week's sprint "))
                                pop()
                                append(sp.names)
                            },
                            Modifier.weight(1f), color = C.text, fontSize = TS.caption,
                        )
                        SmallAction(tr("รับ", "Take"), true) { vm.fillSprint(sp.projects, take = true, swap = false) }
                        SmallAction(tr("เปลี่ยน", "Swap"), false) { vm.fillSprint(sp.projects, take = false, swap = true) }
                    }
                    Text(
                        sp.note + if (sprintDetail) tr(" (แตะเพื่อย่อ)", " (tap to fold)") else tr(" (แตะดู)", " (tap to see)"),
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable { sprintDetail = !sprintDetail },
                        color = if (sp.late) warmText else C.muted, fontSize = TS.caption,
                    )
                    if (sprintDetail) sp.lines.forEach { Text(it, color = C.text2, fontSize = TS.caption) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val errand = state.dayFill.takeIf { it.day == plan.day.toString() }?.errand
                if (errand == null) PillButton(tr("มีธุระ", "Errand")) { chips = !chips }
                else PillButton(tr("ธุระเสร็จแล้ว จัดคืนให้", "Errand over, plan again")) { vm.fillErrandEnd(); chips = false }
                if (asks > 0) PillButton(tr("เลขาถาม $asks ข้อ", "$asks questions"), violetText, violetEdge, onAsks)
            }
            if (chips && state.dayFill.errand == null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(tr("ไม่ว่างนานแค่ไหน", "Busy for how long"), color = C.text2, fontSize = TS.caption)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60 to tr("1 ชม.", "1 h"), 120 to tr("2 ชม.", "2 h"), 180 to tr("3 ชม.", "3 h"), null to tr("ทั้งเย็นนี้", "All evening")).forEach { (min, label) ->
                            PillButton(label) { vm.fillErrand(plan.start, min, label); chips = false }
                        }
                    }
                }
            }
            if (plan.end <= plan.start) Text(tr("วันนี้ไม่เหลือเวลาว่างให้จัดแล้ว", "No free time left to plan today"), color = C.muted, fontSize = TS.caption)

            val expanded = open ?: plan.next
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val time = when (row) {
                            is DayFill.Row.Card -> tr("ราว ", "about ") + DayFill.hm(row.at)
                            is DayFill.Row.Locked -> row.at?.let(DayFill::hm) ?: ""
                            is DayFill.Row.Gap -> DayFill.hm(row.at)
                            is DayFill.Row.Done -> row.at?.let(DayFill::hm) ?: ""
                        }
                        Text(time, Modifier.width(50.dp).padding(top = 10.dp), color = C.muted, fontSize = TS.micro, textAlign = TextAlign.End)
                        when (row) {
                            is DayFill.Row.Locked -> Column(
                                Modifier.weight(1f).alpha(if (row.past) 0.6f else 1f).clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(row.title, color = C.text2, fontSize = TS.body, fontWeight = FontWeight.Medium)
                                Text(row.why, color = C.muted, fontSize = TS.caption)
                            }
                            is DayFill.Row.Gap -> Text(
                                tr("ว่าง ${dur(row.len)} (ช่องกันชน ไว้รับเรื่องไม่คาดคิด ไม่มีอะไรก็พักได้)", "Free ${dur(row.len)} (a buffer for the unexpected, or a rest)"),
                                Modifier.weight(1f).dashed(C.faint).padding(horizontal = 12.dp, vertical = 6.dp),
                                color = C.muted, fontSize = TS.caption,
                            )
                            is DayFill.Row.Done -> Row(
                                Modifier.weight(1f).alpha(0.55f).clip(RoundedCornerShape(10.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(10.dp))
                                    .clickable { onOpen(row.task) }.padding(start = 4.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TaskCheck(row.task, { vm.toggleDone(row.task) }, size = 18.dp, touch = 36.dp)
                                Text(row.task.title, Modifier.weight(1f), color = C.muted, fontSize = TS.body, textDecoration = TextDecoration.LineThrough, maxLines = 2)
                            }
                            is DayFill.Row.Card -> {
                                val p = row.placed
                                if (p.item.id == expanded) {
                                    val timed = p.item.task?.let { state.dayFill.accepted[DayFill.ident(it)]?.timed }
                                    BigCard(p, row.picked, timed, plan, vm, input, onReview) { open = it }
                                } else {
                                    SmallCard(p, row.picked, vm) { open = p.item.id }
                                }
                            }
                        }
                    }
                }
            }
            val later = plan.later.filter { it.item.kind != DayFill.Kind.LIFE }
            if (later.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("ย้ายไปวันอื่น ไม่นับว่าข้าม", "Moved to another day, not counted as skipped"), color = C.text2, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
                    later.forEach { l ->
                        Text(
                            l.item.title + ": " + l.why,
                            // An answer given today can be taken back by tapping it, as a card can.
                            if (l.picked != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.fillUnpick(l.item) } else Modifier,
                            color = if (l.risk) warmText else C.text2, fontSize = TS.caption,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SmallCard(p: DayFill.Placed, picked: DayFill.Pick?, vm: TaskViewModel, onExpand: () -> Unit) {
    val k = look(p.item.kind)
    Row(
        Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(C.card).border(1.dp, k.edge, RoundedCornerShape(10.dp))
            .clickable(onClick = onExpand).padding(start = if (p.item.task != null && p.item.kind != DayFill.Kind.LIFE) 2.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val task = p.item.task
        if (task != null && p.item.kind != DayFill.Kind.LIFE) TaskCheck(task, { vm.toggleDone(task) }, size = 18.dp, touch = 36.dp)
        else Box(Modifier.size(6.dp).clip(CircleShape).background(k.dot))
        Text(p.item.title, Modifier.weight(1f).padding(vertical = 8.dp), color = C.text, fontSize = TS.body, maxLines = 2)
        p.item.tag?.let { Text(it, color = tagGreen, fontSize = TS.micro) }
        Text(if (picked == DayFill.Pick.TAKE) tr("รับแล้ว", "Taken") else dur(p.len), color = C.muted, fontSize = TS.caption)
    }
}

@Composable
private fun RowScope.BigCard(
    p: DayFill.Placed, picked: DayFill.Pick?, timed: Boolean?, plan: DayFill.Plan, vm: TaskViewModel, input: () -> DayFill.Input,
    onReview: (Task) -> Unit, setOpen: (String?) -> Unit,
) {
    val item = p.item
    val k = look(item.kind)
    val task = item.task
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C.card).border(1.dp, k.edge, RoundedCornerShape(12.dp))
            .then(if (picked != null) Modifier.clickable { vm.fillUnpick(item); setOpen(null) } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                item.kind.label, Modifier.clip(RoundedCornerShape(999.dp)).background(k.chipBg).padding(horizontal = 8.dp, vertical = 2.dp),
                color = k.chipFg, fontSize = TS.micro, fontWeight = FontWeight.SemiBold,
            )
            Text(dur(p.len), color = C.muted, fontSize = TS.caption)
            Box(Modifier.weight(1f))
            item.tag?.let { Text(it, color = tagGreen, fontSize = TS.micro) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (task != null && item.kind != DayFill.Kind.LIFE) TaskCheck(task, { vm.toggleDone(task) }, size = 18.dp, touch = 32.dp)
            Text(item.title, Modifier.weight(1f), color = C.text, fontSize = TS.title, fontWeight = FontWeight.Medium)
        }
        Text(
            item.why + if (p.short) tr(" (ย่อเหลือ ${dur(p.len)} เพราะวันนี้เวลาน้อยลง)", " (cut to ${dur(p.len)}, less time today)") else "",
            color = C.text2, fontSize = TS.caption,
        )
        if (picked != null) {
            val text = when (picked) {
                DayFill.Pick.TAKE -> if (timed == false) {
                    tr("ลงเป็นงานวันนี้แล้ว ใช้เวลาเตือนเดิมของงานนี้", "Planned for today, with its own reminder")
                } else tr("ลงเป็นงานแล้ว เตือนราว ${DayFill.hm(p.at)} แผนขยับ เวลาเตือนก็ขยับตาม", "Planned, reminder about ${DayFill.hm(p.at)}; it follows the plan")
                else -> ""
            }
            if (text.isNotEmpty()) Text(text, color = okText, fontSize = TS.body, fontWeight = FontWeight.Medium)
            Text(tr("แตะการ์ดเพื่อเปลี่ยนใจ", "Tap the card to change your mind"), color = C.muted, fontSize = TS.caption)
            return@Column
        }
        // The owner's own time and meals have no answers: they are where they are.
        if (item.kind == DayFill.Kind.LIFE || item.own) return@Column
        val take = { vm.fillTake(p); setOpen(null) }
        when (item.kind) {
            DayFill.Kind.DUE, DayFill.Kind.URGENT, DayFill.Kind.AGAIN -> TwoButtons(
                tr("เริ่ม 15 นาที", "Start 15 minutes"), take,
                tr("วันนี้หมดแรง", "Out of energy"), { vm.fillTired(item, plan.mode); setOpen(null) },
            )
            DayFill.Kind.REVIEW -> TwoButtons(
                tr("ทบทวน", "Review"), { task?.let(onReview) },
                tr("ไว้ก่อน", "Later"), { vm.fillSkip(item); setOpen(null) },
            )
            DayFill.Kind.ASK -> {
                val inp = input
                BigButton(tr("แบ่งให้เล็กลง", "Make it smaller"), true, Modifier.fillMaxWidth()) { vm.fillAsk(item, DayFill.Pick.SPLIT, inp()); setOpen(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!item.keptOnce) BigButton(tr("เก็บไว้ก่อน", "Keep for later"), false, Modifier.weight(1f)) { vm.fillAsk(item, DayFill.Pick.LATER, inp()); setOpen(null) }
                    BigButton(tr("ปล่อยไป", "Let it go"), false, Modifier.weight(1f)) { vm.fillAsk(item, DayFill.Pick.DROP, inp()); setOpen(null) }
                }
            }
            else -> TwoButtons(
                tr("รับ", "Take"), take,
                tr("ไม่ใช่ เปลี่ยนให้", "Not this, swap"), { vm.fillSwap(item); setOpen(null) },
            )
        }
        if (item.kind != DayFill.Kind.REVIEW && item.kind != DayFill.Kind.ASK) {
            Text(
                tr("ช่วงนี้ไม่ว่าง ย้ายไปช่วงอื่น", "Busy now, move it later"),
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.fillBusy(p, plan.mode); setOpen(null) }.padding(vertical = 8.dp),
                color = C.accentText, fontSize = TS.caption,
            )
        }
    }
}

@Composable
private fun TwoButtons(a: String, onA: () -> Unit, b: String, onB: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigButton(a, true, Modifier.weight(1f), onA)
        BigButton(b, false, Modifier.weight(1f), onB)
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .then(if (filled) Modifier.background(C.accent) else Modifier.background(C.card).border(1.dp, C.control, RoundedCornerShape(10.dp)))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) C.onAccent else C.text2, fontSize = TS.body, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmallAction(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp))
            .then(if (filled) Modifier.background(C.accent) else Modifier.background(C.card).border(1.dp, C.control, RoundedCornerShape(8.dp)))
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) C.onAccent else C.text2, fontSize = TS.caption, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PillButton(text: String, fg: Color = C.text2, edge: Color = C.control, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(999.dp)).background(C.card).border(1.dp, edge, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = TS.caption) }
}

@Composable
private fun Notice(text: String, fg: Color, bg: Color, bold: Boolean = false) {
    Text(
        text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
        color = fg, fontSize = TS.caption, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
    )
}

/** A dashed rounded border, for free time kept clear. */
private fun Modifier.dashed(color: Color) = drawBehind {
    val r = 10.dp.toPx()
    drawRoundRect(color, cornerRadius = CornerRadius(r, r), style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}
