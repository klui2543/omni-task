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
import app.omnitask.model.tr

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
                    Text(tr("งาน", "Tasks"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = C.text)
                    SquareButton(Ic.search, tr("ค้นหา", "Search"), { searching = !searching; if (!searching) vm.setQuery(q.copy(text = "")) })
                }
                if (searching) {
                    BasicTextField(
                        value = q.text,
                        onValueChange = { vm.setQuery(q.copy(text = it)) },
                        singleLine = true,
                        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                        cursorBrush = SolidColor(C.accent),
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                            .border(1.dp, C.cardBorder, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            if (q.text.isEmpty()) Text(tr("ค้นหาชื่องาน", "Search task titles"), color = C.faint, fontSize = TS.body)
                            inner()
                        },
                    )
                }
                FilterBar(
                    state, vm, onFilter = { sheet = Sheet.FILTER }, showSort = true,
                    onGroup = { sheet = Sheet.GROUP }, onSort = { sheet = Sheet.SORT }, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (groups.isEmpty()) {
            item { Card { Text(tr("ไม่มีงานตรงตัวกรอง", "No tasks match the filters"), Modifier.padding(16.dp), color = C.muted) } }
        }
        groups.forEach { g ->
            item(key = "g:" + g.label) {
                Card {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(g.tone.color))
                        Text(g.label, Modifier.weight(1f).padding(start = 8.dp), color = g.tone.color, style = MaterialTheme.typography.titleSmall)
                        Text("${g.tasks.size}", color = C.muted, fontSize = TS.caption)
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
        Sheet.GROUP -> OptionSheet(tr("จัดกลุ่มตาม", "Group by"), GroupBy.entries.map { it to it.label }, q.groupBy, { vm.setQuery(q.copy(groupBy = it)) }) { sheet = null }
        Sheet.SORT -> SortSheet(q, vm::setQuery) { sheet = null }
        null -> Unit
    }
}

/** Quick views, then filter, group and sort buttons. Shared by the task list and every view. */
@Composable
fun FilterBar(
    state: UiState,
    vm: TaskViewModel,
    onFilter: () -> Unit,
    showSort: Boolean,
    modifier: Modifier = Modifier,
    onGroup: (() -> Unit)? = null,
    onSort: (() -> Unit)? = null,
) {
    val q = state.query
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskQuery.SAVED.forEachIndexed { i, (label, _) ->
                val on = state.savedView == i
                Text(
                    label,
                    Modifier.height(34.dp).clip(CircleShape).background(if (on) C.accent else C.card)
                        .border(1.dp, if (on) C.accent else C.cardBorder, CircleShape)
                        .clickable { vm.pickSavedView(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (on) C.onAccent else C.text2, fontSize = TS.body,
                )
            }
        }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val active = q.activeFilters + if (q.text.isNotBlank()) 1 else 0
            ToolButton(if (active > 0) tr("กรอง $active", "Filter $active") else tr("กรอง", "Filter"), active > 0, Ic.filter, onFilter)
            if (onGroup != null) ToolButton(tr("กลุ่ม: ", "Group: ") + q.groupBy.label, false, onClick = onGroup)
            if (showSort) {
                val extra = if (q.sorts.size > 1) " +${q.sorts.size - 1}" else ""
                ToolButton(tr("เรียง: ", "Sort: ") + "${q.sortBy.label} ${if (q.ascending) "↑" else "↓"}$extra", false, onClick = onSort ?: { vm.setQuery(q.copy(ascending = !q.ascending)) })
            }
        }
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
        Text(label, color = if (active) C.accentText else C.text2, fontSize = TS.body)
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
fun FilterSheet(state: UiState, vm: TaskViewModel, onDismiss: () -> Unit) {
    val q = state.query
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("กรองงาน", "Filter tasks"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("ล้างทั้งหมด", "Clear all"),
                Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    vm.setQuery(q.copy(statuses = TaskQuery.DEFAULT.statuses, priorities = emptySet(), tags = emptySet(), notes = emptySet(), bucket = null))
                }.padding(8.dp),
                color = C.accentText,
            )
        }
        Label(tr("สถานะ", "Status"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Status.entries.forEach { s -> Chip(s.label, s in q.statuses, { vm.setQuery(q.copy(statuses = q.statuses.toggle(s))) }) }
        }
        Label(tr("ความสำคัญ", "Priority"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Priority.entries.forEach { p -> Chip(p.label, p in q.priorities, { vm.setQuery(q.copy(priorities = q.priorities.toggle(p))) }, dot = p.tint) }
        }
        Label(tr("ช่วงวันที่", "Date range"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("ทุกวัน", "Any date"), q.bucket == null, { vm.setQuery(q.copy(bucket = null)) })
            DateBucket.entries.forEach { b -> Chip(b.label, q.bucket == b, { vm.setQuery(q.copy(bucket = if (q.bucket == b) null else b)) }) }
        }
        val tags = state.tags.filterNot { it.startsWith("remind-at-") }
        if (tags.isNotEmpty()) {
            Label(tr("Tag (ตรงอย่างน้อยหนึ่ง)", "Tags (match any)"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t -> Chip("#$t", t in q.tags, { vm.setQuery(q.copy(tags = q.tags.toggle(t))) }) }
            }
        }
        if (state.notes.size > 1) {
            Label(tr("โน้ต", "Notes"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.notes.forEach { n -> Chip(n, n in q.notes, { vm.setQuery(q.copy(notes = q.notes.toggle(n))) }) }
            }
        }
        val count = state.tasks.count { q.matches(it, state.today) }
        PrimaryButton(tr("แสดง $count งาน", "Show $count tasks"), onDismiss, Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

/** Sort by several levels: the first decides, each next one breaks ties. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortSheet(q: TaskQuery, onChange: (TaskQuery) -> Unit, onDismiss: () -> Unit) {
    val levels = q.sorts
    fun set(list: List<Pair<SortBy, Boolean>>) {
        if (list.isEmpty()) return
        onChange(q.copy(sortBy = list.first().first, ascending = list.first().second, thenBy = list.drop(1)))
    }
    SheetFrame(onDismiss) {
        Text(tr("เรียงลำดับ", "Sort"), style = MaterialTheme.typography.titleMedium)
        levels.forEachIndexed { i, (by, asc) ->
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (i == 0) tr("เรียงตาม", "Sort by") else tr("แล้วตามด้วย", "Then by"), Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
                    Text(
                        if (asc) tr("↑ น้อยไปมาก", "↑ Ascending") else tr("↓ มากไปน้อย", "↓ Descending"),
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { set(levels.toMutableList().also { it[i] = by to !asc }) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = C.accentText, fontSize = TS.caption,
                    )
                    if (levels.size > 1) SquareButton(Ic.close, tr("เอาออก", "Remove"), { set(levels.filterIndexed { j, _ -> j != i }) })
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // A field already used by another level is left out, so each level adds something.
                    SortBy.entries.filter { it == by || levels.none { l -> l.first == it } }.forEach { f ->
                        Chip(f.label, f == by, { set(levels.toMutableList().also { it[i] = f to asc }) })
                    }
                }
            }
        }
        if (levels.size < 3) {
            val next = SortBy.entries.firstOrNull { f -> levels.none { it.first == f } }
            if (next != null) {
                GhostButton(tr("+ เรียงต่อด้วย", "+ Then by"), { set(levels + (next to true)) }, Modifier.padding(top = 12.dp).fillMaxWidth())
            }
        }
        Text(tr("ถ้ายังเท่ากัน เรียงตามความสำคัญ แล้วชื่องาน", "Ties fall back to priority, then title"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
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
        Text(label, Modifier.weight(1f), color = C.text, fontSize = TS.body)
        if (on) Icon(Ic.check, null, tint = C.accent, modifier = Modifier.size(18.dp))
    }
    Divider()
}

@Composable
fun Label(text: String) {
    Text(text, Modifier.padding(top = 18.dp, bottom = 8.dp), color = C.muted, fontSize = TS.body)
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v
