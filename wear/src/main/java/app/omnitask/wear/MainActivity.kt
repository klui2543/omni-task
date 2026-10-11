package app.omnitask.wear

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tonight's alarm, and the place to set or change it on the watch; a time set here goes back to the phone.
 * Opening it also reads the phone's latest time, in case the app came after it.
 */
class MainActivity : ComponentActivity() {
    private var at by mutableStateOf<LocalDateTime?>(null)
    private var editing by mutableStateOf<LocalTime?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        at = WakeAlarm.at(this)
        setContent {
            val time = editing
            if (time != null) {
                BackHandler { editing = null }
                Editor(time, { editing = it }) {
                    at = WakeAlarm.setOnWatch(this, time)
                    editing = null
                }
            } else {
                Overview()
            }
        }
    }

    @Composable
    private fun Overview() {
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("Omni Watch", W.muted, 12)
            val time = at
            if (time == null) {
                Label(stringResource(R.string.none), W.text, 15, modifier = Modifier.padding(top = 6.dp))
                Label(
                    stringResource(R.string.set), Color.Black, 13, FontWeight.Medium,
                    Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(W.lime)
                        .clickable { editing = LocalTime.of(6, 0) }.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                Column(Modifier.clickable { editing = time.toLocalTime() }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Label(stringResource(R.string.tonight), W.text, 14, modifier = Modifier.padding(top = 6.dp))
                    Label(time.format(HM), W.text, 34, FontWeight.Medium)
                }
                val sleep = WakeAlarm.sleepMinutes(this@MainActivity)
                if (sleep > 0) Pill(stringResource(R.string.sleep, hm(sleep)), if (sleep < 5 * 60) W.red else W.lime)
                Pill(stringResource(R.string.tap_to_edit), W.muted)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        at = WakeAlarm.at(this)
        Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*${WakeAlarm.PATH}")).addOnSuccessListener { items ->
            items.forEach { WakeAlarm.fromPhone(this, DataMapItem.fromDataItem(it).dataMap) }
            items.release()
            at = WakeAlarm.at(this)
        }
    }

    private companion object {
        val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** − and + move the time five minutes (held, they keep going), and so does each click of the bezel. */
@Composable
private fun Editor(time: LocalTime, onChange: (LocalTime) -> Unit, onSave: () -> Unit) {
    val focus = remember { FocusRequester() }
    val current by rememberUpdatedState(time)
    val step = { minutes: Long -> onChange(current.plusMinutes(minutes)) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .onRotaryScrollEvent { e -> if (e.verticalScrollPixels != 0f) step(if (e.verticalScrollPixels > 0) 5 else -5); true }
            .focusRequester(focus).focusable(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(stringResource(R.string.wake), W.muted, 12)
        Row(Modifier.padding(top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Repeat("−") { step(-5) }
            Label(time.format(DateTimeFormatter.ofPattern("HH:mm")), W.text, 34, FontWeight.Medium, Modifier.padding(horizontal = 8.dp))
            Repeat("+") { step(5) }
        }
        Label(
            stringResource(R.string.save), Color.Black, 13, FontWeight.Medium,
            Modifier.clip(RoundedCornerShape(50)).background(W.lime).clickable(onClick = onSave).padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Repeat(text: String, onStep: () -> Unit) {
    val latest by rememberUpdatedState(onStep)
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(W.raised).pointerInput(Unit) {
            // The repeat runs in the input's own scope; the gesture block itself cannot start coroutines.
            coroutineScope {
                awaitEachGesture {
                    awaitFirstDown()
                    latest()
                    val held = launch {
                        delay(450)
                        while (true) { latest(); delay(90) }
                    }
                    waitForUpOrCancellation()
                    held.cancel()
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) { Label(text, W.text, 20) }
}
