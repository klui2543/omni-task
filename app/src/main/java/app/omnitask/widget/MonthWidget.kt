package app.omnitask.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.omnitask.MainActivity
import app.omnitask.R
import app.omnitask.data.CalendarReader
import app.omnitask.data.MonthMarksStore
import app.omnitask.model.Appearance
import app.omnitask.model.Lang
import app.omnitask.model.MonthGrid
import app.omnitask.model.MonthMarks
import app.omnitask.model.PaletteChoice
import app.omnitask.model.Task
import app.omnitask.model.ThemeMode
import app.omnitask.model.load
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import app.omnitask.notify.Scheduler
import app.omnitask.ui.Palette
import app.omnitask.ui.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The month on the home screen (4×5): a grid from Sunday with the owner's coloured frames and emoji, red Sundays and
 * public holidays, a dot on days with work or events, and today and tomorrow listed below. The grid is drawn as one
 * picture, since a widget cannot draw a frame running across days; a tap layer over it lists the tapped day's work
 * under the grid. ‹ › move this widget a month at a time; the title brings it back to this month and today. Frames
 * and emoji are set from the pencil.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val offset = MonthWidgetState.offset(context, id)
        val data = MonthData.load(context, offset, MonthWidgetState.day(context, id))
        provideContent { MonthContent(context, data) }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

/** Which month each placed widget shows, as months from this one; kept per widget so two of them can differ. */
internal object MonthWidgetState {
    private fun prefs(context: Context) = context.getSharedPreferences("omnitask.monthWidget", Context.MODE_PRIVATE)

    private suspend fun key(context: Context, id: GlanceId) = "offset.${GlanceAppWidgetManager(context).getAppWidgetId(id)}"

    suspend fun offset(context: Context, id: GlanceId): Int = prefs(context).getInt(key(context, id), 0)

    suspend fun set(context: Context, id: GlanceId, offset: Int) = prefs(context).edit().putInt(key(context, id), offset).apply()

    private suspend fun dayKey(context: Context, id: GlanceId) = "day.${GlanceAppWidgetManager(context).getAppWidgetId(id)}"

    /** The day tapped on this widget, whose work the list shows; null shows today and tomorrow. */
    suspend fun day(context: Context, id: GlanceId): LocalDate? =
        prefs(context).getString(dayKey(context, id), null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    suspend fun setDay(context: Context, id: GlanceId, day: LocalDate?) =
        prefs(context).edit().apply { if (day == null) remove(dayKey(context, id)) else putString(dayKey(context, id), day.toString()) }.apply()
}

private val DELTA = ActionParameters.Key<Int>("delta")
private val DAY = ActionParameters.Key<String>("day")

/** ‹ and › move the widget a month; a delta of 0 brings it back to this month and to today's list. */
class MonthShift : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA] ?: 0
        MonthWidgetState.set(context, glanceId, if (delta == 0) 0 else MonthWidgetState.offset(context, glanceId) + delta)
        if (delta == 0) MonthWidgetState.setDay(context, glanceId, null)
        MonthWidget().update(context, glanceId)
    }
}

/** A tap on a day lists that day's work under the grid; tapping it again goes back to today. */
class MonthPick : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val day = parameters[DAY]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val now = MonthWidgetState.day(context, glanceId)
        MonthWidgetState.setDay(context, glanceId, if (now == day || day == LocalDate.now()) null else day)
        MonthWidget().update(context, glanceId)
    }
}

/** Opens the month page in the app at [day]; with [pick], that day comes selected. */
private fun openMonth(context: Context, day: LocalDate, pick: Boolean) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_MONTH)
        .putExtra(MainActivity.EXTRA_DAY, day.toString())
        .putExtra(MainActivity.EXTRA_PICK, pick)
        // A distinct address per day keeps each cell's PendingIntent its own.
        .setData(android.net.Uri.parse("omnitask://widget/month/$day/${if (pick) 1 else 0}")),
)

internal class MonthData(
    val today: LocalDate,
    val month: LocalDate,
    val offset: Int,
    val marks: MonthMarks,
    val holidays: Map<LocalDate, String>,
    /** Days with open work or an event, for the dot. */
    val busy: Set<LocalDate>,
    /** The day tapped, ringed on the grid; null when the list shows today and tomorrow. */
    val selected: LocalDate?,
    val agenda: List<Pair<LocalDate, List<Item>>>,
) {
    sealed interface Item {
        class Event(val title: String, val time: String) : Item
        class Todo(val task: Task) : Item
    }

    companion object {
        suspend fun load(context: Context, offset: Int, selected: LocalDate? = null): MonthData = withContext(Dispatchers.IO) {
            Lang.load(context)
            Appearance.load(context)
            val today = LocalDate.now()
            val month = today.withDayOfMonth(1).plusMonths(offset.toLong())
            val days = MonthGrid.days(month)
            val listed = if (selected == null || selected == today) listOf(today, today.plusDays(1)) else listOf(selected)
            val from = minOf(days.first(), listed.first())
            val to = maxOf(days.last(), listed.last())
            val holidayIds = runCatching { CalendarReader.calendars(context).filter { it.holiday }.map { it.id }.toSet() }.getOrDefault(emptySet())
            val events = runCatching { CalendarReader.events(context, from.atStartOfDay(), to.plusDays(1).atStartOfDay()) }.getOrDefault(emptyList())
                .filter { it.calendarId !in holidayIds }
            val holidays = runCatching { CalendarReader.holidays(context, from, to) }.getOrDefault(emptyMap())
            val tasks = Scheduler.loadVaultTasks(context).filter { it.isOpen }
            fun tasksOn(d: LocalDate) = tasks.filter { (it.due ?: it.scheduled) == d }
            fun eventsOn(d: LocalDate) = events.filter { onDay(it, d) }
            val busy = days.filter { d -> tasksOn(d).isNotEmpty() || eventsOn(d).isNotEmpty() }.toSet()
            val agenda = listed.map { d ->
                val events: List<Item> = eventsOn(d).sortedWith(compareBy<CalendarEvent>({ !it.allDay }, { it.begin })).map {
                    Item.Event(it.title, if (it.allDay || it.begin.date != d) tr("ทั้งวัน", "All day") else hm(it.begin.time))
                }
                d to events + tasksOn(d).map { Item.Todo(it) }
            }
            MonthData(today, month, offset, MonthMarksStore.local(context), holidays, busy, selected?.takeIf { it != today }, agenda)
        }

        /** An all-day event ends at the next midnight; a timed one counts on every day it touches. */
        fun onDay(e: CalendarEvent, d: LocalDate): Boolean =
            e.begin.date <= d && (e.end.date > d || (e.begin.date == d))
    }
}

/** The colours the picture is drawn in: what the widgets use, light or dark as the phone is now. */
internal fun widgetPalette(context: Context): Palette {
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return when {
        Appearance.palette == PaletteChoice.MIDNIGHT -> Palettes.midnight
        Appearance.themeMode == ThemeMode.LIGHT -> Palettes.graphiteLight
        Appearance.themeMode == ThemeMode.DARK -> Palettes.graphiteDark
        night -> Palettes.graphiteDark
        else -> Palettes.graphiteLight
    }
}

/** Room above the first row for a frame's name tag, which sits on the frame's top edge. */
private const val TAG_INSET = 8f

@Composable
private fun MonthContent(context: Context, data: MonthData) {
    val size = LocalSize.current
    val rows = MonthGrid.weeks(data.month)
    val gridWidth = size.width.value - 24f
    // Header and weekday heads take about 56dp; the list below needs 90dp to be worth showing.
    val room = size.height.value - 16f - 56f - TAG_INSET
    val showAgenda = room - rows * 42f >= 90f
    val cell = if (showAgenda) 42f else (room / rows).coerceIn(24f, 64f)
    val gridHeight = TAG_INSET + rows * cell
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(cp { it.card }).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Header(context, data)
        Row(GlanceModifier.fillMaxWidth().height(16.dp)) {
            MonthGrid.heads.forEachIndexed { i, h ->
                Text(
                    h, GlanceModifier.defaultWeight(),
                    style = TextStyle(color = if (i == 0) cp { it.red } else cp { it.faint }, fontSize = 10.5.sp, textAlign = TextAlign.Center),
                )
            }
        }
        Box(GlanceModifier.fillMaxWidth().height(gridHeight.dp)) {
            val bitmap = MonthPainter(context, widgetPalette(context)).draw(data, gridWidth, cell, rows)
            Image(ImageProvider(bitmap), MonthGrid.title(data.month), GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Column(GlanceModifier.fillMaxSize().padding(top = TAG_INSET.dp)) {
                val days = MonthGrid.days(data.month)
                for (w in 0 until rows) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (i in 0 until 7) {
                            val d = days[w * 7 + i]
                            // With the list showing, a tap lists the day's work there; too small for a list, it opens the app.
                            val tap = if (showAgenda) actionRunCallback<MonthPick>(actionParametersOf(DAY to d.toString())) else open(context)
                            Box(GlanceModifier.defaultWeight().fillMaxHeight().clickable(tap)) {}
                        }
                    }
                }
            }
        }
        if (showAgenda) {
            Spacer(GlanceModifier.height(8.dp))
            Agenda(context, data)
        }
    }
}

@Composable
private fun Header(context: Context, data: MonthData) {
    Row(GlanceModifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        val title = GlanceModifier.defaultWeight().padding(start = 2.dp)
        Text(
            MonthGrid.title(data.month),
            if (data.offset != 0 || data.selected != null) title.clickable(actionRunCallback<MonthShift>(actionParametersOf(DELTA to 0))) else title.clickable(open(context)),
            style = TextStyle(color = cp { it.text }, fontSize = 17.sp, fontWeight = androidx.glance.text.FontWeight.Medium),
            maxLines = 1,
        )
        IconButton(R.drawable.ic_w_back, tr("เดือนก่อน", "Previous month"), cp { it.muted }, actionRunCallback<MonthShift>(actionParametersOf(DELTA to -1)))
        IconButton(R.drawable.ic_w_next, tr("เดือนถัดไป", "Next month"), cp { it.muted }, actionRunCallback<MonthShift>(actionParametersOf(DELTA to 1)))
        IconButton(R.drawable.ic_w_pen, tr("ใส่กรอบและ Emoji", "Frames and emoji"), cp { it.accentText },
            // The pencil is the way into frames and emoji; a day picked on the widget comes along chosen.
            data.selected?.let { openMonth(context, it, pick = true) } ?: openMonth(context, data.month, pick = false))
        Spacer(GlanceModifier.width(4.dp))
        Circle(context, R.drawable.ic_w_plus, cp { it.accent }, cp { it.onAccent }, MainActivity.ACTION_ADD, size = 30)
    }
}

@Composable
private fun IconButton(icon: Int, description: String, tint: ColorProvider, action: androidx.glance.action.Action) {
    Box(GlanceModifier.size(32.dp).cornerRadius(16.dp).clickable(action), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), description, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(tint))
    }
}

@Composable
private fun Agenda(context: Context, data: MonthData) {
    LazyColumn(GlanceModifier.fillMaxSize()) {
        data.agenda.forEachIndexed { index, (day, items) ->
            item { DayHead(data, day, index > 0) }
            data.holidays[day]?.let { name -> item { Label(name, cp { it.red }, 12.5.sp, modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp)) } }
            if (items.isEmpty() && data.holidays[day] == null) {
                item { Label(tr("ว่าง", "Nothing planned"), cp { it.faint }, 12.5.sp, modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp)) }
            }
            items.forEach { entry -> item { AgendaLine(context, entry) } }
        }
    }
}

@Composable
private fun DayHead(data: MonthData, day: LocalDate, gap: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = if (gap) 6.dp else 0.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val stickers = data.marks.stickersOn(day).joinToString("")
        val name = when (day) {
            data.today -> tr("วันนี้ ", "Today ") + MonthGrid.dayMonth(day)
            data.today.plusDays(1) -> tr("พรุ่งนี้ ", "Tomorrow ") + MonthGrid.dayMonth(day)
            else -> MonthGrid.heads[(day.dayOfWeek.ordinal + 1) % 7] + " " + MonthGrid.dayMonth(day)
        }
        Label(name + if (stickers.isEmpty()) "" else " $stickers", if (day == data.selected) cp { it.accentText } else cp { it.muted }, 11.5.sp, bold = day == data.selected)
        data.marks.frameOn(day)?.let { f ->
            val (n, of) = f.position(day) ?: return@let
            val color = Color(f.color.argb)
            val name = f.label.ifEmpty { tr("กรอบ", "Frame") }
            Spacer(GlanceModifier.width(6.dp))
            Box(GlanceModifier.cornerRadius(9.dp).background(ColorProvider(color.copy(alpha = 0.2f))).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Label(if (of == 1) name else tr("$name วันที่ $n จาก $of", "$name, day $n of $of"), ColorProvider(color), 10.5.sp)
            }
        }
    }
}

@Composable
private fun AgendaLine(context: Context, item: MonthData.Item) {
    Row(GlanceModifier.fillMaxWidth().height(30.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when (item) {
            is MonthData.Item.Event -> {
                Box(GlanceModifier.width(3.dp).height(16.dp).cornerRadius(2.dp).background(cp { it.teal })) {}
                Label(item.title, cp { it.text }, 13.5.sp, modifier = GlanceModifier.defaultWeight().padding(start = 10.dp).clickable(open(context)))
                Label(item.time, cp { it.muted }, 11.5.sp)
            }
            is MonthData.Item.Todo -> {
                Box(
                    GlanceModifier.size(20.dp).clickable(actionRunCallback<TickAction>(actionParametersOf(RAW to item.task.raw))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.ic_w_ring), tr("ติ๊กเสร็จ", "Mark done"), GlanceModifier.size(17.dp), colorFilter = ColorFilter.tint(cp { it.faint }))
                }
                Label(item.task.title, cp { it.text }, 13.5.sp, modifier = GlanceModifier.defaultWeight().padding(start = 8.dp).clickable(open(context)))
            }
        }
    }
}

/**
 * Draws the month grid with its frames: the lines between days, the frames (a light fill, an outline round the
 * outside only, a name tag on the top edge), day numbers, the dot for busy days and up to three emoji per day.
 */
internal class MonthPainter(private val context: Context, private val p: Palette) {
    private val dm = context.resources.displayMetrics
    // Sharp enough on any phone, and small enough to stay under the widget's picture limit.
    private val scale = minOf(dm.density, 2.75f)
    private fun dp(v: Float) = v * scale
    private fun sp(v: Float) = v * scale * (dm.scaledDensity / dm.density)

    fun draw(data: MonthData, widthDp: Float, cellDp: Float, rows: Int): Bitmap {
        val w = dp(widthDp)
        val cw = w / 7f
        val ch = dp(cellDp)
        val top = dp(TAG_INSET)
        val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), (top + ch * rows).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val days = MonthGrid.days(data.month)
        fun rect(i: Int) = RectF((i % 7) * cw, top + (i / 7) * ch, (i % 7 + 1) * cw, top + (i / 7 + 1) * ch)
        val index = days.withIndex().associate { (i, d) -> d to i }

        // The lines between days.
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.control.toArgb(); alpha = 200; strokeWidth = maxOf(1f, dp(0.8f)); style = Paint.Style.STROKE }
        val half = line.strokeWidth / 2
        for (r in 0..rows) c.drawLine(0f, top + r * ch + if (r == 0) half else if (r == rows) -half else 0f, w, top + r * ch + if (r == 0) half else if (r == rows) -half else 0f, line)
        for (k in 0..7) { val x = (k * cw).coerceIn(half, w - half); c.drawLine(x, top, x, top + rows * ch, line) }

        // Frames: fill, then the outer edges, then the name tag on the first day.
        val frames = data.marks.framesBetween(days.first(), days.last())
        val fill = Paint().apply { style = Paint.Style.FILL }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.3f); strokeCap = Paint.Cap.SQUARE }
        frames.forEach { f ->
            val set = f.days.toSet()
            val argb = f.color.argb.toInt()
            fill.color = argb; fill.alpha = 28
            edge.color = argb
            f.days.forEach { d ->
                val i = index[d] ?: return@forEach
                val r = rect(i)
                c.drawRect(r, fill)
                val s = MonthGrid.sides(d, set)
                if (s.top) c.drawLine(r.left, r.top, r.right, r.top, edge)
                if (s.bottom) c.drawLine(r.left, r.bottom, r.right, r.bottom, edge)
                if (s.left) c.drawLine(r.left, r.top, r.left, r.bottom, edge)
                if (s.right) c.drawLine(r.right, r.top, r.right, r.bottom, edge)
            }
        }
        val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(9f); typeface = Typeface.DEFAULT_BOLD; color = 0xFF0E0E10.toInt() }
        val tagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        frames.filter { it.label.isNotEmpty() }.forEach { f ->
            val i = f.days.firstNotNullOfOrNull { index[it] } ?: return@forEach
            val r = rect(i)
            val text = ellipsize(f.label, tagText, cw * 3f)
            val tw = tagText.measureText(text) + dp(10f)
            val left = minOf(r.left + dp(1f), w - tw)
            val box = RectF(left, r.top - dp(7f), left + tw, r.top + dp(6f))
            tagBg.color = f.color.argb.toInt()
            c.drawRoundRect(box, dp(4f), dp(4f), tagBg)
            c.drawText(text, box.left + dp(5f), box.centerY() - (tagText.descent() + tagText.ascent()) / 2, tagText)
        }

        // Day numbers, dots and emoji.
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(12.5f); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
        val todayBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent.toArgb() }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent.toArgb(); style = Paint.Style.STROKE; strokeWidth = dp(1.6f) }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.lime.toArgb() }
        val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(10f); textAlign = Paint.Align.RIGHT }
        val more = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(8.5f); textAlign = Paint.Align.RIGHT; color = p.muted.toArgb() }
        val small = cellDp < 34f
        days.forEachIndexed { i, d ->
            val r = rect(i)
            val inMonth = d.monthNumber == data.month.monthNumber
            val cy = r.top + dp(if (small) 3f else 5f) + dp(11f)
            if (d == data.selected) c.drawCircle(r.centerX(), cy, dp(11.5f), ring)
            if (d == data.today) {
                c.drawCircle(r.centerX(), cy, dp(11.5f), todayBg)
                num.color = p.onAccent.toArgb(); num.typeface = Typeface.DEFAULT_BOLD
            } else {
                num.typeface = Typeface.DEFAULT
                num.color = when {
                    MonthGrid.isSunday(d) || d in data.holidays -> p.red.toArgb()
                    else -> p.text2.toArgb()
                }
                if (!inMonth) num.alpha = 80
            }
            c.drawText("${d.dayOfMonth}", r.centerX(), cy - (num.descent() + num.ascent()) / 2, num)
            num.alpha = 255
            if (!inMonth) return@forEachIndexed
            if (d in data.busy) c.drawCircle(r.left + dp(8.5f), r.bottom - dp(8.5f), dp(2.5f), dot)
            val stickers = data.marks.stickersOn(d)
            if (stickers.isNotEmpty() && !small) {
                val extra = stickers.size - 3
                var x = r.right - dp(3f)
                val y = r.bottom - dp(4f)
                if (extra > 0) { c.drawText("+$extra", x, y, more); x -= more.measureText("+$extra") + dp(1f) }
                c.drawText(stickers.take(3).joinToString(""), x, y, emoji)
            }
        }
        return bmp
    }

    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > max) end--
        return text.substring(0, end) + "…"
    }
}
