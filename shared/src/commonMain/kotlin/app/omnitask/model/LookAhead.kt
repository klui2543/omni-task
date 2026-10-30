package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The secretary looking eight weeks ahead behind the scenes (owner's pick ก, mock look-ahead v2, 2026-10-30): the real
 * free time of every week from the shifts, the day job, events, trips and holidays, so today's plan can do work before
 * a tight week, give a whole free day's morning to something the owner wants to do, give own time back after tight
 * weeks, and warn early about a far deadline. The owner sees only short tags on today's page; nothing is written to
 * the notes beyond what today's plan already writes.
 *
 * The owner's six rules (mock look-ahead, section 3):
 * 1. Count the real usable time of every day for eight weeks. A week the shift calendar has not reached yet loses as
 *    much to shifts as the known weeks did on average (the app reads the calendar from yesterday on, so the weeks
 *    already posted stand in for "the months before").
 * 2. A tight week (less than half its usual free time): a whole free day before it gets one long slot of work, and in
 *    the tight week itself the sprint has one project.
 * 3. A whole free day: its morning is two hours of something the owner wants to do, before work.
 * 4. Fewer own-time days than half in the last two weeks: a week that is not tight gives own time back first.
 * 5. A deadline further than two weeks: the sprint line warns only when the time left is nearly not enough.
 * 6. What is planned for other days stays on the phone.
 */
object LookAhead {

    const val WEEKS = 8
    const val DAYS = WEEKS * 7L
    /** Usable minutes that make a day off a whole free day (a Sunday at home has about seven). */
    const val FREE_DAY = 5 * 60
    /** The free day's morning of the owner's own, and the work slot before a tight week. */
    const val LONG = 120
    const val MORNING = 9 * 60
    /** Own-time days in the last [OWED_SPAN] days below this: the time is owed (the plan offers one a day, about ten). */
    const val OWED_DAYS = 5
    const val OWED_SPAN = 14L

    data class Week(
        val start: LocalDate,
        /** Usable minutes from today on (meals, own time and the buffer taken off); a day after a night shift has none. */
        val usable: Int,
        /** The same days in a usual week: the day job only, no shifts, events or holidays. */
        val plain: Int,
        val nights: Int,
        /** The shift calendar reaches this week; an unknown week is estimated (rule 1) and never tagged. */
        val known: Boolean,
        /** Days counted: from today on, so this week may have fewer than seven. */
        val days: Int = 7,
        /** The share an unknown week loses to shifts (rule 1); none for a known week. */
        val cut: Double = 0.0,
        /** Usable minutes of each day, Monday first (before the cut; none before today). */
        val byDay: List<Int> = emptyList(),
    ) {
        val heavy: Boolean get() = known && plain > 0 && usable * 2 < plain
        val ratio: Double get() = if (plain > 0) usable.toDouble() / plain else 1.0
        val hours: Int get() = (usable + 30) / 60
        val plainHours: Int get() = (plain + 30) / 60
    }

    /** Usable minutes on [day] as the sprint counts them; none on a day after a night shift (the plan rests then) or away. */
    fun usable(day: LocalDate, inp: DayFill.Input): Int {
        if (away(day, inp)) return 0
        val fr = DayFill.frame(day, inp, if (day == inp.now.date) inp.now else null)
        return if (fr.mode == DayFill.Mode.AFTER_NIGHT) 0 else Sprint.usableIn(fr)
    }

    /** Days off at home: a holiday the owner wrote in is free time, unlike leave or a trip. */
    private val HOME = listOf("วันหยุด", "หยุดงาน", "หยุดชดเชย", "day off", "holiday")

    /** A whole day away ("ไปนครปฐม", leave, a holiday trip): the owner is not at home, so the plan's work has no time then. */
    fun away(day: LocalDate, inp: DayFill.Input): Boolean = inp.events.any { e ->
        RealDay.isDayAway(e, inp.facts) && HOME.none { e.title.contains(it, ignoreCase = true) } &&
            e.begin.date <= day && day < maxOf(e.end.date, e.begin.date.plusDays(1))
    }

    /**
     * The input with only the events near the eight weeks (the app reads a year of the calendar): every day's frame
     * filters the events, and the look-ahead counts about a hundred and seventy days' frames.
     */
    fun near(inp: DayFill.Input): DayFill.Input {
        val from = inp.now.date.minusDays(2).atStartOfDay()
        val to = DayFill.weekOf(inp.now.date).plusDays(DAYS + 2).atStartOfDay()
        val events = inp.events.filter { it.end >= from && it.begin <= to }
        return DayFill.Input(inp.tasks, events, inp.profile, inp.facts, inp.now, inp.memory, inp.moves, inp.sleeps, inp.sleepProblem, inp.ahead)
    }

    /** The same owner and tasks in a week with nothing but the day job. */
    private fun plain(inp: DayFill.Input) = DayFill.Input(inp.tasks, emptyList(), inp.profile, inp.facts.copy(daysOff = emptyMap()), inp.now)

    /** The last count and what it was counted from: today's plan is counted again every minute the page is open. */
    private class Key(
        val events: List<CalendarEvent>, val tasks: List<Task>, val sleeps: List<SleepSession>?,
        val profile: Profile, val facts: RealDay.Facts, val day: LocalDate, val half: Int,
    ) {
        override fun equals(other: Any?) = other is Key && events === other.events && tasks === other.tasks && sleeps === other.sleeps &&
            profile == other.profile && facts == other.facts && day == other.day && half == other.half
        override fun hashCode() = day.hashCode() * 31 + half
    }
    private var last: Pair<Key, List<Week>>? = null

    /**
     * This week (Monday to Sunday, from today on) and the seven after it. Counted again when the calendar, the tasks,
     * the watch's sleeps or the profile change, and every half hour (this week's hours run from now).
     */
    fun weeks(all: DayFill.Input): List<Week> {
        val key = Key(all.events, all.tasks, all.sleeps, all.profile, all.facts, all.now.date, (all.now.hour * 60 + all.now.minute) / 30)
        last?.let { (k, ws) -> if (k == key) return ws }
        return count(all).also { last = key to it }
    }

    private fun count(all: DayFill.Input): List<Week> {
        val today = all.now.date
        val p = all.profile
        val f = all.facts
        // How far the shift calendar reaches, from all of it; the days themselves from the events near them.
        val lastShift = all.events.filter { !f.isOthers(it) && RealDay.isShift(it, p, f) }.maxOfOrNull { it.begin.date }
        val inp = near(all)
        val plainInp = plain(inp)
        val shifts = inp.events.filter { !f.isOthers(it) && RealDay.isShift(it, p, f) }
        val noShifts = DayFill.Input(inp.tasks, inp.events - shifts.toSet(), inp.profile, inp.facts, inp.now)
        val first = DayFill.weekOf(today)
        class Raw(val week: Week, val dates: List<LocalDate>)
        val raw = (0 until WEEKS).map { w ->
            val start = first.plusDays(7L * w)
            val end = start.plusDays(7)
            val days = (0L..6L).map { start.plusDays(it) }.filter { it >= today }
            val byDay = (0L..6L).map { start.plusDays(it) }.map { if (it >= today) usable(it, inp) else 0 }
            Raw(
                Week(
                    start,
                    byDay.sum(),
                    days.sumOf { usable(it, plainInp) },
                    shifts.count { !it.allDay && RealDay.isNight(it, p, f) && it.begin.date >= start && it.begin.date < end },
                    known = lastShift == null || start <= lastShift,
                    days = days.size,
                    byDay = byDay,
                ),
                days,
            )
        }
        // Rule 1: a week past the last posted shift loses to shifts what the known weeks lost to shifts on average (a
        // trip or a holiday in a known week is its own, and the unknown week's own events are already counted).
        val lost = raw.filter { it.week.known && lastShift != null }.mapNotNull { r ->
            val without = r.dates.sumOf { usable(it, noShifts) }
            if (without > 0) maxOf(0.0, 1.0 - r.week.usable.toDouble() / without) else null
        }
        if (lost.isEmpty()) return raw.map { it.week }
        val share = lost.sum() / lost.size
        return raw.map { r -> r.week.let { if (it.known) it else it.copy(usable = (it.usable * (1.0 - share)).toInt(), cut = share) } }
    }

    /** Usable minutes on [day] as rule 1 counts them, for the free time before a deadline (past the eight weeks, as the last). */
    fun usableCounted(day: LocalDate, inp: DayFill.Input, ws: List<Week>): Int {
        val n = (day.toEpochDays() - ws.first().start.toEpochDays()).toInt()
        val w = ws.getOrNull(n / 7)
        val u = w?.byDay?.getOrNull(n % 7) ?: usable(day, inp)
        return (u * (1.0 - (w ?: ws.last()).cut)).toInt()
    }

    /** A day off with most of the day free, and no night shift around it (the frame from its start, not from now). */
    fun freeDay(day: LocalDate, inp: DayFill.Input): Boolean {
        if (away(day, inp)) return false
        val fr = DayFill.frame(day, inp)
        return fr.mode == DayFill.Mode.NORMAL && !fr.job && Sprint.usableIn(fr) >= FREE_DAY
    }

    /** The lightest of this week and the next three, with at least three quarters of its usual time. */
    fun lightest(ws: List<Week>, i: Int = 0): Boolean {
        val w = ws.getOrNull(i) ?: return false
        // A known week with most of it still ahead (on a Saturday, "the lightest week" would be two days).
        if (!w.known || w.days < 4 || w.heavy || w.ratio < 0.75) return false
        // Against the weeks the shift calendar reaches: an estimate cannot make a known week second best.
        return ws.drop(i).take(4).filter { it.known }.all { it.ratio <= w.ratio }
    }

    private fun nightsText(w: Week) = if (w.nights > 0) tr("เวรดึก ${w.nights} คืน ", "${w.nights} night shifts, ") else ""

    /** Why a week is tight: "เวรดึก 3 คืน ว่างราว 12 ชม. ปกติว่างราว 28 ชม.". */
    fun tightWhy(w: Week): String =
        nightsText(w) + tr("ว่างราว ${w.hours} ชม. ปกติว่างราว ${w.plainHours} ชม.", "about ${w.hours} h free, usually about ${w.plainHours} h")

    /** The tag a week gets: tight or the lightest; none for a usual week. */
    fun weekHint(ws: List<Week>, i: Int): DayFill.Hint? {
        val w = ws.getOrNull(i) ?: return null
        return when {
            w.heavy -> DayFill.Hint(
                tr("แน่น ${w.hours} ชม.", "Tight, ${w.hours} h"),
                tightWhy(w),
                DayFill.Tone.HEAVY,
            )
            lightest(ws, i) -> {
                val nextTight = ws.drop(i + 1).take(3).firstOrNull { it.heavy }
                DayFill.Hint(
                    tr("เบาสุด ${w.hours} ชม.", "Lightest, ${w.hours} h"),
                    tr("เบาสุดใน 4 สัปดาห์ข้างหน้า", "The lightest of the next four weeks") +
                        (nextTight?.let { n -> tr(" สัปดาห์ ${DayFill.shortDay(n.start)} แน่น ", ". The week of ${n.start} is tight: ") + nightsText(n) + tr("งานที่ค้างควรจบในสัปดาห์นี้", "work left is best finished this week") } ?: ""),
                    DayFill.Tone.LIGHT,
                )
            }
            else -> null
        }
    }

    /** Rule 2: the next calendar week is tight. */
    fun beforeTight(ws: List<Week>): Week? = ws.getOrNull(1)?.takeIf { it.heavy }

    /** Days in the last [OWED_SPAN] days with own time: a myspace task ticked, a progress line on one, or its slot taken. */
    fun ownDays(inp: DayFill.Input): Int {
        val today = inp.now.date
        val from = today.minusDays(OWED_SPAN)
        val days = HashSet<LocalDate>()
        fun add(d: LocalDate?) { if (d != null && d >= from && d < today) days += d }
        val mine = inp.tasks.filter { DayFill.isMine(it) }
        mine.forEach { t ->
            add(t.done)
            StuckLog.entries(t).forEach { add(it.day) }
            inp.memory.accepted[DayFill.ident(t)]?.let { a -> runCatching { LocalDate.parse(a.day) }.getOrNull()?.let(::add) }
        }
        return days.size
    }

    /** Rule 4: little own time lately, and this week is not tight. */
    fun owed(inp: DayFill.Input, ws: List<Week>): Int? {
        if (ws.firstOrNull()?.heavy != false) return null
        val n = ownDays(inp)
        return n.takeIf { it < OWED_DAYS }
    }

    /**
     * Rule 6: what a whole free day ahead would hold, as today's plan would place it then (the owner's morning and the
     * work before a tight week), for the week card. Nothing is written: it is planned again on the day.
     */
    fun booked(inp: DayFill.Input, day: LocalDate): List<DayFill.Placed> {
        if (day <= inp.now.date || !freeDay(day, inp)) return emptyList()
        val then = DayFill.Input(inp.tasks, inp.events, inp.profile, inp.facts, day.atTime(0, 0), inp.memory, inp.moves, ahead = true)
        return DayFill.plan(then).placed.filter { p -> p.item.hints.any { it.tone == DayFill.Tone.WANT || it.tone == DayFill.Tone.HEAVY } }
    }
}
