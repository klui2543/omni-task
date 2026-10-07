package app.omnitask.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.DateBucket
import app.omnitask.model.GroupBy
import app.omnitask.model.Priority
import app.omnitask.model.Projects
import app.omnitask.model.SortBy
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.TaskQuery
import app.omnitask.model.label

private enum class Sheet { FILTER, GROUP, SORT }

@Composable
fun TasksScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val q = state.query
    val groups = q.run(state.tasks, state.today)
    val blocked = Projects.blocked(state.tasks)
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var searching by remember { mutableStateOf(q.text.isNotEmpty()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("งาน", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = C.text)
                    SquareButton(Ic.search, "ค้นหา", { searching = !searching; if (!searching) vm.setQuery(q.copy(text = "")) })
                }
                if (searching) {
                    BasicTextField(
                        value = q.text,
                        onValueChange = { vm.setQuery(q.copy(text = it)) },
                        singleLine = true,
                        textStyle = TextStyle(color = C.text, fontSize = 15.sp, fontFamily = Prompt),
                        cursorBrush = SolidColor(C.accent),
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                            .border(1.dp, C.cardBorder, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            if (q.text.isEmpty()) Text("ค้นหาชื่องาน", color = C.faint, fontSize = 15.sp)
                            inner()
                        },
                    )
                }
                Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskQuery.SAVED.forEachIndexed { i, (label, _) ->
                        val on = state.savedView == i
                        Text(
                            label,
                            Modifier.height(34.dp).clip(CircleShape).background(if (on) C.accent else C.card)
                                .border(1.dp, if (on) C.accent else C.cardBorder, CircleShape)
                                .clickable { vm.pickSavedView(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
                            color = if (on) C.onAccent else C.text2, fontSize = 13.sp,
                        )
                    }
                }
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolButton(if (q.activeFilters > 0) "กรอง ${q.activeFilters}" else "กรอง", q.activeFilters > 0, Ic.filter) { sheet = Sheet.FILTER }
                    ToolButton("กลุ่ม: ${q.groupBy.label}", false) { sheet = Sheet.GROUP }
                    ToolButton("เรียง: ${q.sortBy.label} ${if (q.ascending) "↑" else "↓"}", false) { sheet = Sheet.SORT }
                }
            }
        }

        if (groups.isEmpty()) {
            item { Card { Text("ไม่มีงานตรงตัวกรอง", Modifier.padding(16.dp), color = C.muted) } }
        }
        groups.forEach { g ->
            item(key = "g:" + g.label) {
                Card {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(g.tone.color))
                        Text(g.label, Modifier.weight(1f).padding(start = 8.dp), color = g.tone.color, style = MaterialTheme.typography.titleSmall)
                        Text("${g.tasks.size}", color = C.muted, fontSize = 12.5.sp)
                    }
                    g.tasks.forEachIndexed { i, t ->
                        if (i > 0) Divider(start = 48.dp)
                        TaskRow(
                            t, state.today, { vm.toggleDone(t) }, { onOpen(t) },
                            blockedBy = if (t in blocked) Projects.waitingOn(t, state.tasks) else null,
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        Sheet.FILTER -> FilterSheet(state, vm) { sheet = null }
        Sheet.GROUP -> OptionSheet("จัดกลุ่มตาม", GroupBy.entries.map { it to it.label }, q.groupBy, { vm.setQuery(q.copy(groupBy = it)) }) { sheet = null }
        Sheet.SORT -> SortSheet(q, vm::setQuery) { sheet = null }
        null -> Unit
    }
}

@Composable
private fun ToolButton(label: String, active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (active) C.accentDeep else C.card)
            .border(1.dp, if (active) Color(0xFF2D2852) else C.cardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (active) C.accentText else C.text2, modifier = Modifier.size(14.dp))
        Text(label, color = if (active) C.accentText else C.text2, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.card, contentColor = C.text, scrimColor = Color(0x99000000)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    val q = state.query
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("กรองงาน", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                "ล้างทั้งหมด",
                Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    vm.setQuery(q.copy(statuses = TaskQuery.DEFAULT.statuses, priorities = emptySet(), tags = emptySet(), notes = emptySet(), bucket = null))
                }.padding(8.dp),
                color = C.accentText,
            )
        }
        Label("สถานะ")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Status.entries.forEach { s -> Chip(s.label, s in q.statuses, { vm.setQuery(q.copy(statuses = q.statuses.toggle(s))) }) }
        }
        Label("ความสำคัญ")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Priority.entries.forEach { p -> Chip(p.label, p in q.priorities, { vm.setQuery(q.copy(priorities = q.priorities.toggle(p))) }, dot = p.tint) }
        }
        Label("ช่วงวันที่")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("ทุกวัน", q.bucket == null, { vm.setQuery(q.copy(bucket = null)) })
            DateBucket.entries.forEach { b -> Chip(b.label, q.bucket == b, { vm.setQuery(q.copy(bucket = if (q.bucket == b) null else b)) }) }
        }
        val tags = state.tags.filterNot { it.startsWith("remind-at-") }
        if (tags.isNotEmpty()) {
            Label("Tag (ตรงอย่างน้อยหนึ่ง)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t -> Chip("#$t", t in q.tags, { vm.setQuery(q.copy(tags = q.tags.toggle(t))) }) }
            }
        }
        if (state.notes.size > 1) {
            Label("โน้ต")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.notes.forEach { n -> Chip(n, n in q.notes, { vm.setQuery(q.copy(notes = q.notes.toggle(n))) }) }
            }
        }
        val count = state.tasks.count { q.matches(it, state.today) }
        PrimaryButton("แสดง $count งาน", onDismiss, Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

@Composable
private fun SortSheet(q: TaskQuery, onChange: (TaskQuery) -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss) {
        Text("เรียงลำดับ", style = MaterialTheme.typography.titleMedium)
        Segmented(listOf(true to "↑ น้อยไปมาก", false to "↓ มากไปน้อย"), q.ascending, { onChange(q.copy(ascending = it)) }, Modifier.padding(top = 12.dp).fillMaxWidth())
        SortBy.entries.forEach { s -> OptionRow(s.label, s == q.sortBy) { onChange(q.copy(sortBy = s)) } }
        Text("ลำดับรอง: ความสำคัญ แล้วตามด้วยชื่องาน", Modifier.padding(top = 10.dp), color = C.muted, fontSize = 12.5.sp)
    }
}

@Composable
fun <T> OptionSheet(title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { (v, label) -> OptionRow(label, v == selected) { onPick(v); onDismiss() } }
    }
}

@Composable
private fun OptionRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = C.text, fontSize = 15.sp)
        if (on) Icon(Ic.check, null, tint = C.accent, modifier = Modifier.size(18.dp))
    }
    Divider()
}

@Composable
fun Label(text: String) {
    Text(text, Modifier.padding(top = 18.dp, bottom = 8.dp), color = C.muted, fontSize = 13.sp)
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v
