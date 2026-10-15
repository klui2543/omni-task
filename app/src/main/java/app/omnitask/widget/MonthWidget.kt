package app.omnitask.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.ExperimentalGlanceApi
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
import androidx.glance.appwidget.runComposition
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.omnitask.model.CellStyle
import app.omnitask.model.DayWork
import app.omnitask.model.Lang
import app.omnitask.model.MonthGrid
import app.omnitask.model.MonthMarks
import app.omnitask.model.MonthNav
import app.omnitask.notify.AlarmKind
import app.omnitask.notify.PlannedAlarm
import androidx.datastore.preferences.core.MutablePreferences
import kotlinx.datetime.LocalDateTime
import app.omnitask.model.PaletteChoice
import app.omnitask.model.Task
import app.omnitask.model.ThemeMode
import app.omnitask.model.load
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import app.omnitask.notify.Scheduler
import app.omnitask.ui.Palette
import app.omnitask.ui.Palettes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The month on the home screen (4×5): a grid from Sunday with the owner's coloured frames and emoji, red Sundays and
 * public holidays, each day's work shown as a deeper background or as short title bars ([CellStyle]), and today and
 * tomorrow listed below. The grid is drawn as one
 * picture, since a widget cannot draw a frame running across days; a tap layer over it lists the tapped day's work
 * under the grid. ‹ › move this widget a month at a time; the title brings it back to this month and today. Frames
 * and emoji are set from the pencil.
 *
 * Which month and day show is the widget's own state, saved with it and kept in memory by [MonthLive], and the work
 * for several months around it is loaded once into [MonthSource]: a tap recomposes from what is already in memory.
 * A tap does not go through Glance's own session for the widget (see [MonthLive] for why). A new [STAMP] (see
 * [OmniWidgets.refresh]) tells the composition to load again after the vault or the calendar changed.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = MonthLive.key(context, id)
        WidgetLog.attach(context)
        WidgetLog.add("provideGlance widget=$widgetId")
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val first = MonthSource.load(
            context, (MonthLive.navs[widgetId] ?: nav(prefs)).fresh(System.currentTimeMillis()).offset, maxOf(prefs[STAMP] ?: 0L, MonthLive.stamp.value),
        )
        provideContent {
            val state = currentState<Preferences>()
            // What a tap set in memory is the newest; the saved state stands in after the app was closed. Left alone for
            // ten minutes, the widget is back on today (an alarm redraws it then; see MonthPick).
            val nav = (MonthLive.navs[widgetId] ?: nav(state)).fresh(System.currentTimeMillis())
            val stamp = maxOf(state[STAMP] ?: 0L, MonthLive.stamp.value)
            var source by remember { mutableStateOf(first) }
            // Load again only when the data changed or the month moved past what is loaded.
            LaunchedEffect(nav.offset, stamp) {
                if (stamp != source.stamp || !source.covers(nav.offset)) source = MonthSource.load(context, nav.offset, stamp)
            }
            MonthContent(context, source.view(nav.offset, nav.listed(source.today)))
        }
    }

    companion object {
        /** The month shown, as months from this one. */
        internal val OFFSET = intPreferencesKey("offset")
        /** The day tapped, whose work the list shows; absent shows today and tomorrow. */
        internal val DAY = stringPreferencesKey("day")
        /** Changes when the vault or the calendar did, so the loaded work is read again. */
        internal val STAMP = longPreferencesKey("stamp")
        /** When the widget was last tapped, for going back to today after a while. */
        internal val TOUCHED = longPreferencesKey("touched")

        internal fun nav(p: Preferences) = MonthNav(
            offset = p[OFFSET] ?: 0,
            picked = p[DAY]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            touchedAt = p[TOUCHED] ?: 0L,
        )

        internal fun save(p: MutablePreferences, nav: MonthNav) {
            p[OFFSET] = nav.offset
            if (nav.picked == null) p.remove(DAY) else p[DAY] = nav.picked.toString()
            p[TOUCHED] = nav.touchedAt
        }

        /**
         * Applies [change] to the widget's place, shows it at once (see [MonthLive]), saves it for after the app is
         * closed, and sets the alarm that brings the widget back to today.
         */
        internal suspend fun act(context: Context, id: GlanceId, change: (MonthNav, Long) -> MonthNav) {
            val now = System.currentTimeMillis()
            val widgetId = MonthLive.key(context, id)
            val current = MonthLive.navs[widgetId] ?: nav(getAppWidgetState(context, PreferencesGlanceStateDefinition, id))
            val next = change(current.fresh(now), now)
            MonthLive.show(context, id, next)
            // The broadcast is done; saving and the alarm need not hold the next tap up.
            val app = context.applicationContext
            MonthLive.scope.launch {
                runCatching { updateAppWidgetState(app, id) { p -> save(p, next) } }.onFailure { WidgetLog.add("save failed $it") }
                MonthLive.armHome(app)
            }
        }

        /** Redraws every month widget with fresh data. */
        suspend fun refresh(context: Context) {
            // One stamp for all of them, so they share one load; a composition MonthLive keeps reads it from memory and
            // loads again itself, so only a widget without one goes through Glance's own update.
            val stamp = System.currentTimeMillis()
            Snapshot.withMutableSnapshot { MonthLive.stamp.value = stamp }
            GlanceAppWidgetManager(context).getGlanceIds(MonthWidget::class.java).forEach { id ->
                runCatching {
                    updateAppWidgetState(context, id) { it[STAMP] = stamp }
                    if (!MonthLive.poke(context, id)) MonthWidget().update(context, id)
                }
            }
            WidgetLog.add("refresh stamp=$stamp")
        }
    }
}

/**
 * The month widget's composition kept running inside the app while the widget is in use, so a tap redraws only what
 * changed and quick taps fold into one redraw.
 *
 * Going through Glance's own session was slow on the owner's phone: [GlanceAppWidget.update] waits for that session,
 * which Glance runs through WorkManager and ends a few seconds after the last event, and starting it again took about
 * five seconds with every later tap waiting behind it. Drawing the whole widget afresh for each tap took about half a
 * second, and the taps queued up behind each other. Here the composition runs in the app's own process (Glance's
 * [runComposition]), the tapped place is snapshot state it reads ([navs]), and each new picture goes straight to
 * [AppWidgetManager]. Each tap is also sent to the session as an event, which keeps it alive while the widget is in
 * use and has it draw at its quick rate; left alone it ends, and while the widget was tapped in the last [KEEP_MS] it
 * is started again in the background, so the next tap finds it ready. The launcher's own requests (an update, a new
 * size) are answered from here too, so only one composition draws this widget.
 */
internal object MonthLive {
    /** Where each widget is, by app widget id; set by a tap, read while composing. */
    val navs = mutableStateMapOf<Int, MonthNav>()

    /** The data stamp [MonthWidget.refresh] set last, for compositions that started before it. */
    val stamp = mutableStateOf(0L)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private const val KEEP_MS = 90_000L
    private val running = HashMap<Int, Job>()
    /** The picture each widget was given last, to give again when the launcher asks. */
    private val lastViews = HashMap<Int, RemoteViews>()

    @Volatile
    private var lastTap = 0L

    /** When the widget was last tapped, in elapsedRealtime, for the log and for WidgetWarm. */
    @Volatile
    var lastTapAt = 0L
        private set

    @Volatile
    private var homeArmedAt = 0L

    fun key(context: Context, id: GlanceId): Int = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(-1)

    fun set(widgetId: Int, nav: MonthNav) {
        Snapshot.withMutableSnapshot { navs[widgetId] = nav }
    }

    /** Shows [nav] on the widget: the kept composition redraws with it, or one is started for it. */
    fun show(context: Context, id: GlanceId, nav: MonthNav) {
        val widgetId = key(context, id)
        lastTapAt = SystemClock.elapsedRealtime()
        lastTap = System.currentTimeMillis()
        set(widgetId, nav)
        val app = context.applicationContext
        val session = GlanceSessions.session(widgetId)
        WidgetLog.add("tap widget=$widgetId day=${nav.picked} offset=${nav.offset} session=${session != null} warm=${WidgetWarm.running}")
        WidgetWarm.keep(app)
        val job = keep(app, id, widgetId, null) { views -> publish(app, widgetId, views) }
        if (session != null) scope.launch { runCatching { GlanceSessions.updateGlance(session) }.onFailure { WidgetLog.add("event failed $it") } }
        else WidgetLog.add("no session, job active=${job.isActive}")
    }

    /** Tells a kept composition that its data changed; false when this widget has none. */
    fun poke(context: Context, id: GlanceId): Boolean {
        val session = GlanceSessions.session(key(context, id)) ?: return false
        scope.launch { runCatching { GlanceSessions.updateGlance(session) } }
        return true
    }

    /** Gives the launcher the last picture again and has the composition draw afresh; false when there is none yet. */
    fun again(context: Context, widgetId: Int, options: Bundle?): Boolean {
        val views = synchronized(running) { lastViews[widgetId] } ?: return false
        WidgetLog.add("launcher asked widget=$widgetId options=${options != null}")
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views) }
        val session = GlanceSessions.session(widgetId) ?: return false
        scope.launch { runCatching { if (options != null) GlanceSessions.updateAppWidgetOptions(session, options) else GlanceSessions.updateGlance(session) } }
        return true
    }

    /** Sets the alarm that brings the widget back to today, at most once a while: it only needs to come after the taps. */
    fun armHome(context: Context) {
        val now = System.currentTimeMillis()
        if (now - homeArmedAt < 30_000) return
        homeArmedAt = now
        runCatching {
            Scheduler.scheduleOneOff(
                context,
                PlannedAlarm(AlarmKind.RESCAN, LocalDateTime.now().plusSeconds(MonthNav.IDLE_MS / 1000 + 5), "month-widget-home"),
            )
        }.onFailure { WidgetLog.add("alarm failed $it") }
    }

    private fun publish(context: Context, widgetId: Int, views: RemoteViews) {
        val began = SystemClock.elapsedRealtime()
        synchronized(running) { lastViews[widgetId] = views }
        val result = runCatching { AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views) }
        WidgetLog.add("drew widget=$widgetId ${WidgetLog.ms(lastTapAt)} ms after the last tap, handing over took ${WidgetLog.ms(began)} ms${result.exceptionOrNull()?.let { " failed $it" } ?: ""}")
    }

    /**
     * Keeps [id]'s composition running and hands each new picture to [publish]; [options] stands in for the launcher's
     * (the emulator test has no launcher). Falls back to Glance's own update when the composition cannot run here.
     */
    @OptIn(ExperimentalGlanceApi::class)
    fun keep(context: Context, id: GlanceId, widgetId: Int, options: Bundle?, publish: (RemoteViews) -> Unit): Job = synchronized(running) {
        running[widgetId]?.takeIf { it.isActive } ?: scope.launch {
            try {
                do {
                    val began = SystemClock.elapsedRealtime()
                    WidgetLog.add("composition starts widget=$widgetId")
                    val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                    val sizes = options ?: AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
                    var drawn = 0
                    MonthWidget().runComposition(context, id, options = sizes, state = state).conflate().collect { publish(it); drawn++ }
                    WidgetLog.add("composition ended widget=$widgetId after ${WidgetLog.ms(began)} ms, $drawn pictures")
                    // Nothing drawn here: Glance's own update takes over. One that ends at once would only spin.
                    if (drawn == 0) {
                        MonthWidget().update(context, id)
                        break
                    }
                    if (WidgetLog.ms(began) < 1_000) break
                } while (System.currentTimeMillis() - lastTap < KEEP_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WidgetLog.add("composition failed widget=$widgetId $e")
                runCatching { MonthWidget().update(context, id) }
            }
        }.also { running[widgetId] = it }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onReceive(context: Context, intent: Intent) {
        WidgetLog.attach(context)
        WidgetLog.add("receiver ${intent.action?.substringAfterLast('.')}")
        super.onReceive(context, intent)
    }

    // The launcher asks for the widget again (its own schedule, or the stack it is in turned to it): a kept composition
    // answers at once, and only a widget without one goes through Glance's own session.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val left = appWidgetIds.filter { !MonthLive.again(context, it, null) }
        if (left.isNotEmpty()) super.onUpdate(context, appWidgetManager, left.toIntArray())
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        if (!MonthLive.again(context, appWidgetId, newOptions)) super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}

private val DELTA = ActionParameters.Key<Int>("delta")
private val DAY_PARAM = ActionParameters.Key<String>("day")

/** ‹ and › move the widget a month; a delta of 0 (the title) brings it back to this month and today's list. */
class MonthShift : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA] ?: 0
        MonthWidget.act(context, glanceId) { nav, now -> if (delta == 0) nav.home(now) else nav.shift(delta, now) }
    }
}

/** A tap on a day always lists that day's work (see [MonthNav] for the rules). */
class MonthPick : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val day = parameters[DAY_PARAM]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        MonthWidget.act(context, glanceId) { nav, now -> nav.tapDay(day, LocalDate.now(), now) }
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

/** Opens the app's calendar at [day], with that day's work listed. */
internal fun openCalendar(context: Context, day: LocalDate) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_CALENDAR)
        .putExtra(MainActivity.EXTRA_DAY, day.toString())
        .setData(android.net.Uri.parse("omnitask://widget/calendar/$day")),
)

/**
 * The work, events and holidays from [from] to [to], loaded once; [view] picks out one month and one day from it
 * without reading anything again.
 */
internal class MonthSource(
    val today: LocalDate,
    val stamp: Long,
    private val from: LocalDate,
    private val to: LocalDate,
    private val marks: MonthMarks,
    private val tasks: List<Task>,
    private val events: List<CalendarEvent>,
    private val holidays: Map<LocalDate, String>,
    private val style: CellStyle = CellStyle.TINT,
) {
    private fun month(offset: Int) = today.withDayOfMonth(1).plusMonths(offset.toLong())

    fun covers(offset: Int): Boolean = MonthGrid.days(month(offset)).let { it.first() >= from && it.last() <= to }

    fun view(offset: Int, picked: LocalDate?): MonthData {
        val month = month(offset)
        val days = MonthGrid.days(month)
        val selected = picked?.takeIf { it != today && it >= from && it <= to }
        val listed = if (selected == null) listOf(today, today.plusDays(1)) else listOf(selected)
        fun tasksOn(d: LocalDate) = tasks.filter { (it.due ?: it.scheduled) == d }
        fun eventsOn(d: LocalDate) = events.filter { MonthData.onDay(it, d) }
        val work = MonthData.work(days, month, ::tasksOn, ::eventsOn)
        val agenda = listed.map { d ->
            val events: List<MonthData.Item> = eventsOn(d).sortedWith(compareBy<CalendarEvent>({ !it.allDay }, { it.begin })).map {
                MonthData.Item.Event(it.title, if (it.allDay || it.begin.date != d) tr("ทั้งวัน", "All day") else hm(it.begin.time))
            }
            // Today also carries the work past its deadline, as on Focus and the Today widget, after today's own.
            val overdue = if (d == today) tasks.filter { t -> t.due?.let { it < today } == true }.sortedBy { it.due }
                .map { MonthData.Item.Todo(it, ChronoUnit.DAYS.between(it.due!!, today)) } else emptyList()
            d to events + tasksOn(d).map { MonthData.Item.Todo(it) } + overdue
        }
        return MonthData(today, month, offset, marks, holidays, work, selected, agenda, style)
    }

    companion object {
        /** Stands in for the phone's data in the emulator test that draws the widget for a look. */
        @androidx.annotation.VisibleForTesting
        internal var sample: MonthSource? = null

        /**
         * The months loaded last, kept for as long as the app's process lives. Glance ends a widget's session after five
         * seconds without a tap (and about 45 seconds after it began), and the next tap starts a new one; reading the
         * vault and the calendar again there took seconds, so taps seemed lost. A new session takes these instead.
         */
        @Volatile
        private var kept: MonthSource? = null

        /** Whether [source] can stand for a fresh load: same data ([stamp]), same day, and the month in what it holds. */
        fun reusable(source: MonthSource?, stamp: Long, offset: Int, today: LocalDate): Boolean =
            source != null && source.stamp == stamp && source.today == today && source.covers(offset)

        /** Loads the months around [offset]: three before it and three after, so ‹ › rarely need a load. */
        suspend fun load(context: Context, offset: Int, stamp: Long): MonthSource {
            sample?.let { return it }
            kept?.takeIf { reusable(it, stamp, offset, LocalDate.now()) }?.let { return it }
            return read(context, offset, stamp).also { kept = it }
        }

        private suspend fun read(context: Context, offset: Int, stamp: Long): MonthSource = withContext(Dispatchers.IO) {
            val began = SystemClock.elapsedRealtime()
            try { readNow(context, offset, stamp) } finally { WidgetLog.add("loaded the vault and the calendar in ${WidgetLog.ms(began)} ms") }
        }

        private fun readNow(context: Context, offset: Int, stamp: Long): MonthSource {
            Lang.load(context)
            Appearance.load(context)
            val today = LocalDate.now()
            val center = today.withDayOfMonth(1).plusMonths(offset.toLong())
            val from = minOf(MonthGrid.start(center.minusMonths(3)), today)
            val to = maxOf(MonthGrid.days(center.plusMonths(3)).last(), today.plusDays(1))
            val holidayIds = runCatching { CalendarReader.calendars(context).filter { it.holiday }.map { it.id }.toSet() }.getOrDefault(emptySet())
            val events = runCatching { CalendarReader.events(context, from.atStartOfDay(), to.plusDays(1).atStartOfDay()) }.getOrDefault(emptyList())
                .filter { it.calendarId !in holidayIds }
            val holidays = runCatching { CalendarReader.holidays(context, from, to) }.getOrDefault(emptyMap())
            val tasks = Scheduler.loadVaultTasks(context).filter { it.isOpen }
            return MonthSource(today, stamp, from, to, MonthMarksStore.local(context), tasks, events, holidays, MonthMarksStore.cellStyle(context))
        }
    }
}

internal class MonthData(
    val today: LocalDate,
    val month: LocalDate,
    val offset: Int,
    val marks: MonthMarks,
    val holidays: Map<LocalDate, String>,
    /** What each day holds that the grid shows: events first, then open work; a daily routine is left out. */
    val work: Map<LocalDate, List<Mark>>,
    /** The day tapped, boxed on the grid; null when the list shows today and tomorrow. */
    val selected: LocalDate?,
    val agenda: List<Pair<LocalDate, List<Item>>>,
    val style: CellStyle = CellStyle.TINT,
) {
    /** Compared by value, so the widget keeps its picture when a tap leaves the work as it was. */
    data class Mark(val title: String, val event: Boolean)

    sealed interface Item {
        class Event(val title: String, val time: String) : Item
        /** [late] is how many days past its deadline an overdue task is, listed under today. */
        class Todo(val task: Task, val late: Long = 0) : Item
    }

    companion object {
        /** An all-day event ends at the next midnight; a timed one counts on every day it touches. */
        fun onDay(e: CalendarEvent, d: LocalDate): Boolean =
            e.begin.date <= d && (e.end.date > d || (e.begin.date == d))

        /** What the grid shows on each of [days], from the open work and the events on them. */
        fun work(
            days: List<LocalDate>,
            month: LocalDate,
            tasksOn: (LocalDate) -> List<Task>,
            eventsOn: (LocalDate) -> List<CalendarEvent>,
        ): Map<LocalDate, List<Mark>> {
            val events = days.associateWith(eventsOn)
            val inMonth = days.filter { it.monthNumber == month.monthNumber }
            val routine = DayWork.routine(inMonth.associateWith { d -> events[d].orEmpty().map { it.title } }, inMonth)
            return days.associateWith { d ->
                events[d].orEmpty().filter { it.title.trim() !in routine }
                    .sortedWith(compareBy<CalendarEvent>({ !it.allDay }, { it.begin })).map { Mark(it.title, true) } +
                    tasksOn(d).filter { it.isOpen }.map { Mark(it.title, false) }
            }.filterValues { it.isNotEmpty() }
        }
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

/**
 * The month pictures drawn last, kept across compositions: a tap draws the widget afresh (see MonthWidget.push), and
 * the picture only changes with the month, the work, the style or the size. A few are kept, one per size the launcher
 * asks for.
 */
private object Pictures {
    data class Key(
        val month: LocalDate, val today: LocalDate, val marks: MonthMarks, val holidays: Map<LocalDate, String>,
        val work: Map<LocalDate, List<MonthData.Mark>>, val style: CellStyle, val width: Float, val cell: Float, val rows: Int,
        val palette: Palette,
    )

    private val kept = object : LinkedHashMap<Key, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>?) = size > 4
    }

    @Synchronized
    fun get(key: Key, draw: () -> Bitmap): Bitmap = kept.getOrPut(key, draw)
}

/** The least room the list under the grid needs: a day's heading, one row and the "more" line. */
private const val MIN_LIST = 86f

@Composable
private fun MonthContent(context: Context, data: MonthData) {
    val size = LocalSize.current
    val rows = MonthGrid.weeks(data.month)
    val gridWidth = size.width.value - 24f
    // Header and weekday heads take about 56dp. Days get the room first, up to a size a finger hits easily (taller
    // with title bars); the list keeps at least a heading, one row and the "more" line, or goes when that does not fit.
    val room = size.height.value - 16f - 56f - TAG_INSET
    val want = if (data.style == CellStyle.BARS) 58f else 52f
    val withList = ((room - MIN_LIST) / rows).coerceAtMost(want)
    val showAgenda = withList >= 40f
    val cell = if (showAgenda) withList else (room / rows).coerceIn(24f, 64f)
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
            // The picture changes only with the month, the work or the size; a tap on a day keeps it and moves the box,
            // drawn on top, so the launcher gets a small update.
            val palette = widgetPalette(context)
            val bitmap = Pictures.get(Pictures.Key(data.month, data.today, data.marks, data.holidays, data.work, data.style, gridWidth, cell, rows, palette)) {
                MonthPainter(context, palette, maxScale = 2f, background = palette.card.toArgb(), ring = false)
                    .draw(data, gridWidth, cell, rows)
            }
            Image(ImageProvider(bitmap), MonthGrid.title(data.month), GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Column(GlanceModifier.fillMaxSize().padding(top = TAG_INSET.dp)) {
                val days = MonthGrid.days(data.month)
                for (w in 0 until rows) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (i in 0 until 7) {
                            val d = days[w * 7 + i]
                            // With the list showing, a tap lists the day's work there; too small for a list, it opens the app.
                            val tap = if (showAgenda) actionRunCallback<MonthPick>(actionParametersOf(DAY_PARAM to d.toString())) else openCalendar(context, d)
                            Box(GlanceModifier.defaultWeight().fillMaxHeight().clickable(tap)) {
                                // The chosen day is boxed whole, so the tap is plainly on that day.
                                if (d == data.selected) Image(
                                    ImageProvider(R.drawable.w_cell_pick), null,
                                    GlanceModifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds,
                                    colorFilter = ColorFilter.tint(cp { it.accent }),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showAgenda) {
            Spacer(GlanceModifier.height(8.dp))
            Agenda(context, data, room - rows * cell - 8f)
        }
    }
}

@Composable
private fun Header(context: Context, data: MonthData) {
    Row(GlanceModifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        val title = GlanceModifier.defaultWeight().padding(start = 2.dp)
        Text(
            MonthGrid.title(data.month),
            if (data.offset != 0 || data.selected != null) title.clickable(actionRunCallback<MonthShift>(actionParametersOf(DELTA to 0))) else title.clickable(openCalendar(context, data.today)),
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
private fun Agenda(context: Context, data: MonthData, heightDp: Float) {
    // A widget in a stack cannot scroll: the list shows what fits, and the last line says how much is left and opens
    // the app. Heights are what each row takes, a little generous so nothing is cut at the bottom.
    class Line(val height: Float, val entry: Boolean, val content: @Composable () -> Unit)
    val lines = ArrayList<Line>()
    data.agenda.forEachIndexed { index, (day, items) ->
        lines += Line(if (index > 0) 26f else 20f, false) { DayHead(context, data, day, index > 0) }
        data.holidays[day]?.let { name -> lines += Line(20f, false) { Label(name, cp { it.red }, 12.5.sp, modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp)) } }
        if (items.isEmpty() && data.holidays[day] == null) {
            lines += Line(22f, false) { Label(tr("ว่าง", "Nothing planned"), cp { it.faint }, 12.5.sp, modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp)) }
        }
        items.forEach { entry -> lines += Line(30f, true) { AgendaLine(context, day, entry) } }
    }
    // Everything fits, or as much as fits above the "more" line.
    val budget = if (lines.sumOf { it.height.toDouble() } <= heightDp) heightDp else heightDp - 28f
    var used = 0f
    var shownLines = lines.takeWhile { used += it.height; used <= budget }
    // A day's heading with nothing shown under it goes too; the "more" line covers it.
    if (shownLines.size < lines.size) shownLines = shownLines.dropLastWhile { !it.entry }
    val left = lines.count { it.entry } - shownLines.count { it.entry }
    LazyColumn(GlanceModifier.fillMaxSize()) {
        shownLines.forEach { line -> item { line.content() } }
        if (left > 0) item {
            Row(GlanceModifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp).clickable(openCalendar(context, data.selected ?: data.today)), verticalAlignment = Alignment.CenterVertically) {
                Label(tr("อีก $left รายการ, เปิดในแอป", "$left more, open the app"), cp { it.accentText }, 12.5.sp)
            }
        }
    }
}

@Composable
private fun DayHead(context: Context, data: MonthData, day: LocalDate, gap: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = if (gap) 6.dp else 0.dp, bottom = 2.dp).clickable(openCalendar(context, day)), verticalAlignment = Alignment.CenterVertically) {
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
private fun AgendaLine(context: Context, day: LocalDate, item: MonthData.Item) {
    Row(GlanceModifier.fillMaxWidth().height(30.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when (item) {
            is MonthData.Item.Event -> {
                Box(GlanceModifier.width(3.dp).height(16.dp).cornerRadius(2.dp).background(cp { it.teal })) {}
                Label(item.title, cp { it.text }, 13.5.sp, modifier = GlanceModifier.defaultWeight().padding(start = 10.dp).clickable(openCalendar(context, day)))
                Label(item.time, cp { it.muted }, 11.5.sp)
            }
            is MonthData.Item.Todo -> {
                Box(
                    GlanceModifier.size(20.dp).clickable(actionRunCallback<TickAction>(actionParametersOf(RAW to item.task.raw))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.ic_w_ring), tr("ติ๊กเสร็จ", "Mark done"), GlanceModifier.size(17.dp), colorFilter = ColorFilter.tint(if (item.late > 0) cp { it.red } else cp { it.faint }))
                }
                Label(item.task.title, cp { it.text }, 13.5.sp, modifier = GlanceModifier.defaultWeight().padding(start = 8.dp).clickable(openCalendar(context, day)))
                if (item.late > 0) Label(tr("เลย ${item.late} วัน", "${item.late} days late"), cp { it.red }, 11.5.sp)
            }
        }
    }
}

/**
 * Draws the month grid with its frames: the lines between days, each day's work (a deeper background, or title bars,
 * as [MonthData.style] says), the frames (a light fill, an outline round the outside only, a name tag on the top edge),
 * day numbers, the box round the chosen day and up to three emoji per day.
 */
internal class MonthPainter(
    private val context: Context,
    private val p: Palette,
    /** Pixels per dp at most; the widget keeps its picture small so each redraw sends little to the launcher. */
    maxScale: Float = 2.75f,
    /** Fills the picture with this colour and drops its transparency, which halves its size. */
    private val background: Int? = null,
    /** Boxes the chosen day; the widget draws the box as a separate piece so a tap needs no new picture. */
    private val ring: Boolean = true,
) {
    private val dm = context.resources.displayMetrics
    private val scale = minOf(dm.density, maxScale)
    /** The density this painter draws at, for sizing the widget's picture and its tap layer alike. */
    val pixelsPerDp get() = scale
    private fun dp(v: Float) = v * scale
    private fun sp(v: Float) = v * scale * (dm.scaledDensity / dm.density)

    fun draw(data: MonthData, widthDp: Float, cellDp: Float, rows: Int): Bitmap {
        val w = dp(widthDp)
        val cw = w / 7f
        val ch = dp(cellDp)
        val top = dp(TAG_INSET)
        val bmp = Bitmap.createBitmap(
            w.toInt().coerceAtLeast(1), (top + ch * rows).toInt().coerceAtLeast(1),
            if (background != null) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        background?.let { c.drawColor(it) }
        val days = MonthGrid.days(data.month)
        fun rect(i: Int) = RectF((i % 7) * cw, top + (i / 7) * ch, (i % 7 + 1) * cw, top + (i / 7 + 1) * ch)
        val index = days.withIndex().associate { (i, d) -> d to i }
        fun inMonth(d: LocalDate) = d.monthNumber == data.month.monthNumber
        val bars = data.style == CellStyle.BARS

        // How full each day is, as a deeper background (the tint style).
        if (!bars) {
            val tint = Paint().apply { style = Paint.Style.FILL; color = p.lime.toArgb() }
            days.forEachIndexed { i, d ->
                val level = DayWork.level(data.work[d]?.size ?: 0)
                if (level == 0 || !inMonth(d)) return@forEachIndexed
                tint.alpha = TINT_ALPHA[level]
                c.drawRect(rect(i), tint)
            }
        }

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

        // The chosen day, boxed whole as on the widget.
        if (ring) data.selected?.let { d -> index[d] }?.let { i ->
            val r = rect(i).apply { inset(dp(1f), dp(1f)) }
            c.drawRect(r, Paint().apply { style = Paint.Style.FILL; color = p.accent.toArgb(); alpha = 40 })
            c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.5f); color = p.accent.toArgb() })
        }

        // Day numbers, title bars and emoji.
        // Bars need the room under the number, so the number stays small with them.
        val big = cellDp >= 46f && !bars
        val radius = dp(if (big) 13f else 11.5f)
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(if (big) 14f else 12.5f); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
        val todayBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent.toArgb() }
        val barBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val barText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(9f); color = p.onAccent.toArgb() }
        val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(10f); textAlign = Paint.Align.RIGHT }
        val more = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(8.5f); textAlign = Paint.Align.RIGHT; color = p.muted.toArgb() }
        val moreLeft = Paint(more).apply { textAlign = Paint.Align.LEFT; textSize = sp(9f) }
        val small = cellDp < 34f
        days.forEachIndexed { i, d ->
            val r = rect(i)
            val cy = r.top + dp(if (small) 3f else 4f) + radius
            if (d == data.today) {
                c.drawCircle(r.centerX(), cy, radius, todayBg)
                num.color = p.onAccent.toArgb(); num.typeface = Typeface.DEFAULT_BOLD
            } else {
                num.typeface = Typeface.DEFAULT
                num.color = when {
                    MonthGrid.isSunday(d) || d in data.holidays -> p.red.toArgb()
                    else -> p.text2.toArgb()
                }
                if (!inMonth(d)) num.alpha = 80
            }
            c.drawText("${d.dayOfMonth}", r.centerX(), cy - (num.descent() + num.ascent()) / 2, num)
            num.alpha = 255
            if (!inMonth(d)) return@forEachIndexed
            val stickers = data.marks.stickersOn(d)
            if (bars) {
                // As many bars as fit under the number; what is left over is counted as "+n" in the top corner.
                val items = data.work[d].orEmpty()
                val bh = dp(12f)
                val step = bh + dp(1.5f)
                var y = cy + radius + dp(2f)
                val fit = ((r.bottom - dp(1f) - y + dp(1.5f)) / step).toInt().coerceAtLeast(0)
                val shown = minOf(items.size, fit)
                items.take(shown).forEach { m ->
                    barBg.color = if (m.event) p.accent.toArgb() else p.lime.toArgb()
                    val box = RectF(r.left + dp(2f), y, r.right - dp(2f), y + bh)
                    c.drawRoundRect(box, dp(3f), dp(3f), barBg)
                    c.save(); c.clipRect(box)
                    c.drawText(m.title, box.left + dp(3f), box.centerY() - (barText.descent() + barText.ascent()) / 2, barText)
                    c.restore()
                    y += step
                }
                if (items.size > shown) c.drawText("+${items.size - shown}", r.left + dp(3f), r.top + dp(11f), moreLeft)
                // Emoji go up by the number, out of the bars' way.
                if (stickers.isNotEmpty() && !small) c.drawText(stickers.first(), r.right - dp(2f), r.top + dp(12f), emoji)
            } else if (stickers.isNotEmpty() && !small) {
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

    private companion object {
        /** How deep the background gets for each level of [DayWork.level]. */
        val TINT_ALPHA = intArrayOf(0, 34, 66, 107)
    }
}
