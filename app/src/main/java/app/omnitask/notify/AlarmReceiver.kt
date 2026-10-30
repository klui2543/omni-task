package app.omnitask.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.omnitask.MainActivity
import app.omnitask.widget.OmniWidgets
import kotlinx.coroutines.runBlocking
import app.omnitask.R
import app.omnitask.data.BehaviorLog
import app.omnitask.data.TaskLine
import app.omnitask.data.VaultRepository
import app.omnitask.model.Behavior
import app.omnitask.model.Lang
import app.omnitask.model.StuckLog
import app.omnitask.model.load
import app.omnitask.model.tr
import app.omnitask.ui.DayFillMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.concurrent.thread
import app.omnitask.time.*

/**
 * Wakes on every planned alarm, on the notification buttons, and after a reboot or app update.
 * Each wake also rebuilds the plan, so reminders follow edits that arrived through sync.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Lang.load(context)
        val pending = goAsync()
        thread {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(context: Context, intent: Intent) {
        // A reminder swiped away only goes into the behavior log; nothing else needs doing.
        if (intent.action == ACTION_DISMISS) return learn(context, intent, Behavior.DISMISS)
        ensureChannels(context)
        when (intent.action) {
            ACTION_ALARM -> onAlarm(context, intent)
            ACTION_DONE -> onDone(context, intent)
            ACTION_SNOOZE_HOUR -> snooze(context, intent, LocalDateTime.now().plusHours(1), 60)
            ACTION_SNOOZE_DAY -> snooze(context, intent, LocalDateTime.now().plusDays(1), 24 * 60)
        }
        // Boot, package update and every alarm all end the same way: a fresh plan.
        Scheduler.reschedule(context, Scheduler.loadVaultTasks(context))
        // The same wakes keep the home-screen widgets current (and roll them over at the 3-hour rescan).
        runCatching { runBlocking { OmniWidgets.refresh(context) } }
    }

    private fun onAlarm(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND)?.let { runCatching { AlarmKind.valueOf(it) }.getOrNull() } ?: return
        val id = intent.getStringExtra(Scheduler.EXTRA_ID) ?: return
        val title = intent.getStringExtra(Scheduler.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(Scheduler.EXTRA_TEXT).orEmpty()
        when (kind) {
            AlarmKind.TASK -> {
                // Skip if the line was ticked or changed since the alarm was set.
                val raw = intent.getStringExtra(Scheduler.EXTRA_RAW) ?: return
                val task = Scheduler.loadVaultTasks(context).firstOrNull { it.raw == raw } ?: return
                if (!task.isOpen) return
                // A slot the day plan gave it today says how long is left to make it (the owner's mix, 2026-10-30).
                val left = DayFillMode.reminderText(context, task, LocalDateTime.now())
                val b = base(context, CHANNEL_TASKS, title, left ?: text, reminded = raw)
                if (left != null) b.setStyle(NotificationCompat.BigTextStyle().bigText(left))
                b.setDeleteIntent(action(context, ACTION_DISMISS, intent))
                b.addAction(0, tr("เสร็จ", "Done"), action(context, ACTION_DONE, intent))
                b.addAction(0, tr("อีก 1 ชม.", "In 1 hour"), action(context, ACTION_SNOOZE_HOUR, intent))
                b.addAction(0, tr("พรุ่งนี้", "Tomorrow"), action(context, ACTION_SNOOZE_DAY, intent))
                post(context, id, b)
            }
            AlarmKind.EVENT -> post(context, id, base(context, CHANNEL_CALENDAR, title, text))
            AlarmKind.DIGEST -> {
                val msg = Digest.daily(Scheduler.loadVaultTasks(context), LocalDate.now(), Scheduler.loadSettings(context), BehaviorLog.cached(context)) ?: return
                post(context, "digest", inbox(base(context, CHANNEL_DIGEST, msg.title, msg.lines.first()), msg.lines))
            }
            AlarmKind.WEEKLY -> {
                val tasks = Scheduler.loadVaultTasks(context)
                val msg = Digest.weekly(tasks, LocalDate.now(), BehaviorLog.cached(context))
                // With long-open tasks nobody has explained, opening the review goes straight to asking about them.
                val ask = if (StuckLog.toAsk(tasks, LocalDate.now()).isNotEmpty()) MainActivity.ACTION_STUCK else null
                post(context, "weekly", inbox(base(context, CHANNEL_DIGEST, msg.title, msg.lines.first(), openAction = ask), msg.lines))
            }
            AlarmKind.TEST -> post(
                context, id,
                base(
                    context, CHANNEL_TASKS, tr("ทดสอบการแจ้งเตือน", "Test notification"),
                    tr("ถ้าเห็นข้อความนี้ตอนปิดแอปอยู่ แปลว่าการแจ้งเตือนใช้ได้แล้ว", "If you see this while the app is closed, notifications work."),
                ),
            )
            AlarmKind.RESCAN -> Unit
        }
    }

    private fun onDone(context: Context, intent: Intent) {
        val raw = intent.getStringExtra(Scheduler.EXTRA_RAW) ?: return
        val task = Scheduler.loadVaultTasks(context).firstOrNull { it.raw == raw }
        if (task != null && task.isOpen) {
            BehaviorLog.record(context, Behavior.of(Behavior.DONE, task, LocalDateTime.now(), BehaviorLog.SRC))
            runCatching {
                val repo = VaultRepository(context)
                // A repeating task also gets its next occurrence; if its rule can't be read it stays open.
                if (task.recurrence != null) repo.completeRecurring(task, LocalDate.now())
                else repo.rewriteLine(task) { TaskLine.setDone(it, true, LocalDate.now()) }
            }
        }
        cancel(context, intent)
    }

    private fun snooze(context: Context, intent: Intent, at: LocalDateTime, minutes: Int) {
        val id = intent.getStringExtra(Scheduler.EXTRA_ID) ?: return
        learn(context, intent, Behavior.SNOOZE, minutes)
        Scheduler.scheduleOneOff(
            context,
            PlannedAlarm(
                AlarmKind.TASK, at, "snooze:$id",
                intent.getStringExtra(Scheduler.EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(Scheduler.EXTRA_TEXT).orEmpty(),
                taskRaw = intent.getStringExtra(Scheduler.EXTRA_RAW),
                fileUri = intent.getStringExtra(Scheduler.EXTRA_FILE),
            ),
        )
        cancel(context, intent)
    }

    /** Notes in the behavior log what was done with the reminder of the task line the alarm carries. */
    private fun learn(context: Context, intent: Intent, ev: String, minutes: Int? = null) {
        val task = intent.getStringExtra(Scheduler.EXTRA_RAW)?.let { TaskLine.parse(it) } ?: return
        BehaviorLog.record(context, Behavior.of(ev, task, LocalDateTime.now(), BehaviorLog.SRC, min = minutes))
    }

    private fun cancel(context: Context, intent: Intent) {
        intent.getStringExtra(Scheduler.EXTRA_ID)?.let { NotificationManagerCompat.from(context).cancel(it.hashCode()) }
    }

    /**
     * [reminded]: the task line of a task reminder, so opening the app from it is noted in the behavior log.
     * [openAction]: what the app does when opened from it ([MainActivity.EXTRA_ACTION]).
     */
    private fun base(context: Context, channel: String, title: String, text: String, reminded: String? = null, openAction: String? = null): NotificationCompat.Builder {
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reminded?.let { launch.putExtra(MainActivity.EXTRA_REMINDED, it) }
        openAction?.let { launch.putExtra(MainActivity.EXTRA_ACTION, it) }
        val extras = reminded != null || openAction != null
        val open = PendingIntent.getActivity(
            context, reminded?.hashCode() ?: openAction?.hashCode() ?: 0, launch,
            if (extras) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (channel == CHANNEL_DIGEST) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
    }

    private fun inbox(b: NotificationCompat.Builder, lines: List<String>) =
        b.setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(7).forEach { s.addLine(it) } })

    private fun action(context: Context, action: String, source: Intent): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            data = source.data
            putExtras(source)
        }
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(context: Context, id: String, b: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id.hashCode(), b.build())
    }

    companion object {
        const val ACTION_ALARM = "app.omnitask.ALARM"
        const val ACTION_DONE = "app.omnitask.DONE"
        const val ACTION_SNOOZE_HOUR = "app.omnitask.SNOOZE_HOUR"
        const val ACTION_SNOOZE_DAY = "app.omnitask.SNOOZE_DAY"
        const val ACTION_DISMISS = "app.omnitask.DISMISS"

        const val CHANNEL_TASKS = "tasks"
        const val CHANNEL_CALENDAR = "calendar"
        const val CHANNEL_DIGEST = "digest"

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_TASKS, tr("งานถึงเวลา", "Task reminders"), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CALENDAR, tr("นัดในปฏิทิน", "Calendar events"), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DIGEST, tr("สรุปงาน", "Summaries"), NotificationManager.IMPORTANCE_DEFAULT))
        }

        /** Fires a test notification a minute from now, so the user can close the app and check it arrives. */
        fun scheduleTest(context: Context) {
            Scheduler.scheduleOneOff(context, PlannedAlarm(AlarmKind.TEST, LocalDateTime.now().plusMinutes(1), "test"))
        }
    }
}
