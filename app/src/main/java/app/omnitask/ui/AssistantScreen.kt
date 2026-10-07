package app.omnitask.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.data.CalendarReader
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Planner
import app.omnitask.model.Profile
import app.omnitask.model.Task
import app.omnitask.model.tr
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val EXAMPLES: List<String>
    get() = listOf(
        tr("อยากไปวิ่งสัปดาห์นี้ ควรไปตอนไหนดี", "When should I go running this week?"),
        tr("อยากเขียน proposal 2 ชม. ควรทำตอนไหน", "When should I write the proposal for 2 hours?"),
        tr("ต้องไปธนาคาร พรุ่งนี้ตอนไหนดี", "When should I go to the bank tomorrow?"),
    )

/**
 * The assistant from the C+ mockup: it reads the profile note, the tasks and Google Calendar,
 * answers "when should I do X" with ranked slots, and asks before remembering anything.
 * Everything here is rule-based; "ถาม Claude" hands the picture to the Claude app on the owner's subscription.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var interviewing by rememberSaveable { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var custom by remember { mutableStateOf<CustomRequest?>(null) }
    var pickingRange by remember { mutableStateOf(false) }
    val calendarAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pendingConfirm?.let { (i, title) -> vm.confirmSlot(i, title) }
        pendingConfirm = null
        vm.calendarChanged()
    }
    fun confirm(index: Int, title: String) {
        if (CalendarReader.canWrite(context)) {
            vm.confirmSlot(index, title)
        } else {
            pendingConfirm = index to title
            calendarAsk.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }
    fun send(text: String = input) {
        if (text.isBlank()) return
        vm.ask(text.trim())
        input = ""
    }
    fun askClaude(question: String) {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, question + "\n\n" + snapshot(state))
        try {
            context.startActivity(Intent(share).setPackage("com.anthropic.claude"))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(share, tr("ส่งให้ Claude", "Send to Claude")))
        }
    }

    val list = rememberLazyListState()
    LaunchedEffect(state.chat.size) { if (state.chat.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }
    val profile = state.profile

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("ผู้ช่วย", "Assistant"), style = MaterialTheme.typography.headlineSmall, color = C.text)
                Text(sources(state), color = C.muted, fontSize = TS.caption)
            }
            if (state.chat.isNotEmpty()) SquareButton(Ic.refresh, tr("เริ่มใหม่", "Start over"), vm::clearChat)
        }

        LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !profile.exists || interviewing -> item(key = "interview") {
                    Interview(profile, first = !profile.exists, onSave = { vm.saveProfile(it); interviewing = false }, onCancel = if (profile.exists) ({ interviewing = false }) else null)
                }
                profile.stale(state.today) -> item(key = "stale") {
                    AskCard(
                        tr("ชีวิตช่วงนี้ยังเหมือนเดิมไหม?", "Is life still the same lately?"),
                        tr("ไม่ได้ทบทวนมา 30 วัน", "Not reviewed in 30 days"),
                        tr(
                            "ตื่น ${Profile.hm(profile.wake)} นอน ${Profile.hm(profile.sleep)} สมองดี ${Profile.hm(profile.focusFrom)} ถึง ${Profile.hm(profile.focusTo)} ออกกำลังกาย ${Profile.hm(profile.exercise)}",
                            "Wake ${Profile.hm(profile.wake)}, sleep ${Profile.hm(profile.sleep)}, focus ${Profile.hm(profile.focusFrom)} to ${Profile.hm(profile.focusTo)}, exercise ${Profile.hm(profile.exercise)}",
                        ),
                        tr("ปรับ", "Update"), { interviewing = true }, tr("ยังเหมือนเดิม", "Still the same"), { vm.saveProfile(profile) },
                    )
                }
                state.insight != null -> item(key = "insight") {
                    val ask = state.insight
                    AskCard(
                        tr("จำไว้ไหม?", "Remember this?"), ask.window, ask.text,
                        tr("ไม่ใช่", "No"), { vm.answerInsight(false) }, tr("ใช่ จำไว้", "Yes, remember"), { vm.answerInsight(true) },
                    )
                }
            }

            if (state.chat.isEmpty()) {
                item(key = "examples") {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                tr("ถามได้ว่าอยากทำอะไร แล้วผู้ช่วยจะหาช่วงที่เหมาะให้ 3 ช่วง พร้อมเหตุผล", "Say what you want to do and the assistant finds 3 good times, with reasons"),
                                color = C.text2, fontSize = TS.body, lineHeight = 20.sp,
                            )
                            EXAMPLES.forEach { q ->
                                Text(
                                    q,
                                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken)
                                        .clickable { send(q) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    color = C.accentText, fontSize = TS.body,
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(state.chat) { index, item ->
                when (item) {
                    is Chat.Asked -> Bubble(item.text)
                    is Chat.Slots -> SlotsCard(
                        item, { vm.pickSlot(index, it) }, { vm.moreSlots(index) }, { confirm(index, it) },
                        onCustom = { title, allDay -> custom = CustomRequest(index, title, allDay, item.plan.minutes, item.plan.slots.firstOrNull()?.day ?: state.today) },
                    ) { askClaude(item.plan.request) }
                    is Chat.Today -> TodayCard(item.tasks, state, onOpen)
                    is Chat.Review -> ReviewCard(item)
                    is Chat.Duration -> DurationCard(item) { vm.answerDuration(index, it) }
                    is Chat.Agenda -> AgendaCard(item, state, onOpen)
                    is Chat.RangePlan -> RangePlanCard(item, state, onOpen, { vm.toggleRangeCalendar(index) }) { which ->
                        if (item.toCalendar && !CalendarReader.canWrite(context)) {
                            calendarAsk.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                        vm.acceptProposals(index, which)
                    }
                    is Chat.Ranked -> RankedCard(item, onOpen)
                }
            }

            if (state.chat.isEmpty()) item(key = "quick") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickCard(tr("สัปดาห์หน้ามีอะไร", "Next week"), tr("งานและนัดทั้งหมด", "Tasks and events"), Modifier.weight(1f)) {
                            vm.showAgenda(nextWeek(state.today), tr("สัปดาห์หน้ามีอะไรบ้าง", "What's on next week?"))
                        }
                        QuickCard(tr("วางแผนสัปดาห์หน้า", "Plan next week"), tr("จัดงานที่ยังไม่มีวันลงช่องว่าง", "Fit undated work into free time"), Modifier.weight(1f)) {
                            vm.planRange(nextWeek(state.today), tr("วางแผนสัปดาห์หน้าให้หน่อย", "Plan next week"))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val morning = state.notify.digestTimes.firstOrNull()?.let { tr("แผนตอนเช้า ${Profile.hm(it)}", "Morning plan ${Profile.hm(it)}") } ?: tr("ตามงานและนัดวันนี้", "From today's tasks and events")
                        QuickCard(tr("จัดลำดับวันนี้", "Plan today"), morning, Modifier.weight(1f), vm::planToday)
                        QuickCard(
                            tr("ทบทวนสัปดาห์", "Weekly review"),
                            if (state.notify.weeklyReview) tr("ทุกอาทิตย์ 20:00", "Sundays 20:00") else tr("สรุป 7 วันที่ผ่านมา", "The last 7 days"),
                            Modifier.weight(1f), vm::reviewWeek,
                        )
                    }
                    QuickCard(
                        tr("ถาม Claude", "Ask Claude"),
                        tr("ส่งงานและนัดวันนี้ไปที่แอป Claude ใช้ subscription ไม่ต้องจ่าย API", "Send today's tasks and events to the Claude app, on your subscription, no API cost"),
                        Modifier.fillMaxWidth(),
                    ) {
                        askClaude(tr("ช่วยจัดลำดับงานวันนี้ให้หน่อย ตามเวลาว่างในปฏิทิน", "Please order today's tasks around the free time in my calendar"))
                    }
                }
            }
        }

        val ime = WindowInsets.isImeVisible
        // Once the conversation has started the big cards give way to this row, so the chat keeps the space.
        if (state.chat.isNotEmpty() && !ime) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuickChip(tr("จัดลำดับวันนี้", "Plan today"), vm::planToday)
                QuickChip(tr("จัดลำดับทั้งหมด", "Prioritize all")) { vm.rankAll() }
                QuickChip(tr("สัปดาห์หน้ามีอะไร", "Next week")) { vm.showAgenda(nextWeek(state.today), tr("สัปดาห์หน้ามีอะไรบ้าง", "What's on next week?")) }
                QuickChip(tr("เดือนหน้ามีอะไร", "Next month")) { vm.showAgenda(nextMonth(state.today), tr("เดือนหน้ามีอะไรบ้าง", "What's on next month?")) }
                QuickChip(tr("วางแผนสัปดาห์หน้า", "Plan next week")) { vm.planRange(nextWeek(state.today), tr("วางแผนสัปดาห์หน้าให้หน่อย", "Plan next week")) }
                QuickChip(tr("วางแผนเดือนนี้", "Plan this month")) {
                    vm.planRange(state.today to state.today.with(TemporalAdjusters.lastDayOfMonth()), tr("วางแผนเดือนนี้ให้หน่อย", "Plan this month"))
                }
                QuickChip(tr("เลือกช่วงเอง", "Pick dates")) { pickingRange = true }
                QuickChip(tr("ทบทวนสัปดาห์", "Weekly review"), vm::reviewWeek)
                QuickChip(tr("ถาม Claude", "Ask Claude")) { askClaude(tr("ช่วยจัดลำดับงานวันนี้ให้หน่อย ตามเวลาว่างในปฏิทิน", "Please order today's tasks around the free time in my calendar")) }
            }
        }
        Row(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = if (ime) 8.dp else NavClearance - 12.dp)
                .then(if (ime) Modifier.imePadding() else Modifier)
                .fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.card).border(1.dp, Color(0xFF262B3A), RoundedCornerShape(24.dp))
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 3,
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) Text(tr("อยากทำอะไร หรือถามเรื่องงาน", "What do you want to do?"), color = C.faint, fontSize = TS.body)
                        inner()
                    }
                },
            )
            Box(
                Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).background(C.accent).clickable { send() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.send, tr("ส่ง", "Send"), tint = C.onAccent, modifier = Modifier.size(17.dp)) }
        }
    }

    custom?.let { c ->
        CustomTimeDialog(c, onDismiss = { custom = null }) { day, time ->
            if (!CalendarReader.canWrite(context)) calendarAsk.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            vm.confirmCustom(c.index, c.title, day, time, c.minutes)
            custom = null
        }
    }
    if (pickingRange) {
        RangeDialog(
            onDismiss = { pickingRange = false },
            onAgenda = { from, to -> vm.showAgenda(from to to, tr("ช่วงนี้มีอะไรบ้าง", "What's on in this range?")); pickingRange = false },
            onPlan = { from, to -> vm.planRange(from to to, tr("วางแผนช่วงนี้ให้หน่อย", "Plan this range")); pickingRange = false },
        )
    }
}

private fun nextWeek(today: LocalDate) = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }

private fun nextMonth(today: LocalDate) = today.plusMonths(1).withDayOfMonth(1).let { it to it.with(TemporalAdjusters.lastDayOfMonth()) }

/** A slot the owner sets by hand: all-day on a date, or a date and a time. */
private data class CustomRequest(val index: Int, val title: String, val allDay: Boolean, val minutes: Int, val day: LocalDate)

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        color = C.text2, fontSize = TS.caption, maxLines = 1,
    )
}

/** "About how long?" with ready answers, including "don't know" (the assistant then estimates from the kind of work). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationCard(item: Chat.Duration, onAnswer: (Int) -> Unit) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Text(tr("งานนี้น่าจะใช้เวลาประมาณเท่าไร", "About how long will this take?"), color = C.text, fontSize = TS.body)
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 45, 60, 90, 120, 180).forEach { m ->
                    Chip(Planner.duration(m), item.answered == m, { onAnswer(m) })
                }
                Chip(tr("ไม่รู้", "Not sure"), item.answered == -1, { onAnswer(-1) })
            }
            if (item.answered == -1) {
                Text(tr("ใช้ค่าประมาณจากประเภทงานแทน", "Estimating from the kind of work"), Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption)
            }
        }
    }
}

@Composable
private fun AgendaCard(item: Chat.Agenda, state: UiState, onOpen: (Task) -> Unit) {
    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", TH)
    val hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    Card {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(item.title, Modifier.padding(horizontal = 14.dp), color = C.text, style = MaterialTheme.typography.titleSmall)
            Text(item.summary, Modifier.padding(horizontal = 14.dp, vertical = 2.dp), color = C.muted, fontSize = TS.caption)
            if (item.days.isEmpty()) Text(tr("ว่างทั้งช่วง ไม่มีงานหรือนัด", "Nothing on in this range"), Modifier.padding(14.dp), color = C.muted, fontSize = TS.body)
            item.days.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
                    Text(
                        if (d.day == state.today) tr("วันนี้", "Today") else d.day.format(dayFmt),
                        Modifier.width(76.dp), color = if (d.day == state.today) C.accentText else C.text2, fontSize = TS.caption,
                    )
                    Column(Modifier.weight(1f)) {
                        d.events.forEach { e ->
                            Text(
                                (if (e.allDay) tr("ทั้งวัน ", "All day ") else e.begin.format(hm) + " ") + e.title,
                                color = C.tealText, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        d.tasks.forEach { t ->
                            Text(
                                (if (t.due == d.day) tr("ครบ: ", "Due: ") else tr("นัดทำ: ", "Do: ")) + t.title,
                                Modifier.clickable { onOpen(t) },
                                color = if (t.due == d.day) C.text else C.text2, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangePlanCard(item: Chat.RangePlan, state: UiState, onOpen: (Task) -> Unit, onToggleCalendar: () -> Unit, onAccept: (List<Int>) -> Unit) {
    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", TH)
    Card {
        Column(Modifier.padding(14.dp)) {
            Text(tr("แผน ", "Plan for ") + item.title, color = C.text, style = MaterialTheme.typography.titleSmall)
            Text(
                if (item.proposals.isEmpty()) tr("ไม่มีงานที่ยังไม่มีวัน หรือช่วงนี้ไม่มีเวลาว่างพอ", "No undated work, or no free time in this range")
                else tr("จัดงานที่ยังไม่มีวันลงช่องว่าง เรียงจากเร่งสุด ไม่ชนนัดและเวร", "Undated work placed in free time, most urgent first, around events and shifts"),
                Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.caption,
            )
            item.proposals.forEachIndexed { i, p ->
                val done = i in item.accepted
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(78.dp)) {
                        Text(p.slot.day.format(dayFmt), color = C.text2, fontSize = TS.caption)
                        Text(Profile.hm(p.slot.start), color = C.accentText, fontSize = TS.caption)
                    }
                    Column(Modifier.weight(1f).clickable { onOpen(p.task) }) {
                        Text(p.task.title, color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(Planner.duration(p.minutes), color = C.muted, fontSize = TS.caption)
                    }
                    if (done) {
                        Icon(Ic.check, tr("ลงแผนแล้ว", "Planned"), tint = C.lime, modifier = Modifier.padding(10.dp).size(18.dp))
                    } else {
                        SquareButton(Ic.plus, tr("ลงแผน", "Plan it"), { onAccept(listOf(i)) })
                    }
                }
            }
            if (item.proposals.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggleCalendar).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("ลง Google Calendar ด้วย", "Also add to Google Calendar"), Modifier.weight(1f), color = C.text, fontSize = TS.body)
                    OnOff(item.toCalendar)
                }
                val left = item.proposals.indices.filter { it !in item.accepted }
                PrimaryButton(
                    if (left.isEmpty()) tr("ลงแผนครบแล้ว", "All planned") else tr("ลงแผนทั้งหมด ${left.size} งาน", "Plan all ${left.size}"),
                    { if (left.isNotEmpty()) onAccept(left) },
                    Modifier.padding(top = 8.dp).fillMaxWidth(), color = if (left.isEmpty()) C.lime else C.accent,
                )
                Text(
                    tr("ลงแผน = ใส่ ⏳ วันนัดทำ และ 🎯 เวลาเตือนให้งานนั้น", "Planning sets the task's ⏳ scheduled date and 🎯 reminder time"),
                    Modifier.padding(top = 6.dp), color = C.faint, fontSize = TS.caption,
                )
            }
        }
    }
}

@Composable
private fun RankedCard(item: Chat.Ranked, onOpen: (Task) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                tr("ลำดับที่ควรทำ เรียงจากเลยกำหนด ใกล้ครบ คนรอ แล้วค่อยความสำคัญ", "Do them in this order: overdue, due soon, people waiting, then importance"),
                Modifier.padding(horizontal = 14.dp), color = C.text, fontSize = TS.body,
            )
            item.items.forEachIndexed { i, (t, why) ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", Modifier.width(24.dp), color = C.accentText, fontSize = TS.body)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = C.text, fontSize = TS.body)
                        Text(why, color = if (i < 3) C.amber else C.muted, fontSize = TS.caption)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTimeDialog(c: CustomRequest, onDismiss: () -> Unit, onSet: (LocalDate, LocalTime?) -> Unit) {
    val date = rememberDatePickerState(initialSelectedDateMillis = c.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    var pickingTime by remember { mutableStateOf(false) }
    val time = rememberTimePickerState(9, 0, is24Hour = true)
    fun day() = date.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: c.day
    if (!pickingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            colors = DatePickerDefaults.colors(containerColor = C.raised),
            confirmButton = {
                TextButton(onClick = { if (c.allDay) onSet(day(), null) else pickingTime = true }) {
                    Text(if (c.allDay) tr("ลงทั้งวัน", "Add all day") else tr("ต่อไป: เลือกเวลา", "Next: time"), color = C.accent)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
        ) { DatePicker(state = date, colors = DatePickerDefaults.colors(containerColor = C.raised)) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = C.raised,
            title = { Text(tr("เริ่มกี่โมง", "Start time")) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimeInput(state = time)
                    Text(day().format(SHORT_DATE) + tr(", ยาว ", ", ") + Planner.duration(c.minutes), color = C.muted, fontSize = TS.caption)
                }
            },
            confirmButton = { TextButton(onClick = { onSet(day(), LocalTime.of(time.hour, time.minute)) }) { Text(tr("ลงตาราง", "Add"), color = C.accent) } },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(tr("กลับ", "Back"), color = C.text2) } },
        )
    }
}

/** Pick any range, then either look at it or plan it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(onDismiss: () -> Unit, onAgenda: (LocalDate, LocalDate) -> Unit, onPlan: (LocalDate, LocalDate) -> Unit) {
    val range = rememberDateRangePickerState()
    fun d(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val from = d(range.selectedStartDateMillis)
    val to = d(range.selectedEndDateMillis) ?: from
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = C.raised),
        confirmButton = {
            Row {
                TextButton(onClick = { if (from != null && to != null) onAgenda(from, to) }, enabled = from != null) { Text(tr("ดูว่ามีอะไร", "Look"), color = C.accentText) }
                TextButton(onClick = { if (from != null && to != null) onPlan(from, to) }, enabled = from != null) { Text(tr("วางแผนให้", "Plan it"), color = C.accent) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    ) {
        DateRangePicker(
            state = range,
            modifier = Modifier.weight(1f),
            title = { Text(tr("เลือกช่วงวัน", "Pick a date range"), Modifier.padding(start = 24.dp, top = 16.dp), color = C.text) },
            colors = DatePickerDefaults.colors(containerColor = C.raised),
        )
    }
}

private fun sources(state: UiState): String {
    val parts = buildList {
        if (state.profile.exists) add(Profile.PATH.substringAfterLast('/'))
        add(tr("งาน ${state.tasks.count { it.isOpen }} รายการ", "${state.tasks.count { it.isOpen }} tasks"))
        if (state.calendarAccess == true) add("Google Calendar")
    }
    val and = tr("และ", "and")
    return tr("อ่านจาก ", "Reading ") + parts.dropLast(1).joinToString(", ").let { if (it.isEmpty()) parts.last() else "$it $and ${parts.last()}" }
}

@Composable
private fun AskCard(title: String, badge: String, body: String, noLabel: String, onNo: () -> Unit, yesLabel: String, onYes: () -> Unit) {
    Card(color = C.accentDeep, border = Color(0xFF2D2852)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.spark, null, tint = C.accentText, modifier = Modifier.size(16.dp))
                Text(title, Modifier.weight(1f).padding(start = 8.dp), color = C.accentText, fontSize = TS.caption)
                Text(badge, color = C.muted, fontSize = TS.caption)
            }
            Text(body, Modifier.padding(top = 8.dp), color = C.text, fontSize = TS.body, lineHeight = 21.sp)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF3A3466), RoundedCornerShape(10.dp)).clickable(onClick = onNo),
                    contentAlignment = Alignment.Center,
                ) { Text(noLabel, color = C.accentText, fontSize = TS.caption) }
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.accent).clickable(onClick = onYes),
                    contentAlignment = Alignment.Center,
                ) { Text(yesLabel, color = C.onAccent, fontSize = TS.caption, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

/** First-time interview (and the monthly re-ask): four questions answered with chips, saved to the profile note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Interview(current: Profile, first: Boolean, onSave: (Profile) -> Unit, onCancel: (() -> Unit)?) {
    var p by remember { mutableStateOf(current) }
    // Free answers per question; those that aren't a plain time are kept in the profile as written.
    val others = remember { mutableStateMapOf<String, String>() }
    fun other(key: String) = others[key]
    fun setOther(key: String, v: String?) { if (v == null) others.remove(key) else others[key] = v }
    Card(color = C.accentDeep, border = Color(0xFF2D2852)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.spark, null, tint = C.accentText, modifier = Modifier.size(16.dp))
                Text(if (first) tr("ขอรู้จักกันก่อน", "Getting to know you") else tr("ปรับโปรไฟล์", "Update profile"), Modifier.weight(1f).padding(start = 8.dp), color = C.accentText, fontSize = TS.caption)
                Text(tr("4 ข้อ", "4 questions"), color = C.muted, fontSize = TS.caption)
            }
            Text(
                tr(
                    "คำตอบจะเก็บใน ${Profile.PATH} แก้ใน Obsidian ได้ และผู้ช่วยจะถามใหม่ทุกเดือนเพราะชีวิตเปลี่ยนได้",
                    "Answers are saved in ${Profile.PATH}, editable in Obsidian. The assistant asks again every month, since life changes.",
                ),
                Modifier.padding(top = 6.dp), color = C.text2, fontSize = TS.body, lineHeight = 19.sp,
            )
            fun times(vararg hm: String) = hm.map { LocalTime.parse(it) }
            Question(
                tr("ปกติตื่นกี่โมง", "When do you usually wake up?"), times("05:30", "06:00", "06:30", "07:00", "08:00"), p.wake,
                other("ตื่น"), { setOther("ตื่น", it) },
            ) { p = p.copy(wake = it) }
            Question(
                tr("เข้านอนกี่โมง", "When do you go to bed?"), times("21:30", "22:00", "22:30", "23:00", "23:30"), p.sleep,
                other("นอน"), { setOther("นอน", it) },
            ) { p = p.copy(sleep = it) }
            Text(tr("ช่วงไหนสมองดีที่สุด", "When is your mind sharpest?"), Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.text, fontSize = TS.body)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("08:00" to "11:00", "10:00" to "13:00", "13:00" to "16:00", "19:00" to "22:00").forEach { (a, b) ->
                    val from = LocalTime.parse(a)
                    Chip(tr("$a ถึง $b", "$a to $b"), other("ช่วงสมองดี") == null && p.focusFrom == from, {
                        setOther("ช่วงสมองดี", null)
                        p = p.copy(focusFrom = from, focusTo = LocalTime.parse(b))
                    })
                }
                Chip(tr("อื่นๆ", "Other"), other("ช่วงสมองดี") != null, { setOther("ช่วงสมองดี", other("ช่วงสมองดี") ?: "") })
            }
            other("ช่วงสมองดี")?.let { v ->
                OtherField(v, tr("เช่น 05:00-07:00 หรือ หลังลงเวรเช้า", "e.g. 05:00-07:00, or after a morning shift")) { text ->
                    setOther("ช่วงสมองดี", text)
                    val found = TIME_TEXT.findAll(text).mapNotNull { parseTime(it.value) }.toList()
                    if (found.size >= 2) p = p.copy(focusFrom = found[0], focusTo = found[1])
                }
            }
            Question(
                tr("ชอบออกกำลังกายตอนไหน", "When do you like to exercise?"), times("06:00", "07:00", "17:30", "18:30", "20:00"), p.exercise,
                other("ออกกำลังกาย"), { setOther("ออกกำลังกาย", it) },
            ) { p = p.copy(exercise = it) }
            Text(
                tr(
                    "เวรอ่านจากนัดใน Google Calendar ที่มีคำว่า \"${p.shiftWords.joinToString("\", \"")}\" และเวรดึกจากคำว่า \"${p.nightWords.joinToString("\", \"")}\"",
                    "Shifts are Google Calendar events containing \"${p.shiftWords.joinToString("\", \"")}\", night shifts those containing \"${p.nightWords.joinToString("\", \"")}\"",
                ),
                Modifier.padding(top = 12.dp), color = C.muted, fontSize = TS.caption, lineHeight = 18.sp,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCancel != null) GhostButton(tr("ยกเลิก", "Cancel"), onCancel, Modifier.weight(1f))
                PrimaryButton(tr("บันทึกลงโปรไฟล์", "Save to profile"), {
                    // A described answer ("แล้วแต่เวร") is remembered word for word under its question.
                    val described = others.filter { (_, v) -> v.isNotBlank() && TIME_TEXT.find(v) == null }.map { (k, v) -> "$k: ${v.trim()}" }
                    onSave(p.copy(remembered = (p.remembered.filterNot { r -> described.any { r.startsWith(it.substringBefore(':') + ":") } } + described).distinct()))
                }, Modifier.weight(1.4f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Question(
    title: String, options: List<LocalTime>, selected: LocalTime, other: String?,
    onOther: (String?) -> Unit, onPick: (LocalTime) -> Unit,
) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.text, fontSize = TS.body)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { t -> Chip(Profile.hm(t), other == null && t == selected, { onOther(null); onPick(t) }) }
        Chip(tr("อื่นๆ", "Other"), other != null, { onOther(other ?: "") })
    }
    if (other != null) OtherField(other, tr("พิมพ์เวลา เช่น 05:45 หรือเล่าเอง เช่น แล้วแต่เวร", "Type a time like 05:45, or describe it, e.g. depends on shifts")) { text ->
        onOther(text)
        parseTime(text)?.let(onPick)
    }
}

/** A free answer when no choice fits: a time is used as the answer, anything else is remembered as written. */
@Composable
private fun OtherField(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.caption)
                inner()
            }
        },
    )
}

private val TIME_TEXT = Regex("""(\d{1,2})[:.](\d{2})""")

private fun parseTime(text: String): LocalTime? = TIME_TEXT.find(text)?.let {
    val h = it.groupValues[1].toInt()
    val m = it.groupValues[2].toInt()
    if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
}

@Composable
private fun Bubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(C.accent).padding(horizontal = 14.dp, vertical = 10.dp),
            color = C.onAccent, fontSize = TS.body, lineHeight = 20.sp,
        )
    }
}

@Composable
private fun SlotsCard(
    item: Chat.Slots, onPick: (Int) -> Unit, onMore: () -> Unit, onConfirm: (String) -> Unit,
    onCustom: (title: String, allDay: Boolean) -> Unit, onClaude: () -> Unit,
) {
    val plan = item.plan
    var title by remember(plan) { mutableStateOf(plan.title) }
    val first = item.page * 3
    val shown = plan.slots.drop(first).take(3)
    Card {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
            Text(plan.intro, color = C.text, fontSize = TS.body, lineHeight = 21.sp)
            if (item.done == null) {
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("ชื่องาน", "Title"), Modifier.width(56.dp), color = C.muted, fontSize = TS.caption)
                    BasicTextField(
                        value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont), cursorBrush = SolidColor(C.accent),
                    )
                }
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEachIndexed { i, slot ->
                    val index = first + i
                    val on = item.picked == index
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFF1E1B33) else C.sunken)
                            .border(1.dp, if (on) C.accent else Color(0xFF242938), RoundedCornerShape(14.dp))
                            .clickable { onPick(index) }.padding(12.dp),
                    ) {
                        Column(Modifier.width(56.dp)) {
                            Text(slot.dayLabel, color = if (on) C.accentText else C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                            Text(Profile.hm(slot.start), color = C.muted, fontSize = TS.caption)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(slot.title, color = C.text, fontSize = TS.body)
                                if (index == 0) {
                                    Text(
                                        tr("แนะนำ", "Best"),
                                        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF2B3A1A)).padding(horizontal = 7.dp, vertical = 1.dp),
                                        color = C.lime, fontSize = TS.caption,
                                    )
                                }
                            }
                            Text(slot.why, Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.caption, lineHeight = 17.sp)
                        }
                    }
                }
            }
            if (shown.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (plan.slots.size > 3) GhostButton(tr("ดูช่วงอื่น", "More times"), onMore, Modifier.weight(1f))
                    PrimaryButton(
                        item.done ?: tr("สร้างงาน + ลงปฏิทิน", "Add task + calendar"),
                        { if (item.done == null && title.isNotBlank()) onConfirm(title.trim()) },
                        Modifier.weight(1.4f), color = if (item.done != null) C.lime else C.accent,
                    )
                }
            }
            // When no time fits, or the time isn't known yet: put it on a day as all-day, or pick the time yourself.
            if (item.done == null) {
                Text(
                    if (shown.isEmpty()) tr("ลงตารางแบบไหนดี", "How should it go on the calendar?") else tr("ไม่ใช่ช่วงไหนเลย", "None of these?"),
                    Modifier.padding(top = 12.dp), color = C.muted, fontSize = TS.caption,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickChip(tr("ลงทั้งวัน", "All day")) { if (title.isNotBlank()) onCustom(title.trim(), true) }
                    QuickChip(tr("ตั้งเวลาเอง", "Pick a time")) { if (title.isNotBlank()) onCustom(title.trim(), false) }
                    QuickChip(tr("ถาม Claude", "Ask Claude"), onClaude)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(tasks: List<Task>, state: UiState, onOpen: (Task) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                if (tasks.isEmpty()) {
                    tr("วันนี้ไม่มีงานเร่ง ลองหยิบงานลงทุนอนาคตสักงาน", "Nothing urgent today. Try picking some future work.")
                } else {
                    tr("เรียงจากต้องทำก่อน ไปคนที่รอ แล้วค่อยงานเพื่ออนาคต", "Must-dos first, then people waiting, then future work")
                },
                Modifier.padding(horizontal = 14.dp), color = C.text, fontSize = TS.body, lineHeight = 21.sp,
            )
            tasks.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", Modifier.width(22.dp), color = C.accentText, fontSize = TS.body)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = C.text, fontSize = TS.body)
                        reason(t, state).takeIf { it.isNotEmpty() }?.let { Text(it, color = C.muted, fontSize = TS.caption) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(item: Chat.Review) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Text(item.title, color = C.text, style = MaterialTheme.typography.titleSmall)
            item.lines.forEach { Text(it, Modifier.padding(top = 6.dp), color = C.text2, fontSize = TS.body) }
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, color = C.text, fontSize = TS.body)
        Text(subtitle, color = C.muted, fontSize = TS.caption)
    }
}

private fun reason(t: Task, state: UiState): String {
    val b = state.brief
    return when {
        t.due?.let { it < state.today } == true -> tr("เลยกำหนดแล้ว", "Overdue")
        t.due == state.today -> tr("ครบวันนี้", "Due today")
        t.scheduled == state.today -> tr("นัดทำวันนี้", "Scheduled today")
        t in b.waiting -> {
            val who = Focus.waitingFor(t)
            val age = Focus.ageDays(t, state.today)
            tr((who ?: "มีคน") + " รอ " + (age?.let { "$it วัน" } ?: "อยู่"), (who ?: "Someone") + " waiting" + (age?.let { " $it days" } ?: ""))
        }
        t in b.future -> tr("ลงทุนอนาคต ไม่มีเดดไลน์แต่สำคัญ", "Future work: no deadline, but it matters")
        else -> ""
    }
}

/** A plain-text picture of today for Claude: profile, timeline and the open list. */
private fun snapshot(state: UiState): String {
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val plan = DayPlan.build(state.tasks, state.todayEvents, state.today)
    return buildString {
        appendLine("วันนี้ ${state.today}")
        if (state.profile.exists) {
            appendLine("[โปรไฟล์]")
            appendLine(state.profile.render().lines().filter { it.startsWith("- ") }.joinToString("\n"))
        }
        plan.forEach { s ->
            appendLine("[${s.part.label}]")
            s.items.forEach { item ->
                when (item) {
                    is DayPlan.Item.TaskItem -> appendLine("- งาน: ${item.task.raw.trim()}")
                    is DayPlan.Item.EventItem -> appendLine("- นัด: ${item.event.title} ${if (item.event.allDay) "ทั้งวัน" else item.event.begin.format(hm) + " ถึง " + item.event.end.format(hm)}")
                }
            }
        }
        appendLine("[งานที่ยังไม่เสร็จทั้งหมด]")
        state.tasks.filter { it.isOpen }.take(60).forEach { appendLine(it.raw.trim()) }
    }
}
