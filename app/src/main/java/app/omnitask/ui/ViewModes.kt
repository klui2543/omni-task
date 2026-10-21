@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.omnitask.ui

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.omnitask.data.CalendarReader
import app.omnitask.data.MonthMarksStore
import app.omnitask.model.MonthGrid
import app.omnitask.widget.MonthData
import app.omnitask.widget.MonthPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import app.omnitask.time.*

enum class Mode(private val th: String, private val en: String) {
    KANBAN("Kanban", "Kanban"), MATRIX("Matrix", "Matrix"), GANTT("Timeline", "Timeline"), CALENDAR("ปฏิทิน", "Calendar");

    val label get() = tr(th, en)
}

/**
 * One view over [pool] (every status, for Kanban's columns) or [narrowed] (what the other views show).
 * The Tasks tab passes the filtered vault; a project passes its own tasks ([scoped]: only its subtasks join the calendar).
 */
@Composable
fun ViewBody(mode: Mode, state: UiState, pool: List<Task>, narrowed: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit, scoped: Boolean = false) {
    val q = state.query
    when (mode) {
        Mode.KANBAN -> Kanban(state, pool, vm, onOpen)
        Mode.MATRIX -> Matrix(state, narrowed, vm, onOpen)
        Mode.GANTT -> GanttView(state, narrowed, vm, onOpen)
        // Subtasks live inside their parent, but a dated one still belongs on the calendar.
        Mode.CALENDAR -> CalendarViews(
            state,
            narrowed + state.tasks.filter { s ->
                s.isSubtask && s !in narrowed && (s.due != null || s.scheduled != null) && (!q.hideDone || s.isOpen) && (!scoped || pool.any { it.key == s.parent })
            },
            vm, onOpen,
        )
    }
}

@Composable
private fun Kanban(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val q = state.query
    val columns = listOf(Status.TODO to C.faint, Status.IN_PROGRESS to C.accent, Status.DONE to C.lime)
    val ordered = q.copy(statuses = emptySet(), groupBy = GroupBy.NONE).run(pool, state.today).flatMap { it.tasks }
    // Three columns share a wide screen (landscape, unfolded); on a phone they keep 272dp and scroll sideways.
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val colW = maxOf(272.dp, (screenW - 28.dp - 20.dp - if (screenW >= 600.dp) 96.dp else 0.dp) / 3)
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
                // A folded column (tap its head) is a narrow strip, as in TaskForge, and still takes drops.
                // With done work hidden, Done starts folded; opening it shows the last week's done work.
                val hiddenDone = status == Status.DONE && q.hideDone
                val folded = status.name in state.foldedColumns || hiddenDone
                val (drop, hovering) = rememberTaskDrop { key ->
                    state.tasks.firstOrNull { it.key == key }?.let { if (it.status != status) vm.setStatus(it, status) }
                }
                if (folded) {
                    Column(
                        Modifier.width(52.dp).fillMaxHeight().clip(RoundedCornerShape(C.radius))
                            .background(if (hovering.value) C.accentDeep else C.sunken)
                            .border(1.dp, if (hovering.value) dot else C.divider, RoundedCornerShape(C.radius))
                            .then(drop)
                            .clickable { if (hiddenDone) vm.setQuery(q.copy(hideDone = false)) else vm.toggleColumn(status) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        if (status != Status.DONE) {
                            Box(
                                Modifier.padding(top = 12.dp).size(32.dp).clip(CircleShape).clickable { vm.requestQuickAdd(QuickAddRequest(status = status)) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Ic.plus, tr("เพิ่มงาน", "Add task"), tint = C.muted, modifier = Modifier.size(16.dp)) }
                        }
                        Text("${cards.size}", Modifier.padding(top = 12.dp), color = C.text2, fontSize = TS.body)
                        VerticalText(status.label, Modifier.padding(top = 10.dp), color = C.text, fontSize = TS.body)
                    }
                    return@items
                }
                Column(
                    Modifier.width(colW).fillMaxHeight().clip(RoundedCornerShape(C.radius))
                        .background(if (hovering.value) C.accentDeep else C.sunken)
                        .border(1.dp, if (hovering.value) dot else C.divider, RoundedCornerShape(C.radius))
                        .then(drop),
                ) {
                    Row(Modifier.fillMaxWidth().clickable { vm.toggleColumn(status) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Text(status.label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, color = C.text)
                        Text("${cards.size}", color = C.muted, fontSize = TS.caption)
                        Icon(Ic.back, tr("พับ", "Fold"), tint = C.faint, modifier = Modifier.padding(start = 8.dp).size(14.dp))
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.raised).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
            .taskDragSource(t.key, t.priority.tint) { onOpen(t) }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 3.dp).width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(if (t.status == Status.DONE) C.lime else t.priority.tint))
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 6.dp)) {
                Text(t.title, color = if (t.isOpen) C.text else C.muted, fontSize = TS.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                // The details, one quiet line under the title.
                t.descriptionPreview?.let { Text(it, color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        // Dates and counts on one line with the move arrows at its end, so the card stays short.
        val meta = metaOf(t, state.today, compact = true, progress = state.progressOf(t))
        Row(Modifier.padding(start = 11.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                meta.forEach { Pill(it.text, it.bg, it.fg, it.icon) }
            }
            // Small arrows for one-tap moves; long-press and drag works too.
            if (i > 0) MoveButton(Ic.back, tr("ย้ายไป ", "Move to ") + order[i - 1].label) { vm.setStatus(t, order[i - 1]) }
            if (i in 0 until order.lastIndex) MoveButton(Ic.next, tr("ย้ายไป ", "Move to ") + order[i + 1].label) { vm.setStatus(t, order[i + 1]) }
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
                        q, byQuadrant[q].orEmpty().sortedBy { it.due ?: it.scheduled ?: LocalDate.LATEST }, state.today, onOpen,
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

/** The calendar's own views: the month grid, or a timeline of 7, 3 or 1 days. */
enum class CalendarSpan(val days: Int, private val th: String, private val en: String) {
    MONTH(0, "เดือน", "Month"), WEEK(7, "7 วัน", "7 days"), THREE(3, "3 วัน", "3 days"), DAY(1, "วัน", "Day");

    val label get() = tr(th, en)
}

@Composable
private fun CalendarViews(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val span = state.calendarSpan
    LaunchedEffect(state.calendarDay) { if (state.calendarDay != null) vm.setCalendarSpan(CalendarSpan.MONTH) }
    Column(Modifier.fillMaxSize()) {
        Segmented(CalendarSpan.entries.map { it to it.label }, span, vm::setCalendarSpan, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
        if (span == CalendarSpan.MONTH) {
            MonthCalendar(state, pool, vm, onOpen)
        } else {
            // The month list carries this card itself; the timelines keep it above their fixed grid.
            CalendarConnect(state, vm, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
            CalendarTimeline(state, pool, vm, span.days, onOpen, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthCalendar(state: UiState, pool: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(state.today.toString()) }
    val sel = LocalDate.parse(selected)
    var shown by rememberSaveable { mutableStateOf(state.today.yearMonth.toString()) }
    val month = YearMonth.parse(shown)
    LaunchedEffect(month) { vm.loadMonth(month.firstDay()) }
    val listState = rememberLazyListState()
    // The month widget sent a day: show its month with it chosen, and bring that day's list up, which sits below the
    // fold under the month (not when the frames page closed back here). The scroll runs on its own key: clearing the request restarts the effect keyed by it, which
    // would cancel a scroll started there.
    var reveal by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.calendarDay) {
        state.calendarDay?.let { d ->
            selected = d.toString()
            shown = d.yearMonth.toString()
            if (state.calendarReveal) reveal++
            vm.requestCalendar(null)
        }
    }
    LaunchedEffect(reveal) {
        if (reveal == 0) return@LaunchedEffect
        withFrameNanos { }
        listState.animateScrollToItem(DAY_LIST_ITEM)
    }
    // Where each row of the day's list ends on screen, so the page can say how many are still under the nav.
    val rowBottoms = remember { mutableStateMapOf<Int, Float>() }
    var viewBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    // The list's card leaves the composition when it is scrolled out of reach; then all its rows count as below.
    var listShown by remember { mutableStateOf(true) }
    var dayCount by remember { mutableIntStateOf(0) }
    val clearance = with(LocalDensity.current) { NavClearance.toPx() }
    fun go(delta: Long) {
        val next = month.plusMonths(delta)
        shown = next.toString()
        selected = (if (next == state.today.yearMonth) state.today else next.firstDay()).toString()
    }
    val first = month.firstDay()
    val days = MonthGrid.days(first)
    val holidayIds = state.calendars.filter { it.holiday }.map { it.id }.toSet()
    fun tasksOn(d: LocalDate) = pool.filter { it.status != Status.CANCELLED && (it.due == d || it.scheduled == d) }
    fun eventsOn(d: LocalDate) = state.events.filter { it.calendarId !in holidayIds && MonthData.onDay(it, d) }
    // The frames and emoji, read again when the page for them closes.
    val marks = remember(state.month) { MonthMarksStore.local(context) }
    var holidays by remember { mutableStateOf(emptyMap<LocalDate, String>()) }
    LaunchedEffect(month) {
        holidays = withContext(Dispatchers.IO) { runCatching { CalendarReader.holidays(context, days.first(), days.last()) }.getOrDefault(emptyMap()) }
    }
    // How days show their work, set on the frames and emoji page; read again when that page closes.
    val style = remember(state.month) { MonthMarksStore.cellStyle(context) }
    val emojiSize = remember(state.month) { MonthMarksStore.emojiSize(context) }
    val work = MonthData.work(days, first, ::tasksOn, ::eventsOn, state.today)
    // The frames as the widget draws them: those set by hand and those the emoji rules make from the emoji and the task titles.
    val framed = remember(marks, state.tasks) {
        marks.shown(state.tasks.filter { it.isOpen }.mapNotNull { t -> (t.due ?: t.scheduled)?.let { it to t.title } }.groupBy({ it.first }, { it.second }))
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().onGloballyPositioned { viewBottom = it.boundsInWindow().bottom },
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = NavClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { CalendarConnect(state, vm) }
            item {
                Card {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(MonthGrid.title(first), Modifier.weight(1f), color = C.text, style = MaterialTheme.typography.titleSmall)
                        TodayButton({ shown = state.today.yearMonth.toString(); selected = state.today.toString() }, Modifier.padding(end = 6.dp))
                        SquareButton(Ic.pen, tr("ใส่กรอบและ Emoji", "Frames and emoji"), { vm.requestMonth(MonthRequest(sel, pick = true)) })
                        Box(Modifier.width(6.dp))
                        SquareButton(Ic.back, tr("เดือนก่อน", "Previous month"), { go(-1) })
                        Box(Modifier.width(6.dp))
                        SquareButton(Ic.next, tr("เดือนถัดไป", "Next month"), { go(1) })
                    }
                    Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                        MonthGrid.heads.forEachIndexed { i, h ->
                            Text(h, Modifier.weight(1f), color = if (i == 0) C.red else C.faint, fontSize = TS.caption, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        MonthPicture(
                            MonthData(state.today, first, 0, framed, holidays, work, sel, emptyList(), style, emojiSize),
                            onTap = { d -> selected = d.toString(); if (d.yearMonth != month) shown = d.yearMonth.toString() },
                        )
                    }
                    Box(Modifier.height(10.dp))
                }
            }
            item {
                Card {
                    val stickers = marks.stickersOn(sel).joinToString("")
                    val eventActions = rememberEventActions(vm)
                    val ev: List<CalendarEvent> = eventsOn(sel)
                    // Today also lists the work past its deadline after its own, as the widget and Focus do.
                    val ts = tasksOn(sel) + if (sel == state.today) pool.filter { it.isOpen && it.due?.let { d -> d < state.today } == true }.sortedBy { it.due } else emptyList()
                    val count = ev.size + ts.size
                    // Rows from another day must not count.
                    LaunchedEffect(sel, count) { rowBottoms.keys.retainAll { it < count } }
                    SideEffect { dayCount = count }
                    DisposableEffect(Unit) {
                        listShown = true
                        onDispose { listShown = false; rowBottoms.clear() }
                    }
                    fun Modifier.row(i: Int) = onGloballyPositioned { rowBottoms[i] = it.boundsInWindow().bottom }
                    FlowRow(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            (if (sel == state.today) tr("วันนี้, ", "Today, ") else "") + sel.format(DateTimeFormatter.ofPattern("EEEE d MMMM", TH)) +
                                if (stickers.isEmpty()) "" else " $stickers",
                            color = C.text2, style = MaterialTheme.typography.titleSmall,
                        )
                        // How many the day holds, so it is plain whether the list below shows them all.
                        if (count > 0) Text(
                            tr("$count รายการ", if (count == 1) "1 item" else "$count items"),
                            Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(10.dp)).background(C.accent.copy(alpha = 0.22f))
                                .padding(horizontal = 9.dp, vertical = 1.dp),
                            color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.SemiBold,
                        )
                        framed.frameOn(sel)?.takeIf { it.label.isNotEmpty() }?.let { f ->
                            val color = Color(f.color.argb)
                            val (n, of) = f.position(sel) ?: (1 to 1)
                            Text(
                                if (of == 1) f.label else tr("${f.label} วันที่ $n จาก $of", "${f.label}, day $n of $of"),
                                Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 1.dp),
                                color = color, fontSize = TS.caption,
                            )
                        }
                    }
                    holidays[sel]?.let { Text(it, Modifier.padding(start = 16.dp, bottom = 4.dp), color = C.red, fontSize = TS.caption) }
                    if (ev.isEmpty() && ts.isEmpty()) Text(tr("ว่างทั้งวัน", "Free all day"), Modifier.padding(16.dp), color = C.muted)
                    ev.forEachIndexed { i, e ->
                        Row(
                            Modifier.fillMaxWidth().row(i).combinedClickable(onClick = { eventActions.open(e) }, onLongClick = { eventActions.inspect(e) })
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(C.teal))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(e.title, color = C.text, fontSize = TS.body)
                                Text(if (e.allDay) tr("ทั้งวัน", "All day") else tr("${e.begin.toLocalTime()} ถึง ${e.end.toLocalTime()}", "${e.begin.toLocalTime()} to ${e.end.toLocalTime()}"), color = C.tealText, fontSize = TS.caption)
                            }
                        }
                    }
                    ts.forEachIndexed { i, t -> Box(Modifier.row(ev.size + i)) { TaskRow(t, state.today, { vm.toggleDone(t) }, { onOpen(t) }, compact = true) } }
                    Box(Modifier.height(6.dp))
                }
            }
        }
        // The rows still under the nav (or below the screen), as a button that brings the list up.
        val below = if (listShown) rowBottoms.values.count { it > viewBottom - clearance + 2f } else dayCount
        if (below > 0) {
            val scope = rememberCoroutineScope()
            Text(
                tr("ดูอีก $below รายการ \u2193", "$below more \u2193"),
                Modifier.align(Alignment.BottomCenter).padding(bottom = NavClearance + 8.dp).clip(RoundedCornerShape(18.dp)).background(C.accent)
                    .clickable { scope.launch { listState.animateScrollToItem(DAY_LIST_ITEM) } }.padding(horizontal = 16.dp, vertical = 7.dp),
                color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Where the chosen day's list sits in the month page: after the calendar card and the month. */
private const val DAY_LIST_ITEM = 2

/**
 * The month as the widget draws it (frames, emoji, holidays, each day's work, the box round the chosen day), one
 * picture with a tap layer over it.
 */
@Composable
private fun MonthPicture(data: MonthData, onTap: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val days = MonthGrid.days(data.month)
    val rows = days.size / 7
    val cell = if (data.style == app.omnitask.model.CellStyle.BARS) 60f else 52f
    val inset = 8f
    BoxWithConstraints(Modifier.fillMaxWidth().height((inset + rows * cell).dp)) {
        val widthDp = maxWidth.value
        val palette = Palettes.current
        val bitmap = remember(data.month, data.marks, data.holidays, data.work, data.style, data.emoji, data.selected, data.today, widthDp, palette) {
            MonthPainter(context, palette).draw(data, widthDp, cell, rows).asImageBitmap()
        }
        Image(bitmap, MonthGrid.title(data.month), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(Modifier.fillMaxSize().padding(top = inset.dp)) {
            for (w in 0 until rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (i in 0 until 7) {
                        val d = days[w * 7 + i]
                        Box(Modifier.weight(1f).fillMaxSize().clickable { onTap(d) })
                    }
                }
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
