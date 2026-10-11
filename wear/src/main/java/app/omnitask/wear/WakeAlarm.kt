package app.omnitask.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.DataMap
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The one wake alarm the watch keeps: the time the phone sent (or a snooze), saved so it survives a restart,
 * and handed to the system as an alarm clock so it rings on time even in deep sleep.
 */
object WakeAlarm {
    /** The data item the phone writes: "at" is a local date-time (empty to cancel), "sleep" the minutes of sleep. */
    const val PATH = "/wake"
    private const val PREFS = "wake"
    private const val KEY_AT = "at"
    private const val KEY_SLEEP = "sleep"
    private const val KEY_SNOOZES = "snoozes"
    private const val KEY_SENT = "sent"

    /** How many times a ring nobody answers snoozes itself before it gives up. */
    const val AUTO_SNOOZES = 3

    /** The saved time while it is still ahead. */
    fun at(context: Context): LocalDateTime? =
        prefs(context).getString(KEY_AT, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }?.takeIf { it > LocalDateTime.now() }

    fun sleepMinutes(context: Context): Long = prefs(context).getLong(KEY_SLEEP, 0)

    fun snoozes(context: Context): Int = prefs(context).getInt(KEY_SNOOZES, 0)

    /**
     * Applies what the phone sent, once per send (the app reads the item again on open, and that must not
     * undo a snooze). A time already past is dropped rather than rung late.
     */
    fun fromPhone(context: Context, map: DataMap) {
        val sent = map.getLong(KEY_SENT)
        if (sent != 0L && sent == prefs(context).getLong(KEY_SENT, 0)) return
        prefs(context).edit().putLong(KEY_SENT, sent).apply()
        val at = map.getString(KEY_AT)?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        set(context, at?.takeIf { it > LocalDateTime.now() }, map.getLong(KEY_SLEEP), snoozes = 0)
    }

    fun set(context: Context, at: LocalDateTime?, sleep: Long = sleepMinutes(context), snoozes: Int = 0) {
        prefs(context).edit().apply {
            if (at == null) remove(KEY_AT) else putString(KEY_AT, at.toString())
            putLong(KEY_SLEEP, sleep)
            putInt(KEY_SNOOZES, snoozes)
        }.apply()
        schedule(context)
    }

    fun snooze(context: Context, minutes: Long, auto: Boolean) =
        set(context, LocalDateTime.now().plusMinutes(minutes).withSecond(0).withNano(0), snoozes = if (auto) snoozes(context) + 1 else 0)

    /** Puts the saved time in the system alarm clock, or clears it. Also run after a reboot. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val ring = PendingIntent.getActivity(
            context, 0, Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = at(context)
        if (at == null || at <= LocalDateTime.now()) {
            alarms.cancel(ring)
            return
        }
        val show = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), ring)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
