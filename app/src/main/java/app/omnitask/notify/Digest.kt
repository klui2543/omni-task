package app.omnitask.notify

import app.omnitask.model.Focus
import app.omnitask.model.Task
import java.time.LocalDate

/** The text of the scheduled summaries, kept apart from Android so it can be tested. */
object Digest {

    data class Message(val title: String, val lines: List<String>)

    fun daily(tasks: List<Task>, today: LocalDate, settings: NotifySettings): Message? {
        val brief = Focus.build(tasks, today)
        val overdue = brief.must.filter { t -> t.due?.let { it < today } == true }
        val dueToday = brief.must - overdue.toSet()
        val lines = buildList {
            if (settings.digestOverdue) overdue.forEach { add("เลยกำหนด: ${it.title}") }
            if (settings.digestDueToday) dueToday.forEach { add("วันนี้: ${it.title}") }
            if (settings.digestWaiting) brief.waiting.forEach { t ->
                val who = Focus.waitingFor(t)?.let { "$it " } ?: ""
                val age = Focus.ageDays(t, today)?.let { "$it วัน" } ?: ""
                add("รอ ${who}$age: ${t.title}".replace("  ", " ").replace(" :", ":"))
            }
            brief.future.firstOrNull()?.let { add("ลงทุนอนาคต: ${it.title}") }
        }
        if (lines.isEmpty()) return null
        val parts = buildList {
            if (settings.digestOverdue && overdue.isNotEmpty()) add("เลยกำหนด ${overdue.size}")
            if (settings.digestDueToday) add("วันนี้ ${dueToday.size}")
            if (settings.digestWaiting && brief.waiting.isNotEmpty()) add("คนรอ ${brief.waiting.size}")
        }
        return Message(parts.joinToString(", ").ifEmpty { "แผนวันนี้" }, lines)
    }

    fun weekly(tasks: List<Task>, today: LocalDate): Message {
        val weekAgo = today.minusDays(7)
        val done = tasks.filter { t -> t.done?.let { it > weekAgo } == true }
        val waitingDone = done.count { Focus.isWaiting(it) }
        val futureDone = done.count { Focus.isFutureWork(it) }
        val stillWaiting = tasks.filter { it.isOpen && Focus.isWaiting(it) }
        val overdue = tasks.filter { t -> t.isOpen && t.due?.let { it < today } == true }
        return Message(
            "สัปดาห์นี้เสร็จ ${done.size} งาน",
            listOf(
                "งานที่มีคนรอ: เสร็จ $waitingDone ค้าง ${stillWaiting.size}",
                "ลงทุนอนาคต เสร็จ $futureDone",
                "เลยกำหนดค้างอยู่ ${overdue.size}",
            ),
        )
    }
}
