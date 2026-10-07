package app.omnitask.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.data.CalendarReader
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Planner
import app.omnitask.model.Profile
import app.omnitask.model.Task
import app.omnitask.model.tr
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
                    is Chat.Slots -> SlotsCard(item, { vm.pickSlot(index, it) }, { vm.moreSlots(index) }, { confirm(index, it) }) { askClaude(item.plan.request) }
                    is Chat.Today -> TodayCard(item.tasks, state, onOpen)
                    is Chat.Review -> ReviewCard(item)
                }
            }

            item(key = "quick") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Question(tr("ปกติตื่นกี่โมง", "When do you usually wake up?"), times("05:30", "06:00", "06:30", "07:00", "08:00"), p.wake) { p = p.copy(wake = it) }
            Question(tr("เข้านอนกี่โมง", "When do you go to bed?"), times("21:30", "22:00", "22:30", "23:00", "23:30"), p.sleep) { p = p.copy(sleep = it) }
            Text(tr("ช่วงไหนสมองดีที่สุด", "When is your mind sharpest?"), Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.text, fontSize = TS.body)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("08:00" to "11:00", "10:00" to "13:00", "13:00" to "16:00", "19:00" to "22:00").forEach { (a, b) ->
                    val from = LocalTime.parse(a)
                    Chip(tr("$a ถึง $b", "$a to $b"), p.focusFrom == from, { p = p.copy(focusFrom = from, focusTo = LocalTime.parse(b)) })
                }
            }
            Question(tr("ชอบออกกำลังกายตอนไหน", "When do you like to exercise?"), times("06:00", "07:00", "17:30", "18:30", "20:00"), p.exercise) { p = p.copy(exercise = it) }
            Text(
                tr(
                    "เวรอ่านจากนัดใน Google Calendar ที่มีคำว่า \"${p.shiftWords.joinToString("\", \"")}\" และเวรดึกจากคำว่า \"${p.nightWords.joinToString("\", \"")}\"",
                    "Shifts are Google Calendar events containing \"${p.shiftWords.joinToString("\", \"")}\", night shifts those containing \"${p.nightWords.joinToString("\", \"")}\"",
                ),
                Modifier.padding(top = 12.dp), color = C.muted, fontSize = TS.caption, lineHeight = 18.sp,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCancel != null) GhostButton(tr("ยกเลิก", "Cancel"), onCancel, Modifier.weight(1f))
                PrimaryButton(tr("บันทึกลงโปรไฟล์", "Save to profile"), { onSave(p) }, Modifier.weight(1.4f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Question(title: String, options: List<LocalTime>, selected: LocalTime, onPick: (LocalTime) -> Unit) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.text, fontSize = TS.body)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { t -> Chip(Profile.hm(t), t == selected, { onPick(t) }) }
    }
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
private fun SlotsCard(item: Chat.Slots, onPick: (Int) -> Unit, onMore: () -> Unit, onConfirm: (String) -> Unit, onClaude: () -> Unit) {
    val plan = item.plan
    var title by remember(plan) { mutableStateOf(plan.title) }
    val first = item.page * 3
    val shown = plan.slots.drop(first).take(3)
    Card {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
            Text(plan.intro, color = C.text, fontSize = TS.body, lineHeight = 21.sp)
            if (shown.isNotEmpty()) {
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
            Text(
                tr("ให้ Claude ช่วยคิดแทน", "Ask Claude instead"),
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClaude).padding(vertical = 6.dp, horizontal = 2.dp),
                color = C.accentText, fontSize = TS.caption,
            )
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
