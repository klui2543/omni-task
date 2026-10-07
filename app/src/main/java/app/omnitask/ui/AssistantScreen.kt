package app.omnitask.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Task
import java.time.format.DateTimeFormatter

/**
 * Until the AI plan is settled (it should run on the owner's Claude subscription, not a paid API key),
 * this screen gives the rule-based plan and hands the full picture to the Claude app in one tap.
 */
@Composable
fun AssistantScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val context = LocalContext.current
    val brief = state.brief
    fun ask(question: String) {
        val text = question + "\n\n" + snapshot(state)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val claude = Intent(send).setPackage("com.anthropic.claude")
        try {
            context.startActivity(claude)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(send, "ส่งให้ Claude"))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp)) {
                Text("ผู้ช่วย", style = MaterialTheme.typography.headlineSmall, color = C.text)
                Text("อ่านงาน ${state.tasks.count { it.isOpen }} รายการ และนัด ${state.todayEvents.size} นัดวันนี้", color = C.muted, fontSize = 12.5.sp)
            }
        }
        item {
            Card(color = C.accentDeep, border = Color(0xFF2D2852)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.spark, null, tint = C.accentText, modifier = Modifier.size(18.dp))
                        Text("ถาม Claude", Modifier.padding(start = 8.dp), color = C.accentText, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "แอปจะเตรียมรายการงานและนัดวันนี้ให้ แล้วเปิดแอป Claude ใช้ subscription ที่คุณมีอยู่ ไม่ต้องจ่ายค่า API",
                        Modifier.padding(top = 6.dp), color = C.text2, fontSize = 13.5.sp,
                    )
                    listOf(
                        "ช่วยจัดลำดับงานวันนี้ให้หน่อย ตามเวลาว่างในปฏิทิน",
                        "สัปดาห์นี้ควรตัดหรือเลื่อนงานไหนออก",
                        "ใครรอผมอยู่บ้าง และควรตอบใครก่อน",
                    ).forEach { q ->
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.accentSoft)
                                .clickable { ask(q) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(q, Modifier.weight(1f), color = C.text, fontSize = 14.sp)
                            Icon(Ic.send, null, tint = C.accentText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        item {
            Card {
                SectionHead("แผนตามกฎในแอป", C.text2)
                val ordered = brief.must + brief.waiting + brief.future
                if (ordered.isEmpty()) Text("ไม่มีงานเร่งด่วนวันนี้", Modifier.padding(16.dp), color = C.muted)
                ordered.distinct().forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", Modifier.width(24.dp), color = C.accentText, fontSize = 14.sp)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = C.text, fontSize = 14.5.sp)
                            Text(reason(t, state), color = C.muted, fontSize = 12.5.sp)
                        }
                    }
                }
                Box(Modifier.height(6.dp))
            }
        }
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

/** A plain-text picture of today for Claude: timeline, waiting, future work and the open list. */
private fun snapshot(state: UiState): String {
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val plan = DayPlan.build(state.tasks, state.todayEvents, state.today)
    return buildString {
        appendLine("วันนี้ ${state.today}")
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
