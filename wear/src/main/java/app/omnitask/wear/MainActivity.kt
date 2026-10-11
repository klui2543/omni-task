package app.omnitask.wear

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.time.format.DateTimeFormatter

/** Shows tonight's alarm. Opening it also reads the phone's latest time, in case the app came after it. */
class MainActivity : ComponentActivity() {
    private var at by mutableStateOf<java.time.LocalDateTime?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        at = WakeAlarm.at(this)
        setContent {
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label("Omni", W.muted, 12)
                val time = at
                if (time == null) {
                    Label(stringResource(R.string.none), W.text, 15, modifier = Modifier.padding(top = 6.dp))
                    Label(stringResource(R.string.none_hint), W.muted, 12, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Label(stringResource(R.string.tonight), W.text, 14, modifier = Modifier.padding(top = 6.dp))
                    Label(time.format(HM), W.text, 34, FontWeight.Medium)
                    val sleep = WakeAlarm.sleepMinutes(this@MainActivity)
                    if (sleep > 0) Pill(stringResource(R.string.sleep, hm(sleep)), if (sleep < 5 * 60) W.red else W.lime)
                }
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
