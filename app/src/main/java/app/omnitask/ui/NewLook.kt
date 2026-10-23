package app.omnitask.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.NextUp
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate

/**
 * The new look, tried one piece at a time behind a switch in Settings so the owner can go back at once.
 * For now: one "do next" card at the top of Focus, and red kept for real deadlines (📅) only.
 * Kept on this phone, as Compose state so the screens follow the switch at once.
 */
object NewLook {
    private const val PREFS = "omni-new-look"
    var on by mutableStateOf(false)
        private set
    /** Tasks put off with "not now", for the day they were put off. */
    var skipped by mutableStateOf(emptySet<String>())
        private set
    private var skippedOn: String? = null

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        on = p.getBoolean("on", false)
        skippedOn = p.getString("skippedOn", null)
        skipped = p.getStringSet("skipped", emptySet()).orEmpty()
    }

    fun set(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", value).apply()
    }

    fun skippedFor(today: LocalDate): Set<String> = if (skippedOn == today.toString()) skipped else emptySet()

    fun skip(context: Context, key: String, today: LocalDate) {
        skipped = skippedFor(today) + key
        skippedOn = today.toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("skippedOn", skippedOn).putStringSet("skipped", skipped).apply()
    }
}

/** The one task to do next: what it is, why this one, and the free time left. Start it, or put it off for today. */
@Composable
fun NextUpCard(pick: NextUp.Pick, freeText: String?, onOpen: (Task) -> Unit, onStart: () -> Unit, onDone: () -> Unit, onSkip: () -> Unit) {
    val doing = pick.task.status == Status.IN_PROGRESS
    Card(color = C.accentDeep, border = C.accentDeep) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("ทำต่อไป", "Do next"), color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.Medium)
            Text(
                pick.task.title, Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(pick.task) },
                color = C.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            val red = pick.why == NextUp.Why.DUE_LATE || pick.why == NextUp.Why.DUE_TODAY
            Text(
                pick.reason + (freeText?.let { tr(", ว่างเหลือ $it", ", $it free") } ?: ""),
                color = if (red) C.red else C.muted, fontSize = TS.caption,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.accent)
                        .clickable { if (doing) onDone() else onStart() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (doing) tr("เสร็จแล้ว", "Done") else tr("เริ่ม", "Start"), color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.raised).clickable(onClick = onSkip).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("ไม่ใช่ตอนนี้", "Not now"), color = C.text2, fontSize = TS.body) }
            }
        }
    }
}

/** The Settings switch for the new look. */
@Composable
fun NewLookSetting(context: Context) {
    SettingsGroup(tr("ลองหน้าตาใหม่", "Try the new look")) {
        Row(Modifier.fillMaxWidth().clickable { NewLook.set(context, !NewLook.on) }.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("การ์ด ทำต่อไป บนหน้าโฟกัส", "A do next card on Focus"), color = C.text, fontSize = TS.body)
                Text(
                    tr("สีแดงเฉพาะกำหนดจริง (📅) เป้าที่ตั้งเอง (⏳) ที่เลยมาเป็นสีเทา ปิดเมื่อไหร่ก็กลับเป็นแบบเดิม", "Red only for real deadlines (📅); slipped own targets (⏳) turn grey. Switch off to go back."),
                    color = C.muted, fontSize = TS.caption,
                )
            }
            OnOff(NewLook.on)
        }
    }
}
