package app.omnitask.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

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
        val range = "%02d:00 ถึง %02d:00".format(hour, hour + 3)
        return Ask(
            "focus:$hour",
            "ดูเหมือนคุณปิดงานที่ใช้สมองได้บ่อยช่วง $range ($count จาก ${deep.size} งาน)",
            "ช่วงสมองดีคือ $range",
            "สังเกต 4 สัปดาห์",
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
            "ดูเหมือนคุณออกกำลังกายราว ${Profile.hm(usual)} บ่อยที่สุด (${moves.size} ครั้ง)",
            "ออกกำลังกายราว ${Profile.hm(usual)}",
            "สังเกต 4 สัปดาห์",
        ) { it.copy(exercise = usual) }
    }

    private fun bestDay(tasks: List<Task>, profile: Profile, today: LocalDate): Ask? {
        val done = tasks.mapNotNull { it.done }.filter { it > today.minusDays(21) && it <= today }
        if (done.size < 10) return null
        val (day, count) = DayOfWeek.entries.map { d -> d to done.count { it.dayOfWeek == d } }.maxBy { it.second }
        if (count < done.size * 0.3 || day == profile.bestDay) return null
        val name = Profile.DAY_NAMES[day.value - 1]
        return Ask(
            "day:${day.name}",
            "ดูเหมือนคุณปิดงานได้มากที่สุดวัน$name ($count จาก ${done.size} งาน)",
            "วัน$name เป็นวันที่ทำงานได้ดี",
            "สังเกต 3 สัปดาห์",
        ) { it.copy(bestDay = day) }
    }
}
