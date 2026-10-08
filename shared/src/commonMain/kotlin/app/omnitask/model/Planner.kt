package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.math.abs
import app.omnitask.time.*

/**
 * "อยากทำ X ควรทำตอนไหน": reads the request, guesses how long and what kind of effort it takes,
 * then ranks free calendar slots against shifts, the profile and the tasks already due that day.
 * Rule-based, so it runs offline and costs nothing.
 */
object Planner {

    enum class Kind(val minutes: Int, val buffer: Int) {
        MOVE(45, 30), DEEP(90, 0), QUICK(20, 0), ERRAND(60, 30), GENERAL(60, 0);

        val label: String
            get() = when (this) {
                MOVE -> tr("ใช้แรง", "Physical")
                DEEP -> tr("ใช้สมอง", "Deep work")
                QUICK -> tr("งานสั้น", "Quick")
                ERRAND -> tr("ธุระนอกบ้าน", "Errand")
                GENERAL -> tr("ทั่วไป", "General")
            }

        val blurb: String
            get() = when (this) {
                MOVE -> tr("เป็นงานใช้แรง ไม่ต้องคิดมาก", "It is physical, little thinking needed.")
                DEEP -> tr("เป็นงานที่ต้องใช้สมองต่อเนื่อง", "It needs unbroken focus.")
                QUICK -> tr("เป็นงานสั้น แทรกช่วงว่างได้", "It is short and fits in a gap.")
                ERRAND -> tr("ต้องออกไปข้างนอก จึงต้องเป็นเวลาที่ร้านหรือหน่วยงานเปิด", "It means going out, so it has to be during opening hours.")
                GENERAL -> tr("เป็นงานทั่วไป", "It is a general task.")
            }
    }

    data class Slot(val day: LocalDate, val start: LocalTime, val end: LocalTime, val title: String, val why: String, val score: Int) {
        val dayLabel get() = "${SHORT_DAYS[day.dayOfWeek.isoDayNumber - 1]} ${day.dayOfMonth}"
    }

    data class Plan(val request: String, val title: String, val kind: Kind, val minutes: Int, val intro: String, val slots: List<Slot>)

    val SHORT_DAYS: List<String>
        get() = if (Lang.english) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("จ", "อ", "พ", "พฤ", "ศ", "ส", "อา")

    private val MOVE_WORDS = listOf(
        "วิ่ง", "ออกกำลัง", "ยิม", "ฟิตเนส", "ว่ายน้ำ", "ปั่น", "โยคะ", "เดิน", "เตะบอล", "แบด", "เวท", "run", "gym",
        "exercise", "workout", "swim", "cycling", "bike", "yoga", "walk", "jog", "football", "badminton", "tennis", "weights",
    )
    private val DEEP_WORDS = listOf(
        "เขียน", "อ่าน", "proposal", "สไลด์", "paper", "เรียน", "ทบทวน", "วิเคราะห์", "วางแผน", "เตรียม", "สอบ", "โค้ด", "งานวิจัย", "รายงาน",
        "write", "read", "slides", "study", "review", "analy", "plan", "prepare", "exam", "code", "research", "report", "thesis",
    )
    private val QUICK_WORDS = listOf(
        "โทร", "ตอบ", "ส่ง", "อีเมล", "ไลน์", "จ่าย", "โอน", "นัด", "เช็ค", "ยืนยัน",
        "call", "reply", "send", "email", "message", "pay", "transfer", "book", "check", "confirm",
    )
    private val ERRAND_WORDS = listOf(
        "ซื้อ", "ธนาคาร", "ไปรษณีย์", "ร้าน", "ตัดผม", "หาหมอ", "ทำฟัน", "ล้างรถ", "อำเภอ", "ห้าง",
        "buy", "shopping", "groceries", "bank", "post office", "shop", "store", "haircut", "doctor", "dentist", "car wash", "mall",
    )

    fun kindOf(text: String): Kind {
        val t = text.lowercase()
        return when {
            MOVE_WORDS.any { it in t } -> Kind.MOVE
            ERRAND_WORDS.any { it in t } -> Kind.ERRAND
            DEEP_WORDS.any { it in t } -> Kind.DEEP
            QUICK_WORDS.any { it in t } -> Kind.QUICK
            else -> Kind.GENERAL
        }
    }

    private val MINUTES = Regex("""(\d+)\s*(?:นาที|min)""")
    private val HOURS = Regex("""(\d+(?:\.\d+)?)\s*(?:ชม|ชั่วโมง|hour|hr)""")

    fun minutesOf(text: String, kind: Kind): Int = explicitMinutes(text) ?: kind.minutes

    /** The duration written in the request ("45 นาที", "2 ชม."), or null so the assistant asks. */
    fun explicitMinutes(text: String): Int? =
        MINUTES.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: HOURS.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 60).toInt() }

    /** Which days the request is about. */
    fun daysOf(text: String, today: LocalDate): List<LocalDate> {
        val t = text.lowercase()
        return when {
            "วันนี้" in t || "today" in t || "tonight" in t -> listOf(today)
            "พรุ่งนี้" in t || "tomorrow" in t -> listOf(today.plusDays(1))
            "สัปดาห์หน้า" in t || "อาทิตย์หน้า" in t || "next week" in t -> {
                val mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                (0L..6L).map { mon.plusDays(it) }
            }
            "สุดสัปดาห์" in t || "เสาร์อาทิตย์" in t || "วันหยุด" in t || "weekend" in t || "day off" in t -> {
                val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                listOf(sat, sat.plusDays(1)).let { if (today.dayOfWeek == DayOfWeek.SUNDAY) listOf(today) + it else it }
            }
            else -> (0L..6L).map { today.plusDays(it) }
        }
    }

    /**
     * The date range a look-ahead or planning request is about: this or next week, this or next month,
     * "14 วัน", tomorrow or today. Null when the text names none.
     */
    fun rangeOf(text: String, today: LocalDate): Pair<LocalDate, LocalDate>? {
        val t = text.lowercase()
        Regex("""(\d+)\s*(?:วัน|days?)""").find(t)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in 1..92 }?.let { return today to today.plusDays(it - 1) }
        return when {
            "เดือนหน้า" in t || "next month" in t -> today.plusMonths(1).withDayOfMonth(1).let { it to it.with(TemporalAdjusters.lastDayOfMonth()) }
            "เดือนนี้" in t || "this month" in t -> today to today.with(TemporalAdjusters.lastDayOfMonth())
            "สัปดาห์หน้า" in t || "อาทิตย์หน้า" in t || "next week" in t -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
            "สัปดาห์นี้" in t || "this week" in t -> today to today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            "พรุ่งนี้" in t || "tomorrow" in t -> today.plusDays(1).let { it to it }
            "วันนี้" in t || "today" in t -> today to today
            else -> null
        }
    }

    private val FILLER = listOf(
        "ควรไปตอนไหนดี", "ควรทำตอนไหนดี", "ตอนไหนดี", "ควรไปตอนไหน", "ควรทำตอนไหน", "ตอนไหน", "เมื่อไหร่ดี", "เมื่อไรดี",
        "สักครั้ง", "สักวัน", "สัปดาห์นี้", "สัปดาห์หน้า", "อาทิตย์หน้า", "สุดสัปดาห์", "วันนี้", "พรุ่งนี้", "หน่อย", "ครับ", "ค่ะ", "คะ",
    )
    private val EN_WANT = Regex("""^(?:i\s+(?:want|need|have|would like)\s+to|i'd like to|want to|need to)\s+""", RegexOption.IGNORE_CASE)
    private val EN_FILLER = Regex(
        """\b(?:when should i|what time should i|when is (?:a good|the best) time to|when can i|this weekend|next week|this week|tomorrow|today|tonight|sometime|please)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "อยากไปวิ่ง 5 กม. สักครั้งสัปดาห์นี้ ควรไปตอนไหนดี" becomes "ไปวิ่ง 5 กม.". */
    fun titleOf(text: String): String {
        var t = text.trim().removePrefix("อยากจะ").removePrefix("อยาก").removePrefix("ต้อง").trim()
        t = t.replace(EN_WANT, "")
        FILLER.forEach { t = t.replace(it, " ") }
        t = t.replace(EN_FILLER, " ")
        t = t.replace(Regex("""[?？]"""), " ").replace(Regex("""\s+"""), " ").trim()
        return t.ifEmpty { text.trim() }
    }

    fun plan(text: String, tasks: List<Task>, events: List<CalendarEvent>, profile: Profile, now: LocalDateTime, minutesOverride: Int? = null): Plan {
        val kind = kindOf(text)
        val minutes = minutesOverride ?: minutesOf(text, kind)
        val need = minutes + kind.buffer
        val today = now.toLocalDate()
        val slots = daysOf(text, today).flatMapIndexed { index, day -> slotsOn(day, index, kind, minutes, need, tasks, events, profile, now) }
            .sortedByDescending { it.score }
        // One option per day first, so the three shown are spread out; the rest come after "ดูช่วงอื่น".
        val firstPerDay = slots.distinctBy { it.day }
        val ordered = firstPerDay + (slots - firstPerDay.toSet())
        val extra = if (kind.buffer > 0) tr(" + เผื่ออีก ${kind.buffer} นาที", " + ${kind.buffer} min buffer") else ""
        val intro = tr("ใช้เวลาราว ${duration(minutes)}$extra ${kind.blurb} ", "About ${duration(minutes)}$extra. ${kind.blurb} ") +
            if (ordered.isEmpty()) {
                tr("แต่ช่วงที่ถามยังไม่มีเวลาว่างพอเลย ลองถามช่วงอื่นดูนะ", "But there is not enough free time then. Try asking about other days.")
            } else {
                tr("ผมเลือกช่วงที่ไม่ชนนัดและเวร", "I picked times that avoid your events and shifts.")
            }
        return Plan(text, titleOf(text), kind, minutes, intro, ordered)
    }

    /** Ranked slots for work of this kind and length over the given days. */
    fun slotsFor(kind: Kind, minutes: Int, days: List<LocalDate>, tasks: List<Task>, events: List<CalendarEvent>, profile: Profile, now: LocalDateTime): List<Slot> =
        days.flatMapIndexed { index, day -> slotsOn(day, index, kind, minutes, minutes + kind.buffer, tasks, events, profile, now) }
            .sortedByDescending { it.score }

    /** One task placed in a range plan. */
    data class Proposal(val task: Task, val slot: Slot, val minutes: Int)

    /**
     * Places tasks that have no date yet into the free time of [days], most urgent first. Each placed task
     * blocks its slot for the next, so two never land on the same time. Tasks with no room are left out.
     */
    fun planRange(candidates: List<Task>, days: List<LocalDate>, tasks: List<Task>, events: List<CalendarEvent>, profile: Profile, now: LocalDateTime): List<Proposal> {
        val busy = events.toMutableList()
        val out = ArrayList<Proposal>()
        candidates.forEach { t ->
            val kind = kindOf(t.title)
            val minutes = minutesOf(t.title, kind)
            // Spread the work: each task already placed on a day makes that day a little less attractive.
            val slot = slotsFor(kind, minutes, days, tasks, busy, profile, now)
                .maxByOrNull { sl -> sl.score - 8 * out.count { it.slot.day == sl.day } } ?: return@forEach
            out += Proposal(t, slot, minutes)
            busy += CalendarEvent(-1L - out.size, t.title, slot.day.atTime(slot.start), slot.day.atTime(slot.start).plusMinutes((minutes + kind.buffer).toLong()))
        }
        return out.sortedWith(compareBy({ it.slot.day }, { it.slot.start }))
    }

    private fun slotsOn(
        day: LocalDate, index: Int, kind: Kind, minutes: Int, need: Int,
        tasks: List<Task>, events: List<CalendarEvent>, profile: Profile, now: LocalDateTime,
    ): List<Slot> {
        fun isShift(e: CalendarEvent) = profile.shiftWords.any { e.title.contains(it, ignoreCase = true) }
        fun isNight(e: CalendarEvent) = isShift(e) && profile.nightWords.any { e.title.contains(it, ignoreCase = true) }
        fun on(d: LocalDate) = events.filter { it.begin < d.plusDays(1).atStartOfDay() && it.end > d.atStartOfDay() }

        val todays = on(day)
        val shifts = todays.filter(::isShift)
        val nightToday = todays.any(::isNight)
        val nightBefore = on(day.minusDays(1)).any(::isNight)
        val earlyTomorrow = on(day.plusDays(1)).any { isShift(it) && !it.allDay && it.begin.toLocalTime() < LocalTime.of(9, 0) }
        val dueThatDay = tasks.count { it.isOpen && it.due == day }

        var from = day.atTime(profile.wake).plusMinutes(30)
        val until = day.atTime(profile.sleep).minusMinutes(60)
        if (day == now.toLocalDate()) from = maxOf(from, roundUp(now.plusMinutes(15)))
        if (from >= until) return emptyList()

        // Free gaps, keeping 15 minutes clear around every timed event.
        val busy = todays.filter { !it.allDay }.map { it.begin.minusMinutes(15) to it.end.plusMinutes(15) }.sortedBy { it.first }
        val gaps = ArrayList<Pair<LocalDateTime, LocalDateTime>>()
        var cursor = from
        busy.forEach { (b, e) ->
            if (b > cursor) gaps += cursor to minOf(b, until)
            if (e > cursor) cursor = e
        }
        if (cursor < until) gaps += cursor to until
        val fits = gaps.filter { (b, e) -> ChronoUnit.MINUTES.between(b, e) >= need }
        if (fits.isEmpty()) return emptyList()

        val anchors = when (kind) {
            Kind.MOVE -> listOf(profile.exercise, profile.wake.plusMinutes(15))
            Kind.DEEP -> listOf(profile.focusFrom, LocalTime.of(13, 30))
            Kind.QUICK -> listOf(from.toLocalTime())
            Kind.ERRAND -> listOf(LocalTime.of(10, 0), LocalTime.of(13, 30), LocalTime.of(16, 0))
            Kind.GENERAL -> listOf(profile.focusFrom, LocalTime.of(13, 30), LocalTime.of(19, 0))
        }

        return anchors.mapNotNull { anchor ->
            val want = day.atTime(anchor)
            fits.map { (b, e) ->
                val latest = e.minusMinutes(need.toLong())
                val start = roundUp(if (want < b) b else if (want > latest) latest else want).let { if (it > latest) latest else it }
                start to e
            }.minByOrNull { (s, _) -> abs(ChronoUnit.MINUTES.between(want, s)) }?.let { (start, gapEnd) ->
                val reasons = ArrayList<String>()
                var score = 100 - index * 3
                val off = abs(ChronoUnit.MINUTES.between(want, start))
                score -= (off / 15).toInt() * 2
                val t = start.toLocalTime()
                when (kind) {
                    Kind.MOVE -> if (off <= 30 && anchor == profile.exercise) reasons += tr("ตรงกับเวลาออกกำลังกายที่คุณตั้งไว้ (${Profile.hm(profile.exercise)})", "Matches your usual exercise time (${Profile.hm(profile.exercise)}).")
                    Kind.DEEP -> if (t >= profile.focusFrom && t < profile.focusTo) {
                        reasons += tr(
                            "อยู่ในช่วงสมองดีของคุณ ${Profile.hm(profile.focusFrom)} ถึง ${Profile.hm(profile.focusTo)}",
                            "In your best focus hours, ${Profile.hm(profile.focusFrom)} to ${Profile.hm(profile.focusTo)}.",
                        )
                        score += 10
                    }
                    Kind.ERRAND -> if (t < LocalTime.of(9, 0) || t > LocalTime.of(17, 0)) { score -= 25; reasons += tr("ร้านหรือหน่วยงานอาจยังไม่เปิด", "Places may be closed.") }
                    else -> Unit
                }
                if (profile.bestDay == day.dayOfWeek && kind == Kind.DEEP) { score += 12; reasons += tr("เป็นวันที่คุณทำงานได้ดี", "One of your most productive days.") }
                if (nightToday) {
                    score -= if (kind == Kind.MOVE || kind == Kind.DEEP) 45 else 25
                    reasons += tr("วันนี้มีเวรดึก ควรพักให้มาก จึงไม่แนะนำ", "Night shift that day, so rest instead. Not recommended.")
                } else if (shifts.isNotEmpty()) {
                    score -= if (kind == Kind.DEEP) 30 else 12
                    reasons += if (shifts.all { !it.allDay && it.end <= start }) {
                        tr("หลังเลิกเวร อาจเหนื่อยอยู่บ้าง", "After a shift, you may be tired.")
                    } else {
                        tr("วันนั้นมีเวร", "You have a shift that day.")
                    }
                }
                if (nightBefore && kind != Kind.QUICK) { score -= 20; reasons += tr("เพิ่งลงเวรดึกเมื่อคืน", "Just off a night shift.") }
                if (earlyTomorrow && t >= LocalTime.of(19, 30)) { score -= 20; reasons += tr("พรุ่งนี้มีเวรเช้า ไม่ควรดึก", "Early shift next day, so not too late.") }
                if (dueThatDay >= 3 && kind != Kind.QUICK) { score -= 10; reasons += tr("วันนั้นมีงานครบกำหนด $dueThatDay งาน", "$dueThatDay tasks due that day.") }
                reasons += tr("ว่างถึง ${Profile.hm(gapEnd.toLocalTime())}", "Free until ${Profile.hm(gapEnd.toLocalTime())}.")
                Slot(day, t, t.plusMinutes(minutes.toLong()), titleFor(t, shifts, start, nightToday), reasons.joinToString(" "), score)
            }
        }.distinctBy { it.start }
    }

    private fun titleFor(t: LocalTime, shifts: List<CalendarEvent>, start: LocalDateTime, night: Boolean): String {
        val part = when {
            t < LocalTime.of(12, 0) -> tr("ช่วงเช้า", "Morning")
            t < LocalTime.of(16, 0) -> tr("ช่วงบ่าย", "Afternoon")
            t < LocalTime.of(19, 0) -> tr("ช่วงเย็น", "Evening")
            else -> tr("ช่วงค่ำ", "Night")
        }
        val context = when {
            night -> tr("ก่อนเวรดึก", "before night shift")
            shifts.isEmpty() -> tr("ไม่มีเวร", "no shift")
            shifts.all { !it.allDay && it.end <= start } -> tr("หลังเลิกเวร", "after shift")
            shifts.all { !it.allDay && it.begin >= start } -> tr("ก่อนเข้าเวร", "before shift")
            else -> tr("วันที่มีเวร", "shift day")
        }
        return tr("$part $context", "$part, $context")
    }

    private fun roundUp(t: LocalDateTime): LocalDateTime {
        val m = t.minute % 15
        val base = t.withSecond(0).withNano(0)
        return if (m == 0 && t.second == 0) base else base.plusMinutes((15 - m).toLong())
    }

    fun duration(minutes: Int) = when {
        minutes < 60 -> tr("$minutes นาที", "$minutes min")
        minutes % 60 == 0 -> tr("${minutes / 60} ชั่วโมง", "${minutes / 60} h")
        else -> tr("${minutes / 60} ชั่วโมง ${minutes % 60} นาที", "${minutes / 60} h ${minutes % 60} min")
    }

    /** The TaskForge line for a chosen slot, in TaskForge token order. */
    fun taskLine(title: String, slot: Slot, today: LocalDate): String =
        "- [ ] $title #remind-at-scheduled 🎯 ${Profile.hm(slot.start)} ➕ $today ⏳ ${slot.day}"
}
