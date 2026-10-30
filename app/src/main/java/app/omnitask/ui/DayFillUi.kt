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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.omnitask.model.DayFill
import app.omnitask.model.DayPlan
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
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
        SleepSetting(context)
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
 * up, and the errand and question buttons at the foot.
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
    var logOpen by remember { mutableStateOf(false) }
    // A block's reason shows when it is tapped, one at a time.
    var reasonOf by remember { mutableStateOf<String?>(null) }
    // Taken slots whose line no longer matches the plan: their reminder follows, or the plan lets go of a line the
    // owner changed (written once, see TaskViewModel.fillFollow). Looked at again when one of its writes ends.
    LaunchedEffect(plan.moves, plan.letGo, state.fillWrites) { vm.fillFollow(plan.moves, plan.letGo) }
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
            TellStrip(plan.banner, state.fillLog, numbers, logOpen) { logOpen = !logOpen }
            plan.sprint?.let { sp ->
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

            // The day, part by part. Tasks done today fold into one line where the first of them stood.
            val expanded = open ?: plan.next
            val done = plan.rows.filterIsInstance<DayFill.Row.Done>()
            val counts = plan.rows.filterIsInstance<DayFill.Row.Card>().filter { it.placed.item.kind != DayFill.Kind.LIFE }.groupingBy { part(it.at) }.eachCount()
            val firstPart = plan.rows.firstOrNull { it !is DayFill.Row.Done && it.at != null }?.at?.let(::part) ?: DayPlan.Part.MORNING
            var section: DayPlan.Part? = null
            var nowShown = false
            var foldShown = false
            fun enter(p: DayPlan.Part): Boolean = (p != section).also { if (it) section = p }
            plan.rows.forEach { row ->
                if (row is DayFill.Row.Done) {
                    if (foldShown) return@forEach
                    foldShown = true
                    if (section == null) {
                        enter(firstPart)
                        PartHead(firstPart, counts[firstPart] ?: 0)
                    }
                    DoneFold(done.size, doneOpen) { doneOpen = !doneOpen }
                    if (doneOpen) done.forEach { d -> DoneRow(d.task, { vm.toggleDone(d.task) }) { onOpen(d.task) } }
                    return@forEach
                }
                val at = row.at
                if (at != null) {
                    val p = part(at)
                    if (enter(p)) PartHead(p, counts[p] ?: 0)
                    if (!nowShown && at > nowMin && !(row is DayFill.Row.Locked && row.past)) {
                        NowLine(nowMin)
                        nowShown = true
                    }
                }
                when (row) {
                    is DayFill.Row.Locked -> {
                        val (name, range) = blockText(row)
                        val key = "block:" + row.at + ":" + row.title
                        BlockRow(
                            name, range, row.why.takeIf { reasonOf == key && range != it },
                            faded = row.past || (at != null && at <= nowMin), bar = if (row.past) C.faint else C.teal,
                        ) { reasonOf = if (reasonOf == key) null else key }
                    }
                    is DayFill.Row.Gap -> GapRow(
                        DayFill.hm(row.at) + tr(" ถึง ", " to ") + DayFill.hm(row.at + row.len) +
                            tr(" ว่างไว้ เผื่อเรื่องไม่คาดคิด", " kept free for the unexpected"),
                    )
                    is DayFill.Row.Card -> {
                        val p = row.placed
                        val item = p.item
                        if (item.kind == DayFill.Kind.LIFE) {
                            // Meals and rest read as blocks: they are where they are.
                            val key = "life:" + item.id
                            BlockRow(
                                item.title, DayFill.hm(p.at) + tr(" ถึง ", " to ") + DayFill.hm(p.at + p.len),
                                item.why.takeIf { reasonOf == key || item.id == expanded }, faded = p.at + p.len <= nowMin, bar = C.teal,
                            ) { reasonOf = if (reasonOf == key) null else key }
                        } else if (item.id == expanded) {
                            val timed = item.task?.let { state.dayFill.accepted[DayFill.ident(it)]?.timed }
                            NextCard(p, row.picked, timed, today, plan, vm, input, onReview) { open = it }
                        } else {
                            PlanRow(p, row.picked, today, vm) { open = item.id }
                        }
                    }
                    is DayFill.Row.Done -> Unit
                }
            }
            if (!nowShown && plan.rows.isNotEmpty()) NowLine(nowMin)

            // What moved to another day, folded into one line.
            val later = plan.later.filter { it.item.kind != DayFill.Kind.LIFE }
            if (later.isNotEmpty()) {
                Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp).fillMaxWidth().height(1.dp).background(C.divider))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { laterOpen = !laterOpen }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildAnnotatedString {
                            append(tr("ย้ายไปวันอื่น ${later.size} งาน ", "${later.size} moved to another day "))
                            pushStyle(SpanStyle(color = C.muted))
                            append(tr("ไม่นับว่าข้าม", "not counted as skipped"))
                            pop()
                        },
                        Modifier.weight(1f), color = C.text, fontSize = TS.body,
                    )
                    Text(if (laterOpen) tr("ย่อ", "Fold") else tr("แตะดู", "Tap to see"), color = C.muted, fontSize = TS.caption)
                }
                if (laterOpen) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    later.forEach { l ->
                        Column(
                            // An answer given today can be taken back by tapping it, as a card can.
                            if (l.picked != null) Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { vm.fillUnpick(l.item) } else Modifier.fillMaxWidth(),
                        ) {
                            Text(l.item.title, color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.why, color = if (l.risk) C.red else C.muted, fontSize = TS.caption)
                        }
                    }
                }
            }

            // The errand and the secretary's questions, at the foot as in the mock.
            FlowRow(
                Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val errand = state.dayFill.takeIf { it.day == plan.day.toString() }?.errand
                if (errand == null) FootButton(tr("มีธุระ", "Errand")) { chips = !chips }
                else FootButton(tr("ธุระเสร็จแล้ว จัดคืนให้", "Errand over, plan again")) { vm.fillErrandEnd(); chips = false }
                if (asks > 0) FootButton(tr("เลขาถาม $asks ข้อ", "$asks questions"), onAsks)
            }
            if (chips && state.dayFill.errand == null) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("ไม่ว่างนานแค่ไหน", "Busy for how long"), color = C.text2, fontSize = TS.caption)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60 to tr("1 ชม.", "1 h"), 120 to tr("2 ชม.", "2 h"), 180 to tr("3 ชม.", "3 h"), null to tr("ทั้งเย็นนี้", "All evening")).forEach { (min, label) ->
                            FootButton(label) { vm.fillErrand(plan.start, min, label); chips = false }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "เลขาทำอะไรไปบ้าง" (owner's pick ก, 2026-10-30): the latest thing the secretary did, with tonight's shift or the
 * day's numbers under it; a tap lists the whole day, newest first.
 */
@Composable
private fun TellStrip(banner: String?, log: List<Pair<String, String>>, numbers: String, isOpen: Boolean, onClick: () -> Unit) {
    val latest = log.lastOrNull()
    val title = latest?.let { tr("เลขาจัดใหม่ ", "Re-planned at ") + it.first + ": " + it.second } ?: banner ?: numbers
    val sub = when {
        latest != null -> banner ?: numbers
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
        if (log.isEmpty()) Text(tr("วันนี้เลขายังไม่ได้จัดใหม่", "Nothing re-planned today yet"), color = C.muted, fontSize = TS.caption)
    }
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

/** "เสร็จแล้ว N งาน": the tasks ticked today, one tap from seeing them. */
@Composable
private fun DoneFold(n: Int, isOpen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClick = onClick).padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(18.dp).clip(CircleShape).background(C.lime)) }
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
private fun BlockRow(title: String, right: String, reason: String?, faded: Boolean, bar: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 10.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
            Text(
                title, Modifier.weight(1f).padding(start = 10.dp), color = if (faded) C.faint else C.text,
                style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(right, Modifier.padding(start = 8.dp), color = if (faded) C.faint else C.muted, fontSize = TS.caption, maxLines = 1)
        }
        if (reason != null) Text(reason, Modifier.padding(start = 13.dp, bottom = 4.dp), color = C.muted, fontSize = TS.caption)
    }
}

/** Free time left clear on purpose. */
@Composable
private fun GapRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 2.dp, bottom = 2.dp).heightIn(min = 36.dp).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(C.control))
        Text(text, Modifier.weight(1f).padding(start = 10.dp), color = C.muted, fontSize = TS.body)
    }
}

/** The ring of a card that is a task, or a quiet dot for one that is not. */
@Composable
private fun Mark(item: DayFill.Item, vm: TaskViewModel) {
    val task = item.task
    if (task != null) TaskCheck(task, { vm.toggleDone(task) }, size = 20.dp, touch = 36.dp)
    else Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).clip(CircleShape).background(C.faint)) }
}

/** A task row's second line, led by its time: "16:45, เลย 9 วัน, 15 นาที, สปรินต์". Red only for late. */
@Composable
private fun Caption(p: DayFill.Placed, picked: DayFill.Pick?, today: LocalDate) {
    val item = p.item
    val late = lateWord(item.task, today)
    val kind = item.tag ?: when (item.kind) {
        DayFill.Kind.MINE -> tr("เวลาของตัวเอง", "Your own time")
        DayFill.Kind.WORK, DayFill.Kind.NET, DayFill.Kind.LIFE -> null
        else -> item.kind.label
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
                append(tr("รับแล้ว", "Taken"))
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

/** The quiet buttons at the foot (มีธุระ, เลขาถาม), and the errand's lengths. */
@Composable
private fun FootButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(C.raised).border(1.dp, C.control, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.text, fontSize = TS.body) }
}
