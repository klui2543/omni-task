package app.omnitask.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.omnitask.model.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The secretary (a Claude session on the cloud) ends Claude log.md with `<!-- secretary-last: time -->` at every
 * round, every 3 hours in the day. When that time is over a day old the secretary has stopped (an expired token, a
 * retired model, a changed cloud setting), and the app says so: the one place that notices even when the secretary
 * cannot write anything.
 */
object Secretary {
    const val STALE_MS = 24 * 60 * 60 * 1000L

    private val MARK = Regex("""<!--\s*secretary-last:\s*(\S+)\s*-->""")

    /** The time of the last round, from the log's marker line; null when there is none. */
    fun lastRun(log: String?): Long? = log?.let { MARK.findAll(it).lastOrNull() }?.groupValues?.get(1)
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    /** Whether the warning shows: the secretary ran once, its last round is over a day old, and it is not hidden. */
    fun warns(last: Long?, hiddenUntil: Long, now: Long): Boolean = last != null && now - last > STALE_MS && now >= hiddenUntil

    /** "2 ชม. ที่แล้ว" and so on. */
    fun ago(last: Long, now: Long): String {
        val min = ((now - last) / 60_000L).coerceAtLeast(0)
        return when {
            min < 2 -> tr("เมื่อสักครู่", "just now")
            min < 60 -> tr("$min นาทีที่แล้ว", "$min min ago")
            min < 48 * 60 -> tr("${min / 60} ชม. ที่แล้ว", "${min / 60} h ago")
            else -> tr("${min / 1440} วันที่แล้ว", "${min / 1440} days ago")
        }
    }

    fun whenText(last: Long): String {
        val t = Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault())
        return t.format(SHORT_DATE) + " " + t.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    /** What the owner pastes into a new Claude Code session to have it repaired. */
    val REPAIR get() = tr(
        "เลขาหยุดบันทึก ช่วยตรวจและซ่อมตาม README ในโฟลเดอร์ Claude ของ vault",
        "The secretary stopped logging. Check and repair it by the README in the vault's Claude folder.",
    )
}

/** The amber card on the today page while the secretary has stopped: what happened, how to fix it, or hide it for a day. */
@Composable
internal fun SecretaryWarning(last: Long, now: Long, onHide: () -> Unit) {
    var fixing by remember { mutableStateOf(false) }
    if (fixing) SecretaryFixSheet { fixing = false }
    val days = ((now - last) / Secretary.STALE_MS).coerceAtLeast(1)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.amberSoft)
            .border(1.dp, C.amber.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(C.amber))
            Text(
                tr("เลขาไม่ได้บันทึกงานมา $days วัน", "The secretary has not logged for $days ${if (days == 1L) "day" else "days"}"),
                Modifier.padding(start = 8.dp), color = C.amber, fontSize = TS.body, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            tr(
                "บันทึกล่าสุด ${Secretary.whenText(last)} งานที่ทำกับ Claude หลังจากนั้นยังไม่ลง log",
                "Last logged ${Secretary.whenText(last)}. Work done with Claude since then is not in the log yet.",
            ),
            color = C.text2, fontSize = TS.body,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(tr("วิธีแก้", "How to fix"), { fixing = true }, Modifier.weight(1f), color = C.amber)
            GhostButton(tr("ซ่อน 1 วัน", "Hide for a day"), onHide, Modifier.weight(1f))
        }
    }
}

/** How to have the secretary repaired: paste one line into a new Claude Code session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretaryFixSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.card, contentColor = C.text, scrimColor = Color(0x99000000),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr("ซ่อมเลขา", "Repair the secretary"), color = C.text, fontSize = TS.title, fontWeight = FontWeight.Medium)
            Text(tr("1. เปิด Claude Code แล้วเริ่ม session ใหม่ (เครื่องไหนก็ได้)", "1. Open Claude Code and start a new session (any device)"), color = C.text2, fontSize = TS.body)
            Text(tr("2. วางข้อความนี้ แล้วรอ Claude ตรวจและซ่อม", "2. Paste this and let Claude check and repair it"), color = C.text2, fontSize = TS.body)
            Text(
                Secretary.REPAIR,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = C.text, fontSize = TS.body,
            )
            PrimaryButton(if (copied) tr("คัดลอกแล้ว", "Copied") else tr("คัดลอกข้อความ", "Copy the text"), {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("repair", Secretary.REPAIR))
                copied = true
                Toast.makeText(context, tr("คัดลอกแล้ว", "Copied"), Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth())
            Text(tr("สาเหตุที่พบบ่อย", "Common causes"), Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.caption)
            Text(
                tr(
                    "token ของ Obsidian หมดอายุ หรือเปลี่ยนรหัส vault\nรุ่นโมเดลที่เลขาใช้ถูกปลด\nหน้าตั้งค่าคลาวด์ของ Claude เปลี่ยน",
                    "The Obsidian token expired, or the vault password changed\nThe secretary's model was retired\nClaude's cloud settings changed",
                ),
                color = C.muted, fontSize = TS.caption,
            )
            GhostButton(tr("ปิด", "Close"), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
