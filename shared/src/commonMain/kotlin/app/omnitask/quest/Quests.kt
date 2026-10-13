package app.omnitask.quest

import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.TaskKind
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * The Master Guild's board: quests made from the owner's real day, today's tasks, the work that keeps slipping,
 * the calendar and the timed sessions. A quest is done when the real thing is done; its gold is then claimed.
 */
object Quests {

    enum class Kind { DAILY, BOSS, CALENDAR, WEEKLY }

    data class Quest(
        val id: String,
        val kind: Kind,
        val title: String,
        val detail: String,
        val progress: Int,
        val goal: Int,
        val gold: Int,
        val claimed: Boolean,
    ) {
        val done get() = progress >= goal
        val claimable get() = done && !claimed
    }

    const val FOCUS_MINUTES = 50
    const val WEEK_TASKS = 15
    const val BRANCH_TASKS = 5

    /**
     * The board for [now]. [tasks] are the live note's tasks and the archive's finished ones; [moves] counts how
     * often each task (by title) was put off; [shiftWords] are the profile's words for a shift ("เวร").
     */
    fun board(
        tasks: List<Task>,
        events: List<CalendarEvent>,
        state: QuestState,
        moves: Map<String, Int>,
        shiftWords: List<String>,
        now: LocalDateTime,
    ): List<Quest> {
        val today = now.date
        val top = tasks.filter { it.parent == null && it.list == null && !it.todayCopy && it.status != Status.CANCELLED }
        val out = ArrayList<Quest>()
        fun add(id: String, kind: Kind, title: String, detail: String, progress: Int, goal: Int, gold: Int) {
            out += Quest(id, kind, title, detail, progress.coerceAtMost(goal), goal, gold, id in state.claimed)
        }

        // Today's main quest: everything due or planned for today.
        val todays = top.filter { (it.due == today || it.scheduled == today) && (it.isOpen || it.done == today) }
        if (todays.isNotEmpty()) {
            val left = todays.filter { it.isOpen }.joinToString(", ") { it.title }.take(120)
            add(
                "d:$today:due", Kind.DAILY, tr("ภารกิจหลักวันนี้", "Today's main quest"),
                if (left.isEmpty()) tr("เคลียร์งานวันนี้ครบแล้ว", "Every task for today is done") else tr("เหลือ: $left", "Left: $left"),
                todays.count { it.status == Status.DONE }, todays.size, (20 + 10 * todays.size).coerceAtMost(80),
            )
        }

        // The bounty: the task that slipped most, or the oldest overdue one. Finished today, it counts.
        fun bountyOf(t: Task) = (moves[t.title] ?: 0) >= QuestXp.BOUNTY_MOVES || (t.due != null && t.due < (t.done ?: today))
        val slain = top.firstOrNull { it.done == today && bountyOf(it) && TaskKind.of(it) != TaskKind.SOMEDAY }
        val boss = slain ?: top.filter { it.isOpen && bountyOf(it) && TaskKind.of(it) != TaskKind.SOMEDAY }
            .sortedWith(compareByDescending<Task> { moves[it.title] ?: 0 }.thenBy { it.due ?: LocalDate.LATEST }).firstOrNull()
        if (boss != null) {
            val n = moves[boss.title] ?: 0
            val why = when {
                n >= QuestXp.BOUNTY_MOVES -> tr("ถูกเลื่อนมาแล้ว $n ครั้ง", "put off $n times")
                else -> tr("เลยกำหนดมาตั้งแต่ ${boss.due}", "overdue since ${boss.due}")
            }
            add("d:$today:boss", Kind.BOSS, tr("ล่าค่าหัว: ${boss.title}", "Bounty: ${boss.title}"), why, if (slain != null) 1 else 0, 1, 60)
        }

        // Focus: time spent on tasks with the timer.
        val minutes = state.secondsOn(today) / 60
        add(
            "d:$today:focus", Kind.DAILY, tr("ฝึกสมาธิ $FOCUS_MINUTES นาที", "Focus for $FOCUS_MINUTES minutes"),
            tr("จับเวลาทำงานไปแล้ว $minutes นาที", "$minutes minutes timed so far"), minutes, FOCUS_MINUTES, 40,
        )

        // The calendar: each of today's events is a quest outside the guild, done once it is over.
        val shifts = shiftWords.map { it.lowercase() }.filter { it.isNotBlank() }
        events.filter { it.begin.date <= today && it.end.date >= today && it.end > today.atStartOfDay() }
            .filter { e -> !e.allDay || shifts.any { it in e.title.lowercase() } }
            .sortedBy { it.begin }.take(4)
            .forEach { e ->
                val shift = shifts.any { it in e.title.lowercase() }
                val time = if (e.allDay) tr("ทั้งวัน", "all day") else "${hm(e.begin)} ${tr("ถึง", "to")} ${hm(e.end)}"
                add(
                    "c:$today:${e.id}", Kind.CALENDAR,
                    if (shift) tr("เข้าเวร: ${e.title}", "Shift: ${e.title}") else tr("ภารกิจนอกกิลด์: ${e.title}", "Field quest: ${e.title}"),
                    time, if (now >= e.end) 1 else 0, 1, if (shift) 50 else 20,
                )
            }

        // The week, Monday to Sunday.
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = tasks.filter { it.status == Status.DONE && it.list == null && !it.todayCopy && it.done != null && it.done >= monday && it.done <= today }
            .distinctBy { it.title to it.done }
        add(
            "w:$monday:count", Kind.WEEKLY, tr("ทำงานให้เสร็จ $WEEK_TASKS อันในสัปดาห์นี้", "Finish $WEEK_TASKS tasks this week"),
            tr("เสร็จแล้ว ${week.size} อัน", "${week.size} done"), week.size, WEEK_TASKS, 150,
        )
        mainBranch(tasks, today)?.let { branch ->
            val n = week.count { t -> t.tags.any { it == branch || it.startsWith("$branch/") } }
            add(
                "w:$monday:branch", Kind.WEEKLY, tr("งานสาย #$branch $BRANCH_TASKS อัน", "$BRANCH_TASKS tasks in #$branch"),
                tr("สายงานที่ทำมากที่สุดช่วงนี้ เสร็จแล้ว $n อัน", "Your busiest branch lately, $n done"), n, BRANCH_TASKS, 100,
            )
        }
        return out
    }

    /** The top-level tag the owner works under most in the last 30 days (their "guild contract"), kind tags left out. */
    fun mainBranch(tasks: List<Task>, today: LocalDate): String? {
        val since = today.minusDays(30)
        val kinds = TaskKind.entries.mapNotNull { it.tag }.toSet()
        return tasks.filter { t -> (t.done ?: t.created ?: today) >= since }
            .flatMap { t -> t.tags.map { it.substringBefore('/') }.distinct() }
            .filter { it.isNotBlank() && !it.startsWith("remind") && it !in kinds && it != "readinglist" }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    private fun hm(t: LocalDateTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
