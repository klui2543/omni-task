@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.omnitask.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.omnitask.data.CalendarReader
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Planner
import app.omnitask.model.Profile
import app.omnitask.model.Task
import app.omnitask.model.StuckLog
import app.omnitask.model.tr
import kotlinx.datetime.DayOfWeek
import java.time.Instant
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import app.omnitask.time.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

/**
 * The assistant from the C+ mockup: it reads the profile note, the tasks and Google Calendar,
 * answers "when should I do X" with ranked slots, and asks before remembering anything.
 * Everything here is rule-based; "ถาม Claude" hands the picture to the Claude app on the owner's subscription.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val eventActions = rememberEventActions(vm)
    var input by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
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
        // Gemini answers when a key is in; without one the rules do, as before.
        vm.askAi(text.trim())
        input = ""
    }
    // Hands text to the Claude app, on the owner's subscription.
    fun shareToClaude(text: String) {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            context.startActivity(Intent(share).setPackage("com.anthropic.claude"))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(share, tr("ส่งให้ Claude", "Send to Claude")))
        }
    }
    val inputFocus = remember { FocusRequester() }

    // The overview: Timeline or tree, whose work, and the task picked in it. The brief is read on opening.
    var view by rememberSaveable { mutableStateOf(AiView.TIMELINE) }
    var who by rememberSaveable { mutableStateOf(TimelineWho.ALL) }
    var staleOnly by rememberSaveable { mutableStateOf(false) }
    var selKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.ai.hasKey, state.tasks.isNotEmpty()) { vm.aiBrief() }
    val picks = remember(state.ai.brief, state.tasks) { currentPicks(state.ai.brief, state.tasks) }
    val picked = selKey?.let { k -> state.tasks.firstOrNull { it.key == k } }
    val stuckCount = remember(state.tasks, state.today) { state.tasks.count { !it.isSubtask && (it.start ?: it.scheduled ?: it.due) != null && needsLook(it, state) } }
    fun askClaude(question: String) {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, question + "\n\n" + snapshot(state, vm.habits()))
        try {
            context.startActivity(Intent(share).setPackage("com.anthropic.claude"))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(share, tr("ส่งให้ Claude", "Send to Claude")))
        }
    }

    // Two tabs: the overview of the work, and the conversation. Asking something moves to the conversation.
    var tab by rememberSaveable { mutableStateOf(if (state.chat.isEmpty()) AiTab.OVERVIEW else AiTab.CHAT) }
    var seenChat by rememberSaveable { mutableStateOf(state.chat.size) }
    var picking by remember { mutableStateOf(false) }
    var briefOpen by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(state.chat.size, state.ai.thinking) {
        if (state.chat.size > seenChat) tab = AiTab.CHAT
        seenChat = state.chat.size
        if (tab == AiTab.CHAT && state.chat.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    val profile = state.profile
    if (picking) AiModelSheet(state.ai, vm::loadAiModels, vm::setAiModel) { picking = false }

    Column(Modifier.fillMaxSize()) {
        // One line: the title, the two tabs beside it, and starting over once there is a conversation.
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("ผู้ช่วย", "Assistant"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = C.text)
            if (state.chat.isNotEmpty()) SquareButton(Ic.refresh, tr("เริ่มใหม่", "Start over"), vm::clearChat)
            Segmented(
                listOf(AiTab.OVERVIEW to tr("ภาพรวม", "Overview"), AiTab.CHAT to tr("แชต", "Chat") + state.chat.count { it is Chat.Asked }.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()),
                tab, { tab = it }, Modifier.padding(start = 8.dp).width(190.dp),
            )
        }

        // The overview fills the page without scrolling: a one-line brief, the switch and chips, and the map taking
        // the rest, with the picked task's card floating over the map's lower edge, under the thumb.
        val insight = state.insight
        val askCard: (@Composable () -> Unit)? = when {
            profile.stale(state.today) -> {
                {
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
            }
            insight != null -> {
                {
                    val ask = insight
                    AskCard(
                        tr("จำไว้ไหม?", "Remember this?"), ask.window, ask.text,
                        tr("ไม่ใช่", "No"), { vm.answerInsight(false) }, tr("ใช่ จำไว้", "Yes, remember"), { vm.answerInsight(true) },
                    )
                }
            }
            else -> null
        }
        if (tab == AiTab.OVERVIEW && profile.exists && !interviewing) Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            askCard?.invoke()
            AiBriefLine(state.ai, picks.size, briefOpen, { briefOpen = !briefOpen }, { vm.aiBrief(force = true) }, onSettings)
            AiViewBar(view, { view = it }, who, staleOnly, stuckCount, { who = it; staleOnly = false }, { staleOnly = !staleOnly })
            AiMap(
                view, state, vm, onOpen, who, staleOnly, picks.keys, selKey, { selKey = it?.key },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                picked?.let { t ->
                    AiTaskCard(
                        t, picks[t.key], state,
                        onAsk = {
                            input = tr("เรื่องงาน \"${t.title}\" ควรทำอย่างไรต่อ", "About \"${t.title}\": what should I do next?")
                            runCatching { inputFocus.requestFocus() }
                        },
                        onOpen = { onOpen(t) },
                        onClose = { selKey = null },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        floating = true,
                    )
                }
            }
        } else LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!profile.exists || interviewing) item(key = "interview") {
                Interview(profile, first = !profile.exists, onSave = { vm.saveProfile(it); interviewing = false }, onCancel = if (profile.exists) ({ interviewing = false }) else null)
            }



            if (tab == AiTab.CHAT && profile.exists && !interviewing && state.chat.isEmpty() && !state.ai.thinking) item(key = "chat-empty") {
                Text(
                    if (state.ai.hasKey) tr("ถามหรือสั่งงานได้ที่ช่องด้านล่าง เช่น เพิ่มงาน เลื่อนวัน หรือถามว่าอะไรค้างนานสุด", "Ask or give an order below: add a task, move a date, or ask what has sat longest")
                    else tr("ถามได้ที่ช่องด้านล่าง ใส่ Gemini API key ในตั้งค่าเพื่อให้ผู้ช่วยตอบและแก้งานให้ได้", "Ask below. Add a Gemini API key in Settings for answers and changes from the assistant"),
                    Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp), color = C.muted, fontSize = TS.body,
                )
            }
            if (tab == AiTab.CHAT) itemsIndexed(state.chat) { index, item ->
                when (item) {
                    is Chat.Asked -> Bubble(item.text)
                    is Chat.Slots -> SlotsCard(
                        item, { vm.pickSlot(index, it) }, { vm.moreSlots(index) }, { confirm(index, it) },
                        onCustom = { title, allDay -> custom = CustomRequest(index, title, allDay, item.plan.minutes, item.plan.slots.firstOrNull()?.day ?: state.today) },
                    ) { askClaude(item.plan.request) }
                    is Chat.Today -> TodayCard(item.tasks, state, onOpen)
                    is Chat.Review -> ReviewCard(item)
                    is Chat.Duration -> DurationCard(item) { vm.answerDuration(index, it) }
                    is Chat.Agenda -> AgendaCard(item, state, onOpen, eventActions)
                    is Chat.RangePlan -> RangePlanCard(item, state, onOpen, { vm.toggleRangeCalendar(index) }) { which ->
                        if (item.toCalendar && !CalendarReader.canWrite(context)) {
                            calendarAsk.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                        vm.acceptProposals(index, which)
                    }
                    is Chat.Ranked -> RankedCard(item, onOpen)
                    is Chat.AiAnswer -> AiAnswerCard(item, { i, ok -> vm.answerAiAction(index, i, ok) }) { big -> shareToClaude(app.omnitask.model.AiAssist.forClaude(big, state.today)) }
                }
            }
            if (tab == AiTab.CHAT && state.ai.thinking) item(key = "ai-thinking") { AiThinking(state.ai.searching) }

        }

        val ime = WindowInsets.isImeVisible
        // While the field is typed in, the ready questions float up above it with the keyboard.
        if (typing) {
            PresetRow(askPresets(state, vm), Modifier.padding(top = 6.dp)) { input = ""; focus.clearFocus() }
        } else if (!ime) {
            // Once the conversation has started the big cards give way to this row, so the chat keeps the space.
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
            Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = if (ime) 8.dp else NavClearance - 12.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .then(if (ime) Modifier.imePadding() else Modifier)
                .fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(24.dp))
                .padding(start = if (state.ai.hasKey) 6.dp else 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.ai.hasKey) Box(Modifier.padding(end = 8.dp)) { AiModelChip(state.ai.model) { picking = true } }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).focusRequester(inputFocus).onFocusChanged { typing = it.isFocused },
                textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 3,
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) Text(if (state.ai.hasKey) tr("สั่งหรือถามเรื่องงาน", "Ask, or say what to change") else tr("อยากทำอะไร หรือถามเรื่องงาน", "What do you want to do?"), color = C.faint, fontSize = TS.body, maxLines = 1)
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

/** The assistant's two tabs. */
enum class AiTab { OVERVIEW, CHAT }

/** A ready question, offered while typing to the assistant so the common ones need no typing (and are never misread). */
class AskPreset(val label: String, val run: () -> Unit)

fun askPresets(state: UiState, vm: TaskViewModel): List<AskPreset> {
    val today = state.today
    val tomorrow = today.plusDays(1)
    return listOf(
        AskPreset(tr("วันนี้มีอะไร", "Today")) { vm.showAgenda(today to today, tr("วันนี้มีอะไรบ้าง", "What's on today?")) },
        AskPreset(tr("พรุ่งนี้มีอะไร", "Tomorrow")) { vm.showAgenda(tomorrow to tomorrow, tr("พรุ่งนี้มีอะไรบ้าง", "What's on tomorrow?")) },
        AskPreset(tr("จัดลำดับวันนี้", "Plan today"), vm::planToday),
        AskPreset(tr("จัดลำดับทั้งหมด", "Prioritize all")) { vm.rankAll() },
        AskPreset(tr("สัปดาห์หน้ามีอะไร", "Next week")) { vm.showAgenda(nextWeek(today), tr("สัปดาห์หน้ามีอะไรบ้าง", "What's on next week?")) },
        AskPreset(tr("วางแผนสัปดาห์หน้า", "Plan next week")) { vm.planRange(nextWeek(today), tr("วางแผนสัปดาห์หน้าให้หน่อย", "Plan next week")) },
        AskPreset(tr("เดือนหน้ามีอะไร", "Next month")) { vm.showAgenda(nextMonth(today), tr("เดือนหน้ามีอะไรบ้าง", "What's on next month?")) },
        AskPreset(tr("ทบทวนสัปดาห์", "Weekly review"), vm::reviewWeek),
    )
}

/** The presets as a row of chips that scrolls sideways, shown above a field while it is typed in. */
@Composable
fun PresetRow(presets: List<AskPreset>, modifier: Modifier = Modifier, onPicked: () -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presets.forEach { p -> QuickChip(p.label) { p.run(); onPicked() } }
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
private fun AgendaCard(item: Chat.Agenda, state: UiState, onOpen: (Task) -> Unit, events: EventActions) {
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
                                Modifier.combinedClickable(onClick = { events.open(e) }, onLongClick = { events.inspect(e) }),
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
                    tr("ลงแผน = ตั้งวันนัดทำและเวลาเตือนให้งานนั้น", "Planning sets the task's scheduled date and reminder time"),
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
    val context = LocalContext.current
    val date = rememberDatePickerState(initialSelectedDateMillis = c.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    fun day() = date.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate() } ?: c.day
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = C.raised),
        confirmButton = {
            TextButton(onClick = {
                val d = day()
                if (c.allDay) {
                    onSet(d, null)
                } else {
                    // The day is chosen; the time comes from the phone's own clock dial.
                    onDismiss()
                    pickSystemTime(context, LocalTime.of(9, 0)) { t -> onSet(d, t) }
                }
            }) {
                Text(if (c.allDay) tr("ลงทั้งวัน", "Add all day") else tr("ต่อไป: เลือกเวลา", "Next: time"), color = C.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    ) { DatePicker(state = date, colors = DatePickerDefaults.colors(containerColor = C.raised)) }
}

/** Pick any range, then either look at it or plan it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(onDismiss: () -> Unit, onAgenda: (LocalDate, LocalDate) -> Unit, onPlan: (LocalDate, LocalDate) -> Unit) {
    val range = rememberDateRangePickerState()
    fun d(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate() }
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
        if (state.ai.claudeLog) add("Claude log")
    }
    val and = tr("และ", "and")
    return tr("อ่านจาก ", "Reading ") + parts.dropLast(1).joinToString(", ").let { if (it.isEmpty()) parts.last() else "$it $and ${parts.last()}" }
}

@Composable
private fun AskCard(title: String, badge: String, body: String, noLabel: String, onNo: () -> Unit, yesLabel: String, onYes: () -> Unit) {
    Card(color = C.accentDeep, border = C.accentLine) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = C.accentText, fontSize = TS.caption)
                Text(badge, color = C.muted, fontSize = TS.caption)
            }
            Text(body, Modifier.padding(top = 8.dp), color = C.text, fontSize = TS.body, lineHeight = 1.4.em)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, C.accentLine, RoundedCornerShape(10.dp)).clickable(onClick = onNo),
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
    Card(color = C.accentDeep, border = C.accentLine) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (first) tr("ขอรู้จักกันก่อน", "Getting to know you") else tr("ปรับโปรไฟล์", "Update profile"), Modifier.weight(1f), color = C.accentText, fontSize = TS.caption)
                Text(tr("4 ข้อ", "4 questions"), color = C.muted, fontSize = TS.caption)
            }
            Text(
                tr(
                    "คำตอบจะเก็บใน ${Profile.PATH} แก้ใน Obsidian ได้ และผู้ช่วยจะถามใหม่ทุกเดือนเพราะชีวิตเปลี่ยนได้",
                    "Answers are saved in ${Profile.PATH}, editable in Obsidian. The assistant asks again every month, since life changes.",
                ),
                Modifier.padding(top = 6.dp), color = C.text2, fontSize = TS.body, lineHeight = 1.4.em,
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
                Modifier.padding(top = 12.dp), color = C.muted, fontSize = TS.caption, lineHeight = 1.4.em,
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

private val TIME_TEXT = Regex("""(\d{1,2})[.:](\d{2})""")

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
            color = C.onAccent, fontSize = TS.body, lineHeight = 1.4.em,
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
            Text(plan.intro, color = C.text, fontSize = TS.body, lineHeight = 1.4.em)
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
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) C.accentSoft else C.sunken)
                            .border(1.dp, if (on) C.accent else C.cardBorder, RoundedCornerShape(14.dp))
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
                                        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(9.dp)).background(C.limeSoft).padding(horizontal = 7.dp, vertical = 1.dp),
                                        color = C.lime, fontSize = TS.caption,
                                    )
                                }
                            }
                            Text(slot.why, Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.caption, lineHeight = 1.4.em)
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
                Modifier.padding(horizontal = 14.dp), color = C.text, fontSize = TS.body, lineHeight = 1.4.em,
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

/** A plain-text picture of today for Claude: profile, timeline, the owner's habits ([habits]) and the open list. */
private fun snapshot(state: UiState, habits: List<String>): String {
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
        if (habits.isNotEmpty()) {
            appendLine("[พฤติกรรม 4 สัปดาห์]")
            habits.forEach { appendLine("- $it") }
        }
        appendLine("[งานที่ยังไม่เสร็จทั้งหมด]")
        state.tasks.filter { it.isOpen }.take(60).forEach { appendLine(it.raw.trim()) }
        StuckLog.summary(state.tasks).takeIf { it.isNotEmpty() }?.let { lines ->
            appendLine("[ทำไมงานยังค้าง (บันทึกของเจ้าของ)]")
            lines.forEach { appendLine("- $it") }
        }
    }
}
