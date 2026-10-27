package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.data.VaultText
import kotlinx.datetime.LocalDate

/**
 * Writes one review card's answers into the task note, the way the owner agreed (Klui, 2026-10-27): what moved goes
 * on a dated `ค้าง` line (shown as ความคืบหน้า), the next step on a `ก้าวถัดไป:` line, today's review on the `ทบทวน`
 * line, and the rest (status, dates, someone waiting, folder, importance) on the task line itself.
 */
object ReviewWrite {

    /** "งานนี้ตอนนี้": the first question on the card. */
    enum class Progress(private val th: String, private val en: String) {
        GO("เดินอยู่", "Moving"),
        STUCK("ติด", "Stuck"),
        DONE("เสร็จแล้ว", "Done"),
        PARK("พักไว้ก่อน", "Parked"),
        DROP("ไม่ทำแล้ว", "Dropped"),
        ;

        val label get() = tr(th, en)
        val open get() = this == GO || this == STUCK || this == PARK
    }

    /** The answers on one card; null means the question was not asked, or answered "ถูกแล้ว" / "ไม่แน่ใจ" (nothing changes). */
    data class Answers(
        val progress: Progress,
        val why: StuckLog.Reason? = null,
        /** What moved, typed; null for "ไม่มีอะไรใหม่". */
        val note: String? = null,
        /** The next step, typed; null for "ไม่แน่ใจ" (an earlier next step stays). */
        val nextStep: String? = null,
        /** "จะหยิบมาทำ": written as ⏳. */
        val scheduled: LocalDate? = null,
        /** "คนที่รอ": true with or without [waitName], false for "ไม่มี". */
        val waiting: Boolean? = null,
        val waitName: String? = null,
        /** "เขาต้องการ": written as 📅. */
        val due: LocalDate? = null,
        /** "เขาต้องการ: ไม่แน่ใจ": a task to ask them is added for today. */
        val askWhen: Boolean = false,
        /** "อยู่ที่": the folder to move to. */
        val folder: String? = null,
        val delay: Review.Delay? = null,
        val impact: Review.Impact? = null,
    )

    /**
     * Applies [a] to the task at [index] of [lines], with the importance level [priority] the owner saw and
     * confirmed. Returns the line of a new task to add to the task note (asking when it is wanted), or null.
     */
    fun apply(lines: MutableList<String>, index: Int, task: Task, a: Answers, priority: Priority?, today: LocalDate): String? {
        var line = lines[index]
        when (a.progress) {
            Progress.DONE -> line = TaskLine.setStatus(line, Status.DONE, today)
            Progress.DROP -> line = TaskLine.setStatus(line, Status.CANCELLED, today)
            Progress.PARK -> line = TaskLine.addTag(line, Focus.SOMEDAY_TAG)
            else -> {}
        }
        val name = a.waitName?.let(TaskLine::normalizeTag)?.takeIf { it.isNotEmpty() }
        when {
            a.waiting == true && name != null && Focus.waitingFor(task) == null -> {
                // A plain #รอ becomes #รอ/name where it stood.
                line = if (task.tags.any { it == Focus.WAITING_TAG }) TaskLine.retag(line, Focus.WAITING_TAG, "${Focus.WAITING_TAG}/$name")
                else TaskLine.addTag(line, "${Focus.WAITING_TAG}/$name")
            }
            a.waiting == true && !Focus.isWaiting(task) -> line = TaskLine.addTag(line, Focus.WAITING_TAG)
            a.waiting == false -> task.tags.filter { it == Focus.WAITING_TAG || it.startsWith("${Focus.WAITING_TAG}/") }
                .forEach { line = TaskLine.removeTag(line, it) }
        }
        a.scheduled?.let { line = TaskLine.setDate(line, DateField.SCHEDULED, it) }
        a.due?.let { line = TaskLine.setDate(line, DateField.DUE, it) }
        a.folder?.let(Folders::clean)?.takeIf { it.isNotEmpty() }?.let { to ->
            val from = Folders.of(task)
            line = when {
                Folders.isLabel(to) -> TaskLine.addTag(line, to)
                from == null -> TaskLine.homeTag(line, to)
                else -> TaskLine.retag(line, from, to)
            }
        }
        if (priority != null && a.progress.open) line = TaskLine.setPriority(line, priority)
        lines[index] = line

        val note = a.note?.trim()?.takeIf { it.isNotEmpty() }
        val label = if (a.progress == Progress.STUCK) a.why?.label else a.progress.label
        when {
            note != null -> VaultText.addSubLine(lines, index, StuckLog.line(today, if (label != null) "$label: $note" else note))
            a.progress == Progress.STUCK && label != null -> VaultText.addSubLine(lines, index, StuckLog.line(today, label))
        }
        a.nextStep?.trim()?.takeIf { it.isNotEmpty() }?.let {
            VaultText.setSubLine(lines, index, Task.NEXT_STEP, "${Task.NEXT_STEP} ${it.replace(Regex("""\s*\n\s*"""), " ")}")
        }
        ReviewLog.write(lines, index, today)

        if (!a.askWhen || !a.progress.open) return null
        val who = name ?: Focus.waitingFor(task) ?: tr("คนที่รอ", "them")
        var ask = "- [ ] " + tr("ถาม${who}ว่าต้องการ \"${task.title}\" เมื่อไร", "Ask $who when they need \"${task.title}\"")
        Folders.of(task)?.let { ask = TaskLine.addTag(ask, it) }
        ask = TaskLine.setDate(ask, DateField.CREATED, today)
        return TaskLine.setDate(ask, DateField.SCHEDULED, today)
    }
}
