package app.omnitask.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.omnitask.MainActivity
import app.omnitask.R
import app.omnitask.model.DayPlan
import app.omnitask.model.tr
import app.omnitask.time.*
import kotlinx.datetime.LocalDateTime

/** The switch for the lock-screen card, on the phone only; off by default, read from here alone. */
object LockCardMode {
    private const val PREFS = "omni-lockcard"
    private const val ON = "on"

    fun isOn(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ON, false)

    fun setOn(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ON, value).apply()
    }
}

/**
 * One silent notification that stays on the lock screen and looks like the Today widget (date circle, coloured
 * rows, the red now line), for today only. Redrawn whenever the widgets are ([OmniWidgets.refresh]); removed when the
 * switch is off.
 */
object LockCardNotifier {
    private const val CHANNEL = "lockcard"
    private const val ID = 0x10C4
    private const val SLATE = 0xFF7C8696.toInt()

    private val PILL = intArrayOf(R.id.p0, R.id.p1, R.id.p2, R.id.p3, R.id.p4)
    private val NOW = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4)
    private val BAR = intArrayOf(R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4)
    private val TITLE = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4)
    private val TIME = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4)
    private val NOWTIME = intArrayOf(R.id.m0, R.id.m1, R.id.m2, R.id.m3, R.id.m4)
    // The taller row of an event happening now, which the red line crosses (mock widget-now, way ข).
    private val RUN = intArrayOf(R.id.q0, R.id.q1, R.id.q2, R.id.q3, R.id.q4)
    private val RUNSPENT = intArrayOf(R.id.qs0, R.id.qs1, R.id.qs2, R.id.qs3, R.id.qs4)
    private val RUNLINE = intArrayOf(R.id.qx0, R.id.qx1, R.id.qx2, R.id.qx3, R.id.qx4)
    private val RUNBAR = intArrayOf(R.id.qb0, R.id.qb1, R.id.qb2, R.id.qb3, R.id.qb4)
    private val RUNTITLE = intArrayOf(R.id.qt0, R.id.qt1, R.id.qt2, R.id.qt3, R.id.qt4)
    private val RUNTIME = intArrayOf(R.id.qr0, R.id.qr1, R.id.qr2, R.id.qr3, R.id.qr4)

    suspend fun refresh(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (!LockCardMode.isOn(context)) return nm.cancel(ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val data = WidgetData.load(context)
        val now = LocalDateTime.now()
        val day = data.days(now).first()
        val card = LockCard.card(day)

        val nmSys = context.getSystemService(NotificationManager::class.java)
        nmSys.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("ตารางวันนี้บนหน้าล็อคจอ", "Today on the lock screen"), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
        )

        val left = data.todayTasks.size
        val late = DayPlan.lateCount(data.plan)
        val foot = (if (left == 0) tr("ไม่มีงานค้างวันนี้", "all done today") else tr("เหลือ $left งานวันนี้", "$left left today")) +
            if (late > 0) tr(", ค้าง $late", ", $late overdue") else ""

        val big = RemoteViews(context.packageName, R.layout.lock_card)
        val dow = day.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE", app.omnitask.ui.TH))
        big.setTextViewText(R.id.dow, dow)
        big.setTextViewText(R.id.dom, day.date.dayOfMonth.toString())
        var slot = 0
        card.rows.forEach { r ->
            if (slot >= PILL.size) return@forEach
            when (r) {
                is TodayWeek.Row.Now -> {
                    big.setViewVisibility(RUN[slot], android.view.View.GONE)
                    big.setViewVisibility(PILL[slot], android.view.View.GONE)
                    big.setViewVisibility(NOW[slot], android.view.View.VISIBLE)
                    big.setTextViewText(NOWTIME[slot], hm(r.time))
                }
                is TodayWeek.Row.Event -> r.run?.let { running(context, big, slot, r, it) } ?: pill(big, slot, r.title, r.time, r.color ?: SLATE)
                is TodayWeek.Row.Todo -> pill(big, slot, r.task.title, r.time, SLATE)
            }
            slot++
        }
        for (i in slot until PILL.size) {
            big.setViewVisibility(PILL[i], android.view.View.GONE)
            big.setViewVisibility(NOW[i], android.view.View.GONE)
            big.setViewVisibility(RUN[i], android.view.View.GONE)
        }
        if (card.more > 0) {
            big.setViewVisibility(R.id.more, android.view.View.VISIBLE)
            big.setTextViewText(R.id.more, tr("อีก ${card.more} อย่าง ›", "${card.more} more ›"))
        } else big.setViewVisibility(R.id.more, android.view.View.GONE)
        big.setTextViewText(R.id.foot, foot)

        // Folded (some phones show only this line on the lock screen): what is next.
        val first = card.rows.firstOrNull { it !is TodayWeek.Row.Now }
        val line = when (first) {
            is TodayWeek.Row.Event -> first.title + if (first.time.isNotEmpty()) "  " + first.time else ""
            is TodayWeek.Row.Todo -> first.task.title
            else -> tr("ไม่มีอะไรเหลือวันนี้", "Nothing left today")
        }
        val small = RemoteViews(context.packageName, R.layout.lock_card_small)
        small.setTextViewText(R.id.small, line)
        small.setTextViewText(R.id.smallfoot, foot)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(ID, n)
    }

    /** An event happening now: the red line and the tint of the time gone sit [run] of the way down the row. */
    private fun running(context: Context, rv: RemoteViews, slot: Int, r: TodayWeek.Row.Event, run: Float) {
        rv.setViewVisibility(NOW[slot], android.view.View.GONE)
        rv.setViewVisibility(PILL[slot], android.view.View.GONE)
        rv.setViewVisibility(RUN[slot], android.view.View.VISIBLE)
        val dp = context.resources.displayMetrics.density
        val h = TodayWeek.RUN.toFloat()
        val y = (run.coerceIn(0f, 1f) * h).coerceIn(3f, h - 3f)
        // Padding is the one size a lock-screen view can take from code on every Android version.
        rv.setViewPadding(RUNSPENT[slot], 0, 0, 0, ((h - y) * dp).toInt())
        rv.setViewPadding(RUNLINE[slot], 0, ((y - 3f) * dp).toInt(), 0, 0)
        rv.setInt(RUNBAR[slot], "setBackgroundColor", (r.color ?: SLATE) or Color.BLACK)
        rv.setTextViewText(RUNTITLE[slot], r.title)
        rv.setTextViewText(RUNTIME[slot], r.time)
    }

    private fun pill(rv: RemoteViews, slot: Int, title: String, time: String, color: Int) {
        rv.setViewVisibility(RUN[slot], android.view.View.GONE)
        rv.setViewVisibility(NOW[slot], android.view.View.GONE)
        rv.setViewVisibility(PILL[slot], android.view.View.VISIBLE)
        rv.setInt(BAR[slot], "setBackgroundColor", color or Color.BLACK)
        rv.setTextViewText(TITLE[slot], title)
        rv.setTextViewText(TIME[slot], time)
    }
}
