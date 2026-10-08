package app.omnitask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import app.omnitask.MainActivity
import app.omnitask.R
import app.omnitask.data.TaskLine
import app.omnitask.data.VaultRepository
import app.omnitask.model.Focus
import app.omnitask.model.Quadrant
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.notify.Scheduler
import app.omnitask.model.Appearance
import app.omnitask.model.DayPlan
import app.omnitask.model.PaletteChoice
import app.omnitask.model.Projects
import app.omnitask.model.ThemeMode
import app.omnitask.ui.Palette
import app.omnitask.ui.Palettes
import app.omnitask.ui.accent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Redraws every Omni Task widget; called after the app or a widget changes the vault. */
object OmniWidgets {
    suspend fun refresh(context: Context) {
        listOf(QuickAddWidget(), TodayWidget(), NextWidget(), MatrixWidget(), WaitingWidget()).forEach {
            runCatching { it.updateAll(context) }
        }
    }
}

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

private fun open(context: Context, action: String? = null) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .also { if (action != null) it.putExtra(MainActivity.EXTRA_ACTION, action) }
        // Distinct data keeps each button's PendingIntent separate.
        .setData(android.net.Uri.parse("omnitask://widget/${action ?: "open"}")),
)

/**
 * A colour from the app's palette for widgets: the chosen palette, and with "follow the system" a pair the
 * launcher switches between in light and dark mode.
 */
private fun cp(pick: (Palette) -> Color): ColorProvider = when {
    Appearance.palette == PaletteChoice.MIDNIGHT -> ColorProvider(pick(Palettes.midnight))
    Appearance.themeMode == ThemeMode.LIGHT -> ColorProvider(pick(Palettes.linearLight))
    Appearance.themeMode == ThemeMode.DARK -> ColorProvider(pick(Palettes.linearDark))
    else -> ColorProvider(day = pick(Palettes.linearLight), night = pick(Palettes.linearDark))
}

@Composable
private fun Label(text: String, color: ColorProvider = cp { it.muted }, size: TextUnit = 12.sp, bold: Boolean = false, maxLines: Int = 1, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text, modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal),
        maxLines = maxLines,
    )
}

@Composable
private fun Panel(context: Context, modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().cornerRadius(22.dp).background(cp { it.card }).clickable(open(context)),
    ) { content() }
}

// ---- 4×1 quick add ----

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        app.omnitask.model.Lang.load(context)
        Appearance.load(context)
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(cp { it.card }).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(
                    tr("เพิ่มงาน เช่น ส่งรายงาน พรุ่งนี้", "Add a task, e.g. report tomorrow"), cp { it.muted }, 14.sp,
                    modifier = GlanceModifier.defaultWeight().clickable(open(context, MainActivity.ACTION_ADD)),
                )
                Circle(context, R.drawable.ic_w_mic, cp { it.raised }, cp { it.text2 }, MainActivity.ACTION_VOICE)
                Spacer(GlanceModifier.width(6.dp))
                Circle(context, R.drawable.ic_w_spark, cp { it.accent }, cp { it.onAccent }, MainActivity.ACTION_ASSISTANT)
            }
        }
    }
}

@Composable
private fun Circle(context: Context, icon: Int, bg: ColorProvider, fg: ColorProvider, action: String, size: Int = 40) {
    Box(
        GlanceModifier.size(size.dp).cornerRadius((size / 2).dp).background(bg).clickable(open(context, action)),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(icon), null, GlanceModifier.size((size * 0.45).dp), colorFilter = ColorFilter.tint(fg)) }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

// ---- 4×2 today, tickable ----

private val RAW = ActionParameters.Key<String>("raw")

/** Ticks a task from the widget, repeating tasks included, then redraws the widgets and the alarms. */
class TickAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val raw = parameters[RAW] ?: return
        withContext(Dispatchers.IO) {
            val task = Scheduler.loadVaultTasks(context).firstOrNull { it.raw == raw } ?: return@withContext
            val repo = VaultRepository(context)
            runCatching {
                if (task.recurrence != null) repo.completeRecurring(task, LocalDate.now())
                else repo.rewriteLine(task) { TaskLine.setDone(it, true, LocalDate.now()) }
            }
            runCatching { Scheduler.reschedule(context, Scheduler.loadVaultTasks(context)) }
        }
        OmniWidgets.refresh(context)
    }
}

/**
 * Today as a timeline, as on the Focus screen: one header line (what is left, add), then events and
 * timed tasks in time order with a line at the current time, then the tasks without a time. It scrolls
 * when the day is longer than the widget; tap a ring to tick a task.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            val now = LocalDateTime.now()
            val left = data.todayTasks.size
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(cp { it.card }).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            ) {
                Row(GlanceModifier.fillMaxWidth().clickable(open(context)), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Label(tr("วันนี้", "Today"), cp { it.text }, 16.sp, bold = true)
                        Label(
                            data.today.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", app.omnitask.ui.TH)) + ", " +
                                if (left == 0) tr("ไม่มีงานค้าง", "all done") else tr("เหลือ $left งาน", "$left left"),
                            cp { it.muted }, 12.sp,
                        )
                    }
                    Circle(context, R.drawable.ic_w_plus, cp { it.accent }, cp { it.onAccent }, MainActivity.ACTION_ADD, size = 34)
                }
                Spacer(GlanceModifier.height(6.dp))
                val rows = timeline(data, now)
                if (rows.isEmpty()) {
                    Label(tr("วันนี้ว่าง ไม่มีงานหรือนัด", "Nothing due or booked today"), cp { it.muted }, 13.sp, modifier = GlanceModifier.padding(top = 8.dp))
                }
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    rows.forEach { r ->
                        item {
                            when (r) {
                                is Line.Now -> NowLine(r.time)
                                is Line.Head -> Label(r.text, cp { it.faint }, 11.5.sp, modifier = GlanceModifier.padding(top = 8.dp, bottom = 2.dp))
                                is Line.Event -> EventLine(context, r)
                                is Line.Todo -> TodoLine(context, r, data.today)
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface Line {
    data class Now(val time: LocalTime) : Line
    data class Head(val text: String) : Line
    data class Event(val time: String, val title: String, val sub: String) : Line
    data class Todo(val time: String, val task: Task, val sub: String) : Line
}

/** The rows: timed items with the now line among them, then late and untimed tasks under one heading. */
private fun timeline(data: WidgetData, now: LocalDateTime): List<Line> {
    val out = ArrayList<Line>()
    val plan = data.plan
    val timed = plan.filter { it.part != DayPlan.Part.LATE && it.part != DayPlan.Part.ANYTIME }.flatMap { it.items }
    var nowShown = timed.isEmpty()
    timed.forEach { item ->
        val time = item.time
        if (!nowShown && time != null && time > now.toLocalTime()) { out += Line.Now(now.toLocalTime()); nowShown = true }
        out += line(item, time?.let(::hm) ?: "")
    }
    if (!nowShown) out += Line.Now(now.toLocalTime())
    val rest = plan.filter { it.part == DayPlan.Part.LATE || it.part == DayPlan.Part.ANYTIME }.flatMap { it.items }
    if (rest.isNotEmpty()) {
        out += Line.Head(tr("ไม่ระบุเวลา", "Any time"))
        rest.forEach { out += line(it, "") }
    }
    return out
}

private fun line(item: DayPlan.Item, time: String): Line = when (item) {
    is DayPlan.Item.EventItem -> Line.Event(
        time, item.event.title,
        if (item.event.allDay) tr("ทั้งวัน", "All day") else hm(item.event.begin.toLocalTime()) + tr(" ถึง ", " to ") + hm(item.event.end.toLocalTime()),
    )
    is DayPlan.Item.TaskItem -> Line.Todo(time, item.task, listOfNotNull(Projects.projectOf(item.task)?.let { "#$it" }, Focus.waitingFor(item.task)?.let { tr("รอ $it", "waiting on $it") }).joinToString(", "))
}

@Composable
private fun NowLine(time: LocalTime) {
    Row(GlanceModifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(hm(time), cp { it.red }, 10.5.sp, bold = true, modifier = GlanceModifier.width(40.dp))
        Box(GlanceModifier.size(6.dp).cornerRadius(3.dp).background(cp { it.red })) {}
        Box(GlanceModifier.defaultWeight().height(1.5.dp).background(cp { it.red })) {}
    }
}

@Composable
private fun EventLine(context: Context, e: Line.Event) {
    Row(GlanceModifier.fillMaxWidth().height(40.dp).clickable(open(context)), verticalAlignment = Alignment.CenterVertically) {
        Label(e.time, cp { it.muted }, 12.sp, modifier = GlanceModifier.width(40.dp))
        Box(GlanceModifier.width(20.dp), contentAlignment = Alignment.Center) {
            Box(GlanceModifier.width(3.dp).height(22.dp).cornerRadius(2.dp).background(cp { it.teal })) {}
        }
        Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
            Label(e.title, cp { it.text }, 14.sp)
            Label(e.sub, cp { it.tealText }, 11.5.sp)
        }
    }
}

@Composable
private fun TodoLine(context: Context, r: Line.Todo, today: LocalDate) {
    val t = r.task
    val late = t.due?.let { it < today } == true
    Row(GlanceModifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(r.time, cp { it.muted }, 12.sp, modifier = GlanceModifier.width(40.dp))
        Box(
            GlanceModifier.size(20.dp).clickable(actionRunCallback<TickAction>(actionParametersOf(RAW to t.raw))),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_w_ring), tr("ติ๊กเสร็จ", "Mark done"), GlanceModifier.size(19.dp),
                colorFilter = ColorFilter.tint(if (late) cp { it.red } else cp { it.faint }))
        }
        Column(GlanceModifier.defaultWeight().padding(start = 8.dp).clickable(open(context))) {
            Label(t.title, cp { it.text }, 14.sp)
            val sub = listOfNotNull(if (late) tr("เลยกำหนด", "Overdue") else null, r.sub.ifEmpty { null }).joinToString(", ")
            if (sub.isNotEmpty()) Label(sub, if (late) cp { it.red } else cp { it.muted }, 11.5.sp)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

// ---- 2×2 next up ----

class NextWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            Panel(context) {
                Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                    val t = data.brief.must.firstOrNull() ?: data.todayTasks.firstOrNull() ?: data.brief.future.firstOrNull()
                    Label(tr("ทำต่อเลย", "Up next"), cp { it.muted }, 12.sp)
                    Label(t?.title ?: tr("ว่างแล้ว", "All clear"), cp { it.text }, 15.sp, bold = true, maxLines = 2)
                    t?.let {
                        val late = it.due?.let { d -> d < data.today } == true
                        Label(
                            when {
                                late -> tr("เลยกำหนด", "Overdue")
                                it.due == data.today -> tr("ครบวันนี้", "Due today")
                                Focus.isFutureWork(it) -> tr("ลงทุนอนาคต", "Future")
                                else -> tr("วันนี้", "Today")
                            },
                            if (late || it.due == data.today) cp { c -> c.red } else cp { c -> c.muted }, 12.sp,
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Label(tr("ช่องว่างถัดไป", "Next free time"), cp { it.muted }, 12.sp)
                    val gap = data.nextGap(LocalDateTime.now())
                    Label(gap?.let { (a, b) -> hm(a) + tr(" ถึง ", " to ") + hm(b) } ?: tr("วันนี้เต็มแล้ว", "Day is full"), cp { it.accentText }, 14.sp, bold = true)
                }
            }
        }
    }
}

class NextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWidget()
}

// ---- 2×2 Eisenhower ----

class MatrixWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            val counts = data.quadrantCounts()
            Panel(context) {
                Column(GlanceModifier.fillMaxSize().padding(8.dp)) {
                    listOf(Quadrant.DO to Quadrant.PLAN, Quadrant.QUICK to Quadrant.LATER).forEachIndexed { i, (a, b) ->
                        if (i > 0) Spacer(GlanceModifier.height(6.dp))
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            Cell(a, counts[a] ?: 0, GlanceModifier.defaultWeight())
                            Spacer(GlanceModifier.width(6.dp))
                            Cell(b, counts[b] ?: 0, GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(q: Quadrant, count: Int, modifier: GlanceModifier) {
    Column(modifier.fillMaxSize().cornerRadius(14.dp).background(cp { it.sunken }).padding(horizontal = 9.dp, vertical = 7.dp)) {
        Label(q.label, ColorProvider(q.accent), 11.5.sp, bold = true, maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Label("$count", cp { it.text }, 20.sp, bold = true)
    }
}

class MatrixWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MatrixWidget()
}

// ---- 4×1 waiting and future ----

class WaitingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            Panel(context) {
                Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val waiting = data.tasks.filter { it.isOpen && Focus.isWaiting(it) && !Focus.isSomeday(it) }
                    val w = data.brief.waiting.firstOrNull()
                    Column(GlanceModifier.defaultWeight()) {
                        Label(tr("คนรออยู่ ", "Waiting ") + waiting.size, cp { it.muted }, 12.sp)
                        Label(w?.title ?: tr("ไม่มีใครรอ", "Nobody waiting"), cp { it.text }, 13.sp)
                        w?.let {
                            val age = Focus.ageDays(it, data.today)
                            Label(
                                (Focus.waitingFor(it)?.let { n -> "$n " } ?: "") + (age?.let { a -> tr("รอ $a วัน", "waiting $a days") } ?: ""),
                                if ((age ?: 0) >= 7) cp { c -> c.red } else cp { c -> c.muted }, 12.sp,
                            )
                        }
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    val f = data.brief.future.firstOrNull()
                    Column(GlanceModifier.defaultWeight()) {
                        Label(tr("ลงทุนอนาคต", "Future"), cp { it.accentText }, 12.sp)
                        Label(f?.title ?: tr("ยังไม่ได้เลือกงาน", "None picked yet"), cp { it.text }, 13.sp)
                        f?.let { Label(it.firstStep?.let { s -> tr("ก้าวแรก: ", "First step: ") + s } ?: Focus.ageDays(it, data.today)?.let { a -> tr("ค้าง $a วัน", "$a days old") } ?: "", cp { it.muted }, 12.sp) }
                    }
                }
            }
        }
    }
}

class WaitingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaitingWidget()
}
