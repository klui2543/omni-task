package app.omnitask.wear

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The ringing alarm's screen: the time and the two buttons. [RingService] does the ringing, so this only sends it
 * Stop or +5 min, and closes when the ring ends. Back and swipe do nothing, so it cannot be lost by accident.
 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })

        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        setContent {
            val ringing by RingService.ringing.collectAsState()
            LaunchedEffect(ringing) { if (!ringing) finish() }
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(stringResource(R.string.wake_up), W.muted, 13)
                Label(now, W.text, 40, FontWeight.Medium, Modifier.padding(top = 2.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundButton(stringResource(R.string.snooze), W.raised) { send(RingService.ACTION_SNOOZE) }
                    RoundButton(stringResource(R.string.stop), W.red) { send(RingService.ACTION_STOP) }
                }
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, RingService::class.java).setAction(action))
        finish()
    }
}
