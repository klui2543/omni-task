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
import app.omnitask.data.CalendarReader
import app.omnitask.model.DayPlan
import app.omnitask.model.NextUp
import app.omnitask.model.Focus
import app.omnitask.model.TaskKind
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import app.omnitask.model.Countdown
import app.omnitask.model.StuckLog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.time.format.DateTimeFormatter
import app.omnitask.time.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DATE get() = DateTimeFormatter.ofPattern("EEEE d MMMM", TH)

@Composable
fun FocusScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, menu: @Composable () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    val voice by vm.voice.collectAsStateWithLifecycle()
    if (picking) FuturePicker(state, vm) { picking = false }
    if (reviewing) ReviewDeck(state, vm, onOpen) { reviewing = false }
    // The calendar choice, opened from an event (its calendar pointed out) or from the line under the day.
    var calendarsOpen by remember { mutableStateOf(false) }
    var calendarsFrom by remember { mutableStateOf<CalendarEvent?>(null) }
    if (calendarsOpen) {
        CalendarsDialog(CalendarChoices(state.calendars, state.hiddenCalendars, vm::setCalendarShown, vm::calendarChanged), calendarsFrom) { calendarsOpen = false }
    }
    val today = state.today
    val brief = state.brief
    val events = state.todayEvents
    // Tasks already ticked today stay in the day's timeline, greyed, so the day reads as a whole.
    val plan = DayPlan.build(state.tasks, events, today, includeDone = true)
    val openToday = plan.flatMap { it.items }.count { it is DayPlan.Item.TaskItem && it.task.isOpen }
    // Done today counts anything ticked today that belonged to today (or was overdue).
    val doneToday = state.tasks.count { t -> t.done == today && (t.due ?: t.scheduled)?.let { it <= today } == true }
    val total = openToday + doneToday
    // Read off the plan, so the numbers match the overdue card and the timeline below.
    val overdue = DayPlan.lateCount(plan)
    // The new look keeps red for real deadlines (📅) gone by; a slipped own target (⏳) alone is not red.
    val realLate = state.tasks.any { it.isOpen && it.due.let { d -> d != null && d < today } }
    val eventCount = DayPlan.eventCount(plan)
    // The clock the timeline follows: it moves on at every minute, so the now line and the running event keep up with real time.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            now = LocalDateTime.now()
        }
    }
    // The night the sleep card plans, all day long: last night until its wake time, then tonight's.
    // Bed and wake are the ones picked for that evening, or the usual ones from the profile.
    val yesterday = today.minusDays(1)
    fun bedOf(evening: LocalDate) = state.tonightBed?.takeIf { it.first == evening }?.second ?: state.profile.sleep
    fun wakeOf(evening: LocalDate) = state.tonightWake?.takeIf { it.first == evening }?.second
    val clock = now.toLocalTime()
    val eve = if (clock < LocalTime.of(18, 0) && clock < (wakeOf(yesterday) ?: state.profile.wake)) yesterday else today
    // From 18:00 until the wake time it is night: the card counts down to bed instead of the free time left.
    val isNight = eve == yesterday || clock >= LocalTime.of(18, 0)
    val bedtime = bedOf(eve)
    val pickedWake = wakeOf(eve)
    val night = DayPlan.night(state.events, state.profile, now, bedtime, pickedWake, evening = eve)
    // Up until noon after a night: how long it gave, from its planned bedtime.
    val lastNight = if (eve == today && clock < LocalTime.NOON) {
        val bed = bedOf(yesterday)
        val at = if (bed >= LocalTime.NOON) yesterday.atTime(bed) else today.atTime(bed)
        DayPlan.night(state.events, state.profile, at, bed, wakeOf(yesterday), evening = yesterday)?.sleep
    } else null
    val free = DayPlan.freeMinutesLeft(events, now, state.profile.wake, state.profile.sleep)
    val blocked = Projects.blocked(state.tasks)
    val context = LocalContext.current
    val countdowns = Countdown.shown(state.countdowns, state.allTasks, CalendarEvent.distinct(state.events + state.pinnedEvents + state.aheadEvents), now)
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

        // The new look (Settings switch): one task to do next, at the top.
        if (NewLook.on) {
            NextUp.ranked(state.tasks, today, NewLook.skippedFor(today)).firstOrNull()?.let { pick ->
                item(key = "next-up") {
                    NextUpCard(
                        pick, if (free > 0) hours(free) else null, onOpen,
                        onStart = { vm.setStatus(pick.task, Status.IN_PROGRESS) },
                        onDone = { vm.toggleDone(pick.task) },
                        onSkip = { NewLook.skip(context, pick.task.key, today) },
                    )
                }
            }
        }

        // Recordings not written down yet (no network, or the check was closed): try again or throw away.
        if (voice.waiting.isNotEmpty() && voice.recording == null && !voice.busy && voice.review == null) {
            item { VoiceWaitingCard(voice, vm) }
        }

        // The secretary stopped writing Claude log (over a day since its last round): say so until it is back or hidden.
        val nowMs = now.let { System.currentTimeMillis() }
        val secretaryLast = state.ai.secretaryLast
        if (secretaryLast != null && Secretary.warns(secretaryLast, state.ai.secretaryHiddenUntil, nowMs)) {
            item { SecretaryWarning(secretaryLast, nowMs, vm::hideSecretaryWarning) }
        }

        // Items that draw nothing would still take a gap in the column, so they are only added when they show.
        if (calendarConnectShows(state)) item { CalendarConnect(state, vm) }

        item {
            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    DoneRing(doneToday, total)
                    Row(Modifier.weight(1f).padding(start = 12.dp).height(IntrinsicSize.Min)) {
                        Stat("$overdue", tr("ค้างอยู่", "Overdue"), if (overdue > 0 && (!NewLook.on || realLate)) C.red else C.text, Modifier.weight(1f))
                        Stat("$eventCount", tr("นัดวันนี้", "Events today"), C.text, Modifier.weight(1f))
                        if (night != null && isNight) {
                            Stat(hm(night.toBed), tr("ก่อนนอน", "Until bed"), if (night.toBed < 30) C.amber else C.text, Modifier.weight(1f))
                        } else {
                            Stat(hours(free), tr("ว่างเหลือ", "Free left"), C.lime, Modifier.weight(1f))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(C.divider))
                // Tonight's bed and wake times are no longer shown or set here: the free time above works them out
                // from the day's events and the owner's usual rhythm.
                run {
                    // The pinned countdowns, soonest first: a ring for a task, the timeline's bar for an event. Any of
                    // them opens the picker.
                    if (countdowns.isEmpty()) {
                        FooterRow(onClick = { pickingCountdown = true }) {
                            Text(tr("ตั้งนับถอยหลังถึงงานหรือนัดสำคัญ", "Count down to an important task or event"), color = C.faint, fontSize = TS.caption)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().clickable { pickingCountdown = true }.padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("นับถอยหลัง", "Countdowns"), Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
                                Text(tr("แก้ไข", "Edit"), color = C.accentText, fontSize = TS.caption)
                                Icon(Ic.next, null, tint = C.faint, modifier = Modifier.padding(start = 6.dp).size(12.dp))
                            }
                            countdowns.forEach { c ->
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CountdownMark(c.isEvent)
                                    Text(c.title, Modifier.weight(1f).padding(start = 10.dp), color = C.text, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        c.text, Modifier.padding(start = 8.dp), color = if (c.late) C.red else C.accentText,
                                        fontSize = TS.caption, fontWeight = FontWeight.Medium, maxLines = 1,
                                    )
                                }
                            }
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
                            is DayPlan.Item.EventItem -> TimelineEvent(item, now) { calendarsFrom = item.event; calendarsOpen = true }
                        }
                    }
                    if (!nowShown && section.part == DayPlan.Part.EVENING) {
                        NowLine(nowTime)
                        nowShown = true
                    }
                }
                if (!nowShown && daySections.isNotEmpty()) NowLine(nowTime)
                // Which calendars the day shows, one tap from changing it.
                val phoneCalendars = state.calendars.filter { it.visible }
                if (state.calendarAccess == true && phoneCalendars.isNotEmpty()) {
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(C.divider))
                    FooterRow(onClick = { calendarsFrom = null; calendarsOpen = true }) {
                        val on = phoneCalendars.count { !CalendarReader.isHidden(it.id, state.hiddenCalendars) }
                        Text(tr("ปฏิทินที่แสดง $on จาก ${phoneCalendars.size}", "Calendars shown: $on of ${phoneCalendars.size}"), color = C.faint, fontSize = TS.caption)
                    }
                } else {
                    Box(Modifier.height(12.dp))
                }
            }
        }

        if (overdueSection != null) item {
            Card {
                Box(Modifier.height(6.dp))
                PlanHead(overdueSection.part.label, if (!NewLook.on || realLate) C.red else C.muted, overdueSection.items.count { it is DayPlan.Item.TaskItem && it.task.isOpen })
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
                        Text(tr("ข้อเสนอ", "Suggestions"), color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.Medium)
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
                    C.amber, tr("คนรออยู่", "Waiting"), brief.waiting,
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
                    C.accentText, tr("ลงทุนอนาคต", "Future"), brief.future,
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
    tint: Color, title: String, list: List<T>, empty: String,
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
                CardHead(tint, title, if (list.size > 1) "${pager.currentPage + 1}/${list.size}" else list.size.takeIf { it > 0 }?.toString())
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

/** The line under the summary: tonight's sleep, or the pinned countdown during the day. */
@Composable
private fun FooterRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** A card's title row: the name in the card's colour, and a count. */
@Composable
private fun CardHead(tint: Color, title: String, count: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = tint, fontSize = TS.body, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
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
    var explaining by remember(t.key) { mutableStateOf(false) }
    // Late or long-open: how it has moved (a bar a week) and when it last did, one tap from writing why.
    val stuck = !done && StuckLog.matters(t, today)
    val subs = if (stuck) state.subtasksOf(t) else emptyList()
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
                if (stuck) stuckStatus(t, subs, today) else null,
                if (t.status == Status.IN_PROGRESS) tr("กำลังทำ", "In progress") else null,
                Projects.projectOf(t),
                state.progressOf(t)?.let { (d, n) -> tr("งานย่อย $d/$n", "Subtasks $d/$n") },
            ).joinToString(", ")
            val clock = if (done) null else item.time
            // The new look: a slipped own target (⏳, no 📅 gone by) says so quietly.
            val targetText = if (NewLook.on && !done && lateText == null && NextUp.onlyTargetLate(t, today)) {
                t.scheduled?.let { tr("เลยเป้า ${ChronoUnit.DAYS.between(it, today)} วัน", "target ${ChronoUnit.DAYS.between(it, today)} days ago") }
            } else null
            if (clock != null || lateText != null || targetText != null || rest.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 1.dp).then(if (stuck) Modifier.clip(RoundedCornerShape(6.dp)).clickable { explaining = true } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The time leads the line.
                    clock?.let {
                        Text(it.format(HM) + if (lateText != null || rest.isNotEmpty()) ", " else "", color = C.muted, fontSize = TS.caption)
                    }
                    if (lateText != null) Text(lateText, color = C.red, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                    if (targetText != null) Text(targetText, color = C.muted, fontSize = TS.caption)
                    if (rest.isNotEmpty()) Text((if (lateText != null || targetText != null) ", " else "") + rest, color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (stuck) WeekBarsButton(StuckLog.weeks(t, subs, today)) { explaining = true }
    }
    if (explaining) StuckDialog(t, state.subtasksOf(t), today, { vm.noteStuck(t, it) }) { explaining = false }
}

/**
 * A calendar event on the timeline against the clock: greyed once it has ended, and while it runs the red now line
 * crosses the card at how far into the event it is, with the part already gone tinted darker. Tapping it opens the
 * calendar choice with its calendar pointed out.
 */
@Composable
private fun TimelineEvent(item: DayPlan.Item.EventItem, now: LocalDateTime, onClick: () -> Unit) {
    val e = item.event
    val phase = DayPlan.phase(e, now)
    val past = phase == DayPlan.Phase.PAST
    val running = phase == DayPlan.Phase.ACTIVE
    val fraction = if (running) DayPlan.progress(e, now) else 0f
    val bg = if (past && !C.calm) C.raised else C.tealSoft
    val spent = C.teal.copy(alpha = 0.16f)
    val red = C.red
    // Every event here is from the phone's calendar and on today's page, so neither needs saying.
    val range = DayPlan.range(e, item.time != null, now.toLocalDate()) { it.format(HM) }
    val left = ChronoUnit.MINUTES.between(now, e.end)
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = if (running) 4.dp else 2.dp, bottom = if (running) 4.dp else 2.dp), verticalAlignment = Alignment.Top) {
        Row(
            Modifier.weight(1f).heightIn(min = if (running) 0.dp else 36.dp).height(IntrinsicSize.Min).clip(if (C.calm) RectangleShape else RoundedCornerShape(8.dp)).background(bg)
                .clickable(onClick = onClick)
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
            val mine = CalendarStyles.color(e.calendarId)
            Box(Modifier.width(3.dp).fillMaxHeight().background(if (past) C.faint else mine ?: C.teal))
            if (running) {
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(e.title, color = C.eventText, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(range + tr(", เหลือ $left นาที", ", $left min left"), color = C.tealText, fontSize = TS.caption)
                }
            } else {
                Row(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.title, Modifier.weight(1f), color = if (past) C.faint else C.eventText, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CalendarStyles.label(e.calendarId)?.let { Text(it, Modifier.padding(start = 8.dp), color = if (past) C.faint else mine ?: C.tealText, fontSize = TS.caption, maxLines = 1) }
                    Text(range, Modifier.padding(start = 8.dp), color = if (past) C.faint else C.tealText, fontSize = TS.caption, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(C.radius)).background(C.amberSoft).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp), verticalAlignment = Alignment.Top) {
        Text(text, Modifier.weight(1f).padding(start = 4.dp, top = 3.dp), color = C.text, fontSize = TS.body)
        Box(Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Ic.close, tr("ซ่อน", "Hide"), tint = C.muted, modifier = Modifier.size(13.dp))
        }
    }
}

/** A task's ring or an event's bar, as on the timeline, so the countdowns read apart. */
@Composable
private fun CountdownMark(event: Boolean) {
    Box(Modifier.width(12.dp).height(14.dp), contentAlignment = Alignment.Center) {
        if (event) Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(1.dp)).background(C.teal))
        else Box(Modifier.size(12.dp).clip(CircleShape).border(1.5.dp, C.faint, CircleShape))
    }
}

/**
 * What Focus counts down to: a tab for tasks and one for calendar events, so the two do not mix, any number ticked in
 * either. A tick saves at once; the ticked ones lead their tab.
 */
@Composable
private fun CountdownPicker(state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var eventsTab by remember { mutableStateOf(false) }
    // The calendar tab offers the year ahead, more than the screens keep.
    LaunchedEffect(Unit) { vm.loadAhead() }
    val now = LocalDateTime.now()
    val pins = state.countdowns
    val taskChoices = Countdown.taskChoices(pins, state.allTasks, now)
    val eventChoices = Countdown.eventChoices(pins, CalendarEvent.distinct(state.events + state.pinnedEvents + state.aheadEvents), now)
    val tasksOn = taskChoices.count { it.on }
    val eventsOn = eventChoices.count { it.on }
    val q = text.trim()
    val list = (if (eventsTab) eventChoices else taskChoices).filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("นับถอยหลัง", "Countdowns"), Modifier.weight(1f))
                if (tasksOn + eventsOn > 0) Text(tr("เลือกแล้ว ${tasksOn + eventsOn}", "${tasksOn + eventsOn} chosen"), color = C.muted, fontSize = TS.caption)
            }
        },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(3.dp)) {
                    PickerTab(tr("งาน", "Tasks"), tasksOn, !eventsTab, Modifier.weight(1f)) { eventsTab = false }
                    PickerTab(tr("ปฏิทิน", "Calendar"), eventsOn, eventsTab, Modifier.weight(1f)) { eventsTab = true }
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(C.accent),
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(if (eventsTab) tr("ค้นหานัด", "Search events") else tr("ค้นหางาน", "Search tasks"), color = C.faint, fontSize = TS.body)
                            inner()
                        }
                    },
                )
                LazyColumn(Modifier.padding(top = 4.dp).heightIn(max = 360.dp)) {
                    if (list.isEmpty()) item {
                        Text(
                            when {
                                !eventsTab -> tr("ไม่พบงานที่มีวัน", "No tasks with a date")
                                state.calendarAccess != true -> tr("ยังอ่านปฏิทินในเครื่องไม่ได้", "The phone's calendars cannot be read yet")
                                else -> tr("ไม่พบนัดในปีข้างหน้า", "No events in the year ahead")
                            },
                            Modifier.padding(vertical = 12.dp), color = C.muted, fontSize = TS.body,
                        )
                    }
                    list.forEachIndexed { i, c ->
                        val prev = list.getOrNull(i - 1)
                        // The ticked ones under "chosen", then the rest under their group (a month, for events).
                        val head = when {
                            c.on -> if (prev == null) tr("เลือกไว้", "Chosen") else null
                            prev == null || prev.on || prev.group != c.group -> c.group
                            else -> null
                        }
                        if (head != null) item {
                            Column {
                                if (prev?.on == true) Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(C.divider))
                                Text(head, Modifier.padding(top = 8.dp, bottom = 2.dp, start = 2.dp), color = C.faint, fontSize = TS.micro, fontWeight = FontWeight.Medium)
                            }
                        }
                        item { ChoiceRow(c) { vm.setCountdowns(Countdown.toggle(pins, c, now)) } }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("เสร็จ", "Done"), color = C.accentText) } },
        dismissButton = {
            if (pins.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { vm.setCountdowns(emptyList()) }) { Text(tr("ล้างทั้งหมด", "Clear all"), color = C.red) }
        },
    )
}

/** One half of the picker's switch, with how many are ticked in it. */
@Composable
private fun PickerTab(label: String, count: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (on) C.control else Color.Transparent).clickable(onClick = onClick).padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (on) C.text else C.muted, fontSize = TS.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
        if (count > 0) {
            Text(
                "$count", Modifier.padding(start = 5.dp).clip(RoundedCornerShape(9.dp)).background(C.accent.copy(alpha = 0.18f)).padding(horizontal = 6.dp),
                color = C.accentText, fontSize = TS.micro,
            )
        }
    }
}

/** A row of the picker: a tick, the title with its date under it, and how far off it is. */
@Composable
private fun ChoiceRow(c: Countdown.Choice, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val box = RoundedCornerShape(5.dp)
        Box(
            Modifier.size(20.dp).clip(box).then(if (c.on) Modifier.background(C.accent) else Modifier.border(2.dp, C.faint, box)),
            contentAlignment = Alignment.Center,
        ) { if (c.on) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(14.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(c.title, color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.sub.isNotEmpty()) Text(c.sub, color = C.muted, fontSize = TS.caption, maxLines = 1)
        }
        Text(c.text, Modifier.padding(start = 8.dp), color = if (c.late) C.red else C.muted, fontSize = TS.caption, maxLines = 1)
    }
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
