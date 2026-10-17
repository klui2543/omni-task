package app.omnitask.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.omnitask.R
import app.omnitask.data.CalendarReader
import app.omnitask.data.DriveLink
import app.omnitask.data.GoogleCalendarCheck
import app.omnitask.model.Lang
import app.omnitask.model.load
import app.omnitask.model.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import app.omnitask.time.now

/**
 * The month widget's reload button, as a short foreground service: pulling Google Calendar and reading the vault again
 * can take seconds, and a foreground service is not frozen while it runs. The tap itself comes through
 * [MonthTapActivity] (see there for why), which starts this service while it is in front.
 *
 * The service stays [LINGER_MS] after the last start, then ends. Android shows a foreground service's notification
 * once it has run for ten seconds, so a silent one shows in the shade while it runs.
 */
class MonthTapService : Service() {
    private val main = Handler(Looper.getMainLooper())

    /** Reloads still running; the service waits for them before it ends. */
    private var busy = 0

    /** The newest start: ending by it leaves the service running when a tap has arrived meanwhile. */
    private var lastStart = 0

    private val stop = Runnable { if (busy == 0) stopSelf(lastStart) else later() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WidgetLog.attach(this)
        lastStart = startId
        toFront()
        intent?.let(::take)
        later()
        return START_NOT_STICKY
    }

    private fun take(intent: Intent) {
        WidgetLog.add("service started widget=${intent.getIntExtra(EXTRA_WIDGET, -1)} reload=${intent.getBooleanExtra(EXTRA_RELOAD, false)}")
        val app = applicationContext
        if (intent.getBooleanExtra(EXTRA_RELOAD, false)) {
            busy++
            MonthLive.scope.launch {
                try {
                    reloadNow(app)
                    WidgetLog.add("reload asked on the widget")
                } catch (e: Exception) {
                    WidgetLog.add("reload failed $e")
                } finally {
                    main.post { busy--; later() }
                }
            }
            return
        }
        tapNow(app, intent)
    }

    /** Ends the service [LINGER_MS] from now, unless it is started again first. */
    private fun later() {
        main.removeCallbacks(stop)
        main.postDelayed(stop, LINGER_MS)
    }

    private fun toFront() {
        if (front) return
        runCatching {
            Lang.load(this)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("ปฏิทินบนหน้าจอหลัก", "Month widget"), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
            )
            val note = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(tr("กำลังโหลดปฏิทินใหม่", "Reloading the calendar"))
                .setSilent(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                .build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            else startForeground(NOTE_ID, note)
            front = true
        }.onFailure { WidgetLog.add("foreground failed $it") }
    }

    // A short foreground service may run three minutes at a time; the next tap starts it again.
    @Deprecated("Android 15 calls the one with the service type")
    override fun onTimeout(startId: Int) = timedOut()

    override fun onTimeout(startId: Int, fgsType: Int) = timedOut()

    private fun timedOut() {
        WidgetLog.add("tap service ran its three minutes")
        stopForeground(STOP_FOREGROUND_REMOVE)
        front = false
        stopSelf(lastStart)
    }

    override fun onDestroy() {
        main.removeCallbacks(stop)
        front = false
        super.onDestroy()
    }

    companion object {
        /** Shows a day or month tap from [intent] on the widget; a tap's own work runs off the main thread. */
        internal fun tapNow(app: android.content.Context, intent: Intent) {
            val widgetId = intent.getIntExtra(EXTRA_WIDGET, -1)
            val day = intent.getStringExtra(EXTRA_TAP_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val delta = if (intent.hasExtra(EXTRA_DELTA)) intent.getIntExtra(EXTRA_DELTA, 0) else null
            MonthLive.scope.launch {
                try {
                    val id = GlanceAppWidgetManager(app).getGlanceIdBy(widgetId)
                    when {
                        day != null -> MonthWidget.act(app, id) { nav, now -> nav.tapDay(day, LocalDate.now(), now) }
                        delta != null -> MonthWidget.act(app, id) { nav, now -> if (delta == 0) nav.home(now) else nav.shift(delta, now) }
                    }
                } catch (e: Exception) {
                    WidgetLog.add("tap failed $e")
                }
            }
        }

        /** Starts the reload from [MonthTapActivity]; when the service cannot start, the reload runs without it. */
        internal fun startReload(context: android.content.Context, intent: Intent) {
            val app = context.applicationContext
            val start = Intent(app, MonthTapService::class.java).putExtras(intent).setData(intent.data)
            runCatching { app.startForegroundService(start) }.onFailure {
                WidgetLog.add("reload service failed $it, reloading without it")
                MonthLive.scope.launch { runCatching { reloadNow(app) }.onFailure { e -> WidgetLog.add("reload failed $e") } }
            }
        }

        /**
         * Reads everything again: Drive past the widgets' two-minute cache, Google's list of deleted events, and the
         * phone's calendar (its sync comes in later and redraws the widgets through [CalendarWatch]).
         */
        internal suspend fun reloadNow(app: android.content.Context) {
            withContext(Dispatchers.IO) {
                runCatching { CalendarReader.requestSync(app, force = true) }
                if (DriveLink.isOn(app)) runCatching { DriveLink.use(app) { it.refresh(0) } }.onFailure { WidgetLog.add("reload: Drive failed $it") }
                runCatching { GoogleCalendarCheck.refresh(app, force = true) }
            }
            OmniWidgets.refresh(app)
        }

        /** How long the service stays after the last start; a reload's own work keeps it longer. */
        private const val LINGER_MS = 3_000L
        private const val CHANNEL = "month-widget"
        private const val NOTE_ID = 4203

        internal const val EXTRA_WIDGET = "widget"
        internal const val EXTRA_TAP_DAY = "day"
        internal const val EXTRA_RELOAD = "reload"
        internal const val EXTRA_DELTA = "delta"

        /** Whether the service is running in the foreground, for the widget log. */
        @Volatile
        var front = false
            private set
    }
}

/**
 * Notes in the widget log when the app was held still while the widget is in use, for a minute after each tap: the
 * whole app (the phone froze it, or the screen went off) or only its main thread, where a tap is taken.
 */
internal object Stalls {
    private const val WATCH_MS = 60_000L
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var watching = false

    @Volatile
    private var probing = false

    fun watch(context: android.content.Context) {
        if (watching) return
        watching = true
        val power = context.applicationContext.getSystemService(PowerManager::class.java)
        MonthLive.scope.launch {
            try {
                var last = SystemClock.elapsedRealtime()
                while (last - MonthLive.lastTapAt < WATCH_MS) {
                    if (!probing) {
                        probing = true
                        val posted = SystemClock.elapsedRealtime()
                        main.post {
                            probing = false
                            val waited = WidgetLog.ms(posted)
                            if (waited > 500) WidgetLog.add("main thread was busy for $waited ms")
                        }
                    }
                    delay(250)
                    val now = SystemClock.elapsedRealtime()
                    if (now - last > 1_500) {
                        WidgetLog.add("the app did not run for ${now - last - 250} ms${if (power?.isInteractive == false) " (screen off)" else ""}")
                    }
                    last = now
                }
            } finally {
                watching = false
            }
        }
    }
}
