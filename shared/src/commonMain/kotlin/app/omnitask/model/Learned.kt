package app.omnitask.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt
import app.omnitask.time.*

/**
 * What the behavior log ([Behavior]) says about the owner's habits, read by rules (no AI, works offline): which tasks
 * keep getting moved, which are likely to slip, what a new task usually comes with, and a short summary for Claude.
 * Nothing here changes a task or the profile by itself; it is shown, and the owner decides.
 */
object Learned {

    /** How far back the habits are read. */
    const val DAYS = 90

    /** A task moved to a later day this many times counts as one that keeps slipping. */
    const val SLIP_MOVES = 3

    private fun recent(events: List<BehaviorEvent>, today: LocalDate, days: Int = DAYS) =
        events.filter { e -> e.time?.date?.let { it > today.minusDays(days.toLong()) && it <= today } == true }

    private fun later(e: BehaviorEvent): Boolean {
        val from = e.from?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val to = e.to?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return e.ev == Behavior.MOVE && to > from
    }

    // ---- Tasks that keep slipping ----

    data class Slip(val task: Task, val moves: Int)

    /** Open tasks moved to a later day at least [SLIP_MOVES] times, the most moved first. */
    fun slipping(tasks: List<Task>, events: List<BehaviorEvent>, today: LocalDate): List<Slip> {
        val moves = recent(events, today).filter { later(it) }.groupingBy { it.title.orEmpty() }.eachCount()
        return tasks.filter { it.isOpen && !it.isSubtask }
            .mapNotNull { t -> moves[t.title]?.takeIf { it >= SLIP_MOVES }?.let { Slip(t, it) } }
            .distinctBy { it.task.title }
            .sortedByDescending { it.moves }
    }

    /** What a task is grouped by to judge how its kind of work tends to go: its first tag, else its kind. */
    private fun keyOf(title: String, tags: List<String>): String =
        tags.firstOrNull { !it.startsWith("remind-at-") }?.let { "#$it" } ?: Planner.kindOf(title).name

    data class Risk(val task: Task, val share: Double, val key: String)

    /**
     * Open tasks due or set for today or tomorrow whose kind of work (first tag, else kind) has been moved before
     * at least half the time, over at least five such tasks. Tasks already [slipping] are left out.
     */
    fun atRisk(tasks: List<Task>, events: List<BehaviorEvent>, today: LocalDate): List<Risk> {
        val log = recent(events, today).filter { it.title != null }
        val titlesByKey = log.groupBy { keyOf(it.title!!, it.tags) }.mapValues { (_, es) -> es.mapTo(HashSet()) { it.title!! } }
        val movedTitles = log.filter { later(it) }.mapTo(HashSet()) { it.title!! }
        val slips = slipping(tasks, events, today).mapTo(HashSet()) { it.task.title }
        val soon = today.plusDays(1)
        return tasks.filter { t ->
            t.isOpen && !t.isSubtask && t.title !in slips &&
                listOfNotNull(t.due, t.scheduled).any { it in today..soon }
        }.mapNotNull { t ->
            val key = keyOf(t.title, t.tags)
            val titles = titlesByKey[key].orEmpty()
            if (titles.size < 5) return@mapNotNull null
            val share = titles.count { it in movedTitles }.toDouble() / titles.size
            if (share < 0.5) null else Risk(t, share, key)
        }.sortedByDescending { it.share }
    }

    // ---- Reminders put off ----

    /** Kinds of work whose reminders were put off by about an hour at least four times in four weeks, with the count. */
    fun snoozedKinds(events: List<BehaviorEvent>, today: LocalDate): Map<Planner.Kind, Int> =
        recent(events, today, 28).filter { it.ev == Behavior.SNOOZE && (it.min ?: 0) in 30..180 }
            .mapNotNull { e -> Planner.Kind.entries.firstOrNull { it.name == e.kind } }
            .groupingBy { it }.eachCount().filterValues { it >= 4 }

    // ---- What a new task usually comes with ----

    /** One thing to add to a quick-add sentence: what the chip says, and the words it adds. */
    data class Hint(val label: String, val words: String)

    /** Letter pairs of a title, for telling how alike two titles are; Thai has no spaces between words. */
    private fun pairs(s: String): Set<String> {
        val t = s.lowercase().filter { !it.isWhitespace() }
        return if (t.length < 2) setOf(t) else (0 until t.length - 1).mapTo(HashSet()) { t.substring(it, it + 2) }
    }

    private fun alike(a: Set<String>, b: Set<String>): Boolean = a.isNotEmpty() && b.isNotEmpty() &&
        2.0 * a.count { it in b } / (a.size + b.size) >= 0.6

    /** The value most of [values] share (at least half, and at least twice), or null. */
    private fun <T> usual(values: List<T?>): T? {
        val found = values.filterNotNull()
        val (top, n) = found.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return if (n >= 2 && n * 2 >= values.size) top else null
    }

    private fun hm(t: LocalTime) = Profile.hm(t)

    /**
     * What tasks like [draft] usually got when they were added before (tags, a reminder time, a due day so many days
     * ahead), for what the draft does not have yet. A reminder time is set later by the profile's [Profile.later]
     * for its kind. Needs two or more alike tasks in the log.
     */
    fun hints(draft: QuickAdd.Draft, events: List<BehaviorEvent>, profile: Profile, today: LocalDate): List<Hint> {
        if (draft.title.isBlank()) return emptyList()
        val me = pairs(draft.title)
        val past = recent(events, today).filter { it.ev == Behavior.CREATE && it.title != null && alike(me, pairs(it.title)) }
        if (past.size < 2) return emptyList()
        val out = ArrayList<Hint>()
        val have = draft.tags.toSet()
        past.flatMap { it.tags.distinct() }.filter { !it.startsWith("remind-at-") && it !in have }
            .groupingBy { it }.eachCount().filter { it.value >= 2 && it.value * 5 >= past.size * 3 }
            .entries.sortedByDescending { it.value }.take(3)
            .forEach { out += Hint("#${it.key}", "#${it.key}") }
        if (draft.due == null) {
            val ahead = usual(past.map { e ->
                val due = e.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@map null
                val made = e.time?.date ?: return@map null
                (due.toEpochDays() - made.toEpochDays()).takeIf { it in 0..60 }
            })
            if (ahead != null) {
                val day = today.plusDays(ahead.toLong())
                val words = when (ahead) {
                    0 -> "วันนี้"
                    1 -> "พรุ่งนี้"
                    else -> "${day.dayOfMonth}/${day.monthNumber}"
                }
                val label = when (ahead) {
                    0 -> tr("วันนี้", "Today")
                    1 -> tr("พรุ่งนี้", "Tomorrow")
                    else -> tr("ครบใน $ahead วัน", "Due in $ahead days")
                }
                out += Hint(label, words)
            }
        }
        if (draft.time == null) {
            val time = usual(past.map { e -> e.remind?.let { runCatching { LocalTime.parse(it) }.getOrNull() } })
            if (time != null) {
                val shift = profile.later[Planner.kindOf(draft.title)] ?: 0
                val minutes = (time.hour * 60 + time.minute + shift).coerceAtMost(23 * 60 + 59)
                val at = LocalTime.of(minutes / 60, minutes % 60)
                out += Hint(tr("เตือน ", "Remind ") + hm(at), hm(at))
            }
        }
        return out
    }

    // ---- For Claude ----

    /** A few lines on how the owner has worked over the last four weeks, for the picture handed to Claude (in Thai). */
    fun summary(tasks: List<Task>, events: List<BehaviorEvent>, today: LocalDate): List<String> {
        val month = recent(events, today, 28)
        if (month.isEmpty()) return emptyList()
        return buildList {
            val made = month.count { it.ev == Behavior.CREATE }
            val done = month.filter { it.ev == Behavior.DONE }
            add("เพิ่มงาน $made ทำเสร็จ ${done.size} ใน 4 สัปดาห์")
            val hours = done.mapNotNull { it.time?.hour }
            if (hours.size >= 5) {
                val (h, n) = (5..20).map { h -> h to hours.count { it in h until h + 3 } }.maxBy { it.second }
                add("ปิดงานบ่อยช่วง ${hhmm(h, 0)} ถึง ${hhmm(h + 3, 0)} (${(100.0 * n / hours.size).roundToInt()}%)")
            }
            val days = done.mapNotNull { it.time?.date?.dayOfWeek }
            if (days.size >= 7) {
                val day = days.groupingBy { it }.eachCount().maxBy { it.value }.key
                add("วันที่ปิดงานได้มากที่สุด: ${Profile.DAY_NAMES[day.isoDayNumber - 1]}")
            }
            val moved = month.count { later(it) }
            if (moved > 0) add("เลื่อนงานไปวันหลัง $moved ครั้ง")
            slipping(tasks, events, today).take(5).forEach { add("เลื่อนบ่อย: ${it.task.title} (${it.moves} ครั้ง)") }
            snoozedKinds(events, today).forEach { (kind, n) -> add("มักเลื่อนเตือนงาน${Profile.KIND_NAMES.getValue(kind)} ออกไป 1 ชม. ($n ครั้ง)") }
            val ignored = month.count { it.ev == Behavior.DISMISS }
            if (ignored > 0) add("ปัดแจ้งเตือนทิ้ง $ignored ครั้ง")
        }
    }
}
