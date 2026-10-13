package app.omnitask.wear

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import java.time.LocalDate
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
    private var editing by mutableStateOf<Draft?>(null)
    private var testAt by mutableStateOf<LocalDateTime?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Without it the ring still buzzes and sounds, but its screen cannot come up over the watch face.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        at = WakeAlarm.at(this)
        setContent {
            val draft = editing
            if (draft != null) {
                BackHandler { editing = null }
                Editor(draft, { editing = it }) {
                    WakeAlarm.setOnWatch(this, draft.at)
                    at = draft.at
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
                        .clickable { editing = Draft(WakeAlarm.next(LocalTime.of(6, 0)), pickedDay = false) }.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                Column(Modifier.clickable { editing = Draft(time, pickedDay = time != WakeAlarm.next(time.toLocalTime())) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Label(stringResource(R.string.alarm_on, dayWord(time.toLocalDate())), W.text, 14, modifier = Modifier.padding(top = 6.dp))
                    Label(time.format(HM), W.text, 34, FontWeight.Medium)
                    Label(stringResource(R.string.rings_in, until(time)), W.muted, 12)
                }
                val sleep = WakeAlarm.sleepMinutes(this@MainActivity)
                if (sleep > 0) Pill(stringResource(R.string.sleep, hm(sleep)), if (sleep < 5 * 60) W.red else W.lime)
                Pill(stringResource(R.string.tap_to_edit), W.muted)
            }
            val test = testAt
            Label(
                if (test == null) stringResource(R.string.test) else stringResource(R.string.test_set, test.format(HM)), W.muted, 11,
                modifier = Modifier.padding(top = 10.dp).clickable { testAt = WakeAlarm.test(this@MainActivity) }.padding(4.dp),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Puts the saved time back in the alarm clock, in case something (an update, a force stop) cleared it.
        WakeAlarm.schedule(this)
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

/**
 * What the editor holds: the day and time it would ring. [pickedDay] says the day was chosen by hand; until then the day
 * follows the time, today while it is still ahead and else tomorrow, as an alarm clock does.
 */
data class Draft(val at: LocalDateTime, val pickedDay: Boolean)

/**
 * Set the alarm like a clock's: tap the hours or the minutes to pick which one − and + (and the bezel) move, the hours
 * by one and the minutes by five (held, they keep going); the day line below goes a day back or ahead.
 */
@Composable
private fun Editor(draft: Draft, onChange: (Draft) -> Unit, onSave: () -> Unit) {
    val focus = remember { FocusRequester() }
    val current by rememberUpdatedState(draft)
    var hours by remember { mutableStateOf(true) }
    val step = { up: Boolean ->
        val d = current
        val t = d.at.toLocalTime().let { if (hours) it.plusHours(if (up) 1 else -1) else it.plusMinutes(if (up) 5 else -5) }
        onChange(if (d.pickedDay) d.copy(at = d.at.toLocalDate().atTime(t)) else d.copy(at = WakeAlarm.next(t)))
    }
    val today = LocalDate.now()
    val shiftDay = { days: Long ->
        val d = current
        val day = d.at.toLocalDate().plusDays(days)
        if (day >= today) onChange(Draft(day.atTime(d.at.toLocalTime()), pickedDay = true))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .onRotaryScrollEvent { e -> if (e.verticalScrollPixels != 0f) step(e.verticalScrollPixels > 0); true }
            .focusRequester(focus).focusable(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Repeat("−") { step(false) }
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Digits(draft.at.format(H), hours) { hours = true }
                Label(":", W.text, 30, FontWeight.Medium)
                Digits(draft.at.format(M), !hours) { hours = false }
            }
            Repeat("+") { step(true) }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("‹", if (draft.at.toLocalDate() > today) W.text else W.raised, 18, modifier = Modifier.clickable { shiftDay(-1) }.padding(horizontal = 12.dp))
            Label(dayWord(draft.at.toLocalDate()), W.text, 13)
            Label("›", W.text, 18, modifier = Modifier.clickable { shiftDay(1) }.padding(horizontal = 12.dp))
        }
        val passed = draft.at <= LocalDateTime.now()
        Label(
            if (passed) stringResource(R.string.passed) else stringResource(R.string.rings_in, until(draft.at)),
            if (passed) W.red else W.muted, 11, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Label(
            stringResource(R.string.save), Color.Black, 13, FontWeight.Medium,
            Modifier.clip(RoundedCornerShape(50)).background(if (passed) W.raised else W.lime)
                .clickable(enabled = !passed, onClick = onSave).padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

/** The hours or the minutes of the time; the one − and + move is underlined in lime. */
@Composable
private fun Digits(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(text, if (selected) W.text else W.muted, 34, FontWeight.Medium)
        Box(Modifier.size(width = 26.dp, height = 2.dp).background(if (selected) W.lime else Color.Transparent))
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
