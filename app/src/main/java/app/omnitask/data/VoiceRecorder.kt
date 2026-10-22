package app.omnitask.data

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.omnitask.MainActivity
import app.omnitask.R
import app.omnitask.model.tr
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tasks spoken into the phone. A recording runs in [VoiceRecordService], a foreground service, so it goes on while
 * the screen is off or another app is open, until the owner stops it. It is cut at pauses into pieces of a few
 * seconds (`pNNN.wav` in a folder of its own under `files/voice`), each sent to Gemini as soon as it is cut, so the
 * words and the tasks show while the owner speaks. A piece stays until its recording is saved or thrown away, and
 * the words Gemini heard in it sit beside it (`pNNN.txt`), so a piece that could not be sent (no network) is tried
 * again later and nothing is lost.
 */
object VoiceRec {
    /** The recording in progress: its folder and when it began (epoch ms). */
    data class Recording(val dir: File, val since: Long)

    private val _now = MutableStateFlow<Recording?>(null)
    val now: StateFlow<Recording?> = _now

    private val _pieces = MutableSharedFlow<File>(extraBufferCapacity = 64)

    /** A piece just cut, to be written down at once. */
    val pieces: SharedFlow<File> = _pieces

    private val _done = MutableSharedFlow<File>(extraBufferCapacity = 4)

    /** The folder of a recording that just stopped and was kept. */
    val done: SharedFlow<File> = _done

    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Why a recording could not start, in words for the owner. */
    val failed: SharedFlow<String> = _failed

    internal fun started(r: Recording) { _now.value = r }
    internal fun piece(f: File) { _pieces.tryEmit(f) }

    internal fun stopped(kept: File?) {
        _now.value = null
        kept?.let { _done.tryEmit(it) }
    }

    internal fun fail(why: String) { _now.value = null; _failed.tryEmit(why) }

    fun root(context: Context): File = File(context.filesDir, "voice").apply { mkdirs() }

    /** Recordings not saved or thrown away yet, oldest first (the one in progress is not among them). */
    fun waiting(context: Context): List<File> {
        val root = root(context)
        // Single files from the first version (one file per recording) are of no use now.
        root.listFiles { f -> f.isFile }?.forEach { it.delete() }
        return root.listFiles { f -> f.isDirectory && f != _now.value?.dir && pieces(f).isNotEmpty() }.orEmpty().sortedBy { it.name }
    }

    /** A recording's pieces in order. */
    fun pieces(dir: File): List<File> = dir.listFiles { f -> f.extension == "wav" }.orEmpty().sortedBy { it.name }

    /** The words Gemini heard in [piece], or null when it has not been written down yet. */
    fun heard(piece: File): String? = File(piece.parentFile, piece.nameWithoutExtension + ".txt").takeIf { it.exists() }?.readText()

    fun keepHeard(piece: File, text: String) = File(piece.parentFile, piece.nameWithoutExtension + ".txt").writeText(text)

    /** When the recording in [dir] began (epoch ms), from its name. */
    fun since(dir: File): Long? = dir.name.toLongOrNull()

    fun start(context: Context) = send(context, VoiceRecordService.START)
    fun stop(context: Context) = send(context, VoiceRecordService.STOP)
    fun discard(context: Context) = send(context, VoiceRecordService.DISCARD)

    private fun send(context: Context, action: String) {
        val intent = Intent(context, VoiceRecordService::class.java).setAction(action)
        if (action == VoiceRecordService.START) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }
}

class VoiceRecordService : Service() {
    @Volatile private var running = false
    @Volatile private var keep = true
    private var thread: Thread? = null
    private var dir: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> begin()
            STOP -> end(keep = true)
            DISCARD -> end(keep = false)
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // asked for before the service is started
    private fun begin() {
        if (running) return
        val since = System.currentTimeMillis()
        // The notification must come first: a foreground service has a few seconds to show one.
        try {
            val note = notification(since)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTE_ID, note)
        } catch (e: Exception) {
            VoiceRec.fail(tr("เริ่มอัดไม่ได้: ${e.message}", "Could not start recording: ${e.message}"))
            stopSelf()
            return
        }
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 2 * 4))
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        if (record == null || runCatching { record.startRecording() }.isFailure || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { record?.release() }
            VoiceRec.fail(tr("เริ่มอัดไม่ได้ ไมค์อาจถูกแอปอื่นใช้อยู่", "Could not start recording. Another app may be using the mic"))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val out = File(VoiceRec.root(this), since.toString()).apply { mkdirs() }
        dir = out
        running = true
        keep = true
        VoiceRec.started(VoiceRec.Recording(out, since))
        thread = Thread({ loop(record, out, since) }, "voice-record").apply { start() }
    }

    /**
     * Reads the mic in 100 ms frames and cuts a piece at a pause (0.6 s quiet once it is 5 s long) or at 15 s.
     * A piece with no speech in it is not kept.
     */
    private fun loop(record: AudioRecord, out: File, since: Long) {
        val frame = ShortArray(FRAME)
        val piece = ByteArrayOutputStream()
        var frames = 0
        var quiet = 0
        var loud = false
        var count = 0
        var floor = 300.0
        fun cut() {
            if (frames > 0 && loud) {
                count++
                val f = File(out, "p%03d.wav".format(count))
                runCatching { f.writeBytes(wav(piece.toByteArray())) }.onSuccess { VoiceRec.piece(f) }
            }
            piece.reset(); frames = 0; quiet = 0; loud = false
        }
        try {
            while (running) {
                val n = record.read(frame, 0, FRAME)
                if (n <= 0) continue
                val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN).apply { asShortBuffer().put(frame, 0, n) }.array()
                piece.write(bytes)
                frames++
                var sum = 0.0
                for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                val rms = sqrt(sum / n)
                // The quiet level follows the room slowly, so a noisy ward is not taken for speech throughout.
                if (rms < floor * 1.5) floor = floor * 0.95 + rms * 0.05
                val speaking = rms > maxOf(450.0, floor * 3)
                if (speaking) { loud = true; quiet = 0 } else quiet++
                if ((frames >= MIN_FRAMES && quiet >= PAUSE_FRAMES) || frames >= MAX_FRAMES) cut()
                if (System.currentTimeMillis() - since > MAX_MS) { running = false }
            }
            if (keep) cut()
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            val kept = out.takeIf { keep && VoiceRec.pieces(it).isNotEmpty() }
            if (kept == null) out.deleteRecursively()
            VoiceRec.stopped(kept)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun end(keep: Boolean) {
        if (!running) { stopSelf(); return }
        this.keep = keep
        running = false
    }

    override fun onDestroy() {
        // Killed while recording: the loop keeps what was said so far.
        running = false
        thread?.join(2000)
        super.onDestroy()
    }

    /** 16-bit mono PCM at [RATE] as a WAV file. */
    private fun wav(pcm: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return b.array()
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
        private const val RATE = 16_000

        /** 100 ms of samples. */
        private const val FRAME = RATE / 10
        private const val MIN_FRAMES = 50
        private const val PAUSE_FRAMES = 6
        private const val MAX_FRAMES = 150

        /** Twenty minutes at most. */
        private const val MAX_MS = 20 * 60 * 1000L
    }
}
