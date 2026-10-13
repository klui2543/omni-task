package app.omnitask.web

import app.omnitask.data.VaultText
import app.omnitask.model.Countdown
import app.omnitask.model.DayPlan
import app.omnitask.model.Focus
import app.omnitask.model.Profile
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.TaskKind
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import app.omnitask.time.ChronoUnit
import app.omnitask.time.NOON
import app.omnitask.time.atTime
import app.omnitask.time.hhmm
import app.omnitask.time.minusDays
import app.omnitask.time.of
import app.omnitask.time.toLocalDate
import app.omnitask.time.toLocalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The Focus page for the web: the same brief, day plan, suggestions and review queue as the Android screen,
 * built by the same code ([Focus], [DayPlan], [Countdown]). What the owner chose on this device (skipped,
 * dismissed, reviewed, countdown...) and the calendar events come in as text, and the page comes out as JSON.
 */
object WebFocus {

    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    @Serializable
    data class EventIn(
        val id: Long = 0, val title: String, val begin: String, val end: String, val allDay: Boolean = false,
        /** The event's page in Google Calendar (`htmlLink`), or empty. [id] must be unique among the events given. */
        val link: String = "",
    )

    /** Tonight's bedtime when it differs from the usual one: the evening it starts on and the time. */
    @Serializable
    data class BedIn(val evening: String, val time: String)

    @Serializable
    data class StateIn(
        /** The device's own clock as local `yyyy-MM-ddTHH:mm`. */
        val now: String,
        val skippedToday: List<String> = emptyList(),
        val dismissed: List<String> = emptyList(),
        /** Review dates by task title. */
        val reviewed: Map<String, String> = emptyMap(),
        val futureCount: Int = 1,
        val countdown: String? = null,
        val tonightBed: BedIn? = null,
        /** Tonight's wake time when it is not the usual one (a morning event no longer moves it). */
        val tonightWake: BedIn? = null,
        /** Tonight's wake time was set on the watch (Android's `sleep.wake.watch`, through the settings file). */
        val wakeFromWatch: Boolean = false,
        /** The text of the profile note (`Omni/โปรไฟล์.md`), when the vault has one. */
        val profile: String? = null,
        val events: List<EventIn> = emptyList(),
        /** Branch states as Android saves them; tasks in a parked branch are left out. */
        val branches: List<String> = emptyList(),
    )

    @Serializable
    data class PlanItem(
        /** `task`, `event` or `now` (the red line at the current time). */
        val type: String,
        val time: String? = null,
        val key: String? = null,
        val title: String? = null,
        val lead: String? = null,
        val late: Boolean = false,
        val extra: String? = null,
        val blocked: Boolean = false,
        val range: String? = null,
        /** For an event: its page in Google Calendar. */
        val link: String? = null,
        /** For a task: already ticked off, kept in the plan to be greyed. */
        val done: Boolean = false,
        /** For an event: `past`, `active` (the clock is inside it) or `upcoming`. */
        val phase: String? = null,
        /** For an active event: how far into it the clock is, 0 to 1, where the red line crosses its card. */
        val progress: Float = 0f,
    )

    @Serializable
    data class Section(val label: String, val late: Boolean, val tasks: Int, val items: List<PlanItem>)

    @Serializable
    data class Ring(val done: Int, val total: Int, val overdue: Int, val events: Int)

    @Serializable
    data class Night(
        val bedAt: String,
        val wakeAt: String,
        val toBed: Long,
        val sleep: Long,
        val because: String?,
        /** When the event that pulls the wake time earlier begins, `HH:mm`. */
        val becauseAt: String? = null,
        /** The wake time was picked for tonight. */
        val wakePicked: Boolean = false,
        /** The picked wake time came from the watch. */
        val fromWatch: Boolean = false,
        /** The wake time is tomorrow's (the night is planned ahead, in the daytime). */
        val tomorrow: Boolean = false,
    )

    /**
     * The summary's third number: until bed at night (`night`), the free time left in the daytime (`free`). The night
     * the sleep card plans is there all day long: last night's until its wake time, then tonight's.
     */
    @Serializable
    data class Third(val kind: String, val minutes: Long, val night: Night? = null)

    @Serializable
    data class CountdownOut(val key: String, val title: String, val text: String, val late: Boolean)

    @Serializable
    data class SuggestionOut(val id: String, val key: String, val kind: String, val title: String, val text: String)

    @Serializable
    data class WaitingOut(val key: String, val who: String?, val age: Long?)

    @Serializable
    data class ReviewOut(val key: String, val kind: String, val age: Long?, val last: String?)

    @Serializable
    data class Pick(val key: String, val title: String, val sub: String)

    @Serializable
    data class Out(
        val ring: Ring,
        val third: Third,
        val countdown: CountdownOut?,
        val notices: List<String>,
        val plan: List<Section>,
        val suggestions: List<SuggestionOut>,
        val waiting: List<WaitingOut>,
        val future: List<String>,
        val futureAge: Map<String, Long>,
        val futureChosen: List<Pick>,
        val futureCandidates: List<Pick>,
        val review: List<ReviewOut>,
        val countdownChoices: List<Pick>,
        /** The usual bedtime or tonight's pick, as `HH:mm`, and the evening it counts for. */
        val bedtime: String,
        val evening: String,
        /** The usual wake time or tonight's pick, `HH:mm`. */
        val wake: String = "",
        /** The coming Saturday: where neglected future work and "do this Saturday" are scheduled. */
        val softDate: String,
        /** Until noon after a night: the minutes of sleep it gave, from its planned bedtime. */
        val lastNight: Long? = null,
    )

    private fun hm(t: LocalTime) = hhmm(t.hour, t.minute)

    /** The focus page for the note at [text], as JSON. */
    fun build(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = json.decodeFromString<StateIn>(stateJson)
        val now = LocalDateTime.parse(s.now)
        val today = now.date
        val tasks = WebCore.withoutParked(WebNotes.tasks(fileKey, path, text, today), s.branches)
        val profile = Profile.parse(s.profile)
        val events = s.events.map { CalendarEvent(it.id, it.title, LocalDateTime.parse(it.begin), LocalDateTime.parse(it.end), it.allDay) }
        val todayEvents = events.filter { it.begin.date <= today && it.end.date >= today && it.end > today.atTime(0, 0) }
        val reviewed = s.reviewed.mapNotNull { (k, v) -> runCatching { LocalDate.parse(v) }.getOrNull()?.let { k to it } }.toMap()

        val brief = Focus.build(tasks, today, s.futureCount, s.skippedToday.toSet(), s.dismissed.toSet())
        val plan = DayPlan.build(tasks, todayEvents, today, includeDone = true)
        val blocked = Projects.blocked(tasks)
        val progress = tasks.filter { it.parent != null && it.status != Status.CANCELLED }.groupBy { it.parent!! }
            .mapValues { (_, kids) -> kids.count { it.status == Status.DONE } to kids.size }

        // Done today counts anything ticked today that belonged to today (or was overdue), as on Android.
        val openToday = plan.flatMap { it.items }.count { it is DayPlan.Item.TaskItem && it.task.isOpen }
        val doneToday = tasks.count { t -> t.done == today && (t.due ?: t.scheduled)?.let { it <= today } == true }
        val overdue = brief.must.count { t -> t.due?.let { it < today } == true }

        // The night the sleep card plans, all day long: last night until its wake time, then tonight's (as Android's
        // Focus screen). Bed and wake are the ones picked for that evening, or the usual ones from the profile.
        val yesterday = today.minusDays(1)
        fun pickOf(b: BedIn?, evening: LocalDate) =
            b?.takeIf { it.evening == evening.toString() }?.let { runCatching { LocalTime.parse(it.time) }.getOrNull() }
        fun bedOf(evening: LocalDate) = pickOf(s.tonightBed, evening) ?: profile.sleep
        fun wakeOf(evening: LocalDate) = pickOf(s.tonightWake, evening)
        val clock = now.time
        val eve = if (clock < LocalTime.of(18, 0) && clock < (wakeOf(yesterday) ?: profile.wake)) yesterday else today
        // From 18:00 until the wake time it is night: the summary counts down to bed instead of the free time left.
        val isNight = eve == yesterday || clock >= LocalTime.of(18, 0)
        val bedtime = bedOf(eve)
        val pickedWake = wakeOf(eve)
        val night = DayPlan.night(events, profile, now, bedtime, pickedWake, evening = eve)
        // Until noon after a night: how long it gave, from its planned bedtime.
        val lastNight = if (eve == today && clock < LocalTime.NOON) {
            val bed = bedOf(yesterday)
            val at = if (bed >= LocalTime.NOON) yesterday.atTime(bed) else today.atTime(bed)
            DayPlan.night(events, profile, at, bed, wakeOf(yesterday), evening = yesterday)?.sleep
        } else {
            null
        }
        val nightOut = night?.let {
            Night(
                hm(it.bedAt.time), hm(it.wakeAt.time), it.toBed, it.sleep, it.because?.title, it.because?.let { e -> hm(e.begin.time) },
                wakePicked = pickedWake != null, fromWatch = pickedWake != null && s.wakeFromWatch, tomorrow = it.wakeAt.date > today,
            )
        }
        val third = if (night != null && isNight) {
            Third("night", night.toBed, nightOut)
        } else {
            Third("free", DayPlan.freeMinutesLeft(events, now, profile.wake, profile.sleep), nightOut)
        }

        val countdownTask = s.countdown?.let { title -> tasks.filter { it.title == title }.maxByOrNull { if (it.isOpen) 1 else 0 } }
        val countdown = countdownTask?.let {
            val late = it.due?.let { d -> d < today } == true
            CountdownOut(it.key, it.title, Countdown.text(it, now) ?: tr("ไม่มีวันครบกำหนด", "no date"), late)
        }

        return json.encodeToString(
            Out(
                ring = Ring(doneToday, openToday + doneToday, overdue, todayEvents.count { !it.allDay }),
                third = third,
                countdown = countdown,
                notices = brief.warnings,
                plan = planOut(plan, today, now, blocked, progress, s.events.filter { it.link.isNotEmpty() }.associate { it.id to it.link }),
                suggestions = brief.suggestions.map { SuggestionOut(it.id, it.task.key, it.kind.name, it.task.title, it.text) },
                waiting = brief.waiting.map { WaitingOut(it.key, Focus.waitingFor(it), Focus.ageDays(it, today)) },
                future = brief.future.map { it.key },
                futureAge = brief.future.mapNotNull { t -> Focus.ageDays(t, today)?.let { t.key to it } }.toMap(),
                futureChosen = tasks.filter { it.isOpen && Focus.isFutureWork(it) }.map { pick(it) },
                futureCandidates = tasks.filter { it.isOpen && it.due == null && !Focus.isWaiting(it) && !Focus.isFutureWork(it) }
                    .sortedWith(compareBy<Task>({ it.priority.ordinal }, { it.created })).map { pick(it) },
                review = Focus.toReview(tasks, today, reviewed).map {
                    ReviewOut(it.key, TaskKind.of(it).name, Focus.ageDays(it, today), reviewed[it.title]?.toString())
                },
                countdownChoices = tasks.filter { it.isOpen && Countdown.target(it) != null }.sortedBy { Countdown.target(it) }
                    .map { Pick(it.key, it.title, Countdown.text(it, now) ?: "") },
                bedtime = hm(bedtime),
                evening = eve.toString(),
                wake = hm(pickedWake ?: profile.wake),
                softDate = Focus.softDate(today).toString(),
                lastNight = lastNight,
            ),
        )
    }

    private fun pick(t: Task) = Pick(
        t.key, t.title,
        listOfNotNull(if (Focus.isSomeday(t)) tr("พักไว้", "Someday") else null, t.noteName.takeIf { it.isNotEmpty() }).joinToString(", "),
    )

    /**
     * The day plan with its red "now" line placed where Android puts it: before the first timed item still to come,
     * across the event that is running (the line is then that event's [PlanItem.progress]), or at the end of the
     * timed items when none is left. Tasks ticked today and events that are over stay in the plan, marked for greying.
     */
    private fun planOut(
        plan: List<DayPlan.Section>, today: LocalDate, now: LocalDateTime, blocked: Set<Task>, progress: Map<String, Pair<Int, Int>>,
        links: Map<Long, String> = emptyMap(),
    ): List<Section> {
        val nowTime = now.time
        val inEvent = plan.any { sec -> sec.items.any { it is DayPlan.Item.EventItem && DayPlan.phase(it.event, now) == DayPlan.Phase.ACTIVE } }
        var nowShown = inEvent || plan.none { it.part != DayPlan.Part.LATE && it.part != DayPlan.Part.ANYTIME && it.items.any { i -> i.time != null } }
        val out = ArrayList<Pair<DayPlan.Section, ArrayList<PlanItem>>>()
        plan.forEach { section ->
            // Untimed work comes after the timed day, so a line not yet placed ends the section before it.
            if (!nowShown && section.part == DayPlan.Part.ANYTIME && out.isNotEmpty()) {
                out.last().second += PlanItem("now", hm(nowTime))
                nowShown = true
            }
            val items = ArrayList<PlanItem>()
            section.items.forEach { item ->
                val t = item.time
                if (!nowShown && t != null && t > nowTime) {
                    items += PlanItem("now", hm(nowTime))
                    nowShown = true
                }
                items += when (item) {
                    is DayPlan.Item.TaskItem -> taskItem(item, today, item.task in blocked, progress)
                    is DayPlan.Item.EventItem -> {
                        val e = item.event
                        val phase = DayPlan.phase(e, now)
                        val range = DayPlan.range(e, item.time != null, today) { hm(it) }
                        // Every event is on today's page and from the calendar, so only the one that is on says more.
                        val tail = if (phase == DayPlan.Phase.ACTIVE) {
                            val left = ChronoUnit.MINUTES.between(now, e.end)
                            tr(", เหลือ $left นาที", ", $left min left")
                        } else {
                            ""
                        }
                        PlanItem(
                            "event", item.time?.let { hm(it) }, title = e.title, range = range + tail, link = links[e.id],
                            phase = phase.name.lowercase(), progress = if (phase == DayPlan.Phase.ACTIVE) DayPlan.progress(e, now) else 0f,
                        )
                    }
                }
            }
            if (!nowShown && section.part == DayPlan.Part.EVENING) {
                items += PlanItem("now", hm(nowTime))
                nowShown = true
            }
            out += section to items
        }
        if (!nowShown && out.isNotEmpty()) out.last().second += PlanItem("now", hm(nowTime))
        return out.map { (section, items) ->
            Section(section.part.label, section.part == DayPlan.Part.LATE, section.items.count { it is DayPlan.Item.TaskItem && it.task.isOpen }, items)
        }
    }

    private fun taskItem(item: DayPlan.Item.TaskItem, today: LocalDate, blocked: Boolean, progress: Map<String, Pair<Int, Int>>): PlanItem {
        val t = item.task
        val late = t.isOpen && t.due?.let { it < today } == true
        val done = t.status == Status.DONE
        // Every row is today's, so "due today" would only repeat: a lead is for overdue work only, and a finished task is a title.
        val lead = if (late && !done) t.due?.let { tr("เลย ${ChronoUnit.DAYS.between(it, today)} วัน", "${ChronoUnit.DAYS.between(it, today)} days late") } else null
        val sub = if (done) emptyList() else listOfNotNull(
            if (t.status == Status.IN_PROGRESS) tr("กำลังทำ", "In progress") else null,
            Projects.projectOf(t),
            progress[t.key]?.let { (d, n) -> tr("งานย่อย $d/$n", "Subtasks $d/$n") },
        )
        return PlanItem(
            "task", item.time?.let { hm(it) }, t.key, t.title, lead, late,
            if (sub.isEmpty()) null else sub.joinToString(", "), blocked,
            done = done,
        )
    }
}
