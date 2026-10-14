package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import app.omnitask.time.*

/**
 * The countdowns the owner pins on the Focus screen: any number of them, each a task (by its deadline) or a calendar
 * event (by its start). They are kept as text ([Pin.key]) in the phone's settings or the browser.
 */
object Countdown {

    /** One pinned countdown. */
    sealed interface Pin {
        val key: String
    }

    /** A task, by its title (a line's position changes, its title rarely). */
    data class TaskPin(val title: String) : Pin {
        override val key get() = "task:$title"
    }

    /**
     * A calendar event as it was when pinned. [id] is the phone's event id, so an event moved in the calendar is
     * followed; 0 on the web, whose ids do not outlast the page.
     */
    data class EventPin(val id: Long, val title: String, val begin: LocalDateTime, val end: LocalDateTime, val allDay: Boolean) : Pin {
        override val key get() = "event:$id|$begin|$end|${if (allDay) 1 else 0}|$title"
    }

    /** The pin a [key] stands for; a bare title is the single task countdown kept before there could be several. */
    fun parse(key: String): Pin? = when {
        key.startsWith("task:") -> TaskPin(key.removePrefix("task:"))
        key.startsWith("event:") -> runCatching {
            val f = key.removePrefix("event:").split('|', limit = 5)
            EventPin(f[0].toLong(), f[4], LocalDateTime.parse(f[1]), LocalDateTime.parse(f[2]), f[3] == "1")
        }.getOrNull()
        key.isNotBlank() -> TaskPin(key)
        else -> null
    }

    fun parseAll(keys: List<String>): List<Pin> = keys.mapNotNull { parse(it) }.distinctBy { it.key }

    fun pinOf(e: CalendarEvent, keepId: Boolean = true) =
        EventPin(if (keepId) e.id else 0, e.title.replace('\n', ' ').trim(), e.begin, e.end, e.allDay)

    /** Pins without the events that ended over a month ago, so the list does not grow for ever. */
    fun tidy(pins: List<Pin>, now: LocalDateTime): List<Pin> =
        pins.filterNot { it is EventPin && it.end < now.date.minusDays(30).atStartOfDay() }.distinctBy { it.key }

    /**
     * The event [pin] stands for now: the same event by id and title (the instance nearest the pinned start, so a moved
     * one is followed; the title too, as the settings travel to phones whose ids differ), else one with its title and
     * start, else the pin as it was saved.
     */
    fun resolve(pin: EventPin, events: List<CalendarEvent>): CalendarEvent =
        events.filter { pin.id != 0L && it.id == pin.id && it.title.trim() == pin.title }.minByOrNull { abs(ChronoUnit.MINUTES.between(pin.begin, it.begin)) }
            ?: events.firstOrNull { it.title.trim() == pin.title && it.begin == pin.begin }
            ?: CalendarEvent(pin.id, pin.title, pin.begin, pin.end, pin.allDay)

    /** A countdown as Focus shows it. */
    data class Shown(val pin: Pin, val title: String, val text: String, val late: Boolean, val isEvent: Boolean, val taskKey: String?)

    /**
     * The pinned countdowns to show, soonest first. A task no longer open (ticked off, or gone) and an event that is
     * over are left out; a late task stays, marked [Shown.late].
     */
    fun shown(pins: List<Pin>, tasks: List<Task>, events: List<CalendarEvent>, now: LocalDateTime): List<Shown> =
        pins.mapNotNull { pin ->
            when (pin) {
                is TaskPin -> {
                    val t = tasks.filter { it.isOpen && it.title == pin.title }.minByOrNull { target(it) ?: LocalDate(9999, 12, 31) } ?: return@mapNotNull null
                    val late = target(t)?.let { it < now.date } == true
                    Shown(pin, t.title, text(t, now) ?: tr("ไม่มีวันครบกำหนด", "no date"), late, false, t.key) to at(t)
                }
                is EventPin -> {
                    val e = resolve(pin, events)
                    if (e.end <= now) return@mapNotNull null
                    Shown(pin, e.title, text(e, now), false, true, null) to e.begin
                }
            }
        }.sortedWith(compareBy(nullsLast()) { it.second }).map { it.first }

    /** A row of the picker: ticking it saves [pin] (or takes it away when [on]). [group] heads the rows it belongs with. */
    data class Choice(val pin: Pin, val title: String, val sub: String, val text: String, val late: Boolean, val on: Boolean, val group: String)

    /** Takes [choice] off when it is ticked, else adds it. */
    fun toggle(pins: List<Pin>, choice: Choice, now: LocalDateTime): List<Pin> =
        tidy(if (choice.on) pins.filterNot { it.key == choice.pin.key } else pins + choice.pin, now)

    /** The tasks tab: the pinned ones first, then every open task with a date, soonest first. */
    fun taskChoices(pins: List<Pin>, tasks: List<Task>, now: LocalDateTime): List<Choice> {
        val pinned = pins.filterIsInstance<TaskPin>().map { it.title }.toSet()
        val open = tasks.filter { it.isOpen }.groupBy { it.title }.mapNotNull { (_, same) -> same.minByOrNull { target(it) ?: LocalDate(9999, 12, 31) } }
        fun choice(t: Task, on: Boolean) = Choice(
            TaskPin(t.title), t.title, taskDay(t) ?: "", text(t, now) ?: tr("ไม่มีวันครบกำหนด", "no date"),
            target(t)?.let { it < now.date } == true, on, if (on) "" else tr("งานที่มีวัน", "Tasks with a date"),
        )
        return open.filter { it.title in pinned }.sortedWith(compareBy(nullsLast()) { at(it) }).map { choice(it, true) } +
            open.filter { it.title !in pinned && target(it) != null }.sortedWith(compareBy(nullsLast()) { at(it) }).map { choice(it, false) }
    }

    /**
     * The calendar tab: the pinned events first, then every event not over yet, soonest first, headed by month. On the
     * web [keepIds] is off, as its event ids do not outlast the page.
     */
    fun eventChoices(pins: List<Pin>, events: List<CalendarEvent>, now: LocalDateTime, keepIds: Boolean = true): List<Choice> {
        val pinned = pins.filterIsInstance<EventPin>().map { it to resolve(it, events) }.filter { it.second.end > now }.sortedBy { it.second.begin }
        val taken = pinned.map { it.second.title.trim() to it.second.begin }.toSet()
        val rest = CalendarEvent.distinct(events).filter { it.end > now && (it.title.trim() to it.begin) !in taken }.sortedBy { it.begin }
        return pinned.map { (pin, e) -> Choice(pin, e.title, eventDay(e), text(e, now), false, true, "") } +
            rest.map { e -> Choice(pinOf(e, keepIds), e.title, eventDay(e), text(e, now), false, false, month(e.begin.date, now.date)) }
    }

    /** "อีก 12 วัน", "อีก 3 ชม. 20 นาที", "วันนี้" or "เลยมา 2 วัน"; null when the task has no date. */
    fun text(task: Task, now: LocalDateTime): String? {
        val due = target(task) ?: return null
        val at = task.reminderAt?.takeIf { it.toLocalDate() == due }
        if (due == now.date && at != null) {
            val mins = ChronoUnit.MINUTES.between(now, at)
            return when {
                mins < 0 -> tr("เลยเวลามา ${hm(-mins)}", "${hm(-mins, false)} ago")
                else -> tr("อีก ${hm(mins)}", "in ${hm(mins, false)}")
            }
        }
        return days(due, now.date)
    }

    /** How far off an event's start is: hours and minutes on its day, else days; "ตอนนี้" while it runs. */
    fun text(e: CalendarEvent, now: LocalDateTime): String = when {
        e.begin <= now -> tr("ตอนนี้", "now")
        !e.allDay && e.begin.date == now.date -> {
            val mins = ChronoUnit.MINUTES.between(now, e.begin)
            tr("อีก ${hm(mins)}", "in ${hm(mins, false)}")
        }
        else -> days(e.begin.date, now.date)
    }

    private fun days(day: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, day)
        return when {
            days == 0L -> tr("วันนี้", "today")
            days == 1L -> tr("พรุ่งนี้", "tomorrow")
            days > 0 -> tr("อีก $days วัน", "in $days days")
            else -> tr("เลยมา ${-days} วัน", "${-days} days ago")
        }
    }

    /** The date the countdown runs to, for sorting the picker. */
    fun target(task: Task): LocalDate? = task.due ?: task.scheduled

    /** The moment a task counts down to: its date, at its reminder time when it has one that day. */
    private fun at(task: Task): LocalDateTime? {
        val day = target(task) ?: return null
        return task.reminderAt?.takeIf { it.date == day } ?: day.atStartOfDay()
    }

    /** "ครบ พฤ. 15 ต.ค." or "นัดทำ จ. 12 ต.ค.", the date the countdown runs to. */
    private fun taskDay(t: Task): String? = when {
        t.due != null -> tr("ครบ ", "Due ") + day(t.due)
        t.scheduled != null -> tr("นัดทำ ", "Scheduled ") + day(t.scheduled)
        else -> null
    }

    /** "อ. 20 ต.ค. ทั้งวัน" or "พฤ. 15 ต.ค. 08:00". */
    private fun eventDay(e: CalendarEvent): String =
        day(e.begin.date) + " " + if (e.allDay) tr("ทั้งวัน", "all day") else "${pad(e.begin.hour)}:${pad(e.begin.minute)}"

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    /** A short day like "พฤ. 15 ต.ค." ("Thu 15 Oct"). */
    fun day(d: LocalDate): String {
        val w = d.dayOfWeek.ordinal
        val m = d.monthNumber - 1
        return tr("${TH_DAYS[w]} ${d.dayOfMonth} ${TH_MONTHS[m]}", "${EN_DAYS[w]} ${d.dayOfMonth} ${EN_MONTHS[m]}")
    }

    /** The month heading in the calendar tab, with the year when it is not this one. */
    fun month(d: LocalDate, today: LocalDate): String {
        val name = tr(TH_MONTHS_LONG[d.monthNumber - 1], EN_MONTHS_LONG[d.monthNumber - 1])
        return if (d.year == today.year) name else "$name ${d.year}"
    }

    // Monday first, as kotlinx's DayOfWeek is.
    private val TH_DAYS = listOf("จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.", "อา.")
    private val EN_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val TH_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
    private val EN_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val TH_MONTHS_LONG = listOf(
        "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม",
    )
    private val EN_MONTHS_LONG = listOf(
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
    )

    private fun hm(minutes: Long, thai: Boolean = true): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (thai) {
            if (h > 0) "$h ชม. $m นาที" else "$m นาที"
        } else {
            if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }
}
