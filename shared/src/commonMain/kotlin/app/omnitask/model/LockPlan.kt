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
    data class Slot(val title: String, val at: Int?, val end: Int?, val card: Boolean, val ident: String? = null)

    data class Copy(val day: LocalDate, val slots: List<Slot>)

    fun of(plan: DayFill.Plan): Copy = Copy(
        plan.day,
        plan.rows.mapNotNull { r ->
            when (r) {
                is DayFill.Row.Locked -> if (r.from != null) null else {
                    val end = r.until ?: r.event?.takeIf { !it.allDay }?.let { DayFill.minutesOf(it.end, plan.day) }
                    Slot(r.title.substringBefore(tr(" ถึง ", " until ")), r.at, end?.takeIf { r.at != null && it > r.at }, card = false)
                }
                is DayFill.Row.Card -> Slot(r.placed.item.title, r.placed.at, r.placed.at + r.placed.len, card = true, ident = r.placed.item.task?.let { DayFill.ident(it) })
                else -> null
            }
        },
    )

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
        }
    }

    fun decode(text: String?): Copy? {
        val lines = text?.split('\n') ?: return null
        val day = runCatching { LocalDate.parse(lines.first()) }.getOrNull() ?: return null
        val slots = lines.drop(1).mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 5) return@mapNotNull null
            Slot(f[4], f[1].toIntOrNull()?.takeIf { it >= 0 }, f[2].toIntOrNull()?.takeIf { it >= 0 }, card = f[0] == "c", ident = f[3].ifEmpty { null })
        }
        return Copy(day, slots)
    }
}
