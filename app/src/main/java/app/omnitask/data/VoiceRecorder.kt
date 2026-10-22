package app.omnitask.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.omnitask.MainActivity
import app.omnitask.R
import app.omnitask.model.tr
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tasks spoken into the phone. A recording runs in [VoiceRecordService], a foreground service, so it goes on while
 * the screen is off or another app is open, until the owner stops it. Each recording is a file in `files/voice`;
 * a file stays there until its tasks are saved or it is thrown away, so a recording Gemini could not read (no
 * network) is never lost.
 */
object VoiceRec {
    /** The recording in progress: its file and when it began (epoch ms). */
    data class Recording(val file: File, val since: Long)

    private val _now = MutableStateFlow<Recording?>(null)
    val now: StateFlow<Recording?> = _now

    private val _done = MutableSharedFlow<File>(extraBufferCapacity = 4)

    /** A recording that just stopped and was kept, to be written down. */
    val done: SharedFlow<File> = _done

    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Why a recording could not start or was lost, in words for the owner. */
    val failed: SharedFlow<String> = _failed

    internal fun started(r: Recording) { _now.value = r }

    internal fun stopped(kept: File?) {
        _now.value = null
        kept?.let { _done.tryEmit(it) }
    }

    internal fun fail(why: String) { _now.value = null; _failed.tryEmit(why) }

    fun dir(context: Context): File = File(context.filesDir, "voice").apply { mkdirs() }

    /** Recordings waiting to be written down, oldest first (the one in progress is not among them). */
    fun waiting(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.extension == "m4a" && f != _now.value?.file }.orEmpty().sortedBy { it.lastModified() }

    fun start(context: Context) = send(context, VoiceRecordService.START)
    fun stop(context: Context) = send(context, VoiceRecordService.STOP)
    fun discard(context: Context) = send(context, VoiceRecordService.DISCARD)

    private fun send(context: Context, action: String) {
        val intent = Intent(context, VoiceRecordService::class.java).setAction(action)
        if (action == VoiceRecordService.START) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }
}

class VoiceRecordService : Service() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> begin()
            STOP -> end(keep = true)
            DISCARD -> end(keep = false)
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (recorder != null) return
        val since = System.currentTimeMillis()
        // The notification must come first: a foreground service has a few seconds to show one.
        val note = notification(since)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTE_ID, note)
        } catch (e: Exception) {
            VoiceRec.fail(tr("เริ่มอัดไม่ได้: ${e.message}", "Could not start recording: ${e.message}"))
            stopSelf()
            return
        }
        val out = File(VoiceRec.dir(this), "voice-$since.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(MAX_MS)
            r.setOutputFile(out.absolutePath)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) end(keep = true) }
            r.prepare()
            r.start()
        } catch (e: Exception) {
            runCatching { r.release() }
            out.delete()
            VoiceRec.fail(tr("เริ่มอัดไม่ได้ ไมค์อาจถูกแอปอื่นใช้อยู่", "Could not start recording. Another app may be using the mic"))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        recorder = r
        file = out
        VoiceRec.started(VoiceRec.Recording(out, since))
    }

    private fun end(keep: Boolean) {
        val r = recorder
        val f = file
        recorder = null
        file = null
        // Stopping a recorder that heard almost nothing throws; the file is then unusable.
        val ok = r != null && runCatching { r.stop() }.isSuccess
        runCatching { r?.release() }
        val kept = f?.takeIf { keep && ok && it.length() > 0 }
        if (kept == null) f?.delete()
        VoiceRec.stopped(kept)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Killed while recording: keep what was said so far.
        if (recorder != null) end(keep = true)
        super.onDestroy()
    }

    private fun notification(since: Long): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("อัดเสียงจดงาน", "Voice notes"), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoiceRecordService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(tr("Omni กำลังอัดเสียง", "Omni is recording"))
            .setContentText(tr("กดหยุดเมื่อพูดงานครบ", "Stop when you have said everything"))
            .setWhen(since)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, tr("หยุด", "Stop"), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val START = "app.omnitask.voice.START"
        const val STOP = "app.omnitask.voice.STOP"
        const val DISCARD = "app.omnitask.voice.DISCARD"
        private const val CHANNEL = "voice"
        private const val NOTE_ID = 7301

        /** Twenty minutes: about 5 MB, well inside what Gemini takes in one request. */
        private const val MAX_MS = 20 * 60 * 1000
    }
}
