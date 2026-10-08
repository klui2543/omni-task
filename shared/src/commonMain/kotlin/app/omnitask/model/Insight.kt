package app.omnitask.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.math.abs
import app.omnitask.time.*

/**
 * Patterns the assistant noticed and wants to confirm before remembering ("จำไว้ไหม?").
 * It only ever asks; nothing lands in the profile until the owner says yes.
 */
object Insight {

    data class Ask(val id: String, val text: String, val remember: String, val window: String, val apply: (Profile) -> Profile)

    /** A completion the app saw happen: when, and what kind of work it was. */
    data class Done(val at: LocalDateTime, val kind: Planner.Kind)

    fun next(tasks: List<Task>, log: List<Done>, profile: Profile, today: LocalDate, declined: Set<String>): Ask? =
        listOfNotNull(focusWindow(log, profile, today), exerciseTime(log, profile, today), bestDay(tasks, profile, today))
            .firstOrNull { it.id !in declined }

    private fun focusWindow(log: List<Done>, profile: Profile, today: LocalDate): Ask? {
        val deep = log.filter { it.kind == Planner.Kind.DEEP && it.at.toLocalDate() > today.minusDays(28) }
        if (deep.size < 5) return null
        val (hour, count) = (5..19).map { h -> h to deep.count { it.at.hour in h until h + 3 } }.maxBy { it.second }
        if (count < deep.size * 0.6 || abs(hour - profile.focusFrom.hour) < 2) return null
        val range = "${hhmm(hour, 0)} ถึง ${hhmm(hour + 3, 0)}"
        val enRange = "${hhmm(hour, 0)} to ${hhmm(hour + 3, 0)}"
        return Ask(
            "focus:$hour",
            tr(
                "ดูเหมือนคุณปิดงานที่ใช้สมองได้บ่อยช่วง $range ($count จาก ${deep.size} งาน)",
                "You often finish deep work around $enRange ($count of ${deep.size} tasks)",
            ),
            // [Ask.remember] is written into the profile note, which stays Thai.
            "ช่วงสมองดีคือ $range",
            tr("สังเกต 4 สัปดาห์", "Seen over 4 weeks"),
        ) { it.copy(focusFrom = LocalTime.of(hour, 0), focusTo = LocalTime.of(hour + 3, 0)) }
    }

    private fun exerciseTime(log: List<Done>, profile: Profile, today: LocalDate): Ask? {
        val moves = log.filter { it.kind == Planner.Kind.MOVE && it.at.toLocalDate() > today.minusDays(28) }
        if (moves.size < 4) return null
        val minutes = moves.map { it.at.hour * 60 + it.at.minute }.sorted()
        val median = minutes[minutes.size / 2] / 15 * 15
        val usual = LocalTime.of(median / 60, median % 60)
        if (abs(median - (profile.exercise.hour * 60 + profile.exercise.minute)) < 60) return null
        return Ask(
            "move:$median",
            tr(
                "ดูเหมือนคุณออกกำลังกายราว ${Profile.hm(usual)} บ่อยที่สุด (${moves.size} ครั้ง)",
                "You usually exercise around ${Profile.hm(usual)} (${moves.size} times)",
            ),
            "ออกกำลังกายราว ${Profile.hm(usual)}",
            tr("สังเกต 4 สัปดาห์", "Seen over 4 weeks"),
        ) { it.copy(exercise = usual) }
    }

    private fun bestDay(tasks: List<Task>, profile: Profile, today: LocalDate): Ask? {
        val done = tasks.mapNotNull { it.done }.filter { it > today.minusDays(21) && it <= today }
        if (done.size < 10) return null
        val (day, count) = DayOfWeek.entries.map { d -> d to done.count { it.dayOfWeek == d } }.maxBy { it.second }
        if (count < done.size * 0.3 || day == profile.bestDay) return null
        val name = Profile.DAY_NAMES[day.isoDayNumber - 1]
        val enName = Profile.dayName(day)
        return Ask(
            "day:${day.name}",
            tr(
                "ดูเหมือนคุณปิดงานได้มากที่สุดวัน$name ($count จาก ${done.size} งาน)",
                "You finish the most tasks on $enName ($count of ${done.size} tasks)",
            ),
            "วัน$name เป็นวันที่ทำงานได้ดี",
            tr("สังเกต 3 สัปดาห์", "Seen over 3 weeks"),
        ) { it.copy(bestDay = day) }
    }
}
