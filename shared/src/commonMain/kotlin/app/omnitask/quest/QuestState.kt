package app.omnitask.quest

import app.omnitask.data.VaultText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * One thing the owner did in the game: picked a tribe, bought a sword, claimed a quest, ended a timed session.
 * [at] is the device's time, `yyyy-MM-ddTHH:mm:ss`. What each field means depends on [act] (see [QuestLog]).
 */
@Serializable
data class QuestAction(
    val id: String,
    val at: String,
    val act: String,
    val a: String? = null,
    val b: String? = null,
    val n: Int? = null,
    val gold: Int? = null,
    val kills: Int? = null,
) {
    val time: LocalDateTime? get() = runCatching { LocalDateTime.parse(at) }.getOrNull()
}

/**
 * The game's own file, `omni-quest.json` in the Omni folder: one action per line, only ever added to, the same way
 * as the behavior log, so two devices playing offline join line by line. The game never writes the task notes.
 */
object QuestLog {
    const val NAME = "omni-quest.json"
    const val PATH = "${VaultText.OMNI_DIR}/$NAME"
    const val HEADER = """{"omni":"quest","version":1}"""

    const val START = "start"
    const val TRIBE = "tribe"
    const val NAME_SET = "name"
    const val LOOK = "look"
    const val MASCOT = "mascot"
    const val JOB = "job"
    const val BUY = "buy"
    const val SELL = "sell"
    const val EQUIP = "equip"
    const val LEARN = "learn"
    const val LOADOUT = "loadout"
    const val CLAIM = "claim"
    /** A timed work session: [QuestAction.a] the task, [QuestAction.n] seconds, gold and kills won, [QuestAction.b] a drop. */
    const val IDLE = "idle"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    fun parse(text: String?): List<QuestAction> = entries(text).map { it.second }

    private fun entries(text: String?): List<Pair<String, QuestAction>> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        return text.lines().mapNotNull { raw ->
            val line = raw.trim().removeSuffix(",")
            if (!line.startsWith("{")) return@mapNotNull null
            val a = runCatching { json.decodeFromString(QuestAction.serializer(), line) }.getOrNull() ?: return@mapNotNull null
            if (a.id.isBlank() || a.act.isBlank() || a.time == null || !seen.add(a.id)) null else line to a
        }
    }

    fun line(a: QuestAction): String = json.encodeToString(QuestAction.serializer(), a)

    /** The file's text with [add] at the end; lines already there keep their place and text. */
    fun merge(text: String?, add: List<QuestAction>): String {
        val kept = entries(text)
        val known = kept.mapTo(HashSet()) { it.second.id }
        val fresh = add.filter { it.time != null && known.add(it.id) }.map { line(it) to it }
        if (text != null && fresh.isEmpty()) return text
        return (listOf(HEADER) + (kept + fresh).map { it.first }).joinToString("\n", postfix = "\n")
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    fun stamp(at: LocalDateTime): String = "${at.date}T${pad(at.hour)}:${pad(at.minute)}:${pad(at.second)}"

    fun newId(src: String, at: LocalDateTime, random: Random = Random): String =
        "q-$src-${stamp(at).filter { it.isDigit() }}-${random.nextInt(36 * 36 * 36 * 36).toString(36).padStart(4, '0')}"

    fun action(act: String, at: LocalDateTime, src: String, a: String? = null, b: String? = null, n: Int? = null, gold: Int? = null, kills: Int? = null) =
        QuestAction(newId(src, at), stamp(at), act, a, b, n, gold, kills)
}

/** The character's look; each value is an index into [QuestWorld]'s lists. */
@Serializable
data class Look(val hair: Int = 0, val hairColor: Int = 0, val skin: Int = 0, val outfit: Int = 0)

/** One finished timed session, for the idle page's history and today's quest. */
data class Session(val day: LocalDate, val task: String, val seconds: Int, val gold: Int, val kills: Int, val drop: String?)

/**
 * Everything the owner chose and owns, from replaying the log. Gold here is only what the game itself moved
 * (bought, sold, claimed, won in battle); [QuestXp.Result.gold] adds what the tasks earned.
 */
data class QuestState(
    val start: LocalDate? = null,
    val name: String = "",
    val tribe: Tribe? = null,
    val mascot: Tribe? = null,
    val job: Job = Job.NOVICE,
    val look: Look = Look(),
    val owned: List<String> = QuestWorld.starter,
    val equipped: Map<Slot, String> = QuestWorld.starter.mapNotNull { id -> QuestWorld.item(id)?.let { it.slot to id } }.toMap(),
    val learned: Set<String> = emptySet(),
    val loadout: List<String> = emptyList(),
    val claimed: Set<String> = emptySet(),
    val goldIn: Long = 0,
    val goldOut: Long = 0,
    val sessions: List<Session> = emptyList(),
) {
    val begun get() = start != null && tribe != null

    /** Gold to spend now. */
    fun balance(xp: QuestXp.Result) = xp.gold + goldIn - goldOut

    /** Skill points: one per level after the first, less those spent. */
    fun skillPoints(level: Int) = (level - 1 - learned.size).coerceAtLeast(0)

    fun secondsOn(day: LocalDate) = sessions.filter { it.day == day }.sumOf { it.seconds }

    /** Mascots this character can have by their side: their own tribe's, then more as they grow. */
    fun mascots(level: Int): List<Tribe> {
        val own = tribe ?: return emptyList()
        return when {
            level >= QuestWorld.ALL_MASCOTS_LEVEL -> listOf(own) + Tribe.entries.filter { it != own }
            level >= QuestWorld.SECOND_MASCOT_LEVEL -> listOf(own, Tribe.entries[(own.ordinal + 1) % Tribe.entries.size])
            else -> listOf(own)
        }
    }

    companion object {
        fun replay(actions: List<QuestAction>): QuestState {
            var s = QuestState()
            for (a in actions.sortedBy { it.at }) s = s.apply(a)
            return s
        }
    }

    fun apply(a: QuestAction): QuestState = when (a.act) {
        QuestLog.START -> if (start == null) copy(start = a.time?.date) else this
        QuestLog.TRIBE -> Tribe.entries.firstOrNull { it.name == a.a }?.let { t -> copy(tribe = t, mascot = mascot ?: t) } ?: this
        QuestLog.NAME_SET -> copy(name = a.a.orEmpty().take(24))
        QuestLog.MASCOT -> Tribe.entries.firstOrNull { it.name == a.a }?.let { copy(mascot = it) } ?: this
        QuestLog.LOOK -> {
            val v = a.n ?: 0
            when (a.a) {
                "hair" -> copy(look = look.copy(hair = v))
                "hairColor" -> copy(look = look.copy(hairColor = v))
                "skin" -> copy(look = look.copy(skin = v))
                "outfit" -> copy(look = look.copy(outfit = v))
                else -> this
            }
        }
        QuestLog.JOB -> Job.entries.firstOrNull { it.name == a.a }?.let { copy(job = it) } ?: this
        QuestLog.BUY -> QuestWorld.item(a.a)?.let { copy(owned = owned + it.id, goldOut = goldOut + (a.gold ?: it.price)) } ?: this
        QuestLog.SELL -> if (a.a != null && a.a in owned) {
            val left = owned.toMutableList().apply { remove(a.a) }
            val wearing = equipped.filterValues { it != a.a || a.a in left }
            copy(owned = left, equipped = wearing, goldIn = goldIn + (a.gold ?: 0))
        } else this
        QuestLog.EQUIP -> QuestWorld.item(a.a)?.takeIf { it.id in owned }?.let { copy(equipped = equipped + (it.slot to it.id)) } ?: this
        QuestLog.LEARN -> QuestWorld.skill(a.a)?.let { copy(learned = learned + it.id) } ?: this
        QuestLog.LOADOUT -> copy(loadout = a.a.orEmpty().split(',').filter { it.isNotBlank() && it in learned })
        QuestLog.CLAIM -> if (a.a != null && a.a !in claimed) copy(claimed = claimed + a.a, goldIn = goldIn + (a.gold ?: 0)) else this
        QuestLog.IDLE -> {
            val day = a.time?.date
            if (day == null) this else copy(
                goldIn = goldIn + (a.gold ?: 0),
                owned = if (a.b != null && QuestWorld.item(a.b) != null) owned + a.b else owned,
                sessions = sessions + Session(day, a.a.orEmpty(), a.n ?: 0, a.gold ?: 0, a.kills ?: 0, a.b),
            )
        }
        else -> this
    }
}

/** The character as it fights: stats with equipment and passive skills, and what they make. */
data class Fighter(
    val level: Int,
    val stats: Map<Stat, Int>,
    val atk: Int,
    val matk: Int,
    val def: Int,
    val hp: Int,
    /** Attacks per second. */
    val speed: Double,
    /** Chance of a critical hit, 0 to 1. */
    val crit: Double,
    /** Extra gold from battles, 0.12 for +12%. */
    val goldBonus: Double,
) {
    /** One number to compare against a zone's recommended power. */
    val power: Int get() = ((atk + matk) * speed * (1 + crit) + def + hp / 10).toInt()

    companion object {
        fun of(xp: QuestXp.Result, s: QuestState, timing: Boolean = false): Fighter {
            val gear = s.equipped.values.mapNotNull { QuestWorld.item(it) }
            val passives = s.learned.mapNotNull { QuestWorld.skill(it) }.filter { it.kind == SkillKind.PASSIVE }
            val raw = Stat.entries.associateWith { st -> xp.stats.getValue(st) + gear.sumOf { it.bonus[st] ?: 0 } }.toMutableMap()
            fun boost(st: Stat, pct: Int) { raw[st] = (raw.getValue(st) * (100 + pct) / 100.0).toInt() }
            if ("blaze-heart" in s.learned) boost(Stat.STR, 12)
            if ("moon-wisdom" in s.learned) boost(Stat.INT, 12)
            if ("tailwind" in s.learned) boost(Stat.AGI, 12)
            if ("blessing" in s.learned) Stat.entries.forEach { boost(it, 15) }
            val st = raw.toMap()
            val l = xp.level
            var atk = l + st.getValue(Stat.STR) * 2 + st.getValue(Stat.DEX) / 2 + gear.sumOf { it.atk }
            var matk = st.getValue(Stat.INT) * 2 + st.getValue(Stat.DEX) / 3 + gear.sumOf { it.matk }
            if (timing && passives.any { it.id == "focus" }) {
                atk = atk * 110 / 100
                matk = matk * 110 / 100
            }
            val def = st.getValue(Stat.VIT) + gear.sumOf { it.def }
            var hp = 40 + l * 8 + st.getValue(Stat.VIT) * 6
            if ("deep-root" in s.learned) hp = hp * 112 / 100
            return Fighter(
                level = l, stats = st, atk = atk, matk = matk, def = def, hp = hp,
                speed = 0.6 + st.getValue(Stat.AGI) * 0.02 + st.getValue(Stat.DEX) * 0.005,
                crit = (0.03 + st.getValue(Stat.LUK) * 0.004).coerceAtMost(0.5),
                goldBonus = if ("lucky-bubble" in s.learned) 0.12 else 0.0,
            )
        }
    }
}
