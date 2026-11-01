package app.omnitask.model

import kotlinx.datetime.LocalDate

/**
 * The today page's plan as the lock-screen card reads it (owner's pick ก, 2026-11-01, mock lock-now-next): what is on
 * now and what comes next with its start, from the same plan the today page shows. The app keeps a copy each time the
 * plan changes; the card reads that copy, so it agrees with the page even while the app is closed. Free time the plan
 * keeps clear, ticked work and other people's events are left out.
 */
object LockPlan {
    /** One block of the day: minutes from midnight, [end] null for a moment (wake, bed); [at] null for all day. */
    data class Slot(val title: String, val at: Int?, val end: Int?, val card: Boolean, val ident: String? = null, val calendar: Long? = null)

    data class Copy(val day: LocalDate, val slots: List<Slot>)

    fun of(plan: DayFill.Plan): Copy = Copy(
        plan.day,
        plan.rows.mapNotNull { r ->
            when (r) {
                is DayFill.Row.Locked -> if (r.from != null) null else {
                    val end = r.until ?: r.event?.takeIf { !it.allDay }?.let { DayFill.minutesOf(it.end, plan.day) }
                    Slot(r.title.replace(UNTIL, ""), r.at, end?.takeIf { r.at != null && it > r.at }, card = false, calendar = r.event?.calendarId)
                }
                // Work is a card; a life block the plan put (dinner, rest) reads like the other blocks.
                is DayFill.Row.Card -> r.placed.item.task.let { t ->
                    Slot(r.placed.item.title, r.placed.at, r.placed.at + r.placed.len, card = t != null, ident = t?.let { DayFill.ident(it) })
                }
                else -> null
            }
        },
    )

    /** The end the plan writes after a block's name ("กินข้าว พัก ถึง 19:20"); a name with ถึง inside it stays whole. */
    private val UNTIL = Regex(" (ถึง|until) \\d{1,2}:\\d{2}$")

    /** The next minute after [now] at which a block starts or ends, so the card is redrawn then; null when none is left. */
    fun nextChange(copy: Copy, now: Int): Int? {
        val running = copy.slots.any { s -> s.at != null && s.end != null && s.at <= now && now < s.end }
        val edge = copy.slots.flatMap { listOfNotNull(it.at, it.end) }.filter { it > now }.minOrNull()
        return if (running) listOfNotNull(edge, now + 10).minOrNull() else edge
    }

    /**
     * Work in the copy that is over: no open line of it is still for today or before (ticked, or a repeating one ticked
     * and moved to its next day). A line with the same name already ticked does not hide one still open.
     */
    fun done(copy: Copy, tasks: List<Task>, today: LocalDate): Set<String> {
        val still = tasks.filter { t -> t.isOpen && (t.scheduled ?: t.due).let { it == null || it <= today } }.map { DayFill.ident(it) }.toSet()
        return copy.slots.mapNotNull { it.ident }.filter { it !in still }.toSet()
    }

    /**
     * [copy] with the owner's calendar [events] it does not hold yet (added since the page last planned), so an event
     * put in the calendar while the app is closed still shows. Matched by name and start.
     */
    fun withEvents(copy: Copy, events: List<Slot>): Copy {
        val have = copy.slots.map { it.title to it.at }.toSet()
        val add = events.filter { (it.title to it.at) !in have }
        return if (add.isEmpty()) copy else copy.copy(slots = copy.slots + add)
    }

    /** What is on at [now] (minutes) and what starts next; work in [done] (by ident) is over. */
    fun nowNext(copy: Copy, now: Int, done: Set<String>): Pair<Slot?, Slot?> {
        val live = copy.slots.filter { s -> s.at != null && (s.ident == null || s.ident !in done) }
        val on = live.filter { s -> s.end != null && s.at!! <= now && now < s.end }.maxByOrNull { it.at!! }
        val next = live.filter { it.at!! > now }.minByOrNull { it.at!! }
        return on to next
    }

    /** The folded card's first two lines: "ตอนนี้: กินข้าว พัก ถึง 19:20" and "ต่อไป 20:15 เขียนถ้อยคำ"; null with nothing left. */
    fun lines(copy: Copy, now: Int, done: Set<String>): Pair<String, String?>? {
        val (on, next) = nowNext(copy, now, done)
        val nextLine = next?.let { tr("ต่อไป ", "Next ") + DayFill.hm(it.at!!) + " " + it.title }
        return when {
            on != null -> (tr("ตอนนี้: ", "Now: ") + on.title + tr(" ถึง ", " until ") + DayFill.hm(on.end!!)) to nextLine
            nextLine != null -> nextLine to null
            else -> null
        }
    }

    // Kept as text: one slot a line, fields split by a tab (titles never hold one).
    fun encode(copy: Copy): String = buildString {
        append(copy.day.toString())
        copy.slots.forEach { s ->
            append('\n').append(if (s.card) "c" else "b").append('\t').append(s.at ?: -1).append('\t').append(s.end ?: -1)
                .append('\t').append(s.ident?.replace('\t', ' ')?.replace('\n', ' ') ?: "").append('\t').append(s.title.replace('\t', ' ').replace('\n', ' '))
                .append('\t').append(s.calendar ?: "")
        }
    }

    fun decode(text: String?): Copy? {
        val lines = text?.split('\n') ?: return null
        val day = runCatching { LocalDate.parse(lines.first()) }.getOrNull() ?: return null
        val slots = lines.drop(1).mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 5) return@mapNotNull null
            Slot(f[4], f[1].toIntOrNull()?.takeIf { it >= 0 }, f[2].toIntOrNull()?.takeIf { it >= 0 }, card = f[0] == "c", ident = f[3].ifEmpty { null }, calendar = f.getOrNull(5)?.toLongOrNull())
        }
        return Copy(day, slots)
    }
}
