package app.omnitask.quest

import app.omnitask.model.tr
import kotlin.random.Random

/**
 * The idle battle that runs while the owner times a task: the party walks through a zone and fights one foe after
 * another, by itself. Everything comes from the session's seed and the elapsed seconds, so the page can be closed
 * and opened again (or the phone turned off) and the fight is where it would have been.
 */
object Idle {

    /** Time past this counts for nothing, so a timer left running overnight earns no more than a long session. */
    const val MAX_SECONDS = 3 * 60 * 60

    /** A session shorter than this is not kept. */
    const val MIN_SECONDS = 60

    data class Zone(val id: String, private val th: String, private val en: String, val minLevel: Int, val foes: List<Pair<String, String>>, val emoji: String) {
        val name get() = tr(th, en)
        fun foeName(i: Int): String = foes[i % foes.size].let { tr(it.first, it.second) }
    }

    val zones = listOf(
        Zone("mist", "ป่าหมอกจาง", "Thin Mist Woods", 1, listOf("สไลม์งานค้าง" to "Backlog slime", "ภูตผัดวัน" to "Procrastination wisp", "เห็ดง่วงนอน" to "Sleepy mushroom"), "🌫️"),
        Zone("cave", "ถ้ำแจ้งเตือน", "Cave of Pings", 10, listOf("ค้างคาวแจ้งเตือน" to "Ping bat", "ก้อนหินลืมตอบ" to "Unanswered golem", "งูอีเมล" to "Inbox serpent"), "🦇"),
        Zone("tower", "หอคอยเอกสาร", "Paperwork Tower", 20, listOf("กระดาษบินได้" to "Flying form", "ตราประทับยักษ์" to "Giant stamp", "แฟ้มหลงทาง" to "Lost folder"), "📜"),
        Zone("castle", "ปราสาทผัดวัน", "Castle of Tomorrow", 35, listOf("อัศวินพรุ่งนี้" to "Knight of Tomorrow", "นาฬิกาเลื่อนนัด" to "Snooze clock", "เงาเดดไลน์" to "Deadline shade"), "🏰"),
        Zone("crown", "ยอดยกดราซิล", "Crown of Ygdrasil", 60, listOf("มังกรหมอก" to "Mist dragon", "ราชาผัดวัน" to "King of Later", "ผู้เฝ้าราก" to "Root warden"), "🌳"),
    )

    fun zoneFor(level: Int): Zone = zones.last { level >= it.minLevel }

    data class Foe(val index: Int, val kind: Int, val name: String, val maxHp: Int, val gold: Int, val from: Double, val to: Double, val boss: Boolean)

    /** Where the fight stands [seconds] into a session. */
    data class Frame(
        val zone: Zone,
        val foe: Foe,
        /** How much of the current foe's HP is left, 1 to 0. */
        val hpLeft: Double,
        val kills: Int,
        val gold: Int,
        val drop: String?,
        val seconds: Int,
    )

    /** How long a foe takes to fall, in seconds: tougher foes and weaker fighters take longer, within bounds. */
    private fun killTime(hp: Int, f: Fighter): Double {
        val dps = (f.atk + f.matk) * f.speed * (1 + f.crit * 0.5)
        return (hp / dps.coerceAtLeast(0.1)).coerceIn(15.0, 60.0)
    }

    fun frame(seed: Long, f: Fighter, seconds: Int): Frame {
        val t = seconds.coerceIn(0, MAX_SECONDS).toDouble()
        val zone = zoneFor(f.level)
        var at = 0.0
        var kills = 0
        var gold = 0
        var drop: String? = null
        var i = 0
        while (true) {
            val r = Random(seed * 31 + i)
            val boss = i > 0 && i % 25 == 0
            val hp = ((30 + f.level * 12) * (0.8 + r.nextDouble() * 0.5) * (if (boss) 4 else 1)).toInt()
            val coins = ((1 + f.level / 10) * (if (boss) 10 else 1) * (1 + f.goldBonus)).toInt().coerceAtLeast(1)
            val end = at + killTime(hp, f) * (if (boss) 2 else 1)
            val kind = if (boss) i % zone.foes.size else r.nextInt(zone.foes.size)
            val foe = Foe(i, kind, (if (boss) tr("บอส: ", "Boss: ") else "") + zone.foeName(kind), hp, coins, at, end, boss)
            if (end > t) return Frame(zone, foe, 1 - (t - at) / (end - at), kills, gold, drop, t.toInt())
            kills++
            gold += coins
            if (drop == null && (boss || r.nextInt(150) == 0)) {
                drop = QuestWorld.drops.filter { (QuestWorld.item(it)?.minLevel ?: 99) <= f.level + 5 }.randomOrNull(r)
            }
            at = end
            i++
        }
    }
}
