package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.TaskKind
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import app.omnitask.model.Countdown
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.time.format.DateTimeFormatter
import app.omnitask.time.*

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DATE get() = DateTimeFormatter.ofPattern("EEEE d MMMM", TH)

@Composable
fun FocusScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, menu: @Composable () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    if (picking) FuturePicker(state, vm) { picking = false }
    if (reviewing) ReviewDeck(state, vm, onOpen) { reviewing = false }
    val today = state.today
    val brief = state.brief
    val events = state.todayEvents
    // Tasks already ticked today stay in the day's timeline, greyed, so the day reads as a whole.
    val plan = DayPlan.build(state.tasks, events, today, includeDone = true)
    val openToday = plan.flatMap { it.items }.count { it is DayPlan.Item.TaskItem && it.task.isOpen }
    // Done today counts anything ticked today that belonged to today (or was overdue).
    val doneToday = state.tasks.count { t -> t.done == today && (t.due ?: t.scheduled)?.let { it <= today } == true }
    val total = openToday + doneToday
    val overdue = brief.must.count { t -> t.due?.let { it < today } == true }
    // The clock the timeline follows: it moves on at every minute, so the now line and the running event keep up with real time.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            now = LocalDateTime.now()
        }
    }
    // Tonight's bedtime: the one picked for this evening, or the usual one from the profile.
    val eve = if (now.toLocalTime() >= LocalTime.of(18, 0)) today else today.minusDays(1)
    val bedtime = state.tonightBed?.takeIf { it.first == eve }?.second ?: state.profile.sleep
    val pickedWake = state.tonightWake?.takeIf { it.first == eve }?.second
    val night = DayPlan.night(state.events, state.profile, now, bedtime, pickedWake)
    val free = DayPlan.freeMinutesLeft(events, now, state.profile.wake, state.profile.sleep)
    val blocked = Projects.blocked(state.tasks)
    val context = LocalContext.current
    val countdown = state.countdown?.let { title -> state.allTasks.filter { it.title == title }.maxByOrNull { if (it.isOpen) 1 else 0 } }
    var pickingCountdown by remember { mutableStateOf(false) }
    if (pickingCountdown) CountdownPicker(state, vm) { pickingCountdown = false }
    var hidden by remember { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = NavClearance + FabClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(LONG_DATE), color = C.muted, fontSize = TS.body)
                    Text(greeting(), style = MaterialTheme.typography.headlineSmall, color = C.text)
                }
                // The assistant is the floating button now; only the wide layout's menu is left up here.
                menu()
            }
        }

        // Items that draw nothing would still take a gap in the column, so they are only added when they show.
        if (calendarConnectShows(state)) item { CalendarConnect(state, vm) }

        item {
            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    DoneRing(doneToday, total)
                    Row(Modifier.weight(1f).padding(start = 12.dp).height(IntrinsicSize.Min)) {
                        Stat("$overdue", tr("เลยกำหนด", "Overdue"), if (overdue > 0) C.red else C.text, Modifier.weight(1f))
                        Stat("${events.count { !it.allDay }}", tr("นัดวันนี้", "Events today"), C.text, Modifier.weight(1f))
                        if (night != null) {
                            Stat(hm(night.toBed), tr("ก่อนนอน", "Until bed"), if (night.toBed < 30) C.amber else C.text, Modifier.weight(1f))
                        } else {
                            Stat(hours(free), tr("ว่างเหลือ", "Free left"), C.lime, Modifier.weight(1f))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(C.divider))
                if (night != null) {
                    // Tap to move tonight's bedtime; the usual one is in settings.
                    val color = when {
                        night.sleep < 5 * 60 -> C.red
                        night.sleep < 7 * 60 -> C.amber
                        else -> C.lime
                    }
                    // Bed and wake as two tiles to tap, the hours of sleep beside them, why the wake time is what it is,
                    // then the alarm on the watch.
                    val wake = night.wakeAt.toLocalTime()
                    val alarm = state.alarmSet?.takeIf { it.first == eve }
                    // Set for tonight and the wake time moved (picked again, or a new morning event): send the new one.
                    LaunchedEffect(alarm, night.wakeAt) {
                        if (alarm != null && alarm.second != wake) vm.setWakeAlarm(eve, night.wakeAt, night.sleep)
                    }
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.moon, null, tint = C.accentText, modifier = Modifier.size(15.dp))
                            Text(tr("คืนนี้", "Tonight"), Modifier.weight(1f).padding(start = 8.dp), color = C.muted, fontSize = TS.caption)
                            Text(
                                tr("ได้นอน ${hm(night.sleep)}", "${hm(night.sleep)} of sleep"),
                                Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 2.dp),
                                color = color, fontSize = TS.caption, fontWeight = FontWeight.Medium, maxLines = 1,
                            )
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SleepTile(tr("นอน", "Bed"), night.bedAt.format(HM), Modifier.weight(1f)) {
                                pickSystemTime(context, bedtime) { vm.setTonightBedtime(eve, it) }
                            }
                            SleepTile(tr("ตื่น", "Up"), night.wakeAt.format(HM), Modifier.weight(1f)) {
                                pickSystemTime(context, wake) { vm.setTonightWake(eve, it) }
                            }
                        }
                        // Why the wake time is what it is: an event in the morning, or picked for tonight.
                        val why = when {
                            pickedWake != null -> tr("ตั้งเองสำหรับคืนนี้", "Picked for tonight")
                            night.because != null -> tr("เผื่อ 1 ชม. ก่อน ${night.because!!.begin.format(HM)}: ${night.because!!.title}", "An hour before ${night.because!!.begin.format(HM)}: ${night.because!!.title}")
                            else -> null
                        }
                        if (why != null) {
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(why, Modifier.weight(1f), color = C.faint, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (pickedWake != null) {
                                    Text(
                                        tr("ใช้เวลาปกติ", "Use usual"),
                                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.setTonightWake(eve, null) }.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = C.accentText, fontSize = TS.micro,
                                    )
                                }
                            }
                        }
                        val on = alarm != null && !state.alarmFailed
                        val tint = when { on -> C.lime; state.alarmFailed -> C.red; else -> C.accentText }
                        Row(
                            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (on) C.lime.copy(alpha = 0.12f) else C.raised)
                                .clickable { vm.setWakeAlarm(eve, if (on) null else night.wakeAt, night.sleep) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (on) Ic.check else Ic.watch, null, tint = tint, modifier = Modifier.size(15.dp))
                            Text(
                                when {
                                    state.alarmFailed -> tr("ส่งไม่ถึงนาฬิกา แตะเพื่อลองใหม่", "Did not reach the watch, tap to retry")
                                    on -> tr("นาฬิกาจะปลุก ${night.wakeAt.format(HM)}", "The watch rings at ${night.wakeAt.format(HM)}")
                                    else -> tr("ปลุกที่นาฬิกา ${night.wakeAt.format(HM)}", "Ring the watch at ${night.wakeAt.format(HM)}")
                                },
                                Modifier.padding(start = 8.dp), color = if (on || state.alarmFailed) tint else C.text,
                                fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    // The pinned countdown: any task, counted to its date (and time on the day).
                    FooterRow(Ic.hourglass, onClick = { pickingCountdown = true }) {
                        if (countdown == null) {
                            Text(tr("ตั้งนับถอยหลังถึงงานสำคัญ", "Count down to an important task"), color = C.faint, fontSize = TS.caption)
                        } else {
                            Text(countdown.title, Modifier.weight(1f, fill = false), color = C.text, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "  " + (Countdown.text(countdown, now) ?: tr("ไม่มีวันครบกำหนด", "no date")),
                                color = if (countdown.due?.let { it < today } == true) C.red else C.accentText, fontSize = TS.caption, fontWeight = FontWeight.Medium, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // A sync clash left a second copy of a note: say so, since its tasks are left out until it is sorted.
        state.conflicts.map { path ->
            tr(
                "มีไฟล์ซ้ำจากการ sync: ${path.substringAfterLast('/')}\nแอปไม่อ่านไฟล์นี้ ดูว่ามีอะไรที่ไฟล์หลักไม่มีไหม แล้วลบทิ้ง",
                "A sync clash left a copy: ${path.substringAfterLast('/')}\nThe app skips it. Check it for anything the main note lacks, then delete it.",
            )
        }.filter { it !in hidden }.forEach { w -> item { Notice(w) { hidden = hidden + w } } }
        brief.warnings.filter { it !in hidden }.forEach { w -> item { Notice(w) { hidden = hidden + w } } }

        if (state.toReview.isNotEmpty()) item { ReviewPrompt(state) { reviewing = true } }

        // Overdue work is not part of the day's timeline: it sits in its own card below it.
        val overdueSection = plan.firstOrNull { it.part == DayPlan.Part.LATE }
        val daySections = plan.filter { it.part != DayPlan.Part.LATE }
        if (daySections.isNotEmpty() || overdueSection == null) item {
            Card {
                Box(Modifier.height(6.dp))
                if (daySections.isEmpty()) {
                    Text(tr("วันนี้ยังไม่มีงานหรือนัด", "Nothing due or booked today"), Modifier.padding(16.dp), color = C.muted)
                }
                // The red now line sits before the first timed item still to come; while an event is running it
                // crosses that event's card instead, at how far into it the clock is.
                val nowTime = now.toLocalTime()
                val inEvent = daySections.any { sec -> sec.items.any { it is DayPlan.Item.EventItem && DayPlan.phase(it.event, now) == DayPlan.Phase.ACTIVE } }
                var nowShown = inEvent || daySections.none { it.part != DayPlan.Part.ANYTIME && it.items.any { i -> i.time != null } }
                daySections.forEach { section ->
                    if (!nowShown && section.part == DayPlan.Part.ANYTIME) {
                        NowLine(nowTime)
                        nowShown = true
                    }
                    PlanHead(section.part.label, C.muted, section.items.count { it is DayPlan.Item.TaskItem && it.task.isOpen })
                    section.items.forEach { item ->
                        val t = item.time
                        if (!nowShown && t != null && t > nowTime) {
                            NowLine(nowTime)
                            nowShown = true
                        }
                        when (item) {
                            is DayPlan.Item.TaskItem -> TimelineTask(item, state, vm, onOpen, item.task in blocked)
                            is DayPlan.Item.EventItem -> TimelineEvent(item, now)
                        }
                    }
                    if (!nowShown && section.part == DayPlan.Part.EVENING) {
                        NowLine(nowTime)
                        nowShown = true
                    }
                }
                if (!nowShown && daySections.isNotEmpty()) NowLine(nowTime)
                Box(Modifier.height(12.dp))
            }
        }

        if (overdueSection != null) item {
            Card {
                Box(Modifier.height(6.dp))
                PlanHead(overdueSection.part.label, C.red, overdueSection.items.count { it is DayPlan.Item.TaskItem && it.task.isOpen })
                overdueSection.items.forEach { item ->
                    if (item is DayPlan.Item.TaskItem) TimelineTask(item, state, vm, onOpen, item.task in blocked)
                }
                Box(Modifier.height(12.dp))
            }
        }

        if (brief.suggestions.isNotEmpty()) {
            item {
                Card {
                    Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.spark, null, tint = C.accentText, modifier = Modifier.size(15.dp))
                        Text(tr("ข้อเสนอ", "Suggestions"), Modifier.padding(start = 6.dp), color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                    }
                    brief.suggestions.forEachIndexed { i, s ->
                        if (i > 0) Divider(start = 12.dp)
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(s.task.title, Modifier.clickable { onOpen(s.task) }, color = C.text, fontSize = TS.body)
                            Text(s.text, color = C.muted, fontSize = TS.caption)
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                                SmallButton(tr("ไม่เอา", "Dismiss"), filled = false) { vm.dismissSuggestion(s) }
                                SmallButton(tr("ตกลง", "Accept"), filled = true) { vm.acceptSuggestion(s) }
                            }
                        }
                    }
                    Box(Modifier.height(4.dp))
                }
            }
        }

        // Either card goes when its kind is hidden; the other then takes the row.
        val showWaiting = TaskKind.WAITING !in state.hiddenKinds
        val showFuture = TaskKind.FUTURE !in state.hiddenKinds
        if (showWaiting || showFuture) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Each card shows one task at a time: swipe for the next, or tap the head to see them all at once.
                if (showWaiting) PagedCard(
                    Ic.clock, C.amber, C.amberSoft, tr("คนรออยู่", "Waiting"), brief.waiting,
                    tr("ติด #รอ/ชื่อ ให้งานที่มีคนรอ", "Tag tasks people wait on with #รอ/name"), onOpen, Modifier.weight(1f),
                ) { t ->
                    val age = Focus.ageDays(t, today)
                    Text(t.title, color = C.text, fontSize = TS.body, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        (Focus.waitingFor(t)?.let { "$it " } ?: "") + (age?.let { tr("รอ $it วัน", "waiting $it days") } ?: tr("รออยู่", "waiting")),
                        Modifier.padding(top = 4.dp), color = if ((age ?: 0) >= 7) C.red else C.amber, fontSize = TS.caption, maxLines = 1,
                    )
                }
                if (showFuture) PagedCard(
                    Ic.up, C.accentText, C.accentSoft, tr("ลงทุนอนาคต", "Future"), brief.future,
                    tr("เลือกงานที่สำคัญต่ออนาคต แต่ไม่มีเดดไลน์", "Pick work that matters for the future but has no deadline"), onOpen, Modifier.weight(1f),
                    more = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr("+ เลือกงาน", "+ Pick"),
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = true }.padding(vertical = 4.dp),
                                color = C.accentText, fontSize = TS.caption, maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Stepper(state.futureCount, { vm.setFutureCount((state.futureCount + it).coerceIn(1, 5)) })
                        }
                    },
                ) { t ->
                    Text(t.title, color = C.text, fontSize = TS.body, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Focus.ageDays(t, today)?.let { tr("ค้าง $it วัน", "open $it days") } ?: "", Modifier.weight(1f), color = C.muted, fontSize = TS.caption, maxLines = 1)
                        Text(
                            tr("ข้ามวันนี้", "Skip today"),
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.skipFuture(t) }.padding(horizontal = 4.dp),
                            color = C.accentText, fontSize = TS.caption, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A small card that shows one of [items] at a time: swipe sideways for the next, the head counts "1/3", and tapping
 * the head opens all of them in a dialog in the middle of the screen. Tapping a task opens it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun <T : Task> PagedCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, soft: Color, title: String, list: List<T>, empty: String,
    onOpen: (Task) -> Unit, modifier: Modifier = Modifier, more: (@Composable () -> Unit)? = null, content: @Composable (T) -> Unit,
) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { list.size }
    var all by remember { mutableStateOf(false) }
    if (all) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { all = false },
            containerColor = C.raised,
            title = { Text(title + "  ${list.size}") },
            text = {
                Column {
                    if (list.isEmpty()) Text(empty, color = C.muted, fontSize = TS.body)
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(list.size) { i ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
                            Column(Modifier.fillMaxWidth().clickable { all = false; onOpen(list[i]) }.padding(vertical = 10.dp)) { content(list[i]) }
                        }
                    }
                    // Adding to the card lives here, so the card itself stays the same size as its neighbour.
                    if (more != null) {
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(C.divider))
                        Box(Modifier.padding(top = 8.dp)) { more() }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { all = false }) { Text(tr("ปิด", "Close"), color = C.accentText) } },
        )
    }
    Card(modifier) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = true }) {
                CardHead(icon, tint, soft, title, if (list.size > 1) "${pager.currentPage + 1}/${list.size}" else list.size.takeIf { it > 0 }?.toString())
            }
            if (list.isEmpty()) {
                // The same two lines and caption line a task takes, so an empty card is as tall as a full one.
                Column(Modifier.padding(top = 10.dp)) {
                    Text(empty, color = C.muted, fontSize = TS.body, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("", Modifier.padding(top = 4.dp), fontSize = TS.caption, minLines = 1)
                }
                Box(Modifier.padding(top = 10.dp).height(5.dp))
            } else {
                androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.padding(top = 10.dp).fillMaxWidth(), pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { i ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(list[i]) }) { content(list[i]) }
                }
                // The dots row is always there (empty for one task), so both cards keep one height.
                Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    if (list.size > 1) repeat(list.size) { i -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == pager.currentPage) C.text2 else C.control)) }
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("วันละ", "Per day"), Modifier.padding(end = 4.dp), color = C.muted, fontSize = TS.caption)
        StepDot("−") { onStep(-1) }
        Text("$value", Modifier.padding(horizontal = 6.dp), color = C.text, fontSize = TS.body)
        StepDot("+") { onStep(1) }
    }
}

@Composable
private fun StepDot(sign: String, onClick: () -> Unit) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(C.raised).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(sign, color = C.text2, fontSize = TS.body)
    }
}

/** One of the three numbers beside the ring, with a hairline before it. */
@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Row(modifier.fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.divider))
        Column(Modifier.padding(start = 10.dp)) {
            Text(value, fontSize = TS.stat, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, softWrap = false)
            Text(label, fontSize = TS.caption, color = C.muted, maxLines = 1, softWrap = false)
        }
    }
}

/** Today's done over all, the label inside the ring. */
@Composable
private fun DoneRing(done: Int, total: Int) {
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        ProgressRing(if (total == 0) 0f else done.toFloat() / total, 64.dp, 6.dp, C.accent)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$total", color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(tr("เสร็จแล้ว", "done"), color = C.muted, fontSize = TS.micro)
        }
    }
}

/** One of tonight's two times, big enough to tap. */
@Composable
private fun SleepTile(label: String, time: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.raised).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = C.muted, fontSize = TS.micro)
        Text(time, color = C.text, fontSize = TS.title, fontWeight = FontWeight.Medium)
    }
}

/** The line under the summary: tonight's sleep, or the pinned countdown during the day. */
@Composable
private fun FooterRow(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.accentText, modifier = Modifier.padding(end = 8.dp).size(15.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.padding(start = 6.dp).size(12.dp))
    }
}

/** Small text buttons for the cards: a filled one for yes, a quiet one for no. */
@Composable
private fun SmallButton(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (filled) C.accent else C.raised).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) C.onAccent else C.text2, fontSize = TS.caption, fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal, maxLines = 1) }
}

/** A card's title row: an icon in a soft circle, the name, and a count. */
@Composable
private fun CardHead(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, soft: Color, title: String, count: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Text(title, Modifier.weight(1f).padding(start = 8.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        if (count != null) Text(count, color = C.muted, fontSize = TS.caption)
    }
}

/** A part of the day: its name, a hairline, and how many tasks it holds. */
@Composable
private fun PlanHead(title: String, color: Color, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(C.divider))
        if (count > 0) Text("$count", color = C.muted, fontSize = TS.caption)
    }
}

@Composable
private fun NowLine(now: LocalTime) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(C.red))
        Text(now.format(HM), Modifier.padding(horizontal = 6.dp), color = C.red, fontSize = TS.micro, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.5.dp).background(C.red.copy(alpha = 0.7f)))
    }
}

@Composable
private fun TimelineTask(item: DayPlan.Item.TaskItem, state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, isBlocked: Boolean) {
    val t = item.task
    val today = state.today
    val late = t.isLate(today)
    // A task already ticked off stays on the timeline, greyed out.
    val done = t.status == Status.DONE
    Row(
        Modifier.fillMaxWidth().alpha(if (done) 0.55f else 1f).clickable { onOpen(t) }.padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TaskCheck(t, { vm.toggleDone(t) }, size = 22.dp, touch = 36.dp)
        Column(Modifier.weight(1f).heightIn(min = 36.dp).padding(start = 2.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.title, Modifier.weight(1f, fill = false), color = if (isBlocked || done) C.muted else C.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                PriorityDot(t)
            }
            // Every row here is today's, so "due today" would only repeat. A finished task is one line.
            val lateText = if (late && !done) t.due?.let { tr("เลย ${ChronoUnit.DAYS.between(it, today)} วัน", "${ChronoUnit.DAYS.between(it, today)} days late") } else null
            val rest = if (done) "" else listOfNotNull(
                if (t.status == Status.IN_PROGRESS) tr("กำลังทำ", "In progress") else null,
                Projects.projectOf(t),
                state.progressOf(t)?.let { (d, n) -> tr("งานย่อย $d/$n", "Subtasks $d/$n") },
            ).joinToString(", ")
            val clock = if (done) null else item.time
            if (clock != null || lateText != null || rest.isNotEmpty()) {
                Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The time leads the line as a small clock label, as the reminder does on the Tasks page.
                    clock?.let {
                        Icon(Ic.clock, null, tint = C.muted, modifier = Modifier.size(12.dp))
                        Text(it.format(HM) + if (lateText != null || rest.isNotEmpty()) ", " else "", Modifier.padding(start = 3.dp), color = C.muted, fontSize = TS.caption)
                    }
                    if (lateText != null) Text(lateText, color = C.red, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                    if (rest.isNotEmpty()) Text((if (lateText != null) ", " else "") + rest, color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * A calendar event on the timeline against the clock: greyed once it has ended, and while it runs the red now line
 * crosses the card at how far into the event it is, with the part already gone tinted darker.
 */
@Composable
private fun TimelineEvent(item: DayPlan.Item.EventItem, now: LocalDateTime) {
    val e = item.event
    val phase = DayPlan.phase(e, now)
    val past = phase == DayPlan.Phase.PAST
    val running = phase == DayPlan.Phase.ACTIVE
    val fraction = if (running) DayPlan.progress(e, now) else 0f
    val bg = if (past && !C.calm) C.raised else C.tealSoft
    val spent = C.teal.copy(alpha = 0.16f)
    val red = C.red
    // Every event here is from the phone's calendar and on today's page, so neither needs saying.
    val range = if (e.allDay) tr("ทั้งวัน", "All day") else tr("${e.begin.format(HM)} ถึง ${e.end.format(HM)}", "${e.begin.format(HM)} to ${e.end.format(HM)}")
    val left = ChronoUnit.MINUTES.between(now, e.end)
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = if (running) 4.dp else 2.dp, bottom = if (running) 4.dp else 2.dp), verticalAlignment = Alignment.Top) {
        Row(
            Modifier.weight(1f).heightIn(min = if (running) 0.dp else 36.dp).height(IntrinsicSize.Min).clip(if (C.calm) RectangleShape else RoundedCornerShape(8.dp)).background(bg)
                .drawBehind {
                    if (running) {
                        val y = size.height * fraction
                        drawRect(spent, size = androidx.compose.ui.geometry.Size(size.width, y))
                        drawLine(red, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                        drawCircle(red, 3.5.dp.toPx(), Offset(3.5.dp.toPx(), y))
                    }
                }
                .padding(start = if (C.calm) 0.dp else 10.dp, end = 10.dp, top = if (running) 8.dp else 4.dp, bottom = if (running) 8.dp else 4.dp),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(if (past) C.faint else C.teal))
            if (running) {
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(e.title, color = C.eventText, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(range + tr(", เหลือ $left นาที", ", $left min left"), color = C.tealText, fontSize = TS.caption)
                }
            } else {
                Row(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.title, Modifier.weight(1f), color = if (past) C.faint else C.eventText, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(range, Modifier.padding(start = 8.dp), color = if (past) C.faint else C.tealText, fontSize = TS.caption, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(C.radius)).background(C.amberSoft).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(C.amber.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Ic.bell, null, tint = C.amber, modifier = Modifier.size(16.dp))
        }
        Text(text, Modifier.weight(1f).padding(start = 8.dp, top = 3.dp), color = C.text, fontSize = TS.body)
        Box(Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Ic.close, tr("ซ่อน", "Hide"), tint = C.muted, modifier = Modifier.size(13.dp))
        }
    }
}

/** Any open task with a date can be the countdown, today's or months away. */
@Composable
private fun CountdownPicker(state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val now = LocalDateTime.now()
    val candidates = state.allTasks.filter { it.isOpen && Countdown.target(it) != null }
        .filter { text.isBlank() || it.title.contains(text.trim(), ignoreCase = true) }
        .sortedBy { Countdown.target(it) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("นับถอยหลังถึงงานไหน", "Count down to which task")) },
        text = {
            Column {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(tr("ค้นหางาน", "Search tasks"), color = C.faint, fontSize = TS.body)
                            inner()
                        }
                    },
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 360.dp)) {
                    items(candidates.size) { i ->
                        val t = candidates[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.setCountdown(t); onDismiss() }.padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.title, Modifier.weight(1f), color = if (t.title == state.countdown) C.accentText else C.text, fontSize = TS.body, maxLines = 2)
                            Text(Countdown.text(t, now) ?: "", Modifier.padding(start = 8.dp), color = C.muted, fontSize = TS.caption)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) } },
        dismissButton = {
            if (state.countdown != null) androidx.compose.material3.TextButton(onClick = { vm.setCountdown(null); onDismiss() }) { Text(tr("เลิกนับ", "Clear"), color = C.red) }
        },
    )
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> tr("สวัสดีตอนเช้า", "Good morning")
        h < 17 -> tr("สวัสดีตอนบ่าย", "Good afternoon")
        else -> tr("สวัสดีตอนเย็น", "Good evening")
    }
}

private fun hm(minutes: Long): String = tr("%d:%02d ชม.", "%d:%02d h").format(minutes / 60, minutes % 60)

private fun hours(minutes: Long): String =
    if (minutes >= 60) tr("${minutes / 60} ชม.", "${minutes / 60} h") else tr("$minutes นาที", "$minutes min")
