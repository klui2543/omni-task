package app.omnitask.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.data.CalendarReader
import app.omnitask.data.MonthMarksStore
import app.omnitask.model.CellStyle
import app.omnitask.model.FrameColor
import app.omnitask.model.MonthGrid
import app.omnitask.model.MonthMarks
import app.omnitask.model.tr
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.omnitask.widget.AppRestriction
import app.omnitask.model.DayWork
import app.omnitask.widget.MonthData
import app.omnitask.widget.MonthPainter
import app.omnitask.widget.OmniWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/** Room above the first row for a frame's name tag; the same as on the widget, whose painter draws this grid too. */
private const val TAG_INSET = 8f
private const val CELL = 46f

/**
 * The month widget's page in the app: tap days or drag across them, then give them a coloured frame (with a name if
 * wanted) or take frames off; with one day chosen, add or remove its emoji. Every change is saved at once, to the
 * phone and the vault, and the widget redraws.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthScreen(request: MonthRequest, state: UiState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(request.day.withDayOfMonth(1)) }
    var selection by remember { mutableStateOf(emptySet<LocalDate>()) }
    // A small file in the app's own storage, read once as the page opens.
    var marks by remember { mutableStateOf(MonthMarksStore.local(context)) }
    var color by remember { mutableStateOf(FrameColor.BLUE) }
    var label by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MonthMarks.Frame?>(null) }
    var holidays by remember { mutableStateOf(emptyMap<LocalDate, String>()) }
    var events by remember { mutableStateOf(emptyList<app.omnitask.notify.CalendarEvent>()) }
    var style by remember { mutableStateOf(MonthMarksStore.cellStyle(context)) }

    LaunchedEffect(request) {
        month = request.day.withDayOfMonth(1)
        selection = if (request.pick) setOf(request.day) else emptySet()
        editing = null
    }
    LaunchedEffect(month) {
        val days = MonthGrid.days(month)
        withContext(Dispatchers.IO) {
            holidays = runCatching { CalendarReader.holidays(context, days.first(), days.last()) }.getOrDefault(emptyMap())
            val holidayIds = runCatching { CalendarReader.calendars(context).filter { it.holiday }.map { it.id }.toSet() }.getOrDefault(emptySet())
            events = runCatching { CalendarReader.events(context, days.first().atStartOfDay(), days.last().plusDays(1).atStartOfDay()) }.getOrDefault(emptyList())
                .filter { it.calendarId !in holidayIds }
        }
    }

    fun commit(next: MonthMarks) {
        marks = next
        scope.launch(Dispatchers.IO) {
            runCatching { MonthMarksStore.save(context, next) }
            runCatching { OmniWidgets.refresh(context) }
        }
    }
    fun clear() { selection = emptySet(); editing = null; label = "" }

    val current = marks
    val days = MonthGrid.days(month)
    fun tasksOn(d: LocalDate) = state.tasks.filter { it.isOpen && (it.due ?: it.scheduled) == d }
    fun eventsOn(d: LocalDate) = events.filter { MonthData.onDay(it, d) }
    val work = remember(state.tasks, events, month, state.today) { MonthData.work(days, month, ::tasksOn, ::eventsOn, state.today) }
    // Events on most days of the month (a workout): left out of the cells, listed faint under a day.
    val routine = remember(events, month) {
        val inMonth = days.filter { it.monthNumber == month.monthNumber }
        DayWork.routine(inMonth.associateWith { d -> eventsOn(d).map { it.title } }, inMonth)
    }
    fun dayLines(d: LocalDate): List<DayLine> = buildList {
        holidays[d]?.let { add(DayLine("", it, DayLine.Kind.LATE)) }
        eventsOn(d).sortedWith(compareBy<app.omnitask.notify.CalendarEvent>({ !it.allDay }, { it.begin })).forEach { e ->
            val time = if (e.allDay || e.begin.date != d) tr("ทั้งวัน", "All day") else "%02d:%02d".format(e.begin.hour, e.begin.minute)
            add(DayLine(time, e.title, if (e.title.trim() in routine) DayLine.Kind.ROUTINE else DayLine.Kind.EVENT))
        }
        tasksOn(d).forEach { add(DayLine(tr("งาน", "Task"), it.title, if (d < state.today) DayLine.Kind.LATE else DayLine.Kind.TASK)) }
        // Today also carries the work past its deadline, as the widget and Focus do.
        if (d == state.today) state.tasks.filter { t -> t.isOpen && t.due?.let { it < d } == true }.sortedBy { it.due }.forEach {
            add(DayLine(tr("เลย", "Late"), it.title, DayLine.Kind.LATE))
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("ปิด", "Close"), Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(6.dp), color = C.accentText, fontSize = TS.body)
            Text(MonthGrid.title(month), Modifier.weight(1f), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            SquareButton(Ic.back, tr("เดือนก่อน", "Previous month"), { month = month.minusMonths(1); clear() })
            Box(Modifier.width(6.dp))
            SquareButton(Ic.next, tr("เดือนถัดไป", "Next month"), { month = month.plusMonths(1); clear() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        MonthGrid.heads.forEachIndexed { i, h ->
                            Text(h, Modifier.weight(1f), color = if (i == 0) C.red else C.faint, fontSize = TS.caption, textAlign = TextAlign.Center)
                        }
                    }
                    Grid(
                        context, month, days, state.today, current, holidays, work, style, selection,
                        onTap = { d ->
                            selection = if (d in selection) selection - d else selection + d
                            if (selection.isEmpty()) editing = null
                        },
                        onLongPress = { d -> selection = setOf(d); editing = null },
                        onDrag = { d, add -> selection = if (add) selection + d else selection - d },
                    )
                }
            }
            if (selection.isEmpty()) {
                Text(
                    tr("แตะวัน หรือลากนิ้วผ่านหลายวัน เพื่อใส่กรอบ\nแตะวันเดียวเพื่อดูงานและติด Emoji", "Tap days or drag across them to frame them.\nPick one day to see its work and add emoji."),
                    Modifier.padding(horizontal = 6.dp), color = C.muted, fontSize = TS.caption,
                )
                FrameList(
                    current, month,
                    onPick = { f -> selection = f.days.toSet(); color = f.color; label = f.label; editing = f },
                    onDelete = { f -> commit(current.without(f)) },
                )
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("งานในช่องวัน (widget และปฏิทินในแอป)", "Work in the day cells (widget and the app's calendar)"), color = C.text, fontSize = TS.body)
                        Segmented(
                            listOf(CellStyle.TINT to tr("สีตามความแน่น", "Shade by load"), CellStyle.BARS to tr("ชื่องาน", "Titles")),
                            style,
                            { next ->
                                style = next
                                scope.launch(Dispatchers.IO) {
                                    MonthMarksStore.setCellStyle(context, next)
                                    runCatching { OmniWidgets.refresh(context) }
                                }
                            },
                            Modifier.fillMaxWidth(),
                        )
                        Text(
                            tr("นัดที่มีเกือบทุกวัน เช่น ออกกำลังกาย ไม่นับในช่อง แต่ยังอยู่ในรายการของวัน", "An event on nearly every day, such as a workout, is left out of the cells but stays in the day's list."),
                            color = C.muted, fontSize = TS.caption,
                        )
                        // A restricted app has its taps on the widget held back by the phone for seconds. Checked again
                        // each time the page comes back, as from the settings page the row opens.
                        var restricted by remember { mutableStateOf(AppRestriction.restricted(context)) }
                        LifecycleResumeEffect(Unit) {
                            restricted = AppRestriction.restricted(context)
                            onPauseOrDispose { }
                        }
                        if (restricted) {
                            Text(
                                tr(
                                    "แอปถูกจำกัดการทำงานเบื้องหลัง widget จะค้างเป็นช่วงแล้วกระโดด\nแตะเพื่อเปิดตั้งค่าแอป เลือก แบตเตอรี่ แล้วเลือก ไม่จำกัด (Samsung: เอาออกจากแอปที่หลับด้วย)",
                                    "The phone restricts this app in the background, so the widget freezes and then jumps.\nTap to open the app's settings, choose Battery, then Unrestricted (Samsung: also remove it from the sleeping apps).",
                                ),
                                Modifier.clip(RoundedCornerShape(8.dp)).background(C.amberSoft).clickable {
                                    runCatching { context.startActivity(AppRestriction.settingsIntent(context)) }
                                }.padding(10.dp).fillMaxWidth(),
                                color = C.text, fontSize = TS.caption,
                            )
                        }
                        // For finding out why a tap on the widget was slow: what it did and when, to send along.
                        Text(
                            tr("แชร์บันทึกการทำงานของ widget", "Share the widget's log"),
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                val text = app.omnitask.widget.WidgetLog.all(context).lines().takeLast(400).joinToString("\n")
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "Omni widget log")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, text.ifEmpty { "(empty)" })
                                runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                            }.padding(vertical = 4.dp),
                            color = C.accentText, fontSize = TS.caption,
                        )
                    }
                }
            } else {
                if (selection.size == 1) {
                    val d = selection.first()
                    DayCard(d, dayLines(d), short = false)
                    StickerEditor(context, d, current.stickersOn(d)) { commit(current.withStickers(d, it)) }
                } else {
                    Card {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("เลือก ${selection.size} วัน", "${selection.size} days chosen"), Modifier.weight(1f), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                                Text(tr("แตะช่องเดิมเพื่อเอาออก", "Tap a day again to drop it"), color = C.muted, fontSize = TS.caption)
                            }
                            selection.sorted().forEach { d -> DayLines(d, dayLines(d), short = true) }
                        }
                    }
                }
                FrameEditor(
                    selection, color, label, editing != null,
                    onColor = { color = it },
                    onLabel = { label = it },
                    onRemove = {
                        commit((editing?.let { current.without(it) } ?: current).withoutFrames(selection))
                        clear()
                    },
                    onSave = {
                        commit((editing?.let { current.without(it) } ?: current).withFrame(selection, color, label))
                        clear()
                    },
                    onClear = { clear() },
                )
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Grid(
    context: Context,
    month: LocalDate,
    days: List<LocalDate>,
    today: LocalDate,
    marks: MonthMarks,
    holidays: Map<LocalDate, String>,
    work: Map<LocalDate, List<MonthData.Mark>>,
    style: CellStyle,
    selection: Set<LocalDate>,
    onTap: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
    onDrag: (LocalDate, Boolean) -> Unit,
) {
    val rows = days.size / 7
    // Taller days with title bars, as in the app's calendar, so two fit under the number.
    val cell = if (style == CellStyle.BARS) 60f else CELL
    BoxWithConstraints(Modifier.fillMaxWidth().height((TAG_INSET + rows * cell).dp)) {
        val widthDp = maxWidth.value
        val palette = Palettes.current
        val bitmap = remember(month, marks, holidays, work, style, widthDp, palette, today) {
            MonthPainter(context, palette).draw(MonthData(today, month, 0, marks, holidays, work, null, emptyList(), style), widthDp, cell, rows).asImageBitmap()
        }
        Image(bitmap, MonthGrid.title(month), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val chosen by rememberUpdatedState(selection)
        val tap by rememberUpdatedState(onTap)
        val press by rememberUpdatedState(onLongPress)
        val drag by rememberUpdatedState(onDrag)
        val density = LocalDensity.current
        val cellW = with(density) { maxWidth.toPx() } / 7f
        val cellH = with(density) { cell.dp.toPx() }
        val top = with(density) { TAG_INSET.dp.toPx() }
        fun dayAt(o: Offset): LocalDate? {
            val col = (o.x / cellW).toInt()
            val row = ((o.y - top) / cellH).toInt()
            if (o.y < top || col !in 0..6 || row !in 0 until rows) return null
            return days[row * 7 + col]
        }
        Column(
            Modifier.fillMaxSize().padding(top = TAG_INSET.dp)
                .pointerInput(days, cellW) { detectTapGestures(onTap = { o -> dayAt(o)?.let(tap) }, onLongPress = { o -> dayAt(o)?.let(press) }) }
                .pointerInput(days, cellW) {
                    var adding = true
                    var last: LocalDate? = null
                    detectDragGestures(
                        onDragStart = { o -> dayAt(o)?.let { d -> adding = d !in chosen; last = d; drag(d, adding) } },
                        onDrag = { change, _ -> dayAt(change.position)?.takeIf { it != last }?.let { d -> last = d; drag(d, adding) } },
                    )
                },
        ) {
            for (w in 0 until rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (i in 0 until 7) {
                        val d = days[w * 7 + i]
                        Box(Modifier.weight(1f).fillMaxSize().padding(3.dp)) {
                            if (d in selection) {
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(C.accent.copy(alpha = 0.18f))
                                        .border(1.5.dp, C.accent, RoundedCornerShape(8.dp)),
                                )
                                Text("✓", Modifier.align(Alignment.TopEnd).padding(end = 3.dp), color = C.accentText, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Colour, name, and the buttons for the days chosen. */
@Composable
private fun FrameEditor(
    selection: Set<LocalDate>,
    color: FrameColor,
    label: String,
    editing: Boolean,
    onColor: (FrameColor) -> Unit,
    onLabel: (String) -> Unit,
    onRemove: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Card {
        Column(Modifier.padding(14.dp)) {
            val pieces = MonthMarks.pieces(selection)
            Text(
                if (editing) tr("แก้กรอบ", "Edit frame") else tr("เลือกแล้ว ${selection.size} วัน", "${selection.size} days chosen"),
                color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (pieces.size == 1) span(pieces[0]) else tr("วันไม่ติดกัน จะได้ ${pieces.size} กรอบ", "Days apart: ${pieces.size} frames"),
                Modifier.padding(top = 2.dp, bottom = 12.dp), color = C.muted, fontSize = TS.caption,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FrameColor.entries.forEach { c ->
                    val on = c == color
                    Box(
                        Modifier.size(30.dp).then(if (on) Modifier.border(2.dp, C.text, CircleShape).padding(4.dp) else Modifier)
                            .clip(CircleShape).background(Color(c.argb)).clickable { onColor(c) },
                    )
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).border(1.5.dp, C.faint, CircleShape).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.close, tr("เอากรอบออก", "Remove frame"), tint = C.faint, modifier = Modifier.size(14.dp)) }
            }
            Text(tr("ชื่อกรอบ (ไม่ใส่ก็ได้)", "Name (optional)"), Modifier.padding(top = 14.dp, bottom = 6.dp), color = C.muted, fontSize = TS.caption)
            TextBox(label, tr("เช่น สอบกลางภาค", "e.g. Exams"), onLabel)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(tr("ล้างที่เลือก", "Clear"), onClear, Modifier.weight(1f))
                PrimaryButton(tr("บันทึกกรอบ", "Save frame"), onSave, Modifier.weight(1f))
            }
        }
    }
}

/** The month's frames, to pick one for editing; the bin takes it off. */
@Composable
private fun FrameList(marks: MonthMarks, month: LocalDate, onPick: (MonthMarks.Frame) -> Unit, onDelete: (MonthMarks.Frame) -> Unit) {
    val frames = marks.framesBetween(month, month.plusMonths(1).minusDays(1))
    if (frames.isEmpty()) return
    Card {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(tr("กรอบเดือนนี้", "Frames this month"), Modifier.padding(bottom = 4.dp), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            frames.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(f) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 18.dp, height = 14.dp).border(2.dp, Color(f.color.argb), RoundedCornerShape(3.dp)))
                    Text(f.label.ifEmpty { tr("ไม่มีชื่อ", "No name") }, Modifier.weight(1f).padding(start = 12.dp), color = if (f.label.isEmpty()) C.muted else C.text, fontSize = TS.body)
                    Text(span(f.days), color = C.muted, fontSize = TS.caption)
                    Icon(
                        Ic.trash, tr("ลบกรอบ", "Delete frame"), tint = C.faint,
                        modifier = Modifier.padding(start = 10.dp).size(18.dp).clip(CircleShape).clickable { onDelete(f) },
                    )
                }
            }
        }
    }
}

/** "อา 18 ถึง พ 21 ต.ค.", or one day alone. */
private fun span(days: List<LocalDate>): String {
    fun dow(d: LocalDate) = MonthGrid.heads[(d.dayOfWeek.ordinal + 1) % 7]
    val a = days.first()
    val b = days.last()
    return if (a == b) "${dow(a)} ${MonthGrid.dayMonth(a)}"
    else if (a.monthNumber == b.monthNumber) "${dow(a)} ${a.dayOfMonth} " + tr("ถึง", "to") + " ${dow(b)} ${MonthGrid.dayMonth(b)}"
    else "${dow(a)} ${MonthGrid.dayMonth(a)} " + tr("ถึง", "to") + " ${dow(b)} ${MonthGrid.dayMonth(b)}"
}

private fun emojiSets() = listOf(
    tr("อารมณ์", "Mood") to "😀 😊 🥰 😍 😎 🤩 😴 😢 😭 😡 🤒 🤯 😌 🥳 😇 🫶",
    tr("งาน", "Work") to "📌 📝 💼 📊 💻 📞 📅 ⏰ ✅ ❗ 🔥 💡 📚 🎯 💰 🏦",
    tr("เดินทาง", "Travel") to "✈️ 🚗 🚆 🏖️ 🏔️ 🏕️ 🗺️ 🧳 🏨 🚢 🛵 📸 🌏 🏝️ 🚌 ⛩️",
    tr("อาหาร", "Food") to "🍜 🍣 🍕 🍔 🍰 🎂 ☕ 🍺 🍷 🥗 🍱 🍲 🍦 🍩 🥤 🍳",
    tr("กิจกรรม", "Activity") to "💪 🏃 🧘 🏊 🚴 ⚽ 🏸 🎮 🎬 🎵 🎤 🎨 🛍️ 🧹 🐶 🌱",
    tr("อื่นๆ", "Other") to "❤️ ⭐ 🎉 🎁 🎃 🎄 🏥 💊 🦷 💇 🙏 🕯️ 🌙 ☀️ 🌧️ 🔔",
).map { (name, list) -> name to list.split(' ') }

private const val KEY_RECENT = "month.recentEmoji"

/** The emoji on one day: tap one to take it off, pick from the sets or type any other. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerEditor(context: Context, day: LocalDate, stickers: List<String>, onChange: (List<String>) -> Unit) {
    val prefs = remember { context.getSharedPreferences("omnitask", Context.MODE_PRIVATE) }
    var recent by remember { mutableStateOf(prefs.getString(KEY_RECENT, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()) }
    val sets = (if (recent.isEmpty()) emptyList() else listOf(tr("ใช้ล่าสุด", "Recent") to recent)) + emojiSets()
    var tab by remember { mutableStateOf(0) }
    var typed by remember(day) { mutableStateOf("") }
    val full = stickers.size >= MonthMarks.MAX_STICKERS
    fun add(e: String) {
        val emoji = e.trim().take(16)
        if (emoji.isEmpty() || full) return
        onChange(stickers + emoji)
        recent = (listOf(emoji) + recent.filter { it != emoji }).take(16)
        prefs.edit().putString(KEY_RECENT, recent.joinToString("\n")).apply()
    }
    Card {
        Column(Modifier.padding(14.dp)) {
            Text(tr("Emoji ของวันที่ ${MonthGrid.dayMonth(day)}", "Emoji on ${MonthGrid.dayMonth(day)}"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(
                if (stickers.isEmpty()) tr("ยังไม่มี เลือกได้หลายตัว widget แสดง 3 ตัวแรก", "None yet. Add several; the widget shows the first 3.")
                else tr("แตะ Emoji เพื่อเอาออก", "Tap one to take it off"),
                Modifier.padding(top = 2.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption,
            )
            if (stickers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    stickers.forEachIndexed { i, e ->
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(C.raised).clickable { onChange(stickers.filterIndexed { j, _ -> j != i }) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sets.forEachIndexed { i, (name, _) -> Chip(name, i == tab.coerceAtMost(sets.size - 1), { tab = i }) }
            }
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sets[tab.coerceAtMost(sets.size - 1)].second.forEach { e ->
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = !full) { add(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 22.sp, color = if (full) C.faint else Color.Unspecified) }
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TextBox(typed, tr("หรือพิมพ์ Emoji อื่น", "Or type another emoji")) { typed = it } }
                Box(Modifier.width(8.dp))
                GhostButton(tr("เพิ่ม", "Add"), { add(typed); typed = "" })
            }
            if (full) Text(tr("ครบ ${MonthMarks.MAX_STICKERS} ตัวแล้ว", "That is ${MonthMarks.MAX_STICKERS}, the most a day keeps"), Modifier.padding(top = 8.dp), color = C.amber, fontSize = TS.caption)
        }
    }
}

@Composable
private fun TextBox(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body)
                inner()
            }
        },
    )
}

/** One line of a day's list on this page: when, what, and the colour of its bar in the cells. */
private data class DayLine(val time: String, val title: String, val kind: Kind) {
    enum class Kind { EVENT, TASK, LATE, ROUTINE }
}

private fun dayName(d: LocalDate) = MonthGrid.heads[(d.dayOfWeek.ordinal + 1) % 7] + " " + MonthGrid.dayMonth(d)

/** The day tapped: everything on it, so the owner sees what the day holds before framing it. */
@Composable
private fun DayCard(day: LocalDate, lines: List<DayLine>, short: Boolean) {
    Card {
        Column(Modifier.padding(14.dp)) { DayLines(day, lines, short) }
    }
}

/**
 * A day's heading and its lines, in time order. [short] (several days chosen) shows the first two besides the daily
 * routine and counts the rest.
 */
@Composable
private fun DayLines(day: LocalDate, lines: List<DayLine>, short: Boolean) {
    val counted = lines.filter { it.kind != DayLine.Kind.ROUTINE }
    val shown = if (short) counted.take(2) else lines
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayName(day), Modifier.weight(1f), color = if (short) C.accentText else C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
            if (!short) Text(if (counted.isEmpty()) tr("ว่าง", "Free") else tr("${counted.size} รายการ", "${counted.size} items"), color = C.muted, fontSize = TS.caption)
        }
        if (shown.isEmpty()) Text(tr("ว่าง", "Nothing planned"), color = C.faint, fontSize = TS.caption)
        shown.forEach { line ->
            val routine = line.kind == DayLine.Kind.ROUTINE
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.time, Modifier.width(48.dp), color = C.muted, fontSize = TS.caption, maxLines = 1)
                Box(
                    Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(
                        when (line.kind) {
                            DayLine.Kind.EVENT -> C.accent
                            DayLine.Kind.TASK -> C.lime
                            DayLine.Kind.LATE -> C.red
                            DayLine.Kind.ROUTINE -> C.faint
                        },
                    ),
                )
                Text(
                    line.title + if (routine) tr(" (ทุกวัน)", " (daily)") else "",
                    Modifier.weight(1f).padding(start = 8.dp),
                    color = if (routine) C.faint else C.text, fontSize = TS.body, maxLines = 2,
                )
            }
        }
        if (short && counted.size > shown.size) Text(tr("อีก ${counted.size - shown.size} รายการ", "${counted.size - shown.size} more"), color = C.muted, fontSize = TS.caption)
    }
}
