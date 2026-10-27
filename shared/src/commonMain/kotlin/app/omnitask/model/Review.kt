package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The review mode (โหมดทบทวนงาน): open tasks one card at a time, the owner's order (Klui, 2026-10-27): work in a
 * project first, then other open work, then work that has not moved for a long time. No Anki-style spacing: a card
 * answered or skipped goes to the end of the pile, done or dropped work leaves it.
 *
 * It also weighs a task: importance from what happens if it is never done and who or what hangs on it, urgency from
 * time only, each with its reasons, so the owner sees why and can disagree (option ค).
 */
object Review {

    enum class Group(private val th: String, private val en: String) {
        PROJECT("งานในโปรเจกต์", "In a project"),
        OTHER("งานอื่น", "Other work"),
        STALE("ไม่ขยับนาน", "Not moved for long"),
        ;

        val label get() = tr(th, en)
    }

    /** A task untouched this long (no new ➕, ค้าง or ทบทวน line) has not moved. */
    const val STALE_DAYS = StuckLog.ASK_AFTER_DAYS

    data class Card(val task: Task, val group: Group)

    /** Tasks that can be reviewed: open, top-level, not in a list note, not a repeating chore. */
    fun candidates(tasks: List<Task>): List<Task> =
        tasks.filter { it.isOpen && it.parent == null && it.list == null && !it.todayCopy && it.recurrence == null }

    /** The last day something happened to the task: made, a ค้าง line, or a review. */
    fun lastMove(task: Task): LocalDate? = listOfNotNull(task.created, StuckLog.last(task), ReviewLog.last(task)).maxOrNull()

    fun quietDays(task: Task, today: LocalDate): Long? = lastMove(task)?.let { ChronoUnit.DAYS.between(it, today) }

    fun groupOf(task: Task, today: LocalDate): Group = when {
        Focus.isSomeday(task) -> Group.STALE
        Projects.projectOf(task) != null -> Group.PROJECT
        (quietDays(task, today) ?: 0) >= STALE_DAYS -> Group.STALE
        else -> Group.OTHER
    }

    /**
     * The pile: by group, then the task reviewed longest ago (never reviewed first), then someone waiting, then
     * the quietest. Reviewing writes today's date, which sends the card to the end of its group next time.
     */
    fun deck(tasks: List<Task>, today: LocalDate): List<Card> =
        candidates(tasks)
            .map { Card(it, groupOf(it, today)) }
            .sortedWith(
                compareBy<Card>({ it.group.ordinal })
                    .thenBy(nullsFirst()) { ReviewLog.last(it.task) }
                    .thenBy { if (Focus.isWaiting(it.task)) 0 else 1 }
                    .thenBy(nullsFirst()) { lastMove(it.task) },
            )

    // ---- Weighing ----

    /** "ถ้าไม่ทำเลย จะเกิดอะไร": the answer and its points toward importance. */
    enum class Impact(private val th: String, private val en: String, val points: Int) {
        NONE("ไม่มีอะไร", "Nothing", 0),
        SELF("ตัวเองเสียโอกาส", "I lose a chance", 15),
        OTHERS("คนอื่นเดือดร้อน", "Others are hurt", 30),
        BIG("กระทบผู้ป่วยหรือเงิน", "Patients or money", 45),
        ;

        val label get() = tr(th, en)
    }

    /** "ถ้าเลื่อนไปอีก 1 สัปดาห์ จะเป็นยังไง": feeds urgency for work without a 📅. */
    enum class Delay(private val th: String, private val en: String) {
        SAME("ไม่ต่าง", "No different"),
        BIT("แย่ลงนิดหน่อย", "A bit worse"),
        MUCH("แย่ลงมาก", "Much worse"),
        MISS("พลาดเลย", "Missed"),
        ;

        val label get() = tr(th, en)
    }

    enum class Urgency(private val th: String, private val en: String) {
        LOW("ต่ำ", "Low"),
        MID("กลาง", "Medium"),
        HIGH("สูง", "High"),
        TOP("สูงมาก", "Very high"),
        ;

        val label get() = tr(th, en)
    }

    data class Guess(val impact: Impact, val why: String)

    data class Part(val label: String, val points: Int)

    data class Weight(
        val score: Int,
        val priority: Priority,
        val parts: List<Part>,
        val urgency: Urgency,
        val urgencyWhy: String,
        /** Where the answers and the signals disagree, said plainly; empty when they agree. */
        val flags: List<String>,
    )

    /** Points to importance level. */
    fun levelOf(score: Int): Priority = when {
        score >= 60 -> Priority.HIGHEST
        score >= 45 -> Priority.HIGH
        score >= 25 -> Priority.MEDIUM
        score >= 10 -> Priority.NONE
        else -> Priority.LOW
    }

    /** The system's first guess at the impact, from what the note already says. */
    fun guess(task: Task, today: LocalDate): Guess {
        val dueSoon = task.due?.let { ChronoUnit.DAYS.between(today, it) <= 3 } == true
        return when {
            Focus.isWaiting(task) -> Guess(Impact.OTHERS, Focus.waitingFor(task)?.let { tr("$it รออยู่", "$it is waiting") } ?: tr("มีคนรออยู่ (#รอ)", "Someone is waiting (#รอ)"))
            dueSoon -> Guess(Impact.SELF, tr("ใกล้หรือเลยวันส่ง", "Due soon or late"))
            Projects.projectOf(task) != null -> Guess(Impact.SELF, tr("อยู่ในโปรเจกต์", "Part of a project"))
            (quietDays(task, today) ?: 0) >= STALE_DAYS -> Guess(Impact.NONE, tr("นิ่งมา ${quietDays(task, today)} วัน ไม่มีใครรอ", "Quiet ${quietDays(task, today)} days, nobody waiting"))
            else -> Guess(Impact.SELF, tr("ไม่มีสัญญาณชัด", "No clear sign"))
        }
    }

    /**
     * Importance and urgency for [task]. [impact], [waiting], [due] and [delay] are the owner's answers on the card
     * (null: not answered, the note decides); [moves] is how often the task was moved to a later day ([Learned]).
     */
    fun weigh(
        task: Task,
        tasks: List<Task>,
        today: LocalDate,
        impact: Impact? = null,
        waiting: Boolean? = null,
        due: LocalDate? = null,
        delay: Delay? = null,
        moves: Int = 0,
    ): Weight {
        val guessed = guess(task, today)
        val chosen = impact ?: guessed.impact
        val isWaiting = waiting ?: Focus.isWaiting(task)
        val parts = buildList {
            add(Part(tr("ผลถ้าไม่ทำ: ", "If never done: ") + chosen.label + if (impact == null) tr(" (ระบบเดา)", " (guessed)") else "", chosen.points))
            if (isWaiting) add(Part(tr("มีคนรองานนี้", "Someone is waiting"), 15))
            if (Projects.projectOf(task) != null) add(Part(tr("อยู่ในโปรเจกต์", "Part of a project"), 10))
            val blocks = task.id?.let { id -> tasks.count { it.isOpen && id in it.dependsOn } } ?: 0
            if (blocks > 0) add(Part(tr("งานอื่นรองานนี้อยู่ $blocks งาน", "$blocks other tasks wait on this"), minOf(20, 10 * blocks)))
            if (Focus.isFutureWork(task)) add(Part(tr("ตรงเป้าระยะยาว (#อนาคต)", "Long-term goal (#อนาคต)"), 10))
        }
        val score = parts.sumOf { it.points }
        val dueDay = due ?: task.due
        val (urgency, why) = when {
            dueDay != null -> {
                val left = ChronoUnit.DAYS.between(today, dueDay)
                val u = when {
                    left <= 1 -> Urgency.TOP
                    left <= 3 -> Urgency.HIGH
                    left <= 7 -> Urgency.MID
                    else -> Urgency.LOW
                }
                u to (if (left < 0) tr("เลยวันส่งมา ${-left} วัน", "${-left} days late") else tr("ส่งในอีก $left วัน", "Due in $left days"))
            }
            delay == Delay.MISS -> Urgency.HIGH to tr("ถ้าเลื่อนอีกสัปดาห์จะพลาด", "A week later is too late")
            delay == Delay.MUCH -> Urgency.MID to tr("ถ้าเลื่อนอีกสัปดาห์จะแย่ลงมาก", "A week later is much worse")
            isWaiting -> Urgency.MID to tr("มีคนรอ แต่ไม่มีวันส่ง", "Someone waits, no due date")
            else -> Urgency.LOW to tr("ไม่มีวันส่ง และเลื่อนได้", "No due date, can wait")
        }
        val flags = buildList {
            if (chosen.points < guessed.impact.points) add(tr("แต่ระบบเห็นว่า${guessed.why} แน่ใจไหม", "But ${guessed.why}. Sure?"))
            if (moves >= Learned.SLIP_MOVES) add(
                if (score >= 45) tr("บอกว่าสำคัญ แต่เลื่อนมาแล้ว $moves ครั้ง ติดอะไรไหม", "Important, yet moved $moves times. What holds it?")
                else tr("เลื่อนมาแล้ว $moves ครั้ง ถ้าไม่สำคัญจริง ปล่อยได้ไหม", "Moved $moves times. If it does not matter, let it go?"),
            )
        }
        return Weight(score, levelOf(score), parts, urgency, why, flags)
    }
}
