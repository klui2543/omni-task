package app.omnitask.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.label

private val RING = listOf(C.accent, C.amber, C.tealChip, C.blue, C.red)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val projects = Projects.build(state.tasks, state.today)
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val open = projects.firstOrNull { it.name == openName }
    BackHandler(enabled = open != null) { openName = null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (open != null) {
                    SquareButton(Ic.back, "กลับ", { openName = null })
                    Box(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(open?.name ?: "โปรเจกต์", style = MaterialTheme.typography.headlineSmall, color = C.text)
                    Text(
                        if (open != null) "ติ๊กงานต้นทางเพื่อปลดล็อกงานที่รออยู่" else "โปรเจกต์มาจาก Tag แรกของงาน",
                        color = C.muted, fontSize = TS.caption,
                    )
                }
            }
        }

        if (open == null) {
            if (projects.isEmpty()) item { Card { Text("ยังไม่มีงานที่ติด Tag", Modifier.padding(16.dp), color = C.muted) } }
            projects.forEachIndexed { i, p ->
                item(key = p.name) {
                    Card(Modifier.clickable { openName = p.name }) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressRing(p.ratio, 52.dp, 5.dp, RING[i % RING.size], "${(p.ratio * 100).toInt()}%", 12)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, color = C.text)
                                    Text("เสร็จ ${p.done} จาก ${p.tasks.size} งาน", color = C.muted, fontSize = TS.caption)
                                }
                            }
                            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                p.next?.let { Pill("ถัดไป: ${it.title}", C.raised, C.text2) }
                                if (p.overdue > 0) Pill("เลยกำหนด ${p.overdue}", C.redSoft, C.red)
                                if (p.blocked.isNotEmpty()) Pill("ติดรองานอื่น ${p.blocked.size}", C.amberSoft, C.amber, Ic.lock)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${(open.ratio * 100).toInt()}%", "เสร็จแล้ว", C.lime, Modifier.weight(1f))
                    StatCard("${open.overdue}", "เลยกำหนด", if (open.overdue > 0) C.red else C.text, Modifier.weight(1f))
                    StatCard("${open.blocked.size}", "ติดรองานอื่น", if (open.blocked.isNotEmpty()) C.amber else C.text, Modifier.weight(1f))
                }
            }
            listOf(Status.IN_PROGRESS, Status.TODO, Status.DONE).forEach { status ->
                val list = open.tasks.filter { it.status == status }.sortedBy { it.due ?: it.scheduled ?: java.time.LocalDate.MAX }
                if (list.isNotEmpty()) {
                    item(key = "s:" + status.name) {
                        Card {
                            SectionHead(status.label, if (status == Status.IN_PROGRESS) C.accentText else C.text2, "${list.size}")
                            list.forEachIndexed { i, t ->
                                if (i > 0) Divider(start = 48.dp)
                                TaskRow(
                                    t, state.today, { vm.toggleDone(t) }, { onOpen(t) },
                                    blockedBy = if (t in open.blocked) Projects.waitingOn(t, state.tasks) else null,
                                )
                            }
                            Box(Modifier.height(4.dp))
                        }
                    }
                }
            }
            item {
                Text(
                    "Milestone จะมาพร้อมรูปแบบไฟล์ dotpm ส่วนงานที่ต้องรองานอื่นใช้ 🆔 และ ⛔ แบบปลั๊กอิน Tasks",
                    Modifier.padding(horizontal = 6.dp), color = C.faint, fontSize = TS.caption,
                )
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = TS.stat, fontWeight = FontWeight.Medium, color = color)
            Text(label, fontSize = TS.caption, color = C.muted)
        }
    }
}
