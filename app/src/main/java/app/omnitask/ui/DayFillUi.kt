package app.omnitask.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import app.omnitask.widget.LockCardMode
import app.omnitask.widget.LockCardNotifier
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.omnitask.model.DayFill
import app.omnitask.model.DayPlan
import app.omnitask.model.RealDay
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs

/**
 * "เลขาจัดตารางวันนี้" (step 2, owner's picks of 2026-10-29, mock v12): the secretary fills today's real free time on
 * the today page. A Settings switch, off at first; this is its one reader.
 */
object DayFillMode {
    /** The phone's file of the day plan: the switch here, the plan's memory and its day log by the view model. */
    const val PREFS = "omni-dayfill"
    private const val ON = "on"
    /** The key of the plan's memory in [PREFS], for the view model and the reminder alike. */
    const val MEMORY = "memory"
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

    /**
     * The reminder of a slot taken today, at its time (the owner's mix, mock plan-catchup): "ถ้ายังไม่เริ่มตอนนี้ เหลืออีก
     * N ถึงจะทำทันวันนี้ (เริ่มช้าสุด HH:mm)". Read fresh from the phone, since the reminder wakes without the app open;
     * null (the reminder as before) with the switch off or when the memory cannot be read.
     */
    fun reminderText(context: Context, task: Task, now: LocalDateTime): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ON, false)) return null
        val mem = DayFill.decode(prefs.getString(MEMORY, null)) ?: return null
        return DayFill.reminderLeft(mem, task, now)
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
        SleepSetting(context)
        LookAheadSetting(context)
        LockCardSetting(context)
    }
}


/** "1 ชม. 30 นาที", "45 นาที", as in the mock. */
private fun dur(m: Int): String = if (m >= 60) {
    tr("${m / 60} ชม.", "${m / 60} h") + if (m % 60 != 0) tr(" ${m % 60} นาที", " ${m % 60} min") else ""
} else tr("$m นาที", "$m min")

/** Morning, afternoon or evening, cut where the Focus page timeline cuts them (noon, 17:00). Past midnight is evening. */
private fun part(at: Int): DayPlan.Part = when {
    at < 12 * 60 -> DayPlan.Part.MORNING
    at < 17 * 60 -> DayPlan.Part.AFTERNOON
    else -> DayPlan.Part.EVENING
}

/**
 * A block's name and its time range: its title ends in "ถึง HH:mm", so that end goes on the right after its start
 * ("08:30 ถึง 16:30"), as the mock has it. A block with no start (all day) shows its reason there instead.
 */
private fun blockText(row: DayFill.Row.Locked): Pair<String, String> {
    val start = row.at?.let(DayFill::hm)
    listOf(" ถึง ", " until ").forEach { sep ->
        val end = row.title.substringAfterLast(sep, "")
        if (end.matches(Regex("\\d{1,2}:\\d{2}"))) {
            return row.title.substringBeforeLast(sep) to (start?.let { it + tr(" ถึง ", " to ") } ?: "") + end
        }
    }
    return row.title to (start ?: row.why)
}

/** Days past the real deadline, for the red word on a task row ("เลย 9 วัน"). */
private fun lateWord(t: Task?, today: LocalDate): String? {
    val due = t?.due ?: return null
    if (due >= today) return null
    val n = today.toEpochDays() - due.toEpochDays()
    return tr("เลย $n วัน", "$n days late")
}

/**
 * The plan on the today page, in the Focus page timeline's look (owner's pick, 2026-10-30, mock Timeline): the day's
 * notices, the day in its parts (morning, afternoon, evening) with the red now line, blocks it works around as event
 * rows, the next task open on a soft band with its buttons, the free time kept clear, what moved to another day folded
 * up; under the strip on top, the line of tasks done today, then the errand, question and moved buttons in one row.
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
    var doneOpen by remember { mutableStateOf(false) }
    var laterOpen by remember { mutableStateOf(false) }
    // The moved task whose "ทำวันนี้" is open to choose a time (owner's pick ก+ข, 2026-10-31).
    var putting by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var logOpen by remember { mutableStateOf(false) }
    // A block's reason shows when it is tapped, one at a time.
    var reasonOf by remember { mutableStateOf<String?>(null) }
    // The owner's mix (mock plan-catchup, 2026-10-30): a taken slot gone by and not done asks "ทำไปหรือยัง"; after
    // "ยังไม่ได้ทำ" it waits for a place ([replacing]); a long press drags it, or a task taken today, to a time of the
    // owner's own.
    var replacing by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<Dragged?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    // Where each row sits on screen (its middle) and the time of each timed one, to turn a drag into a time. A row
    // that leaves the page takes its entries with it.
    val tops = remember { HashMap<String, Float>() }
    val times = remember { HashMap<String, Int>() }
    val planNow by rememberUpdatedState(plan)
    val nowNow by rememberUpdatedState(now.hour * 60 + now.minute)
    // The reminder at a taken slot counts to when the plan stops: kept on the phone for it, once its memory is read.
    LaunchedEffect(plan.day, plan.end, state.dayFill) { if (plan.day == now.date) vm.fillStop(plan.end) }
    // Taken slots whose line no longer matches the plan: their reminder follows, or the plan lets go of a line the
    // owner changed (written once, see TaskViewModel.fillFollow). Looked at again when one of its writes ends.
    LaunchedEffect(plan.moves, plan.letGo, state.fillWrites) { vm.fillFollow(plan.moves, plan.letGo) }
    // The look-ahead's rule 4 (switch on): today's own time, offered and done, kept on the phone.
    // Looked at again after "ยังไม่ได้ทำ", which leaves the plan as it was.
    LaunchedEffect(plan, LookAheadMode.on, state.dayFill.notYet) { if (LookAheadMode.on) vm.ownSeen(plan, input()) }
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

    val today = state.today
    val nowMin = now.hour * 60 + now.minute
    Card {
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            // What the secretary did and the day's numbers, in one strip that opens to the whole day (owner's pick ก).
            val numbers = tr("ว่างจริง ${dur(plan.free)}, จัดไว้ ${dur(plan.free - plan.buffer)}, เหลือกันชน ${dur(plan.buffer)}", "Free ${dur(plan.free)}, planned ${dur(plan.free - plan.buffer)}, buffer ${dur(plan.buffer)}")
            TellStrip(plan.banner, state.fillLog.takeIf { state.fillLogDay == plan.day.toString() }.orEmpty(), numbers, logOpen) { logOpen = !logOpen }
            // Tasks done today: one line between the strip and the buttons, open to the list (owner's pick, mock
            // focus-order, 2026-10-30), no longer inside the timeline.
            val done = plan.rows.filterIsInstance<DayFill.Row.Done>()
            if (done.isNotEmpty()) {
                DoneFold(done.size, doneOpen) { doneOpen = !doneOpen }
                if (doneOpen) done.forEach { d -> DoneRow(d.task, { vm.toggleDone(d.task) }) { onOpen(d.task) } }
            }
            // The errand, the secretary's questions and what moved to another day: one row of small buttons under the
            // strip, so they are seen on opening the page (owner's pick ก, mock plan-buttons, 2026-10-30).
            val later = plan.later.filter { it.item.kind != DayFill.Kind.LIFE }
            val errand = state.dayFill.takeIf { it.day == plan.day.toString() }?.errand
            FlowRow(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (errand == null) ActChip(tr("มีธุระ", "Errand"), chips) { chips = !chips; laterOpen = false }
                else ActChip(tr("ธุระเสร็จแล้ว จัดคืนให้", "Errand over, plan again"), false) { vm.fillErrandEnd(); chips = false }
                if (asks > 0) ActChip(tr("เลขาถาม $asks ข้อ", "$asks questions"), false, onAsks)
                if (later.isNotEmpty()) ActChip(tr("ย้ายไปวันอื่น ${later.size}", "${later.size} moved to another day"), laterOpen) { laterOpen = !laterOpen; chips = false }
            }
            if (chips && errand == null) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("ไม่ว่างนานแค่ไหน", "Busy for how long"), color = C.text2, fontSize = TS.caption)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60 to tr("1 ชม.", "1 h"), 120 to tr("2 ชม.", "2 h"), 180 to tr("3 ชม.", "3 h"), null to tr("ทั้งเย็นนี้", "All evening")).forEach { (min, label) ->
                            FootButton(label) { vm.fillErrand(plan.start, min, label); chips = false }
                        }
                    }
                }
            }
            if (laterOpen && later.isNotEmpty()) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("ไม่นับว่าข้าม", "Not counted as skipped"), color = C.muted, fontSize = TS.caption)
                later.forEach { l ->
                    // A task moved by the plan (not one the owner answered) can be put back today, at a time of the owner's own.
                    val canPut = l.picked == null && l.item.task != null
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            // An answer given today can be taken back by tapping it, as a card can.
                            (if (l.picked != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.fillUnpick(l.item) } else Modifier).weight(1f),
                        ) {
                            Text(l.item.title, color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.why, color = if (l.risk) C.red else C.muted, fontSize = TS.caption)
                        }
                        if (canPut) SmallAction(tr("ทำวันนี้", "Do today"), putting == l.item.id) { putting = if (putting == l.item.id) null else l.item.id }
                    }
                    if (canPut && putting == l.item.id) {
                        val put = { at: Int -> vm.fillPlace(DayFill.Placed(l.item, at, l.item.len, short = false, running = false), at); putting = null }
                        PutTimes(plan, l.item.len, nowMin, put) {
                            val from = maxOf(nowMin, plan.start)
                            pickSystemTime(context, LocalTime.of((from / 60) % 24, from % 60)) { t ->
                                val at = t.hour * 60 + t.minute
                                if (at < from) android.widget.Toast.makeText(context, tr("เลือกเวลาตั้งแต่ตอนนี้เป็นต้นไป", "Pick a time from now on"), android.widget.Toast.LENGTH_SHORT).show()
                                else put(at)
                            }
                        }
                    }
                }
            }
            val sprintHint = plan.sprint?.hint?.takeIf { LookAheadMode.on }
            if (sprintHint != null) plan.sprint?.let { sp ->
                // Mock look-ahead v2: the sprint and its warning in one row, led by a tag; the tag opens the reason and
                // the week's numbers.
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sp.late) C.redSoft else C.raised)
                            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HintPill(sprintHint.label, sprintHint.tone) { sprintDetail = !sprintDetail }
                        Text(
                            sp.lead ?: (tr("สปรินต์ ", "Sprint ") + sp.names),
                            Modifier.weight(1f), color = C.text, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (sp.taken) {
                            Text(
                                tr("รับแล้ว", "Taken"),
                                Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.fillSprint(sp.projects, take = null, swap = false) }.padding(horizontal = 4.dp, vertical = 6.dp),
                                color = C.lime, fontSize = TS.caption,
                            )
                        } else {
                            SmallAction(tr("รับ", "Take"), true) { vm.fillSprint(sp.projects, take = true, swap = false) }
                            SmallAction(tr("เปลี่ยน", "Swap"), false) { vm.fillSprint(sp.projects, take = false, swap = true) }
                        }
                    }
                    if (sprintDetail) Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(sprintHint.why, color = C.muted, fontSize = TS.caption)
                        if (sp.lead != null) Text(tr("สปรินต์ ", "Sprint ") + sp.names, color = C.text2, fontSize = TS.caption)
                        sp.lines.forEach { Text(it, color = C.text2, fontSize = TS.caption) }
                    }
                }
            } else plan.sprint?.let { sp ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            buildAnnotatedString {
                                pushStyle(SpanStyle(color = C.text2, fontWeight = FontWeight.SemiBold))
                                append(tr("สปรินต์สัปดาห์นี้ ", "This week's sprint "))
                                pop()
                                append(sp.names)
                            },
                            Modifier.weight(1f), color = C.text, fontSize = TS.caption,
                        )
                        if (sp.taken) {
                            // Taken today: a tap opens it again, as in the mock.
                            Text(
                                tr("รับแล้ว", "Taken"),
                                Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.fillSprint(sp.projects, take = null, swap = false) }.padding(horizontal = 4.dp, vertical = 6.dp),
                                color = C.lime, fontSize = TS.caption,
                            )
                        } else {
                            SmallAction(tr("รับ", "Take"), true) { vm.fillSprint(sp.projects, take = true, swap = false) }
                            SmallAction(tr("เปลี่ยน", "Swap"), false) { vm.fillSprint(sp.projects, take = false, swap = true) }
                        }
                    }
                    Text(
                        sp.note + if (sprintDetail) tr(" (แตะเพื่อย่อ)", " (tap to fold)") else tr(" (แตะดู)", " (tap to see)"),
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable { sprintDetail = !sprintDetail },
                        color = if (sp.late) C.amber else C.muted, fontSize = TS.caption,
                    )
                    if (sprintDetail) sp.lines.forEach { Text(it, color = C.text2, fontSize = TS.caption) }
                }
            }
            if (plan.end <= plan.start) {
                Text(tr("วันนี้ไม่เหลือเวลาว่างให้จัดแล้ว", "No free time left to plan today"), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = C.muted, fontSize = TS.caption)
            }

            // The day, part by part. Tasks done today are in their line above, not here. While a finger drags, the
            // timeline holds still as it was when the press began (the plan is rebuilt each minute), so the row under
            // the finger stays that row and goes on getting the drag.
            val held = drag
            val view = held?.plan ?: plan
            val viewNow = held?.now ?: nowMin
            val expanded = open ?: view.next
            // The slot not done that waits for a place: shown after the now line, where the time offered first is.
            val waiting = view.rows.filterIsInstance<DayFill.Row.Card>().firstOrNull { c ->
                c.placed.item.id == replacing && c.placed.at + c.placed.len <= viewNow && c.placed.item.task?.isOpen == true
            }?.placed
            val counts = view.rows.filterIsInstance<DayFill.Row.Card>().filter { it.placed.item.kind != DayFill.Kind.LIFE && it.placed != waiting }
                .groupingBy { part(it.at) }.eachCount()
            var section: DayPlan.Part? = null
            var nowShown = false
            fun enter(p: DayPlan.Part): Boolean = (p != section).also { if (it) section = p }
            val offers = waiting?.let { DayFill.suggest(view, it.len, viewNow) }.orEmpty()
            var waitingShown = false
            // A long press drags a task; its time follows the finger between the rows around it (its own row too, so
            // where it starts is its own time).
            fun timeAt(y: Float, len: Int): Int {
                val pl = drag?.plan ?: planNow
                // Never earlier than the clock now, even while the timeline holds still.
                val lo = maxOf(nowNow, pl.start).let { if (it % 5 == 0) it else it + 5 - it % 5 }
                val hi = maxOf(lo, (pl.bed - len) - (pl.bed - len) % 5)
                val pts = times.mapNotNull { (k, t) -> tops[k]?.let { t to it } }.sortedBy { it.second }
                if (pts.isEmpty()) return lo
                val raw = when {
                    y <= pts.first().second -> pts.first().first.toFloat()
                    y >= pts.last().second -> pts.last().first.toFloat()
                    else -> {
                        val i = pts.indexOfLast { it.second <= y }
                        val a = pts[i]
                        val b = pts[minOf(i + 1, pts.lastIndex)]
                        if (b.second <= a.second) a.first.toFloat() else a.first + (y - a.second) / (b.second - a.second) * (b.first - a.first)
                    }
                }
                return (Math.round(raw / 5f) * 5).coerceIn(lo, hi)
            }
            // The slot is read when the finger lifts, so a plan that changed meanwhile puts the line as it is now.
            suspend fun PointerInputScope.drags(rowKey: String, cur: State<DayFill.Placed?>) {
                val slop = 10.dp.toPx()
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val y = tops[rowKey]
                        // One drag at a time: a second finger's long press leaves the first one be.
                        if (drag == null && cur.value != null && y != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag = Dragged(rowKey, y, planNow, nowNow)
                            dragDy = 0f
                        }
                    },
                    onDrag = { change, amount ->
                        if (drag?.key == rowKey) {
                            change.consume()
                            dragDy += amount.y
                        }
                    },
                    onDragEnd = {
                        val d = drag
                        if (d != null && d.key == rowKey) {
                            // The task as the plan has it now (its line may have changed during the drag).
                            val p = cur.value?.let { held -> planNow.placed.firstOrNull { it.item.id == held.item.id } ?: held }
                            // A press let go where it began puts nothing.
                            if (p != null && abs(dragDy) >= slop) {
                                vm.fillPlace(p, timeAt(d.from + dragDy, p.len))
                                replacing = null
                                open = null
                            }
                            drag = null
                            dragDy = 0f
                        }
                    },
                    onDragCancel = {
                        if (drag?.key == rowKey) {
                            drag = null
                            dragDy = 0f
                        }
                    },
                )
            }
            /**
             * One row: where it sits (and its time, when it has one) is kept for the drag. [p]: the task a long press
             * drags. The dragged row follows the finger with the time it would get on a tag over it, so nothing under
             * it shifts.
             */
            @Composable
            fun Track(rowKey: String, at: Int?, p: DayFill.Placed? = null, over: Boolean = false, content: @Composable () -> Unit) {
                val moving = p != null && drag?.key == rowKey
                val cur = rememberUpdatedState(p)
                SideEffect { if (at != null) times[rowKey] = at else times.remove(rowKey) }
                DisposableEffect(rowKey) { onDispose { tops.remove(rowKey); times.remove(rowKey) } }
                val amber = C.amber
                Box(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { c -> if (!moving) tops[rowKey] = c.positionInRoot().y + c.size.height / 2f }
                        .then(if (moving) Modifier.zIndex(1f).graphicsLayer { translationY = dragDy; shadowElevation = 12f }.background(C.raised) else Modifier)
                        .then(if (over && !moving) Modifier.drawBehind { drawRect(amber, size = Size(3.dp.toPx(), size.height)) } else Modifier)
                        .then(if (p != null) Modifier.pointerInput(rowKey) { drags(rowKey, cur) } else Modifier),
                ) {
                    Column(Modifier.fillMaxWidth()) { content() }
                    val d = drag
                    if (moving && p != null && d != null) {
                        val t = timeAt(d.from + dragDy, p.len)
                        val o = DayFill.overlapText(DayFill.overlaps(view, t, p.len, p.item.id), ::thaiShort)
                        Column(
                            Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp).clip(RoundedCornerShape(12.dp))
                                .background(C.accent).padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(DayFill.hm(t), color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                            if (o != null) Text(o, color = C.onAccent, fontSize = TS.micro)
                        }
                    }
                }
            }
            val used = HashSet<String>()
            view.rows.forEach { row ->
                if (row is DayFill.Row.Done) return@forEach
                // The slot waiting for a place shows after the now line instead, with the times offered.
                if (row is DayFill.Row.Card && waiting != null && row.placed == waiting) return@forEach
                // A row is known by what it is, not by where it sits, so a drag holds on when the rows before it change.
                val base = when (row) {
                    is DayFill.Row.Card -> "c:" + row.placed.item.id
                    is DayFill.Row.Locked -> "b:" + row.at + ":" + row.title
                    is DayFill.Row.Gap -> "g:" + row.at
                    is DayFill.Row.Done -> "d"
                }
                var unique = base
                var n = 1
                while (!used.add(unique)) unique = base + "#" + n++
                val rk = unique
                val at = row.at
                if (at != null) {
                    val p = part(at)
                    if (enter(p)) PartHead(p, counts[p] ?: 0)
                    if (!nowShown && at > viewNow && !(row is DayFill.Row.Locked && row.past)) {
                        NowLine(viewNow)
                        nowShown = true
                    }
                    if (waiting != null && !waitingShown && nowShown && (offers.isEmpty() || at >= offers.first())) {
                        waitingShown = true
                        Track("waiting", null, waiting) { WaitingCard(waiting, offers, today, vm) { replacing = null } }
                    }
                }
                key(rk) {
                    when (row) {
                        is DayFill.Row.Locked -> Track(rk, at) {
                            val (name, range) = blockText(row)
                            val rk = "block:" + row.at + ":" + row.title
                            // A calendar event can be called "ว่าง ทำงานได้" for today (owner's pick ก+ข, 2026-10-31).
                            val ev = row.event?.takeIf { !it.allDay && !row.past && it.title != RealDay.jobTitle && (row.at ?: 0) < plan.end }
                            val freeAction: (@Composable () -> Unit)? = if (ev != null && reasonOf == rk) {
                                {
                                    if (row.free) SmallAction(tr("นับว่าไม่ว่างตามปฏิทิน", "Busy as the calendar says"), false) { vm.fillFreeEvent(ev, false) }
                                    else SmallAction(tr("ว่าง ทำงานได้ วันนี้", "Free to work today"), true) { vm.fillFreeEvent(ev, true) }
                                }
                            } else null
                            BlockRow(
                                name, range, row.why.takeIf { reasonOf == rk && range != it },
                                faded = row.past || (at != null && at <= viewNow && !row.free), bar = if (row.past) C.faint else if (row.free) C.lime else C.teal,
                                tag = if (row.free) tr("ว่าง ทำงานได้", "Free to work") else null,
                                extra = freeAction,
                            ) { reasonOf = if (reasonOf == rk) null else rk }
                        }
                        is DayFill.Row.Gap -> Track(rk, at) {
                            val hi = waiting != null && offers.firstOrNull()?.let { it >= row.at && it < row.at + row.len } == true
                            GapRow(
                                DayFill.hm(row.at) + tr(" ถึง ", " to ") + DayFill.hm(row.at + row.len) +
                                    if (hi) tr(" ว่าง ${DayFill.lenText(row.len)} ช่องที่แนะนำ", " free ${DayFill.lenText(row.len)}, the one offered")
                                    else tr(" ว่างไว้ เผื่อเรื่องไม่คาดคิด", " kept free for the unexpected"),
                                hi,
                            )
                        }
                        is DayFill.Row.Card -> {
                            val p = row.placed
                            val item = p.item
                            if (item.kind == DayFill.Kind.LIFE) Track(rk, at) {
                                // Meals and rest read as blocks: they are where they are.
                                val lk = "life:" + item.id
                                BlockRow(
                                    item.title, DayFill.hm(p.at) + tr(" ถึง ", " to ") + DayFill.hm(p.at + p.len),
                                    item.why.takeIf { reasonOf == lk || item.id == expanded }, faded = p.at + p.len <= viewNow, bar = C.teal,
                                ) { reasonOf = if (reasonOf == lk) null else lk }
                            } else {
                                val task = item.task
                                val acc = task?.let { state.dayFill.accepted[DayFill.ident(it)] }?.takeIf { it.day == view.day.toString() }
                                val taken = row.picked == DayFill.Pick.TAKE && acc != null && task?.isOpen == true
                                val gone = p.at + p.len <= viewNow
                                val over = if (gone) null else DayFill.overlapText(DayFill.overlapsOf(view, p), ::thaiShort)
                                // Only a task taken today moves by a long press (and the one waiting for a place).
                                Track(rk, at, p.takeIf { taken }, over = over != null) {
                                    if (item.id == expanded) {
                                        val timed = task?.let { state.dayFill.accepted[DayFill.ident(it)]?.timed }
                                        NextCard(p, row.picked, timed, today, view, vm, input, onReview) { open = it }
                                    } else {
                                        PlanRow(p, row.picked, today, vm) { open = item.id }
                                    }
                                    val indent = Modifier.padding(start = 44.dp, end = 12.dp)
                                    over?.let { Text(it, indent, color = C.amber, fontSize = TS.caption) }
                                    if (taken && gone) {
                                        Column(indent.padding(top = 2.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(tr("เลยเวลา ${DayFill.hm(p.at)} แล้ว ทำไปหรือยัง", "${DayFill.hm(p.at)} went by. Done it?"), color = C.text, fontSize = TS.body)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                SmallAction(tr("ทำแล้ว", "Done"), false) { task?.let(vm::toggleDone) }
                                                SmallAction(tr("ยังไม่ได้ทำ", "Not yet"), true) { replacing = item.id; open = null; vm.fillNotYet(p) }
                                            }
                                        }
                                    }
                                    if (taken && p.at <= viewNow) DayFill.leftText(DayFill.latestStart(view, p.len), viewNow)?.let { left ->
                                        val soon = DayFill.latestStart(view, p.len) - viewNow < 60
                                        Text(left, indent.padding(bottom = 4.dp), color = if (soon) C.amber else C.lime, fontSize = TS.caption)
                                    }
                                }
                            }
                        }
                        is DayFill.Row.Done -> Unit
                    }
                }
            }
            if (!nowShown && view.rows.isNotEmpty()) NowLine(viewNow)
            if (waiting != null && !waitingShown) Track("waiting", null, waiting) { WaitingCard(waiting, offers, today, vm) { replacing = null } }

        }
    }
}

/**
 * "เลขาทำอะไรไปบ้าง" (owner's pick ก, 2026-10-30): the latest thing the secretary did, with tonight's shift or the
 * day's numbers under it; a tap lists the whole day, newest first.
 */
@Composable
private fun TellStrip(banner: String?, log: List<Pair<String, String>>, numbers: String, isOpen: Boolean, onClick: () -> Unit) {
    val latest = log.lastOrNull()?.let { (at, text) -> splitNews(text).let { (what, moves) -> "$at $what" to moves } }
    val title = latest?.first ?: banner ?: numbers
    val sub = when {
        latest != null -> listOfNotNull(latest.second, banner).joinToString("\n").ifEmpty { numbers }
        banner != null -> numbers
        else -> null
    }
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(C.radius))
            .background(C.raised).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(C.accent))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = TS.body, maxLines = if (isOpen) 6 else 2, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, color = C.muted, fontSize = TS.caption, maxLines = if (isOpen) 4 else 2, overflow = TextOverflow.Ellipsis) }
        }
        Text(if (isOpen) tr("ย่อ", "Fold") else tr("ดูทั้งวัน", "Whole day"), color = C.muted, fontSize = TS.caption)
    }
    if (!isOpen) return
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LogLine(tr("ตอนนี้", "Now"), numbers)
        banner?.let { LogLine(tr("วันนี้", "Today"), it) }
        log.asReversed().forEach { (at, text) -> LogLine(at, text) }
    }
}

/**
 * A told line cut in two: what happened (the strip's headline) and what the secretary moved for it (the smaller line).
 * When the plan still fitted, the smaller line says so, and the headline does not claim a re-plan.
 */
private fun splitNews(text: String): Pair<String, String?> {
    for (cut in listOf(" เลขาจัดใหม่ให้แล้ว: ", ". Replanned: ")) {
        val i = text.indexOf(cut)
        if (i >= 0) return text.substring(0, i) to tr("เลขาจัดใหม่: ", "Re-planned: ") + text.substring(i + cut.length)
    }
    for (cut in listOf(" แผนเดิมยังพอดี", ". The plan still fits")) {
        val i = text.indexOf(cut)
        if (i >= 0) return text.substring(0, i) to text.substring(i).trimStart(' ', '.')
    }
    return text to null
}

@Composable
private fun LogLine(at: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(at, Modifier.width(44.dp), color = C.muted, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
        Text(text, Modifier.weight(1f), color = C.text2, fontSize = TS.caption)
    }
}

/** A part of the day: its name, a hairline, and how many tasks it holds, as on the Focus page. */
@Composable
private fun PartHead(p: DayPlan.Part, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.label, color = C.muted, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(C.divider))
        if (count > 0) Text("$count", color = C.muted, fontSize = TS.caption)
    }
}

@Composable
private fun NowLine(nowMin: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(C.red))
        Text(DayFill.hm(nowMin), Modifier.padding(horizontal = 6.dp), color = C.red, fontSize = TS.micro, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.5.dp).background(C.red.copy(alpha = 0.7f)))
    }
}

/** "เสร็จแล้ว N งาน": the tasks ticked today, one tap from seeing them. A plain ring with a tick, not green (owner's pick ค). */
@Composable
private fun DoneFold(n: Int, isOpen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClick = onClick).padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).border(1.8.dp, C.muted, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ic.check, null, tint = C.muted, modifier = Modifier.size(12.dp))
            }
        }
        Text(tr("เสร็จแล้ว $n งาน", "$n done"), Modifier.weight(1f).padding(start = 2.dp), color = C.muted, fontSize = TS.body)
        Text(if (isOpen) tr("ย่อ", "Fold") else tr("แตะดู", "Tap to see"), color = C.muted, fontSize = TS.caption)
    }
}

@Composable
private fun DoneRow(task: Task, onTick: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(0.55f).clickable(onClick = onClick).padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheck(task, onTick, size = 18.dp, touch = 36.dp)
        Text(task.title, Modifier.weight(1f).padding(start = 2.dp), color = C.muted, fontSize = TS.body, textDecoration = TextDecoration.LineThrough, maxLines = 2)
    }
}

/** A block the plan works around (an event, the day job, sleep, an errand, a meal): a bar, its name, its time. */
@Composable
private fun BlockRow(
    title: String, right: String, reason: String?, faded: Boolean, bar: Color, tag: String? = null, extra: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 10.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
            Text(
                title, Modifier.weight(1f).padding(start = 10.dp), color = if (faded) C.faint else C.text,
                style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (tag != null) Text(tag, Modifier.padding(start = 8.dp), color = C.lime, fontSize = TS.caption, maxLines = 1)
            Text(right, Modifier.padding(start = 8.dp), color = if (faded) C.faint else C.muted, fontSize = TS.caption, maxLines = 1)
        }
        if (reason != null) Text(reason, Modifier.padding(start = 13.dp, bottom = 4.dp), color = C.muted, fontSize = TS.caption)
        if (extra != null) Box(Modifier.padding(start = 13.dp, top = 2.dp, bottom = 6.dp)) { extra() }
    }
}

/**
 * Where to put a moved task back today ("ทำวันนี้"): now, the free times the plan has (the first recommended), or a time
 * of the owner's own. A time on top of something is allowed: the task then says what it overlaps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PutTimes(plan: DayFill.Plan, len: Int, nowMin: Int, put: (Int) -> Unit, own: () -> Unit) {
    // Now, to the next quarter hour; then the plan's free times that are not the same time.
    val now = (maxOf(nowMin, plan.start) + 14) / 15 * 15
    val offers = (listOf(now) + DayFill.suggest(plan, len, nowMin)).distinct()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("ลงเวลาไหน ถ้าซ้อนกับนัดหรืองานอื่น จะบอกไว้ที่งาน", "Which time? If it overlaps an event or other work, the task says so"), color = C.muted, fontSize = TS.caption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            offers.forEach { at ->
                val label = if (at == now) tr("ตอนนี้ ", "Now ") + DayFill.hm(at) else DayFill.hm(at) + tr(" ว่าง", " free")
                PlaceChip(label, rec = at == now) { put(at) }
            }
            PlaceChip(tr("เลือกเวลาเอง", "Pick a time"), rec = false, onClick = own)
        }
    }
}

/** Free time left clear on purpose; [hi] while it holds the time offered first for a task not done. */
@Composable
private fun GapRow(text: String, hi: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 2.dp, bottom = 2.dp).heightIn(min = 36.dp).height(IntrinsicSize.Min)
            .then(if (hi) Modifier.clip(RoundedCornerShape(8.dp)).background(C.accentSoft) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (hi) C.accent else C.control))
        Text(text, Modifier.weight(1f).padding(start = 10.dp), color = if (hi) C.accentText else C.muted, fontSize = TS.body)
    }
}

/** The row being dragged, where its middle was when the finger pressed, and the plan and minute held while it lasts. */
private data class Dragged(val key: String, val from: Float, val plan: DayFill.Plan, val now: Int)

/**
 * A name cut short for "ซ้อนกับ" at a whole word, about ten letters or more: Thai has no spaces, so the phone's word
 * breaker finds where words end. A name with no word end early enough is cut as the plan cuts it.
 */
private fun thaiShort(name: String): String {
    if (name.length <= 16) return name
    val words = BreakIterator.getWordInstance(Locale.forLanguageTag("th"))
    words.setText(name)
    var end = words.next()
    while (end != BreakIterator.DONE && name.substring(0, end).trimEnd().length < 10) end = words.next()
    if (end == BreakIterator.DONE || end > 22) return DayFill.short(name)
    return name.substring(0, end).trimEnd()
}

/**
 * A taken slot gone by and not done, after "ยังไม่ได้ทำ" (mock plan-catchup board 3): the times free after now, the first
 * one recommended and the last the latest, or a long press to drag it anywhere, on top of other work too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaitingCard(p: DayFill.Placed, offers: List<Int>, today: LocalDate, vm: TaskViewModel, onPlaced: () -> Unit) {
    val item = p.item
    Column(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().background(C.accentSoft).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Mark(item, vm)
            Column(Modifier.weight(1f).heightIn(min = 36.dp).padding(start = 2.dp), verticalArrangement = Arrangement.Center) {
                Text(item.title, color = C.text, fontSize = TS.title)
                val late = lateWord(item.task, today)
                Text(
                    buildAnnotatedString {
                        append(tr("ยังไม่ได้วาง", "Not placed"))
                        if (late != null) {
                            append(", ")
                            pushStyle(SpanStyle(color = C.red, fontWeight = FontWeight.Medium))
                            append(late)
                            pop()
                        }
                        append(", ")
                        append(dur(p.len))
                    },
                    color = C.muted, fontSize = TS.caption,
                )
            }
        }
        Column(Modifier.padding(start = 38.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (offers.isNotEmpty()) {
                Text(tr("ยังไม่ได้ทำ: วางช่องไหนดี", "Not done yet: where to put it?"), color = C.text, fontSize = TS.body)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    offers.forEachIndexed { i, at ->
                        val label = DayFill.hm(at) + when {
                            i == 0 -> tr(" แนะนำ", " recommended")
                            i == offers.lastIndex -> tr(" ช้าสุด", " latest")
                            else -> ""
                        }
                        PlaceChip(label, rec = i == 0) { vm.fillPlace(p, at); onPlaced() }
                    }
                }
                Text(tr("หรือกดค้างที่งานแล้วลากไปวางเวลาไหนก็ได้ ซ้อนกับงานอื่นได้", "Or press and hold the task and drag it to any time, on top of other work too"), color = C.muted, fontSize = TS.caption)
            } else {
                Text(
                    tr("วันนี้ไม่มีช่องว่างพอ กดค้างที่งานแล้วลากไปวางเวลาไหนก็ได้ ซ้อนกับงานอื่นได้", "No free time left today: press and hold the task and drag it to any time, on top of other work too"),
                    color = C.muted, fontSize = TS.caption,
                )
            }
        }
    }
}

/** A time offered for a task not done; the recommended one is outlined. */
@Composable
private fun PlaceChip(text: String, rec: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(C.raised)
            .border(1.dp, if (rec) C.accent else C.control, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (rec) C.accentText else C.text, fontSize = TS.caption, fontWeight = if (rec) FontWeight.SemiBold else FontWeight.Normal) }
}

/** The ring of a card that is a task, or a quiet dot for one that is not. */
@Composable
private fun Mark(item: DayFill.Item, vm: TaskViewModel) {
    val task = item.task
    if (task != null) TaskCheck(task, { vm.toggleDone(task) }, size = 20.dp, touch = 36.dp)
    else Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).clip(CircleShape).background(C.faint)) }
}

/** A task row's second line, led by its time: "16:45, เลย 9 วัน, 15 นาที, สปรินต์". Red only for late. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Caption(p: DayFill.Placed, picked: DayFill.Pick?, today: LocalDate) {
    val item = p.item
    val late = lateWord(item.task, today)
    val kind = item.tag ?: when (item.kind) {
        // The look-ahead's "อยากทำ" says it in the mock's place.
        DayFill.Kind.MINE -> tr("เวลาของตัวเอง", "Your own time").takeIf { !LookAheadMode.on || item.hints.none { it.tone == DayFill.Tone.WANT } }
        DayFill.Kind.WORK, DayFill.Kind.NET, DayFill.Kind.LIFE -> null
        else -> item.kind.label
    }
    if (LookAheadMode.on) {
        // Mock look-ahead v2: the kind and the look-ahead's tags as short tags after the time; a tap shows the reason.
        var why by remember(item.id) { mutableStateOf<String?>(null) }
        fun toggle(w: String) { why = if (why == w) null else w }
        Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildAnnotatedString {
                        append(DayFill.hm(p.at))
                        if (late != null) {
                            append(", ")
                            pushStyle(SpanStyle(color = C.red, fontWeight = FontWeight.Medium))
                            append(late)
                            pop()
                        }
                        append(", ")
                        if (picked == DayFill.Pick.TAKE) {
                            pushStyle(SpanStyle(color = C.lime))
                            append(if (p.pinned) tr("Klui วางเอง", "Put by you") else tr("รับแล้ว", "Taken"))
                            pop()
                        } else append(dur(p.len))
                    },
                    Modifier.align(Alignment.CenterVertically).padding(end = 2.dp), color = C.muted, fontSize = TS.caption,
                )
                if (kind != null) HintPill(kind, null, Modifier.align(Alignment.CenterVertically)) { toggle(item.why) }
                item.hints.forEach { h -> HintPill(h.label, h.tone, Modifier.align(Alignment.CenterVertically)) { toggle(h.why) } }
            }
            why?.let { Text(it, Modifier.padding(top = 3.dp), color = C.muted, fontSize = TS.caption) }
        }
        return
    }
    Text(
        buildAnnotatedString {
            append(DayFill.hm(p.at))
            if (late != null) {
                append(", ")
                pushStyle(SpanStyle(color = C.red, fontWeight = FontWeight.Medium))
                append(late)
                pop()
            }
            append(", ")
            if (picked == DayFill.Pick.TAKE) {
                pushStyle(SpanStyle(color = C.lime))
                append(if (p.pinned) tr("Klui วางเอง", "Put by you") else tr("รับแล้ว", "Taken"))
                pop()
            } else append(dur(p.len))
            if (kind != null) append(", $kind")
        },
        color = C.muted, fontSize = TS.caption,
    )
}

@Composable
private fun PlanRow(p: DayFill.Placed, picked: DayFill.Pick?, today: LocalDate, vm: TaskViewModel, onExpand: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(start = 6.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Mark(p.item, vm)
        Column(Modifier.weight(1f).heightIn(min = 36.dp).padding(start = 2.dp), verticalArrangement = Arrangement.Center) {
            Text(p.item.title, color = C.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
            Caption(p, picked, today)
        }
    }
}

/** The task to do next, open on a soft band: why it is here, and its buttons. */
@Composable
private fun NextCard(
    p: DayFill.Placed, picked: DayFill.Pick?, timed: Boolean?, today: LocalDate, plan: DayFill.Plan, vm: TaskViewModel,
    input: () -> DayFill.Input, onReview: (Task) -> Unit, setOpen: (String?) -> Unit,
) {
    val item = p.item
    val task = item.task
    val indent = Modifier.padding(start = 38.dp)
    Column(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().background(C.accentSoft).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Mark(item, vm)
            Column(Modifier.weight(1f).heightIn(min = 36.dp).padding(start = 2.dp), verticalArrangement = Arrangement.Center) {
                Text(item.title, color = C.text, fontSize = TS.title)
                Caption(p, picked, today)
            }
        }
        Text(
            item.why + if (p.short) tr(" (ย่อเหลือ ${dur(p.len)} เพราะวันนี้เวลาน้อยลง)", " (cut to ${dur(p.len)}, less time today)") else "",
            indent, color = C.muted, fontSize = TS.caption,
        )
        if (picked != null) {
            val text = when (picked) {
                DayFill.Pick.TAKE -> if (timed == false) {
                    tr("ลงเป็นงานวันนี้แล้ว ใช้เวลาเตือนเดิมของงานนี้ ", "Planned for today, with its own reminder. ")
                } else if (p.pinned) {
                    // Put by the owner: it stays there, so the plan does not move its reminder.
                    tr("Klui วางเอง เตือน ${DayFill.hm(p.at)} ", "Put by you, reminder at ${DayFill.hm(p.at)}. ")
                } else tr("ลงเป็นงานแล้ว เตือน ${DayFill.hm(p.at)} แผนขยับ เวลาเตือนก็ขยับตาม ", "Planned, reminder at ${DayFill.hm(p.at)}; it follows the plan. ")
                else -> ""
            }
            Text(
                text + tr("แตะเพื่อเปลี่ยนใจ", "Tap to change your mind"),
                indent.clip(RoundedCornerShape(6.dp)).clickable { vm.fillUnpick(item); setOpen(null) }.padding(vertical = 6.dp),
                color = if (text.isNotEmpty()) C.lime else C.muted, fontSize = TS.caption,
            )
            return@Column
        }
        // The owner's own reminder time has no answers: it is where it is.
        if (item.own) return@Column
        val take = { vm.fillTake(p); setOpen(null) }
        Column(indent.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        BigButton(tr("เก็บไว้ก่อน", "Keep for later"), false, Modifier.weight(1f)) { vm.fillAsk(item, DayFill.Pick.LATER, inp()); setOpen(null) }
                        BigButton(tr("ปล่อยไป", "Let it go"), false, Modifier.weight(1f)) { vm.fillAsk(item, DayFill.Pick.DROP, inp()); setOpen(null) }
                    }
                }
                else -> TwoButtons(
                    tr("รับ", "Take"), take,
                    tr("ไม่ใช่ เปลี่ยนให้", "Not this, swap"), { vm.fillSwap(item); setOpen(null) },
                )
            }
        }
        if (item.kind != DayFill.Kind.REVIEW && item.kind != DayFill.Kind.ASK) {
            Text(
                tr("ช่วงนี้ไม่ว่าง ย้ายไปช่วงอื่น", "Busy now, move it later"),
                indent.clip(RoundedCornerShape(6.dp)).clickable { vm.fillBusy(p, plan.mode); setOpen(null) }.padding(vertical = 8.dp),
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
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
            .then(if (filled) Modifier.background(C.accent) else Modifier.background(C.raised).border(1.dp, C.control, RoundedCornerShape(10.dp)))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) C.onAccent else C.text, fontSize = TS.body, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmallAction(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp))
            .then(if (filled) Modifier.background(C.accent) else Modifier.background(C.raised).border(1.dp, C.control, RoundedCornerShape(8.dp)))
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) C.onAccent else C.text, fontSize = TS.caption, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** A small round button in the row under the strip; [on] while what it opens is open. */
@Composable
private fun ActChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(if (on) C.accentSoft else C.raised)
            .border(1.dp, if (on) C.accent else C.control, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) C.accentText else C.text, fontSize = TS.caption) }
}

/** The errand's lengths. */
@Composable
private fun FootButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(C.raised).border(1.dp, C.control, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.text, fontSize = TS.body) }
}

/** The switch for the lock-screen card (the Today widget's look, for today only). Off by default. */
@Composable
fun LockCardSetting(context: Context) {
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(LockCardMode.isOn(context)) }
    SettingSwitch(
        tr("การ์ดวันนี้บนหน้าล็อคจอ", "Today card on the lock screen"),
        tr(
            "การ์ดเงียบที่ค้างอยู่ หน้าตาเหมือนวิดเจ็ตวันนี้ เห็นเฉพาะวันนี้ไม่เกิน 4 แถว  ปิด: ไม่มีการ์ด (บน OPPO ต้องเปิด “แสดงเนื้อหาบนหน้าล็อคจอ” ให้แอปด้วย)",
            "A silent card that stays, looking like the Today widget, today only, up to 4 rows. Off: no card (on OPPO also allow the app to show content on the lock screen)",
        ),
        on,
    ) { v ->
        on = v
        LockCardMode.setOn(context, v)
        scope.launch { runCatching { LockCardNotifier.refresh(context) } }
    }
}
