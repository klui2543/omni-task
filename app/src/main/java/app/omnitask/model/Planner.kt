package app.omnitask.model

import app.omnitask.notify.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * "อยากทำ X ควรทำตอนไหน": reads the request, guesses how long and what kind of effort it takes,
 * then ranks free calendar slots against shifts, the profile and the tasks already due that day.
 * Rule-based, so it runs offline and costs nothing.
 */
object Planner {

    enum class Kind(val label: String, val minutes: Int, val buffer: Int, val blurb: String) {
        MOVE("ใช้แรง", 45, 30, "เป็นงานใช้แรง ไม่ต้องคิดมาก"),
        DEEP("ใช้สมอง", 90, 0, "เป็นงานที่ต้องใช้สมองต่อเนื่อง"),
        QUICK("งานสั้น", 20, 0, "เป็นงานสั้น แทรกช่วงว่างได้"),
        ERRAND("ธุระนอกบ้าน", 60, 30, "ต้องออกไปข้างนอก จึงต้องเป็นเวลาที่ร้านหรือหน่วยงานเปิด"),
        GENERAL("ทั่วไป", 60, 0, "เป็นงานทั่วไป"),
    }

    data class Slot(val day: LocalDate, val start: LocalTime, val end: LocalTime, val title: String, val why: String, val score: Int) {
        val dayLabel get() = "${SHORT_DAYS[day.dayOfWeek.value - 1]} ${day.dayOfMonth}"
    }

    data class Plan(val request: String, val title: String, val kind: Kind, val minutes: Int, val intro: String, val slots: List<Slot>)

    val SHORT_DAYS = listOf("จ", "อ", "พ", "พฤ", "ศ", "ส", "อา")

    private val MOVE_WORDS = listOf("วิ่ง", "ออกกำลัง", "ยิม", "ฟิตเนส", "ว่ายน้ำ", "ปั่น", "โยคะ", "เดิน", "เตะบอล", "แบด", "เวท", "run", "gym")
    private val DEEP_WORDS = listOf("เขียน", "อ่าน", "proposal", "สไลด์", "paper", "เรียน", "ทบทวน", "วิเคราะห์", "วางแผน", "เตรียม", "สอบ", "โค้ด", "งานวิจัย", "รายงาน")
    private val QUICK_WORDS = listOf("โทร", "ตอบ", "ส่ง", "อีเมล", "ไลน์", "จ่าย", "โอน", "นัด", "เช็ค", "ยืนยัน")
    private val ERRAND_WORDS = listOf("ซื้อ", "ธนาคาร", "ไปรษณีย์", "ร้าน", "ตัดผม", "หาหมอ", "ทำฟัน", "ล้างรถ", "อำเภอ", "ห้าง")

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

    fun minutesOf(text: String, kind: Kind): Int =
        MINUTES.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: HOURS.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 60).toInt() }
            ?: kind.minutes

    /** Which days the request is about. */
    fun daysOf(text: String, today: LocalDate): List<LocalDate> = when {
        "วันนี้" in text -> listOf(today)
        "พรุ่งนี้" in text -> listOf(today.plusDays(1))
        "สัปดาห์หน้า" in text || "อาทิตย์หน้า" in text -> {
            val mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            (0L..6L).map { mon.plusDays(it) }
        }
        "สุดสัปดาห์" in text || "เสาร์อาทิตย์" in text || "วันหยุด" in text -> {
            val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            listOf(sat, sat.plusDays(1)).let { if (today.dayOfWeek == DayOfWeek.SUNDAY) listOf(today) + it else it }
        }
        else -> (0L..6L).map { today.plusDays(it) }
    }

    private val FILLER = listOf(
        "ควรไปตอนไหนดี", "ควรทำตอนไหนดี", "ตอนไหนดี", "ควรไปตอนไหน", "ควรทำตอนไหน", "ตอนไหน", "เมื่อไหร่ดี", "เมื่อไรดี",
        "สักครั้ง", "สักวัน", "สัปดาห์นี้", "สัปดาห์หน้า", "อาทิตย์หน้า", "สุดสัปดาห์", "วันนี้", "พรุ่งนี้", "หน่อย", "ครับ", "ค่ะ", "คะ",
    )

    /** "อยากไปวิ่ง 5 กม. สักครั้งสัปดาห์นี้ ควรไปตอนไหนดี" becomes "ไปวิ่ง 5 กม.". */
    fun titleOf(text: String): String {
        var t = text.trim().removePrefix("อยากจะ").removePrefix("อยาก").removePrefix("ต้อง").trim()
        FILLER.forEach { t = t.replace(it, " ") }
        t = t.replace(Regex("""[?？]"""), " ").replace(Regex("""\s+"""), " ").trim()
        return t.ifEmpty { text.trim() }
    }

    fun plan(text: String, tasks: List<Task>, events: List<CalendarEvent>, profile: Profile, now: LocalDateTime): Plan {
        val kind = kindOf(text)
        val minutes = minutesOf(text, kind)
        val need = minutes + kind.buffer
        val today = now.toLocalDate()
        val slots = daysOf(text, today).flatMapIndexed { index, day -> slotsOn(day, index, kind, minutes, need, tasks, events, profile, now) }
            .sortedByDescending { it.score }
        // One option per day first, so the three shown are spread out; the rest come after "ดูช่วงอื่น".
        val firstPerDay = slots.distinctBy { it.day }
        val ordered = firstPerDay + (slots - firstPerDay.toSet())
        val extra = if (kind.buffer > 0) " + เผื่ออีก ${kind.buffer} นาที" else ""
        val intro = "ใช้เวลาราว ${duration(minutes)}$extra ${kind.blurb} " +
            if (ordered.isEmpty()) "แต่ช่วงที่ถามยังไม่มีเวลาว่างพอเลย ลองถามช่วงอื่นดูนะ" else "ผมเลือกช่วงที่ไม่ชนนัดและเวร"
        return Plan(text, titleOf(text), kind, minutes, intro, ordered)
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
                    Kind.MOVE -> if (off <= 30 && anchor == profile.exercise) reasons += "ตรงกับเวลาออกกำลังกายที่คุณตั้งไว้ (${Profile.hm(profile.exercise)})"
                    Kind.DEEP -> if (t >= profile.focusFrom && t < profile.focusTo) {
                        reasons += "อยู่ในช่วงสมองดีของคุณ ${Profile.hm(profile.focusFrom)} ถึง ${Profile.hm(profile.focusTo)}"
                        score += 10
                    }
                    Kind.ERRAND -> if (t < LocalTime.of(9, 0) || t > LocalTime.of(17, 0)) { score -= 25; reasons += "ร้านหรือหน่วยงานอาจยังไม่เปิด" }
                    else -> Unit
                }
                if (profile.bestDay == day.dayOfWeek && kind == Kind.DEEP) { score += 12; reasons += "เป็นวันที่คุณทำงานได้ดี" }
                if (nightToday) {
                    score -= if (kind == Kind.MOVE || kind == Kind.DEEP) 45 else 25
                    reasons += "วันนี้มีเวรดึก ควรพักให้มาก จึงไม่แนะนำ"
                } else if (shifts.isNotEmpty()) {
                    score -= if (kind == Kind.DEEP) 30 else 12
                    reasons += if (shifts.all { !it.allDay && it.end <= start }) "หลังเลิกเวร อาจเหนื่อยอยู่บ้าง" else "วันนั้นมีเวร"
                }
                if (nightBefore && kind != Kind.QUICK) { score -= 20; reasons += "เพิ่งลงเวรดึกเมื่อคืน" }
                if (earlyTomorrow && t >= LocalTime.of(19, 30)) { score -= 20; reasons += "พรุ่งนี้มีเวรเช้า ไม่ควรดึก" }
                if (dueThatDay >= 3 && kind != Kind.QUICK) { score -= 10; reasons += "วันนั้นมีงานครบกำหนด $dueThatDay งาน" }
                reasons += "ว่างถึง ${Profile.hm(gapEnd.toLocalTime())}"
                Slot(day, t, t.plusMinutes(minutes.toLong()), titleFor(t, shifts, start, nightToday), reasons.joinToString(" "), score)
            }
        }.distinctBy { it.start }
    }

    private fun titleFor(t: LocalTime, shifts: List<CalendarEvent>, start: LocalDateTime, night: Boolean): String {
        val part = when {
            t < LocalTime.of(12, 0) -> "ช่วงเช้า"
            t < LocalTime.of(16, 0) -> "ช่วงบ่าย"
            t < LocalTime.of(19, 0) -> "ช่วงเย็น"
            else -> "ช่วงค่ำ"
        }
        val context = when {
            night -> "ก่อนเวรดึก"
            shifts.isEmpty() -> "ไม่มีเวร"
            shifts.all { !it.allDay && it.end <= start } -> "หลังเลิกเวร"
            shifts.all { !it.allDay && it.begin >= start } -> "ก่อนเข้าเวร"
            else -> "วันที่มีเวร"
        }
        return "$part $context"
    }

    private fun roundUp(t: LocalDateTime): LocalDateTime {
        val m = t.minute % 15
        val base = t.withSecond(0).withNano(0)
        return if (m == 0 && t.second == 0) base else base.plusMinutes((15 - m).toLong())
    }

    fun duration(minutes: Int) = when {
        minutes < 60 -> "$minutes นาที"
        minutes % 60 == 0 -> "${minutes / 60} ชั่วโมง"
        else -> "${minutes / 60} ชั่วโมง ${minutes % 60} นาที"
    }

    /** The TaskForge line for a chosen slot, in TaskForge token order. */
    fun taskLine(title: String, slot: Slot, today: LocalDate): String =
        "- [ ] $title #remind-at-scheduled 🎯 ${Profile.hm(slot.start)} ➕ $today ⏳ ${slot.day}"
}
