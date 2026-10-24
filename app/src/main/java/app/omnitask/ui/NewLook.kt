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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
 * So far: Today with one "do next" card and no scores or nudges, red kept for real deadlines (📅), a Secretary tab in
 * place of the assistant line (Settings behind a gear on Today), the secretary opening on its chat, one button for the
 * task views, and quieter task rows. The calendar is left as it is.
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

    fun clearSkips(context: Context) {
        skipped = emptySet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("skipped").remove("skippedOn").apply()
    }

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
                Text(tr("หน้าวันนี้ แท็บเลขา และหน้าตาที่สงบขึ้น", "Today, a Secretary tab and a calmer look"), color = C.text, fontSize = TS.body)
                Text(
                    tr(
                        "แท็บ วันนี้ งาน + โปรเจกต์ เลขา (ตั้งค่าอยู่ที่เฟืองบนหน้าวันนี้) การ์ด ทำต่อไป, แดงเฉพาะกำหนดจริง (📅), งานค้างพับเป็นบรรทัดเดียว, เลขาเปิดที่แชต, มุมมองงานรวมเป็นปุ่มเดียว ปฏิทินเหมือนเดิม ปิดเมื่อไหร่ก็กลับเป็นแบบเดิม",
                        "Tabs Today, Tasks, +, Projects, Secretary (Settings is the gear on Today). A do next card, red only for real deadlines (📅), overdue folded to one line, the secretary opens on its chat, one button for the task views. The calendar is unchanged. Switch off to go back.",
                    ),
                    color = C.muted, fontSize = TS.caption,
                )
            }
            OnOff(NewLook.on)
        }
    }
}

/**
 * The day's brief at the top of the secretary's chat, in the new look: a small head with the time and a refresh, the
 * day in one line, then the task to do first and the one after, each with its reason and opening on a tap. Own
 * targets that slipped are one quiet line, with an offer to find them new dates.
 */
@Composable
fun BriefCard(
    ai: AiUi, picks: List<Pair<Task, String>>, lateTargets: Int,
    onOpen: (Task) -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit, onAsk: (String) -> Unit,
) {
    Card {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("สรุปวันนี้", "Today's brief"), Modifier.weight(1f), color = C.muted, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(enabled = !ai.briefing, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Icon(Ic.refresh, tr("อ่านใหม่", "Read again"), tint = C.faint, modifier = Modifier.size(16.dp)) }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val brief = ai.brief
                val line = when {
                    brief == null && ai.briefing -> tr("กำลังอ่านงานทั้งหมด...", "Reading all your work...")
                    brief == null && ai.briefError != null -> ai.briefError
                    brief == null -> tr("แตะปุ่มอ่านใหม่ เพื่อให้เลขาสรุปวันนี้", "Tap read again for today's brief")
                    else -> brief.summary
                }
                if (!line.isNullOrBlank()) Text(
                    line, color = if (brief == null && ai.briefError != null) C.amber else C.text2, fontSize = TS.body,
                )
                picks.take(2).forEachIndexed { i, (t, reason) ->
                    Row {
                        Text(
                            if (i == 0) tr("ทำก่อน", "First") else tr("แล้วต่อ", "Then"),
                            Modifier.width(64.dp).padding(top = 1.dp), color = if (i == 0) C.accentText else C.muted, fontSize = TS.caption, fontWeight = FontWeight.SemiBold,
                        )
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { onOpen(t) }) {
                            Text(t.title, color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (reason.isNotBlank()) Text(reason, color = C.muted, fontSize = TS.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (lateTargets > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("เลยเป้าที่ตั้งเอง $lateTargets งาน ไม่ต้องทำวันนี้", "$lateTargets own targets slipped, not for today"), Modifier.weight(1f), color = C.faint, fontSize = TS.caption)
                    Text(
                        tr("เสนอวันใหม่", "New dates"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            onAsk(tr("ช่วยเสนอวันใหม่ให้งานที่เลยเป้าที่ตั้งเอง $lateTargets งาน ตามเวลาว่างจริง ไม่ต้องทำทั้งหมดในวันเดียว", "Suggest new dates for the $lateTargets slipped own targets from my real free time, not all on one day"))
                        }.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = C.accentText, fontSize = TS.caption,
                    )
                }
            }
        }
    }
}
