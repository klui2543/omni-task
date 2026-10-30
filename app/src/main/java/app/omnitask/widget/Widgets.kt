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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import app.omnitask.MainActivity
import app.omnitask.R
import app.omnitask.data.BehaviorLog
import app.omnitask.data.TaskLine
import app.omnitask.data.VaultRepository
import app.omnitask.model.Behavior
import app.omnitask.model.Focus
import app.omnitask.model.Quadrant
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import app.omnitask.model.load
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/** Redraws every Omni Task widget; called after the app or a widget changes the vault. */
object OmniWidgets {
    suspend fun refresh(context: Context) {
        CalendarWatch.armIfNeeded(context)
        listOf(QuickAddWidget(), TodayWidget(), NextWidget(), MatrixWidget(), WaitingWidget()).forEach {
            runCatching { it.updateAll(context) }
        }
        // The month widget keeps its data in its composition; a new stamp makes it load again.
        runCatching { MonthWidget.refresh(context) }
    }
}

internal fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

internal fun open(context: Context, action: String? = null) = actionStartActivity(
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
internal fun cp(pick: (Palette) -> Color): ColorProvider = when {
    Appearance.palette == PaletteChoice.MIDNIGHT -> ColorProvider(pick(Palettes.midnight))
    Appearance.themeMode == ThemeMode.LIGHT -> ColorProvider(pick(Palettes.graphiteLight))
    Appearance.themeMode == ThemeMode.DARK -> ColorProvider(pick(Palettes.graphiteDark))
    else -> ColorProvider(day = pick(Palettes.graphiteLight), night = pick(Palettes.graphiteDark))
}

@Composable
internal fun Label(text: String, color: ColorProvider = cp { it.muted }, size: TextUnit = 12.sp, bold: Boolean = false, maxLines: Int = 1, modifier: GlanceModifier = GlanceModifier, strike: Boolean = false) {
    Text(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        ),
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
        app.omnitask.ui.CalendarStyles.load(context)
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
internal fun Circle(context: Context, icon: Int, bg: ColorProvider, fg: ColorProvider, action: String, size: Int = 40) {
    Box(
        GlanceModifier.size(size.dp).cornerRadius((size / 2).dp).background(bg).clickable(open(context, action)),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(icon), null, GlanceModifier.size((size * 0.45).dp), colorFilter = ColorFilter.tint(fg)) }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

// ---- 4×2 today, tickable ----

internal val RAW = ActionParameters.Key<String>("raw")

/** Ticks a task from the widget, repeating tasks included, then redraws the widgets and the alarms. */
class TickAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val raw = parameters[RAW] ?: return
        withContext(Dispatchers.IO) {
            val task = Scheduler.loadVaultTasks(context).firstOrNull { it.raw == raw } ?: return@withContext
            val repo = VaultRepository(context)
            if (task.isOpen) BehaviorLog.record(context, Behavior.of(Behavior.DONE, task, LocalDateTime.now(), BehaviorLog.SRC))
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
 * Today and the days after it, Google Calendar's schedule widget in the app's colours (owner, 2026-10-30, mock round 4):
 * a narrow date column (today in a circle), events as soft bars in their calendar's colour, tasks with a ring to tick,
 * the now line on today. It never scrolls (in a launcher stack a swipe changes the widget), so each page holds as many
 * whole days as fit its height; ‹ › page through three weeks, "วันนี้" comes back, and after ten minutes left alone it
 * opens on today again. Overdue work is one red "ค้าง N" in the header.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            val now = LocalDateTime.now()
            val state = currentState<Preferences>()
            val fresh = System.currentTimeMillis() - (state[TOUCHED] ?: 0L) < IDLE_MS
            val size = LocalSize.current
            val space = (size.height.value - CHROME).toInt().coerceAtLeast(TodayWeek.ROW)
            val pages = TodayWeek.pages(data.days(now), space)
            val index = (if (fresh) state[PAGE] ?: 0 else 0).coerceIn(0, pages.lastIndex)
            val page = pages[index]
            val left = data.todayTasks.size
            val late = DayPlan.lateCount(data.plan)
            val hasToday = page.days.first().date == data.today
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(cp { it.card }).padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            ) {
                Row(GlanceModifier.fillMaxWidth().height(HEADER.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().padding(start = 4.dp).clickable(open(context))) {
                        Label(span(page.days.first().date, page.days.last().date), cp { it.text }, 15.sp, bold = true)
                        if (hasToday) Row {
                            Label(if (left == 0) tr("ไม่มีงานค้างวันนี้", "all done today") else tr("เหลือ $left งานวันนี้", "$left left today"), cp { it.muted }, 11.5.sp)
                            if (late > 0) Label(tr(", ค้าง $late ›", ", $late overdue ›"), cp { it.red }, 11.5.sp, bold = true, modifier = GlanceModifier.clickable(open(context)))
                        } else Label(tr("${page.days.size} วัน", "${page.days.size} days"), cp { it.muted }, 11.5.sp)
                    }
                    if (index > 0) {
                        Box(
                            GlanceModifier.height(26.dp).cornerRadius(13.dp).background(cp { it.accent }).padding(horizontal = 9.dp)
                                .clickable(actionRunCallback<PageAction>(actionParametersOf(GO to 0))),
                            contentAlignment = Alignment.Center,
                        ) { Label(tr("วันนี้", "Today"), cp { it.onAccent }, 11.5.sp) }
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    PageButton("‹", tr("ก่อนหน้า", "Previous"), (index - 1).coerceAtLeast(0))
                    Spacer(GlanceModifier.width(4.dp))
                    PageButton("›", tr("ถัดไป", "Next"), (index + 1).coerceAtMost(pages.lastIndex))
                    Spacer(GlanceModifier.width(4.dp))
                    Circle(context, R.drawable.ic_w_plus, cp { it.accent }, cp { it.onAccent }, MainActivity.ACTION_ADD, size = 30)
                }
                Spacer(GlanceModifier.height(4.dp))
                page.days.forEachIndexed { i, day ->
                    if (i > 0) Spacer(GlanceModifier.height(TodayWeek.DAY_GAP.dp))
                    DayBlock(context, day, data.today, cap = page.cap)
                }
            }
        }
    }

    companion object {
        internal val PAGE = intPreferencesKey("page")
        internal val TOUCHED = longPreferencesKey("touched")
        internal val GO = ActionParameters.Key<Int>("go")
        private const val IDLE_MS = 10 * 60 * 1000L
        private const val HEADER = 34
        /** Padding, header and the gap under it, in dp: what the days cannot use. */
        private const val CHROME = 10 + 8 + HEADER + 4
    }
}

/** Moves a Today widget to another page and remembers when, so it comes back to today after a while. */
class PageAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val go = parameters[TodayWidget.GO] ?: 0
        updateAppWidgetState(context, glanceId) {
            it[TodayWidget.PAGE] = go
            it[TodayWidget.TOUCHED] = System.currentTimeMillis()
        }
        TodayWidget().update(context, glanceId)
    }
}

@Composable
private fun PageButton(text: String, label: String, go: Int) {
    Box(
        GlanceModifier.size(30.dp).cornerRadius(15.dp).background(cp { it.raised })
            .clickable(actionRunCallback<PageAction>(actionParametersOf(TodayWidget.GO to go))),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = TextStyle(color = cp { it.text }, fontSize = 17.sp)) }
}

/** "30 ต.ค. ถึง 1 พ.ย.", or one date for a page of one day. */
private fun span(from: LocalDate, to: LocalDate): String {
    val f = java.time.format.DateTimeFormatter.ofPattern("d MMM", app.omnitask.ui.TH)
    return if (from == to) from.format(f) else tr("${from.format(f)} ถึง ${to.format(f)}", "${from.format(f)} to ${to.format(f)}")
}

/** One day: the date column (40 dp, today in a circle, the month under the first of a month; the header names the rest) and its rows. */
@Composable
private fun DayBlock(context: Context, day: TodayWeek.Day, today: LocalDate, cap: Int?) {
    val isToday = day.date == today
    Row(GlanceModifier.fillMaxWidth()) {
        Column(GlanceModifier.width(40.dp).clickable(open(context)), horizontalAlignment = Alignment.CenterHorizontally) {
            val wd = day.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE", app.omnitask.ui.TH))
            if (isToday) {
                Column(
                    GlanceModifier.size(36.dp).cornerRadius(18.dp).background(cp { it.accent }),
                    verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Label(wd, cp { it.onAccent }, 10.sp)
                    Label("${day.date.dayOfMonth}", cp { it.onAccent }, 14.5.sp, bold = true)
                }
            } else {
                Label(wd, cp { it.muted }, 11.sp)
                Label("${day.date.dayOfMonth}", cp { it.text }, 16.sp)
                if (day.date.dayOfMonth == 1) {
                    Label(day.date.format(java.time.format.DateTimeFormatter.ofPattern("MMM", app.omnitask.ui.TH)), cp { it.accent }, 9.sp, bold = true)
                }
            }
        }
        Spacer(GlanceModifier.width(7.dp))
        Column(GlanceModifier.defaultWeight()) {
            if (day.count == 0) {
                Box(GlanceModifier.height(TodayWeek.EMPTY.dp).padding(start = 9.dp), contentAlignment = Alignment.CenterStart) {
                    Label(tr("ว่าง", "Free"), cp { it.faint }, 12.5.sp)
                }
                return@Column
            }
            var shown = 0
            var first = true
            day.rows.forEach { r ->
                if (r !is TodayWeek.Row.Now && cap != null && shown >= cap) return@forEach
                if (!first) Spacer(GlanceModifier.height(TodayWeek.ROW_GAP.dp))
                first = false
                when (r) {
                    is TodayWeek.Row.Now -> NowRow(r.time)
                    is TodayWeek.Row.Event -> { EventRow(context, r); shown++ }
                    is TodayWeek.Row.Todo -> { TodoRow(context, r, today); shown++ }
                }
            }
            if (cap != null && day.count > cap) {
                Spacer(GlanceModifier.height(TodayWeek.ROW_GAP.dp))
                Box(GlanceModifier.height(TodayWeek.MORE.dp).padding(start = 9.dp).clickable(open(context)), contentAlignment = Alignment.CenterStart) {
                    Label(tr("+ อีก ${day.count - cap}", "+ ${day.count - cap} more"), cp { it.accent }, 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun NowRow(time: LocalTime) {
    Row(GlanceModifier.fillMaxWidth().height(TodayWeek.NOW.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(cp { it.red })) {}
        Label(hm(time), cp { it.red }, 10.sp, bold = true, modifier = GlanceModifier.padding(horizontal = 4.dp))
        Box(GlanceModifier.defaultWeight().height(1.5.dp).background(cp { it.red })) {}
    }
}

/** The calendar's colour softened into the card, as the soft bars of the mock. */
private fun soft(argb: Int?, p: Palette): Color {
    val c = argb?.let { Color(it) } ?: p.teal
    return androidx.compose.ui.graphics.lerp(p.card, c, if (p.dark) 0.26f else 0.20f)
}

@Composable
private fun EventRow(context: Context, e: TodayWeek.Row.Event) {
    Row(
        GlanceModifier.fillMaxWidth().height(TodayWeek.ROW.dp).cornerRadius(9.dp)
            .background(if (e.past) cp { it.raised } else cp { soft(e.color, it) }).padding(horizontal = 9.dp).clickable(open(context)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(3.dp).height(15.dp).cornerRadius(2.dp).background(if (e.past) cp { it.faint } else e.color?.let { ColorProvider(Color(it)) } ?: cp { it.teal })) {}
        Label(e.title, if (e.past) cp { it.faint } else cp { it.text }, 14.sp, modifier = GlanceModifier.defaultWeight().padding(start = 8.dp))
        if (e.time.isNotEmpty()) Label(e.time, cp { it.muted }, 12.sp, modifier = GlanceModifier.padding(start = 6.dp))
    }
}

/** A task on one line: the ring (a grey dot once done), the title, and its time or project on the right. */
@Composable
private fun TodoRow(context: Context, r: TodayWeek.Row.Todo, today: LocalDate) {
    val t = r.task
    val done = t.status == Status.DONE
    val late = !done && t.due?.let { it < today } == true
    Row(GlanceModifier.fillMaxWidth().height(TodayWeek.ROW.dp).padding(start = 4.dp, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Box(GlanceModifier.size(20.dp), contentAlignment = Alignment.Center) {
                Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(cp { it.faint })) {}
            }
        } else Box(
            GlanceModifier.size(20.dp).clickable(actionRunCallback<TickAction>(actionParametersOf(RAW to t.raw))),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_w_ring), tr("ติ๊กเสร็จ", "Mark done"), GlanceModifier.size(17.dp),
                colorFilter = ColorFilter.tint(if (late) cp { it.red } else cp { it.faint }))
        }
        Label(t.title, if (done) cp { it.faint } else cp { it.text }, 14.sp, strike = done,
            modifier = GlanceModifier.defaultWeight().padding(start = 6.dp).clickable(open(context)))
        if (!done && r.time.isNotEmpty()) Label(r.time, cp { it.muted }, 12.sp, modifier = GlanceModifier.padding(start = 6.dp))
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
                        f?.let { Label(Focus.ageDays(it, data.today)?.let { a -> tr("ค้าง $a วัน", "$a days old") } ?: "", cp { it.muted }, 12.sp) }
                    }
                }
            }
        }
    }
}

class WaitingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaitingWidget()
}
