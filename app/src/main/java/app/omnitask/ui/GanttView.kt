package app.omnitask.ui

import androidx.compose.ui.geometry.Size

import androidx.compose.ui.draw.drawWithContent

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.omnitask.data.CalendarReader
import app.omnitask.model.Projects
import app.omnitask.model.StuckLog
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import app.omnitask.time.*

/** The fewest and most days the Timeline shows across; two fingers pinch between them. */
private const val MIN_DAYS = 3f
private const val MAX_DAYS = 90f

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
private val GanttEventColors get() = GanttColors(C.teal, C.teal.copy(alpha = 0.16f), C.eventText, C.tealText)

/** Whose work the Timeline shows: everyone's, mine (all but Claude's), or Claude's. */
internal enum class TimelineWho { ALL, ME, CLAUDE }

/**
 * The Timeline set inside another page (the assistant's): that page owns the chips and the picked task, rings the
 * tasks in [picks] in the accent, and shows its own card for the picked task, so the Timeline drops its header,
 * chips, events and card. A picked bar is tinted rather than edged in white.
 */
internal class TimelineEmbed(
    val who: TimelineWho,
    val staleOnly: Boolean,
    val picks: Set<String>,
    val selKey: String?,
    val onSelect: (Task?) -> Unit,
)

/** Days an open task has sat since it was made or last moved (a note, a subtask ticked), when that is long. */
private fun staleDays(t: Task, subtasks: List<Task>, today: LocalDate): Long? {
    if (!t.isOpen || t.recurrence != null) return null
    val base = listOfNotNull(t.created, StuckLog.lastMove(t, subtasks)).maxOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(base, today)
    return days.takeIf { it >= StuckLog.ASK_AFTER_DAYS }
}

/**
 * The Timeline: tasks and Google Calendar events as bars across the days. Two fingers pinch from a few days to three
 * months, one finger slides through time. Chips pick whose work shows, or only work that has sat too long. A tap on
 * a bar picks it: the tasks it waits for and that wait for it stay bright, and a card says when, why it waits, and
 * offers to open it or note why it is stuck.
 */
@Composable
fun GanttView(state: UiState, tasks: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit, embed: TimelineEmbed? = null) {
    var span by rememberSaveable { mutableFloatStateOf(14f) }
    // The first day shown, in days from today (fractions while sliding).
    var start by rememberSaveable { mutableFloatStateOf(-1f) }
    var showEventsOwn by rememberSaveable { mutableStateOf(true) }
    var whoOwn by rememberSaveable { mutableStateOf(TimelineWho.ALL) }
    var staleOnlyOwn by rememberSaveable { mutableStateOf(false) }
    var selKeyOwn by rememberSaveable { mutableStateOf<String?>(null) }
    // Inside the assistant's page that page owns these; the Timeline tab keeps its own.
    val showEvents = embed == null && showEventsOwn
    val who = embed?.who ?: whoOwn
    val staleOnly = embed?.staleOnly ?: staleOnlyOwn
    val selKey = if (embed != null) embed.selKey else selKeyOwn
    var noting by remember { mutableStateOf<Task?>(null) }
    val today = state.today
    val whole = kotlin.math.floor(start).toLong()
    val frac = start - whole
    val first = today.plusDays(whole)
    val days = kotlin.math.ceil(span + frac).toInt().coerceAtLeast(1)
    val last = first.plusDays(days - 1L)
    // Keeps the events of the shown range, also when it runs into the next month.
    LaunchedEffect(first, days) {
        // Up to three months: a day in each month the range touches.
        var m = first
        while (m <= last) { vm.loadMonth(m); m = m.plusDays(28) }
        vm.loadMonth(last)
    }

    val events = rememberEventActions(vm)
    val byId = remember(state.allTasks) { state.allTasks.filter { it.id != null }.associateBy { it.id } }
    fun blockerOf(t: Task): Task? = t.dependsOn.mapNotNull { byId[it] }.firstOrNull { it.isOpen }
    fun isMine(t: Task) = t.tags.none { it.equals(CLAUDE_TAG, ignoreCase = true) }
    fun isClaude(t: Task) = !isMine(t)
    val stale = remember(tasks, today) { tasks.associateWith { staleDays(it, state.subtasksOf(it), today) } }
    fun needs(t: Task) = t.isOpen && (stale[t] != null || t.due?.let { it < today } == true)

    // Dated tasks that are open, or were done within the range; a bar runs from start (or scheduled) to due.
    val pool = tasks.filter { t -> t.status != Status.CANCELLED && (t.isOpen || t.done?.let { it >= first } == true) }
    val picked = pool.filter { t ->
        (!staleOnly || needs(t)) && when (who) {
            TimelineWho.ALL -> true
            TimelineWho.ME -> isMine(t)
            TimelineWho.CLAUDE -> isClaude(t)
        }
    }
    val spans = picked.mapNotNull { t ->
        val a = t.start ?: t.scheduled ?: t.due ?: return@mapNotNull null
        val b = t.due ?: t.scheduled ?: a
        GanttSpan(t, a, if (b < a) a else b)
    }
    val noProject = tr("ไม่มีโปรเจกต์", "No project")
    val groups = spans
        .groupBy { Projects.projectOf(it.task) ?: noProject }
        .toSortedMap(compareBy<String> { it == noProject }.thenBy { it })
    // Events overlapping the range, one row per title, so a recurring shift reads as a single row of bars.
    val eventRows = if (!showEvents || staleOnly) emptyList() else state.events
        .filter { it.begin.toLocalDate() <= last && it.ganttLastDay() >= first }
        .groupBy { it.title }
        .toList()
        .sortedBy { (_, list) -> list.minOf { it.begin } }

    val sel = spans.firstOrNull { it.task.key == selKey }?.task
    val related = sel?.let { s -> setOfNotNull(s.key, blockerOf(s)?.key) + pool.filter { t -> t.dependsOn.any { byId[it] === s } }.map { it.key } }.orEmpty()

    noting?.let { t -> StuckDialog(t, state.subtasksOf(t), today, { vm.noteStuck(t, it) }) { noting = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowW = maxOf(maxWidth - 28.dp, 1.dp)
        val dayW = rowW / span
        val shift = dayW * frac
        val density = LocalDensity.current.density
        Column(Modifier.fillMaxSize()) {
            if (embed == null) Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(ganttRange(today.plusDays(kotlin.math.round(start).toLong()), today.plusDays(kotlin.math.round(start + span).toLong() - 1), today), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(spanText(span), color = C.muted, fontSize = TS.micro, maxLines = 1)
                }
                TodayButton({ start = -1f }, Modifier.padding(end = 6.dp))
                SquareButton(Ic.back, tr("ก่อนหน้า", "Earlier"), { start -= maxOf(1f, span * 0.75f) })
                Box(Modifier.width(6.dp))
                SquareButton(Ic.next, tr("ถัดไป", "Later"), { start += maxOf(1f, span * 0.75f) })
            }
            if (embed == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(tr("ทั้งหมด", "All"), who == TimelineWho.ALL && !staleOnly, { whoOwn = TimelineWho.ALL; staleOnlyOwn = false })
                Chip(tr("ฉัน", "Me"), who == TimelineWho.ME && !staleOnly, { whoOwn = TimelineWho.ME; staleOnlyOwn = false }, dot = C.tealChip)
                Chip("Claude", who == TimelineWho.CLAUDE && !staleOnly, { whoOwn = TimelineWho.CLAUDE; staleOnlyOwn = false }, dot = C.accentText)
                Chip(tr("ค้างนาน ${pool.count { needs(it) }}", "Stuck ${pool.count { needs(it) }}"), staleOnly, { staleOnlyOwn = !staleOnlyOwn }, dot = C.amber)
                if (state.calendarAccess == true) Chip(tr("นัดหมาย", "Events"), showEvents, { showEventsOwn = !showEventsOwn }, dot = C.teal)
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (embed == null) 14.dp else 0.dp).clipToBounds()
                    // One finger slides through the days; the list still scrolls up and down.
                    .pointerInput(span) {
                        detectHorizontalDragGestures { change, amount ->
                            start -= amount / (dayW.value * density)
                            change.consume()
                        }
                    }
                    // Two fingers pinch: apart shows fewer, wider days; together shows more. The day under the fingers stays put.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f) {
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val at = start + span * (event.calculateCentroid().x / w)
                                        val next = (span / zoom).coerceIn(MIN_DAYS, MAX_DAYS)
                                        start = at - next * (event.calculateCentroid().x / w)
                                        span = next
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                Column(Modifier.offset(x = -shift).width(dayW * days)) {
                    GanttDays(first, days, today, dayW)
                    Divider()
                    val todayAt = ChronoUnit.DAYS.between(first, today).toInt()
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(top = 2.dp, bottom = if (embed != null) 8.dp else NavClearance + if (sel != null) 160.dp else 0.dp)
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
                        val laneW = dayW * days
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
                                        dim = e.ganttLastDay() < today || sel != null,
                                        onClick = { events.open(e) },
                                        onLongClick = { events.inspect(e) },
                                    )
                                },
                                colors = GanttEventColors,
                                dayW = dayW,
                                rowW = laneW,
                                onClick = { events.open(next) },
                                onLongClick = { events.inspect(next) },
                            )
                        }
                        groups.forEach { (project, list) ->
                            GanttCaption(project, C.accentText)
                            list.sortedBy { it.start }.forEach { s ->
                                val t = s.task
                                val blocker = blockerOf(t)
                                val days0 = stale[t]
                                val late = t.isOpen && t.due?.let { it < today } == true
                                val note = when {
                                    blocker != null -> tr("รอ: ${blocker.title}", "Waiting on ${blocker.title}")
                                    days0 != null -> tr("ค้าง $days0 วัน", "Stuck $days0 days")
                                    late -> tr("เลย ${ChronoUnit.DAYS.between(t.due!!, today)} วัน", "${ChronoUnit.DAYS.between(t.due!!, today)} days late")
                                    else -> null
                                }
                                GanttTaskLane(
                                    s, first, days, today, dayW, laneW,
                                    colors = timelineColors(t, isClaude(t), late || days0 != null),
                                    note = note, waiting = blocker != null, stuck = days0 != null,
                                    dim = sel != null && t.key !in related,
                                    picked = t.key == selKey,
                                    embedded = embed != null,
                                    aiPick = embed?.picks?.contains(t.key) == true,
                                ) {
                                    val next = if (selKey == t.key) null else t.key
                                    if (embed != null) embed.onSelect(if (next == null) null else t) else selKeyOwn = next
                                }
                            }
                        }
                        if (groups.isEmpty() && eventRows.isEmpty()) {
                            Text(
                                if (staleOnly) tr("ไม่มีงานค้างนาน", "Nothing has sat too long") else tr("ยังไม่มีงานที่มีวันที่", "No dated tasks yet"),
                                Modifier.padding(16.dp), color = C.muted, fontSize = TS.body,
                            )
                        }
                    }
                }
            }
        }

        // The picked task: when it runs, why it waits, and what to do with it.
        if (sel != null && embed == null) {
            val blocker = blockerOf(sel)
            val waiters = pool.filter { t -> t.isOpen && t.dependsOn.any { byId[it] === sel } }
            val days0 = stale[sel]
            Column(
                Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = NavClearance + 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)).background(C.raised).border(1.dp, C.cardBorder, RoundedCornerShape(18.dp))
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sel.title, Modifier.weight(1f), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (isClaude(sel)) "Claude" else tr("ฉัน", "Me"),
                        Modifier.clip(RoundedCornerShape(10.dp)).background(if (isClaude(sel)) C.accentSoft else C.tealSoft).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = if (isClaude(sel)) C.accentText else C.tealChip, fontSize = TS.micro,
                    )
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { selKeyOwn = null }, contentAlignment = Alignment.Center) {
                        Icon(Ic.close, tr("ปิด", "Close"), tint = C.muted, modifier = Modifier.size(18.dp))
                    }
                }
                val a = sel.start ?: sel.scheduled ?: sel.due
                val b = sel.due ?: sel.scheduled ?: a
                if (a != null && b != null) {
                    val n = ChronoUnit.DAYS.between(a, b) + 1
                    Text(ganttRange(a, b, today) + if (n > 1) tr("  ($n วัน)", "  ($n days)") else "", color = C.text2, fontSize = TS.caption)
                }
                if (days0 != null) Text(tr("ไม่ขยับมา $days0 วัน", "Not moved for $days0 days"), color = C.amber, fontSize = TS.caption)
                else if (sel.isOpen && sel.due?.let { it < today } == true) Text(tr("เลยกำหนด ${ChronoUnit.DAYS.between(sel.due!!, today)} วัน", "${ChronoUnit.DAYS.between(sel.due!!, today)} days late"), color = C.amber, fontSize = TS.caption)
                if (blocker != null) Text(tr("รอ: ${blocker.title}", "Waiting on ${blocker.title}"), color = C.text2, fontSize = TS.caption)
                waiters.forEach { w -> Text(tr("งานที่รอข้อนี้: ${w.title}", "Waiting on this: ${w.title}"), color = C.text2, fontSize = TS.caption) }
                Row(Modifier.padding(top = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (needs(sel)) GhostButton(tr("เขียนเหตุผลที่ค้าง", "Note why it is stuck"), { noting = sel }, Modifier.weight(1f))
                    PrimaryButton(tr("เปิดงาน", "Open"), { onOpen(sel) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** "เห็น 14 วัน", or about how many months when it is long. */
private fun spanText(span: Float): String {
    val d = kotlin.math.round(span).toInt()
    return if (d >= 56) tr("เห็นประมาณ ${kotlin.math.round(span / 3f) / 10f} เดือน", "About ${kotlin.math.round(span / 3f) / 10f} months") else tr("เห็น $d วัน", "$d days")
}

/** Whose turn colours the bar: mine in teal, Claude's in the accent; done work is muted, late or stuck work turns its note amber. */
private fun timelineColors(t: Task, claude: Boolean, warn: Boolean): GanttColors {
    if (!t.isOpen) return GanttColors(C.lime.copy(alpha = 0.55f), C.lime.copy(alpha = 0.08f), C.muted, C.faint, struck = true)
    val tint = if (claude) C.accentText else if (t.tags.any { it.equals(ME_TAG, ignoreCase = true) }) C.tealChip else C.muted
    return GanttColors(tint, tint.copy(alpha = 0.16f), C.text, if (warn) C.amber else C.muted)
}

/** A task's bar from its first to its last day. */
private class GanttSpan(val task: Task, val start: LocalDate, val end: LocalDate)

/** One bar in a lane, in days from the range start, already cut to the range. */
private class GanttBar(val from: Int, val to: Int, val date: String, val dim: Boolean = false, val onClick: () -> Unit, val onLongClick: (() -> Unit)? = null)

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

/** Weekday and date over each day; today in the accent circle. Narrow days label every other day, then Mondays only. */
@Composable
private fun GanttDays(first: LocalDate, days: Int, today: LocalDate, dayW: Dp) {
    // A label may be wider than a narrow column; it stays centred on its day and spills into the gap beside it.
    val labelW = maxOf(dayW, 28.dp)
    val step = when {
        dayW >= 22.dp -> 1
        dayW >= 12.dp -> 2
        else -> 7
    }
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        for (i in 0 until days) {
            val d = first.plusDays(i.toLong())
            val isToday = d == today
            // Counted from today, so today always keeps its label; at a week a step, Mondays carry the month.
            val shown = isToday || when (step) {
                1 -> true
                2 -> ChronoUnit.DAYS.between(today, d).mod(2L) == 0L
                else -> d.dayOfWeek == DayOfWeek.MONDAY
            }
            if (shown) {
                Column(Modifier.offset(x = dayW * i + (dayW - labelW) / 2).width(labelW), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (step == 7 && !isToday) d.format(SHORT_DATE) else d.dayOfWeek.short(), color = if (isToday) C.accentText else C.faint, fontSize = TS.micro, maxLines = 1, softWrap = false)
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

/** A task's row: its bar cut to the range, or a pointer at the edge when it lies wholly before or after it. */
@Composable
private fun GanttTaskLane(
    s: GanttSpan, first: LocalDate, days: Int, today: LocalDate, dayW: Dp, rowW: Dp,
    colors: GanttColors, note: String?, waiting: Boolean, stuck: Boolean, dim: Boolean, picked: Boolean,
    embedded: Boolean = false, aiPick: Boolean = false, onClick: () -> Unit,
) {
    val date = note ?: ganttRange(s.start, s.end, today)
    val from = ChronoUnit.DAYS.between(first, s.start)
    val to = ChronoUnit.DAYS.between(first, s.end)
    Box(Modifier.alpha(if (dim) 0.3f else 1f)) {
        when {
            to < 0 -> GanttEdgeLane(s.task.title, date, colors, before = true, onClick = onClick)
            from >= days -> GanttEdgeLane(s.task.title, date, colors, before = false, onClick = onClick)
            else -> {
                val bar = GanttBar(from.coerceAtLeast(0L).toInt(), to.coerceAtMost(days - 1L).toInt(), date, onClick = onClick)
                GanttLane(
                    s.task.title, listOf(bar), colors, dayW, rowW, onClick, dashed = waiting, stripe = if (stuck) C.amber else null,
                    picked = picked && !embedded, tinted = picked && embedded, ring = if (aiPick) C.accent else null,
                )
            }
        }
    }
}

/** One row of bars sharing a title: a task's single bar, or every occurrence of an event. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GanttLane(
    title: String, bars: List<GanttBar>, colors: GanttColors, dayW: Dp, rowW: Dp, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    dashed: Boolean = false, stripe: Color? = null, picked: Boolean = false, tinted: Boolean = false, ring: Color? = null,
) {
    // A sliver between neighbouring days, thinner when thirty days share a phone.
    val inset = if (dayW < 20.dp) 1.dp else 2.dp
    val placed = bars.sortedBy { it.from }.map { b ->
        GanttPlaced(b, dayW * b.from + inset, (dayW * (b.to - b.from + 1) - inset * 2).coerceAtLeast(2.dp))
    }
    val beside = if (placed.isEmpty() || placed.any { it.width >= GanttInsideMin }) null else ganttBeside(placed, rowW)
    Box(Modifier.fillMaxWidth().height(GanttLaneH).clipToBounds().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        placed.forEach { p ->
            Row(
                Modifier.align(Alignment.CenterStart).offset(x = p.x).width(p.width).height(GanttBarH)
                    .alpha(if (p.bar.dim) 0.55f else 1f).clip(GanttBarShape).background(if (tinted) colors.accent.copy(alpha = 0.4f) else colors.fill)
                    // Waiting on other work: a dashed edge. Picked: a solid light edge.
                    .then(
                        if (dashed || picked) Modifier.drawBehind {
                            val w = 1.5.dp.toPx()
                            drawRoundRect(
                                if (picked) C.text else colors.accent, cornerRadius = CornerRadius(10.dp.toPx()),
                                style = Stroke(w, pathEffect = if (picked) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                            )
                        } else Modifier,
                    )
                    // The assistant's pick: a ring in the accent, over any other edge.
                    .then(
                        if (ring != null) Modifier.drawWithContent {
                            drawContent()
                            val w = 2.dp.toPx()
                            drawRoundRect(ring, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(w))
                        } else Modifier,
                    )
                    .combinedClickable(onClick = p.bar.onClick, onLongClick = p.bar.onLongClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(if (stripe != null) 4.dp else 3.dp).fillMaxHeight().background(stripe ?: colors.accent))
                if (p.width >= GanttInsideMin) GanttBarText(title, p.bar.date, colors, Modifier.padding(start = 7.dp, end = 6.dp))
            }
        }
        if (beside != null) {
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = beside.x).width(beside.width.coerceAtLeast(0.dp))
                    .combinedClickable(onClick = beside.placed.bar.onClick, onLongClick = beside.placed.bar.onLongClick),
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
