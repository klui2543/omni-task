package app.omnitask.model

import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * How tired the owner is likely to be today, from what the app already has (owner's pick ก, 2026-10-30): the watch's
 * sleep over the last two weeks, the night shifts of the last week and the tasks that keep being moved. Nothing to fill
 * in. One night alone does not say it: the sleep owed is counted against the owner's own usual night, not a fixed 8 h.
 */
object Fatigue {
    enum class Level { OK, TIRED, VERY_TIRED }

    data class Read(
        val level: Level,
        /** Minutes of sleep short of the usual night, over the last [WEEK] nights. */
        val owed: Int,
        /** Last night was short by more than [SHORT] minutes. */
        val lastShort: Boolean,
        val nights: Int,
        val oftenMoved: Int,
        /** The reasons, in the owner's words, for the strip on top of the plan; empty when [level] is [Level.OK]. */
        val why: List<String>,
        /** Minutes the plan really stops earlier (0 when a shift already stops it sooner), set by the day's frame. */
        val stopped: Int = 0,
    ) {
        /** Minutes the plan is to stop earlier. */
        val stopEarlier: Int get() = when (level) { Level.OK -> 0; Level.TIRED -> 30; Level.VERY_TIRED -> 60 }
    }

    const val WEEK = 7
    /** A night this much under the usual counts as short. */
    const val SHORT = 90
    /** Sleep owed over the week: from this much the owner is tired, and from twice it very tired. */
    const val OWED = 150
    /** Night shifts in the last week: from this many the owner is tired, and from one more very tired. */
    const val NIGHTS = 2
    /** Tasks moved three times or more: from this many, a sign the days are too full. */
    const val MOVED = 3

    /**
     * [day]'s read. [sleeps] are the watch's (null: switch off, then nothing is read and the plan stays as it was);
     * [nights] the night shifts that began in the [WEEK] days before [day]; [oftenMoved] the open tasks moved three
     * times or more; [own] tells the evenings whose night is the owner's own (not one a shift set, which [nights]
     * already counts). Null too while the watch has under [SleepTimes.MIN_NIGHTS] nights: not enough to say.
     */
    fun read(day: LocalDate, sleeps: List<SleepSession>?, nights: Int, oftenMoved: Int, own: (LocalDate) -> Boolean = { true }): Read? {
        if (sleeps == null) return null
        val lengths = (1..SleepTimes.DAYS).associateWith { i -> day.minusDays(i.toLong()).takeIf(own)?.let { SleepTimes.night(sleeps, it)?.minutes } }
        val usual = SleepTimes.usual(day, { true }) { d -> lengths[(day.toEpochDays() - d.toEpochDays()).toInt()] } ?: return null
        val owed = (1..WEEK).sumOf { i -> lengths[i]?.let { maxOf(0, usual - it) } ?: 0 }
        val lastShort = lengths[1]?.let { it < usual - SHORT } == true
        // One short night alone does not make a tired day (the owner's point): it is told, not counted.
        var score = 0
        if (owed >= 2 * OWED) score += 2 else if (owed >= OWED) score += 1
        if (nights > NIGHTS) score += 2 else if (nights >= NIGHTS) score += 1
        if (oftenMoved >= MOVED) score += 1
        val level = when {
            score >= 4 -> Level.VERY_TIRED
            score >= 2 -> Level.TIRED
            else -> Level.OK
        }
        val why = if (level == Level.OK) emptyList() else listOfNotNull(
            owed.takeIf { it >= OWED }?.let { tr("นอนน้อยกว่าปกติรวม ${dur(it)} ใน 7 คืน", "${dur(it)} less sleep than usual over 7 nights") },
            tr("เมื่อคืนนอนน้อย", "a short night").takeIf { lastShort },
            nights.takeIf { it >= NIGHTS }?.let { tr("เวรดึก $it ครั้งใน 7 วัน", "$it night shifts in 7 days") },
            oftenMoved.takeIf { it >= MOVED }?.let { tr("มีงานถูกเลื่อนซ้ำ $it งาน", "$it tasks moved again and again") },
        )
        return Read(level, owed, lastShort, nights, oftenMoved, why)
    }

    private fun dur(m: Int): String = if (m % 60 == 0) tr("${m / 60} ชม.", "${m / 60} h") else tr("${m / 60} ชม. ${m % 60} นาที", "${m / 60} h ${m % 60} min")

    /** The strip's line: how tired, why, and what the plan does about it. */
    fun text(r: Read): String? = when (r.level) {
        Level.OK -> null
        Level.TIRED -> tr("วันนี้น่าจะล้า (", "Likely tired today (") + r.why.joinToString(", ") +
            tr(") เลขาจัดเบาลง เผื่อเวลาว่างมากขึ้น", "): a lighter plan, more free time") + earlier(r)
        Level.VERY_TIRED -> tr("วันนี้น่าจะล้ามาก (", "Likely very tired today (") + r.why.joinToString(", ") +
            tr(") เลขาจัดเฉพาะที่จำเป็น เผื่อเวลาว่างมากขึ้น", "): only what must be done, more free time") + earlier(r)
    }

    /** "และหยุดจัดงานเร็วขึ้น ..." only when the plan really stops earlier. */
    private fun earlier(r: Read): String = if (r.stopped <= 0) "" else tr(" และหยุดจัดงานเร็วขึ้น ", ", stops ") + dur(r.stopped).let { if (r.stopped < 60) tr("${r.stopped} นาที", "${r.stopped} min") else it } + tr("", " earlier")
}
