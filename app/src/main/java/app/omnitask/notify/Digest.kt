package app.omnitask.notify

import app.omnitask.model.Focus
import app.omnitask.model.Task
import app.omnitask.model.tr
import java.time.LocalDate

/** The text of the scheduled summaries, kept apart from Android so it can be tested. */
object Digest {

    data class Message(val title: String, val lines: List<String>)

    fun daily(tasks: List<Task>, today: LocalDate, settings: NotifySettings): Message? {
        val brief = Focus.build(tasks, today)
        val overdue = brief.must.filter { t -> t.due?.let { it < today } == true }
        val dueToday = brief.must - overdue.toSet()
        val lines = buildList {
            if (settings.digestOverdue) overdue.forEach { add(tr("เลยกำหนด: ${it.title}", "Overdue: ${it.title}")) }
            if (settings.digestDueToday) dueToday.forEach { add(tr("วันนี้: ${it.title}", "Today: ${it.title}")) }
            if (settings.digestWaiting) brief.waiting.forEach { t ->
                val who = Focus.waitingFor(t)?.let { "$it " } ?: ""
                val age = Focus.ageDays(t, today)?.let { tr("$it วัน", "$it days") } ?: ""
                add(tr("รอ ${who}$age: ${t.title}", "Waiting ${who}$age: ${t.title}").replace("  ", " ").replace(" :", ":"))
            }
            brief.future.firstOrNull()?.let { add(tr("ลงทุนอนาคต: ${it.title}", "Future: ${it.title}")) }
        }
        if (lines.isEmpty()) return null
        val parts = buildList {
            if (settings.digestOverdue && overdue.isNotEmpty()) add(tr("เลยกำหนด ${overdue.size}", "Overdue ${overdue.size}"))
            if (settings.digestDueToday) add(tr("วันนี้ ${dueToday.size}", "Today ${dueToday.size}"))
            if (settings.digestWaiting && brief.waiting.isNotEmpty()) add(tr("คนรอ ${brief.waiting.size}", "Waiting ${brief.waiting.size}"))
        }
        return Message(parts.joinToString(", ").ifEmpty { tr("แผนวันนี้", "Today's plan") }, lines)
    }

    fun weekly(tasks: List<Task>, today: LocalDate): Message {
        val weekAgo = today.minusDays(7)
        val done = tasks.filter { t -> t.done?.let { it > weekAgo } == true }
        val waitingDone = done.count { Focus.isWaiting(it) }
        val futureDone = done.count { Focus.isFutureWork(it) }
        val stillWaiting = tasks.filter { it.isOpen && Focus.isWaiting(it) }
        val overdue = tasks.filter { t -> t.isOpen && t.due?.let { it < today } == true }
        return Message(
            tr("สัปดาห์นี้เสร็จ ${done.size} งาน", "${done.size} tasks done this week"),
            listOf(
                tr("งานที่มีคนรอ: เสร็จ $waitingDone ค้าง ${stillWaiting.size}", "Waiting: $waitingDone done, ${stillWaiting.size} open"),
                tr("ลงทุนอนาคต เสร็จ $futureDone", "Future work: $futureDone done"),
                tr("เลยกำหนดค้างอยู่ ${overdue.size}", "Still overdue: ${overdue.size}"),
            ),
        )
    }
}
