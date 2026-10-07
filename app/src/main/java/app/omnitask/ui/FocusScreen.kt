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
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    val free = DayPlan.freeMinutes(events, today)
    val blocked = Projects.blocked(state.tasks)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(LONG_DATE), color = C.muted, fontSize = TS.body)
                    Text(greeting(), style = MaterialTheme.typography.headlineSmall, color = C.text)
                }
                SquareButton(Ic.spark, tr("ผู้ช่วย", "Assistant"), onAssistant)
                Box(Modifier.width(8.dp))
                menu()
            }
        }

        item { CalendarConnect(state, vm) }

        item {
            Card {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(if (total == 0) 0f else doneToday.toFloat() / total, 64.dp, 6.dp, label = "$doneToday/$total", labelSize = 15)
                    Row(Modifier.weight(1f).padding(start = 16.dp)) {
                        Stat("$overdue", tr("เลยกำหนด", "Overdue"), if (overdue > 0) C.red else C.text, Modifier.weight(1f))
                        Stat("${events.count { !it.allDay }}", tr("นัดวันนี้", "Events today"), C.text, Modifier.weight(1f))
                        Stat(hours(free), tr("เวลาว่าง", "Free time"), C.lime, Modifier.weight(1f))
                    }
                }
            }
        }

        brief.warnings.forEach { w -> item { Notice(w) } }

        item { ReviewPrompt(state) { reviewing = true } }

        item {
            Card {
                if (plan.isEmpty()) {
                    Text(tr("วันนี้ยังไม่มีงานหรือนัด", "Nothing due or booked today"), Modifier.padding(16.dp), color = C.muted)
                }
                plan.forEach { section ->
                    SectionHead(section.part.label, if (section.part == DayPlan.Part.LATE) C.red else C.text2)
                    section.items.forEach { item ->
                        when (item) {
                            is DayPlan.Item.TaskItem -> TimelineTask(item, state, vm, onOpen, item.task in blocked)
                            is DayPlan.Item.EventItem -> TimelineEvent(item)
                        }
                    }
                }
                Box(Modifier.height(6.dp))
            }
        }

        if (brief.suggestions.isNotEmpty()) {
            item {
                Card {
                    SectionHead(tr("ข้อเสนอ", "Suggestions"), C.accentText)
                    brief.suggestions.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { onOpen(s.task) }.padding(vertical = 4.dp)) {
                                Text(s.task.title, color = C.text, style = MaterialTheme.typography.bodyMedium)
                                Text(s.text, color = C.muted, fontSize = TS.caption)
                            }
                            SquareButton(Ic.close, tr("ไม่เอา", "Dismiss"), { vm.dismissSuggestion(s) })
                            Box(Modifier.width(6.dp))
                            SquareButton(Ic.check, tr("ยอมรับ", "Accept"), { vm.acceptSuggestion(s) }, filled = true)
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("คนรออยู่", "Waiting"), Modifier.weight(1f), color = C.muted, fontSize = TS.body)
                            Text("${brief.waiting.size}", fontSize = TS.stat, fontWeight = FontWeight.Medium, color = C.text)
                        }
                        if (brief.waiting.isEmpty()) {
                            Text(tr("ติด #รอ/ชื่อ ให้งานที่มีคนรอ", "Tag tasks people wait on with #รอ/name"), Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption)
                        }
                        brief.waiting.forEach { t ->
                            val age = Focus.ageDays(t, today)
                            Column(Modifier.padding(top = 8.dp).clickable { onOpen(t) }) {
                                Text(t.title, color = C.text, fontSize = TS.body, maxLines = 2)
                                t.firstStep?.let { Text(tr("ก้าวแรก: $it", "First step: $it"), color = C.accentText, fontSize = TS.caption, maxLines = 2) }
                                Text(
                                    (Focus.waitingFor(t)?.let { "$it " } ?: "") + (age?.let { tr("รอ $it วัน", "waiting $it days") } ?: tr("รออยู่", "waiting")),
                                    color = if ((age ?: 0) >= 7) C.red else C.muted, fontSize = TS.caption,
                                )
                            }
                        }
                    }
                }
                Card(Modifier.weight(1f).fillMaxHeight(), color = C.accentDeep, border = Color(0xFF2D2852)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("ลงทุนอนาคต", "Future"), Modifier.weight(1f), color = C.accentText, fontSize = TS.body)
                            Stepper(state.futureCount, { vm.setFutureCount((state.futureCount + it).coerceIn(1, 5)) })
                        }
                        if (brief.future.isEmpty()) {
                            Text(tr("เลือกงานที่สำคัญต่ออนาคต แต่ไม่มีเดดไลน์", "Pick work that matters for the future but has no deadline"), Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption)
                        }
                        brief.future.forEach { t ->
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(t.title, Modifier.clickable { onOpen(t) }, color = C.text, fontSize = TS.body, maxLines = 2)
                                t.firstStep?.let { Text(tr("ก้าวแรก: $it", "First step: $it"), color = C.accentText, fontSize = TS.caption, maxLines = 2) }
                                Focus.ageDays(t, today)?.let { Text(tr("ค้าง $it วัน", "open $it days"), color = C.muted, fontSize = TS.caption) }
                                Text(
                                    tr("ข้ามวันนี้", "Skip today"),
                                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(C.accentSoft)
                                        .clickable { vm.skipFuture(t) }.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = C.accentText, fontSize = TS.caption,
                                )
                            }
                        }
                        Text(
                            tr("เลือกงาน", "Pick tasks"),
                            Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF3A3466), RoundedCornerShape(12.dp))
                                .clickable { picking = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = C.accentText, fontSize = TS.caption,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("−", Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable { onStep(-1) }.padding(top = 4.dp), color = C.accentText, fontSize = TS.title, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("$value", color = C.text, fontSize = TS.body)
        Text("+", Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable { onStep(1) }.padding(top = 4.dp), color = C.accentText, fontSize = TS.title, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = TS.title, fontWeight = FontWeight.Medium, color = color)
        Text(label, fontSize = TS.caption, color = C.muted)
    }
}

@Composable
private fun TimelineTask(item: DayPlan.Item.TaskItem, state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit, isBlocked: Boolean) {
    val t = item.task
    val today = state.today
    val late = t.isLate(today)
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskCheck(t, { vm.toggleDone(t) })
        Column(Modifier.weight(1f).clickable { onOpen(t) }.padding(vertical = 8.dp)) {
            Text(t.title, color = if (isBlocked) C.muted else C.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val lead = when {
                    item.time != null -> item.time.format(HM)
                    late -> t.due?.let { tr("เลย ${ChronoUnit.DAYS.between(it, today)} วัน", "${ChronoUnit.DAYS.between(it, today)} days late") }
                    t.due == today -> tr("ครบวันนี้", "Due today")
                    else -> tr("นัดวันนี้", "Scheduled today")
                }
                if (lead != null) Text(lead, color = if (late) C.red else C.text, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                val sub = listOfNotNull(if (t.status == Status.IN_PROGRESS) tr("กำลังทำ", "In progress") else null, Projects.projectOf(t)).joinToString(", ")
                if (sub.isNotEmpty()) Text(sub, color = C.muted, fontSize = TS.caption)
            }
        }
    }
}

@Composable
private fun TimelineEvent(item: DayPlan.Item.EventItem) {
    val e = item.event
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(4.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(C.teal))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(e.title, color = Color(0xFFCFF4F0), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (e.allDay) tr("ทั้งวัน", "All day") else tr("${e.begin.format(HM)} ถึง ${e.end.format(HM)}", "${e.begin.format(HM)} to ${e.end.format(HM)}"), color = C.teal, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                Text("Google Calendar", color = C.tealText, fontSize = TS.caption)
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.amberSoft).padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(Ic.bell, null, tint = C.amber, modifier = Modifier.size(18.dp))
        Text(text, Modifier.padding(start = 10.dp), color = C.text, fontSize = TS.body)
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

private fun hours(minutes: Long): String =
    if (minutes >= 60) tr("${minutes / 60} ชม.", "${minutes / 60} h") else tr("$minutes นาที", "$minutes min")
