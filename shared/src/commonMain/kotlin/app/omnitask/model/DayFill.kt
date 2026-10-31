package app.omnitask.model

import app.omnitask.data.TaskLine
import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.omnitask.time.*

/**
 * The secretary's plan for the rest of today (step 2, the owner's picks of 2026-10-29, mock v12): the real free time
 * left after the day job, the calendars and the shifts, filled with what matters, and a third of it kept clear as a
 * buffer. It is made again from scratch at every change (an errand, a new event, a new urgent task, a late start), so
 * nothing in it is rigid: work moves to the next free time, the owner's own time shrinks to its shortest only when work
 * that must be done would not fit, and what does not fit today says so.
 *
 * Rules the owner set: at most two things that must be done a day, none the day after a night shift (a recovery day,
 * where nothing put off counts); myspace (tags under #My-space) starts on time; work never reviewed passes the review
 * card first; a weekly sprint of at most two projects gets one slot a day each; a task moved three times brings a
 * question (split it, keep it for later, let it go). Night shifts are the owner's main income: before one the plan is
 * light and stops early, after one it rests; it never treats a shift as a problem.
 *
 * Times are minutes from the start of [Plan.day]; after midnight they run past 1440.
 */
object DayFill {

    // ---- What the owner did, kept on the phone ----

    enum class Pick { TAKE, TIRED, LATER, DROP, SPLIT, SKIP }

    @Serializable
    data class Errand(val from: Int, val to: Int? = null)

    /**
     * A slot the owner took: the day and time the plan wrote as ⏳ (and as 🎯 when [timed]; a task with a reminder of
     * its own keeps it, so only ⏳ is written). [pinned]: the owner put it at [at] themselves (a suggested time or a
     * drag): it stays there even on top of something else, and nothing else is pushed for it. [len]: its length when
     * taken, for the reminder's "เหลืออีก N ถึงจะทำทันวันนี้" (0 when taken before that was kept: the reminder is as before).
     */
    @Serializable
    data class Accepted(val day: String, val at: Int, val timed: Boolean = true, val pinned: Boolean = false, val len: Int = 0)

    @Serializable
    data class Line(val before: String, val after: String)

    @Serializable
    data class SprintPick(val week: String, val projects: List<String>, val taken: Boolean = false, val swap: Int = 0, val takenOn: String? = null)

    /**
     * Everything the plan remembers on this phone: today's taps ([day] and the maps after it, cleared on a new day) and
     * what lasts longer: taken slots, the days a task was put off with "ช่วงนี้ไม่ว่าง" (a move; once a day each), the
     * last day it got "วันนี้หมดแรง" (not a move: it only comes back first the next day), answered questions, and the
     * week's sprint.
     */
    @Serializable
    data class Memory(
        val day: String = "",
        val picks: Map<String, Pick> = emptyMap(),
        val swaps: Map<String, Int> = emptyMap(),
        val notBefore: Map<String, Int> = emptyMap(),
        val errand: Errand? = null,
        val accepted: Map<String, Accepted> = emptyMap(),
        val pushed: Map<String, List<String>> = emptyMap(),
        val asked: Map<String, String> = emptyMap(),
        val sprint: SprintPick? = null,
        val tired: Map<String, String> = emptyMap(),
        /**
         * Slots taken today ("net", "mine", "sprint:<project>") and the task that took each ([ident]): a slot taken once
         * keeps that task and is not offered again with another.
         */
        val slots: Map<String, String> = emptyMap(),
        /**
         * Lines the plan wrote, by card: what each was before (to put back when the owner taps the card again) and what
         * the plan wrote last. A line that no longer reads as written last was changed by the owner: the plan leaves it.
         */
        val lines: Map<String, Line> = emptyMap(),
        /** When today's plan stops (minutes from the day's start), as the today page last saw it: the reminder counts to it. */
        val stop: Int? = null,
        /**
         * "ยังไม่ได้ทำ" on a taken slot gone by, today: the task ([ident]) and where its slot started when it was tapped.
         * The look-ahead's own-time record reads it; a slot put at a new time is judged afresh there.
         */
        val notYet: Map<String, Int> = emptyMap(),
        /**
         * Today's events the owner called "ว่าง ทำงานได้" ([freeKey]s, owner's pick ก+ข, 2026-10-31): the plan does not count
         * their hours as busy. Today only: it is gone on a new day.
         */
        val freeEvents: Set<String> = emptySet(),
    ) {
        /** The same memory on [today]: yesterday's taps go, what lasts stays (put-off days for 30 days). */
        fun on(today: LocalDate): Memory {
            if (day == today.toString()) return this
            val cut = today.minusDays(30).toString()
            val kept = accepted.filterValues { it.day >= today.minusDays(1).toString() }
            return Memory(
                day = today.toString(),
                accepted = kept,
                pushed = pushed.mapValues { (_, d) -> d.filter { it >= cut } }.filterValues { it.isNotEmpty() },
                asked = asked.filterValues { it >= cut },
                sprint = sprint,
                tired = tired.filterValues { it >= today.minusDays(1).toString() },
                // A taken slot's line stays known while the slot does (one moved to tomorrow is still followed there).
                lines = lines.filterKeys { it in kept },
            )
        }

        fun pushedOn(ident: String, day: LocalDate) = pushed[ident].orEmpty().contains(day.toString())

        fun withPush(ident: String, day: LocalDate): Memory {
            val days = pushed[ident].orEmpty()
            return if (day.toString() in days) this else copy(pushed = pushed + (ident to days + day.toString()))
        }
    }

    /** How an event is known to [Memory.freeEvents]: a repeating event shares its id, so its start is part of it. */
    fun freeKey(e: CalendarEvent): String = "${e.id}@${e.begin}"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(m: Memory): String = json.encodeToString(Memory.serializer(), m)

    /** The memory kept on the phone: empty when nothing is stored, null when what is stored cannot be read. */
    fun decode(text: String?): Memory? = if (text.isNullOrBlank()) Memory() else runCatching { json.decodeFromString(Memory.serializer(), text) }.getOrNull()

    /** How a task is known across reloads (its line number moves when lines are added above it). */
    fun ident(t: Task): String = t.noteName + "\u0001" + t.title.trim()

    /**
     * What the plan writes on a line and follows: ⏳ and the reminder. The rest of the line is the owner's (or another
     * part of the app's), so a change there is not the owner taking the slot back.
     */
    data class Marks(val scheduled: LocalDate?, val time: LocalTime?, val on: ReminderOn?)

    fun marks(t: Task) = Marks(t.scheduled, t.reminderTime, t.reminderOn)

    fun marks(raw: String): Marks? = TaskLine.parse(raw)?.let(::marks)

    /** [raw] with the ⏳ and reminder of [to] put back; everything else stays as the line reads now. */
    fun withMarks(raw: String, to: Marks): String {
        val dated = TaskLine.setDate(raw, TaskLine.DateField.SCHEDULED, to.scheduled)
        val now = marks(dated)
        return if (now != null && now.time == to.time && (to.time == null || now.on == to.on)) dated
        else TaskLine.setReminder(dated, to.time, to.on ?: ReminderOn.SCHEDULED)
    }

    // ---- The plan ----

    enum class Kind(private val th: String, private val en: String) {
        LIFE("ชีวิต", "Life"),
        MINE("myspace", "myspace"),
        URGENT("งานด่วนที่เพิ่งเข้ามา", "New and urgent"),
        DUE("ใกล้ส่งแล้ว", "Due soon"),
        AGAIN("ต่อจากเมื่อวาน", "From yesterday"),
        ASK("เลขาขอถาม", "A question"),
        NET("งาน", "Work"),
        WORK("งาน", "Work"),
        PROJECT("ทำต่อโปรเจกต์", "Project"),
        REVIEW("ยังไม่ได้ทบทวน", "Not reviewed"),
        ;

        val label get() = tr(th, en)
    }

    enum class Mode { NORMAL, BEFORE_NIGHT, AFTER_NIGHT }

    /** The look-ahead's tags (mock look-ahead v2): [WANT] the owner's own, [HEAVY] a tight week or a deadline, [LIGHT] a light week. */
    enum class Tone { WANT, HEAVY, LIGHT }

    /** A short tag on a row or line; a tap shows [why] (owner, 2026-10-30: "still too textful"). */
    data class Hint(val label: String, val why: String, val tone: Tone)

    /** One thing the plan places. [must] work is placed whatever the buffer; other work only while a third stays clear. */
    data class Item(
        val id: String,
        val kind: Kind,
        val prio: Double,
        val len: Int,
        val title: String,
        val why: String,
        val task: Task? = null,
        val min: Int? = null,
        val from: Int? = null,
        val to: Int? = null,
        val must: Boolean = false,
        val tag: String? = null,
        val lateText: String? = null,
        /** Taken from this plan earlier: it keeps this time while it still can. */
        val fixedAt: Int? = null,
        /** The owner's own reminder time today: it never moves. */
        val own: Boolean = false,
        /** Other things the owner can swap to ("ไม่ใช่ เปลี่ยนให้"); zero when there is none. */
        val more: Int = 0,
        /** The slot this card fills ("net", "mine", "sprint:<project>"): what "ไม่ใช่ เปลี่ยนให้" swaps, and what a take uses up. */
        val slot: String? = null,
        /** Tags from the look-ahead (switch on only); empty otherwise. */
        val hints: List<Hint> = emptyList(),
    ) {
        val movable get() = !own && kind != Kind.LIFE && kind != Kind.REVIEW && kind != Kind.ASK
    }

    data class Placed(val item: Item, val at: Int, val len: Int, val short: Boolean, val running: Boolean, val pinned: Boolean = false)

    data class Later(val item: Item, val why: String, val risk: Boolean, val picked: Pick?)

    sealed class Row {
        abstract val at: Int?

        /** A block the plan works around: the day job, an event, a shift, sleep, an errand. */
        data class Locked(override val at: Int?, val title: String, val why: String, val event: CalendarEvent? = null, val past: Boolean = false, val until: Int? = null, val free: Boolean = false) : Row()

        /** Free time left clear on purpose. */
        data class Gap(override val at: Int, val len: Int) : Row()

        data class Card(override val at: Int, val placed: Placed, val picked: Pick?) : Row()

        /** A task already ticked today. */
        data class Done(override val at: Int?, val task: Task) : Row()
    }

    /** A taken slot's line to write again: ⏳ [day] and, when the plan set the reminder, 🎯 at [at]. [card] keys its line. */
    data class Move(val task: Task, val at: Int?, val day: LocalDate, val card: String)

    /** The week's sprint line; [taken]: taken today, it shows "รับแล้ว" in place of the buttons. */
    data class SprintLine(
        val names: String, val note: String, val late: Boolean, val lines: List<String>, val projects: List<String>, val taken: Boolean = false,
        /** The look-ahead's tag for the line (switch on only): the line then shows in one row. */
        val hint: Hint? = null,
        /** With the tag, a deadline at risk names its task in that row ("สไลด์สอนน้องใหม่ ส่ง 27 พ.ย."), as mock v2 board ง. */
        val lead: String? = null,
    )

    data class Plan(
        val day: LocalDate,
        val start: Int,
        val end: Int,
        val mode: Mode,
        val rows: List<Row>,
        val later: List<Later>,
        val free: Int,
        val planned: Int,
        val buffer: Int,
        val banner: String?,
        val sprint: SprintLine?,
        /** Taken slots whose time the plan changed: their 🎯 (or ⏳, when today has no room) is written again. */
        val moves: List<Move>,
        /** Taken slots whose line the owner changed since the plan wrote it ([ident]s): the plan lets go of them. */
        val letGo: List<String>,
        /** The first work card, shown open. */
        val next: String?,
        /** Bedtime (minutes from the day's start): a task the owner drags is placed before it. */
        val bed: Int = end,
        /** The free stretches from [start] to [end] that no block or card takes (any length): where a task can be put. */
        val open: List<Pair<Int, Int>> = emptyList(),
    ) {
        val placed: List<Placed> get() = rows.filterIsInstance<Row.Card>().map { it.placed }
    }

    class Input(
        val tasks: List<Task>,
        val events: List<CalendarEvent>,
        val profile: Profile,
        val facts: RealDay.Facts,
        val now: LocalDateTime,
        val memory: Memory = Memory(),
        /** Times a task was moved to a later day ([Learned.movesLater] over the behaviour log). */
        val moves: (Task) -> Int = { 0 },
        /**
         * The watch's sleeps of the last [SleepTimes.DAYS] days (Settings switch, off at first): null while it is off,
         * and then the profile's wake and bed times stand as before; empty when it is on and nothing came in yet.
         */
        val sleeps: List<SleepSession>? = null,
        /** Why the watch could not be read (no Health Connect, no permission, an error), said on the plan's rows. */
        val sleepProblem: String? = null,
        /** The look-ahead (Settings switch "เลขามองไกล 8 สัปดาห์", off at first; see [LookAhead]). */
        val ahead: Boolean = false,
        /**
         * The look-ahead's own read of the calendar: two months back (the shifts that estimate a month not posted yet)
         * to eight weeks ahead (the plan's [events] reach about five). Null: the look-ahead counts from [events].
         */
        val far: List<CalendarEvent>? = null,
        /**
         * Own time by day as this phone kept it (look-ahead rule 4, owner's pick ก of 2026-10-30): the minutes the plan
         * offered and the minutes the owner did. Null: not kept (the switch off).
         */
        val own: Map<LocalDate, LookAhead.OwnDay>? = null,
    )

    /** Lengths and windows the owner saw in the mock. */
    private const val CLEAR = 15
    private const val STEP = 5
    const val NET_DAYS = 14L
    private const val MUST_PER_DAY = 2
    private const val REVIEWS_PER_DAY = 2

    fun hm(m: Int): String = clock(m).let { hhmm(it.hour, it.minute) }

    /** The clock time of [m] minutes from the day's start (past midnight and before it wrap round). */
    fun clock(m: Int): LocalTime = (((m % 1440) + 1440) % 1440).let { x -> LocalTime(x / 60, x % 60) }

    fun minutesOf(t: LocalDateTime, day: LocalDate): Int =
        (t.date.toEpochDays() - day.toEpochDays()).toInt() * 1440 + t.hour * 60 + t.minute

    private fun minutes(t: LocalTime) = t.hour * 60 + t.minute

    fun isMine(t: Task) = t.tags.any { tag -> tag.substringBefore('/').lowercase().filter { it.isLetterOrDigit() } == "myspace" }

    // ---- The day's frame: when the plan may run, what is busy, and the kind of day ----

    class Frame(
        val day: LocalDate,
        val start: Int,
        val end: Int,
        val bed: Int,
        val mode: Mode,
        val nightAt: CalendarEvent?,
        val job: Boolean,
        /** Timed events of the owner's (and the day job) that touch the day, in order. */
        val timed: List<CalendarEvent>,
        val allDay: List<CalendarEvent>,
        val busy: List<Pair<Int, Int>>,
        /** When the owner woke (or usually wakes), in minutes from the day's start. */
        val wake: Int = 0,
        /** Where [wake] came from, shown on the plan's "ตื่น" row; null while the watch switch is off (no row). */
        val wakeWhy: String? = null,
        /** Where the bedtime came from, added to the "นอน" row's reason; null while the watch switch is off. */
        val bedWhy: String? = null,
        /** How tired the owner likely is (today's plan, watch switch on); null: not read, the plan is as before. */
        val tired: Fatigue.Read? = null,
        /** [Memory.freeEvents] of the day: events in [timed] that do not take the owner's time. */
        val freed: Set<String> = emptySet(),
    )

    fun frame(day: LocalDate, inp: Input, from: LocalDateTime? = null): Frame {
        val p = inp.profile
        val f = inp.facts
        val own = inp.events.filterNot { f.isOthers(it) }.filter { it.allDay || it.end > it.begin }
        fun on(d: LocalDate) = own.filter { it.begin < d.plusDays(1).atStartOfDay() && it.end > d.atStartOfDay() }
        val jobBlocks = RealDay.jobBlocks(listOf(day), own, f)
        val todays = on(day) + jobBlocks
        // A shift that starts after midnight tonight ("O11 รอบ 3" at 02:30), or a night shift that starts this evening.
        val nightNext = RealDay.nightStart(day.plusDays(1), own, p, f)
        val nightEvening = todays.filter { !it.allDay && it.begin.date == day && it.begin.hour >= 12 && RealDay.isNight(it, p, f) }.minByOrNull { it.begin }
        val nightAt = nightEvening ?: nightNext
        // A night shift that ran into this morning.
        fun afterNightOn(d: LocalDate) = own.any { !it.allDay && RealDay.isNight(it, p, f) && it.begin < d.atTime(RealDay.NIGHT_END) && it.end > d.minusDays(1).atTime(18, 0) } ||
            on(d.minusDays(1)).any { it.allDay && RealDay.isNight(it, p, f) }
        val afterNight = afterNightOn(day)
        val mode = when {
            afterNight -> Mode.AFTER_NIGHT
            nightAt != null -> Mode.BEFORE_NIGHT
            else -> Mode.NORMAL
        }
        val profileBed = minutes(p.sleep).let { if (it < 12 * 60) it + 1440 else it }
        var wakeAt = minutes(p.wake)
        var bed = profileBed
        var wakeWhy: String? = null
        var bedWhy: String? = null
        // Only today's plan: other days (a day to keep a task for, the sprint's hours) stay on the profile.
        inp.sleeps?.takeIf { day == inp.now.date }?.let { sl ->
            // The watch (owner's pick, 2026-10-30). Days and evenings set by a night shift are not the owner's usual.
            fun job(d: LocalDate) = RealDay.jobBlocks(listOf(d), own, f).isNotEmpty()
            fun shiftMorning(d: LocalDate) = afterNightOn(d) || RealDay.nightStart(d, own, p, f) != null
            fun kind(d: LocalDate) = if (job(d)) tr("วันทำงาน", "work days") else tr("วันหยุด", "days off")
            val fallback = inp.sleepProblem?.let { tr("จากโปรไฟล์ เพราะ", "From your profile: ") + it }
                ?: tr("จากโปรไฟล์ ข้อมูลนาฬิกายังไม่พอ", "From your profile: not enough from the watch yet")
            val woke = SleepTimes.wokeOn(sl, day)
            // The morning after the early night that follows a shift is left out too.
            val usualWake = SleepTimes.usual(day, { d -> !shiftMorning(d) && !afterNightOn(d.minusDays(1)) && job(d) == job(day) }) { d -> SleepTimes.wokeOn(sl, d) }
            when {
                woke != null -> { wakeAt = woke; wakeWhy = tr("จากนาฬิกาเมื่อเช้า", "From the watch this morning") }
                usualWake != null -> {
                    wakeAt = usualWake
                    wakeWhy = tr("ยังไม่มีข้อมูลนาฬิกาเมื่อเช้า ใช้เวลาปกติของ${kind(day)}ใน 14 วันล่าสุดไปก่อน", "Not in from the watch yet: your usual time on ${kind(day)} over the last 14 days")
                }
                else -> wakeWhy = fallback
            }
            val tomorrow = day.plusDays(1)
            val usualBed = SleepTimes.usual(day, { d -> !shiftMorning(d.plusDays(1)) && !afterNightOn(d) && job(d.plusDays(1)) == job(tomorrow) }) { d -> SleepTimes.bedOn(sl, d) }
            if (usualBed != null) {
                bed = usualBed
                bedWhy = tr("เวลาปกติของคืนก่อน${kind(tomorrow)}ใน 14 วันล่าสุด จากนาฬิกา", "Your usual time before ${kind(tomorrow)} over the last 14 days, from the watch")
            } else bedWhy = fallback
        }
        if (nightNext != null) bed = minOf(bed, minutes(RealDay.BED_BEFORE_NIGHT))
        if (afterNight) bed -= 60
        var end = bed - 60
        // Tiredness built up over days (owner's pick ก, 2026-10-30): today's plan only, with the watch switch on.
        val tired = inp.sleeps?.takeIf { day == inp.now.date }?.let { sl ->
            // Shifts that began in the last week, the one that ended this morning too.
            val nights = own.count { e -> !e.allDay && RealDay.isNight(e, p, f) && e.begin >= day.minusDays(Fatigue.WEEK.toLong()).atStartOfDay() && e.begin < inp.now }
            // As the secretary's "slipping" counts: main tasks only, one per title.
            val often = inp.tasks.filter { it.isOpen && !it.isSubtask && inp.moves(it) >= 3 }.map { it.title.trim() }.distinct().size
            // The short nights a shift sets (the evening before it, the night after it) are its, counted under nights.
            fun ownNight(eve: LocalDate) = RealDay.nightStart(eve.plusDays(1), own, p, f) == null && !afterNightOn(eve) && !afterNightOn(eve.plusDays(1))
            Fatigue.read(day, sl, nights, often, ::ownNight)
        }
        nightEvening?.let { end = minOf(end, minutesOf(it.begin, day) - 30) }
        // Stops earlier only when it is not already stopping for a shift: the strip says by how much it really did.
        val tiredRead = tired?.let { r -> val was = end; end = minOf(end, bed - 60 - r.stopEarlier); r.copy(stopped = was - end) }
        val wake = wakeAt + 30
        val start = maxOf(wake, from?.let { roundUp(minutesOf(it, day)) } ?: wake)
        val timed = todays.filter { !it.allDay }.sortedBy { it.begin }
        // An event the owner called "ว่าง ทำงานได้" today stays on the plan but takes no time from it.
        val freed = inp.memory.on(day).freeEvents
        val busy = timed.filter { freeKey(it) !in freed }.map { e -> (minutesOf(e.begin, day) - CLEAR) to (minutesOf(e.end, day) + CLEAR) }
        return Frame(day, start, end, bed, mode, nightAt, jobBlocks.isNotEmpty(), timed, todays.filter { it.allDay }, busy, wakeAt, wakeWhy, bedWhy, tiredRead, freed)
    }

    private fun roundUp(m: Int) = if (m % STEP == 0) m else m + STEP - m % STEP

    /** Free minutes between [Frame.start] and [Frame.end] that no event takes. */
    fun freeIn(fr: Frame): Int {
        if (fr.end <= fr.start) return 0
        val occ = BooleanArray(fr.end - fr.start)
        fr.busy.forEach { (a, b) -> for (t in maxOf(a, fr.start) until minOf(b, fr.end)) occ[t - fr.start] = true }
        return occ.count { !it }
    }

    // ---- Building the day ----

    fun plan(inp: Input): Plan {
        val today = inp.now.date
        val mem = inp.memory.on(today)
        val fr = frame(today, inp, inp.now)
        val ws = if (inp.ahead) LookAhead.weeks(inp) else null
        val items = items(inp, fr, mem, ws)
        val first = place(fr, items, mem, shrink = false)
        val placed = if (first.later.any { it.item.must && it.picked == null && it.item.kind != Kind.LIFE && it.item.kind != Kind.MINE }) {
            place(fr, items, mem, shrink = true)
        } else first
        return assemble(inp, fr, mem, items, placed, ws)
    }

    private class Placing(val placed: List<Placed>, val later: List<Later>, val free: Int)

    private fun place(fr: Frame, items: List<Item>, mem: Memory, shrink: Boolean): Placing {
        val start = fr.start
        val end = fr.end
        val span = maxOf(0, end - start)
        val occ = BooleanArray(span)
        val busy = fr.busy.toMutableList()
        mem.errand?.let { e -> busy += e.from to (e.to ?: end) }
        busy.forEach { (a, b) -> for (t in maxOf(a, start) until minOf(b, end)) occ[t - start] = true }
        val free = occ.count { !it }
        // The buffer: a third of the free time; half when tired; all of it when very tired, so only what must be done
        // is placed (the owner's own time and meals keep their place).
        val target = when (fr.tired?.level) {
            Fatigue.Level.VERY_TIRED -> free
            Fatigue.Level.TIRED -> maxOf(30, free / 2)
            else -> maxOf(30, free / 3)
        }
        // Put by the owner at a time of their own (a suggested time or a drag; mock plan-catchup): it stays where they
        // put it and takes no room from what is already there, so nothing is pushed for it (an overlap is only flagged).
        // Work the plan still has to place goes round it; the owner's own time and meals keep their place on it.
        fun pinnedAt(it0: Item): Int? {
            if (mem.picks[it0.id].let { it != null && it != Pick.TAKE }) return null
            val f = it0.fixedAt ?: return null
            val acc = it0.task?.let { mem.accepted[ident(it)] }?.takeIf { it.pinned && it.day == fr.day.toString() && it.at == f } ?: return null
            return acc.at.takeIf { (mem.notBefore[it0.id] ?: Int.MIN_VALUE) <= it }
        }
        val pins = items.mapNotNull { it0 -> pinnedAt(it0)?.let { it0 to it } }
        val pinOcc = BooleanArray(span)
        pins.forEach { (it0, at) -> for (i in maxOf(at, start) until minOf(at + it0.len, end)) pinOcc[i - start] = true }
        fun fits(t: Int, len: Int, work: Boolean = false): Boolean {
            if (t < start || t + len > end) return false
            for (i in t until t + len) if (occ[i - start] || (work && pinOcc[i - start])) return false
            return true
        }
        fun take(t: Int, len: Int) { for (i in maxOf(t, start) until minOf(t + len, end)) occ[i - start] = true }
        fun firstFit(from: Int, to: Int, len: Int, work: Boolean): Int {
            var x = roundUp(from)
            while (x + len <= minOf(to, end)) {
                if (fits(x, len, work)) return x
                x += STEP
            }
            return -1
        }
        val placed = ArrayList<Placed>()
        val later = ArrayList<Later>()
        val pinned = pins.map { it.first.id }.toSet()
        // Taken slots and the owner's own times hold their place first; the rest follow by priority.
        val ordered = items.sortedWith(compareBy<Item>({ if (it.fixedAt != null) 0 else 1 }, { it.prio }))
        ordered.forEach { it0 ->
            val pick = mem.picks[it0.id]
            if (pick != null && pick != Pick.TAKE) {
                later += Later(it0, pickText(pick, it0), risk = false, picked = pick)
                return@forEach
            }
            if (it0.id in pinned) return@forEach
            // "ช่วงนี้ไม่ว่าง" on a taken slot moves it on like any card; its reminder then follows.
            val fixed = it0.fixedAt?.takeIf { f -> it0.own || (mem.notBefore[it0.id] ?: Int.MIN_VALUE) <= f }
            if (fixed != null) {
                val running = fixed < start && fixed + it0.len > start
                when {
                    // Already under way: it stays where it is.
                    running -> { take(fixed, it0.len); placed += Placed(it0, fixed, it0.len, short = false, running = true); return@forEach }
                    // Its time is still free: it keeps it (the owner's own time keeps it even when something overlaps).
                    fixed >= start && (it0.own || fits(fixed, it0.len)) -> { take(fixed, it0.len); placed += Placed(it0, fixed, it0.len, short = false, running = false); return@forEach }
                    // Its time already went by (the owner's own, or a taken slot not ticked): it is shown where it was and
                    // nothing moves it, so its reminder is not chased along the clock.
                    it0.own || fixed + it0.len <= start -> { placed += Placed(it0, fixed, it0.len, short = false, running = false); return@forEach }
                }
            }
            val from = maxOf(start, it0.from ?: start, mem.notBefore[it0.id] ?: start)
            val to = it0.to ?: end
            val work = it0.kind != Kind.LIFE && it0.kind != Kind.MINE
            var len = if (shrink && it0.min != null) it0.min else it0.len
            var t = firstFit(from, to, len, work)
            if (t < 0 && it0.min != null && len != it0.min) { len = it0.min; t = firstFit(from, to, len, work) }
            // A slot the owner put counts as used, so the buffer stays what it should be.
            val leftNow = (0 until span).count { !occ[it] && !pinOcc[it] }
            var capped = false
            if (t >= 0 && !it0.must && it0.kind != Kind.LIFE && it0.kind != Kind.MINE && leftNow - len < target) { t = -1; capped = true }
            if (t < 0) {
                if (it0.kind == Kind.LIFE) return@forEach
                val why = when {
                    it0.lateText != null -> it0.lateText
                    it0.kind == Kind.MINE -> tr("วันนี้ไม่มีช่องว่างพอ ไว้พรุ่งนี้", "No room today, tomorrow then")
                    capped && fr.tired != null && fr.tired.level != Fatigue.Level.OK -> tr("ย้ายไปพรุ่งนี้ วันนี้น่าจะล้า เลขาจัดเบาลง", "Moved to tomorrow: likely tired today, a lighter plan")
                    capped -> tr("ย้ายไปพรุ่งนี้ เพื่อเหลือช่องกันชนไว้", "Moved to tomorrow to keep a buffer")
                    else -> tr("วันนี้ไม่มีเวลาพอ ย้ายไปพรุ่งนี้", "Not enough time today, moved to tomorrow")
                }
                later += Later(it0, why, risk = it0.must && (it0.kind == Kind.URGENT || it0.kind == Kind.DUE), picked = null)
                return@forEach
            }
            take(t, len)
            placed += Placed(it0, t, len, short = len < it0.len, running = false)
        }
        pins.forEach { (it0, at) -> placed += Placed(it0, at, it0.len, short = false, running = at < start && at + it0.len > start, pinned = true) }
        return Placing(placed.sortedBy { it.at }, later, free)
    }

    private fun pickText(pick: Pick, item: Item): String = when (pick) {
        Pick.TIRED -> tr("วันนี้หมดแรง ไม่นับว่าข้าม เลขาหาช่องใหม่ให้", "Out of energy today, not counted as skipped")
        Pick.LATER -> item.task?.scheduled?.let { tr("เก็บไว้ก่อน ย้ายไป ${shortDay(it)} แล้ว", "Kept for later, moved to ${shortDay(it)}") }
            ?: tr("เก็บไว้ก่อน เลขาเลือกวันใหม่ให้", "Kept for later")
        Pick.DROP -> tr("ปล่อยไปแล้ว งานย้ายไปที่เก็บงาน ไม่ได้ลบ", "Let go, moved to the archive, not deleted")
        Pick.SPLIT -> tr("ขอให้เลขาแบ่งงานแล้ว จะเสนองานย่อยในเลขาถาม", "The secretary will propose smaller steps")
        Pick.SKIP -> tr("ไว้ก่อน ยังไม่ลงตาราง", "Later, not in the plan")
        Pick.TAKE -> ""
    }

    /** "2 ชม.", "1 ชม. 30 นาที", "45 นาที". */
    fun lenText(m: Int): String = if (m >= 60) {
        tr("${m / 60} ชม.", "${m / 60} h") + if (m % 60 != 0) tr(" ${m % 60} นาที", " ${m % 60} min") else ""
    } else tr("$m นาที", "$m min")

    fun shortDay(d: LocalDate): String = Planner.SHORT_DAYS[d.dayOfWeek.ordinal] + " " + d.dayOfMonth + " " + Countdown.monthShort(d)

    /** What the plan may place today, from the tasks, the owner's habits and the sprint. */
    private fun items(inp: Input, fr: Frame, mem: Memory, ws: List<LookAhead.Week>?): List<Item> {
        val today = fr.day
        val tomorrow = today.plusDays(1)
        val yesterday = today.minusDays(1)
        val after = fr.mode == Mode.AFTER_NIGHT
        val light = fr.mode != Mode.NORMAL
        val blocked = Projects.blocked(inp.tasks)
        val open = inp.tasks.filter { it.isOpen && it.list == null && it !in blocked }
        val subtasks = inp.tasks.filter { it.isSubtask }
        val out = ArrayList<Item>()
        val used = HashSet<String>()
        val sprint = mem.sprint?.takeIf { it.taken && it.week == weekOf(today).toString() }?.projects.orEmpty()
        // A sprint slot taken today keeps its card even when the sprint is opened again to choose anew.
        val sprintSlots = (sprint + mem.slots.keys.filter { it.startsWith("sprint:") }.map { it.removePrefix("sprint:") })
            .distinctBy { it.lowercase() }
        // A taken slot the plan moved to tomorrow (no room left today) is tomorrow's now, as its line says.
        fun movedOn(t: Task) = mem.accepted[ident(t)]?.let { it.day > today.toString() } == true
        fun tagOf(t: Task) = if (Projects.projectOf(t)?.let { p -> sprint.any { it.equals(p, ignoreCase = true) } } == true) tr("สปรินต์", "Sprint") else null

        // Work: what each open task is for today, if anything.
        val musts = ArrayList<Item>()
        val work = ArrayList<Item>()
        val takers = mem.slots.values.toSet()
        open.filter { !isMine(it) }.forEach { t ->
            val id = ident(t)
            // A task that took a net or sprint slot stays that card, as it was offered (see below).
            if (id in takers || movedOn(t)) return@forEach
            val acc = mem.accepted[id]?.takeIf { it.day == today.toString() }
            val ownTime = t.reminderAt?.takeIf { acc == null && it.date == today }
            val len0 = Planner.minutesOf(t.title, Planner.kindOf(t.title)).coerceIn(15, 90)
            val due = t.due
            val item: Item? = when {
                t.created == today && due != null && due <= tomorrow -> Item(
                    id, Kind.URGENT, 0.8, minOf(len0, 60), t.title,
                    tr("งานเข้ามาวันนี้และส่ง${if (due <= today) "วันนี้" else "พรุ่งนี้"} เลขาให้ขึ้นก่อนงานอื่น แต่ไม่เบียดเวลากินข้าวกับเวลาของตัวเอง", "New today and due soon, so it goes first"),
                    t, must = true, lateText = tr("ไม่ทันวันนี้", "No room today"),
                )
                due != null && due <= tomorrow -> {
                    val when_ = when {
                        due < today -> tr("เลยกำหนดมา ${(today.toEpochDays() - due.toEpochDays())} วัน", "${(today.toEpochDays() - due.toEpochDays())} days late")
                        due == today -> tr("ครบกำหนดวันนี้", "Due today")
                        else -> tr("ส่งพรุ่งนี้", "Due tomorrow")
                    }
                    Item(
                        id, Kind.DUE, if (due <= today) 1.0 else 1.5, 15, t.title, when_ + tr(" เริ่มแค่ 15 นาทีก่อน", ", start with 15 minutes"), t, must = true,
                        lateText = when_ + tr(" แต่วันนี้ไม่มีเวลาพอ", ", but there is no time today"),
                    )
                }
                mem.pushedOn(id, yesterday) || mem.tired[id] == yesterday.toString() -> Item(id, Kind.AGAIN, 1.2, 15, t.title, tr("เมื่อวานเลื่อนไว้ วันนี้เริ่มแค่ 15 นาทีก่อน", "Put off yesterday: 15 minutes today"), t, must = true)
                t.scheduled == today -> Item(id, Kind.WORK, 2.5, minOf(len0, 60), t.title, tr("ตั้งใจทำวันนี้", "Planned for today"), t, must = true)
                t.status == Status.IN_PROGRESS -> Item(id, Kind.WORK, 3.2, if (light) 30 else 45, t.title, progressWhy(t, subtasks) ?: tr("ทำค้างไว้", "Already started"), t)
                t.scheduled != null && t.scheduled < today -> Item(
                    id, Kind.WORK, 3.5, 30, t.title,
                    tr("เป้าที่ตั้งเองเลยมา ${(today.toEpochDays() - t.scheduled.toEpochDays())} วัน", "Own target slipped"), t,
                )
                else -> null
            }
            if (item == null && ownTime == null && acc == null) return@forEach
            val base = item ?: Item(id, Kind.WORK, 2.5, minOf(len0, 60), t.title, tr("ตั้งใจทำวันนี้", "Planned for today"), t, must = true)
            val tagged = base.copy(tag = tagOf(t))
            val fixedItem = when {
                acc != null -> tagged.copy(fixedAt = acc.at)
                ownTime != null -> tagged.copy(fixedAt = ownTime.hour * 60 + ownTime.minute, own = true, why = tr("เวลาเตือนที่ตั้งไว้เอง", "Your own reminder time"))
                else -> tagged
            }
            if (fixedItem.must && fixedItem.kind in setOf(Kind.URGENT, Kind.DUE, Kind.AGAIN)) musts += fixedItem else work += fixedItem
        }
        // At most two things that must be done today, none after a night shift; the rest become offers.
        musts.sortedBy { it.prio }.forEachIndexed { i, it ->
            out += when {
                after -> tr("วันพักฟื้น ไม่ต้องเริ่มวันนี้ ถ้าไหวค่อยทำ", "A recovery day: only if you feel like it").let { w -> it.copy(must = false, prio = 3.0, why = w, lateText = w) }
                i >= MUST_PER_DAY && it.fixedAt == null -> (it.why + tr(" (วันนี้มีงานต้องทำครบ 2 อย่างแล้ว ถ้ายังมีแรงค่อยทำ)", " (two must-dos already today)"))
                    .let { w -> it.copy(must = false, prio = 3.05, why = w, lateText = w) }
                else -> it
            }
            used += it.id
        }
        // Slipped targets and work under way: the two that matter most of each, the rest stay in the task lists.
        val doing = work.filter { it.kind == Kind.WORK && it.prio == 3.2 }.sortedByDescending { it.task?.priority?.ordinal?.let { o -> -o } ?: 0 }.take(2)
        val slipped = work.filter { it.prio == 3.5 }.take(2)
        val rest = work.filter { it.prio != 3.2 && it.prio != 3.5 }
        (rest + doing + slipped).forEach { out += if (after && it.must && it.fixedAt == null) it.copy(must = false, prio = 3.0) else it; used += it.id }

        // The sprint's projects: one slot a day each, its next task (not after a night shift).
        if (!after) sprintSlots.forEach { proj ->
            val slot = "sprint:$proj"
            val taker = mem.slots[slot]
            val cands = open.filter { !isMine(it) && Projects.projectOf(it)?.equals(proj, ignoreCase = true) == true && ident(it) !in used && it.parent == null && !Focus.isSomeday(it) }
                .sortedWith(compareBy<Task>({ if (it.status == Status.IN_PROGRESS) 0 else 1 }, { it.due ?: LocalDate(9999, 12, 31) }, { it.created ?: LocalDate(9999, 12, 31) }))
            val n = mem.swaps[slot] ?: 0
            // Taken today: the same task keeps the slot (gone once it is done, dropped or moved to tomorrow); otherwise
            // the n-th choice, while the project is still in the sprint.
            val next = (if (taker != null) open.firstOrNull { ident(it) == taker } else if (proj in sprint) cands.getOrNull(n) else null)
                ?.takeIf { !movedOn(it) } ?: return@forEach
            used += ident(next)
            val why = progressWhy(next, subtasks) ?: next.nextStep?.let { tr("ก้าวถัดไป: $it", "Next step: $it") } ?: tr("ทำต่อโปรเจกต์ในสปรินต์", "Sprint project")
            out += Item(
                ident(next), Kind.PROJECT, 3.0, if (light) 30 else 45, next.title,
                if (fr.mode == Mode.BEFORE_NIGHT) why + tr(" วันนี้แค่ 30 นาทีเพราะมีเวร", ", 30 minutes today for the shift") else why,
                next, tag = tr("สปรินต์", "Sprint"), more = if (taker != null) 0 else cands.size - n - 1, slot = slot,
                fixedAt = mem.accepted[ident(next)]?.takeIf { it.day == today.toString() }?.at,
            )
        }

        // The look-ahead (switch on), rule 2: on a whole free day before a tight week, one long slot of work today. The
        // sprint's first slot grows to it; with no sprint, the project most in need gets its next task. Not a must, so
        // tiredness can still take it out (the owner's own time stays).
        val freeDay = ws != null && LookAhead.freeDay(today, inp)
        val tight = if (freeDay && !after) LookAhead.beforeTight(ws!!) else null
        if (tight != null) {
            val hint = Hint(
                tr("ก่อนสัปดาห์แน่น", "Before a tight week"),
                tr("สัปดาห์หน้าว่างแค่ราว ${tight.hours} ชม.", "Next week has only about ${tight.hours} h free"),
                Tone.HEAVY,
            )
            val i = out.indexOfFirst { it.slot?.startsWith("sprint:") == true }
            if (i >= 0) {
                val it0 = out[i]
                out[i] = it0.copy(len = maxOf(it0.len, LookAhead.LONG), min = it0.len, hints = it0.hints + hint)
            } else {
                val slot = "ahead"
                val taker = mem.slots[slot]
                val cands = Sprint.rank(inp, ws).flatMap { r ->
                    open.filter { t -> !isMine(t) && Projects.projectOf(t)?.equals(r.project, ignoreCase = true) == true && ident(t) !in used && t.parent == null && !Focus.isSomeday(t) }
                        .sortedWith(compareBy<Task>({ if (it.status == Status.IN_PROGRESS) 0 else 1 }, { it.due ?: LocalDate(9999, 12, 31) }, { it.created ?: LocalDate(9999, 12, 31) }))
                }.distinctBy { ident(it) }
                val n = mem.swaps[slot] ?: 0
                (if (taker != null) open.firstOrNull { ident(it) == taker } else cands.getOrNull(n))?.takeIf { !movedOn(it) }?.let { t ->
                    used += ident(t)
                    val why = progressWhy(t, subtasks) ?: t.nextStep?.let { tr("ก้าวถัดไป: $it", "Next step: $it") } ?: hint.why
                    out += Item(
                        ident(t), Kind.PROJECT, 2.8, LookAhead.LONG, t.title, why, t, min = 45,
                        tag = tagOf(t), more = if (taker != null) 0 else cands.size - n - 1, slot = slot, hints = listOf(hint),
                        fixedAt = mem.accepted[ident(t)]?.takeIf { it.day == today.toString() }?.at,
                    )
                }
            }
        }

        // The 14-day net: work with no date that has not moved for two weeks gets 15 minutes, so it does not sink.
        // A review today does not take a task out of it: the card said it would get a slot once reviewed.
        if (!after) {
            val taker = mem.slots["net"]
            val sunk = open.filter {
                it.parent == null && it.due == null && it.scheduled == null && it.recurrence == null && !Focus.isSomeday(it) &&
                    !isMine(it) && ident(it) !in used && (quiet(it, subtasks, today, before = today) ?: 0) >= NET_DAYS
            }.sortedByDescending { quiet(it, subtasks, today, before = today) ?: 0 }
            val n = (mem.swaps["net"] ?: 0)
            // Taken today: the same task keeps the slot (gone once it is done or dropped); otherwise the n-th choice.
            (if (taker != null) open.firstOrNull { ident(it) == taker } else sunk.getOrNull(n))?.takeIf { !movedOn(it) }?.let { t ->
                used += ident(t)
                val days = quiet(t, subtasks, today, before = today)
                out += Item(
                    ident(t), Kind.NET, 2.0, 15, t.title,
                    tr("ยังไม่มีวันกำหนด ค้างมา $days วัน ขยับสัก 15 นาที ไม่ให้จมหาย", "No date, quiet for $days days: 15 minutes so it does not sink"),
                    t, must = true, more = if (taker != null) 0 else sunk.size - n - 1, tag = tagOf(t), slot = "net",
                    fixedAt = mem.accepted[ident(t)]?.takeIf { it.day == today.toString() }?.at,
                )
            }
        }

        // A task moved three times: ask what would help (one a day, not again for 14 days once answered).
        val cut = today.minusDays(14).toString()
        val answeredToday = mem.asked.values.any { it == today.toString() }
        // Work due by tomorrow keeps its slot instead: the question waits until it is not urgent.
        open.filter { it.parent == null && !isMine(it) && (it.due == null || it.due > tomorrow) }
            .map { it to inp.moves(it) + (mem.pushed[ident(it)]?.size ?: 0) }
            .filter { (t, n) ->
                n >= Learned.SLIP_MOVES &&
                    // Once one is answered today, only that one stays (showing the answer, to be taken back).
                    if (answeredToday) mem.picks["ask:" + ident(t)] != null else mem.asked[ident(t)]?.let { it < cut } ?: true
            }
            .maxByOrNull { it.second }?.let { (t, n) ->
                out.removeAll { it.task?.let(::ident) == ident(t) }
                out += Item(
                    "ask:" + ident(t), Kind.ASK, 2.0, 5, t.title,
                    tr("เลื่อนมา $n ครั้งแล้ว ไม่เป็นไร อาจใหญ่เกินไป อยากให้เลขาช่วยแบบไหน (ตอบวันไหนก็ได้)", "Moved $n times. No problem, it may be too big. How can the secretary help?"),
                    t,
                )
            }

        // Every task passes the review card before it is planned (owner, 2026-10-29), except what is due soon, what the
        // owner set for today and what is under way. Two a day; the rest wait in the list below.
        var reviews = 0
        val gated = out.map { it0 ->
            val t = it0.task
            // Only what the review card can take (repeating tasks and subtasks never come up there).
            val gate = t != null && it0.fixedAt == null && t in Review.candidates(listOf(t)) &&
                (it0.kind == Kind.NET || it0.kind == Kind.PROJECT || (it0.kind == Kind.WORK && it0.prio == 3.5))
            if (!gate || ReviewLog.last(t!!) != null) return@map it0
            reviews++
            if (reviews > REVIEWS_PER_DAY) {
                tr("ยังไม่ได้ทบทวน ทบทวนแล้วเลขาลงตารางให้", "Not reviewed yet").let { w ->
                    it0.copy(id = "review:" + ident(t), kind = Kind.REVIEW, prio = 9.0, len = 5, must = false, more = 0, tag = null, why = w, lateText = w, from = fr.end, to = fr.end, hints = emptyList())
                }
            } else {
                it0.copy(
                    id = "review:" + ident(t), kind = Kind.REVIEW, prio = 4.0, len = 5, must = false, more = 0, tag = null, hints = emptyList(),
                    why = tr("ยังไม่ได้ทบทวน เลขาเลยยังไม่รู้ว่าด่วนหรือสำคัญแค่ไหน ทบทวนแล้วจะลงตารางให้", "Not reviewed, so the secretary cannot tell how urgent it is. Review it and it gets a slot"),
                )
            }
        }
        out.clear()
        out += gated

        // The owner's own time: myspace starts on time, and meals move within their window.
        // The look-ahead (switch on), rule 3: on a whole free day what got stuck comes first (as the owner's stock study,
        // put off because "กลับห้องไปก็เหนื่อยแล้ว").
        val mine = open.filter { isMine(it) }.sortedWith(
            compareBy<Task>({ if (it.scheduled == today) 0 else 1 }, { if (freeDay && StuckLog.last(it) != null) 0 else 1 }, { it.created ?: LocalDate(9999, 12, 31) }),
        )
        val owed = ws?.let { LookAhead.owed(inp, it) }
        if (mine.isNotEmpty()) {
            val n = mem.swaps["mine"] ?: 0
            val taker = mem.slots["mine"]
            // Taken today: the same task keeps the slot, wherever the sort now puts it; otherwise the n-th choice.
            (if (taker != null) mine.firstOrNull { ident(it) == taker } else mine.getOrNull(n))?.takeIf { !movedOn(it) }?.let { t ->
                val (wFrom, len, min) = when (fr.mode) {
                    Mode.NORMAL -> Triple(19 * 60, 60, 30)
                    Mode.BEFORE_NIGHT -> Triple(18 * 60, 45, 20)
                    Mode.AFTER_NIGHT -> Triple(17 * 60, 30, 20)
                }
                val acc = mem.accepted[ident(t)]?.takeIf { it.day == today.toString() }
                // Rule 3 is the morning's: past noon without it, or once a want is done today, own time is the evening's
                // (a daily habit ticked, 🔁, is not the morning's want).
                val long = freeDay && fr.mode == Mode.NORMAL && (acc != null || fr.start < LookAhead.NOON) &&
                    inp.tasks.none { isMine(it) && it.done == today && it.recurrence == null }
                // Rule 3: a whole free day's morning, two hours, before work. Rule 4: own time owed is not cut short.
                val hints = buildList {
                    if (long || owed != null) add(
                        Hint(
                            tr("อยากทำ", "Want to"),
                            if (long) tr("วันว่างทั้งวัน ช่วงเช้าเป็นของ Klui", "A whole free day: the morning is yours") +
                                (StuckLog.entries(t).lastOrNull()?.let { e -> tr(" เรื่องนี้เคยค้างเพราะ", ". It got stuck before: ") + e.reason } ?: "")
                            else tr("เวลาของตัวเอง งานไม่เบียด", "Your own time: work never pushes it"),
                            Tone.WANT,
                        ),
                    )
                    if (owed != null) add(Hint(tr("คืนเวลา", "Time back"), owed, Tone.LIGHT))
                }
                out += Item(
                    ident(t), Kind.MINE, if (long) 0.4 else 0.5, if (long) LookAhead.LONG else len, t.title,
                    when (fr.mode) {
                        Mode.AFTER_NIGHT -> tr("วันนี้เสนอแต่เรื่องเบา กดเปลี่ยนได้โดยไม่นับ", "Something light today")
                        Mode.BEFORE_NIGHT -> tr("เวลาของตัวเอง เรื่องเบา ๆ ก่อนนอน", "Your own time, something light before bed")
                        Mode.NORMAL -> tr("เวลาของตัวเอง งานไม่เบียด ขยับหรือย่อได้เมื่อมีธุระหรือนัดเท่านั้น", "Your own time: work never pushes it, only errands or events do")
                    },
                    t, min = if (owed != null) (if (long) LookAhead.LONG else len) else if (long) 60 else min,
                    from = if (long) LookAhead.MORNING else minOf(wFrom, maxOf(fr.start, fr.end - len)), to = fr.end, more = if (taker != null) 0 else mine.size - n - 1,
                    fixedAt = acc?.at, slot = "mine", hints = hints,
                )
                // Rule 3: the morning is the owner's before work; work waits until the two hours are over.
                if (long) {
                    val mineEnd = (acc?.at ?: maxOf(fr.start, LookAhead.MORNING)) + LookAhead.LONG
                    for (i in out.indices) {
                        val w = out[i]
                        if (w.kind != Kind.MINE && w.kind != Kind.LIFE && w.fixedAt == null) out[i] = w.copy(from = maxOf(w.from ?: 0, mineEnd))
                    }
                }
            }
        }
        val (dFrom, dTo) = if (fr.mode == Mode.NORMAL) 18 * 60 to 19 * 60 + 45 else 17 * 60 + 30 to 19 * 60 + 30
        if (minOf(dTo, fr.end) - maxOf(dFrom, fr.start) >= 30) out += Item(
            "dinner", Kind.LIFE, 0.0, 45, tr("กินข้าว พัก", "Dinner and a break"),
            tr("ขยับได้ระหว่าง ${hm(dFrom)} ถึง ${hm(dTo)}", "Can move between ${hm(dFrom)} and ${hm(dTo)}"), min = 30, from = dFrom, to = dTo,
        )
        if (!fr.job && minOf(13 * 60 + 30, fr.end) - maxOf(11 * 60 + 30, fr.start) >= 30) out += Item(
            "lunch", Kind.LIFE, 0.0, 45, tr("กินข้าวเที่ยง พัก", "Lunch and a break"),
            tr("ขยับได้ระหว่าง 11:30 ถึง 13:30", "Can move between 11:30 and 13:30"), min = 30, from = 11 * 60 + 30, to = 13 * 60 + 30,
        )
        // Things to swap to: the swap index picks the next one, and a swap past the last leaves the slot free.
        return out
    }

    private fun progressWhy(t: Task, subtasks: List<Task>): String? {
        val last = StuckLog.entries(t).lastOrNull() ?: return null
        return tr("ต่อจากที่จดไว้ ", "Where you left it ") + Countdown.day(last.day) + ": " + last.reason
    }

    /**
     * Days since anything happened to the task: made, a progress line, a subtask ticked, or a review ([before]: only a
     * review before that day counts, so a review from the plan today keeps it in the net).
     */
    fun quiet(t: Task, subtasks: List<Task>, today: LocalDate, before: LocalDate? = null): Long? {
        val mine = subtasks.filter { it.parent == t.key }
        val review = ReviewLog.last(t)?.takeIf { before == null || it < before }
        val last = listOfNotNull(t.created, StuckLog.lastMove(t, mine), review).maxOrNull() ?: return null
        return (today.toEpochDays() - last.toEpochDays()).toLong()
    }

    fun weekOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.ordinal).toLong())

    private fun assemble(inp: Input, fr: Frame, mem: Memory, items: List<Item>, pl: Placing, ws: List<LookAhead.Week>?): Plan {
        val today = fr.day
        val rows = ArrayList<Row>()
        // Before the plan: the day so far (all-day events, what already happened, what is done).
        fr.allDay.forEach { rows += Row.Locked(null, it.title, tr("ทั้งวัน", "All day"), it) }
        val before = ArrayList<Row.Locked>()
        // With the watch switch on, the day starts at the owner's wake (real, usual, or the profile's).
        val nowMin = minutesOf(inp.now, today)
        fr.wakeWhy?.let { before += Row.Locked(fr.wake, tr("ตื่น", "Woke up"), it, past = fr.wake <= nowMin) }
        fr.timed.filter { minutesOf(it.end, today) <= fr.start }.forEach { e ->
            before += Row.Locked(minutesOf(e.begin, today), lockedTitle(e, today, inp), lockedWhy(e, inp, today), e, past = true)
        }
        rows += before.sortedBy { it.at }
        inp.tasks.filter { it.done == today && it.list == null && (it.due ?: it.scheduled)?.let { d -> d <= today } != false }
            .forEach { t -> rows += Row.Done(t.reminderAt?.takeIf { it.date == today }?.let { minutesOf(it, today) }, t) }
        // The plan itself: cards, the blocks it works around and the free time between them.
        val blocks = ArrayList<Row.Locked>()
        mem.errand?.let { e -> blocks += Row.Locked(e.from, tr("ธุระ ถึง ${hm(e.to ?: fr.end)}", "Errand until ${hm(e.to ?: fr.end)}"), tr("คุณกดมีธุระไว้ งานช่วงนี้ย้ายออกหมด", "You said you have an errand: work moved out of it"), until = e.to ?: fr.end) }
        fr.timed.filter { minutesOf(it.end, today) > fr.start && minutesOf(it.begin, today) < fr.end }.forEach { e ->
            val free = freeKey(e) in fr.freed
            blocks += Row.Locked(
                minutesOf(e.begin, today), lockedTitle(e, today, inp),
                if (free) tr("ว่าง ทำงานได้ วันนี้ เลขาไม่นับช่วงนี้ว่าไม่ว่าง", "Free to work today: the plan does not count this time as busy") else lockedWhy(e, inp, today), e, free = free,
            )
        }
        val timeline = (pl.placed.map { it.at to (it as Any) } + blocks.map { it.at!! to (it as Any) }).sortedBy { it.first }
        var cursor = fr.start
        val open = ArrayList<Pair<Int, Int>>()
        // [free]: where the free stretch for a task ends (the plan keeps [CLEAR] minutes before an event too).
        fun gap(a: Int, b: Int, free: Int = b) {
            val o = minOf(free, b, fr.end)
            if (o > a) open += a to o
            if (b - a >= 15) rows += Row.Gap(a, b - a)
        }
        timeline.forEach { (at, x) ->
            when (x) {
                is Row.Locked -> {
                    gap(cursor, at, if (x.event != null && !x.free) at - CLEAR else at)
                    rows += x
                    val e = x.event
                    val stop = when {
                        // An event called free takes no time: the free stretches run on through it.
                        x.free -> maxOf(cursor, at)
                        e != null -> minutesOf(e.end, today) + CLEAR
                        else -> mem.errand?.let { it.to ?: fr.end } ?: at
                    }
                    cursor = maxOf(cursor, stop)
                }
                is Placed -> {
                    gap(cursor, x.at)
                    rows += Row.Card(x.at, x, mem.picks[x.item.id])
                    cursor = maxOf(cursor, x.at + x.len)
                }
            }
        }
        gap(cursor, fr.end)
        // After the plan: events later tonight, the shift after midnight, sleep.
        val afterRows = ArrayList<Row.Locked>()
        fr.timed.filter { minutesOf(it.begin, today) >= fr.end }.forEach { e ->
            afterRows += Row.Locked(minutesOf(e.begin, today), lockedTitle(e, today, inp), lockedWhy(e, inp, today), e)
        }
        afterRows += Row.Locked(
            fr.bed, tr("นอน", "Sleep"),
            when (fr.mode) {
                Mode.BEFORE_NIGHT -> tr("นอนเร็ว ให้พร้อมเข้าเวร", "Early night, ready for the shift")
                Mode.AFTER_NIGHT -> tr("นอนเร็วขึ้น เพราะเพิ่งลงเวรดึก", "Earlier, after the night shift")
                Mode.NORMAL -> listOfNotNull(fr.bedWhy, tr("${hm(fr.end)} ถึง ${hm(fr.bed)} ไม่จัดอะไร ไว้ผ่อนก่อนนอน", "Nothing planned from ${hm(fr.end)}: wind down")).joinToString(", ")
            },
        )
        inp.events.filter { !it.allDay && !inp.facts.isOthers(it) && it.begin.date == today.plusDays(1) && it.begin.time < RealDay.NIGHT_END && RealDay.isShift(it, inp.profile, inp.facts) }
            .forEach { e -> afterRows += Row.Locked(minutesOf(e.begin, today), lockedTitle(e, today, inp), lockedWhy(e, inp, today), e) }
        rows += afterRows.sortedBy { it.at }

        val planned = pl.placed.filter { it.item.kind != Kind.LIFE }.sumOf { it.len }
        val lifeLen = pl.placed.filter { it.item.kind == Kind.LIFE }.sumOf { it.len }
        val buffer = maxOf(0, pl.free - planned - lifeLen)
        val banner = when (fr.mode) {
            Mode.AFTER_NIGHT -> tr("เพิ่งลงเวรดึก วันนี้วันพักฟื้น ไม่มีงานที่ต้องเริ่ม สปรินต์พัก ข้ามได้โดยไม่นับ", "Just off a night shift: a recovery day, nothing must start, skips do not count")
            Mode.BEFORE_NIGHT -> fr.nightAt?.let { tr("คืนนี้มีเวร ${hm(minutesOf(it.begin, today))} เลขาจัดงานเบาให้ และหยุดจัดงานตั้งแต่ ${hm(fr.end)} จะได้นอนพอ", "Shift at ${hm(minutesOf(it.begin, today))} tonight: a light plan that stops at ${hm(fr.end)}") }
            Mode.NORMAL -> null
        }.let { b -> listOfNotNull(b, fr.tired?.let(Fatigue::text)).joinToString("\n").ifEmpty { null } }
        // Taken slots: their line follows the plan (its reminder moves with the card; with no room today, ⏳ goes to
        // tomorrow at the same time, but never past the 📅). The note is compared with where the card is, so an undo
        // puts the line back too. A line the owner changed since the plan wrote it is left alone and let go of.
        val moves = ArrayList<Move>()
        val letGo = ArrayList<String>()
        fun follow(t: Task, card: String, acc: Accepted, day: LocalDate, at: Int) {
            val time = if (acc.timed) clock(at) else null
            if (t.scheduled == day && (time == null || t.reminderTime == time)) return
            val line = mem.lines[card]
            when (marks(t)) {
                // As the plan wrote it last: it follows the card.
                line?.let { marks(it.after) } -> moves += Move(t, at, day, card)
                // As it was before the plan wrote it: that write has not landed yet, so wait for it.
                line?.let { marks(it.before) } -> Unit
                // The owner set ⏳ or the reminder themselves: left as it is, and the slot let go of.
                else -> letGo += ident(t)
            }
        }
        pl.placed.forEach { p ->
            val t = p.item.task ?: return@forEach
            val acc = mem.accepted[ident(t)]?.takeIf { it.day == today.toString() } ?: return@forEach
            if (!p.item.own) follow(t, p.item.id, acc, today, p.at)
        }
        pl.later.forEach { l ->
            val t = l.item.task ?: return@forEach
            val acc = mem.accepted[ident(t)]?.takeIf { it.day == today.toString() } ?: return@forEach
            val tomorrow = today.plusDays(1)
            if (l.picked == null && (t.due == null || t.due >= tomorrow)) follow(t, l.item.id, acc, tomorrow, acc.at)
        }
        val later = pl.later.filter { it.item.kind != Kind.LIFE && it.item.prio < 9.0 || it.picked != null } +
            pl.later.filter { it.item.prio >= 9.0 && it.picked == null }
        val next = pl.placed.firstOrNull { it.item.kind != Kind.LIFE && !it.item.own && mem.picks[it.item.id] == null && !it.running && it.at + it.len > fr.start }?.item?.id
        return Plan(
            today, fr.start, fr.end, fr.mode, rows, later.distinctBy { it.item.id }, pl.free, planned, buffer, banner,
            Sprint.line(inp, fr, mem, ws), moves, letGo, next, bed = fr.bed, open = open,
        )
    }

    private fun lockedTitle(e: CalendarEvent, today: LocalDate, inp: Input): String {
        val end = minutesOf(e.end, today)
        // A shift reads as one ("เวร O11 รอบ 3"), as in the mock, when its own title does not say so.
        val name = if (RealDay.isShift(e, inp.profile, inp.facts) && "เวร" !in e.title) tr("เวร ", "Shift ") + e.title else e.title
        return name + tr(" ถึง ${hm(end)}", " until ${hm(end)}")
    }

    private fun lockedWhy(e: CalendarEvent, inp: Input, today: LocalDate): String {
        val from = inp.facts.calendarNames[e.calendarId]?.let { " $it" } ?: ""
        return when {
            e.title == RealDay.jobTitle -> tr("จันทร์ถึงศุกร์ วันนี้ไม่ใช่วันหยุด", "Monday to Friday, not a holiday today")
            RealDay.isShift(e, inp.profile, inp.facts) && e.end.date == today && e.begin.date == today && e.begin.hour < 6 ->
                tr("เมื่อคืน จากปฏิทินเวร$from", "Last night, from the shift calendar$from")
            RealDay.isShift(e, inp.profile, inp.facts) -> tr("จากปฏิทินเวร$from", "From the shift calendar$from")
            else -> tr("จากปฏิทิน", "From the calendar")
        }
    }

    // ---- What changed, told after a reflow ----

    /** "เลขาจัดใหม่ให้แล้ว: ..." after [what] changed the plan from [before] to [after]. */
    fun news(what: String, before: Plan, after: Plan): String {
        val was = before.placed.associateBy { it.item.id }
        val shifted = after.placed.count { p -> was[p.item.id]?.let { it.at != p.at } == true }
        val shortened = after.placed.count { p -> p.short && was[p.item.id]?.short != true }
        val moved = after.later.count { it.picked == null } - before.later.count { it.picked == null }
        val bits = buildList {
            if (shifted > 0) add(tr("ขยับเวลา $shifted อย่าง เวลาเตือนขยับตาม", "$shifted moved, their reminders too"))
            if (shortened > 0) add(tr("ย่อเวลาของตัวเองให้สั้นลง ไม่ตัดทิ้ง", "Your own time shortened, not dropped"))
            if (moved > 0) add(tr("ย้ายไปพรุ่งนี้ $moved อย่าง", "$moved moved to tomorrow"))
            if (moved < 0) add(tr("ดึงกลับมาวันนี้ ${-moved} อย่าง", "${-moved} back today"))
        }
        return what + if (bits.isNotEmpty()) tr(" เลขาจัดใหม่ให้แล้ว: ", ". Replanned: ") + bits.joinToString(", ")
        else tr(" แผนเดิมยังพอดี ใช้ช่องกันชนแทน", ". The plan still fits, the buffer takes it")
    }

    /**
     * The next day from tomorrow, within [NET_DAYS] days, with free time for [len] minutes after a third kept clear:
     * where "เก็บไว้ก่อน" puts a task. Night-shift days come last.
     */
    fun laterDay(inp: Input, len: Int, due: LocalDate? = null): LocalDate {
        val today = inp.now.date
        val days = (1L..NET_DAYS).map { today.plusDays(it) }.filter { due == null || it <= due }
        val ok = days.filter { d ->
            val fr = frame(d, inp)
            freeIn(fr) * 2 / 3 - 90 >= len
        }
        return ok.firstOrNull { frame(it, inp).mode == Mode.NORMAL } ?: ok.firstOrNull() ?: today.plusDays(1)
    }

    // ---- When real life does not follow the plan (the owner's mix, mock plan-catchup, 2026-10-30) ----

    /** The latest a task of [len] minutes can start and still be done before the plan stops today. */
    fun latestStart(plan: Plan, len: Int): Int = plan.end - len

    /** On a taken row: "ถ้ายังไม่เริ่ม เหลืออีก 3 ชม. ถึงจะทันวันนี้"; null once [latest] went by. */
    fun leftText(latest: Int, now: Int): String? = (latest - now).takeIf { it > 0 }?.let { n ->
        tr("ถ้ายังไม่เริ่ม เหลืออีก ${lenText(n)} ถึงจะทันวันนี้", "If not started, ${lenText(n)} left to make it today")
    }

    /**
     * The reminder of a slot taken today, at its time: "ถ้ายังไม่เริ่มตอนนี้ เหลืออีก 3 ชม. ถึงจะทำทันวันนี้ (เริ่มช้าสุด
     * 19:45)". Null (the reminder says what it said before) when it is not one, when the plan's stop today is not known
     * (the today page was not opened today), when the slot was taken before its length was kept, or once it is too late.
     */
    fun reminderLeft(mem: Memory, t: Task, now: LocalDateTime): String? {
        val today = now.date.toString()
        if (mem.day != today) return null
        val stop = mem.stop ?: return null
        val acc = mem.accepted[ident(t)]?.takeIf { it.day == today && it.timed && it.len > 0 } ?: return null
        val latest = stop - acc.len
        val n = latest - (now.hour * 60 + now.minute)
        if (n <= 0) return null
        return tr("ถ้ายังไม่เริ่มตอนนี้ เหลืออีก ${lenText(n)} ถึงจะทำทันวันนี้ (เริ่มช้าสุด ${hm(latest)})", "If not started now, ${lenText(n)} left to make it today (start by ${hm(latest)})")
    }

    /**
     * Where a task of [len] minutes not done in its slot can go, from [now]: the first free time (recommended), one half
     * hour or the next free stretch after it, and the latest; times in the plan's free stretches only, so none sits on
     * anything. Empty when nothing fits before the plan stops.
     */
    fun suggest(plan: Plan, len: Int, now: Int): List<Int> {
        val from = roundUp(maxOf(now, plan.start))
        val fits = plan.open.mapNotNull { (a, b) -> roundUp(maxOf(a, from)).takeIf { it + len <= b }?.let { it to b } }
        if (fits.isEmpty()) return emptyList()
        val first = fits.first().first
        val latest = fits.last().let { (a, b) -> maxOf(a, (b - len) - (b - len) % STEP) }
        val marks = fits.flatMap { (a, b) -> listOf(a) + generateSequence(a - a % 30 + 30) { it + 30 }.takeWhile { it + len <= b } }
        val mid = marks.filter { it >= first + 30 && it < latest }.minOrNull()
        return listOfNotNull(first, mid, latest).distinct()
    }

    /** What [at] for [len] minutes sits on, besides the card [skip]: each one's name and the minutes they share. */
    fun overlaps(plan: Plan, at: Int, len: Int, skip: String): List<Pair<String, Int>> = plan.rows.mapNotNull { r ->
        val span: Triple<Int, Int, String>? = when (r) {
            is Row.Card -> r.placed.takeIf { it.item.id != skip }?.let { Triple(it.at, it.at + it.len, it.item.title) }
            is Row.Locked -> {
                val end = r.until ?: r.event?.takeIf { !it.allDay }?.let { minutesOf(it.end, plan.day) }
                // Work put on an event called free is on no one's time.
                if (r.at != null && end != null && !r.free) Triple(r.at, end, r.title) else null
            }
            else -> null
        }
        span?.let { (a, b, name) -> (minOf(at + len, b) - maxOf(at, a)).takeIf { it > 0 }?.let { name to it } }
    }

    /**
     * What a card shows as "ซ้อนกับ" (mock plan-catchup board 4): a card the owner put shows all it sits on; any other
     * card only the put cards on it. The plan never overlaps cards by itself, so nothing else is flagged.
     */
    fun overlapsOf(plan: Plan, p: Placed): List<Pair<String, Int>> =
        if (p.pinned) overlaps(plan, p.at, p.len, p.item.id)
        else plan.placed.filter { it.pinned && it.item.id != p.item.id }
            .mapNotNull { q -> (minOf(p.at + p.len, q.at + q.len) - maxOf(p.at, q.at)).takeIf { it > 0 }?.let { q.item.title to it } }

    /** "ซ้อนกับ How to ดูหุ้น 15 นาที": the overlaps of a card, names cut short by [cut]. */
    fun overlapText(list: List<Pair<String, Int>>, cut: (String) -> String = { short(it) }): String? =
        list.takeIf { it.isNotEmpty() }?.joinToString(", ") { (name, n) -> cut(name) + " " + lenText(n) }?.let { tr("ซ้อนกับ ", "Overlaps ") + it }

    /** A name cut to about [n] letters at a whole letter (Thai marks stay with theirs), with "…" when cut. */
    fun short(name: String, n: Int = 16): String {
        if (name.length <= n) return name
        var i = n
        while (i < name.length && name[i].code in THAI_MARKS) i++
        return name.substring(0, i).trimEnd() + "…"
    }

    private val THAI_MARKS = setOf(0x0E30, 0x0E31, 0x0E32, 0x0E33, 0x0E45) + (0x0E34..0x0E3A) + (0x0E47..0x0E4E)
}

/**
 * The week's sprint (owner's picks, 2026-10-29): at most two projects, fitted to the real free hours outside the job and
 * the shifts. The order: a real deadline that will be missed unless work starts this week, then a deadline within two
 * weeks or someone waiting, then work under way with a recent progress note, then what has been quiet the longest.
 * The owner takes it or swaps the second project; one line shows until it is taken, then a small tag on the cards.
 */
object Sprint {

    /** [far]: the deadline is past two weeks and counts only because the time left is nearly not enough (look-ahead). */
    data class Ranked(
        val project: String, val rank: Int, val need: Int, val freeBefore: Int?, val due: LocalDate?, val quiet: Long, val far: Boolean = false,
        /** The task due first, when [due] counts. */
        val dueTitle: String? = null,
    )

    /** Usable minutes on [day]: the free time, less meals and own time, with a third kept clear. */
    fun usable(day: LocalDate, inp: DayFill.Input): Int = usableIn(DayFill.frame(day, inp, if (day == inp.now.date) inp.now else null))

    fun usableIn(fr: DayFill.Frame): Int {
        val life = (if (fr.job) 45 else 90) + 60
        return maxOf(0, (DayFill.freeIn(fr) - life) * 2 / 3)
    }

    fun rank(inp: DayFill.Input, ws0: List<LookAhead.Week>? = null): List<Ranked> {
        val today = inp.now.date
        val open = inp.tasks.filter { it.isOpen && it.list == null && !DayFill.isMine(it) && !Focus.isSomeday(it) }
        val subtasks = inp.tasks.filter { it.isSubtask }
        val byProject = open.groupBy { Projects.projectOf(it) }.filterKeys { it != null }.mapKeys { it.key!! }
        val usableBy = HashMap<LocalDate, Int>()
        // The look-ahead counts days as rule 1 does: none after a night shift or away, and a week the shift calendar
        // has not reached loses its share to shifts.
        val ws = if (inp.ahead) ws0 ?: if (byProject.isEmpty()) null else LookAhead.weeks(inp) else null
        fun use(d: LocalDate) = usableBy.getOrPut(d) { if (ws != null) LookAhead.usableCounted(d, inp, ws) else usable(d, inp) }
        return byProject.map { (proj, tasks) ->
            // The look-ahead (switch on) sees deadlines eight weeks out; one past two weeks counts only when the time
            // left is nearly not enough (rule 5: the need, doubled as always, at three quarters of the free time or more).
            val horizon = if (inp.ahead) LookAhead.DAYS else DayFill.NET_DAYS
            val soon = tasks.filter { it.due != null && it.due >= today && it.due <= today.plusDays(horizon) }.sortedBy { it.due }
            val due0 = soon.firstOrNull()?.due
            val need = soon.filter { it.due!! <= (due0 ?: today) }.sumOf { Planner.minutesOf(it.title, Planner.kindOf(it.title)) } * 2
            val free0 = due0?.let { d -> (0L..(d.toEpochDays() - today.toEpochDays()).toLong()).sumOf { use(today.plusDays(it)) } }
            val far = due0 != null && due0 > today.plusDays(DayFill.NET_DAYS)
            val tight = free0 != null && need * 4 >= free0 * 3
            val due = due0?.takeIf { !far || tight }
            val free = free0?.takeIf { due != null }
            val waiting = tasks.any { Focus.isWaiting(it) }
            val recent = tasks.any { t -> t.status == Status.IN_PROGRESS && StuckLog.last(t)?.let { it >= today.minusDays(DayFill.NET_DAYS) } == true }
            val quiet = tasks.maxOf { DayFill.quiet(it, subtasks, today) ?: 0 }
            val rank = when {
                due != null && free != null && (need > free || far) -> 0
                due != null || waiting -> 1
                recent -> 2
                else -> 3
            }
            Ranked(proj, rank, need, free, due, quiet, far = far && due != null, dueTitle = soon.firstOrNull()?.title?.takeIf { due != null })
        }.sortedWith(compareBy<Ranked>({ it.rank }, { it.due ?: LocalDate(9999, 12, 31) }, { -it.quiet }))
    }

    /** The proposal with the second project swapped [swap] times; one project in a tight week. */
    fun propose(inp: DayFill.Input, swap: Int, ws: List<LookAhead.Week>? = null): List<Ranked> {
        val ranked = rank(inp, ws)
        if (ranked.isEmpty()) return emptyList()
        val count = if (weekLeft(inp, ws) < 6 * 60 || ws?.firstOrNull()?.heavy == true) 1 else 2
        if (count == 1 || ranked.size == 1) return listOf(ranked[swap % ranked.size])
        val second = ranked.drop(1)
        return listOf(ranked[0], second[swap % second.size])
    }

    /**
     * Usable minutes left this week. The look-ahead (rule 2: a tight week has one project) counts it as its tags do, so
     * the hours under a tag are the tag's hours: none after a night shift or away.
     */
    private fun weekLeft(inp: DayFill.Input, ws: List<LookAhead.Week>?): Int {
        ws?.firstOrNull()?.let { return it.usable }
        val today = inp.now.date
        return (0L..6L).map { DayFill.weekOf(today).plusDays(it) }.filter { it >= today }.sumOf { usable(it, inp) }
    }

    fun line(inp: DayFill.Input, fr: DayFill.Frame, mem: DayFill.Memory, ws0: List<LookAhead.Week>? = null): DayFill.SprintLine? {
        val ws = ws0 ?: if (inp.ahead) LookAhead.weeks(inp) else null
        val today = inp.now.date
        val week = DayFill.weekOf(today).toString()
        val pick = mem.sprint?.takeIf { it.week == week }
        // Taken: the line stays that day only, saying so (a tap opens it again, as in the mock), then the cards' tag is enough.
        if (pick?.taken == true && pick.takenOn != today.toString()) return null
        if (fr.mode == DayFill.Mode.AFTER_NIGHT) return null
        val chosen = propose(inp, pick?.swap ?: 0, ws)
        if (chosen.isEmpty()) return null
        // With the look-ahead, rounded as its tags are ("แน่น 12 ชม." and "ราว 12 ชม." under it).
        val hours = ws?.firstOrNull()?.hours ?: (weekLeft(inp, ws) / 60)
        val late = chosen.firstOrNull { it.rank == 0 }
        // The look-ahead's rule 5: a far deadline with the time nearly not enough needs a start this week.
        val start = late?.takeIf { ws != null && it.far && it.need <= (it.freeBefore ?: 0) }
        val note = late?.let { r ->
            val short = maxOf(1, (r.need - (r.freeBefore ?: 0) + 59) / 60)
            if (start != null) tr("${r.project} ต้องเริ่มสัปดาห์นี้ถึงจะทันวันส่ง ${DayFill.shortDay(r.due!!)}", "${r.project} needs a start this week to make ${r.due}")
            else tr("${r.project} อาจไม่ทันวันส่ง ${DayFill.shortDay(r.due!!)} ขาดราว $short ชม.", "${r.project} may miss ${r.due}, about $short h short")
        } ?: tr("เวลาว่างนอกเวรสัปดาห์นี้ราว $hours ชม.", "About $hours h free outside shifts this week")
        val lines = buildList {
            if (start != null && ws != null) easiest(inp, start, ws)?.let(::add)
            add(tr("เวลาว่างนอกเวรสัปดาห์นี้ราว $hours ชม. (หักเวลากินข้าว เวลาของตัวเอง และช่องกันชนแล้ว)", "About $hours h free this week after meals, own time and buffer"))
            chosen.forEach { r ->
                add(
                    if (r.due != null) tr("${r.project}: ต้องใช้ราว ${(r.need + 59) / 60} ชม. ก่อนวันส่ง ${DayFill.shortDay(r.due)} ว่างก่อนวันนั้นราว ${(r.freeBefore ?: 0) / 60} ชม.", "${r.project}: about ${(r.need + 59) / 60} h before ${r.due}")
                    else tr("${r.project}: ไม่มีวันส่ง ได้ช่องทำต่อวันละช่อง", "${r.project}: no deadline, one slot a day"),
                )
            }
        }
        // The tags of mock v2 only: "เริ่มสัปดาห์นี้" for rule 5, else a tight or the lightest week. A deadline that will be
        // missed, or a usual week, keeps the line as it was.
        val hint = when {
            ws == null -> null
            start != null -> DayFill.Hint(
                tr("เริ่มสัปดาห์นี้", "Start this week"),
                tr("ต้องใช้ราว ${(start.need + 59) / 60} ชม. ว่างก่อนวันส่งเหลือราว ${(start.freeBefore ?: 0) / 60} ชม.", "About ${(start.need + 59) / 60} h needed, about ${(start.freeBefore ?: 0) / 60} h free before the deadline"),
                DayFill.Tone.HEAVY,
            )
            late != null -> null
            else -> LookAhead.weekHint(ws, 0)
        }
        val lead = start?.dueTitle?.let { t -> start.due?.let { d -> t + tr(" ส่ง ", ", due ") + d.dayOfMonth + " " + Countdown.monthShort(d) } }
        if (pick?.taken == true) return DayFill.SprintLine(pick.projects.joinToString(", "), note, late != null, lines, pick.projects, taken = true, hint = hint, lead = lead)
        return DayFill.SprintLine(chosen.joinToString(", ") { it.project }, note, late != null, lines, chosen.map { it.project }, hint = hint, lead = lead)
    }

    /**
     * Rule 5's easiest way to make a far deadline, in the shape of mock round 1 ("อาทิตย์ 8 กับ 15 วันละ 2 ชม. แล้วเป็น
     * สปรินต์ในสัปดาห์เบา 16 ถึง 22"): two hours a day on the days with the most time first (the whole free days), until
     * the work (doubled) fits. The whole free days are said by date, the other days as their week's sprint.
     */
    private fun easiest(inp: DayFill.Input, r: Ranked, ws: List<LookAhead.Week>): String? {
        val due = r.due ?: return null
        val today = inp.now.date
        val free = (0L..(due.toEpochDays() - today.toEpochDays()).toLong()).map { today.plusDays(it) }
            .map { it to LookAhead.usableCounted(it, inp, ws) }.filter { it.second >= 60 }
        val whole = free.map { it.first }.filter { it > today && LookAhead.freeDay(it, inp) }.toSet()
        val order = free.sortedWith(compareBy({ -it.second }, { it.first }))
        // Two hours a day at most when that is enough; when not, the days' whole time.
        fun pick(cap: Int): List<Pair<LocalDate, Int>>? {
            var left = r.need
            val out = ArrayList<Pair<LocalDate, Int>>()
            for ((d, m) in order) {
                if (left <= 0) break
                val take = minOf(cap, m, left)
                out += d to take
                left -= take
            }
            return out.takeIf { left <= 0 && it.isNotEmpty() }
        }
        val picked = (pick(LookAhead.LONG) ?: pick(Int.MAX_VALUE) ?: return null).sortedBy { it.first }
        fun one(d: LocalDate, m: Int) = d to DayFill.shortDay(d) + " " + DayFill.lenText(m)
        val parts = picked.filter { it.first in whole }.map { (d, m) -> one(d, m) } +
            picked.filter { it.first !in whole }.groupBy { DayFill.weekOf(it.first) }.values.map { days ->
                val a = days.first().first
                val b = days.last().first
                if (days.size == 1) one(a, days.single().second)
                else a to tr("สปรินต์ ", "sprint ") + (if (a.monthNumber == b.monthNumber) "${a.dayOfMonth}" else "${a.dayOfMonth} ${Countdown.monthShort(a)}") +
                    tr(" ถึง ", " to ") + b.dayOfMonth + " " + Countdown.monthShort(b) + " " + DayFill.lenText(days.sumOf { it.second })
            }
        return tr("ทางที่ทันสบายสุด: ", "The easiest way: ") + parts.sortedBy { it.first }.joinToString(", ") { it.second }
    }
}
