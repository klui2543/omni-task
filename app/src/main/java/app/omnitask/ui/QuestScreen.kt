package app.omnitask.ui

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.quest.Fighter
import app.omnitask.quest.Idle
import app.omnitask.quest.Item
import app.omnitask.quest.Job
import app.omnitask.quest.MascotVoice
import app.omnitask.quest.QuestLog
import app.omnitask.quest.QuestState
import app.omnitask.quest.QuestWorld
import app.omnitask.quest.QuestXp
import app.omnitask.quest.Quests
import app.omnitask.quest.SkillKind
import app.omnitask.quest.Slot
import app.omnitask.quest.Stat
import app.omnitask.quest.Story
import app.omnitask.quest.Tribe
import app.omnitask.time.*
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlin.math.sin

/** The task being timed: its title, when it started (epoch ms), and the seed of its idle battle. Kept in preferences. */
data class QuestTimer(val title: String, val start: Long, val seed: Long) {
    fun seconds(now: Long): Int = ((now - start) / 1000).toInt().coerceAtLeast(0)

    fun save(prefs: SharedPreferences) = prefs.edit().putString(KEY, "$start\t$seed\t$title").apply()

    companion object {
        const val KEY = "quest.timer"

        fun load(prefs: SharedPreferences): QuestTimer? = prefs.getString(KEY, null)?.split('\t', limit = 3)?.takeIf { it.size == 3 }?.let {
            runCatching { QuestTimer(it[2], it[0].toLong(), it[1].toLong()) }.getOrNull()
        }

        fun clear(prefs: SharedPreferences) = prefs.edit().remove(KEY).apply()
    }
}

val UiState.questState: QuestState get() = QuestState.replay(questLog)

/** What the vault earned the character: the live note's finished tasks (parked ones too) and the archive's. */
fun UiState.questXp(q: QuestState = questState): QuestXp.Result =
    QuestXp.compute(tasks + parked + questArchive, questMoves, q.start, q.tribe, today)

fun UiState.fighter(timing: Boolean = false): Fighter {
    val q = questState
    return Fighter.of(questXp(q), q, timing)
}

fun UiState.questBoard(q: QuestState): List<Quests.Quest> =
    Quests.board(tasks + parked + questArchive, events, q, questMoves, profile.shiftWords, LocalDateTime.now())

/** Stand-in art until the drawn mascots arrive (`docs/rpg-design.md`, section 14). */
val Tribe.emoji: String
    get() = when (this) {
        Tribe.IGNIS -> "🐉"
        Tribe.LUNA -> "🦉"
        Tribe.ZEPHYR -> "🦊"
        Tribe.AQUA -> "🐋"
        Tribe.SYLVA -> "🦌"
    }

val Tribe.tint: Color get() = Color(color)

private val ZONE_FOES = mapOf(
    "mist" to listOf("🫧", "👻", "🍄"),
    "cave" to listOf("🦇", "🪨", "🐍"),
    "tower" to listOf("📄", "🔖", "📁"),
    "castle" to listOf("🛡️", "⏰", "🌑"),
    "crown" to listOf("🐲", "👑", "🌲"),
)

private enum class QuestTab(private val th: String, private val en: String) {
    QUESTS("เควส", "Quests"), STORY("เนื้อเรื่อง", "Story"), GEAR("อุปกรณ์", "Gear"), SHOP("ร้านค้า", "Shop"), SKILLS("สกิล", "Skills"), HERO("ตัวละคร", "Character");

    val label get() = tr(th, en)
}

/** Omni Quest's main page: the character card, the mascot's word, and the guild's tabs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestScreen(state: UiState, vm: TaskViewModel, onIdle: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val q = remember(state.questLog) { state.questState }
    val xp = remember(state.tasks, state.parked, state.questArchive, state.questMoves, q.start, q.tribe, state.today) { state.questXp(q) }
    if (!q.begun) {
        CreateCharacter(state, vm, xp, onBack)
        return
    }
    val fighter = remember(xp, q) { Fighter.of(xp, q) }
    val board = remember(state.tasks, state.questArchive, state.events, q, state.questMoves) { state.questBoard(q) }
    val gold = q.balance(xp)
    var tab by rememberSaveable { mutableStateOf(QuestTab.QUESTS) }
    val facts = remember(state.tasks, state.events, q, xp.level, state.today) { state.storyFacts(q, xp) }
    val cast = Story.Cast(q.name, q.tribe ?: Tribe.LUNA, q.mascot ?: q.tribe ?: Tribe.LUNA, Quests.mainBranch(state.tasks + state.questArchive, state.today))
    val openScenes = remember(facts) { Story.open(facts) }
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    // The story begins as soon as the character is made.
    LaunchedEffect(q.begun) { if (q.begun && "c1-1" !in q.seen) playing = "c1-1" }
    playing?.let { Story.scene(it) }?.let { scene ->
        SceneScreen(scene, cast) { finished ->
            if (finished && scene.id !in q.seen) vm.questAct(QuestLog.SCENE, a = scene.id, gold = scene.gold)
            playing = null
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquareButton(Ic.back, tr("กลับ", "Back"), onBack)
                Text("Omni Quest", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, color = C.text)
                Pill("🪙 $gold", C.amberSoft, C.amber)
            }
        }
        item { CharacterCard(q, xp, fighter) }
        item {
            val now = LocalDateTime.now()
            val facts = MascotVoice.Facts(
                overdue = state.tasks.count { it.isOpen && it.parent == null && (it.due ?: it.scheduled)?.let { d -> d < state.today } == true },
                dueToday = state.tasks.count { it.isOpen && it.parent == null && (it.due == state.today || it.scheduled == state.today) },
                focusMinutes = q.secondsOn(state.today) / 60,
                boss = board.firstOrNull { it.kind == Quests.Kind.BOSS && !it.done }?.title?.substringAfter(": "),
                claimable = board.count { it.claimable },
                asleep = state.profile.let { p ->
                    if (p.sleep > p.wake) now.time >= p.sleep || now.time < p.wake else now.time >= p.sleep && now.time < p.wake
                },
            )
            val mascot = q.mascot ?: q.tribe ?: Tribe.LUNA
            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(mascot.tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Text(mascot.emoji, fontSize = 24.sp)
                    }
                    Text(MascotVoice.line(mascot, facts), Modifier.weight(1f).padding(start = 12.dp), color = C.text, fontSize = TS.body)
                }
            }
        }
        item { TimerStrip(state, onIdle) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QuestTab.entries.forEach { t ->
                    val count = when (t) {
                        QuestTab.QUESTS -> board.count { it.claimable }
                        QuestTab.STORY -> openScenes.size
                        else -> 0
                    }
                    val badge = if (count > 0) " $count" else ""
                    Chip(t.label + badge, tab == t, { tab = t })
                }
            }
        }
        when (tab) {
            QuestTab.QUESTS -> items(board, key = { it.id }) { quest -> QuestRow(quest) { vm.questAct(QuestLog.CLAIM, a = quest.id, gold = quest.gold) } }
            QuestTab.STORY -> item { StoryTab(state, q, xp, facts, cast, openScenes) { playing = it } }
            QuestTab.GEAR -> item { GearTab(q, xp.level, vm) }
            QuestTab.SHOP -> item { ShopTab(q, xp.level, gold, vm) }
            QuestTab.SKILLS -> item { SkillsTab(q, xp.level, vm) }
            QuestTab.HERO -> item { HeroTab(q, xp, vm) }
        }
    }
}

@Composable
private fun CharacterCard(q: QuestState, xp: QuestXp.Result, f: Fighter) {
    val tribe = q.tribe ?: return
    Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Chibi(q, Modifier.size(104.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(q.name.ifBlank { tr("นักผจญภัย", "Adventurer") }, color = C.text, fontSize = TS.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Lv ${xp.level}  ${tr("เผ่า", "")}${tribe.label}  ${q.job.label}",
                    color = tribe.tint, fontSize = TS.caption, fontWeight = FontWeight.Medium,
                )
                Bar(if (xp.maxed) 1f else xp.into.toFloat() / xp.span, tribe.tint, Modifier.padding(top = 8.dp))
                Text(
                    if (xp.maxed) tr("เลเวลสูงสุดแล้ว", "Max level") else tr("XP ${xp.into}/${xp.span}  วันนี้ +${xp.today.toInt()}", "XP ${xp.into}/${xp.span}  today +${xp.today.toInt()}"),
                    Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.micro,
                )
            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Stat.entries.forEach { s ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${f.stats.getValue(s)}", color = if (s == tribe.stat) tribe.tint else C.text, fontSize = TS.title, fontWeight = FontWeight.Medium)
                    Text(s.short, color = C.muted, fontSize = TS.micro)
                }
            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            listOf("ATK" to f.atk, "MATK" to f.matk, "DEF" to f.def, "HP" to f.hp, tr("พลัง", "Power") to f.power).forEach { (k, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$v", color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                    Text(k, color = C.muted, fontSize = TS.micro)
                }
            }
        }
    }
}

/** A thin rounded progress bar. */
@Composable
private fun Bar(ratio: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(C.sunken)) {
        Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(color))
    }
}

/** The running timer, or how to start one. */
@Composable
private fun TimerStrip(state: UiState, onIdle: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.timer) {
        while (state.timer != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val t = state.timer
    Card(Modifier.clickable(onClick = onIdle)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚔️", fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                if (t == null) {
                    Text(tr("ออกผจญภัยระหว่างทำงาน", "Adventure while you work"), color = C.text, fontSize = TS.body)
                    Text(tr("เปิดงานแล้วกดปุ่มดาบเพื่อจับเวลา ทีมจะต่อสู้ให้ระหว่างนั้น", "Open a task and tap the sword to time it; the party fights meanwhile"), color = C.muted, fontSize = TS.caption)
                } else {
                    Text(t.title, color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("กำลังจับเวลา ${clock(t.seconds(now))}", "Timing ${clock(t.seconds(now))}"), color = C.accentText, fontSize = TS.caption)
                }
            }
            androidx.compose.material3.Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(16.dp))
        }
    }
}

/** The Focus page's line for Omni Quest: the running timer, or the character's level and rewards waiting. */
@Composable
fun QuestStrip(state: UiState, vm: TaskViewModel) {
    val q = remember(state.questLog) { state.questState }
    val xp = remember(state.tasks, state.parked, state.questArchive, state.questMoves, q.start, q.tribe, state.today) { state.questXp(q) }
    val claimable = remember(state.tasks, state.questArchive, state.events, q, state.questMoves) { state.questBoard(q).count { it.claimable } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.timer) {
        while (state.timer != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val t = state.timer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
            .clickable { if (t != null) vm.requestIdle() else vm.requestQuest(true) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (t != null) "⚔️" else q.mascot?.emoji ?: "🛡️", fontSize = 20.sp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            if (t != null) {
                Text(t.title, color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr("ทีมกำลังต่อสู้ ${clock(t.seconds(now))}", "The party is fighting ${clock(t.seconds(now))}"), color = C.accentText, fontSize = TS.caption)
            } else if (!q.begun) {
                Text("Omni Quest", color = C.text, fontSize = TS.body)
                Text(tr("สร้างตัวละครที่เก่งขึ้นจากงานจริง", "A character that grows from your real work"), color = C.muted, fontSize = TS.caption)
            } else {
                Text("${q.name.ifBlank { "Omni Quest" }}  Lv ${xp.level}", color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Bar(if (xp.maxed) 1f else xp.into.toFloat() / xp.span, q.tribe?.tint ?: C.accent, Modifier.padding(top = 5.dp), height = 4.dp)
            }
        }
        if (claimable > 0 && t == null) Pill(tr("รางวัล $claimable", "$claimable rewards"), C.amberSoft, C.amber, modifier = Modifier.padding(start = 8.dp))
    }
}

fun clock(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

@Composable
private fun QuestRow(quest: Quests.Quest, onClaim: () -> Unit) {
    val icon = when (quest.kind) {
        Quests.Kind.DAILY -> "📜"
        Quests.Kind.BOSS -> "👹"
        Quests.Kind.CALENDAR -> "🗺️"
        Quests.Kind.WEEKLY -> "🏆"
    }
    Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(quest.title, color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(quest.detail, color = C.muted, fontSize = TS.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bar(quest.progress.toFloat() / quest.goal, if (quest.done) C.lime else C.accent, Modifier.weight(1f))
                    Text("${quest.progress}/${quest.goal}", Modifier.padding(start = 8.dp), color = C.muted, fontSize = TS.micro)
                }
            }
            Box(Modifier.padding(start = 10.dp).width(76.dp), contentAlignment = Alignment.Center) {
                when {
                    quest.claimed -> Text(tr("รับแล้ว", "Claimed"), color = C.faint, fontSize = TS.caption)
                    quest.claimable -> SmallAction("🪙 ${quest.gold}", filled = true, onClick = onClaim)
                    else -> Text("🪙 ${quest.gold}", color = C.muted, fontSize = TS.caption)
                }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(10.dp))
            .background(if (filled && enabled) C.accent else Color.Transparent)
            .border(1.dp, if (filled && enabled) C.accent else C.control, RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (!enabled) C.faint else if (filled) C.onAccent else C.text2, fontSize = TS.caption, maxLines = 1) }
}

private fun Item.summary(): String = buildList {
    if (atk > 0) add("ATK +$atk")
    if (matk > 0) add("MATK +$matk")
    if (def > 0) add("DEF +$def")
    bonus.forEach { (s, v) -> add("${s.short} +$v") }
}.joinToString("  ")

@Composable
private fun ItemRow(item: Item, level: Int, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.icon, fontSize = 22.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name, color = C.text, fontSize = TS.body)
            Text(item.summary(), color = C.muted, fontSize = TS.caption)
            if (item.minLevel > level) Text(tr("ใส่ได้ที่ Lv ${item.minLevel}", "Wearable at Lv ${item.minLevel}"), color = C.amber, fontSize = TS.micro)
        }
        trailing()
    }
}

@Composable
private fun GearTab(q: QuestState, level: Int, vm: TaskViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Slot.entries.forEach { slot ->
            val worn = q.equipped[slot]
            val mine = q.owned.distinct().mapNotNull { QuestWorld.item(it) }.filter { it.slot == slot }
            SettingsGroup(slot.label) {
                if (mine.isEmpty()) Text(tr("ยังไม่มี", "None yet"), Modifier.padding(vertical = 10.dp), color = C.muted, fontSize = TS.caption)
                mine.forEachIndexed { i, item ->
                    if (i > 0) Divider()
                    ItemRow(item, level) {
                        if (item.id == worn) {
                            Text(tr("ใส่อยู่", "Worn"), color = C.lime, fontSize = TS.caption)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item.id !in QuestWorld.starter) SmallAction(tr("ขาย ${item.sellPrice}", "Sell ${item.sellPrice}"), filled = false) {
                                    vm.questAct(QuestLog.SELL, a = item.id, gold = item.sellPrice)
                                }
                                SmallAction(tr("ใส่", "Wear"), filled = true, enabled = item.minLevel <= level) { vm.questAct(QuestLog.EQUIP, a = item.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopTab(q: QuestState, level: Int, gold: Long, vm: TaskViewModel) {
    val forSale = QuestWorld.items.filter { it.price > 0 && it.id !in q.owned }.sortedWith(compareBy({ it.minLevel }, { it.price }))
    SettingsGroup(tr("ร้านค้ากิลด์รุ่งอรุณ", "Dawn Guild shop")) {
        if (forSale.isEmpty()) Text(tr("ซื้อครบทุกชิ้นแล้ว", "You own everything here"), Modifier.padding(vertical = 10.dp), color = C.muted, fontSize = TS.caption)
        forSale.forEachIndexed { i, item ->
            if (i > 0) Divider()
            ItemRow(item, level) {
                SmallAction("🪙 ${item.price}", filled = true, enabled = gold >= item.price) { vm.questAct(QuestLog.BUY, a = item.id, gold = item.price) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsTab(q: QuestState, level: Int, vm: TaskViewModel) {
    val points = q.skillPoints(level)
    val all = QuestWorld.skillsFor(q.tribe, q.job)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("แต้มสกิลเหลือ $points", "$points skill points left"), Modifier.padding(start = 4.dp), color = C.text2, fontSize = TS.body)
        val active = q.learned.mapNotNull { QuestWorld.skill(it) }.filter { it.kind == SkillKind.ACTIVE }
        if (active.isNotEmpty()) {
            SettingsGroup(tr("สกิลที่ใช้ในการต่อสู้ (สูงสุด ${QuestWorld.ACTIVE_SLOTS})", "Battle skills (up to ${QuestWorld.ACTIVE_SLOTS})")) {
                FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    active.forEach { s ->
                        val on = s.id in q.loadout
                        Chip(s.name, on, {
                            val next = if (on) q.loadout - s.id else (q.loadout + s.id).takeLast(QuestWorld.ACTIVE_SLOTS)
                            vm.questAct(QuestLog.LOADOUT, a = next.joinToString(","))
                        })
                    }
                }
            }
        }
        SettingsGroup(tr("ต้นสกิล", "Skill tree")) {
            all.sortedBy { it.minLevel }.forEachIndexed { i, s ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.kind == SkillKind.ACTIVE) "✨" else "🔰", fontSize = 20.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(s.name, color = C.text, fontSize = TS.body)
                        val kind = if (s.kind == SkillKind.ACTIVE) tr("ใช้งาน พลัง ${s.power}%", "Active, ${s.power}% power") else tr("ติดตัว", "Passive")
                        Text(listOf(kind, s.note, "Lv ${s.minLevel}").filter { it.isNotBlank() }.joinToString("  "), color = C.muted, fontSize = TS.caption)
                    }
                    when {
                        s.id in q.learned -> Text(tr("เรียนแล้ว", "Learned"), color = C.lime, fontSize = TS.caption)
                        else -> SmallAction(tr("เรียน", "Learn"), filled = true, enabled = points > 0 && level >= s.minLevel) { vm.questAct(QuestLog.LEARN, a = s.id) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroTab(q: QuestState, xp: QuestXp.Result, vm: TaskViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(tr("หน้าตา", "Look")) {
            LookPicker(q) { part, v -> vm.questAct(QuestLog.LOOK, a = part, n = v) }
        }
        SettingsGroup(tr("อาชีพ", "Job")) {
            if (q.job == Job.NOVICE && xp.level < QuestWorld.JOB_LEVEL) {
                Text(tr("เลือกอาชีพได้ที่ Lv ${QuestWorld.JOB_LEVEL}", "Pick a job at Lv ${QuestWorld.JOB_LEVEL}"), Modifier.padding(vertical = 10.dp), color = C.muted, fontSize = TS.caption)
            } else {
                Job.entries.filter { it != Job.NOVICE }.forEachIndexed { i, j ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(j.label, color = C.text, fontSize = TS.body)
                            Text("${j.style}  ${j.main.short}", color = C.muted, fontSize = TS.caption)
                        }
                        if (q.job == j) Text(tr("อาชีพปัจจุบัน", "Current"), color = C.lime, fontSize = TS.caption)
                        else if (q.job == Job.NOVICE) SmallAction(tr("เลือก", "Choose"), filled = true) { vm.questAct(QuestLog.JOB, a = j.name) }
                    }
                }
            }
        }
        SettingsGroup(tr("Mascot คู่ใจ", "Mascot")) {
            FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                q.mascots(xp.level).forEach { t -> Chip("${t.emoji} ${t.mascot}", q.mascot == t, { vm.questAct(QuestLog.MASCOT, a = t.name) }) }
            }
            Text(
                tr("Mascot เผ่าอื่นมาเป็นเพื่อนที่ Lv ${QuestWorld.SECOND_MASCOT_LEVEL} และครบทุกเผ่าที่ Lv ${QuestWorld.ALL_MASCOTS_LEVEL}",
                    "Other tribes' mascots join at Lv ${QuestWorld.SECOND_MASCOT_LEVEL}, all of them at Lv ${QuestWorld.ALL_MASCOTS_LEVEL}"),
                Modifier.padding(bottom = 10.dp), color = C.muted, fontSize = TS.micro,
            )
        }
        SettingsGroup(tr("พลังจากชีวิตจริง", "Power from real life")) {
            Tribe.entries.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(t.tint))
                    Text("${t.realm} (${t.stat.short})", Modifier.weight(1f).padding(start = 10.dp), color = C.text2, fontSize = TS.caption)
                    Text(tr("${xp.byRealm[t] ?: 0} งาน", "${xp.byRealm[t] ?: 0} tasks"), color = C.text, fontSize = TS.caption)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(tr("งานทั่วไป (XP อย่างเดียว)", "General work (XP only)"), Modifier.weight(1f).padding(start = 20.dp), color = C.muted, fontSize = TS.caption)
                Text(tr("${xp.byRealm[null] ?: 0} งาน", "${xp.byRealm[null] ?: 0} tasks"), color = C.muted, fontSize = TS.caption)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPicker(q: QuestState, onPick: (String, Int) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chibi(q, Modifier.size(84.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatches(QuestWorld.hairColors, q.look.hairColor) { onPick("hairColor", it) }
                Swatches(QuestWorld.skinTones, q.look.skin) { onPick("skin", it) }
                Swatches(QuestWorld.outfitColors, q.look.outfit) { onPick("outfit", it) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QuestWorld.hairStyles.forEachIndexed { i, name -> Chip(tr("ผม$name", "Hair ${i + 1}"), q.look.hair == i, { onPick("hair", i) }) }
        }
    }
}

@Composable
private fun Swatches(colors: List<Long>, selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { i, c ->
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(Color(c))
                    .border(2.dp, if (i == selected) C.accent else C.control, CircleShape).clickable { onPick(i) },
            )
        }
    }
}

/** First visit: name, tribe and look, then the guild takes the new adventurer in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCharacter(state: UiState, vm: TaskViewModel, xp: QuestXp.Result, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var tribe by rememberSaveable { mutableStateOf<Tribe?>(null) }
    var hair by rememberSaveable { mutableIntStateOf(0) }
    var hairColor by rememberSaveable { mutableIntStateOf(0) }
    var skin by rememberSaveable { mutableIntStateOf(0) }
    var outfit by rememberSaveable { mutableIntStateOf(0) }
    val preview = QuestState(tribe = tribe, look = app.omnitask.quest.Look(hair, hairColor, skin, outfit))
    val past = xp.earned.size
    // What the old work becomes once the game starts today: half, and no further than level 20.
    val legend = remember(xp) {
        QuestXp.compute(state.tasks + state.parked + state.questArchive, state.questMoves, state.today, null, state.today)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquareButton(Ic.back, tr("กลับ", "Back"), onBack)
                Text(tr("สร้างตัวละคร", "Create your character"), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, color = C.text)
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        tr(
                            "ต้นยกดราซิลกำลังถูกหมอกผัดวันกลืน งานจริงทุกชิ้นที่ทำเสร็จคือแสงอรุณที่ฟื้นกิ่งไม้ มาสเตอร์ออร์รินแห่งกิลด์รุ่งอรุณรอรับคุณอยู่",
                            "The Mist of Later is swallowing Ygdrasil. Every real task you finish is dawnlight that heals a branch. Master Orrin of the Dawn Guild is waiting for you.",
                        ),
                        color = C.text2, fontSize = TS.body,
                    )
                    if (past > 0) Text(
                        tr("งานที่เคยทำเสร็จ $past อัน จะกลายเป็นตำนานเดิม เริ่มที่ Lv ${legend.level}", "$past finished tasks become your legend: you start at Lv ${legend.level}"),
                        Modifier.padding(top = 8.dp), color = C.accentText, fontSize = TS.caption,
                    )
                }
            }
        }
        item {
            SettingsGroup(tr("ชื่อ", "Name")) {
                BasicTextField(
                    value = name, onValueChange = { name = it.take(24) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                    cursorBrush = SolidColor(C.accent), singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) Text(tr("ชื่อนักผจญภัย", "Adventurer's name"), color = C.faint, fontSize = TS.body)
                            inner()
                        }
                    },
                )
            }
        }
        item { Text(tr("เลือกเผ่า", "Choose a tribe"), Modifier.padding(start = 4.dp), color = C.text2, fontSize = TS.body) }
        items(Tribe.entries.toList(), key = { it.name }) { t ->
            val on = tribe == t
            Card(
                Modifier.clickable { tribe = t },
                color = if (on) t.tint.copy(alpha = 0.12f) else C.card,
                border = if (on) t.tint else C.cardBorder,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(t.tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Text(t.emoji, fontSize = 26.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(tr("เผ่า${t.label}  เวท${t.element}", "${t.label}  ${t.element} magic"), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                        Text(tr("โตจาก: ${t.realm}", "Grows from: ${t.realm}"), color = C.text2, fontSize = TS.caption)
                        Text("${t.stat.short}  Mascot ${t.mascot} (${t.mascotLook})", color = C.muted, fontSize = TS.caption)
                    }
                    Text(tr("${xp.byRealm[t] ?: 0} งาน", "${xp.byRealm[t] ?: 0} tasks"), color = t.tint, fontSize = TS.caption)
                }
            }
        }
        item {
            SettingsGroup(tr("หน้าตา", "Look")) {
                LookPicker(preview) { part, v ->
                    when (part) {
                        "hair" -> hair = v
                        "hairColor" -> hairColor = v
                        "skin" -> skin = v
                        "outfit" -> outfit = v
                    }
                }
            }
        }
        item {
            val ready = tribe != null
            PrimaryButton(
                if (ready) tr("เข้าร่วมกิลด์", "Join the guild") else tr("เลือกเผ่าก่อน", "Choose a tribe first"),
                {
                    val t = tribe ?: return@PrimaryButton
                    vm.questAct(QuestLog.START)
                    vm.questAct(QuestLog.NAME_SET, a = name.trim())
                    vm.questAct(QuestLog.TRIBE, a = t.name)
                    vm.questAct(QuestLog.LOOK, a = "hair", n = hair)
                    vm.questAct(QuestLog.LOOK, a = "hairColor", n = hairColor)
                    vm.questAct(QuestLog.LOOK, a = "skin", n = skin)
                    vm.questAct(QuestLog.LOOK, a = "outfit", n = outfit)
                },
                Modifier.fillMaxWidth(),
                color = tribe?.tint ?: C.control,
            )
        }
    }
}

/**
 * The character as a small paper doll drawn in code: aura, body, head, hair, face, weapon. Stand-in art until the
 * drawn parts arrive; [bob] lifts it for the idle animation.
 */
@Composable
fun Chibi(q: QuestState, modifier: Modifier = Modifier, bob: Float = 0f, swing: Float = 0f) {
    val skin = Color(QuestWorld.skinTones.getOrElse(q.look.skin) { QuestWorld.skinTones[0] })
    val hair = Color(QuestWorld.hairColors.getOrElse(q.look.hairColor) { QuestWorld.hairColors[0] })
    val outfit = Color(QuestWorld.outfitColors.getOrElse(q.look.outfit) { QuestWorld.outfitColors[0] })
    val aura = q.tribe?.tint ?: C.accent
    val weapon = q.equipped[Slot.WEAPON] != null
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val lift = -bob * h * 0.04f
        drawCircle(aura.copy(alpha = 0.16f), radius = w * 0.48f, center = Offset(w / 2, h / 2))
        // Body
        drawRoundRect(outfit, topLeft = Offset(w * 0.32f, h * 0.55f + lift), size = Size(w * 0.36f, h * 0.30f), cornerRadius = CornerRadius(w * 0.08f))
        // Weapon in the right hand
        if (weapon) {
            val base = Offset(w * 0.72f, h * 0.70f + lift)
            val angle = -0.6f + swing
            val tip = Offset(base.x + w * 0.22f * kotlin.math.cos(angle), base.y - h * 0.30f * kotlin.math.cos(angle * 0.5f))
            drawLine(Color(0xFFB8BEC8), base, tip, strokeWidth = w * 0.04f)
            drawLine(Color(0xFF8B5A2B), base, Offset(base.x - w * 0.03f, base.y + h * 0.05f), strokeWidth = w * 0.05f)
        }
        // Head
        val head = Offset(w / 2, h * 0.38f + lift)
        val r = w * 0.20f
        hairBack(q.look.hair, hair, head, r)
        drawCircle(skin, radius = r, center = head)
        drawArc(hair, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(head.x - r * 1.05f, head.y - r * 1.12f), size = Size(r * 2.1f, r * 1.5f))
        // Face
        drawCircle(Color(0xFF2B2B33), radius = r * 0.11f, center = Offset(head.x - r * 0.38f, head.y + r * 0.18f))
        drawCircle(Color(0xFF2B2B33), radius = r * 0.11f, center = Offset(head.x + r * 0.38f, head.y + r * 0.18f))
        drawCircle(Color(0xFFF2A0A0).copy(alpha = 0.6f), radius = r * 0.13f, center = Offset(head.x - r * 0.62f, head.y + r * 0.42f))
        drawCircle(Color(0xFFF2A0A0).copy(alpha = 0.6f), radius = r * 0.13f, center = Offset(head.x + r * 0.62f, head.y + r * 0.42f))
        drawArc(Color(0xFF2B2B33), startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(head.x - r * 0.18f, head.y + r * 0.32f), size = Size(r * 0.36f, r * 0.24f), style = Stroke(width = r * 0.07f))
    }
}

private fun DrawScope.hairBack(style: Int, color: Color, head: Offset, r: Float) {
    when (style) {
        1 -> drawRoundRect(color, topLeft = Offset(head.x - r * 1.1f, head.y - r * 0.4f), size = Size(r * 2.2f, r * 1.9f), cornerRadius = CornerRadius(r * 0.5f))
        2 -> drawCircle(color, radius = r * 0.45f, center = Offset(head.x + r * 1.05f, head.y + r * 0.1f))
        3 -> for (i in -2..2) drawCircle(color, radius = r * 0.42f, center = Offset(head.x + i * r * 0.48f, head.y - r * 0.85f + (if (i % 2 == 0) 0f else r * 0.1f)))
        4 -> drawCircle(color, radius = r * 0.38f, center = Offset(head.x, head.y - r * 1.2f))
        else -> {}
    }
}

/**
 * The idle page: the party fights through a zone while the owner works on the timed task. Everything on screen
 * follows from the seconds since the timer started ([Idle.frame]), so closing the page loses nothing.
 */
@Composable
fun IdleScreen(state: UiState, vm: TaskViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val q = remember(state.questLog) { state.questState }
    val xp = remember(state.tasks, state.parked, state.questArchive, state.questMoves, q.start, q.tribe, state.today) { state.questXp(q) }
    val fighter = remember(xp, q) { Fighter.of(xp, q, timing = true) }
    val timer = state.timer

    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Ic.back, tr("ย่อ", "Minimise"), onClose)
            Text(tr("ผจญภัยระหว่างทำงาน", "Adventure while you work"), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, color = C.text)
        }
        if (timer == null) {
            IdlePicker(state, vm)
            return@Column
        }
        val seconds = timer.seconds(now)
        val frame = Idle.frame(timer.seed, fighter, seconds)
        val over = seconds >= Idle.MAX_SECONDS
        Card {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timer.title, color = C.text2, fontSize = TS.body, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(clock(seconds), Modifier.padding(top = 4.dp), color = C.text, fontSize = 44.sp, fontWeight = FontWeight.Light)
                if (over) Text(tr("ครบ 3 ชั่วโมงแล้ว พักสักหน่อยนะ ทีมหยุดเก็บของแล้ว", "Three hours already. Take a break; the party stopped looting."), color = C.amber, fontSize = TS.caption, textAlign = TextAlign.Center)
            }
        }
        Card {
            Column(Modifier.padding(14.dp)) {
                Text("${frame.zone.emoji} ${frame.zone.name}", color = C.muted, fontSize = TS.caption)
                Battle(q, frame, now)
                Text(frame.foe.name, color = if (frame.foe.boss) C.red else C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                Bar(frame.hpLeft.toFloat(), if (frame.foe.boss) C.red else C.lime, Modifier.padding(top = 6.dp), height = 8.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IdleStat("⚔️", "${frame.kills}", tr("ล้มศัตรู", "Defeated"), Modifier.weight(1f))
            IdleStat("🪙", "${frame.gold}", tr("เงิน", "Gold"), Modifier.weight(1f))
            IdleStat("🎁", frame.drop?.let { QuestWorld.item(it)?.icon } ?: "-", tr("ของดรอป", "Drop"), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(tr("งานเสร็จแล้ว", "Task done"), { vm.stopTimer(finish = true); onClose() }, Modifier.fillMaxWidth(), color = C.lime)
        GhostButton(tr("หยุดจับเวลา (เก็บของที่ได้)", "Stop timer (keep the loot)"), { vm.stopTimer(finish = false); onClose() }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun IdleStat(icon: String, value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$icon $value", color = C.text, fontSize = TS.title)
            Text(label, color = C.muted, fontSize = TS.micro)
        }
    }
}

/** The fight itself: the hero and mascot on the left lunging at the foe on the right. */
@Composable
private fun Battle(q: QuestState, frame: Idle.Frame, now: Long) {
    val beat = (now % 1200L) / 1200f
    val wave = sin(beat * 2 * Math.PI).toFloat()
    val hit = beat < 0.18f
    Row(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Chibi(q, Modifier.size(120.dp).padding(start = (if (hit) 18 else 0).dp), bob = wave, swing = if (hit) 1.1f else 0f)
            Text((q.mascot ?: q.tribe ?: Tribe.LUNA).emoji, Modifier.align(Alignment.BottomStart).padding(bottom = (6 + 4 * wave).dp), fontSize = 28.sp)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val pics = ZONE_FOES[frame.zone.id] ?: listOf("👾")
            Text(
                pics[frame.foe.kind % pics.size],
                Modifier.padding(end = (if (hit) 8 else 0).dp),
                fontSize = if (frame.foe.boss) 72.sp else 54.sp,
                color = if (hit) C.red else Color.Unspecified,
            )
            if (hit) Text("💥", Modifier.align(Alignment.TopStart), fontSize = 26.sp)
        }
    }
}

/** No task is being timed: pick one of today's to start. */
@Composable
private fun IdlePicker(state: UiState, vm: TaskViewModel) {
    val today = state.today
    val open = state.tasks.filter { it.isOpen && it.list == null }
        .sortedWith(compareBy<Task>({ !(it.due == today || it.scheduled == today) }, { it.due ?: it.scheduled ?: today.plusDays(9999) }))
        .take(12)
    Text(tr("เลือกงานที่จะทำ ทีมจะออกผจญภัยไปพร้อมกับคุณ", "Pick a task to work on; the party sets out with you"), color = C.text2, fontSize = TS.body)
    Card {
        if (open.isEmpty()) Text(tr("ไม่มีงานค้าง", "Nothing open"), Modifier.padding(14.dp), color = C.muted, fontSize = TS.body)
        open.forEachIndexed { i, t ->
            if (i > 0) Divider()
            Row(
                Modifier.fillMaxWidth().clickable { vm.startTimer(t) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.title, Modifier.weight(1f), color = C.text, fontSize = TS.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("⚔️", Modifier.padding(start = 8.dp), fontSize = 18.sp)
            }
        }
    }
}

/** What the story knows of the owner's real progress and day. */
fun UiState.storyFacts(q: QuestState, xp: QuestXp.Result): Story.Facts {
    val shifts = profile.shiftWords.map { it.lowercase() }.filter { it.isNotBlank() }
    return Story.Facts(
        level = xp.level,
        sessions = q.sessions.size,
        focusSeconds = q.sessions.sumOf { it.seconds },
        bossSlain = q.bossSlain,
        weeklyDone = q.weeklyDone,
        seen = q.seen,
        today = today,
        shiftToday = todayEvents.any { e -> shifts.any { it in e.title.lowercase() } },
        busyWeek = tasks.count { it.isOpen && it.parent == null && it.due?.let { d -> d >= today && d <= today.plusDays(6) } == true } >= 7,
    )
}

@Composable
private fun StoryTab(state: UiState, q: QuestState, xp: QuestXp.Result, facts: Story.Facts, cast: Story.Cast, open: List<Story.Scene>, onPlay: (String) -> Unit) {
    val now = LocalDateTime.now()
    val day = remember(state.tasks, state.events, q, xp) {
        val doneToday = (state.tasks + state.parked).filter { it.done == state.today && it.list == null }
        Story.Day(
            today = state.today,
            doneToday = doneToday.size,
            lastDone = doneToday.lastOrNull()?.title,
            overdue = state.tasks.count { it.isOpen && it.parent == null && (it.due ?: it.scheduled)?.let { d -> d < state.today } == true },
            focusMinutes = q.secondsOn(state.today) / 60,
            topRealm = xp.earned.filter { it.day >= state.today.minusDays(7) && it.realm != null }.groupingBy { it.realm!! }.eachCount().maxByOrNull { it.value }?.key,
            shiftToday = facts.shiftToday,
            nextEvent = state.todayEvents.filter { !it.allDay && it.begin > now }.minByOrNull { it.begin }?.title,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The friends who have joined so far, each with a word for today.
        val friends = buildList {
            add(Story.Speaker.ORRIN)
            if ("c1-3" in q.seen) add(Story.Speaker.LEO)
            if ("c1-4" in q.seen) add(Story.Speaker.CELINE)
        }
        SettingsGroup(tr("เพื่อนในกิลด์", "Guild friends")) {
            friends.forEachIndexed { i, who ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Text(who.emoji, fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(who.label, color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                        Text(Story.talk(who, day, cast), color = C.text, fontSize = TS.body)
                    }
                }
            }
        }
        if (open.isNotEmpty()) {
            SettingsGroup(tr("ฉากใหม่", "New scenes")) {
                open.forEachIndexed { i, scene ->
                    if (i > 0) Divider()
                    SceneRow(scene, tr("ดู", "Play"), filled = true) { onPlay(scene.id) }
                }
            }
        }
        Story.next(facts)?.let { next ->
            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 20.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(next.title, color = C.text2, fontSize = TS.body)
                        Text(tr("ปลดล็อกเมื่อ: ${next.unlock.hint}", "Opens when: ${next.unlock.hint}"), color = C.muted, fontSize = TS.caption)
                    }
                }
            }
        }
        Story.chapters.entries.sortedBy { if (it.key == 0) 99 else it.key }.forEach { (chapter, title) ->
            val seen = Story.scenes.filter { it.chapter == chapter && it.id in q.seen }
            if (seen.isNotEmpty()) SettingsGroup(title) {
                seen.forEachIndexed { i, scene ->
                    if (i > 0) Divider()
                    SceneRow(scene, tr("ดูอีกครั้ง", "Replay"), filled = false) { onPlay(scene.id) }
                }
            }
        }
    }
}

@Composable
private fun SceneRow(scene: Story.Scene, action: String, filled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (scene.event) "🎉" else "📖", fontSize = 20.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(scene.title, color = C.text, fontSize = TS.body)
            Text(Story.chapters[scene.chapter].orEmpty(), color = C.muted, fontSize = TS.caption)
        }
        SmallAction(action, filled = filled, onClick = onClick)
    }
}

/** A story scene, one line at a time: tap to go on, and the last tap ends it. */
@Composable
private fun SceneScreen(scene: Story.Scene, cast: Story.Cast, onDone: (finished: Boolean) -> Unit) {
    BackHandler { onDone(false) }
    var shown by rememberSaveable(scene.id) { mutableIntStateOf(1) }
    val lines = scene.lines.take(shown)
    val last = shown >= scene.lines.size
    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().padding(14.dp)
            .clickable { if (last) onDone(true) else shown++ },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Ic.close, tr("ปิด", "Close"), { onDone(false) })
            Column(Modifier.padding(start = 12.dp)) {
                Text(scene.title, style = MaterialTheme.typography.titleLarge, color = C.text)
                Text(Story.chapters[scene.chapter].orEmpty(), color = C.muted, fontSize = TS.caption)
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), reverseLayout = false) {
            items(lines.size) { i ->
                val line = lines[i]
                val text = Story.fill(line.text, cast)
                if (line.who == Story.Speaker.NARRATOR) {
                    Text(text, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), color = C.text2, fontSize = TS.body, textAlign = TextAlign.Center)
                } else {
                    val emoji = if (line.who == Story.Speaker.MASCOT) cast.mascot.emoji else if (line.who == Story.Speaker.HERO) "🙂" else line.who.emoji
                    val name = Story.fill(line.who.label, cast)
                    val color = when (line.who) {
                        Story.Speaker.MASCOT -> cast.mascot.tint
                        Story.Speaker.HERO -> cast.tribe.tint
                        Story.Speaker.LEO -> Tribe.IGNIS.tint
                        Story.Speaker.CELINE -> Tribe.LUNA.tint
                        Story.Speaker.MIST -> C.muted
                        else -> C.accentText
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp)
                        }
                        Column(
                            Modifier.weight(1f).padding(start = 10.dp).clip(RoundedCornerShape(14.dp)).background(C.card)
                                .border(1.dp, C.cardBorder, RoundedCornerShape(14.dp)).padding(12.dp),
                        ) {
                            Text(name, color = color, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                            Text(text, color = if (line.who == Story.Speaker.MIST) C.muted else C.text, fontSize = TS.body)
                        }
                    }
                }
            }
        }
        Text(
            if (last) tr("แตะเพื่อจบฉาก (ได้ 🪙 ${scene.gold} ครั้งแรก)", "Tap to finish (🪙 ${scene.gold} the first time)") else tr("แตะเพื่ออ่านต่อ", "Tap to continue"),
            Modifier.fillMaxWidth(), color = C.accentText, fontSize = TS.caption, textAlign = TextAlign.Center,
        )
    }
}
