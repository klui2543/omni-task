package app.omnitask.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.omnitask.model.Focus
import app.omnitask.model.Task

@Composable
fun FocusScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val brief = state.brief
    val colors = MaterialTheme.colorScheme

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (brief.warnings.isNotEmpty()) {
            item(key = "warnings") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    brief.warnings.forEach { Notice(it) }
                }
            }
        }

        if (brief.suggestions.isNotEmpty()) {
            sectionHeader("ข้อเสนอ", brief.suggestions.size.toString())
            items(brief.suggestions, key = { "s:" + it.id }) { s ->
                SuggestionRow(s, onAccept = { vm.acceptSuggestion(s) }, onDismiss = { vm.dismissSuggestion(s) }, onOpen = { onOpen(s.task) })
                HorizontalDivider(Modifier.padding(start = 18.dp), color = colors.outlineVariant)
            }
        }

        sectionHeader("ต้องทำ", "เลยกำหนด / ครบวันนี้")
        taskSection("must", brief.must, state, vm, onOpen, empty = "ไม่มีงานเลยกำหนด")

        sectionHeader("คนรออยู่", "#${Focus.WAITING_TAG}/ชื่อ")
        if (brief.waiting.isEmpty()) {
            emptyLine("waiting", "ยังไม่มีงานที่ติด #${Focus.WAITING_TAG} — ใส่ในหน้าแก้งาน")
        } else {
            items(brief.waiting, key = { "w:" + it.key }) { task ->
                val age = Focus.ageDays(task, state.today)
                val who = Focus.waitingFor(task)
                Column {
                    TaskRow(task, state.today, onToggle = { vm.toggleDone(task) }, onOpen = { onOpen(task) })
                    Text(
                        listOfNotNull(who?.let { "รอ: $it" }, age?.let { "$it วันแล้ว" }).joinToString(" · "),
                        Modifier.padding(start = 52.dp, bottom = 10.dp).padding(top = 0.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if ((age ?: 0) >= 7) colors.error else colors.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(start = 52.dp), color = colors.outlineVariant)
                }
            }
        }

        item(key = "h:future") {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ลงทุนอนาคต", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    Text("สำคัญ · ไม่มีเดดไลน์ · ไม่มีคนรอ", style = MaterialTheme.typography.labelSmall, color = colors.outline)
                }
                IconButton(onClick = { vm.setFutureCount((state.futureCount - 1).coerceAtLeast(1)) }, enabled = state.futureCount > 1) {
                    Text("−", color = if (state.futureCount > 1) colors.onSurfaceVariant else colors.outlineVariant)
                }
                Text(state.futureCount.toString(), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { vm.setFutureCount((state.futureCount + 1).coerceAtMost(5)) }, enabled = state.futureCount < 5) {
                    Icon(Icons.Default.Add, contentDescription = "เพิ่มจำนวน", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp), color = colors.outlineVariant)
        }
        if (brief.future.isEmpty()) {
            emptyLine("future", "ไม่มีงานสำคัญที่ไม่มีเดดไลน์ — ตั้ง priority ⏫ ให้งานเพื่ออนาคต")
        } else {
            items(brief.future, key = { "f:" + it.key }) { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TaskRow(task, state.today, onToggle = { vm.toggleDone(task) }, onOpen = { onOpen(task) })
                    }
                    TextButton(onClick = { vm.skipFuture(task) }) {
                        Text("ข้ามวันนี้", style = MaterialTheme.typography.labelMedium, color = colors.outline)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 52.dp), color = colors.outlineVariant)
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: String, hint: String) {
    item(key = "h:$title") {
        val colors = MaterialTheme.colorScheme
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.outline)
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

private fun LazyListScope.emptyLine(key: String, text: String) {
    item(key = "e:$key") {
        Text(
            text,
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun LazyListScope.taskSection(
    key: String,
    tasks: List<Task>,
    state: UiState,
    vm: TaskViewModel,
    onOpen: (Task) -> Unit,
    empty: String,
) {
    if (tasks.isEmpty()) {
        emptyLine(key, empty)
        return
    }
    items(tasks, key = { "$key:" + it.key }) { task ->
        TaskRow(task, state.today, onToggle = { vm.toggleDone(task) }, onOpen = { onOpen(task) })
        HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Notice(text: String) {
    val colors = MaterialTheme.colorScheme
    val tone = Color(0xFFC08A3E)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
            Text(text, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
    }
}

@Composable
private fun SuggestionRow(s: Focus.Suggestion, onAccept: () -> Unit, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                Text(s.task.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 2)
            }
            Text(s.text, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "ไม่เอา", tint = colors.outline, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onAccept) {
            Icon(Icons.Default.Check, contentDescription = "ยอมรับ", tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}
