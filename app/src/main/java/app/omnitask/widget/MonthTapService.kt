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
 * Takes the month widget's taps, as a short foreground service.
 *
 * On the owner's Samsung phone a tap sent as a broadcast waited about five seconds whenever the app had sat still for
 * a moment, and the taps of those seconds then arrived together: the phone freezes an app in the background and holds
 * what is sent to it. It did so with the app unrestricted, and it stopped the job meant to keep the app awake five
 * seconds after it began (reason 11). A tap on a widget may start a foreground service, which the phone starts at
 * once and does not freeze while it runs. It stays [LINGER_MS] after the last tap, so the app is not frozen between
 * taps while the widget is in use (the owner asked for a minute), then ends; the next tap starts it again.
 *
 * Android shows a foreground service's notification once it has run for ten seconds, so a silent one shows in the
 * shade while the widget is in use and goes when the service ends.
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
        val widgetId = intent.getIntExtra(EXTRA_WIDGET, -1)
        val day = intent.getStringExtra(EXTRA_TAP_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val delta = if (intent.hasExtra(EXTRA_DELTA)) intent.getIntExtra(EXTRA_DELTA, 0) else null
        WidgetLog.add("tap received widget=$widgetId day=$day delta=$delta")
        val app = applicationContext
        if (intent.getBooleanExtra(EXTRA_RELOAD, false)) {
            busy++
            MonthLive.scope.launch {
                try {
                    withContext(Dispatchers.IO) { runCatching { CalendarReader.requestSync(app, force = true) } }
                    OmniWidgets.refresh(app)
                    WidgetLog.add("reload asked on the widget")
                } catch (e: Exception) {
                    WidgetLog.add("reload failed $e")
                } finally {
                    main.post { busy--; later() }
                }
            }
            return
        }
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

    /** Ends the service [LINGER_MS] from now, unless another tap comes first. */
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
                .setContentTitle(tr("กำลังเปิดวันที่แตะบนปฏิทิน", "Showing the day tapped on the calendar"))
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
        /** How long the service stays after the last tap. */
        private const val LINGER_MS = 60_000L
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
