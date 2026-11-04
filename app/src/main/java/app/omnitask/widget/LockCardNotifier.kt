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
import app.omnitask.model.DayFill
import app.omnitask.model.DayPlan
import app.omnitask.model.LockPlan
import app.omnitask.model.NextThing
import app.omnitask.notify.Scheduler
import app.omnitask.model.tr
import app.omnitask.time.*
import kotlinx.datetime.LocalDateTime

/**
 * The copy of the today page's plan the card reads (owner's pick ก, 2026-11-01): written by the page each time its plan
 * changes, kept on the phone only.
 */
object LockPlanStore {
    private const val PREFS = "omni-lockplan"
    private const val COPY = "copy"

    /** Keeps [copy]; false when it is the one already kept, so the card is not redrawn for nothing. */
    fun save(context: Context, copy: LockPlan.Copy): Boolean {
        val text = LockPlan.encode(copy)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(COPY, null) == text) return false
        prefs.edit().putString(COPY, text).apply()
        return true
    }

    fun load(context: Context): LockPlan.Copy? = LockPlan.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(COPY, null))
}

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
    /** Planned work on the card: the app's accent. */
    private const val ACCENT = 0xFF9DB4D3.toInt()

    private val PILL = intArrayOf(R.id.p0, R.id.p1, R.id.p2, R.id.p3, R.id.p4)
    private val NOW = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4)
    private val BAR = intArrayOf(R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4)
    private val TITLE = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4)
    private val TIME = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4)
    private val END = intArrayOf(R.id.e0, R.id.e1, R.id.e2, R.id.e3, R.id.e4)
    private val RUNEND = intArrayOf(R.id.qe0, R.id.qe1, R.id.qe2, R.id.qe3, R.id.qe4)
    private val NOWTIME = intArrayOf(R.id.m0, R.id.m1, R.id.m2, R.id.m3, R.id.m4)
    // The taller row of an event happening now, which the red line crosses (mock widget-now, way ข).
    private val RUN = intArrayOf(R.id.q0, R.id.q1, R.id.q2, R.id.q3, R.id.q4)
    private val RUNSPENT = intArrayOf(R.id.qs0, R.id.qs1, R.id.qs2, R.id.qs3, R.id.qs4)
    private val RUNLINE = intArrayOf(R.id.qx0, R.id.qx1, R.id.qx2, R.id.qx3, R.id.qx4)
    private val RUNBAR = intArrayOf(R.id.qb0, R.id.qb1, R.id.qb2, R.id.qb3, R.id.qb4)
    private val RUNTITLE = intArrayOf(R.id.qt0, R.id.qt1, R.id.qt2, R.id.qt3, R.id.qt4)
    private val RUNTIME = intArrayOf(R.id.qr0, R.id.qr1, R.id.qr2, R.id.qr3, R.id.qr4)
    // The two buttons under the one thing next.
    private val NEXTROW = intArrayOf(R.id.x0, R.id.x1, R.id.x2, R.id.x3, R.id.x4)
    private val NEXTDONE = intArrayOf(R.id.xd0, R.id.xd1, R.id.xd2, R.id.xd3, R.id.xd4)
    private val NEXTSKIP = intArrayOf(R.id.xs0, R.id.xs1, R.id.xs2, R.id.xs3, R.id.xs4)
    /** The one thing next: a lighter blue than the planned work, so it reads first. */
    private const val NEXT = 0xFFB9CBE4.toInt()

    suspend fun refresh(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (!LockCardMode.isOn(context)) return nm.cancel(ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val data = WidgetData.load(context)
        val now = LocalDateTime.now()
        // Only the owner's own: another person's calendar is never the card's headline (owner, 2026-10-31, way ค).
        app.omnitask.ui.CalendarStyles.load(context)
        // The today page's plan when it has kept one for today (owner's pick ก, 2026-11-01); the widget's rows before that.
        val others: (Long) -> Boolean = { app.omnitask.ui.CalendarStyles.label(it) != null }
        val kept = LockPlanStore.load(context)
        if (kept != null && kept.day != data.today) WidgetLog.add("lockcard: plan copy is from ${kept.day}, using the widget rows")
        val copy = kept?.takeIf { it.day == data.today }?.let { c ->
            // The owner's events put in the calendar since the page last planned.
            LockPlan.withEvents(
                c,
                data.eventsAhead.filter { !others(it.calendarId) && it.begin.date <= data.today && it.end > data.today.atStartOfDay() }.map { e ->
                    val timed = !e.allDay && e.begin.date == data.today
                    LockPlan.Slot(
                        e.title, if (timed) DayFill.minutesOf(e.begin, data.today) else null,
                        if (timed) DayFill.minutesOf(e.end, data.today).coerceAtMost(24 * 60) else null, card = false, calendar = e.calendarId,
                    )
                },
            )
        }
        val done = copy?.let { LockPlan.done(it, data.tasks, data.today) }.orEmpty()
        val day = if (copy != null) LockCard.fromPlan(copy, now, done, ACCENT) { calendarArgb(it) }
        else LockCard.today(data.today, now, data.tasks, data.eventsAhead, others) { calendarArgb(it.calendarId) }
        // The one thing next across the projects (owner's pick ก, 2026-11-03): a row of the list, never twice.
        val pick = NextThingSkips.ranked(context, data.tasks, data.today).firstOrNull()
        val (card, nextAt) = LockCard.card(day).let { c ->
            if (pick == null) c to -1 else {
                val ident = DayFill.ident(pick.task)
                val titles = setOf(pick.task.title) + copy?.slots?.filter { it.ident == ident }?.map { it.title }.orEmpty()
                LockCard.placeNext(c, day.rows, titles, pick.task.key)
            }
        }

        val nmSys = context.getSystemService(NotificationManager::class.java)
        nmSys.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("ตารางวันนี้บนหน้าล็อคจอ", "Today on the lock screen"), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
        )

        val left = data.todayTasks.size
        val late = DayPlan.lateCount(data.plan, data.today)
        val foot = (if (left == 0) tr("ไม่มีงานค้างวันนี้", "all done today") else tr("เหลือ $left งานวันนี้", "$left left today")) +
            if (late > 0) tr(", ค้าง $late", ", $late overdue") else ""

        val big = RemoteViews(context.packageName, R.layout.lock_card)
        // The day as a list (owner's pick ข2, 2026-11-01): the date, how many are left, and overdue as a red chip.
        // "อาทิตย์ 1 พ.ย." as the mock: the Thai long weekday comes as "วันอาทิตย์".
        val weekday = day.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", app.omnitask.ui.TH)).removePrefix("วัน")
        big.setTextViewText(R.id.dateline, weekday + " " + day.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", app.omnitask.ui.TH)))
        big.setTextViewText(R.id.datesub, if (left == 0) tr("ไม่มีงานค้างวันนี้", "all done today") else tr("เหลือ $left งาน", "$left left"))
        if (late > 0) {
            big.setViewVisibility(R.id.latechip, android.view.View.VISIBLE)
            big.setTextViewText(R.id.latechip, tr("ค้าง $late", "$late overdue"))
        } else big.setViewVisibility(R.id.latechip, android.view.View.GONE)
        var slot = 0
        card.rows.forEachIndexed { index, r ->
            if (slot >= PILL.size) return@forEachIndexed
            big.setViewVisibility(NEXTROW[slot], android.view.View.GONE)
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
            if (index == nextAt && pick != null) next(context, big, slot, r, pick)
            slot++
        }
        for (i in slot until PILL.size) {
            big.setViewVisibility(NEXTROW[i], android.view.View.GONE)
            big.setViewVisibility(PILL[i], android.view.View.GONE)
            big.setViewVisibility(NOW[i], android.view.View.GONE)
            big.setViewVisibility(RUN[i], android.view.View.GONE)
        }
        if (card.more > 0) {
            big.setViewVisibility(R.id.more, android.view.View.VISIBLE)
            big.setTextViewText(R.id.more, tr("อีก ${card.more} อย่าง ›", "${card.more} more ›"))
        } else big.setViewVisibility(R.id.more, android.view.View.GONE)

        // Folded (some phones show only this on the lock screen): what is on now and what is next with its start, from
        // the plan; before the page has kept one, what is next on the widget's rows.
        val first = card.rows.firstOrNull { it !is TodayWeek.Row.Now }
        val planned = copy?.let { LockPlan.lines(it, now.hour * 60 + now.minute, done) }
        val line = planned?.first ?: when (first) {
            is TodayWeek.Row.Event -> first.title + if (first.time.isNotEmpty()) "  " + first.time else ""
            is TodayWeek.Row.Todo -> first.task.title
            else -> tr("ไม่มีอะไรเหลือวันนี้", "Nothing left today")
        }
        val small = RemoteViews(context.packageName, R.layout.lock_card_small)
        small.setTextViewText(R.id.small, line)
        val nextLine = planned?.second
        small.setViewVisibility(R.id.smallnext, if (nextLine != null) android.view.View.VISIBLE else android.view.View.GONE)
        small.setTextViewText(R.id.smallnext, nextLine ?: "")
        small.setTextViewText(R.id.smallfoot, foot)

        // On Mondays what to prepare in four weeks (owner's pick ก, 2026-11-03, mock six-pains screen 3).
        ahead(context, big, data)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The round mic (owner's pick ก, 2026-11-02, mock lock-mic): a voice note. With the mic allowed it starts the
        // recording service straight from the card, which the phone runs without unlocking (owner, 2 Nov: "Can we
        // bypass lock screen?", "Ok then try"; an activity would ask to unlock first). The recording notification has
        // the stop button and the recording waits in the app's voice list. Without the permission it opens the app,
        // which asks for it.
        val micAllowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val voice = if (micAllowed) PendingIntent.getForegroundService(
            context, 1,
            Intent(context, app.omnitask.data.VoiceRecordService::class.java).setAction(app.omnitask.data.VoiceRecordService.START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ) else PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_VOICE)
                .setData(android.net.Uri.parse("omnitask://lockcard/voice")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        small.setOnClickPendingIntent(R.id.smallmic, voice)
        big.setOnClickPendingIntent(R.id.mic, voice)
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

    /**
     * The row of the one thing next: "ถัดไป: <title>" in a lighter blue, "ถัดไป" in the time column when today has no time
     * for it, and its two buttons under it in line with the titles (owner's pick ก, 2026-11-03, mock six-pains-fix).
     * Never red: put off, it simply waits.
     */
    private fun next(context: Context, big: RemoteViews, slot: Int, r: TodayWeek.Row, pick: NextThing.Pick) {
        // The colour goes in the text itself: a refreshed card can reuse the old views, and a text colour set on a
        // view would stay on whatever row sits there next time.
        fun blue(text: String) = android.text.SpannableString(text).apply {
            setSpan(android.text.style.ForegroundColorSpan(NEXT), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // As the mock: the time and the bar in the lighter blue, the name in the card's own colour.
        val label = tr("ถัดไป: ", "Next: ") + pick.task.title
        if (r is TodayWeek.Row.Event && r.run != null) {
            big.setTextViewText(RUNTITLE[slot], label)
            big.setInt(RUNBAR[slot], "setBackgroundColor", NEXT)
        } else {
            big.setTextViewText(TITLE[slot], label)
            big.setInt(BAR[slot], "setBackgroundColor", NEXT)
            val time = when (r) { is TodayWeek.Row.Event -> r.time; is TodayWeek.Row.Todo -> r.time; else -> "" }
            big.setTextViewText(TIME[slot], blue(split(time).first.ifEmpty { tr("ถัดไป", "Next") }))
        }
        big.setViewVisibility(NEXTROW[slot], android.view.View.VISIBLE)
        big.setTextViewText(NEXTDONE[slot], tr("ทำแล้ว", "Done"))
        big.setTextViewText(NEXTSKIP[slot], tr("ไม่ใช่ตอนนี้", "Not now"))
        fun button(action: String, code: Int) = PendingIntent.getBroadcast(
            context, code,
            Intent(context, LockNextReceiver::class.java).setAction(action)
                .putExtra(LockNextReceiver.EXTRA_KEY, pick.task.key).putExtra(LockNextReceiver.EXTRA_RAW, pick.task.raw),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        big.setOnClickPendingIntent(NEXTDONE[slot], button(LockNextReceiver.DONE, 21))
        big.setOnClickPendingIntent(NEXTSKIP[slot], button(LockNextReceiver.SKIP, 22))
    }

    private suspend fun ahead(context: Context, big: RemoteViews, data: WidgetData) {
        val ahead = if (NextThing.monday(data.today)) {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { Scheduler.readVaultFile(context, NextThing.MILESTONES_PATH) } }
                .onFailure { WidgetLog.add("lockcard: could not read the milestones: ${it.message}") }.getOrNull()
            NextThing.ahead(text, data.today)
        } else emptyList()
        if (ahead.isEmpty()) return big.setViewVisibility(R.id.aheadbox, android.view.View.GONE)
        big.setViewVisibility(R.id.aheadbox, android.view.View.VISIBLE)
        big.setTextViewText(R.id.aheadk, tr("4 สัปดาห์ข้างหน้า เตรียม ${ahead.size} เรื่อง", "Next 4 weeks: ${ahead.size} to prepare"))
        listOf(R.id.ahead0 to R.id.aheadm0, R.id.ahead1 to R.id.aheadm1).forEachIndexed { i, (t, m) ->
            val a = ahead.getOrNull(i)
            val shown = if (a != null) android.view.View.VISIBLE else android.view.View.GONE
            big.setViewVisibility(t, shown)
            big.setViewVisibility(m, shown)
            big.setTextViewText(t, a?.title ?: "")
            big.setTextViewText(m, a?.let { tr("ต้องเริ่มภายใน ", "Start by ") + it.by } ?: "")
        }
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
        val (start, end) = split(r.time)
        rv.setTextViewText(RUNTITLE[slot], r.title)
        rv.setTextViewText(RUNTIME[slot], start)
        rv.setTextViewText(RUNEND[slot], end)
    }

    private val CLOCK = Regex("^\\d{1,2}:\\d{2}")

    /** A row's time text as the list shows it: the start in the left column, and "ถึง 19:20" (or a project, or a day count) after the title. */
    private fun split(time: String): Pair<String, String> {
        val m = CLOCK.find(time)
        if (m != null) return m.value to time.removePrefix(m.value).trim()
        return if (time == tr("ทั้งวัน", "All day")) time to "" else "" to time
    }

    private fun pill(rv: RemoteViews, slot: Int, title: String, time: String, color: Int) {
        rv.setViewVisibility(RUN[slot], android.view.View.GONE)
        rv.setViewVisibility(NOW[slot], android.view.View.GONE)
        rv.setViewVisibility(PILL[slot], android.view.View.VISIBLE)
        rv.setInt(BAR[slot], "setBackgroundColor", color or Color.BLACK)
        val (start, end) = split(time)
        rv.setTextViewText(TITLE[slot], title)
        rv.setTextViewText(TIME[slot], start)
        rv.setTextViewText(END[slot], end)
    }
}
