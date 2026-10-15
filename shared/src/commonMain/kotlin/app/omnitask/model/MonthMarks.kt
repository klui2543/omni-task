package app.omnitask.model

import app.omnitask.data.VaultText
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.omnitask.time.*

/**
 * What the owner puts on the month widget by hand: coloured frames around the days to focus on, and emoji on days.
 * Kept in the vault as `หลังบ้าน/Omni/omni-calendar.json`, so a cleared phone or another one gets them back.
 *
 * A frame is the days chosen together that touch on the month grid (side by side, a week apart, or Saturday then
 * Sunday). Days chosen together that do not touch become separate frames, and a day belongs to one frame at most:
 * framing it again takes it out of the frame it was in.
 */
@Serializable
data class MonthMarks(
    val version: Int = 1,
    /** When this copy was saved, in epoch milliseconds; of the phone's copy and the vault's, the later one wins. */
    val savedAt: Long = 0,
    val frames: List<Frame> = emptyList(),
    /** Emoji per day, keyed by the ISO date, in the order they were added. */
    val stickers: Map<String, List<String>> = emptyMap(),
) {
    @Serializable
    data class Frame(val days: List<LocalDate>, val color: FrameColor, val label: String = "") {
        val first get() = days.first()
        val last get() = days.last()

        /** Which day of the frame [day] is, from 1, and how many days it has; null when [day] is not in it. */
        fun position(day: LocalDate): Pair<Int, Int>? = days.indexOf(day).takeIf { it >= 0 }?.let { it + 1 to days.size }
    }

    fun frameOn(day: LocalDate): Frame? = frames.firstOrNull { day in it.days }

    fun stickersOn(day: LocalDate): List<String> = stickers[day.toString()].orEmpty()

    /** The frames with a day in [first, last], earliest first. */
    fun framesBetween(first: LocalDate, last: LocalDate): List<Frame> =
        frames.filter { f -> f.days.any { it >= first && it <= last } }.sortedBy { it.first }

    /** Frames [selection] in [color]: each group of touching days becomes one frame, taken out of any frame it was in. */
    fun withFrame(selection: Set<LocalDate>, color: FrameColor, label: String): MonthMarks {
        if (selection.isEmpty()) return this
        val added = pieces(selection).map { Frame(it, color, label.trim()) }
        return withoutFrames(selection).let { it.copy(frames = (it.frames + added).sortedBy { f -> f.first }) }
    }

    /** Takes [selection] out of every frame; a frame cut in two carries on as two frames. */
    fun withoutFrames(selection: Set<LocalDate>): MonthMarks {
        if (selection.isEmpty()) return this
        val kept = frames.flatMap { f ->
            val left = f.days.toSet() - selection
            if (left.size == f.days.size) listOf(f) else pieces(left).map { f.copy(days = it) }
        }
        return copy(frames = kept)
    }

    /** Drops [frame] as a whole, for editing it: the caller frames the new choice of days next. */
    fun without(frame: Frame): MonthMarks = copy(frames = frames - frame)

    /** Sets the emoji on [day]; an empty list clears the day. */
    fun withStickers(day: LocalDate, emoji: List<String>): MonthMarks {
        val clean = emoji.map { it.trim() }.filter { it.isNotEmpty() }
        val key = day.toString()
        return copy(stickers = if (clean.isEmpty()) stickers - key else stickers + (key to clean))
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val PATH = "${VaultText.OMNI_DIR}/omni-calendar.json"

        /** Most emoji a day keeps; the widget shows three and counts the rest. */
        const val MAX_STICKERS = 6

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

        fun decode(text: String?): MonthMarks? = text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        /** The copy saved last; the vault's when they tie, so every phone ends up with the same marks. */
        fun newer(local: MonthMarks, vault: MonthMarks?): MonthMarks = if (vault != null && vault.savedAt >= local.savedAt) vault else local

        /** Splits [days] into groups that touch on the grid, each sorted, the groups in date order. */
        fun pieces(days: Set<LocalDate>): List<List<LocalDate>> {
            val left = days.toMutableSet()
            val out = ArrayList<List<LocalDate>>()
            while (left.isNotEmpty()) {
                val start = left.min()
                left -= start
                val group = mutableListOf(start)
                var i = 0
                while (i < group.size) {
                    val d = group[i++]
                    for (n in listOf(d.minusDays(1), d.plusDays(1), d.minusDays(7), d.plusDays(7))) {
                        if (left.remove(n)) group += n
                    }
                }
                out += group.sorted()
            }
            return out.sortedBy { it.first() }
        }
    }
}

/** The colours a frame can have; the apps pick the shade. */
@Serializable
enum class FrameColor {
    @SerialName("red") RED,
    @SerialName("amber") AMBER,
    @SerialName("green") GREEN,
    @SerialName("blue") BLUE,
    @SerialName("violet") VIOLET,
    ;

    /** A shade that reads on a dark and a light page alike. */
    val argb: Long
        get() = when (this) {
            RED -> 0xFFD9726C
            AMBER -> 0xFFC4A56A
            GREEN -> 0xFF7FAE93
            BLUE -> 0xFF8AA6D6
            VIOLET -> 0xFFA893C9
        }
}

/** The month as the widget lays it out: weeks from Sunday, in Thai with the Buddhist year. */
object MonthGrid {

    /** The Sunday the grid of [month]'s month starts on. */
    fun start(month: LocalDate): LocalDate {
        val first = month.withDayOfMonth(1)
        return first.minusDays((first.dayOfWeek.ordinal + 1L) % 7)
    }

    /** How many weeks the month spans: 4 to 6. */
    fun weeks(month: LocalDate): Int {
        val first = month.withDayOfMonth(1)
        val lead = (first.dayOfWeek.ordinal + 1) % 7
        return (lead + first.lengthOfMonth() + 6) / 7
    }

    /** Every day on the grid, a week to a row. */
    fun days(month: LocalDate): List<LocalDate> {
        val start = start(month)
        return (0 until weeks(month) * 7).map { start.plusDays(it.toLong()) }
    }

    fun isSunday(day: LocalDate) = day.dayOfWeek == DayOfWeek.SUNDAY

    /**
     * Which sides of [day]'s cell a frame of [days] draws. A side is left open where the frame carries on: the next
     * or previous day (so a frame running from Saturday into Sunday stays open at both ends of the break), or the
     * same weekday a week before or after.
     */
    data class Sides(val top: Boolean, val bottom: Boolean, val left: Boolean, val right: Boolean)

    fun sides(day: LocalDate, days: Set<LocalDate>) = Sides(
        top = day.minusDays(7) !in days,
        bottom = day.plusDays(7) !in days,
        left = day.minusDays(1) !in days,
        right = day.plusDays(1) !in days,
    )

    /** "ตุลาคม 2569", or "October 2026" in English. */
    fun title(month: LocalDate): String =
        if (Lang.english) "${EN_MONTHS[month.monthNumber - 1]} ${month.year}" else "${TH_MONTHS[month.monthNumber - 1]} ${month.year + 543}"

    /** "15 ต.ค." or "15 Oct". */
    fun dayMonth(day: LocalDate): String =
        "${day.dayOfMonth} " + if (Lang.english) EN_MONTHS[day.monthNumber - 1].take(3) else TH_SHORT[day.monthNumber - 1]

    /** Weekday heads from Sunday. */
    val heads get() = if (Lang.english) listOf("S", "M", "T", "W", "T", "F", "S") else listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส")

    private val TH_MONTHS = listOf(
        "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม",
    )
    private val TH_SHORT = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
    private val EN_MONTHS = listOf(
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
    )
}

/**
 * Which month the month widget shows and which day's work it lists, and how each tap changes that. Every tap on a
 * day lists that day, whether it was meant or not: tapping the chosen day again keeps it, and a day from the next or
 * previous month (the faint ones at the grid's edges) brings that month up. Today is reached by tapping it or the
 * title. Left alone for [IDLE_MS], the widget goes back to today. [touchedAt] is when it was last tapped, in epoch
 * milliseconds.
 */
data class MonthNav(val offset: Int = 0, val picked: LocalDate? = null, val touchedAt: Long = 0) {

    /** What the widget shows at [now]: this state, or today's month after it was left alone long enough. */
    fun fresh(now: Long): MonthNav = if (touchedAt != 0L && now - touchedAt > IDLE_MS) MonthNav() else this

    fun tapDay(day: LocalDate, today: LocalDate, now: Long): MonthNav =
        MonthNav(offset = monthsBetween(today, day), picked = day.takeIf { it != today }, touchedAt = now)

    /** ‹ and ›: the chosen day stays, so its list is still there after looking at another month. */
    fun shift(delta: Int, now: Long): MonthNav = fresh(now).copy(offset = fresh(now).offset + delta, touchedAt = now)

    fun home(now: Long): MonthNav = MonthNav(touchedAt = now)

    /** The day whose work is listed; null lists today and tomorrow. */
    fun listed(today: LocalDate): LocalDate? = picked?.takeIf { it != today }

    companion object {
        const val IDLE_MS = 10 * 60 * 1000L

        fun monthsBetween(from: LocalDate, to: LocalDate) = (to.year - from.year) * 12 + (to.monthNumber - from.monthNumber)
    }
}
