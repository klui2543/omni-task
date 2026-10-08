package app.omnitask.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** How many days share the width. Thirty days label every other day so the header does not crowd. */
private enum class GanttZoom(val days: Int, val labelStep: Int, private val th: String, private val en: String) {
    WEEK(7, 1, "7 วัน", "7 days"), TWO_WEEKS(14, 1, "14 วัน", "14 days"), MONTH(30, 2, "30 วัน", "30 days");

    val label get() = tr(th, en)
}

/** 38dp at the design size; taller when the text size (in settings or on the phone) is larger, so both lines fit. */
private val GanttBarH: Dp
    @Composable get() = with(LocalDensity.current) { maxOf(38.dp, (TS.caption * 1.25f).toDp() + (TS.micro * 1.25f).toDp() + 7.dp) }
private val GanttLaneH: Dp
    @Composable get() = GanttBarH + 6.dp
private val GanttBarShape = RoundedCornerShape(10.dp)

/** Bars narrower than this carry their title beside them instead of inside. */
private val GanttInsideMin = 90.dp
private val GanttGap = 6.dp
private val GanttClock = DateTimeFormatter.ofPattern("HH:mm")
private val GanttEventColors = GanttColors(C.teal, C.teal.copy(alpha = 0.16f), Color(0xFFCFF4F0), C.tealText)

/**
 * Tasks and Google Calendar events as bars over 7, 14 or 30 days that share the full width, so the view fits
 * a phone, landscape and the unfolded Find N alike. Each bar carries its title; a bar too short for it gets the
 * title beside it. The range opens a day before today; swipe sideways or use the arrows to page.
 */
@Composable
fun GanttView(state: UiState, tasks: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var zoom by rememberSaveable { mutableStateOf(GanttZoom.TWO_WEEKS) }
    var startText by rememberSaveable { mutableStateOf(state.today.minusDays(1).toString()) }
    var showEvents by rememberSaveable { mutableStateOf(true) }
    val today = state.today
    val days = zoom.days
    val first = LocalDate.parse(startText)
    val last = first.plusDays(days - 1L)
    val showsToday = today >= first && today <= last
    fun shift(steps: Int) {
        startText = LocalDate.parse(startText).plusDays(steps.toLong() * days).toString()
    }
    fun backToToday() {
        startText = today.minusDays(1).toString()
    }
    // Keeps the events of the shown range, also when it runs into the next month.
    LaunchedEffect(first, days) {
        if (YearMonth.from(last) != YearMonth.from(first)) vm.loadMonth(last)
        vm.loadMonth(first)
    }

    val context = LocalContext.current
    fun openEvent(e: CalendarEvent) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    // Dated tasks that are open, or were done within the range; a bar runs from start (or scheduled) to due.
    val spans = tasks
        .filter { t -> t.status != Status.CANCELLED && (t.isOpen || t.done?.let { it >= first } == true) }
        .mapNotNull { t ->
            val a = t.start ?: t.scheduled ?: t.due ?: return@mapNotNull null
            val b = t.due ?: t.scheduled ?: a
            GanttSpan(t, a, if (b < a) a else b)
        }
    val noProject = tr("ไม่มีโปรเจกต์", "No project")
    val groups = spans
        .groupBy { Projects.projectOf(it.task) ?: noProject }
        .toSortedMap(compareBy<String> { it == noProject }.thenBy { it })
    // Events overlapping the range, one row per title, so a recurring shift reads as a single row of bars.
    val eventRows = if (!showEvents) emptyList() else state.events
        .filter { it.begin.toLocalDate() <= last && it.ganttLastDay() >= first }
        .groupBy { it.title }
        .toList()
        .sortedBy { (_, list) -> list.minOf { it.begin } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // No title column: the days split the whole width, wide or narrow.
        val rowW = maxOf(maxWidth - 28.dp, 1.dp)
        val dayW = rowW / days
        val range = ganttRange(first, last, today)
        val oneLineHeader = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize()) {
            if (oneLineHeader) {
                // Landscape and unfolded: the whole header fits on one line, leaving more height for the rows.
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        range, Modifier.weight(1f).padding(end = 10.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (state.calendarAccess == true) {
                        Chip(tr("นัดจาก Google Calendar", "Google Calendar events"), showEvents, { showEvents = !showEvents }, dot = C.teal, modifier = Modifier.padding(end = 10.dp))
                    }
                    GanttZoomSwitch(zoom, { zoom = it }, Modifier.padding(end = 10.dp))
                    GanttPaging(days, showsToday, { backToToday() }) { shift(it) }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        range, Modifier.weight(1f).padding(end = 10.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    GanttZoomSwitch(zoom, { zoom = it })
                }
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The chip gives way when the Today button needs the room.
                    Box(Modifier.weight(1f)) {
                        if (state.calendarAccess == true) {
                            Chip(tr("นัดจาก Google Calendar", "Google Calendar events"), showEvents, { showEvents = !showEvents }, dot = C.teal)
                        }
                    }
                    GanttPaging(days, showsToday, { backToToday() }) { shift(it) }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)
                    .pointerInput(days) {
                        // A sideways swipe pages the range, as in the calendar timelines.
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = { if (abs(dragged) > 64.dp.toPx()) shift(if (dragged < 0) 1 else -1) },
                        ) { change, amount ->
                            dragged += amount
                            change.consume()
                        }
                    },
            ) {
                GanttDays(first, days, zoom.labelStep, today, dayW)
                Divider()
                val todayAt = ChronoUnit.DAYS.between(first, today).toInt()
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(top = 2.dp, bottom = NavClearance)
                        .drawBehind {
                            // Week starts as faint guides, and today as an accent line, behind the bars.
                            val stroke = 1.dp.toPx()
                            for (i in 1 until days) {
                                if (first.plusDays(i.toLong()).dayOfWeek == DayOfWeek.MONDAY) {
                                    val x = (dayW * i).toPx()
                                    drawLine(C.divider, Offset(x, 0f), Offset(x, size.height), stroke)
                                }
                            }
                            if (todayAt in 0 until days) {
                                val x = (dayW * todayAt + dayW / 2).toPx()
                                drawLine(C.accent.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), stroke)
                            }
                        },
                ) {
                    if (eventRows.isNotEmpty()) GanttCaption("Google Calendar", C.teal)
                    eventRows.forEach { (title, list) ->
                        val next = list.firstOrNull { it.ganttLastDay() >= today } ?: list.first()
                        GanttLane(
                            title = title.ifBlank { tr("ไม่มีชื่อ", "Untitled") },
                            bars = list.map { e ->
                                GanttBar(
                                    from = ChronoUnit.DAYS.between(first, e.begin.toLocalDate()).coerceAtLeast(0L).toInt(),
                                    to = ChronoUnit.DAYS.between(first, e.ganttLastDay()).coerceAtMost(days - 1L).toInt(),
                                    date = ganttEventDate(e, today),
                                    dim = e.ganttLastDay() < today,
                                    onClick = { openEvent(e) },
                                )
                            },
                            colors = GanttEventColors,
                            dayW = dayW,
                            rowW = rowW,
                            onClick = { openEvent(next) },
                        )
                    }
                    groups.forEach { (project, list) ->
                        GanttCaption(project, C.tealChip)
                        list.sortedBy { it.start }.forEach { s -> GanttTaskLane(s, first, days, today, dayW, rowW) { onOpen(s.task) } }
                    }
                    if (groups.isEmpty() && eventRows.isEmpty()) {
                        Text(tr("ยังไม่มีงานที่มีวันที่", "No dated tasks yet"), Modifier.padding(16.dp), color = C.muted, fontSize = TS.body)
                    }
                }
            }
        }
    }
}

/** A task's bar from its first to its last day. */
private class GanttSpan(val task: Task, val start: LocalDate, val end: LocalDate)

/** One bar in a lane, in days from the range start, already cut to the range. */
private class GanttBar(val from: Int, val to: Int, val date: String, val dim: Boolean = false, val onClick: () -> Unit)

/** A bar laid out in the lane. */
private class GanttPlaced(val bar: GanttBar, val x: Dp, val width: Dp)

/** Where the title goes when every bar in its lane is too short to hold it. */
private class GanttBeside(val placed: GanttPlaced, val x: Dp, val width: Dp, val alignEnd: Boolean, val covers: Boolean)

private class GanttColors(val accent: Color, val fill: Color, val title: Color, val date: Color, val struck: Boolean = false)

/** Overdue in red, done in a muted lime, else the priority colour (grey for no priority). */
private fun ganttTaskColors(t: Task, today: LocalDate): GanttColors {
    if (!t.isOpen) return GanttColors(C.lime.copy(alpha = 0.55f), C.lime.copy(alpha = 0.08f), C.muted, C.faint, struck = true)
    if (t.due?.let { it < today } == true) return GanttColors(C.red, C.red.copy(alpha = 0.16f), C.text, C.red)
    val tint = t.priority.tint.takeIf { it != C.faint } ?: C.muted
    return GanttColors(tint, tint.copy(alpha = 0.16f), C.text, C.muted)
}

/** Weekday and date over each day; today in the accent circle. */
@Composable
private fun GanttDays(first: LocalDate, days: Int, step: Int, today: LocalDate, dayW: Dp) {
    // A label may be wider than a thirty-day column; it stays centred on its day and spills into the gap beside it.
    val labelW = maxOf(dayW, 28.dp)
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        for (i in 0 until days) {
            val d = first.plusDays(i.toLong())
            val isToday = d == today
            // Counted from today, so today always keeps its label when only every other day has one.
            if (step == 1 || ChronoUnit.DAYS.between(today, d).mod(step.toLong()) == 0L) {
                Column(Modifier.offset(x = dayW * i + (dayW - labelW) / 2).width(labelW), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.short(), color = if (isToday) C.accentText else C.faint, fontSize = TS.micro, maxLines = 1)
                    Text(
                        "${d.dayOfMonth}",
                        Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(if (isToday) C.accent else Color.Transparent).padding(top = 2.dp),
                        color = if (isToday) C.onAccent else C.text2, fontSize = TS.micro, maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun GanttCaption(text: String, color: Color) {
    Text(
        text, Modifier.fillMaxWidth().padding(start = 2.dp, top = 12.dp, bottom = 2.dp),
        color = color, fontSize = TS.caption, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** A compact 7 / 14 / 30 day switch for the header. */
@Composable
private fun GanttZoomSwitch(zoom: GanttZoom, onSelect: (GanttZoom) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GanttZoom.entries.forEach { z ->
            val on = z == zoom
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(if (on) C.accent else Color.Transparent)
                    .clickable { onSelect(z) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(z.label, color = if (on) C.onAccent else C.muted, fontSize = TS.caption, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/** Today (only while today is out of view), then the arrows that page by the zoom size. */
@Composable
private fun GanttPaging(days: Int, showsToday: Boolean, onToday: () -> Unit, onShift: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!showsToday) {
            Text(
                tr("วันนี้", "Today"),
                Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onToday).padding(horizontal = 10.dp, vertical = 6.dp),
                color = C.accentText, fontSize = TS.caption, maxLines = 1,
            )
        }
        SquareButton(Ic.back, tr("ย้อน $days วัน", "Previous $days days"), { onShift(-1) })
        Box(Modifier.width(6.dp))
        SquareButton(Ic.next, tr("ถัดไป $days วัน", "Next $days days"), { onShift(1) })
    }
}

/** A task's row: its bar cut to the range, or a pointer at the edge when it lies wholly before or after it. */
@Composable
private fun GanttTaskLane(s: GanttSpan, first: LocalDate, days: Int, today: LocalDate, dayW: Dp, rowW: Dp, onClick: () -> Unit) {
    val colors = ganttTaskColors(s.task, today)
    val date = ganttRange(s.start, s.end, today)
    val from = ChronoUnit.DAYS.between(first, s.start)
    val to = ChronoUnit.DAYS.between(first, s.end)
    when {
        to < 0 -> GanttEdgeLane(s.task.title, date, colors, before = true, onClick = onClick)
        from >= days -> GanttEdgeLane(s.task.title, date, colors, before = false, onClick = onClick)
        else -> {
            val bar = GanttBar(from.coerceAtLeast(0L).toInt(), to.coerceAtMost(days - 1L).toInt(), date, onClick = onClick)
            GanttLane(s.task.title, listOf(bar), colors, dayW, rowW, onClick)
        }
    }
}

/** One row of bars sharing a title: a task's single bar, or every occurrence of an event. */
@Composable
private fun GanttLane(title: String, bars: List<GanttBar>, colors: GanttColors, dayW: Dp, rowW: Dp, onClick: () -> Unit) {
    // A sliver between neighbouring days, thinner when thirty days share a phone.
    val inset = if (dayW < 20.dp) 1.dp else 2.dp
    val placed = bars.sortedBy { it.from }.map { b ->
        GanttPlaced(b, dayW * b.from + inset, (dayW * (b.to - b.from + 1) - inset * 2).coerceAtLeast(2.dp))
    }
    val beside = if (placed.isEmpty() || placed.any { it.width >= GanttInsideMin }) null else ganttBeside(placed, rowW)
    Box(Modifier.fillMaxWidth().height(GanttLaneH).clipToBounds().clickable(onClick = onClick)) {
        placed.forEach { p ->
            Row(
                Modifier.align(Alignment.CenterStart).offset(x = p.x).width(p.width).height(GanttBarH)
                    .alpha(if (p.bar.dim) 0.55f else 1f).clip(GanttBarShape).background(colors.fill)
                    .clickable(onClick = p.bar.onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(colors.accent))
                if (p.width >= GanttInsideMin) GanttBarText(title, p.bar.date, colors, Modifier.padding(start = 7.dp, end = 6.dp))
            }
        }
        if (beside != null) {
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = beside.x).width(beside.width.coerceAtLeast(0.dp))
                    .clickable(onClick = beside.placed.bar.onClick),
                contentAlignment = if (beside.alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                GanttBarText(
                    title, beside.placed.bar.date, colors,
                    // Over other bars the title gets a plain backing so it stays readable.
                    if (beside.covers) Modifier.clip(RoundedCornerShape(6.dp)).background(C.bg).padding(horizontal = 4.dp) else Modifier,
                    end = beside.alignEnd,
                )
            }
        }
    }
}

/**
 * Where a title goes when its bars are too short for it: right after the first bar with room after it, else the
 * widest free stretch (left of the bar when it sits at the end of the range), else over the bars that crowd the row.
 */
private fun ganttBeside(placed: List<GanttPlaced>, rowW: Dp): GanttBeside {
    val after = placed.mapIndexed { i, p ->
        val x = p.x + p.width + GanttGap
        val end = if (i < placed.lastIndex) placed[i + 1].x - GanttGap else rowW
        GanttBeside(p, x, end - x, alignEnd = false, covers = false)
    }
    after.firstOrNull { it.width >= GanttInsideMin }?.let { return it }
    val head = placed.first()
    val gaps = after + GanttBeside(head, 0.dp, head.x - GanttGap, alignEnd = true, covers = false)
    val widest = gaps.maxBy { it.width }
    if (widest.width >= GanttInsideMin / 2) return widest
    val x = head.x + head.width + GanttGap
    return GanttBeside(head, x, rowW - x, alignEnd = false, covers = true)
}

/** A task wholly before or after the range: its title at that edge with an arrow, so it does not drop out of sight. */
@Composable
private fun GanttEdgeLane(title: String, date: String, colors: GanttColors, before: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(GanttLaneH).clickable(onClick = onClick),
        horizontalArrangement = if (before) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (before) Icon(Ic.back, tr("ก่อนช่วงนี้", "Before this range"), Modifier.size(14.dp), tint = colors.accent)
        GanttBarText(title, date, colors, Modifier.weight(1f, fill = false).padding(horizontal = 6.dp), end = !before)
        if (!before) Icon(Ic.next, tr("หลังช่วงนี้", "After this range"), Modifier.size(14.dp), tint = colors.accent)
    }
}

/** The title, and under it the dates (or times) in a smaller line. */
@Composable
private fun GanttBarText(title: String, date: String, colors: GanttColors, modifier: Modifier = Modifier, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(
            title, color = colors.title, fontSize = TS.caption, fontWeight = FontWeight.Medium, lineHeight = TS.caption * 1.25f,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (colors.struck) TextDecoration.LineThrough else null,
        )
        Text(date, color = colors.date, fontSize = TS.micro, lineHeight = TS.micro * 1.25f, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The last day an event touches; one ending at midnight belongs to the day before. */
private fun CalendarEvent.ganttLastDay(): LocalDate {
    val a = begin.toLocalDate()
    val b = end.minusNanos(1).toLocalDate()
    return if (b < a) a else b
}

/** "8 ต.ค.", "8 ถึง 10 ต.ค." or "29 ก.ย. ถึง 5 ต.ค."; the year only when it is not this year. */
private fun ganttRange(a: LocalDate, b: LocalDate, today: LocalDate): String {
    val year = if (a.year != today.year || b.year != today.year) " ${b.year}" else ""
    if (a == b) return a.format(SHORT_DATE) + year
    val from = if (a.month == b.month && a.year == b.year) "${a.dayOfMonth}" else a.format(SHORT_DATE)
    val to = b.format(SHORT_DATE) + year
    return tr("$from ถึง $to", "$from to $to")
}

/** An event's second line: its times when it falls on one day, else its days. */
private fun ganttEventDate(e: CalendarEvent, today: LocalDate): String {
    val a = e.begin.toLocalDate()
    val b = e.ganttLastDay()
    return when {
        a != b -> ganttRange(a, b, today)
        e.allDay -> tr("ทั้งวัน", "All day")
        else -> tr("${e.begin.format(GanttClock)} ถึง ${e.end.format(GanttClock)}", "${e.begin.format(GanttClock)} to ${e.end.format(GanttClock)}")
    }
}
