package app.omnitask.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The alarm clock fires [RingReceiver], which starts this service. It is the part that rings: it holds the watch
 * awake, buzzes and plays the alarm sound until Stop or +5 min, and posts a full-screen notification that opens
 * [RingActivity] even on a dark, locked screen. The screen is only the buttons; if the system will not show it,
 * the notification carries the same two. Left alone it snoozes itself after ten minutes, up to
 * [WakeAlarm.AUTO_SNOOZES] times. A test ring (from the watch app) leaves the real alarm as it was.
 */
class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null
    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null
    private var test = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { if (!test) WakeAlarm.set(this, null); end(); return START_NOT_STICKY }
            ACTION_SNOOZE -> { if (!test) WakeAlarm.snooze(this, 5, auto = false); end(); return START_NOT_STICKY }
        }
        // Marked ringing before the notification opens the ring screen, which closes itself when it is not.
        val already = state.value
        state.value = true
        // Every start from the receiver must go foreground, even one that finds the alarm already ringing.
        ServiceCompat.startForeground(this, NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (already) return START_NOT_STICKY
        test = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "omni:ring").apply { acquire(RING_MS + 60_000L) }
        buzz()
        sound()
        handler.postDelayed({
            if (!test) {
                if (WakeAlarm.snoozes(this) < WakeAlarm.AUTO_SNOOZES) WakeAlarm.snooze(this, 5, auto = true) else WakeAlarm.set(this, null)
            }
            end()
        }, RING_MS)
        return START_NOT_STICKY
    }

    private fun buzz() {
        val v = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator = v
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 400), 0), ALARM)
    }

    /** The watch's own alarm sound, looping; a watch without a speaker or sound just buzzes. */
    private fun sound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(ALARM)
                setDataSource(this@RingService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.wake_up), NotificationManager.IMPORTANCE_HIGH).apply {
                // The service makes the sound and the buzz itself; the channel stays silent so they don't double.
                setSound(null, null)
                enableVibration(false)
            },
        )
        val screen = PendingIntent.getActivity(
            this, 0, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.wake_up))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .addAction(0, getString(R.string.snooze), action(ACTION_SNOOZE))
            .addAction(0, getString(R.string.stop), action(ACTION_STOP))
            .build()
    }

    private fun action(name: String): PendingIntent =
        PendingIntent.getService(this, name.hashCode(), Intent(this, RingService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE)

    private fun quiet() {
        handler.removeCallbacksAndMessages(null)
        vibrator?.cancel()
        vibrator = null
        player?.runCatching { stop(); release() }
        player = null
        wake?.takeIf { it.isHeld }?.release()
        wake = null
    }

    private fun end() {
        quiet()
        state.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        quiet()
        state.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.omnitask.wear.STOP"
        const val ACTION_SNOOZE = "app.omnitask.wear.SNOOZE"
        const val EXTRA_TEST = "test"
        private const val CHANNEL = "wake"
        private const val NOTIFICATION = 1
        private const val RING_MS = 10 * 60_000L
        private val ALARM: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private val state = MutableStateFlow(false)

        /** True while the alarm rings; the ring screen closes itself when it turns false. */
        val ringing: StateFlow<Boolean> = state
    }
}

/** What the alarm clock fires: starts [RingService], which a receiver woken by an alarm clock is allowed to do. */
class RingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(
            Intent(context, RingService::class.java).putExtra(RingService.EXTRA_TEST, intent.getBooleanExtra(RingService.EXTRA_TEST, false)),
        )
    }
}
