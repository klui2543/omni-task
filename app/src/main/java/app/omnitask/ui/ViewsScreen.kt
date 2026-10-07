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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class Mode(private val th: String, private val en: String) {
    KANBAN("Kanban", "Kanban"), MATRIX("Matrix", "Matrix"), GANTT("Gantt", "Gantt"), CALENDAR("ปฏิทิน", "Calendar");

    val label get() = tr(th, en)
}

@Composable
fun ViewsScreen(state: UiState, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(Mode.KANBAN) }
    var filtering by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    val q = state.query
    // Every view honours the same filters as the task list. Kanban shows all statuses as its columns,
    // so the status filter only narrows the other views, and only when it differs from the default.
    val pool = state.tasks.filter { q.copy(statuses = emptySet()).matches(it, state.today) }
    val narrowed = if (q.statuses == TaskQuery.DEFAULT.statuses) pool else pool.filter { it.status in q.statuses }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)) {
            Text(tr("มุมมอง", "Views"), Modifier.padding(start = 4.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall, color = C.text)
            Segmented(Mode.entries.map { it to it.label }, mode, { mode = it }, Modifier.fillMaxWidth())
            FilterBar(state, vm, onFilter = { filtering = true }, showSort = mode == Mode.KANBAN, modifier = Modifier.padding(top = 8.dp), onSort = { sorting = true })
        }
        when (mode) {
            Mode.KANBAN -> Kanban(state, pool, vm, onOpen)
            Mode.MATRIX -> Matrix(state, narrowed, vm, onOpen)
            Mode.GANTT -> Gantt(state, narrowed, onOpen)
            Mode.CALENDAR -> CalendarViews(state, narrowed, vm, onOpen)
        }
    }
    if (filtering) FilterSheet(state, vm) { filtering = false }
    if (sorting) SortSheet(state.query, vm::setQuery) { sorting = false }
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
                        Text("${cards.size}", color = C.muted, fontSize = TS.caption)
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
            .taskDragSource(t.key, t.priority.tint) { onOpen(t) }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 2.dp).width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(if (t.status == Status.DONE) C.lime else t.priority.tint))
            Text(
                t.title, Modifier.weight(1f).padding(start = 8.dp, end = 4.dp), color = if (t.isOpen) C.text else C.muted, fontSize = TS.body,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            // Small arrows for one-tap moves; long-press and drag works too.
            if (i > 0) MoveButton(Ic.back, tr("ย้ายไป ", "Move to ") + order[i - 1].label) { vm.setStatus(t, order[i - 1]) }
            if (i in 0 until order.lastIndex) MoveButton(Ic.next, tr("ย้ายไป ", "Move to ") + order[i + 1].label) { vm.setStatus(t, order[i + 1]) }
        }
        val meta = metaOf(t, state.today, compact = true)
        if (meta.isNotEmpty()) {
            FlowRow(Modifier.padding(start = 11.dp, top = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                meta.forEach { Pill(it.text, it.bg, it.fg, it.icon) }
            }
        }
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = C.muted, modifier = Modifier.size(14.dp))
    }
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
        Text(tr("กดค้างแล้วลากขึ้นหรือลงเพื่อเปลี่ยนความสำคัญ", "Long-press and drag up or down to change priority"), Modifier.fillMaxWidth(), color = C.muted, fontSize = TS.caption, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            Text(q.label, Modifier.weight(1f).padding(start = 6.dp), color = q.accent, fontSize = TS.caption, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("${tasks.size}", color = C.muted, fontSize = TS.caption)
        }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.key }) { t ->
                Column(Modifier.fillMaxWidth().taskDragSource(t.key, q.accent) { onOpen(t) }) {
                    Text(t.title, color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val d = t.due ?: t.scheduled
                    if (d != null) Text(if (d == today) tr("วันนี้", "Today") else d.format(SHORT_DATE), color = if (d < today) C.red else C.muted, fontSize = TS.caption)
                }
            }
        }
    }
}

/** Bars from start (or scheduled) to due across the coming weeks, grouped by project. */
private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun Gantt(state: UiState, pool: List<Task>, onOpen: (Task) -> Unit) {
    val first = state.today.minusDays(3)
    val days = 21
    val dayW = 30.dp
    val nameW = 128.dp
    val dated = pool.filter { t -> t.status != Status.CANCELLED && (t.due != null || t.scheduled != null || t.start != null) }
        .filter { t -> t.isOpen || t.done?.let { it >= first } == true }
    val noProject = tr("ไม่มีโปรเจกต์", "No project")
    val rows = dated
        .groupBy { Projects.projectOf(it) ?: noProject }
        .toSortedMap(compareBy<String> { it == noProject }.thenBy { it })
    val hScroll = rememberScrollState()
    val context = LocalContext.current
    var showEvents by rememberSaveable { mutableStateOf(true) }
    val last = first.plusDays(days - 1L)
    // Events overlapping the range, one row per title, so a recurring shift reads as a single row of bars.
    fun lastDay(e: CalendarEvent) = maxOf(e.begin.toLocalDate(), e.end.minusNanos(1).toLocalDate())
    val eventRows = if (!showEvents) emptyList() else state.events
        .filter { it.begin.toLocalDate() <= last && lastDay(it) >= first }
        .groupBy { it.title }
        .toList()
        .sortedBy { (_, list) -> list.minOf { it.begin } }
    Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = NavClearance - 10.dp)) {
        if (state.calendarAccess == true) {
            Row(Modifier.padding(bottom = 8.dp)) {
                Chip(tr("นัดจาก Google Calendar", "Google Calendar events"), showEvents, { showEvents = !showEvents }, dot = C.teal)
            }
        }
        Card(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    Box(Modifier.width(nameW))
                    Row(Modifier.horizontalScroll(hScroll).padding(vertical = 8.dp)) {
                        for (i in 0 until days) {
                            val d = first.plusDays(i.toLong())
                            Column(Modifier.width(dayW), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.dayOfWeek.short(), color = C.faint, fontSize = TS.micro)
                                Text(
                                    "${d.dayOfMonth}",
                                    Modifier.size(22.dp).clip(CircleShape).background(if (d == state.today) C.accent else Color.Transparent).padding(top = 2.dp),
                                    color = if (d == state.today) C.onAccent else C.text2, fontSize = TS.micro,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                if (eventRows.isNotEmpty()) {
                    Text("Google Calendar", Modifier.fillMaxWidth().background(C.sunken).padding(horizontal = 12.dp, vertical = 6.dp), color = C.teal, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                }
                eventRows.forEach { (title, list) ->
                    val next = list.firstOrNull { lastDay(it) >= state.today } ?: list.first()
                    Row(
                        Modifier.height(42.dp).clickable {
                            val uri = android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, next.id)
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(nameW).padding(horizontal = 12.dp)) {
                            Text(title, color = Color(0xFFCFF4F0), fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (next.allDay) tr("ทั้งวัน", "All day") else next.begin.format(HM) + tr(" ถึง ", " to ") + next.end.format(HM),
                                color = C.tealText, fontSize = TS.micro, maxLines = 1,
                            )
                        }
                        Box(Modifier.horizontalScroll(hScroll).width(dayW * days).fillMaxHeight()) {
                            Box(Modifier.offset(x = dayW * (ChronoUnit.DAYS.between(first, state.today).toInt()) + dayW / 2).width(1.dp).fillMaxHeight().background(C.accent.copy(alpha = 0.5f)))
                            list.forEach { e ->
                                val from = ChronoUnit.DAYS.between(first, e.begin.toLocalDate()).coerceIn(0, days.toLong() - 1)
                                val to = ChronoUnit.DAYS.between(first, lastDay(e)).coerceIn(0, days.toLong() - 1)
                                Box(
                                    Modifier.align(Alignment.CenterStart).offset(x = dayW * from.toInt() + 3.dp)
                                        .width(dayW * (to - from + 1).toInt() - 6.dp).height(12.dp)
                                        .clip(RoundedCornerShape(6.dp)).background(C.teal.copy(alpha = if (lastDay(e) < state.today) 0.4f else 0.85f)),
                                )
                            }
                        }
                    }
                }
                rows.forEach { (project, tasks) ->
                    Text(project, Modifier.fillMaxWidth().background(C.sunken).padding(horizontal = 12.dp, vertical = 6.dp), color = C.tealChip, fontSize = TS.caption, fontWeight = FontWeight.Medium)
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
                            Text(t.title, Modifier.width(nameW).padding(horizontal = 12.dp), color = if (t.isOpen) C.text else C.muted, fontSize = TS.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                if (rows.isEmpty() && eventRows.isEmpty()) Text(tr("ยังไม่มีงานที่มีวันที่", "No dated tasks yet"), Modifier.padding(16.dp), color = C.muted)
            }
        }
    }
}

/** The calendar's own views: the month grid, or a timeline of 7, 3 or 1 days. */
private enum class CalendarSpan(val days: Int, private val th: String, private val en: String) {
    MONTH(0, "เดือน", "Month"), WEEK(7, "7 วัน", "7 days"), THREE(3, "3 วัน", "3 days"), DAY(1, "วัน", "Day");

    val label get() = tr(th, en)
}

@Composable
private fun CalendarViews(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var span by rememberSaveable { mutableStateOf(CalendarSpan.MONTH) }
    Column(Modifier.fillMaxSize()) {
        Segmented(CalendarSpan.entries.map { it to it.label }, span, { span = it }, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
        if (span == CalendarSpan.MONTH) {
            MonthCalendar(state, pool, vm, onOpen)
        } else {
            // The month list carries this card itself; the timelines keep it above their fixed grid.
            CalendarConnect(state, vm, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
            CalendarTimeline(state, pool, vm, span.days, onOpen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MonthCalendar(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(state.today.toString()) }
    val sel = LocalDate.parse(selected)
    var shown by rememberSaveable { mutableStateOf(YearMonth.from(state.today).toString()) }
    val month = YearMonth.parse(shown)
    LaunchedEffect(month) { vm.loadMonth(month.atDay(1)) }
    fun go(delta: Long) {
        val next = month.plusMonths(delta)
        shown = next.toString()
        selected = (if (next == YearMonth.from(state.today)) state.today else next.atDay(1)).toString()
    }
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
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", TH)), Modifier.weight(1f), color = C.text, style = MaterialTheme.typography.titleSmall)
                    if (month != YearMonth.from(state.today)) {
                        Text(
                            tr("วันนี้", "Today"),
                            Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { go(YearMonth.from(state.today).let { java.time.temporal.ChronoUnit.MONTHS.between(month, it) }) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            color = C.accentText, fontSize = TS.caption,
                        )
                    }
                    SquareButton(Ic.back, tr("เดือนก่อน", "Previous month"), { go(-1) })
                    Box(Modifier.width(6.dp))
                    SquareButton(Ic.next, tr("เดือนถัดไป", "Next month"), { go(1) })
                }
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    DayOfWeek.entries.forEach { Text(it.short(), Modifier.weight(1f), color = C.faint, fontSize = TS.caption, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
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
                                    fontSize = TS.body, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                    (if (sel == state.today) tr("วันนี้, ", "Today, ") else "") + sel.format(DateTimeFormatter.ofPattern("EEEE d MMMM", TH)),
                    Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), color = C.text2, style = MaterialTheme.typography.titleSmall,
                )
                val ev: List<CalendarEvent> = eventsOn(sel)
                val ts = tasksOn(sel)
                if (ev.isEmpty() && ts.isEmpty()) Text(tr("ว่างทั้งวัน", "Free all day"), Modifier.padding(16.dp), color = C.muted)
                ev.forEach { e ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(C.teal))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(e.title, color = C.text, fontSize = TS.body)
                            Text(if (e.allDay) tr("ทั้งวัน", "All day") else tr("${e.begin.toLocalTime()} ถึง ${e.end.toLocalTime()}", "${e.begin.toLocalTime()} to ${e.end.toLocalTime()}"), color = C.tealText, fontSize = TS.caption)
                        }
                    }
                }
                ts.forEach { t -> TaskRow(t, state.today, { vm.toggleDone(t) }, { onOpen(t) }, compact = true) }
                Box(Modifier.height(6.dp))
            }
        }
    }
}

/** Short weekday names for the month grid, the Gantt and the timelines in CalendarTimeline.kt. */
internal fun DayOfWeek.short() = when (this) {
    DayOfWeek.MONDAY -> tr("จ", "Mo")
    DayOfWeek.TUESDAY -> tr("อ", "Tu")
    DayOfWeek.WEDNESDAY -> tr("พ", "We")
    DayOfWeek.THURSDAY -> tr("พฤ", "Th")
    DayOfWeek.FRIDAY -> tr("ศ", "Fr")
    DayOfWeek.SATURDAY -> tr("ส", "Sa")
    DayOfWeek.SUNDAY -> tr("อา", "Su")
}
