package app.omnitask.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlinx.coroutines.delay

/** The hour labels down the left edge; the day headers and the all-day strip leave the same gap. */
private val LabelW = 40.dp
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val EventText = Color(0xFFCFF4F0)
private const val DAY_MIN = 24 * 60

/** The shortest block drawn, in minutes, so a quick event still has room for its title. */
private const val MIN_BLOCK = 20

/** All-day items shown per day before the strip folds the rest into "+n". */
private const val STRIP_FOLD = 2

/**
 * A Google Calendar style timeline of [days] days (7, 3 or 1): all-day events and tasks without a time in a
 * strip on top, then an hour grid with timed events and tasks placed by time. Seven days start on Monday;
 * three days and one day start at the chosen day. Swipe sideways or use the arrows to page.
 */
@Composable
fun CalendarTimeline(state: UiState, pool: List<Task>, vm: TaskViewModel, days: Int, onOpen: (Task) -> Unit, modifier: Modifier = Modifier) {
    var anchorText by rememberSaveable { mutableStateOf(state.today.toString()) }
    val anchor = LocalDate.parse(anchorText)
    val start = if (days == 7) anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else anchor
    val range = (0 until days).map { start.plusDays(it.toLong()) }
    val end = range.last()
    val showsToday = state.today >= start && state.today <= end
    fun shift(steps: Int) {
        anchorText = LocalDate.parse(anchorText).plusDays(steps.toLong() * days).toString()
    }
    // Keeps the events of the shown range, even weeks away from the ones loaded at start.
    LaunchedEffect(start) { vm.loadMonth(start) }

    val context = LocalContext.current
    fun openEvent(e: CalendarEvent) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    val textSize = when (days) {
        1 -> TS.body
        3 -> TS.caption
        else -> TS.micro
    }
    val hourH = if (days == 1) 56.dp else 48.dp
    var unfolded by rememberSaveable { mutableStateOf(false) }
    // Counts taps on Today, so each one also scrolls the hour grid back to now.
    var toNow by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000)
        }
    }

    val live = pool.filter { it.status != Status.CANCELLED }
    fun tasksOn(d: LocalDate) = live.filter { it.due == d || it.scheduled == d }
    // A task with a time sits in the grid on the date its reminder hangs on; on its other date it stays in the strip.
    fun timedOn(t: Task, d: LocalDate) = t.reminderTime != null && t.reminderAt?.toLocalDate() == d

    val strip: List<List<AllDayItem>> = range.map { d ->
        state.events.filter { it.inStrip() && it.covers(d) }.map { AllDayItem(event = it) } +
            tasksOn(d).filterNot { timedOn(it, d) }
                .sortedWith(compareBy<Task> { !it.isOpen }.thenBy { it.priority.ordinal })
                .map { AllDayItem(task = it) }
    }
    val blocks: List<List<TimelineBlock>> = range.map { d ->
        val from = d.atStartOfDay()
        val to = from.plusDays(1)
        val events = state.events.filter { !it.inStrip() && it.covers(d) }.map { e ->
            // Events that run past midnight are cut at the day's edges, so each day shows its own part.
            val a = if (e.begin <= from) 0 else e.begin.hour * 60 + e.begin.minute
            val b = if (e.end >= to) DAY_MIN else e.end.hour * 60 + e.end.minute
            TimelineBlock(a, b, event = e)
        }
        val tasks = tasksOn(d).filter { timedOn(it, d) }.map { t ->
            val a = t.reminderTime!!.let { it.hour * 60 + it.minute }
            TimelineBlock(a, a + 30, task = t)
        }
        packColumns(events + tasks)
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                rangeLabel(start, end, state.today), Modifier.weight(1f), color = C.text, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            TodayButton({ anchorText = state.today.toString(); toNow++ }, Modifier.padding(end = 6.dp))
            val (prev, next) = when (days) {
                1 -> tr("วันก่อน", "Previous day") to tr("วันถัดไป", "Next day")
                7 -> tr("สัปดาห์ก่อน", "Previous week") to tr("สัปดาห์ถัดไป", "Next week")
                else -> tr("ย้อน $days วัน", "Previous $days days") to tr("ถัดไป $days วัน", "Next $days days")
            }
            SquareButton(Ic.back, prev, { shift(-1) })
            Box(Modifier.width(6.dp))
            SquareButton(Ic.next, next, { shift(1) })
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 14.dp)
                .pointerInput(days) {
                    // A sideways swipe pages the range, as in Google Calendar.
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
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Box(Modifier.width(LabelW))
                range.forEach { d ->
                    val isToday = d == state.today
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.dayOfWeek.short(), color = if (isToday) C.accentText else C.faint, fontSize = TS.micro)
                        Text(
                            "${d.dayOfMonth}",
                            Modifier.padding(top = 2.dp).size(26.dp).clip(CircleShape).background(if (isToday) C.accent else Color.Transparent).padding(top = 3.dp),
                            color = if (isToday) C.onAccent else C.text, fontSize = TS.body, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Divider()

            if (strip.any { it.isNotEmpty() }) {
                val folds = strip.any { it.size > STRIP_FOLD }
                Row(Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    Box(Modifier.width(LabelW), contentAlignment = Alignment.TopCenter) {
                        if (folds) {
                            Icon(
                                if (unfolded) Ic.up else Ic.down,
                                if (unfolded) tr("ย่อ", "Show less") else tr("แสดงทั้งหมด", "Show all"),
                                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { unfolded = !unfolded }.padding(6.dp),
                                tint = C.muted,
                            )
                        }
                    }
                    strip.forEach { items ->
                        Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val shown = if (unfolded) items else items.take(STRIP_FOLD)
                            shown.forEach { item ->
                                AllDayPill(item, textSize) {
                                    val e = item.event
                                    if (e != null) openEvent(e) else item.task?.let(onOpen)
                                }
                            }
                            if (items.size > shown.size) {
                                Text(
                                    "+${items.size - shown.size}",
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { unfolded = true }.padding(horizontal = 5.dp, vertical = 1.dp),
                                    color = C.muted, fontSize = TS.micro, maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Divider()
            }

            // Opens near the current hour (07:00 when today is not shown); again when the number of days changes.
            val scroll = rememberScrollState()
            var scrolledFor by rememberSaveable { mutableStateOf(0) }
            val density = LocalDensity.current
            LaunchedEffect(days) {
                if (scrolledFor != days) {
                    val hour = if (showsToday) (LocalTime.now().hour - 1).coerceAtLeast(0) else 7
                    scroll.scrollTo(with(density) { (hourH * hour).roundToPx() })
                    scrolledFor = days
                }
            }
            LaunchedEffect(toNow) {
                if (toNow > 0) scroll.animateScrollTo(with(density) { (hourH * (LocalTime.now().hour - 1).coerceAtLeast(0)).roundToPx() })
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(top = 8.dp, bottom = NavClearance)) {
                Row(
                    Modifier.fillMaxWidth().height(hourH * 24).drawBehind {
                        val left = LabelW.toPx()
                        val hour = hourH.toPx()
                        val stroke = 1.dp.toPx()
                        for (h in 0..24) drawLine(C.divider, Offset(left - 6.dp.toPx(), h * hour), Offset(size.width, h * hour), stroke)
                        val colW = (size.width - left) / days
                        for (i in 0 until days) drawLine(C.divider, Offset(left + i * colW, 0f), Offset(left + i * colW, size.height), stroke)
                    },
                ) {
                    Box(Modifier.width(LabelW).fillMaxHeight()) {
                        for (h in 0 until 24) {
                            Text(
                                "%02d".format(h),
                                Modifier.fillMaxWidth().offset(y = hourH * h - 7.dp).padding(end = 10.dp),
                                color = C.faint, fontSize = TS.micro, lineHeight = 1.4.em, textAlign = TextAlign.End,
                            )
                        }
                    }
                    range.forEachIndexed { i, d ->
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            val colW = maxWidth
                            blocks[i].forEach { b ->
                                val h = hourH * ((b.bottom - b.top) / 60f)
                                val place = Modifier.offset(x = colW * b.col / b.cols, y = hourH * (b.top / 60f)).width(colW / b.cols).height(h)
                                val e = b.event
                                val t = b.task
                                if (e != null) {
                                    TimeBlock(
                                        e.title, if (days == 1) eventTime(e) else null,
                                        bar = C.teal, tint = C.teal.copy(alpha = 0.25f), fg = EventText, timeColor = C.tealText,
                                        size = textSize, height = h, done = false, onClick = { openEvent(e) }, modifier = place,
                                    )
                                } else if (t != null) {
                                    val tint = t.priority.tint
                                    TimeBlock(
                                        t.title, if (days == 1) t.reminderTime?.format(CLOCK) else null,
                                        bar = if (t.isOpen) tint else C.faint, tint = tint.copy(alpha = if (t.isOpen) 0.22f else 0.10f),
                                        fg = if (t.isOpen) C.text else C.muted, timeColor = C.muted,
                                        size = textSize, height = h, done = !t.isOpen, onClick = { onOpen(t) }, modifier = place,
                                    )
                                }
                            }
                            if (d == state.today) {
                                val y = hourH * ((now.hour * 60 + now.minute) / 60f)
                                Box(Modifier.fillMaxWidth().offset(y = y - 1.dp).height(2.dp).background(C.red))
                                Box(Modifier.offset(x = (-4).dp, y = y - 5.dp).size(10.dp).clip(CircleShape).background(C.red))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One block in the hour grid. [time] is shown only in the single-day view, where there is room for it. */
@Composable
private fun TimeBlock(
    title: String,
    time: String?,
    bar: Color,
    tint: Color,
    fg: Color,
    timeColor: Color,
    size: TextUnit,
    height: Dp,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    // Lines that fit the block; sp and dp match at the default font scale, and anything past the edge is clipped.
    val line = size.value * 1.25f
    val room = ((height.value - 6f) / line).toInt().coerceAtLeast(1)
    val deco = if (done) TextDecoration.LineThrough else null
    Row(
        // The plain background first, so the hour lines do not show through the tint.
        modifier.padding(start = 1.dp, end = 2.dp, bottom = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(C.bg).background(tint).clickable(onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
        Column(Modifier.padding(start = 4.dp, end = 3.dp, top = 2.dp)) {
            if (time != null && room < 2) {
                // Too short for two lines: the time follows the title on one line.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, Modifier.weight(1f, fill = false), color = fg, fontSize = size, lineHeight = size * 1.25f,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = deco,
                    )
                    Text(time, Modifier.padding(start = 6.dp), color = timeColor, fontSize = TS.caption, lineHeight = size * 1.25f, maxLines = 1)
                }
            } else {
                Text(
                    title, color = fg, fontSize = size, lineHeight = size * 1.25f,
                    maxLines = if (time != null) room - 1 else room, overflow = TextOverflow.Ellipsis, textDecoration = deco,
                )
                if (time != null) Text(time, color = timeColor, fontSize = TS.caption, lineHeight = TS.caption * 1.25f, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AllDayPill(item: AllDayItem, size: TextUnit, onClick: () -> Unit) {
    val task = item.task
    val done = task != null && !task.isOpen
    val bg = if (task == null) C.teal.copy(alpha = 0.25f) else if (done) C.faint.copy(alpha = 0.12f) else task.priority.tint.copy(alpha = 0.22f)
    Text(
        item.title,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 2.dp),
        color = if (task == null) EventText else if (done) C.muted else C.text,
        fontSize = size, lineHeight = size * 1.25f, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textDecoration = if (done) TextDecoration.LineThrough else null,
    )
}

/** An entry in the all-day strip: a calendar event, or a task without a time on that day. */
private class AllDayItem(val event: CalendarEvent? = null, val task: Task? = null) {
    val title: String get() = event?.title ?: task?.title.orEmpty()
}

/** One block in a day column, in minutes from midnight, already cut to the day. */
private class TimelineBlock(from: Int, to: Int, val event: CalendarEvent? = null, val task: Task? = null) {
    val top = from.coerceIn(0, DAY_MIN - MIN_BLOCK)
    val bottom = to.coerceIn(top + MIN_BLOCK, DAY_MIN)

    /** Its place among the blocks it overlaps: column [col] of [cols]. */
    var col = 0
    var cols = 1
}

/**
 * Lays overlapping blocks side by side. Blocks that overlap, directly or through a chain, form a group;
 * each block takes the first column free at its start, and the whole group shares one column count.
 */
private fun packColumns(blocks: List<TimelineBlock>): List<TimelineBlock> {
    val sorted = blocks.sortedWith(compareBy<TimelineBlock> { it.top }.thenByDescending { it.bottom })
    val group = ArrayList<TimelineBlock>()
    val columnEnds = ArrayList<Int>()
    var groupEnd = 0
    fun closeGroup() {
        group.forEach { it.cols = columnEnds.size }
        group.clear()
        columnEnds.clear()
    }
    sorted.forEach { b ->
        if (b.top >= groupEnd) closeGroup()
        val free = columnEnds.indexOfFirst { it <= b.top }
        if (free >= 0) {
            b.col = free
            columnEnds[free] = b.bottom
        } else {
            b.col = columnEnds.size
            columnEnds.add(b.bottom)
        }
        group.add(b)
        groupEnd = maxOf(groupEnd, b.bottom)
    }
    closeGroup()
    return sorted
}

/** All-day events, and timed ones a day or longer, go in the strip instead of filling whole columns. */
private fun CalendarEvent.inStrip() = allDay || Duration.between(begin, end).toHours() >= 24

/** Whether the event covers any part of [d]; an event with no length counts on the day it starts. */
private fun CalendarEvent.covers(d: LocalDate): Boolean {
    val from = d.atStartOfDay()
    val to = from.plusDays(1)
    return begin < to && (end > from || (end <= begin && begin >= from))
}

private fun eventTime(e: CalendarEvent) =
    tr("${e.begin.format(CLOCK)} ถึง ${e.end.format(CLOCK)}", "${e.begin.format(CLOCK)} to ${e.end.format(CLOCK)}")

/** "8 ถึง 14 ต.ค.", "29 ก.ย. ถึง 5 ต.ค.", or the full day name for a single day; the year only when it is not this year. */
private fun rangeLabel(start: LocalDate, end: LocalDate, today: LocalDate): String {
    val year = if (start.year != today.year || end.year != today.year) " ${end.year}" else ""
    if (start == end) {
        val day = start.format(DateTimeFormatter.ofPattern("EEEE d MMM", TH)) + year
        return if (start == today) tr("วันนี้, ", "Today, ") + day else day
    }
    val from = if (start.month == end.month) "${start.dayOfMonth}" else start.format(SHORT_DATE)
    val to = end.format(SHORT_DATE) + year
    return tr("$from ถึง $to", "$from to $to")
}
