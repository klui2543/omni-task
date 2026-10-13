package app.omnitask.quest

import app.omnitask.model.Behavior
import app.omnitask.model.BehaviorEvent
import app.omnitask.model.Focus
import app.omnitask.model.Planner
import app.omnitask.model.Priority
import app.omnitask.model.Status
import app.omnitask.model.Task
import kotlinx.datetime.LocalDate
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What the vault has earned the character: XP, level, stats and gold, all worked out again from the finished
 * tasks every time, so nothing earned can be lost. The rules are in `docs/rpg-design.md`, section 4.
 */
object QuestXp {

    /** The first [FULL_PER_DAY] XP of a day count in full, up to [HALF_PER_DAY] at half, then [TAIL] of it. */
    const val FULL_PER_DAY = 200.0
    const val HALF_PER_DAY = 400.0
    const val TAIL = 0.2

    /** Work done before the game began ("the old legend") counts at this share, and never past [LEGEND_CAP_LEVEL]. */
    const val LEGEND_SHARE = 0.5
    const val LEGEND_CAP_LEVEL = 20

    /** A task moved this many times or more is a bounty: finishing it pays [BOUNTY] times. */
    const val BOUNTY_MOVES = 3
    const val BOUNTY = 1.5

    fun base(p: Priority): Double = when (p) {
        Priority.HIGHEST -> 30.0
        Priority.HIGH -> 20.0
        Priority.MEDIUM -> 14.0
        Priority.NONE -> 10.0
        Priority.LOW -> 6.0
        Priority.LOWEST -> 4.0
    }

    /** XP needed in all to stand at [level]: `6 x (level - 1)^2.25`. */
    fun totalFor(level: Int): Long = (6.0 * (level - 1).coerceAtLeast(0).toDouble().pow(2.25)).roundToInt().toLong()

    fun levelOf(xp: Long): Int {
        var l = 1
        while (l < QuestWorld.MAX_LEVEL && totalFor(l + 1) <= xp) l++
        return l
    }

    private val HABIT_WORDS = listOf("routine", "กิจวัตร", "mind space", "นิสัย", "ทุกวัน")
    private val BODY_WORDS = listOf("สุขภาพ", "ตรวจสุขภาพ", "หมอฟัน", "นอนเร็ว", "ยืดเส้น", "health", "doctor", "dentist", "sleep early")
    private val LEARN_WORDS = listOf(
        "หนังสือ", "อ่าน", "เรียน", "how to", "หุ้น", "ลงทุน", "การเงิน", "รายรับ", "รายจ่าย", "ออม", "คอร์ส", "สอบ",
        "book", "learn", "course", "invest", "stock", "budget", "saving", "finance",
    )
    private val LEARN_TAGS = listOf(Focus.FUTURE_TAG, "readinglist", "อ่าน", "เรียน", "การเงิน", "ลงทุน")
    private val PAPER_WORDS = listOf(
        "ใบลา", "ยกเวร", "แลกเวร", "เอกสาร", "ลงชื่อ", "ยื่น", "สมัคร", "ต่ออายุ", "จอง", "ดึงรูป",
        "form", "submit", "document", "renew", "sign",
    )
    private val SYSTEM_WORDS = listOf(
        "ระบบ", "ux", "ui", "โค้ด", "script", "automation", "แอป", "app", "บัค", "bug", "flow", "mock", "ออกแบบ", "excel",
        "sap", "plugin", "ปรับปรุง", "อัพโหลด", "อัปโหลด", "deploy", "database", "api",
    )

    /**
     * Which tribe's part of life a task belongs to, or null for general work (XP only, no stat). Checked in this
     * order so the most telling sign wins: habits, learning and money, body, paperwork and errands, systems and
     * deep work. The words come from the owner's own notes.
     */
    fun realmOf(task: Task): Tribe? {
        val t = task.title.lowercase()
        val tags = task.tags.map { it.lowercase() }
        fun tagged(names: List<String>) = tags.any { tag -> names.any { tag == it || tag.startsWith("$it/") } }
        return when {
            task.recurrence != null || HABIT_WORDS.any { it in t } -> Tribe.SYLVA
            tagged(LEARN_TAGS) || LEARN_WORDS.any { it in t } -> Tribe.AQUA
            BODY_WORDS.any { it in t } -> Tribe.IGNIS
            else -> when (Planner.kindOf(task.title)) {
                Planner.Kind.MOVE -> Tribe.IGNIS
                Planner.Kind.ERRAND, Planner.Kind.QUICK -> Tribe.ZEPHYR
                Planner.Kind.DEEP -> Tribe.LUNA
                Planner.Kind.GENERAL -> when {
                    PAPER_WORDS.any { it in t } -> Tribe.ZEPHYR
                    SYSTEM_WORDS.any { it in t } -> Tribe.LUNA
                    else -> null
                }
            }
        }
    }

    /** How many times each task (by title) had a date moved later, from the behavior log. */
    fun moves(events: List<BehaviorEvent>): Map<String, Int> =
        events.filter { e -> e.ev == Behavior.MOVE && e.title != null && (e.from == null || e.to == null || e.to > e.from) }
            .groupingBy { it.title!! }.eachCount()

    /** One finished task as the game saw it. */
    data class Earned(
        val title: String,
        val day: LocalDate,
        val realm: Tribe?,
        /** XP after every rule, the legend share and the daily cap included. */
        val xp: Double,
        val onTime: Boolean,
        val bounty: Boolean,
        val legend: Boolean,
    )

    data class Result(
        val xp: Long,
        val level: Int,
        /** XP into this level, and what the next one takes from the start of this one. */
        val into: Long,
        val span: Long,
        /** Stats before equipment and skills. */
        val stats: Map<Stat, Int>,
        /** Gold the tasks earned (XP turned into coins); what was spent is in [QuestState]. */
        val gold: Long,
        val earned: List<Earned>,
        val today: Double,
        /** Finished tasks per part of life, for the realm chart and the tribe advice. */
        val byRealm: Map<Tribe?, Int>,
    ) {
        val maxed get() = level >= QuestWorld.MAX_LEVEL
    }

    /**
     * The character [tasks] made: every finished task (live note, archive, conflict copies left out by the caller),
     * once each; the same title finished twice on one day counts once, so a task in both the note and the archive,
     * or one ticked over and over, adds nothing. [start] is when the game began; work done before it is the legend. [tribe] grows its own stat 20%
     * faster. [moved] is [moves] of the behavior log. List items (Bucket list...) earn titles, not XP, so they are
     * left out here.
     */
    fun compute(tasks: List<Task>, moved: Map<String, Int>, start: LocalDate?, tribe: Tribe?, today: LocalDate): Result {
        val done = tasks.filter { it.status == Status.DONE && it.done != null && it.list == null && !it.todayCopy }
            .distinctBy { Triple(it.title, it.done, it.recurrence) }
            .sortedWith(compareBy<Task>({ it.done }, { it.lineIndex }))
        val earned = ArrayList<Earned>()
        var legendXp = 0.0
        val legendCap = totalFor(LEGEND_CAP_LEVEL).toDouble()
        for ((day, ofDay) in done.groupBy { it.done!! }) {
            var dayXp = 0.0
            for (t in ofDay) {
                var xp = base(t.priority)
                val onTime = t.due != null && day <= t.due
                if (onTime) xp *= 1.2
                if (t.parent != null) xp *= 0.5
                val bounty = (moved[t.title] ?: 0) >= BOUNTY_MOVES
                if (bounty) xp *= BOUNTY
                // The daily cap: what passes the full and half bands is cut.
                val capped = band(dayXp, xp)
                dayXp += xp
                val legend = start != null && day < start
                val counted = if (legend) {
                    val share = (capped * LEGEND_SHARE).coerceAtMost((legendCap - legendXp).coerceAtLeast(0.0))
                    legendXp += share
                    share
                } else capped
                earned += Earned(t.title, day, realmOf(t), counted, onTime, bounty, legend)
            }
        }
        val xp = earned.sumOf { it.xp }.toLong()
        val level = levelOf(xp)
        val points = HashMap<Stat, Double>()
        for (e in earned) {
            e.realm?.let { r -> points[r.stat] = (points[r.stat] ?: 0.0) + e.xp * (if (r == tribe) 1.2 else 1.0) }
            if (e.onTime) points[Stat.DEX] = (points[Stat.DEX] ?: 0.0) + e.xp * 0.5
        }
        val stats = Stat.entries.associateWith { s -> statOf(points[s] ?: 0.0) }
        val next = if (level >= QuestWorld.MAX_LEVEL) totalFor(level) else totalFor(level + 1)
        return Result(
            xp = xp, level = level, into = xp - totalFor(level), span = (next - totalFor(level)).coerceAtLeast(1),
            stats = stats, gold = earned.sumOf { it.xp }.roundToInt().toLong(), earned = earned,
            today = earned.filter { it.day == today }.sumOf { it.xp },
            byRealm = earned.groupingBy { it.realm }.eachCount(),
        )
    }

    /** [xp] added on top of [before] XP already earned today, after the daily bands. */
    fun band(before: Double, xp: Double): Double {
        var left = xp
        var at = before
        var out = 0.0
        fun take(limit: Double, rate: Double) {
            if (left <= 0 || at >= limit) return
            val part = min(left, limit - at)
            out += part * rate
            at += part
            left -= part
        }
        take(FULL_PER_DAY, 1.0)
        take(HALF_PER_DAY, 0.5)
        if (left > 0) out += left * TAIL
        return out
    }

    /** A stat from the XP its kind of work earned: `1 + floor(sqrt(points / 3))`, at most 99. */
    fun statOf(points: Double): Int = (1 + floor(sqrt(points / 3.0)).toInt()).coerceAtMost(99)
}
