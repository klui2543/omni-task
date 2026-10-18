package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.omnitask.data.CalendarReader
import app.omnitask.model.QuickAdd
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*
import java.time.format.DateTimeFormatter

/**
 * The event a quick-add sentence would make, laid out like Google Calendar's new-event sheet: an all-day
 * switch, the start row and the end row (day on the left, time on the right), and the calendar. What the
 * sentence says fills it; a tap on a row changes it, and typing a new day or time starts over from the sentence.
 */
class QuickEventState {
    var allDay by mutableStateOf<Boolean?>(null)
    var startDay by mutableStateOf<LocalDate?>(null)
    var startTime by mutableStateOf<LocalTime?>(null)
    /** Minutes from start to end, for a timed event. */
    var length by mutableStateOf<Long?>(null)
    /** Days from the first day to the last, for an all-day event. */
    var span by mutableStateOf<Long?>(null)

    fun clear() { allDay = null; startDay = null; startTime = null; length = null; span = null }
}

/** Start and end as the calendar will hold them; an all-day event's [end] is its last day at midnight. */
data class QuickEvent(val allDay: Boolean, val start: LocalDateTime, val end: LocalDateTime)

private val NINE = LocalTime.of(9, 0)

fun quickEvent(draft: QuickAdd.Draft, s: QuickEventState, today: LocalDate): QuickEvent {
    val typedDay = draft.due ?: today
    val typedStart = typedDay.atTime(draft.time ?: NINE)
    val allDay = s.allDay ?: (draft.time == null)
    val day = s.startDay ?: typedDay
    if (allDay) {
        val span = s.span ?: draft.endDue?.let { maxOf(0L, ChronoUnit.DAYS.between(typedDay, it)) } ?: 0L
        return QuickEvent(true, day.atStartOfDay(), day.plusDays(span).atStartOfDay())
    }
    // "9:00-11:00" sets the length; "22:00-01:00" ends the next day. A date range alone keeps the start time on its last day.
    val typedLength = when {
        draft.endTime != null -> {
            var end = (draft.endDue ?: typedDay).atTime(draft.endTime!!)
            if (end <= typedStart) end = end.plusDays(1)
            ChronoUnit.MINUTES.between(typedStart, end)
        }
        draft.endDue != null -> ChronoUnit.MINUTES.between(typedStart, draft.endDue!!.atTime(draft.time ?: NINE).plusMinutes(60))
        else -> null
    }
    val start = day.atTime(s.startTime ?: draft.time ?: NINE)
    return QuickEvent(false, start, start.plusMinutes(s.length ?: typedLength ?: 60L))
}

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

private fun lengthText(m: Long) = when {
    m < 60 -> tr("$m นาที", "$m min")
    m % 60 == 0L -> tr("${m / 60} ชม.", "${m / 60} h")
    else -> tr("${m / 60} ชม. ${m % 60} นาที", "${m / 60} h ${m % 60} min")
}

private val DAY_DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", TH)

private fun dayLabel(day: LocalDate, today: LocalDate) = when (day) {
    today -> tr("วันนี้, ", "Today, ")
    today.plusDays(1) -> tr("พรุ่งนี้, ", "Tomorrow, ")
    else -> ""
} + day.format(DAY_DATE)

// The end list: every 15 minutes for the first hour, then every half hour, up to a day.
private val LENGTHS: List<Long> = (listOf(15L, 30L, 45L) + (60L..1440L step 30L))

@Composable
fun QuickEventRows(
    draft: QuickAdd.Draft, s: QuickEventState, today: LocalDate,
    calendars: List<CalendarReader.Calendar>, calendar: CalendarReader.Calendar?, onCalendar: (Long) -> Unit,
    onMore: (() -> Unit)?,
) {
    // A new day or time in the sentence wins over what was picked before.
    LaunchedEffect(draft.due, draft.time, draft.endTime, draft.endDue) { s.clear() }
    val ev = quickEvent(draft, s, today)
    var picking by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(top = 14.dp).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable { s.allDay = !ev.allDay },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Ic.clock, null, tint = C.muted, modifier = Modifier.size(20.dp))
            Text(tr("ทั้งวัน", "All day"), Modifier.weight(1f), color = C.text, fontSize = TS.body)
            OnOff(ev.allDay)
        }
        // Start.
        TimeRow(
            day = dayLabel(ev.start.date, today), onDay = { picking = "startDay" },
            time = if (ev.allDay) null else hm(ev.start.time), onTime = { menu = "start" },
        ) {
            if (menu == "start") {
                val scroll = rememberScrollState()
                val rowPx = with(LocalDensity.current) { 48.dp.roundToPx() }
                val chosen = ev.start.time.hour * 2 + ev.start.time.minute / 30
                LaunchedEffect(Unit) { scroll.scrollTo(maxOf(0, chosen - 2) * rowPx) }
                DropdownMenu(true, { menu = null }, Modifier.heightIn(max = 300.dp), scrollState = scroll, containerColor = C.raised) {
                    (0 until 48).forEach { i ->
                        val t = LocalTime.of(i / 2, i % 2 * 30)
                        MenuTime(hm(t), null, t == ev.start.time) { s.startTime = t; menu = null }
                    }
                }
            }
        }
        // End: on the same day the list shows each end with its length, the way Google Calendar does.
        val sameDay = ev.end.date == ev.start.date
        TimeRow(
            day = dayLabel(ev.end.date, today), onDay = { picking = "endDay" },
            time = if (ev.allDay) null else hm(ev.end.time), onTime = { menu = "end" },
        ) {
            if (menu == "end") {
                val scroll = rememberScrollState()
                val rowPx = with(LocalDensity.current) { 48.dp.roundToPx() }
                val options: List<Pair<LocalDateTime, Long?>> = if (sameDay) {
                    LENGTHS.map { ev.start.plusMinutes(it) to it }
                } else {
                    (0 until 48).map { i -> ev.end.date.atTime(i / 2, i % 2 * 30) to null }.filter { it.first > ev.start }
                }
                val chosen = options.indexOfFirst { it.first >= ev.end }.coerceAtLeast(0)
                LaunchedEffect(Unit) { scroll.scrollTo(maxOf(0, chosen - 2) * rowPx) }
                DropdownMenu(true, { menu = null }, Modifier.heightIn(max = 300.dp), scrollState = scroll, containerColor = C.raised) {
                    options.forEach { (end, len) ->
                        MenuTime(hm(end.time), len?.let { lengthText(it) }, end == ev.end) {
                            s.length = len ?: ChronoUnit.MINUTES.between(ev.start, end)
                            menu = null
                        }
                    }
                }
            }
        }
        // Calendar.
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(enabled = calendars.size > 1) { menu = "calendar" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Ic.calendar, null, tint = C.muted, modifier = Modifier.size(20.dp))
                Box(Modifier.size(12.dp).clip(CircleShape).background(C.accent))
                Text(
                    calendar?.let { it.name.ifBlank { it.account } } ?: tr("ยังไม่มีปฏิทินที่ลงได้", "No calendar to add to"),
                    Modifier.weight(1f).padding(start = 0.dp), color = C.text, fontSize = TS.body, maxLines = 1,
                )
            }
            DropdownMenu(menu == "calendar", { menu = null }, containerColor = C.raised) {
                calendars.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.name.ifBlank { c.account }, color = if (c.id == calendar?.id) C.accentText else C.text, fontSize = TS.body) },
                        onClick = { onCalendar(c.id); menu = null },
                    )
                }
            }
        }
        if (onMore != null) Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onMore),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.width(20.dp))
            Text(tr("ตัวเลือกเพิ่มเติม", "More options"), Modifier.weight(1f), color = C.muted, fontSize = TS.body)
            Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(16.dp))
        }
    }

    when (picking) {
        "startDay" -> DayDialog(tr("วันเริ่ม", "Start day"), ev.start.date, null, { s.startDay = it }, { picking = null })
        "endDay" -> DayDialog(tr("วันจบ", "End day"), ev.end.date, null, { d ->
            val first = ev.start.date
            if (ev.allDay) {
                s.span = maxOf(0L, ChronoUnit.DAYS.between(first, d))
            } else {
                // Keep the end's time on the new day; an end before the start is not an end.
                s.length = maxOf(15L, ChronoUnit.MINUTES.between(ev.start, d.atTime(ev.end.time)))
            }
        }, { picking = null })
    }
}

@Composable
private fun TimeRow(day: String, onDay: () -> Unit, time: String?, onTime: () -> Unit, menu: @Composable () -> Unit) {
    // The whole row picks the day; only the time on the right opens the time list.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onDay),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.width(20.dp))
        Box(Modifier.weight(1f)) {
            Text(day, Modifier.padding(vertical = 8.dp), color = C.text, fontSize = TS.body, maxLines = 1)
        }
        if (time != null) {
            Box {
                Text(
                    time, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTime).padding(horizontal = 8.dp, vertical = 8.dp),
                    color = C.text, fontSize = TS.body,
                )
                menu()
            }
        }
    }
}

@Composable
private fun MenuTime(time: String, length: String?, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(time, color = if (selected) C.accentText else C.text, fontSize = TS.body)
                if (length != null) Text("($length)", color = if (selected) C.accentText else C.muted, fontSize = TS.body)
            }
        },
        onClick = onClick,
        modifier = Modifier.height(48.dp).let { if (selected) it.background(C.accentSoft) else it },
    )
}
