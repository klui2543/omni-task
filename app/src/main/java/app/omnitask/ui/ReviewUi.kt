package app.omnitask.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.data.TaskLine
import app.omnitask.model.Countdown
import app.omnitask.model.Focus
import app.omnitask.model.Folders
import app.omnitask.model.Priority
import app.omnitask.model.Review
import app.omnitask.model.ReviewWrite
import app.omnitask.model.StuckLog
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The review mode's two switches, kept on this phone. [on] shows the review on the secretary page; [writes] lets
 * "บันทึก" write into the notes. With [writes] off a card is answered and moved on, and "ดูต้นฉบับ" shows what would
 * be written, but nothing is. [writes] starts on (Klui, 2026-10-29, after the check that every app keeps the review
 * lines when a description is edited); it is kept under a new key so an earlier "off" from the trial does not stick.
 * [full] lets the review take the whole screen (the bottom bar steps aside); it starts off, so the bottom bar stays and
 * the answer bar sits above it (Klui, 2026-10-29: "ตั้งค่าให้เปลี่ยนกันเต็มจอหรือไม่เต็มจอก็ได้").
 */
object ReviewMode {
    private const val PREFS = "omni-review"
    var on by mutableStateOf(false)
        private set
    private const val WRITES = "writes-v2"
    var writes by mutableStateOf(true)
        private set
    private const val FULL = "full"
    var full by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        on = p.getBoolean("on", false)
        writes = p.getBoolean(WRITES, true)
        full = p.getBoolean(FULL, false)
    }

    fun setOn(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", value).apply()
    }

    fun setWrites(context: Context, value: Boolean) {
        writes = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(WRITES, value).apply()
    }

    fun setFull(context: Context, value: Boolean) {
        full = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(FULL, value).apply()
    }
}

/** The Settings switches for the review mode. */
@Composable
fun ReviewSetting(context: Context) {
    LaunchedEffect(Unit) { ReviewMode.load(context) }
    SettingsGroup(tr("โหมดทบทวนงาน", "Review mode")) {
        // No switch for showing it: the review is always the first card on the secretary page (owner, 2026-10-28).
        SettingSwitch(
            tr("จดคำตอบลงโน้ตจริง", "Write answers into the notes"),
            tr("ปิดไว้: ตอบได้ ดูต้นฉบับได้ แต่ยังไม่จดอะไรลงโน้ต", "Off: you can answer and see the note, nothing is written"),
            ReviewMode.writes,
        ) { ReviewMode.setWrites(context, it) }
        SettingSwitch(
            tr("ทบทวนแบบเต็มจอ", "Full screen review"),
            tr("เปิด: ซ่อนแถบเมนูด้านล่างตอนทบทวน  ปิด: แถบเมนูยังอยู่ ปุ่มตอบอยู่เหนือแถบ", "On: the bottom bar hides while you review. Off: the bar stays, the answer buttons sit above it"),
            ReviewMode.full,
        ) { ReviewMode.setFull(context, it) }
    }
}

@Composable
internal fun SettingSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = C.text, fontSize = TS.body)
            Text(sub, color = C.muted, fontSize = TS.caption)
        }
        OnOff(checked)
    }
}

/** The way in, on the secretary page under its head: how many cards are in the pile. */
@Composable
fun ReviewStrip(count: Int, onStart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp)).background(C.accentSoft).border(2.dp, C.accent, RoundedCornerShape(14.dp))
            .clickable(onClick = onStart).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("ทบทวนงาน $count ใบ", "Review $count cards"), Modifier.weight(1f), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
        Text(tr("เริ่ม ›", "Start ›"), color = C.muted, fontSize = TS.caption)
    }
}

/** The cards to review now, for the strip's count. */
fun reviewDeck(state: UiState): List<Review.Card> = Review.deck(state.tasks, state.today)

// ---- One card's questions ----

private enum class Tone { PLAIN, GOOD, BAD, QUIET }

private data class Opt(val v: String, val l: String, val tone: Tone = Tone.PLAIN)

/** One question: [row] is its name in the panel, [typed] lets the owner write (only progress and the next step). */
private data class Q(val k: String, val row: String, val t: String, val sub: String?, val opts: List<Opt>, val typed: Boolean = false, val ph: String = "")

private data class Ans(val v: String, val l: String, val typed: Boolean = false)

private const val UNSURE = "?"

private fun short(d: LocalDate) = Countdown.day(d).substringAfter(' ')

/** The days offered for "when": today, tomorrow, this Saturday, next Monday. */
private fun whenDays(today: LocalDate): List<Pair<String, LocalDate>> = listOf(
    tr("วันนี้", "Today") to today,
    tr("พรุ่งนี้", "Tomorrow") to today.plusDays(1),
    tr("เสาร์นี้", "This Saturday") to Focus.softDate(today),
    tr("สัปดาห์หน้า", "Next week") to today.plusDays(7L - today.dayOfWeek.ordinal),
)

private fun dateOf(a: Ans?): LocalDate? = a?.takeIf { it.v != UNSURE }?.let { runCatching { LocalDate.parse(it.v) }.getOrNull() }

private val CLOSED = setOf(ReviewWrite.Progress.DONE.name, ReviewWrite.Progress.PARK.name, ReviewWrite.Progress.DROP.name)

/** The questions for [task] given the answers so far; later ones depend on earlier answers (the mock Klui picked). */
private fun questions(task: Task, a: Map<String, Ans>, today: LocalDate, folders: List<String>): List<Q> {
    val status = Q(
        "status", tr("สถานะ", "Status"), tr("ตอนนี้งานนี้เป็นยังไง", "How is this task now"), null,
        ReviewWrite.Progress.entries.map {
            Opt(
                it.name, it.label,
                when (it) {
                    ReviewWrite.Progress.STUCK -> Tone.BAD
                    ReviewWrite.Progress.DONE -> Tone.GOOD
                    ReviewWrite.Progress.PARK, ReviewWrite.Progress.DROP -> Tone.QUIET
                    else -> Tone.PLAIN
                },
            )
        },
    )
    val st = a["status"]?.v
    if (st == null || st in CLOSED) return listOf(status)
    return buildList {
        add(status)
        if (st == ReviewWrite.Progress.STUCK.name) add(Q("why", tr("ติดเพราะ", "Stuck because"), tr("ติดเพราะอะไร", "What holds it"), null, StuckLog.Reason.entries.map { Opt(it.name, it.label) }))
        val said = StuckLog.entries(task).lastOrNull()
        add(
            Q(
                "prog", tr("ความคืบหน้า", "Progress"), tr("ความคืบหน้า จดถึงตัวเองไว้ดูครั้งหน้า", "Progress, a note to yourself for next time"),
                said?.let { tr("ครั้งก่อน: ${short(it.day)} ${it.reason}", "Last time: ${short(it.day)} ${it.reason}") } ?: tr("ยังไม่เคยจด", "Nothing noted yet"),
                listOf(Opt("-", tr("ไม่มีอะไรใหม่", "Nothing new"))), typed = true,
                ph = tr("เช่น ทำหน้าแรกเสร็จแล้ว เหลือหน้ารายงาน", "e.g. first page done, report page left"),
            ),
        )
        add(
            Q(
                "next", tr("ก้าวถัดไป", "Next step"), tr("ก้าวถัดไปคืออะไร", "What is the next step"),
                tr("สั้นๆ ที่ลงมือได้เลย", "Short, something you can start on"),
                listOf(Opt(UNSURE, tr("ไม่แน่ใจ", "Not sure"))), typed = true,
                ph = tr("เช่น ส่ง mock ให้พี่ดูก่อน", "e.g. send the mock for a look first"),
            ),
        )
        val late = task.due?.let { it < today } == true
        if ((task.scheduled == null && task.due == null) || late) add(
            Q(
                "when", tr("จะหยิบมาทำ", "When to do it"),
                if (late) tr("เลยวันส่งแล้ว จะทำใหม่เมื่อไร", "Past due, when will you do it") else tr("จะหยิบมาทำเมื่อไร", "When will you pick it up"),
                if (late) tr("เลื่อนได้ ไม่ผิด", "Moving it is fine") else tr("งานนี้ยังไม่มีวัน", "This task has no date yet"),
                whenDays(today).map { (l, d) -> Opt(d.toString(), "$l ${short(d)}") } + Opt(UNSURE, tr("ไม่แน่ใจ", "Not sure")),
            ),
        )
        val waiting = Focus.isWaiting(task)
        val who = Focus.waitingFor(task) ?: Review.nameIn(task.title)
        add(
            Q(
                "wait", tr("คนที่รอ", "Who waits"), tr("มีคนรองานนี้จากเราไหม", "Is someone waiting for this from you"),
                if (waiting) tr("ตอนนี้ติด #รอ อยู่", "Tagged #รอ now") else who?.let { tr("ชื่องานมี $it", "The title names $it") },
                buildList {
                    add(Opt("no", tr("ไม่มี", "No one")))
                    who?.let { add(Opt("name:$it", tr("$it รอ", "$it waits"))) }
                    add(Opt("yes", tr("มี", "Yes")))
                },
            ),
        )
        val w = a["wait"]?.v
        if (w != null && w != "no" && task.due == null) add(
            Q(
                "wdate", tr("เขาต้องการ", "They need it"), tr("เขาต้องการเมื่อไร", "When do they need it"), null,
                whenDays(today).drop(1).map { (l, d) -> Opt(d.toString(), "$l ${short(d)}") } + Opt(UNSURE, tr("ไม่แน่ใจ", "Not sure")),
            ),
        )
        val here = Folders.of(task)
        add(
            Q(
                "folder", tr("อยู่ที่", "Is in"), tr("งานนี้อยู่ถูกโปรเจกต์หรือโฟลเดอร์ไหม", "Is this in the right project or folder"),
                tr("ตอนนี้อยู่: ", "Now in: ") + (here ?: tr("ยังไม่มีบ้าน", "No home yet")),
                listOf(Opt("=", tr("ถูกแล้ว", "Right"))) + folders.filter { it != here }.map { Opt("to:$it", it.replace("/", " / ")) } +
                    (if (here != null) listOf(Opt("to:", tr("ยังไม่มีบ้าน", "No home yet"))) else emptyList()),
            ),
        )
        if (task.due == null && dateOf(a["wdate"]) == null) add(
            Q(
                "delay", tr("ถ้าเลื่อน 1 สัปดาห์", "A week later"), tr("ถ้าเลื่อนไปอีก 1 สัปดาห์ จะเป็นยังไง", "If it waits a week, what happens"),
                tr("ใช้คิดว่าด่วนแค่ไหน", "Used for urgency"),
                Review.Delay.entries.map { Opt(it.name, it.label) } + Opt(UNSURE, tr("ไม่แน่ใจ", "Not sure")),
            ),
        )
        val g = Review.guess(task, today)
        add(
            Q(
                "pri", tr("ถ้าไม่ทำเลย", "If never done"), tr("ถ้าไม่ทำเลย จะเกิดอะไร", "If it is never done, what happens"),
                tr("ระบบเดา: ${g.impact.label} เพราะ${g.why}", "Guess: ${g.impact.label}, ${g.why}"),
                Review.Impact.entries.map { Opt(it.name, it.label + if (it == g.impact) tr(" (ระบบเดา)", " (guess)") else "") },
            ),
        )
    }
}

/** The answers in the shape [ReviewWrite] writes. */
private fun toAnswers(a: Map<String, Ans>): ReviewWrite.Answers {
    val w = a["wait"]?.v
    return ReviewWrite.Answers(
        progress = ReviewWrite.Progress.valueOf(a.getValue("status").v),
        why = a["why"]?.let { StuckLog.Reason.valueOf(it.v) },
        note = a["prog"]?.takeIf { it.typed }?.l,
        nextStep = a["next"]?.takeIf { it.typed }?.l,
        scheduled = dateOf(a["when"]),
        waiting = w?.let { it != "no" },
        waitName = w?.takeIf { it.startsWith("name:") }?.removePrefix("name:"),
        due = dateOf(a["wdate"]),
        askWhen = a["wdate"]?.v == UNSURE,
        folder = a["folder"]?.v?.takeIf { it.startsWith("to:") }?.removePrefix("to:"),
        delay = a["delay"]?.v?.takeIf { it != UNSURE }?.let { Review.Delay.valueOf(it) },
        impact = a["pri"]?.let { Review.Impact.valueOf(it.v) },
    )
}

private fun weigh(task: Task, tasks: List<Task>, a: Map<String, Ans>, today: LocalDate, moves: Int): Review.Weight {
    val x = toAnswersOrNull(a)
    return Review.weigh(task, tasks, today, impact = x?.impact, waiting = x?.waiting, due = x?.due, delay = x?.delay, moves = moves)
}

private fun toAnswersOrNull(a: Map<String, Ans>) = if (a["status"] == null) null else toAnswers(a)

/** The task's flag as the card shows it ("⏫ สูง"). */
private fun levelName(p: Priority) = (p.emoji?.let { "$it " } ?: "") + p.label

/** The importance level the summary gives, in the mock's words ("⏫ มาก"). */
private fun importanceName(p: Priority) = (p.emoji?.let { "$it " } ?: "") + when (p) {
    Priority.HIGHEST -> tr("มากที่สุด", "Highest")
    Priority.HIGH -> tr("มาก", "High")
    Priority.MEDIUM -> tr("กลาง", "Medium")
    Priority.NONE -> tr("ปกติ", "Normal")
    Priority.LOW -> tr("น้อย", "Low")
    Priority.LOWEST -> tr("น้อยที่สุด", "Lowest")
}

/** One card in the pile: where its line is, so the task is found again after a reload. */
private data class Entry(val task: Task, val group: Review.Group)

/** What "↶ ย้อน" goes back to: the pile, the answers and the count before a move, and what that move wrote. */
private data class Step(val deck: List<Entry>, val answers: Map<String, Ans>, val count: Int, val saved: TaskViewModel.ReviewSaved?)

/**
 * The review mode (Klui's pick, mock v5): the task's index card on top, a panel of the questions and answers under
 * it, and the answer bar at the bottom with "↶ ย้อน" and "บันทึก ใบถัดไป". Skipping is on the card. A card saved
 * or skipped goes to the end of the pile; done or dropped work leaves it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(state: UiState, vm: TaskViewModel, only: Task? = null, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { ReviewMode.load(context) }
    val today = state.today
    // A card of the day plan opens one task ([only]): the same review card, closed once it is answered or skipped.
    val deck = remember {
        mutableStateListOf<Entry>().apply {
            if (only != null) add(Entry(only, Review.groupOf(only, today))) else addAll(Review.deck(state.tasks, today).map { Entry(it.task, it.group) })
        }
    }
    var answers by remember { mutableStateOf(mapOf<String, Ans>()) }
    var cur by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    // Steps back for "↶ ย้อน": the pile, the answers and what was written, before each move.
    val history = remember { mutableStateListOf<Step>() }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
    var whyOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf("") }

    val entry = deck.firstOrNull()
    // The task as loaded now (its notes may have changed since the pile was made).
    val task = entry?.let { e -> state.tasks.firstOrNull { it.filePath == e.task.filePath && it.raw == e.task.raw } ?: e.task }
    // Folders the owner picked lately first (Folders.recent went with the folders fix), then every folder in use.
    val folders = remember(state.tasks, state.pickedFolders) {
        val all = state.tasks.filter { it.isOpen && it.list == null }.mapNotNull { Folders.of(it) }.distinct()
        val recent = state.pickedFolders.mapNotNull { p -> all.firstOrNull { it.equals(app.omnitask.model.ProjectNames.canonical(p), ignoreCase = true) } }
        (recent + all).distinct()
    }
    val moves = remember(task?.title) { task?.let(vm::reviewMoves) ?: 0 }

    // The answer bar ("↶ ย้อน", "บันทึก ใบถัดไป") must never sit under the floating bottom bar (Klui, 2026-10-29:
    // answered all, could not go on or back). Full screen (a Settings switch, off at first): the bar steps aside.
    // Otherwise the page ends above the bar; with the keyboard up the bar is under the keyboard, so only the keyboard counts.
    if (ReviewMode.full) FullPage()
    val dock = LocalDockClearance.current
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(
        Modifier.fillMaxSize().padding(bottom = if (keyboard) 0.dp else dock)
            .then(if (dock == 0.dp) Modifier.navigationBarsPadding() else Modifier).imePadding()
            .padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Ic.back, tr("กลับ", "Back"), tint = C.text, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("ทบทวนงาน", "Review"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
                Text(tr("ทบทวนแล้ว $count ใบ  เหลือในกอง ${deck.size} ใบ", "$count reviewed, ${deck.size} in the pile"), color = C.muted, fontSize = TS.caption)
            }
            Text(
                tr("ดูต้นฉบับ", "See the note"),
                Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, C.control, RoundedCornerShape(999.dp))
                    .clickable(enabled = task != null) { noteOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                color = C.text2, fontSize = TS.caption,
            )
        }
        if (!ReviewMode.writes) Text(
            tr("ยังไม่จดลงโน้ตจริง เปิดได้ที่ตั้งค่า โหมดทบทวนงาน", "Not writing to the notes yet. Turn it on in Settings, Review mode"),
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.amberSoft).padding(horizontal = 12.dp, vertical = 6.dp),
            color = C.amber, fontSize = TS.caption, fontWeight = FontWeight.Medium,
        )
        if (task == null || entry == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(tr("ไม่มีงานให้ทบทวนแล้ว", "Nothing left to review"), color = C.muted, fontSize = TS.body)
            }
            return@Column
        }
        val qs = questions(task, answers, today, folders)
        val open = cur?.let { k -> qs.firstOrNull { it.k == k } } ?: qs.firstOrNull { answers[it.k] == null }
        val allDone = qs.all { answers[it.k] != null }
        val closed = answers["status"]?.v in CLOSED
        val weight = if (answers["status"] != null && !closed) weigh(task, state.tasks, answers, today, moves) else null

        fun move(save: Boolean) {
            val leaving = save && answers["status"]?.v.let { it == ReviewWrite.Progress.DONE.name || it == ReviewWrite.Progress.DROP.name }
            val before = Step(deck.toList(), answers, count, null)
            fun advance(saved: TaskViewModel.ReviewSaved?, written: Entry?) {
                if (only != null) { saved?.let(vm::reviewFromPlan); return onClose() }
                history.add(before.copy(saved = saved))
                deck.removeAt(0)
                if (!leaving) deck.add(written ?: entry)
                if (save) count++
                answers = emptyMap(); cur = null; typing = ""
                toast = when {
                    !save -> tr("ข้ามไว้ท้ายกอง ไม่จดอะไร", "Skipped to the end, nothing written")
                    !ReviewMode.writes -> tr("ยังไม่จดจริง ใบนี้ไปท้ายกอง", "Not written (switched off), card to the end")
                    leaving -> tr("จดแล้ว ออกจากกอง", "Written, out of the pile")
                    else -> tr("จดแล้ว ใบนี้ไปท้ายกอง", "Written, card to the end")
                }
            }
            if (!save || !ReviewMode.writes) { advance(null, null); return }
            busy = true
            vm.reviewSave(task, toAnswers(answers), weight?.priority) { saved ->
                busy = false
                if (saved == null) return@reviewSave
                val after = TaskLine.parse(saved.after)?.copy(filePath = task.filePath, fileUri = task.fileUri, lineIndex = task.lineIndex, notes = task.notes)
                advance(saved, after?.let { Entry(it, entry.group) })
            }
        }

        fun undo() {
            val step = history.removeLastOrNull() ?: return
            fun back() {
                deck.clear(); deck.addAll(step.deck); answers = step.answers; count = step.count; cur = null; typing = ""
                toast = tr("กลับมาใบที่แล้ว แก้คำตอบได้เลย", "Back to the last card, change the answers")
            }
            val saved = step.saved ?: return back()
            busy = true
            vm.reviewUndo(saved) { ok -> busy = false; if (ok) back() else history.add(step) }
        }

        // The task's index card.
        Card {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.group.label, Modifier.clip(RoundedCornerShape(999.dp)).background(C.accentSoft).padding(horizontal = 8.dp, vertical = 2.dp), color = C.accentText, fontSize = TS.caption)
                    Text(Folders.of(task)?.replace("/", " / ") ?: tr("ยังไม่มีบ้าน", "No home yet"), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(task.title, color = C.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Focus.ageDays(task, today)?.let { Text(tr("อายุ $it วัน", "$it days old"), color = C.text2, fontSize = TS.caption) }
                        val due = task.due
                        when {
                            due != null -> {
                                val late = ChronoUnit.DAYS.between(due, today)
                                Text(
                                    tr("ส่ง ${short(due)}", "Due ${short(due)}") + if (late > 0) tr(" เลยมา $late วัน", ", $late days late") else "",
                                    color = if (late > 0) C.red else C.text2, fontSize = TS.caption, fontWeight = if (late > 0) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                            task.scheduled != null -> Text(tr("เริ่ม ${short(task.scheduled!!)}", "Starts ${short(task.scheduled!!)}"), color = C.text2, fontSize = TS.caption)
                            else -> Text(tr("ยังไม่มีวัน", "No date yet"), color = C.amber, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
                        }
                        if (task.priority != Priority.NONE) Text(levelName(task.priority), color = C.text2, fontSize = TS.caption)
                        if (Focus.isWaiting(task) && task.due == null) Text(tr("มีคนรอ ไม่มีวันส่ง", "Someone waits, no due date"), color = C.amber, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        tr("ข้ามใบนี้ ›", "Skip ›"),
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(999.dp)).background(C.raised).border(1.dp, C.control, RoundedCornerShape(999.dp))
                            .clickable(enabled = !busy) { move(false) }.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = C.text2, fontSize = TS.caption,
                    )
                }
            }
        }

        // The panel: each question with its answer, the one being asked lit; a tap opens it again.
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            qs.forEachIndexed { i, q ->
                val ans = answers[q.k]
                val on = q == open
                if (i > 0) Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(C.divider))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).background(if (on) C.accentSoft else Color.Transparent)
                        .clickable { cur = q.k; typing = ans?.takeIf { it.typed }?.l ?: "" }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(q.row, Modifier.width(118.dp), color = C.muted, fontSize = TS.body)
                    Text(
                        ans?.l ?: if (on) tr("กำลังถาม", "Asking") else tr("ยังไม่ตอบ", "Not answered"),
                        Modifier.weight(1f).alpha(if (ans == null && !on) 0.7f else 1f),
                        color = when { on -> C.accentText; ans != null -> C.text; else -> C.muted },
                        fontSize = TS.body, fontWeight = if (on) FontWeight.SemiBold else if (ans != null) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("›", color = C.muted, fontSize = TS.body)
                }
            }
            if (weight != null && allDone && cur == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(C.divider))
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { whyOpen = true }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("สรุป", "Summary"), Modifier.width(118.dp), color = C.muted, fontSize = TS.body)
                    Text(
                        tr("สำคัญ ${importanceName(weight.priority)}  ด่วน${weight.urgency.label}", "Importance ${importanceName(weight.priority)}, urgency ${weight.urgency.label}"),
                        Modifier.weight(1f), color = C.accentText, fontSize = TS.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("›", color = C.muted, fontSize = TS.body)
                }
                weight.flags.firstOrNull()?.let { Text(it, Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp), color = C.amber, fontSize = TS.caption, fontWeight = FontWeight.SemiBold) }
            }
        }

        // The answer bar.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(16.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (open != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(open.t, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    open.sub?.let { Text(it, Modifier.weight(1f), color = C.muted, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                FlowRow(
                    Modifier.fillMaxWidth().heightIn(max = 112.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    open.opts.forEach { o ->
                        val ans = answers[open.k]
                        val sel = ans != null && !ans.typed && ans.v == o.v
                        val fg = when {
                            sel -> C.onAccent
                            o.tone == Tone.GOOD -> C.lime
                            o.tone == Tone.BAD -> C.red
                            o.tone == Tone.QUIET -> C.muted
                            else -> C.text
                        }
                        Text(
                            o.l,
                            Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) C.accent else C.raised)
                                .border(1.dp, if (sel) C.accent else C.control, RoundedCornerShape(10.dp))
                                .clickable {
                                    // "ถูกแล้ว" shows where the task stays, as the mock does.
                                    val shown = if (open.k == "folder" && o.v == "=") Folders.of(task)?.replace("/", " / ") ?: tr("ยังไม่มีบ้าน", "No home yet")
                                    else o.l.removeSuffix(tr(" (ระบบเดา)", " (guess)"))
                                    answers = pick(answers, open.k, Ans(o.v, shown)); cur = null; typing = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = fg, fontSize = TS.body, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                if (open.typed) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    fun submit() {
                        val v = typing.trim()
                        if (v.isEmpty()) return
                        answers = pick(answers, open.k, Ans("typed", v, typed = true)); cur = null; typing = ""
                    }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.bg).border(1.dp, C.control, RoundedCornerShape(10.dp))
                            .padding(horizontal = 11.dp, vertical = 10.dp),
                    ) {
                        if (typing.isEmpty()) Text(open.ph, color = C.muted, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicTextField(
                            typing, { typing = it }, Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                            cursorBrush = SolidColor(C.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                        )
                    }
                    PrimaryButton(tr("ตอบ", "Answer"), { submit() })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val canBack = history.isNotEmpty() && !busy
                Box(
                    Modifier.heightIn(min = 44.dp).alpha(if (canBack) 1f else 0.45f).clip(RoundedCornerShape(10.dp)).background(C.raised)
                        .border(1.dp, C.control, RoundedCornerShape(10.dp)).clickable(enabled = canBack) { undo() }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("↶ ย้อน", "↶ Back"), color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium) }
                val canSave = allDone && !busy
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).alpha(if (canSave) 1f else 0.45f).clip(RoundedCornerShape(10.dp)).background(C.accent)
                        .clickable(enabled = canSave) { move(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (allDone) tr("บันทึก ใบถัดไป", "Save, next card") else tr("ตอบให้ครบก่อนบันทึก", "Answer all to save"),
                        color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (whyOpen && weight != null) SheetFrame({ whyOpen = false }) { WhySheet(weight, moves) }
        if (noteOpen) SheetFrame({ noteOpen = false }) { NoteSheet(task, answers, weight?.priority, today) }
    }
    toast?.let { t ->
        Box(Modifier.fillMaxWidth().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
            Text(t, Modifier.clip(RoundedCornerShape(999.dp)).background(C.text).padding(horizontal = 14.dp, vertical = 8.dp), color = C.bg, fontSize = TS.caption)
        }
    }
}

/** Sets one answer; a new status clears the questions it no longer asks, "ไม่มี" clears when they need it. */
private fun pick(a: Map<String, Ans>, k: String, ans: Ans): Map<String, Ans> {
    var out = a + (k to ans)
    if (k == "status" && ans.v in CLOSED) out = mapOf(k to ans)
    if (k == "status" && ans.v != ReviewWrite.Progress.STUCK.name) out = out - "why"
    if (k == "wait" && ans.v == "no") out = out - "wdate"
    return out
}

/** "ทำไมระบบให้ระดับนี้": the points, the level they make, and the urgency, each with its reason. */
@Composable
private fun WhySheet(w: Review.Weight, moves: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("ทำไมระบบให้ระดับนี้", "Why this level"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
        Text(
            tr("สำคัญ กับ ด่วน คิดแยกกัน สำคัญดูจากผลและคนที่เกี่ยว ด่วนดูจากเวลาอย่างเดียว", "Importance and urgency are separate: importance from what happens and who is involved, urgency from time only"),
            color = C.muted, fontSize = TS.caption,
        )
        w.parts.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(p.label, Modifier.weight(1f), color = C.text, fontSize = TS.body)
                Text("+${p.points}", color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(tr("รวม ${w.score} คะแนน = สำคัญ ${importanceName(w.priority)}", "${w.score} points = ${importanceName(w.priority)}"), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
        Text(
            tr("เกณฑ์: 60 ขึ้นไป 🔺, 45 ⏫, 25 🔼, 10 ปกติ, ต่ำกว่านั้น 🔽 จดลงโน้ตเป็นธงความสำคัญเดิม", "Levels: 60 and up 🔺, 45 ⏫, 25 🔼, 10 normal, below 🔽. Written as the usual priority flag"),
            color = C.muted, fontSize = TS.caption,
        )
        Text(tr("ด่วน${w.urgency.label}", "Urgency ${w.urgency.label}"), Modifier.padding(top = 6.dp), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
        Text(w.urgencyWhy, color = C.text2, fontSize = TS.body)
        w.flags.forEach { Text(it, color = C.amber, fontSize = TS.body, fontWeight = FontWeight.SemiBold) }
        Text(tr("เลื่อนไปวันหลังมาแล้ว $moves ครั้ง (จากบันทึกการเลื่อนของแอป)", "Moved later $moves times (from the app's move log)"), color = C.muted, fontSize = TS.caption)
    }
}

/** "ดูต้นฉบับ": the task's lines in the note as they would be after saving these answers. */
@Composable
private fun NoteSheet(task: Task, a: Map<String, Ans>, priority: Priority?, today: LocalDate) {
    val now = listOf(task.raw) + task.notes.map { "    - $it" }
    val after = if (a["status"] == null) now else now.toMutableList().also { lines ->
        ReviewWrite.apply(lines, 0, task, toAnswers(a), priority, today)?.let { lines.add(""); lines.add(tr("+ งานใหม่: ", "+ new task: ") + it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("ถ้ากดบันทึก จะจดลงโน้ตแบบนี้", "Saving writes this into the note"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
        Text(
            if (ReviewMode.writes) tr("บรรทัดสีเหลืองคือที่เปลี่ยน", "Yellow lines change")
            else tr("ตอนนี้ยังไม่จดจริง (ปิดไว้ในตั้งค่า) บรรทัดสีเหลืองคือที่จะเปลี่ยน", "Not writing yet (off in Settings). Yellow lines would change"),
            color = C.muted, fontSize = TS.caption,
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(10.dp)) {
            after.forEach { line ->
                val changed = line !in now
                Text(
                    line.ifEmpty { " " },
                    Modifier.then(if (changed) Modifier.clip(RoundedCornerShape(3.dp)).background(C.amberSoft) else Modifier),
                    color = if (changed) C.amber else C.text2, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 19.sp,
                )
            }
        }
    }
}
