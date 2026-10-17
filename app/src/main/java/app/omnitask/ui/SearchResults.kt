package app.omnitask.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.omnitask.data.CalendarReader
import app.omnitask.model.Countdown
import app.omnitask.model.Projects
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Which results the search shows: everything, only tasks, or only calendar events. */
private enum class SearchKind { ALL, TASKS, EVENTS }

/** The results last read: the text they are for and the events found, so the tasks can follow the same text. */
private class Found(val text: String, val events: List<EventHit>)

/** One calendar event found by the search: the instance shown, how many times it repeats in the range, and its calendar. */
private class EventHit(val event: CalendarEvent, val repeats: Int, val calendar: String?)

/** One dated heading of results, or one of the fixed ones (late, no date, past, done). */
private class SearchGroup(val label: String, val tasks: List<Task>, val events: List<EventHit>, val faded: Boolean = false)

/**
 * The search page of the Tasks tab: the tasks and the phone's calendar events whose titles hold [text], a year either
 * side of today, under one heading per day so what comes first reads first. Tapping an event goes to its day in the calendar.
 */
@Composable
fun SearchResults(
    state: UiState,
    vm: TaskViewModel,
    text: String,
    onOpen: (Task) -> Unit,
    onEvent: (CalendarEvent) -> Unit,
) {
    val context = LocalContext.current
    val today = state.today
    var kind by remember { mutableStateOf(SearchKind.ALL) }
    val needle = text.trim()

    // The provider is read off the main thread, a moment after typing stops; it reads again when the calendar changes.
    // Tasks and events show together, once the events for the text are in, so rows never move under a finger.
    val found by produceState(Found("", emptyList()), needle, today, state.events) {
        if (needle.isEmpty()) { value = Found("", emptyList()); return@produceState }
        delay(250)
        value = Found(needle, withContext(Dispatchers.IO) {
            runCatching {
                val names = CalendarReader.calendars(context).associate { it.id to it.name }
                // A repeating event comes back once per time; show the next one (or the last, when all are past) and the count.
                CalendarReader.search(context, needle, today).groupBy { it.id }.values.map { times ->
                    val shown = times.firstOrNull { it.begin.date >= today } ?: times.last()
                    EventHit(shown, times.size, names[shown.calendarId])
                }
            }.getOrDefault(emptyList())
        })
    }
    val shownText = found.text
    val events = found.events

    val tasks = remember(state.allTasks, shownText) {
        if (shownText.isEmpty()) emptyList()
        else state.allTasks.filter { it.title.contains(shownText, ignoreCase = true) || it.description.contains(shownText, ignoreCase = true) }
    }

    val groups = remember(tasks, events, kind, today) {
        buildGroups(if (kind == SearchKind.EVENTS) emptyList() else tasks, if (kind == SearchKind.TASKS) emptyList() else events, today)
    }
    val blocked = remember(state.tasks) { Projects.blocked(state.tasks) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = NavClearance + FabClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(tr("ทั้งหมด ${tasks.size + events.size}", "All ${tasks.size + events.size}"), kind == SearchKind.ALL, { kind = SearchKind.ALL })
                    Chip(tr("งาน ${tasks.size}", "Tasks ${tasks.size}"), kind == SearchKind.TASKS, { kind = SearchKind.TASKS })
                    Chip(tr("ปฏิทิน ${events.size}", "Calendar ${events.size}"), kind == SearchKind.EVENTS, { kind = SearchKind.EVENTS })
                }
            }
            if (groups.isEmpty() && shownText.isNotEmpty()) {
                item {
                    Card { Text(tr("ไม่พบงานหรือ event ที่มีคำนี้", "No tasks or events match"), Modifier.padding(16.dp), color = C.muted) }
                }
            }
            groups.forEach { g ->
                item(key = "s:" + g.label) {
                    Column(Modifier.alpha(if (g.faded) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(g.label, Modifier.padding(start = 4.dp), color = C.muted, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                        Card {
                            var first = true
                            g.tasks.forEach { t ->
                                if (!first) Divider(start = 48.dp)
                                first = false
                                TaskRow(
                                    t, today, { vm.toggleDone(t) }, { onOpen(t) },
                                    blockedBy = if (t in blocked) Projects.waitingOn(t, state.tasks) else null,
                                    progress = state.progressOf(t),
                                )
                            }
                            g.events.forEach { h ->
                                if (!first) Divider(start = 48.dp)
                                first = false
                                EventRow(h, showDate = g.faded || g.label == LATE_LABEL, today = today) { onEvent(h.event) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(hit: EventHit, showDate: Boolean, today: LocalDate, onClick: () -> Unit) {
    val e = hit.event
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Ic.calendar, null, tint = C.tealChip, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                if (showDate) add(dayLabel(e.begin.date, today))
                add(if (e.allDay) tr("ทั้งวัน", "All day") else "${hm(e.begin.hour, e.begin.minute)} " + tr("ถึง", "to") + " ${hm(e.end.hour, e.end.minute)}")
                if (hit.repeats > 1) add(tr("ซ้ำ ${hit.repeats} ครั้ง", "repeats ${hit.repeats} times"))
                hit.calendar?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            Text(parts.joinToString("   "), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
    }
}

private fun hm(h: Int, m: Int) = h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')

private val LATE_LABEL get() = tr("เลยกำหนด", "Overdue")

/** "ส. 24 ต.ค.", with the year (Buddhist in Thai) when it is not this one. */
private fun dayLabel(d: LocalDate, today: LocalDate): String =
    Countdown.day(d) + if (d.year == today.year) "" else " " + tr("${d.year + 543}", "${d.year}")

/** The day a task belongs to in the results: its due date, else when it is scheduled, else when it starts. */
private fun Task.searchDay(): LocalDate? = due ?: scheduled ?: start

/**
 * Late open tasks first, then one heading per day from today on (tasks and events together), then the open tasks with
 * no date, then the events already past (latest first), then the finished tasks.
 */
private fun buildGroups(tasks: List<Task>, events: List<EventHit>, today: LocalDate): List<SearchGroup> {
    val open = tasks.filter { it.isOpen }
    val done = tasks.filterNot { it.isOpen }
    val late = open.filter { t -> t.searchDay()?.let { it < today } == true }
    val undated = open.filter { it.searchDay() == null }
    val datedTasks = (open - late.toSet() - undated.toSet()).groupBy { it.searchDay()!! }
    val (coming, past) = events.partition { it.event.end.date >= today || it.event.begin.date >= today }
    val datedEvents = coming.groupBy { maxOf(it.event.begin.date, today) }

    val out = ArrayList<SearchGroup>()
    if (late.isNotEmpty()) out += SearchGroup(LATE_LABEL, late.sortedBy { it.searchDay() }, emptyList())
    (datedTasks.keys + datedEvents.keys).sorted().forEach { d ->
        val label = when (d) {
            today -> tr("วันนี้", "Today") + "  " + dayLabel(d, today)
            else -> dayLabel(d, today)
        }
        out += SearchGroup(label, datedTasks[d].orEmpty(), datedEvents[d].orEmpty().sortedBy { it.event.begin })
    }
    if (undated.isNotEmpty()) out += SearchGroup(tr("ไม่มีวัน", "No date"), undated, emptyList())
    if (past.isNotEmpty()) out += SearchGroup(tr("ผ่านไปแล้ว", "Past"), emptyList(), past.sortedByDescending { it.event.begin }, faded = true)
    if (done.isNotEmpty()) out += SearchGroup(tr("เสร็จแล้ว", "Done"), done, emptyList(), faded = true)
    return out
}
