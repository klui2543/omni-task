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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import app.omnitask.model.Countdown
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.time.format.DateTimeFormatter
import app.omnitask.time.*

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DATE get() = DateTimeFormatter.ofPattern("EEEE d MMMM", TH)

@Composable
fun FocusScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, menu: @Composable () -> Unit, onAssistant: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    if (picking) FuturePicker(state, vm) { picking = false }
    if (reviewing) ReviewDeck(state, vm, onOpen) { reviewing = false }
    val today = state.today
    val brief = state.brief
    val events = state.todayEvents
    val plan = DayPlan.build(state.tasks, events, today)
    val openToday = plan.flatMap { it.items }.filterIsInstance<DayPlan.Item.TaskItem>().size
    // Done today counts anything ticked today that belonged to today (or was overdue).
    val doneToday = state.tasks.count { t -> t.done == today && (t.due ?: t.scheduled)?.let { it <= today } == true }
    val total = openToday + doneToday
    val overdue = brief.must.count { t -> t.due?.let { it < today } == true }
    val now = LocalDateTime.now()
    // Tonight's bedtime: the one picked for this evening, or the usual one from the profile.
    val eve = if (now.toLocalTime() >= LocalTime.of(18, 0)) today else today.minusDays(1)
    val bedtime = state.tonightBed?.takeIf { it.first == eve }?.second ?: state.profile.sleep
    val night = DayPlan.night(state.events, state.profile, now, bedtime)
    val free = DayPlan.freeMinutesLeft(events, now, state.profile.wake, state.profile.sleep)
    val blocked = Projects.blocked(state.tasks)
    val context = LocalContext.current
    val countdown = state.countdown?.let { title -> state.allTasks.filter { it.title == title }.maxByOrNull { if (it.isOpen) 1 else 0 } }
    var pickingCountdown by remember { mutableStateOf(false) }
    if (pickingCountdown) CountdownPicker(state, vm) { pickingCountdown = false }
    var hidden by remember { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = NavClearance + FabClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(LONG_DATE), color = C.muted, fontSize = TS.body)
                    Text(greeting(), style = MaterialTheme.typography.headlineSmall, color = C.text)
                }
                RoundButton(Ic.spark, tr("ผู้ช่วย", "Assistant"), C.accentSoft, C.accentText, onAssistant)
                Box(Modifier.width(8.dp))
                menu()
            }
        }

        item { CalendarConnect(state, vm) }

        item {
            Card {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(C.divider))
                if (night != null) {
                    // Tap to move tonight's bedtime; the usual one is in settings.
                    val color = when {
                        night.sleep < 5 * 60 -> C.red
                        night.sleep < 7 * 60 -> C.amber
                        else -> C.lime
                    }
                    // Times on top with the hours of sleep in a pill; why the alarm is early on a quiet line under them.
                    FooterRow(Ic.moon, onClick = { pickSystemTime(context, bedtime) { vm.setTonightBedtime(eve, it) } }) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr("นอน ${night.bedAt.format(HM)}   ตื่น ${night.wakeAt.format(HM)}", "Bed ${night.bedAt.format(HM)}   Up ${night.wakeAt.format(HM)}"),
                                    Modifier.weight(1f), color = C.text, fontSize = TS.caption, maxLines = 1,
                                )
                                Text(
                                    tr("ได้นอน ${hm(night.sleep)}", "${hm(night.sleep)} of sleep"),
                                    Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 2.dp),
                                    color = color, fontSize = TS.caption, fontWeight = FontWeight.Medium, maxLines = 1,
                                )
                            }
                            night.because?.let {
                                Text(
                                    tr("ตื่นก่อน ${it.title} 1 ชม.", "An hour before ${it.title}"),
                                    Modifier.padding(top = 3.dp), color = C.faint, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    // The pinned countdown: any task, counted to its date (and time on the day).
                    FooterRow(Ic.hourglass, onClick = { pickingCountdown = true }) {
                        if (countdown == null) {
                            Text(tr("นับถอยหลัง", "Countdown"), color = C.faint, fontSize = TS.caption)
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

        brief.warnings.filter { it !in hidden }.forEach { w -> item { Notice(w) { hidden = hidden + w } } }

        item { ReviewPrompt(state) { reviewing = true } }

        item {
            Card {
                if (plan.isEmpty()) {
                    Text(tr("วันนี้ยังไม่มีงานหรือนัด", "Nothing due or booked today"), Modifier.padding(16.dp), color = C.muted)
                }
                // The red now line sits before the first timed item still to come.
                var nowShown = plan.none { it.part != DayPlan.Part.LATE && it.part != DayPlan.Part.ANYTIME && it.items.any { i -> i.time != null } }
                val nowTime = now.toLocalTime()
                plan.forEach { section ->
                    PlanHead(section.part.label, if (section.part == DayPlan.Part.LATE) C.red else C.muted, section.items.count { it is DayPlan.Item.TaskItem })
                    section.items.forEach { item ->
                        val t = item.time
                        if (!nowShown && t != null && t > nowTime) {
                            NowLine(nowTime)
                            nowShown = true
                        }
                        when (item) {
                            is DayPlan.Item.TaskItem -> TimelineTask(item, state, vm, onOpen, item.task in blocked)
                            is DayPlan.Item.EventItem -> TimelineEvent(item)
                        }
                    }
                    if (!nowShown && section.part == DayPlan.Part.EVENING) {
                        NowLine(nowTime)
                        nowShown = true
                    }
                }
                Box(Modifier.height(8.dp))
            }
        }

        if (brief.suggestions.isNotEmpty()) {
            item {
                Card {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.spark, null, tint = C.accentText, modifier = Modifier.size(15.dp))
                        Text(tr("ข้อเสนอ", "Suggestions"), Modifier.padding(start = 6.dp), color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                    }
                    brief.suggestions.forEachIndexed { i, s ->
                        if (i > 0) Divider(start = 16.dp)
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showWaiting) Card(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxHeight().padding(14.dp)) {
                        CardHead(Ic.clock, C.amber, C.amberSoft, tr("คนรออยู่", "Waiting"), "${brief.waiting.size}")
                        if (brief.waiting.isEmpty()) {
                            Text("#รอ/" + tr("ชื่อ", "name"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
                        }
                        brief.waiting.forEach { t ->
                            val age = Focus.ageDays(t, today)
                            Column(Modifier.padding(top = 10.dp).clickable { onOpen(t) }) {
                                Text(t.title, color = C.text, fontSize = TS.body, maxLines = 2)
                                Pill(
                                    (Focus.waitingFor(t)?.let { "$it " } ?: "") + (age?.let { tr("รอ $it วัน", "waiting $it days") } ?: tr("รออยู่", "waiting")),
                                    if ((age ?: 0) >= 7) C.redSoft else C.amberSoft, if ((age ?: 0) >= 7) C.red else C.amber,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
                if (showFuture) Card(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxHeight().padding(14.dp)) {
                        CardHead(Ic.up, C.accentText, C.accentSoft, tr("ลงทุนอนาคต", "Future"), null)
                        if (brief.future.isEmpty()) {
                            Text(tr("ยังไม่ได้เลือก", "None picked"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
                        }
                        brief.future.forEach { t ->
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(t.title, Modifier.clickable { onOpen(t) }, color = C.text, fontSize = TS.body, maxLines = 3)
                                val sub = listOfNotNull(Focus.ageDays(t, today)?.let { tr("$it วัน", "$it days") })
                                if (sub.isNotEmpty()) Text(sub.joinToString(", "), color = C.muted, fontSize = TS.caption, maxLines = 2)
                                SmallButton(tr("ข้าม", "Skip"), filled = false, modifier = Modifier.padding(top = 8.dp)) { vm.skipFuture(t) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(C.divider))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr("เลือกงาน", "Pick"),
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = true }.padding(vertical = 4.dp),
                                color = C.accentText, fontSize = TS.caption, maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Stepper(state.futureCount, { vm.setFutureCount((state.futureCount + it).coerceIn(1, 5)) })
                        }
                    }
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
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        ProgressRing(if (total == 0) 0f else done.toFloat() / total, 76.dp, 7.dp, C.accent)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$total", color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(tr("เสร็จแล้ว", "done"), color = C.muted, fontSize = TS.micro)
        }
    }
}

/** The line under the summary: tonight's sleep, or the pinned countdown during the day. */
@Composable
private fun FooterRow(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.accentText, modifier = Modifier.padding(end = 8.dp).size(15.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.padding(start = 6.dp).size(12.dp))
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = fg, modifier = Modifier.size(19.dp))
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
        Box(Modifier.size(28.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Text(title, Modifier.weight(1f).padding(start = 8.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        if (count != null) Text(count, color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
    }
}

/** A part of the day: its name, a hairline, and how many tasks it holds. */
@Composable
private fun PlanHead(title: String, color: Color, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(C.divider))
        if (count > 0) Text("$count", color = C.muted, fontSize = TS.caption)
    }
}

@Composable
private fun NowLine(now: LocalTime) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(now.format(HM), Modifier.width(TIME_W), color = C.red, fontSize = TS.micro, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(7.dp).clip(CircleShape).background(C.red))
        Box(Modifier.weight(1f).height(1.5.dp).background(C.red.copy(alpha = 0.7f)))
    }
}

/** The time column every plan row starts with, so titles line up. */
private val TIME_W = 46.dp

@Composable
private fun TimelineTask(item: DayPlan.Item.TaskItem, state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, isBlocked: Boolean) {
    val t = item.task
    val today = state.today
    val late = t.isLate(today)
    Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Text(item.time?.format(HM) ?: "", Modifier.width(TIME_W).padding(top = 3.dp), color = C.muted, fontSize = TS.caption)
        TaskCheck(t, { vm.toggleDone(t) }, size = 22.dp, touch = 26.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(t.title, color = if (isBlocked) C.muted else C.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
            val lead = when {
                late -> t.due?.let { tr("เลย ${ChronoUnit.DAYS.between(it, today)} วัน", "${ChronoUnit.DAYS.between(it, today)} days late") }
                else -> null
            }
            val sub = listOfNotNull(
                Projects.projectOf(t),
                state.progressOf(t)?.let { (d, n) -> "$d/$n" },
            )
            Row(Modifier.padding(top = 1.dp)) {
                if (lead != null) Text(lead, color = if (late) C.red else C.text2, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                if (sub.isNotEmpty()) Text((if (lead != null) ", " else "") + sub.joinToString(", "), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TimelineEvent(item: DayPlan.Item.EventItem) {
    val e = item.event
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.Top) {
        Text(item.time?.format(HM) ?: "", Modifier.width(TIME_W).padding(top = 9.dp), color = C.muted, fontSize = TS.caption)
        Row(Modifier.weight(1f).height(IntrinsicSize.Min).clip(RoundedCornerShape(8.dp)).background(C.tealSoft).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(C.teal))
            Column(Modifier.padding(start = 10.dp)) {
                Text(e.title, color = C.eventText, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    (if (e.allDay) tr("ทั้งวัน", "All day") else tr("${e.begin.format(HM)} ถึง ${e.end.format(HM)}", "${e.begin.format(HM)} to ${e.end.format(HM)}")) + ", Google Calendar",
                    color = C.tealText, fontSize = TS.caption,
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(C.radius)).background(C.amberSoft).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(C.amber.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Ic.bell, null, tint = C.amber, modifier = Modifier.size(16.dp))
        }
        Text(text, Modifier.weight(1f).padding(start = 10.dp, top = 4.dp), color = C.text, fontSize = TS.body)
        Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
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
