package app.omnitask.wear

import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The ringing alarm: buzzes in a steady rhythm until Stop or +5 min. Left alone it snoozes itself after
 * ten minutes, up to [WakeAlarm.AUTO_SNOOZES] times. Back and swipe do nothing, so it cannot be lost by accident.
 */
class RingActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var vibrator: Vibrator
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        @Suppress("DEPRECATION")
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
        handler.postDelayed({
            if (WakeAlarm.snoozes(this) < WakeAlarm.AUTO_SNOOZES) WakeAlarm.snooze(this, 5, auto = true) else WakeAlarm.set(this, null)
            end()
        }, 10 * 60_000L)

        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        setContent {
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(stringResource(R.string.wake_up), W.muted, 13)
                Label(now, W.text, 40, FontWeight.Medium, Modifier.padding(top = 2.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundButton(stringResource(R.string.snooze), W.raised) { WakeAlarm.snooze(this@RingActivity, 5, auto = false); end() }
                    RoundButton(stringResource(R.string.stop), W.red) { WakeAlarm.set(this@RingActivity, null); end() }
                }
            }
        }
    }

    private fun end() {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        vibrator.cancel()
        finish()
    }

    override fun onDestroy() {
        // Closed some other way (the system, a crash): stop buzzing but keep the alarm as it was.
        handler.removeCallbacksAndMessages(null)
        vibrator.cancel()
        super.onDestroy()
    }
}
