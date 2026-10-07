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
import app.omnitask.ui.C
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

@Composable
private fun Label(text: String, color: Color = C.muted, size: TextUnit = 12.sp, bold: Boolean = false, maxLines: Int = 1, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text, modifier,
        style = TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal),
        maxLines = maxLines,
    )
}

@Composable
private fun Panel(context: Context, modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().cornerRadius(22.dp).background(ColorProvider(C.card)).clickable(open(context)),
    ) { content() }
}

// ---- 4×1 quick add ----

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        app.omnitask.model.Lang.load(context)
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(ColorProvider(C.card)).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(
                    tr("เพิ่มงาน เช่น ส่งรายงาน พรุ่งนี้", "Add a task, e.g. report tomorrow"), C.muted, 14.sp,
                    modifier = GlanceModifier.defaultWeight().clickable(open(context, MainActivity.ACTION_ADD)),
                )
                Circle(context, R.drawable.ic_w_mic, C.raised, C.text2, MainActivity.ACTION_VOICE)
                Spacer(GlanceModifier.width(6.dp))
                Circle(context, R.drawable.ic_w_spark, C.accent, C.onAccent, MainActivity.ACTION_ASSISTANT)
            }
        }
    }
}

@Composable
private fun Circle(context: Context, icon: Int, bg: Color, fg: Color, action: String) {
    Box(
        GlanceModifier.size(40.dp).cornerRadius(20.dp).background(ColorProvider(bg)).clickable(open(context, action)),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(icon), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(ColorProvider(fg))) }
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

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            Panel(context) {
                Column(GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
                    val total = data.todayTasks.size + data.doneToday
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            GlanceModifier.size(40.dp).cornerRadius(20.dp).background(ColorProvider(C.sunken)),
                            contentAlignment = Alignment.Center,
                        ) { Label("${data.doneToday}/$total", if (total > 0 && data.doneToday == total) C.lime else C.text, 12.sp, bold = true) }
                        Column(GlanceModifier.padding(start = 12.dp)) {
                            Label(tr("วันนี้", "Today"), C.text, 15.sp, bold = true)
                            val next = data.nextEvent(LocalDateTime.now())
                            Label(
                                next?.let { tr("นัดถัดไป ", "Next ") + hm(it.begin.toLocalTime()) + " " + it.title }
                                    ?: if (data.todayTasks.isEmpty()) tr("ไม่มีงานค้างวันนี้", "Nothing left today") else tr("ไม่มีนัดแล้ววันนี้", "No more events today"),
                                C.tealText, 12.sp,
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    data.todayTasks.take(4).forEach { t -> TickRow(context, t, data.today) }
                }
            }
        }
    }
}

@Composable
private fun TickRow(context: Context, t: Task, today: LocalDate) {
    Row(GlanceModifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(36.dp).clickable(actionRunCallback<TickAction>(actionParametersOf(RAW to t.raw))),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_w_ring), tr("ติ๊กเสร็จ", "Mark done"), GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(ColorProvider(if (t.due?.let { it < today } == true) C.red else C.faint)))
        }
        Label(t.title, C.text, 14.sp, modifier = GlanceModifier.defaultWeight().padding(start = 4.dp))
        val chip = when {
            t.due?.let { it < today } == true -> tr("เลยกำหนด", "Overdue") to C.red
            Focus.isWaiting(t) -> (Focus.waitingFor(t) ?: tr("มีคนรอ", "Waiting")) to C.amber
            t.reminderTime != null -> hm(t.reminderTime) to C.accentText
            else -> null
        }
        chip?.let { (text, color) ->
            Box(GlanceModifier.cornerRadius(11.dp).background(ColorProvider(C.raised)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Label(text, color, 11.5.sp)
            }
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
                    Label(tr("ทำต่อเลย", "Up next"), C.muted, 12.sp)
                    Label(t?.title ?: tr("ว่างแล้ว", "All clear"), C.text, 15.sp, bold = true, maxLines = 2)
                    t?.let {
                        val late = it.due?.let { d -> d < data.today } == true
                        Label(
                            when {
                                late -> tr("เลยกำหนด", "Overdue")
                                it.due == data.today -> tr("ครบวันนี้", "Due today")
                                Focus.isFutureWork(it) -> tr("ลงทุนอนาคต", "Future")
                                else -> tr("วันนี้", "Today")
                            },
                            if (late || it.due == data.today) C.red else C.muted, 12.sp,
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Label(tr("ช่องว่างถัดไป", "Next free time"), C.muted, 12.sp)
                    val gap = data.nextGap(LocalDateTime.now())
                    Label(gap?.let { (a, b) -> hm(a) + tr(" ถึง ", " to ") + hm(b) } ?: tr("วันนี้เต็มแล้ว", "Day is full"), C.accentText, 14.sp, bold = true)
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
    Column(modifier.fillMaxSize().cornerRadius(14.dp).background(ColorProvider(C.sunken)).padding(horizontal = 9.dp, vertical = 7.dp)) {
        Label(q.label, q.accent, 11.5.sp, bold = true, maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Label("$count", C.text, 20.sp, bold = true)
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
                        Label(tr("คนรออยู่ ", "Waiting ") + waiting.size, C.muted, 12.sp)
                        Label(w?.title ?: tr("ไม่มีใครรอ", "Nobody waiting"), C.text, 13.sp)
                        w?.let {
                            val age = Focus.ageDays(it, data.today)
                            Label(
                                (Focus.waitingFor(it)?.let { n -> "$n " } ?: "") + (age?.let { a -> tr("รอ $a วัน", "waiting $a days") } ?: ""),
                                if ((age ?: 0) >= 7) C.red else C.muted, 12.sp,
                            )
                        }
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    val f = data.brief.future.firstOrNull()
                    Column(GlanceModifier.defaultWeight()) {
                        Label(tr("ลงทุนอนาคต", "Future"), C.accentText, 12.sp)
                        Label(f?.title ?: tr("ยังไม่ได้เลือกงาน", "None picked yet"), C.text, 13.sp)
                        f?.let { Label(it.firstStep?.let { s -> tr("ก้าวแรก: ", "First step: ") + s } ?: Focus.ageDays(it, data.today)?.let { a -> tr("ค้าง $a วัน", "$a days old") } ?: "", C.muted, 12.sp) }
                    }
                }
            }
        }
    }
}

class WaitingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaitingWidget()
}
