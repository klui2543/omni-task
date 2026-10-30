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
 * The owner's six rules (mock look-ahead, section 3, agreed with mock v2):
 * 1. Count the real usable time of every day for eight weeks. A month the shift calendar has not reached yet loses as
 *    much to shifts as the two months before did on average.
 * 2. A tight week (less than half its usual free time): a whole free day before it gets one long slot of work, and in
 *    the tight week itself the sprint has one project.
 * 3. A whole free day: its morning is two hours of something the owner wants to do, before work.
 * 4. Less own time than half of what the plan offered in the last two weeks: the next light week gives it back. The app
 *    keeps no record of the slots it offered, so for now this counts the days with own time instead ([OWED_DAYS]).
 * 5. A deadline further than two weeks: the sprint line warns only when the time left is nearly not enough (or not
 *    enough), with the easiest way to make it.
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
    /** Rule 3 is the morning's: past this without it, own time is the evening's as before. */
    const val NOON = 12 * 60
    /**
     * Own-time days in the last [OWED_SPAN] days below this: the time is owed (rule 4). The plan offers one own-time slot
     * a day, about ten in two weeks with the shifts, and keeps no record of them, so half is five days.
     */
    const val OWED_DAYS = 5
    const val OWED_SPAN = 14L
    /** Weeks before this one that estimate a month not posted yet (rule 1: two months). */
    const val PAST_WEEKS = 8

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
        /** The share an unknown week's days not posted lose to shifts (rule 1); none for a known week. */
        val cut: Double = 0.0,
        /** Usable minutes of each day, Monday first (a day not posted after the cut; none before today). */
        val byDay: List<Int> = emptyList(),
        /** The whole week from its Monday, each day from its start: rule 4 judges the week, not the days left in it. */
        val weekUsable: Int = usable,
        val weekPlain: Int = plain,
    ) {
        /** Tight (rule 2): less than half its usual time, with most of the week still ahead. */
        val heavy: Boolean get() = known && days >= 3 && plain > 0 && usable * 2 < plain
        /** Light (rule 4): known, with three quarters of its usual time or more. */
        val light: Boolean get() = known && days >= 3 && !heavy && ratio >= 0.75
        val ratio: Double get() = if (plain > 0) usable.toDouble() / plain else 1.0
        /** Light as a whole week, with three quarters of its usual time or more (rule 4). */
        val lightWeek: Boolean get() = known && weekPlain > 0 && weekUsable * 4 >= weekPlain * 3
        val hours: Int get() = (usable + 30) / 60
        val plainHours: Int get() = (plain + 30) / 60
    }

    /** Usable minutes on [day] as the sprint counts them; none on a day after a night shift (the plan rests then) or away. */
    fun usable(day: LocalDate, inp: DayFill.Input, fromNow: Boolean = true): Int {
        if (away(day, inp)) return 0
        val fr = DayFill.frame(day, inp, if (fromNow && day == inp.now.date) inp.now else null)
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
     * The input the look-ahead counts from: its own read of the calendar when there is one, and only the events near
     * the weeks it counts (every day's frame filters the events, and about three hundred days' frames are counted).
     */
    fun near(inp: DayFill.Input): DayFill.Input {
        val from = DayFill.weekOf(inp.now.date).minusDays(7L * PAST_WEEKS + 2).atStartOfDay()
        val to = DayFill.weekOf(inp.now.date).plusDays(DAYS + 2).atStartOfDay()
        val events = (inp.far ?: inp.events).filter { it.end >= from && it.begin <= to }
        return DayFill.Input(inp.tasks, events, inp.profile, inp.facts, inp.now, inp.memory, inp.moves, inp.sleeps, inp.sleepProblem, inp.ahead)
    }

    /** The same owner and tasks in a week with nothing but the day job. */
    private fun plain(inp: DayFill.Input) = DayFill.Input(inp.tasks, emptyList(), inp.profile, inp.facts.copy(daysOff = emptyMap()), inp.now)

    /** What a count was made from: today's plan is counted again every minute the page is open. */
    private class Key(
        val events: List<CalendarEvent>, val tasks: List<Task>, val sleeps: List<SleepSession>?,
        val profile: Profile, val facts: RealDay.Facts, val day: LocalDate, val half: Int,
    ) {
        override fun equals(other: Any?) = other is Key && events === other.events && tasks === other.tasks && sleeps === other.sleeps &&
            profile == other.profile && facts == other.facts && day == other.day && half == other.half
        override fun hashCode() = day.hashCode() * 31 + half
    }

    /**
     * The last few counts (today's, and the free days the week card plans ahead, off the main thread); replaced whole,
     * never changed in place, so a race only counts again.
     */
    @kotlin.concurrent.Volatile private var counts: Map<Key, List<Week>> = emptyMap()

    /**
     * This week (Monday to Sunday, from today on) and the seven after it. Counted again when the calendar, the tasks,
     * the watch's sleeps or the profile change, and every half hour (this week's hours run from now).
     */
    fun weeks(all: DayFill.Input): List<Week> {
        val key = Key(all.far ?: all.events, all.tasks, all.sleeps, all.profile, all.facts, all.now.date, (all.now.hour * 60 + all.now.minute) / 30)
        counts[key]?.let { return it }
        val ws = count(all)
        counts = (if (counts.size >= 8) emptyMap() else counts) + (key to ws)
        return ws
    }

    private fun count(all: DayFill.Input): List<Week> {
        val today = all.now.date
        val p = all.profile
        val f = all.facts
        // How far the shift calendar reaches, from all of it; the days themselves from the events near them.
        val lastShift = (all.far ?: all.events).filter { !f.isOthers(it) && RealDay.isShift(it, p, f) }.maxOfOrNull { it.begin.date }
        val inp = near(all)
        val plainInp = plain(inp)
        val shifts = inp.events.filter { !f.isOthers(it) && RealDay.isShift(it, p, f) }
        val noShifts = DayFill.Input(inp.tasks, inp.events - shifts.toSet(), inp.profile, inp.facts, inp.now)
        val first = DayFill.weekOf(today)
        // Shifts are posted a month at a time: a day is known when the calendar has a shift on or after it, or one in
        // its month (a month posted to its end with no shift in its last week is still posted).
        val months = shifts.map { it.begin.date.year * 12 + it.begin.date.monthNumber }.toSet()
        fun posted(d: LocalDate) = lastShift == null || d <= lastShift || (d.year * 12 + d.monthNumber) in months
        val raw = (0 until WEEKS).map { w ->
            val start = first.plusDays(7L * w)
            val end = start.plusDays(7)
            val days = (0L..6L).map { start.plusDays(it) }.filter { it >= today }
            val whole = (0L..6L).map { start.plusDays(it) }
            val byDay = whole.map { if (it >= today) usable(it, inp) else 0 }
            val plainBy = whole.map { if (it >= today) usable(it, plainInp) else 0 }
            // The whole week as it stands from each day's start, today too (rule 4 judges it the same all week).
            fun fromStart(i: Int, d: LocalDate, by: List<Int>, from: DayFill.Input) = if (d > today) by[i] else usable(d, from, fromNow = false)
            Week(
                start,
                byDay.sum(),
                plainBy.sum(),
                shifts.count { !it.allDay && RealDay.isNight(it, p, f) && it.begin.date >= start && it.begin.date < end },
                known = days.all { posted(it) },
                days = days.size,
                byDay = byDay,
                weekUsable = whole.withIndex().sumOf { (i, d) -> fromStart(i, d, byDay, inp) },
                weekPlain = whole.withIndex().sumOf { (i, d) -> fromStart(i, d, plainBy, plainInp) },
            )
        }
        if (lastShift == null || raw.all { it.known }) return raw
        // Rule 1: a week past the posted shifts loses what shifts took in the two months before, on average (only the
        // shifts: a trip or a holiday then was its own, and the unknown week's own events are counted already). Without
        // the look-ahead's own read, the posted weeks ahead stand in for them.
        val past = if (all.far != null) (1..PAST_WEEKS).map { w -> (0L..6L).map { first.minusDays(7L * w).plusDays(it) } }
        else raw.filter { it.known }.map { w -> (0L..6L).map { w.start.plusDays(it) }.filter { it >= today } }
        val lost = past.mapNotNull { dates ->
            val without = dates.sumOf { usable(it, noShifts) }
            if (without > 0) maxOf(0.0, 1.0 - dates.sumOf { usable(it, inp) }.toDouble() / without) else null
        }
        if (lost.isEmpty()) return raw
        val share = lost.sum() / lost.size
        // Only the days not posted lose the share: a posted day's own shifts are counted already.
        return raw.map { w ->
            if (w.known) w else {
                val byDay = w.byDay.mapIndexed { i, u -> if (posted(w.start.plusDays(i.toLong()))) u else (u * (1.0 - share)).toInt() }
                w.copy(usable = byDay.sum(), cut = share, byDay = byDay)
            }
        }
    }

    /** Usable minutes on [day] as rule 1 counts them, for the free time before a deadline (past the eight weeks, as the last). */
    fun usableCounted(day: LocalDate, inp: DayFill.Input, ws: List<Week>): Int {
        val n = (day.toEpochDays() - ws.first().start.toEpochDays()).toInt()
        if (n >= 0) ws.getOrNull(n / 7)?.byDay?.getOrNull(n % 7)?.let { return it }
        return (usable(day, inp) * (1.0 - ws.last().cut)).toInt()
    }

    /** A day off with most of the day free, and no night shift around it (the frame from its start, not from now). */
    fun freeDay(day: LocalDate, all: DayFill.Input): Boolean {
        val inp = if (all.far != null) near(all) else all
        if (away(day, inp)) return false
        val fr = DayFill.frame(day, inp)
        return fr.mode == DayFill.Mode.NORMAL && !fr.job && Sprint.usableIn(fr) >= FREE_DAY
    }

    /** The lightest of this week and the next three, with at least three quarters of its usual time. */
    fun lightest(ws: List<Week>, i: Int = 0): Boolean {
        val w = ws.getOrNull(i) ?: return false
        // A known week with most of it still ahead (on a Saturday, "the lightest week" would be two days).
        if (!w.light || w.days < 4) return false
        // Against the weeks the shift calendar reaches: an estimate cannot make a known week second best.
        return ws.drop(i).take(4).filter { it.known }.all { it.ratio <= w.ratio }
    }

    private fun nightsText(w: Week) = if (w.nights > 0) tr("เวรดึก ${w.nights} คืน", "${w.nights} night shifts") else ""

    /** Why a week is tight, as mock v2 says it: "เวรดึก 3 คืน ปกติว่างราว 28 ชม.". */
    fun tightWhy(w: Week): String =
        listOf(nightsText(w), tr("ปกติว่างราว ${w.plainHours} ชม.", "usually about ${w.plainHours} h free")).filter { it.isNotEmpty() }.joinToString(" ")

    /** The tag a week gets: tight or the lightest; none for a usual week. */
    fun weekHint(ws: List<Week>, i: Int): DayFill.Hint? {
        val w = ws.getOrNull(i) ?: return null
        return when {
            w.heavy -> DayFill.Hint(tr("แน่น ${w.hours} ชม.", "Tight, ${w.hours} h"), tightWhy(w), DayFill.Tone.HEAVY)
            lightest(ws, i) -> {
                // As mock v2: "ไม่มีเวรดึกทั้งสัปดาห์ สัปดาห์ถัดไปเวรดึก 3 คืน".
                val next = ws.getOrNull(i + 1)?.takeIf { it.heavy }
                DayFill.Hint(
                    tr("เบาสุด ${w.hours} ชม.", "Lightest, ${w.hours} h"),
                    (if (w.nights == 0) tr("ไม่มีเวรดึกทั้งสัปดาห์", "No night shift this week") else nightsText(w)) +
                        (next?.let { n -> if (n.nights > 0) tr(" สัปดาห์ถัดไปเวรดึก ${n.nights} คืน", ", ${n.nights} next week") else tr(" สัปดาห์ถัดไปแน่น", ", next week is tight") } ?: ""),
                    DayFill.Tone.LIGHT,
                )
            }
            else -> null
        }
    }

    /** Rule 2: the next calendar week is tight. */
    fun beforeTight(ws: List<Week>): Week? = ws.getOrNull(1)?.takeIf { it.heavy }

    /**
     * Days in the last [OWED_SPAN] days with own time: a myspace task ticked, or a progress line written on one (the
     * plan's taken slots are kept for today only).
     */
    fun ownDays(inp: DayFill.Input): Int {
        val today = inp.now.date
        val from = today.minusDays(OWED_SPAN)
        val days = HashSet<LocalDate>()
        fun add(d: LocalDate?) { if (d != null && d >= from && d < today) days += d }
        inp.tasks.filter { DayFill.isMine(it) }.forEach { t ->
            add(t.done)
            // A note of progress; not one that says why it got stuck ("ไม่มีแรง", "ไม่มีเวลา").
            StuckLog.entries(t).filter { e -> StuckLog.Reason.entries.none { it.opens(e.reason) } }.forEach { add(it.day) }
        }
        return days.size
    }

    /**
     * Rule 4: little own time lately, and this week is a light one (judged whole, so to its last evening): the reason
     * the "คืนเวลา" tag gives, or null. Once this phone has kept own time for two weeks ([DayFill.Input.own]), as the
     * rule says: less than half of what the plan offered. Until then, the days with own time ([OWED_DAYS]).
     */
    fun owed(inp: DayFill.Input, ws: List<Week>): String? {
        if (ws.firstOrNull()?.lightWeek != true) return null
        val today = inp.now.date
        val from = today.minusDays(OWED_SPAN)
        val own = inp.own
        if (own != null && own.keys.any { it <= from }) {
            val days = own.filterKeys { it >= from && it < today }.values
            val offered = days.sumOf { it.offered }
            val done = days.sumOf { it.done }
            if (offered == 0 || done * 2 >= offered) return null
            val h = (done + 30) / 60
            return tr("2 สัปดาห์ล่าสุด ได้ทำเรื่องที่อยากทำแค่ราว $h ชม.", "Only about $h h of what you want to do in the last two weeks")
        }
        val n = ownDays(inp).takeIf { it < OWED_DAYS } ?: return null
        return tr("2 สัปดาห์ล่าสุดได้ทำเรื่องที่อยากทำแค่ $n วัน", "Only $n days of what you want to do in the last two weeks")
    }

    /** One day's own time (rule 4): minutes the plan offered, and minutes the owner did. */
    data class OwnDay(val offered: Int, val done: Int)

    /** Days of own time kept on the phone: more than the two weeks rule 4 reads, so the record's start is known. */
    const val OWN_KEEP = 30L

    /** [days] as the phone keeps them, the last [OWN_KEEP] days: "2026-10-30=60,45;2026-10-31=0,0". */
    fun ownText(days: Map<LocalDate, OwnDay>, today: LocalDate): String =
        days.filterKeys { it > today.minusDays(OWN_KEEP) }.toList().sortedBy { it.first }.joinToString(";") { (d, o) -> "$d=${o.offered},${o.done}" }

    /** The days read back from [ownText], and how many parts did not read (left out, for the caller to log). */
    fun ownParse(text: String?): Pair<Map<LocalDate, OwnDay>, Int> {
        if (text.isNullOrBlank()) return emptyMap<LocalDate, OwnDay>() to 0
        var bad = 0
        val days = text.split(';').mapNotNull { part ->
            val m = Regex("""^(\d{4}-\d{2}-\d{2})=(\d+),(\d+)$""").matchEntire(part.trim())
            val day = m?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }
            val offered = m?.groupValues?.get(2)?.toIntOrNull()
            val done = m?.groupValues?.get(3)?.toIntOrNull()
            if (day == null || offered == null || done == null) { bad++; null } else day to OwnDay(offered, done)
        }.toMap()
        return days to bad
    }

    /**
     * Today's own time as [plan] shows it now, kept with what was seen earlier today ([before]): the most the plan
     * offered, and that much done once the owner took the slot, ticked a want, or wrote progress on one today (not a
     * note of why it got stuck).
     */
    fun ownToday(plan: DayFill.Plan, inp: DayFill.Input, before: OwnDay?): OwnDay {
        val today = inp.now.date
        val offered = maxOf(before?.offered ?: 0, plan.placed.filter { it.item.kind == DayFill.Kind.MINE }.sumOf { it.len })
        val taken = inp.memory.takeIf { it.day == today.toString() }?.slots?.get("mine") != null
        val did = taken || inp.tasks.any { t ->
            DayFill.isMine(t) && (t.done == today || StuckLog.entries(t).any { e -> e.day == today && StuckLog.Reason.entries.none { it.opens(e.reason) } })
        }
        return OwnDay(offered, if (did) maxOf(before?.done ?: 0, offered) else before?.done ?: 0)
    }

    /**
     * Rule 6: what a whole free day ahead would hold, as today's plan would place it then (the owner's morning and the
     * work before a tight week), for the week card. Nothing is written: it is planned again on the day.
     */
    fun booked(inp: DayFill.Input, day: LocalDate): List<DayFill.Placed> {
        if (day <= inp.now.date || !freeDay(day, inp)) return emptyList()
        val then = DayFill.Input(inp.tasks, inp.events, inp.profile, inp.facts, day.atTime(0, 0), inp.memory, inp.moves, ahead = true, far = inp.far, own = inp.own)
        return DayFill.plan(then).placed.filter { p -> p.item.hints.any { it.tone == DayFill.Tone.WANT || it.tone == DayFill.Tone.HEAVY } }
    }
}
