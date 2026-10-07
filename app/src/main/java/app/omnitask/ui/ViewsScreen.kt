package app.omnitask.ui

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.Projects
import app.omnitask.model.Quadrant
import app.omnitask.model.GroupBy
import app.omnitask.model.Status
import app.omnitask.model.TaskQuery
import app.omnitask.model.Task
import app.omnitask.model.label
import app.omnitask.model.quadrant
import app.omnitask.notify.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class Mode(val label: String) { KANBAN("Kanban"), MATRIX("Matrix"), GANTT("Gantt"), CALENDAR("ปฏิทิน") }

@Composable
fun ViewsScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(Mode.KANBAN) }
    var filtering by remember { mutableStateOf(false) }
    val q = state.query
    // Every view honours the same filters as the task list. Kanban shows all statuses as its columns,
    // so the status filter only narrows the other views, and only when it differs from the default.
    val pool = state.tasks.filter { q.copy(statuses = emptySet()).matches(it, state.today) }
    val narrowed = if (q.statuses == TaskQuery.DEFAULT.statuses) pool else pool.filter { it.status in q.statuses }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)) {
            Text("มุมมอง", Modifier.padding(start = 4.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall, color = C.text)
            Segmented(Mode.entries.map { it to it.label }, mode, { mode = it }, Modifier.fillMaxWidth())
            FilterBar(state, vm, onFilter = { filtering = true }, showSort = mode == Mode.KANBAN, modifier = Modifier.padding(top = 8.dp))
        }
        when (mode) {
            Mode.KANBAN -> Kanban(state, pool, vm, onOpen)
            Mode.MATRIX -> Matrix(state, narrowed, vm, onOpen)
            Mode.GANTT -> Gantt(state, narrowed, onOpen)
            Mode.CALENDAR -> MonthCalendar(state, narrowed, vm, onOpen)
        }
    }
    if (filtering) FilterSheet(state, vm) { filtering = false }
}

@Composable
private fun Kanban(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val q = state.query
    val columns = listOf(Status.TODO to C.faint, Status.IN_PROGRESS to C.accent, Status.DONE to C.lime)
    val ordered = q.copy(statuses = emptySet(), groupBy = GroupBy.NONE).run(pool, state.today).flatMap { it.tasks }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            Modifier.fillMaxSize().padding(top = 4.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = NavClearance),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(columns) { (status, dot) ->
                val cards = ordered.filter { t ->
                    t.status == status && (status != Status.DONE || t.done?.let { it > state.today.minusDays(7) } == true)
                }
                val (drop, hovering) = rememberTaskDrop { key ->
                    state.tasks.firstOrNull { it.key == key }?.let { if (it.status != status) vm.setStatus(it, status) }
                }
                Column(
                    Modifier.width(272.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                        .background(if (hovering.value) C.accentDeep else C.sunken)
                        .border(1.dp, if (hovering.value) dot else C.divider, RoundedCornerShape(18.dp))
                        .then(drop),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Text(status.label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, color = C.text)
                        Text("${cards.size}", color = C.muted, fontSize = 12.5.sp)
                    }
                    LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cards, key = { it.key }) { t -> KanbanCard(t, state, vm, onOpen) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KanbanCard(t: Task, state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val order = listOf(Status.TODO, Status.IN_PROGRESS, Status.DONE)
    val i = order.indexOf(t.status)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.raised).border(1.dp, Color(0xFF2A2F3E), RoundedCornerShape(14.dp))
            .taskDragSource(t.key, t.priority.tint) { onOpen(t) }.padding(12.dp),
    ) {
        Row {
            Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(if (t.status == Status.DONE) C.lime else t.priority.tint))
            Text(
                t.title, Modifier.padding(start = 8.dp), color = if (t.isOpen) C.text else C.muted, fontSize = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        val meta = metaOf(t, state.today, compact = true)
        if (meta.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                meta.forEach { Pill(it.text, it.bg, it.fg, it.icon) }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (i > 0) MoveButton("← ${order[i - 1].label}") { vm.setStatus(t, order[i - 1]) }
            if (i in 0 until order.lastIndex) MoveButton("${order[i + 1].label} →") { vm.setStatus(t, order[i + 1]) }
        }
    }
}

@Composable
private fun MoveButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, C.control, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        color = C.text2, fontSize = 12.5.sp,
    )
}

/** 2×2 grid. Long-press a task and drag it up or down to change its importance. */
@Composable
private fun Matrix(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val byQuadrant = pool.filter { it.isOpen }.groupBy { it.quadrant(state.today, state.urgentRule) }
    Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = NavClearance - 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Quadrant.DO to Quadrant.PLAN, Quadrant.QUICK to Quadrant.LATER).forEach { (a, b) ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(a, b).forEach { q ->
                    QuadrantCard(
                        q, byQuadrant[q].orEmpty().sortedBy { it.due ?: it.scheduled ?: LocalDate.MAX }, state.today, onOpen,
                        onDrop = { key -> state.tasks.firstOrNull { it.key == key }?.let { vm.moveToQuadrant(it, q) } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        Text("กดค้างแล้วลากขึ้นหรือลงเพื่อเปลี่ยนความสำคัญ", Modifier.fillMaxWidth(), color = C.muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun QuadrantCard(q: Quadrant, tasks: List<Task>, today: LocalDate, onOpen: (Task) -> Unit, onDrop: (String) -> Unit, modifier: Modifier) {
    val (drop, hovering) = rememberTaskDrop(onDrop)
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (hovering.value) C.accentDeep else C.card)
            .border(1.dp, if (hovering.value) q.accent else C.cardBorder, RoundedCornerShape(18.dp))
            .then(drop),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(q.accent))
            Text(q.label, Modifier.weight(1f).padding(start = 6.dp), color = q.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("${tasks.size}", color = C.muted, fontSize = 12.5.sp)
        }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.key }) { t ->
                Column(Modifier.fillMaxWidth().taskDragSource(t.key, q.accent) { onOpen(t) }) {
                    Text(t.title, color = C.text, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val d = t.due ?: t.scheduled
                    if (d != null) Text(if (d == today) "วันนี้" else d.format(SHORT_DATE), color = if (d < today) C.red else C.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Bars from start (or scheduled) to due across the coming weeks, grouped by project. */
@Composable
private fun Gantt(state: UiState, pool: List<Task>, onOpen: (Task) -> Unit) {
    val first = state.today.minusDays(3)
    val days = 21
    val dayW = 30.dp
    val nameW = 128.dp
    val rows = pool.filter { t -> t.status != Status.CANCELLED && (t.due != null || t.scheduled != null || t.start != null) }
        .filter { t -> t.isOpen || t.done?.let { it >= first } == true }
        .groupBy { Projects.projectOf(it) ?: "ไม่มีโปรเจกต์" }
        .toSortedMap(compareBy<String> { it == "ไม่มีโปรเจกต์" }.thenBy { it })
    val hScroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = NavClearance - 10.dp)) {
        Card(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    Box(Modifier.width(nameW))
                    Row(Modifier.horizontalScroll(hScroll).padding(vertical = 8.dp)) {
                        for (i in 0 until days) {
                            val d = first.plusDays(i.toLong())
                            Column(Modifier.width(dayW), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.dayOfWeek.thai(), color = C.faint, fontSize = 10.5.sp)
                                Text(
                                    "${d.dayOfMonth}",
                                    Modifier.size(22.dp).clip(CircleShape).background(if (d == state.today) C.accent else Color.Transparent).padding(top = 2.dp),
                                    color = if (d == state.today) C.onAccent else C.text2, fontSize = 11.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                rows.forEach { (project, tasks) ->
                    Text(project, Modifier.fillMaxWidth().background(C.sunken).padding(horizontal = 12.dp, vertical = 6.dp), color = C.tealChip, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    tasks.sortedBy { it.start ?: it.scheduled ?: it.due }.forEach { t ->
                        val a = t.start ?: t.scheduled ?: t.due!!
                        val b = maxOf(a, t.due ?: t.scheduled ?: a)
                        val from = ChronoUnit.DAYS.between(first, a).coerceIn(0, days.toLong() - 1)
                        val to = ChronoUnit.DAYS.between(first, b).coerceIn(0, days.toLong() - 1)
                        val visible = !(b < first || a > first.plusDays(days.toLong() - 1))
                        val color = when {
                            !t.isOpen -> C.lime.copy(alpha = 0.55f)
                            t.due?.let { it < state.today } == true -> C.red
                            else -> t.priority.tint.takeIf { it != C.faint } ?: C.muted
                        }
                        Row(Modifier.height(42.dp).clickable { onOpen(t) }, verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title, Modifier.width(nameW).padding(horizontal = 12.dp), color = if (t.isOpen) C.text else C.muted, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.horizontalScroll(hScroll).width(dayW * days).fillMaxHeight()) {
                                Box(Modifier.offset(x = dayW * (ChronoUnit.DAYS.between(first, state.today).toInt()) + dayW / 2).width(1.dp).fillMaxHeight().background(C.accent.copy(alpha = 0.5f)))
                                if (visible) {
                                    Box(
                                        Modifier.align(Alignment.CenterStart).offset(x = dayW * from.toInt() + 3.dp)
                                            .width(dayW * (to - from + 1).toInt() - 6.dp).height(12.dp)
                                            .clip(RoundedCornerShape(6.dp)).background(color),
                                    )
                                }
                            }
                        }
                    }
                }
                if (rows.isEmpty()) Text("ยังไม่มีงานที่มีวันที่", Modifier.padding(16.dp), color = C.muted)
            }
        }
    }
}

@Composable
private fun MonthCalendar(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(state.today.toString()) }
    val sel = LocalDate.parse(selected)
    val month = YearMonth.from(state.today)
    val firstCell = month.atDay(1).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    fun tasksOn(d: LocalDate) = pool.filter { it.status != Status.CANCELLED && (it.due == d || it.scheduled == d) }
    fun eventsOn(d: LocalDate) = state.events.filter { it.begin.toLocalDate() <= d && (it.end.toLocalDate() > d || it.begin.toLocalDate() == d) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { CalendarConnect(state, vm) }
        item {
            Card {
                Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", TH)), Modifier.padding(start = 16.dp, top = 12.dp), color = C.text, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    DayOfWeek.entries.forEach { Text(it.thai(), Modifier.weight(1f), color = C.faint, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
                for (week in 0 until 6) {
                    val rowStart = firstCell.plusDays(week * 7L)
                    if (week == 5 && rowStart.month != month.month) continue
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        for (i in 0 until 7) {
                            val d = rowStart.plusDays(i.toLong())
                            val inMonth = d.month == month.month
                            val tasks = tasksOn(d)
                            val dots = buildList {
                                if (tasks.any { it.isOpen && it.due != null && it.due!! < state.today }) add(C.red)
                                if (tasks.isNotEmpty()) add(C.accent)
                                if (eventsOn(d).isNotEmpty()) add(C.teal)
                            }
                            Column(
                                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (d == sel && d != state.today) C.raised else Color.Transparent)
                                    .clickable { selected = d.toString() },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "${d.dayOfMonth}",
                                    Modifier.padding(top = 5.dp).size(26.dp).clip(CircleShape).background(if (d == state.today) C.accent else Color.Transparent).padding(top = 3.dp),
                                    color = when {
                                        d == state.today -> C.onAccent
                                        !inMonth -> C.faint
                                        else -> C.text
                                    },
                                    fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    dots.forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(it)) }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.height(8.dp))
            }
        }
        item {
            Card {
                Text(
                    (if (sel == state.today) "วันนี้, " else "") + sel.format(DateTimeFormatter.ofPattern("EEEE d MMMM", TH)),
                    Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), color = C.text2, style = MaterialTheme.typography.titleSmall,
                )
                val ev: List<CalendarEvent> = eventsOn(sel)
                val ts = tasksOn(sel)
                if (ev.isEmpty() && ts.isEmpty()) Text("ว่างทั้งวัน", Modifier.padding(16.dp), color = C.muted)
                ev.forEach { e ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(C.teal))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(e.title, color = C.text, fontSize = 14.sp)
                            Text(if (e.allDay) "ทั้งวัน" else "${e.begin.toLocalTime()} ถึง ${e.end.toLocalTime()}", color = C.tealText, fontSize = 12.5.sp)
                        }
                    }
                }
                ts.forEach { t -> TaskRow(t, state.today, { vm.toggleDone(t) }, { onOpen(t) }, compact = true) }
                Box(Modifier.height(6.dp))
            }
        }
    }
}

private fun DayOfWeek.thai() = when (this) {
    DayOfWeek.MONDAY -> "จ"
    DayOfWeek.TUESDAY -> "อ"
    DayOfWeek.WEDNESDAY -> "พ"
    DayOfWeek.THURSDAY -> "พฤ"
    DayOfWeek.FRIDAY -> "ศ"
    DayOfWeek.SATURDAY -> "ส"
    DayOfWeek.SUNDAY -> "อา"
}
