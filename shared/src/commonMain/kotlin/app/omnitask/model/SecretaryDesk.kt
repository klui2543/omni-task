package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber

/**
 * The secretary's own answers, worked out by rules from the tasks and the calendar, with no AI: what to do now,
 * spreading overdue work over days that are really free, what tomorrow holds, and the hidden steps of a big task.
 * Each answer comes with the buttons the owner is likely to want next. What the rules cannot answer goes to Claude.
 */
object SecretaryDesk {
    enum class Ask { OPEN, NEXT, OTHER, LIGHT, START, DONE, LATE, APPLY, STEP_BY_STEP, TOMORROW, WEEK, SPLIT, APPLY_SPLIT, CLAUDE }

    data class Chip(val label: String, val ask: Ask)

    /** A date change the owner can accept: [task]'s [field] to [date], with a few words on why that day. */
    data class Move(val task: Task, val field: TaskLine.DateField, val date: LocalDate, val note: String)

    data class Reply(
        val lines: List<String>,
        val chips: List<Chip>,
        /** The task the answer is about (opens on a tap; START, DONE and SPLIT act on it). */
        val task: Task? = null,
        val moves: List<Move> = emptyList(),
        val steps: List<String> = emptyList(),
    )

    /**
     * What the rules read. [othersEvent] tells an event in someone else's calendar (the owner labelled it, such as
     * "แฟน"): it does not take the owner's time. [skipped] are tasks put off for today.
     */
    data class Ctx(
        val tasks: List<Task>,
        val events: List<CalendarEvent>,
        val today: LocalDate,
        val now: LocalDateTime,
        val skipped: Set<String> = emptySet(),
        val othersEvent: (CalendarEvent) -> Boolean = { false },
    )

    private val CLAUDE_CHIP get() = Chip(tr("คุยเสียงกับ Claude", "Talk to Claude"), Ask.CLAUDE)

    /** The buttons when the chat opens, picked from the time of day, tomorrow and the overdue work. */
    fun opening(ctx: Ctx): Reply {
        val late = lateTasks(ctx)
        val tomorrow = ownEvents(ctx, ctx.today.plusDays(1))
        val evening = ctx.now.hour >= 17
        val lines = buildList {
            add(greeting(ctx.now.hour) + freeLine(ctx))
            tomorrow.firstOrNull()?.let { add(tr("พรุ่งนี้: ", "Tomorrow: ") + it.title + if (tomorrow.size > 1) tr(" และอีก ${tomorrow.size - 1} นัด", " and ${tomorrow.size - 1} more") else "") }
            val soon = ctx.tasks.count { it.isOpen && it.due.let { d -> d != null && d >= ctx.today && d <= ctx.today.plusDays(3) } }
            if (soon > 0 || late.isNotEmpty()) add(
                listOfNotNull(
                    if (soon > 0) tr("กำหนดจริงใน 3 วัน $soon งาน", "$soon real deadlines in 3 days") else null,
                    if (late.isNotEmpty()) tr("ค้างจากก่อนหน้า ${late.size} งาน", "${late.size} left over") else null,
                ).joinToString(", "),
            )
        }
        val chips = buildList {
            if (evening) add(Chip(tr("พรุ่งนี้มีอะไร", "What's tomorrow"), Ask.TOMORROW))
            add(Chip(tr("ตอนนี้ทำอะไรดี", "What now"), Ask.NEXT))
            if (late.isNotEmpty()) add(Chip(tr("งานค้าง ${late.size} งาน", "${late.size} left over"), Ask.LATE))
            if (!evening) add(Chip(tr("พรุ่งนี้มีอะไร", "What's tomorrow"), Ask.TOMORROW))
            add(Chip(tr("สัปดาห์นี้", "This week"), Ask.WEEK))
            add(Chip(tr("งานใหญ่ ไม่รู้เริ่มตรงไหน", "Big task, where to start"), Ask.SPLIT))
            add(CLAUDE_CHIP)
        }
        return Reply(lines, chips)
    }

    /** One task to do now, with why and the buttons that follow from it. [exclude] are tasks already offered. */
    fun next(ctx: Ctx, exclude: Set<String> = emptySet(), light: Boolean = false): Reply {
        val ranked = NextUp.ranked(ctx.tasks, ctx.today, ctx.skipped + exclude)
        val pick = if (light) ranked.firstOrNull { isLight(it.task, ctx.tasks) } ?: ranked.firstOrNull() else ranked.firstOrNull()
        if (pick == null) return Reply(
            listOf(tr("ไม่มีงานที่ต้องทำวันนี้แล้ว พักได้", "Nothing needs doing today. Rest.")),
            listOf(Chip(tr("พรุ่งนี้มีอะไร", "What's tomorrow"), Ask.TOMORROW), Chip(tr("สัปดาห์นี้", "This week"), Ask.WEEK), CLAUDE_CHIP),
        )
        val sub = ctx.tasks.count { it.parent == pick.task.key && it.isOpen }
        val lines = buildList {
            add(pick.task.title)
            add(pick.reason + freeLine(ctx).let { if (it.isBlank()) "" else ", " + it.trimStart(' ', ',') })
            if (sub > 0) add(tr("มีงานย่อยค้าง $sub ข้อ ทำข้อแรกพอ", "$sub open subtasks: do the first one"))
        }
        return Reply(
            lines,
            listOf(
                Chip(if (pick.task.status == Status.IN_PROGRESS) tr("เสร็จแล้ว", "Done") else tr("เริ่มเลย", "Start"), if (pick.task.status == Status.IN_PROGRESS) Ask.DONE else Ask.START),
                Chip(tr("ขออันอื่น", "Something else"), Ask.OTHER),
                Chip(tr("เหนื่อย ของานเบา", "Tired, something light"), Ask.LIGHT),
                Chip(tr("งานนี้ใหญ่ไป แตกให้หน่อย", "Too big, split it"), Ask.SPLIT),
            ),
            task = pick.task,
        )
    }

    /** Overdue work spread over the days ahead that are really free, at most one a day, real deadlines first. */
    fun late(ctx: Ctx): Reply {
        val late = lateTasks(ctx)
        if (late.isEmpty()) return Reply(listOf(tr("ไม่มีงานค้างแล้ว", "Nothing left over")), listOf(Chip(tr("ตอนนี้ทำอะไรดี", "What now"), Ask.NEXT), CLAUDE_CHIP))
        val moves = spread(ctx, late)
        val lines = listOf(
            tr("ผมกระจายลงวันที่ว่างจริงตามนัดและเวร วันละไม่เกิน 1 งาน", "Spread over days that are really free, at most one a day"),
            tr("ยังไม่เปลี่ยนอะไร จนกว่าคุณกด ลงตามนี้", "Nothing changes until you tap Apply"),
        )
        return Reply(
            lines,
            listOf(
                Chip(tr("ลงตามนี้", "Apply"), Ask.APPLY),
                Chip(tr("ขยับทีละงาน", "One at a time"), Ask.STEP_BY_STEP),
                CLAUDE_CHIP,
            ),
            moves = moves,
        )
    }

    /** Tomorrow: the owner's events and the work dated for it. */
    fun tomorrow(ctx: Ctx): Reply = day(ctx, ctx.today.plusDays(1), tr("พรุ่งนี้", "Tomorrow"))

    /** The next seven days, one line each: events, then the work dated for it. */
    fun week(ctx: Ctx): Reply {
        val lines = (0..6).map { i ->
            val d = ctx.today.plusDays(i.toLong())
            val ev = ownEvents(ctx, d)
            val work = dated(ctx, d)
            dayName(d) + " " + d.dayOfMonth + ": " + (listOfNotNull(
                ev.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.title },
                work.takeIf { it.isNotEmpty() }?.let { tr("งาน ${it.size}", "${it.size} tasks") },
            ).joinToString(", ").ifBlank { tr("ว่าง", "free") })
        }
        return Reply(lines, listOf(Chip(tr("งานค้าง", "Left over"), Ask.LATE), Chip(tr("ตอนนี้ทำอะไรดี", "What now"), Ask.NEXT), CLAUDE_CHIP))
    }

    private fun day(ctx: Ctx, d: LocalDate, name: String): Reply {
        val ev = ownEvents(ctx, d)
        val work = dated(ctx, d)
        val lines = buildList {
            add(name + " " + dayName(d) + " " + d.dayOfMonth)
            if (ev.isEmpty() && work.isEmpty()) add(tr("ว่างทั้งวัน", "Free all day"))
            ev.forEach { add(if (it.allDay) it.title + tr(" (ทั้งวัน)", " (all day)") else hm(it.begin.hour, it.begin.minute) + " " + it.title) }
            work.forEach { add(tr("งาน: ", "Task: ") + it.title) }
        }
        return Reply(lines, listOf(Chip(tr("ตอนนี้ทำอะไรดี", "What now"), Ask.NEXT), Chip(tr("สัปดาห์นี้", "This week"), Ask.WEEK), CLAUDE_CHIP))
    }

    /**
     * The hidden steps a task of this kind usually has (what the owner tends not to see at first), to tick. [task] is
     * the one in question, or the most pressing open one.
     */
    fun split(ctx: Ctx, task: Task?): Reply {
        val t = task ?: NextUp.ranked(ctx.tasks, ctx.today, ctx.skipped).firstOrNull()?.task
            ?: ctx.tasks.filter { it.isOpen && it.parent == null }.maxByOrNull { it.title.length }
            ?: return Reply(listOf(tr("ยังไม่มีงานให้แตก", "No task to split")), listOf(CLAUDE_CHIP))
        val have = ctx.tasks.filter { it.parent == t.key }.map { it.title.trim() }.toSet()
        val steps = hiddenSteps(t).filter { it !in have }
        return Reply(
            listOf(t.title, tr("งานแบบนี้มักมีขั้นที่มองไม่เห็นตอนแรก เลือกที่ใช่ แล้วผมใส่เป็นงานย่อย", "Tasks like this hide steps. Pick the ones that apply; they become subtasks")),
            listOf(Chip(tr("แตกตามนี้", "Add these"), Ask.APPLY_SPLIT), Chip(tr("ให้ Claude ช่วยคิด", "Ask Claude"), Ask.CLAUDE)),
            task = t, steps = steps,
        )
    }

    /** What a typed question asks, when the rules can answer it; null sends it on to Claude. */
    fun match(text: String): Ask? {
        val t = text.lowercase().replace(" ", "")
        return when {
            listOf("พรุ่งนี้", "tomorrow").any { it in t } -> Ask.TOMORROW
            listOf("สัปดาห์", "อาทิตย์นี้", "week").any { it in t } -> Ask.WEEK
            listOf("ค้าง", "เลยกำหนด", "เลยเป้า", "overdue").any { it in t } -> Ask.LATE
            listOf("แตก", "ไม่รู้เริ่ม", "ใหญ่", "split").any { it in t } -> Ask.SPLIT
            listOf("เหนื่อย", "งานเบา", "tired").any { it in t } -> Ask.LIGHT
            listOf("ทำอะไรดี", "ทำอะไรก่อน", "ตอนนี้", "วันนี้ทำ", "whatnow").any { it in t } -> Ask.NEXT
            else -> null
        }
    }

    /** Open work past its date (a real deadline or an own target), the oldest first. */
    fun lateTasks(ctx: Ctx): List<Task> = ctx.tasks.filter { t ->
        t.isOpen && t.parent == null && t.list == null && (t.due?.let { it < ctx.today } == true || t.scheduled?.let { it < ctx.today } == true)
    }.sortedWith(compareBy({ it.due == null || it.due!! >= ctx.today }, { it.due ?: it.scheduled }))

    /**
     * Days from today (if the evening has not come) over the next two weeks with room: under six hours of the owner's
     * own timed events, no all-day event of the owner's, and nothing already dated there. At most one task a day.
     */
    fun spread(ctx: Ctx, tasks: List<Task>): List<Move> {
        val start = if (ctx.now.hour < 17) ctx.today else ctx.today.plusDays(1)
        val free = (0..20).map { start.plusDays(it.toLong()) }.filter { d -> roomOn(ctx, d) }.toMutableList()
        return tasks.mapNotNull { t ->
            val d = free.removeFirstOrNull() ?: return@mapNotNull null
            val realLate = t.due?.let { it < ctx.today } == true
            val field = if (realLate || t.scheduled == null) TaskLine.DateField.DUE else TaskLine.DateField.SCHEDULED
            val note = if (realLate) tr("กำหนดจริงเลยมา ${t.due!!.daysUntil(ctx.today)} วัน: วันแรกที่ว่าง", "Deadline passed: first free day")
            else tr("เป้าที่ตั้งเอง: ทำขั้นเดียวพอ", "Own target: one step is enough")
            Move(t, field, d, note)
        }
    }

    private fun roomOn(ctx: Ctx, d: LocalDate): Boolean {
        val own = ownEvents(ctx, d)
        if (own.any { it.allDay }) return false
        val busy = own.filter { !it.allDay }.sumOf { e -> (e.begin.time.hour * 60 + e.begin.time.minute).let { b -> ((e.end.time.hour * 60 + e.end.time.minute) - b).coerceAtLeast(0) } }
        return busy < 6 * 60 && dated(ctx, d).isEmpty()
    }

    private fun ownEvents(ctx: Ctx, d: LocalDate) = ctx.events.filter { e ->
        !ctx.othersEvent(e) && e.begin.date <= d && (e.end.date > d || e.begin.date == d)
    }.sortedWith(compareBy({ !it.allDay }, { it.begin }))

    private fun dated(ctx: Ctx, d: LocalDate) = ctx.tasks.filter { it.isOpen && it.parent == null && (it.due ?: it.scheduled) == d }

    /** A task that asks little: no open subtasks and a short title, or one tagged light. */
    private fun isLight(t: Task, all: List<Task>): Boolean =
        t.tags.any { it == "เบา" || it.equals("light", ignoreCase = true) } || (all.none { it.parent == t.key && it.isOpen } && t.title.length <= 40)

    /** Hidden steps by the kind of task, from the interview: wording, fitting the screen, someone trying it. */
    fun hiddenSteps(t: Task): List<String> {
        val title = t.title.lowercase()
        val writing = listOf("เขียน", "ป้าย", "บทความ", "ข้อความ", "สรุป", "ร่าง").any { it in title }
        val build = listOf("ระบบ", "แอป", "app", "เว็บ", "flow", "ปรับ").any { it in title }
        return buildList {
            add(tr("ร่างแรก แบบหยาบ", "A rough first draft"))
            if (writing) add(tr("ตรวจคำและภาษาให้คนอ่านเข้าใจ", "Check the wording reads clearly"))
            if (build || writing) add(tr("ดูว่าพอดีจอมือถือ", "Check it fits a phone screen"))
            add(tr("ให้คนอื่นลองใช้หรืออ่าน", "Have someone try or read it"))
            add(tr("แก้ตามที่ได้ยิน", "Fix what you hear back"))
            add(tr("ปล่อยใช้และบอกคนที่เกี่ยวข้อง", "Release it and tell the people involved"))
        }
    }

    private fun freeLine(ctx: Ctx): String {
        val own = ownEvents(ctx, ctx.today).filter { !it.allDay && it.end > ctx.now }
        val next = own.firstOrNull { it.begin > ctx.now }
        return when {
            own.any { it.begin <= ctx.now } -> tr(" ตอนนี้ติดนัดอยู่", " you are in an event now")
            next != null -> tr(" ว่างถึง ", " free until ") + hm(next.begin.hour, next.begin.minute)
            else -> ""
        }
    }

    private fun greeting(hour: Int) = when {
        hour < 11 -> tr("อรุณสวัสดิ์", "Good morning")
        hour < 17 -> tr("สวัสดีตอนบ่าย", "Good afternoon")
        else -> tr("สวัสดีตอนเย็น", "Good evening")
    }

    private fun dayName(d: LocalDate) = tr(
        listOf("จ", "อ", "พ", "พฤ", "ศ", "ส", "อา")[d.dayOfWeek.isoDayNumber - 1],
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[d.dayOfWeek.isoDayNumber - 1],
    )

    private fun hm(h: Int, m: Int) = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
