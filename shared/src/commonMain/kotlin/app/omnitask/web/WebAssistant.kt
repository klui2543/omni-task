package app.omnitask.web

import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.model.Focus
import app.omnitask.model.Insight
import app.omnitask.model.Planner
import app.omnitask.model.Profile
import app.omnitask.model.ReminderOn
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.DayPlan
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import app.omnitask.notify.Digest
import app.omnitask.time.atStartOfDay
import app.omnitask.time.minusDays
import app.omnitask.time.plusDays
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The Assistant page for the web: the profile note, the insight questions, and the rule-based answers of the
 * chat (free time for something, a look at a range, a plan for a range, a ranking, the weekly review), all built
 * by the same code the Android assistant uses ([Profile], [Insight], [Planner], [Focus], [DayPlan], [Digest]).
 * Everything crosses as text: the note text and a JSON state in, JSON or note text out.
 */
object WebAssistant {

    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    /** What the page knows besides the note: the clock, the profile note's text, the calendar and this device's choices. */
    @Serializable
    data class StateIn(
        /** The device's own clock as local `yyyy-MM-ddTHH:mm`. */
        val now: String,
        val profile: String? = null,
        val events: List<WebFocus.EventIn> = emptyList(),
        /** When work got done, as `yyyy-MM-ddTHH:mm:ss|KIND`, for noticing the owner's rhythm. */
        val doneLog: List<String> = emptyList(),
        /** Ids of insights the owner said no to. */
        val declined: List<String> = emptyList(),
        /** The insight being answered (for [insightYes]). */
        val insightId: String? = null,
        val futureCount: Int = 1,
        val skippedToday: List<String> = emptyList(),
    )

    private fun parse(stateJson: String) = json.decodeFromString<StateIn>(stateJson)

    private fun events(s: StateIn) = s.events.map { CalendarEvent(it.id, it.title, LocalDateTime.parse(it.begin), LocalDateTime.parse(it.end), it.allDay) }

    private fun hm(t: LocalTime) = Profile.hm(t)

    // ---- Profile ----

    @Serializable
    data class ProfileOut(
        val exists: Boolean,
        val wake: String,
        val sleep: String,
        val focusFrom: String,
        val focusTo: String,
        val exercise: String,
        val shiftWords: List<String>,
        val nightWords: List<String>,
        val bestDay: String?,
        val remembered: List<String>,
        val updated: String?,
        /** Lifestyles change, so the assistant asks again once the profile is a month old. */
        val stale: Boolean,
    )

    /** The profile note's text (null when the vault has none) as the fields the page shows. */
    fun profile(text: String?, today: String): String {
        val p = Profile.parse(text)
        return json.encodeToString(
            ProfileOut(
                p.exists, hm(p.wake), hm(p.sleep), hm(p.focusFrom), hm(p.focusTo), hm(p.exercise), p.shiftWords, p.nightWords,
                p.bestDay?.let { Profile.DAY_NAMES[it.ordinal] }, p.remembered, p.updated?.toString(), p.stale(LocalDate.parse(today)),
            ),
        )
    }

    /** One change to the profile note; every field left out stays as the note has it. */
    @Serializable
    data class ProfileChange(
        val wake: String? = null,
        val sleep: String? = null,
        val focusFrom: String? = null,
        val focusTo: String? = null,
        val exercise: String? = null,
        /** A day name as written in the note (จันทร์ ... อาทิตย์). */
        val bestDay: String? = null,
        /** Lines to add under "จำไว้". */
        val remember: List<String> = emptyList(),
        /** Answers given in the owner's own words ("แล้วแต่เวร"): kept word for word under their question, replacing an earlier one. */
        val described: Map<String, String> = emptyMap(),
    )

    /**
     * The profile note after [changeJson]: the note as it stands now is read again, only the named fields change, and
     * saving counts as "still true" so the monthly re-ask restarts. This is Android's saveProfile, one field at a time,
     * so a change made in Obsidian to another field is kept.
     */
    fun profileApply(text: String?, changeJson: String, today: String): String {
        val c = json.decodeFromString<ProfileChange>(changeJson)
        var p = Profile.parse(text)
        c.wake?.let { p = p.copy(wake = LocalTime.parse(it)) }
        c.sleep?.let { p = p.copy(sleep = LocalTime.parse(it)) }
        c.focusFrom?.let { p = p.copy(focusFrom = LocalTime.parse(it)) }
        c.focusTo?.let { p = p.copy(focusTo = LocalTime.parse(it)) }
        c.exercise?.let { p = p.copy(exercise = LocalTime.parse(it)) }
        c.bestDay?.let { name ->
            val i = Profile.DAY_NAMES.indexOf(name)
            if (i >= 0) p = p.copy(bestDay = DayOfWeek.entries[i])
        }
        val described = c.described.filter { (_, v) -> v.isNotBlank() }.map { (k, v) -> "$k: ${v.trim()}" }
        p = p.copy(
            remembered = (p.remembered.filterNot { r -> described.any { r.startsWith(it.substringBefore(':') + ":") } } + described + c.remember).distinct(),
        )
        return p.copy(exists = true, updated = LocalDate.parse(today)).render()
    }

    // ---- Insight ("observe, then ask before remembering") ----

    @Serializable
    data class AskOut(val id: String, val text: String, val remember: String, val window: String)

    /** The pattern worth asking about, or `null`. */
    fun insight(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = parse(stateJson)
        val ask = next(fileKey, path, text, s) ?: return "null"
        return json.encodeToString(AskOut(ask.id, ask.text, ask.remember, ask.window))
    }

    private fun log(s: StateIn): List<Insight.Done> = s.doneLog.mapNotNull { e ->
        val at = runCatching { LocalDateTime.parse(e.substringBefore('|')) }.getOrNull() ?: return@mapNotNull null
        val kind = Planner.Kind.entries.firstOrNull { it.name == e.substringAfter('|') } ?: return@mapNotNull null
        Insight.Done(at, kind)
    }

    private fun next(fileKey: String, path: String, text: String, s: StateIn): Insight.Ask? {
        val today = LocalDateTime.parse(s.now).date
        val tasks = WebNotes.tasks(fileKey, path, text)
        return Insight.next(tasks, log(s), Profile.parse(s.profile), today, s.declined.toSet())
    }

    /**
     * The profile note after the owner said yes to [StateIn.insightId]: the insight's change plus its sentence under
     * "จำไว้". An empty text means that question is no longer being asked, so nothing should be written.
     */
    fun insightYes(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = parse(stateJson)
        val ask = next(fileKey, path, text, s)?.takeIf { it.id == s.insightId } ?: return ""
        val p = Profile.parse(s.profile)
        val today = LocalDateTime.parse(s.now).date
        return ask.apply(p).copy(remembered = (p.remembered + ask.remember).distinct(), exists = true, updated = today).render()
    }

    /** What kind of work a task title is (MOVE, DEEP, QUICK, ERRAND, GENERAL), for the done log. */
    fun kindOf(title: String): String = Planner.kindOf(title).name

    // ---- The chat ----

    @Serializable
    data class RouteOut(val kind: String, val from: String? = null, val to: String? = null)

    /**
     * What a sentence asks for, as Android's chat reads it: `plan` or `agenda` over a range (this week when none is
     * named), `rank`, `slots` when it says how long it takes, otherwise `duration` (the assistant asks how long first).
     */
    fun route(request: String, today: String): String {
        val t = request.lowercase()
        val day = LocalDate.parse(today)
        val range = Planner.rangeOf(request, day) ?: (day to day.plusDays(6))
        val out = when {
            listOf("วางแผน", "plan ").any { it in "$t " } && !t.startsWith("plan my day") -> RouteOut("plan", range.first.toString(), range.second.toString())
            listOf("มีอะไร", "what's on", "whats on", "what do i have", "agenda").any { it in t } -> RouteOut("agenda", range.first.toString(), range.second.toString())
            listOf("จัดลำดับ", "ทำอะไรก่อน", "ควรทำอะไร", "prioritize", "priorities", "what first").any { it in t } -> RouteOut("rank")
            Planner.explicitMinutes(request) != null -> RouteOut("slots")
            else -> RouteOut("duration")
        }
        return json.encodeToString(out)
    }

    @Serializable
    data class SlotOut(val day: String, val dayLabel: String, val start: String, val end: String, val title: String, val why: String, val score: Int)

    @Serializable
    data class PlanOut(val request: String, val title: String, val kind: String, val kindLabel: String, val minutes: Int, val intro: String, val slots: List<SlotOut>)

    /** Three-at-a-time candidate times for [request]; [minutes] at 0 or less means "estimate from the kind of work". */
    fun slots(fileKey: String, path: String, text: String, stateJson: String, request: String, minutes: Int): String {
        val s = parse(stateJson)
        val tasks = WebNotes.tasks(fileKey, path, text)
        val plan = Planner.plan(request, tasks, events(s), Profile.parse(s.profile), LocalDateTime.parse(s.now), minutes.takeIf { it > 0 })
        return json.encodeToString(
            PlanOut(
                plan.request, plan.title, plan.kind.name, plan.kind.label, plan.minutes, plan.intro,
                plan.slots.map { SlotOut(it.day.toString(), it.dayLabel, hm(it.start), hm(it.end), it.title, it.why, it.score) },
            ),
        )
    }

    @Serializable
    data class Ranked(val key: String, val title: String, val reason: String)

    /** Every open task in the order it should be done, the first twelve, each with its reason. */
    fun rank(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = parse(stateJson)
        val today = LocalDateTime.parse(s.now).date
        val tasks = WebNotes.tasks(fileKey, path, text)
        return json.encodeToString(Focus.rank(tasks, today).take(12).map { (t, why) -> Ranked(t.key, t.title, why) })
    }

    /** Today's list: must-dos, then people waiting, then future work, each with why it is there. */
    fun today(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = parse(stateJson)
        val today = LocalDateTime.parse(s.now).date
        val tasks = WebNotes.tasks(fileKey, path, text)
        val b = Focus.build(tasks, today, s.futureCount, s.skippedToday.toSet())
        val list = (b.must + b.waiting + b.future).distinct()
        return json.encodeToString(list.map { t -> Ranked(t.key, t.title, reason(t, b, today)) })
    }

    private fun reason(t: Task, b: Focus.Brief, today: LocalDate): String = when {
        t.due?.let { it < today } == true -> tr("เลยกำหนดแล้ว", "Overdue")
        t.due == today -> tr("ครบวันนี้", "Due today")
        t.scheduled == today -> tr("นัดทำวันนี้", "Scheduled today")
        t in b.waiting -> {
            val who = Focus.waitingFor(t)
            val age = Focus.ageDays(t, today)
            tr((who ?: "มีคน") + " รอ " + (age?.let { "$it วัน" } ?: "อยู่"), (who ?: "Someone") + " waiting" + (age?.let { " $it days" } ?: ""))
        }
        t in b.future -> tr("ลงทุนอนาคต ไม่มีเดดไลน์แต่สำคัญ", "Future work: no deadline, but it matters")
        else -> ""
    }

    @Serializable
    data class AgendaEvent(val title: String, val allDay: Boolean, val time: String?, val link: String? = null)

    @Serializable
    data class AgendaTask(val key: String, val title: String, val due: Boolean)

    @Serializable
    data class AgendaDay(val day: String, val events: List<AgendaEvent>, val tasks: List<AgendaTask>)

    @Serializable
    data class AgendaOut(val events: Int, val due: Int, val busiest: String?, val days: List<AgendaDay>)

    /** What is coming from [from] to [to] (inclusive), day by day, from both the note and the calendar; empty days are left out. */
    fun agenda(fileKey: String, path: String, text: String, stateJson: String, from: String, to: String): String {
        val s = parse(stateJson)
        val a = LocalDate.parse(from)
        val b = LocalDate.parse(to)
        val tasks = WebNotes.tasks(fileKey, path, text).filter { it.status != Status.CANCELLED && !Focus.isSomeday(it) }
        val all = events(s).filter { it.end > a.atStartOfDay() && it.begin < b.plusDays(1).atStartOfDay() }
        val days = generateSequence(a) { it.plusDays(1) }.takeWhile { it <= b }.map { d ->
            val evs = all.filter { it.begin < d.plusDays(1).atStartOfDay() && it.end > d.atStartOfDay() }.sortedBy { it.begin }
            val ts = tasks.filter { t -> t.isOpen && (t.due == d || t.scheduled == d) }.sortedBy { it.priority.ordinal }
            d to (evs to ts)
        }.toList()
        val busiest = days.maxByOrNull { (_, v) -> v.first.count { !it.allDay } + v.second.size }
            ?.takeIf { (_, v) -> v.first.size + v.second.size > 2 }?.first
        val due = days.sumOf { (d, v) -> v.second.count { it.due == d } }
        val links = s.events.filter { it.link.isNotEmpty() }.associate { it.id to it.link }
        val out = days.filter { (_, v) -> v.first.isNotEmpty() || v.second.isNotEmpty() }.map { (d, v) ->
            AgendaDay(
                d.toString(),
                v.first.map { AgendaEvent(it.title, it.allDay, if (it.allDay) null else hm(it.begin.time), links[it.id]) },
                v.second.map { AgendaTask(it.key, it.title, it.due == d) },
            )
        }
        return json.encodeToString(AgendaOut(all.size, due, busiest?.toString(), out))
    }

    @Serializable
    data class Proposal(val key: String, val title: String, val raw: String, val lineIndex: Int, val day: String, val start: String, val minutes: Int)

    /**
     * Undated work placed into the free time of [from] to [to], most urgent first, around events and shifts. Nothing is
     * written: the page offers each proposal, and [scheduleTasks] writes the ones accepted.
     */
    fun planRange(fileKey: String, path: String, text: String, stateJson: String, from: String, to: String): String {
        val s = parse(stateJson)
        val now = LocalDateTime.parse(s.now)
        val today = now.date
        val a = LocalDate.parse(from)
        val b = LocalDate.parse(to)
        val tasks = WebNotes.tasks(fileKey, path, text)
        val candidates = Focus.rank(tasks, today).map { it.first }.filter { t ->
            t.scheduled.let { it == null || it < today } && t.due.let { it == null || it >= a } && t.reminderTime == null
        }.take(12)
        val days = generateSequence(if (a > today) a else today) { it.plusDays(1) }.takeWhile { it <= b }.toList()
        val proposals = Planner.planRange(candidates, days, tasks, events(s), Profile.parse(s.profile), now)
        return json.encodeToString(proposals.map { Proposal(it.task.key, it.task.title, it.task.raw, it.task.lineIndex, it.slot.day.toString(), hm(it.slot.start), it.minutes) })
    }

    @Serializable
    data class ReviewOut(val title: String, val lines: List<String>)

    /** The last seven days in three lines. */
    fun weekly(fileKey: String, path: String, text: String, stateJson: String): String {
        val today = LocalDateTime.parse(parse(stateJson).now).date
        val m = Digest.weekly(WebNotes.tasks(fileKey, path, text), today)
        return json.encodeToString(ReviewOut(m.title, m.lines))
    }

    /** A plain-text picture of today for Claude: the profile, the timeline and the open list (what Android hands to the Claude app). */
    fun snapshot(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = parse(stateJson)
        val today = LocalDateTime.parse(s.now).date
        val tasks = WebNotes.tasks(fileKey, path, text)
        val profile = Profile.parse(s.profile)
        val todayEvents = events(s).filter { it.begin < today.plusDays(1).atStartOfDay() && it.end > today.atStartOfDay() }
        return buildString {
            appendLine("วันนี้ $today")
            if (profile.exists) {
                appendLine("[โปรไฟล์]")
                appendLine(profile.render().lines().filter { it.startsWith("- ") }.joinToString("\n"))
            }
            DayPlan.build(tasks, todayEvents, today).forEach { sec ->
                appendLine("[${sec.part.label}]")
                sec.items.forEach { item ->
                    when (item) {
                        is DayPlan.Item.TaskItem -> appendLine("- งาน: ${item.task.raw.trim()}")
                        is DayPlan.Item.EventItem ->
                            appendLine("- นัด: ${item.event.title} ${if (item.event.allDay) "ทั้งวัน" else hm(item.event.begin.time) + " ถึง " + hm(item.event.end.time)}")
                    }
                }
            }
            appendLine("[งานที่ยังไม่เสร็จทั้งหมด]")
            tasks.filter { it.isOpen }.take(60).forEach { appendLine(it.raw.trim()) }
        }
    }

    // ---- Writing to the note ----

    /** A new task line for a day and an optional time, in TaskForge token order (Android's `Planner.taskLine`, or an all-day line). */
    fun slotLine(title: String, day: String, start: String?, today: String): String {
        val clean = title.replace(Regex("""\s+"""), " ").trim()
        val d = LocalDate.parse(day)
        val t = LocalDate.parse(today)
        return if (start != null) {
            val time = LocalTime.parse(start)
            Planner.taskLine(clean, Planner.Slot(d, time, time, "", "", 0), t)
        } else {
            "- [ ] $clean ➕ $t ⏳ $d"
        }
    }

    /** The note with [linesJson] added at the end, one task per line. */
    fun addLines(text: String, linesJson: String): String {
        val lines = json.decodeFromString<List<String>>(linesJson)
        if (lines.isEmpty()) return json.encodeToString(WebCore.EditResult(false, error = "empty"))
        return json.encodeToString(WebCore.EditResult(true, lines.fold(text) { acc, l -> VaultText.appendLine(acc, l) }))
    }

    @Serializable
    data class SweepOut(val text: String, val archive: String, val titles: List<String>)

    /**
     * The daily archive sweep (Settings: "ย้ายเข้าคลังอัตโนมัติหลัง N วัน"): finished top-level tasks closed [days] or more
     * days before [today] leave the live note for the archive note, project work excepted. `null` when nothing is to move.
     */
    fun sweep(live: String, archive: String, days: Int, today: String): String {
        val day = LocalDate.parse(today)
        val swept = app.omnitask.data.Archive.sweep(live, day.minusDays(days.toLong())) { app.omnitask.model.Projects.projectOf(it) != null } ?: return "null"
        val next = app.omnitask.data.Archive.append(archive.ifEmpty { null }, swept.blocks, day)
        return json.encodeToString(SweepOut(swept.text, next, swept.titles))
    }

    @Serializable
    data class ScheduleIn(val raw: String, val lineIndex: Int, val day: String, val time: String)

    @Serializable
    data class ScheduleOut(val ok: Boolean, val text: String? = null, val done: List<Boolean> = emptyList())

    /**
     * Puts the accepted proposals into the note: ⏳ the day and the reminder time on each task (Android's "ลงแผน").
     * A task whose line has changed elsewhere since it was read is left alone and reported as not done.
     */
    fun scheduleTasks(text: String, itemsJson: String): String {
        val items = json.decodeFromString<List<ScheduleIn>>(itemsJson)
        val separator = VaultText.separatorOf(text)
        val lines = text.split(separator).toMutableList()
        val done = items.map { item ->
            val i = if (lines.getOrNull(item.lineIndex) == item.raw) item.lineIndex else lines.indexOf(item.raw)
            if (i < 0) {
                false
            } else {
                val dated = TaskLine.setDate(lines[i], TaskLine.DateField.SCHEDULED, LocalDate.parse(item.day))
                lines[i] = TaskLine.setReminder(dated, LocalTime.parse(item.time), ReminderOn.SCHEDULED)
                true
            }
        }
        return json.encodeToString(ScheduleOut(done.any { it }, lines.joinToString(separator), done))
    }
}
