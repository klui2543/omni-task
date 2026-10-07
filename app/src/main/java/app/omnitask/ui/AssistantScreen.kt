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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val EXAMPLES = listOf(
    "อยากไปวิ่งสัปดาห์นี้ ควรไปตอนไหนดี",
    "อยากเขียน proposal 2 ชม. ควรทำตอนไหน",
    "ต้องไปธนาคาร พรุ่งนี้ตอนไหนดี",
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
            context.startActivity(Intent.createChooser(share, "ส่งให้ Claude"))
        }
    }

    val list = rememberLazyListState()
    LaunchedEffect(state.chat.size) { if (state.chat.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }
    val profile = state.profile

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ผู้ช่วย", style = MaterialTheme.typography.headlineSmall, color = C.text)
                Text(sources(state), color = C.muted, fontSize = 12.5.sp)
            }
            if (state.chat.isNotEmpty()) SquareButton(Ic.refresh, "เริ่มใหม่", vm::clearChat)
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
                    AskCard("ชีวิตช่วงนี้ยังเหมือนเดิมไหม?", "ไม่ได้ทบทวนมา 30 วัน", "ตื่น ${Profile.hm(profile.wake)} นอน ${Profile.hm(profile.sleep)} สมองดี ${Profile.hm(profile.focusFrom)} ถึง ${Profile.hm(profile.focusTo)} ออกกำลังกาย ${Profile.hm(profile.exercise)}",
                        "ปรับ", { interviewing = true }, "ยังเหมือนเดิม", { vm.saveProfile(profile) })
                }
                state.insight != null -> item(key = "insight") {
                    val ask = state.insight
                    AskCard("จำไว้ไหม?", ask.window, ask.text, "ไม่ใช่", { vm.answerInsight(false) }, "ใช่ จำไว้", { vm.answerInsight(true) })
                }
            }

            if (state.chat.isEmpty()) {
                item(key = "examples") {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text("ถามได้ว่าอยากทำอะไร แล้วผู้ช่วยจะหาช่วงที่เหมาะให้ 3 ช่วง พร้อมเหตุผล", color = C.text2, fontSize = 13.5.sp, lineHeight = 20.sp)
                            EXAMPLES.forEach { q ->
                                Text(
                                    q,
                                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken)
                                        .clickable { send(q) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    color = C.accentText, fontSize = 13.5.sp,
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
                        val morning = state.notify.digestTimes.firstOrNull()?.let { "แผนตอนเช้า ${Profile.hm(it)}" } ?: "ตามงานและนัดวันนี้"
                        QuickCard("จัดลำดับวันนี้", morning, Modifier.weight(1f), vm::planToday)
                        QuickCard("ทบทวนสัปดาห์", if (state.notify.weeklyReview) "ทุกอาทิตย์ 20:00" else "สรุป 7 วันที่ผ่านมา", Modifier.weight(1f), vm::reviewWeek)
                    }
                    QuickCard("ถาม Claude", "ส่งงานและนัดวันนี้ไปที่แอป Claude ใช้ subscription ไม่ต้องจ่าย API", Modifier.fillMaxWidth()) {
                        askClaude("ช่วยจัดลำดับงานวันนี้ให้หน่อย ตามเวลาว่างในปฏิทิน")
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
                textStyle = TextStyle(color = C.text, fontSize = 14.sp, fontFamily = Prompt),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 3,
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) Text("อยากทำอะไร หรือถามเรื่องงาน", color = C.faint, fontSize = 14.sp)
                        inner()
                    }
                },
            )
            Box(
                Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).background(C.accent).clickable { send() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.send, "ส่ง", tint = C.onAccent, modifier = Modifier.size(17.dp)) }
        }
    }
}

private fun sources(state: UiState): String {
    val parts = buildList {
        if (state.profile.exists) add("โปรไฟล์.md")
        add("งาน ${state.tasks.count { it.isOpen }} รายการ")
        if (state.calendarAccess == true) add("Google Calendar")
    }
    return "อ่านจาก " + parts.dropLast(1).joinToString(", ").let { if (it.isEmpty()) parts.last() else "$it และ ${parts.last()}" }
}

@Composable
private fun AskCard(title: String, badge: String, body: String, noLabel: String, onNo: () -> Unit, yesLabel: String, onYes: () -> Unit) {
    Card(color = C.accentDeep, border = Color(0xFF2D2852)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.spark, null, tint = C.accentText, modifier = Modifier.size(16.dp))
                Text(title, Modifier.weight(1f).padding(start = 8.dp), color = C.accentText, fontSize = 12.5.sp)
                Text(badge, color = C.muted, fontSize = 12.5.sp)
            }
            Text(body, Modifier.padding(top = 8.dp), color = C.text, fontSize = 14.sp, lineHeight = 21.sp)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF3A3466), RoundedCornerShape(10.dp)).clickable(onClick = onNo),
                    contentAlignment = Alignment.Center,
                ) { Text(noLabel, color = C.accentText, fontSize = 12.5.sp) }
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.accent).clickable(onClick = onYes),
                    contentAlignment = Alignment.Center,
                ) { Text(yesLabel, color = C.onAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Medium) }
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
                Text(if (first) "ขอรู้จักกันก่อน" else "ปรับโปรไฟล์", Modifier.weight(1f).padding(start = 8.dp), color = C.accentText, fontSize = 12.5.sp)
                Text("4 ข้อ", color = C.muted, fontSize = 12.5.sp)
            }
            Text(
                "คำตอบจะเก็บใน ${Profile.PATH} แก้ใน Obsidian ได้ และผู้ช่วยจะถามใหม่ทุกเดือนเพราะชีวิตเปลี่ยนได้",
                Modifier.padding(top = 6.dp), color = C.text2, fontSize = 13.sp, lineHeight = 19.sp,
            )
            fun times(vararg hm: String) = hm.map { LocalTime.parse(it) }
            Question("ปกติตื่นกี่โมง", times("05:30", "06:00", "06:30", "07:00", "08:00"), p.wake) { p = p.copy(wake = it) }
            Question("เข้านอนกี่โมง", times("21:30", "22:00", "22:30", "23:00", "23:30"), p.sleep) { p = p.copy(sleep = it) }
            Text("ช่วงไหนสมองดีที่สุด", Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.text, fontSize = 13.5.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("08:00" to "11:00", "10:00" to "13:00", "13:00" to "16:00", "19:00" to "22:00").forEach { (a, b) ->
                    val from = LocalTime.parse(a)
                    Chip("$a ถึง $b", p.focusFrom == from, { p = p.copy(focusFrom = from, focusTo = LocalTime.parse(b)) })
                }
            }
            Question("ชอบออกกำลังกายตอนไหน", times("06:00", "07:00", "17:30", "18:30", "20:00"), p.exercise) { p = p.copy(exercise = it) }
            Text(
                "เวรอ่านจากนัดใน Google Calendar ที่มีคำว่า \"${p.shiftWords.joinToString("\", \"")}\" และเวรดึกจากคำว่า \"${p.nightWords.joinToString("\", \"")}\"",
                Modifier.padding(top = 12.dp), color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCancel != null) GhostButton("ยกเลิก", onCancel, Modifier.weight(1f))
                PrimaryButton("บันทึกลงโปรไฟล์", { onSave(p) }, Modifier.weight(1.4f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Question(title: String, options: List<LocalTime>, selected: LocalTime, onPick: (LocalTime) -> Unit) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.text, fontSize = 13.5.sp)
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
            color = C.onAccent, fontSize = 14.sp, lineHeight = 20.sp,
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
            Text(plan.intro, color = C.text, fontSize = 14.sp, lineHeight = 21.sp)
            if (shown.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ชื่องาน", Modifier.width(56.dp), color = C.muted, fontSize = 12.5.sp)
                    BasicTextField(
                        value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = C.text, fontSize = 14.sp, fontFamily = Prompt), cursorBrush = SolidColor(C.accent),
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
                            Text(slot.dayLabel, color = if (on) C.accentText else C.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(Profile.hm(slot.start), color = C.muted, fontSize = 12.5.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(slot.title, color = C.text, fontSize = 13.5.sp)
                                if (index == 0) {
                                    Text(
                                        "แนะนำ",
                                        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF2B3A1A)).padding(horizontal = 7.dp, vertical = 1.dp),
                                        color = C.lime, fontSize = 12.sp,
                                    )
                                }
                            }
                            Text(slot.why, Modifier.padding(top = 2.dp), color = C.muted, fontSize = 12.5.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
            if (shown.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (plan.slots.size > 3) GhostButton("ดูช่วงอื่น", onMore, Modifier.weight(1f))
                    PrimaryButton(
                        item.done ?: "สร้างงาน + ลงปฏิทิน",
                        { if (item.done == null && title.isNotBlank()) onConfirm(title.trim()) },
                        Modifier.weight(1.4f), color = if (item.done != null) C.lime else C.accent,
                    )
                }
            }
            Text(
                "ให้ Claude ช่วยคิดแทน",
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClaude).padding(vertical = 6.dp, horizontal = 2.dp),
                color = C.accentText, fontSize = 12.5.sp,
            )
        }
    }
}

@Composable
private fun TodayCard(tasks: List<Task>, state: UiState, onOpen: (Task) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                if (tasks.isEmpty()) "วันนี้ไม่มีงานเร่ง ลองหยิบงานลงทุนอนาคตสักงาน" else "เรียงจากต้องทำก่อน ไปคนที่รอ แล้วค่อยงานเพื่ออนาคต",
                Modifier.padding(horizontal = 14.dp), color = C.text, fontSize = 14.sp, lineHeight = 21.sp,
            )
            tasks.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", Modifier.width(22.dp), color = C.accentText, fontSize = 14.sp)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = C.text, fontSize = 14.sp)
                        reason(t, state).takeIf { it.isNotEmpty() }?.let { Text(it, color = C.muted, fontSize = 12.5.sp) }
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
            item.lines.forEach { Text(it, Modifier.padding(top = 6.dp), color = C.text2, fontSize = 13.5.sp) }
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, color = C.text, fontSize = 13.5.sp)
        Text(subtitle, color = C.muted, fontSize = 12.5.sp)
    }
}

private fun reason(t: Task, state: UiState): String {
    val b = state.brief
    return when {
        t.due?.let { it < state.today } == true -> "เลยกำหนดแล้ว"
        t.due == state.today -> "ครบวันนี้"
        t.scheduled == state.today -> "นัดทำวันนี้"
        t in b.waiting -> (Focus.waitingFor(t) ?: "มีคน") + " รอ " + (Focus.ageDays(t, state.today)?.let { "$it วัน" } ?: "อยู่")
        t in b.future -> "ลงทุนอนาคต ไม่มีเดดไลน์แต่สำคัญ"
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
